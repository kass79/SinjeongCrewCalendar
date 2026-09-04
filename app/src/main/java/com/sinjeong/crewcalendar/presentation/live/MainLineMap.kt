package com.sinjeong.crewcalendar.presentation.live

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sinjeong.crewcalendar.R
import com.sinjeong.crewcalendar.domain.model.DutyCode
import com.sinjeong.crewcalendar.domain.model.Line2Stations
import com.sinjeong.crewcalendar.domain.model.Line2Timetable
import com.sinjeong.crewcalendar.domain.model.MyTrain
import com.sinjeong.crewcalendar.domain.model.dutyTrainNumbers
import com.sinjeong.crewcalendar.domain.model.myTrainAt
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin

/*
 * 2호선 **본선 순환선** 실시간 지도 — v1.6.85 에서 **운전실 보조설비 화면**으로 다시 그렸다.
 * 사용자(기관사)가 실제 운전실 화면 사진 두 장을 주며 *"본선은 이 이미지처럼 깔끔하게"* 라고
 * 확정한 그림이다. 아래가 그 사양이고, **추측으로 바꾸지 말 것**:
 *
 *  1. 진한 남색 바탕(#0E2A47) 고정 — 앱 테마(라이트/다크)와 **무관**하다.
 *  2. **가로 화면 고정.** 원본이 가로다.
 *  3. 순환선은 **원이 아니라 둥근 사각형**. 굵은 초록 선에 43역을 네 변으로 나눠 붙인다.
 *  4. 역 = 흰 점, **열차가 서 있는 역 = 빨간 점**, 역 이름 **43개 전부**.
 *  5. 열차는 하늘색 열번 배지 — **내선은 선 안쪽 · 외선은 선 바깥쪽**.
 *
 * ## v1.6.85 2차 — 사용자 피드백 세 가지
 *
 * *"가독성을 늘려야겠어… 핸드폰 옆에 여백이 많은데? 그리고 본인 열차는 표시해주는거지?"*
 *
 *  · **여백을 없앴다.** 원인은 디자인이 아니라 버그였다 — 돌린 상자에 `Modifier.size` 를
 *    썼더니 부모가 준 **짧은 쪽 폭에 다시 갇혀** 정사각형이 됐다. `requiredSize` 로 고치고
 *    루프 여백을 [margin] 만큼만 남겨 화면을 꽉 채운다.
 *  · **라벨이 절대 안 겹친다.** 실제로 [TextMeasurer] 로 재서 겹치면 밀어낸다([layoutLabels]).
 *  · **내 열차를 반드시 보여 준다.** 지도에서 맨 나중에(제일 위에) 그리고, 헤더와 상태바에
 *    글자로 한 번씩 더 말한다. 못 찾으면 "미검출"이라고 **말이라도 한다.**
 *
 * ## ⚠ 세로 화면에서 기기를 돌리지 않는다 — **그림을 돌린다**
 *
 * `requestedOrientation` 으로 방향을 강제하면 `MainActivity` 에 `configChanges` 가 없어
 * **액티비티가 재생성되고** [LineMap] 의 `var showFull by remember` 가 날아가 다이얼로그가
 * 그 자리에서 닫힌다. 매니페스트를 고치는 것도 금물이다(폴드 동작이 그 위에 얹혀 있다).
 * 그래서 v1.6.9 의 전체화면 행로표(`RouteImageDialog`, `Matrix.postRotate(90f)`)와 **같은
 * 방향**으로 그림만 돌린다. Compose 는 이 변환을 **히트테스트에도 반영**하므로 열차 탭이
 * 그대로 동작한다(에뮬 확인).
 *
 * ## ⚠ 위치는 **둘레 호길이(arc length)** 로 잡는다
 *
 * 열차 좌표는 `역 인덱스 + offset`(최대 ±0.6)이라 **역과 역 사이**에 놓인다. 두 역을 직선으로
 * 이어 lerp 하면 모서리를 가로지르는 열차가 **선 밖으로 뜬다**. 그래서 둥근 사각형 둘레를
 * (직선 4 + 모서리호 4)로 잘라 누적 길이를 만들고([Loop]), 역마다 그 위의 `s` 값을 준 뒤
 * `Loop.at(s)` 로 점과 접선을 얻는다. 배지를 선 안/밖으로 미는 것도 이 접선의 법선이다.
 *
 * ## 보간을 하지 않는 이유(v1.6.84 결정 유지)
 *
 * 본선 43역에는 구간별 실측 주행시간이 없다 — 지어내면 화면 속도가 실차와 어긋난다.
 * 역 단위 위치만 쓰고, 값이 바뀔 때 `tween(1500, Linear)` 로 옮기는 데까지만 한다.
 * 폴링 눈금(2초·절대시각)·1초 시계·`DisposableEffect` 로그도 [BranchLiveMap] 규칙 그대로다.
 */

/**
 * 상태바·제스처바가 이 화면을 덮는 띠(위, 아래).
 *
 * ## ⚠ 이 다이얼로그에서는 인셋 API 가 전부 0 을 준다 (실측 3종)
 *
 * `WindowInsets.systemBars` · 다이얼로그 뷰의 `rootWindowInsets` · 액티비티 decorView 의
 * `rootWindowInsets` 를 차례로 다 써 봤지만 셋 다 0/null 이었다(`BoxWithConstraints` 가
 * 화면 그대로 411x913.9dp 를 줬다 = 여백이 하나도 안 빠졌다는 뜻). 전체화면 다이얼로그라
 * 인셋이 전달되지 않는 자리다.
 *
 * 그래서 **액티비티 창의 실제 여백을 재서** 쓰고, 그것도 못 구하면 아래 기본값으로 간다.
 * 이 값을 안 빼면 돌린 내용의 한쪽이 바 밑으로 들어가 잘린다 —
 * 실측: 세로에서 **오른쪽 차선 역명 다섯(건대입구·구의·강변·잠실나루·잠실)과 닫기 X** 가
 * 통째로 안 보였다.
 *
 * 기본값은 이 앱이 도는 기기(폴드7·에뮬 1080x2400 노치)에서 잰 값이다 — **보정 손잡이**이니
 * 다른 기기에서 잘리거나 남으면 여기만 고치면 된다.
 */
private fun decorInsets(ctx: Context, dens: Density): Pair<Dp, Dp> {
    var c: Context? = ctx
    while (c is ContextWrapper && c !is Activity) c = c.baseContext
    (c as? Activity)?.window?.decorView?.let { dv ->
        val f = dv.rootWindowInsets
        val top = f?.systemWindowInsetTop ?: 0
        val bottom = f?.systemWindowInsetBottom ?: 0
        // ⚠ 제스처 네비게이션은 **덮어쓰는 바**라 `systemWindowInsetBottom` 이 0 으로 온다
        // (실측: 위는 136px 로 제대로 왔는데 아래가 0 이라 오른쪽 차선이 계속 물렸다).
        // 그래서 아래는 최소값을 깔아 준다 — 여기가 보정 손잡이다.
        if (top > 0) return with(dens) {
            top.toDp() to bottom.toDp().coerceAtLeast(44.dp)
        }
    }
    return 54.dp to 44.dp
}

