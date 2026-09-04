package com.sinjeong.crewcalendar.presentation.live

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.min

/*
 * 옆모습 **증기기관차**(은하철도 999) 한 장 — 본선 지도와 지선 카드가 **같이 쓴다**.
 * 모양을 고칠 일이 있으면 여기 한 곳만 고친다.
 *
 * 사용자(기관사) 요청 원문:
 *   *"본인 열차와 신정지선 신도림행 가는 열차만이라도 은하철도999로 만들어줘! 방향이 헷갈려!"*
 *   *"열차 아이콘 안에 열번이 있어야 되는 규칙은 알지?"*
 *
 * ## 규칙 둘 — 둘 다 사용자 확정이라 되돌리지 말 것
 *
 * 1. **열번은 아이콘 *안*에 있다.** 이 앱에서 열차 표시는 늘 "열번이 들어 있는 상자"였다
 *    (열번 배지). 기관차도 예외가 아니라 **몸통 안**에 열번을 넣는다.
 *    기관차 밑에 배지를 따로 다는 것은 **틀린 규칙**이다 — 상자가 둘이 되어 무엇이 열차인지
 *    흐려지고, 겹침 회피가 잡아야 할 상자도 둘이 된다.
 * 2. **머리(앞코)가 곧 진행 방향이다.** 이 그림을 넣은 이유가 *"방향이 헷갈려!"* 하나뿐이라
 *    머리가 반대로 돌면 기능이 통째로 거짓말이 된다. 방향은 [headingFor] 한 곳에서만 정하고
 *    [LocoTest] 가 다섯 경우 + 지선을 잠근다.
 * 3. **행선은 지붕 위 행선판**(v1.6.91). *"열차 아이콘 위에 지금 보다 한단계 작게 올려라..
 *    왜 따로 노냐?"* — 깃대 끝에 매달려 빈자리를 찾아다니던 깃발은 없앴다. 판은 몸통에
 *    붙어 **한 몸으로** 움직이고, 회피 상자도 [locoBox] `board = true` 한 상자로 넘어간다.
 * 4. **바퀴는 늘 선로를 본다**(v1.6.96). *"외선,내선에서보면 바퀴가 선로쪽으로 안되어있는
 *    열차 아이콘이 있는데?"* — 본선 외선은 바퀴가 루프 안쪽, 내선은 바깥쪽이고, 지선은 늘
 *    아래(제 선로)다. 판정은 [locoFlip] 한 곳이고 머리 방향은 그대로 지킨다.
 *
 * ## 왜 회전이 아니라 [Heading] 네 칸인가
 *
 * 순환선 접선은 연속이지만 **글자는 연속으로 못 눕힌다** — 37° 기울어진 열번은 못 읽는다.
 * 그래서 네 방향으로 접는다: 좌우는 **미러**(글자는 똑바로), 위아래는 **90° 회전**(글자도 몸통
 * 따라 돈다 — 거꾸로 서지는 않게 위는 −90°, 아래는 +90°).
 * 세로 화면에서는 지도 **전체가** `rotationZ = 90f` 로 돌아가므로([MainLineMapDialog]) 여기서
 * 따로 손댈 것이 없다 — 초록 선과 기관차가 같이 돌아 관계가 안 변한다.
 */

/** 그림 좌표 한 칸 = 1dp × `scale`. 몸통 길이 46 · 열번 띠 높이 16 — 4자리가 들어가는 폭이다. */
internal const val LOCO_LEN = 46f
internal const val LOCO_H = 16f

/**
 * 겹침 회피가 쓸 **상자** — 굴뚝과 바퀴까지 다 들어간다(연기는 뺀다. 반투명 장식이다).
 * 그림은 `x −24…+23` · `y −15.2…+13.9` 에 들어가므로 넉넉히 대칭 상자로 잡는다.
 */
internal const val LOCO_BOX_W = 48f
internal const val LOCO_BOX_H = 31f

/**
 * 진행 방향 물결(갈매기 3개)이 **앞코 너머로** 뻗는 길이(v1.6.91).
 * 상자를 **앞쪽으로만** 이만큼 늘린다 — 대칭으로 늘리면 상자가 63dp 가 되어 배지가 줄줄이
 * 접힌다. 늘리는 쪽은 [locoBox] `wake = true` 를 준 곳(본선 지도 = 역 이름이 있는 곳)뿐이다.
 */
