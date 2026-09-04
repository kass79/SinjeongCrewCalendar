package com.sinjeong.crewcalendar.presentation.live

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.text.style.TextOverflow
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
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
 *
 * ## 셋째 값 [side] — 가로(기기 회전)에서 창이 어긋나던 원인 (v1.6.88)
 *
 * 이 다이얼로그는 **화면 전체 크기로 재어 놓고**(Compose 가 받은 상자 = 2399x1079)
 * 창틀은 노치를 피한 자리에 놓인다. 에뮬 가로 실측:
 *
 * ```
 * act=2400x1080  actSys=136,74,0,63  actCut=136,0,0,0
 * loc=136,74  vis=Rect(136,74 - 2400,1017)  view=2399x1079
 * ```
 *
 * 즉 창은 `x=136`(노치 폭)부터 그려지는데 내용은 2399 폭으로 그려져 **오른쪽 135px 가 화면
 * 밖으로 나갔다**(오른쪽 차선 역명·닫기 X). 왼쪽 136px 는 창이 아예 없어 뒤의 달력이 비쳤다.
 * 세로가 멀쩡했던 건 같은 어긋남이 **위쪽**에 생기고, 위 [top] 여백이 그걸 그대로 상쇄해
 * 줬기 때문이다(우연이 아니라 이 함수가 그러라고 있는 것).
 *
 * 그래서 가로에서는 **가로로 깎인 만큼**(`왼쪽 + 오른쪽` 시스템 여백 = 노치·측면 내비) 을
 * 내용 오른쪽에 padding 으로 돌려준다. 세로 경로는 한 글자도 건드리지 않는다.
 */
