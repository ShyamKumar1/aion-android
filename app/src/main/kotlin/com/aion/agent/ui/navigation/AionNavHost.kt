package com.aion.agent.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.aion.agent.ui.chat.ChatScreen
import com.aion.agent.ui.settings.SettingsScreen

/**
 * Root navigation graph. Two destinations: Chat (default) and Settings.
 */
@Composable
fun AionNavHost(
    navController: NavHostController,
) {
    Scaffold(
        bottomBar = { AionBottomBar(navController) },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Route.Chat.path,
        ) {
            composable(Route.Chat.path) {
                ChatScreen(paddingValues = padding)
            }
            composable(Route.Settings.path) {
                SettingsScreen()
            }
        }
    }
}

@Composable
private fun AionBottomBar(navController: NavHostController) {
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination
    NavigationBar {
        Route.All.forEach { route ->
            val selected = current?.hierarchy?.any { it.route == route.path } == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(route.path) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(route.icon, contentDescription = route.label) },
                label = { Text(route.label) },
            )
        }
    }
}

sealed class Route(
    val path: String,
    val label: String,
    val icon: ImageVector,
) {
    data object Chat : Route("chat", "Chat", Icons.Filled.SmartToy)
    data object Settings : Route("settings", "Settings", Icons.Filled.Settings)

    companion object {
        val All = listOf(Chat, Settings)
    }
}