internal const val LOCO_WAKE = 15f

/**
 * 지붕 위 **행선판**([drawLoco] `dest`)이 상자 위로 더 먹는 높이(v1.6.91).
 * 글자(8.5sp × scale) + 위아래 여백 + 내 열차 외곽 2겹(2.7dp)까지 잡은 **넉넉한 상한**이다.
 * 상자는 장애물이라 조금 커도 해가 없지만 **모자라면 역 이름을 문다** — 그래서 넉넉히 잡는다.
 * 늘어나는 쪽은 **지붕 쪽 한쪽뿐**이라 상자가 위아래로도 비대칭이다([locoHalf]).
 */
internal const val LOCO_BOARD_H = 17f

/** 기관차 머리가 보는 쪽. 화면 좌표라 [DOWN] 이 y 가 커지는 쪽이다. */
internal enum class Heading { RIGHT, LEFT, UP, DOWN }

/**
 * **인덱스가 커지는 쪽의 접선** + 진행 방향 → [Heading]. 순수 함수 — 안드로이드가 안 낀다.
 *
 * @param tx,ty 인덱스가 커지는 쪽(본선 = 내선 = 시계, 지선 = 신도림행)의 접선. y 는 아래가 양수.
 * @param forward 그 방향으로 달리면 true(본선 내선 · 지선 신도림행), 반대면 false.
 *
 * 본선 루프: 윗변 내선 → [RIGHT] · 윗변 외선 → [LEFT] · 아랫변 내선 → [LEFT] ·
 * 오른쪽 변 내선 → [DOWN] · 왼쪽 변 내선 → [UP]. **모서리 호는 긴 쪽 성분이 이겨** 가까운
 * 변과 같은 방향으로 떨어진다. 지선은 신도림이 오른쪽 끝이라 신도림행이 [RIGHT] 다.
 */
internal fun headingFor(tx: Float, ty: Float, forward: Boolean): Heading {
    val d = if (forward) 1f else -1f
    val dx = tx * d
    val dy = ty * d
    return if (abs(dx) >= abs(dy)) {
        if (dx >= 0f) Heading.RIGHT else Heading.LEFT
    } else {
        if (dy >= 0f) Heading.DOWN else Heading.UP
    }
}

/**
 * 안 뒤집은 몸통의 **배(바퀴)가 가는 쪽** — 지도 좌표. [drawLoco] 의 `p()` 표와 한 벌이라
 * 둘 중 하나만 고치면 안 된다. 순수 함수 — [LocoTest] 가 잠근다.
 */
internal fun locoBelly(heading: Heading): Pair<Float, Float> = when (heading) {
    Heading.RIGHT, Heading.LEFT -> 0f to 1f
    Heading.DOWN -> -1f to 0f
    Heading.UP -> 1f to 0f
}

/**
 * **바퀴는 늘 선로를 본다** — 몸통을 긴 축으로 **뒤집어야** 하는가(v1.6.96 사용자 확정).
 *
 * > *"외선,내선에서보면 바퀴가 선로쪽으로 안되어있는 열차 아이콘이 있는데?"*
 *
 * ## ⚠ v1.6.91 의 `locoFlip(heading, mapDeg)` 이 틀렸던 이유
 *
 * 종전 판단 기준은 *"화면에서 배가 하늘을 보는가"* 였다. 글자가 거꾸로 서는 것만 보고 만든
 * 기준이라 **바퀴가 어느 쪽에 있어야 하는지(= 선로 쪽)** 를 아예 안 봤다. 그래서 배가 늘
 * **한 화면 방향**(세로 화면 기준 왼쪽/아래)으로만 향했고, 선로가 반대쪽에 있는 차선에서는
 * 바퀴가 허공을 봤다 — 여덟 경우 중 **넷**이 틀렸다(v1.6.95 `R02` 실측: 화면 왼쪽 세로변
 * 외선 `2021`·아래 가로변 외선 `3009` 가 바퀴를 선로 반대쪽으로 들었다).
 *
 * 규칙은 이제 하나다: **바퀴 = 선로 쪽.** 본선 외선(루프 바깥 차선)은 바퀴가 루프 안쪽으로,
 * 내선(안쪽 차선)은 바깥쪽으로 간다. 머리 방향([headingFor])은 그대로다 — 긴 축 대칭(거울)
 * 이라야 머리를 지킨 채 배가 돌아눕는다(좌우 미러가 이미 쓰는 그 수법이다).
 *
 * 글자는 이 뒤집기와 **무관하게** [locoTextDeg] 가 화면 기준으로 바로 세운다 — v1.6.91 이
 * 고친 "거꾸로 찍힌 `8509`" 는 그쪽이 잠그고 있으므로 여기서 빠져도 되살아나지 않는다.
 *
 * @param railX,railY 기관차 중심에서 **선로 쪽**을 가리키는 벡터(지도 좌표. y 는 아래가 +).
 *   본선은 제 차선 바깥 방향의 **반대**(`-out`), 지선은 기관차가 늘 선로 위에 앉으므로 `(0, +1)`.
 *
 * 순수 함수 — [LocoTest] 가 여덟 경우 + 지선 둘을 잠근다.
 */
