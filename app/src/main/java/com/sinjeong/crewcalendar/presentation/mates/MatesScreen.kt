package com.sinjeong.crewcalendar.presentation.mates

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.sinjeong.crewcalendar.presentation.roster.*
import com.sinjeong.crewcalendar.presentation.theme.LocalDutyColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class MatesViewModel @Inject constructor(
    private val mateRepo: MateRepository,
    userRepo: UserRepository,
    private val rosterRepo: RosterRepository,
) : ViewModel() {
    /**
     * 보고 있는 구간 = **오늘부터 한 달**을 0으로 두고 한 달씩 민 값(v1.6.42 ⑦).
     * v1.6.39에서 월 이동 화살표를 없앴는데 사용자가 다시 요청했다:
     * *"한달보여주는건 좋은데 다음달 넘어가는 기능이 없네?"*
     * 달(月) 단위가 아니라 **구간** 단위다 — 1이면 9/21~10/20처럼 오늘 날짜를 유지한 채 밀린다.
     * 0 아래로는 안 간다(*"과거는 필요 없다"* — 지난 근무는 달력 탭에서 본다).
     */
    private val _period = MutableStateFlow(0)
    val period: StateFlow<Int> = _period.asStateFlow()

    fun movePeriod(delta: Int) { _period.update { (it + delta).coerceAtLeast(0) } }
    val mates: StateFlow<List<Mate>> = mateRepo.observeMates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val user: StateFlow<User?> = userRepo.observeMe()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Firebase 연동 시: 로그인 근무자 실데이터 (없으면 빈 목록 → 내장 명단만) */
    val liveUsers: StateFlow<List<RosterEntry>> = rosterRepo.observeUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 근무변경 실시간 반영 (Firebase 미연동이면 빈 맵).
     *
     * 표시 범위가 **오늘부터 한 달**이라 달을 두 개 걸친다(v1.6.39). `observeMonthOverrides`는
     * 월 단위라 이번 달·다음 달 두 벌을 받아 uid별로 합쳐야 한다 — 한 달치만 보면
     * 경계 너머(다음 달 초)의 근무변경이 통째로 빠져 **원래 교번**이 그려진다. 조용히 틀리는 자리다.
     *
     * 달이 바뀌는 순간은 신경 쓰지 않는다. 화면의 `today`도 진입 시점에 고정되므로
     * 자정을 넘겨 앱을 계속 켜 두면 둘이 같이 하루 낡을 뿐 서로 어긋나지는 않는다.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val monthOverrides: StateFlow<Map<String, Map<LocalDate, String>>> = _period.flatMapLatest { p ->
        // 구간은 길이가 정확히 한 달이라 걸치는 달은 언제나 두 개다(시작 달 + 그 다음 달)
        val ym = YearMonth.now().plusMonths(p.toLong())
        combine(
            rosterRepo.observeMonthOverrides(ym),
            rosterRepo.observeMonthOverrides(ym.plusMonths(1)),
        ) { thisMonth, nextMonth ->
            (thisMonth.keys + nextMonth.keys).associateWith {
                (thisMonth[it] ?: emptyMap()) + (nextMonth[it] ?: emptyMap())
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** 등록: 오늘 근무 위치(patternIndex)로 offset 계산 — 근무선택과 같은 원리 */
    fun addMate(name: String, group: CrewGroup, todayPatternIndex: Int) {
        val pattern = Bundled.patternFor(group)
        val days = ChronoUnit.DAYS.between(pattern.anchorDate, LocalDate.now()).toInt()
        val offset = Math.floorMod(todayPatternIndex - days, pattern.length)
        viewModelScope.launch { mateRepo.upsert(Mate(name.trim(), group, offset)) }
    }

    /**
     * 수정: 저장 키가 "이름|소속"이라 둘 중 하나라도 바뀌면 **옛 키를 먼저 지워야** 유령 행이 안 남는다.
     * ★즐겨찾기 그룹은 그대로 옮긴다 — 이름 오타를 고쳤다고 ★이 풀리면 안 된다.
     */
    fun editMate(old: Mate, name: String, group: CrewGroup, todayPatternIndex: Int) {
        val offset = Bundled.patternFor(group).offsetFor(LocalDate.now(), todayPatternIndex)
        viewModelScope.launch {
            if (old.name != name.trim() || old.group != group) mateRepo.remove(old)
            mateRepo.upsert(Mate(name.trim(), group, offset, old.favGroup))
        }
    }

    /**
     * 즐겨찾기 지정/해제. 동료로 등록 안 된 사람(내장 명단·로그인 근무자·본인)은 이 시점에 Mate로 만든다.
     * 해제는 favGroup=null — Mate는 지우지 않는다(수동 등록분 보호).
     * 매칭은 **이름+소속** — 이름만으로 찾으면 동명이인(김지환 기관사/차장)이 서로를 덮어쓴다.
     */
    fun setFav(name: String, group: CrewGroup, offset: Int, fav: FavGroup?) {
        val existing = mates.value.find { it.name == name && it.group == group }
        viewModelScope.launch {
            mateRepo.upsert(existing?.copy(favGroup = fav) ?: Mate(name, group, offset, fav))
        }
    }

    fun remove(mate: Mate) {
        viewModelScope.launch { mateRepo.remove(mate) }
    }
}

/**
 * 카테고리 칩 2행 3열 (v1.6.39, 사용자가 배치까지 지정).
 *
 *     본선 기관사   본선 차장   신정지선
 *     4조2교대     통상근무    ★즐겨찾기
 *
 * `null` = ★즐겨찾기(저장한 동료 + 본인). 나머지는 그 소속 전원.
 *
 * **아무 칩도 안 눌린 상태 = 전체 명단**이고 그게 진입 기본값이다(v1.6.42 ⑤ —
 * *"동료탭에 들어가면 먼저 전체를 보여줘야지..?"*). 칩은 **필터로만** 동작한다:
 * 누르면 그 갈래만, 눌린 칩을 다시 누르면 해제되어 전체로 돌아온다.
 * 칸을 하나 더 만들지 않으므로 v1.6.39의 2행 3열 배치가 그대로 유지된다.
 */
private val CATEGORY_ROWS: List<List<CrewGroup?>> = listOf(
    listOf(CrewGroup.MAIN_DRIVER, CrewGroup.MAIN_CONDUCTOR, CrewGroup.BRANCH),
    listOf(CrewGroup.SHIFT_4_2, CrewGroup.OFFICE_DAY, null),
)

/**
 * 동료 탭 — v1.6.39에서 상단바 `동료근무`(RosterScreen)를 흡수한 **통합 화면**.
 *
 * 종전엔 같은 매트릭스를 두 화면이 그렸다. 상단바 동료근무는 사업소 전 인원, 하단 동료 탭은
 * 저장한 동료. 그런데 즐겨찾기를 지정하려면 동료근무로 들어가야 했고 거기 이미 동료가 다 나오니
 * 사용자 눈엔 같은 화면이 둘이었다("헤더에 동료근무를 없애야 할듯").
 * → 카테고리 칩 6개(소속 5 + ★즐겨찾기)로 갈라 한 화면에 넣었다.
 * ★즐겨찾기를 고르면 그 아래에 **★그룹 하위 필터**(전체·동호회·우리 조·기타)가 한 줄 더 펼쳐진다
 * (v1.6.40 복원 — v1.6.39에서 2행 3열로 정리하며 빠졌던 것). 다른 칩을 고르면 도로 접힌다.
 *
 * **첫 화면 = 전체 명단**(v1.6.42 ⑤). 칩은 고르는 것이 아니라 **거는 것**이고, 아무것도 안 걸린
 * 상태가 기본이다. 종전 기본값이던 ★즐겨찾기는 담은 동료가 없으면 빈 화면으로 시작했다
 * (*"동료탭에 들어가면 먼저 전체를 보여줘야지..?"*).
 *
 * 표시 범위는 **구간 시작일부터 한 달** — v1.6.38의 "오늘~말일"을 대체한다. 말일이 가까우면
 * 칸이 두세 개만 남아 화면 대부분이 비던 문제를 없앤다. 대신 **달 경계를 넘으므로**:
 *  · 헤더 날짜는 달이 바뀌는 칸에 `9/1`처럼 월을 적는다([MatrixDateHeader]).
 *  · 근무변경은 두 달치를 합쳐 받는다([MatesViewModel.monthOverrides]).
 *  · **‹ › 구간 이동**은 v1.6.39에서 지웠다가 v1.6.42 ⑦에서 되살렸다. 달(月) 이동이 아니라
 *    구간 이동이다 — › 한 번에 `8/21~9/20` → `9/21~10/20`. 과거 방향은 오늘 구간에서 막는다
 *    ([MatesViewModel.period]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatesScreen(viewModel: MatesViewModel = hiltViewModel()) {
    val mates by viewModel.mates.collectAsStateWithLifecycle()
    val user by viewModel.user.collectAsStateWithLifecycle()
    val liveUsers by viewModel.liveUsers.collectAsStateWithLifecycle()
    val monthOverrides by viewModel.monthOverrides.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    /** 선택된 소속 칩. null = 소속 필터 없음 */
    var category by remember { mutableStateOf<CrewGroup?>(null) }
    /** ★즐겨찾기 칩. `category`와 배타 — **둘 다 꺼져 있으면 전체 명단**(진입 기본값, v1.6.42 ⑤) */
    var favMode by remember { mutableStateOf(false) }
    /** ★그룹 하위 필터. `null` = 전체. ★즐겨찾기를 고른 동안에만 쓰인다(셋째 줄). */
    var favFilter by remember { mutableStateOf<FavGroup?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var sheetTarget by remember { mutableStateOf<MatrixPerson?>(null) }
    var editTarget by remember { mutableStateOf<Mate?>(null) }

    val duty = LocalDutyColors.current
    val m = MatrixMetrics.Roomy
    val period by viewModel.period.collectAsStateWithLifecycle()
    // 한 구간 = 시작일부터 한 달. `plusMonths(1)`이라 2월이면 28일, 8월이면 31칸.
    // period 0이면 시작일이 오늘, 1이면 한 달 뒤(9/21~10/20) — 헤더 표기와 같은 값이다.
    val dates = remember(period) {
        val start = LocalDate.now().plusMonths(period.toLong())
        val end = start.plusMonths(1)
        generateSequence(start) { it.plusDays(1) }.takeWhile { it < end }.toList()
    }
    // 첫 칸이 언제나 구간 시작일이라 가로 시작 위치는 0 — 헤더와 전 행이 이 하나를 공유해 같이 움직인다.
    val hScroll = rememberScrollState()

    val q = query.trim()
    val me = meAsPerson(user)
    val favKeys = remember(mates) {
        mates.filter { it.favGroup != null }.map { mateKey(it.name, it.group) }.toSet()
    }

    // 소속 칩용 전체 명단 (종전 동료근무와 같은 합성 규칙).
    // 우선순위: 나 → 로그인 근무자(실데이터) → 수동등록 동료 → 내장 명단.
    // 중복 제거는 **이름+소속** — 이름만 보면 동명이인(기관사/차장 김지환)이 서로를 지운다.
    val roster = remember(user, mates, liveUsers) {
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

    // ★그룹별 인원수 (셋째 줄 칩에 붙는다). 본인 행은 필터와 무관하게 늘 보이므로 세지 않는다 —
    // 세면 `우리 조 3`인데 동료는 둘만 보이는 어긋남이 생긴다.
    val favCounts = remember(mates, me) {
        FavGroup.entries.associateWith { g ->
            mates.count { it.favGroup == g && mateKey(it.name, it.group) != me?.key }
        }
    }

    val rows = if (favMode) {
        // ★즐겨찾기 = 저장한 동료 + 본인. ★그룹이 지정된 사람이 위로, 본인은 항상 맨 위
        // (내 근무가 모든 비교의 기준선이다 — ★그룹 필터를 걸어도 빠지지 않는다).
        listOfNotNull(me?.takeIf { q.isEmpty() || it.cleanName.contains(q) }) +
            mates.filter { q.isEmpty() || it.name.contains(q) }
                .filter { favFilter == null || it.favGroup == favFilter }
                .filter { mateKey(it.name, it.group) != me?.key }
                .sortedWith(compareBy({ it.favGroup == null }, { it.favGroup?.ordinal ?: 9 }, { it.name }))
                .map { MatrixPerson(it.name, it.group, it.patternOffset) }
    } else {
        // `category == null` = 전체(필터 없음). 한 줄로 두 경우를 다 본다 — 소속 칩은 필터일 뿐이다.
        // 전체일 땐 소속이 섞이므로 정렬 키에 소속을 끼워 같은 소속끼리 붙여 놓는다.
        roster.filter { (category == null || it.group == category) && (q.isEmpty() || it.name.contains(q)) }
            .sortedWith(compareBy({ !it.isMe }, { it.key !in favKeys }, { it.group.ordinal }, { it.name }))
    }

    // 칩을 바꾸면 명단이 통째로 달라지는데 세로 위치는 그대로라 중간부터 보였다 — "나" 행이 화면 밖.
    // 가로(날짜) 스크롤은 헤더와 공유하는 상태라 건드리지 않는다.
    val listState = rememberLazyListState()
    LaunchedEffect(category, favMode, favFilter) { listState.scrollToItem(0) }
    // 구간을 옮기면 가로 위치도 처음으로 — 안 하면 › 를 눌렀는데 화면은 구간 중간부터 보인다
    LaunchedEffect(period) { hScroll.scrollTo(0) }
    // ★에서 나오면 셋째 줄이 사라지므로 필터도 같이 풀어 둔다 —
    // 안 풀면 ★로 돌아왔을 때 보이지 않던 필터가 걸린 채라 "동료가 없어졌다"가 된다.
    LaunchedEffect(favMode) { if (!favMode) favFilter = null }

    Scaffold(
        topBar = {
            // 달력 헤더와 같은 컴팩트 방식(44dp). 기본 `TopAppBar`를 쓰면 AppRoot의 Scaffold가
            // 이미 얹은 상태바 인셋 **위에 한 번 더** 얹혀(64dp + 상태바) 화면 위쪽 근 90dp가
            // 통째로 비었다 — 실화면에서 잡은 것. 날짜를 한 줄이라도 더 보여 주는 게 맞다.
            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(
                    Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("동료", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                    Spacer(Modifier.width(4.dp))
                    // ⑦ 구간 이동. `‹`는 오늘 구간에서 꺼진다 — 과거로는 안 간다(v1.6.42).
                    IconButton(
                        onClick = { viewModel.movePeriod(-1) },
                        enabled = period > 0,
                        modifier = Modifier.size(30.dp),
                    ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "이전 구간", Modifier.size(22.dp)) }
                    Text(
                        "${dates.first().monthValue}/${dates.first().dayOfMonth}" +
                            " ~ ${dates.last().monthValue}/${dates.last().dayOfMonth}",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    IconButton(
                        onClick = { viewModel.movePeriod(1) },
                        modifier = Modifier.size(30.dp),
                    ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "다음 구간", Modifier.size(22.dp)) }
                    Spacer(Modifier.width(4.dp))
                    // v1.6.21의 섹션 인원수를 여기로 옮겼다 — 한 번에 한 소속만 보이므로
                    // 목록 안 섹션 머리글은 칩과 같은 말을 두 번 하는 셈이 된다.
                    Text(
                        "${rows.size}명",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, "동료 추가") }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("이름으로 검색", fontSize = 13.sp) }, singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                trailingIcon = if (query.isNotEmpty()) {
                    { TextButton(onClick = { query = "" }) { Text("지움", fontSize = 12.sp) } }
                } else null,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                shape = RoundedCornerShape(999.dp),
            )
            // 2행 3열 고정 — 가로 스크롤이 아니라 격자라 여섯 칸이 항상 다 보인다
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                CATEGORY_ROWS.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        row.forEach { g ->
                            // 눌린 칩을 다시 누르면 해제 = 전체로 복귀(v1.6.42 ⑤)
                            if (g == null) MatesChip("★즐겨찾기", favMode) {
                                favMode = !favMode
                                if (favMode) category = null
                            } else MatesChip(g.label, category == g) {
                                category = if (category == g) null else g
                                favMode = false
                            }
                        }
                    }
                }
            }

            // 셋째 줄 = ★그룹 하위 필터. **★즐겨찾기를 고른 동안에만** 나온다(v1.6.40 복원).
            // 항상 세 줄이면 "깔끔하게 2행 3열"이라는 v1.6.39 요청과 정면으로 어긋나고,
            // 소속 칩(98명 명단)에는 ★그룹이라는 개념 자체가 없어 걸 것도 없다.
            // 위 격자 밖에 두는 이유: 안에 넣으면 `spacedBy(5.dp)`가 숨어 있는 동안에도
            // 5dp를 차지해 화면이 그만큼 빈 채로 남는다.
            AnimatedVisibility(
                visible = favMode,
                enter = expandVertically(tween(180)) + fadeIn(tween(180)),
                exit = shrinkVertically(tween(180)) + fadeOut(tween(120)),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    // `전체`는 인원수를 안 붙인다 — 상단바가 이미 `N명`으로 같은 말을 하고 있다.
                    MatesChip("전체", favFilter == null) { favFilter = null }
                    FavGroup.entries.forEach { g ->
                        MatesChip("${g.label} ${favCounts[g] ?: 0}", favFilter == g) { favFilter = g }
                    }
                }
            }

            // 빈 상태는 **왜 비었는지 + 무엇을 하면 채워지는지**를 같이 적는다(v1.6.41 ③).
            // 조건 순서가 곧 우선순위다: 검색어 > ★그룹 필터 > 아무도 안 담음 > 그 소속에 사람 없음.
            //
            // `onlyMe` = ★즐겨찾기 화면에 내 행 하나만 남은 상태(담은 동료 0명 또는 ★그룹 필터가 0명).
            // **내 행은 필터와 무관하게 늘 들어 있어 `rows`가 절대 비지 않는다** — 따로 안 잡으면
            // 한 줄만 덩그러니 뜨고 아무 설명이 없다. 검색 중일 때는 제외(내가 검색에 걸린 정상 결과다).
            val onlyMe = favMode && q.isEmpty() && rows.none { !it.isMe }
            if (onlyMe || rows.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when {
                    q.isNotEmpty() -> EmptyHint(
                        "'$q' 와 맞는 이름이 없습니다",
                        "이름 일부만 넣어도 찾습니다. 다른 소속에 있는 사람이면 위 칩을 바꿔 보세요.",
                        "검색 지우기",
                    ) { query = "" }
                    favFilter != null -> EmptyHint(
                        "★${favFilter?.label}에 담긴 동료가 없습니다",
                        "이름을 눌러 뜨는 시트에서 ★그룹을 골라 담으면 여기 모입니다.",
                        "전체 보기",
                    ) { favFilter = null }
                    favMode -> EmptyHint(
                        "아직 담은 동료가 없습니다",
                        "★즐겨찾기를 다시 눌러 전체 명단으로 돌아간 뒤 이름을 누르면 ★로 담을 수 있습니다.\n" +
                            "담으면 내 근무와 날짜별로 나란히 비교됩니다.",
                        "전체 보기",
                    ) { favMode = false }
                    else -> EmptyHint("표시할 사람이 없습니다", "명단이 갱신되면 자동으로 나타납니다.")
                }
            } else {
                MatrixDateHeader(dates, m, hScroll, duty)
                HorizontalDivider()
                LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(rows.size, key = { rows[it].key }) { i ->
                        val p = rows[i]
                        MatrixRow(p, dates, m, hScroll, duty,
                            isFav = p.key in favKeys,
                            overrides = p.uid?.let { monthOverrides[it] } ?: emptyMap(),
                            zebra = i % 2 == 1,
                            onNameClick = { sheetTarget = p })
                    }
                }
            }
        }
    }

    sheetTarget?.let { person ->
        val mate = mates.find { it.name == person.cleanName && it.group == person.group }
        // 내장 명단에 있는 이름은 근무가 BundledRoster 값이라 고칠 게 없다 — 수동 등록분만 수정 가능
        val manual = mate != null &&
            BundledRoster.forGroup(mate.group).none { it.first == mate.name }
        PersonSheet(
            person,
            fav = mate?.favGroup,
            onSetFav = { viewModel.setFav(person.cleanName, person.group, person.offset, it) },
            onDismiss = { sheetTarget = null },
            // 본인 행은 지울 게 없다
            onRemove = if (mate != null && !person.isMe) ({ viewModel.remove(mate) }) else null,
            onEdit = if (manual && !person.isMe) ({ editTarget = mate }) else null,
        )
    }

    if (showAdd) {
        AddMateSheet(
            onAdd = { name, group, idx -> viewModel.addMate(name, group, idx); showAdd = false },
            onDismiss = { showAdd = false },
        )
    }

    editTarget?.let { target ->
        AddMateSheet(
            onAdd = { name, group, idx ->
                viewModel.editMate(target, name, group, idx); editTarget = null
            },
            onDismiss = { editTarget = null },
            edit = target,
        )
    }
}

