package com.sinjeong.crewcalendar.presentation.live

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import com.sinjeong.crewcalendar.R
import com.sinjeong.crewcalendar.domain.model.DEFAULT_SEG_SEC
import com.sinjeong.crewcalendar.domain.model.DutyCode
import com.sinjeong.crewcalendar.domain.model.Line2Stations
import com.sinjeong.crewcalendar.domain.model.Line2Timetable
import com.sinjeong.crewcalendar.domain.model.LiveRef
import com.sinjeong.crewcalendar.domain.model.MyTrain
import com.sinjeong.crewcalendar.domain.model.TrainMotion
import com.sinjeong.crewcalendar.domain.model.dutyTrainNumbers
import com.sinjeong.crewcalendar.domain.model.myDestination
import com.sinjeong.crewcalendar.domain.model.myTrainAt
import com.sinjeong.crewcalendar.domain.model.pickRun
import com.sinjeong.crewcalendar.domain.model.pruneMotions
import com.sinjeong.crewcalendar.domain.model.sameRun
import com.sinjeong.crewcalendar.domain.model.stepMotion
import com.sinjeong.crewcalendar.presentation.theme.MapStyle
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
import kotlin.math.cos
import kotlin.math.ceil
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
 *  5. **열차와 역 이름은 늘 선로의 반대편**(v1.6.98 — 사용자가 준 외선 보조설비 사진 그대로):
 *     **가로 변은 열차가 선로 위·역 이름이 아래**(윗변이면 열차가 루프 밖, 아랫변이면 루프 안),
 *     **세로 변은 열차가 루프 바깥·역 이름이 안쪽**. 모서리 호는 가까운 변의 규칙을 따른다.
 *     **차선은 한 줄뿐이다** — 내선도 외선도 바퀴를 선로에 붙이고 서고, 방향은 자리가 아니라
 *     **기관차 머리**([headingFor])가 말한다. 겹칠 때만 선로에서 멀어지는 쪽으로 계단을
 *     오르고, 오른 열차는 발밑에 **받침선**(짧은 초록 선분)을 깔아 뜨지 않게 한다.
 *     (그림은 v1.6.91 부터 전부 증기기관차 — 하늘색 몸통, 내 열차만 노랑. 네모 배지는 없다.)
 *     **바퀴는 늘 선로 쪽**이라 가로 변에서는 몸통이 늘 바로 선다(v1.6.96 — [locoFlip]).
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

/*
 * ⚠ 색 상수는 v1.7.0 에서 **[MapPalette] 한 곳**으로 옮겼다(`MapStyle.kt`) — 설정에서 고른
 * 스타일(운전실 남색 / 클레이)에 따라 한 벌씩 통째로 갈아 끼운다. `CAB_PALETTE` 값은
 * v1.6.99 의 상수와 **같다**(`MapStyleTest` 가 잠근다).
 */

/**
 * **신도림·성수** — 사용자(기관사) 확정: *"신도림역, 성수역이 중요하니까 조금 더 크게 다른
 * 색상으로"*. 둘 다 지선이 갈라지는 역이라 승무원에게 기준점이다.
 *
 * 색은 팔레트가 정한다: 남색 스타일은 **둘 다 주황**(내 열차 노랑·정차 빨강과 안 헷갈린다),
 * 클레이는 **신도림 초록·성수 빨강**이다([MapPalette.keyInk]).
 */
private val KEY_STATIONS = setOf("신도림", "성수")

/**
 * **운전취급역** 6곳 — 사용자(기관사) 확정(v1.6.98):
 * *"신도림, 성수빼고 역텍스트 색깔은 동일하게 해! 그리고 서울대입구,교대,삼성,종합운동장,
 * 을지로입구,홍대입구역은 운전취급역이니까 약간 다른 색깔로 해줘!"*
 *
 * 크기는 **보통 역과 같다** — 색만 다르다([MapPalette.op]). 연한 하늘색(클레이는 파랑)이라
 * 보통 역과는 갈리되 신도림·성수와는 안 헷갈린다.
 */
private val OP_STATIONS =
    setOf("서울대입구", "교대", "삼성", "종합운동장", "을지로입구", "홍대입구")

/**
 * **보통 역 이름**이 기준 크기에서 내려가는 한 단계(v1.6.96 사용자 요청 — *"역 텍스트 너무 큰거
 * 아니가? … 쫌 쭐여도 되는데?"*). 이 앱이 쓰는 "한 단계" = 1.5sp 다.
 *
 * ⚠ **[KEY_STATIONS] 에는 안 먹인다** — 신도림·성수는 종전 크기·주황 그대로가 사용자 확정이다.
 * 폴드 펼침의 큰 글자(`big`)에도 같은 폭으로 붙어 비율이 유지된다.
 *
 * ## 몇 단을 내리는지 — v1.6.97 에서 한 단 더
 *
 * 사용자: *"역 이름 한단계 더 축소해도 될듯? 그리고 긴 5자 넘어가는 긴 역사는 더 축소해도 되고
 * 신도림, 성수 빼고."* 그래서 **보통 역 2단([LABEL_DROP]) · 긴 역 3단([LABEL_DROP_LONG])** 이다.
 */
private const val LABEL_STEP = 1.5f

/** 보통 역 이름이 내려가는 단수(v1.6.97 — v1.6.96 의 1단에서 한 단 더). */
private const val LABEL_DROP = 2

/** [LONG_NAME_LEN] 이상 긴 역 이름이 내려가는 단수 — 보통 역보다 한 단 더(v1.6.97). */
private const val LABEL_DROP_LONG = 3

/**
 * **긴 역 이름** 잣대 — 글자 수로만 판정한다(사용자 원문 *"긴 5자 넘어가는 긴 역사"*).
 *
 * 본선 43역 중 여기 걸리는 건 8개다: `을지로입구`·`을지로3가`·`을지로4가`·
 * `동대문역사문화공원`·`종합운동장`·`서울대입구`·`구로디지털단지`·`영등포구청`.
 * 이들이 루프 안쪽으로 가장 깊이 파고들어 서로 포개던 이름들이라 여기만 한 단 더 내려도
 * 겹침이 눈에 띄게 준다. **[KEY_STATIONS] 은 글자 수와 무관하게 예외**다(둘 다 2자라 애초에
 * 안 걸리지만, 규칙 순서상 `key` 를 먼저 본다).
 */
private const val LONG_NAME_LEN = 5
private const val TAG = "BranchLive"

/**
 * **클레이 그림자 밀도 가드**(v1.7.0) — 그리는 열차가 이 수를 넘으면 남의 열차 그림자는
 * **한 겹**만 찍는다(내 열차는 늘 두 겹이라 그래도 도드라진다).
 *
 * 전체 보기는 20~30대가 뜨는데 두 겹이면 그림자만 60장이다 — 겹쳐서 뭉개지기도 하고
 * 프레임도 먹는다(시안 "구려질 자리" 첫째). 방향 필터를 켜면 대개 이 아래로 떨어진다.
 */
private const val CLAY_SHADOW_MAX = 15

/** 폴드 펼침 기관차 배수 — [margin] 과 [drawCabLoop] 이 **같은 값**을 봐야 한다. */
private fun locoScale(big: Boolean) = if (big) 54f / LOCO_LEN else 1f

/**
 * 열차 중심이 **제 선로**에서 물러나는 거리 — 바퀴가 그 선로에 닿는 한 자리다.
 *
 * ⚠ **한 선로에 차선은 하나뿐이다**(v1.6.98). 같은 선로에서 반대 방향을 한 차선 밖에 세워
 * 봤더니 그 열차들이 **선로에서 떠 보였다**(사용자: *"떠다니는데? 아니지?"*). 겹칠 때만
 * 계단으로 피하고, 계단으로 올라간 열차는 발밑에 **받침선**이 깔린다.
 *
 * v1.7.4 부터 **전체 보기는 선로가 두 줄**이라 내선·외선이 각자 제 선로 위에 선다
 * ([laneGap]) — 차선을 늘린 것이 아니라 **선로를 늘린 것**이다. 단독 보기는 종전대로 한 줄.
 */
private fun badgeOff(big: Boolean) = (if (big) 18 else 14).dp

/** 선로 굵기 — [laneGap] 과 [drawCabLoop] 이 **같은 값**을 봐야 한다. */
private fun railW(big: Boolean) = (if (big) 9f else 7.5f).dp

/** 모서리 반지름의 기준값 — 복선은 이 값을 가운데 두고 두 선로가 [laneGap] 만큼 벌어진다. */
private fun baseRadius(big: Boolean) = (if (big) 40 else 28).dp

/** 다른 열차 기관차 배수 — 전체 보기(복선) / 방향 필터. 자세히는 [CabScreen] `otherK`. */
private const val OTHER_K_ALL = 0.7f
private const val OTHER_K_DIR = 0.82f

/**
 * **전체 보기 복선의 두 선로 사이 간격**(v1.7.4) — 바깥 = 외선 · 안쪽 = 내선.
 *
 * 사용자 확정: *"외선은 노선 바깥 내선은 노선 안쪽으로 다니게 하면 어떨까?"* 두 선로 다
 * 같은 색·같은 굵기다(지선 카드처럼 한쪽을 연하게 하지 **않는다** — 본선은 두 방향이 대등하다).
 *
 * ## 왜 계산값인가 — 이 틈에 **열차 한 대가 선다**
 *
 * 가로 변에서 **윗변 내선**과 **아랫변 외선**은 제 선로(안쪽/바깥쪽) 위에 서는데, 그 자리가
 * 곧 **두 선로 사이**다. 그러니 틈은 기관차 한 대가 옆 선로를 안 밟고 들어갈 만큼이라야 한다:
 *
 * `차선 오프셋([badgeOff]) + 기관차 반높이 + 선로 반굵기 + 2dp`
 *
 * 기관차는 **타 열차(0.7배)** 기준이다(전체 보기의 남의 열차가 다 이 크기다 — [OTHER_K_ALL]).
 * 폰 `14 + 10.85 + 3.75 + 2 = 30.6dp` · 펼침 `18 + 12.74 + 4.5 + 2 = 37.2dp`.
 *
 * ## v1.7.5 — **모든 열차가 같은 크기**라 틈 하나로 다 잰다
 *
 * 사용자: *"본인열차 크기도 다른열차 크기랑 동일하게 해! 이미 색상을 다르게 했잖어!"* —
 * 내 열차도 남의 열차와 **같은 배율**(전체 0.7 · 단독 0.82)이다. 구분은 **노란 몸통 · 빨간
 * 열번 · 흰 테 · 연기 · 갈매기 · 행선판**뿐이다([drawCabLoop]).
 *
 * 그러면 틈은 **가장 큰 상자 하나**만 재면 된다 = 행선판을 단 내 열차([roofHalf]):
 * 외곽 2겹([LOCO_RING_H] 17.9dp — 회피 상자보다 2.4dp 크다) + 행선판([LOCO_BOARD_H] 17dp).
 *
 * 폰 `14 + 24.43 + 3.75 + 2 = 44.18dp` · 펼침 `18 + 28.68 + 4.5 + 2 = 53.18dp`.
 *
 * ⚠ v1.7.4 는 내 열차가 **1배**여서 행선판까지 재면 46.5dp 였고, 그러면 안쪽 루프가 역 이름을
 * 포갰다 — 그래서 판이 반대편 선로를 밟는 것을 *알고 둔 대가*로 뒀다. 이제 0.7 배라 같은
 * 계산이 **44.18dp** 로 내려와 그 대가를 안 치른다(실화면 `_미리보기_v1.7.5\AB03` 로 확인).
 */
private fun laneGap(big: Boolean): Dp =
    badgeOff(big) + roofHalf(big, OTHER_K_ALL).dp + railW(big) / 2f + 2.dp

/**
 * **지붕 쪽 반높이** — 가장 큰 상자(행선판 단 내 열차) 기준. 모든 열차가 같은 배율이므로
 * 이 함수 하나가 [laneGap](틈)과 [trainPad](여백)를 같이 재고, 배수 [k] 만 달리 받는다
 * (전체 [OTHER_K_ALL] · 단독 [OTHER_K_DIR]).
 */
private fun roofHalf(big: Boolean, k: Float): Float =
    (LOCO_RING_H + LOCO_BOARD_H) * locoScale(big) * k

/**
 * 열차 쪽이 선로에서 먹는 깊이 — **가로 변은 위, 세로 변은 루프 바깥**(v1.6.98 보조설비 배치).
 *
 * ## 왜 계산값인가 (v1.6.96 의 교훈)
 *
 * 눈대중 30dp 가 **내 열차를 선로 위로 밀어 올린** 적이 있다. 내 열차는 지붕 위에 행선판을
 * 얹어 지붕 쪽 반높이가 `15.5 + 17 = 32.5dp` 인데, 그걸 안 세고 정한 값이라 상자가 캔버스를
 * 넘었고 `coerceIn` 이 안쪽으로 밀어 초록 선을 밟았다. 사용자 확정 처방:
 * *"캔버스 밖으로 나가면 clamp 대신 여백을 늘려라."*
 *
 * 그래서 `차선 오프셋 + (지붕 + 행선판) × 배수` — **가장 큰 상자** 기준이다([roofHalf]).
 * 계단으로 올라간 열차가 캔버스를 넘는 것은 [drawCabLoop] 가 그 단을 건너뛰어 막는다.
 *
 * ⚠ v1.7.5 — 기준이 **내 열차 1배** 에서 **모두 같은 배율**로 바뀌면서 다시 쟀다. 두 화면 중
 * 기관차가 큰 쪽(단독 보기 [OTHER_K_DIR] 0.82)으로 잡는다 — 폰 46.5 → **42.62dp**,
 * 펼침 56.15 → **51.62dp** 로 그만큼이 루프에 돌아간다.
 */
private fun trainPad(big: Boolean) = badgeOff(big) + roofHalf(big, OTHER_K_DIR).dp

/**
 * **아래 변 역명**이 루프 밖으로 먹는 깊이(v1.6.98) — 가로 변은 역 이름이 늘 선로 **아래**라
 * 아랫변만 루프 바깥으로 나간다.
 *
 * `역명 간격(30dp) + [DIAG] 로 눕힌 가장 긴 이름의 세로 반높이`. 아랫변 16역 중 가장 긴
 * `구로디지털단지`(7자)를 기준으로 잰다 — 방향 필터 화면(폰 9.0sp / 펼침 11.5sp)이 제일 크다.
 * 실화면에서 잘리면 **여기만** 고치면 된다(보정 손잡이).
 */
private fun namePad(big: Boolean) = (if (big) 66 else 56).dp

// 열차가 서는 쪽(가로 변 = 위 / 세로 변 = 루프 밖)은 순수 함수 `mainTrainSide` 한 곳이 정한다.
// ⚠ 그 함수는 [Loco] 에 산다 — 이 파일은 최상위에 `Color(...)` 가 있어 **테스트 하네스에서
// 클래스 초기화가 터진다**(Compose 미포함). `headingFor` 가 거기 있는 것과 같은 사정이다.

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
 * 좌·우변 역명이 **한 칸 더 루프 안쪽**으로 물러나는 폭(v1.6.98 모서리 처방 — [layoutLabels]).
 * 가장 긴 세로변 이름(`영등포구청` ≈ 55dp)의 절반이 조금 넘어, 물러나면 종전 자리와 안 겹친다.
 */