/** 운전실 화면 바탕 — 테마와 무관하게 늘 이 남색이다. */
private val CabNavy = Color(0xFF0E2A47)
private val LoopGreen = Color(0xFF2FC24A)
private val StationWhite = Color(0xFFFFFFFF)
/** 열차가 서 있는 역 */
private val StationRed = Color(0xFFF0392B)
/** 열번 배지 — 옅은 하늘색 바탕 + 진한 남색 글씨 */
private val BadgeSky = Color(0xFFA9DCF5)
private val BadgeInk = Color(0xFF0A2036)
private val MineYellow = Color(0xFFFFE14D)
private val MineInk = Color(0xFFB3261E)
private val Dim = Color(0xFF8FA9C4)
private const val TAG = "BranchLive"

/**
 * 루프 바깥 여백 — 사용자가 *"옆에 여백이 많다"* 고 해서 최소로 줄였다.
 *
 * ⚠ 0 으로 못 내린다. **외선 열번 배지가 선 바깥에 붙기 때문**이다(배지 중심 14dp +
 * 절반 높이 ≈12dp = 26dp). 10dp 로 뒀더니 아랫변·윗변 외선 배지가 잘렸다(실측).
 */
private fun margin(big: Boolean) = (if (big) 42 else 30).dp

/**
 * 좌·우 **역명 전용 차선**. 세로변 10개 역(건대입구…잠실 · 대림…당산)의 이름을 루프 **바깥**
 * 이 폭 안에 가로로 적는다.
 *
 * ⚠ 종전에는 이 10개도 루프 안쪽에 적었는데, 모서리에서 윗변·아랫변의 긴 대각선 라벨과
 * 겹쳐 밀어내기가 **연쇄**했다(하나 밀면 다음과 부딪혀 또 밀리고, 왼쪽 위가 통째로 뭉갰다).
 * 바깥에 자기 차선을 주면 그 충돌이 애초에 안 난다. 폭은 `배지 바깥끝(33dp) + 가장 긴
 * 이름(영등포구청 ≈55dp)` 이 들어갈 만큼이다.
 */
private fun lane(big: Boolean) = (if (big) 108 else 92).dp

/**
 * 둘레의 시작 역 — 사진의 왼쪽 위 첫 역. [Line2Stations.MAIN] 에서 인덱스 37 이다.
 *
 * ⚠ [Line2Stations.MAIN] 은 **내선 순서 그대로 두고**(순서를 바꾸면 지도의 모든 열차가 어긋난다)
 * 그리기에서만 여기서부터 세어 둘레 자리를 만든다. `Line2Test.둘레 네 변 배치가 사진과 같다` 가
 * 네 변의 역 목록을 글자 하나까지 잠근다.
 */
private const val LOOP_START = "합정"

/** 네 변에 나눠 담는 역 수 — 사진 그대로. 합이 43 이다. */
private const val TOP_N = 17      // 합정 … 성수      (왼→오)
private const val RIGHT_N = 5     // 건대입구 … 잠실   (위→아래)
private const val BOTTOM_N = 16   // 잠실새내 … 구로디지털단지 (오→왼)
private const val LEFT_N = 5      // 대림 … 당산      (아래→위)
private const val LOOP_N = TOP_N + RIGHT_N + BOTTOM_N + LEFT_N   // 43

/**
 * 윗변·아랫변 역명 기울기. 사진은 −45° 인데 **−35° 로 눕혔다** — 폰에서 루프 안쪽 높이가
 * ≈163dp 뿐이라 −45° 면 위아래 라벨이 각각 104dp 씩 파고들어 가운데에서 만난다(실측).
 * −35° 면 세로로 79dp 만 쓰고 가로로 길어지는데, 역 간격(≈51dp)에 `sin35°` 를 곱한
 * 이웃 간 수직거리 29dp 가 글자 높이 16dp 보다 커서 이웃끼리는 여전히 안 닿는다.
 */
private const val DIAG = -35f

/**
 * 방향 필터 — 한쪽만 그리면 배지가 반으로 줄어 역명·열번을 크게 쓸 수 있다.
 *
 * 기본값은 **내 열차 방향**이다(사용자 확정). 아직 아무 칩도 안 눌렀으면 상태는 `null` 이고
 * 그릴 때만 내 열차에서 방향을 꺼내 쓴다 — 칩을 한 번 누르면 그 뒤로는 내 열차가 바뀌어도
 * 사용자가 고른 값이 이긴다. (회전·접힘에서 초기값으로 돌아가는 건 `rememberSaveable`
 * 함정 때문에 **알고 둔 대가** — 파일 KDoc 참고.)
 */
internal enum class DirFilter(val label: String) { INNER("내선"), OUTER("외선"), ALL("전체") }

/**
 * 전체화면 순환선 지도.
 *
 * @param duty 오늘 근무 — 내 열번 판정에 쓴다. **본선·지선을 가리지 않는다**(사용자 확정).
 * @param date 오늘 날짜(야간 후반 익일 판정에 필요)
 */
