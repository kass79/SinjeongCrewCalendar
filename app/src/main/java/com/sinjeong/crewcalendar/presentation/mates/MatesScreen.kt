package com.sinjeong.crewcalendar.presentation.mates

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.sinjeong.crewcalendar.presentation.theme.DutyColors
import com.sinjeong.crewcalendar.presentation.theme.LocalDutyColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class MatesViewModel @Inject constructor(
    private val mateRepo: MateRepository,
) : ViewModel() {
    val mates: StateFlow<List<Mate>> = mateRepo.observeMates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 등록: 오늘 근무 위치(patternIndex)로 offset 계산 — 근무선택과 같은 원리 */
    fun addMate(name: String, group: CrewGroup, todayPatternIndex: Int) {
        val pattern = Bundled.patternFor(group)
        val days = ChronoUnit.DAYS.between(pattern.anchorDate, LocalDate.now()).toInt()
        val offset = Math.floorMod(todayPatternIndex - days, pattern.length)
        viewModelScope.launch { mateRepo.upsert(Mate(name.trim(), group, offset)) }
    }

    fun setFavGroup(mate: Mate, fav: FavGroup?) {
        viewModelScope.launch { mateRepo.upsert(mate.copy(favGroup = fav)) }
    }

    fun remove(name: String) {
        viewModelScope.launch { mateRepo.remove(name) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatesScreen(viewModel: MatesViewModel = hiltViewModel()) {
    val mates by viewModel.mates.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<FavGroup?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("동료 근무", fontWeight = FontWeight.ExtraBold) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, "동료 추가") }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("이름으로 검색") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                shape = RoundedCornerShape(999.dp),
            )
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
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

            val visible = mates
                .filter { query.isBlank() || it.name.contains(query.trim()) }
                .filter { filter == null || it.favGroup == filter }
                .sortedWith(compareBy({ it.favGroup == null }, { it.favGroup?.ordinal ?: 9 }, { it.name }))

            if (visible.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        if (mates.isEmpty()) "아직 등록된 동료가 없습니다.\n+ 버튼으로 추가하세요 — 이름·소속·오늘 근무만 알면 됩니다."
                        else "조건에 맞는 동료가 없습니다",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f).padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 88.dp),
                ) {
                    items(visible.size, key = { visible[it].name }) { i ->
                        MateCard(
                            mate = visible[i],
                            onSetFav = { viewModel.setFavGroup(visible[i], it) },
                            onRemove = { viewModel.remove(visible[i].name) },
                        )
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddMateSheet(
            onAdd = { name, group, idx -> viewModel.addMate(name, group, idx); showAdd = false },
            onDismiss = { showAdd = false },
        )
    }
}

private fun dutyColors(code: DutyCode, duty: DutyColors, fallback: Color): Pair<Color, Color> =
    when (code.type) {
        DutyType.MAIN_DAY, DutyType.OFFICE -> duty.main to duty.onMain
        DutyType.MAIN_NIGHT, DutyType.BRANCH_NIGHT, DutyType.SPECIAL -> duty.night to duty.onNight
        DutyType.POST_NIGHT -> duty.off to duty.onOff
        DutyType.REST, DutyType.BRANCH_REST -> duty.rest to duty.onRest
        DutyType.STANDBY, DutyType.BRANCH_STANDBY -> duty.standby to duty.onStandby
        DutyType.BRANCH -> duty.branch to duty.onBranch
        DutyType.ETC -> Color.Transparent to fallback
    }

@Composable
private fun MateCard(mate: Mate, onSetFav: (FavGroup?) -> Unit, onRemove: () -> Unit) {
    val duty = LocalDutyColors.current
    var menu by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val todayDuty = mate.dutyOn(today)
    val (bg, fg) = dutyColors(todayDuty, duty, MaterialTheme.colorScheme.onSurfaceVariant)

    OutlinedCard {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = CircleShape) {
                    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                        Text(mate.name.take(1), fontWeight = FontWeight.ExtraBold)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(mate.name, fontWeight = FontWeight.Bold)
                    Text(
                        mate.group.label + (mate.favGroup?.let { " · ★ ${it.label}" } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(8.dp)) {
                    Text(
                        todayDuty.display.ifBlank { "—" },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(
                            if (mate.favGroup != null) Icons.Default.Star else Icons.Outlined.StarOutline,
                            "즐겨찾기 그룹",
                            tint = if (mate.favGroup != null) duty.onStandby else MaterialTheme.colorScheme.outline,
                        )
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        FavGroup.entries.forEach { g ->
                            DropdownMenuItem(
                                text = { Text("★ ${g.label}" + if (mate.favGroup == g) " ✓" else "") },
                                onClick = { onSetFav(g); menu = false },
                            )
                        }
                        if (mate.favGroup != null) DropdownMenuItem(
                            text = { Text("즐겨찾기 해제") },
                            onClick = { onSetFav(null); menu = false },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("동료 삭제", color = MaterialTheme.colorScheme.error) },
                            onClick = { onRemove(); menu = false },
                        )
                    }
                }
            }
            // 오늘부터 7일 미니 스트립
            Row(Modifier.padding(top = 9.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                (0..6).forEach { d ->
                    val code = mate.dutyOn(today.plusDays(d.toLong()))
                    val (b, f) = dutyColors(code, duty, MaterialTheme.colorScheme.onSurfaceVariant)
                    Surface(color = b, contentColor = f, shape = RoundedCornerShape(6.dp), modifier = Modifier.weight(1f)) {
                        Text(
                            code.display.ifBlank { "—" },
                            modifier = Modifier.padding(vertical = 4.dp),
                            fontSize = 9.5.sp, fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center, maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** 동료 추가: 이름 → 소속 → 오늘 근무 선택 (근무선택과 동일 원리로 전체 자동 계산) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddMateSheet(onAdd: (String, CrewGroup, Int) -> Unit, onDismiss: () -> Unit) {
    val duty = LocalDutyColors.current
    var name by remember { mutableStateOf("") }
    var group by remember { mutableStateOf(CrewGroup.BRANCH) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("동료 추가", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("이름") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                CrewGroup.entries.forEachIndexed { i, g ->
                    SegmentedButton(
                        selected = group == g, onClick = { group = g },
                        shape = SegmentedButtonDefaults.itemShape(i, CrewGroup.entries.size),
                    ) { Text(g.label, fontSize = 11.sp) }
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
                    val (bg, fg) = dutyColors(code, duty, MaterialTheme.colorScheme.onSurfaceVariant)
                    Surface(
                        onClick = { if (name.trim().length >= 2) onAdd(name, group, i) },
                        color = bg, contentColor = fg,
                        shape = RoundedCornerShape(9.dp),
                        border = if (name.trim().length < 2)
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
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
            if (name.trim().length < 2) Text(
                "이름을 먼저 입력하면 근무를 선택할 수 있습니다",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
