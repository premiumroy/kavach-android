package com.kavach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kavach.ui.*
import com.kavach.ui.theme.KavachTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KavachTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    RootScaffold(vm)
                }
            }
        }
    }
}

private data class Destination(val route: String, val label: String, val icon: ImageVector)

@Composable
private fun RootScaffold(vm: MainViewModel) {
    val nav = rememberNavController()
    val destinations = listOf(
        Destination("home", "Home", Icons.Filled.Shield),
        Destination("apps", "Apps", Icons.Filled.Apps),
        Destination("rules", "Rules", Icons.Filled.Rule),
        Destination("settings", "Settings", Icons.Filled.Settings),
    )
    Scaffold(
        bottomBar = {
            NavigationBar {
                val backStack by nav.currentBackStackEntryAsState()
                val current = backStack?.destination
                destinations.forEach { dest ->
                    NavigationBarItem(
                        selected = current?.hierarchy?.any { it.route == dest.route } == true,
                        onClick = { nav.navigate(dest.route) { launchSingleTop = true } },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) },
                    )
                }
            }
        }
    ) { padding ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") { HomeScreen(vm) }
            composable("apps") { AppsScreen(vm) }
            composable("rules") { RulesScreen(vm) }
            composable("settings") { SettingsScreen(vm) }
        }
    }
}
