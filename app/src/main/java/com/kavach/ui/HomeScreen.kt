package com.kavach.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kavach.service.VpnController

@Composable
fun HomeScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    val running by vm.running.collectAsState()
    val blocked by vm.blockedCount.collectAsState()
    val stats by vm.stats.collectAsState()
    val forwarded by vm.dnsForwarded.collectAsState()
    val failed by vm.dnsFailed.collectAsState()
    val upstream by vm.upstream.collectAsState()
    val testResult by vm.testResult.collectAsState()
    val updateStatus by vm.updateStatus.collectAsState()

    val consent = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            VpnController.start(context)
            vm.setFilterEnabled(true)
        } else {
            vm.setFilterEnabled(false)
        }
    }

    fun toggle(on: Boolean) {
        if (on) {
            val intent = VpnController.prepare(context)
            if (intent != null) consent.launch(intent)
            else { VpnController.start(context); vm.setFilterEnabled(true) }
        } else {
            VpnController.stop(context)
            vm.setFilterEnabled(false)
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Kavach", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Ad and tracker blocker that runs behind your whole phone.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Card {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (running) "Protection ON" else "Protection OFF",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(12.dp))
                Switch(checked = settings.filterEnabled || running, onCheckedChange = { toggle(it) })
                Spacer(Modifier.height(12.dp))
                Text("Blocked this session: $blocked", style = MaterialTheme.typography.bodyLarge)
            }
        }

        Card {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Blocklists", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text("${stats.domainCount} domains loaded from ${stats.sources} downloaded lists",
                    style = MaterialTheme.typography.bodyMedium)
                if (stats.domainCount < 1000) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Only the small built-in starter list is active. Tap below to download the real blocklists.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Button(onClick = { vm.updateBlocklists() }) { Text("Update blocklists") }
                updateStatus?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Card {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Diagnostics", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text("Blocked: $blocked   Allowed: $forwarded   Failed: $failed",
                    style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Upstream DNS: " + (if (upstream.isEmpty()) "not detected yet" else upstream.joinToString(", ")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Button(onClick = { vm.testBlocking() }) { Text("Test ad blocking") }
                testResult?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(6.dp))
                Text("Allowed should be much larger than Failed. If Failed keeps climbing, " +
                     "your network is blocking our DNS servers - set a different upstream DNS in Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Text("Tip: first launch downloads lists and needs internet. After that it works offline.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