/**
 * 빈 화면 안내 한 벌 — **제목 한 줄 + 무엇을 하면 채워지는지 + (있으면) 그걸 바로 하는 버튼**.
 * 일러스트는 안 넣는다. 화면이 비었을 때 필요한 건 그림이 아니라 다음 행동이다.
 */
@Composable
private fun EmptyHint(
    title: String,
    body: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        Modifier.padding(horizontal = 28.dp).padding(bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            title, textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold,
        )
        Text(
            body, textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (action != null && onAction != null) {
            Spacer(Modifier.height(2.dp))
            FilledTonalButton(onClick = onAction) { Text(action, fontSize = 13.sp) }
        }
    }
}

/**
 * 동료 탭 칩 한 벌 — 2행 3열 카테고리와 셋째 줄 ★그룹이 **같은 컴포저블**을 쓴다
 * (weight 1f · 32dp). 두 벌로 두면 한쪽만 고치는 사고가 난다.
 *
 * 글자 크기는 **11sp가 아니라 11dp**다(v1.6.42 ⑥). 칩 높이가 32dp로 못박혀 있어서 글자만
 * 시스템 배율을 따라가면 넘친다 — fontScale 1.3에서 `★즐겨찾기`의 위아래가 잘렸다(에뮬 실측).
 * `dp.toSp()`가 배율을 되나눠 주므로 글꼴 설정이 뭐든 칩 안에 그대로 들어간다.
 * 근무 코드 칩([com.sinjeong.crewcalendar.presentation.roster.MatrixMetrics])이 칸 폭을 dp로
 * 잡고 글자를 거기 맞추는 것과 같은 이유·같은 규칙이다.
 */
