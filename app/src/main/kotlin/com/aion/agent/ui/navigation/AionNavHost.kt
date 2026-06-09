package com.aion.agent.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.aion.agent.core.AgentCapability
import com.aion.agent.ui.chat.ChatScreen
import com.aion.agent.ui.notifications.NotificationHistoryScreen
import com.aion.agent.ui.logs.LogViewerScreen
import com.aion.agent.ui.onboarding.OnboardingModelChoice
import com.aion.agent.ui.onboarding.OnboardingScreen
import com.aion.agent.ui.settings.BatteryDashboardScreen
import com.aion.agent.ui.settings.PrivacyDashboardScreen
import com.aion.agent.ui.settings.SettingsScreen

/**
 * Root navigation graph. Shows onboarding on first launch, then the main app.
 */
@Composable
fun AionNavHost(
    navController: NavHostController,
    onboardingViewModel: OnboardingViewModel = hiltViewModel(),
) {
    val onboardingCompleted by onboardingViewModel.hasCompleted.collectAsStateWithLifecycle(false)

    if (!onboardingCompleted) {
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            OnboardingScreen(
                onComplete = { capability, modelChoice ->
                    onboardingViewModel.completeOnboarding(capability, modelChoice)
                },
            )
        }
    } else {
        MainAppScaffold(navController)
    }
}

/**
 * Main app with bottom nav bar.
 */
@Composable
private fun MainAppScaffold(navController: NavHostController) {
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
                SettingsScreen(
                    onNavigateToBattery = { navController.navigate(Route.Battery.path) },
                    onNavigateToNotifications = { navController.navigate(Route.Notifications.path) },
                    onNavigateToPrivacy = { navController.navigate(Route.Privacy.path) },
                    onNavigateToLogs = { navController.navigate(Route.EventLog.path) },
                )
            }
            composable(Route.Battery.path) {
                BatteryDashboardScreen()
            }
            composable(Route.Notifications.path) {
                NotificationHistoryScreen()
            }
            composable(Route.Privacy.path) {
                PrivacyDashboardScreen()
            }
            composable(Route.EventLog.path) {
                LogViewerScreen()
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
    data object Battery : Route("settings/battery", "Battery", Icons.Filled.BatteryFull)
    data object Notifications : Route("settings/notifications", "Notifications", Icons.Filled.Notifications)
    data object Privacy : Route("settings/privacy", "Privacy", Icons.Filled.VerifiedUser)
    data object EventLog : Route("settings/logs", "Event Log", Icons.Filled.BugReport)

    companion object {
        val All = listOf(Chat, Settings)
    }
}
