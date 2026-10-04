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
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Reads DNS packets from the tun device, blocks listed domains, and forwards
 * everything else to the upstream resolver.
 *
 * Threading: one reader thread + a small worker pool. Writes back to the tun are
 * serialised. Upstream sockets are `protect()`ed so they do not loop through
 * our own tunnel.
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
            val response = DnsMessage.buildBlockedResponse(parsed.payload) ?: return
            write(IpPacket.buildReply(response, parsed, ipId.incrementAndGet()))
            onBlocked(name)
            return
        }
        val upstream = forward(parsed.payload) ?: return
        write(IpPacket.buildReply(upstream, parsed, ipId.incrementAndGet()))
    }

    /** Send the raw DNS query upstream and return the raw response, or null. */
    private fun forward(query: ByteArray): ByteArray? {
        val servers = upstreamProvider().ifEmpty { listOf("1.1.1.1", "8.8.8.8") }
        for (server in servers) {
            try {
                DatagramSocket().use { socket ->
                    protectSocket(socket)
                    socket.soTimeout = 4_000
                    val addr = InetAddress.getByName(server)
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
