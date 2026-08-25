package com.sinjeong.crewcalendar.presentation.live

import android.content.Context
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
/** 정차 표시 빨강 — v1.6.47엔 아이콘 모서리 점이었고 v1.6.49부터 **역 동그라미** 몫이다 */
private val StopRed = Color(0xFFFF2D2D)
private const val DEPOT_RUN_END = 2.5f     // 도림천 지나 중간쯤에서 기지 진입(아이콘 소멸)
private const val DEPOT_RUN_SEC = 150f     // 신도림→기지 진입 소요 가정
private const val TAG = "BranchLive"
/** 회차 기억 저장 키 — 값 형식은 [BranchLive.turnMemory] */
private const val TURN_KEY = "turn_memory"

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
/**
 * @param bleed 상세시트 좌우 여백을 **밖으로 되찾는 폭**(한쪽). 지도만 시트 폭에 가깝게 넓힌다
 *   (v1.6.50 사용자: *"애뮬레이트 가로 길이는 조금 더 늘려줘"*). 역 간격이 그만큼 벌어져
 *   열차·라벨이 겹칠 일이 준다. 여백을 얼마나 내줄 수 있는지는 **부르는 쪽만 안다** —
 *   접힘 시트는 20dp, 펼침 패널은 10dp라 같은 값을 쓰면 패널에서 카드가 벽에 붙는다.
 */