private fun decorInsets(ctx: Context, dens: Density): Triple<Dp, Dp, Dp> {
    var c: Context? = ctx
    while (c is ContextWrapper && c !is Activity) c = c.baseContext
    (c as? Activity)?.window?.decorView?.let { dv ->
        val f = dv.rootWindowInsets
        val top = f?.systemWindowInsetTop ?: 0
        val bottom = f?.systemWindowInsetBottom ?: 0
        // 가로로 깎인 총량. 노치가 왼쪽이든(회전 90) 오른쪽이든(270) 창 폭이 그만큼 줄고,
        // 창은 왼쪽 여백만큼 밀려 시작한다 — 그래서 **합만 알면** 오른쪽에 돌려주면 맞는다.
        val side = (f?.systemWindowInsetLeft ?: 0) + (f?.systemWindowInsetRight ?: 0)
        // ⚠ 제스처 네비게이션은 **덮어쓰는 바**라 `systemWindowInsetBottom` 이 0 으로 온다
        // (실측: 위는 136px 로 제대로 왔는데 아래가 0 이라 오른쪽 차선이 계속 물렸다).
        // 그래서 아래는 최소값을 깔아 준다 — 여기가 보정 손잡이다.
        if (top > 0) return with(dens) {
            Triple(top.toDp(), bottom.toDp().coerceAtLeast(44.dp), side.toDp())
        }
    }
    return Triple(54.dp, 44.dp, 0.dp)
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

/**
 * **신도림·성수** — 사용자(기관사) 확정: *"신도림역, 성수역이 중요하니까 조금 더 크게 다른
 * 색상으로"*. 둘 다 지선이 갈라지는 역이라 승무원에게 기준점이다.
 *
 * 주황이다 — 내 열차 노랑([MineYellow])·열차 서 있는 역 빨강([StationRed])과 안 헷갈린다.
 */
private val KeyOrange = Color(0xFFFFB74D)
private val KEY_STATIONS = setOf("신도림", "성수")
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
    /** 가로에서 창이 노치만큼 밀려 잘리는 몫 — [decorInsets] KDoc 의 실측을 보라. */
    val safeSide = sysIns.third

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
                // 가로는 창이 노치만큼 밀려 시작하므로 **오른쪽으로 그만큼 좁힌다**
                // (v1.6.88 — 안 하면 오른쪽 차선 역명과 닫기 X 가 화면 밖으로 나간다).
                val inset =
                    if (portrait) PaddingValues(start = safeTop, end = safeBottom)
                    else PaddingValues(top = safeTop, bottom = safeBottom, end = safeSide)

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
    // ⚠ 자산이 1.4MB 다 — 읽기·파싱을 **본 스레드에서 하면 안 된다**(다이얼로그가 뜨는 순간
    // 그대로 멎는다). 다 읽기 전에는 null 이라 지연·다음 역 토막만 잠깐 안 보인다.
    val tt by produceState<Line2Timetable?>(null) {
        value = withContext(Dispatchers.IO) { Line2TimetableLoader.get(ctx) }
    }
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
                // ── 열차 이동 (v1.6.88) ────────────────────────────────
                // 열번마다 [Animatable] 하나. 새 스냅샷이 오면 **1초 tween** 으로 그 자리까지
                // 옮기고, 다음 스냅샷이 올 때까지 시간표의 **역간 소요시간** 속도로 다음 역
                // **95% 지점까지만** 기어간다. 절대 다음 역을 넘지 않는다 — 넘으면 있지도 않은
                // 도착을 그리게 된다. 시간표에 없는 열번이면 120초로 본다.
                //
                // ⚠ 장부는 **거른 목록이 아니라 전체 목록**으로 돌린다. 필터를 껐다 켤 때마다
                // 애니메이션이 버려지면 열차가 제자리에서 다시 튄다.
                // ⚠ `LaunchedEffect(trains)` 는 리스트가 **값으로 같으면 다시 안 돈다** — 폴링
                // 눈금(2초)마다 스냅샷을 갈아 끼워도 내용이 같으면 기어가던 것이 안 끊긴다.
                val anims = remember { mutableMapOf<String, Animatable<Float, AnimationVector1D>>() }
                LaunchedEffect(trains) {
                    // 사라진 열차(운행 종료·오류로 빈 스냅샷)는 장부에서도 지운다
                    anims.keys.retainAll(trains.map { it.trainNo }.toSet())
                    trains.forEach { t ->
                        val target = t.stationIdx + t.offset
                        val a = anims.getOrPut(t.trainNo) { Animatable(target) }
                        // 순환선이라 42.9 → 0.2 는 **뒤로 42.7 이 아니라 앞으로 0.3** 이다.
                        // 반 바퀴를 넘는 차이는 ±43 을 더해 "펼친" 좌표로 옮기고, 그릴 때 접는다.
                        val diff = target - a.value
                        val goal = when {
                            diff > LOOP_N / 2f -> target - LOOP_N
                            diff < -LOOP_N / 2f -> target + LOOP_N
                            else -> target
                        }
                        launch {
                            a.animateTo(goal, tween(1000, easing = LinearEasing))
                            val seg = tt?.segmentSeconds(
                                weekTag, Line2Timetable.inoutOf(t.inner), t.trainNo, t.statnNm,
                            ) ?: 120
                            val dir = if (t.inner) 1f else -1f
                            val nextStop = if (t.inner) floor(goal) + 1f else ceil(goal) - 1f
                            val creepTo = nextStop - dir * 0.05f
                            val remain = abs(creepTo - a.value)
                            if (remain > 0.01f) a.animateTo(
                                creepTo,
                                tween(
                                    (seg * 1000 * remain).toInt().coerceIn(1000, 240_000),
                                    easing = LinearEasing,
                                ),
                            )
                        }
                    }
                }
                val placed = drawn.map { t ->
                    val a = anims.getOrPut(t.trainNo) { Animatable(t.stationIdx + t.offset) }
                    t to (((a.value % LOOP_N) + LOOP_N) % LOOP_N)
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
 *
 * v1.6.88 에서 헤더·상태바가 **한 줄**이 되며 넷 → 둘로 줄였다. 넘쳐서 `Ellipsis` 가 걸리면
 * 뒤의 `외 N개` 부터 잘려 **몇 대인지도 모르게** 되기 때문이다.
 */
private fun shortNos(nos: List<String>): String =
    nos.take(2).joinToString("·") + if (nos.size > 2) " 외 " + (nos.size - 2) + "개" else ""

/**
 * 헤더 한 줄의 **앞 토막** — 잘리면 안 되는 것들. `null` = 후보 열번조차 없다.
 *
 * 열번·방향·지연·다음 역을 앞에 둔다(사용자 요청: *"노선 공간을 조금 더"* → 헤더가 한 줄이
 * 되면서 넘칠 수 있는데, 잘려도 되는 건 **현재 역·상태**뿐이다 — 그건 지도에 빨간 점으로도
 * 나온다).
 *
 * [delay]·[nextSec] 는 [Line2Timetable] 이 준 값이고 **시간표에 열번이 없으면 null** 이라
 * 그 두 토막만 조용히 빠진다(없는 값을 지어내지 않는다 — [MyTrain] KDoc 과 같은 규칙).
 */
private fun mineHead(
    mineMark: MainTrainMark?, candidates: List<String>, delay: Int?, nextSec: Int?,
): String? = when {
    mineMark != null ->
        "내 열차 " + mineMark.trainNo + " · " + (if (mineMark.inner) "내선" else "외선") +
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
    candidates.isNotEmpty() -> "내 열차 미검출(운행 전/후)"
    else -> null
}

/** 헤더 한 줄의 **뒤 토막** — 자리가 모자라면 여기부터 `Ellipsis` 로 잘린다. */
private fun mineTail(mineMark: MainTrainMark?, candidates: List<String>): String = when {
    // ⚠ 도착 예정 **시각은 만들지 않는다** — 그 데이터가 앱에 없다(MyTrain KDoc).
    // API 가 준 역명(statnNm)과 상태(trainSttus)만 그대로 옮긴다.
    mineMark != null -> mineMark.statusText
    candidates.isNotEmpty() -> "오늘 열번 " + shortNos(candidates)
    else -> ""
}

/**
 * 헤더 **한 줄** (v1.6.88) — `2호선 실시간 · 09/04(금) 10:08:57 · 내 열차 2039 · 외선 ·
 * +2분 지연 · 다음 역 3분 후 · 동대문역사문화공원 진입`, 오른쪽 끝에 닫기 X.
 *
 * 사용자(기관사) 요청: *"위쪽 헤더는 1줄로, 텍스트 크기를 한 단계씩만 더 줄여서 노선 공간을
 * 조금 더 확보"*. 두 줄(제목+큰 시계 / 내 열차)을 한 줄로 접고 글자를 2sp 씩 내렸다.
 *
 * ⚠ 줄 높이는 이제 **닫기 단추가 정한다** — 기본 [IconButton] 은 48dp 라 글자를 줄여도
 * 높이가 안 줄었다. 36dp 로 묶어 실제로 12dp 를 지도에 돌려준다.
 * ⚠ 뒤 토막에만 [weight] 를 준다. `weight` 는 무게 없는 형제들을 **먼저** 재고 남은 자리를
 * 주므로, 이렇게 두면 앞 토막과 닫기 X 가 자리를 먼저 가져가고 넘치는 건 뒤 토막뿐이다.
 */
@Composable
private fun CabHeader(
    nowMillis: Long, mineMark: MainTrainMark?, candidates: List<String>,
    delay: Int?, nextSec: Int?, big: Boolean, onDismiss: () -> Unit,
) {
    val t = remember(nowMillis / 1_000) {
        LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), ZoneId.systemDefault())
    }
    val sp = (if (big) 12f else 9.5f).sp
    val mineColor = if (mineMark != null) MineYellow else Dim
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "2호선 실시간 · ",
            fontSize = sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1,
        )
        Text(
            "%02d/%02d(%s) ".format(t.monthValue, t.dayOfMonth, WEEKDAYS[t.dayOfWeek.value - 1]),
            fontSize = sp, color = Dim, maxLines = 1,
        )
        // 시계는 노란색 유지(사용자 확정) — 한 줄에서 눈에 걸리라고 2sp 만 크게.
        Text(
            "%02d:%02d:%02d".format(t.hour, t.minute, t.second),
            fontSize = (if (big) 14f else 11.5f).sp, fontWeight = FontWeight.Bold,
            color = MineYellow, maxLines = 1,
        )
        mineHead(mineMark, candidates, delay, nextSec)?.let {
            Text(
                " · $it",
                fontSize = sp, fontWeight = FontWeight.Bold, color = mineColor,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        val tail = mineTail(mineMark, candidates)
        Text(
            if (tail.isEmpty()) "" else " · $tail",
            fontSize = sp, fontWeight = FontWeight.Bold, color = mineColor,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Close, "닫기", Modifier.size(20.dp), tint = Color.White)
        }
    }
}