private val SIDE_LANE2 = 34.dp

/**
 * 가로 변 라벨이 **변을 따라** 흘러도 되는 칸 수(v1.6.99 — [layoutLabels]). 한 칸은 11dp 이고
 * 실제 이동은 `push.x = ±0.7` 이 곱해진 값이라 **≈23dp** 다.
 *
 * 잣대는 **역 간격**이다: 폴드 펼침의 윗변이 ≈32dp, 폰이 ≈41dp 라 23dp 는 어느 쪽에서도
 * 이웃을 못 넘는다 = **역 순서가 안 뒤집힌다.** 종전 8칸(≈62dp)은 펼침에서 두 역을 건너뛰었다.
 */
private const val LATERAL_STEPS = 3f

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
 * @param style 설정 > 화면 > 지도 스타일(v1.7.0). 색만 정한다 — **배치는 스타일과 무관**하다.
 */
@Composable
internal fun MainLineMapDialog(
    duty: DutyCode?, date: LocalDate, style: MapStyle = MapStyle.CAB, onDismiss: () -> Unit,
) {
    val pal = paletteOf(style)
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

    /*
     * 헤더 ↻ — **캐시를 건너뛰고 즉시 1회**(v1.7.5 ⑤). 사용자: *"지금 신정지선에 에뮬레이터
     * 오른쪽 위에있는 리프레시 버튼 본선 전체보기에도 하나 만들어줘."*
     * 지선 카드와 **똑같이** [rememberCoroutineScope] 의 일회성 launch 다 — 컴포지션에 묶여
     * 있어 지도를 닫으면 같이 취소되고, 폴링 눈금은 그대로라 조회 수가 늘지 않는다.
     */
    val scope = rememberCoroutineScope()
    val refresh: () -> Unit = { scope.launch { snap = BranchLive.loadSnapshot(force = true) } }

    // 오늘 근무가 잡는 열번 **전부**가 후보다(본선·지선 무관 — 사용자 확정).
    val candidates = remember(duty, date) {
        duty?.let { dutyTrainNumbers(it, date) }.orEmpty()
    }
    // 사업 시각 문구("출고 전 …")는 본선 근무에서만 나온다 — 지선은 시각표가 없다.
    val mine = remember(duty, date, now / 60_000) {
        duty?.let { myTrainAt(it, date, LocalDateTime.now()) }
    }
    val shown = snap.mainTrains
    // 자산 1.4MB — 본 스레드에서 읽으면 다이얼로그가 멎는다. 로더는 캐시라 [CabScreen] 것과 한 벌이다.
    val ctxTt = LocalContext.current
    val ttPick by produceState<Line2Timetable?>(null) {
        value = withContext(Dispatchers.IO) { Line2TimetableLoader.get(ctxTt) }
    }
    /*
     * **후보(몸통) 하나에 라이브 하나** — [pickRun] 이 고른다(v1.7.3).
     *
     * ⚠ 견주는 잣대는 [sameRun] 이다(v1.7.2) — 행로표의 `2340` 이 API 에는 **`8340`** 으로
     * 뜬다. 글자 그대로 견주던 종전 코드는 사용자 실측에서 `44` 다이아를 통째로 놓쳤다.
     * 다만 뒤 세 자리만 보므로 `2372`·`8372` 가 동시에 살아 있으면 **둘 다** 맞다고 한다 —
     * v1.7.2 는 그래서 한 몸통에 노란 열차를 둘 세웠다. [pickRun] 이 정확일치 → 시간표 근접
     * (지연 ±15분) → 작은 접두 순으로 **하나만** 고른다.
     * **서로 다른 몸통**(전반 `340` · 후반 `042`)은 각각 골라 여전히 둘 다 노랑이다.
     * [mineRoute] 는 그때 맞은 **행로표 번호**(헤더가 괄호로 같이 보여 준다).
     */
    val minePairs = remember(candidates, shown, ttPick, now / 60_000) {
        val lives = shown.map { LiveRef(it.trainNo, it.destName, it.statnNm, it.inner) }
        val (day, sec) = Line2Timetable.serviceClock(LocalDateTime.now())
        val w = Line2Timetable.weekTagOf(day)
        val lookup = { cand: String, ref: LiveRef ->
            ttPick?.schedSecAt(w, Line2Timetable.inoutOf(ref.inner), cand, ref.station, sec, ref.dest)
        }
        candidates.mapNotNull { no ->
            pickRun(no, lives, sec, lookup)?.let { l -> no to shown.first { it.trainNo == l.trainNo } }
        }
    }
    val minePair = minePairs.firstOrNull()
    val mineMark = minePair?.second
    val mineRoute = minePair?.first
    // 내 열번이 **여럿 살아 있으면 전부 노랑**(헤더·칩은 첫 번째만). 야간처럼 전·후반 열번이
    // 같이 굴러가는 시간대엔 두 대가 동시에 뜬다 — 하나만 칠하면 나머지가 남의 열차로 보인다.
    val mineNos = remember(minePairs) { minePairs.mapTo(HashSet()) { it.second.trainNo } }
    /*
     * **내 열차 행선판 글자**(v1.7.5) — `API 열번 → "홍대입구행"`. 여기 없으면 **안 단다.**
     *
     * ⚠ 종전엔 API 행선(`destName`)을 그대로 달았는데, 그 필드는 타절·입고 열차에서 전부
     * `성수종착` 이라 사용자 실측(`8401`)에서 **홍대입구행에 `성수행` 을 달았다**. 이제
     * [myDestination] 이 행로표 주박·입고 표지 → 접두 → **없음** 순으로 정한다.
     */
    val mineBoards = remember(minePairs, duty, date) {
        duty?.let { d ->
            minePairs.mapNotNull { (_, m) ->
                myDestination(d, date, m.trainNo)?.let { m.trainNo to it }
            }
        }.orEmpty().toMap()
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
        /*
         * ── 상태바 아이콘 명암 (v1.7.0) ──────────────────────────────
         * 클레이는 **크림 바탕**이라 시계·배터리가 흰색이면 안 보인다. 다이얼로그는 제 창이
         * 따로 있으므로 **그 창의** 컨트롤러만 건드린다 — 액티비티 설정(`MainActivity`)은
         * 그대로고, 다이얼로그가 닫히면 창째로 사라져 되돌릴 것도 없다.
         * ⚠ **남색일 때는 한 줄도 안 건드린다** — v1.6.99 화면을 픽셀 단위로 지키기 위해서다.
         */
        if (pal.clay) {
            val dlgView = LocalView.current
            SideEffect {
                (dlgView.parent as? DialogWindowProvider)?.window?.let { w ->
                    WindowCompat.getInsetsController(w, dlgView)
                        .isAppearanceLightStatusBars = true
                }
            }
        }
        Surface(Modifier.fillMaxSize(), color = pal.bg) {
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
                        // ⚠ **회전각을 안으로 내려 준다**(v1.6.91). 캔버스는 자기가 돌아간
                        // 줄 모르므로, 열번·행선판 글자를 화면 기준으로 바로 세우려면
                        // 그리는 쪽이 이 값을 알아야 한다([locoTextDeg]).
                        CabScreen(ch, inset, now, shown, mine, mineMark, mineRoute, mineNos, mineBoards, candidates,
                            snap.error, picked, { picked = it }, eff, { filter = it },
                            userPicked = filter != null, onRefresh = refresh, onDismiss = onDismiss,
                            mapDeg = 90f, pal = pal)
                    }
                } else {
                    CabScreen(ch, inset, now, shown, mine, mineMark, mineRoute, mineNos, mineBoards, candidates,
                        snap.error, picked, { picked = it }, eff, { filter = it },
                        userPicked = filter != null, onRefresh = refresh, onDismiss = onDismiss,
                        mapDeg = 0f, pal = pal)
                }
            }
        }
    }
}

/**
 * 잰 높이는 그대로 두고 **부모에게만 [h] 라고 말한다**(v1.7.7 A5 — 헤더 ↻).
 *
 * M3 의 `minimumInteractiveComponentSize()` 는 터치 영역을 넓히려고 **레이아웃 크기**를
 * 48dp 로 올린다. 그러면 그 단추가 든 줄이 통째로 48dp 가 된다. 여기서는 자식을 제 크기로
 * 재되(터치 영역이 살아 있다) 신고 높이만 줄이고 **가운데 정렬로 얹어** 위아래로 고르게
 * 넘치게 한다 — Row 는 clip 하지 않으므로 넘친 자리도 그대로 눌린다.
 * 자식이 [h] 보다 작으면 아무것도 안 한다.
 */
private fun Modifier.shrinkHeight(h: Dp) = layout { measurable, constraints ->
    val p = measurable.measure(constraints.copy(minHeight = 0))
    val out = minOf(p.height, h.roundToPx())
    layout(p.width, out) { p.place(0, (out - p.height) / 2) }
}

