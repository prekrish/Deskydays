package com.deskdays.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.deskdays.app.DeskDaysApp
import com.deskdays.app.R
import com.deskdays.app.data.SettingsRepository
import com.deskdays.app.data.TrackingMode
import com.deskdays.app.ui.office.OfficeSetupMode
import com.deskdays.app.ui.office.OfficeSetupScreen
import com.deskdays.app.ui.onboarding.LocationAccessFlow
import com.deskdays.app.ui.onboarding.OnboardingFlow
import com.deskdays.app.ui.privacy.PrivacyPolicyScreen
import kotlinx.coroutines.launch
import com.deskdays.app.ui.screens.PlaceholderScreen
import com.deskdays.app.ui.theme.DeskColors

/** Bottom navigation tabs: Today · Calendar · Stats · Office. */
enum class Tab(val route: String, val label: String, @DrawableRes val icon: Int) {
    Today("today", "Today", R.drawable.ic_tab_today),
    Calendar("calendar", "Calendar", R.drawable.ic_tab_calendar),
    Stats("stats", "Stats", R.drawable.ic_tab_stats),
    Office("office", "Office", R.drawable.ic_tab_office),
}

/** Shows onboarding until it's finished, then the main tabs. */
@Composable
fun DeskDaysRoot() {
    val container = (LocalContext.current.applicationContext as DeskDaysApp).container
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val s = settings
    when {
        s == null -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) // loading
        !s.onboardingDone -> OnboardingFlow(container.settings)
        else -> MainTabs(container.settings)
    }
}

private object Routes {
    const val TRACKING = "office/tracking"
    const val PRIVACY = "privacy"
}

@Composable
private fun MainTabs(settingsRepo: SettingsRepository) {
    val scope = rememberCoroutineScope()
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.entries.forEach { tab ->
                    val selected = current?.hierarchy?.any {
                        it.route == tab.route || (tab == Tab.Office && it.route == Routes.TRACKING)
                    } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = DeskColors.AccentDark,
                            selectedTextColor = DeskColors.AccentDark,
                            indicatorColor = DeskColors.AccentContainer,
                            unselectedIconColor = DeskColors.TextSecondary,
                            unselectedTextColor = DeskColors.TextSecondary,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Tab.Today.route, modifier = Modifier.padding(padding)) {
            composable(Tab.Today.route) { PlaceholderScreen("Today", "Progress ring, arrival time and month progress.") }
            composable(Tab.Calendar.route) { PlaceholderScreen("Calendar", "Month grid and day details.") }
            composable(Tab.Stats.route) { PlaceholderScreen("Stats", "Monthly % against target, history and CSV export.") }
            composable(Tab.Office.route) {
                OfficeSetupScreen(
                    mode = OfficeSetupMode.SETTINGS,
                    onDone = {},
                    onChangeTracking = { nav.navigate(Routes.TRACKING) },
                    onOpenPrivacyPolicy = { nav.navigate(Routes.PRIVACY) },
                )
            }
            composable(Routes.TRACKING) {
                LocationAccessFlow(
                    onAutomatic = {
                        scope.launch { settingsRepo.setTrackingMode(TrackingMode.AUTOMATIC) }
                        nav.popBackStack()
                    },
                    onManual = {
                        scope.launch { settingsRepo.setTrackingMode(TrackingMode.MANUAL) }
                        nav.popBackStack()
                    },
                    onOpenPrivacyPolicy = { nav.navigate(Routes.PRIVACY) },
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.PRIVACY) { PrivacyPolicyScreen(onBack = { nav.popBackStack() }) }
        }
    }
}