@Composable
private fun RowScope.MatesChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                label,
                fontSize = with(LocalDensity.current) { 11.dp.toSp() },
                maxLines = 1, softWrap = false,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        modifier = Modifier.weight(1f).height(32.dp),
    )
}

/**
 * 동료 추가: 이름 → 소속 → 오늘 근무 선택 (근무선택과 동일 원리로 전체 자동 계산).
 * `edit`가 있으면 같은 시트가 **수정 모드**로 뜬다 — 값이 채워져 있고, 근무 칸을 탭해도 바로
 * 저장되지 않고 선택만 된다(이름만 고칠 수도 있으니 [저장] 버튼으로 확정).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddMateSheet(
    onAdd: (String, CrewGroup, Int) -> Unit,
    onDismiss: () -> Unit,
    edit: Mate? = null,
) {
    val duty = LocalDutyColors.current
    val today = remember { LocalDate.now() }
    var name by remember { mutableStateOf(edit?.name ?: "") }
    var group by remember { mutableStateOf(edit?.group ?: CrewGroup.BRANCH) }
    // 지금 저장된 근무 칸을 미리 선택해 둔다. 소속을 바꾸면 교번표가 통째로 달라지므로 다시 골라야 한다.
    var picked by remember(group) {
        mutableStateOf(
            if (edit != null && group == edit.group) {
                val p = Bundled.patternFor(group)
                Math.floorMod(
                    ChronoUnit.DAYS.between(p.anchorDate, today).toInt() + edit.patternOffset,
                    p.length,
                )
            } else -1,
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                if (edit == null) "동료 추가" else "동료 수정",
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold,
            )
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("이름") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            // 소속 5종이라 SegmentedButton 한 줄엔 글자가 안 들어간다 → 접히는 칩
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CrewGroup.entries.forEach { g ->
                    FilterChip(
                        selected = group == g, onClick = { group = g },
                        label = { Text(g.label, fontSize = 11.sp) },
                    )
                }
            }
            Text(
                "오늘 이 동료의 근무를 고르세요 — 나머지 날짜는 교번 순서로 자동 계산됩니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val pattern = Bundled.patternFor(group)
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.heightIn(max = 300.dp),
            ) {
                items(pattern.sequence.size) { i ->
                    val code = DutyCode.parse(pattern.sequence[i])
                    val (bg, fg) = dutyCellColors(code.colorType, duty, MaterialTheme.colorScheme.onSurfaceVariant)
                    Surface(
                        onClick = {
                            if (edit != null) picked = i
                            else if (name.trim().length >= 2) onAdd(name, group, i)
                        },
                        color = bg, contentColor = fg,
                        shape = RoundedCornerShape(9.dp),
                        border = when {
                            i == picked -> BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary)
                            name.trim().length < 2 ->
                                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            else -> null
                        },
                    ) {
                        Text(
                            code.display,
                            modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold, maxLines = 1,
                        )
                    }
                }
            }
            if (edit == null && name.trim().length < 2) Text(
                "이름을 먼저 입력하면 근무를 선택할 수 있습니다",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (edit != null) {
                if (picked < 0) Text(
                    "소속을 바꿨습니다 — 오늘 근무를 다시 골라주세요.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Button(
                    onClick = { onAdd(name, group, picked) },
                    enabled = name.trim().length >= 2 && picked >= 0,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("저장") }
            }
        }
    }
}
