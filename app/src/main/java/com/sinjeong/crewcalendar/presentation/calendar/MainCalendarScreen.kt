package com.sinjeong.crewcalendar.presentation.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sinjeong.crewcalendar.domain.model.Bundled
import com.sinjeong.crewcalendar.domain.model.CrewGroup
import com.sinjeong.crewcalendar.domain.model.DaySchedule
import com.sinjeong.crewcalendar.domain.model.DutyCode
import com.sinjeong.crewcalendar.domain.model.DutyType
import com.sinjeong.crewcalendar.domain.model.MainLegs
import com.sinjeong.crewcalendar.domain.model.NightCombo
import com.sinjeong.crewcalendar.domain.model.RouteTable
import com.sinjeong.crewcalendar.domain.usecase.TodayDuty
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

    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); viewModel.dismissError() }
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
                    Spacer(Modifier.weight(1f))
                    RestCountChip(state.restDayCount)
                    Spacer(Modifier.width(4.dp))
                    FilledTonalButton(
                        onClick = { viewModel.openDutyPicker(LocalDate.now()) },
                        contentPadding = PaddingValues(horizontal = 7.dp),
                        modifier = Modifier.height(28.dp),
                    ) { Text("근무선택", fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold) }
                    Spacer(Modifier.width(4.dp))
                    FilledTonalButton(
                        onClick = onOpenRoster,
                        contentPadding = PaddingValues(horizontal = 7.dp),
                        modifier = Modifier.height(28.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = LocalDutyColors.current.branch,
                            contentColor = LocalDutyColors.current.onBranch,
                        ),
                    ) { Text("동료근무", fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold) }
                    IconButton(
                        onClick = { viewModel.toggleTheme(isDark) },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                            "다크/라이트 전환",
                            modifier = Modifier.size(18.dp),
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
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(Icons.Default.Share, "근무표 공유", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = viewModel::goToday, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Today, "오늘로", modifier = Modifier.size(18.dp))
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
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
                    selected = state.selectedDate,
                    onSelect = viewModel::selectDate,
                    onSwipeMonth = viewModel::moveMonth,
                    onOpenTimetable = { fullTimetable = "tt_work" to "근무시각표" },
                    onOpenDeadhead = { fullTimetable = "tt_deadhead" to "편승시각표" },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    // 날짜 탭 → 상세 시트 (출근시간·전반/후반사업·근무시간·메모·근무변경)
    state.selectedDate?.let { date ->
        state.days.firstOrNull { it.date == date }?.let { day ->
            DayDetailSheet(
                day = day,
                onDismiss = { viewModel.selectDate(null) },
                onSaveMemo = { viewModel.saveMemo(date, it) },
                onPickDuty = { viewModel.openDutyPicker(date) },
                onChangeDuty = { viewModel.openDutyChange(date) },
                onRevert = { viewModel.changeDuty(date, null) },
            )
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
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
            fontSize = 11.sp, fontWeight = FontWeight.Bold,
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
                        "${today.duty.display.ifBlank { "근무 없음" }} 근무",
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
            userScrollEnabled = false,
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
        Text(
            twoLine,
            fontSize = 12.5.sp, lineHeight = 16.sp,
            fontWeight = FontWeight.ExtraBold, color = fg, textAlign = TextAlign.Center,
            softWrap = false,
        )
    }
}

@Composable
private fun DayCell(day: DaySchedule, isSelected: Boolean, height: Dp, big: Boolean, onClick: () -> Unit) {
    val duty = LocalDutyColors.current
    val isToday = day.date == LocalDate.now()
    val (chipBg, chipFg) = dutyChipColors(day.duty.type, duty, MaterialTheme.colorScheme.onSurfaceVariant)
    // big = 칸이 넉넉할 때(≥100dp) 전체 폰트 한 단계 확대
    val dateSize = if (big) 9.5.sp else 7.5.sp
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
                    isToday -> Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
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
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${day.date.dayOfMonth}",
                fontSize = dateSize, lineHeight = dateSize * 1.05,
                fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.SemiBold,
                color = when {
                    isToday -> MaterialTheme.colorScheme.primary
                    day.holidayName != null || day.date.dayOfWeek == DayOfWeek.SUNDAY -> duty.sunday
                    day.date.dayOfWeek == DayOfWeek.SATURDAY -> duty.saturday
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                // 날짜 숫자 뒤 아주 옅은 사각형 — onSurface 알파라 라이트/다크 자동 대응
                modifier = Modifier
                    .padding(start = 3.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
            val nameTag = day.holidayName ?: day.memorialName ?: day.seasonalTerm
            nameTag?.let {
                // 공휴일 이름: 아주 작게, 칸을 절대 밀지 않도록 남은 폭 안에서만
                Text(
                    it, fontSize = holSize, lineHeight = holSize * 1.1, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    color = if (day.holidayName != null || day.memorialName != null) duty.sunday else duty.onStandby,
                    modifier = Modifier.weight(1f).padding(start = 2.dp, end = 2.dp),
                )
            }
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
        if (day.duty.raw.isNotBlank()) {
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
        }
        day.signOn?.let {
            Text(
                it, fontSize = signOnSize, lineHeight = signOnSize * 1.06, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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

/* ── 날짜 상세 시트 (기존 앱 형식: 출근시간/전반사업/후반사업/근무시간) ── */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayDetailSheet(
    day: DaySchedule,
    onDismiss: () -> Unit,
    onSaveMemo: (String) -> Unit,
    onPickDuty: () -> Unit,
    onChangeDuty: () -> Unit,
    onRevert: () -> Unit,
) {
    val duty = LocalDutyColors.current
    var memo by remember(day.date) { mutableStateOf(day.memo) }
    val row = Bundled.timeRowFor(day.duty, day.date)
    val combo = if (day.duty.isNight) Bundled.comboOf(day.date) else null
    val (chipBg, chipFg) = dutyChipColors(day.duty.type, duty, MaterialTheme.colorScheme.onSurfaceVariant)
    val isDia = day.duty.type in setOf(DutyType.MAIN_DAY, DutyType.MAIN_NIGHT, DutyType.BRANCH, DutyType.BRANCH_NIGHT)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
                            append(day.duty.display.ifBlank { "근무 없음" })
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
            var showRoute by remember { mutableStateOf(false) }
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
                    // 지선 주간 지1~8 (bwd/bhol) + 야간 지10~14 (평평≡평휴=bnwd, 휴평=bnhp, 휴휴=bnhol — 10/12/13 종료편성이 다름)
                    day.duty.isBranch -> when {
                        day.duty.type == DutyType.BRANCH && n in 1..8 ->
                            if (holiday) "bhol_$n" else "bwd_$n"
                        day.duty.type == DutyType.BRANCH_NIGHT && n in 10..14 -> when (combo) {
                            NightCombo.HP -> "bnhp_$n"
                            NightCombo.HH -> "bnhol_$n"
                            else -> "bnwd_$n"
                        }
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
            if (routeAsset != null) {
                RouteImageInline(routeAsset) { showRoute = true }
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
                OutlinedButton(onClick = onPickDuty) { Text("근무선택") }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onSaveMemo(""); onDismiss() }) { Text("삭제") }
                    TextButton(onClick = onDismiss) { Text("취소") }
                    Button(onClick = { onSaveMemo(memo); onDismiss() }) { Text("저장") }
                }
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

/* ── 근무선택 시트: ① 소속 → ② 근무 그리드 ─────────────── */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DutyPickerSheet(
    picker: DutyPickerState,
    currentGroup: CrewGroup?,
    currentOffset: Int,
    onPickGroup: (CrewGroup) -> Unit,
    onBack: () -> Unit,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val duty = LocalDutyColors.current
    val dateLabel = picker.date.format(DateTimeFormatter.ofPattern("M/d (E)", Locale.KOREAN))

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (picker.group == null) {
                // 1단계: 소속
                Text(
                    "근무선택  1/2 · 소속",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold,
                )
                Text("먼저 소속을 고르세요.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                CrewGroup.entries.forEach { g ->
                    val isCurrent = g == currentGroup
                    OutlinedCard(
                        onClick = { onPickGroup(g) },
                        border = if (isCurrent) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                        else CardDefaults.outlinedCardBorder(),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp)) {
                            Text(g.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                            Text(
                                "${Bundled.patternFor(g).length}칸 교번 순환" + if (isCurrent) " · 현재 선택" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                // 2단계: 근무 그리드
                val pattern = Bundled.patternFor(picker.group)
                val days = ChronoUnit.DAYS.between(pattern.anchorDate, picker.date).toInt()
                val currentIndex = if (picker.group == currentGroup)
                    Math.floorMod(days + currentOffset, pattern.length) else -1

                Text(
                    "근무선택  2/2 · ${picker.group.label}",
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
