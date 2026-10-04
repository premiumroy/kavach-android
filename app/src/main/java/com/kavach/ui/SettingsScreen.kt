package com.kavach.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val settings by vm.settings.collectAsState()
    val sources by vm.sources.collectAsState()
    var upstream by remember(settings.upstreamDns) { mutableStateOf(settings.upstreamDns) }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Settings", style = MaterialTheme.typography.titleLarge) }

        item {
            OutlinedTextField(
                value = upstream,
                onValueChange = { upstream = it; vm.setUpstream(it) },
                label = { Text("Upstream DNS (blank = network default)") },
                placeholder = { Text("1.1.1.1 or 9.9.9.9") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Block IPv6", Modifier.weight(1f))
                Switch(checked = settings.blockIpv6, onCheckedChange = { vm.setBlockIpv6(it) })
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Start on boot", Modifier.weight(1f))
                Switch(checked = settings.startOnBoot, onCheckedChange = { vm.setStartOnBoot(it) })
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Keep a blocked log", Modifier.weight(1f))
                Switch(checked = settings.logBlocked, onCheckedChange = { vm.setLogBlocked(it) })
            }
        }

        item {
            Text("Blocklist sources", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp))
        }
        items(sources, key = { it.id }) { src ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(src.name, style = MaterialTheme.typography.bodyMedium)
                    if (src.domainCount > 0)
                        Text("${src.domainCount} domains", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = src.enabled, onCheckedChange = { vm.toggleSource(src) })
            }
            HorizontalDivider()
        }
        item {
            Button(onClick = { vm.updateBlocklists() }, modifier = Modifier.fillMaxWidth()) {
                Text("Update all enabled sources")
            }
        }
        item {
            Text("Kavach never sends your traffic to a remote server. The VPN is local and " +
                 "carries only DNS.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp))
        }
        item {
            Text("What DNS blocking can and cannot do:",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp))
            Text("CAN block: ads and trackers that come from separate ad domains " +
                 "(doubleclick, ad networks, analytics, and most in-app ad SDKs). " +
                 "CANNOT block: ads served from the same domain as the content you are reading - " +
                 "Facebook/Instagram sponsored posts, YouTube in-app video ads, and sponsored " +
                 "content inside social feeds. That is a limit of every DNS blocker, not a bug.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Text("If blocking seems to do nothing, make sure Android's Private DNS is set to " +
                 "Automatic (Settings > Network > Private DNS). Private DNS (DoT) sends DNS " +
                 "directly to a provider and bypasses this filter.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp))
        }
    }
}