/** 상단바 + 지도 + 하단 상태바. 세로 창에서는 통째로 90도 돌아간다. */
@Composable
private fun CabScreen(
    ch: Dp, inset: PaddingValues, nowMillis: Long,
    trains: List<MainTrainMark>, mine: MyTrain?, mineMark: MainTrainMark?,
    /** [mineMark] 를 맞힌 **행로표 번호** — API 번호와 다를 때만 헤더가 괄호로 보여 준다. */
    mineRoute: String?,
    /** 지금 살아 있는 **내 열번 전부**(API 번호). 전부 노란 기관차로 그린다. */
    mineNos: Set<String>,
    /**
     * 내 열차 **행선판 글자**(v1.7.5) — `API 열번 → "홍대입구행"`. **없으면 안 단다**
     * ([myDestination] — API 행선은 못 믿는다). 헤더도 같은 값을 말한다([mineHead]).
     */
    mineBoards: Map<String, String>,
    candidates: List<String>,
    error: String?, picked: String?, onPick: (String?) -> Unit,
    eff: DirFilter, onFilter: (DirFilter) -> Unit,
    /**
     * 사용자가 **내선/외선 칩을 직접 눌렀나**(v1.7.5 ⑥). 참이면 방향이 다른 내 열차도
     * 안 그린다. 거짓(기본 화면)이면 [eff] 가 내 열차 방향을 따라온 것이라 종전 그대로다.
     */
    userPicked: Boolean,
    /** 헤더 ↻ — 캐시를 건너뛰고 즉시 1회 조회(`loadSnapshot(force = true)`). */
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    /** 지도 전체 회전 — 세로 창 90f. 글자를 바로 세우는 데 쓴다([drawLoco] `mapDeg`). */
    mapDeg: Float,
    /** 색 한 벌 — 스타일이 정한다([MapPalette]). 배치 값은 여기서 하나도 안 온다. */
    pal: MapPalette,
) {
    // 폴드 펼침처럼 세로가 넉넉하면 글자를 키운다(사진처럼 시원하게).
    val big = ch >= 480.dp
    // 한 방향만 그리면 배지가 반으로 줄어드니 글자를 키운다. 겹침은 [layoutLabels] 가
    // **측정값으로** 판정하므로 이 두 값만 바꿔도 회피가 따라온다.
    val filtered = eff != DirFilter.ALL
    val labelSp = if (filtered) (if (big) 16f else 13.5f) else (if (big) 14f else 11.5f)
    /*
     * 다른 열차 기관차 크기 배수(v1.6.91). 종전 `badgeSp` 와 **같은 이유로** 필터를 켜면 커진다 —
     * 한 차선만 그리니 자리가 남는다.
     *
     * ⚠ 전체(0.8)는 눈대중이 아니었다. 0.8 배 기관차 상자는 48×31 → **38×25dp** 로, v1.6.90 의
     * 하늘색 배지(≈39×23dp)와 거의 같다 — 그래서 20대가 넘게 떠도 혼잡도가 안 늘어난다.
     * 1.0 그대로 두면 상자가 폭 23%·높이 35% 커져 역 이름이 밀린다.
     *
     * ## v1.6.97 — 한 단(0.1) 더 작게
     *
     * 사용자: *"열차 아이콘도 본인 열차빼고 한단계 더 축소해야 최적화 될꺼같은데?"* 그래서
     * 전체 **0.8 → 0.7**, 방향 필터 **0.92 → 0.82** 이다(같은 폭으로 내려 두 화면의 비율을 지킨다).
     *
     * ⚠ **하한은 열번 판독**이다. 열번은 `11sp × scale`([drawLoco]) 이라 0.7 배에서 **7.7sp** —
     * 4자리 ExtraBold 가 몸통 열번 띠(폭 −23…16dp × 0.7 ≈ 27dp) 안에 그대로 든다(실측 확인).
     * 더 내리면 글자가 몸통을 넘거나 안 읽힌다 — **0.7 밑으로는 가지 말 것.**
     * ⚠ v1.7.5 — **내 열차도 이 값이다**(사용자 확정 *"본인열차 크기도 다른열차 크기랑 동일하게"*).
     *   종전엔 내 열차만 [locoScale](1배)이었다. 구분은 색·테·연기·행선판이 한다.
     * ⚠ 지선 카드(`LineMap.kt`)의 `UP_LOCO_K` 는 별개다 — 사용자가 본선만 지목했다.
     * ⚠ 전체(0.7 = [OTHER_K_ALL])는 [laneGap] 이 두 선로 사이 간격을 잴 때 **같이 본다**.
     */
    val otherK = if (filtered) OTHER_K_DIR else OTHER_K_ALL

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
            // 시간표에는 `2340` 만 있고 API 는 `8340` 을 준다 — 행선까지 넘겨야
            // [Line2Timetable] 이 같은 운행을 찾는다(지선 `5xxx` 충돌 가림, v1.7.2).
            val dl = t.delayMinutes(w, io, m.trainNo, m.statnNm, m.trainSttus, sec, m.destName)
            dl to dl?.let {
                t.secondsToNextStop(w, io, m.trainNo, m.statnNm, it, sec, m.destName)
            }
        }
    }

    /*
     * 방향 필터 — **사용자가 직접 고르면 내 열차도 예외가 아니다**(v1.7.5 ⑥).
     *
     * 사용자: *"외선에 내열차 표시는 잘되고 있는데.. 내선버튼을 눌렀는데도 나타나면 안되잖아!!!!"*
     * v1.6.88~v1.7.4 는 내 열차를 **어느 모드에서든** 그렸다 — 그래서 `내선` 칩을 눌러도
     * 외선인 내 열차가 내선 선로 위에 홀로 서 있었다(거짓 자리다).
     *
     * 가르는 것은 [userPicked] 다. **기본 화면**(아직 아무 칩도 안 누른 상태)은 종전대로
     * 내 열차 방향을 따라가고 전체 보기도 그대로다 — 사용자가 **고른 것만** 절대 규칙이다.
     * 헤더의 `내 열차 …` 문구는 필터와 무관하게 남고, 숨은 상태면 한 토막이 붙는다([mineHead]).
     *
     * ⚠ 여기서 한 번만 거른다(v1.6.94). 상태바가 "왜 비었는지"를 말하려면 **거른 뒤**가
     * 비었는지(이 방향만 없다)와 **거르기 전**이 비었는지(진짜 없다)를 둘 다 알아야 한다.
     * 툴팁도 이 목록에서만 잡히므로(`centers`) 숨은 열차는 눌러도 안 뜬다.
     */
    val drawn = trains.filter {
        eff == DirFilter.ALL || (eff == DirFilter.INNER) == it.inner ||
            (!userPicked && it.trainNo in mineNos)
    }
    val emptyMsg = mainEmptyReason(trains.isEmpty(), drawn.isEmpty(), error)
    /** 내 열차가 **필터에 가려** 지도에 없나 — 헤더가 그 한 토막을 말한다. */
    val mineHidden = mineMark != null && drawn.none { it.trainNo == mineMark.trainNo }

    Column(Modifier.fillMaxSize().padding(inset)) {
        CabHeader(nowMillis, mineMark, mineRoute, mineBoards[mineMark?.trainNo], candidates,
            delay, nextSec, mineHidden, big, pal, onRefresh, onDismiss)
        Box(Modifier.fillMaxWidth().weight(1f)) {
            val d = LocalDensity.current
            // 지도 안 글자배율 상한 — 그림은 dp, 글자만 sp라 배율을 키우면 역 이름이 넘친다.
            // ⚠ TextMeasurer 는 반드시 이 안에서 만든다(지선 지도와 같은 처방).
            CompositionLocalProvider(
                LocalDensity provides Density(d.density, d.fontScale.coerceAtMost(1.15f))
            ) {
                val tm = rememberTextMeasurer()
                /*
                 * ── 열차 이동 (v1.7.5 — **시간 기반 등속 전진**) ────────
                 *
                 * 사용자: *"열차아이콘 움직임이 뒤로는 없어야 되잖아..보니까 정차했을때 약간
                 * 뒤로 움직임이 있네.. 출발을 했으면 일정한 속도로 다음역까지 앞으로 가줘야지..
                 * 자연스럽게 갑자기 슬라이딩 하면 안되지"*
                 *
                 * v1.6.88~v1.7.4 는 열번마다 [Animatable] 을 두고 새 목표까지 **1초 tween** 으로
                 * 미끄러뜨렸다 — 목표가 뒤로 오면 **뒤로 미끄러지고**, 멀리 오면 1초 안에
                 * 메우느라 **훅 밀렸다**(거리가 클수록 빨라진다 = 등속의 반대).
                 *
                 * 이제 규칙은 순수 함수 [stepMotion] 한 곳이고(`TrainMotionTest` 12건이 잠근다)
                 * 여기는 **장부와 시계**만 댄다. 지선 카드가 v1.6.70 부터 쓰던 그 방식이다.
                 *
                 * ⚠ 장부는 **거른 목록이 아니라 전체 목록**([trains])으로 돌린다 — 필터를 껐다
                 * 켤 때마다 기억이 버려지면 열차가 제자리에서 다시 튄다.
                 * ⚠ 걸음은 **그리는 자리**에서 돈다. 이 캔버스는 연기 위상([phase]) 때문에 어차피
                 * 매 프레임 다시 그려지므로 시계가 공짜로 딸려 온다(따로 프레임 시계를 두면
                 * 컴포지션이 한 벌 더 돈다).
                 */
                /** 역간 소요(초) — 시간표의 **그 운행·그 구간**. 없으면 [DEFAULT_SEG_SEC]. */
                val segSecs = remember(trains, tt, weekTag) {
                    trains.associate { t ->
                        t.trainNo to (
                            // ⚠ **`nowSec` 을 넘긴다**(v1.7.7 A1). 안 넘기면 `stopAt` 이
                            // 종전대로 **첫 정차**를 잡아, 성수를 두 번(시·종착) 적은 운행
                            // 1142건에서 새벽 출고 구간의 소요를 쓴다.
                            tt?.segmentSeconds(
                                weekTag, Line2Timetable.inoutOf(t.inner), t.trainNo, t.statnNm,
                                nowSec = Line2Timetable.serviceClock(LocalDateTime.now()).second,
                                dest = t.destName,
                            )?.toFloat() ?: DEFAULT_SEG_SEC
                            )
                    }
                }
                val motions = remember { mutableMapOf<String, TrainMotion>() }
                /*
                 * 연기·물결 위상 — **지도 한 장에 하나뿐**이다(v1.6.91). 열차마다
                 * [rememberInfiniteTransition] 을 만들면 43대분 트랜지션이 돌아 프레임이 죽는다.
                 * ⚠ v1.6.90 에서 걷어낸 **펄스 링과는 다른 물건**이다 — 되살린 게 아니다.
                 */
                val phase by rememberInfiniteTransition(label = "loco").animateFloat(
                    0f, 1f,
                    infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "phase",
                )
                var hit by remember { mutableStateOf<List<Pair<Offset, String>>>(emptyList()) }
                /*
                 * ⚠ **key 는 [Unit] 이다**(v1.7.7 A2). 종전엔 `pointerInput(hit)` 이었는데
                 * [hit] 은 **그리는 자리에서 매 프레임 새 목록으로 덮인다**(아래 `hit = drawCabLoop(...)`)
                 * — 캔버스는 연기 위상 때문에 초당 수십 번 다시 그려지므로 key 가 매번 바뀌어
                 * `detectTapGestures` 코루틴이 **손가락이 닿기도 전에 취소·재시작**됐다.
                 * 그래서 **달리는 열차를 눌러도 툴팁이 안 떴다**(멈춘 화면에서만 됐다).
                 * key 를 고정하면 제스처는 한 번만 뜨고, 람다 안의 [hit]·[pick] 은 그때그때
                 * 최신 값을 읽는다(`by` 는 같은 [MutableState] 를 붙들고 있다).
                 */
                val pick by rememberUpdatedState(onPick)
                Canvas(
                    Modifier.fillMaxSize().pointerInput(Unit) {
                        detectTapGestures { tap ->
                            fun dist(o: Offset) =
                                hypot((o.x - tap.x).toDouble(), (o.y - tap.y).toDouble())
                            val near = hit.minByOrNull { dist(it.first) }
                            pick(near?.takeIf { dist(it.first) < 26.dp.toPx() }?.second)
                        }
                    }
                ) {
                    // 한 걸음 — **전체 목록**을 앞으로만 옮긴다([stepMotion]).
                    val nowDraw = System.currentTimeMillis()
                    pruneMotions(motions, trains.mapTo(HashSet()) { it.trainNo }, nowDraw)
                    trains.forEach { t ->
                        motions[t.trainNo] = stepMotion(
                            motions[t.trainNo], t.stationIdx + t.offset,
                            holding = t.trainSttus == "1", inner = t.inner,
                            segSec = segSecs[t.trainNo] ?: DEFAULT_SEG_SEC, nowMs = nowDraw,
                        )
                    }
                    val placed = drawn.map { t ->
                        t to (motions[t.trainNo]?.folded ?: (t.stationIdx + t.offset))
                    }
                    // 전체 보기만 **복선**이다(v1.7.4). 방향 필터를 켜면 한 방향뿐이라
                    // 선로도 한 줄 — v1.7.3 화면 그대로다.
                    hit = drawCabLoop(tm, placed, mineNos, mineBoards, big, picked,
                        labelSp, otherK, phase, mapDeg, pal, dual = !filtered)
                }
            }
        }
        CabStatusBar(mine, mineMark, candidates, emptyMsg, error != null && trains.isEmpty(),
            big, pal, eff, onFilter)
    }
}

/**
 * 열차가 하나도 안 그려질 때 **왜 비었는지** 한 줄로. `null` = 그릴 열차가 있다.
 *
 * ## v1.6.94 확정 — 빈 상태·오류는 **지도에 얹지 않고 상태바 한 줄로 말한다**
 *
 * v1.6.93 은 이 문구를 지도 **한가운데**에 반투명 판과 함께 그렸다. 두 가지가 한꺼번에 틀렸다:
 *
 *  1. 순환선 **안쪽은 역 이름이 가장 빽빽한 자리**다. 판이 동대문역사문화공원·을지로4가·
 *     신당·방배를 통째로 덮었다 — 확정 규칙 *"역 이름이 가려지면 실패"* 위반이다.
 *     바탕을 깔아도 가리는 건 그대로다. **덮을 자리가 애초에 없다.**
 *  2. 왼쪽 상태 칩이 [error] 를 이미 말하고 있어 한 화면에 **같은 문장이 두 번** 나왔다.
 *
 * 그래서 지도 위에는 아무 글자도 얹지 않고, 이 함수가 고른 한 줄만 [CabStatusBar] 칩이 말한다.
 * 문구·판정은 지선 카드([LineMap])와 같은 것을 쓴다 — 두 지도가 다른 시각에 "운행 종료"라고
 * 말하면 안 된다.
 *
 * @param allEmpty 필터 **전** 목록이 비었나. 거짓이면 "이 방향만 없다"는 뜻이다.
 * @param drawnEmpty 필터 **후** 목록이 비었나.
 * @param error 스냅샷 오류 — 조회 실패·한도 소진·인터넷 끊김이 이미 사람 말로 들어 있다
 *   ([BranchLive.humanError]).
 */
internal fun mainEmptyReason(
    allEmpty: Boolean, drawnEmpty: Boolean, error: String?, now: LocalTime = LocalTime.now(),
): String? = when {
    !drawnEmpty -> null
    !allEmpty -> "이 방향 열차 없음 · [전체]"
    error != null -> error
    inService(now) -> "실시간 조회 중…"
    else -> "금일 운행 종료 (영업 05:30~)"
}

/* ────────────────────────── 상단바 ────────────────────────── */

private val WEEKDAYS = listOf("월", "화", "수", "목", "금", "토", "일")

/**
 * 후보 열번 줄이기 — 지선 다이아는 한 근무가 **스무 개 넘는 열번**을 잡아서 그대로 이으면
 * 헤더 한 줄을 통째로 먹는다(실측: `5668·5669·…·5527` 20개가 화면 세로를 다 채웠다).
 *
 * v1.6.88 에서 헤더·상태바가 **한 줄**이 되며 넷 → 둘로 줄였다. 넘쳐서 `Ellipsis` 가 걸리면
 * 뒤의 `외 N개` 부터 잘려 **몇 대인지도 모르게** 되기 때문이다.
 *
 * [take] 는 헤더가 폭에 안 들어갈 때 **한 개로 더 줄이는** 손잡이다(v1.6.94 [HEAD_LADDER]).
 * 몇 개를 적든 `외 N개` 가 총수를 지키므로 정보는 안 사라진다.
 */
private fun shortNos(nos: List<String>, take: Int = 2): String =
    nos.take(take).joinToString("·") + if (nos.size > take) " 외 " + (nos.size - take) + "개" else ""

/**
 * 헤더 한 줄의 **앞 토막** — 잘리면 안 되는 것들. `null` = 후보 열번조차 없다.
 *
 * 열번·방향·지연·다음 역을 앞에 둔다(사용자 요청: *"노선 공간을 조금 더"* → 헤더가 한 줄이
 * 되면서 넘칠 수 있는데, 잘려도 되는 건 **현재 역·상태**뿐이다 — 그건 지도에 빨간 점으로도
 * 나온다).
 *
 * [delay]·[nextSec] 는 [Line2Timetable] 이 준 값이고 **시간표에 열번이 없으면 null** 이라
 * 그 두 토막만 조용히 빠진다(없는 값을 지어내지 않는다 — [MyTrain] KDoc 과 같은 규칙).
 *
 * [dest] 도 같은 규칙이다(v1.7.5) — [myDestination] 이 행로표 표지·접두로 **확실히 아는
 * 경우에만** 온다. 지붕 위 행선판과 **같은 값**이라 서로를 확인해 주고, 열차가 계단에 올라
 * 판이 작게 보일 때도 헤더에서 읽힌다.
 * ⚠ **API 행선(`destName`)을 여기 쓰지 말 것** — 타절·입고 열차가 전부 `성수종착` 이다.
 *
 * [hidden] 은 **필터가 내 열차를 숨겼을 때**만 온다(v1.7.5 ⑥) — 사용자가 내선/외선을 직접
 * 고르면 반대 방향인 내 열차는 **안 그린다**. 지도에 없는 이유를 헤더가 한 토막으로 말한다.
 */
private fun mineHead(
    mineMark: MainTrainMark?, mineRoute: String?, dest: String?, candidates: List<String>,
    delay: Int?, nextSec: Int?, hidden: Boolean = false,
): String? = when {
    mineMark != null ->
        // 열번은 **API 번호 그대로**다(`8340`). 행로표와 다를 때만 괄호로 같이 적는다 —
        // 승무원이 지도에서 보는 숫자와 행로표 숫자가 다른 이유를 화면이 스스로 말해야 한다(v1.7.2).
        "내 열차 " + mineMark.trainNo +
            (mineRoute?.takeIf { it != mineMark.trainNo }?.let { "(행로표 $it)" }.orEmpty()) +
            " · " + (if (mineMark.inner) "내선" else "외선") +
            dest?.let { " · $it" }.orEmpty() +
            delay?.let {
                when {
                    it > 0 -> " · +${it}분 지연"
                    it < 0 -> " · ${-it}분 빠름"
                    else -> " · 정시"
                }
            }.orEmpty() +
            nextSec?.let {
                if (it <= 0) " · 곧 도착" else " · 다음 역 ${(it + 59) / 60}분 후"
            }.orEmpty() +
            // 필터에 가려 지도에 없다 — **한 토막만**(어느 화면에 있는지).
            if (hidden) " (${if (mineMark.inner) "내선" else "외선"} 화면에 있음)" else ""
    candidates.isNotEmpty() -> "내 열차 미검출(운행 전/후)"
    else -> null
}

/** 헤더 한 줄의 **뒤 토막**. */
private fun mineTail(mineMark: MainTrainMark?, candidates: List<String>, take: Int = 2): String =
    when {
        // ⚠ 도착 예정 **시각은 만들지 않는다** — 그 데이터가 앱에 없다(MyTrain KDoc).
        // API 가 준 역명(statnNm)과 상태(trainSttus)만 그대로 옮긴다.
        mineMark != null -> mineMark.statusText
        candidates.isNotEmpty() -> "오늘 열번 " + shortNos(candidates, take)
        else -> ""
    }

/**
 * 헤더 한 줄에서 **무엇을 먼저 버릴지** — 넉넉한 것부터 짧은 것 순. 첫 번째로 **폭에 들어가는**
 * 칸을 쓴다([CabHeader] 가 [TextMeasurer] 로 실제로 재서 고른다).
 *
 * ## v1.6.94 — 잘려서 `…` 로 끝나는 것은 실패다
 *
 * v1.6.93 은 헤더를 한 문장으로 합쳐 `weight(1f)` 하나만 줘서 **닫기 X 를 살렸는데**, 대신
 * 남은 폭을 못 채운 만큼이 `Ellipsis` 로 잘렸다 — 실화면에서 `… · 오늘 열…` 로 끝나
 * **오늘 열번(2501·2523 외 2개)이 통째로 사라졌다.** 단추는 살고 정보가 죽은 것이다.
 *
 * 사용자 확정 우선순위: **내 열차 상태 > 오늘 열번 > 날짜·시계.** 그래서 사다리는 위에서부터
 * ① 글자 한 단계 축소(하한 [HEAD_MIN_K]) ② 요일 ③ 초 ④ 열번 목록 한 개로 ⑤ 날짜 ⑥ 제목
 * 순으로만 덜어 내고, **마지막 칸에도 내 열차 상태와 오늘 열번은 남는다.** 각 칸은 앞 칸의
 * 부분집합이라 되돌아가지 않는다.
 */
