package com.godark.app.dns

/**
 * Minimal IPv4/UDP packet plumbing for the GUARD-mode DNS interceptor.
 *
 * In GUARD mode the only route captured by the tun interface is the virtual
 * DNS server (10.111.0.2/32), so every packet we see here is a DNS query.
 * We parse just enough to extract the UDP payload and to craft a valid
 * response packet going the other way.
 *
 * Note: UDP checksum is legitimately optional over IPv4 (set to 0), which
 * keeps this code small and auditable.
 */
object Packets {

    data class UdpPacket(
        val srcIp: ByteArray,   // 4 bytes
        val dstIp: ByteArray,   // 4 bytes
        val srcPort: Int,
        val dstPort: Int,
        val payload: ByteArray
    )

    /** Returns null for anything that isn't IPv4 UDP. */
    fun parse(buf: ByteArray, len: Int): UdpPacket? {
        if (len < 28) return null
        val version = (buf[0].toInt() shr 4) and 0xF
        if (version != 4) return null
        val ihl = (buf[0].toInt() and 0xF) * 4
        if (buf[9].toInt() != 17) return null // not UDP
        if (len < ihl + 8) return null

        val srcIp = buf.copyOfRange(12, 16)
        val dstIp = buf.copyOfRange(16, 20)
        val srcPort = ((buf[ihl].toInt() and 0xFF) shl 8) or (buf[ihl + 1].toInt() and 0xFF)
        val dstPort = ((buf[ihl + 2].toInt() and 0xFF) shl 8) or (buf[ihl + 3].toInt() and 0xFF)
        val udpLen = ((buf[ihl + 4].toInt() and 0xFF) shl 8) or (buf[ihl + 5].toInt() and 0xFF)
        val payloadLen = (udpLen - 8).coerceAtMost(len - ihl - 8)
        if (payloadLen <= 0) return null
        val payload = buf.copyOfRange(ihl + 8, ihl + 8 + payloadLen)
        return UdpPacket(srcIp, dstIp, srcPort, dstPort, payload)
    }

    /** Builds a response packet: src/dst swapped relative to the request. */
    fun buildResponse(request: UdpPacket, responsePayload: ByteArray): ByteArray {
        val ipLen = 20
        val udpLen = 8 + responsePayload.size
        val total = ipLen + udpLen
        val out = ByteArray(total)

        // IPv4 header
        out[0] = 0x45                                  // version 4, IHL 5
        out[1] = 0
        out[2] = ((total shr 8) and 0xFF).toByte()
        out[3] = (total and 0xFF).toByte()
        out[4] = 0; out[5] = 0                         // identification
        out[6] = 0x40; out[7] = 0                      // don't fragment
        out[8] = 64                                    // TTL
        out[9] = 17                                    // UDP
        // checksum at 10..11 filled below
        System.arraycopy(request.dstIp, 0, out, 12, 4) // src = original dst
        System.arraycopy(request.srcIp, 0, out, 16, 4) // dst = original src
        val ipSum = checksum(out, 0, ipLen)
        out[10] = ((ipSum shr 8) and 0xFF).toByte()
        out[11] = (ipSum and 0xFF).toByte()

        // UDP header
        out[20] = ((request.dstPort shr 8) and 0xFF).toByte()
        out[21] = (request.dstPort and 0xFF).toByte()
        out[22] = ((request.srcPort shr 8) and 0xFF).toByte()
        out[23] = (request.srcPort and 0xFF).toByte()
        out[24] = ((udpLen shr 8) and 0xFF).toByte()
        out[25] = (udpLen and 0xFF).toByte()
        out[26] = 0; out[27] = 0                       // UDP checksum optional on IPv4

        System.arraycopy(responsePayload, 0, out, 28, responsePayload.size)
        return out
    }

    private fun checksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        while (i < offset + length - 1) {
            sum += (((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)).toLong()
            i += 2
        }
        if (length % 2 != 0) sum += ((data[offset + length - 1].toInt() and 0xFF) shl 8).toLong()
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv().toInt() and 0xFFFF
    }
}
