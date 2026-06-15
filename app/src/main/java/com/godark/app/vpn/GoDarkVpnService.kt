package com.godark.app.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.godark.app.MainActivity
import com.godark.app.R
import com.godark.app.dns.Blocklist
import com.godark.app.dns.DnsEngine
import com.godark.app.dns.Packets
import com.godark.app.state.GoDarkState
import com.godark.app.state.Mode
import com.godark.app.state.Prefs
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/**
 * One VPN service, two postures:
 *
 * DARK  — route everything into the tun and drop it. Total network silence.
 * GUARD — route ONLY the virtual DNS server into the tun. App traffic flows
 *         normally; every DNS lookup is filtered and forwarded encrypted.
 *
 * Dead-man principle: never let the user believe they're protected when
 * they aren't. If the system kills us, we either recover automatically
 * (START_STICKY restart -> re-engage the persisted desired mode) or we
 * scream with a high-priority EXPOSED alert.
 */
class GoDarkVpnService : VpnService() {

    companion object {
        const val ACTION_DARK = "com.godark.app.vpn.DARK"
        const val ACTION_GUARD = "com.godark.app.vpn.GUARD"
        const val ACTION_STOP = "com.godark.app.vpn.STOP"
        private const val CHANNEL_ID = "godark_vpn"
        private const val CHANNEL_ALERT = "godark_alert"
        private const val NOTIF_ID = 1
        private const val NOTIF_ALERT_ID = 3
        private const val DNS_VIRTUAL = "10.111.0.2"

        fun start(context: Context, mode: Mode) {
            Prefs.setDesiredMode(context, mode)
            val action = if (mode == Mode.DARK) ACTION_DARK else ACTION_GUARD
            context.startForegroundService(
                Intent(context, GoDarkVpnService::class.java).setAction(action)
            )
        }

        fun stop(context: Context) {
            Prefs.setDesiredMode(context, Mode.EXPOSED)
            context.startService(
                Intent(context, GoDarkVpnService::class.java).setAction(ACTION_STOP)
            )
        }
    }

    private var tun: ParcelFileDescriptor? = null
    @Volatile private var running = false
    private val dnsPool = Executors.newFixedThreadPool(8)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { shutdown(); return START_NOT_STICKY }
            ACTION_GUARD -> engage(Mode.GUARD)
            ACTION_DARK -> engage(Mode.DARK)
            else -> {
                // null intent = the system killed us and is restarting us.
                val desired = Prefs.desiredMode(this)
                if (desired != Mode.EXPOSED && prepare(this) == null) {
                    engage(desired)
                    notifyRecovered(desired)
                } else if (desired != Mode.EXPOSED) {
                    // We can't re-establish (consent lost) -> fail LOUDLY.
                    alertExposed("GoDark was stopped and could not recover.")
                    stopSelf()
                    return START_NOT_STICKY
                } else {
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }
        return START_STICKY
    }

    private fun engage(mode: Mode) {
        if (tun != null) {
            running = false
            try { tun?.close() } catch (_: Exception) {}
            tun = null
        }

        createChannels()
        startForeground(NOTIF_ID, buildNotification(mode))

        val builder = Builder()
            .setSession(if (mode == Mode.DARK) "GoDark blackout" else "GoDark guard")
            .addAddress("10.111.0.1", 32)
            .setBlocking(true)
            .setMtu(1500)

        if (mode == Mode.DARK) {
            builder.addRoute("0.0.0.0", 0)
            builder.addRoute("::", 0)
        } else {
            builder.addDnsServer(DNS_VIRTUAL)
            builder.addRoute(DNS_VIRTUAL, 32)
            Blocklist.ensureLoaded(this)
        }

        tun = builder.establish()
        if (tun == null) {
            alertExposed("GoDark could not take the VPN slot. Another VPN may be active.")
            stopSelf()
            return
        }

        running = true
        GoDarkState.mode.value = mode

        when (mode) {
            Mode.DARK -> blackholeLoop()
            else -> dnsLoop()
        }
    }

