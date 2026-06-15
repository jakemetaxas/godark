package com.godark.app.sentinel

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.app.Service
import com.godark.app.MainActivity
import com.godark.app.R
import com.godark.app.state.GoDarkState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sentinel: the mic/cam watchdog.
 *
 * We cannot hardware-kill sensors without root, but we CAN see every
 * activation the instant it happens:
 *  - CameraManager.AvailabilityCallback fires when any app opens a camera.
 *  - AudioManager.AudioRecordingCallback fires when any recording starts.
 *
 * Escalation rule: sensor activity while the screen is OFF is treated as
 * hostile until proven otherwise -> high-importance alert.
 */
class SentinelService : Service() {

    companion object {
        private const val CHANNEL_QUIET = "godark_sentinel_quiet"
        private const val CHANNEL_EVENT = "godark_sentinel_event"
        private const val CHANNEL_ALARM = "godark_sentinel_alarm"
        private const val NOTIF_ID = 2
        private var alertCounter = 100

        fun start(context: Context) {
            context.startForegroundService(Intent(context, SentinelService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SentinelService::class.java))
        }
    }

    private lateinit var cameraManager: CameraManager
    private lateinit var audioManager: AudioManager
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var screenOff = false
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            screenOff = intent?.action == Intent.ACTION_SCREEN_OFF
        }
    }

    private val cameraCallback = object : CameraManager.AvailabilityCallback() {
        override fun onCameraUnavailable(cameraId: String) {
            onSensorEvent("Camera $cameraId opened")
        }
        override fun onCameraAvailable(cameraId: String) {
            log("Camera $cameraId released")
        }
    }

    private val audioCallback = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>?) {
            if (!configs.isNullOrEmpty()) {
                onSensorEvent("Microphone active (${configs.size} session${if (configs.size > 1) "s" else ""})")
            } else {
                log("Microphone released")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        createChannels()
        startForeground(NOTIF_ID, watchNotification())

        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        })
        cameraManager.registerAvailabilityCallback(cameraCallback, handler)
        audioManager.registerAudioRecordingCallback(audioCallback, handler)

        GoDarkState.sentinelOn.value = true
        log("Sentinel armed")
    }

    private fun onSensorEvent(what: String) {
        val hostile = screenOff
        val stamp = timeFmt.format(Date())
        val entry = if (hostile) "$stamp  ⚠ $what (SCREEN WAS OFF)" else "$stamp  $what"
        log(entry)

        val channel = if (hostile) CHANNEL_ALARM else CHANNEL_EVENT
        val title = if (hostile) "⚠ GoDark: $what while screen OFF" else "GoDark: $what"

        val tap = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_godark)
            .setContentTitle(title)
            .setContentText("Tap to open the Sentinel log.")
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(alertCounter++, n)
    }

    private fun log(entry: String) = GoDarkState.logSentinel(entry)

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_QUIET, getString(R.string.notif_channel_sentinel),
                // LOW: quiet but not invisible; MIN invites OEM killing on Vivo.
                NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_EVENT, "Sentinel: camera/mic in use",
                // DEFAULT: pops up when an app opens the camera or mic, no alarm sound.
                NotificationManager.IMPORTANCE_DEFAULT)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALARM, "Sentinel: screen-off alerts",
                NotificationManager.IMPORTANCE_HIGH)
        )
    }

    private fun watchNotification(): Notification {
        val tap = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_QUIET)
            .setSmallIcon(R.drawable.ic_godark)
            .setContentTitle("👁 Sentinel — watching camera & mic")
            .setContentText("This is the sensor watchdog, not network protection.")
            .setContentIntent(tap)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }

    override fun onDestroy() {
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        try { cameraManager.unregisterAvailabilityCallback(cameraCallback) } catch (_: Exception) {}
        try { audioManager.unregisterAudioRecordingCallback(audioCallback) } catch (_: Exception) {}
        GoDarkState.sentinelOn.value = false
        log("Sentinel disarmed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}