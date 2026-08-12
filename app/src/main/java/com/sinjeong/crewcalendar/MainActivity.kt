package com.sinjeong.crewcalendar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sinjeong.crewcalendar.presentation.admin.AdminScreen
import com.sinjeong.crewcalendar.presentation.auth.AuthViewModel
import com.sinjeong.crewcalendar.presentation.auth.LoginScreen
import com.sinjeong.crewcalendar.presentation.calendar.MainCalendarScreen
import com.sinjeong.crewcalendar.presentation.contacts.OfficeContactsScreen
import com.sinjeong.crewcalendar.presentation.mates.MatesScreen
import com.sinjeong.crewcalendar.presentation.roster.RosterScreen
import com.sinjeong.crewcalendar.presentation.settings.SettingsScreen
import com.sinjeong.crewcalendar.presentation.timetable.DeadheadScreen
import com.sinjeong.crewcalendar.presentation.theme.SinjeongTheme
import com.sinjeong.crewcalendar.presentation.theme.ThemeController
import com.sinjeong.crewcalendar.presentation.theme.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var themeController: ThemeController

    // 내일 근무 알림용 알림 권한 (안드로이드 13+, 거부해도 앱은 정상 동작)
    private val notifPermission =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        setContent {
            val mode by themeController.mode.collectAsState()
            val dark = when (mode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            // 시스템 바 아이콘(시계·배터리·제스처바) 명암.
            // enableEdgeToEdge()는 resources.configuration.uiMode로만 판단해서
            // (1) 앱 안에서 고른 다크/라이트를 무시하고 (2) onCreate에서 한 번만 정해진다.
            // → 시스템은 라이트인데 앱만 다크로 두면 어두운 배경에 어두운 아이콘이라 안 보였다.
            // 테마를 그리는 값(dark)과 같은 값으로 매번 다시 맞춘다.
            val view = LocalView.current
            SideEffect {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            SinjeongTheme(darkTheme = dark) {
                AppRoot()
            }
        }
    }
}

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Calendar("calendar", "달력", Icons.Default.CalendarMonth),
    Mates("mates", "동료", Icons.Default.Groups),
    Settings("settings", "설정", Icons.Default.Settings),
}

@Composable
private fun AppRoot() {
    // 이름+사번 로그인 게이트
    val authVm: AuthViewModel = hiltViewModel()
    val user by authVm.user.collectAsStateWithLifecycle()
    if (user == null) {
        LoginScreen()
        return
    }

    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentDest = backStack?.destination

    Scaffold(
        bottomBar = {
            // M3 기본 80dp → 56dp. 달력에 세로 공간을 그만큼 돌려준다(사용자 요청 "3단계 줄여줘").
            // 제스처 바 높이는 따로 더해 준다 — 56dp 안에서 깎으면 탭이 제스처 바에 먹힌다.
            val gestureBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            NavigationBar(modifier = Modifier.height(56.dp + gestureBar)) {
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
                        icon = { Icon(tab.icon, tab.label, modifier = Modifier.size(20.dp)) },
                        label = { Text(tab.label, fontSize = 10.sp, maxLines = 1) },
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
            composable(Tab.Calendar.route) {
                MainCalendarScreen(
                    onOpenRoster = { nav.navigate("roster") },
                )
            }
            composable("roster") { RosterScreen(onBack = { nav.popBackStack() }) }
            composable("deadhead") { DeadheadScreen(onBack = { nav.popBackStack() }) }
            composable(Tab.Mates.route) { MatesScreen() }
            composable(Tab.Settings.route) {
                SettingsScreen(
                    onOpenContacts = { nav.navigate("contacts") },
                    onOpenAdmin = { nav.navigate("admin") },
                )
            }
            composable("contacts") { OfficeContactsScreen(onBack = { nav.popBackStack() }) }
            composable("admin") { AdminScreen(onBack = { nav.popBackStack() }) }
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
