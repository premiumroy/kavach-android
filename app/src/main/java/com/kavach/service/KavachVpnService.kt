package com.kavach.service

import android.content.Intent
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
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
 * A *local* VPN: the tun carries only DNS traffic to our fake resolver; nothing
 * is sent to any remote server.
 *
 * Upstream resolvers are taken from the device's real (non-VPN) network, captured
 * before the tunnel is established and refreshed on network changes. This is
 * essential - asking the system for DNS *after* the tunnel is up returns our own
 * fake resolver and would loop forever, which kills all connectivity.
 */
class KavachVpnService : VpnService() {

    private var tun: ParcelFileDescriptor? = null
    private var proxy: DnsProxy? = null
    private var blockedSession = 0
    private var sinceNotification = 0

    @Volatile private var upstreamDns: List<String> = emptyList()
    private var callbackRegistered = false

    private val netCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refreshUpstream()
        override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) = refreshUpstream()
        override fun onLost(network: Network) = refreshUpstream()
    }

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

        // Capture the real DNS servers BEFORE the tunnel exists.
        upstreamDns = queryUnderlyingDns()
        DnsStats.upstream.value = upstreamDns
        DnsStats.reset()
        registerNetworkCallback()

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

        // Keep Kavach's OWN traffic out of the tunnel. Its blocklist downloads
        // must never depend on the filter that is still starting up.
        runCatching { builder.addDisallowedApplication(packageName) }

        for (pkg in settings.bypassedApps) {
            if (pkg != packageName) runCatching { builder.addDisallowedApplication(pkg) }
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
        startForeground(Notification.NOTIFICATION_ID, Notification.build(this, blockedSession))

        val upstreamProvider: () -> List<String> = {
            val custom = settings.upstreamDns.trim()
            val base = if (custom.isNotBlank()) listOf(custom) else upstreamDns
            (base + FALLBACK_DNS)
                .filter { it.isNotBlank() && it != IPV4_DNS && it != IPV6_DNS }
                .distinct()
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
        blockedSession++
        _blocked.value = blockedSession
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
            getSystemService(android.app.NotificationManager::class.java)
                .notify(Notification.NOTIFICATION_ID, Notification.build(this, blockedSession))
        }
    }

    private fun stopVpn() {
        unregisterNetworkCallback()
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
        unregisterNetworkCallback()
        proxy?.stop()
        proxy = null
        runCatching { tun?.close() }
        tun = null
        _running.value = false
        super.onDestroy()
    }

    // ---- upstream resolution ------------------------------------------------

    private fun registerNetworkCallback() {
        if (callbackRegistered) return
        runCatching {
            val cm = getSystemService(ConnectivityManager::class.java) ?: return
            cm.registerDefaultNetworkCallback(netCallback)
            callbackRegistered = true
        }
    }

    private fun unregisterNetworkCallback() {
        if (!callbackRegistered) return
        runCatching {
            getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(netCallback)
        }
        callbackRegistered = false
    }

    private fun refreshUpstream() {
        val dns = queryUnderlyingDns()
        if (dns.isNotEmpty()) {
            upstreamDns = dns
            DnsStats.upstream.value = dns
        }
    }

    /**
     * DNS servers of every real (non-VPN) network. VPN networks are skipped so we
     * never point at our own fake resolver.
     */
    private fun queryUnderlyingDns(): List<String> {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return emptyList()
        val out = LinkedHashSet<String>()
        for (network in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue
            val lp = cm.getLinkProperties(network) ?: continue
            for (addr in lp.dnsServers) {
                addr.hostAddress?.takeIf { it.isNotBlank() }?.let { out.add(it) }
            }
        }
        return out.toList()
    }

    companion object {
        const val ACTION_START = "com.kavach.action.START"
        const val ACTION_STOP = "com.kavach.action.STOP"

        private const val IPV4_ADDRESS = "10.111.222.1"
        private const val IPV4_DNS = "10.111.222.2"
        private const val IPV6_ADDRESS = "fd00:1:2:3::1"
        private const val IPV6_DNS = "fd00:1:2:3::2"

        // Used only if the device's own resolvers are unavailable.
        private val FALLBACK_DNS = listOf(
            "1.1.1.1", "8.8.8.8", "9.9.9.9",
            "2606:4700:4700::1111", "2001:4860:4860::8888",
        )

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running

        private val _blocked = MutableStateFlow(0)
        val blocked: StateFlow<Int> = _blocked

        fun markRunning(value: Boolean) { _running.value = value }
    }
}
