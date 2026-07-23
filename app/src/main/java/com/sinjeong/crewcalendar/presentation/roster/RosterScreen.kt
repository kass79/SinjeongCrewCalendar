package com.sinjeong.crewcalendar.presentation.roster

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class RosterViewModel @Inject constructor(
    userRepo: UserRepository,
    mateRepo: MateRepository,
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
}

private data class Person(
    val name: String,
    val group: CrewGroup,
    val offset: Int,
    val isMe: Boolean,
    /** 로그인 사용자(사번) — 근무변경 실시간 반영 대상 */
    val uid: String? = null,
)

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
    // ★ = 동료 탭에서 즐겨찾기 그룹에 넣은 사람들
    val favNames = remember(mates) { mates.filter { it.favGroup != null }.map { it.name }.toSet() }
    val duty = LocalDutyColors.current

    val people = remember(user, mates, liveUsers) {
        val me = user?.let {
            val g = Bundled.groupFor(it.patternId)?.let { grp ->
                if (it.role == CrewRole.CONDUCTOR) CrewGroup.MAIN_CONDUCTOR else grp
            } ?: CrewGroup.BRANCH
            Person("${it.name} (나)", g, it.patternOffset, isMe = true, uid = it.uid)
        }
        // 우선순위: 나 → 로그인 근무자(실데이터) → 수동등록 동료 → 내장 명단. 이름으로 중복 제거
        val taken = mutableSetOf<String>()
        user?.let { taken += it.name }
        val live = liveUsers.filter { it.name !in taken && it.uid != user?.uid }
            .map { taken += it.name; Person(it.name, it.group, it.patternOffset, isMe = false, uid = it.uid) }
        val manual = mates.filter { it.name !in taken }
            .map { taken += it.name; Person(it.name, it.group, it.patternOffset, isMe = false) }
        val bundled = CrewGroup.entries.flatMap { g ->
            BundledRoster.forGroup(g)
                .filterNot { it.first in taken }
                .map { (name, off) -> Person(name, g, off, isMe = false) }
        }
        listOfNotNull(me) + live + manual + bundled
    }

    val cellW = 38.dp
    val nameW = 64.dp
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
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("전체", fontSize = 11.sp) })
                CrewGroup.entries.forEach { g ->
                    FilterChip(selected = filter == g, onClick = { filter = g }, label = { Text(g.label, fontSize = 11.sp) })
                }
                FilterChip(selected = favOnly, onClick = { favOnly = !favOnly }, label = { Text("★", fontSize = 12.sp) })
                Spacer(Modifier.weight(1f))
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
                Box(Modifier.width(nameW).padding(4.dp)) {
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
                                .padding(vertical = 2.dp),
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

            val sections = listOf(CrewGroup.MAIN_DRIVER, CrewGroup.MAIN_CONDUCTOR, CrewGroup.BRANCH)
                .filter { filter == null || filter == it }
            LazyColumn {
                sections.forEach { g ->
                    val q = query.trim()
                    val members = people.filter {
                        it.group == g &&
                            (q.isEmpty() || it.name.contains(q)) &&
                            (!favOnly || it.isMe || it.name in favNames)
                    }.sortedWith(compareBy({ !it.isMe }, { it.name }))
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
                            overrides = p.uid?.let { monthOverrides[it] } ?: emptyMap(),
                            onNameClick = { dialTarget = p })
                    }
                }
            }
        }
    }

    dialTarget?.let { person ->
        DialSheet(person, onDismiss = { dialTarget = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialSheet(person: Person, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val cleanName = person.name.removeSuffix(" (나)").trim()
    val phone = BundledStaff.phoneFor(cleanName, person.group == CrewGroup.MAIN_CONDUCTOR)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(cleanName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text(person.group.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (phone != null) {
                Text(phone, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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

@Composable
private fun PersonRow(
    p: Person,
    month: YearMonth,
    cellW: androidx.compose.ui.unit.Dp,
    nameW: androidx.compose.ui.unit.Dp,
    hScroll: androidx.compose.foundation.ScrollState,
    duty: DutyColors,
    /** 근무변경 실시간 반영 (날짜 → 변경 근무, Firebase 연동 시) */
    overrides: Map<LocalDate, String> = emptyMap(),
    onNameClick: () -> Unit = {},
) {
    val pattern = Bundled.patternFor(p.group)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            p.name,
            fontSize = 10.sp, fontWeight = FontWeight.Bold,
            color = if (p.isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(nameW).clickable { onNameClick() }.padding(horizontal = 4.dp),
        )
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
                Box(Modifier.width(cellW).padding(1.dp)) {
                    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(5.dp)) {
                        Text(
                            code.display.ifBlank { "·" },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            fontSize = 8.5.sp, fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center, maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
