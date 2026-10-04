package com.kavach.ui

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kavach.KavachApp
import com.kavach.data.*
import com.kavach.service.KavachVpnService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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

    val rules: StateFlow<List<RuleEntity>> =
        db.rules().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val sources: StateFlow<List<SourceEntity>> =
        db.sources().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val recentLogs: StateFlow<List<LogEntity>> =
        db.logs().observeRecent().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps

    init {
        viewModelScope.launch { seedSourcesIfEmpty() }
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

    fun updateBlocklists() = viewModelScope.launch {
        container.repository.updateAll()
        settingsStore.setLastUpdate(System.currentTimeMillis())
    }

    fun clearLogs() = viewModelScope.launch { db.logs().clear() }

    fun reloadRules() = viewModelScope.launch { container.repository.reload() }
}
