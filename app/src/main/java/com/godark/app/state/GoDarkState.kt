package com.godark.app.state

import kotlinx.coroutines.flow.MutableStateFlow

enum class Mode { EXPOSED, GUARD, DARK }

/**
 * Single source of truth for GoDark's runtime state.
 * Services push into these flows; the UI and the QS tile observe them.
 */
object GoDarkState {
    /** Current posture of the phone. */
    val mode = MutableStateFlow(Mode.EXPOSED)

    /** True while the Sentinel watchdog service is running. */
    val sentinelOn = MutableStateFlow(false)

    /** Rolling log of sentinel events, newest first. */
    val sentinelLog = MutableStateFlow<List<String>>(emptyList())

    /** Rolling log of DNS decisions, newest first. */
    val dnsLog = MutableStateFlow<List<String>>(emptyList())

    /** Counters for the current GUARD session. */
    val dnsBlocked = MutableStateFlow(0)
    val dnsTotal = MutableStateFlow(0)

    /** Blocklist status line shown in the UI ("142,381 domains loaded" etc). */
    val blocklistStatus = MutableStateFlow("Blocklist not loaded")

    fun logSentinel(event: String) {
        sentinelLog.value = (listOf(event) + sentinelLog.value).take(50)
    }

    fun logDns(event: String) {
        dnsLog.value = (listOf(event) + dnsLog.value).take(100)
    }
}