@Composable
internal fun MainLineMapDialog(duty: DutyCode?, date: LocalDate, onDismiss: () -> Unit) {
    var snap by remember { mutableStateOf(Snapshot()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var picked by remember { mutableStateOf<String?>(null) }
    // null = 아직 사용자가 칩을 안 눌렀다 → 아래에서 내 열차 방향으로 정한다
    var filter by remember { mutableStateOf<DirFilter?>(null) }

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

    // 오늘 근무가 잡는 열번 **전부**가 후보다(본선·지선 무관 — 사용자 확정).
    val candidates = remember(duty, date) {
        duty?.let { dutyTrainNumbers(it, date) }.orEmpty()
    }
    // 사업 시각 문구("출고 전 …")는 본선 근무에서만 나온다 — 지선은 시각표가 없다.
    val mine = remember(duty, date, now / 60_000) {
        duty?.let { myTrainAt(it, date, LocalDateTime.now()) }
    }
    val shown = snap.mainTrains
    // 후보 중 **실제로 API 에 살아 있는** 첫 번째가 내 열차다(추정하지 않는다 — MyTrain KDoc)
    val mineMark = remember(candidates, shown) {
        candidates.firstNotNullOfOrNull { no -> shown.firstOrNull { it.trainNo == no } }
    }
    val eff = filter
        ?: mineMark?.let { if (it.inner) DirFilter.INNER else DirFilter.OUTER }
        ?: DirFilter.ALL

    // ⚠ `decorFitsSystemWindows = false` 가 있어야 다이얼로그에 인셋이 **전달된다**. 없으면
    // 아래 `windowInsetsPadding` 이 0 을 받아 무효가 되고, 돌린 내용의 한쪽이 상태바·제스처바
    // 밑으로 들어가 잘린다(실측: 세로에서 오른쪽 차선 역명과 닫기 X 가 통째로 안 보였다).
    // ⚠ **시스템바 여백을 Compose 인셋으로 못 받는다.** 이 다이얼로그 안에서
    // `WindowInsets.systemBars` 는 0 을 준다(실측: 상자가 화면 그대로 411x913.9dp 로 잡혔다).
    // 그래서 뷰에서 직접 읽는다 — 안 그러면 돌린 내용의 한쪽이 상태바·제스처바 밑으로 잘린다
    // (실측: 세로에서 오른쪽 차선 역명 다섯과 닫기 X 가 통째로 안 보였다).
    val view = LocalView.current
    val dens = LocalDensity.current
    val sysIns = decorInsets(view.context, dens)
    val safeTop = sysIns.first
    val safeBottom = sysIns.second

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = CabNavy) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val portrait = maxHeight > maxWidth
                // 돌린 뒤의 **내용 크기**. 세로 창이면 가로세로를 맞바꿔 잡는다.
                val cw = if (portrait) maxHeight else maxWidth
                val ch = if (portrait) maxWidth else maxHeight
                // rotationZ = +90 은 내용을 시계로 돌린다 → 내용의 **왼쪽** 변이 화면 위
                // (상태바), **오른쪽** 변이 화면 아래(제스처바)로 간다. 그래서 시스템바 여백도
                // 같이 돌려서 준다.
                val inset =
                    if (portrait) PaddingValues(start = safeTop, end = safeBottom)
                    else PaddingValues(top = safeTop, bottom = safeBottom)

                if (portrait) {
                    Box(
                        Modifier.align(Alignment.Center)
                            // ⚠ **`requiredSize` 여야 한다.** `size` 는 부모가 준 최대 폭
                            // (세로 창이니 짧은 쪽)에 다시 갇혀서 정사각형이 되고, 돌려 봐야
                            // 화면 가운데 네모난 띠만 채운다(실측: 411dp 정사각형이 나왔다).
                            .requiredSize(width = cw, height = ch)
                            .graphicsLayer { rotationZ = 90f },
                    ) {
                        CabScreen(ch, inset, now, shown, mine, mineMark, candidates,
                            snap.error, picked, { picked = it }, eff, { filter = it }, onDismiss)
                    }
                } else {
                    CabScreen(ch, inset, now, shown, mine, mineMark, candidates,
                        snap.error, picked, { picked = it }, eff, { filter = it }, onDismiss)
                }
            }
        }
    }
}

/** 상단바 + 지도 + 하단 상태바. 세로 창에서는 통째로 90도 돌아간다. */
@Composable
private fun CabScreen(
    ch: Dp, inset: PaddingValues, nowMillis: Long,
    trains: List<MainTrainMark>, mine: MyTrain?, mineMark: MainTrainMark?, candidates: List<String>,
    error: String?, picked: String?, onPick: (String?) -> Unit,
    eff: DirFilter, onFilter: (DirFilter) -> Unit, onDismiss: () -> Unit,
) {
    // 폴드 펼침처럼 세로가 넉넉하면 글자를 키운다(사진처럼 시원하게).
    val big = ch >= 480.dp
    // 한 방향만 그리면 배지가 반으로 줄어드니 글자를 키운다. 겹침은 [layoutLabels] 가
    // **측정값으로** 판정하므로 이 두 값만 바꿔도 회피가 따라온다.
    val filtered = eff != DirFilter.ALL
    val labelSp = if (filtered) (if (big) 16f else 13.5f) else (if (big) 14f else 11.5f)
    val badgeSp = if (filtered) (if (big) 14.5f else 12f) else (if (big) 13f else 10.5f)

    val ctx = LocalContext.current
    val tt = remember { Line2TimetableLoader.get(ctx) }
    val weekTag = remember(nowMillis / 60_000) {
        Line2Timetable.weekTagOf(Line2Timetable.serviceClock(LocalDateTime.now()).first)
    }
    // 내 열차의 지연·다음 역 — 시간표만 보므로 **API 호출이 늘지 않는다**. 15초마다 다시 센다.
    val (delay, nextSec) = remember(
        mineMark?.trainNo, mineMark?.statnNm, mineMark?.trainSttus, tt, nowMillis / 15_000,
    ) {
        val m = mineMark; val t = tt
        if (m == null || t == null) null to null else {
            val (d, sec) = Line2Timetable.serviceClock(LocalDateTime.now())
            val w = Line2Timetable.weekTagOf(d); val io = Line2Timetable.inoutOf(m.inner)
            val dl = t.delayMinutes(w, io, m.trainNo, m.statnNm, m.trainSttus, sec)
            dl to dl?.let { t.secondsToNextStop(w, io, m.trainNo, m.statnNm, it, sec) }
        }
    }

    Column(Modifier.fillMaxSize().padding(inset)) {
        CabHeader(nowMillis, mineMark, candidates, delay, nextSec, big, onDismiss)
        Box(Modifier.fillMaxWidth().weight(1f)) {
            val d = LocalDensity.current
            // 지도 안 글자배율 상한 — 그림은 dp, 글자만 sp라 배율을 키우면 역 이름이 넘친다.
            // ⚠ TextMeasurer 는 반드시 이 안에서 만든다(지선 지도와 같은 처방).
            CompositionLocalProvider(
                LocalDensity provides Density(d.density, d.fontScale.coerceAtMost(1.15f))
            ) {
                val tm = rememberTextMeasurer()
                val infinite = rememberInfiniteTransition(label = "loop")
                val pulse by infinite.animateFloat(
                    0f, 1f,
                    infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
                    label = "pulse",
                )
                // 방향 필터 — **내 열차는 어느 모드에서든 그린다**(사용자 확정).
                val drawn = trains.filter {
                    eff == DirFilter.ALL || (eff == DirFilter.INNER) == it.inner ||
                        it.trainNo == mineMark?.trainNo
                }
                // 열차마다 자리를 부드럽게 옮긴다(등속 보간은 안 한다 — 파일 KDoc)
                val placed = drawn.map { t ->
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
                    Modifier.fillMaxSize().pointerInput(hit) {
                        detectTapGestures { tap ->
                            fun dist(o: Offset) =
                                hypot((o.x - tap.x).toDouble(), (o.y - tap.y).toDouble())
                            val near = hit.minByOrNull { dist(it.first) }
                            onPick(near?.takeIf { dist(it.first) < 26.dp.toPx() }?.second)
                        }
                    }
                ) {
                    hit = drawCabLoop(tm, placed, mineMark?.trainNo, pulse, big, picked,
                        labelSp, badgeSp)
                }
            }
        }
        CabStatusBar(mine, mineMark, candidates, error, trains.isNotEmpty(), big, eff, onFilter)
    }
}