private data class HeadSpec(
    val title: Boolean = true, val date: Boolean = true, val weekday: Boolean = true,
    val seconds: Boolean = true, val take: Int = 2, val k: Float = 1f,
)

/** 글자 축소 하한 — 이 밑으로는 안 줄이고 **조각을 버린다**(작아서 못 읽으면 잘린 것과 같다). */
private const val HEAD_MIN_K = 0.88f

private val HEAD_LADDER = listOf(
    HeadSpec(),
    HeadSpec(k = HEAD_MIN_K),                                        // ① 글자 한 단계
    HeadSpec(weekday = false, k = HEAD_MIN_K),                       // ② 요일 `(금)`
    HeadSpec(weekday = false, seconds = false, k = HEAD_MIN_K),      // ③ 시계 초
    HeadSpec(weekday = false, seconds = false, take = 1, k = HEAD_MIN_K),          // ④ 열번 목록
    HeadSpec(date = false, weekday = false, seconds = false, take = 1, k = HEAD_MIN_K),   // ⑤ 날짜
    HeadSpec(title = false, date = false, weekday = false, seconds = false,
        take = 1, k = HEAD_MIN_K),                                   // ⑥ 제목
)

/**
 * 헤더 **한 줄** (v1.6.88) — `2호선 실시간 · 09/04(금) 10:08:57 · 내 열차 2039 · 외선 ·
 * +2분 지연 · 다음 역 3분 후 · 동대문역사문화공원 진입`, 오른쪽 끝에 닫기 X.
 *
 * 사용자(기관사) 요청: *"위쪽 헤더는 1줄로, 텍스트 크기를 한 단계씩만 더 줄여서 노선 공간을
 * 조금 더 확보"*. 두 줄(제목+큰 시계 / 내 열차)을 한 줄로 접고 글자를 2sp 씩 내렸다.
 *
 * ⚠ 줄 높이는 이제 **닫기 단추가 정한다** — 기본 [IconButton] 은 48dp 라 글자를 줄여도
 * 높이가 안 줄었다. 36dp 로 묶어 실제로 12dp 를 지도에 돌려준다.
 *
 * ⚠ **늘어나는 토막에 [weight] 를 준다**(v1.6.93 정정). `Row` 는 무게 **없는** 자식을 먼저
 * 순서대로 재면서 남은 폭을 깎아 나가고, 무게 있는 자식이 그 나머지를 나눠 갖는다. v1.6.88 은
 * 이 순서를 거꾸로 적어 놓고 [mineHead] 토막을 **무게 없이** 뒀는데, 그러면 긴 `내 열차 …`
 * 한 줄이 남은 폭을 통째로 먹고 **뒤에 오는 닫기 X 가 폭 0 으로 측정된다**(배율 2.0 근처).
 *
 * ⚠ **줄이는 것과 잘리는 것은 다르다**(v1.6.94). v1.6.93 은 한 문장으로 합쳐 닫기 X 를
 * 살렸지만, 남은 폭을 넘는 몫이 그대로 `Ellipsis` 가 됐다 — 실화면이 `… · 오늘 열…` 로 끝나
 * **오늘 열번이 사라졌다.** 이제 [HEAD_LADDER] 를 위에서부터 [TextMeasurer] 로 **실제로 재서**
 * 처음으로 들어가는 칸을 고른다. 폭은 `weight(1f)` 자리에 놓은 [BoxWithConstraints] 가
 * **Row 가 실제로 준 값**으로 알려 주므로 닫기 X 폭을 추측하지 않는다.
 */
