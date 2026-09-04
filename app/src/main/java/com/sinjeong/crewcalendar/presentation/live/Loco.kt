package com.sinjeong.crewcalendar.presentation.live

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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

/** 기관차 한 장이 실제로 먹는 자리 — 배지 계단·역명 회피가 이 상자를 장애물로 쓴다. */
internal fun DrawScope.locoBox(center: Offset, heading: Heading, scale: Float): Rect {
    val turned = heading == Heading.UP || heading == Heading.DOWN
    val w = (if (turned) LOCO_BOX_H else LOCO_BOX_W) * scale * 1.dp.toPx() / 2f
    val h = (if (turned) LOCO_BOX_W else LOCO_BOX_H) * scale * 1.dp.toPx() / 2f
    return Rect(center.x - w, center.y - h, center.x + w, center.y + h)
}

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
 * @param smoke 굴뚝 위 옅은 연기 2점
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
) {
    val u = scale * 1.dp.toPx()

    /**
     * **제 몸 좌표 → 화면 좌표.** 머리가 +x, 아래가 +y 인 오른쪽 보기 그림 하나만 적고
     * 여기서 접는다. 네 변환 모두 90도의 배수(또는 미러)라 축정렬 상자가 축정렬로 남는다 —
     * 그래서 `drawRoundRect` 를 그대로 쓸 수 있고 변환 스택을 안 쌓아도 된다.
     */
    fun p(x: Float, y: Float): Offset = when (heading) {
        Heading.RIGHT -> Offset(center.x + x * u, center.y + y * u)
        Heading.LEFT -> Offset(center.x - x * u, center.y + y * u)   // 좌우 반전
        Heading.DOWN -> Offset(center.x - y * u, center.y + x * u)   // +90°
        Heading.UP -> Offset(center.x + y * u, center.y - x * u)     // −90°
    }

    fun box(x0: Float, y0: Float, x1: Float, y1: Float, c: Color, r: Float = 0f) {
        val a = p(x0, y0)
        val b = p(x1, y1)
        drawRoundRect(
            c,
            topLeft = Offset(min(a.x, b.x), min(a.y, b.y)),
            size = Size(abs(b.x - a.x), abs(b.y - a.y)),
            cornerRadius = CornerRadius(r * u),
        )
    }

    if (smoke) {
        drawCircle(body.copy(alpha = 0.60f), 2.5f * u, p(9f, -20f))
        drawCircle(body.copy(alpha = 0.35f), 1.8f * u, p(12.5f, -24.5f))
    }
    box(4.8f, -15.2f, 12.2f, -13.6f, body, 0.6f)     // 굴뚝 갓
    box(6f, -14f, 11f, -8f, body)                    // 굴뚝
    box(-24f, -14f, -8.5f, -12f, body, 1f)           // 운전실 지붕(양쪽으로 조금 나온다)
    box(-23f, -12.5f, -9.5f, 8f, body, 1.5f)         // 운전실 — 몸통보다 높다
    box(-11f, -8f, 16f, 8f, body, 4f)                // 보일러 몸통
    // 앞코(배장기) — 여기가 **진행 방향**이다.
    drawPath(
        Path().apply {
            val a = p(16f, -7f); val b = p(23f, 0.5f); val c = p(16f, 8f)
            moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); close()
        },
        body,
    )
    // ⚠ 운전실 창은 **열번 띠(y −8…+8) 위쪽**에만 둔다 — 띠 안에 두면 남색 창이 열번을 삼킨다.
    box(-20.5f, -11.5f, -12f, -8.8f, wheel, 0.6f)
    box(-21.5f, 8f, 17f, 9.8f, wheel)                // 대차 프레임
    for (cx in floatArrayOf(-16.5f, -4.5f, 8.5f)) {  // 동륜 3개
        val c = p(cx, 10.3f)
        drawCircle(wheel, 3.6f * u, c)
        drawCircle(body, 3.6f * u, c, style = Stroke(width = 1.2f * u))
        drawCircle(body, 1.2f * u, c)
    }

    /*
     * 열번 — **맨 나중에** 그린다(바퀴가 띠를 스쳐도 글자가 이긴다).
     * 좌우 반전에서는 글자를 같이 뒤집으면 거울글씨가 되므로 **자리만 옮기고 똑바로** 쓰고,
     * 위아래에서는 몸통을 따라 돌린다(거꾸로 안 서게 위 −90° / 아래 +90°).
     */
    val lab = textMeasurer.measure(
        number,
        TextStyle(
            fontSize = (9.5f * scale).sp, fontWeight = FontWeight.ExtraBold, color = numberColor,
        ),
    )
    val nc = p(-3.5f, 0f)   // 보일러 + 운전실 가운데
    val deg = when (heading) {
        Heading.UP -> -90f
        Heading.DOWN -> 90f
        else -> 0f
    }
    rotate(deg, pivot = nc) {
        drawText(
            lab,
            topLeft = Offset(nc.x - lab.size.width / 2f, nc.y - lab.size.height / 2f),
        )
    }
}