/* ────────────────────────── 상단바 ────────────────────────── */

private val WEEKDAYS = listOf("월", "화", "수", "목", "금", "토", "일")

/**
 * 후보 열번 줄이기 — 지선 다이아는 한 근무가 **스무 개 넘는 열번**을 잡아서 그대로 이으면
 * 헤더 한 줄을 통째로 먹는다(실측: `5668·5669·…·5527` 20개가 화면 세로를 다 채웠다).
 */
private fun shortNos(nos: List<String>): String =
    nos.take(4).joinToString("·") + if (nos.size > 4) " 외 " + (nos.size - 4) + "개" else ""

/**
 * 내 열차 한 줄 — 헤더와 상태바가 같은 글을 쓴다. `null` = 후보조차 없다.
 *
 * [delay]·[nextSec] 는 [Line2Timetable] 이 준 값이고 **시간표에 열번이 없으면 null** 이라
 * 그 두 토막만 조용히 빠진다(없는 값을 지어내지 않는다 — [MyTrain] KDoc 과 같은 규칙).
 */
private fun mineLine(
    mineMark: MainTrainMark?, candidates: List<String>, delay: Int?, nextSec: Int?,
): String? = when {
    mineMark != null ->
        "내 열차 " + mineMark.trainNo + " · " + (if (mineMark.inner) "내선" else "외선") +
            " · " + mineMark.statusText +
            delay?.let {
                when {
                    it > 0 -> " · +${it}분 지연"
                    it < 0 -> " · ${-it}분 빠름"
                    else -> " · 정시"
                }
            }.orEmpty() +
            nextSec?.let {
                if (it <= 0) " · 곧 도착" else " · 다음 역 ${(it + 59) / 60}분 후"
            }.orEmpty()
    candidates.isNotEmpty() -> "내 열차 미검출 (운행 전/후) · " + shortNos(candidates)
    else -> null
}

/** 좌: 소제목 · 우: 날짜 + 큰 노란 디지털 시계, 그 아래 내 열차 한 줄. */
@Composable
private fun CabHeader(
    nowMillis: Long, mineMark: MainTrainMark?, candidates: List<String>,
    delay: Int?, nextSec: Int?, big: Boolean, onDismiss: () -> Unit,
) {
    val t = remember(nowMillis / 1_000) {
        LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), ZoneId.systemDefault())
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, top = 3.dp, end = 2.dp, bottom = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "2호선 실시간",
            fontSize = if (big) 20.sp else 16.sp, fontWeight = FontWeight.Bold,
            color = Color.White, modifier = Modifier.weight(1f),
        )
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "%02d/%02d (%s)".format(
                        t.monthValue, t.dayOfMonth, WEEKDAYS[t.dayOfWeek.value - 1]),
                    fontSize = if (big) 15.sp else 12.sp, color = Dim,
                    modifier = Modifier.padding(end = 10.dp, bottom = 3.dp),
                )
                Text(
                    "%02d:%02d:%02d".format(t.hour, t.minute, t.second),
                    fontSize = if (big) 30.sp else 22.sp, fontWeight = FontWeight.Bold,
                    color = MineYellow,
                )
            }
            // ⚠ 도착 예정 **시각은 만들지 않는다** — 그 데이터가 앱에 없다(MyTrain KDoc).
            // API 가 준 역명(statnNm)과 상태(trainSttus)만 그대로 옮긴다.
            mineLine(mineMark, candidates, delay, nextSec)?.let {
                Text(
                    it,
                    fontSize = if (big) 14.sp else 11.5.sp, fontWeight = FontWeight.Bold,
                    color = if (mineMark != null) MineYellow else Dim,
                )
            }
        }
        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "닫기", tint = Color.White) }
    }
}

/* ────────────────────────── 하단 상태바 ────────────────────────── */

/** 사진의 버튼 줄 — 알약 칩. ⚠ **편성번호·전방/후방은 데이터가 없어 넣지 않는다.** */
@Composable
private fun CabStatusBar(
    mine: MyTrain?, mineMark: MainTrainMark?, candidates: List<String>,
    error: String?, hasTrains: Boolean, big: Boolean,
    filter: DirFilter, onFilter: (DirFilter) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Chip("2호선", big, LoopGreen)
        when {
            // ⚠ 오류는 **열차가 하나도 없을 때만** 말한다. 스냅샷의 `error` 는 지선 카드가 쓰는
            // 양천구청 **도착** API 실패까지 합쳐 온 값이라(위치 API 는 멀쩡할 수 있다), 그대로
            // 띄우면 열차 14대를 그려 놓고 "정보를 못 받았어요"라고 말하게 된다(v1.6.84 실측).
            error != null && !hasTrains -> Chip(error, big, Color(0xFFE9A23B))
            mineMark != null -> {
                Chip("내 열번 " + mineMark.trainNo, big, MineYellow, fill = true)
                Chip(mineMark.destName + "행", big, MineYellow)
                Chip(if (mineMark.inner) "↻ 내선" else "↺ 외선", big, BadgeSky)
            }
            candidates.isNotEmpty() -> {
                Chip("내 열차 미검출 (운행 전/후)", big, Dim)
                // 사업 시각을 아는 본선 근무면 언제 나가는지까지 말해 준다.
                if (mine != null && !mine.riding && mine.startAt != null) {
                    Chip(
                        "다음 " + mine.nos.first() + " " + fmt(mine.startAt) +
                            (if (mine.nextDay) " (익일)" else ""),
                        big, Color(0xFFCFE3F5),
                    )
                } else {
                    Chip("오늘 열번 " + shortNos(candidates), big, Color(0xFFCFE3F5))
                }
            }
            else -> Chip("오늘 근무 열번: 없음", big, Dim)
        }
        // 오른쪽 끝에 방향 필터. 기본은 내 열차 방향이라 처음 열면 이미 한쪽이 켜져 있다.
        Spacer(Modifier.weight(1f))
        DirFilter.entries.forEach { f ->
            Chip(f.label, big, BadgeSky, fill = f == filter) { onFilter(f) }
        }
    }
}

private fun fmt(t: LocalTime) = "%02d:%02d".format(t.hour, t.minute)

@Composable
private fun Chip(
    text: String, big: Boolean, tint: Color, fill: Boolean = false, onClick: (() -> Unit)? = null,
) = Surface(
    color = if (fill) tint else tint.copy(alpha = 0.12f),
    shape = RoundedCornerShape(50),
    border = BorderStroke(1.dp, tint.copy(alpha = 0.85f)),
) {
    Text(
        text,
        fontSize = if (big) 14.sp else 11.5.sp, fontWeight = FontWeight.Bold,
        color = if (fill) MineInk else tint,
        // 누를 수 있는 칩만 클릭 영역을 만든다 — 나머지는 종전대로 그냥 글씨다.
        modifier = Modifier
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 11.dp, vertical = 4.dp),
    )
}

