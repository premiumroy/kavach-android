package com.kavach.dns

/**
 * Minimal IPv4/IPv6 + UDP parsing and construction.
 *
 * Kavach is a DNS-only VPN: the tun captures just DNS traffic, so we only need
 * to understand IP+UDP and rebuild the reply packet. TCP and everything else is
 * never routed into the tunnel.
 */
object IpPacket {

    data class Parsed(
        val srcIp: ByteArray,
        val dstIp: ByteArray,
        val srcPort: Int,
        val dstPort: Int,
        val isIpv6: Boolean,
        val payload: ByteArray,
    )

    /** Parse an IP packet read from the tun device. Returns null if not UDP. */
    fun parse(buf: ByteArray, len: Int): Parsed? {
        if (len < 20) return null
        val version = (buf[0].toInt() ushr 4) and 0x0F
        return when (version) {
            4 -> parseV4(buf, len)
            6 -> parseV6(buf, len)
            else -> null
        }
    }

    private fun parseV4(buf: ByteArray, len: Int): Parsed? {
        val ihl = (buf[0].toInt() and 0x0F) * 4
        if (ihl < 20 || len < ihl + 8) return null
        val protocol = buf[9].toInt() and 0xFF
        if (protocol != 17) return null // UDP only
        val srcIp = buf.copyOfRange(12, 16)
        val dstIp = buf.copyOfRange(16, 20)
        val srcPort = ((buf[ihl].toInt() and 0xFF) shl 8) or (buf[ihl + 1].toInt() and 0xFF)
        val dstPort = ((buf[ihl + 2].toInt() and 0xFF) shl 8) or (buf[ihl + 3].toInt() and 0xFF)
        val udpLen = ((buf[ihl + 4].toInt() and 0xFF) shl 8) or (buf[ihl + 5].toInt() and 0xFF)
        val payloadStart = ihl + 8
        val payloadLen = (udpLen - 8).coerceAtLeast(0)
        if (payloadStart + payloadLen > len) return null
        return Parsed(srcIp, dstIp, srcPort, dstPort, false, buf.copyOfRange(payloadStart, payloadStart + payloadLen))
    }

    private fun parseV6(buf: ByteArray, len: Int): Parsed? {
        if (len < 48) return null
        val nextHeader = buf[6].toInt() and 0xFF
        if (nextHeader != 17) return null // UDP only, no extension headers
        val srcIp = buf.copyOfRange(8, 24)
        val dstIp = buf.copyOfRange(24, 40)
        val srcPort = ((buf[40].toInt() and 0xFF) shl 8) or (buf[41].toInt() and 0xFF)
        val dstPort = ((buf[42].toInt() and 0xFF) shl 8) or (buf[43].toInt() and 0xFF)
        val udpLen = ((buf[44].toInt() and 0xFF) shl 8) or (buf[45].toInt() and 0xFF)
        val payloadStart = 48
        val payloadLen = (udpLen - 8).coerceAtLeast(0)
        if (payloadStart + payloadLen > len) return null
        return Parsed(srcIp, dstIp, srcPort, dstPort, true, buf.copyOfRange(payloadStart, payloadStart + payloadLen))
    }

    /**
     * Build the reply packet: swap source/destination of [orig] and carry [payload]
     * (the DNS response) back to the app that asked.
     */
    fun buildReply(payload: ByteArray, orig: Parsed, ipId: Int): ByteArray =
        if (orig.isIpv6) buildV6Udp(payload, orig.dstIp, orig.dstPort, orig.srcIp, orig.srcPort)
        else buildV4Udp(payload, orig.dstIp, orig.dstPort, orig.srcIp, orig.srcPort, ipId)

    private fun buildV4Udp(
        payload: ByteArray, srcIp: ByteArray, srcPort: Int,
        dstIp: ByteArray, dstPort: Int, ipId: Int,
    ): ByteArray {
        val total = 20 + 8 + payload.size
        val p = ByteArray(total)
        p[0] = 0x45                       // IPv4, IHL=5
        p[1] = 0
        p[2] = ((total ushr 8) and 0xFF).toByte()
        p[3] = (total and 0xFF).toByte()
        p[4] = ((ipId ushr 8) and 0xFF).toByte()
        p[5] = (ipId and 0xFF).toByte()
        p[6] = 0x40                       // Don't fragment
        p[7] = 0
        p[8] = 64                         // TTL
        p[9] = 17                         // UDP
        p[10] = 0; p[11] = 0              // checksum placeholder
        System.arraycopy(srcIp, 0, p, 12, 4)
        System.arraycopy(dstIp, 0, p, 16, 4)
        val checksum = ipChecksum(p, 0, 20)
        p[10] = ((checksum ushr 8) and 0xFF).toByte()
        p[11] = (checksum and 0xFF).toByte()
        writeUdp(p, 20, srcPort, dstPort, payload)
        return p
    }

    private fun buildV6Udp(
        payload: ByteArray, srcIp: ByteArray, srcPort: Int,
        dstIp: ByteArray, dstPort: Int,
    ): ByteArray {
        val total = 40 + 8 + payload.size
        val p = ByteArray(total)
        p[0] = 0x60                       // IPv6
        p[4] = (((8 + payload.size) ushr 8) and 0xFF).toByte()
        p[5] = ((8 + payload.size) and 0xFF).toByte()
        p[6] = 17                         // next header: UDP
        p[7] = 64                         // hop limit
        System.arraycopy(srcIp, 0, p, 8, 16)
        System.arraycopy(dstIp, 0, p, 24, 16)
        writeUdp(p, 40, srcPort, dstPort, payload)
        return p
    }

    private fun writeUdp(p: ByteArray, offset: Int, srcPort: Int, dstPort: Int, payload: ByteArray) {
        p[offset] = ((srcPort ushr 8) and 0xFF).toByte()
        p[offset + 1] = (srcPort and 0xFF).toByte()
        p[offset + 2] = ((dstPort ushr 8) and 0xFF).toByte()
        p[offset + 3] = (dstPort and 0xFF).toByte()
        val udpLen = 8 + payload.size
        p[offset + 4] = ((udpLen ushr 8) and 0xFF).toByte()
        p[offset + 5] = (udpLen and 0xFF).toByte()
        p[offset + 6] = 0                 // checksum 0 = "not computed" (valid for IPv4 UDP)
        p[offset + 7] = 0
        System.arraycopy(payload, 0, p, offset + 8, payload.size)
    }

    private fun ipChecksum(buf: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        val end = offset + length
        while (i < end) {
            sum += (((buf[i].toInt() and 0xFF) shl 8) or (buf[i + 1].toInt() and 0xFF)).toLong()
            i += 2
        }
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return (sum.inv() and 0xFFFF).toInt()
    }
}
