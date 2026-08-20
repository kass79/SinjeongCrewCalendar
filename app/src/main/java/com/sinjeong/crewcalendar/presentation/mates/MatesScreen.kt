package com.sinjeong.crewcalendar.presentation.mates

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
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
import com.sinjeong.crewcalendar.domain.model.*
import com.sinjeong.crewcalendar.domain.repository.MateRepository
import com.sinjeong.crewcalendar.domain.repository.RosterRepository
import com.sinjeong.crewcalendar.domain.repository.UserRepository
import com.sinjeong.crewcalendar.presentation.roster.*
import com.sinjeong.crewcalendar.presentation.theme.LocalDutyColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class MatesViewModel @Inject constructor(
    private val mateRepo: MateRepository,
    userRepo: UserRepository,
    rosterRepo: RosterRepository,
) : ViewModel() {
    val mates: StateFlow<List<Mate>> = mateRepo.observeMates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val user: StateFlow<User?> = userRepo.observeMe()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val monthFlow = MutableStateFlow(YearMonth.now())

    /** 내 근무변경 실시간 반영 (Firebase 미연동이면 빈 맵) */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val monthOverrides: StateFlow<Map<String, Map<LocalDate, String>>> =
        monthFlow.flatMapLatest { rosterRepo.observeMonthOverrides(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun setMonth(m: YearMonth) { monthFlow.value = m }

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

    /** 동료근무 화면과 같은 규칙 — Mate가 없으면(=본인) 그 시점에 만든다 */
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
 * 동료 탭 — 저장한 동료 + 본인을 **날짜별로 나란히** 비교하는 매트릭스.
 * 동료근무(RosterScreen)와 같은 컴포저블(`DutyMatrix.kt`)을 쓰되, 인원이 적으므로
 * `MatrixMetrics.Roomy`로 칸을 넉넉하게 잡는다 — 그게 이 화면의 존재 이유다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatesScreen(viewModel: MatesViewModel = hiltViewModel()) {
    val mates by viewModel.mates.collectAsStateWithLifecycle()
    val user by viewModel.user.collectAsStateWithLifecycle()
    val monthOverrides by viewModel.monthOverrides.collectAsStateWithLifecycle()
    var month by remember { mutableStateOf(YearMonth.now()) }
    LaunchedEffect(month) { viewModel.setMonth(month) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<FavGroup?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var sheetTarget by remember { mutableStateOf<MatrixPerson?>(null) }
    var editTarget by remember { mutableStateOf<Mate?>(null) }

    val duty = LocalDutyColors.current
    val m = MatrixMetrics.Roomy
    // 이번 달은 오늘~말일, 다른 달은 1일~말일 (v1.6.38 — 과거는 비교에 쓸모가 없다)
    val startDay = todayStartDay(month)
    val hScroll = rememberMatrixScroll(month, m.cellW, startDay)

    val q = query.trim()
    val me = meAsPerson(user)?.takeIf { q.isEmpty() || it.name.contains(q) }
    // 본인은 ★필터에서 제외 — 동료근무 화면과 같은 규칙(내 근무가 기준선이라 항상 보여야 한다)
    val rows = listOfNotNull(me) + mates
        .filter { q.isEmpty() || it.name.contains(q) }
        .filter { filter == null || it.favGroup == filter }
        .filter { mateKey(it.name, it.group) != me?.key }
        .sortedWith(compareBy({ it.favGroup == null }, { it.favGroup?.ordinal ?: 9 }, { it.name }))
        .map { MatrixPerson(it.name, it.group, it.patternOffset) }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("동료 ${month.year}.${month.monthValue}", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                    IconButton(onClick = { month = month.minusMonths(1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "이전 달")
                    }
                    IconButton(onClick = { month = month.plusMonths(1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "다음 달")
                    }
                }
            })
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
            // ★그룹이 늘어나면 한 줄을 넘길 수 있다 → 가로 스크롤
            Row(
                Modifier.horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(selected = filter == null, onClick = { filter = null },
                    label = { Text("전체", fontSize = 11.sp) })
                FavGroup.entries.forEach { g ->
                    val count = mates.count { it.favGroup == g }
                    FilterChip(selected = filter == g, onClick = { filter = g },
                        label = { Text("★ ${g.label}" + if (count > 0) " $count" else "", fontSize = 11.sp) })
                }
            }

            if (mates.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "아직 등록된 동료가 없습니다.\n+ 버튼으로 추가하세요 — 이름·소속·오늘 근무만 알면 됩니다.\n" +
                            "추가하면 여기서 내 근무와 날짜별로 나란히 비교됩니다.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (rows.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("조건에 맞는 동료가 없습니다", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                MatrixDateHeader(month, m, hScroll, duty, startDay)
                HorizontalDivider()
                val favKeys = remember(mates) {
                    mates.filter { it.favGroup != null }.map { mateKey(it.name, it.group) }.toSet()
                }
                LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(rows.size, key = { rows[it].key }) { i ->
                        val p = rows[i]
                        MatrixRow(p, month, m, hScroll, duty,
                            isFav = p.key in favKeys,
                            overrides = p.uid?.let { monthOverrides[it] } ?: emptyMap(),
                            zebra = i % 2 == 1,
                            startDay = startDay,
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