/* ────────────────────────── 둘레 기하 ────────────────────────── */

private const val HALF_PI = (PI / 2.0).toFloat()

/**
 * 둥근 사각형 둘레의 **호길이 매개변수**. 직선 4 + 모서리호 4 = 여덟 구간을 순서대로 잇는다.
 *
 * 진행 방향은 **시계**(윗변 왼→오 · 오른변 위→아래 · 아랫변 오→왼 · 왼변 아래→위)이고,
 * 그게 곧 **내선**이다. [at] 은 점과 **단위 접선**을 함께 준다 — 법선(선 안쪽 방향)은 접선을
 * 90도 돌린 `(-ty, tx)` 다(화면 좌표는 y 가 아래로 커지므로 이게 안쪽을 가리킨다).
 */
private class Loop(
    private val x0: Float, private val y0: Float,
    private val x1: Float, private val y1: Float, private val r: Float,
) {
    private val hLen = (x1 - x0 - 2f * r).coerceAtLeast(1f)
    private val vLen = (y1 - y0 - 2f * r).coerceAtLeast(1f)
    private val arc = HALF_PI * r
    val total = 2f * (hLen + vLen) + 4f * arc

    private val rightS = hLen + arc
    private val bottomS = rightS + vLen + arc
    private val leftS = bottomS + hLen + arc

    private fun arcAt(cx: Float, cy: Float, t: Float) =
        Offset(cx + r * cos(t), cy + r * sin(t)) to Offset(-sin(t), cos(t))

    /** 둘레 거리 [sRaw] 의 (점, 단위 접선). 한 바퀴를 넘거나 음수여도 접어서 받는다. */
    fun at(sRaw: Float): Pair<Offset, Offset> {
        var u = sRaw % total
        if (u < 0f) u += total
        if (u < hLen) return Offset(x0 + r + u, y0) to Offset(1f, 0f)
        u -= hLen
        if (u < arc) return arcAt(x1 - r, y0 + r, -HALF_PI + u / r)
        u -= arc
        if (u < vLen) return Offset(x1, y0 + r + u) to Offset(0f, 1f)
        u -= vLen
        if (u < arc) return arcAt(x1 - r, y1 - r, u / r)
        u -= arc
        if (u < hLen) return Offset(x1 - r - u, y1) to Offset(-1f, 0f)
        u -= hLen
        if (u < arc) return arcAt(x0 + r, y1 - r, HALF_PI + u / r)
        u -= arc
        if (u < vLen) return Offset(x0, y1 - r - u) to Offset(0f, -1f)
        u -= vLen
        return arcAt(x0 + r, y0 + r, PI.toFloat() + u / r)
    }

    /**
     * 둘레 자리 [k](0..42) 역의 누적 거리. 각 변의 역들은 그 변의 **직선 구간에 균등**
     * (양끝 포함)하게 놓고, 모서리 호는 변과 변을 잇는 빈 구간으로 남긴다.
     */
    fun sOf(k: Int): Float = when {
        k < TOP_N -> k / (TOP_N - 1f) * hLen
        k < TOP_N + RIGHT_N -> rightS + (k - TOP_N) / (RIGHT_N - 1f) * vLen
        k < TOP_N + RIGHT_N + BOTTOM_N ->
            bottomS + (k - TOP_N - RIGHT_N) / (BOTTOM_N - 1f) * hLen
        else -> leftS + (k - TOP_N - RIGHT_N - BOTTOM_N) / (LEFT_N - 1f) * vLen
    }

    /**
     * **역과 역 사이**의 실수 자리 [kf] → 둘레 거리. 이웃한 두 역의 누적 거리를 잇는 구간
     * 위에서 비례로 잡으므로 모서리에서도 열차가 **선 위에 남는다**(직선 lerp 는 선 밖으로 뜬다).
     */
    fun sOfPos(kf: Float): Float {
        var p = kf % LOOP_N
        if (p < 0f) p += LOOP_N
        val lo = floor(p).toInt().coerceIn(0, LOOP_N - 1)
        val a = sOf(lo)
        val b = if (lo == LOOP_N - 1) total else sOf(lo + 1)
        return a + (p - lo) * (b - a)
    }

    /** 모서리 호 한가운데 — 역이 없는 자리라 방향 화살표를 놓기 좋다. */
    fun cornerMid(corner: Int): Float {
        val next = when (corner) {
            0 -> TOP_N; 1 -> TOP_N + RIGHT_N; 2 -> TOP_N + RIGHT_N + BOTTOM_N; else -> LOOP_N
        }
        return (sOf(next - 1) + (if (next == LOOP_N) total else sOf(next))) / 2f
    }
}

/* ────────────────────────── 역 이름 배치 ────────────────────────── */

/**
 * 그릴 준비가 끝난 역 이름 한 장. [pivot] 을 중심으로 [deg] 만큼 돌려 그린다.
 *
 * @param leftAnchored true = 글자가 [pivot] 에서 **오른쪽으로** 뻗는다 / false = 왼쪽으로
 */
private class Lab(
    val layout: TextLayoutResult,
    var pivot: Offset,
    val deg: Float,
    val leftAnchored: Boolean,
) {
    /**
     * 돌려 놓은 글자 상자의 **네 꼭짓점**.
     *
     * ⚠ 축정렬 사각형(AABB)으로 겹침을 보면 안 된다 — 55×16dp 글자를 35° 돌리면 AABB 는
     * 54×45dp 로 부풀어, 실제로는 스치지도 않는 이웃끼리 "겹쳤다"고 나온다. 그러면 밀어내기가
     * **연쇄**해서 아랫변 라벨이 윗변까지 올라간다(실측: `서울대입구`가 `을지로입구` 위에
     * 올라탔다). 그래서 [overlaps] 가 분리축(SAT)으로 진짜 겹침만 본다.
     */
    fun quad(pad: Float): List<Offset> {
        val w = layout.size.width.toFloat() + pad
        val h = layout.size.height.toFloat() + pad
        val lx = if (leftAnchored) pivot.x - pad / 2f else pivot.x - w + pad / 2f
        val ty = pivot.y - h / 2f
        val rad = deg * PI.toFloat() / 180f
        val cs = cos(rad)
        val sn = sin(rad)
        // 시계 방향 네 점 — SAT 는 이웃한 변 두 개의 법선만 보면 된다.
        return listOf(lx to ty, lx + w to ty, lx + w to ty + h, lx to ty + h).map { (cx, cy) ->
            val dx = cx - pivot.x
            val dy = cy - pivot.y
            Offset(pivot.x + dx * cs - dy * sn, pivot.y + dx * sn + dy * cs)
        }
    }
}

/** 축정렬 사각형도 같은 네 점 꼴로. */
private fun Rect.quad(): List<Offset> =
    listOf(Offset(left, top), Offset(right, top), Offset(right, bottom), Offset(left, bottom))

