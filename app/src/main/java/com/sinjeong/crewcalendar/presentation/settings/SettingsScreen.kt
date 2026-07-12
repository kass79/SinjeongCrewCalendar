package com.sinjeong.crewcalendar.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sinjeong.crewcalendar.domain.model.Bundled
import com.sinjeong.crewcalendar.domain.model.RouteTable
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
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val mode by viewModel.themeController.mode.collectAsStateWithLifecycle()
    val savedMonths by viewModel.savedMonths.collectAsStateWithLifecycle()
    var confirmLogout by remember { mutableStateOf(false) }

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

            // 데이터 정보
            SectionTitle("데이터")
            SettingRow(title = "시각표", sub = "25.03.04 개정 (본선·지선)")
            SettingRow(
                title = "행로표 (열번·근무시간)",
                sub = "확인일 2023.10.04 · 본선 ${RouteTable.MAIN_DAY_WEEKDAY.size + RouteTable.MAIN_NIGHT.size}개 다이아 " +
                    "· 지선/평휴(야간) 자료 입수 대기",
            )
            SettingRow(title = "저장 방식", sub = "체험판 — 이 폰에만 저장 (서버 연동 시 자동 백업·동료 공유)")

            // 문의 · 저작권
            SectionTitle("문의")
            SettingRow(title = "kass7942@gmail.com", sub = "근무 수정·건의는 메일로")
            Text(
                "© 2026 Kang SungJin. All rights reserved.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )

            Spacer(Modifier.height(24.dp))
        }
    }

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