internal fun locoFlip(heading: Heading, railX: Float, railY: Float): Boolean {
    val (bx, by) = locoBelly(heading)
    return bx * railX + by * railY < 0f     // 배가 선로 반대쪽을 보면 뒤집는다
}

/**
 * 열번·행선판 글자의 회전각. **화면에 얹은 뒤(`+ mapDeg`) 거꾸로 서지 않는** 값을 고른다 —
 * 위아래 90°(옆으로 눕는 것)는 역 이름과 같은 사정이라 그대로 두고, 90°를 넘으면 180°를 더한다.
 * 순수 함수 — [LocoTest] 가 잠근다.
 */
internal fun locoTextDeg(heading: Heading, mapDeg: Float): Float {
    val base = when (heading) {
        Heading.UP -> -90f
        Heading.DOWN -> 90f
        else -> 0f
    }
    var s = (base + mapDeg) % 360f
    if (s > 180f) s -= 360f
    if (s <= -180f) s += 360f
    return if (abs(s) > 90f) base + 180f else base
}

/**
 * 상자의 네 반지름(**왼·위·오른·아래**, dp 단위) — 안드로이드가 안 끼는 **순수 함수**라
 * [LocoTest] 가 잠근다. 머리 방향에 따라 앞뒤(`wake`)·지붕(`board`)이 어느 화면 방향으로
 * 늘어나는지가 이 표 하나로 정해진다. 틀리면 **역 이름 위에 행선판이 얹힌다.**
 *
 * @param flip [locoFlip] — 뒤집힌 몸통은 지붕과 배가 맞바뀐다.
 */
internal fun locoHalf(
    heading: Heading, wake: Boolean, board: Boolean, flip: Boolean = false,
): FloatArray {
    val back = LOCO_BOX_W / 2f
    val front = back + if (wake) LOCO_WAKE else 0f
    val side = LOCO_BOX_H / 2f
    val roofH = side + if (board) LOCO_BOARD_H else 0f
    val roof = if (flip) side else roofH      // 지붕 쪽
    val belly = if (flip) roofH else side     // 배 쪽
    return when (heading) {           // 제 몸 −y(지붕)가 가는 화면 방향 = roof 자리
        Heading.RIGHT -> floatArrayOf(back, roof, front, belly)
        Heading.LEFT -> floatArrayOf(front, roof, back, belly)
        Heading.DOWN -> floatArrayOf(belly, back, roof, front)
        Heading.UP -> floatArrayOf(roof, front, belly, back)
    }
}

/**
 * 기관차 한 장이 실제로 먹는 자리 — 배지 계단·역명 회피가 이 상자를 장애물로 쓴다.
 *
 * @param wake true = 앞쪽 물결([LOCO_WAKE])까지 넣는다. **앞쪽으로만** 늘어난다.
 * @param board true = 지붕 위 행선판([LOCO_BOARD_H])까지 넣는다. **지붕 쪽으로만** 늘어난다.
 *   기관차와 행선판은 **한 몸**이므로 회피도 한 상자로 넘긴다(v1.6.91 사용자 확정).
 * @param railTowards 선로 쪽 단위벡터([drawLoco] 와 **같은 값**을 줘야 한다) — 뒤집힌 몸통은
 *   지붕이 반대쪽으로 뻗으므로 상자도 따라 뒤집힌다.
 *
 * 둘 다 한쪽으로만 늘어나므로 상자는 **앞뒤·위아래가 다르다** — 쓰는 쪽에서 `left`/`right`/
 * `top`/`bottom` 을 따로 봐야 한다.
 */