/** 볼록 사각형 두 개가 진짜로 겹치는가 — 분리축 정리(SAT). 직사각형이라 축은 변마다 둘씩. */
private fun overlaps(a: List<Offset>, b: List<Offset>): Boolean {
    for (poly in listOf(a, b)) for (i in 0 until 2) {
        val ax = -(poly[i + 1].y - poly[i].y)
        val ay = poly[i + 1].x - poly[i].x
        var minA = Float.MAX_VALUE; var maxA = -Float.MAX_VALUE
        var minB = Float.MAX_VALUE; var maxB = -Float.MAX_VALUE
        for (p in a) { val d = p.x * ax + p.y * ay; if (d < minA) minA = d; if (d > maxA) maxA = d }
        for (p in b) { val d = p.x * ax + p.y * ay; if (d < minB) minB = d; if (d > maxB) maxB = d }
        if (maxA <= minB || maxB <= minA) return false
    }
    return true
}

/**
 * 43개 역 이름을 **서로 겹치지 않게** 놓는다 — 사용자 피드백 *"텍스트가 안 겹칠 것 같은데?"*.
 *
 * ## 왜 변마다 각도가 다른가
 *
 * 윗변·아랫변은 역이 촘촘해(17·16개) 가로로 쓰면 바로 뭉갠다. **−45° 로 기울이면 이웃 라벨과의
 * 수직 거리가 `가로간격 × sin45°` 만큼 벌어져** 43개를 다 적어도 서로 안 닿는다(윗변 실측
 * 간격 ≈51dp → 36dp 벌어짐, 글자 높이 ≈16dp).
 *
 * 좌·우변은 역이 5개뿐이라 세로로 넉넉하다 — 여기는 **가로로 쓴다.** 굳이 기울이면 윗변·아랫변
 * 라벨과 **같은 대각선 띠**에 올라타서 모서리에서 정면충돌한다(오른아래·왼위 두 모서리가
 * 정확히 그렇다: 두 라벨의 기준점 차가 대각선과 평행해 띠 간격이 0이 된다).
 *
 * ## 그래도 남는 모서리 네 곳은 **재서** 민다
 *
 * 방향이 달라도 모서리에서는 상자가 걸칠 수 있다. 그래서 [TextMeasurer] 로 실제 크기를 재
 * [Lab.bounds] 로 겹침을 보고, 겹치면 그 변의 **안쪽 방향으로** 한 칸씩(최대 3칸) 밀어낸다.
 * 윗변·아랫변을 먼저 놓아 줄이 흐트러지지 않게 하고, 밀리는 쪽은 좌·우변 10개뿐이다.
 */
private fun DrawScope.layoutLabels(
    tm: TextMeasurer, loop: Loop, start: Int, occupied: Set<Int>, sizeSp: Float,
    obstacles: List<Rect>,
): List<Lab> {
    // ⚠ 배지는 [drawCabLoop] 가 **장애물로 넘겨 주므로** 여기서 크게 비켜 둘 필요가 없다.
    // 대신 깊이를 아껴야 한다 — 폰에서 루프 안쪽 높이가 ≈163dp 뿐인데 윗변·아랫변 라벨이
    // 양쪽에서 파고들기 때문이다(34dp 로 뒀을 때 가운데에서 서로 만났다).
    val gap = 30.dp.toPx()
    val step = 11.dp.toPx()
    val placed = mutableListOf<Lab>()

    // 윗변·아랫변(대각선) 먼저 → 좌·우변(가로). 밀려나는 쪽이 소수가 되게 하는 순서다.
    val order = (0 until LOOP_N).sortedBy { k -> if (k < TOP_N || k in (TOP_N + RIGHT_N) until (TOP_N + RIGHT_N + BOTTOM_N)) 0 else 1 }

    for (k in order) {
        val name = Line2Stations.MAIN[(k + start) % LOOP_N]
        val red = k in occupied
        val layout = tm.measure(name, TextStyle(
            fontSize = sizeSp.sp,
            fontWeight = if (red) FontWeight.ExtraBold else FontWeight.Medium,
            color = if (red) Color(0xFFFFD9D4) else Color.White))
        val (p, _) = loop.at(loop.sOf(k))
        val onTop = k < TOP_N
        val onRight = k in TOP_N until (TOP_N + RIGHT_N)
        val onBottom = k in (TOP_N + RIGHT_N) until (TOP_N + RIGHT_N + BOTTOM_N)

        // 윗변·아랫변은 **선 안쪽**으로 gap 만큼 들어와 대각선으로 뻗고, 좌·우변은 **바깥쪽**
        // 자기 차선([lane])에 가로로 적는다. 밀어내는 방향도 다르다 — 윗변·아랫변은 안쪽으로,
        // 좌·우변은 **변을 따라 그 변의 한가운데 쪽으로**(역이 5개뿐이라 위아래가 늘 비어 있다).
        val midY = size.height / 2f
        val midX = size.width / 2f
        val side = 30.dp.toPx()
        // ⚠ 윗변·아랫변은 **안쪽 + 모서리 반대쪽**으로 비스듬히 민다. 순수하게 안쪽으로만
        // 밀면 왼위·오른아래 모서리에서 윗변 라벨과 아랫변 라벨이 서로에게 다가가 만난다
        // (실측: `구로디지털단지`가 `홍대입구` 위로, `잠실새내`가 `성수` 위로 올라탔다).
        val awayX = if (p.x < midX) 0.7f else -0.7f
        val (pivot, push) = when {
            onTop -> Offset(p.x + 4.dp.toPx(), p.y + gap) to Offset(awayX, 0.7f)
            onBottom -> Offset(p.x - 4.dp.toPx(), p.y - gap) to Offset(awayX, -0.7f)
            onRight -> Offset(p.x + side, p.y) to Offset(0f, if (p.y < midY) 1f else -1f)
            else -> Offset(p.x - side, p.y) to Offset(0f, if (p.y < midY) 1f else -1f)
        }
        val lab = Lab(
            layout, pivot,
            deg = if (onTop || onBottom) DIAG else 0f,
            // 오른변은 차선이 오른쪽이라 왼쪽 끝을 붙여 오른쪽으로, 왼변은 그 반대.
            leftAnchored = onBottom || onRight,
        )
        // 겹치면 그 변 안쪽으로 한 칸씩 민다(최대 3칸). 대부분 0칸에서 끝난다.
        // 좌·우변은 위아래 **양쪽**이 비어 있으니 번갈아 밀어 본다(가까운 쪽이 막히면 반대쪽).
        // 윗변·아랫변은 바깥이 선이라 안쪽 한 방향뿐이다.
        val alternate = !(onTop || onBottom)
        val pad = 3.dp.toPx()
        var tries = 0
        fun clashes(): Boolean {
            val q = lab.quad(pad)
            return obstacles.any { overlaps(it.quad(), q) } ||
                placed.any { overlaps(it.quad(pad), q) }
        }
        while (tries < 14 && clashes()) {
            tries++
            val d = if (alternate) (if (tries % 2 == 1) 1f else -1f) * ((tries + 1) / 2) * step
                    else tries * step
            lab.pivot = Offset(pivot.x + push.x * d, pivot.y + push.y * d)
        }
        placed += lab
    }
    return placed
}

