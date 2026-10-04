package com.kavach.dns

import java.io.ByteArrayOutputStream

/** DNS query parsing and sinkhole-response construction. */
object DnsMessage {

    private const val HEADER_LEN = 12

    /** Extract the queried domain name (lowercase) from a DNS query payload. */
    fun queryName(buf: ByteArray): String? {
        if (buf.size < HEADER_LEN + 5) return null
        val sb = StringBuilder()
        var pos = HEADER_LEN
        var guard = 0
        while (pos < buf.size && guard++ < 128) {
            val len = buf[pos].toInt() and 0xFF
            if (len == 0) break
            if (len and 0xC0 == 0xC0) break // compression pointer: not expected in a question
            pos++
            if (pos + len > buf.size) return null
            sb.append(String(buf, pos, len, Charsets.US_ASCII))
            sb.append('.')
            pos += len
        }
        return sb.toString().trimEnd('.').lowercase().ifEmpty { null }
    }

    /** Byte offset just past the question section (qname + qtype + qclass). */
    private fun questionEnd(buf: ByteArray): Int? {
        var pos = HEADER_LEN
        var guard = 0
        while (pos < buf.size && guard++ < 128) {
            val len = buf[pos].toInt() and 0xFF
            if (len == 0) { pos += 1; break }
            if (len and 0xC0 == 0xC0) { pos += 2; break }
            pos += 1 + len
        }
        val end = pos + 4 // qtype(2) + qclass(2)
        return if (end <= buf.size) end else null
    }

    /**
     * Build a sinkhole response for a blocked name.
     * A queries get 0.0.0.0; AAAA and others get an empty (NODATA) answer so the
     * client stops trying without a broken record.
     */
    fun buildBlockedResponse(query: ByteArray): ByteArray? {
        val qEnd = questionEnd(query) ?: return null
        val qType = ((query[qEnd - 4].toInt() and 0xFF) shl 8) or (query[qEnd - 3].toInt() and 0xFF)
        val out = ByteArrayOutputStream()
        // Header: same ID, QR=1 RD=1 RA=1, RCODE=0
        out.write(query[0].toInt()); out.write(query[1].toInt())
        out.write(0x81); out.write(0x80)
        out.write(0x00); out.write(0x01)          // QDCOUNT = 1
        val answers = if (qType == 1) 1 else 0
        out.write(0x00); out.write(answers)       // ANCOUNT
        out.write(0x00); out.write(0x00)          // NSCOUNT
        out.write(0x00); out.write(0x00)          // ARCOUNT
        out.write(query, HEADER_LEN, qEnd - HEADER_LEN) // question section
        if (answers == 1) {
            out.write(0xC0); out.write(0x0C)      // name pointer to offset 12
            out.write(0x00); out.write(0x01)      // TYPE A
            out.write(0x00); out.write(0x01)      // CLASS IN
            out.write(0x00); out.write(0x00); out.write(0x00); out.write(0x3C) // TTL 60s
            out.write(0x00); out.write(0x04)      // RDLENGTH
            out.write(0x00); out.write(0x00); out.write(0x00); out.write(0x00) // 0.0.0.0
        }
        return out.toByteArray()
    }
}
