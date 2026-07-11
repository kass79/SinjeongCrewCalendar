package com.sinjeong.crewcalendar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sinjeong.crewcalendar.presentation.auth.AuthViewModel
import com.sinjeong.crewcalendar.presentation.auth.LoginScreen
import com.sinjeong.crewcalendar.presentation.calendar.MainCalendarScreen
import com.sinjeong.crewcalendar.presentation.diaboard.DiaBoardScreen
import com.sinjeong.crewcalendar.presentation.mates.MatesScreen
import com.sinjeong.crewcalendar.presentation.settings.SettingsScreen
import com.sinjeong.crewcalendar.presentation.theme.SinjeongTheme
import com.sinjeong.crewcalendar.presentation.theme.ThemeController
import com.sinjeong.crewcalendar.presentation.theme.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var themeController: ThemeController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val mode by themeController.mode.collectAsState()
            val dark = when (mode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            SinjeongTheme(darkTheme = dark) {
                AppRoot()
            }
        }
    }
}

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Calendar("calendar", "달력", Icons.Default.CalendarMonth),
    DiaBoard("diaboard", "교번표", Icons.Default.TableChart),
    Mates("mates", "동료", Icons.Default.Groups),
    Settings("settings", "설정", Icons.Default.Settings),
}

@Composable
private fun AppRoot() {
    // 이름+사번 로그인 게이트
    val authVm: AuthViewModel = hiltViewModel()
    val user by authVm.user.collectAsStateWithLifecycle()
    if (user == null) {
        LoginScreen(onLogin = authVm::login)
        return
    }

    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentDest = backStack?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentDest?.hierarchy?.any { it.route == tab.route } == true,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Tab.Calendar.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Tab.Calendar.route) { MainCalendarScreen() }
            composable(Tab.DiaBoard.route) { DiaBoardScreen() }
            composable(Tab.Mates.route) { MatesScreen() }
            composable(Tab.Settings.route) { SettingsScreen() }
        }
    }
}

/** 후속 구현 예정 화면 자리 */
@Composable
private fun PlaceholderScreen(title: String) {
    Box {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(24.dp),
        )
    }
}