/* ────────────────────────── 그리기 ────────────────────────── */

/** 순환선 한 장을 그리고 **열차 탭 판정용 좌표**를 돌려준다. */
private fun DrawScope.drawCabLoop(
    tm: TextMeasurer,
    trains: List<Pair<MainTrainMark, Float>>,
    mineNo: String?,
    pulse: Float,
    big: Boolean,
    picked: String?,
    /** 역 이름 크기 — 방향 필터가 켜져 있으면 커진다([CabScreen]) */
    labelSp: Float,
    /** 열번 배지 크기 — 같은 이유로 커진다. **재는 곳과 그리는 곳이 같은 값을 써야** 한다 */
    badgeSp: Float,
): List<Pair<Offset, String>> {
    val m = margin(big).toPx()
    val ln = lane(big).toPx()
    val radius = (if (big) 40 else 28).dp.toPx()
    val loop = Loop(ln, m, size.width - ln, size.height - m, radius)
    val start = Line2Stations.MAIN.indexOf(LOOP_START)

    // ── 초록 굵은 둥근 사각형 ────────────────────────────────
    drawRoundRect(
        LoopGreen,
        topLeft = Offset(ln, m),
        size = Size(size.width - 2 * ln, size.height - 2 * m),
        cornerRadius = CornerRadius(radius),
        style = Stroke(width = (if (big) 9f else 7.5f).dp.toPx()),
    )

    // ── 방향 화살표 — 모서리 호 한가운데(역이 없는 자리)에 안/밖 하나씩 ──
    val arrowOff = (if (big) 15 else 12).dp.toPx()
    for (corner in 0 until 4) {
        val (p, t) = loop.at(loop.cornerMid(corner))
        val nIn = Offset(-t.y, t.x)
        drawChevron(Offset(p.x + nIn.x * arrowOff, p.y + nIn.y * arrowOff), t,
            LoopGreen, 5.dp.toPx())                                   // 내선 = 시계
        drawChevron(Offset(p.x - nIn.x * arrowOff, p.y - nIn.y * arrowOff),
            Offset(-t.x, -t.y), BadgeSky, 5.dp.toPx())                // 외선 = 반시계
    }

    // ── 역 43개 ──────────────────────────────────────────────
    // 열차가 서 있는 역(진입·도착·출발 = 역에 걸쳐 있는 자리)은 빨간 점.
    val occupied = trains.mapNotNull { (_, p) ->
        val kf = p - start
        val k = kotlin.math.round(kf)
        if (abs(kf - k) <= 0.2f) ((k.toInt() % LOOP_N) + LOOP_N) % LOOP_N else null
    }.toSet()

    for (k in 0 until LOOP_N) {
        val (p, _) = loop.at(loop.sOf(k))
        val red = k in occupied
        drawCircle(if (red) StationRed else StationWhite, (if (big) 5f else 4f).dp.toPx(), p)
        if (red) drawCircle(Color.White.copy(alpha = 0.55f), (if (big) 5f else 4f).dp.toPx(), p,
            style = Stroke(width = 1.5.dp.toPx()))
    }
    // ── 열차 자리를 **라벨보다 먼저** 잡는다 ────────────────
    // 내선은 선 **안쪽**, 외선은 **바깥쪽** — 방향을 자리로 구분한다(사진 사양).
    val badgeOff = (if (big) 18 else 14).dp.toPx()
    fun spot(t: MainTrainMark, pos: Float): Pair<Offset, Offset> {
        val (p, tan) = loop.at(loop.sOfPos(pos - start))
        val nIn = Offset(-tan.y, tan.x)
        val dir = if (t.inner) 1f else -1f
        return Offset(p.x + nIn.x * badgeOff * dir, p.y + nIn.y * badgeOff * dir) to
            Offset(nIn.x * dir, nIn.y * dir)
    }
    val spots = trains.map { (t, pos) -> Triple(t, spot(t, pos).first, spot(t, pos).second) }
    // ⚠ 배지를 **장애물로 넘겨** 역 이름이 그 자리를 피해 가게 한다. 안 그러면 배지가
    // 역 이름을 덮는다(실측: `구의`·`강변`이 3046·2011 배지 밑에 깔렸다).
    val bw = tm.measure("0000", TextStyle(fontSize = badgeSp.sp,
        fontWeight = FontWeight.ExtraBold))
    val bhw = (bw.size.width + (if (big) 18 else 15).dp.toPx()) / 2f
    val bhh = (bw.size.height + (if (big) 12 else 10).dp.toPx()) / 2f
    val badgeRects = spots.map { (_, c, _) ->
        Rect(c.x - bhw, c.y - bhh, c.x + bhw, c.y + bhh)
    }

    // 라벨 글자는 **11sp 아래로 내리지 않는다**(사용자: 가독성). 좁으면 겹침 회피로 푼다.
    layoutLabels(tm, loop, start, occupied, labelSp, badgeRects).forEach { lab ->
        rotate(lab.deg, pivot = lab.pivot) {
            drawText(lab.layout, topLeft = Offset(
                if (lab.leftAnchored) lab.pivot.x else lab.pivot.x - lab.layout.size.width,
                lab.pivot.y - lab.layout.size.height / 2f))
        }
    }

    // ── 열차 배지 ────────────────────────────────────────────
    val hits = spots.map { (t, c, _) -> c to t.trainNo }
    // ⚠ **내 열차는 맨 나중에** 그린다 — 다른 배지에 가리면 "표시가 안 된다"는 말이 된다.
    val (mineRows, others) = spots.partition { it.first.trainNo == mineNo }
    others.forEach { (t, c, _) -> drawBadge(tm, c, t.trainNo, false, big, pulse, badgeSp) }
    mineRows.forEach { (t, c, out) ->
        drawBadge(tm, c, t.trainNo, true, big, pulse, badgeSp)
        drawFlag(tm, c, out, t.destName, big)   // 배지 위 노란 행선 깃발
    }

    // ── 탭한 열차의 툴팁 (열차보다 나중에 그려 위에 얹힌다) ──
    picked?.let { no ->
        trains.firstOrNull { it.first.trainNo == no }?.let { (t, pos) ->
            drawTip(tm, spot(t, pos).first, t, no == mineNo)
        }
    }
    return hits
}

