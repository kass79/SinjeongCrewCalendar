package com.sinjeong.crewcalendar.presentation.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.sinjeong.crewcalendar.BuildConfig
import com.sinjeong.crewcalendar.widget.AlarmPermission
import com.sinjeong.crewcalendar.domain.model.Bundled
import com.sinjeong.crewcalendar.domain.model.User
import com.sinjeong.crewcalendar.data.local.LocalUserRepository
import com.sinjeong.crewcalendar.domain.repository.SnapshotRepository
import com.sinjeong.crewcalendar.domain.repository.UserRepository
import com.sinjeong.crewcalendar.presentation.theme.ThemeController
import com.sinjeong.crewcalendar.presentation.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

/**
 * 안전앱 "슬기로운 승무생활" 열기 — 설정 화면과 달력 상단바가 함께 쓴다.
 * 설치됨 → 실행 / 미설치 → 플레이스토어 / 스토어 없음 → 웹. 어떤 경우에도 크래시 없음.
 * (조회는 매니페스트 <queries>에 패키지가 선언돼 있어야 동작한다)
 */
fun openSafetyApp(context: Context) {
    val pkg = "com.sinjeong.safety"
    val launch = runCatching { context.packageManager.getLaunchIntentForPackage(pkg) }.getOrNull()
    if (launch != null) {
        runCatching { context.startActivity(launch) }
        return
    }
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))) }
        .onFailure {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")),
                )
            }
        }
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userRepo: UserRepository,
    private val snapshotRepo: SnapshotRepository,
    val themeController: ThemeController,
) : ViewModel() {
    val user: StateFlow<User?> = userRepo.observeMe()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _savedMonths = MutableStateFlow<List<YearMonth>>(emptyList())
    val savedMonths: StateFlow<List<YearMonth>> = _savedMonths

    fun refreshSavedMonths() {
        val uid = user.value?.uid ?: return
        viewModelScope.launch { _savedMonths.value = snapshotRepo.savedMonths(uid) }
    }

    fun setTheme(mode: ThemeMode) = themeController.set(mode)

    fun logout() {
        viewModelScope.launch {
            when (val r = userRepo) {
                is LocalUserRepository -> r.logout()
                is com.sinjeong.crewcalendar.data.remote.FirestoreUserRepository -> r.logout()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onOpenContacts: () -> Unit = {},
    onOpenAdmin: () -> Unit = {},
) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val mode by viewModel.themeController.mode.collectAsStateWithLifecycle()
    val savedMonths by viewModel.savedMonths.collectAsStateWithLifecycle()
    var confirmLogout by remember { mutableStateOf(false) }
    var askAdminPw by remember { mutableStateOf(false) }

    LaunchedEffect(user?.uid) { viewModel.refreshSavedMonths() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("설정", fontWeight = FontWeight.ExtraBold) }) },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // 내 정보
            SectionTitle("내 정보")
            SettingRow(
                title = user?.name ?: "-",
                sub = "사번 ${user?.uid ?: "-"}",
                trailing = { TextButton(onClick = { confirmLogout = true }) { Text("로그아웃") } },
            )
            SettingRow(
                title = "근무 패턴",
                sub = buildString {
                    val g = Bundled.groupFor(user?.patternId)
                    append(g?.label ?: "미선택")
                    append(" · 변경은 달력 상단 [근무선택]")
                },
            )

            // 화면
            SectionTitle("화면")
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("테마", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                SingleChoiceSegmentedButtonRow {
                    ThemeMode.entries.forEachIndexed { i, m ->
                        SegmentedButton(
                            selected = mode == m,
                            onClick = { viewModel.setTheme(m) },
                            shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                        ) {
                            Text(
                                when (m) { ThemeMode.SYSTEM -> "시스템"; ThemeMode.LIGHT -> "라이트"; ThemeMode.DARK -> "다크" },
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }

            // 월별 근무기록 (동결 보존)
            SectionTitle("월별 근무기록")
            if (savedMonths.isEmpty()) {
                SettingRow(
                    title = "보존된 기록 없음",
                    sub = "지난 달을 처음 열람할 때 자동으로 그 달 근무가 동결 저장됩니다. 이후 근무선택을 바꿔도 과거 기록은 변하지 않습니다.",
                )
            } else {
                savedMonths.forEach { m ->
                    SettingRow(title = "$m", sub = "동결 저장됨 — 달력에서 해당 월로 이동하면 그대로 표시")
                }
            }

            val ctx = LocalContext.current

            // 알림
            SectionTitle("알림")
            val settingsPrefs = remember { ctx.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE) }
            var notifyOn by remember { mutableStateOf(settingsPrefs.getBoolean("notify_tomorrow", true)) }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("내일 근무 미리 알림", fontWeight = FontWeight.Bold)
                    Text(
                        "매일 저녁 8시, 내일 근무·출근시각 알림 (휴일·비번은 알리지 않음)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = notifyOn, onCheckedChange = {
                    notifyOn = it
                    settingsPrefs.edit().putBoolean("notify_tomorrow", it).apply()
                })
            }
            var briefingOn by remember { mutableStateOf(settingsPrefs.getBoolean("notify_briefing", true)) }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("출근 1시간 전 브리핑", fontWeight = FontWeight.Bold)
                    Text(
                        "당일 출근 1시간 전, 근무·출근시각·날씨 요약 알림 (새벽 근무도 그대로 울림)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = briefingOn, onCheckedChange = {
                    briefingOn = it
                    settingsPrefs.edit().putBoolean("notify_briefing", it).apply()
                    com.sinjeong.crewcalendar.widget.BriefingAlarm.requestRearm(ctx) // 켜면 등록, 끄면 해제
                })
            }

            // 권한이 없으면 위 토글을 켜 놔도 브리핑·편승 알람이 걸리지 않는다(v1.6.32).
            // warning = 아예 안 울림(빨강) / notice = 울리긴 하는데 약함(전체화면 알람 꺼짐).
            // 화면에 돌아올 때마다 다시 읽어야 설정에서 켜고 온 것이 바로 반영된다.
            val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
            var alarmWarn by remember { mutableStateOf(AlarmPermission.warning(ctx)) }
            var alarmNote by remember { mutableStateOf(AlarmPermission.notice(ctx)) }
            LaunchedEffect(Unit) {
                lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
                    alarmWarn = AlarmPermission.warning(ctx)
                    alarmNote = AlarmPermission.notice(ctx)
                }
            }
            (alarmWarn ?: alarmNote)?.let { msg ->
                val blocking = alarmWarn != null
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (blocking) "알람이 울리지 않는 상태입니다" else "알람 화면이 뜨지 않습니다",
                            fontWeight = FontWeight.Bold,
                            color = if (blocking) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            msg,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { AlarmPermission.openSettings(ctx) }) { Text("설정 열기") }
                }
            }

            // 함께 쓰는 앱
            SectionTitle("함께 쓰는 앱")
            SettingRow(
                title = "사업소 연락처",
                sub = "부서·관제·주박지 번호",
                trailing = { TextButton(onClick = onOpenContacts) { Text("열기") } },
            )
            SettingRow(
                title = "슬기로운 승무생활",
                sub = "사업소 안전정보 앱",
                trailing = { TextButton(onClick = { openSafetyApp(ctx) }) { Text("열기") } },
            )

            // 데이터 정보
            SectionTitle("데이터")
            SettingRow(title = "시각표", sub = "25.03.04 개정 (본선·지선)")
            SettingRow(
                title = "행로표 (열번·근무시간)",
                sub = "본선 확인일 23.10.04 · 지선 25.03.04 — 전 다이아 열번·근무시간 내장",
            )
            SettingRow(title = "저장 방식", sub = "근무선택·근무변경은 동료와 공유, 메모·과거기록은 이 폰에만 저장")

            // 관리자
            SectionTitle("관리자")
            SettingRow(
                title = "동료 대리등록",
                sub = "앱을 아직 안 깐 동료의 이름·사번·근무를 대신 등록 (암호 필요)",
                trailing = {
                    TextButton(onClick = {
                        if (com.sinjeong.crewcalendar.presentation.admin.AdminGate.unlocked) onOpenAdmin()
                        else askAdminPw = true
                    }) { Text("열기") }
                },
            )

            // 앱 정보 · 문의 · 저작권
            SectionTitle("앱 정보")
            SettingRow(
                title = "앱 버전",
                sub = "신정승무 캘린더",
                trailing = {
                    Text(
                        "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
            )

            SectionTitle("문의")
            SettingRow(title = "kass7942@gmail.com", sub = "근무 수정·건의는 메일로")
            Text(
                "© 2026 KANG SUNG JIN. ALL RIGHTS RESERVED.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )

            Spacer(Modifier.height(24.dp))
        }
    }

    if (askAdminPw) com.sinjeong.crewcalendar.presentation.admin.AdminPasswordDialog(
        onUnlocked = { askAdminPw = false; onOpenAdmin() },
        onDismiss = { askAdminPw = false },
    )

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("로그아웃") },
            text = { Text("로그인 정보만 지워집니다. 근무기록·메모는 폰에 남습니다.") },
            confirmButton = {
                TextButton(onClick = { viewModel.logout(); confirmLogout = false }) { Text("로그아웃") }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("취소") } },
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 18.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingRow(title: String, sub: String, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(sub, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}