internal fun DrawScope.locoBox(
    center: Offset, heading: Heading, scale: Float,
    wake: Boolean = false, board: Boolean = false,
    railTowards: Offset = Offset(0f, 1f),
): Rect {
    val u = scale * 1.dp.toPx()
    val h = locoHalf(heading, wake, board, locoFlip(heading, railTowards.x, railTowards.y))
    return Rect(
        center.x - h[0] * u, center.y - h[1] * u, center.x + h[2] * u, center.y + h[3] * u)
}

/**
 * 몸통색을 흰색/검정 쪽으로 [t] 만큼 섞는다 — 입체감(v1.6.91)의 밝기 축 하나뿐이다.
 * HSL 로 돌지 않는 이유: 노랑(`#FFE14D`)과 하늘(`#A9DCF5`) 둘 다 채도가 높아 흰·검 혼합만으로
 * 충분히 위아래가 갈리고, 색상이 안 돌아 **같은 열차 색으로 읽힌다**.
 */
private fun Color.mix(o: Color, t: Float) = Color(
    red + (o.red - red) * t, green + (o.green - green) * t, blue + (o.blue - blue) * t, alpha)

/**
 * 증기기관차 한 장 — 보일러 몸통 + 뒤쪽 운전실 + 굴뚝 + 바퀴 3개 + 앞코(배장기).
 * **열번은 보일러·운전실 가운데**에 굵게 박힌다(위 규칙 1).
 *
 * @param center 열차가 있는 자리 = 그림 한가운데
 * @param heading 머리가 보는 쪽([headingFor])
 * @param scale 1f = 46×16dp 몸통. 폴드 펼침은 54dp 라 `54/46`
 * @param body 몸통 색 — 내 열차 노랑(`#FFE14D`), 지선 신도림행 하늘(`#A9DCF5`)
 * @param wheel 바퀴·창·대차 색(남색)
 * @param number 열번 4자리
 * @param numberColor 열번 글씨색 — 노랑 몸통엔 빨강, 하늘 몸통엔 남색
 * @param smoke 굴뚝 위 흰 연기 3점(v1.6.91 — 2점·몸통색은 실화면에서 거의 안 보였다)
 * @param wake 앞코 너머 진행 방향 갈매기 3개. **연기와 함께 내 열차·지선 전용**이다 —
 *   본선 전체 필터는 20대가 넘게 뜨는데 전부 연기를 내면 지도가 지저분해진다(v1.6.91 사용자 확정).
 * @param phase 0~1 위상. 연기가 떠오르고 물결이 앞으로 흐르는 눈금이다.
 *   **지도 한 장에 하나만** 만들어 인자로 내린다(열차마다 트랜지션을 만들면 프레임이 죽는다).
 * @param highlight 내 열차 — 몸통 바깥에 흰 테 + 노란 테 2겹
 * @param dest 지붕 위 **행선판** 글자(`"성수행"`). 빈 문자열이면 안 단다. 아래 행선판 절을 보라.
 * @param smokeK 연기가 떠오르는 **길이 배수**. 지선 카드 **아래 차선**(까치산행)은 0.5 —
 *   기관차 위가 바로 역 이름 줄이라 제 길이로 오르면 글자에 흰 점이 얹힌다(v1.6.91 실측).
 * @param railTowards 기관차 중심에서 **선로 쪽**(지도 좌표). 바퀴가 늘 이쪽을 보도록
 *   몸통을 뒤집는다([locoFlip], v1.6.96). ⚠ [locoBox] 에 **같은 값**을 줘야 회피 상자가
 *   그림과 맞는다. 기본값 `(0, +1)` = 선로가 밑에 있다(지선 카드가 늘 그렇다).
 * @param mapDeg 지도 **전체 회전**(세로 화면 90f — [MainLineMapDialog] 의 `rotationZ`).
 *   **글자를 화면 기준으로 바로 세우는 데만** 쓴다([locoTextDeg]). 몸통 뒤집기는 v1.6.96
 *   부터 [railTowards] 가 정한다.
 */
