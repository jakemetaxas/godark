package com.godark.app.dns

import android.content.Context
import com.godark.app.state.GoDarkState
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Blocklist manager.
 *
 * Default: HaGeZi Multi Normal (plain-domains format) — well maintained,
 * low false-positive rate, trusted in the FOSS community.
 *
 * The list is downloaded once, cached in app-private storage, and loaded
 * into an in-memory HashSet. Matching checks the exact domain and every
 * parent domain (ads.tracker.com matches a "tracker.com" entry).
 *
 * Everything stays local: the only network call this class ever makes is
 * fetching the public list the user selected.
 */
object Blocklist {

    private const val DEFAULT_URL =
        "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/domains/multi.txt"
    private const val CACHE_FILE = "blocklist.txt"

    @Volatile private var domains: HashSet<String> = HashSet()
    @Volatile var ready = false
        private set

    val size: Int get() = domains.size

    /** Loads from cache if present, otherwise downloads. Non-blocking. */
    fun ensureLoaded(context: Context) {
        if (ready) return
        thread(name = "godark-blocklist", isDaemon = true) {
            val cache = File(context.filesDir, CACHE_FILE)
            try {
                if (!cache.exists() || cache.length() == 0L) {
                    GoDarkState.blocklistStatus.value = "Downloading blocklist…"
                    download(cache)
                }
                GoDarkState.blocklistStatus.value = "Loading blocklist…"
                load(cache)
                GoDarkState.blocklistStatus.value =
                    "%,d domains loaded".format(domains.size)
                ready = true
            } catch (e: Exception) {
                GoDarkState.blocklistStatus.value =
                    "Blocklist unavailable (${e.javaClass.simpleName}). Filtering disabled, DNS still encrypted"
                ready = false
            }
        }
    }

    /** Force re-download (settings: "update blocklist"). */
    fun refresh(context: Context) {
        ready = false
        File(context.filesDir, CACHE_FILE).delete()
        ensureLoaded(context)
    }

    fun isBlocked(domain: String): Boolean {
        if (!ready) return false
        var d = domain.lowercase().trimEnd('.')
        while (true) {
            if (d in domains) return true
            val dot = d.indexOf('.')
            if (dot < 0) return false
            d = d.substring(dot + 1)
        }
    }

    private fun download(target: File) {
        val conn = URL(DEFAULT_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 30_000
        conn.inputStream.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        conn.disconnect()
    }

    private fun load(file: File) {
        val set = HashSet<String>(400_000)
        file.bufferedReader().useLines { lines ->
            for (raw in lines) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                // plain-domains format; tolerate hosts format ("0.0.0.0 domain") too
                val token = line.substringAfterLast(' ').lowercase()
                if (token.isNotEmpty()) set.add(token)
            }
        }
        domains = set
    }
}