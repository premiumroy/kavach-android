package com.kavach.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("kavach_settings")

data class KavachSettings(
    val filterEnabled: Boolean = false,
    val upstreamDns: String = "",        // empty = use the network's DNS servers
    val blockIpv6: Boolean = false,
    val startOnBoot: Boolean = false,
    val logBlocked: Boolean = true,
    val bypassedApps: Set<String> = emptySet(),
)

class SettingsStore(private val context: Context) {
    private val K_ENABLED = booleanPreferencesKey("filter_enabled")
    private val K_UPSTREAM = stringPreferencesKey("upstream_dns")
    private val K_IPV6 = booleanPreferencesKey("block_ipv6")
    private val K_BOOT = booleanPreferencesKey("start_on_boot")
    private val K_LOG = booleanPreferencesKey("log_blocked")
    private val K_LAST_UPDATE = longPreferencesKey("last_update")
    private val K_BYPASS = stringSetPreferencesKey("bypassed_apps")

    val flow: Flow<KavachSettings> = context.dataStore.data.map { p ->
        KavachSettings(
            filterEnabled = p[K_ENABLED] ?: false,
            upstreamDns = p[K_UPSTREAM] ?: "",
            blockIpv6 = p[K_IPV6] ?: false,
            startOnBoot = p[K_BOOT] ?: false,
            logBlocked = p[K_LOG] ?: true,
            bypassedApps = p[K_BYPASS] ?: emptySet(),
        )
    }

    suspend fun setFilterEnabled(v: Boolean) = context.dataStore.edit { it[K_ENABLED] = v }
    suspend fun setUpstream(v: String) = context.dataStore.edit { it[K_UPSTREAM] = v }
    suspend fun setBlockIpv6(v: Boolean) = context.dataStore.edit { it[K_IPV6] = v }
    suspend fun setStartOnBoot(v: Boolean) = context.dataStore.edit { it[K_BOOT] = v }
    suspend fun setLogBlocked(v: Boolean) = context.dataStore.edit { it[K_LOG] = v }
    suspend fun setLastUpdate(ts: Long) = context.dataStore.edit { it[K_LAST_UPDATE] = ts }
    suspend fun setBypassedApps(v: Set<String>) = context.dataStore.edit { it[K_BYPASS] = v }
}
