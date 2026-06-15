package com.godark.app.dns

import android.content.Context
import com.godark.app.state.GoDarkState
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The GUARD-mode DNS engine.
 *
 * For every query:
 *   blocked domain  -> answer NXDOMAIN locally (the tracker simply "doesn't exist")
 *   clean domain    -> forward upstream over DNS-over-HTTPS (encrypted),
 *                      falling back to plain UDP only if DoH fails
 *
 * All decisions are logged locally. Nothing else ever leaves the device.
 */
object DnsEngine {

    /** Upstream resolvers the user can cycle through. */
    enum class Upstream(val label: String, val dohUrl: String, val udpIp: String) {
        QUAD9("Quad9", "https://dns.quad9.net/dns-query", "9.9.9.9"),
        ADGUARD("AdGuard", "https://dns.adguard-dns.com/dns-query", "94.140.14.14"),
        CLOUDFLARE("Cloudflare", "https://cloudflare-dns.com/dns-query", "1.1.1.1");
    }

    private const val PREFS = "godark"
    private const val KEY_UPSTREAM = "upstream"
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun upstream(context: Context): Upstream {
        val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_UPSTREAM, Upstream.QUAD9.name)
        return runCatching { Upstream.valueOf(name!!) }.getOrDefault(Upstream.QUAD9)
    }

    fun cycleUpstream(context: Context): Upstream {
        val all = Upstream.entries
        val next = all[(upstream(context).ordinal + 1) % all.size]
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_UPSTREAM, next.name).apply()
        return next
    }

    /** Handles one raw DNS query payload; always returns a response payload. */
    fun handle(context: Context, query: ByteArray): ByteArray {
        val domain = extractQName(query) ?: return servfail(query)
        GoDarkState.dnsTotal.value++

        if (Blocklist.isBlocked(domain)) {
            GoDarkState.dnsBlocked.value++
            GoDarkState.logDns("${timeFmt.format(Date())}  ✕ $domain")
            return nxdomain(query)
        }

        GoDarkState.logDns("${timeFmt.format(Date())}  ✓ $domain")

        val up = upstream(context)
        return try {
            forwardDoH(up.dohUrl, query)
        } catch (_: Exception) {
            try {
                forwardUdp(up.udpIp, query)
            } catch (_: Exception) {
                servfail(query)
            }
        }
    }

    // ---- wire format helpers -------------------------------------------

    /** Reads the first question name from a DNS message. */
    private fun extractQName(msg: ByteArray): String? {
        if (msg.size < 17) return null
        val sb = StringBuilder()
        var i = 12
        while (i < msg.size) {
            val len = msg[i].toInt() and 0xFF
            if (len == 0) break
            if (len >= 0xC0) return null // compression in a question: malformed
            if (i + 1 + len > msg.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            for (j in 1..len) sb.append((msg[i + j].toInt() and 0xFF).toChar())
            i += len + 1
        }
        return if (sb.isEmpty()) null else sb.toString()
    }

    /** Echo the query back as an authoritative NXDOMAIN. */
    private fun nxdomain(query: ByteArray): ByteArray {
        val r = query.copyOf()
        r[2] = 0x81.toByte() // QR=1, RD preserved-ish
        r[3] = 0x83.toByte() // RA=1, RCODE=3 (NXDOMAIN)
        // zero answer/authority/additional counts
        r[6] = 0; r[7] = 0; r[8] = 0; r[9] = 0; r[10] = 0; r[11] = 0
        return r
    }

    private fun servfail(query: ByteArray): ByteArray {
        val r = query.copyOf()
        if (r.size >= 4) { r[2] = 0x81.toByte(); r[3] = 0x82.toByte() }
        return r
    }

    // ---- upstream transports -------------------------------------------

    private fun forwardDoH(dohUrl: String, query: ByteArray): ByteArray {
        val conn = URL(dohUrl).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/dns-message")
        conn.setRequestProperty("Accept", "application/dns-message")
        conn.doOutput = true
        conn.connectTimeout = 3_000
        conn.readTimeout = 5_000
        conn.outputStream.use { it.write(query) }
        if (conn.responseCode != 200) {
            conn.disconnect()
            throw IllegalStateException("DoH HTTP ${conn.responseCode}")
        }
        val resp = conn.inputStream.use { it.readBytes() }
        conn.disconnect()
        if (resp.isEmpty()) throw IllegalStateException("empty DoH response")
        return resp
    }

    private fun forwardUdp(ip: String, query: ByteArray): ByteArray {
        DatagramSocket().use { socket ->
            socket.soTimeout = 4_000
            socket.send(DatagramPacket(query, query.size, InetAddress.getByName(ip), 53))
            val buf = ByteArray(4096)
            val pkt = DatagramPacket(buf, buf.size)
            socket.receive(pkt)
            return buf.copyOf(pkt.length)
        }
    }
}
