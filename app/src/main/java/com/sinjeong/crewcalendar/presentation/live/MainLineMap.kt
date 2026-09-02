package com.sinjeong.crewcalendar.presentation.live

import android.util.Log
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sinjeong.crewcalendar.R
import com.sinjeong.crewcalendar.domain.model.DutyCode
import com.sinjeong.crewcalendar.domain.model.Line2Stations
import com.sinjeong.crewcalendar.domain.model.MyTrain
import com.sinjeong.crewcalendar.domain.model.myTrainAt
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/*
 * 2호선 **본선 순환선** 실시간 지도 (v1.6.84) — 지선 지도([BranchLiveMap]) 위의
 * `전체 보기` 단추로 연다. 사용자가 말한 *"편승 에뮬레이터에 본선"* 이 이 화면이다.
 *
 * 답해야 하는 질문은 하나다: **"내 열번이 지금 어디쯤인지."**
 * 그래서 43역 전부를 고르게 그리되 **내 열차 하나만** 크게 · 라벨 · 펄스로 띄운다.
 *
 * ## 데이터는 지선 지도와 **같은 스냅샷**이다
 *
 * [BranchLive.loadSnapshot] 을 그대로 부른다 — 같은 캐시 · 같은 주기(10초/4초) ·
 * 같은 키 로테이션이라 **API 호출이 늘지 않는다**. 위치 API 는 원래부터 2호선 전체를
 * 받아 지선만 걸러 쓰고 있었고, 이제 나머지를 [Snapshot.mainTrains] 로 같이 쓸 뿐이다.
 *
 * ## 보간을 하지 않는 이유
 *
 * 지선 지도는 구간별 실측 주행시간([BranchLine.SEG_UP])이 있어서 등속 보간이 가능했다.
 * 본선 43역에는 그런 실측이 없다 — 구간마다 주행시간이 다른데 그걸 지어내면 화면 속도가
 * 실차와 어긋난다. 그래서 **역 단위 위치만** 쓰고, 값이 바뀔 때 [animateFloatAsState] 로
 * 부드럽게 옮기는 것까지만 한다. 필요해지면 구간 실측표를 넣고 여기만 손보면 된다.
 *
 * ## 생명주기
 *
 * 폴링·시계·애니메이션이 전부 이 컴포저블의 컴포지션에 묶여 있어 **다이얼로그를 닫으면
 * 함께 취소된다** — [BranchLiveMap] 과 같은 처방이고, 이유(2초 절대시각 눈금)도 그쪽
 * KDoc 에 적혀 있다. 여기서 다시 적지 않는다.
 */

/** 내선(시계) — 지선 지도의 초록과 같은 계열. */
private val InnerGreen = Color(0xFF3EC42E)
/** 외선(반시계) — 초록과 확실히 갈리는 파랑. */
private val OuterBlue = Color(0xFF4FA8FF)
private val MineYellow = Color(0xFFF4E625)
private const val TAG = "BranchLive"

/**
 * 전체화면 순환선 지도.
 *
 * @param duty 오늘 근무 — 내 열번 판정에 쓴다. 본선 근무가 아니면 내 열차는 없다.
 * @param date 오늘 날짜(야간 후반 익일 판정에 필요)
 */
