package com.sinjeong.crewcalendar.presentation.live

import android.util.Log
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalTime
import kotlin.math.ceil
import kotlin.math.floor

/*
 * DID 노선도 — 편승지키미(kass79/SinjeongShuttle2) `ui/LineMap.kt` 이식(v1.6.43).
 * 그림은 원본 그대로다. 바꾼 것은 ① 안 쓰이던 `delay: DelayStatus` 파라미터 제거
 * (원본에서도 본문에서 한 번도 안 읽혔다) ② `inboundEtaAdj` 람다를 인라인
 * (fetchedAtMillis·nowMillis만 있으면 계산되는 값이라 파라미터일 이유가 없었다)
 * ③ 빈 상태 문구의 영업시간 판정을 [inService] 로 대체 — 원본 `Timetable`은 시각표
 * 전체(148줄)를 들고 있는데 여기서 필요한 건 boolean 하나였다.
 */

private val DidBg = Color(0xFF141414)
private val BandGreen = Color(0xFF3EC42E)
private val DidYellow = Color(0xFFF4E625)
private val InbOrange = Color(0xFFFF8F00)
private const val DEPOT_RUN_END = 2.5f     // 도림천 지나 중간쯤에서 기지 진입(아이콘 소멸)
private const val DEPOT_RUN_SEC = 150f     // 신도림→기지 진입 소요 가정
private const val TAG = "BranchLive"

/** 영업시간: 05:30~24:00 + 익일 0~1시(전날 영업 연장). 빈 상태 문구를 고르는 데만 쓴다. */
private fun inService(t: LocalTime = LocalTime.now()): Boolean {
    val mins = t.hour * 60 + t.minute
    return mins >= 330 || mins < 60
}

/**
 * 오늘 상세시트 안의 실시간 지선 열차 지도.
 *
 * **생명주기가 이 컴포넌트의 핵심이다.** 여기 있는 코루틴은 전부 컴포지션에 묶여 있어
 * 시트가 닫혀 이 컴포저블이 컴포지션에서 빠지는 순간 함께 취소된다:
 *  · 폴링 [LaunchedEffect] (5초) — 취소되면 [BranchLive.loadSnapshot] 호출이 멎는다
 *  · 시계 [LaunchedEffect] (1초) — 보간 이동·입고 카운트다운의 시간축
 *  · 새로고침 버튼의 [rememberCoroutineScope] 일회성 launch
 *  · [rememberInfiniteTransition] 애니메이션(셔머·바브·셰브런)
 * 즉 배터리·API 한도 소모 범위가 **"시트가 열려 있는 동안"** 으로 확실히 갇힌다.
 * (열림/닫힘은 [DisposableEffect]가 logcat `BranchLive` 태그로 남긴다 — 실기기 확인용)
 */