/** 열번 배지 — 옅은 하늘색(내 열차는 노랑) 둥근 사각형 + 진한 글씨. */
private fun DrawScope.drawBadge(
    tm: TextMeasurer, c: Offset, no: String, mine: Boolean, big: Boolean, pulse: Float,
    badgeSp: Float,
) {
    val lab = tm.measure(no, TextStyle(
        fontSize = badgeSp.sp, fontWeight = FontWeight.ExtraBold,
        color = if (mine) MineInk else BadgeInk))
    val w = lab.size.width + (if (big) 14 else 11).dp.toPx()
    val h = lab.size.height + (if (big) 8 else 6).dp.toPx()
    val tl = Offset(c.x - w / 2f, c.y - h / 2f)
    if (mine) {
        // 은은한 펄스 — 반투명 노란 테두리가 커지며 사라진다
        val g = pulse * (if (big) 16 else 12).dp.toPx()
        drawRoundRect(MineYellow.copy(alpha = (1f - pulse) * 0.5f),
            topLeft = Offset(tl.x - g, tl.y - g), size = Size(w + 2 * g, h + 2 * g),
            cornerRadius = CornerRadius(6.dp.toPx() + g))
    }
    drawRoundRect(CabNavy, topLeft = Offset(tl.x - 2.dp.toPx(), tl.y - 2.dp.toPx()),
        size = Size(w + 4.dp.toPx(), h + 4.dp.toPx()), cornerRadius = CornerRadius(7.dp.toPx()))
    drawRoundRect(if (mine) MineYellow else BadgeSky, topLeft = tl, size = Size(w, h),
        cornerRadius = CornerRadius(5.dp.toPx()))
    if (mine) drawRoundRect(Color.White, topLeft = tl, size = Size(w, h),
        cornerRadius = CornerRadius(5.dp.toPx()), style = Stroke(width = 1.5.dp.toPx()))
    drawText(lab, topLeft = Offset(c.x - lab.size.width / 2f, c.y - lab.size.height / 2f))
}

/** 내 열차 배지 위의 **노란 행선 깃발** — 짧은 깃대 + 노란 조각. */
private fun DrawScope.drawFlag(
    tm: TextMeasurer, badge: Offset, out: Offset, dest: String, big: Boolean,
) {
    if (dest.isBlank()) return
    val pole = (if (big) 32 else 26).dp.toPx()
    val lab = tm.measure(dest + "행", TextStyle(
        fontSize = (if (big) 12f else 9.5f).sp, fontWeight = FontWeight.Bold, color = MineInk))
    val w = lab.size.width + 10.dp.toPx()
    val h = lab.size.height + 5.dp.toPx()
    // ⚠ 깃발은 배지에서 **선 바깥쪽**으로 더 나가므로 외선 열차가 루프 맨 위/아래에 있으면
    // 그림 밖으로 새어 상단바 글씨를 덮는다(실측: 폴드에서 `성수행` 깃발이 헤더의
    // `내 열차 2039 …` 위에 얹혔다). 여백을 그만큼 늘리면 늘 놀고 있는 자리가 생기니
    // **깃발만 화면 안으로 붙인다** — 깃대는 배지에서 깃발까지 그대로 이어 준다.
    fun tipAt(dir: Offset) = Offset(badge.x + dir.x * pole, badge.y + dir.y * pole)
    fun fits(t: Offset) =
        t.x - w / 2f >= 0f && t.x + w / 2f <= size.width &&
            t.y - h / 2f >= 0f && t.y + h / 2f <= size.height
    // 바깥쪽이 모자라면 **안쪽으로 뒤집는다** — 루프 안은 늘 비어 있다. 그냥 화면 안으로
    // 끌어당기기만 하면 깃발이 배지 위에 포개져 열번이 안 보인다(실측: 가로에서 `2039` 가
    // `성수행` 밑에 깔렸다).
    val outward = tipAt(out)
    val inward = tipAt(Offset(-out.x, -out.y))
    val tip = when {
        fits(outward) -> outward
        fits(inward) -> inward
        else -> Offset(
            outward.x.coerceIn(w / 2f, (size.width - w / 2f).coerceAtLeast(w / 2f)),
            outward.y.coerceIn(h / 2f, (size.height - h / 2f).coerceAtLeast(h / 2f)),
        )
    }
    drawLine(MineYellow, badge, tip, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
    val tl = Offset(tip.x - w / 2f, tip.y - h / 2f)
    drawRoundRect(MineYellow, topLeft = tl, size = Size(w, h), cornerRadius = CornerRadius(3.dp.toPx()))
    drawText(lab, topLeft = Offset(tip.x - lab.size.width / 2f, tip.y - lab.size.height / 2f))
}

/** 탭한 열차의 툴팁 — 열번 · 다음역 · 행선 · 내/외선. */
private fun DrawScope.drawTip(tm: TextMeasurer, c: Offset, t: MainTrainMark, mine: Boolean) {
    val line1 = t.trainNo + "  " + (if (t.inner) "내선" else "외선") + (if (mine) "  · 내 열차" else "")
    val line2 = t.statusText + " · " + t.destName + "행"
    val l1 = tm.measure(line1, TextStyle(fontSize = 13.sp,
        fontWeight = FontWeight.ExtraBold, color = if (mine) MineYellow else Color.White))
    val l2 = tm.measure(line2, TextStyle(fontSize = 12.sp, color = Color(0xFFCFE3F5)))
    val w = maxOf(l1.size.width, l2.size.width) + 22.dp.toPx()
    val h = l1.size.height + l2.size.height + 15.dp.toPx()
    val x = (c.x - w / 2f).coerceIn(0f, (size.width - w).coerceAtLeast(0f))
    val y = (c.y - h - 16.dp.toPx()).coerceAtLeast(0f)
    drawRoundRect(Color(0xFF0A1E33), topLeft = Offset(x, y), size = Size(w, h),
        cornerRadius = CornerRadius(10.dp.toPx()))
    drawRoundRect(BadgeSky.copy(alpha = 0.55f), topLeft = Offset(x, y), size = Size(w, h),
        cornerRadius = CornerRadius(10.dp.toPx()), style = Stroke(width = 1.2.dp.toPx()))
    drawText(l1, topLeft = Offset(x + 11.dp.toPx(), y + 6.dp.toPx()))
    drawText(l2, topLeft = Offset(x + 11.dp.toPx(), y + 6.dp.toPx() + l1.size.height))
}

/** 진행 방향 화살촉. [t] 는 진행 방향 단위벡터. */
private fun DrawScope.drawChevron(p: Offset, t: Offset, color: Color, h: Float) {
    val tip = Offset(p.x + t.x * h, p.y + t.y * h)
    val nx = -t.y
    val ny = t.x
    val back = Offset(p.x - t.x * h * 0.6f, p.y - t.y * h * 0.6f)
    val l = Offset(back.x + nx * h * 0.8f, back.y + ny * h * 0.8f)
    val r = Offset(back.x - nx * h * 0.8f, back.y - ny * h * 0.8f)
    drawPath(Path().apply { moveTo(l.x, l.y); lineTo(tip.x, tip.y); lineTo(r.x, r.y) },
        color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
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
