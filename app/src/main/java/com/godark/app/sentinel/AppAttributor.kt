package com.godark.app.sentinel

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process

/**
 * Best-effort attribution of "which app is responsible" for a sensor event.
 *
 * HONESTY CONTRACT:
 *  - This is an INFERENCE from the current foreground app, not proof.
 *  - It is right most of the time (the app using the camera is usually the
 *    one on screen) but WRONG in exactly the suspicious cases: an app using
 *    the mic from the background, or any use while the screen is off.
 *  - Therefore callers must label results as "likely X", and must treat a
 *    null result as "unknown (background or screen off)" rather than guessing.
 *  - Requires the user to have granted Usage Access. Without it, returns null
 *    and Sentinel stays in anonymous mode. Attribution is strictly opt-in.
 *
 * Everything here is read locally and never leaves the device.
 */
object AppAttributor {

    /** True if the user has granted the Usage Access permission. */
    fun hasUsageAccess(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Returns a human-readable label for the foreground app, or null if it
     * can't be determined (no permission, screen off, nothing in foreground).
     * Callers should prefix with "likely" and fall back to "unknown" on null.
     */
    fun foregroundAppLabel(context: Context): String? {
        if (!hasUsageAccess(context)) return null
        val pkg = foregroundPackage(context) ?: return null
        // Don't bother attributing GoDark's own UI to itself.
        if (pkg == context.packageName) return null
        return prettyName(context, pkg)
    }

    private fun foregroundPackage(context: Context): String? {
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            // Look back a few seconds; the sensor event is essentially "now".
            val stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - 10_000,
                now
            ) ?: return null
            stats.maxByOrNull { it.lastTimeUsed }?.packageName
        } catch (_: Exception) {
            null
        }
    }

    private fun prettyName(context: Context, pkg: String): String {
        return try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(info).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            pkg
        } catch (_: Exception) {
            pkg
        }
    }
}