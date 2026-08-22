package com.sinjeong.crewcalendar.presentation.calendar

import androidx.compose.foundation.BorderStroke
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sinjeong.crewcalendar.R
import com.sinjeong.crewcalendar.domain.model.Bundled
import com.sinjeong.crewcalendar.domain.model.BundledTimetable
import com.sinjeong.crewcalendar.domain.model.CrewGroup
import com.sinjeong.crewcalendar.domain.model.DaySchedule
import com.sinjeong.crewcalendar.domain.model.DutyCode
import com.sinjeong.crewcalendar.domain.model.DutyType
import com.sinjeong.crewcalendar.domain.model.MainLegs
import com.sinjeong.crewcalendar.domain.model.NightCombo
import com.sinjeong.crewcalendar.domain.model.RouteTable
import com.sinjeong.crewcalendar.domain.model.ShiftTeam
import com.sinjeong.crewcalendar.presentation.live.BranchLiveMap
import com.sinjeong.crewcalendar.presentation.roster.changedCorner
import com.sinjeong.crewcalendar.presentation.roster.dutyCellColors
import com.sinjeong.crewcalendar.presentation.settings.openSafetyApp
import com.sinjeong.crewcalendar.presentation.theme.DutyColors
import com.sinjeong.crewcalendar.presentation.theme.LocalDutyColors
import com.sinjeong.crewcalendar.presentation.theme.ThemeMode
import com.sinjeong.crewcalendar.presentation.weather.WeatherCell
import com.sinjeong.crewcalendar.widget.AlarmPermission
import com.sinjeong.crewcalendar.widget.DeadheadAlarm
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * 메인 달력 화면 (시안 v10).
 * 앱바: ‹월› · 휴N개 칩 · 근무선택 칩 · 테마 토글 · 오늘
 * 셀: 근무 칩 + 출근시각 + 메모 (근무변경 시 원래근무/새근무 2줄)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainCalendarScreen(
    onOpenTimetable: () -> Unit = {},
    onOpenDeadhead: () -> Unit = {},
    viewModel: MainCalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeController.mode.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var fullTimetable by remember { mutableStateOf<Pair<String, String>?>(null) }  // (asset, title)
    // 폭 600dp 이상(폴드 펼침·태블릿) = 좌우 2패널. 그 미만은 기존 바텀시트 그대로
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    // 펼침 전용 선택 날짜 — 접었다 펴도 살아남게 epochDay(Long)로 저장
    var panelEpochDay by rememberSaveable { mutableStateOf<Long?>(null) }
    // 펼침 기본 선택 = 오늘(이번 달일 때만). 다른 달이면 null → "날짜를 선택하세요"
    val detailDate = if (wide)
        panelEpochDay?.let(LocalDate::ofEpochDay)
            ?: LocalDate.now().takeIf { YearMonth.from(it) == state.month }
    else state.selectedDate

    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); viewModel.dismissError() }
    }
    // 접힘→펼침 전환: 열려 있던 시트의 날짜를 오른쪽 패널로 인계
    LaunchedEffect(wide) {
        if (wide) state.selectedDate?.let { panelEpochDay = it.toEpochDay(); viewModel.selectDate(null) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            // 컴팩트 헤더(기본 TopAppBar 64dp → 40dp) — 남는 상단 공간을 달력에 양보
            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(
                    // 상단 공백 제거 — 상태바와 겹쳐도 됨(사용자 요청). statusBarsPadding 미적용
                    Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 10.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        state.month.format(DateTimeFormatter.ofPattern("M월")),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false,
                    )
                    // 휴무 칩은 "8월" 바로 옆 — 월과 같이 읽는 정보라 오른쪽 버튼 무리에서 떼어냈다(v1.6.17)
                    Spacer(Modifier.width(6.dp))
                    RestCountChip(state.restDayCount)
                    Spacer(Modifier.weight(1f))
                    FilledTonalButton(
                        onClick = { viewModel.openDutyPicker(LocalDate.now()) },
                        contentPadding = PaddingValues(horizontal = 6.dp),
                        modifier = Modifier.height(28.dp),
                    ) { Text("근무선택", fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold) }
                    // 아이콘 3종은 반대로 한 단계 키웠다(32→36dp, 아이콘 18→21dp) — 44dp 헤더에 아직 여유가 있다
                    IconButton(
                        onClick = { viewModel.toggleTheme(isDark) },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                            "다크/라이트 전환",
                            modifier = Modifier.size(21.dp),
                        )
                    }
                    IconButton(
                        onClick = {
                            runCatching {
                                val uri = com.sinjeong.crewcalendar.util.renderMonthImage(
                                    context, state.month, state.days, state.user?.name ?: "내",
                                )
                                com.sinjeong.crewcalendar.util.shareMonthImage(context, uri)
                            }
                        },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(Icons.Default.Share, "근무표 공유", modifier = Modifier.size(21.dp))
                    }
                    // "오늘로" 버튼 제거(v1.6.11 사용자 요청). ViewModel.goToday는 남겨둠 — 되돌리기 쉽게
                    IconButton(
                        onClick = { openSafetyApp(context) },
                        modifier = Modifier.size(36.dp),
                    ) {
                        // 벡터 아이콘 → 안전앱 실제 런처 아이콘(mascot). 원형 클립으로 런처처럼 보이게
                        Image(
                            painterResource(R.drawable.ic_safety_app),
                            "슬기로운 승무생활",
                            modifier = Modifier.size(26.dp).clip(CircleShape),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Row(Modifier.padding(padding).fillMaxSize()) {
            // 펼침 비율 50:50 — "폴더 펼쳤을 때 화면 반반"(v1.6.11 사용자 선택). v1.6.10은 38:62
            Column(Modifier.weight(if (wide) 0.5f else 1f).fillMaxHeight()) {
                WeekdayHeader()

                if (state.isLoading) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    CalendarGrid(
                        month = state.month,
                        days = state.days,
                        selected = detailDate,
                        onSelect = { if (wide) panelEpochDay = it.toEpochDay() else viewModel.selectDate(it) },
                        onSwipeMonth = viewModel::moveMonth,
                        onOpenTimetable = { fullTimetable = "tt_work" to "근무시각표" },
                        onOpenDeadhead = { fullTimetable = "tt_deadhead" to "편승시각표" },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            // 펼침: 오른쪽 상세 패널 — 바텀시트와 같은 내용(DayDetailContent) 재사용
            if (wide) Surface(
                Modifier.weight(0.5f).fillMaxHeight(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            ) {
                val day = detailDate?.let { d -> state.days.firstOrNull { it.date == d } }
                // 접힘 바텀시트와 같은 규칙 — 키보드가 떠 있는 동안 스크롤을 바닥에 붙여
                // 메모와 버튼 줄이 함께 키보드 바로 위에 오게 한다.
                val panelScroll = rememberScrollState()
                val panelImeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
                LaunchedEffect(panelImeOpen, day?.date) {
                    if (panelImeOpen) snapshotFlow { panelScroll.maxValue }.collect { panelScroll.scrollTo(it) }
                }
                if (day == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("날짜를 선택하세요", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    DayDetailContent(
                        day = day,
                        onSaveMemo = { viewModel.saveMemo(day.date, it) },
                        onChangeDuty = { viewModel.openDutyChange(day.date) },
                        onRevert = { viewModel.changeDuty(day.date, null) },
                        onClose = { panelEpochDay = null },
                        compact = false,
                        // imePadding()이 verticalScroll()보다 앞 — 키보드만큼 스크롤 뷰포트가 줄어야
                        // 메모 TextField의 bringIntoView가 보이는 영역으로 스크롤한다.
                        //
                        // ⚠ **`fillMaxSize`가 아니라 `fillMaxWidth`다**(v1.6.28에 고침). `fillMaxSize`면
                        // 내용 Column이 뷰포트 높이에 **딱 맞춰져** `maxValue`가 0이 되고, 그러면
                        // 스크롤이 아예 안 먹는다. 키보드가 떠서 뷰포트가 줄면 넘친 버튼 줄이
                        // 잘려 나가 **스크롤로도 못 가는 상태**가 됐다(펼침 실측).
                        // 높이를 감싸게 두면 내용이 뷰포트를 넘을 때 정상적으로 스크롤된다.
                        modifier = Modifier.fillMaxWidth().imePadding()
                            .verticalScroll(panelScroll).padding(top = 14.dp),
                    )
                }
            }
        }
    }

    // 접힘: 날짜 탭 → 상세 시트 (출근시간·전반/후반사업·근무시간·메모·근무변경)
    if (!wide) state.selectedDate?.let { date ->
        state.days.firstOrNull { it.date == date }?.let { day ->
            // 남은 이슈 6번(저장 버튼 아래 빈 공간) 해결 — v1.6.28.
            //
            // v1.6.12 우회책은 두 가지였다: ① 스크롤 안쪽 끝에 키보드 높이만큼 패딩,
            // ② 키보드가 떠 있는 동안 스크롤을 바닥에 고정. 당시엔 시트 윈도우가 키보드에
            // 안 줄어서 둘 다 필요했다. **지금은 시트가 스스로 줄어든다**(에뮬 실측, API 36).
            // 그래서 ①이 이중보정이 되어 버튼 아래에 정확히 키보드 높이만큼(실측 ~950px)
            // 빈 공간을 만들고 있었다.
            //
            // → **①만 걷어내고 ②는 남긴다.** ②까지 빼면 메모 TextField의 bringIntoView가
            //   메모칸만 보이는 데까지 스크롤해서 그 아래 버튼 줄(근무선택·삭제·취소·저장)이
            //   행로표가 있는 긴 시트에서 키보드에 가린다(실측 확인 — 한 번 더 밀어야 나왔다).
            //   바닥고정이면 내용 맨 끝 = 버튼 줄이 키보드 바로 위에 붙어 메모·버튼이 함께 보인다.
            // 시트는 처음부터 완전히 펼쳐 연다 — 50% 부분전개면 커진 행로표가 화면을 다 먹어 메모가 안 보인다.
            ModalBottomSheet(
                onDismissRequest = { viewModel.selectDate(null) },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            ) {
                val scroll = rememberScrollState()
                val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
                LaunchedEffect(imeOpen) {
                    if (imeOpen) snapshotFlow { scroll.maxValue }.collect { scroll.scrollTo(it) }
                }
                DayDetailContent(
                    day = day,
                    onSaveMemo = { viewModel.saveMemo(date, it) },
                    onChangeDuty = { viewModel.openDutyChange(date) },
                    onRevert = { viewModel.changeDuty(date, null) },
                    onClose = { viewModel.selectDate(null) },
                    modifier = Modifier.verticalScroll(scroll),
                )
            }
        }
    }

    // 근무선택: ① 소속 → ② 근무
    state.picker?.let { picker ->
        DutyPickerSheet(
            picker = picker,
            currentGroup = state.currentGroup,
            currentOffset = state.user?.patternOffset ?: 0,
            onPickGroup = viewModel::pickGroup,
            onBack = viewModel::backToGroupStep,
            onPick = viewModel::confirmDutyPosition,
            onDismiss = viewModel::closeDutyPicker,
        )
    }

    // 근무변경: 이 날짜 하루만
    state.changeDate?.let { date ->
        state.days.firstOrNull { it.date == date }?.let { day ->
            DutyChangeSheet(
                day = day,
                onChange = { viewModel.changeDuty(date, it) },
                onRevert = { viewModel.changeDuty(date, null) },
                onDismiss = viewModel::closeDutyChange,
            )
        }
    }

    fullTimetable?.let { (asset, title) ->
        RouteImageDialog(asset = asset, title = title, onDismiss = { fullTimetable = null })
    }
}

/* ── 달력 위 오늘 카드 = **v1.6.45에서 제거**(사용자: "내일 다이아 출근 헤드 위쪽에 있는거 없어도
      될듯 중복이야..") ─────────────────────────────────
   요일 헤더 바로 위에 있던 한 줄 카드(`오늘 · 9 다이아 · 출근 7:02` + 편승 알람 칩)다. 달력 칸이
   이미 다이아·출근시각을 보여줘서 같은 정보가 두 번 나왔다. 편승 알람 칩은 상세시트에 그대로 있다.
   되살리려면 `git show da7cacf` — `TodayCard`/`todayLine` 컴포저블과 `MainCalendarViewModel.cardDays`,
   그리고 여기서 호출 한 줄이면 된다(달력이 44dp 다시 좁아진다). `focusDate`는 위젯이 계속 쓴다. */

/* ── 앱바 휴일갯수 칩 (작고 옅게) ─────────────────────── */
@Composable
private fun RestCountChip(count: Int) {
    val duty = LocalDutyColors.current
    Surface(
        color = duty.rest.copy(alpha = 0.45f),
        contentColor = duty.onRest.copy(alpha = 0.8f),
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            "휴 ${count}개",
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            fontSize = 9.5.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, softWrap = false,
        )
    }
}

/* ── 요일 헤더 ────────────────────────────────────────── */
@Composable
private fun WeekdayHeader() {
    val duty = LocalDutyColors.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        listOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY).forEach { dow ->
            Text(
                dow.getDisplayName(TextStyle.SHORT, Locale.KOREAN),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = when (dow) {
                    DayOfWeek.SUNDAY -> duty.sunday
                    DayOfWeek.SATURDAY -> duty.saturday
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/* ── 달력 그리드 ──────────────────────────────────────── */
@Composable
private fun CalendarGrid(
    month: YearMonth,
    days: List<DaySchedule>,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
    onSwipeMonth: (Long) -> Unit,
    onOpenTimetable: () -> Unit,
    onOpenDeadhead: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val leading = month.atDay(1).dayOfWeek.value % 7
    val cells0: List<DaySchedule?> = List(leading) { null } + days
    val trailing = (7 - (cells0.size % 7)) % 7
    val cells: List<DaySchedule?> = cells0 + List(trailing) { null }
    // 빈 칸(null) 처음 2개 = 근무시각표 / 편승시각표 카드, 세 번째 = 현재 날씨(v1.6.36).
    // 빈 칸이 두 칸뿐인 달(1일이 화요일 등)엔 날씨가 자리를 못 얻어 안 뜬다 — 시각표 카드도
    // 원래 그런 규칙이라 새로 생긴 제약이 아니고, 못 그릴 땐 조용히 사라지는 게 이 기능의 원칙이다.
    val nullIdx = cells.indices.filter { cells[it] == null }
    val card1 = nullIdx.getOrNull(0)
    val card2 = nullIdx.getOrNull(1)
    val card3 = nullIdx.getOrNull(2)
    val duty = LocalDutyColors.current
    var dragX by remember { mutableFloatStateOf(0f) }
    val rows = (cells.size + 6) / 7

    // 칸 높이 = 남은 화면을 주 수로 나눔 → 폰마다 최대 크기로 자동 최적화
    BoxWithConstraints(modifier) {
        // 하한 60dp: 폴드 펼침 등 낮은 화면에서도 마지막 주가 짤리지 않게
        val cellHeight = ((maxHeight - (3.dp * (rows - 1))) / rows).coerceIn(60.dp, 150.dp)
        // 칸 폭 = 가용폭 − 좌우 8dp − 칸 사이 3dp×6. 접힘 폰 ~54dp(411dp) / 펼침 50% 달력 ~44dp.
        // 임계 60dp(v1.6.10) → 34dp(v1.6.11): 사용자가 이름을 날짜 "옆 같은 줄"에 원함.
        // 날짜 6sp + 알약 여백 축소 + HolidayTag 하한 4.5sp면 34dp 칸에도 `광복절`이 들어간다.
        // 34dp 미만(계산상 실기기엔 없음)만 아랫줄 폴백으로 남겨둔다.
        val nameBelow = (maxWidth - 8.dp * 2 - 3.dp * 6) / 7 < 34.dp
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier
                .padding(horizontal = 8.dp)
                // 손가락 스와이프로 월 이동 (좌←다음달, 우→이전달)
                .pointerInput(month) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragX = 0f },
                        onHorizontalDrag = { _, delta -> dragX += delta },
                        onDragEnd = {
                            if (dragX < -120f) onSwipeMonth(1)
                            else if (dragX > 120f) onSwipeMonth(-1)
                        },
                    )
                },
            verticalArrangement = Arrangement.spacedBy(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            // 평소엔 스크롤 끔 — 한 달이 한 화면에 다 보여야 하고, 세로 스크롤이 켜져 있으면
            // 월 이동 스와이프가 흔들린다. 다만 칸 하한 60dp 때문에 세로가 짧은 화면
            // (가로화면·낮은 창)에선 마지막 주가 잘려 23~31일에 아예 손이 안 닿았다.
            // 넘칠 때만 켠다 — 안 넘치는 기존 화면들은 동작이 그대로다.
            userScrollEnabled = cellHeight * rows + 3.dp * (rows - 1) > maxHeight,
        ) {
            items(cells.size, key = { it }) { i ->
                val day = cells[i]
                when (i) {
                    card1 -> TimetableCard("근무시각표", R.drawable.ic_tt_work, onOpenTimetable, cellHeight, duty.main, duty.onMain)
                    card2 -> TimetableCard("편승시각표", R.drawable.ic_tt_deadhead, onOpenDeadhead, cellHeight, duty.branch, duty.onBranch)
                    card3 -> WeatherCell(cellHeight)
                    else -> if (day == null) Spacer(Modifier.height(cellHeight))
                    else DayCell(
                        day, isSelected = day.date == selected, height = cellHeight,
                        big = cellHeight >= 100.dp, // 펼침 화면 등 칸이 크면 글자도 키움
                        nameBelow = nameBelow,
                        onClick = { onSelect(day.date) },
                    )
                }
            }
        }
    }
}

/* ── 빈 칸에 들어가는 시각표 바로가기 카드 ─────────────── */
@Composable
private fun TimetableCard(
    label: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    height: Dp,
    bg: Color,
    fg: Color,
) {
    // "근무시각표" → "근무\n시각표" 의도적 2줄 — 좁은 칸에서 어색한 중간 줄바꿈 방지 + 큰 글씨
    val twoLine = if (label.length >= 5) label.substring(0, 2) + "\n" + label.substring(2) else label
    Column(
        Modifier
            .height(height)
            .clip(RoundedCornerShape(10.dp))
            .background(bg.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // ⚠ Icon이 아니라 Image — tint가 파스텔 색을 통째로 지운다(날씨·편승 아이콘과 같은 함정).
        // 아이콘 뜻은 카드 글자가 이미 말하므로 contentDescription은 null(중복 낭독 방지).
        Image(painterResource(icon), null, Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        // 칸이 좁거나 시스템 글꼴 확대 시 가로로 짤리지 않게 자동 축소 (DayCell 다이아 칩과 같은 방식)
        var fitSize by remember(twoLine, height) { mutableStateOf(12.5.sp) }
        Text(
            twoLine,
            fontSize = fitSize, lineHeight = fitSize * 1.28,
            fontWeight = FontWeight.ExtraBold, color = fg, textAlign = TextAlign.Center,
            softWrap = false,
            onTextLayout = { if (it.hasVisualOverflow && fitSize > 7.sp) fitSize *= 0.92f },
        )
    }
}

/**
 * 공휴일·기념일·절기 이름. 주어진 폭을 넘치면 들어갈 때까지 자동 축소(다이아 칩과 같은 방식) —
 * 접힘 칸에서 `광복절`이 `광…`으로 잘리던 문제(v1.6.8). 최소 크기에서도 안 들어갈 때만 말줄임.
 */
@Composable
private fun HolidayTag(name: String, size: TextUnit, color: Color, modifier: Modifier) {
    var fit by remember(name, size) { mutableStateOf(size) }
    Text(
        name, fontSize = fit, lineHeight = fit * 1.1, maxLines = 1,
        softWrap = false, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End,
        color = color, modifier = modifier,
        // hasVisualOverflow만 보면 안 된다(v1.6.11): 말줄임이 적용된 순간 "한 줄에 들어갔다"가 돼
        // false로 떨어지고 축소가 멈춘다 — `광복절`이 6.5sp에서 `광복…`으로 굳던 원인.
        // isLineEllipsized로 말줄임 자체를 감지해 안 잘릴 때까지 줄인다.
        onTextLayout = {
            val cut = it.hasVisualOverflow || (it.lineCount > 0 && it.isLineEllipsized(0))
            if (cut && fit > 4.5.sp) fit *= 0.92f
        },
    )
}

@Composable
private fun DayCell(
    day: DaySchedule,
    isSelected: Boolean,
    height: Dp,
    big: Boolean,
    nameBelow: Boolean,
    onClick: () -> Unit,
) {
    val duty = LocalDutyColors.current
    val isToday = day.date == LocalDate.now()
    val (chipBg, chipFg) = dutyCellColors(day.duty.colorType, duty, MaterialTheme.colorScheme.onSurfaceVariant)
    // big = 칸이 넉넉할 때(≥100dp) 전체 폰트 한 단계 확대
    // 날짜 숫자는 공휴일 이름·근무 칩에 폭을 양보하려고 작게(v1.6.8 7.5→7, v1.6.10 7→6.5, v1.6.11 6.5→6sp)
    val dateSize = if (big) 8.sp else 6.sp
    val holSize = if (big) 8.sp else 6.5.sp
    val chipSizeBig = if (big) 13.sp else 11.5.sp
    val chipSizeSmall = if (big) 11.5.sp else 10.sp
    val signOnSize = if (big) 8.sp else 7.sp
    val memoSize = if (big) 9.5.sp else 8.sp

    Column(
        Modifier
            .height(height)
            .clip(RoundedCornerShape(10.dp))
            .then(
                when {
                    // 오늘: 칸 바탕을 강조색으로 물들이되(v1.6.24 요청) **꽉 찬 primaryContainer가 아니라
                    // 알파 0.10 얹기**로 바꿨다(v1.6.41). 강조색이 2호선 그린으로 고정되면서
                    // `primaryContainer`가 **주간 근무색과 정확히 같은 값**이 됐고(다크 #005229 동일),
                    // 오늘이 주간 다이아인 날 칩이 바탕에 통째로 묻혀 사라졌다(에뮬 실측).
                    // 알파 얹기는 바탕이 칩보다 늘 어둡게(다크)/밝게(라이트) 남아 칩 대비가 다른 칸과 같다.
                    // 오늘 표시는 2.5dp 테두리 + 꽉 찬 날짜 배지 + 달력 위 오늘 카드가 함께 진다.
                    isToday -> Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                        .border(2.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                    isSelected -> Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                    // 칸 구분: 희미한 라운드 사각형
                    else -> Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                            RoundedCornerShape(10.dp),
                        )
                }
            )
            // 근무변경된 날: 오른쪽 아래 모서리 접힘. 폭·높이 비용 0dp(칸 위에 겹쳐 그린다)
            .then(
                if (day.isOverridden)
                    Modifier.changedCorner(MaterialTheme.colorScheme.primary, if (big) 12.dp else 10.dp)
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(vertical = 3.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        // 날짜 줄: 공휴일이면 숫자 빨강, 이름 표시 (기념일은 이름만 빨강)
        val nameTag = day.holidayName ?: day.memorialName ?: day.seasonalTerm
        val nameColor = if (day.holidayName != null || day.memorialName != null) duty.sunday else duty.onStandby
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${day.date.dayOfMonth}",
                fontSize = dateSize, lineHeight = dateSize * 1.05,
                fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.SemiBold,
                color = when {
                    // 오늘은 숫자를 꽉 찬 primary 배지로 — 구글 캘린더식 "오늘 점"
                    isToday -> MaterialTheme.colorScheme.onPrimary
                    day.holidayName != null || day.date.dayOfWeek == DayOfWeek.SUNDAY -> duty.sunday
                    day.date.dayOfWeek == DayOfWeek.SATURDAY -> duty.saturday
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                // 날짜 숫자 뒤 아주 옅은 사각형 — onSurface 알파라 라이트/다크 자동 대응
                // 여백 축소(v1.6.11 start 2→1, horizontal 2→1): 같은 줄 공휴일 이름에 폭 양보
                modifier = Modifier
                    .padding(start = 1.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (isToday) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
                    )
                    .padding(horizontal = if (isToday) 3.dp else 1.dp, vertical = 1.dp),
            )
            // 이름은 날짜 옆 같은 줄에 붙인다(v1.6.11 사용자 요청). 넘치면 HolidayTag가 4.5sp까지 자동 축소
            if (!nameBelow) nameTag?.let {
                HolidayTag(it, holSize, nameColor, Modifier.weight(1f).padding(start = 1.dp, end = 1.dp))
            }
        }
        // 좁은 칸(펼침 44% 달력 ~37dp)은 아랫줄에서 칸 전체 폭을 쓴다 — 세로는 남아돌아 아래를 안 민다
        if (nameBelow) nameTag?.let {
            HolidayTag(it, holSize, nameColor, Modifier.fillMaxWidth().padding(horizontal = 2.dp))
        }
        // 근무변경된 날: 원래 근무 작게(취소선) + 새 근무 2줄
        if (day.isOverridden && day.originalDutyRaw != null) {
            Text(
                DutyCode.parse(day.originalDutyRaw).display,
                fontSize = 8.5.sp, fontWeight = FontWeight.Bold,
                textDecoration = TextDecoration.LineThrough,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                maxLines = 1,
            )
        }
        if (day.duty.raw.isNotBlank()) Box {
            Surface(color = chipBg, contentColor = chipFg, shape = RoundedCornerShape(7.dp)) {
                // 칩 폭 통일(글자수 무관 동일) — 높이는 글자에 맞춰(시스템 글꼴 확대 시 짤림 방지)
                // 충당 계열만 두 줄(`대기충당`⏎`지2`)로 온다 — DutyCode.gridLabel 참고.
                val lines = day.duty.gridLabel.split('\n')
                val two = lines.size > 1
                val dens = LocalDensity.current
                // 두 줄 칩은 칸에서 **줄 하나를 더 먹는다**. 시스템 글꼴을 키우면 그만큼 아래
                // 출근시각이 칸 밖으로 밀려 잘렸다(fontScale 1.5 실측: `7:47`이 반토막).
                // 지도([BranchLiveMap])와 같은 처방 — **이 칩 안에서만** 글자배율에 상한을 둔다.
                // dp 배율(density)은 손대지 않아 칸·알약 크기는 그대로고, 큰 글꼴 사용자도
                // 1.15배까지는 커진 글자를 본다. 한 줄 칩(대다수)은 종전대로 시스템 배율을 따른다.
                CompositionLocalProvider(
                    LocalDensity provides
                        if (two) Density(dens.density, dens.fontScale.coerceAtMost(1.15f)) else dens
                ) {
                Box(
                    // 두 줄은 세로 여백을 절반으로 — 줄이 하나 늘어난 만큼 아껴야 한다
                    Modifier.width(if (big) 42.dp else 34.dp).padding(vertical = if (two) 1.dp else 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        lines.forEachIndexed { i, label ->
                            // 두 줄일 때 윗줄은 `대기충당`류 접두어라 작게 깔고, **아랫줄 다이아를 크게** 준다.
                            // 승무원이 칸에서 실제로 읽어야 하는 건 대신 뛰는 다이아 번호다(v1.6.46).
                            val prefix = two && i == 0
                            val baseSize = when {
                                prefix -> chipSizeSmall * 0.7f
                                label.length >= 3 -> chipSizeSmall
                                else -> chipSizeBig
                            }
                            // 다이아 텍스트 자동 맞춤: 칩 폭을 넘치면 들어갈 때까지 축소 (시스템 글꼴 확대에도 안 짤림)
                            var fitSize by remember(label, big) { mutableStateOf(baseSize) }
                            Text(
                                label,
                                fontSize = fitSize,
                                // ⚠ 1.0 밑으로는 내리지 말 것 — 한글 받침이 줄상자에 잘린다.
                                lineHeight = fitSize * if (two) 1.05 else 1.15,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1, softWrap = false,
                                onTextLayout = { if (it.hasVisualOverflow && fitSize > 7.sp) fitSize *= 0.92f },
                            )
                        }
                    }
                }
                }
            }
            // 야간 근무(본선/지선 야간 다이아)에만 노란 초승달 배지 — 다이아 왼쪽 위 모서리에 걸침.
            // SPECIAL은 같은 보라색이지만 isNight=false라 자동 제외.
            // 날짜 줄이 아니라 칩 위에 오프셋으로 얹는다 — 날짜·공휴일 이름 폭을 한 픽셀도 안 뺏는다.
            if (day.duty.isNight) Icon(
                Icons.Default.DarkMode, null,
                // 라이트/다크 모두 보이는 노랑 — 배경 밝기로 고름(다이나믹 컬러에도 대응)
                tint = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f)
                    Color(0xFFE09600) else Color(0xFFFFD54F),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (-4).dp, y = (-2).dp)
                    .size(if (big) 11.dp else 9.dp),
            )
        }
        if (day.signOn != null) {
            Text(
                day.signOn, fontSize = signOnSize, lineHeight = signOnSize * 1.06, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (day.duty.isWorkDay && day.duty.number != null && Bundled.isHolidayTimetable(day.date)) {
            // 휴일 운휴 다이아(본선 주간 26~29) — 시각이 아예 없어 칸이 비어 보이던 자리를 채운다
            Text(
                "운휴", fontSize = signOnSize, lineHeight = signOnSize * 1.06, fontWeight = FontWeight.Bold,
                color = duty.sunday,
            )
        }
        // 메모는 항상 한 줄 보이게 (칸이 작아도 잘리지 않도록 축소)
        if (day.memo.isNotBlank()) {
            Text(
                day.memo, fontSize = memoSize, lineHeight = memoSize * 1.06,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/* ── 날짜 상세 내용 (기존 앱 형식: 출근시간/전반사업/후반사업/근무시간)
      접힘 = ModalBottomSheet 안, 펼침 = 오른쪽 패널 안. 컨테이너만 다르고 내용은 동일 ── */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayDetailContent(
    day: DaySchedule,
    onSaveMemo: (String) -> Unit,
    onChangeDuty: () -> Unit,
    onRevert: () -> Unit,
    onClose: () -> Unit,
    compact: Boolean = true,   // true=접힘 바텀시트(기존 그대로), false=펼침 오른쪽 패널
    modifier: Modifier = Modifier,
) {
    val duty = LocalDutyColors.current
    var memo by remember(day.date) { mutableStateOf(day.memo) }
    val row = Bundled.timeRowFor(day.duty, day.date)
    val combo = if (day.duty.isNight) Bundled.comboOf(day.date) else null

    Column(
        modifier.padding(horizontal = if (compact) 20.dp else 10.dp).padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
            // 펼침 패널은 이 줄 자체를 생략 — 행로표에 폭/높이를 몰아준다
            //
            // v1.6.45: 근무칩("1 Dia")·지선칩을 뺐다(사용자: *"근무선택해서 들어가면 헤드 정보는
            // 없어도 될듯? 1  dia 지선 <- 이런거.."*). 달력 칸에서 그 칸을 눌러 들어온 것이라
            // 어느 다이아인지는 이미 알고 온다. 칩 둘이 빠지면서 남은 `근무변경`을 여기 날짜 줄
            // 오른쪽으로 합쳐 한 줄을 통째로 아꼈다.
            if (compact) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            day.date.format(DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)),
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                        )
                        (day.holidayName ?: day.memorialName)?.let {
                            Text(it, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold,
                                color = duty.sunday, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        day.seasonalTerm?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = duty.onStandby,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Surface(
                        onClick = onChangeDuty,
                        color = duty.standby, contentColor = duty.onStandby,
                        shape = RoundedCornerShape(999.dp),
                    ) {
                        Text("근무변경", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
                    }
                }
            }
            if (day.isOverridden && day.originalDutyRaw != null) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(10.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "✱ 수동변경됨 · 패턴값: ${DutyCode.parse(day.originalDutyRaw).display}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onRevert) { Text("되돌리기", fontSize = 12.sp) }
                    }
                }
            }
            // 배치 확정: "전반사업 07:18~10:33 / └열번 xxxx" — 시각은 시각표, 열번은 행로표
            val holiday = Bundled.isHolidayTimetable(day.date)
            val isNight = day.duty.type == DutyType.MAIN_NIGHT
            // 대기(대1~13)는 번호가 본선 교번과 겹친다 — 타입까지 봐야 `대3`이 `3` 다이아의
            // 사업시각·열번을 끌어오지 않는다 (v1.6.27에서 잡힌 표시 오류)
            val isMain = day.duty.type == DutyType.MAIN_DAY || isNight
            val mainLegs = day.duty.number?.takeIf { isMain }?.let { n ->
                if (isNight) combo?.let { MainLegs.forNight(n, it) } else MainLegs.forDay(n, holiday)
            }
            val trains = day.duty.number?.let { n ->
                when {
                    day.duty.isBranch && day.duty.type != DutyType.BRANCH_STANDBY -> RouteTable.forBranch(n, holiday)
                    day.duty.isBranch -> null
                    isNight -> combo?.let { RouteTable.forMainNight(n, it) }
                    isMain -> RouteTable.forMainDay(n, holiday)
                    else -> null
                }
            }
            val branchLegs = if (day.duty.isBranch) row?.let { r ->
                r.firstLeg?.let { f -> r.secondLeg?.let { s -> f to s } }
            } else null
            fun fmtLeg(t: String) = t.replace('#', '~').replace('-', '~').replace("▼", " ▼")

            // 행로표 원본 (본선 + 지선)
            val routeAsset = day.duty.number?.let { n ->
                when {
                    // 지선 주간 지1~8 (bwd/bhol) + 야간 지10~14 (당일 휴일=bnhol, 평일=bnwd)
                    // 야간 표는 시각이 전부 동일하고 종료편성(지N)만 조합별로 달라 당일 기준 2종이면 충분
                    day.duty.isBranch -> when {
                        day.duty.type == DutyType.BRANCH && n in 1..8 ->
                            if (holiday) "bhol_$n" else "bwd_$n"
                        day.duty.type == DutyType.BRANCH_NIGHT && n in 10..14 ->
                            if (holiday) "bnhol_$n" else "bnwd_$n"
                        else -> null
                    }
                    day.duty.type == DutyType.MAIN_NIGHT -> combo?.let { c ->
                        val tag = when (c) {
                            NightCombo.PP -> "pp"; NightCombo.PH -> "ph"
                            NightCombo.HP -> "hp"; NightCombo.HH -> "hh"
                        }
                        "${tag}_$n"
                    }
                    day.duty.type == DutyType.MAIN_DAY ->
                        if (holiday) { if (n <= 25) "hol_$n" else null } else "wd_$n"
                    else -> null
                }
            }
            // 전체화면 행로표는 인라인 표를 탭했을 때만 연다(날짜 탭 자동 오픈은 되돌림).
            // 키를 날짜로 둬서 ① 닫으면 리컴포지션에도 다시 안 열리고 ② 날짜를 바꾸면 닫힌 채 시작.
            var showRoute by remember(day.date) { mutableStateOf(false) }
            // 사업시각이 없는 근무(대기 대1~13·지대 / **운휴대기 = 휴휴 33·34·35**)는 출근·종료만 적는다.
            // 행로표가 있어도 여기서 같이 보여 준다 — `hh_33`~`hh_35` 스캔은 "운휴대기"라고만 적혀 있고
            // 시각이 한 줄도 없어서, 종전엔 이 조합만 상세시트에 출근시각이 아예 안 떴다(v1.6.36 ④).
            // 시각 자체는 [Bundled.MAIN_NIGHT]의 휴휴값 17:00 / 18:00 / 19:00 = 대기 대11~13과 같은 값이다.
            if (mainLegs == null && branchLegs == null) row?.let { r ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    KvRow("출근", r.signOn)
                    KvRow("종료", (if (r.overnight) "익일 " else "") + r.signOff)
                }
            }
            if (routeAsset != null) {
                // 좌우 여백을 이미지에만 되밀어 (접힘 20→5dp, 펼침 10→3dp) 행로표를 최대로.
                // 접힘은 폭을 키워 세로를 벌고 넘치는 폭은 가로 스크롤 — 펼침은 이미 커서 1f 유지.
                // 본선 원본이 ~2.1:1로 지선(~1.41:1)보다 납작해 같은 배율이면 세로가 덜 커진다 → 본선만 1.8배(v1.6.8)
                val zoom = if (!compact) 1f else if (day.duty.isBranch) 1.5f else 1.8f
                // 펼침은 폭을 더 못 키우니(가로 스크롤 나면 "한눈에"가 깨짐) 세로만 1.4배 늘려 줄 높이를 번다(v1.6.10)
                RouteImageInline(
                    routeAsset,
                    bleed = if (compact) 15.dp else 10.dp,
                    zoom = zoom,
                    vStretch = if (compact) 1f else 1.4f,
                ) { showRoute = true }
            } else {
                if (row != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        when {
                            mainLegs != null -> {
                                KvRow("전반사업", "${mainLegs[0]}~${mainLegs[1]}")
                                KvRow("└ 열번", trains?.firstHalf ?: "—", sub = true)
                                KvRow("후반사업", "${mainLegs[2]}~${mainLegs[3]}" + if (isNight) " (익일)" else "")
                                KvRow("└ 열번", trains?.secondHalf ?: "—", sub = true)
                                trains?.let { KvRow("총근무시간", it.totalWorkTime) }
                            }
                            branchLegs != null -> {
                                KvRow("전반사업", fmtLeg(branchLegs.first))
                                KvRow("└ 열번", trains?.firstHalf ?: "—", sub = true)
                                KvRow("후반사업", fmtLeg(branchLegs.second))
                                KvRow("└ 열번", trains?.secondHalf ?: "—", sub = true)
                                trains?.let { KvRow("총근무시간", it.totalWorkTime) }
                            }
                            // 사업시각이 없는 근무(출근·종료만)는 위에서 이미 그렸다
                        }
                    }
                }
                // 출근시각·행로표·열번이 모두 없는 다이아 = 그날 그 다이아 운행이 없음
                // (본선 주간 26~29는 휴일 시각표에 아예 없다 — 종전엔 시트가 텅 빈 채 쪼그라들었다)
                if (row == null && day.duty.number != null && day.duty.isWorkDay) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                if (holiday) "휴일 운휴 · 근무 없음" else "운휴 · 근무 없음",
                                fontWeight = FontWeight.ExtraBold, fontSize = 15.sp,
                                color = duty.sunday,
                            )
                            Text(
                                "${day.duty.display} 다이아는 " +
                                    (if (holiday) "휴일에 " else "") +
                                    "운행하지 않습니다. 실제 근무는 사업소 게시 근무표를 확인하세요.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            // 실시간 신정지선 열차 지도 — **오늘 상세시트에만** (v1.6.43).
            //
            // 편승지키미(SinjeongShuttle2)의 노선도를 코드째 이식한 것이다(앱 실행 연결이 아니라
            // 이식이라 편승지키미가 안 깔려 있어도 뜬다). 배치를 행로표 바로 밑으로 잡은 이유:
            // 행로표에서 내 열번을 확인한 직후 그 열차가 지금 어디쯤인지를 같은 화면에서 잇는다.
            //
            // ⚠ **달력 그리드에는 절대 넣지 않는다.** 그 화면은 앱이 켜 있는 내내 떠 있어서
            //   5초 폴링이 배터리와 일 1,000회 API 한도를 그대로 태운다. 상세시트는 열었다
            //   닫는 화면이라 [BranchLiveMap]의 LaunchedEffect가 닫힘과 함께 취소된다 —
            //   소모 범위가 "시트가 열려 있는 동안"으로 갇히는 게 이 배치의 핵심이다.
            //
            // 오늘이 아닌 날짜에선 아예 컴포지션에 들어가지 않는다(과거·미래 날짜에 실시간
            // 위치는 의미가 없고, 그날 시트를 열 때마다 API를 태울 이유도 없다).
            if (day.date == LocalDate.now()) BranchLiveMap()
            // 출근 알람 — 시각이 있는 근무(본선·지선·대기)에만 띄운다. 계산은 BundledTimetable.advise.
            // 후반 칩은 후반사업이 실제로 있는 근무(본선 다이아 / 지선 사업시각)에만 붙인다 —
            // 대기 근무에 "알람 없음" 칩이 두 개 겹쳐 뜨는 걸 막는다.
            if (row != null) {
                val hasSecond = mainLegs != null || branchLegs != null
                // v1.6.42 ③ — 오른쪽 정렬(Alignment.End)에서 왼쪽으로. 사용자: *"전반알람 아이콘을
                // 왼쪽으로 위치를 변경해"*. 칩 안에서 아이콘은 이미 글자 왼쪽이었고, 오른쪽에 붙어
                // 있던 건 칩 줄 자체였다.
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    DeadheadAlarmChip(day.date, day.duty, second = false)
                    if (hasSecond) DeadheadAlarmChip(day.date, day.duty, second = true)
                }
            }
            if (showRoute && routeAsset != null) {
                RouteImageDialog(
                    asset = routeAsset,
                    title = "${day.duty.display} 다이아 행로표",
                    onDismiss = { showRoute = false },
                )
            }
            OutlinedTextField(
                value = memo, onValueChange = { memo = it },
                label = { Text("메모") }, modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            Row(Modifier.fillMaxWidth()) {
                // 펼침은 날짜·근무변경 줄 자체가 없으니 **이 버튼이 근무변경의 유일한 진입점**이다.
                // 접힘의 `근무선택` 버튼은 v1.6.45에서 뺐다(사용자: *"근무변경과 근무선택 중복 아니야?"*) —
                // 날짜 줄 오른쪽 `근무변경` 칩과 앱바 `근무선택` 버튼이 각각 그 자리를 대신한다.
                if (!compact) OutlinedButton(onClick = onChangeDuty) { Text("근무변경") }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onSaveMemo(""); onClose() }) { Text("삭제") }
                    TextButton(onClick = onClose) { Text("취소") }
                    Button(onClick = { onSaveMemo(memo); onClose() }) { Text("저장") }
                }
            }
    }
}

/**
 * 편승(출근) 알람 버튼 (행로표 바로 아래, 오른쪽 정렬). **전반사업용·후반사업용 두 개**가 나란히 뜬다.
 *
 * 권장 시각과 안내 문구는 [BundledTimetable.advise]가 준다
 * (지선 = 양천구청 도착 5분 전 / 본선 신도림 교대 = 편승 열차 5분 전 /
 * **기지 출고 = 출고 50분 전**(v1.6.34) / 대기·야간 후반 = 알람 없음).
 * **자동 예약은 하지 않는다** — 버튼만 띄우고 사용자가 눌러 확인해야 걸린다.
 *
 * 근무변경으로 다이아가 바뀌면 예약해 둔 시각이 안 맞을 수 있으므로,
 * 시트를 열 때(=`at`이 바뀔 때) 예약이 살아 있으면 새 시각으로 다시 걸거나 해제한다.
 *
 * 아이콘은 v1.6.29에서 만든 파스텔 벡터 2종(`ic_deadhead_first` 민트 / `ic_deadhead_second` 라벤더).
 * **`Icon`이 아니라 `Image`로 그린다** — `Icon`은 tint를 먹여 파스텔 색을 통째로 지워 버린다.
 *
 * ### 네 상태 색 (v1.6.30 — 사용자: "알림 예약/해제 구분 색깔을 쫌 해줘야 할듯해")
 *
 * 종전엔 예약됨(primaryContainer)과 예약 가능(surfaceVariant)이 둘 다 옅은 채움이라 구분이 약했다.
 * **채움 여부**로 켜짐/꺼짐을 가르고, 색 계열(민트=전반 / 라벤더=후반)은 그대로 둔다.
 *
 * | 상태 | 라이트 | 다크 | 명암비 |
 * |---|---|---|---|
 * | **예약됨** | 잉크색 **꽉 채움** + 흰 글자 | 파스텔 **꽉 채움** + 잉크 글자 | 7.21·10.22 / 5.02·5.80 |
 * | 예약 가능 | 투명 + 잉크 테두리·글자 | 투명 + 파스텔 테두리·글자 | 7.04·9.x / 12.62·10.29 |
 * | 지남 | surfaceVariant + 흐린 글자 | 〃 | — |
 * | 알람없음 | surfaceVariant + 흐린 글자 | 〃 | — |
 *
 * 지남과 알람없음은 둘 다 무채색으로 물러나고 **글자**("지남" / "알람없음")로 갈린다 —
 * 둘 다 "지금 할 수 있는 게 없다"는 같은 뜻이라 색까지 나눌 이유가 없다.
 */
@Composable
private fun DeadheadAlarmChip(date: LocalDate, duty: DutyCode, second: Boolean) {
    val ctx = LocalContext.current
    val leg = if (second) DeadheadAlarm.LEG_SECOND else DeadheadAlarm.LEG_FIRST
    val advice = remember(date, duty, second) { BundledTimetable.advise(duty, date, second) }
    val at = advice.at
    val past = at != null && !LocalDateTime.of(date, at).isAfter(LocalDateTime.now())
    var booked by remember(date, second) { mutableStateOf<java.time.LocalTime?>(null) }
    var ask by remember(date, second) { mutableStateOf(false) }

    LaunchedEffect(date, at, second, ask) {
        // 권한이 없으면 예약 자체가 성립하지 않는다. 시스템이 권한을 끄는 순간 이미 걸린 알람을
        // 지워 버리기 때문에(`exact_alarm_permission_revoked`) 저장된 값만 믿으면 칩이 거짓말을 한다.
        // 예약목록(prefs)은 지우지 않는다 — 권한을 켜면 [DeadheadAlarm.rearmAll]이 되살린다.
        val cur = DeadheadAlarm.scheduledAt(ctx, date, leg).takeIf { AlarmPermission.canExact(ctx) }
        booked = when {
            cur == null || at == null || past -> { if (cur != null) DeadheadAlarm.cancel(ctx, date, leg); null }
            cur != at -> if (DeadheadAlarm.schedule(ctx, date, leg, at, advice.text)) at else null
            else -> cur
        }
    }

    fun hm(t: java.time.LocalTime) = "%d:%02d".format(t.hour, t.minute)
    val on = booked != null
    val disabled = at == null || past
    val half = if (second) "후반" else "전반"
    // 기지 출고 알람(v1.6.34)은 편승과 계산 규칙이 다르다 — 칩에도 "출고"를 적어 구분한다.
    // 아이콘·색은 전반 민트 / 후반 라벤더 그대로 둔다(구분은 글자 하나면 충분하고, 색을 더 쪼개면
    // 네 상태 색 표가 여덟 줄이 된다).
    val tag = if (advice.depot) "$half 출고" else half

    // 아이콘과 같은 계열. `ink`는 아이콘의 딥 톤(#12756A/#513F96)보다 한 단계 진하다 —
    // 파스텔 위 글자를 AA(4.5:1) 위로 올리려면 이만큼 필요하다(3.87 → 5.02).
    val pastel = if (second) Color(0xFFC9BCEF) else Color(0xFFA7E3D8)
    val ink = if (second) Color(0xFF453383) else Color(0xFF0F6259)
    val lightTheme = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    val accent = if (lightTheme) ink else pastel // 테두리·글자에 쓰는 계열색

    Surface(
        onClick = { ask = true },
        shape = RoundedCornerShape(999.dp),
        color = when {
            disabled -> MaterialTheme.colorScheme.surfaceVariant
            on -> accent // 예약됨 = 꽉 채움
            else -> Color.Transparent // 예약 가능 = 테두리만
        },
        contentColor = when {
            disabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            on -> if (lightTheme) Color.White else ink
            else -> accent
        },
        border = if (!disabled && !on) BorderStroke(1.5.dp, accent) else null,
    ) {
        // v1.6.42 ③ — 두 단계 축소. 글자 12→9 / 아이콘 18→13 / 패딩 10·6→7·4 / 간격 5→3.5dp.
        // 눌리는 영역은 안 줄어든다: 클릭 가능한 M3 `Surface`가 minimumInteractiveComponentSize(48dp)를
        // 스스로 얹기 때문에 칩 그림만 작아지고 손가락이 닿는 넓이는 그대로다.
        // **아이콘은 원래부터 글자 왼쪽**이다(이 Row의 첫 자식) — 사용자가 말한 "왼쪽으로"는
        // 상세시트에서 칩 줄이 오른쪽 끝에 붙어 있던 것이라 [DayDetailContent]의 정렬을 바꿨다.
        Row(
            Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.5.dp),
        ) {
            Image(
                painterResource(if (second) R.drawable.ic_deadhead_second else R.drawable.ic_deadhead_first),
                if (advice.depot) "$half 기지 출고 알람" else "$half 편승 알람",
                Modifier.size(13.dp),
                alpha = if (disabled) 0.5f else 1f,
            )
            Text(
                when {
                    at == null -> "$half 알람없음"
                    past -> "$tag ${hm(at)} 지남"
                    on -> "$tag ${hm(at)} 예약"
                    else -> "$tag ${hm(at)}"
                },
                fontSize = 9.sp, fontWeight = FontWeight.ExtraBold,
            )
        }
    }

    // 권한 안내는 예약을 누르기 **전에** 같은 다이얼로그에서 한다 — "예약됨인데 안 울림"이 최악이라
    // 켜기 전엔 예약을 막는다(v1.6.32, 근거는 AlarmPermission 주석).
    val permWarn = if (ask) AlarmPermission.warning(ctx) else null
    if (ask) AlertDialog(
        onDismissRequest = { ask = false },
        title = {
            Text(
                when {
                    permWarn != null -> "알람 권한을 켜 주세요"
                    advice.depot -> "${half}사업 기지 출고 알람"
                    else -> "${half}사업 편승 알람"
                },
            )
        },
        text = {
            Text(
                when {
                    at == null -> advice.text
                    past -> "${advice.text}\n\n이미 지난 시각이라 알림을 예약할 수 없어요."
                    permWarn != null -> "${advice.text}\n\n$permWarn"
                    on -> "${advice.text}\n\n이대로 예약돼 있습니다. 알림을 해제할까요?"
                    else -> "${advice.text}\n\n이 시각에 알림을 받을까요?"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            if (at != null && !past) TextButton(onClick = {
                when {
                    permWarn != null -> AlarmPermission.openSettings(ctx)
                    on -> { DeadheadAlarm.cancel(ctx, date, leg); booked = null }
                    else -> booked = if (DeadheadAlarm.schedule(ctx, date, leg, at, advice.text)) at else null
                }
                ask = false
            }) { Text(if (permWarn != null) "설정 열기" else if (on) "예약 해제" else "알림 예약") }
        },
        dismissButton = { TextButton(onClick = { ask = false }) { Text("닫기") } },
    )
}

@Composable
private fun KvRow(key: String, value: String, sub: Boolean = false) {
    Row {
        Text(
            key, modifier = Modifier.width(82.dp),
            style = if (sub) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = if (sub) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            fontWeight = if (sub) FontWeight.Bold else FontWeight.ExtraBold,
            color = if (sub) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 1단계에서 카드 한 장으로 묶어 보여주는 사업소 근무형태 (enum은 분리 유지 — 동료근무 섹션 구분용) */
private val SITE_GROUPS = listOf(CrewGroup.OFFICE_DAY, CrewGroup.SHIFT_4_2)

/* ── 근무선택 시트: ① 소속 → (사업소면 근무형태) → ② 근무 그리드 ───────────────
   관리자 화면(동료 대리등록)도 이 시트를 그대로 재사용한다 — patternOffset 계산 경로를 하나로 유지. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DutyPickerSheet(
    picker: DutyPickerState,
    currentGroup: CrewGroup?,
    currentOffset: Int,
    onPickGroup: (CrewGroup) -> Unit,
    onBack: () -> Unit,
    onPick: (CrewGroup, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val duty = LocalDutyColors.current
    val dateLabel = picker.date.format(DateTimeFormatter.ofPattern("M/d (E)", Locale.KOREAN))

    // 소속 카드가 화면을 넘는다 → 소속 단계에서만 세로 스크롤
    // (근무 그리드 단계는 LazyVerticalGrid를 품고 있어 스크롤을 겹치면 안 된다)
    val groupScroll = rememberScrollState()
    // 사업소 근무형태(통상근무·4조2교대)는 인원이 적어 1단계에선 카드 하나로 묶고,
    // 그 카드를 누르면 여기서 둘 중 하나를 고른다 → 첫 화면이 정확히 4장.
    // CrewGroup enum은 그대로 5종 — 동료근무 섹션 구분이 살아 있어야 한다.
    var siteStep by remember { mutableStateOf(false) }
    // **반쪽 전개 금지**(v1.6.42 ②). 종전엔 기본 `sheetState`라 내용이 화면 절반을 넘는 순간
    // 시트가 절반 높이로 열려 1단계 마지막 카드(`통상근무 / 4조2교대`)가 접힌 채 시작했다
    // (사용자: *"근무선택에 한번에 보이지 않아.. 맨밑에 통상근무/4조2교대 ← 까지 보이게"*).
    // `skipPartiallyExpanded`면 시트 높이가 곧 **내용 높이**(화면 전체가 상한)라 항상 다 보인다.
    // 상세시트가 v1.6.28부터 쓰는 방식과 같다.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)
                .then(if (picker.group == null) Modifier.verticalScroll(groupScroll) else Modifier),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (picker.group == null) {
                // 1단계: 소속 (승무 3종 + 사업소 묶음) / siteStep이면 사업소 근무형태 2종
                // 안내문(`먼저 소속을 고르세요.`)은 지웠다 — 제목이 이미 `1/2 · 소속`이라
                // 같은 말을 두 번 하면서 카드 한 장 값(약 18dp)을 먹고 있었다(v1.6.42 ②).
                Text(
                    if (siteStep) "근무선택 · 근무형태" else "근무선택  1/2 · 소속",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold,
                )
                if (siteStep) TextButton(onClick = { siteStep = false }, contentPadding = PaddingValues(0.dp)) {
                    Text("‹ 소속 다시 선택")
                }
                val shown = if (siteStep) SITE_GROUPS else CrewGroup.entries.filter { it !in SITE_GROUPS }
                shown.forEach { g ->
                    val isCurrent = g == currentGroup
                    val pattern = Bundled.patternFor(g)
                    OutlinedCard(
                        // 통상근무는 조 구분이 없다(1칸 순환) → 고르면 바로 확정, 조 선택 없음
                        onClick = { if (pattern.length == 1) onPick(g, 0) else onPickGroup(g) },
                        border = if (isCurrent) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                        else CardDefaults.outlinedCardBorder(),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(g.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                            Text(
                                when (g) {
                                    CrewGroup.SHIFT_4_2 -> "운용조·기지관제 · 주간→야간→비번→휴무 · A~D조"
                                    CrewGroup.OFFICE_DAY -> "사무실·소장·지도과·관리과 · 월~금 주간, 토·일·공휴일 휴무"
                                    else -> "${pattern.length}칸 교번 순환"
                                } + if (isCurrent) " · 현재 선택" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (!siteStep) {
                    val isCurrent = currentGroup in SITE_GROUPS
                    OutlinedCard(
                        onClick = { siteStep = true },
                        border = if (isCurrent) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                        else CardDefaults.outlinedCardBorder(),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(
                                SITE_GROUPS.joinToString(" / ") { it.label },
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold,
                            )
                            Text(
                                "사무실·지도과·관리과·운용조·기지관제" +
                                    if (isCurrent) " · 현재 ${currentGroup?.label}" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else if (picker.group == CrewGroup.SHIFT_4_2) {
                // 2단계(4조2교대): 29칸 다이아 대신 A·B·C·D 4개 조
                val pattern = Bundled.SHIFT_PATTERN
                val days = ChronoUnit.DAYS.between(pattern.anchorDate, picker.date).toInt()
                Text(
                    "근무선택  2/2 · 4조2교대",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold,
                )
                TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) { Text("‹ 근무형태 다시 선택") }
                Text(
                    "내 조를 고르세요. 괄호는 $dateLabel 근무입니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ShiftTeam.entries.forEach { team ->
                    val code = pattern.dutyOn(picker.date, team.offset)
                    val isCurrent = picker.group == currentGroup && currentOffset == team.offset
                    val (bg, fg) = dutyCellColors(code.colorType, duty, MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedCard(
                        // offsetFor(date, idx) == team.offset 이 되는 idx 를 역산해 넘긴다
                        onClick = { onPick(CrewGroup.SHIFT_4_2, Math.floorMod(team.offset + days, pattern.length)) },
                        border = if (isCurrent) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                        else CardDefaults.outlinedCardBorder(),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                team.label, modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold,
                            )
                            Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(9.dp)) {
                                Text(
                                    code.display,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                            }
                            if (isCurrent) Text(
                                "  현재 선택", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            } else {
                // 2단계: 근무 그리드
                val group = picker.group
                val pattern = Bundled.patternFor(group)
                val days = ChronoUnit.DAYS.between(pattern.anchorDate, picker.date).toInt()
                val currentIndex = if (group == currentGroup)
                    Math.floorMod(days + currentOffset, pattern.length) else -1

                Text(
                    "근무선택  2/2 · ${group.label}",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold,
                )
                TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) { Text("‹ 소속 다시 선택") }
                Text(
                    "$dateLabel 내 근무를 고르세요. 앞뒤 모든 날짜가 교번 순서대로 자동 입력됩니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DutySequenceGrid(pattern.sequence, currentIndex) { i -> onPick(group, i) }
                Text(
                    "※ 언제든 다시 선택 가능 — 근무가 밀렸을 때 그 날짜 기준으로 다시 찍으면 됩니다.\n" +
                        "※ 비번(~)은 목록에 없습니다 — 야간 다이아를 고르면 다음날 자동으로 붙습니다.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 교번 순환 한 벌을 6열 칩 그리드로. 근무선택 2단계와 **근무변경의 충당 계열 다이아 선택**이 같이 쓴다.
 * 두 벌로 나누면 색·크기가 갈라지므로 한 벌만 둔다.
 *
 * 칸은 **다이아 번호순으로 보여주되**(v1.6.35 — 교번 순환 그대로면 번호가 뒤죽박죽이라 못 찾는다),
 * 각 칸이 들고 다니는 인덱스는 **원래 시퀀스 인덱스** 그대로다. `onPick`으로 나가는 값이
 * 곧 `Pattern.offsetFor`의 입력이라 여기서 인덱스를 바꾸면 근무표가 통째로 어긋난다.
 */
@Composable
private fun DutySequenceGrid(sequence: List<String>, currentIndex: Int, onPick: (Int) -> Unit) {
    val duty = LocalDutyColors.current
    val order = remember(sequence) { DutyCode.displayOrder(sequence) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        verticalArrangement = Arrangement.spacedBy(5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.heightIn(max = 340.dp),
    ) {
        items(order.size) { pos ->
            val i = order[pos]
            val code = DutyCode.parse(sequence[i])
            val (bg, fg) = dutyCellColors(code.colorType, duty, MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(
                onClick = { onPick(i) },
                color = bg, contentColor = fg,
                shape = RoundedCornerShape(9.dp),
                border = if (i == currentIndex) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
            ) {
                Text(
                    code.display,
                    modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                )
            }
        }
    }
}

/* ── 근무변경 시트: 직접입력 / 변경없음 / 22종 평면 격자 (v1.6.42 ④) ──────────────────

   v1.6.40의 휴가 3묶음 아코디언을 **걷어냈다.** 사용자: *"근무변경에 들어가도 한번에 보이지 않네..?
   연차,대휴 누르면 내려가버리네? 불편하네?"* — 접기는 두 가지를 동시에 어겼다.
    · 펼치면 하위 칩이 **아래로 끼어들어** 방금 누른 칩이 밑으로 밀린다(누른 자리가 움직인다).
    · 그래도 한 화면에 안 들어왔다. 진짜 원인은 접기가 아니라 **시트가 절반 높이로 열린 것**이다
      (기본 `sheetState`는 내용이 화면 절반을 넘으면 PartiallyExpanded에서 시작한다. 실측:
      1080x2400·420dpi에서 시트 상단 y=1200px = 정확히 절반, 마지막 줄과 `닫기`가 잘림).

   → `skipPartiallyExpanded` + **22종 평면 4열**. 4열이면 6줄, 칩 세로여백 10→8dp로 격자가
   211dp(fs 1.0)·253dp(fs 1.3)라 시트 전체가 465dp(fs 1.0)·530dp(fs 1.3) — 914dp 화면에 다 들어온다.
   접기가 없으니 **어느 칩을 눌러도 레이아웃이 움직이지 않는다**(충당 계열만 같은 자리에서 화면이
   통째로 다이아 선택으로 교체된다 — 원래부터 그랬다). 저장값은 종전 그대로 하위 코드다(`촉연`→`촉연`). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DutyChangeSheet(
    day: DaySchedule,
    onChange: (String) -> Unit,
    onRevert: () -> Unit,
    onDismiss: () -> Unit,
) {
    val duty = LocalDutyColors.current
    var manualMode by remember { mutableStateOf(false) }
    var manualText by remember { mutableStateOf("") }
    // 충당·대기충당·교체는 "그 다이아를 대신 뛰는 근무"라 다이아를 함께 받는다 → 소속 → 다이아 2단계.
    // 시트를 닫았다 열면 remember가 초기화돼 다시 목록부터 시작한다.
    var fillFor by remember { mutableStateOf<String?>(null) }
    var fillGroup by remember { mutableStateOf<CrewGroup?>(null) }
    val dateLabel = day.date.format(DateTimeFormatter.ofPattern("M/d (E)", Locale.KOREAN))
    val originalLabel = DutyCode.parse(day.originalDutyRaw ?: day.duty.raw).display

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
      Box {
        // 🔄 워터마크(v1.6.42 ④ 사용자 요청). `matchParentSize`라 **시트 높이에 한 톨도 안 얹힌다**
        // — 크기는 아래 Column이 정하고 이모지는 그 안에서 가운데 정렬만 한다.
        // 알파 0.06: 라이트(연보라 바탕)·다크 양쪽에서 "있는 줄은 알겠고 글자는 안 방해하는" 선.
        // 먼저 선언 = 먼저 그려짐 = 내용 뒤.
        Text(
            "🔄", fontSize = 190.sp,
            modifier = Modifier.matchParentSize().wrapContentSize(Alignment.Center).alpha(0.06f),
        )
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
        val fill = fillFor
        if (fill != null) {
            Text(
                "$fill · 다이아 선택",
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold,
            )
            TextButton(
                onClick = { if (fillGroup != null) fillGroup = null else fillFor = null },
                contentPadding = PaddingValues(0.dp),
            ) { Text(if (fillGroup != null) "‹ 소속 다시 선택" else "‹ 근무변경 다시 선택") }
            Text(
                "대신 뛰는 다이아를 고르면 출근시각·행로표·열번이 그 다이아 기준으로 나옵니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedCard(onClick = { onChange(fill) }) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("다이아 없이 저장", fontWeight = FontWeight.ExtraBold)
                    Text("어떤 다이아인지 모를 때 — \"$fill\"만 기록합니다",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val g = fillGroup
            if (g == null) {
                // 사업소 근무형태(통상·4조2교대)는 다이아가 없어 뺀다 — 승무 3종만 대행 대상이다
                CrewGroup.entries.filter { it !in SITE_GROUPS }.forEach { grp ->
                    OutlinedCard(onClick = { fillGroup = grp }) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(grp.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                            Text("${Bundled.patternFor(grp).length}칸 교번 순환",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                val seq = Bundled.patternFor(g).sequence
                DutySequenceGrid(seq, -1) { i -> onChange("$fill ${seq[i]}") }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("닫기") }
            return@Column
        }
            Text(
                "근무변경  $dateLabel 하루만 · 패턴 유지",
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold,
            )
            OutlinedCard(onClick = { manualMode = !manualMode }) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("직접입력", fontWeight = FontWeight.ExtraBold)
                    Text("교번·다이아 등 자유 입력 (예: 지7, 45)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (manualMode) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = manualText, onValueChange = { manualText = it },
                        placeholder = { Text("예: 지7, 45, 대2, 회의") },
                        modifier = Modifier.weight(1f), singleLine = true,
                    )
                    Button(onClick = { if (manualText.isNotBlank()) onChange(manualText.trim()) }) { Text("적용") }
                }
            }
            OutlinedCard(onClick = onRevert) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("변경없음 (원래 근무)", fontWeight = FontWeight.ExtraBold)
                    Text("패턴값 ${originalLabel}(으)로 되돌리기",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // 22종 평면 4열 6줄. `heightIn` 상한은 안전장치일 뿐 실제로는 안 걸린다
            // (fs 1.3에서도 약 253dp) — 걸리면 격자만 따로 스크롤되므로 화면이 깨지지는 않는다.
            val options = DutyCode.CHANGE_OPTIONS
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.heightIn(max = 480.dp),
            ) {
                items(options.size) { i ->
                    val code = options[i]
                    val (bg, fg) = dutyCellColors(
                        DutyCode.parse(code).colorType, duty, MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Surface(
                        // 충당 계열은 바로 확정하지 않고 다이아 선택 단계로 넘어간다
                        onClick = {
                            if (code in DutyCode.FILL_OPTIONS) { fillFor = code; fillGroup = null }
                            else onChange(code)
                        },
                        color = bg, contentColor = fg,
                        shape = RoundedCornerShape(9.dp),
                        border = if (code == (day.duty.fill ?: day.duty.raw))
                            BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    ) {
                        // 4글자(`대기충당`·`돌봄휴가`)까지 한 줄로 들어가야 해서 12.5 → 11.5sp.
                        // 411dp 폭 기준 칸이 89dp라 fs 1.3에서도(4 x 11.5 x 1.3 = 60dp) 남는다.
                        Text(
                            code,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp).fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1,
                        )
                    }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("닫기") }
        }
      }
    }
}