@Composable
private fun CabHeader(
    nowMillis: Long, mineMark: MainTrainMark?, mineRoute: String?,
    /** 내 열차 **행선**([myDestination]) — 모르면 null 이라 낱말 자체가 안 나온다(v1.7.5). */
    mineDest: String?,
    candidates: List<String>,
    delay: Int?, nextSec: Int?,
    /** 내 열차가 **필터에 가려** 지도에 없나 — 문구 끝에 한 토막이 붙는다(v1.7.5 ⑥). */
    mineHidden: Boolean,
    big: Boolean, pal: MapPalette,
    /** 즉시 갱신(캐시 건너뛰기) — 지선 카드의 그 ↻ 와 **같은 함수**다([RefreshButton]). */
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    val t = remember(nowMillis / 1_000) {
        LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), ZoneId.systemDefault())
    }
    val baseSp = if (big) 12f else 9.5f
    // 시계는 노란색 유지(사용자 확정) — 한 줄에서 눈에 걸리라고 2sp 만 크게.
    val clockSp = if (big) 14f else 11.5f
    // 크림 바탕에서는 노랑이 안 보인다 — 팔레트가 스타일에 맞는 강조색을 준다.
    val mineColor = if (mineMark != null) pal.mineText else pal.dim
    val head = mineHead(mineMark, mineRoute, mineDest, candidates, delay, nextSec, mineHidden)
    val tm = rememberTextMeasurer()

    fun build(s: HeadSpec): Pair<AnnotatedString, TextStyle> {
        val txt = buildAnnotatedString {
            if (s.title) withStyle(
                SpanStyle(color = pal.title, fontWeight = FontWeight.Bold)
            ) { append("2호선 실시간") }
            if (s.title) append(" · ")
            if (s.date) withStyle(SpanStyle(color = pal.dim)) {
                append("%02d/%02d".format(t.monthValue, t.dayOfMonth))
                if (s.weekday) append("(${WEEKDAYS[t.dayOfWeek.value - 1]})")
                append(" ")
            }
            withStyle(
                SpanStyle(
                    color = pal.clock, fontSize = (clockSp * s.k).sp,
                    fontWeight = FontWeight.Bold,
                )
            ) {
                append(
                    if (s.seconds) "%02d:%02d:%02d".format(t.hour, t.minute, t.second)
                    else "%02d:%02d".format(t.hour, t.minute)
                )
            }
            val info = listOfNotNull(
                head, mineTail(mineMark, candidates, s.take).ifEmpty { null },
            ).joinToString(" · ")
            if (info.isNotEmpty()) withStyle(
                SpanStyle(color = mineColor, fontWeight = FontWeight.Bold)
            ) { append(" · $info") }
        }
        return txt to TextStyle(fontSize = (baseSp * s.k).sp)
    }

    Row(
        // ⚠ **[zIndex] 1** — 아래 ↻ 는 36dp 헤더 밖으로 6dp 씩 넘쳐 눌린다(v1.7.7 A5).
        // 그런데 [Column] 의 히트 테스트는 **나중 자식이 먼저**라, 지도 [Canvas] 의
        // `detectTapGestures` 가 그 넘친 자리를 먼저 채 갔다(실측: 단추 중심에서 아래로
        // 35px 부터 툴팁 판정으로 빠졌다). z 를 올리면 그리기·히트 테스트 둘 다 헤더가 먼저다.
        Modifier.zIndex(1f).fillMaxWidth().padding(start = 12.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BoxWithConstraints(Modifier.weight(1f)) {
            val room = constraints.maxWidth
            val pick = HEAD_LADDER.asSequence().map(::build).firstOrNull { (txt, style) ->
                tm.measure(txt, style, maxLines = 1).size.width <= room
            } ?: build(HEAD_LADDER.last())
            Text(
                pick.first, style = pick.second,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        // ↻ 는 **닫기 X 왼쪽**이다(v1.7.5 ⑤ 사용자 지정). 무게 없는 자식이라 Row 가 먼저
        // 재고, 위 [BoxWithConstraints] 는 **그 폭을 뺀 나머지**로 사다리를 고른다.
        //
        // ⚠ **높이만 36dp 로 줄여 신고한다**(v1.7.7 A5). [RefreshButton] 은 `Surface(onClick)`
        // 이라 `minimumInteractiveComponentSize()` 가 레이아웃 크기까지 **48dp** 로 올린다 —
        // v1.7.5 가 이 단추를 넣으면서 헤더가 36 → 48dp 로 커졌고 그만큼(12dp) 지도가 줄었다.
        // 아래 [shrinkHeight] 는 **재기는 48dp 로 재고 부모에게는 36dp 라고 말한 뒤 가운데
        // 정렬로 얹는다** — Row 는 clip 하지 않으므로 위아래로 6dp 씩 넘친 부분도 그대로
        // 눌린다(터치 48dp 유지). `requiredSize` 로 자식을 키우면 Row 가 그 크기를 그대로
        // 받아 헤더가 다시 48dp 가 된다 — 신고 높이를 줄이는 쪽이라야 한다.
        Box(Modifier.shrinkHeight(36.dp)) { RefreshButton(pal, onRefresh) }
        IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Close, "닫기", Modifier.size(20.dp), tint = pal.title)
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
 *
 * v1.6.94 부터 **빈 상태를 말하는 곳도 여기 하나뿐**이다([mainEmptyReason]) — 지도 한가운데
 * 오버레이는 없앴다. 한 줄 규칙은 그대로다.
 *
 * @param empty 열차가 하나도 안 그려지는 이유. `null` 이면 그릴 열차가 있다.
 * @param failed [empty] 가 **실패**인가(조회 실패·한도 소진). 참이면 주황, 거짓이면 흐린 색이다 —
 *   영업 종료·조회 중은 잘못된 게 아니니 경고색을 쓰지 않는다.
 */
@Composable
private fun CabStatusBar(
    mine: MyTrain?, mineMark: MainTrainMark?, candidates: List<String>,
    empty: String?, failed: Boolean, big: Boolean, pal: MapPalette,
    filter: DirFilter, onFilter: (DirFilter) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        /*
         * ⚠ 왼쪽 칩 둘을 **한 겹 더 싼다**(v1.6.93 — 지선 카드가 v1.6.91 에 이미 고친 결함이
         * 여기 그대로 남아 있었다). 종전엔 정보 칩의 `weight(1f, fill = false)` 와 빈
         * [Spacer] 의 `weight(1f)` 가 남는 폭을 **반씩** 나눠 가져, 배율 1.5 에서 정보 칩이
         * `오늘 열번 5668 · …` 로 잘리는데 그 옆은 텅 비어 있었다. 이제 안쪽 Row 가 남는 폭을
         * 통째로 받아 정보 칩이 제 폭을 먼저 가져가고, 빈자리는 그 안에 남는다
         * (덤으로 방향 필터가 오른쪽 끝에 붙는다 — [LineMap] 하단 칩 줄과 같은 처방).
         */
        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Chip("2호선", big, pal.rail, pal)
            // 정보 칩은 안쪽 Row 가 준 나머지 안에서만 늘어난다 — 넘치면 여기만 `Ellipsis` 다.
            // 잘린 정보는 헤더 줄에 그대로 다시 나오지만, 못 누르는 단추는 없는 단추다.
            val shrink = Modifier.weight(1f, fill = false)
            when {
                // ⚠ 오류는 **열차가 하나도 없을 때만** 말한다. 스냅샷의 `error` 는 지선 카드가 쓰는
                // 양천구청 **도착** API 실패까지 합쳐 온 값이라(위치 API 는 멀쩡할 수 있다), 그대로
                // 띄우면 열차 14대를 그려 놓고 "정보를 못 받았어요"라고 말하게 된다(v1.6.84 실측).
                // 그 판정은 이제 [mainEmptyReason] 한 곳이 한다 — 영업 종료·조회 중·한도 소진도
                // 같이 구분해서 말한다(v1.6.94).
                empty != null ->
                    Chip(empty, big, if (failed) pal.fail else pal.dim, pal, modifier = shrink)
                mineMark != null ->
                    Chip("내 열번 " + mineMark.trainNo, big, pal.mineBody, pal,
                        fill = true, modifier = shrink)
                // 사업 시각을 아는 본선 근무면 언제 나가는지까지 말해 준다.
                mine != null && !mine.riding && mine.startAt != null && candidates.isNotEmpty() ->
                    Chip(
                        "다음 " + mine.nos.first() + " " + fmt(mine.startAt) +
                            (if (mine.nextDay) " (익일)" else ""),
                        big, pal.info, pal, modifier = shrink,
                    )
                candidates.isNotEmpty() ->
                    Chip("오늘 열번 " + shortNos(candidates), big, pal.info, pal, modifier = shrink)
                else -> Chip("오늘 근무 열번: 없음", big, pal.dim, pal, modifier = shrink)
            }
        }
        // 오른쪽 끝에 방향 필터. 기본은 내 열차 방향이라 처음 열면 이미 한쪽이 켜져 있다.
        DirFilter.entries.forEach { f ->
            Chip(f.label, big, pal.otherBody, pal, fill = f == filter) { onFilter(f) }
        }
    }
}

private fun fmt(t: LocalTime) = "%02d:%02d".format(t.hour, t.minute)

/**
 * ⚠ 누를 수 있는 칩은 **[Surface] 가 통째로 단추**다(v1.6.93). 종전엔 안쪽 [Text] 에
 * `clickable` 을 걸어 실제 터치 영역이 글자 높이(≈20dp)뿐이었다 — M3 최소 48dp 의 절반도
 * 안 된다. `Surface(onClick = …)` 은 리플·`Role.Button` 과 함께
 * `minimumInteractiveComponentSize()` 를 스스로 붙여 **보이는 크기는 그대로 두고** 터치만
 * 48dp 로 넓힌다(줄 높이는 그만큼 늘어난다 — 못 누르는 단추보다 낫다).
 */
@Composable
private fun Chip(
    text: String, big: Boolean, tint: Color, pal: MapPalette, fill: Boolean = false,
    modifier: Modifier = Modifier, onClick: (() -> Unit)? = null,
) {
    /*
     * ⚠ 클레이는 **칩 색이 한 가지**다(v1.7.0 — 민트 알약, 고른 칩만 흰 알약).
     * 남색 화면은 칩마다 색이 달라 정보를 색으로도 말했지만, 크림 바탕에서 색을 여섯 개
     * 흩뿌리면 지도보다 칩 줄이 시끄러워진다(시안 확정). 대신 **고른 칩은 흰 알약**이라
     * 어느 방향을 보고 있는지가 더 또렷하다. 남색 스타일은 한 줄도 안 바뀐다.
     */
    val ink = if (pal.clay) pal.chipInk else if (fill) pal.chipInk else tint
    val label: @Composable () -> Unit = {
        Text(
            text,
            // v1.6.88 한 단계 축소(14→12.5 / 11.5→10) — 상태바 한 줄에 필터까지 다 들어가야 한다.
            fontSize = if (big) 12.5.sp else 10.sp, fontWeight = FontWeight.Bold,
            color = ink,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 4.dp),
        )
    }
    val bg = when {
        pal.clay -> if (fill) pal.chipSel else pal.chip
        fill -> tint
        else -> tint.copy(alpha = 0.12f)
    }
    val shape = RoundedCornerShape(50)
    val line = BorderStroke(1.dp, if (pal.clay) pal.chipInk.copy(alpha = 0.30f)
                                  else tint.copy(alpha = 0.85f))
    if (onClick == null) Surface(modifier, shape, bg, border = line) { label() }
    else Surface(onClick, modifier, shape = shape, color = bg, border = line) { label() }
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
    /** 선로 네모의 경계 — [layoutLabels] 가 **선로 침범**을 이 값으로 판정한다(v1.7.7 D7). */
    val x0: Float, val y0: Float,
    val x1: Float, val y1: Float, private val r: Float,
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
 * 역 이름 한 개의 **글자 모양**(크기·굵기·색). [layoutLabels] 안에 있던 식을 그대로 꺼냈다 —
 * v1.7.7 부터 [drawCabLoop] 도 **세로 변 이름 차선 폭을 재느라** 같은 값을 알아야 하는데,
 * 두 군데서 따로 만들면 한쪽만 고쳐 어긋난다.
 *
 * ⚠ **[KEY_STATIONS](신도림·성수)는 `sizeSp + 2`·굵게·주황 그대로**가 사용자 확정이다.
 * 보통 역은 [LABEL_STEP] **2단**([LABEL_DROP]), [LONG_NAME_LEN] 자 이상 긴 역은 **3단**
 * ([LABEL_DROP_LONG]) 내린 크기다(v1.6.96~97). 색만 팔레트가 준다(v1.7.0) — 남색은
 * 신도림·성수가 둘 다 주황이고 클레이는 신도림 초록·성수 빨강이다([MapPalette.keyInk]).
 * **정차 강조로 색을 바꾸지 않는다** — 정차는 선로 위 빨간 점 하나로만 말한다(v1.6.98).
 */
private fun labelStyle(name: String, sizeSp: Float, pal: MapPalette): TextStyle {
    val key = name in KEY_STATIONS
    return TextStyle(
        fontSize = (
            if (key) sizeSp + 2f
            else sizeSp - LABEL_STEP *
                (if (name.length >= LONG_NAME_LEN) LABEL_DROP_LONG else LABEL_DROP)
            ).sp,
        fontWeight = if (key) FontWeight.ExtraBold else FontWeight.Medium,
        color = when {
            key -> pal.keyInk(name)
            name in OP_STATIONS -> pal.op
            else -> pal.label
        },
    )
}

/**
 * 그릴 준비가 끝난 역 이름 한 장. [pivot] 을 중심으로 [deg] 만큼 돌려 그린다.
 *
 * @param leftAnchored true = 글자가 [pivot] 에서 **오른쪽으로** 뻗는다 / false = 왼쪽으로
 */
private class Lab(
    val layout: TextLayoutResult,
    var pivot: Offset,
    /** ⚠ **`var` 다** — 모서리에서 막히면 거울로 뒤집는다(v1.7.7 M2, [layoutLabels] 끝). */
    var deg: Float,
    var leftAnchored: Boolean,
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
 * ## v1.6.98 — 회피가 **한 겹**으로 줄었다
 *
 * 이제 **열차와 역 이름이 늘 선로의 반대편**이다(가로 변은 열차가 위·이름이 아래, 세로 변은
 * 열차가 루프 밖·이름이 안). 그래서 못 피하면 배지 **위에** 덧그리던 `over` 2차 탐색을
 * 걷어냈다 — 한 변 안에서는 둘이 애초에 못 만나므로 그 보험이 값을 못 한다.
 *
 * ⚠ 그래도 [obstacles](열차 상자)는 **남긴다.** 안 넘기면 **모서리에서** 물린다 —
 * 실측(가로 외선): 오른아래 모서리에서 아랫변 열차 `3135` 가 오른변 라벨 `잠실` 을 덮었다.
 * 아랫변 열차는 루프 **안**에 서고 좌·우변 라벨도 루프 **안**에 적히니, 두 영역이 겹치는
 * 곳이 딱 모서리 넷이다.
 *
 * ## 모서리는 **좌·우변이 한 칸 안쪽으로** 물러나 푼다 (v1.6.98)
 *
 * 좌·우변 라벨이 루프 **안**으로 들어오면서 모서리 넷에서 윗·아랫변 대각선 라벨과 정면으로
 * 만난다. 좌·우변은 폭 30dp 짜리 좁은 세로줄에 갇혀 위아래로 ±36dp 밖에 못 움직이는데
 * 대각선은 루프 안쪽 전체를 쓰니, **양보는 좁은 쪽이 아니라 넓은 쪽이 해야** 맞다.
 *
 * 순서를 뒤집어 좌·우변을 먼저 놓아 봤더니 이번엔 대각선이 갇혔다(실측: 왼위 모서리에서
 * `합정` 이 `당산` 위에 올라탔다 — 아래·오른쪽이 이미 `영등포구청`·`문래`·`신도림` 이었다).
 * 그래서 순서는 종전대로(대각선 먼저) 두고, 좌·우변에 **2차 시도**를 준다: 제자리 세로줄이
 * 다 막히면 루프 **안쪽으로 한 칸([SIDE_LANE2]) 물러난 세로줄**에서 다시 찾는다. 물러나도
 * 제 역 점과 **같은 높이 대**라 어느 점의 이름인지가 안 흐려진다(실측: `영등포구청` 하나만
 * 물러나 `홍대입구` 대각선 밑으로 깨끗이 빠졌다).
 */
private fun DrawScope.layoutLabels(
    tm: TextMeasurer, loop: Loop,
    /**
     * **안쪽 선로**(v1.7.4 복선). 단독 보기는 [loop] 과 **같은 객체**라 아래 계산이 한 글자도
     * 안 바뀐다.
     *
     * 복선에서 이름이 붙는 선로는 **두 선로 묶음의 바깥 테두리**다(사용자 확정):
     * 윗변은 **안쪽 선로 아래** · 아랫변은 **바깥 선로 아래** · 좌·우변은 **안쪽 선로 안쪽**.
     * 그래서 아랫변만 [loop](바깥 선로)을 보고 나머지는 이 [loopIn] 을 본다 —
     * 이름이 **두 선로 사이 공간을 침범하지 않는다.**
     */
    loopIn: Loop,
    start: Int, sizeSp: Float, obstacles: List<Rect>,
    pal: MapPalette,
    /** 선로 굵기 — **선로 침범 판정**에 쓴다(v1.7.7 D7). */
    railW: Float,
    /**
     * 좌·우변 이름을 루프 **밖**에 적나(v1.7.7 D1). `null` 이면 종전대로 루프 **안쪽**이다.
     * 값이 있으면 `(왼쪽 이름의 오른끝 x, 오른쪽 이름의 왼끝 x)` — [drawCabLoop] 가
     * 열차 차선([trainPad]) 바깥으로 재 둔 자리다.
     */
    sideLaneX: Pair<Float, Float>?,
): List<Lab> {
    /*
     * ── 이름이 살 수 있는 **띠**를 먼저 잰다 (v1.7.7 D1) ─────────
     * 안쪽 루프 안의 세로 두께다. 폰 세로는 ≈140dp 인데 **가로 화면 복선은 ≈74dp** 뿐이다 —
     * 두 선로 사이 틈([laneGap] 44.18dp)이 위아래로 두 번 들어가기 때문이다. 그 얇은 띠에
     * 30dp 짜리 여유를 그대로 쓰면 윗변 이름이 **띠 바닥(= 반대편 선로)** 에서 시작한다.
     * 그래서 여유는 띠 두께에 비례한다 — 폰 세로에서는 종전 30dp 그대로다.
     */
    val bandH = (loopIn.y1 - loopIn.y0) - railW
    val flat = sideLaneX != null
    val gap = if (!flat) 30.dp.toPx() else (bandH * 0.20f).coerceIn(9.dp.toPx(), 30.dp.toPx())
    /** 아랫변은 루프 **밖**([namePad] 자리)이라 띠 두께와 무관하다 — 종전 30dp 그대로. */
    val gapOut = 30.dp.toPx()
    val pad = 3.dp.toPx()
    /** 선로 겉면에서 이만큼 떨어져야 "안 닿았다"고 본다(v1.7.7 D7 — 실측 2px 닿음). */
    val clear = railW / 2f + 2.dp.toPx()
    val placed = mutableListOf<Lab>()

    /** 윗변 역 한 칸 폭 — 얇은 띠에서 이름을 **접는 폭**이다. */
    val cellTop = abs(loopIn.at(loopIn.sOf(1)).first.x - loopIn.at(loopIn.sOf(0)).first.x)

    /*
     * 이름 상자를 **한 번에 다 잰다**. 아래 세로 변 펴기(`sideY`)가 높이를 먼저 알아야 하고,
     * 같은 글자를 두 번 재면 두 값이 어긋날 자리가 생긴다.
     *
     * ⚠ **가로 변 긴 이름은 띠가 얇을 때 한 칸 폭으로 접는다**(v1.7.7 D1). 이름을 줄이지는
     * 않는다(사용자 확정 *"역 이름은 절대 줄여 쓰지 않는다 … 두 줄로 접는 것은 허용"*).
     * `동대문역사문화공원`(10자)은 −35° 로 누우면 세로로 **폭 × sin35 ≈ 0.57배**를 먹어
     * 얇은 띠를 통째로 관통한다 — 두 줄로 접으면 폭이 반이라 그 깊이도 반이 된다.
     */
    val layouts = (0 until LOOP_N).associateWith { k ->
        val name = Line2Stations.MAIN[(k + start) % LOOP_N]
        val style = labelStyle(name, sizeSp, pal)
        if (!flat || k >= TOP_N) tm.measure(name, style)
        else {
            /*
             * 줄 수는 **한 칸 폭**이 정하고, 그 줄 수를 유지하는 **가장 좁은 폭**으로 다시 잰다.
             * 한 칸에 딱 맞춰 접으면 첫 줄만 꽉 차 `동대문역사문 / 화공원` 처럼 어정쩡하게
             * 끊기는데, 가장 좁은 폭을 찾으면 `동대문역사 / 문화공원` 으로 고르게 나뉘고
             * 상자도 그만큼 좁아진다(= 대각선 깊이가 준다).
             *
             * ⚠ 산수로 `전체 폭 ÷ 줄 수` 를 계산하면 **글자 자름 반올림**에 한 픽셀씩 물려
             * 도리어 석 줄(`동대문역 / 사문화공 / 원`)이 됐다(실측). 폭을 **재서** 고르면
             * 글꼴·배율·줄바꿈 규칙이 뭘 하든 답이 맞는다. 이분 탐색이라 재는 횟수는
             * `log2(한 칸 폭)` ≈ 7 번이고, 얇은 띠(가로 화면)에서 윗변 17개에만 돈다.
             */
            val cap = cellTop.toInt().coerceAtLeast(1)
            val wide = tm.measure(name, style, constraints = Constraints(maxWidth = cap))
            if (wide.lineCount <= 1) wide else {
                var lo = 1
                var hi = cap
                while (lo < hi) {
                    val mid = (lo + hi) / 2
                    if (tm.measure(name, style, constraints = Constraints(maxWidth = mid))
                            .lineCount <= wide.lineCount) hi = mid else lo = mid + 1
                }
                tm.measure(name, style, constraints = Constraints(maxWidth = hi))
            }
        }
    }

    /** 이름이 매달리는 선로 — 아랫변만 바깥 선로, 나머지는 안쪽 선로([loopIn] KDoc). */
    fun nameLoopOf(k: Int) =
        if (k in (TOP_N + RIGHT_N) until (TOP_N + RIGHT_N + BOTTOM_N)) loop else loopIn

    fun dotOf(k: Int): Offset = nameLoopOf(k).let { it.at(it.sOf(k)).first }

    /*
     * ── 세로 변 이름은 **차례를 지켜 고르게 편다** (v1.7.7 M2·D1) ──
     *
     * 역 간격이 이름 높이보다 좁으면(가로 화면에서 세로 변 다섯 역이 ≈20px 안에 붙는다)
     * 제 점 옆에 그대로 두면 서로 밀어내다가 **순서가 뒤집힌다** — 실측(F08b·F42): 위에서
     * 아래로 `문래 · 영등포구청 · 합정 · 당산` 으로 읽혔는데 실제 순서는
     * `당산 · 영등포구청 · 문래` 다. 변 한가운데를 축으로 이름 높이만큼씩 벌리면 y 가
     * **단조 증가**라 순서가 절대 안 뒤집힌다. 넉넉하면(폰 세로) 손대지 않는다.
     */
    val sideY = HashMap<Int, Float>()
    for (edge in listOf(
        (TOP_N until TOP_N + RIGHT_N).toList(),
        (LOOP_N - LEFT_N until LOOP_N).toList(),
    )) {
        val ks = edge.sortedBy { dotOf(it).y }
        val hs = ks.map { layouts.getValue(it).size.height.toFloat() + pad }
        val need = hs.sum()
        val ys = ks.map { dotOf(it).y }
        if (!flat && need <= ys.last() - ys.first()) continue
        var y = ((ys.first() + ys.last()) / 2f - need / 2f)
            .coerceIn(pad, (size.height - need - pad).coerceAtLeast(pad))
        for (i in ks.indices) { sideY[ks[i]] = y + hs[i] / 2f; y += hs[i] }
    }

    /**
     * 좌·우변 라벨이 **변을 따라** 밀릴 수 있는 최대 거리 = **그 변 역 간격의 절반**(v1.6.99).
     *
     * ⚠ 종전엔 `6dp × 12칸 = ±36dp` 상수였다. 그 값은 **폰 세로**(세로 변 간격 ≈63dp)에서 고른
     * 것이라, 세로 변이 짧아지는 화면에서는 **이웃 역 점을 넘어간다**. 폰 가로 실측:
     * 세로 변 간격이 43dp 뿐인데 `영등포구청`이 36dp 올라가 **`당산` 점 위**에 앉았다.
     * 여기서 매번 재면 폰 세로·폰 가로·폴드 펼침이 각자 제 값을 쓴다 — 손댈 상수가 없다.
     */
    val alongLimit = abs(
        loop.at(loop.sOf(TOP_N)).first.y - loop.at(loop.sOf(TOP_N + 1)).first.y
    ) / 2f

    // **신도림·성수 먼저** → **세로 변 전부** → 윗변·아랫변(대각선).
    // 먼저 놓는 쪽이 자리를 지킨다 — 두 중요 역은 아무 라벨에도 안 밀리고, 나머지가 피해 간다.
    //
    // ⚠ v1.7.7 M2 — **세로 변은 다섯 개가 다 대각선보다 먼저**다. v1.6.99 는 좌·우변의
    // *가운데* 역(문래·영등포구청·구의·강변·잠실나루)만 맨 나중으로 미뤘는데, 그 자리는
    // 위 `sideY` 가 **차례대로 정해 둔 자리**라 늦게 놓을 이유가 없다. 늦게 놓으니 모서리
    // 대각선(`합정`)이 그 열 한복판에 먼저 내려앉아 순서가 뒤집혀 읽혔다(F08b).
    // 대각선은 루프 안쪽 전체를 쓸 수 있으니 **양보는 넓은 쪽이 한다** — 이 파일 KDoc 원칙
    // 그대로다. 대각선이 제자리에서 막히면 아래 **거울**(모서리 반대쪽으로 눕히기)이 받는다.
    val order = (0 until LOOP_N).sortedBy { k ->
        when {
            Line2Stations.MAIN[(k + start) % LOOP_N] in KEY_STATIONS -> 0
            k >= TOP_N + RIGHT_N + BOTTOM_N || k in TOP_N until (TOP_N + RIGHT_N) -> 1
            else -> 2
        }
    }

    for (k in order) {
        val layout = layouts.getValue(k)
        val onTop = k < TOP_N
        val onRight = k in TOP_N until (TOP_N + RIGHT_N)
        val onBottom = k in (TOP_N + RIGHT_N) until (TOP_N + RIGHT_N + BOTTOM_N)
        val horiz = onTop || onBottom
        // ⚠ 두 선로는 **동심**이라 직선 구간의 x·y 범위가 정확히 같다(반지름 차 = 간격).
        // 그래서 같은 [k] 의 두 점이 서로 마주 보고, 이름이 어느 점의 것인지 안 흐려진다.
        val p = dotOf(k)

        /*
         * ── 어느 쪽에 적는가 (v1.6.98 보조설비 배치) ─────────────────────
         * **가로 변(윗변·아랫변)은 늘 선로 아래**로 gap 만큼 내려와 [DIAG] 로 눕는다 —
         * 윗변이면 루프 안쪽, 아랫변이면 루프 바깥쪽이지만 화면에서는 둘 다 "아래"라
         * 한 줄로 끝난다.
         * **세로 변(좌·우)은 루프 안쪽**에 가로로 적는다 — 열차가 바깥으로 나갔으니
         * 종전의 바깥 역명 차선은 없앴다. ⚠ 단 띠가 얇으면([sideLaneX]) 그 차선을 **되살려**
         * 루프 밖에 적는다(v1.7.7 D1) — 안쪽에 다섯 이름이 들어갈 자리가 물리적으로 없다.
         */
        val midY = size.height / 2f
        val midX = size.width / 2f
        val side = 30.dp.toPx()
        // ⚠ 가로 변은 **아래 + 모서리 반대쪽**으로 비스듬히 민다. 순수하게 아래로만 밀면
        // 모서리에서 세로 변의 가로 라벨과 정면으로 만난다.
        val awayX = if (p.x < midX) 0.7f else -0.7f
        val labY = sideY[k] ?: p.y
        val (pivot, push) = when {
            horiz -> Offset(p.x + 4.dp.toPx(), p.y + (if (onBottom) gapOut else gap)) to
                Offset(awayX, 0.7f)
            sideLaneX != null ->
                Offset(if (onRight) sideLaneX.second else sideLaneX.first, labY) to Offset(0f, 0f)
            onRight -> Offset(p.x - side, labY) to Offset(0f, if (p.y < midY) 1f else -1f)
            else -> Offset(p.x + side, labY) to Offset(0f, if (p.y < midY) 1f else -1f)
        }
        val lab = Lab(
            layout, pivot,
            deg = if (horiz) DIAG else 0f,
            // 안쪽으로 뻗어야 한다 — 오른변은 왼쪽으로(=false), 왼변은 오른쪽으로(=true).
            // 밖 차선이면 반대다: 왼쪽 차선은 왼쪽으로, 오른쪽 차선은 오른쪽으로 뻗는다.
            leftAnchored = if (sideLaneX != null && !horiz) onRight else !onRight && !horiz,
        )

        /*
         * 밖 차선에 적는 세로 변 이름은 **자리를 다 정해 놓고 왔다** — 위 `sideY` 가 차례대로
         * 벌려 둔 y 에, x 는 열차 차선 바깥의 고정 열이다. 거기엔 다툴 상대가 없으니
         * 탐색을 돌리지 않는다(돌리면 도리어 차례를 흐트러뜨린다).
         */
        if (sideLaneX != null && !horiz) { placed += lab; continue }

        // 겹치면 그 변 안쪽으로 한 칸씩 민다. 대부분 0칸에서 끝난다.
        // 좌·우변은 위아래 **양쪽**이 비어 있으니 번갈아 밀어 본다(가까운 쪽이 막히면 반대쪽).
        // 윗변·아랫변은 바깥이 선이라 안쪽 한 방향뿐이다.
        //
        // ⚠ 좌·우변은 **촘촘히**(6dp) 보되 **역 간격의 절반까지만** 민다(위 [alongLimit]).
        // ⚠ v1.6.99 — 좌·우변의 **양 끝 역은 모서리 쪽으로 안 민다.** 거기는 이미 이웃 변의
        // 이름 자리라, 막히면 아래 `lane2`(루프 안쪽 한 칸)로 내려가는 편이 맞다.
        val alternate = !horiz
        val edgeFirst = if (onRight) TOP_N else TOP_N + RIGHT_N + BOTTOM_N
        val edgeLast = edgeFirst + (if (onRight) RIGHT_N else LEFT_N) - 1
        val atEdgeEnd = alternate && (k == edgeFirst || k == edgeLast)
        val step = if (alternate) 6.dp.toPx() else 11.dp.toPx()
        val maxTries = if (!alternate) 48 else if (atEdgeEnd) 6 else 12
        /*
         * ⚠ 잘림 판정은 **돌린 상자의 네 꼭짓점**으로 본다(v1.6.98).
         *
         * ⚠ v1.7.7 D7 — **선로를 침범하지 않는다.** 루프 안에 사는 이름(윗변·좌·우변)은
         * 안쪽 선로가 그리는 네모 **안쪽**에 통째로 들어가야 하고, 루프 밖에 사는 이름
         * (아랫변)은 바깥 선로 **아래**라야 한다. 종전엔 캔버스 밖만 봤기 때문에 세로 화면
         * 전체 보기에서 `성수` 상자의 오른쪽 모서리가 오른변 선로를 **0.25dp 물었다**
         * (실측 F07b — 화면에서 글자 밑동 2px 이 초록에 닿았다).
         */
        val inLeft = loopIn.x0 + clear
        val inRight = loopIn.x1 - clear
        val inTop = loopIn.y0 + clear
        val inBottom = loopIn.y1 - clear
        fun inBounds(q: List<Offset>): Boolean {
            if (q.any { it.y < 0f || it.y > size.height }) return false
            if (onBottom) return q.all { it.y >= loop.y1 + clear }
            if (q.any { it.x < inLeft || it.x > inRight || it.y < inTop || it.y > inBottom })
                return false
            // 윗변은 루프 세로 한가운데를 안 넘는다 — 넘으면 아랫변 열차 차선을 문다.
            return !onTop || lab.pivot.y <= size.height / 2f
        }
        /** [limit] = 변을 따라 밀어도 되는 최대 거리. 넘겨야 한다면 이 차선은 포기한다. */
        fun search(from: Offset, limit: Float): Boolean {
            lab.pivot = from
            var t = 0
            while (t < maxTries) {
                val q = lab.quad(pad)
                if (inBounds(q) && placed.none { overlaps(it.quad(pad), q) } &&
                    obstacles.none { overlaps(it.quad(), q) }) return true
                t++
                // 양 끝 역은 [push] 가 가리키는 **변 안쪽 한 방향**으로만 간다(위 주석).
                val d = if (alternate && !atEdgeEnd)
                            (if (t % 2 == 1) 1f else -1f) * ((t + 1) / 2) * step
                        else t * step
                if (abs(d) > limit) return false
                val base = from
                // ⚠ 가로로 흘리는 건 **모서리 회피용**이지 이동 수단이 아니다 — 세 칸
                // (`33 × 0.7 ≈ 23dp`)에서 멈춘다. 더 흘리면 이웃 역을 건너뛰어 순서가 뒤집힌다.
                val dx = if (d > LATERAL_STEPS * step) LATERAL_STEPS * step else d
                lab.pivot = Offset(base.x + push.x * dx, base.y + push.y * d)
            }
            return false
        }
        // 좌·우변은 막히면 **루프 안쪽 한 칸**에서 한 번 더 찾는다(위 KDoc "모서리는 …").
        // 끝내 못 찾으면 제자리에 둔다 — 43개가 다 적히는 편이 낫다(사용자 확정).
        val lane2 = Offset(
            pivot.x + (if (onRight) -1f else 1f) * SIDE_LANE2.toPx(), pivot.y)
        var placedOk =
            if (!alternate) search(pivot, Float.MAX_VALUE)
            else search(pivot, alongLimit) || search(lane2, alongLimit) ||
                search(pivot, Float.MAX_VALUE) || search(lane2, Float.MAX_VALUE)
        /*
         * ── 모서리에서는 **거울**로 한 번 더 (v1.7.7 M2·D1) ─────────
         * 가로 변 이름은 기준점에서 **왼쪽 아래**로 눕는다. 그런데 변의 첫 역(`합정`)은
         * 기준점이 이미 모서리라, 왼쪽으로 눕는 순간 상자가 **선로 밖**으로 나간다 —
         * 종전엔 그걸 못 보고 아래로만 밀어서 좌변 이름 열 한복판까지 내려갔고, 화면에서는
         * `영등포구청` 과 `당산` 사이에 끼어 **차례가 뒤집힌 것처럼** 읽혔다(F08b).
         * 거울(오른쪽 아래로 눕히기)이면 제 점 옆 제자리에 그대로 앉는다.
         */
        if (!placedOk && horiz) {
            lab.deg = -DIAG
            lab.leftAnchored = true
            placedOk = search(pivot, Float.MAX_VALUE)
            if (!placedOk) { lab.deg = DIAG; lab.leftAnchored = false }
        }
        if (!placedOk) lab.pivot = pivot
        placed += lab
    }
    return placed
}

/* ────────────────────────── 그리기 ────────────────────────── */

/** 순환선 한 장을 그리고 **열차 탭 판정용 좌표**를 돌려준다. */
private fun DrawScope.drawCabLoop(
    tm: TextMeasurer,
    trains: List<Pair<MainTrainMark, Float>>,
    /** 지금 살아 있는 **내 열번 전부**(API 번호) — 전부 노란 기관차다(v1.7.2). */
    mineNos: Set<String>,
    /**
     * 내 열차 **행선판 글자**(v1.7.5) — 여기 없는 열차는 **판을 안 단다**([myDestination]).
     * 판까지 넣은 상자가 복선 틈([laneGap])을 정하므로 **둘은 한 벌이다.**
     */
    mineBoards: Map<String, String>,
    big: Boolean,
    picked: String?,
    /** 역 이름 크기 — 방향 필터가 켜져 있으면 커진다([CabScreen]) */
    labelSp: Float,
    /** 다른 열차 기관차 크기 배수 — [CabScreen] 이 필터에 따라 정한다 */
    otherK: Float,
    /** 연기·물결 위상 0~1 — 지도 한 장에 하나([CabScreen]) */
    phase: Float,
    /** 지도 전체 회전(세로 90f). 열번·행선판 **글자를 화면 기준으로 세우는 데만** 쓴다. */
    mapDeg: Float,
    /** 색 한 벌([MapPalette]) — **배치 값은 하나도 여기서 안 온다.** */
    pal: MapPalette,
    /**
     * **복선인가**(v1.7.4) — 전체 보기만 참이다. 참이면 선로가 두 줄(바깥 외선 · 안쪽 내선)
     * 이고 열차·역 점·역 이름이 전부 제 선로를 기준으로 놓인다. 거짓이면 두 `Loop` 이
     * **같은 객체**라 v1.7.3 화면이 픽셀 단위로 그대로 나온다.
     */
    dual: Boolean,
): List<Pair<Offset, String>> {
    /*
     * ── 루프 크기·자리 (v1.6.98 · v1.7.4 복선) ──────────────────
     * 위·왼쪽·오른쪽은 **열차 차선**([trainPad]), 아래는 **역 이름**([namePad])이 먹는다.
     * 셋 다 계산값이라 나머지는 전부 루프에 준다 — 화면을 세로로 꽉 채운다.
     *
     * ## 복선의 바깥 선로는 **단선의 그 자리 그대로**다
     *
     * 바깥(외선) 선로 밖에 서는 것은 **윗변·좌·우변 열차**이고 그 상자는 종전과 같다
     * ([trainPad]). 아랫변 밖에 있는 것도 종전대로 **역 이름뿐**이다([namePad]) — 복선에서
     * 아랫변 외선은 선로 **위**(두 선로 사이)에 서기 때문이다. 그래서 여백을 다시 재도
     * 답이 같고, 안쪽 선로만 [laneGap] 만큼 들어온다 = **루프는 여전히 화면 최대 크기**다.
     *
     * ## 모서리 — 두 개의 **동심** 둥근 사각형
     *
     * 안쪽 반지름 = 바깥 반지름 − 간격이라야 두 곡선이 **어디서나 같은 간격**으로 나란하다
     * (평행곡선). 덤으로 직선 구간의 x·y 범위가 정확히 같아져 **같은 역이 서로 마주 본다**
     * (`Loop.sOf` 가 직선 길이를 등분하므로 `hLen`·`vLen` 이 같으면 자리도 같다).
     * 기준 반지름([baseRadius])을 가운데 두고 반씩 벌려, 안쪽이 너무 뾰족해지지 않게 8dp 로
     * 받친다 — 폰 `43.3 / 12.7dp`, 펼침 `58.6 / 21.4dp`.
     */
    val tp = trainPad(big).toPx()
    val np = namePad(big).toPx()
    val gap = if (dual) laneGap(big).toPx() else 0f
    val rIn = (baseRadius(big).toPx() - gap / 2f).coerceAtLeast(8.dp.toPx())
    val rOut = rIn + gap
    val railW = railW(big).toPx()
    val start = Line2Stations.MAIN.indexOf(LOOP_START)

    /*
     * ── 세로 변 이름을 **안에 적나 밖에 적나** (v1.7.7 D1) ──────
     *
     * 규칙은 그대로 **안쪽 선로 안쪽**이다(사용자 확정 배치). 다만 그 안쪽 띠가 좌·우변
     * 다섯 이름을 담을 수 있을 때 이야기다. **가로 화면 복선**에서는 두 선로 사이 틈
     * ([laneGap] 폰 44.18dp)이 위아래로 두 번 들어가 띠가 **폰 세로의 절반**밖에 안 남는다:
     *
     *     폰 세로 ≈140dp   ↔   폰 가로 ≈74dp   (좌변 다섯 이름이 필요로 하는 높이 ≈74dp)
     *
     * 거기에 윗변 17개가 한 줄로 누우면 좌·우변이 들어갈 자리가 **물리적으로 없다** —
     * v1.7.6 이 `합정`을 `신도림` 점 위에, `잠실`을 `뚝섬` 옆에 포갠 이유다(F42b·F42c).
     * 못 담으면 v1.6.97 까지 쓰던 **루프 밖 이름 차선을 되살린다.** 그 차선은 열차 차선
     * ([trainPad]) **바깥**이라 세로 변 외선 열차와 겹치지 않고, 세로로는 캔버스를 다 쓰므로
     * 다섯 이름이 제 차례대로 편하게 선다.
     *
     * ⚠ 판정은 **재서** 한다(글자배율·펼침·필터가 다 크기를 바꾼다). 여유 30dp 는 안쪽에
     * 적을 때 이름이 선로에서 떨어져 앉는 몫이다 — 그만큼도 못 내면 안에 적을 수 없다.
     */
    val sideKs = (TOP_N until TOP_N + RIGHT_N) + (LOOP_N - LEFT_N until LOOP_N)
    val sideBoxes = sideKs.map { k ->
        val nm = Line2Stations.MAIN[(k + start) % LOOP_N]
        tm.measure(nm, labelStyle(nm, labelSp, pal))
    }
    val sidePad = 3.dp.toPx()
    val sideNeed = listOf(0 until RIGHT_N, RIGHT_N until RIGHT_N + LEFT_N).maxOf { r ->
        r.sumOf { (sideBoxes[it].size.height + sidePad).toDouble() }.toFloat()
    }
    val bandH0 = size.height - tp - np - 2f * gap - railW
    val sideOutside = sideNeed + 30.dp.toPx() > bandH0
    /** 루프 밖 이름 차선의 폭 — 가장 긴 세로 변 이름 + 여백. 안에 적으면 0 이다. */
    val sideLaneW = if (!sideOutside) 0f
                    else sideBoxes.maxOf { it.size.width }.toFloat() + 6.dp.toPx()
    val lx = tp + sideLaneW
    val rx = size.width - tp - sideLaneW
    /** 바깥 선로 = **외선**. 단선이면 이 한 줄뿐이다. */
    val loop = Loop(lx, tp, rx, size.height - np, rOut)
    /** 안쪽 선로 = **내선**. 단선이면 [loop] 과 같은 객체다(회귀 0의 열쇠). */
    val loopIn =
        if (!dual) loop
        else Loop(lx + gap, tp + gap, rx - gap, size.height - np - gap, rIn)
    /** 루프 밖 이름 차선의 기준 x — 왼쪽은 글자의 **오른끝**, 오른쪽은 **왼끝**이다. */
    val sideLaneX =
        if (!sideOutside) null
        else (lx - tp - 2.dp.toPx()) to (rx + tp + 2.dp.toPx())

    /*
     * ── 굵은 둥근 사각형 = 선로 ──────────────────────────────
     * 남색은 종전대로 **초록 한 줄**. 클레이는 **세 줄**(그림자 → 튜브 → 하이라이트)이라
     * 점토를 눌러 만든 관처럼 보인다:
     *  · 그림자는 같은 경로를 화면 아래로 6dp 옮겨 한 번 더 — **블러 없음**(오프셋 복제).
     *  · 튜브는 세로 그라데이션(위 밝고 아래 어둡게).
     *  · 하이라이트는 **같은 경로**를 위로 4dp 옮겨 가늘게 — 시안이 지목한 "모서리에서
     *    하이라이트가 어긋난다"는 자리를 이렇게 막는다(같은 둥근 사각형이라 곡률이 같다).
     * 셋 다 [screenDown] 을 쓰므로 세로 화면에서도 그림자가 화면 아래로 떨어진다.
     *
     * ⚠ **복선은 두 줄이 똑같다**(v1.7.4 사용자 확정) — 색도 굵기도 클레이 튜브 질감도.
     * 지선 카드처럼 한쪽을 연하게 하지 **않는다**: 지선은 신도림행이 주 선로라 위계가 있지만
     * **본선은 내선·외선이 대등**하다. 한쪽을 물러나게 하면 그 방향이 곁가지로 읽힌다.
     */
    fun rail(brush: Color, w: Float, d: Offset, tl: Offset, sz: Size, r: Float) = drawRoundRect(
        brush,
        topLeft = Offset(tl.x + d.x, tl.y + d.y),
        size = sz,
        cornerRadius = CornerRadius(r),
        style = Stroke(width = w),
    )
    fun railPass(tl: Offset, sz: Size, r: Float) {
        pal.railShadow?.let { rail(it, railW, screenDown(mapDeg, 6.dp.toPx()), tl, sz, r) }
        if (pal.clay) drawRoundRect(
            androidx.compose.ui.graphics.Brush.verticalGradient(
                listOf(pal.railTop, pal.rail, pal.railBottom),
                startY = tl.y, endY = tl.y + sz.height,
            ),
            topLeft = tl, size = sz, cornerRadius = CornerRadius(r),
            style = Stroke(width = railW),
        ) else rail(pal.rail, railW, Offset.Zero, tl, sz, r)
        /*
         * ⚠ 하이라이트 굵기는 **선로의 30%**, 띄우는 거리는 그 절반이다(시안의 `6dp · −4dp`
         * 에서 실화면 보고 고쳤다). 시안 값은 선로가 26px 이던 그림 기준이라, 7.5dp 선로에
         * 6dp 를 얹으면 **세로 변에서 선로를 거의 덮는다** — 세로 변은 "화면 아래"로 띄운
         * 오프셋이 선로를 따라 미끄러져 그대로 겹치기 때문이다(실측: 왼쪽 세로변이 연한 띠
         * 하나로 납작해졌다). 지금 값이면 가로 변은 윗면 하이라이트, 세로 변은 가운데 밝은
         * 줄로 읽혀 둘 다 튜브가 된다.
         */
        pal.railHighlight?.let {
            rail(it, railW * 0.30f, screenDown(mapDeg, -railW * 0.26f), tl, sz, r)
        }
    }
    railPass(
        Offset(lx, tp), Size(rx - lx, size.height - tp - np), rOut)
    if (dual) railPass(
        Offset(lx + gap, tp + gap),
        Size(rx - lx - 2 * gap, size.height - tp - np - 2 * gap), rIn)

    // ── 방향 화살표 — 모서리 호 한가운데(역이 없는 자리)에 안/밖 하나씩 ──
    // 복선에서는 **각자 제 선로의 모서리**에 붙는다(단선은 두 줄이 같은 곡선이라 종전과 같다).
    val arrowOff = (if (big) 15 else 12).dp.toPx()
    for (corner in 0 until 4) {
        val (pi, ti) = loopIn.at(loopIn.cornerMid(corner))
        val nIn = Offset(-ti.y, ti.x)
        drawChevron(Offset(pi.x + nIn.x * arrowOff, pi.y + nIn.y * arrowOff), ti,
            if (pal.clay) pal.railBottom else pal.rail, 5.dp.toPx())  // 내선 = 시계
        val (po, to) = loop.at(loop.cornerMid(corner))
        val nOut = Offset(to.y, -to.x)
        drawChevron(Offset(po.x + nOut.x * arrowOff, po.y + nOut.y * arrowOff),
            Offset(-to.x, -to.y), if (pal.clay) pal.op else pal.otherBody, 5.dp.toPx())  // 외선
    }

    // ── 역 43개 ──────────────────────────────────────────────
    // 열차가 서 있는 역(진입·도착·출발 = 역에 걸쳐 있는 자리)은 빨간 점.
    // ⚠ 복선에서는 **그 열차 방향의 선로에만** 빨갛다(지선 카드와 같은 규칙) — 내선이 선
    // 역을 외선 선로에도 빨갛게 찍으면 있지도 않은 정차를 그리게 된다.
    fun occupiedOf(rows: List<Pair<MainTrainMark, Float>>): Set<Int> = rows.mapNotNull { (_, p) ->
        val kf = p - start
        val k = kotlin.math.round(kf)
        if (abs(kf - k) <= 0.2f) ((k.toInt() % LOOP_N) + LOOP_N) % LOOP_N else null
    }.toSet()
    // 단선(방향 필터)은 종전대로 **한 벌** — 내 열차가 반대 방향이어도 그리므로 다 넣는다.
    val occOut = if (!dual) occupiedOf(trains) else occupiedOf(trains.filter { !it.first.inner })
    val occIn = if (!dual) occOut else occupiedOf(trains.filter { it.first.inner })

    /** 역 점 한 벌 — **선로마다** 찍는다(v1.7.4 복선. 지선 카드와 같은 규칙). */
    fun dots(lp: Loop, red: Set<Int>) {
        for (k in 0 until LOOP_N) {
            val (p, _) = lp.at(lp.sOf(k))
            val isRed = k in red
            // 신도림·성수는 지름 1.5배 + 흰 테두리 — 이름과 같은 주황이라 멀리서도 짚인다.
            // 복선에서는 **두 선로에 다** 찍는다(사용자 확정).
            val name = Line2Stations.MAIN[(k + start) % LOOP_N]
            val key = name in KEY_STATIONS
            val rad = (if (big) 5f else 4f).dp.toPx() * (if (key) 1.5f else 1f)
            // 클레이 역 점은 **흰 점토 단추**다 — 밑에 그림자를 한 겹 깔아야 크림 바탕에서 뜬다.
            if (pal.clay) drawCircle(
                pal.shadow, rad, p + screenDown(mapDeg, 2.dp.toPx()))
            drawCircle(
                if (isRed) pal.stationRed else if (key) pal.keyInk(name) else pal.station, rad, p)
            // 클레이는 **모든 역**에 테가 있다(흰 점이 크림에 묻히기 때문). 남색은 종전대로
            // 신도림·성수와 정차 역에만 — 흰 점 자체가 이미 또렷하다.
            if (key || isRed || pal.clay) drawCircle(
                when {
                    key -> Color.White
                    isRed -> Color.White.copy(alpha = 0.55f)
                    else -> pal.stationEdge
                },
                rad, p,
                style = Stroke(width = (if (key && pal.clay) 4f else 1.5f).dp.toPx()))
        }
    }
    dots(loop, occOut)
    if (dual) dots(loopIn, occIn)
    // ── 열차 자리를 **라벨보다 먼저** 잡는다 ────────────────
    val off = badgeOff(big).toPx()
    /** 그 방향의 **제 선로**. 단선이면 둘이 같은 객체라 방향을 안 가린다. */
    fun railOf(inner: Boolean) = if (dual && inner) loopIn else loop
    /**
     * (중심, **열차 쪽 방향**). 선로 쪽은 그 반대다 — 바퀴가 볼 쪽이 곧 `-out` 이다.
     *
     * ⚠ **단선(방향 필터)에서는 내선·외선이 같은 자리에 선다**(v1.6.98). 방향은 [headingFor]
     * 가 정하는 **머리**가 말하고, 자리는 늘 바퀴가 선로에 닿는 한 줄이다.
     *
     * ⚠ **복선(전체 보기)에서는 제 선로가 다르다**(v1.7.4). 자리도 접선도 [railOf] 가 준
     * **그 선로의 기하**에서 나오고, 세로 변에서만 내선이 [mainTrainSide] 의 `innerLane` 로
     * 루프 안쪽에 선다 — 그래야 바깥 선로 밖에 선 외선과 안 겹친다. 가로 변은 둘 다
     * 제 선로 **위**라 두 열차가 아래위로 나란히 선다(윗변 내선·아랫변 외선이 두 선로 사이).
     */
    fun spot(pos: Float, inner: Boolean): Pair<Offset, Offset> {
        val lp = railOf(inner)
        val (p, tan) = lp.at(lp.sOfPos(pos - start))
        val (ox, oy) = mainTrainSide(tan.x, tan.y, innerLane = dual && inner)
        return Offset(p.x + ox * off, p.y + oy * off) to Offset(ox, oy)
    }

    /*
     * ── 열차는 **모두** 증기기관차 (v1.6.91) ───────────────────────
     * v1.6.90 은 내 열차만 기관차였고 나머지는 네모 배지였다. 사용자 지적
     * *"신도림행 네모 아이콘은 왜 따로 다녀?"* — 같은 지도 안에서 열차가 두 모양으로 그려지면
     * 무엇이 열차인지가 흐려진다. 이제 **모양 = 열차** 하나뿐이고, 구분은 다음 둘이 맡는다:
     *   · **머리 = 진행 방향**(내선/외선이 각자의 접선을 본다 — [headingFor])
     *   · **색 = 신분**(내 열차 노랑·빨간 열번 / 일반 하늘·남색 열번)
     * 차선은 **한 줄뿐**이라 방향을 자리로 읽던 종전 규칙은 없다(v1.6.98).
     * 열번은 늘 몸통 안이다([Loco] 규칙 1).
     *
     * ⚠ **연기·물결은 내 열차만.** 전체 필터는 20대가 넘게 뜨는데 전부 연기를 내면 지도가
     * 지저분해진다(지선 카드는 열차가 적어 전부 낸다 — 거긴 다른 판이다).
     */
    /**
     * **모든 열차가 같은 배율**이다(v1.7.5) — 사용자: *"본인열차 크기도 다른열차 크기랑
     * 동일하게 해! 이미 색상을 다르게 했잖어!"* 내 열차만 [locoScale](1배)이던 것을
     * 남의 열차와 같은 `locoScale × otherK` 로 내렸다.
     *
     * ⚠ **크기로 내 열차를 말하지 않는다** — 노란 몸통·빨간 열번·흰 테·연기·갈매기·행선판이
     * 말한다. 여기를 다시 갈라 놓으면 [laneGap]·[trainPad] 계산이 통째로 어긋난다.
     */
    val trainScale = locoScale(big) * otherK
    // 접선은 **제 선로**에서 뽑는다 — 복선의 두 곡선은 동심이라 직선 변에서는 같지만
    // 모서리 호에서는 반지름이 달라 접는 자리가 조금씩 다르다(각자 제 곡률을 따라야 한다).
    fun headingAt(t: MainTrainMark, pos: Float): Heading {
        val lp = railOf(t.inner)
        val (_, tan) = lp.at(lp.sOfPos(pos - start))
        return headingFor(tan.x, tan.y, t.inner)
    }
    val heads = trains.associate { (t, pos) -> t.trainNo to headingAt(t, pos) }
    fun headOf(no: String) = heads[no] ?: Heading.RIGHT

    /*
     * ── 열차끼리 겹침 0 (v1.6.88) ────────────────────────────
     * 같은 차선에 열차가 몰리면 그대로 포개졌다(실측: 합정 모서리에 7366·8401·2403,
     * 사당 근처에 6378·2384·2382). 겹치면 **선로에서 더 멀어지는 쪽**(`out` — v1.6.98 부터
     * 가로 변은 위, 세로 변은 루프 바깥)으로 한 대 높이 + 2dp 씩 **최대 2단** 계단으로 민다.
     * **역 이름 쪽(선로 반대편)으로는 한 칸도 안 내려간다.** 그래도 자리가 없거나 계단이
     * 화면 밖으로 나가면 **아이콘을 접고 점만 남긴다** — 겹쳐 놓아 둘 다 못 읽게 하느니
     * 하나만 읽히는 편이 낫다.
     *
     * ⚠ 계단 폭은 기관차 **짧은 쪽**([LOCO_BOX_H])이면 된다 — 미는 방향(`out`)이 늘 선로에
     * 수직이고 기관차의 긴 축은 선로와 나란하기 때문이다(머리가 어느 쪽이든).
     *
     * 내 열차는 **맨 먼저** 자리를 잡아(빈 판이라 늘 0단) 밀리지도 숨지도 않는다. 그리기는
     * 여전히 맨 나중이라 무엇 위에도 얹힌다.
     * 필터(내선/외선)를 켜면 한 차선만 쓰니 계단이 거의 안 생긴다 — 전체 모드용 장치다.
     *
     * ⚠ **복선에서는 계단이 방향별로 따로 돈다**(v1.7.4). 내선·외선은 이제 **선로가 달라**
     * 애초에 못 겹치는데, 한 장부로 보면 서로를 장애물로 세어 **멀쩡한 0단을 헛되이 포기**한다
     * (윗변 내선이 바로 위 외선 상자에 걸려 두 단씩 올라가면 다시 떠 보인다). 그래서
     * 장부를 두 벌로 나눈다 — 단선이면 [rectsIn] 을 안 쓰므로 종전과 한 글자도 안 다르다.
     */
    val stepPx = LOCO_BOX_H * trainScale * 1.dp.toPx() + 2.dp.toPx()
    /** 바깥 선로(외선 · 단선이면 전부) 열차 상자. */
    val trainRects = mutableListOf<Rect>()
    /** 안쪽 선로(내선) 열차 상자 — **복선에서만** 쓴다. */
    val rectsIn = mutableListOf<Rect>()
    /** 그릴 열차 — (열차, 중심, 바깥 방향). 접힌 열차는 여기 없다. */
    val spots = mutableListOf<Triple<MainTrainMark, Offset, Offset>>()
    /** 탭 판정·툴팁이 쓸 중심 — 접힌 열차도 점 자리로 남는다. */
    val centers = mutableMapOf<String, Offset>()
    /** 계단으로 한 칸 이상 올라간 열차 — 발밑에 **받침선**을 깐다(v1.6.98). */
    val raised = mutableSetOf<String>()
    for ((t, pos) in trains.sortedByDescending { it.first.trainNo in mineNos }) {
        val isMine = t.trainNo in mineNos
        val head = headOf(t.trainNo)
        val (base, out) = spot(pos, t.inner)
        // 같은 선로에 선 열차끼리만 자리를 다툰다(위 KDoc).
        val lane = if (dual && t.inner) rectsIn else trainRects
        /*
         * ⚠ **캔버스 clamp 를 걷어냈다**(v1.6.96). v1.6.90 은 여기서 중심을 `coerceIn` 으로
         * 캔버스 안에 밀어 넣었는데, 내 열차는 상자가 지붕 위 행선판까지(32.5dp) 커서
         * 윗변·아랫변 외선에서 **16.5dp 나 안쪽으로 밀렸다** — 제 차선을 벗어나 초록 선을
         * 밟았다(v1.6.95 `R02` 실측: 노란 `2035` 가 선 위에 올라앉아 선을 가렸다).
         * 자리가 모자라면 열차를 미는 게 아니라 [trainPad] 를 늘린다(사용자 확정) — 이제
         * [trainPad] 가 그 상자를 계산해서 잡으므로 밀 일 자체가 없다.
         */
        centers[t.trainNo] = base
        for (s in 0..2) {
            val c = Offset(base.x + out.x * stepPx * s, base.y + out.y * stepPx * s)
            // 겹침 회피(계단·역명 SAT)가 보는 상자 — 이제 **전부 기관차 상자**이고,
            // 내 열차는 **지붕 위 행선판까지 한 상자**다(v1.6.91 — 역 이름을 물면 실패다).
            // 바퀴가 선로를 보므로 지붕은 늘 `out` 쪽이다 — 상자도 같은 벡터로 접는다.
            // ⚠ `board` 는 **판을 실제로 다는 열차만** 참이다(v1.7.5) — 행선을 모르면
            // 판을 안 다는데(myDestination) 참으로 두면 상자가 17dp 헛되이 커져 역 이름을 민다.
            val r = locoBox(c, head, trainScale,
                wake = isMine, board = isMine && mineBoards[t.trainNo] != null,
                railTowards = Offset(-out.x, -out.y))
            // 밀어낸 열차가 화면 밖으로 나가면 그 단은 없는 셈 친다(0단은 [margin] 이 챙긴다).
            if (s > 0 && (r.left < 0f || r.top < 0f ||
                    r.right > size.width || r.bottom > size.height)) continue
            if (lane.any { it.overlaps(r) }) continue
            lane += r
            spots += Triple(t, c, out)
            centers[t.trainNo] = c
            if (s > 0) raised += t.trainNo
            break
        }
    }

    // 라벨 글자 크기는 [labelSp] 하나가 정한다(보통 역은 [LABEL_STEP] 만큼 더 작다 — v1.6.96
    // 사용자 요청). 좁아도 더 줄이지 않고 **겹침 회피로만** 푼다.
    fun draw(lab: Lab) = rotate(lab.deg, pivot = lab.pivot) {
        val x = if (lab.leftAnchored) lab.pivot.x else lab.pivot.x - lab.layout.size.width
        val y = lab.pivot.y - lab.layout.size.height / 2f
        drawText(lab.layout, topLeft = Offset(x, y))
    }
    // 역 이름은 선로 **반대편**이라 열차 밑에 깔릴 일이 없다 — 한 번에 다 그린다(v1.6.98).
    // (모서리에서만 겹칠 수 있어 [trainRects] 는 여전히 장애물로 넘긴다 — `layoutLabels` KDoc.)
    // ⚠ 장애물은 **두 선로를 합쳐** 넘긴다 — 계단만 방향별로 도는 것이지, 이름은 어느 쪽
    // 열차든 물으면 안 된다(복선에서 좌·우변 내선이 루프 안쪽 = 이름 자리로 들어온다).
    layoutLabels(tm, loop, loopIn, start, labelSp, trainRects + rectsIn, pal, railW, sideLaneX)
        .forEach { draw(it) }

    /*
     * ── 계단으로 올라간 열차의 **받침선** (v1.6.98) ────────────
     * 겹침을 피해 선로에서 한 칸 물러난 열차는 그냥 두면 **허공에 뜬다**(사용자: *"선로위에
     * 열차 아이콘이 지나다니지 않는데? 떠다니는데?"*). 바퀴 밑에 선로와 나란한 짧은 초록
     * 선분을 깔아 **제 발판 위에 선** 그림으로 만든다. 0단(선로에 붙은 열차)에는 안 그린다 —
     * 진짜 선로가 이미 그 자리에 있다.
     */
    spots.filter { it.first.trainNo in raised }.forEach { (t, c, out) ->
        val half = (LOCO_LEN * trainScale * 0.5f).dp.toPx()
        val foot = (LOCO_BOX_H / 2f * trainScale).dp.toPx()
        // 바퀴 자리 = 중심에서 **선로 쪽**(-out)으로 반높이. 선분은 선로와 나란하다(out 에 수직).
        val w = Offset(c.x - out.x * foot, c.y - out.y * foot)
        val ax = -out.y
        val ay = out.x
        drawLine(if (pal.clay) pal.railBottom else pal.rail, Offset(w.x - ax * half, w.y - ay * half),
            Offset(w.x + ax * half, w.y + ay * half),
            strokeWidth = (if (big) 3f else 2.5f).dp.toPx(), cap = StrokeCap.Round)
    }

    // ── 열차 ─────────────────────────────────────────────────
    // 아이콘을 접은 열차도 **점은 남으므로** 탭 판정은 전원을 넣는다.
    val hits = trains.map { (t, _) -> centers.getValue(t.trainNo) to t.trainNo }
    /*
     * ⚠ 3단이 다 막혀 **접힌 열차도 선 위에 점을 남긴다**(v1.6.93 — 지선 카드의 `dotOnly`
     * 상당물). 종전엔 [spots] 에서 빠진 열차가 아무것도 안 그려지는데 [hits] 에는 그대로
     * 남아 있어, **아무것도 없는 자리를 누르면 안 보이는 열차의 툴팁이 떴다.**
     * 점이라도 있으면 "저기 한 대 더 있다"가 보이고 탭 판정과 그림이 다시 일치한다.
     */
    val drawnNos = spots.mapTo(HashSet()) { it.first.trainNo }
    trains.forEach { (t, _) ->
        // ⚠ 클레이는 **몸통색이 아니라 열번색**으로 찍는다 — 흰 몸통 점은 크림 바탕에서
        // 아예 안 보인다(v1.7.0 실측: 남색에서는 하늘색 점이 잘 보이던 자리다).
        if (t.trainNo !in drawnNos) drawCircle(
            when {
                t.trainNo in mineNos -> if (pal.clay) pal.mineInk else pal.mineBody
                // 열번색(빨강)은 정차 빨간 점과 헷갈린다 — 바퀴색이 "너무 작아 못 그린 열차"로 읽힌다.
                pal.clay -> pal.wheel
                else -> pal.otherBody
            },
            2.5.dp.toPx(), centers.getValue(t.trainNo))
    }
    // ⚠ **내 열차는 맨 나중에** 그린다 — 다른 열차에 가리면 "표시가 안 된다"는 말이 된다.
    val (mineRows, others) = spots.partition { it.first.trainNo in mineNos }
    // 다른 열차 — 같은 기관차, **몸통만 짧고**(otherK) 연기·물결은 없다.
    // `railTowards = -out` — 바퀴는 늘 선로 쪽이다(v1.6.96 규칙 4).
    /*
     * ⚠ **클레이 그림자 밀도 가드**(v1.7.0). 그리는 열차가 [CLAY_SHADOW_MAX] 를 넘으면 남의
     * 열차는 그림자 **한 겹**이다 — 전체 보기에 20~30대가 뜨면 두 겹은 서로 겹쳐 뭉개지고
     * 프레임도 먹는다(시안이 지목한 "구려질 자리" 첫째). 내 열차는 늘 두 겹이라 그래도 뜬다.
     */
    val otherShadow = if (!pal.clay) 0 else if (spots.size > CLAY_SHADOW_MAX) 1 else 2
    others.forEach { (t, c, out) ->
        drawLoco(c, headOf(t.trainNo), trainScale, pal.otherBody, pal.wheel, t.trainNo,
            pal.otherInk, tm,
            smoke = false, railTowards = Offset(-out.x, -out.y), mapDeg = mapDeg,
            bodyRamp = if (pal.clay) pal.otherTop to pal.otherBottom else null,
            edge = pal.otherEdge, smokeColor = pal.smoke, shadowColor = pal.shadow,
            clayShadow = otherShadow)
    }
    mineRows.forEach { (t, c, out) ->
        /*
         * ⚠ 종전 노란 배지의 **펄스 테두리는 없앴다.** 반투명 노랑을 남색 위에 깔면 기관차
         * 뒤에 **탁한 회색 상자**가 생겨(실측) 도리어 열번이 흐려졌다. 내 열차는 노란 몸통 +
         * 외곽 2겹 + 연기·물결 + 지붕 위 행선판으로 이미 남의 열차와 안 헷갈린다.
         *
         * 행선은 **지붕 위 행선판**이다(v1.6.91 사용자 확정 — *"열차 아이콘 위에 … 왜 따로
         * 노냐?"*). 깃대에 매달아 빈자리를 찾아다니던 깃발은 없앴다: 열차와 따로 놀았고,
         * 여덟 후보가 다 막히면 결국 역 이름을 물었다. 이제 기관차와 **한 몸**이라 자리를
         * 고를 일이 없고, 회피는 위에서 [locoBox] `board = true` 한 상자로 이미 넘어갔다.
         */
        drawLoco(c, headOf(t.trainNo), trainScale, pal.mineBody, pal.wheel, t.trainNo,
            pal.mineInk, tm,
            smoke = true, phase = phase, highlight = true,
            railTowards = Offset(-out.x, -out.y), mapDeg = mapDeg,
            dest = mineBoards[t.trainNo].orEmpty(),
            bodyRamp = if (pal.clay) pal.mineTop to pal.mineBottom else null,
            edge = if (pal.clay) pal.mineRing else null, ring = pal.mineRing,
            smokeColor = pal.smoke, shadowColor = pal.shadow,
            clayShadow = if (pal.clay) 2 else 0)
    }

    /*
     * ── 그릴 열차가 없어도 **지도 위에는 아무 글자도 얹지 않는다** (v1.6.94) ─────────────
     * v1.6.93 은 여기서 가운데에 반투명 판 + 두 줄을 그렸는데, 순환선 안쪽은 역 이름이 가장
     * 빽빽한 자리라 동대문역사문화공원·을지로4가·신당·방배를 통째로 덮었다(실화면 확인).
     * 게다가 왼쪽 상태 칩이 같은 문장을 이미 말해 **한 화면에 두 번** 나왔다.
     * 빈 상태는 [mainEmptyReason] → [CabStatusBar] 칩 **한 줄**이 전담한다.
     */

    // ── 탭한 열차의 툴팁 (열차보다 나중에 그려 위에 얹힌다) ──
    picked?.let { no -> centers[no]?.let { c ->
        trains.firstOrNull { it.first.trainNo == no }?.let { (t, _) ->
            drawTip(tm, c, t, no in mineNos, mineBoards[no], pal)
        }
    } }
    return hits
}

/**
 * 탭한 열차의 툴팁 — 열번 · 다음역 · 행선 · 내/외선.
 *
 * ⚠ **내 열차 행선은 [myDestination] 값**([mineDest])이고 모르면 **아예 안 적는다**(v1.7.5) —
 * 행선판이 안 다는 말을 툴팁이 `성수행` 이라고 하면 같은 화면이 두 말을 한다.
 * 남의 열차는 견줄 행로표가 없으니 종전대로 API 값을 그대로 옮긴다.
 */
private fun DrawScope.drawTip(
    tm: TextMeasurer, c: Offset, t: MainTrainMark, mine: Boolean, mineDest: String?,
    pal: MapPalette,
) {
    val line1 = t.trainNo + "  " + (if (t.inner) "내선" else "외선") + (if (mine) "  · 내 열차" else "")
    val dest = if (mine) mineDest else t.destName.takeIf { it.isNotBlank() }?.plus("행")
    val line2 = t.statusText + dest?.let { " · $it" }.orEmpty()
    val l1 = tm.measure(line1, TextStyle(fontSize = 13.sp,
        fontWeight = FontWeight.ExtraBold, color = if (mine) pal.mineText else pal.title))
    val l2 = tm.measure(line2, TextStyle(fontSize = 12.sp, color = pal.info))
    val w = maxOf(l1.size.width, l2.size.width) + 22.dp.toPx()
    val h = l1.size.height + l2.size.height + 15.dp.toPx()
    val x = (c.x - w / 2f).coerceIn(0f, (size.width - w).coerceAtLeast(0f))
    val y = (c.y - h - 16.dp.toPx()).coerceAtLeast(0f)
    drawRoundRect(pal.tipBg, topLeft = Offset(x, y), size = Size(w, h),
        cornerRadius = CornerRadius(10.dp.toPx()))
    drawRoundRect(
        if (pal.clay) pal.stationEdge else pal.otherBody.copy(alpha = 0.55f),
        topLeft = Offset(x, y), size = Size(w, h),
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
