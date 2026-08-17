package com.sinjeong.crewcalendar.presentation.calendar

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sinjeong.crewcalendar.R
import com.sinjeong.crewcalendar.domain.model.Bundled
import com.sinjeong.crewcalendar.domain.model.CrewGroup
import com.sinjeong.crewcalendar.domain.model.DaySchedule
import com.sinjeong.crewcalendar.domain.model.DutyCode
import com.sinjeong.crewcalendar.domain.model.DutyType
import com.sinjeong.crewcalendar.domain.model.MainLegs
import com.sinjeong.crewcalendar.domain.model.NightCombo
import com.sinjeong.crewcalendar.domain.model.RouteTable
import com.sinjeong.crewcalendar.domain.model.ShiftTeam
import com.sinjeong.crewcalendar.domain.usecase.TodayDuty
import com.sinjeong.crewcalendar.presentation.settings.openSafetyApp
import com.sinjeong.crewcalendar.presentation.theme.DutyColors
import com.sinjeong.crewcalendar.presentation.theme.LocalDutyColors
import com.sinjeong.crewcalendar.presentation.theme.ThemeMode
import java.time.DayOfWeek
import java.time.LocalDate
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
    onOpenRoster: () -> Unit = {},
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
                    Spacer(Modifier.width(4.dp))
                    FilledTonalButton(
                        onClick = onOpenRoster,
                        contentPadding = PaddingValues(horizontal = 6.dp),
                        modifier = Modifier.height(28.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = LocalDutyColors.current.branch,
                            contentColor = LocalDutyColors.current.onBranch,
                        ),
                    ) { Text("동료근무", fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold) }
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
                // 오늘 요약 카드 제거 — 달력을 최대로 (오늘 정보는 오늘 칸 탭으로)
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
                if (day == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("날짜를 선택하세요", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    DayDetailContent(
                        day = day,
                        onSaveMemo = { viewModel.saveMemo(day.date, it) },
                        onPickDuty = { viewModel.openDutyPicker(day.date) },
                        onChangeDuty = { viewModel.openDutyChange(day.date) },
                        onRevert = { viewModel.changeDuty(day.date, null) },
                        onClose = { panelEpochDay = null },
                        compact = false,
                        // imePadding()이 verticalScroll()보다 앞 — 키보드만큼 스크롤 뷰포트가 줄어야
                        // 메모 TextField의 bringIntoView가 보이는 영역으로 스크롤한다
                        modifier = Modifier.fillMaxSize().imePadding()
                            .verticalScroll(rememberScrollState()).padding(top = 14.dp),
                    )
                }
            }
        }
    }

    // 접힘: 날짜 탭 → 상세 시트 (출근시간·전반/후반사업·근무시간·메모·근무변경)
    if (!wide) state.selectedDate?.let { date ->
        state.days.firstOrNull { it.date == date }?.let { day ->
            // 시트 윈도우는 키보드가 떠도 위치·크기가 그대로다(바닥 883px이 키보드에 덮임).
            // 시트 높이를 키우는 보정(imePadding 등)은 M3 1.3.1 앵커가 리사이즈를 반영하지
            // 못해 실패한다(에뮬 실측). 대신 스크롤 '안쪽' 끝에 키보드 높이만큼 패딩을 넣어
            // 시트 기하는 그대로 두고, 키보드가 열리는 동안 스크롤을 바닥에 붙여
            // 메모·버튼을 키보드 위로 끌어올린다.
            // 시트는 처음부터 완전히 펼쳐 연다 — 50% 부분전개면 커진 행로표가 화면을 다 먹어 메모가 안 보인다.
            ModalBottomSheet(
                onDismissRequest = { viewModel.selectDate(null) },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            ) {
                val scroll = rememberScrollState()
                val imeBottom = with(LocalDensity.current) { WindowInsets.ime.getBottom(this).toDp() }
                LaunchedEffect(imeBottom > 0.dp) {
                    if (imeBottom > 0.dp) snapshotFlow { scroll.maxValue }.collect { scroll.scrollTo(it) }
                }
                DayDetailContent(
                    day = day,
                    onSaveMemo = { viewModel.saveMemo(date, it) },
                    onPickDuty = { viewModel.openDutyPicker(date) },
                    onChangeDuty = { viewModel.openDutyChange(date) },
                    onRevert = { viewModel.changeDuty(date, null) },
                    onClose = { viewModel.selectDate(null) },
                    modifier = Modifier.verticalScroll(scroll).padding(bottom = imeBottom),
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

/* ── 근무 종별 색 (공용) ──────────────────────────────── */
private fun dutyChipColors(type: DutyType, duty: DutyColors, fallback: Color): Pair<Color, Color> =
    when (type) {
        DutyType.MAIN_DAY, DutyType.OFFICE -> duty.main to duty.onMain
        DutyType.MAIN_NIGHT, DutyType.BRANCH_NIGHT, DutyType.SPECIAL -> duty.night to duty.onNight
        DutyType.POST_NIGHT -> duty.off to duty.onOff
        DutyType.REST, DutyType.BRANCH_REST -> duty.rest to duty.onRest
        DutyType.STANDBY, DutyType.BRANCH_STANDBY -> duty.standby to duty.onStandby
        DutyType.BRANCH -> duty.main to duty.onMain
        DutyType.ETC -> Color.Transparent to fallback
    }

/* ── 오늘 요약 카드 ───────────────────────────────────── */
@Composable
private fun TodaySummaryCard(today: TodayDuty, groupLabel: String?, onClick: () -> Unit) {
    val duty = LocalDutyColors.current
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column {
                Text(
                    "오늘 · ${today.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)}",
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    "${today.date.dayOfMonth}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        // 상단 요약은 폭이 넉넉하다 → 한 글자 코드 대신 "주간 근무"처럼 풀어 쓴다
                        "${today.duty.displayLong.ifBlank { "근무 없음" }} 근무",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    groupLabel?.let {
                        Surface(color = duty.branch, contentColor = duty.onBranch, shape = RoundedCornerShape(5.dp)) {
                            Text(it, fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
                val sub = buildString {
                    today.signOn?.let { append("출근 $it") }
                    if (today.memo.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(today.memo)
                    }
                }
                if (sub.isNotBlank()) Text(
                    sub, style = MaterialTheme.typography.bodySmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
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
    // 빈 칸(null) 처음 2개 = 근무시각표 / 편승시각표 카드
    val nullIdx = cells.indices.filter { cells[it] == null }
    val card1 = nullIdx.getOrNull(0)
    val card2 = nullIdx.getOrNull(1)
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
                    card1 -> TimetableCard("근무시각표", onOpenTimetable, cellHeight, duty.main, duty.onMain)
                    card2 -> TimetableCard("편승시각표", onOpenDeadhead, cellHeight, duty.branch, duty.onBranch)
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
private fun TimetableCard(label: String, onClick: () -> Unit, height: Dp, bg: Color, fg: Color) {
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
        Icon(Icons.Default.Schedule, null, tint = fg, modifier = Modifier.size(22.dp))
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
    val (chipBg, chipFg) = dutyChipColors(day.duty.type, duty, MaterialTheme.colorScheme.onSurfaceVariant)
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
                    // 오늘: 테두리만으론 약해서 칸 바탕까지 primaryContainer로 물들인다(v1.6.24).
                    // surfaceVariant는 "선택된 칸"과 같은 색이라 오늘이 묻혔다.
                    isToday -> Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer)
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
                val label = day.duty.display
                Box(
                    Modifier.width(if (big) 42.dp else 34.dp).padding(vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    // 다이아 텍스트 자동 맞춤: 칩 폭을 넘치면 들어갈 때까지 축소 (시스템 글꼴 확대에도 안 짤림)
                    val baseSize = if (label.length >= 3) chipSizeSmall else chipSizeBig
                    var fitSize by remember(label, big) { mutableStateOf(baseSize) }
                    Text(
                        label,
                        fontSize = fitSize,
                        lineHeight = fitSize * 1.15,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1, softWrap = false,
                        onTextLayout = { if (it.hasVisualOverflow && fitSize > 7.sp) fitSize *= 0.92f },
                    )
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
@Composable
private fun DayDetailContent(
    day: DaySchedule,
    onSaveMemo: (String) -> Unit,
    onPickDuty: () -> Unit,
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
    val (chipBg, chipFg) = dutyChipColors(day.duty.type, duty, MaterialTheme.colorScheme.onSurfaceVariant)
    val isDia = day.duty.type in setOf(DutyType.MAIN_DAY, DutyType.MAIN_NIGHT, DutyType.BRANCH, DutyType.BRANCH_NIGHT)

    Column(
        modifier.padding(horizontal = if (compact) 20.dp else 10.dp).padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
            // 펼침 패널은 날짜·근무칩 줄 생략 — 행로표에 폭/높이를 몰아준다
            if (compact) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        day.date.format(DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)),
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold,
                    )
                    (day.holidayName ?: day.memorialName)?.let {
                        Text(it, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, color = duty.sunday)
                    }
                    day.seasonalTerm?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = duty.onStandby)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(color = chipBg, contentColor = chipFg, shape = RoundedCornerShape(10.dp)) {
                        Text(
                            buildString {
                                append(day.duty.displayLong.ifBlank { "근무 없음" })
                                if (isDia) append(" Dia")
                                combo?.let { append(" (${it.label})") }
                            },
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            fontWeight = FontWeight.ExtraBold, fontSize = 15.sp,
                        )
                    }
                    if (day.duty.isBranch) Surface(color = duty.branch, contentColor = duty.onBranch, shape = RoundedCornerShape(6.dp)) {
                        Text("지선", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Surface(
                        onClick = onChangeDuty,
                        color = duty.standby, contentColor = duty.onStandby,
                        shape = RoundedCornerShape(999.dp),
                    ) {
                        Text("근무변경", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
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
            val mainLegs = day.duty.number?.takeIf { !day.duty.isBranch }?.let { n ->
                if (isNight) combo?.let { MainLegs.forNight(n, it) } else MainLegs.forDay(n, holiday)
            }
            val trains = day.duty.number?.let { n ->
                when {
                    day.duty.isBranch && day.duty.type != DutyType.BRANCH_STANDBY -> RouteTable.forBranch(n, holiday)
                    day.duty.isBranch -> null
                    isNight -> combo?.let { RouteTable.forMainNight(n, it) }
                    else -> RouteTable.forMainDay(n, holiday)
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
                row?.let { r ->
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
                            else -> {
                                KvRow("출근", r.signOn)
                                KvRow("종료", (if (r.overnight) "익일 " else "") + r.signOff)
                            }
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
                // 펼침은 상단 근무변경 칩이 없으니 왼쪽 버튼이 그 자리를 대신한다
                if (compact) OutlinedButton(onClick = onPickDuty) { Text("근무선택") }
                else OutlinedButton(onClick = onChangeDuty) { Text("근무변경") }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onSaveMemo(""); onClose() }) { Text("삭제") }
                    TextButton(onClick = onClose) { Text("취소") }
                    Button(onClick = { onSaveMemo(memo); onClose() }) { Text("저장") }
                }
            }
    }
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
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)
                .then(if (picker.group == null) Modifier.verticalScroll(groupScroll) else Modifier),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (picker.group == null) {
                // 1단계: 소속 (승무 3종 + 사업소 묶음) / siteStep이면 사업소 근무형태 2종
                Text(
                    if (siteStep) "근무선택 · 근무형태" else "근무선택  1/2 · 소속",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold,
                )
                if (siteStep) TextButton(onClick = { siteStep = false }, contentPadding = PaddingValues(0.dp)) {
                    Text("‹ 소속 다시 선택")
                }
                Text(
                    if (siteStep) "근무형태를 고르세요." else "먼저 소속을 고르세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp)) {
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
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp)) {
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
                    val (bg, fg) = dutyChipColors(code.type, duty, MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedCard(
                        // offsetFor(date, idx) == team.offset 이 되는 idx 를 역산해 넘긴다
                        onClick = { onPick(CrewGroup.SHIFT_4_2, Math.floorMod(team.offset + days, pattern.length)) },
                        border = if (isCurrent) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                        else CardDefaults.outlinedCardBorder(),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
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
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.heightIn(max = 340.dp),
                ) {
                    items(pattern.sequence.size) { i ->
                        val code = DutyCode.parse(pattern.sequence[i])
                        val (bg, fg) = dutyChipColors(code.type, duty, MaterialTheme.colorScheme.onSurfaceVariant)
                        Surface(
                            onClick = { onPick(group, i) },
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
                Text(
                    "※ 언제든 다시 선택 가능 — 근무가 밀렸을 때 그 날짜 기준으로 다시 찍으면 됩니다.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/* ── 근무변경 시트: 직접입력 / 변경없음 / 25종 목록 ─────── */
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
    val dateLabel = day.date.format(DateTimeFormatter.ofPattern("M/d (E)", Locale.KOREAN))
    val originalLabel = DutyCode.parse(day.originalDutyRaw ?: day.duty.raw).display

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
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
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.heightIn(max = 300.dp),
                userScrollEnabled = true,
            ) {
                items(DutyCode.CHANGE_OPTIONS.size) { i ->
                    val code = DutyCode.CHANGE_OPTIONS[i]
                    val parsed = DutyCode.parse(code)
                    val (bg, fg) = dutyChipColors(parsed.type, duty, MaterialTheme.colorScheme.onSurfaceVariant)
                    Surface(
                        onClick = { onChange(code) },
                        color = bg, contentColor = fg,
                        shape = RoundedCornerShape(9.dp),
                        border = if (code == day.duty.raw) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    ) {
                        Text(
                            code,
                            modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1,
                        )
                    }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("닫기") }
        }
    }
}
