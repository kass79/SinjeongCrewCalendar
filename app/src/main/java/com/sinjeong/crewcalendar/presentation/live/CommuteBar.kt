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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalDensity
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
 * [CommuteMiniLine] 이 한 줄로 접는다 — 왼쪽 끝이 `5번째 전역+`(v1.7.12 ③ 에서 4칸→6칸),
 * 오른쪽 끝이 등록한 역,
 * 그 위에 **호선 색 꼬마 기관차**가 서고, 오른쪽에 **가장 가까운 한 대**의 남은 시간·종착이 붙는다.
 * 칩 줄은 v1.7.9 확정 그대로다(한 줄·옆으로 밀기·최대 [COMMUTE_MAX] 개) — 카스가 고른 것이
 * *"제안 A"* 다.
 *
 * ## v1.7.13 — ① 기관차가 **1초마다 앞으로** · ③ 더 낮게 · ⑦ 닫는 손잡이
 *
 * ① 카스: *"역으로 다가오는 **열차아이콘이 안움직이는데?**"* — v1.7.12 는 15초 폴링 때마다
 * 칸을 뛰고 그 사이엔 멈춰 있었다. 이제 **1초 눈금**([tickMs])이 돌고 자리는 순수 함수
 * [commuteAdvance] 가 남은 초로 칸 사이를 메운다. **API 는 더 안 부른다**(15초 그대로).
 * 눈금은 [LaunchedEffect] 라 **펼친 동안만** 돌고 접으면 멎는다. 오른쪽 `N분 M초` 도 같은
 * 눈금을 타 1초씩 준다(종전엔 15초마다 뭉텅이로 줄었다).
 *
 * ⑦ 카스: *"출퇴근역은 **바로 꺼지는 버튼**도 만들어주면 좋지!"* — **고른 칩에 `×` 를 단다.**
 * 닫는 동작은 v1.7.9 부터 있었다(같은 칩을 다시 누르면 접힌다) — 없던 것은 **그게 보이는
 * 손잡이**였다. 그래서 칩 안에 넣었다: ⓐ 세로가 **한 픽셀도 안 는다**(③ 과 안 부딪힌다) ⓑ
 * 터치 영역은 칩이 이미 갖고 있는 **48dp** 그대로다(아이콘만 16dp — 지선 카드 ↻ 와 같은 처방)
 * ⓒ 누르면 [open] 이 null 이 돼 **15초 폴링과 1초 눈금이 둘 다 그 자리에서 멎는다.**
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
    /** 마지막 조회 시각(ms) — 오른쪽 `N분 M초` 를 1초씩 줄이는 원점이다. */
    var fetchedAt by remember { mutableStateOf(0L) }
    /** **1초 눈금** — 이 값이 바뀌는 것이 곧 리컴포지션이고 [commuteAdvance] 의 한 걸음이다. */
    var tickMs by remember { mutableStateOf(System.currentTimeMillis()) }

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
                .onSuccess { rows = it; error = null; fetchedAt = System.currentTimeMillis() }
                .onFailure { error = BranchLive.humanError(it) }
            loading = false
            next += 15_000
            val now = System.currentTimeMillis()
            if (next < now) next = now + 15_000
            delay(next - now)
        }
    }

    /*
     * **1초 눈금**(v1.7.13 ①) — 호출은 한 번도 안 한다. 조회 뒤 흐른 초만 세어 [commuteAdvance]
     * 가 칸 사이를 메우게 하고, 오른쪽 `N분 M초` 도 같이 준다. 키가 [open] 이라 **접거나 시트를
     * 닫으면 이 루프도 취소돼 멎는다**(폴링과 같은 수명).
     */
    LaunchedEffect(open) {
        if (open == null) return@LaunchedEffect
        // ⚠ **열 때 눈금을 다시 맞춘다.** 안 그러면 [tickMs] 가 시트를 연 시각에 멈춰 있다가
        // 첫 눈금에서 `dt` 가 그만큼(실측 69초) 뛰어 기관차가 한 칸을 순간이동한다.
        tickMs = System.currentTimeMillis()
        while (isActive) {
            delay(1_000)
            tickMs = System.currentTimeMillis()
        }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).shrinkHeight(CHIP_ROW_H),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
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
                    // ⑦ **닫는 손잡이** — 고른 칩에만 붙는다. 칩을 누르면 접히는 동작
                    // (v1.7.9)의 눈에 보이는 표시이고, 터치 영역은 칩의 48dp 그대로다.
                    trailingIcon = if (open != s) null else {
                        { Icon(Icons.Filled.Close, "닫기", Modifier.size(16.dp)) }
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
            /*
             * 아이콘 자리(v1.7.13 ①) — **연속 좌표**다. 같은 칸이 겹치면 종전대로 **가까운 것만**
             * 남기고([distinctBy] 는 앞엣것을 남긴다 = 가까운 쪽), 그 한 대를 [commuteAdvance] 가
             * 한 걸음씩 앞으로 옮긴다.
             *
             * 장부는 `열번 → (자리, 그 걸음의 시각)` 이고 **[open] 이 바뀌면 통째로 버린다**
             * (칩을 옮기면 다른 역 이야기라 이어 달릴 것이 없다). 한 역의 목록은 세 대뿐이라
             * 따로 솎아 낼 것이 없다.
             *
             * ⚠ 걸음의 크기는 **[tickMs] 차이**로 잰다 — 그래서 한 눈금 안에 리컴포지션이
             * 몇 번 돌아도(응답 도착·테마 변경 …) `dt = 0` 이라 **두 번 걷지 않는다.**
             */
            val ledger = remember(s) { mutableMapOf<String, Pair<Float, Long>>() }
            val pos = near.distinctBy { commuteSlot(it.arvlMsg2, it.arvlCd) }.map { r ->
                val was = ledger[r.trainNo]
                val dt = if (was == null) 0f else (tickMs - was.second) / 1000f
                commuteAdvance(was?.first, commuteSlot(r.arvlMsg2, r.arvlCd), r.etaSec, dt)
                    .also { ledger[r.trainNo] = it to tickMs }
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
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
                        /*
                         * ⚠ **칸 키는 배율을 따라간다.** 오른쪽 두 줄은 sp 라 배율 1.5 에서
                         * 45dp 가 되는데 미니 노선을 32dp 로 못 박아 두면 그 차이만큼 캔버스가
                         * 비고, 밑 라벨(sp)이 두꺼워진 만큼 **기관차만 작아진다**
                         * (실측: 배율 1.5 에서 `k` 가 1.0 → 0.67 로 떨어졌다). 두 줄과 같은 키를
                         * 주면 칸 높이는 그대로면서 기관차가 제 크기를 지킨다.
                         */
                        val miniH = with(LocalDensity.current) {
                            maxOf(MINI_MIN_H, ETA_LINE.toDp() + DEST_LINE.toDp())
                        }
                        CommuteMiniLine(s, pos, Modifier.weight(1f).height(miniH))
                        Spacer(Modifier.width(8.dp))
                        /*
                         * ⚠ **칸 높이를 정하는 것은 [MINI_H] 가 아니라 이 두 줄이다**(v1.7.13 ③ 실측).
                         * 글꼴 기본 줄높이는 글자 크기의 1.4배쯤이라 `15sp + 10sp` 두 줄이 48dp 를
                         * 먹어 40dp 짜리 미니 노선을 이기고 있었다 — [MINI_H] 만 40 → 32 로 내렸을 때
                         * 카드가 152 → 148px 로 **4px 밖에 안 준** 이유다. 그래서 줄높이를 **박아 준다**
                         * (18sp / 12sp = 글자의 1.2배). 두 줄 합이 30dp 라 이제 [MINI_H] 가 다시
                         * 키를 정한다. **되돌리면 ③ 이 통째로 되돌아간다.**
                         */
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                // 남은 초도 **1초 눈금**을 탄다(v1.7.13 ①) — 종전엔 폴링마다
                                // 15초씩 뭉텅이로 줄어 기관차가 멈춰 보이는 것과 짝을 이뤘다.
                                if (at != null) atStationText(at.arvlCd)
                                else etaText(lead.etaSec - ((tickMs - fetchedAt) / 1000L).toInt()),
                                fontSize = 15.sp, lineHeight = ETA_LINE,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${lead.destName}행",
                                fontSize = 10.sp, lineHeight = DEST_LINE,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
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
 *
 * v1.7.12 는 **40dp** 였고 v1.7.13 ③ 에서 **32dp** 로 내렸다(카스: *"그리고 조금 더 줄여주고"*).
 * ⚠ **여기가 하한이다.** 밑에 눕는 라벨(10sp ≈ 12dp)과 2dp 틈을 빼면 기관차가 설 높이가
 * `32 − 12 − 2 = 18dp` = [LOCO_TOP_ROOM] 정확히 그 값이라 배율 1.0 에서 `k` 가 **1.0** 이다.
 * 더 내리면 `k` 가 1 밑으로 떨어져 **기관차부터 작아진다**(라벨은 안 준다).
 */
private val MINI_MIN_H = 32.dp

/**
 * 오른쪽 두 줄의 **줄높이**(v1.7.13 ③). 글꼴 기본 줄높이는 글자의 1.4배쯤이라 `15sp + 10sp`
 * 두 줄이 48dp 를 먹어 미니 노선을 이기고 있었다 — 여기를 1.2배로 박아 30sp 로 만든다.
 * 이 두 값이 곧 [MINI_MIN_H] 와 견주는 **칸의 키**다 — 한쪽만 고치면 세로가 도로 는다.
 */
private val ETA_LINE = 18.sp
private val DEST_LINE = 12.sp

/**
 * 칩 줄이 **부모에게 말하는 높이**(v1.7.13 ③). `FilterChip` 은 머티리얼의
 * `minimumInteractiveComponentSize` 때문에 **48dp** 로 재는데 보이는 알약은 32dp 뿐이라
 * 위아래로 8dp 씩 빈 띠가 남는다 — 지선 카드가 v1.7.12 ② 에서 헤더·칩 줄에 쓴 그 처방
 * ([shrinkHeight])을 그대로 쓴다. **터치 영역은 48dp 그대로다**(노드가 제 크기를 그대로
 * 보고하므로 손끝에 걸리는 넓이가 안 준다).
 */
private val CHIP_ROW_H = 34.dp

/** 기관차 한 대가 선 위로 차지하는 높이(굴뚝 끝 ~ 바퀴 바닥). 배율 1 일 때의 값이다. */
private val LOCO_TOP_ROOM = 18.dp

/**
 * 기관차가 **역 점을 덮는** 거리(칸 단위). 이보다 가까우면 그 점을 안 찍는다 —
 * 점의 흰 속이 바퀴 사이로 삐져나와 **턱수염처럼** 보였다(v1.7.12 실측 크롭 둘).
 * 몸통 반폭 11dp + 점 3.2dp = 14.2dp 이고 칸 간격이 접힘에서 ≈52dp 라 0.27 칸이 실측 경계다.
 */
private const val LOCO_COVERS = 0.35f

/**
 * **가로 미니 노선 한 줄** — 왼쪽 끝이 `5번째 전역+`, 오른쪽 끝이 **등록한 그 역**(큰 점)이고
 * 그 위에 다가오는 열차가 기관차로 선다.
 *
 * ⚠ [pos] 는 **정수 칸이 아니라 연속 좌표**다(v1.7.13 ①). [commuteSlot] 이 낸 칸을
 * [commuteAdvance] 가 남은 초로 흘린 값이라 1초마다 조금씩 커진다 — 그래서 기관차가
 * 칸을 뛰지 않고 **미끄러져** 다가온다.
 *
 * 색은 **호선 색**([lineArgb])이다 — 카스: *"아이콘을 좀 더 귀엽게 각호선 색상에 맞게"*.
 * 다만 **글자는 테마 색**을 쓴다(`onSurfaceVariant`) — 1호선 남색(`#0052A4`)·7호선
 * 올리브(`#747F00`) 같은 어두운 호선색은 다크 카드 위에서 대비가 무너진다. 그림(선·점·몸통)만
 * 호선 색이고 읽어야 하는 글자는 테마가 보장하는 대비를 쓴다.
 */
@Composable
private fun CommuteMiniLine(station: CommuteStation, pos: List<Float>, modifier: Modifier) {
    val line = Color(lineArgb(station.subwayId))
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val tm = rememberTextMeasurer()
    Canvas(modifier) {
        val nameL = tm.measure(
            station.name,
            TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ink),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        // 왼쪽 끝 라벨의 `+` 는 **"5번째 전역 이상"** 이다 — 이 칸은 `N ≥ 5` 와 위치를 아예
        // 안 말하는 문장(`8분 후`)이 다 같이 앉는 **바닥 칸**이라([commuteSlot] 6절) 그냥
        // `5번째 전역` 이라 적으면 뭉침이 조용해진다. 글자 하나라 왼쪽 여백도 거의 안 는다.
        val farL = tm.measure(
            "${COMMUTE_SLOTS - 1}번째 전역+",
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
        fun xOf(slot: Float) = x0 + step * slot.coerceIn(0f, (COMMUTE_SLOTS - 1).toFloat())
        // 기관차가 **덮은** 점은 안 찍는다 — v1.7.12 는 칸이 정수라 `i in slots` 였는데
        // 이제 자리가 연속이라([commuteAdvance]) **거리로** 판정한다([LOCO_COVERS]).
        fun covered(i: Int) = pos.any { kotlin.math.abs(it - i) < LOCO_COVERS }

        drawLine(line.copy(alpha = 0.32f), Offset(x0, y), Offset(x0 + step * (COMMUTE_SLOTS - 1), y),
            strokeWidth = 3.5.dp.toPx(), cap = StrokeCap.Round)
        // 지나온 역은 작은 흰 점 + 호선색 테, **등록한 역**만 꽉 찬 큰 점이다.
        // ⚠ 기관차가 선 칸엔 점을 안 찍는다 — 점이 바퀴 사이로 삐져나와 **턱수염처럼** 보였다
        //   (실측 크롭 둘 다). 그 칸은 기관차가 표시를 대신하고, 어느 역인지는 밑 라벨이 말한다.
        for (i in 0 until COMMUTE_SLOTS - 1) {
            if (covered(i)) continue
            drawCircle(Color.White, 3.2.dp.toPx(), Offset(xOf(i.toFloat()), y))
            drawCircle(line, 3.2.dp.toPx(), Offset(xOf(i.toFloat()), y), style = Stroke(1.8.dp.toPx()))
        }
        val lastX = xOf((COMMUTE_SLOTS - 1).toFloat())
        if (!covered(COMMUTE_SLOTS - 1)) drawCircle(line, 5.4.dp.toPx(), Offset(lastX, y))

        drawText(farL, topLeft = Offset(x0 - farL.size.width / 2f, y + 2.dp.toPx()))
        drawText(nameL, topLeft = Offset(
            (lastX - nameL.size.width / 2f)
                .coerceIn(0f, (size.width - nameL.size.width).coerceAtLeast(0f)),
            y + 2.dp.toPx()))

        // 기관차 키 — 선 위에 남은 자리에 맞춘다. 글자배율을 키우면 라벨이 두꺼워져 선이
        // 올라오는데, 그때 아이콘을 안 줄이면 굴뚝이 카드 위로 삐져나간다(배율 1.5 실측 자리).
        val k = (y / LOCO_TOP_ROOM.toPx()).coerceIn(0.55f, 1f)
        pos.forEach { drawCommuteLoco(xOf(it), y, line, ink, k) }
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