@Composable
internal fun BranchLiveMap(bleed: Dp = 0.dp, modifier: Modifier = Modifier) {
    var snap by remember { mutableStateOf(Snapshot()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val scope = rememberCoroutineScope()

    val ctx = LocalContext.current

    DisposableEffect(Unit) {
        Log.i(TAG, "지도 열림 — 폴링 시작")
        onDispose { Log.i(TAG, "지도 닫힘 — 폴링·애니메이션 취소") }
    }
    LaunchedEffect(Unit) {
        // 회차 기억 복원(v1.6.70 ⑤) — 프로세스가 죽어도 **직전에 본** 회차 열차를 되살린다.
        // 근거와 한계는 [BranchLive.restoreTurnMemory] KDoc. 저장소를 여기서 다루는 이유는
        // [BranchLive]를 안드로이드 클래스에서 자유롭게 둬야 유닛테스트가 그대로 돌기 때문이다.
        val sp = withContext(Dispatchers.IO) {
            ctx.getSharedPreferences("branch_live", Context.MODE_PRIVATE)
        }
        BranchLive.restoreTurnMemory(sp.getString(TURN_KEY, "").orEmpty(), System.currentTimeMillis())
        var saved = ""
        while (isActive) {
            snap = BranchLive.loadSnapshot()
            // 바뀔 때만 쓴다 — 시트가 열려 있는 동안 5~15초마다 도는 자리다.
            BranchLive.turnMemory().let { if (it != saved) { saved = it; sp.edit().putString(TURN_KEY, it).apply() } }
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
        error = snap.error,
        onRefresh = { scope.launch { snap = BranchLive.loadSnapshot(force = true) } },
        modifier = modifier.bleedH(bleed),
    )
}

/**
 * 부모의 좌우 여백을 [amount]만큼 **밖으로 넘어가서** 그린다(음수 padding).
 * 차지하는 자리(부모에게 보고하는 폭)는 그대로라 위아래 형제가 밀리지 않는다.
 */
private fun Modifier.bleedH(amount: Dp) = if (amount <= 0.dp) this else this.layout { m, c ->
    val extra = amount.roundToPx() * 2
    val p = m.measure(c.copy(minWidth = c.maxWidth + extra, maxWidth = c.maxWidth + extra))
    layout(c.maxWidth, p.height) { p.place(-extra / 2, 0) }
}

@Composable
private fun LineMapCard(
    trains: List<TrainMark>,
    inbound: List<InboundTrain>,
    fetchedAtMillis: Long,
    nowMillis: Long,
    error: String?,
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
            Column(Modifier.padding(vertical = 8.dp)) {
                val infinite = rememberInfiniteTransition(label = "did")
                val shimmer by infinite.animateFloat(
                    1f, 0.90f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "shimmer")
                // v1.6.70: 여기 있던 `bob`(상하 까딱임)을 **뺐다**(사용자: *"가속, 감속은
                // 없었으면해!"*). 이징이 기본값(FastOutSlowIn)이라 끝에서 느려졌다 가운데서
                // 빨라지는 **가속·감속 그 자체**였다 — 게다가 왕복 운동이라 양 끝에서 방향이
                // 꺾인다. v1.6.55에서 진폭을 ±1.2 → 0~1.2dp로 좁혀 "떠오르는 순간"만 없앴던
                // 것을, 이번엔 운동을 통째로 걷어냈다. 주행/정차 구분은 [shimmer]가 계속 한다.
                val phase by infinite.animateFloat(
                    0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
                    label = "phase")

                // 위치 보간 드리프트 + 뒤로가기 방지(단조 전진)
                val elapsedSec = ((nowMillis - fetchedAtMillis) / 1000f).coerceAtLeast(0f)
                val maxIdx = (BranchLine.stations.size - 1).toFloat()
                val lastShown = remember { HashMap<String, Float>() }
                // 화면에 **실제로 그려진** 위치. 등속 지속시간을 남은 거리로 잡는 데 쓴다(v1.6.70).
                val lastDrawn = remember { HashMap<String, Float>() }
                run { // 사라진 열차의 기억 정리
                    val alive = trains.map { it.trainNo + if (it.toSindorim) "U" else "D" }.toSet()
                    lastShown.keys.retainAll(alive)
                    lastDrawn.keys.retainAll(alive)
                }
                val animated = trains
                    .sortedBy { it.trainNo + if (it.toSindorim) "U" else "D" }
                    .map { t ->
                        key(t.trainNo + if (t.toSindorim) "U" else "D") {
                            val atTerminus = t.position <= 0.2f || t.position >= 3.8f
                            val holding = t.statusText.endsWith("진입") || t.statusText.endsWith("도착") ||
                                (t.statusText.contains("회차") && atTerminus)
                            // 현재 구간의 실측 주행시간 = **화면 속도의 유일한 기준**(실차와 같은 리듬).
                            // 역 화면 간격(`stFrac`)도 같은 실측시간 비율이라 px/초가 구간마다 같다.
                            val segIdx = (if (t.toSindorim) floor(t.position.toDouble())
                                          else ceil(t.position.toDouble()) - 1).toInt().coerceIn(0, 3)
                            val segSec = (if (t.toSindorim) BranchLine.SEG_UP[segIdx]
                                          else BranchLine.SEG_DN[segIdx]).toFloat()
                            val target = if (holding) t.position else {
                                val dir = if (t.toSindorim) 1f else -1f
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
                            /*
                             * **등속**(v1.6.70). 지속시간을 `남은 거리 ÷ 실제 주행속도`로 잡는다.
                             *
                             * 종전 `tween(5000)`은 목표가 새로 올 때마다 "5초 안에 거기까지"로
                             * 다시 잡혔다 — 남은 거리가 크면 빨라지고 작으면 느려져서, 갱신
                             * 경계마다 **속도가 확 달라졌다**(사용자가 본 가속·감속의 정체).
                             * 이제 거리가 얼마든 속도는 `1구간 ÷ segSec` 하나뿐이라
                             * ③의 갱신 주기가 15↔5초로 바뀌어도 화면 속도는 안 변한다.
                             *
                             * ponytail: 상한 = 한 구간 주행시간. 그보다 먼 목표(열차가 오래
                             * 사라졌다 나타난 경우)는 그만큼 빨리 따라붙는다 — 데이터 도약이라
                             * 어차피 등속으로 메울 거리가 아니다. 필요하면 여기 clamp만 손본다.
                             */
                            val durMs = (kotlin.math.abs(forwardSafe - (lastDrawn[k] ?: forwardSafe))
                                * segSec * 1000f).toInt().coerceIn(1, segSec.toInt() * 1000)
                            val p by animateFloatAsState(
                                forwardSafe, tween(durMs, easing = LinearEasing), label = "t${t.trainNo}")
                            lastDrawn[k] = p
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
                // 190 → 172 → 156 → **146dp**(v1.6.55). 이번엔 열차마다 붙던 `신도림행`·
                // `까치산행` 라벨(7.5sp ≈ 8.8dp + 여백 1dp)이 통째로 빠져 **아이콘 위 10dp가
                // 비었다** — 그만큼 캔버스를 줄인다(사용자가 계속 요구해 온 "지도 더 작게").
                // 선로 자리 비율(railY 0.43 · bandY 0.735)은 그대로 두어 위·아래가 고르게 준다
                // (railY 67 → 62.8dp · bandY 114.7 → 107.3dp).
                Canvas(Modifier.fillMaxWidth().height(146.dp).padding(top = 4.dp)) {
                    val pad = 20.dp.toPx()
                    // 역 화면 위치 = 구간 실측시간 비율(상·하행 평균). 표시만 변환(위치 계산은 0~4 유지)
                    val stFrac = floatArrayOf(0f, 0.211f, 0.485f, 0.745f, 1f)
                    fun timeFrac(pos: Float): Float {
                        val i = pos.toInt().coerceIn(0, 3)
                        val f = (pos - i).coerceIn(0f, 1f)
                        return stFrac[i] + (stFrac[i + 1] - stFrac[i]) * f
                    }
                    fun xOf(pos: Float) = pad + timeFrac(pos) * (size.width - pad * 2)

                    val railY = size.height * 0.43f
                    val bandY = size.height * 0.735f
                    // 외선·내선 **같은 두께**(v1.6.55 사용자: *"외선,내선 선로 두께 차이가 많이
                    // 나네?"*). 종전 윗선로 7dp / 아래 띠 13dp로 거의 두 배였다.
                    //
                    // 두께 하나만 두면 나머지가 따라온다 — 역 흰 동그라미는 예나 지금이나
                    // **띠를 꽉 채우는 크기**(지름 = 두께: 종전 3.5×2 = 7 · 6.5×2 = 13)였고,
                    // 정차 빨간 속점도 반지름의 0.68배(2.4/3.5 · 4.4/6.5)로 같은 비율이었다.
                    // 따로 적힌 dp 상수 넷을 여기 유도값 둘로 접었다.
                    // 두 선로를 가르는 건 이제 **초록 농도**뿐이다(윗선로 alpha 0.5 · 아래 1.0).
                    val trackH = 10.dp.toPx()
                    val stR = trackH / 2f          // 역 흰 동그라미 반지름 (5dp)
                    val stopR = trackH * 0.34f     // 정차 빨간 속점 반지름 (3.4dp)
                    val spacing = 30.dp.toPx()

                    // 상단 선로(까치산 방면 ←): 까치산은 단선이라 크로스오버 지점부터 오른쪽만 존재
                    val kkX0 = xOf(0f)
                    val nsX0 = xOf(1f)
                    val railStart = nsX0 - 0.22f * (nsX0 - kkX0)   // 크로스오버 지점
                    drawRect(BandGreen.copy(alpha = 0.5f),
                        topLeft = Offset(railStart, railY - trackH / 2),
                        size = Size(size.width - railStart, trackH))
                    var cl = size.width + spacing - phase * spacing
                    while (cl > railStart) {
                        drawChevron(cl, railY, trackH * 0.36f, Color.White.copy(alpha = 0.35f), false)
                        cl -= spacing
                    }
                    // v1.6.50: 여기 있던 `← 까치산 방면` 알약을 뺐다(사용자: *"까치산 방면,
                    // 신도림 방면 ← 텍스트 없애줘!"*). v1.6.55에선 열차마다 붙던 행선 라벨까지
                    // 뺐다 — 방향을 말하는 건 이제 진행 셰브런(`>`·`<`)과 선로 자리뿐이다.

                    // 메인 밴드 (신도림 방면 →)
                    // 화살표 반높이 계수는 위·아래 **하나로 통일**(종전 0.42 / 0.36).
                    // 0.36이면 2×3.6 + 선 굵기 2.6 = 9.8dp라 10dp 띠 안에 딱 든다.
                    drawRect(BandGreen, topLeft = Offset(0f, bandY - trackH / 2),
                        size = Size(size.width, trackH))
                    var cx = -spacing + phase * spacing
                    while (cx < size.width + spacing) {
                        drawChevron(cx, bandY, trackH * 0.36f, Color.White.copy(alpha = 0.5f), true)
                        cx += spacing
                    }

                    // ── 까치산 건넘선(단선): 윗선로 → 아래선로로 넘어와 까치산 단선 진입 ──
                    // 건넘선 셋도 **같은 굵기**(선로의 절반, 5dp)로 맞췄다 — 종전엔 여기만
                    // `railH`(7dp)고 아래 도림천 X는 3dp라, 같은 성격의 선이 둘로 갈려 있었다.
                    val xoverW = trackH * 0.5f
                    drawLine(BandGreen.copy(alpha = 0.5f),
                        start = Offset(railStart + 11.dp.toPx(), railY),
                        end = Offset(railStart - 11.dp.toPx(), bandY),
                        strokeWidth = xoverW, cap = StrokeCap.Round)

                    // ── 도림천 건넘선(X 복선): 도림천 직후 양방향 크로스오버 ──
                    val drX = xOf(3f)
                    val sdX = xOf(4f)
                    val xoR = drX + 0.16f * (sdX - drX)   // 도림천에 바짝
                    drawLine(BandGreen.copy(alpha = 0.5f),
                        start = Offset(xoR - 11.dp.toPx(), bandY),   // 상행: 아래→위
                        end = Offset(xoR + 11.dp.toPx(), railY),
                        strokeWidth = xoverW, cap = StrokeCap.Round)
                    drawLine(BandGreen.copy(alpha = 0.5f),
                        start = Offset(xoR - 11.dp.toPx(), railY),   // 하행: 위→아래
                        end = Offset(xoR + 11.dp.toPx(), bandY),
                        strokeWidth = xoverW, cap = StrokeCap.Round)

                    // 크로스오버 구간에서 열차가 사선으로 선로를 갈아타는 y좌표.
                    // **역 그리기보다 먼저** 정의한다 — 아래 [holdingStops]가 "어느 선로의 역인지"를
                    // 이걸로 가른다(v1.6.49).
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

                    /**
                     * 정차 중인 열차가 서 있는 역 = `(역 번호, 윗선로인가)`(v1.6.49 사용자:
                     * *"빨간점은 역에 하얀점에 넣어줘야지"*). 종전엔 아이콘 모서리에 점을 찍었는데
                     * 아이콘이 선로 위로 올라가면서 "어느 역에 섰는지"가 더 흐려졌다.
                     *
                     * · 판정은 v1.6.47의 [holding]을 **그대로** 쓴다 — 새 상태도, 새 조회도 없다.
                     * · 상·하행이 갈리므로 선로까지 같이 잡는다. 신도림행 선로(아래 띠)의 역이
                     *   빨개져야 "지금 나갈 열차가 왔다"가 된다.
                     * · 0.25칸(≈한 구간의 1/4) 안에 있을 때만 그 역으로 친다 — 종착 회차(pos 3.8)도
                     *   신도림으로 잡히고, 구간 한복판에 멈춘 신호대기는 어느 역도 물들이지 않는다.
                     */
                    val holdingStops = animated.filter { it.third }
                        .mapNotNull { (t, pos, _) ->
                            val i = Math.round(pos)
                            if (i in BranchLine.stations.indices && kotlin.math.abs(pos - i) <= 0.25f)
                                i to (trainY(t.toSindorim, pos) < (railY + bandY) / 2f)
                            else null
                        }.toSet()

                    // 역 점 + 이름 (양천구청 = 승무사업소, 노란 강조 + 차고 아이콘)
                    BranchLine.stations.forEachIndexed { i, name ->
                        val x = xOf(i.toFloat())
                        val depot = name == "양천구청"
                        // 윗선로는 [railStart] 오른쪽에만 있다(까치산 단선) — 없는 선로 위에
                        // 역 동그라미만 뜨면 허공에 점이 뜬다. 두께를 키우니(7 → 10dp) 그 점이
                        // 눈에 띄어 드러났다.
                        if (x >= railStart) drawCircle(Color.White.copy(alpha = 0.55f), stR, Offset(x, railY))
                        drawCircle(Color.White, stR, Offset(x, bandY))
                        // 흰 동그라미를 통째로 빨갛게 칠하지 않고 **속점**을 얹는다 — 역이라는 표시
                        // (흰 테)는 남기면서 빨강이 뜬다. 양천구청은 노란 링(반지름 10.5dp) 안쪽이라
                        // 빨강·노랑이 같은 원을 다투지 않는다(확대 실측).
                        if (i to true in holdingStops) drawCircle(StopRed, stopR, Offset(x, railY))
                        if (i to false in holdingStops) drawCircle(StopRed, stopR, Offset(x, bandY))
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

                    animated.forEach { (t, pos, holding) ->
                        val x = xOf(pos)
                        // 까치산 방면(상단 선로)은 선로 톤에 맞춰 살짝 흐리게
                        val alpha = (if (t.toSindorim) 1f else 0.78f) *
                            if (holding) 1f else shimmer      // 정차·회차 = 고정 / 주행 = 셔머
                        // 아이콘 y = 선로 중심 그대로(v1.6.70에서 상하 `bob`을 뺐다 — 위 주석)
                        val y = trainY(t.toSindorim, pos)
                        // 아이콘은 선로 **위에 얹혀** 그려진다 — 넘기는 y는 선로 중심,
                        // 돌려받는 값은 그려진 아이콘의 윗변이다(`↻ 회차`를 그 위에 붙이려고).
                        val iconTop = drawTrainIcon(
                            tm, t.trainNo, x, y, trackH, t.toSindorim, alpha, BandGreen)

                        // v1.6.55: 여기 있던 `신도림행`·`까치산행` 라벨을 뺐다(사용자: *"신도림행
                        // 텍스트는 없는게 깔끔한거같애"*). 방향은 **진행 화살표(`>`·`<`)와 위/아래
                        // 선로 자리**가 이미 그리고 있었다 — 같은 말이 두 번이었다.
                        // (v1.6.50이 카드 구석의 `← 까치산 방면` 알약을 뺀 것과 같은 정리다.)

                        // 회차 배지는 종착역(까치산·신도림)에서만 — 위치 기반 절대 규칙.
                        // **정보가 다르므로 남긴다** — 방향이 아니라 "지금 되돌아서는 중"이다.
                        if (t.statusText.contains("회차") && (pos <= 0.2f || pos >= 3.8f)) {
                            // 10 → 8.5sp(v1.6.50 사용자: *"회차 동그라미 조금더 줄여줘"*).
                            // `↻` 글리프가 곧 그 동그라미라 글자 크기가 그대로 원 지름이다.
                            val tag = tm.measure("↻ 회차", TextStyle(
                                fontSize = 8.5.sp, fontWeight = FontWeight.ExtraBold, color = DidYellow))
                            // 라벨이 빠지면서 기준이 `lblY`(라벨 윗변)에서 **아이콘 윗변**으로 옮겨졌다.
                            drawText(tag, topLeft = Offset(
                                (x - tag.size.width / 2f)
                                    .coerceIn(4.dp.toPx(), size.width - tag.size.width - 4.dp.toPx()),
                                iconTop - tag.size.height - 1.dp.toPx()))
                        }
                    }

                    // 입고 회송: 신도림 도착 → 윗선로로 도림천 거쳐 기지 진입 (주황)
                    runnerAnimated.forEach { (no, pos) ->
                        drawTrainIcon(tm, no, xOf(pos), railY, trackH, false, 1f, InbOrange)
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
                            // 6 → 3dp(v1.6.49) 그대로. 방면 라벨이 빠진 v1.6.55에선 아래에서
                            // 올라오는 것이 `↻ 회차` 배지뿐이라 여유가 오히려 늘었다.
                            drawRoundRect(Color.White.copy(alpha = 0.10f),
                                topLeft = Offset(tx - 7.dp.toPx(), 3.dp.toPx()),
                                size = Size(l.size.width + 14.dp.toPx(), l.size.height + 8.dp.toPx()),
                                cornerRadius = CornerRadius(50f))
                            drawText(l, topLeft = Offset(tx, 7.dp.toPx()))
                        }

                    // 표시할 열차가 없을 때: 이유를 알려주는 빈 상태 안내.
                    // ⚠ **실패를 반드시 말로 한다**(v1.6.46). 종전엔 [error]를 화면이 안 읽어서
                    // 비행기 모드·서버 오류·API 한도 소진 어느 쪽이든 `"실시간 조회 중…"` 에 영원히
                    // 머물렀다 — 사용자는 앱이 고장인지 알 수 없었다. 새로고침(↻)은 카드 우상단에 있다.
                    if (animated.isEmpty() && runnerAnimated.isEmpty()) {
                        val msg = error
                            ?: if (inService()) "실시간 조회 중…" else "금일 운행 종료 (영업 05:30~)"
                        val failed = error != null
                        val empty = tm.measure(msg, TextStyle(fontSize = 11.sp,
                            color = if (failed) Color(0xFFE9A23B) else Color(0xFF9AA39C)))
                        val hint = if (!failed) null else tm.measure("오른쪽 위 ↻ 를 눌러 다시 시도",
                            TextStyle(fontSize = 10.sp, color = Color(0xFF9AA39C)))
                        // 두 줄일 때도 **묶음 전체**를 선로 사이 가운데에 둔다 — 첫 줄만 가운데에 두면
                        // 둘째 줄이 아래 초록 띠에 겹쳐 안 읽힌다(실측).
                        val gap = 3.dp.toPx()
                        val blockH = empty.size.height + (hint?.let { gap + it.size.height } ?: 0f)
                        var ty = (railY + bandY) / 2 - blockH / 2
                        drawText(empty, topLeft = Offset(size.width / 2 - empty.size.width / 2, ty))
                        hint?.let {
                            ty += empty.size.height + gap
                            drawText(it, topLeft = Offset(size.width / 2 - it.size.width / 2, ty))
                        }
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
                    .padding(top = 3.dp, end = 6.dp)
                    .clickable { refreshTick++; onRefresh() },
            ) {
                // 직접 그린 새로고침 글리프 (원호 + 화살촉) — 아이콘 라이브러리 의존성 없음.
                // 36 → 30 → **26dp**(v1.6.50 사용자: *"회차 동그라미 조금더 줄여줘"* — 화면의
                // 동그라미 둘을 같이 줄인다). **24dp 밑으로는 안 내린다** — 손가락으로 누르는
                // 단추라 그 아래는 표준 최소 터치 타깃(48dp의 절반)마저 깨진다.
                Canvas(Modifier.padding(6.dp).size(14.dp).rotate(spin)) {
                    val c = Color(0xFFB9F5C0)
                    drawArc(c, startAngle = -50f, sweepAngle = 290f, useCenter = false,
                        style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round))
                    val r = size.minDimension / 2f
                    val ang = Math.toRadians(-50.0)
                    val ax = size.width / 2f + (r * kotlin.math.cos(ang)).toFloat()
                    val ay = size.height / 2f + (r * kotlin.math.sin(ang)).toFloat()
                    val ah = 4.5.dp.toPx()
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

/**
 * 아이콘 **차체** 배율 (v1.6.47 ① → v1.6.49에서 한 번 더:
 * *"열차 아이콘을 조금더 줄여주고 선로위에 있게해줘"*).
 *
 * **부품 치수가 전부 dp 상수라 몇 개만 줄이면 실루엣 비율이 깨진다**(굴뚝 높이는 그대로인데
 * 보일러만 작아지는 식). 그래서 [drawTrainIcon] 안의 모든 dp를 이 하나로 곱한다 —
 * 실루엣은 v1.6.44 그대로고 크기만 준다. 0.85 → 0.72 → **0.60**(차체 20.4 → 17.3 → 14.4dp).
 *
 * ⚠ **열번만 이 배율에서 뺐다**([NO_SP]). 4자리 열번은 승무원이 자기 열차를 가려내는 정보라
 * 배율에 딸려 가면(12 × 0.60 = 7.2sp) 못 읽는다. 알약은 열번이 안 잘리게 [drawTrainIcon]에서
 * 열번 크기를 폭·높이의 하한으로 잡는다 — **0.69 아래로는 알약이 열번에 매여** 더 안 준다
 * (0.60에서 차체폭 = 열번 22.3 + 여백 8.4 = 30.7dp > s(44) = 26.4dp).
 */
private const val ICON_S = 0.60f

/**
 * 열번 글자 크기 — 배율과 무관한 **가독성 하한**. 역명 11.5sp > 이것 > `↻ 회차` 8.5sp.
 * 10 → **9sp**(v1.6.50). 방면 라벨(7.5sp)이 v1.6.55에서 없어졌지만 **여기는 안 건드린다** —
 * 승무원이 자기 열차를 가려내는 정보라 4자리 판독이 걸린 값이다.
 */
private const val NO_SP = 9f

/**
 * 열차 아이콘. [faceRight]=true는 **신도림행**(까치산→양천구청→신도림)이라
 * 증기기관차 실루엣으로, false는 까치산행·입고 회송이라 종전 삼각 노즈로 그린다.
 * 승무원이 양천구청에서 잡아 타는 건 신도림행뿐이라 그 방향만 한눈에 갈려야 한다.
 * **열번 알약은 양쪽 모두 그대로** — 열차를 식별하는 정보라 실루엣이 대체할 수 없다.
 *
 * [trackY]는 **선로 중심**이고 [trackH]는 그 두께다 — 아이콘은 그 위에 얹혀,
 * 동륜이 선로 윗면에 닿은 채로 달린다(v1.6.49). 종전엔 선로 한가운데 겹쳐 그려서
 * 띠·진행 화살표·역 동그라미가 통째로 가렸다(사용자 지적).
 * 정차 빨간 점은 여기서 빠졌다 — **역 동그라미**가 대신 물든다(`holdingStops`).
 *
 * ⚠ v1.6.55: 동륜은 v1.6.49부터 이미 레일 윗면에 **수학적으로 닿아** 있었지만
 * 눈에는 떠 보였다 — 1)의 어두운 후광이 차체 밑으로 `s(3)`까지 내려와 있고 동륜은
 * `s(4)`까지밖에 안 나와, **후광 밖으로 나온 바퀴가 0.6dp(≈1.6px)** 뿐이었다.
 * 사용자가 본 건 "레일 위에 뜬 검은 상자"다. 그래서 ⓐ 후광을 차체 밑변에서 끊고
 * ⓑ 동륜을 반지름만큼 내밀어(차체 밑변 = 동륜 중심) **바퀴 아래 절반이 통째로 보이게**,
 * ⓒ 접지면을 레일에 `s(1)`만큼 물렸다. 차체는 여전히 선로 띠 위로 완전히 떠 있다.
 *
 * @return 그려진 아이콘의 **윗변** y. `↻ 회차` 배지를 그 위에 붙이는 데 쓴다.
 */
private fun DrawScope.drawTrainIcon(
    tm: TextMeasurer, no: String, cxRaw: Float, trackY: Float, trackH: Float,
    faceRight: Boolean, alpha: Float, body: Color,
): Float {
    // 아이콘 안의 모든 치수는 이걸 거친다 — 한 배율로만 줄여야 실루엣이 안 뭉개진다([ICON_S])
    fun s(v: Float) = (v * ICON_S).dp.toPx()
    val label = tm.measure(no, TextStyle(
        fontSize = NO_SP.sp, fontWeight = FontWeight.ExtraBold, color = Color.White))
    val bodyW = maxOf(s(44f), label.size.width + s(14f))
    // 열번이 배율에서 빠졌으므로 **높이도 열번을 하한으로** 잡는다 — 안 그러면 글자배율을 키운
    // 사용자(지도 안 상한 1.2배)에서 열번 위아래가 알약을 넘는다.
    val bodyH = maxOf(s(24f), label.size.height + s(6f))
    // 동륜: 중심을 **차체 밑변에** 두어 아래 절반이 통째로 드러난다(종전엔 s(4)만 내밀어
    // 후광에 거의 다 먹혔다). 그래서 차체 밑변 = 레일 윗면 − 동륜 반지름이 접지 조건이고,
    // 여기에 s(1)만 물려 "닿았다"가 확실히 읽히게 한다.
    val wr = s(4.5f)
    val cy = trackY - trackH / 2 - bodyH / 2 - wr + s(1f)
    // 증기기관차 쪽은 보일러·배장기가 들어갈 앞머리가 조금 더 길다. totalW에 그대로 반영되므로
    // 아래 clamp가 늘어난 폭까지 알아서 막아 준다(종착역 잘림 회귀 방지).
    val noseW = s(if (faceRight) 19f else 11f)
    val totalW = bodyW + noseW
    // 종착역은 `xOf`가 캔버스 여백(20dp)까지만 물러나 있어서 아이콘 반폭(~27dp)이
    // 그대로 넘친다 — 신도림에 선 열차가 카드 밖으로 잘려 나갔다(실기기 확인).
    // 원본은 방면 라벨·회차 배지만 clamp 하고 아이콘은 빠뜨렸다. 같은 규칙을 아이콘에도 준다.
    // **여백 3dp는 후광·흰 윤곽 몫**이다 — v1.6.43은 반폭까지만 물려서 종착역에서 윤곽선
    // 바깥쪽 절반과 후광이 카드 경계에 딱 잘렸다(증기기관차는 앞머리가 길어 더 눈에 띈다).
    // ⚠ 여기만 [ICON_S]를 안 곱한다 — 후광이 2.55dp로 줄어든 만큼 남는 여유가 5)의 빨간 점 몫이다.
    val edge = totalW / 2 + 3.dp.toPx()
    val cx = cxRaw.coerceIn(edge, (size.width - edge).coerceAtLeast(edge))
    val left = if (faceRight) cx - totalW / 2 else cx - totalW / 2 + noseW
    val right = left + bodyW
    val top = cy - bodyH / 2

    // 1) 어두운 후광 (노즈 포함 전체 영역 덮음)
    // 아래로는 **차체 밑변에서 끊는다**(종전 `bodyH + s(6)` → `bodyH + s(3)`, v1.6.55).
    // 후광이 차체보다 아래로 내려오면 그 밑에서 굴러야 할 동륜을 도로 삼켜, 아이콘이
    // 레일 위에 뜬 검은 상자로 보였다. 위쪽 s(3)은 그대로 — 굴뚝·연기 뒤를 받쳐 준다.
    drawRoundRect(Color(0xFF0E0E0E).copy(alpha = 0.9f * alpha),
        topLeft = Offset((if (faceRight) left else left - noseW) - s(3f), top - s(3f)),
        size = Size(totalW + s(6f), bodyH + s(3f)),
        cornerRadius = CornerRadius(s(13f)))

    // 2) 앞머리 — 신도림행만 증기기관차 느낌(보일러·굴뚝·연기·배장기),
    //    나머지는 종전 삼각 노즈. 어느 쪽이든 3)의 알약이 나중에 덮으므로 뒷부분은 매끈하게 이어진다.
    val outline = Stroke(width = s(2.2f), cap = StrokeCap.Round)
    if (faceRight) {
        val boilerH = bodyH * 0.72f
        val boilerFront = right + noseW - s(2f)
        val stackX = right + noseW * 0.42f
        val stackTop = cy - s(19f)

        // 연기: 굴뚝 위로 커지며 흐려지는 동그라미 3개. 방면 라벨과 세로로 겹칠 수 있어
        // 알파를 낮게 잡았다 — 라벨이 나중에 그려져 위에 얹힌다(글자 가림 없음).
        repeat(3) { i ->
            drawCircle(Color.White.copy(alpha = (0.42f - i * 0.12f) * alpha),
                radius = s(2.6f + i * 1.3f),
                center = Offset(stackX - s(i * 1.8f), stackTop - s(2f + i * 4.2f)))
        }
        // 배장기: 보일러 앞 아래로 비스듬히 내려오는 쐐기
        val pilot = Path().apply {
            moveTo(right + s(5f), cy + s(2f))
            lineTo(boilerFront + s(2f), cy + bodyH * 0.52f)
            lineTo(right + s(5f), cy + bodyH * 0.52f)
            close()
        }
        drawPath(pilot, body.copy(alpha = alpha))
        drawPath(pilot, Color.White.copy(alpha = alpha), style = Stroke(width = s(1.8f)))
        // 굴뚝: 위로 벌어지고 갓이 튀어나온 실루엣을 **한 path**로. 갓을 따로 그리면 이 크기에선
        // 흰 윤곽끼리 겹쳐 뭉개진다. 보일러보다 먼저 그려 밑동을 보일러가 덮게 한다.
        val stack = Path().apply {
            moveTo(stackX - s(3.0f), cy)
            lineTo(stackX - s(3.6f), stackTop + s(3f))
            lineTo(stackX - s(5.4f), stackTop + s(3f))
            lineTo(stackX - s(5.4f), stackTop)
            lineTo(stackX + s(5.4f), stackTop)
            lineTo(stackX + s(5.4f), stackTop + s(3f))
            lineTo(stackX + s(3.6f), stackTop + s(3f))
            lineTo(stackX + s(3.0f), cy)
            close()
        }
        drawPath(stack, body.copy(alpha = alpha))
        drawPath(stack, Color.White.copy(alpha = alpha), style = Stroke(width = s(1.6f)))
        // 보일러: 앞이 둥근 원통 + 스모크박스 문
        drawRoundRect(body.copy(alpha = alpha),
            topLeft = Offset(right - s(6f), cy - boilerH / 2),
            size = Size(boilerFront - right + s(6f), boilerH),
            cornerRadius = CornerRadius(boilerH / 2))
        drawRoundRect(Color.White.copy(alpha = alpha),
            topLeft = Offset(right - s(6f), cy - boilerH / 2),
            size = Size(boilerFront - right + s(6f), boilerH),
            cornerRadius = CornerRadius(boilerH / 2), style = outline)
        drawCircle(Color.White.copy(alpha = 0.85f * alpha), boilerH * 0.24f,
            Offset(boilerFront - boilerH * 0.46f, cy), style = Stroke(width = s(1.4f)))
    } else {
        val nose = Path().apply {
            moveTo(left + s(1f), cy - bodyH * 0.36f)
            lineTo(left - noseW, cy)
            lineTo(left + s(1f), cy + bodyH * 0.36f)
            close()
        }
        drawPath(nose, body.copy(alpha = alpha))
        drawPath(nose, Color.White.copy(alpha = alpha), style = outline)
    }

    // 2-b) 동륜: 중심이 차체 밑변이라 **아래 절반이 레일에 얹혀** 보인다(윗절반은 3)이 덮는다).
    // **양방향 아이콘 모두** 굴린다 — 까치산행(삼각 노즈)만 바퀴가 없으면 그쪽 열차는
    // 계속 떠 보인다. 증기기관차 쪽에만 있던 블록을 조건 밖으로 끌어냈다(v1.6.55).
    val wy = cy + bodyH / 2
    val wxs = listOf(0.24f, 0.55f, 0.86f).map { left + bodyW * it }
    wxs.forEach { wx ->
        drawCircle(Color(0xFF13170F).copy(alpha = alpha), wr, Offset(wx, wy))
        drawCircle(Color.White.copy(alpha = 0.9f * alpha), wr, Offset(wx, wy),
            style = Stroke(width = s(1.8f)))
    }
    // 연결봉은 증기기관차 쪽만 — 현대 전동차 밑에 로드가 달리면 오히려 어색하다
    if (faceRight) drawLine(Color.White.copy(alpha = 0.75f * alpha),
        Offset(wxs.first(), wy + wr * 0.55f), Offset(wxs.last(), wy + wr * 0.55f),
        strokeWidth = s(1.6f), cap = StrokeCap.Round)

    // 3) 차체: 둥근 사각 (노즈 밑변을 덮어 매끈하게 연결)
    drawRoundRect(body.copy(alpha = alpha), Offset(left, top), Size(bodyW, bodyH),
        CornerRadius(s(9f)))
    drawRoundRect(Color.White.copy(alpha = alpha), Offset(left, top), Size(bodyW, bodyH),
        CornerRadius(s(9f)), style = Stroke(width = s(2.2f)))

    // 4) 열번
    drawText(label, topLeft = Offset(left + bodyW / 2 - label.size.width / 2f,
        cy - label.size.height / 2f), alpha = alpha)

    // 윗변 = 후광 위 가장자리. 증기기관차는 굴뚝(`stackTop = cy - s(19)`)이 더 솟아 있어 그만큼 위다
    // (연기는 반투명 장식이라 라벨이 살짝 겹쳐도 글자가 이긴다 — 종전과 같은 취급).
    val haloTop = top - s(3f)
    return if (faceRight) minOf(haloTop, cy - s(19f)) else haloTop
}

