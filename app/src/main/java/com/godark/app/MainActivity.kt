package com.godark.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.godark.app.dns.DnsEngine
import com.godark.app.sentinel.SentinelService
import com.godark.app.state.GoDarkState
import com.godark.app.state.Mode
import com.godark.app.state.Prefs
import com.godark.app.vpn.GoDarkVpnService

private val Neon = Color(0xFF00E676)
private val Danger = Color(0xFFFF5252)
private val Moon = Color(0xFFE0E0E0)
private val Dim = Color(0xFF9E9E9E)
private val Panel = Color(0xFF161616)

/** Remembers whether GUARD should resume when the user exits DARK. */
private object UiMemory { var resumeGuardAfterDark = false }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GoDarkScreen(fromTile = intent.getBooleanExtra("request_vpn", false)) }
    }
}

@Composable
fun GoDarkScreen(fromTile: Boolean) {
    val context = LocalContext.current
    val mode by GoDarkState.mode.collectAsState()
    val sentinel by GoDarkState.sentinelOn.collectAsState()
    val sentinelLog by GoDarkState.sentinelLog.collectAsState()
    val dnsLog by GoDarkState.dnsLog.collectAsState()
    val blocked by GoDarkState.dnsBlocked.collectAsState()
    val total by GoDarkState.dnsTotal.collectAsState()
    val blStatus by GoDarkState.blocklistStatus.collectAsState()

    var pendingMode by remember { mutableStateOf<Mode?>(null) }
    var showAbout by remember { mutableStateOf(false) }
    var upstream by remember { mutableStateOf(DnsEngine.upstream(context)) }
    var logTab by remember { mutableStateOf(0) } // 0 = DNS, 1 = Sentinel
    var showAlwaysOnHelp by remember { mutableStateOf(false) }

    // Battery-exemption status, refreshed every time we return to the app
    var batteryExempt by remember { mutableStateOf(isBatteryExempt(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                batteryExempt = isBatteryExempt(context)
                reconcileMode(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Notification permission (Android 13+)
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        reconcileMode(context)
        if (Build.VERSION.SDK_INT >= 33) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // VPN consent flow
    val vpnLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val target = pendingMode
        pendingMode = null
        if (result.resultCode == Activity.RESULT_OK && target != null) {
            GoDarkVpnService.start(context, target)
        }
    }

    fun setMode(target: Mode) {
        when (target) {
            Mode.EXPOSED -> GoDarkVpnService.stop(context)
            else -> {
                val consent: Intent? = VpnService.prepare(context)
                if (consent == null) {
                    GoDarkVpnService.start(context, target)
                } else {
                    pendingMode = target
                    vpnLauncher.launch(consent)
                }
            }
        }
    }

    fun toggleGuard() {
        when (mode) {
            Mode.GUARD -> setMode(Mode.EXPOSED)
            else -> setMode(Mode.GUARD) // from EXPOSED or DARK
        }
    }

    fun toggleDark() {
        when (mode) {
            Mode.DARK -> {
                // Exiting panic: restore GUARD if it was on before
                if (UiMemory.resumeGuardAfterDark) setMode(Mode.GUARD)
                else setMode(Mode.EXPOSED)
                UiMemory.resumeGuardAfterDark = false
            }
            else -> {
                UiMemory.resumeGuardAfterDark = (mode == Mode.GUARD)
                setMode(Mode.DARK)
            }
        }
    }

    LaunchedEffect(fromTile) { if (fromTile && mode == Mode.EXPOSED) setMode(Mode.DARK) }

    val statusColor = when (mode) {
        Mode.EXPOSED -> Danger
        Mode.GUARD -> Neon
        Mode.DARK -> Moon
    }
    val breathe = rememberInfiniteTransition(label = "breathe")
    val alpha by breathe.animateFloat(
        initialValue = 1f,
        targetValue = if (mode == Mode.GUARD) 0.5f else 1f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "alpha"
    )
    val ringColor by animateColorAsState(
        if (mode == Mode.DARK) Moon else Color(0xFF333333),
        tween(300), label = "ring"
    )

    MaterialTheme(colorScheme = darkColorScheme(primary = Neon, background = Color.Black)) {
        if (showAbout) {
            AboutScreen(onBack = { showAbout = false })
            return@MaterialTheme
        }
        Column(
            Modifier.fillMaxSize().background(Color.Black).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("GODARK", color = Color.White, fontSize = 24.sp,
                    fontWeight = FontWeight.Black, letterSpacing = 6.sp)
                Spacer(Modifier.width(10.dp))
                Text("ⓘ", color = Dim, fontSize = 18.sp,
                    modifier = Modifier.clickable { showAbout = true })
            }

            Spacer(Modifier.height(12.dp))

            // ===== STATUS BANNER — always says exactly what is happening =====
            Column(
                Modifier.fillMaxWidth()
                    .background(Panel, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    when (mode) {
                        Mode.EXPOSED -> "● EXPOSED"
                        Mode.GUARD -> "● GUARD ON"
                        Mode.DARK -> "● DARK"
                    },
                    color = statusColor.copy(alpha = alpha), fontSize = 18.sp,
                    fontWeight = FontWeight.Black, letterSpacing = 3.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    when (mode) {
                        Mode.EXPOSED -> "No protection. Trackers see you, nothing is filtered."
                        Mode.GUARD -> "Phone works normally. Trackers blocked, DNS encrypted.\n$blocked blocked of $total lookups"
                        Mode.DARK -> "Total blackout. NOTHING can send or receive. No internet until you exit."
                    },
                    color = Dim, fontSize = 13.sp, textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(16.dp))

            // ===== CONTROL 1: GUARD — the daily-driver switch =====
            Column(
                Modifier.fillMaxWidth()
                    .background(Panel, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                ModuleRow(
                    title = "Guard",
                    subtitle = "Daily protection. Leave this ON.",
                    checked = mode == Mode.GUARD,
                    enabled = mode != Mode.DARK,
                    onToggle = { toggleGuard() }
                )
                HorizontalDivider(color = Color(0xFF262626))
                ModuleRow(
                    title = "Sentinel",
                    subtitle = "Alert when camera or mic turns on",
                    checked = sentinel,
                    enabled = true,
                    onToggle = {
                        if (sentinel) SentinelService.stop(context) else SentinelService.start(context)
                    }
                )
                HorizontalDivider(color = Color(0xFF262626))
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp)
                        .clickable { upstream = DnsEngine.cycleUpstream(context) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Encrypted DNS via ${upstream.label}", color = Color.White,
                            fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(blStatus, color = Dim, fontSize = 12.sp)
                    }
                    Text("change", color = Neon, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(16.dp))

            // ===== HARDEN — make GoDark unkillable =====
            if (!batteryExempt) {
                Column(
                    Modifier.fillMaxWidth()
                        .background(Panel, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Text("HARDEN GODARK", color = Danger, fontSize = 11.sp,
                        letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Battery optimization", color = Color.White, fontSize = 14.sp)
                            Text("Android may kill GoDark in the background. Exempt it.",
                                color = Dim, fontSize = 12.sp)
                        }
                        TextButton(onClick = { requestBatteryExemption(context) }) {
                            Text("FIX", color = Neon, fontWeight = FontWeight.Bold)
                        }
                    }
                    HorizontalDivider(color = Color(0xFF262626))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Always-on VPN", color = Color.White, fontSize = 14.sp)
                            Text("Let Android itself keep GoDark alive, even after reboot.",
                                color = Dim, fontSize = 12.sp)
                        }
                        TextButton(onClick = { showAlwaysOnHelp = true }) {
                            Text("SET UP", color = Neon, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // ===== CONTROL 2: GO DARK — the panic button =====
            Box(
                Modifier.size(150.dp)
                    .border(5.dp, ringColor, CircleShape)
                    .background(if (mode == Mode.DARK) Color(0xFF1A1A1A) else Color(0xFF0A0A0A), CircleShape)
                    .clickable { toggleDark() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (mode == Mode.DARK) "EXIT\nDARK" else "GO\nDARK",
                    color = if (mode == Mode.DARK) Moon else Danger,
                    fontSize = 22.sp, fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center, letterSpacing = 3.sp
                )
            }
            Text(
                if (mode == Mode.DARK) "tap to restore connections"
                else "emergency: kill ALL connections instantly",
                color = Dim, fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp)
            )

            Spacer(Modifier.height(16.dp))

            // ===== LOGS =====
            Row(Modifier.align(Alignment.Start)) {
                LogTab("DNS", logTab == 0) { logTab = 0 }
                Spacer(Modifier.width(16.dp))
                LogTab("SENTINEL", logTab == 1) { logTab = 1 }
            }
            Spacer(Modifier.height(8.dp))
            val entries = if (logTab == 0) dnsLog else sentinelLog
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f)
                    .background(Panel, RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                if (entries.isEmpty()) {
                    item { Text("nothing yet", color = Dim, fontSize = 12.sp) }
                }
                items(entries) { entry ->
                    Text(
                        entry,
                        color = if ("⚠" in entry || "✕" in entry) Danger else Dim,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 3.dp)
                    )
                }
            }
        }

        if (showAlwaysOnHelp) {
            AlertDialog(
                onDismissRequest = { showAlwaysOnHelp = false },
                confirmButton = {
                    TextButton(onClick = {
                        showAlwaysOnHelp = false
                        try {
                            context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (_: Exception) { }
                    }) { Text("OPEN SETTINGS", color = Neon) }
                },
                dismissButton = {
                    TextButton(onClick = { showAlwaysOnHelp = false }) { Text("LATER", color = Dim) }
                },
                containerColor = Panel,
                title = { Text("Always-on VPN", color = Color.White) },
                text = {
                    Text(
                        "In the next screen:\n\n" +
                                "1. Tap the ⚙ next to GoDark\n" +
                                "2. Turn ON \u201cAlways-on VPN\u201d\n" +
                                "3. Optionally turn ON \u201cBlock connections without VPN\u201d. " +
                                "Then if GoDark ever dies, the internet stops instead of " +
                                "silently leaving you exposed.",
                        color = Dim, fontSize = 14.sp
                    )
                }
            )
        }

    }
}

@Composable
private fun LogTab(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (active) Neon else Dim,
        fontSize = 11.sp, letterSpacing = 3.sp,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier.clickable { onClick() }
    )
}

@Composable
private fun ModuleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) Color.White else Dim,
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Dim, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(checkedTrackColor = Neon, checkedThumbColor = Color.Black)
        )
    }
}


