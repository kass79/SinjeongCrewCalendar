package com.sinjeong.crewcalendar.presentation.live

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
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
 *
 * ## v1.7.12 ① — 펼친 칸은 **가로 미니 노선 한 줄**(칩 줄은 그대로)
 *
 * 카스: *"아이콘을 좀 더 귀엽게 각호선 색상에 맞게 … 세로칸은 최소화 해서 일자로"*.
 * 종전엔 다가오는 열차 최대 세 대를 **글자 목록**으로 세로로 쌓았다(약 131dp). 이제
 * [CommuteMiniLine] 이 한 줄로 접는다 — 왼쪽 끝이 `3번째 전역`, 오른쪽 끝이 등록한 역,
 * 그 위에 **호선 색 꼬마 기관차**가 서고, 오른쪽에 **가장 가까운 한 대**의 남은 시간·종착이 붙는다.
 * 칩 줄은 v1.7.9 확정 그대로다(한 줄·옆으로 밀기·최대 4개) — 카스가 고른 것이 *"제안 A"* 다.
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
            val next = commuteApproaching(rows, s)
            // 가까운 순 — [commuteAtStation] 은 지금 역에 든 열차(`arvlCd` 0·1), [commuteApproaching]
            // 은 그 뒤로 다가오는 열차다. 맨 앞이 **가장 가까운 한 대**이고 오른쪽 글자가 그것을 말한다.
            val near = listOfNotNull(at) + next
            val lead = near.firstOrNull()
            // 아이콘 자리 — 같은 칸이 겹치면 **가까운 것만** 남긴다(먼저 온 것이 이긴다).
            val slots = near.map { commuteSlot(it.arvlMsg2, it.arvlCd) }.distinct()
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (lead == null) {
                        // 빈 상태·오류는 **한 줄로만** 말한다(확정 표 — 문구는 v1.7.9 그대로).
                        Text(
                            when {
                                error != null -> error!!
                                loading -> "실시간 조회 중…"
                                !inService() -> "운행 시간이 아닙니다"
                                else -> "다가오는 열차가 없습니다"
                            },
                            fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        CommuteMiniLine(s, slots, Modifier.weight(1f).height(MINI_H))
                        Spacer(Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                if (at != null) atStationText(at.arvlCd) else etaText(lead.etaSec),
                                fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${lead.destName}행",
                                fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}


/* ── 미니 노선 한 줄 (v1.7.12 ①) ───────────────────────────── */

/**
 * 펼친 칸 높이. 세로로 쌓던 글자 목록(약 131dp)을 **한 줄**로 접은 값이다 —
 * 카스: *"세로칸은 최소화 해서 일자로 보여주면 좋지"*.
 */
private val MINI_H = 40.dp

/** 기관차 한 대가 선 위로 차지하는 높이(굴뚝 끝 ~ 바퀴 바닥). 배율 1 일 때의 값이다. */
private val LOCO_TOP_ROOM = 18.dp

/**
 * **가로 미니 노선 한 줄** — 왼쪽 끝이 `3번째 전역`, 오른쪽 끝이 **등록한 그 역**(큰 점)이고
 * 그 위에 다가오는 열차가 기관차로 선다. 칸 좌표는 순수 함수 [commuteSlot] 이 낸다.
 *
 * 색은 **호선 색**([lineArgb])이다 — 카스: *"아이콘을 좀 더 귀엽게 각호선 색상에 맞게"*.
 * 다만 **글자는 테마 색**을 쓴다(`onSurfaceVariant`) — 1호선 남색(`#0052A4`)·7호선
 * 올리브(`#747F00`) 같은 어두운 호선색은 다크 카드 위에서 대비가 무너진다. 그림(선·점·몸통)만
 * 호선 색이고 읽어야 하는 글자는 테마가 보장하는 대비를 쓴다.
 */
@Composable
private fun CommuteMiniLine(station: CommuteStation, slots: List<Int>, modifier: Modifier) {
    val line = Color(lineArgb(station.subwayId))
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val tm = rememberTextMeasurer()
    Canvas(modifier) {
        val nameL = tm.measure(
            station.name,
            TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ink),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        val farL = tm.measure(
            "${COMMUTE_SLOTS - 1}번째 전역",
            TextStyle(fontSize = 8.sp, color = ink.copy(alpha = 0.7f)),
            maxLines = 1,
        )
        // 라벨은 선 밑에 눕는다 — 캔버스 아래쪽을 라벨 높이만큼 비우고 나머지가 그림 자리다.
        val labelH = maxOf(nameL.size.height, farL.size.height).toFloat()
        val y = size.height - labelH - 2.dp.toPx()
        // 양 끝 여백 = 라벨 반 폭(글자가 캔버스 밖으로 안 나간다) — 큰 점 반지름보다는 늘 크다.
        val padL = maxOf(farL.size.width / 2f, 6.dp.toPx())
        val padR = maxOf(nameL.size.width / 2f, 6.dp.toPx())
        val x0 = padL
        val step = (size.width - padL - padR) / (COMMUTE_SLOTS - 1)
        fun xOf(slot: Int) = x0 + step * slot.coerceIn(0, COMMUTE_SLOTS - 1)

        drawLine(line.copy(alpha = 0.32f), Offset(x0, y), Offset(x0 + step * (COMMUTE_SLOTS - 1), y),
            strokeWidth = 3.5.dp.toPx(), cap = StrokeCap.Round)
        // 지나온 역은 작은 흰 점 + 호선색 테, **등록한 역**만 꽉 찬 큰 점이다.
        // ⚠ 기관차가 선 칸엔 점을 안 찍는다 — 점이 바퀴 사이로 삐져나와 **턱수염처럼** 보였다
        //   (실측 크롭 둘 다). 그 칸은 기관차가 표시를 대신하고, 어느 역인지는 밑 라벨이 말한다.
        for (i in 0 until COMMUTE_SLOTS - 1) {
            if (i in slots) continue
            drawCircle(Color.White, 3.2.dp.toPx(), Offset(xOf(i), y))
            drawCircle(line, 3.2.dp.toPx(), Offset(xOf(i), y), style = Stroke(1.8.dp.toPx()))
        }
        if (COMMUTE_SLOTS - 1 !in slots)
            drawCircle(line, 5.4.dp.toPx(), Offset(xOf(COMMUTE_SLOTS - 1), y))

        drawText(farL, topLeft = Offset(x0 - farL.size.width / 2f, y + 2.dp.toPx()))
        drawText(nameL, topLeft = Offset(
            (xOf(COMMUTE_SLOTS - 1) - nameL.size.width / 2f)
                .coerceIn(0f, (size.width - nameL.size.width).coerceAtLeast(0f)),
            y + 2.dp.toPx()))

        // 기관차 키 — 선 위에 남은 자리에 맞춘다. 글자배율을 키우면 라벨이 두꺼워져 선이
        // 올라오는데, 그때 아이콘을 안 줄이면 굴뚝이 카드 위로 삐져나간다(배율 1.5 실측 자리).
        val k = (y / LOCO_TOP_ROOM.toPx()).coerceIn(0.55f, 1f)
        slots.forEach { drawCommuteLoco(xOf(it), y, line, ink, k) }
    }
}

/**
 * **귀여운 꼬마 기관차** — 둥근 몸통 + 흰 창 둘 + 눈웃음 + 바퀴 둘 + 굴뚝(시안 그대로).
 * `(cx, railY)` 는 **바퀴가 닿는 선로 위 한 점**이고 그림은 거기서 위로 자란다.
 *
 * ⚠ **열번을 넣지 않는다.** 확정 표의 *"열차 아이콘 = 열번 상자"* 는 실시간 **지도**(본선·지선)
 * 규칙이다 — 거기선 내 열번을 눈으로 찾는 것이 화면의 목적이라 열번이 아이콘 안에 있어야 한다.
 * 이 칸은 *"내가 탈 열차가 몇 정거장 앞"* 을 말하는 도착 정보라 열번이 할 일이 없고, 몸통이
 * 22dp 라 4자리가 물리적으로 안 든다. [Loco.drawLoco] 를 쓰지 않는 이유도 같다(그쪽은 열번·
 * 행선판·연기를 그리는 지도용 함수다).
 */
private fun DrawScope.drawCommuteLoco(cx: Float, railY: Float, body: Color, wheel: Color, k: Float) {
    fun u(v: Float) = v.dp.toPx() * k
    val w = u(22f)                 // 몸통 폭   (시안 76 units)
    val h = u(11.6f)               // 몸통 높이 (시안 40 units — 가로:세로 1.9:1)
    val r = u(2.0f)                // 바퀴 반지름 (시안 7)
    val cy = railY - r - u(7f)     // 몸통 중심 — 바퀴가 선로에 닿고 몸통은 그 위에 뜬다
    // 굴뚝 (몸통 가운데 위)
    drawRoundRect(body, Offset(cx - u(2.3f), cy - h / 2f - u(3.2f)), Size(u(4.6f), u(4f)),
        CornerRadius(u(1.2f)))
    // 몸통
    drawRoundRect(body, Offset(cx - w / 2f, cy - h / 2f), Size(w, h), CornerRadius(u(3.8f)))
    // 창 둘 (= 눈)
    val ww = u(6.4f); val wh = u(4.9f); val wy = cy - u(3.8f)
    drawRoundRect(Color.White, Offset(cx - u(8.1f), wy), Size(ww, wh), CornerRadius(u(1.7f)))
    drawRoundRect(Color.White, Offset(cx + u(1.2f), wy), Size(ww, wh), CornerRadius(u(1.7f)))
    // 눈웃음 — 아래로 볼록한 얇은 호(`useCenter = false` 라 반달이 아니라 선이다)
    drawArc(Color.White, 20f, 140f, false,
        Offset(cx - u(3.5f), cy + u(1.6f)), Size(u(7f), u(3.4f)),
        style = Stroke(u(1.2f), cap = StrokeCap.Round))
    // 바퀴 둘 — 늘 선로 쪽이다(지도 기관차와 같은 규칙)
    drawCircle(wheel, r, Offset(cx - u(5.7f), railY - r))
    drawCircle(wheel, r, Offset(cx + u(5.7f), railY - r))
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