@Composable
internal fun MainLineMapDialog(duty: DutyCode?, date: LocalDate, onDismiss: () -> Unit) {
    var snap by remember { mutableStateOf(Snapshot()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var picked by remember { mutableStateOf<String?>(null) }

    val bars = WindowInsets.systemBars.asPaddingValues()
    val overshoot = bars.calculateTopPadding() + bars.calculateBottomPadding()

    DisposableEffect(Unit) {
        Log.i(TAG, "본선 지도 열림 — 폴링 시작")
        onDispose { Log.i(TAG, "본선 지도 닫힘 — 폴링·애니메이션 취소") }
    }
    // 눈금 규칙(2초 · 절대시각)은 [BranchLiveMap] 과 같다 — 왜 그런지는 그쪽 KDoc.
    LaunchedEffect(Unit) {
        var nextTick = System.currentTimeMillis()
        while (isActive) {
            snap = BranchLive.loadSnapshot()
            nextTick += 2_000
            val nowMs = System.currentTimeMillis()
            if (nextTick < nowMs) nextTick = nowMs + 2_000
            delay(nextTick - nowMs)
        }
    }
    LaunchedEffect(Unit) { while (isActive) { now = System.currentTimeMillis(); delay(1_000) } }

    // 내 열번은 1초 시계에 맞춰 다시 본다 — 사업 시작 시각을 넘기는 순간 문구가 바뀐다.
    val mine = remember(duty, date, now / 60_000) {
        duty?.let { myTrainAt(it, date, LocalDateTime.now()) }
    }
    val shown = snap.mainTrains
    // 후보 열번 중 **실제로 API 에 살아 있는** 첫 번째가 내 열차다(추정하지 않는다 — MyTrain KDoc)
    val mineNo = remember(mine, shown) {
        mine?.nos?.firstOrNull { no -> shown.any { it.trainNo == no } }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF101010)) {
            Column(Modifier.fillMaxSize().padding(bottom = overshoot)) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 18.dp, top = 10.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "2호선 본선",
                        fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(
                        "열차 " + shown.size + "대",
                        fontSize = 12.sp, color = Color(0xFF9AA39C), modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close, "닫기",
                            tint = Color.White,
                        )
                    }
                }

                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                    // 폴드 펼침에서는 원이 커지고 **43역 라벨을 전부** 그린다.
                    val wide = maxWidth >= 600.dp
                    val d = LocalDensity.current
                    // 지도 안 글자배율 상한 — 지선 지도와 같은 처방(그림은 dp, 글자만 sp라
                    // 배율을 키우면 라벨이 원 밖으로 나간다). ⚠ TextMeasurer 는 반드시 이 안에서.
                    CompositionLocalProvider(
                        LocalDensity provides Density(d.density, d.fontScale.coerceAtMost(1.2f))
                    ) {
                        val tm = rememberTextMeasurer()
                        val infinite = rememberInfiniteTransition(label = "loop")
                        val pulse by infinite.animateFloat(
                            0f, 1f,
                            infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
                            label = "pulse",
                        )
                        // 열차마다 각도를 부드럽게 옮긴다(등속 보간은 안 한다 — 파일 KDoc)
                        val placed = shown.map { t ->
                            key(t.trainNo) {
                                val p by animateFloatAsState(
                                    t.stationIdx + t.offset,
                                    tween(1500, easing = LinearEasing), label = "m" + t.trainNo,
                                )
                                t to p
                            }
                        }
                        var hit by remember { mutableStateOf<List<Pair<Offset, String>>>(emptyList()) }
                        Canvas(
                            Modifier.fillMaxSize().padding(8.dp).pointerInput(hit) {
                                detectTapGestures { tap ->
                                    val near = hit.minByOrNull { hypot(
                                        (it.first.x - tap.x).toDouble(), (it.first.y - tap.y).toDouble()) }
                                    picked = near?.takeIf {
                                        hypot((it.first.x - tap.x).toDouble(),
                                            (it.first.y - tap.y).toDouble()) < 24.dp.toPx()
                                    }?.second
                                }
                            }
                        ) {
                            hit = drawLoop(tm, placed, mineNo, pulse, wide, picked)
                        }
                    }
                }

                MyTrainFooter(duty, mine, mineNo, snap.error, shown.isNotEmpty())
            }
        }
    }
}

/**
 * 순환선 한 장을 그리고 **열차 탭 판정용 좌표**를 돌려준다.
 *
 * 시청(index 0)을 12시에 두고 **내선 = 시계 방향**으로 43역을 등간격 배치한다.
 * 궤도는 둘 — 바깥 원이 외선(반시계), 안쪽 원이 내선(시계). 진행 화살촉이 방향을 말한다.
 */
