package com.kavach.service

import android.app.Service
import android.content.Intent
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import com.kavach.KavachApp
import com.kavach.data.KavachSettings
import com.kavach.data.LogEntity
import com.kavach.util.Notification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * The DNS-filtering VPN service.
 *
 * It is a *local* VPN: the tun only carries DNS traffic to our fake resolver.
 * No traffic is sent to any remote server, and no user data leaves the device.
 */
class KavachVpnService : VpnService() {

    private var tun: ParcelFileDescriptor? = null
    private var proxy: DnsProxy? = null
    private var blockedToday = 0
    private var sinceNotification = 0

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                return START_NOT_STICKY
            }
            else -> startVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        if (proxy != null) return
        val app = KavachApp.from(this)
        val settings = runBlocking { app.settings.flow.first() }

        val builder = Builder()
            .setSession("Kavach")
            .setMtu(1500)
            .addAddress(IPV4_ADDRESS, 32)
            .addDnsServer(IPV4_DNS)
            .addRoute(IPV4_DNS, 32)
            .setBlocking(true)

        if (!settings.blockIpv6) {
            runCatching {
                builder.addAddress(IPV6_ADDRESS, 128)
                builder.addDnsServer(IPV6_DNS)
                builder.addRoute(IPV6_DNS, 128)
            }
        }

        // Per-app: apps the user chose to bypass are allowed outside the tunnel.
        for (pkg in settings.bypassedApps) {
            runCatching { builder.addDisallowedApplication(pkg) }
        }

        val fd = try {
            builder.establish()
        } catch (e: Exception) {
            null
        }
        if (fd == null) {
            stopSelf()
            return
        }
        tun = fd

        Notification.ensureChannel(this)
        startForeground(Notification.NOTIFICATION_ID, Notification.build(this, blockedToday))

        val upstreamProvider: () -> List<String> = {
            if (settings.upstreamDns.isNotBlank()) listOf(settings.upstreamDns.trim())
            else systemDnsServers()
        }

        val p = DnsProxy(
            tunFd = fd,
            repo = app.repository,
            upstreamProvider = upstreamProvider,
            onBlocked = { domain -> recordBlock(domain, settings) },
            protectSocket = { socket -> protect(socket) },
        )
        proxy = p
        p.start()

        _running.value = true
        _blocked.value = 0
    }

    private fun recordBlock(domain: String, settings: KavachSettings) {
        blockedToday++
        _blocked.value = blockedToday
        if (settings.logBlocked) {
            KavachApp.from(this).appScope.launch {
                runCatching {
                    KavachApp.from(this@KavachVpnService).database.logs()
                        .insert(LogEntity(domain = domain, blocked = true))
                }
            }
        }
        if (++sinceNotification >= 5) {
            sinceNotification = 0
            val mgr = getSystemService(android.app.NotificationManager::class.java)
            mgr.notify(Notification.NOTIFICATION_ID, Notification.build(this, blockedToday))
        }
    }

    private fun stopVpn() {
        proxy?.stop()
        proxy = null
        runCatching { tun?.close() }
        tun = null
        _running.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        proxy?.stop()
        proxy = null
        runCatching { tun?.close() }
        tun = null
        _running.value = false
        super.onDestroy()
    }

    /** DNS servers of the active network, used as the upstream resolver. */
    private fun systemDnsServers(): List<String> {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return emptyList()
        val network = cm.activeNetwork ?: return emptyList()
        val lp = cm.getLinkProperties(network) ?: return emptyList()
        return lp.dnsServers.mapNotNull { it.hostAddress }.filter { it.isNotBlank() }
    }

    companion object {
        const val ACTION_START = "com.kavach.action.START"
        const val ACTION_STOP = "com.kavach.action.STOP"

        private const val IPV4_ADDRESS = "10.111.222.1"
        private const val IPV4_DNS = "10.111.222.2"
        private const val IPV6_ADDRESS = "fd00:1:2:3::1"
        private const val IPV6_DNS = "fd00:1:2:3::2"

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running

        private val _blocked = MutableStateFlow(0)
        val blocked: StateFlow<Int> = _blocked

        fun markRunning(value: Boolean) { _running.value = value }
    }
}
