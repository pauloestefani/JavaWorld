package io.github.pauloestefani.javaworld.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.pauloestefani.javaworld.ui.screens.CreateServerScreen
import io.github.pauloestefani.javaworld.ui.screens.DeviceScreen
import io.github.pauloestefani.javaworld.ui.screens.ModSearchScreen
import io.github.pauloestefani.javaworld.ui.screens.ServerDetailScreen
import io.github.pauloestefani.javaworld.ui.screens.ServersScreen
import io.github.pauloestefani.javaworld.ui.screens.TunnelScreen

private data class TopItem(val route: String, val label: String, val icon: ImageVector)

private val topItems = listOf(
    TopItem("servers", "Servidores", Icons.Rounded.Dns),
    TopItem("device", "Celular", Icons.Rounded.Memory),
    TopItem("tunnel", "Túnel", Icons.Rounded.Public),
)

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = topItems.any { it.route == route }

    LaunchedEffect(Unit) {
        Nav.pending.collect { r ->
            if (r != null) {
                Nav.consumed()
                runCatching { nav.navigate(r) { launchSingleTop = true } }
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    topItems.forEach { item ->
                        NavigationBarItem(
                            selected = route == item.route,
                            onClick = { nav.goTop(item.route) },
                            icon = { Icon(item.icon, null) },
                            label = { Text(item.label) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                }
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = "servers", modifier = Modifier.padding(pad)) {
            composable("servers") {
                ServersScreen(
                    onOpen = { nav.navigate("server/$it") },
                    onCreate = { nav.navigate("create") },
                )
            }
            composable("device") { DeviceScreen() }
            composable("tunnel") { TunnelScreen() }
            composable("create") {
                CreateServerScreen(
                    onBack = { nav.popBackStack() },
                    onCreated = { id ->
                        nav.popBackStack()
                        nav.navigate("server/$id")
                    },
                )
            }
            composable(
                "server/{id}?tab={tab}",
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                    navArgument("tab") { type = NavType.IntType; defaultValue = 0 },
                ),
            ) { e ->
                val id = e.arguments?.getString("id").orEmpty()
                ServerDetailScreen(
                    id = id,
                    initialTab = e.arguments?.getInt("tab") ?: 0,
                    onBack = { nav.popBackStack() },
                    onSearchMods = { nav.navigate("mods/$id") },
                    onOpenTunnel = { nav.goTop("tunnel") },
                )
            }
            composable("mods/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { e ->
                ModSearchScreen(id = e.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() })
            }
        }
    }
}

private fun NavHostController.goTop(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
