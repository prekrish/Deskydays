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
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.deskdays.app.R
import com.deskdays.app.ui.screens.PlaceholderScreen
import com.deskdays.app.ui.theme.DeskColors

/** Bottom navigation tabs: Today · Calendar · Stats · Office. */
enum class Tab(val route: String, val label: String, @DrawableRes val icon: Int) {
    Today("today", "Today", R.drawable.ic_tab_today),
    Calendar("calendar", "Calendar", R.drawable.ic_tab_calendar),
    Stats("stats", "Stats", R.drawable.ic_tab_stats),
    Office("office", "Office", R.drawable.ic_tab_office),
}

@Composable
fun DeskDaysRoot() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.entries.forEach { tab ->
                    val selected = current?.hierarchy?.any { it.route == tab.route } == true
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
            composable(Tab.Office.route) { PlaceholderScreen("Office", "Office location, Wi-Fi and counting rules.") }
        }
    }
}
