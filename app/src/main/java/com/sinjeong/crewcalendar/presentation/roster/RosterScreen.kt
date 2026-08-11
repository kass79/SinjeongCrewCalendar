package com.sinjeong.crewcalendar.presentation.roster

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sinjeong.crewcalendar.domain.model.*
import com.sinjeong.crewcalendar.domain.repository.MateRepository
import com.sinjeong.crewcalendar.domain.repository.RosterEntry
import com.sinjeong.crewcalendar.domain.repository.RosterRepository
import com.sinjeong.crewcalendar.domain.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import com.sinjeong.crewcalendar.presentation.theme.DutyColors
import com.sinjeong.crewcalendar.presentation.theme.LocalDutyColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class RosterViewModel @Inject constructor(
    userRepo: UserRepository,
    private val mateRepo: MateRepository,
    rosterRepo: RosterRepository,
) : ViewModel() {
    val user: StateFlow<User?> = userRepo.observeMe()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val mates: StateFlow<List<Mate>> = mateRepo.observeMates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Firebase 연동 시: 로그인 근무자 실데이터 (없으면 빈 목록 → 내장 명단만) */
    val liveUsers: StateFlow<List<RosterEntry>> = rosterRepo.observeUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val monthFlow = MutableStateFlow(YearMonth.now())
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val monthOverrides: StateFlow<Map<String, Map<LocalDate, String>>> =
        monthFlow.flatMapLatest { rosterRepo.observeMonthOverrides(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun setMonth(m: YearMonth) { monthFlow.value = m }

    /**
     * 즐겨찾기 지정/해제. 동료로 등록 안 된 사람(내장 명단·로그인 근무자)은 이 시점에 Mate로 만든다.
     * 해제는 favGroup=null — Mate는 지우지 않는다(수동 등록분 보호).
     * 매칭은 **이름+소속** — 이름만으로 찾으면 동명이인(김지환 기관사/차장)이 서로를 덮어쓴다.
     */
    fun setFav(name: String, group: CrewGroup, offset: Int, fav: FavGroup?) {
        val existing = mates.value.find { it.name == name && it.group == group }
        viewModelScope.launch {
            mateRepo.upsert(existing?.copy(favGroup = fav) ?: Mate(name, group, offset, fav))
        }
    }
}

/** 동료 식별 키 — 이름만으로는 그룹 간 동명이인 3쌍이 충돌한다 */
private fun mateKey(name: String, group: CrewGroup) = "$name|${group.name}"

private data class Person(
    val name: String,
    val group: CrewGroup,
    val offset: Int,
    val isMe: Boolean,
    /** 로그인 사용자(사번) — 근무변경 실시간 반영 대상 */
    val uid: String? = null,
)

/** 즐겨찾기·전화번호는 " (나)" 꼬리표 없는 실제 이름으로 매칭한다 */
private val Person.cleanName: String get() = name.removeSuffix(" (나)").trim()

private val Person.key: String get() = mateKey(cleanName, group)

/**
 * 동료근무 — 사업소 전체 근무표 매트릭스 (가로 날짜 × 세로 이름 ㄱㄴㄷ).
 * 체험판: 나 + 이 폰에 등록한 동료. 서버 연동 시 로그인 근무자 전체로 자동 확장.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RosterScreen(onBack: () -> Unit, viewModel: RosterViewModel = hiltViewModel()) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val mates by viewModel.mates.collectAsStateWithLifecycle()
    val liveUsers by viewModel.liveUsers.collectAsStateWithLifecycle()
    val monthOverrides by viewModel.monthOverrides.collectAsStateWithLifecycle()
    var month by remember { mutableStateOf(YearMonth.now()) }
    LaunchedEffect(month) { viewModel.setMonth(month) }
    var filter by remember { mutableStateOf<CrewGroup?>(null) }
    var query by remember { mutableStateOf("") }
    var favOnly by remember { mutableStateOf(false) }
    var dialTarget by remember { mutableStateOf<Person?>(null) }
    // ★ = 동료 탭에서 즐겨찾기 그룹에 넣은 사람들 (이름+소속 키)
    val favKeys = remember(mates) {
        mates.filter { it.favGroup != null }.map { mateKey(it.name, it.group) }.toSet()
    }
    val duty = LocalDutyColors.current

    val people = remember(user, mates, liveUsers) {
        val myGroup = user?.let {
            Bundled.groupFor(it.patternId)?.let { grp ->
                if (it.role == CrewRole.CONDUCTOR) CrewGroup.MAIN_CONDUCTOR else grp
            } ?: CrewGroup.BRANCH
        }
        val me = user?.let {
            Person("${it.name} (나)", myGroup!!, it.patternOffset, isMe = true, uid = it.uid)
        }
        // 우선순위: 나 → 로그인 근무자(실데이터) → 수동등록 동료 → 내장 명단.
        // 중복 제거는 **이름+소속** — 이름만 보면 동명이인(기관사/차장 김지환)이 서로를 지운다.
        val taken = mutableSetOf<String>()
        user?.let { taken += mateKey(it.name, myGroup!!) }
        val live = liveUsers.filter { mateKey(it.name, it.group) !in taken && it.uid != user?.uid }
            .map {
                taken += mateKey(it.name, it.group)
                Person(it.name, it.group, it.patternOffset, isMe = false, uid = it.uid)
            }
        val manual = mates.filter { mateKey(it.name, it.group) !in taken }
            .map {
                taken += mateKey(it.name, it.group)
                Person(it.name, it.group, it.patternOffset, isMe = false)
            }
        val bundled = CrewGroup.entries.flatMap { g ->
            BundledRoster.forGroup(g)
                .filterNot { mateKey(it.first, g) in taken }
                .map { (name, off) -> Person(name, g, off, isMe = false) }
        }
        listOfNotNull(me) + live + manual + bundled
    }

    // v1.6.16: 한 화면에 날짜를 더 많이 담고(접힘 9일 → 14일) 근무 글자는 오히려 키웠다.
    // 칸이 좁아진 만큼 긴 코드("대11비")는 PersonRow의 자동 축소가 받는다.
    val cellW = 26.dp
    val nameW = 52.dp
    val hScroll = rememberScrollState()
    val density = LocalDensity.current

    // 이번 달이면 오늘 열 근처로 자동 스크롤
    LaunchedEffect(month) {
        if (month == YearMonth.now()) {
            val px = with(density) { (cellW * (LocalDate.now().dayOfMonth - 3)).toPx() }
            hScroll.scrollTo(px.toInt().coerceAtLeast(0))
        } else hScroll.scrollTo(0)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { month = month.minusMonths(1) }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "이전 달")
                        }
                        Text("동료근무 ${month.year}.${month.monthValue}", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                        IconButton(onClick = { month = month.plusMonths(1) }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "다음 달")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // 소속 5종 + 전체 + ★ 이라 한 줄에 안 들어간다 → 가로 스크롤
            Row(
                Modifier.horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("전체", fontSize = 11.sp) })
                CrewGroup.entries.forEach { g ->
                    FilterChip(selected = filter == g, onClick = { filter = g }, label = { Text(g.label, fontSize = 11.sp) })
                }
                FilterChip(selected = favOnly, onClick = { favOnly = !favOnly }, label = { Text("★", fontSize = 12.sp) })
            }
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("이름 검색", fontSize = 12.sp) },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                trailingIcon = if (query.isNotEmpty()) {
                    { TextButton(onClick = { query = "" }) { Text("지움", fontSize = 11.sp) } }
                } else null,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 6.dp),
            )

            val nDays = month.lengthOfMonth()
            // 헤더: 날짜/요일
            Row {
                Box(Modifier.width(nameW).padding(horizontal = 4.dp, vertical = 2.dp)) {
                    Text("이름", fontSize = 9.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(Modifier.horizontalScroll(hScroll)) {
                    (1..nDays).forEach { d ->
                        val date = month.atDay(d)
                        val isToday = date == LocalDate.now()
                        val hol = Bundled.PUBLIC_HOLIDAYS.containsKey(date)
                        Column(
                            Modifier.width(cellW)
                                .background(
                                    if (isToday) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .padding(vertical = 1.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            val c = when {
                                isToday -> MaterialTheme.colorScheme.onPrimaryContainer
                                hol || date.dayOfWeek.value == 7 -> duty.sunday
                                date.dayOfWeek.value == 6 -> duty.saturday
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Text("$d", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = c)
                            Text(
                                listOf("월", "화", "수", "목", "금", "토", "일")[date.dayOfWeek.value - 1],
                                fontSize = 8.sp, color = c,
                            )
                        }
                    }
                }
            }
            HorizontalDivider()

            // 그룹을 추가하면 여기도 늘어나야 한다 — 빠뜨리면 그 소속 인원이 화면에서 통째로 사라진다
            val sections = listOf(
                CrewGroup.MAIN_DRIVER, CrewGroup.MAIN_CONDUCTOR, CrewGroup.BRANCH,
                CrewGroup.SHIFT_4_2, CrewGroup.OFFICE_DAY,
            ).filter { filter == null || filter == it }
            LazyColumn {
                sections.forEach { g ->
                    val q = query.trim()
                    val members = people.filter {
                        it.group == g &&
                            (q.isEmpty() || it.name.contains(q)) &&
                            (!favOnly || it.isMe || it.key in favKeys)
                    }.sortedWith(compareBy({ !it.isMe }, { it.key !in favKeys }, { it.name }))
                    if ((q.isNotEmpty() || favOnly) && members.isEmpty()) return@forEach
                    item(key = "sec-${g.name}") {
                        Text(
                            g.label,
                            fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                    items(members.size, key = { "${g.name}-$it-${members[it].name}" }) { i ->
                        val p = members[i]
                        PersonRow(p, month, cellW, nameW, hScroll, duty,
                            isFav = p.key in favKeys,
                            overrides = p.uid?.let { monthOverrides[it] } ?: emptyMap(),
                            onNameClick = { dialTarget = p })
                    }
                }
            }
        }
    }

    dialTarget?.let { person ->
        DialSheet(
            person,
            fav = mates.find { it.name == person.cleanName && it.group == person.group }?.favGroup,
            onSetFav = { viewModel.setFav(person.cleanName, person.group, person.offset, it) },
            onDismiss = { dialTarget = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun DialSheet(
    person: Person,
    fav: FavGroup?,
    onSetFav: (FavGroup?) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val duty = LocalDutyColors.current
    val cleanName = person.cleanName
    val phone = BundledStaff.phoneFor(cleanName, person.group == CrewGroup.MAIN_CONDUCTOR)
    var favMenu by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(cleanName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                Box {
                    IconButton(onClick = { favMenu = true }) {
                        Icon(
                            if (fav != null) Icons.Default.Star else Icons.Outlined.StarOutline,
                            "즐겨찾기 그룹",
                            tint = if (fav != null) duty.onStandby else MaterialTheme.colorScheme.outline,
                        )
                    }
                    DropdownMenu(expanded = favMenu, onDismissRequest = { favMenu = false }) {
                        FavGroup.entries.forEach { g ->
                            DropdownMenuItem(
                                text = { Text("★ ${g.label}" + if (fav == g) " ✓" else "") },
                                onClick = { onSetFav(g); favMenu = false },
                            )
                        }
                        if (fav != null) DropdownMenuItem(
                            text = { Text("즐겨찾기 해제") },
                            onClick = { onSetFav(null); favMenu = false },
                        )
                    }
                }
            }
            Text(
                person.group.label + (fav?.let { " · ★ ${it.label}" } ?: ""),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (phone != null) {
                Text(
                    phone, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    modifier = Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = {
                            clipboard.setText(AnnotatedString(phone))
                            android.widget.Toast.makeText(context, "복사됨", android.widget.Toast.LENGTH_SHORT).show()
                        },
                    ),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = {
                        runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:" + phone))) }
                        onDismiss()
                    }, modifier = Modifier.weight(1f)) { Text("전화") }
                    OutlinedButton(onClick = {
                        runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:" + phone))) }
                        onDismiss()
                    }, modifier = Modifier.weight(1f)) { Text("문자") }
                }
                Text("판독본 번호예요. 틀리면 관리자에게 알려주세요.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("등록된 전화번호가 없습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * 칸 폭에 맞을 때까지 글자를 줄이는 한 줄 텍스트.
 * 달력 칩(`MainCalendarScreen`)과 같은 방식 — 말줄임이 뜨는 순간 hasVisualOverflow가 false로
 * 떨어지므로 `isLineEllipsized`도 같이 본다.
 */
@Composable
private fun AutoFitText(
    text: String,
    base: androidx.compose.ui.unit.TextUnit,
    min: androidx.compose.ui.unit.TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
    align: TextAlign = TextAlign.Start,
) {
    var fit by remember(text, base) { mutableStateOf(base) }
    Text(
        text,
        modifier = modifier,
        fontSize = fit, lineHeight = fit * 1.1,
        fontWeight = FontWeight.ExtraBold,
        color = color,
        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, textAlign = align,
        onTextLayout = {
            val cut = it.hasVisualOverflow || (it.lineCount > 0 && it.isLineEllipsized(0))
            if (cut && fit > min) fit *= 0.92f
        },
    )
}

@Composable
private fun PersonRow(
    p: Person,
    month: YearMonth,
    cellW: androidx.compose.ui.unit.Dp,
    nameW: androidx.compose.ui.unit.Dp,
    hScroll: androidx.compose.foundation.ScrollState,
    duty: DutyColors,
    isFav: Boolean = false,
    /** 근무변경 실시간 반영 (날짜 → 변경 근무, Firebase 연동 시) */
    overrides: Map<LocalDate, String> = emptyMap(),
    onNameClick: () -> Unit = {},
) {
    val pattern = Bundled.patternFor(p.group)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.width(nameW).clickable { onNameClick() }.padding(horizontal = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 이름 칸을 64→52dp로 줄였으므로 말줄임 대신 자동 축소 — `강민성 (나)`가 더는 안 잘린다
            AutoFitText(
                p.name,
                base = 10.5.sp, min = 6.5.sp,
                color = if (p.isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (isFav) Text("★", fontSize = 8.sp, color = duty.onStandby)
        }
        Row(Modifier.horizontalScroll(hScroll)) {
            (1..month.lengthOfMonth()).forEach { d ->
                val date = month.atDay(d)
                val code = overrides[date]?.let { DutyCode.parse(it) } ?: pattern.dutyOn(date, p.offset)
                val (bg, fg) = when (code.type) {
                    DutyType.MAIN_DAY, DutyType.OFFICE -> duty.main to duty.onMain
                    DutyType.MAIN_NIGHT, DutyType.BRANCH_NIGHT, DutyType.SPECIAL -> duty.night to duty.onNight
                    DutyType.POST_NIGHT -> duty.off to duty.onOff
                    DutyType.REST, DutyType.BRANCH_REST -> duty.rest to duty.onRest
                    DutyType.STANDBY, DutyType.BRANCH_STANDBY -> duty.standby to duty.onStandby
                    DutyType.BRANCH -> duty.main to duty.onMain
                    DutyType.ETC -> Color.Transparent to MaterialTheme.colorScheme.onSurfaceVariant
                }
                Box(Modifier.width(cellW).padding(horizontal = 1.dp, vertical = 0.5.dp)) {
                    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(5.dp)) {
                        // 대부분 코드는 11sp로 커지고, 드문 4글자("대11비")만 들어갈 때까지 줄어든다
                        AutoFitText(
                            code.display.ifBlank { "·" },
                            base = 11.sp, min = 6.5.sp,
                            color = fg,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            align = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
