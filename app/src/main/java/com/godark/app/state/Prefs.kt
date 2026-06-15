package com.godark.app.state

import android.content.Context

/**
 * Persisted intent: what the USER wants the phone's posture to be.
 * Survives process death so the service can recover after being killed.
 */
object Prefs {
    private const val FILE = "godark"
    private const val KEY_DESIRED = "desired_mode"

    fun desiredMode(c: Context): Mode {
        val raw = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_DESIRED, Mode.EXPOSED.name)
        return runCatching { Mode.valueOf(raw!!) }.getOrDefault(Mode.EXPOSED)
    }

    fun setDesiredMode(c: Context, m: Mode) {
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString(KEY_DESIRED, m.name).apply()
    }
}