internal fun DrawScope.drawLoco(
    center: Offset,
    heading: Heading,
    scale: Float,
    body: Color,
    wheel: Color,
    number: String,
    numberColor: Color,
    textMeasurer: TextMeasurer,
    smoke: Boolean,
    wake: Boolean = smoke,
    phase: Float = 0f,
    highlight: Boolean = false,
    dest: String = "",
    smokeK: Float = 1f,
    railTowards: Offset = Offset(0f, 1f),
    mapDeg: Float = 0f,
) {
    val u = scale * 1.dp.toPx()
    /** 배가 선로 반대쪽을 보면 긴 축으로 뒤집는다 — 머리는 지킨 채 바퀴가 선로로 내려간다. */
    val flip = locoFlip(heading, railTowards.x, railTowards.y)

    /**
     * **제 몸 좌표 → 화면 좌표.** 머리가 +x, 아래가 +y 인 오른쪽 보기 그림 하나만 적고
     * 여기서 접는다. 네 변환 모두 90도의 배수(또는 미러)라 축정렬 상자가 축정렬로 남는다 —
     * 그래서 `drawRoundRect` 를 그대로 쓸 수 있고 변환 스택을 안 쌓아도 된다.
     */
    fun p(x: Float, y0: Float): Offset {
        val y = if (flip) -y0 else y0   // 긴 축 대칭 — 좌우 미러와 같은 수법이다
        return when (heading) {
            Heading.RIGHT -> Offset(center.x + x * u, center.y + y * u)
            Heading.LEFT -> Offset(center.x - x * u, center.y + y * u)   // 좌우 반전
            Heading.DOWN -> Offset(center.x - y * u, center.y + x * u)   // +90°
            Heading.UP -> Offset(center.x + y * u, center.y - x * u)     // −90°
        }
    }

    fun box(x0: Float, y0: Float, x1: Float, y1: Float, c: Brush, r: Float = 0f) {
        val a = p(x0, y0)
        val b = p(x1, y1)
        drawRoundRect(
            c,
            topLeft = Offset(min(a.x, b.x), min(a.y, b.y)),
            size = Size(abs(b.x - a.x), abs(b.y - a.y)),
            cornerRadius = CornerRadius(r * u),
        )
    }

    /*
     * ── 입체감 (v1.6.91) ──────────────────────────────────────────
     * 사용자: *"기관차 3d 오브젝트로 바꿔죠"* — 평면 색 상자는 남색 배경에서 스티커로 보였다.
     * 실물을 흉내 내지 않고 **빛 하나**만 준다. 넷 다 제 몸 좌표라 열차가 어느 쪽으로 돌아도
     * 지붕이 밝고 배가 어둡다(뒤집힌 몸통도 [p] 가 같이 접어 준다):
     *   ① 몸통·운전실·굴뚝 **세로 그라데이션**(지붕 밝게 → 배 어둡게)
     *   ② 보일러 윗면 **하이라이트 띠**(흰 0.6, 몸통 높이의 1/6)
     *   ③ 바퀴 아래 **바닥 그림자 타원**(검정 0.35, 진행축으로 납작)
     *   ④ 몸통 외곽 **1dp 어두운 테**
     * ⚠ 그라데이션 **폭**(±0.26/0.24)은 실화면 크롭으로 맞춘다. 넓히면 남색 배경에서 노란
     * 몸통 아랫배가 갈색으로 죽고, 좁히면 도로 평면 스티커가 된다.
     */
    val bodyBrush = Brush.linearGradient(
        listOf(body.mix(Color.White, 0.26f), body, body.mix(Color.Black, 0.24f)),
        start = p(0f, -15.2f), end = p(0f, 9.8f),
    )
    val edgeBrush = SolidColor(body.mix(Color.Black, 0.38f))
    val solid = SolidColor(body)

    /*
     * ── 지붕 위 행선판 (v1.6.91) — 자리만 먼저 잰다 ────────────────
     * 사용자: *"야~ 성수행은 열차 아이콘 위에 지금 보다 한단계 작게 올려라..왜 따로 노냐?"*
     *
     * v1.6.90~91 초반의 행선 깃발은 **깃대 끝에 매달린 따로 노는 상자**였다. 여덟 후보 중
     * 빈자리를 찾아다니느라 열차와 따로 놀았고, 자리를 다 놓치면 결국 역 이름을 물었다.
     * 이제 실제 열차 앞에 다는 **행선판처럼 지붕에 붙어** 기관차와 한 몸으로 움직인다.
     *  · 글자는 열번보다 **한 단계 작게**(9.5 → 8.5), 판은 몸통색 · 글씨는 열번색.
     *  · 폭 = 글자 + 좌우 3dp, 몸통 길이의 **1.3배**를 안 넘는다.
     *  · **열번은 안 가린다** — 판은 몸통 위, 열번은 몸통 안 그대로다.
     *  · 회피 상자도 [locoBox] `board = true` 한 상자로 같이 넘어간다.
     */
    val plate = if (dest.isBlank()) null else textMeasurer.measure(
        dest,
        TextStyle(
            fontSize = (8.5f * scale).sp, fontWeight = FontWeight.ExtraBold, color = numberColor,
        ),
    )
    val plateH = if (plate == null) 0f else plate.size.height / u + 2f
    val plateW = if (plate == null) 0f else
        (plate.size.width / u + 6f).coerceAtMost(LOCO_LEN * 1.3f)
    /** 지붕 = 굴뚝 갓 꼭대기. 내 열차는 **외곽 2겹(2.7dp)까지 지난** 자리라야 판이 안 겹친다. */
    val roofY = if (highlight) -17.9f else -15.2f
    val plateTop = roofY - plateH

    /*
     * ── 연기 (v1.6.91) ────────────────────────────────────────────
     * 사용자: *"지선 열차 은하철도999면 연기가 희미하게 나야 하지 않아?"*
     * 종전 2점은 **몸통색 반투명**이라 남색 위에서 거의 안 보였다(하늘색 0.35 = 남색과 대비 없음).
     * 흰색으로 바꾸고 3점으로 늘렸다 — 굴뚝 위에서 태어나 12dp 떠오르며 커지고(1.6→3.6dp)
     * 옅어지다 사라진다. 뒤로 흘려(x −3) 앞쪽 물결과 안 겹치게 한다.
     */
    if (smoke) for (i in 0 until 3) {
        val v = (phase + i / 3f) % 1f
        // 마지막 25% 에서만 꺼진다 — 그 사이 다음 점이 굴뚝에서 올라와 끊긴 데가 없다.
        val a = (0.55f - 0.30f * v) * ((1f - v) / 0.25f).coerceAtMost(1f)
        // ⚠ 시작 높이 −20 은 **외곽 2겹(+2.7dp)까지 지난 자리**다. 실측에서 −17 로 뒀더니
        // 첫 점이 노란 테 밑에 깔려 세 점 중 하나만 보였다. 12dp 를 떠오르며 서로 벌어진다
        // (6dp 였을 때는 반지름이 겹쳐 한 덩어리로 보였다).
        // 행선판을 달면 **판 위에서** 태어난다 — 판 뒤에서 나오면 굴뚝 연기로 안 읽힌다.
        val y0 = if (plate == null) -20f else plateTop - 2f
        drawCircle(
            Color.White.copy(alpha = a), (1.6f + 2.0f * v * smokeK) * u,
            p(8.5f - 3f * v, y0 - 12f * smokeK * v),
        )
    }

    /*
     * ③ 바닥 그림자 — 바퀴 **밑**에 깔리므로 몸통보다 먼저 그린다. 진행축(제 몸 x)으로 길고
     * 납작해서 열차가 바닥에 닿아 보인다. 상자([LOCO_BOX_H] 반높이 15.5) 안에 들어간다.
     */
    run {
        val a = p(-25.5f, 10.2f)
        val b = p(18.5f, 15f)
        drawOval(
            Color.Black.copy(alpha = 0.35f),
            topLeft = Offset(min(a.x, b.x), min(a.y, b.y)),
            size = Size(abs(b.x - a.x), abs(b.y - a.y)),
        )
    }

    /** 몸통 실루엣 한 벌. [g] 만큼 부풀려 그리면 그대로 **바깥 테**가 된다. */
    fun shell(g: Float, c: Brush) {
        box(4.8f - g, -15.2f - g, 12.2f + g, -13.6f + g, c, 0.6f)  // 굴뚝 갓
        box(6f - g, -14f - g, 11f + g, -8f + g, c)                 // 굴뚝
        box(-24f - g, -14f - g, -8.5f + g, -12f + g, c, 1f)        // 운전실 지붕(양쪽으로 조금)
        box(-23f - g, -12.5f - g, -9.5f + g, 8f + g, c, 1.5f)      // 운전실 — 몸통보다 높다
        box(-11f - g, -8f - g, 16f + g, 8f + g, c, 4f)             // 보일러 몸통
        // 앞코(배장기) — 여기가 **진행 방향**이다.
        drawPath(
            Path().apply {
                val a = p(16f - g, -7f - g)
                val b = p(23f + g * 1.8f, 0.5f)
                val d = p(16f - g, 8f + g)
                moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(d.x, d.y); close()
            },
            c,
        )
    }
    /*
     * 내 열차 외곽 **2겹** (v1.6.91) — 노란 테(1.2dp) 바깥, 흰 테(1.5dp) 안, 그 안이 몸통.
     * ⚠ v1.6.90 에서 걷어낸 **반투명 펄스 링을 되살린 것이 아니다.** 반투명 노랑을 남색 위에
     * 깔면 탁한 회색 상자가 됐다 — 여기는 **불투명 테 두 줄**이라 회색이 안 생긴다(실측 확인).
     * ⚠ 테가 [LOCO_BOX_H] 반높이(15.5dp)를 2.4dp 넘어선다 — 회피 상자보다 살짝 크지만
     *   불투명 테라 글자를 지우지는 않는다(상자를 키우면 배지가 줄줄이 접힌다).
     */
    if (highlight) { shell(2.7f, solid); shell(1.5f, SolidColor(Color.White)) }
    // ④ 어두운 1dp 테 — 내 열차는 **안 두른다**. 노랑·흰 두 겹이 이미 윤곽이라, 사이에 끼우면
    // 흰 테가 0.5dp 로 눌려 두 겹이 한 줄로 보인다(실측).
    if (!highlight) shell(1f, edgeBrush)
    shell(0f, bodyBrush)
    // ② 보일러 윗면 하이라이트 띠 — 몸통 높이(16)의 1/6 ≈ 2.7. **열번 띠 위쪽**이라 글자와
    // 자리를 안 다투고, 어차피 열번은 맨 나중에 그려 무엇에도 안 진다.
    box(-8.5f, -7.4f, 14f, -4.7f, SolidColor(Color.White.copy(alpha = 0.6f)), 1.3f)
    // ⚠ 운전실 창은 **열번 띠(y −8…+8) 위쪽**에만 둔다 — 띠 안에 두면 남색 창이 열번을 삼킨다.
    box(-20.5f, -11.5f, -12f, -8.8f, SolidColor(wheel), 0.6f)
    box(-21.5f, 8f, 17f, 9.8f, SolidColor(wheel))    // 대차 프레임
    /*
     * 동륜 3개 — v1.6.96 에서 반지름을 **30% 줄였다**(3.6 → 2.5. 사용자: *"전체 바퀴를 줄이고
     * 열차번호를 조금 더 크게 해줘!"*). 바퀴가 작아진 만큼 열번 띠가 눈에 먼저 든다.
     *  · 테·축도 같은 비율(1.2 → 0.85)로 줄인다 — 안 줄이면 작은 바퀴가 테로 꽉 찬다.
     *  · 중심을 10.3 → 11 로 내려 **바퀴 윗날이 대차 프레임(y 9.8)에 물리게** 둔다. 안 내리면
     *    작아진 바퀴가 프레임에서 떨어져 몸통 밑에 점 세 개를 찍은 꼴이 된다.
     *  · 아랫날 13.5 는 종전 13.9 와 거의 같다 — **바퀴가 선로에 닿는 자리**(차선 오프셋 14dp)
     *    라서 여기가 움직이면 기관차가 선로에서 뜬다.
     */
    for (cx in floatArrayOf(-16.5f, -4.5f, 8.5f)) {
        val c = p(cx, 11f)
        drawCircle(wheel, 2.5f * u, c)
        drawCircle(body, 2.5f * u, c, style = Stroke(width = 0.85f * u))
        drawCircle(body, 0.85f * u, c)
    }

    /*
     * ── 진행 방향 물결 (v1.6.91) ──────────────────────────────────
     * 사용자: *"오른쪽으로 간다는 화살표 물결 희미하게 해줘도 될꺼같은데?"*
     * 앞코 너머로 갈매기(`>`) 3개가 옅게 앞으로 흐른다. 높이 ±3.2dp = **몸통 높이의 절반 이하**
     * 라 역 이름 위에 스쳐도 글자를 못 지운다. 뻗는 길이는 [LOCO_WAKE] 가 상자에도 들어간다.
     */
    if (wake) for (i in 0 until 3) {
        val v = (phase + i / 3f) % 1f
        // 남색 위 반투명 노랑은 알파가 낮으면 **올리브색**으로 죽는다(실측) — 앞선 갈매기는
        // 0.45 로 올려 노랑이 살게 두고 뒤로 갈수록 사그라진다.
        val a = (0.45f - 0.25f * v) * ((1f - v) / 0.3f).coerceAtMost(1f)
        val x = 25.5f + 10.5f * v
        val t0 = p(x, -3.2f); val t1 = p(x + 2.2f, 0f); val t2 = p(x, 3.2f)
        drawPath(
            Path().apply { moveTo(t0.x, t0.y); lineTo(t1.x, t1.y); lineTo(t2.x, t2.y) },
            body.copy(alpha = a),
            style = Stroke(width = 1.5f * u, cap = StrokeCap.Round),
        )
    }

    // 행선판 — 몸통 위 가운데에 **접해서** 붙는다(자리는 위에서 이미 쟀다).
    // 지붕보다 위라 [bodyBrush] 의 밝은 끝(Clamp)에 걸린다 — 판이 몸통보다 밝아 떠 보인다.
    if (plate != null) box(-3.5f - plateW / 2f, plateTop, -3.5f + plateW / 2f, roofY, bodyBrush, 1.5f)

    /*
     * 열번 — **맨 나중에** 그린다(바퀴가 띠를 스쳐도 글자가 이긴다).
     * 좌우 반전에서는 글자를 같이 뒤집으면 거울글씨가 되므로 **자리만 옮기고 똑바로** 쓰고,
     * 위아래에서는 몸통을 따라 돌린다(거꾸로 안 서게 위 −90° / 아래 +90°).
     * 행선판 글자도 **같은 각도**로 돈다 — 세로 화면에서 열번과 나란히 읽혀야 한다.
     */
    val lab = textMeasurer.measure(
        number,
        TextStyle(
            // v1.6.96 **한 단계 크게**(9.5 → 11). 4자리 ExtraBold 가 ≈27dp — 열번 띠(y −8…+8,
            // 폭 −23…16)에 그대로 들어가므로 몸통은 안 늘렸다. 전체 필터의 0.8배 기관차에서도
            // 8.8sp 라 실화면에서 읽힌다(종전 7.6sp). 행선판은 8.5 그대로 = 열번보다 작다.
            fontSize = (11f * scale).sp, fontWeight = FontWeight.ExtraBold, color = numberColor,
        ),
    )
    val nc = p(-3.5f, 0f)   // 보일러 + 운전실 가운데
    // ⚠ **지도 회전을 더한 화면 기준**으로 정한다 — 지도 좌표에서 옳던 각도가 세로 화면에서
    // 180° 로 서서 `8509` 가 거꾸로 찍혔다(v1.6.91 사용자 지적). [locoTextDeg] 가 잠근다.
    val deg = locoTextDeg(heading, mapDeg)
    fun text(l: androidx.compose.ui.text.TextLayoutResult, c: Offset) = rotate(deg, pivot = c) {
        drawText(l, topLeft = Offset(c.x - l.size.width / 2f, c.y - l.size.height / 2f))
    }
    text(lab, nc)
    if (plate != null) text(plate, p(-3.5f, plateTop + plateH / 2f))
}
