package com.sinjeong.crewcalendar.presentation.roster

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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

/**
 * 동료근무 — 사업소 전체 근무표 매트릭스 (가로 날짜 × 세로 이름 ㄱㄴㄷ).
 * 매트릭스 자체는 `DutyMatrix.kt` 공용 컴포저블이고 여기선 `Dense` 크기로 쓴다.
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
    var dialTarget by remember { mutableStateOf<MatrixPerson?>(null) }
    // ★ = 동료 탭에서 즐겨찾기 그룹에 넣은 사람들 (이름+소속 키)
    val favKeys = remember(mates) {
        mates.filter { it.favGroup != null }.map { mateKey(it.name, it.group) }.toSet()
    }
    val duty = LocalDutyColors.current

    val people = remember(user, mates, liveUsers) {
        val me = meAsPerson(user)
        // 우선순위: 나 → 로그인 근무자(실데이터) → 수동등록 동료 → 내장 명단.
        // 중복 제거는 **이름+소속** — 이름만 보면 동명이인(기관사/차장 김지환)이 서로를 지운다.
        val taken = mutableSetOf<String>()
        me?.let { taken += it.key }
        val live = liveUsers.filter { mateKey(it.name, it.group) !in taken && it.uid != user?.uid }
            .map {
                taken += mateKey(it.name, it.group)
                MatrixPerson(it.name, it.group, it.patternOffset, isMe = false, uid = it.uid)
            }
        val manual = mates.filter { mateKey(it.name, it.group) !in taken }
            .map {
                taken += mateKey(it.name, it.group)
                MatrixPerson(it.name, it.group, it.patternOffset, isMe = false)
            }
        val bundled = CrewGroup.entries.flatMap { g ->
            BundledRoster.forGroup(g)
                .filterNot { mateKey(it.first, g) in taken }
                .map { (name, off) -> MatrixPerson(name, g, off, isMe = false) }
        }
        listOfNotNull(me) + live + manual + bundled
    }

    // v1.6.17: v1.6.16의 26dp는 "너무 작아서 읽기 힘들다"는 피드백 → 32dp로 회복.
    // 글자 자동 축소(AutoFitText)는 유지 — 긴 코드만 줄고 대부분은 11sp 그대로다.
    val m = MatrixMetrics.Dense
    val hScroll = rememberMatrixScroll(month, m.cellW)
    // 필터를 바꾸면 명단이 통째로 달라지는데 세로 위치는 그대로라 중간부터 보였다 — "나" 행이 화면 밖.
    // 가로(날짜) 스크롤은 헤더와 공유하는 상태라 건드리지 않는다.
    val listState = rememberLazyListState()
    LaunchedEffect(filter, favOnly) { listState.scrollToItem(0) }

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

            MatrixDateHeader(month, m, hScroll, duty)
            HorizontalDivider()

            // 그룹을 추가하면 여기도 늘어나야 한다 — 빠뜨리면 그 소속 인원이 화면에서 통째로 사라진다
            val sections = listOf(
                CrewGroup.MAIN_DRIVER, CrewGroup.MAIN_CONDUCTOR, CrewGroup.BRANCH,
                CrewGroup.SHIFT_4_2, CrewGroup.OFFICE_DAY,
            ).filter { filter == null || filter == it }
            LazyColumn(state = listState) {
                sections.forEach { g ->
                    val q = query.trim()
                    val members = people.filter {
                        it.group == g &&
                            (q.isEmpty() || it.name.contains(q)) &&
                            (!favOnly || it.isMe || it.key in favKeys)
                    }.sortedWith(compareBy({ !it.isMe }, { it.key !in favKeys }, { it.name }))
                    if ((q.isNotEmpty() || favOnly) && members.isEmpty()) return@forEach
                    // 섹션 경계를 또렷하게(v1.6.21): 위 구분선 + 왼쪽 색막대 + 인원수.
                    // 종전엔 surfaceVariant 배경뿐이라 스크롤 중 소속이 바뀐 걸 놓치기 쉬웠다.
                    item(key = "sec-${g.name}") {
                        Column {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            Row(
                                Modifier.fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier.padding(start = 6.dp).width(3.dp).height(11.dp)
                                        .background(MaterialTheme.colorScheme.primary),
                                )
                                Text(
                                    g.label,
                                    fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(start = 5.dp),
                                )
                                Text(
                                    " ${members.size}명",
                                    fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    items(members.size, key = { "${g.name}-$it-${members[it].name}" }) { i ->
                        val p = members[i]
                        MatrixRow(p, month, m, hScroll, duty,
                            isFav = p.key in favKeys,
                            overrides = p.uid?.let { monthOverrides[it] } ?: emptyMap(),
                            zebra = i % 2 == 1,
                            onNameClick = { dialTarget = p })
                    }
                }
            }
        }
    }

    dialTarget?.let { person ->
        PersonSheet(
            person,
            fav = mates.find { it.name == person.cleanName && it.group == person.group }?.favGroup,
            onSetFav = { viewModel.setFav(person.cleanName, person.group, person.offset, it) },
            onDismiss = { dialTarget = null },
        )
    }
}