/* ────────────────────────── 하단 상태바 ────────────────────────── */

/**
 * 사진의 버튼 줄 — 알약 칩. ⚠ **편성번호·전방/후방은 데이터가 없어 넣지 않는다.**
 *
 * v1.6.88 에서 **한 줄**로 못 박았다(사용자: *"아래쪽 헤더도 1줄"*):
 * `[2호선] [내 열번 2039 / 오늘 열번 …] ─── [내선][외선][전체]`.
 * 지운 것 — `내 열차 미검출`(헤더 줄과 같은 말) · `행선`(지도의 노란 깃발) ·
 * `↻ 내선`(바로 옆 필터 칩이 이미 켜져 있다). 셋 다 **다른 데서 이미 말하고 있다.**
 */
@Composable
private fun CabStatusBar(
    mine: MyTrain?, mineMark: MainTrainMark?, candidates: List<String>,
    error: String?, hasTrains: Boolean, big: Boolean,
    filter: DirFilter, onFilter: (DirFilter) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Chip("2호선", big, LoopGreen)
        // ⚠ 정보 칩에 [weight] 를 준다. 없으면 줄이 화면보다 길어져 **오른쪽 필터 칩이
        // 잘린다**(실측: 폴드 펼침 700dp 에서 `전체` 가 통째로 안 보였다). 잘린 정보는
        // 헤더 줄에 그대로 다시 나오지만, 못 누르는 단추는 없는 단추다.
        val shrink = Modifier.weight(1f, fill = false)
        when {
            // ⚠ 오류는 **열차가 하나도 없을 때만** 말한다. 스냅샷의 `error` 는 지선 카드가 쓰는
            // 양천구청 **도착** API 실패까지 합쳐 온 값이라(위치 API 는 멀쩡할 수 있다), 그대로
            // 띄우면 열차 14대를 그려 놓고 "정보를 못 받았어요"라고 말하게 된다(v1.6.84 실측).
            error != null && !hasTrains -> Chip(error, big, Color(0xFFE9A23B), modifier = shrink)
            mineMark != null -> Chip("내 열번 " + mineMark.trainNo, big, MineYellow, fill = true)
            // 사업 시각을 아는 본선 근무면 언제 나가는지까지 말해 준다.
            mine != null && !mine.riding && mine.startAt != null && candidates.isNotEmpty() ->
                Chip(
                    "다음 " + mine.nos.first() + " " + fmt(mine.startAt) +
                        (if (mine.nextDay) " (익일)" else ""),
                    big, Color(0xFFCFE3F5), modifier = shrink,
                )
            candidates.isNotEmpty() ->
                Chip("오늘 열번 " + shortNos(candidates), big, Color(0xFFCFE3F5), modifier = shrink)
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
    text: String, big: Boolean, tint: Color, fill: Boolean = false,
    modifier: Modifier = Modifier, onClick: (() -> Unit)? = null,
) = Surface(
    modifier = modifier,
    color = if (fill) tint else tint.copy(alpha = 0.12f),
    shape = RoundedCornerShape(50),
    border = BorderStroke(1.dp, tint.copy(alpha = 0.85f)),
) {
    Text(
        text,
        // v1.6.88 한 단계 축소(14→12.5 / 11.5→10) — 상태바 한 줄에 필터까지 다 들어가야 한다.
        fontSize = if (big) 12.5.sp else 10.sp, fontWeight = FontWeight.Bold,
        color = if (fill) MineInk else tint,
        maxLines = 1, overflow = TextOverflow.Ellipsis,
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

    // **신도림·성수 먼저** → 윗변·아랫변(대각선) → 좌·우변(가로).
    // 먼저 놓는 쪽이 자리를 지킨다 — 두 중요 역은 아무 라벨에도 안 밀리고, 나머지가 피해 간다.
    // (배지에는 여전히 비켜선다. 배지는 이미 자리가 정해진 장애물이라 그 위에 겹쳐 놓으면
    //  주황 이름이 배지 밑에 깔린다.)
    // 그다음이 대각선인 건 밀려나는 쪽이 소수가 되게 하는 종전 순서 그대로다.
    val order = (0 until LOOP_N).sortedBy { k ->
        when {
            Line2Stations.MAIN[(k + start) % LOOP_N] in KEY_STATIONS -> 0
            k < TOP_N || k in (TOP_N + RIGHT_N) until (TOP_N + RIGHT_N + BOTTOM_N) -> 1
            else -> 2
        }
    }

    for (k in order) {
        val name = Line2Stations.MAIN[(k + start) % LOOP_N]
        val red = k in occupied
        val key = name in KEY_STATIONS
        val layout = tm.measure(name, TextStyle(
            // 중요 역만 +2sp·굵게·주황(사용자 확정). 열차가 서 있어도 주황이 이긴다 —
            // 빨간 점이 이미 그 말을 하고 있다.
            fontSize = (if (key) sizeSp + 2f else sizeSp).sp,
            fontWeight = if (key || red) FontWeight.ExtraBold else FontWeight.Medium,
            color = when {
                key -> KeyOrange
                red -> Color(0xFFFFD9D4)
                else -> Color.White
            }))
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
        // 신도림·성수는 지름 1.5배 + 흰 테두리 — 이름과 같은 주황이라 멀리서도 짚인다.
        val key = Line2Stations.MAIN[(k + start) % LOOP_N] in KEY_STATIONS
        val rad = (if (big) 5f else 4f).dp.toPx() * (if (key) 1.5f else 1f)
        drawCircle(if (red) StationRed else if (key) KeyOrange else StationWhite, rad, p)
        if (key || red) drawCircle(
            if (key) Color.White else Color.White.copy(alpha = 0.55f), rad, p,
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
    // ⚠ 배지 상자 크기는 **가장 넓은 열번("0000")으로** 잰다 — 실제 열번마다 재면 상자가
    // 들쭉날쭉해져 겹침 판정과 그린 결과가 어긋난다.
    val bw = tm.measure("0000", TextStyle(fontSize = badgeSp.sp,
        fontWeight = FontWeight.ExtraBold))
    val bhw = (bw.size.width + (if (big) 18 else 15).dp.toPx()) / 2f
    val bhh = (bw.size.height + (if (big) 12 else 10).dp.toPx()) / 2f

    /*
     * ── 배지끼리 겹침 0 (v1.6.88) ────────────────────────────
     * 같은 차선에 열차가 몰리면 배지가 그대로 포개졌다(실측: 합정 모서리에 7366·8401·2403,
     * 사당 근처에 6378·2384·2382). 겹치면 **자기 차선 바깥쪽**(내선은 더 안쪽, 외선은 더
     * 바깥쪽 — 반대편 차선을 절대 침범하지 않는다)으로 `배지 높이 + 2dp` 씩 **최대 2단**
     * 계단으로 민다. 그래도 자리가 없거나 계단이 화면 밖으로 나가면 **배지를 접고 점만
     * 남긴다** — 겹쳐 놓아 둘 다 못 읽게 하느니 하나만 읽히는 편이 낫다.
     *
     * 내 열차는 **맨 먼저** 자리를 잡아(빈 판이라 늘 0단) 밀리지도 숨지도 않는다. 그리기는
     * 여전히 맨 나중이라 무엇 위에도 얹힌다.
     * 필터(내선/외선)를 켜면 한 차선만 쓰니 계단이 거의 안 생긴다 — 전체 모드용 장치다.
     */
    val stepPx = 2f * bhh + 2.dp.toPx()
    val badgeRects = mutableListOf<Rect>()
    /** 그릴 배지 — (열차, 중심, 바깥 방향). 접힌 배지는 여기 없다. */
    val spots = mutableListOf<Triple<MainTrainMark, Offset, Offset>>()
    /** 탭 판정·툴팁이 쓸 중심 — 접힌 배지도 점 자리로 남는다. */
    val centers = mutableMapOf<String, Offset>()
    for ((t, pos) in trains.sortedByDescending { it.first.trainNo == mineNo }) {
        val (base, out) = spot(t, pos)
        centers[t.trainNo] = base
        for (s in 0..2) {
            val c = Offset(base.x + out.x * stepPx * s, base.y + out.y * stepPx * s)
            val r = Rect(c.x - bhw, c.y - bhh, c.x + bhw, c.y + bhh)
            // 밀어낸 배지가 화면 밖으로 나가면 그 단은 없는 셈 친다(0단은 [margin] 이 챙긴다).
            if (s > 0 && (r.left < 0f || r.top < 0f ||
                    r.right > size.width || r.bottom > size.height)) continue
            if (badgeRects.any { it.overlaps(r) }) continue
            badgeRects += r
            spots += Triple(t, c, out)
            centers[t.trainNo] = c
            break
        }
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
    // 배지를 접은 열차도 **점은 남으므로** 탭 판정은 전원을 넣는다.
    val hits = trains.map { (t, _) -> centers.getValue(t.trainNo) to t.trainNo }
    // ⚠ **내 열차는 맨 나중에** 그린다 — 다른 배지에 가리면 "표시가 안 된다"는 말이 된다.
    val (mineRows, others) = spots.partition { it.first.trainNo == mineNo }
    others.forEach { (t, c, _) -> drawBadge(tm, c, t.trainNo, false, big, pulse, badgeSp) }
    mineRows.forEach { (t, c, out) ->
        drawBadge(tm, c, t.trainNo, true, big, pulse, badgeSp)
        drawFlag(tm, c, out, t.destName, big)   // 배지 위 노란 행선 깃발
    }

    // ── 탭한 열차의 툴팁 (열차보다 나중에 그려 위에 얹힌다) ──
    picked?.let { no -> centers[no]?.let { c ->
        trains.firstOrNull { it.first.trainNo == no }?.let { (t, _) ->
            drawTip(tm, c, t, no == mineNo)
        }
    } }
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
