package com.sinjeong.crewcalendar.presentation.live

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/*
 * 출퇴근 역 실시간 — 화면 (v1.7.9 ⑦). 순수 로직은 전부 [CommuteLive.kt] 에 있다.
 *
 * ⚠ **색은 테마에서 뽑는다**(`MaterialTheme`). 상세시트는 **달력 스타일을 안 따른다** —
 * v1.7.6 확정("바꾸는 것은 달력 탭뿐이다 … 상세시트는 손대지 않는다"). 그래서 이 줄도
 * 라이트/다크 테마를 그대로 따라가고, 달력이 클레이여도 시트 안의 다른 카드(행로표·메모)와
 * 같은 색을 쓴다. 팔레트를 여기에만 따로 물리면 시트 안에서 이 줄만 튄다.
 */

/**
 * 상세시트 **행로표 위**의 출퇴근 역 줄.
 *
 * · 등록한 역이 없으면 **아무것도 안 그린다**(높이 0).
 * · 칩 한 줄 — 누르면 그 아래가 펼쳐지고 **다른 칩은 접힌다**(한 번에 하나).
 * · 펼친 동안만 **15초에 1회** 조회. 접거나 시트를 닫으면 [LaunchedEffect] 가 취소돼 멎는다.
 *
 * 호출 예산: 칩 하나를 1분 펼쳐 두면 4회다. 지선 카드(10초/4초)와 달리 사용자가 직접 펼쳐야
 * 도는 경로라 평소엔 0회다.
 */
@Composable
internal fun CommuteBar(
    stations: List<CommuteStation>,
    modifier: Modifier = Modifier,
) {
    if (stations.isEmpty()) return
    var open by remember { mutableStateOf<CommuteStation?>(null) }
    var rows by remember { mutableStateOf(emptyList<ArrivalRow>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    /*
     * 눈금은 **절대 시각**으로 놓는다 — `delay(15_000)` 로 재우면 네트워크에 쓴 시간과 delay
     * 오버슈트가 누적돼 주기가 밀린다(근거는 [BranchLiveMap] 폴링 KDoc, v1.6.72 실측).
     * 키가 [open] 이라 ① 접으면(null) 조기 반환 후 루프가 아예 안 돌고 ② 다른 칩으로 옮기면
     * 옛 루프가 취소되고 새 루프가 선다 — **두 역을 동시에 조회하는 일이 없다.**
     */
    LaunchedEffect(open) {
        val s = open ?: return@LaunchedEffect
        rows = emptyList(); error = null; loading = true
        var next = System.currentTimeMillis()
        while (isActive) {
            BranchLive.arrivalsAt(s.name)
                .onSuccess { rows = it; error = null }
                .onFailure { error = BranchLive.humanError(it) }
            loading = false
            next += 15_000
            val now = System.currentTimeMillis()
            if (next < now) next = now + 15_000
            delay(next - now)
        }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            stations.forEach { s ->
                FilterChip(
                    selected = open == s,
                    onClick = { open = if (open == s) null else s },
                    label = {
                        Text(
                            "${lineName(s.subwayId)} ${s.name}",
                            fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }
        open?.let { s ->
            val at = commuteAtStation(rows, s)
            val next3 = commuteApproaching(rows, s)
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        "${lineName(s.subwayId)} ${s.name} · ${s.updnLine}",
                        fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    at?.let {
                        Text(
                            "${atStationText(it.arvlCd)} · ${it.destName}행",
                            fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    next3.forEach { r ->
                        Column {
                            Text(
                                positionText(r),
                                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${etaText(r.etaSec)} · ${r.destName}행",
                                fontSize = 11.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    // 빈 상태·오류는 **한 줄로만** 말한다(확정 표 — 지도 위에 얹지 않는다).
                    if (at == null && next3.isEmpty()) Text(
                        when {
                            error != null -> error!!
                            loading -> "실시간 조회 중…"
                            !inService() -> "운행 시간이 아닙니다"
                            else -> "다가오는 열차가 없습니다"
                        },
                        fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/* ── 설정 > 출퇴근 역 등록 ──────────────────────────────────── */

/**
 * 등록 화면. **역 목록 자산을 만들지 않는다** — 수도권 700여 역을 번들하면 낡는다.
 * 대신 입력한 이름으로 도착 API 를 **딱 1회** 불러 응답에 실제로 있던 (호선 × 방향)을 칩으로 준다.
 *
 * ⚠ 운행 종료 시간엔 `INFO-200`(빈 목록)이라 조합을 못 뽑는다 — **오류가 아니라 안내**다.
 * ⚠ 역 이름은 **줄여 쓰지 않는다**(확정 표) — 사용자가 친 그대로 저장하고, 그 이름이
 *   API 조회 키다. 그래서 `구로디지털단지` 를 `구로` 로 치면 다른 역이 나온다.
 */
@Composable
internal fun CommuteSettingDialog(
    stations: List<CommuteStation>,
    onSave: (List<CommuteStation>) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var asked by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var options by remember { mutableStateOf(emptyList<CommuteOption>()) }
    var msg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
            Column(
                Modifier.padding(18.dp).heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("출퇴근 역", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                Text(
                    "등록한 역은 달력 상세시트 행로표 위에 뜹니다. 칩을 누르면 그 역으로 " +
                        "다가오는 열차 ${COMMUTE_ROWS}대의 위치·남은 시간이 보입니다 (최대 ${COMMUTE_MAX}개).",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                stations.forEach { s ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${lineName(s.subwayId)} ${s.name}", fontWeight = FontWeight.Bold)
                            Text(
                                s.updnLine, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { onSave(stations - s) }) { Text("삭제") }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                if (stations.size >= COMMUTE_MAX) {
                    Text(
                        "${COMMUTE_MAX}개까지 등록할 수 있습니다 · 하나를 지우면 더 넣을 수 있어요",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it; options = emptyList(); msg = null },
                        label = { Text("역 이름 (줄여 쓰지 마세요)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        if (busy) CircularProgressIndicator(Modifier.height(20.dp))
                        TextButton(
                            enabled = !busy && query.isNotBlank(),
                            onClick = {
                                val name = query.trim()
                                busy = true; msg = null; options = emptyList(); asked = name
                                scope.launch {
                                    BranchLive.arrivalsAt(name)
                                        .onSuccess { rows ->
                                            options = commuteOptions(rows)
                                            // 빈 목록 = INFO-200(운행 종료·결과 0건). 오류가 아니다.
                                            if (options.isEmpty()) msg =
                                                "지금은 오는 열차가 없어 방향을 고를 수 없습니다. " +
                                                    "역 이름이 맞는지 확인하고, 운행 시간에 등록해 주세요."
                                        }
                                        .onFailure { msg = BranchLive.humanError(it) }
                                    busy = false
                                }
                            },
                        ) { Text("확인") }
                    }
                    if (options.isNotEmpty()) {
                        Text(
                            "$asked · 방향을 고르세요 (한 칩 = 한 방향)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        options.forEach { o ->
                            Surface(
                                // 고른 뒤에도 칩을 남긴다 — 같은 역의 **다른 호선·다른 방향**을
                                // 이어서 담을 수 있다(까치산 2호선 + 5호선). 조회를 또 하지 않으니
                                // API 호출도 한 번 아낀다. 같은 조합을 두 번 눌러도 distinct 가 막는다.
                                onClick = { onSave((stations + o.toStation(asked)).distinct()) },
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                shape = RoundedCornerShape(999.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    o.label, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                )
                            }
                        }
                    }
                    msg?.let {
                        Text(
                            it, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("닫기") }
                }
            }
        }
    }
}