@Composable
internal fun BranchLiveMap(modifier: Modifier = Modifier) {
    var snap by remember { mutableStateOf(Snapshot()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        Log.i(TAG, "지도 열림 — 폴링 시작")
        onDispose { Log.i(TAG, "지도 닫힘 — 폴링·애니메이션 취소") }
    }
    LaunchedEffect(Unit) {
        while (isActive) {
            snap = BranchLive.loadSnapshot()
            delay(5_000)
        }
    }
    LaunchedEffect(Unit) {
        while (isActive) { now = System.currentTimeMillis(); delay(1_000) }
    }

    LineMapCard(
        trains = snap.trains,
        inbound = snap.inbound,
        fetchedAtMillis = snap.fetchedAtMillis,
        nowMillis = now,
        onRefresh = { scope.launch { snap = BranchLive.loadSnapshot(force = true) } },
        modifier = modifier,
    )
}

@Composable
private fun LineMapCard(
    trains: List<TrainMark>,
    inbound: List<InboundTrain>,
    fetchedAtMillis: Long,
    nowMillis: Long,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    fun adjEta(t: InboundTrain) =
        (t.etaSec - ((nowMillis - fetchedAtMillis) / 1000)).toInt().coerceAtLeast(0)

    // 입고열차 신도림 도착 시각 추적 → 기지 회송 애니메이션
    val depotRuns = remember { mutableStateMapOf<String, Long>() }
    SideEffect {
        inbound.forEach { t ->
            if (adjEta(t) <= 0 && !depotRuns.containsKey(t.trainNo)) depotRuns[t.trainNo] = nowMillis
        }
        depotRuns.keys.toList().forEach { no ->
            if ((nowMillis - (depotRuns[no] ?: 0L)) / 1000f > DEPOT_RUN_SEC + 20f) depotRuns.remove(no)
        }
    }
    val runners = depotRuns.mapNotNull { (no, ts) ->
        val e = (nowMillis - ts) / 1000f
        if (e > DEPOT_RUN_SEC) null            // 도림천 도달 → 화면에서 퇴장
        else no to (4f - minOf(4f - DEPOT_RUN_END, e * ((4f - DEPOT_RUN_END) / DEPOT_RUN_SEC)))
    }
    Card(
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DidBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Box {
            Column(Modifier.padding(vertical = 10.dp)) {
                val infinite = rememberInfiniteTransition(label = "did")
                val shimmer by infinite.animateFloat(
                    1f, 0.90f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "shimmer")
                val bob by infinite.animateFloat(
                    -1.2f, 1.2f, infiniteRepeatable(tween(620), RepeatMode.Reverse), label = "bob")
                val phase by infinite.animateFloat(
                    0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
                    label = "phase")

                // 위치 보간 드리프트 + 뒤로가기 방지(단조 전진)
                val elapsedSec = ((nowMillis - fetchedAtMillis) / 1000f).coerceAtLeast(0f)
                val maxIdx = (BranchLine.stations.size - 1).toFloat()
                val lastShown = remember { HashMap<String, Float>() }
                run { // 사라진 열차의 기억 정리
                    val alive = trains.map { it.trainNo + if (it.toSindorim) "U" else "D" }.toSet()
                    lastShown.keys.retainAll(alive)
                }
                val animated = trains
                    .sortedBy { it.trainNo + if (it.toSindorim) "U" else "D" }
                    .map { t ->
                        key(t.trainNo + if (t.toSindorim) "U" else "D") {
                            val atTerminus = t.position <= 0.2f || t.position >= 3.8f
                            val holding = t.statusText.endsWith("진입") || t.statusText.endsWith("도착") ||
                                (t.statusText.contains("회차") && atTerminus)
                            val target = if (holding) t.position else {
                                val dir = if (t.toSindorim) 1f else -1f
                                // 현재 구간의 실측 주행시간으로 화면 속도 결정 → 실제 열차와 같은 리듬
                                val segIdx = (if (t.toSindorim) floor(t.position.toDouble())
                                              else ceil(t.position.toDouble()) - 1).toInt().coerceIn(0, 3)
                                val segSec = (if (t.toSindorim) BranchLine.SEG_UP[segIdx]
                                              else BranchLine.SEG_DN[segIdx]).toFloat()
                                val advance = elapsedSec / segSec
                                // 다음 역까지 '완주'하는 연속 보간 (도착 후 그 자리에서 대기)
                                val cap = if (t.toSindorim)
                                    (floor(t.position.toDouble()).toFloat() + 1f - t.position)
                                else
                                    (t.position - (ceil(t.position.toDouble()).toFloat() - 1f))
                                (t.position + dir * minOf(advance, cap.coerceAtLeast(0f)))
                                    .coerceIn(0f, maxIdx)
                            }
                            // 후진 완전 금지: 어떤 크기의 후퇴 신호도 무시하고 전진 상태 유지
                            val k = t.trainNo + if (t.toSindorim) "U" else "D"
                            val prev = lastShown[k]
                            val forwardSafe = if (prev == null) target else {
                                val backstep = if (t.toSindorim) prev - target else target - prev
                                if (backstep > 0f) prev else target
                            }
                            lastShown[k] = forwardSafe
                            // 갱신 주기(5초)에 걸쳐 선형으로 흘러 역과 역 사이를 일정 속도로 이동
                            val p by animateFloatAsState(
                                forwardSafe, tween(5000, easing = LinearEasing), label = "t${t.trainNo}")
                            Triple(t, p, holding)
                        }
                    }
                val runnerAnimated = runners.map { (no, pos) ->
                    val p by animateFloatAsState(pos, tween(1000, easing = LinearEasing), label = "r$no")
                    no to p
                }

                // 노선도는 **글자가 아니라 그림**이다 — 칸 높이(190dp)·역 간격·아이콘이 전부 dp로
                // 고정돼 있는데 글자만 sp라, 시스템 폰트를 키우면 양 끝 역명(까치산·신도림)이
                // 카드 밖으로 잘려 나갔다(fontScale 1.5 실측). 이 저장소가 여러 번 물린 자리다.
                //
                // 그래서 **지도 안에서만** fontScale 상한을 1.2로 잡는다. dp 배율(density)은 그대로라
                // 그림 크기는 안 변하고, 폰트를 키운 사용자도 1.2배까지는 커진 글자를 본다.
                // 시트의 나머지(행로표·메모·버튼)는 시스템 배율을 그대로 따른다.
                //
                // ⚠ [rememberTextMeasurer]는 **만들어질 때의 density를 물고 간다** — 반드시
                //   이 Provider 안에서 만들어야 상한이 실제 측정에 먹는다(밖에 두면 조용히 무시된다).
                val d = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(d.density, d.fontScale.coerceAtMost(1.2f))
                ) {
                val tm = rememberTextMeasurer()
                Canvas(Modifier.fillMaxWidth().height(190.dp).padding(top = 4.dp)) {
                    val pad = 20.dp.toPx()
                    // 역 화면 위치 = 구간 실측시간 비율(상·하행 평균). 표시만 변환(위치 계산은 0~4 유지)
                    val stFrac = floatArrayOf(0f, 0.211f, 0.485f, 0.745f, 1f)
                    fun timeFrac(pos: Float): Float {
                        val i = pos.toInt().coerceIn(0, 3)
                        val f = (pos - i).coerceIn(0f, 1f)
                        return stFrac[i] + (stFrac[i + 1] - stFrac[i]) * f
                    }
                    fun xOf(pos: Float) = pad + timeFrac(pos) * (size.width - pad * 2)

                    val railY = size.height * 0.30f
                    val bandY = size.height * 0.62f
                    val railH = 7.dp.toPx()
                    val bandH = 13.dp.toPx()
                    val spacing = 30.dp.toPx()

                    // 상단 선로(까치산 방면 ←): 까치산은 단선이라 크로스오버 지점부터 오른쪽만 존재
                    val kkX0 = xOf(0f)
                    val nsX0 = xOf(1f)
                    val railStart = nsX0 - 0.22f * (nsX0 - kkX0)   // 크로스오버 지점
                    drawRect(BandGreen.copy(alpha = 0.5f),
                        topLeft = Offset(railStart, railY - railH / 2),
                        size = Size(size.width - railStart, railH))
                    var cl = size.width + spacing - phase * spacing
                    while (cl > railStart) {
                        drawChevron(cl, railY, railH * 0.42f, Color.White.copy(alpha = 0.35f), false)
                        cl -= spacing
                    }
                    drawPill(tm, "← 까치산 방면", 8.dp.toPx(), 11.dp.toPx(), Color(0xFFFFB3AB))

                    // 메인 밴드 (신도림 방면 →)
                    drawRect(BandGreen, topLeft = Offset(0f, bandY - bandH / 2),
                        size = Size(size.width, bandH))
                    var cx = -spacing + phase * spacing
                    while (cx < size.width + spacing) {
                        drawChevron(cx, bandY, bandH * 0.36f, Color.White.copy(alpha = 0.5f), true)
                        cx += spacing
                    }
                    drawPill(tm, "신도림 방면 →", 8.dp.toPx(), size.height - 12.dp.toPx(), Color(0xFF8FD6FF))

                    // ── 까치산 건넘선(단선): 윗선로 → 아래선로로 넘어와 까치산 단선 진입 ──
                    drawLine(BandGreen.copy(alpha = 0.5f),
                        start = Offset(railStart + 11.dp.toPx(), railY),
                        end = Offset(railStart - 11.dp.toPx(), bandY),
                        strokeWidth = railH, cap = StrokeCap.Round)

                    // ── 도림천 건넘선(X 복선): 도림천 직후 양방향 크로스오버 ──
                    val drX = xOf(3f)
                    val sdX = xOf(4f)
                    val xoR = drX + 0.16f * (sdX - drX)   // 도림천에 바짝
                    drawLine(BandGreen.copy(alpha = 0.5f),
                        start = Offset(xoR - 11.dp.toPx(), bandY),   // 상행: 아래→위
                        end = Offset(xoR + 11.dp.toPx(), railY),
                        strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                    drawLine(BandGreen.copy(alpha = 0.5f),
                        start = Offset(xoR - 11.dp.toPx(), railY),   // 하행: 위→아래
                        end = Offset(xoR + 11.dp.toPx(), bandY),
                        strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)

                    // 역 점 + 이름 (양천구청 = 승무사업소, 노란 강조 + 차고 아이콘)
                    BranchLine.stations.forEachIndexed { i, name ->
                        val x = xOf(i.toFloat())
                        val depot = name == "양천구청"
                        drawCircle(Color.White.copy(alpha = 0.55f), 3.5.dp.toPx(), Offset(x, railY))
                        drawCircle(Color.White, 6.5.dp.toPx(), Offset(x, bandY))
                        if (depot) {
                            drawCircle(DidYellow.copy(alpha = 0.16f), 17.dp.toPx(), Offset(x, bandY))
                            drawCircle(DidYellow, 10.5.dp.toPx(), Offset(x, bandY),
                                style = Stroke(width = 3.dp.toPx()))
                        }
                        val label = tm.measure(name, TextStyle(
                            fontSize = 11.5.sp, fontWeight = FontWeight.Bold,
                            color = if (depot) DidYellow else Color.White))
                        // 양 끝 역(까치산·신도림)은 이름 반폭이 캔버스 여백(20dp)보다 넓어 삐져나간다
                        drawText(label, topLeft = Offset(
                            (x - label.size.width / 2f)
                                .coerceIn(0f, (size.width - label.size.width).coerceAtLeast(0f)),
                            bandY + 18.dp.toPx()))
                        if (depot) {
                            val gx = x + label.size.width / 2f + 6.dp.toPx()
                            val gy = bandY + 21.dp.toPx()
                            val w = 12.dp.toPx(); val h = 8.dp.toPx()
                            drawPath(Path().apply {                       // 지붕
                                moveTo(gx - 1.5.dp.toPx(), gy)
                                lineTo(gx + w / 2, gy - 5.5.dp.toPx())
                                lineTo(gx + w + 1.5.dp.toPx(), gy)
                                close()
                            }, DidYellow)
                            drawRoundRect(DidYellow.copy(alpha = 0.85f), Offset(gx, gy), Size(w, h),
                                CornerRadius(1.5.dp.toPx()))
                            drawRoundRect(DidBg,
                                Offset(gx + 3.5.dp.toPx(), gy + 2.5.dp.toPx()),
                                Size(w - 7.dp.toPx(), h - 2.5.dp.toPx()), CornerRadius(1.dp.toPx()))
                        }
                    }

                    // 크로스오버 구간에서 열차가 사선으로 선로를 갈아타는 y좌표
                    fun trainY(toSindorim: Boolean, pos: Float): Float {
                        val fr = timeFrac(pos)
                        val xoKk = timeFrac(1f) - 0.22f * (timeFrac(1f) - timeFrac(0f))
                        val xoDr = timeFrac(3f) + 0.16f * (timeFrac(4f) - timeFrac(3f))
                        // 사선 전환 폭: 너무 좁으면 그 구간만 급격히 꺾여 속도가 튀어 보인다.
                        val ramp = 0.075f
                        return if (toSindorim) when {
                            fr < xoDr -> bandY
                            fr < xoDr + ramp -> bandY + (railY - bandY) * ((fr - xoDr) / ramp).coerceIn(0f, 1f)
                            else -> railY
                        } else when {
                            fr > xoKk -> railY
                            fr > xoKk - ramp -> railY + (bandY - railY) * ((xoKk - fr) / ramp).coerceIn(0f, 1f)
                            else -> bandY
                        }
                    }
                    animated.forEach { (t, pos, holding) ->
                        val x = xOf(pos)
                        // 까치산 방면(상단 선로)은 선로 톤에 맞춰 살짝 흐리게
                        val alpha = (if (t.toSindorim) 1f else 0.78f) *
                            if (holding) 1f else shimmer      // 정차·회차 = 고정 / 주행 = 셔머
                        // 주행 중엔 레일 진동 같은 미세한 상하 바브
                        val y = trainY(t.toSindorim, pos) + (if (!holding) bob.dp.toPx() else 0f)
                        drawTrainIcon(tm, t.trainNo, x, y, t.toSindorim, alpha, BandGreen)

                        val dirLbl = tm.measure(
                            if (t.toSindorim) "신도림행" else "까치산행",
                            TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                color = (if (t.toSindorim) Color(0xFF8FD6FF) else Color(0xFFFFB3AB))
                                    .copy(alpha = alpha)))
                        drawText(dirLbl, topLeft = Offset(
                            (x - dirLbl.size.width / 2f)
                                .coerceIn(2.dp.toPx(), size.width - dirLbl.size.width - 2.dp.toPx()),
                            y - 26.dp.toPx()))
                        // 회차 배지는 종착역(까치산·신도림)에서만 — 위치 기반 절대 규칙
                        if (t.statusText.contains("회차") && (pos <= 0.2f || pos >= 3.8f)) {
                            val tag = tm.measure("↻ 회차", TextStyle(
                                fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = DidYellow))
                            drawText(tag, topLeft = Offset(
                                (x - tag.size.width / 2f)
                                    .coerceIn(4.dp.toPx(), size.width - tag.size.width - 4.dp.toPx()),
                                y - 32.dp.toPx()))
                        }
                    }

                    // 입고 회송: 신도림 도착 → 윗선로로 도림천 거쳐 기지 진입 (주황)
                    runnerAnimated.forEach { (no, pos) ->
                        drawTrainIcon(tm, no, xOf(pos), railY, false, 1f, InbOrange)
                    }

                    // 입고 접근(10분 전~도착 전): 윗선로 위 오른쪽에 텍스트로 안내
                    inbound.map { it to adjEta(it) }
                        .filter { it.second in 1..600 }
                        .minByOrNull { it.second }
                        ?.let { (t, eta) ->
                            val l = tm.measure("↙ 입고 ${t.trainNo} · ${eta / 60}분 ${eta % 60}초",
                                TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold,
                                    color = InbOrange))
                            val tx = size.width - l.size.width - 58.dp.toPx()
                            drawRoundRect(Color.White.copy(alpha = 0.10f),
                                topLeft = Offset(tx - 7.dp.toPx(), 6.dp.toPx()),
                                size = Size(l.size.width + 14.dp.toPx(), l.size.height + 8.dp.toPx()),
                                cornerRadius = CornerRadius(50f))
                            drawText(l, topLeft = Offset(tx, 10.dp.toPx()))
                        }

                    // 표시할 열차가 없을 때: 이유를 알려주는 빈 상태 안내
                    if (animated.isEmpty() && runnerAnimated.isEmpty()) {
                        val empty = tm.measure(
                            if (inService()) "실시간 조회 중…" else "금일 운행 종료 (영업 05:30~)",
                            TextStyle(fontSize = 11.sp, color = Color(0xFF9AA39C)))
                        drawText(empty, topLeft = Offset(
                            size.width / 2 - empty.size.width / 2,
                            (railY + bandY) / 2 - empty.size.height / 2))
                    }
                }
                }
            }
            // 즉시 갱신 버튼 (탭하면 한 바퀴 회전 피드백)
            var refreshTick by remember { mutableIntStateOf(0) }
            val spin by animateFloatAsState(refreshTick * 360f, tween(700), label = "spin")
            Surface(
                color = Color.White.copy(alpha = 0.10f),
                shape = CircleShape,
                border = BorderStroke(1.2.dp, BandGreen.copy(alpha = 0.75f)),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 8.dp)
                    .clickable { refreshTick++; onRefresh() },
            ) {
                // 직접 그린 새로고침 글리프 (원호 + 화살촉) — 아이콘 라이브러리 의존성 없음
                Canvas(Modifier.padding(9.dp).size(18.dp).rotate(spin)) {
                    val c = Color(0xFFB9F5C0)
                    drawArc(c, startAngle = -50f, sweepAngle = 290f, useCenter = false,
                        style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round))
                    val r = size.minDimension / 2f
                    val ang = Math.toRadians(-50.0)
                    val ax = size.width / 2f + (r * kotlin.math.cos(ang)).toFloat()
                    val ay = size.height / 2f + (r * kotlin.math.sin(ang)).toFloat()
                    val ah = 5.dp.toPx()
                    drawPath(Path().apply {
                        moveTo(ax + ah * 0.9f, ay - ah * 0.5f)
                        lineTo(ax + ah * 0.2f, ay + ah * 0.9f)
                        lineTo(ax - ah * 0.9f, ay - ah * 0.3f)
                        close()
                    }, c)
                }
            }
        }
    }
}