    /** DARK: read every packet, drop every packet. */
    private fun blackholeLoop() {
        thread(name = "godark-blackhole", isDaemon = true) {
            val fd = tun?.fileDescriptor ?: return@thread
            val input = FileInputStream(fd)
            val buffer = ByteArray(32 * 1024)
            try {
                while (running) {
                    if (input.read(buffer) < 0) break
                }
            } catch (_: Exception) { /* tun closed */ }
        }
    }

    /** GUARD: every packet here is DNS to the virtual resolver. */
    private fun dnsLoop() {
        thread(name = "godark-dns", isDaemon = true) {
            val fd = tun?.fileDescriptor ?: return@thread
            val input = FileInputStream(fd)
            val output = FileOutputStream(fd)
            val writeLock = Any()
            val buffer = ByteArray(32 * 1024)
            try {
                while (running) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    val pkt = Packets.parse(buffer, n) ?: continue
                    dnsPool.execute {
                        try {
                            val responsePayload = DnsEngine.handle(this, pkt.payload)
                            val responsePacket = Packets.buildResponse(pkt, responsePayload)
                            synchronized(writeLock) {
                                if (running) output.write(responsePacket)
                            }
                        } catch (_: Exception) { /* drop on error */ }
                    }
                }
            } catch (_: Exception) { /* tun closed */ }
        }
    }

    private fun shutdown() {
        running = false
        try { tun?.close() } catch (_: Exception) {}
        tun = null
        GoDarkState.mode.value = Mode.EXPOSED
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() {
        // The tunnel was revoked. Two very different scenarios:
        // 1. The system/OEM killed us -> consent persists -> fight back, re-engage.
        // 2. The user gave the VPN slot to another app -> consent gone -> respect it.
        val desired = Prefs.desiredMode(this)
        if (desired != Mode.EXPOSED && prepare(this) == null) {
            // Consent intact: this was a kill, not a user choice. Recover.
            engage(desired)
            notifyRecovered(desired)
        } else {
            // User genuinely moved on, or we can't recover. Fail loudly.
            alertExposed("GoDark's VPN was revoked. You are EXPOSED.")
            Prefs.setDesiredMode(this, Mode.EXPOSED)
            shutdown()
        }
    }

    override fun onDestroy() {
        running = false
        dnsPool.shutdownNow()
        if (GoDarkState.mode.value != Mode.EXPOSED) GoDarkState.mode.value = Mode.EXPOSED
        super.onDestroy()
    }

    // ---- notifications --------------------------------------------------

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel_vpn),
                // LOW: quiet but visible. Tested against Vivo OriginOS swipe-kill:
                // MIN, LOW, and HIGH all die identically; importance is irrelevant
                // to force-stop. LOW chosen for least annoyance.
                NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERT, "Protection alerts",
                NotificationManager.IMPORTANCE_HIGH)
        )
    }

    private fun tapIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
    )

    private fun buildNotification(mode: Mode): Notification {
        val (title, text) = if (mode == Mode.DARK)
            "🛡 GoDark — DARK (all traffic blocked)" to "Network blackout active."
        else
            "🛡 GoDark — GUARD on (network protected)" to "DNS filtered and encrypted. Phone fully usable."
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_godark)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(tapIntent())
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }

    private fun notifyRecovered(mode: Mode) {
        createChannels()
        val n = Notification.Builder(this, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_godark)
            .setContentTitle("GoDark recovered")
            .setContentText("The system killed GoDark; ${mode.name} mode was restored automatically.")
            .setContentIntent(tapIntent())
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIF_ALERT_ID, n)
    }

    private fun alertExposed(reason: String) {
        createChannels()
        val n = Notification.Builder(this, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_godark)
            .setContentTitle("⚠ You are EXPOSED")
            .setContentText(reason)
            .setStyle(Notification.BigTextStyle().bigText("$reason Open GoDark to re-enable protection."))
            .setContentIntent(tapIntent())
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIF_ALERT_ID, n)
    }
}