private fun isBatteryExempt(context: android.content.Context): Boolean {
    val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

@android.annotation.SuppressLint("BatteryLife")
private fun requestBatteryExemption(context: android.content.Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:" + context.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
        context.startActivity(
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}


/**
 * The in-memory GoDarkState resets to EXPOSED when the OS kills our process,
 * even if the foreground service later restarts. On every app resume we
 * reconcile the UI with reality: if a VPN transport is actually active AND
 * the user's persisted desired mode is GUARD/DARK, trust that over the
 * stale default. Never show the user a state that isn't true.
 */
private fun reconcileMode(context: android.content.Context) {
    val vpnActive = isVpnActive(context)
    val desired = Prefs.desiredMode(context)
    val shown = GoDarkState.mode.value

    if (vpnActive && shown == Mode.EXPOSED && desired != Mode.EXPOSED) {
        // Service is alive but UI forgot. Restore the truth.
        GoDarkState.mode.value = desired
    } else if (!vpnActive && desired != Mode.EXPOSED) {
        // Self-heal: the user wants protection but the tunnel is dead
        // (OEM kill, e.g. Vivo swipe force-stop). If VPN consent is still
        // valid, silently restart the service instead of showing a dead toggle.
        if (VpnService.prepare(context) == null) {
            GoDarkVpnService.start(context, desired)
        } else if (shown != Mode.EXPOSED) {
            GoDarkState.mode.value = Mode.EXPOSED
        }
    } else if (!vpnActive && shown != Mode.EXPOSED) {
        // User genuinely turned protection off elsewhere. Show the truth.
        GoDarkState.mode.value = Mode.EXPOSED
    }
}

private fun isVpnActive(context: android.content.Context): Boolean {
    // Defensive: any system-call failure (denied permission, odd ROM) must
    // degrade to "unknown / not active" rather than crash the launch screen.
    return try {
        val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
                as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    } catch (_: Exception) {
        false
    }
}