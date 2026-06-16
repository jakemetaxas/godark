package com.godark.app.dns

import android.content.Context
import com.godark.app.state.GoDarkState
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Local-only privacy statistics.
 *
 * Everything here stays on the device in SharedPreferences. We record:
 *  - all-time totals (queries + blocks)
 *  - per-day totals (so "today" and "this week" can be derived)
 *  - per-domain block counts (the "top offenders" ranking)
 *
 * Design for privacy + cheapness:
 *  - We aggregate in memory and flush to disk periodically, not on every query.
 *  - We keep only the last 8 day-buckets and cap the domain map, so the
 *    stored footprint stays tiny and history doesn't become a forever-record
 *    of the user's browsing.
 *  - clear() wipes everything: a privacy app must be able to forget.
 */
object Stats {

    private const val PREFS = "godark_stats"
    private const val KEY_ALLTIME_TOTAL = "alltime_total"
    private const val KEY_ALLTIME_BLOCKED = "alltime_blocked"
    private const val KEY_DAYS = "days_json"        // {"2026-06-15": {"t":123,"b":45}, ...}
    private const val KEY_DOMAINS = "domains_json"   // {"doubleclick.net": 312, ...}
    private const val MAX_DAYS = 8
    private const val MAX_DOMAINS = 200
    private const val FLUSH_EVERY = 25               // flush after this many recorded events

    private val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    // In-memory accumulators (flushed periodically)
    private var allTotal = 0L
    private var allBlocked = 0L
    private val days = HashMap<String, LongArray>()      // date -> [total, blocked]
    private val domains = HashMap<String, Long>()        // blocked domain -> count
    private var pending = 0
    private var loaded = false

    @Synchronized
    fun ensureLoaded(context: Context) {
        if (loaded) return
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        allTotal = p.getLong(KEY_ALLTIME_TOTAL, 0)
        allBlocked = p.getLong(KEY_ALLTIME_BLOCKED, 0)
        runCatching {
            val o = JSONObject(p.getString(KEY_DAYS, "{}") ?: "{}")
            o.keys().forEach { k ->
                val d = o.getJSONObject(k)
                days[k] = longArrayOf(d.optLong("t"), d.optLong("b"))
            }
        }
        runCatching {
            val o = JSONObject(p.getString(KEY_DOMAINS, "{}") ?: "{}")
            o.keys().forEach { k -> domains[k] = o.getLong(k) }
        }
        loaded = true
        pushToUi()
    }

    @Synchronized
    fun record(context: Context, domain: String, blocked: Boolean) {
        if (!loaded) ensureLoaded(context)
        val today = dayFmt.format(Date())
        allTotal++
        val bucket = days.getOrPut(today) { longArrayOf(0, 0) }
        bucket[0]++
        if (blocked) {
            allBlocked++
            bucket[1]++
            domains[domain] = (domains[domain] ?: 0) + 1
        }
        if (++pending >= FLUSH_EVERY) flush(context)
    }

    @Synchronized
    fun flush(context: Context) {
        if (!loaded) return
        trim()
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val daysJson = JSONObject()
        days.forEach { (k, v) ->
            daysJson.put(k, JSONObject().put("t", v[0]).put("b", v[1]))
        }
        val domainsJson = JSONObject()
        domains.forEach { (k, v) -> domainsJson.put(k, v) }
        p.edit()
            .putLong(KEY_ALLTIME_TOTAL, allTotal)
            .putLong(KEY_ALLTIME_BLOCKED, allBlocked)
            .putString(KEY_DAYS, daysJson.toString())
            .putString(KEY_DOMAINS, domainsJson.toString())
            .apply()
        pending = 0
        pushToUi()
    }

    /** Keep storage tiny: cap day-buckets and domain map. */
    private fun trim() {
        if (days.size > MAX_DAYS) {
            days.keys.sorted().dropLast(MAX_DAYS).forEach { days.remove(it) }
        }
        if (domains.size > MAX_DOMAINS) {
            val keep = domains.entries.sortedByDescending { it.value }.take(MAX_DOMAINS)
            domains.clear()
            keep.forEach { domains[it.key] = it.value }
        }
    }

    @Synchronized
    fun clear(context: Context) {
        allTotal = 0; allBlocked = 0
        days.clear(); domains.clear(); pending = 0
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        pushToUi()
    }

    // ---- derived views for the UI ----

    data class Snapshot(
        val allTotal: Long, val allBlocked: Long,
        val todayTotal: Long, val todayBlocked: Long,
        val weekTotal: Long, val weekBlocked: Long,
        val topDomains: List<Pair<String, Long>>
    )

    @Synchronized
    fun snapshot(): Snapshot {
        val today = dayFmt.format(Date())
        val t = days[today] ?: longArrayOf(0, 0)
        // last 7 days inclusive
        val cal = Calendar.getInstance()
        var wt = 0L; var wb = 0L
        repeat(7) {
            val key = dayFmt.format(cal.time)
            days[key]?.let { wt += it[0]; wb += it[1] }
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        val top = domains.entries.sortedByDescending { it.value }
            .take(10).map { it.key to it.value }
        return Snapshot(allTotal, allBlocked, t[0], t[1], wt, wb, top)
    }

    private fun pushToUi() {
        GoDarkState.statsSnapshot.value = snapshot()
    }
}