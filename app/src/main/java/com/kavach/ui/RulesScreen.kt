package com.kavach.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kavach.data.RuleEntity
import com.kavach.data.RuleType

@Composable
fun RulesScreen(vm: MainViewModel) {
    val rules by vm.rules.collectAsState()
    val logs by vm.recentLogs.collectAsState()
    var domain by remember { mutableStateOf("") }
    var isBlock by remember { mutableStateOf(true) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Custom rules", style = MaterialTheme.typography.titleLarge)
        Text("Allow rules always win over block rules and blocklists.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = domain, onValueChange = { domain = it },
                label = { Text("domain (e.g. ads.example.com)") },
                modifier = Modifier.weight(1f), singleLine = true,
            )
            Button(onClick = {
                vm.addRule(domain, if (isBlock) RuleType.BLOCK else RuleType.ALLOW)
                domain = ""
            }) { Text("Add") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = isBlock, onClick = { isBlock = true }); Text("Block")
            Spacer(Modifier.width(12.dp))
            RadioButton(selected = !isBlock, onClick = { isBlock = false }); Text("Allow")
        }

        Spacer(Modifier.height(8.dp))
        Text("Active rules (${rules.size})", style = MaterialTheme.typography.titleMedium)
        LazyColumn(Modifier.weight(1f)) {
            items(rules, key = { it.id }) { rule ->
                RuleRow(rule) { vm.deleteRule(rule) }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Recent blocked (${logs.size})", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.clearLogs() }) { Text("Clear") }
        }
        LazyColumn(Modifier.weight(1f)) {
            items(logs, key = { it.id }) { log ->
                Text(log.domain, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    }
}

@Composable
private fun RuleRow(rule: RuleEntity, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(rule.domain, style = MaterialTheme.typography.bodyMedium)
            Text(rule.type.name, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
    }
    HorizontalDivider()
}
