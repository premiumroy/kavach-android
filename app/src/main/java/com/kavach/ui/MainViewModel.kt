package com.kavach.ui

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kavach.KavachApp
import com.kavach.data.*
import com.kavach.service.DnsStats
import com.kavach.service.KavachVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress

data class AppInfo(val packageName: String, val label: String)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val container = KavachApp.from(app)
    private val db = container.database
    private val settingsStore = container.settings

    val settings: StateFlow<KavachSettings> =
        settingsStore.flow.stateIn(viewModelScope, SharingStarted.Eagerly, KavachSettings())

    val stats = container.repository.stats
    val running = KavachVpnService.running
    val blockedCount = KavachVpnService.blocked
    val dnsBlocked = DnsStats.blocked
    val dnsForwarded = DnsStats.forwarded
    val dnsFailed = DnsStats.failed
    val upstream = DnsStats.upstream

    val rules: StateFlow<List<RuleEntity>> =
        db.rules().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val sources: StateFlow<List<SourceEntity>> =
        db.sources().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val recentLogs: StateFlow<List<LogEntity>> =
        db.logs().observeRecent().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps

    init {
        viewModelScope.launch {
            seedSourcesIfEmpty()
            // First run: fetch the real blocklists automatically.
            if (!container.repository.hasCachedLists()) updateBlocklists()
        }
        viewModelScope.launch { loadInstalledApps() }
    }

    private suspend fun seedSourcesIfEmpty() {
        if (db.sources().enabled().isEmpty() && sources.value.isEmpty()) {
            for (src in BlocklistSources.DEFAULTS) db.sources().upsert(src)
        }
    }

    private suspend fun loadInstalledApps() {
        val pm = getApplication<Application>().packageManager
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
            .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        val list = pm.queryIntentActivities(intent, 0)
            .mapNotNull { it.activityInfo?.applicationInfo }
            .distinctBy { it.packageName }
            .map { AppInfo(it.packageName, it.loadLabel(pm).toString()) }
            .sortedBy { it.label.lowercase() }
        _installedApps.value = list
    }

    fun setFilterEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsStore.setFilterEnabled(enabled)
    }

    fun setBypassed(pkg: String, bypass: Boolean) = viewModelScope.launch {
        val current = settingsStore.flow.first().bypassedApps.toMutableSet()
        if (bypass) current.add(pkg) else current.remove(pkg)
        settingsStore.setBypassedApps(current)
    }

    fun setUpstream(value: String) = viewModelScope.launch { settingsStore.setUpstream(value) }
    fun setStartOnBoot(v: Boolean) = viewModelScope.launch { settingsStore.setStartOnBoot(v) }
    fun setBlockIpv6(v: Boolean) = viewModelScope.launch { settingsStore.setBlockIpv6(v) }
    fun setLogBlocked(v: Boolean) = viewModelScope.launch { settingsStore.setLogBlocked(v) }

    fun addRule(domain: String, type: RuleType) = viewModelScope.launch {
        val d = domain.trim().lowercase().removePrefix("http://").removePrefix("https://").trimEnd('/')
        if (d.isNotEmpty()) {
            db.rules().upsert(RuleEntity(domain = d, type = type))
            container.repository.reload()
        }
    }

    fun deleteRule(rule: RuleEntity) = viewModelScope.launch {
        db.rules().delete(rule)
        container.repository.reload()
    }

    fun toggleSource(src: SourceEntity) = viewModelScope.launch {
        db.sources().upsert(src.copy(enabled = !src.enabled))
    }

    private val _updateStatus = MutableStateFlow<String?>(null)
    val updateStatus: StateFlow<String?> = _updateStatus

    fun updateBlocklists() = viewModelScope.launch {
        _updateStatus.value = "Downloading blocklists..."
        val r = container.repository.updateAll()
        settingsStore.setLastUpdate(System.currentTimeMillis())
        _updateStatus.value = buildString {
            if (r.sourcesOk > 0) {
                append("Loaded ${r.totalDomains} domains from ${r.sourcesOk} sources.")
            } else {
                append("Could not download any list - your phone has no working internet right now. ")
                append("The built-in list is still active. Check your connection and tap Update again.")
            }
            if (r.errors.isNotEmpty() && r.sourcesOk > 0) {
                append("\nSome sources failed: ")
                append(r.errors.joinToString("; "))
            }
        }
    }

    fun clearLogs() = viewModelScope.launch { db.logs().clear() }

    fun reloadRules() = viewModelScope.launch { container.repository.reload() }

    private val _testResult = MutableStateFlow<String?>(null)
    val testResult: StateFlow<String?> = _testResult

    /** Resolve a known ad domain through the system resolver (which goes through
     *  our filter) and report whether it was blocked. */
    fun testBlocking() = viewModelScope.launch {
        _testResult.value = "Testing..."
        _testResult.value = withContext(Dispatchers.IO) {
            val host = "doubleclick.net"
            try {
                val addr = InetAddress.getByName(host).hostAddress ?: "?"
                if (addr == "0.0.0.0" || addr == "::" || addr.all { it == '0' || it == ':' })
                    "BLOCKED - $host -> $addr. The filter is working."
                else
                    "NOT blocked - $host resolved to $addr. Filter may be off."
            } catch (e: Exception) {
                "BLOCKED - $host lookup failed (${e.javaClass.simpleName}). The filter is working."
            }
        }
    }
}
