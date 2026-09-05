package com.sinjeong.crewcalendar.presentation.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
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
import com.sinjeong.crewcalendar.domain.model.cancelPendingSegments
import com.sinjeong.crewcalendar.domain.model.pendingSegment
import com.sinjeong.crewcalendar.data.local.LocalUserRepository
import com.sinjeong.crewcalendar.domain.repository.SnapshotRepository
import com.sinjeong.crewcalendar.domain.repository.UserRepository
import com.sinjeong.crewcalendar.presentation.theme.MapStyle
import com.sinjeong.crewcalendar.presentation.theme.ThemeController
import com.sinjeong.crewcalendar.presentation.theme.ThemeMode
import com.sinjeong.crewcalendar.presentation.weather.WX_LOC_FIXED_KEY
import dagger.hilt.android.lifecycle.HiltViewModel
import com.sinjeong.crewcalendar.presentation.live.Line2TimetableLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

/**
 * 이 앱의 플레이스토어 페이지 열기 — 설정 > 앱 버전을 누를 때(v1.6.39).
 *
 * **왜 In-App Update API가 아니라 스토어 페이지인가.**
 * 이 앱은 플레이(비공개 테스트)와 사이드로드 APK **양쪽**으로 배포된다.
 * `com.google.android.play:app-update`는 플레이로 설치된 사본에서만 동작하고
 * 사이드로드에선 조용히 아무것도 안 한다 — "업데이트가 안 된다"는 지금 문제가
 * 그대로 반복된다. 반면 스토어 페이지는 **설치 출처와 무관하게** 열리고,
 * 테스터 계정이면 거기서 바로 업데이트 버튼을 누를 수 있다.
 * 안전앱("슬기로운 승무생활")도 같은 방식이라 두 앱의 사용감이 같아진다.
 * 한계: 사이드로드로만 쓰고 테스터 등록도 안 된 기기에선 "페이지를 찾을 수 없음"이 뜼다.
 * 새 의존성 0건.
 */
