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
    var updating by remember { mutableStateOf(false) }

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
                Spacer(Modifier.height(10.dp))
                Button(onClick = { updating = true; vm.updateBlocklists() }, enabled = !updating) {
                    Text(if (updating) "Updating..." else "Update blocklists")
                }
                LaunchedEffect(stats) { if (updating && stats.loaded) updating = false }
            }
        }

        Text("Tip: first launch downloads lists and needs internet. After that it works offline.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