private fun DrawScope.drawLoop(
    tm: TextMeasurer,
    trains: List<Pair<MainTrainMark, Float>>,
    mineNo: String?,
    pulse: Float,
    wide: Boolean,
    picked: String?,
): List<Pair<Offset, String>> {
    val n = Line2Stations.MAIN.size                       // 43
    val c = Offset(size.width / 2f, size.height / 2f)
    // 반지름은 **가장 긴 역 이름이 들어갈 만큼** 물러나 있어야 한다 — 좌우 끝 라벨은
    // 원 바깥으로 나가는데, 여유가 모자라면 화면 경계에 눌려 선로 위로 올라온다
    // (실측: 46dp 여유에서 `구로디지털단지` 끝 글자가 잘렸다).
    val r = (minOf(size.width, size.height) / 2f) - (if (wide) 78.dp else 64.dp).toPx()
    val rInner = r - 13.dp.toPx()
    fun ang(pos: Float) = (pos / n) * 2f * PI.toFloat() - PI.toFloat() / 2f   // 12시 시작
    fun at(pos: Float, radius: Float) =
        Offset(c.x + radius * cos(ang(pos)), c.y + radius * sin(ang(pos)))

    // ── 궤도 두 줄 ────────────────────────────────────────────
    drawCircle(OuterBlue.copy(alpha = 0.30f), r, c, style = Stroke(width = 7.dp.toPx()))
    drawCircle(InnerGreen.copy(alpha = 0.45f), rInner, c, style = Stroke(width = 7.dp.toPx()))
    // 진행 화살촉 — 바깥은 반시계, 안쪽은 시계
    for (i in 0 until n step 4) {
        drawArrow(at(i + 0.5f, r), ang(i + 0.5f), OuterBlue.copy(alpha = 0.8f), false, 4.dp.toPx())
        drawArrow(at(i + 2.5f, rInner), ang(i + 2.5f), InnerGreen.copy(alpha = 0.9f), true, 4.dp.toPx())
    }

    // ── 지선 두 갈래는 **표시만** ────────────────────────────
    drawBranch(tm, c, r, ::ang, Line2Stations.MAIN.indexOf("성수"), Line2Stations.SEONGSU_BRANCH, wide)
    drawBranch(tm, c, r, ::ang, Line2Stations.MAIN.indexOf("신도림"), Line2Stations.SINJEONG_BRANCH, wide)

    // ── 역 43개 ──────────────────────────────────────────────
    val mineIdx = trains.firstOrNull { it.first.trainNo == mineNo }?.first?.stationIdx
    Line2Stations.MAIN.forEachIndexed { i, name ->
        val p = at(i.toFloat(), (r + rInner) / 2f)
        val key = wide || name in KEY_STATIONS || i == mineIdx
        drawCircle(if (key) Color.White else Color.White.copy(alpha = 0.5f),
            (if (key) 3.5f else 2.5f).dp.toPx(), p)
        if (!key) return@forEachIndexed
        val lab = tm.measure(name, TextStyle(
            fontSize = if (wide) 10.sp else 9.5.sp,
            fontWeight = if (i == mineIdx) FontWeight.ExtraBold else FontWeight.Bold,
            color = if (i == mineIdx) MineYellow else Color.White))
        // 라벨은 원 **바깥**에 두되, **원의 어느 쪽이냐로 정렬을 바꾼다** — 전부 가운데
        // 정렬하면 좌우 끝(성수·신도림)에서 글자가 역 동그라미를 덮는다(실측: `건대입구`가
        // `대입구`로 잘려 보였다). 오른쪽 반원은 점 오른쪽에, 왼쪽 반원은 점 왼쪽에 붙인다.
        // 원 꼭대기·바닥에서는 이웃 역이 가로로 바짝 붙어 라벨이 겹친다(폴드 펼침 실측:
        // `시청`·`을지로입구`가 붙어 버렸다). 그 구간만 한 칸씩 **번갈아 밖으로** 밀어 놓는다.
        val topBand = abs(at(i.toFloat(), r).x - c.x) < r * 0.35f
        val lp = at(i.toFloat(), r + 8.dp.toPx() + if (topBand && i % 2 == 1) 13.dp.toPx() else 0f)
        val side = lp.x - c.x
        val lx = when {
            side > r * 0.35f -> lp.x + 4.dp.toPx()                      // 오른쪽 반원
            side < -r * 0.35f -> lp.x - lab.size.width - 4.dp.toPx()    // 왼쪽 반원
            else -> lp.x - lab.size.width / 2f                          // 위·아래 꼭대기
        }
        drawText(lab, topLeft = Offset(
            lx.coerceIn(0f, (size.width - lab.size.width).coerceAtLeast(0f)),
            (lp.y - lab.size.height / 2f).coerceIn(0f, (size.height - lab.size.height).coerceAtLeast(0f)),
        ))
    }

    // ── 열차 ─────────────────────────────────────────────────
    val hits = mutableListOf<Pair<Offset, String>>()
    trains.forEach { (t, pos) ->
        val radius = if (t.inner) rInner else r
        val p = at(pos, radius)
        hits += p to t.trainNo
        val mine = t.trainNo == mineNo
        val color = if (mine) MineYellow else if (t.inner) InnerGreen else OuterBlue
        if (mine) {
            // 펄스: 반투명 원이 커지며 사라진다
            drawCircle(MineYellow.copy(alpha = (1f - pulse) * 0.45f),
                (5f + pulse * 16f).dp.toPx(), p)
            drawCircle(Color(0xFF101010), 9.dp.toPx(), p)
            drawCircle(color, 8.dp.toPx(), p)
            drawCircle(Color.White, 8.dp.toPx(), p, style = Stroke(width = 2.dp.toPx()))
            val lab = tm.measure("내 열차 " + t.trainNo, TextStyle(
                fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = MineYellow))
            // 라벨은 **원 안쪽**으로 — 원 바깥은 역 이름 자리라 겹친다(실측: `내 열차 2039`가
            // 교대·강남 라벨 위에 얹혔다). 원 한가운데는 늘 비어 있어 부딪힐 것이 없다.
            val inward = Offset(c.x - p.x, c.y - p.y).let { v ->
                val len = hypot(v.x.toDouble(), v.y.toDouble()).toFloat().coerceAtLeast(1f)
                Offset(v.x / len, v.y / len)
            }
            val anchor = Offset(p.x + inward.x * 26.dp.toPx(), p.y + inward.y * 26.dp.toPx())
            val lx = (anchor.x - lab.size.width / 2f)
                .coerceIn(0f, (size.width - lab.size.width).coerceAtLeast(0f))
            val ly = (anchor.y - lab.size.height / 2f)
                .coerceIn(0f, (size.height - lab.size.height).coerceAtLeast(0f))
            drawRoundRect(Color.Black.copy(alpha = 0.72f),
                topLeft = Offset(lx - 5.dp.toPx(), ly - 3.dp.toPx()),
                size = Size(lab.size.width + 10.dp.toPx(), lab.size.height + 6.dp.toPx()),
                cornerRadius = CornerRadius(50f))
            drawText(lab, topLeft = Offset(lx, ly))
        } else {
            drawCircle(Color(0xFF101010), 5.dp.toPx(), p)
            drawCircle(color, 4.dp.toPx(), p)
        }
    }

    // ── 탭한 열차의 툴팁 (열차보다 나중에 그려 위에 얹힌다) ──
    picked?.let { no ->
        trains.firstOrNull { it.first.trainNo == no }?.let { (t, pos) ->
            val p = at(pos, if (t.inner) rInner else r)
            val line1 = t.trainNo + "  " + (if (t.inner) "내선" else "외선")
            val line2 = t.statusText + " · " + t.destName + "행"
            val l1 = tm.measure(line1, TextStyle(fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold, color = Color.White))
            val l2 = tm.measure(line2, TextStyle(fontSize = 11.sp, color = Color(0xFFCFD8D2)))
            val w = maxOf(l1.size.width, l2.size.width) + 20.dp.toPx()
            val h = l1.size.height + l2.size.height + 14.dp.toPx()
            val x = (p.x - w / 2f).coerceIn(0f, (size.width - w).coerceAtLeast(0f))
            val y = (p.y - h - 14.dp.toPx()).coerceAtLeast(0f)
            drawRoundRect(Color(0xFF1E1E1E), topLeft = Offset(x, y), size = Size(w, h),
                cornerRadius = CornerRadius(10.dp.toPx()))
            drawRoundRect(Color.White.copy(alpha = 0.25f), topLeft = Offset(x, y), size = Size(w, h),
                cornerRadius = CornerRadius(10.dp.toPx()),
                style = Stroke(width = 1.dp.toPx()))
            drawText(l1, topLeft = Offset(x + 10.dp.toPx(), y + 6.dp.toPx()))
            drawText(l2, topLeft = Offset(x + 10.dp.toPx(), y + 6.dp.toPx() + l1.size.height))
        }
    }
    return hits
}

