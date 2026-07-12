package com.sinjeong.crewcalendar.presentation.diaboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import com.sinjeong.crewcalendar.domain.model.Bundled
import com.sinjeong.crewcalendar.domain.model.MainLegs
import com.sinjeong.crewcalendar.domain.model.NightCombo
import com.sinjeong.crewcalendar.domain.model.RouteTable
import com.sinjeong.crewcalendar.presentation.theme.LocalDutyColors

private enum class BoardTab(val label: String) { BRANCH("지선"), MAIN_DAY("본선 주간"), MAIN_NIGHT("본선 야간") }

/** 목록 한 행 */
private data class DiaRow(
    val label: String,
    val range: String,
    val kind: RowKind,
    /** "전반 8:13~10:41 · 후반 12:51~14:51" — 없으면 null */
    val legs: String? = null,
) { enum class RowKind { DAY, NIGHT, STANDBY, BRANCH } }

private fun legsLabel(first: String?, second: String?): String? {
    fun fmt(t: String) = t.replace('#', '~').replace('-', '~').replace("▼", "▼")
    if (first == null || second == null) return null
    return "전반 ${fmt(first)} · 후반 ${fmt(second)}"
}

/**
 * 교번표 화면 (시안 v10).
 * 지선 / 본선 주간: 평일·휴일(토) 칩 — 본선 야간: 평평/평휴/휴휴/휴평 칩.
 * 다이아 탭 → 평/휴 (야간은 4종) 비교표 시트.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaBoardScreen() {
    var tab by remember { mutableStateOf(BoardTab.BRANCH) }
    var holiday by remember { mutableStateOf(false) }       // 지선/주간: 평일·휴일
    var combo by remember { mutableStateOf(NightCombo.PP) } // 야간: 4종
    var detailKey by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("교번표", fontWeight = FontWeight.ExtraBold) }) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // 상단 세그먼트
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                BoardTab.entries.forEachIndexed { i, t ->
                    SegmentedButton(
                        selected = tab == t,
                        onClick = { tab = t },
                        shape = SegmentedButtonDefaults.itemShape(i, BoardTab.entries.size),
                    ) { Text(t.label, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                }
            }
            Spacer(Modifier.height(8.dp))
            // 하위 칩
            Row(
                Modifier.padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (tab == BoardTab.MAIN_NIGHT) {
                    NightCombo.entries.forEach { c ->
                        FilterChip(selected = combo == c, onClick = { combo = c },
                            label = { Text(c.label, fontSize = 11.sp) })
                    }
                } else {
                    FilterChip(selected = !holiday, onClick = { holiday = false },
                        label = { Text("평일", fontSize = 11.sp) })
                    FilterChip(selected = holiday, onClick = { holiday = true },
                        label = { Text("휴일·토", fontSize = 11.sp) })
                }
            }
            Text(
                "평/휴 출근시각이 다릅니다 (예: 지14 평일 22:01 · 휴일 21:19). 상세는 다이아를 탭하세요.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            val rows = remember(tab, holiday, combo) { buildRows(tab, holiday, combo) }
            LazyColumn(
                Modifier.weight(1f).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
            ) {
                items(rows, key = { it.label }) { row ->
                    DiaRowCard(row, onClick = { detailKey = row.label })
                }
            }
        }
    }

    detailKey?.let { key ->
        DiaDetailSheet(tab = tab, key = key, onDismiss = { detailKey = null })
    }
}

// 행 = 다이아 + 출근시각 + 전반/후반 사업
private fun buildRows(tab: BoardTab, holiday: Boolean, combo: NightCombo): List<DiaRow> = when (tab) {
    BoardTab.BRANCH -> {
        val src = if (holiday) Bundled.BRANCH_HOLIDAY else Bundled.BRANCH_WEEKDAY
        src.map { (code, r) ->
            val kind = when {
                code.startsWith("지대") -> DiaRow.RowKind.STANDBY
                r.overnight -> DiaRow.RowKind.NIGHT
                else -> DiaRow.RowKind.BRANCH
            }
            DiaRow(code.removePrefix("지"), r.signOn, kind,
                legsLabel(r.firstLeg, r.secondLeg) ?: "대기 · 종료 ${if (r.overnight) "익일 " else ""}${r.signOff}")
        }
    }
    BoardTab.MAIN_DAY -> {
        val src = if (holiday) Bundled.MAIN_DAY_HOLIDAY else Bundled.MAIN_DAY_WEEKDAY
        src.map { (n, r) ->
            val l = MainLegs.forDay(n, holiday)
            DiaRow("$n", r.signOn, DiaRow.RowKind.DAY, l?.let { legsLabel("${it[0]}~${it[1]}", "${it[2]}~${it[3]}") })
        } + Bundled.STANDBY.filterKeys { it.removePrefix("대").toInt() < 11 }.map { (code, r) ->
            DiaRow(code, r.signOn, DiaRow.RowKind.STANDBY, "사업소 대기 · 종료 ${r.signOff}")
        }
    }
    BoardTab.MAIN_NIGHT -> {
        Bundled.MAIN_NIGHT.map { (n, variants) ->
            val l = MainLegs.forNight(n, combo)
            DiaRow("$n", variants[combo]?.first ?: "—", DiaRow.RowKind.NIGHT,
                l?.let { legsLabel("${it[0]}~${it[1]}", "${it[2]}~${it[3]}(익일)") })
        } + Bundled.STANDBY.filterKeys { it.removePrefix("대").toInt() >= 11 }.map { (code, r) ->
            DiaRow(code, r.signOn, DiaRow.RowKind.STANDBY, "야간 대기 · 종료 익일 ${r.signOff}")
        }
    }
}

@Composable
private fun DiaRowCard(row: DiaRow, onClick: () -> Unit) {
    val duty = LocalDutyColors.current
    val (bg, fg) = when (row.kind) {
        DiaRow.RowKind.DAY -> duty.main to duty.onMain
        DiaRow.RowKind.NIGHT -> duty.night to duty.onNight
        DiaRow.RowKind.STANDBY -> duty.standby to duty.onStandby
        DiaRow.RowKind.BRANCH -> duty.branch to duty.onBranch
    }
    // 압축 행 — 칩 + 출근시각(앞) + 전반/후반 사업
    OutlinedCard(onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(9.dp)) {
                Box(Modifier.size(width = 40.dp, height = 30.dp), contentAlignment = Alignment.Center) {
                    Text(row.label, fontWeight = FontWeight.ExtraBold, fontSize = 12.5.sp)
                }
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("출근", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(row.range, fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp)
                }
                row.legs?.let {
                    Text(
                        it, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** 다이아 상세: 평/휴 (야간은 4종 조합) 비교표 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiaDetailSheet(tab: BoardTab, key: String, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (tab) {
                BoardTab.BRANCH -> {
                    val code = "지$key"
                    val wd = Bundled.BRANCH_WEEKDAY[code]
                    val hol = Bundled.BRANCH_HOLIDAY[code]
                    Text("지선 $key", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                    CompareTable(
                        header = listOf("적용일", "출근", "종료"),
                        rows = listOfNotNull(
                            wd?.let { listOf("평일", it.signOn, (if (it.overnight) "익 " else "") + it.signOff) },
                            hol?.let { listOf("휴일·토", it.signOn, (if (it.overnight) "익 " else "") + it.signOff) },
                        ),
                    )
                    wd?.firstLeg?.let {
                        Text("평일 전반 $it · 후반 ${wd.secondLeg}", style = MaterialTheme.typography.labelMedium)
                    }
                    key.toIntOrNull()?.let { n ->
                        val w = RouteTable.forBranch(n, holiday = false)
                        val h = RouteTable.forBranch(n, holiday = true)
                        if (w != null || h != null) Text(
                            "근무시간 평 ${w?.totalWorkTime ?: "—"} · 휴 ${h?.totalWorkTime ?: "—"}",
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                        )
                    }
                }
                BoardTab.MAIN_DAY -> {
                    val n = key.removePrefix("대").toIntOrNull()
                    if (key.startsWith("대")) {
                        val r = Bundled.STANDBY[key]
                        Text("본선 $key", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                        CompareTable(
                            header = listOf("적용일", "출근", "종료"),
                            rows = listOfNotNull(r?.let { listOf("평일·휴일", it.signOn, it.signOff) }),
                        )
                    } else {
                        val wd = n?.let { Bundled.MAIN_DAY_WEEKDAY[it] }
                        val hol = n?.let { Bundled.MAIN_DAY_HOLIDAY[it] }
                        Text("본선 다이아 $key", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                        CompareTable(
                            header = listOf("적용일", "출근", "종료"),
                            rows = listOf(
                                listOf("평일", wd?.signOn ?: "—", wd?.signOff ?: "—"),
                                listOf("휴일·토", hol?.signOn ?: "운휴", hol?.signOff ?: "운휴"),
                            ),
                        )
                        wd?.firstLeg?.let {
                            Text("평일 전반 $it · 후반 ${wd.secondLeg}", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                BoardTab.MAIN_NIGHT -> {
                    if (key.startsWith("대")) {
                        val r = Bundled.STANDBY[key]
                        Text("본선 $key", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                        CompareTable(
                            header = listOf("적용일", "출근", "종료"),
                            rows = listOfNotNull(r?.let { listOf("평일·휴일", it.signOn, "익 " + it.signOff) }),
                        )
                    } else {
                        val n = key.toIntOrNull()
                        val variants = n?.let { Bundled.MAIN_NIGHT[it] }
                        Text("본선 다이아 $key (야간)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                        Text(
                            "당일→익일 성격에 따라 시각이 다릅니다. 달력에는 해당 날짜 조합이 자동 적용됩니다.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        CompareTable(
                            header = listOf("조합", "출근", "익일 종료"),
                            rows = NightCombo.entries.map { c ->
                                val v = variants?.get(c)
                                listOf(c.label, v?.first ?: "—", v?.second ?: "—")
                            },
                        )
                    }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("닫기") }
        }
    }
}

@Composable
private fun CompareTable(header: List<String>, rows: List<List<String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            header.forEach {
                Text(
                    it, modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider()
        rows.forEach { cells ->
            Row(Modifier.padding(vertical = 3.dp)) {
                cells.forEachIndexed { i, c ->
                    Text(
                        c, modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (i == 0) FontWeight.ExtraBold else FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