private fun DrawScope.drawChevron(x: Float, y: Float, h: Float, color: Color, pointRight: Boolean) {
    val w = h * 0.9f * (if (pointRight) 1 else -1)
    drawPath(Path().apply {
        moveTo(x - w / 2, y - h); lineTo(x + w / 2, y); lineTo(x - w / 2, y + h)
    }, color, style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round))
}

private fun DrawScope.drawTrainIcon(
    tm: TextMeasurer, no: String, cxRaw: Float, cy: Float,
    faceRight: Boolean, alpha: Float, body: Color,
) {
    val label = tm.measure(no, TextStyle(
        fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color.White))
    val bodyW = maxOf(44.dp.toPx(), label.size.width + 14.dp.toPx())
    val bodyH = 24.dp.toPx()
    val noseW = 11.dp.toPx()
    val totalW = bodyW + noseW
    // 종착역은 `xOf`가 캔버스 여백(20dp)까지만 물러나 있어서 아이콘 반폭(~27dp)이
    // 그대로 넘친다 — 신도림에 선 열차가 카드 밖으로 잘려 나갔다(실기기 확인).
    // 원본은 방면 라벨·회차 배지만 clamp 하고 아이콘은 빠뜨렸다. 같은 규칙을 아이콘에도 준다.
    val cx = cxRaw.coerceIn(totalW / 2, size.width - totalW / 2)
    val left = if (faceRight) cx - totalW / 2 else cx - totalW / 2 + noseW
    val right = left + bodyW
    val top = cy - bodyH / 2

    // 1) 어두운 후광 (노즈 포함 전체 영역 덮음)
    drawRoundRect(Color(0xFF0E0E0E).copy(alpha = 0.9f * alpha),
        topLeft = Offset((if (faceRight) left else left - noseW) - 3.dp.toPx(), top - 3.dp.toPx()),
        size = Size(totalW + 6.dp.toPx(), bodyH + 6.dp.toPx()),
        cornerRadius = CornerRadius(13.dp.toPx()))

    // 2) 진행 방향 노즈: 삼각형 (차체색 + 흰 윤곽)
    val nose = Path().apply {
        if (faceRight) {
            moveTo(right - 1.dp.toPx(), cy - bodyH * 0.36f)
            lineTo(right + noseW, cy)
            lineTo(right - 1.dp.toPx(), cy + bodyH * 0.36f)
        } else {
            moveTo(left + 1.dp.toPx(), cy - bodyH * 0.36f)
            lineTo(left - noseW, cy)
            lineTo(left + 1.dp.toPx(), cy + bodyH * 0.36f)
        }
        close()
    }
    drawPath(nose, body.copy(alpha = alpha))
    drawPath(nose, Color.White.copy(alpha = alpha),
        style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round))

    // 3) 차체: 둥근 사각 (노즈 밑변을 덮어 매끈하게 연결)
    drawRoundRect(body.copy(alpha = alpha), Offset(left, top), Size(bodyW, bodyH),
        CornerRadius(9.dp.toPx()))
    drawRoundRect(Color.White.copy(alpha = alpha), Offset(left, top), Size(bodyW, bodyH),
        CornerRadius(9.dp.toPx()), style = Stroke(width = 2.2.dp.toPx()))

    // 4) 열번
    drawText(label, topLeft = Offset(left + bodyW / 2 - label.size.width / 2f,
        cy - label.size.height / 2f), alpha = alpha)
}

/** 반투명 필 배경의 방면 라벨 */
private fun DrawScope.drawPill(tm: TextMeasurer, text: String, x: Float, cy: Float, color: Color) {
    val l = tm.measure(text, TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = color))
    val padH = 7.dp.toPx(); val padV = 3.dp.toPx()
    drawRoundRect(Color.White.copy(alpha = 0.10f),
        topLeft = Offset(x, cy - l.size.height / 2 - padV),
        size = Size(l.size.width + padH * 2, l.size.height + padV * 2),
        cornerRadius = CornerRadius(50f))
    drawText(l, topLeft = Offset(x + padH, cy - l.size.height / 2f))
}