/** 지선 한 갈래 — 본선 역에서 바깥으로 뻗는 짧은 가지(점 + 선). **열차는 안 그린다.** */
private fun DrawScope.drawBranch(
    tm: TextMeasurer, c: Offset, r: Float, ang: (Float) -> Float,
    fromIdx: Int, stations: List<String>, wide: Boolean,
) {
    if (fromIdx < 0) return
    val a = ang(fromIdx.toFloat())
    val step = 11.dp.toPx()
    fun at(k: Int) = Offset(c.x + (r + step * k) * cos(a), c.y + (r + step * k) * sin(a))
    val gray = Color.White.copy(alpha = 0.28f)
    drawLine(gray, at(0), at(stations.size - 1), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
    for (k in 1 until stations.size) drawCircle(gray, 2.2.dp.toPx(), at(k))
    if (!wide) return
    // 펼침에서만 가지 끝 이름을 적는다 — 접힘에서는 본선 라벨과 겹친다
    val lab = tm.measure(stations.last(), TextStyle(fontSize = 9.sp, color = Color(0xFF9AA39C)))
    val p = at(stations.size - 1)
    drawText(lab, topLeft = Offset(
        (p.x - lab.size.width / 2f).coerceIn(0f, (size.width - lab.size.width).coerceAtLeast(0f)),
        (p.y + 4.dp.toPx()).coerceAtMost(size.height - lab.size.height)))
}

/** 궤도 위 진행 방향 화살촉. [clockwise] = 각도가 커지는 쪽. */
private fun DrawScope.drawArrow(p: Offset, a: Float, color: Color, clockwise: Boolean, h: Float) {
    val t = a + if (clockwise) PI.toFloat() / 2f else -PI.toFloat() / 2f   // 진행 방향(접선)
    val tip = Offset(p.x + h * cos(t), p.y + h * sin(t))
    val l = Offset(p.x + h * cos(t + 2.4f), p.y + h * sin(t + 2.4f))
    val rr = Offset(p.x + h * cos(t - 2.4f), p.y + h * sin(t - 2.4f))
    drawPath(Path().apply { moveTo(l.x, l.y); lineTo(tip.x, tip.y); lineTo(rr.x, rr.y) },
        color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
}

/**
 * 좁은 화면에서 이름을 적는 역. 43개를 다 적으면 411dp 에서 글자가 겹쳐 뭉갠다 —
 * 갈아타는 역과 눈에 익은 역만 남긴다(내 열차가 있는 역은 좁아도 항상 적는다).
 */
private val KEY_STATIONS = setOf(
    "시청", "왕십리", "성수", "건대입구", "잠실", "삼성", "선릉", "강남", "교대",
    "사당", "신림", "구로디지털단지", "신도림", "당산", "홍대입구", "신촌",
)

/** 화면 아래 한 줄 — "내 열차"가 어떤 상태인지 말로 한다. */
@Composable
private fun MyTrainFooter(
    duty: DutyCode?, mine: MyTrain?, mineNo: String?, error: String?, hasTrains: Boolean,
) {
    val (text, color) = when {
        // ⚠ 오류는 **열차가 하나도 없을 때만** 말한다. 스냅샷의 `error` 는 지선 카드가 쓰는
        // 양천구청 **도착** API 실패까지 합쳐 온 값이라(위치 API 는 멀쩡할 수 있다), 그대로
        // 띄우면 열차 14대를 그려 놓고 "정보를 못 받았어요"라고 말하게 된다(실측).
        error != null && !hasTrains -> error to Color(0xFFE9A23B)
        mineNo != null -> ("내 열차 " + mineNo + " · 노란 점") to MineYellow
        duty != null && duty.isBranch -> "오늘은 지선 근무입니다 — 지선 지도를 보세요" to Color(0xFF9AA39C)
        mine == null -> "오늘은 맡은 열차가 없습니다" to Color(0xFF9AA39C)
        mine.riding -> ("사업 중 · " + mine.nos.joinToString("·") + " 중 아직 안 보입니다") to Color(0xFF9AA39C)
        else -> (
            "출고 전 · " + (if (mine.nextDay) "익일 " else "") + (mine.startAt?.toString() ?: "") +
                " " + (mine.place ?: "") + " 예정 (" + mine.nos.first() + ")"
            ) to Color(0xFFCFD8D2)
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = color,
            modifier = Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        Legend(InnerGreen, "내선"); Spacer(Modifier.width(8.dp)); Legend(OuterBlue, "외선")
    }
}

@Composable
private fun Legend(color: Color, label: String) = Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
) {
    Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(color))
    Text(label, fontSize = 11.sp, color = Color(0xFF9AA39C))
}

/** 지선 지도 카드 우상단의 `전체 보기` 단추 (Lucide `maximize-2`). */
@Composable
internal fun FullMapButton(onClick: () -> Unit) = Surface(
    color = Color.White.copy(alpha = 0.10f),
    shape = RoundedCornerShape(50),
    border = BorderStroke(1.2.dp, Color(0xFF3EC42E).copy(alpha = 0.75f)),
    modifier = Modifier.height(26.dp),
) {
    Row(
        Modifier.clip(RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            painterResource(R.drawable.ic_lucide_maximize_2),
            null, Modifier.size(12.dp), Color(0xFFB9F5C0),
        )
        Text("전체 보기", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB9F5C0))
    }
}
