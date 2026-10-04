package com.kavach.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Pick which apps should BYPASS filtering (everything else is filtered). */
@Composable
fun AppsScreen(vm: MainViewModel) {
    val apps by vm.installedApps.collectAsState()
    val settings by vm.settings.collectAsState()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Per-app control", style = MaterialTheme.typography.titleLarge)
        Text("Turn an app ON to let it bypass filtering. Everything else is filtered.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        if (apps.isEmpty()) {
            Text("No launchable apps found.", style = MaterialTheme.typography.bodyMedium)
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(apps, key = { it.packageName }) { app ->
                val bypass = settings.bypassedApps.contains(app.packageName)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.bodyLarge)
                        Text(app.packageName, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = bypass, onCheckedChange = { vm.setBypassed(app.packageName, it) })
                }
                HorizontalDivider()
            }
        }
    }
}