fun openPlayStore(context: Context) {
    val pkg = context.packageName
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

    /** 지도 스타일(v1.7.0) — 저장은 테마와 같은 저장소·같은 방식이다. */
    fun setMapStyle(style: MapStyle) = themeController.setMapStyle(style)

    /** 예약된 교번 변경 취소 — 아직 시작 안 한 구간만 버린다(지난 달력은 그대로) */
    fun cancelScheduledPattern() {
        val u = user.value ?: return
        viewModelScope.launch { userRepo.upsert(u.cancelPendingSegments()) }
    }

    /**
     * 근무 저장 해제 (v1.6.69). 저장은 날짜 하나만 기억하므로 지우면 끝이고 **근무는 안 바뀐다** —
     * 이미 걸어 둔 교번 구간(예약)은 그대로 남는다(그건 위 `예약 취소`가 따로 맡는다).
     */
    fun clearFrozenDuties() {
        val u = user.value ?: return
        viewModelScope.launch { userRepo.upsert(u.copy(frozenUntil = null)) }
    }

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
    onOpenMenuAdmin: () -> Unit = {},
    onOpenNoticeAdmin: () -> Unit = {},
) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val mode by viewModel.themeController.mode.collectAsStateWithLifecycle()
    val savedMonths by viewModel.savedMonths.collectAsStateWithLifecycle()
    var confirmLogout by remember { mutableStateOf(false) }
    var askAdminPw by remember { mutableStateOf(false) }
    /** 암호를 통과한 뒤 어디로 갈지 — 대리등록 / 식단표 / 공지 세 곳이 같은 잠금을 쓴다 */
    var afterUnlock by remember { mutableStateOf<(() -> Unit)?>(null) }

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
            // 예약된 교번 변경 (v1.6.63). 예약해 둔 날이 오기 전에는 달력이 하나도 안 바뀌어
            // **예약했는지 알 수 없다.** 상시 확인·취소는 여기 한 곳뿐이고(과하게 늘리지 않는다),
            // 예약 직후에는 달력 스낵바가 한 번 더 알린다.
            user?.pendingSegment()?.let { seg ->
                val g = Bundled.groupFor(seg.patternId)
                val duty = Bundled.ALL_PATTERNS.firstOrNull { it.id == seg.patternId }
                    ?.dutyOn(seg.from, seg.patternOffset)?.display.orEmpty()
                SettingRow(
                    title = "${seg.from.monthValue}월 ${seg.from.dayOfMonth}일부터 ${g?.label ?: "새 교번"}",
                    sub = "그 전날까지는 지금 교번 그대로입니다" +
                        if (duty.isNotBlank()) " · ${seg.from.monthValue}/${seg.from.dayOfMonth} 근무 $duty" else "",
                    trailing = {
                        TextButton(onClick = { viewModel.cancelScheduledPattern() }) { Text("예약 취소") }
                    },
                )
            }
            // 근무 저장 (v1.6.69). 실수로 저장했을 때 푸는 유일한 자리 — 저장하면 달력 바탕만
            // 연녹색이 될 뿐 근무는 그대로라, 여기가 없으면 "왜 근무선택이 다음 달부터만 되지?"를
            // 풀 방법이 없다. 예약 안내 바로 아래에 둔 이유: 둘 다 "근무선택에 걸린 제약"이다.
            user?.frozenUntil?.let { until ->
                SettingRow(
                    title = "${until.monthValue}월 ${until.dayOfMonth}일까지 근무 저장됨",
                    sub = "[근무선택]은 ${until.plusDays(1).monthValue}월 ${until.plusDays(1).dayOfMonth}일부터만 적용됩니다" +
                        " · 하루짜리 [근무변경]은 그대로 됩니다",
                    trailing = {
                        TextButton(onClick = { viewModel.clearFrozenDuties() }) { Text("저장 해제") }
                    },
                )
            }

            // 아래 여러 절이 같이 쓰는 값이라 화면 맨 앞으로 끌어올렸다(v1.6.68) — 파일은 하나뿐이다.
            val ctx = LocalContext.current
            val settingsPrefs = remember { ctx.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE) }

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

            /*
             * 지도 스타일(v1.7.0) — 사용자 원문 *"설정에 이런 클레이 디자인도 선택할수있게 가능?"*
             *
             * 실시간 지도(본선 전체 보기·신정지선 카드)의 **색만** 고른다 — 배치·글자 크기·
             * 겹침 회피는 스타일과 무관하게 그대로다. 바로 위 테마 줄과 **같은 세그먼트**라
             * 한 벌로 읽히고, 저장도 테마와 같은 저장소(`theme`)다.
             *
             * 한 줄 통째로 깐 이유는 날씨 줄과 같다 — 제목 옆에 붙이면 글자배율 1.5 에서
             * `운전실 남색` 라벨이 눌린다.
             */
            val mapStyle by viewModel.themeController.mapStyle.collectAsStateWithLifecycle()
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("지도 스타일", fontWeight = FontWeight.Bold)
                Text(
                    if (mapStyle == MapStyle.CLAY)
                        "실시간 지도를 크림 바탕·민트 선로의 클레이 그림으로 (다크 모드에서도 밝게)"
                    else "실시간 지도를 운전실 보조설비 화면처럼 남색으로",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    MapStyle.entries.forEachIndexed { i, s ->
                        SegmentedButton(
                            selected = mapStyle == s,
                            onClick = { viewModel.setMapStyle(s) },
                            shape = SegmentedButtonDefaults.itemShape(i, MapStyle.entries.size),
                        ) { Text(s.label, fontSize = 11.sp) }
                    }
                }
            }

            // 날씨 (v1.6.68). 달력 헤더 칩이 어디 날씨인지 고른다.
            // 선택지는 **둘뿐이다** — "지도에서 임의 지점 고르기"는 만들지 마라. 이 앱 사용자는
            // 한 사업소로 출퇴근하고, 그 외의 경우는 "현재 위치"가 이미 답이다.
            // 컨트롤은 바로 위 테마 줄과 같은 세그먼트 — 둘 중 하나를 고르는 같은 성격이라 한 벌로 읽힌다.
            // 스위치를 안 쓴 이유: "위치 고정 [켬/끔]"으로는 켰을 때 **어디** 날씨인지 이름이 안 보인다.
            // 세그먼트를 한 줄 통째로 깔아 둔다 — 제목 옆에 붙이면 글자배율이 커질 때 라벨이 눌린다.
            SectionTitle("날씨")
            var wxFixed by remember { mutableStateOf(settingsPrefs.getBoolean(WX_LOC_FIXED_KEY, false)) }
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("날씨 기준 위치", fontWeight = FontWeight.Bold)
                Text(
                    if (wxFixed) "신정차량기지 날씨만 봅니다 — 위치 권한을 쓰지 않습니다"
                    else "지금 있는 동네 날씨를 봅니다 (위치를 못 받으면 신정차량기지)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    // 저장만 하고 끝 — 달력을 깨우지 않는다. 탭을 옮기면 달력 목적지가 dispose 되고,
                    // 돌아올 때 WeatherChip 이 격자를 다시 읽는다(Weather.kt LaunchedEffect 주석).
                    listOf(false to "현재 위치", true to "신정차량기지").forEachIndexed { i, (v, label) ->
                        SegmentedButton(
                            selected = wxFixed == v,
                            onClick = {
                                wxFixed = v
                                settingsPrefs.edit().putBoolean(WX_LOC_FIXED_KEY, v).apply()
                            },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                        ) { Text(label, fontSize = 11.sp) }
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

            // 알림
            SectionTitle("알림")
            // 권한이 없으면 위 토글을 켜 놔도 브리핑·편승 알람이 걸리지 않는다(v1.6.32).
            // warning = 아예 안 울림(빨강) / notice = 울리긴 하는데 약함(전체화면 알람 꺼짐).
            // 화면에 돌아올 때마다 다시 읽어야 설정에서 켜고 온 것이 바로 반영된다.
            // ⚠ **토글보다 먼저** 읽는다 — 브리핑 스위치가 이 값을 보고 제 상태를 정한다(v1.6.92 ④).
            val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
            var alarmWarn by remember { mutableStateOf(AlarmPermission.warning(ctx)) }
            var alarmNote by remember { mutableStateOf(AlarmPermission.notice(ctx)) }
            LaunchedEffect(Unit) {
                lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
                    alarmWarn = AlarmPermission.warning(ctx)
                    alarmNote = AlarmPermission.notice(ctx)
                }
            }
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
            /*
             * 브리핑 토글 — **권한이 없으면 켜진 채로 두지 않는다**(v1.6.92 ④).
             *
             * 기본값이 `true`인데 안드로이드 14+ 새 설치는 "알람 및 리마인더"가 기본 꺼짐이라,
             * 종전엔 [BriefingAlarm.arm]이 조용히 `cancel`만 하고 돌아갔다 —
             * **한 번도 안 울리는데 토글은 켜짐.** 출근을 놓치게 하는 자리다.
             * 이제 스위치는 저장값이 아니라 **실제로 울릴 수 있는 상태**를 보여 주고,
             * 권한이 없을 때 켜려 하면 저장 대신 그 설정 화면으로 보낸다(아래 안내 줄이 이유를 적는다).
             */
            var briefingPref by remember { mutableStateOf(settingsPrefs.getBoolean("notify_briefing", true)) }
            val briefingOn = briefingPref && alarmWarn == null
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("출근 1시간 전 브리핑", fontWeight = FontWeight.Bold)
                    Text(
                        if (briefingPref && alarmWarn != null)
                            "권한이 없어 꺼져 있습니다 — 아래 \"설정 열기\"로 \"알람 및 리마인더\"를 켜 주세요"
                        else "당일 출근 1시간 전, 근무·출근시각·날씨 요약 알림 (새벽 근무도 그대로 울림)",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (briefingPref && alarmWarn != null) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = briefingOn, onCheckedChange = { on ->
                    // 켜려는데 못 울리는 상태면 저장하지 않고 곧바로 그 설정으로 보낸다.
                    if (on && alarmWarn != null) {
                        AlarmPermission.openSettings(ctx)
                    } else {
                        briefingPref = on
                        settingsPrefs.edit().putBoolean("notify_briefing", on).apply()
                        com.sinjeong.crewcalendar.widget.BriefingAlarm.requestRearm(ctx) // 켜면 등록, 끄면 해제
                    }
                })
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
                        else { afterUnlock = onOpenAdmin; askAdminPw = true }
                    }) { Text("열기") }
                },
            )
            SettingRow(
                title = "주간식단표 올리기",
                sub = "구내식당 표를 한글파일(.hwp/.hwpx)·사진·PDF로 넣으면 21칸을 채웁니다 (암호 필요)",
                trailing = {
                    TextButton(onClick = {
                        if (com.sinjeong.crewcalendar.presentation.admin.AdminGate.unlocked) onOpenMenuAdmin()
                        else { afterUnlock = onOpenMenuAdmin; askAdminPw = true }
                    }) { Text("열기") }
                },
            )
            SettingRow(
                title = "공지 쓰기",
                sub = "달력 맨 위에 전원에게 보이는 공지 (암호 필요)",
                trailing = {
                    TextButton(onClick = {
                        if (com.sinjeong.crewcalendar.presentation.admin.AdminGate.unlocked) onOpenNoticeAdmin()
                        else { afterUnlock = onOpenNoticeAdmin; askAdminPw = true }
                    }) { Text("열기") }
                },
            )

            // 앱 정보 · 문의 · 저작권
            SectionTitle("앱 정보")
            SettingRow(
                title = "앱 버전",
                sub = "눌러서 최신 버전 확인하기",
                onClick = { openPlayStore(ctx) },
                trailing = {
                    Text(
                        "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
            )

            // 내장 시간표 판(다이아 개정 때 `tools/fetch_line2_timetable.py` 로 다시 굽는다).
            // ⚠ 자산이 1.4MB 라 읽기·파싱은 IO 스레드에서 — 본 스레드에서 하면 화면이 멎는다.
            val ttLabel by produceState("") {
                value = withContext(Dispatchers.IO) {
                    Line2TimetableLoader.get(ctx); Line2TimetableLoader.fetchedLabel
                }
            }
            SettingRow(
                title = "열차 시간표",
                sub = "지도·알람의 지연 계산에 쓰는 서울시 역별 시간표",
                trailing = { Text(ttLabel.ifBlank { "없음" } + "판", fontWeight = FontWeight.Bold) },
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
        onUnlocked = { askAdminPw = false; (afterUnlock ?: onOpenAdmin)(); afterUnlock = null },
        onDismiss = { askAdminPw = false; afterUnlock = null },
    )

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("로그아웃") },
            // signOut() 은 이름·사번만 지운다 — 근무기록·메모는 사번과 묶여 있지 않은
            // 기기 저장분이라, 다른 사번으로 로그인해도 같은 기록이 그대로 보인다.
            // 사용자에게 놀라움이 되지 않게 미리 적는다(v1.6.86 점검 #11).
            text = {
                Text(
                    "로그인 정보만 지워집니다. 근무기록·메모는 폰에 남습니다. " +
                        "다른 사번으로 로그인해도 이 기록이 그대로 보입니다.",
                )
            },
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
private fun SettingRow(
    title: String,
    sub: String,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
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
