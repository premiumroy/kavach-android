package com.kavach.service

import android.os.ParcelFileDescriptor
import com.kavach.data.BlocklistRepository
import com.kavach.dns.DnsMessage
import com.kavach.dns.IpPacket
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet6Address
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Reads DNS packets from the tun device, answers listed domains with a sinkhole,
 * and forwards everything else to a real upstream resolver.
 *
 * Every query gets a reply: blocked -> 0.0.0.0, allowed -> upstream answer,
 * upstream failure -> SERVFAIL. The client is never left hanging.
 */
class DnsProxy(
    tunFd: ParcelFileDescriptor,
    private val repo: BlocklistRepository,
    private val upstreamProvider: () -> List<String>,
    private val onBlocked: (String) -> Unit,
    private val protectSocket: (DatagramSocket) -> Boolean,
) {
    private val input = FileInputStream(tunFd.fileDescriptor)
    private val output = FileOutputStream(tunFd.fileDescriptor)
    private val workers = Executors.newFixedThreadPool(8)
    private val writeLock = Any()
    private val ipId = AtomicInteger(0)

    @Volatile private var running = true

    private val ANY_V4 = InetAddress.getByName("0.0.0.0")
    private val ANY_V6 = InetAddress.getByName("::")

    fun start() {
        Thread({ loop() }, "kavach-dns-reader").start()
    }

    private fun loop() {
        val buf = ByteArray(32767)
        while (running) {
            val n = try {
                input.read(buf)
            } catch (e: IOException) {
                break
            }
            if (n <= 0) continue
            val packet = buf.copyOf(n)
            workers.execute { handle(packet) }
        }
    }

    private fun handle(packet: ByteArray) {
        val parsed = IpPacket.parse(packet, packet.size) ?: return
        val name = DnsMessage.queryName(parsed.payload)

        if (name != null && repo.isBlocked(name)) {
            val response = DnsMessage.buildBlockedResponse(parsed.payload)
            if (response != null) {
                write(IpPacket.buildReply(response, parsed, ipId.incrementAndGet()))
                DnsStats.blocked.value += 1
                onBlocked(name)
            }
            return
        }

        val upstream = forward(parsed.payload)
        if (upstream != null) {
            write(IpPacket.buildReply(upstream, parsed, ipId.incrementAndGet()))
            DnsStats.forwarded.value += 1
        } else {
            // Never leave the client hanging: answer SERVFAIL so it retries fast.
            val servfail = DnsMessage.buildServfail(parsed.payload)
            if (servfail != null) {
                write(IpPacket.buildReply(servfail, parsed, ipId.incrementAndGet()))
            }
            DnsStats.failed.value += 1
        }
    }

    /**
     * Send the raw DNS query upstream and return the raw response, or null.
     *
     * The socket is bound to the same address family as the target: a plain
     * DatagramSocket is IPv4-only, so on an IPv6-primary mobile network the
     * carrier's IPv6 resolver would be unreachable and every lookup would fail.
     */
    private fun forward(query: ByteArray): ByteArray? {
        for (server in upstreamProvider()) {
            try {
                val addr = InetAddress.getByName(server)
                val bind = if (addr is Inet6Address) ANY_V6 else ANY_V4
                DatagramSocket(0, bind).use { socket ->
                    protectSocket(socket)          // do not loop back through our tunnel
                    socket.soTimeout = 3_000
                    socket.send(DatagramPacket(query, query.size, addr, 53))
                    val respBuf = ByteArray(4096)
                    val respPacket = DatagramPacket(respBuf, respBuf.size)
                    socket.receive(respPacket)
                    return respBuf.copyOf(respPacket.length)
                }
            } catch (e: Exception) {
                // try the next server
            }
        }
        return null
    }

    private fun write(bytes: ByteArray) {
        synchronized(writeLock) {
            try {
                output.write(bytes)
            } catch (e: IOException) {
                // tun closed; the loop will exit
            }
        }
    }

    fun stop() {
        running = false
        workers.shutdownNow()
        runCatching { input.close() }
        runCatching { output.close() }
    }
}
