package com.sinjeong.crewcalendar

import com.sinjeong.crewcalendar.presentation.live.Heading
import com.sinjeong.crewcalendar.presentation.live.LOCO_BOARD_H
import com.sinjeong.crewcalendar.presentation.live.LOCO_BOX_H
import com.sinjeong.crewcalendar.presentation.live.headingFor
import com.sinjeong.crewcalendar.presentation.live.locoBelly
import com.sinjeong.crewcalendar.presentation.live.locoFlip
import com.sinjeong.crewcalendar.presentation.live.locoHalf
import com.sinjeong.crewcalendar.presentation.live.locoTextDeg
import com.sinjeong.crewcalendar.presentation.live.mainTrainSide
// ⚠ `mainTrainSide` 는 MainLineMap 이 아니라 **Loco** 에 산다 — MainLineMap 최상위의
// `Color(...)` 가 이 하네스(Compose 미포함)에서 클래스 초기화를 터뜨리기 때문이다.
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 증기기관차 아이콘의 **머리 방향**을 잠근다 — 사용자가 이 그림을 넣은 이유가
 * *"방향이 헷갈려!"* 하나이므로, 머리가 반대로 돌면 기능이 통째로 거짓말이 된다.
 *
 * 화면 좌표라 y 는 **아래가 양수**다. 본선 순환선의 접선은 `Loop.at()` 이 주고 늘
 * **내선(인덱스가 커지는 쪽 = 시계)** 을 가리킨다. 실행법은 [PatternTest] KDoc 참고.
 */
class LocoTest {

    /** 윗변은 왼→오. 내선이면 머리도 오른쪽. */
    @Test
    fun `본선 윗변 내선은 오른쪽`() {
        assertEquals(Heading.RIGHT, headingFor(1f, 0f, true))
    }

    /** 같은 윗변이라도 외선은 반대로 달린다. */
    @Test
    fun `본선 윗변 외선은 왼쪽`() {
        assertEquals(Heading.LEFT, headingFor(1f, 0f, false))
    }

    /** 아랫변은 오→왼. 내선인데도 왼쪽인 것이 이 그림의 값어치다(배지로는 못 읽는 정보). */
    @Test
    fun `본선 아랫변 내선은 왼쪽`() {
        assertEquals(Heading.LEFT, headingFor(-1f, 0f, true))
    }

    /** 오른쪽 변은 위→아래. 화면 y 가 아래로 커지므로 아래쪽. */
    @Test
    fun `본선 오른쪽 변 내선은 아래쪽`() {
        assertEquals(Heading.DOWN, headingFor(0f, 1f, true))
    }

    /** 왼쪽 변은 아래→위. */
    @Test
    fun `본선 왼쪽 변 내선은 위쪽`() {
        assertEquals(Heading.UP, headingFor(0f, -1f, true))
    }

    /**
     * 모서리 호는 **가까운 변 기준** — 긴 쪽 성분이 이긴다. 오른위 모서리를 막 지난
     * 30도 지점(접선 ≈ (0.87, 0.5))은 아직 윗변 쪽이라 오른쪽,
     * 60도(≈ (0.5, 0.87))는 이미 오른쪽 변 쪽이라 아래쪽이다.
     */
    @Test
    fun `모서리 호는 가까운 변을 따른다`() {
        assertEquals(Heading.RIGHT, headingFor(0.866f, 0.5f, true))
        assertEquals(Heading.DOWN, headingFor(0.5f, 0.866f, true))
    }

    /** 지선 카드는 신도림이 오른쪽 끝 — 신도림행은 늘 오른쪽. */
    @Test
    fun `지선 신도림행은 오른쪽`() {
        assertEquals(Heading.RIGHT, headingFor(1f, 0f, true))
    }

    /**
     * **지선 까치산행은 왼쪽**(v1.6.91). 종전엔 까치산행이 네모 배지라 방향이 없었다 —
     * 사용자 지적 *"신도림행 네모 아이콘은 왜 따로 다녀?"* 로 지선의 **모든** 영업 열차가
     * 기관차가 됐고, 한 카드 안에서 신도림행(오른쪽)과 머리가 **반대**여야 그림이 참이 된다.
     */
    @Test
    fun `지선 까치산행은 왼쪽`() {
        assertEquals(Heading.LEFT, headingFor(1f, 0f, false))
    }

    /**
     * **본선 외선도 기관차**(v1.6.91) — 이 방향들은 v1.6.90 까지 그릴 일이 없었다(외선 열차는
     * 전부 네모 배지였다). 내선의 정반대인지 네 변에서 확인한다.
     */
    @Test
    fun `본선 외선은 내선의 반대`() {
        assertEquals(Heading.LEFT, headingFor(1f, 0f, false))    // 윗변
        assertEquals(Heading.UP, headingFor(0f, 1f, false))      // 오른쪽 변
        assertEquals(Heading.RIGHT, headingFor(-1f, 0f, false))  // 아랫변
        assertEquals(Heading.DOWN, headingFor(0f, -1f, false))   // 왼쪽 변
    }

    /**
     * **행선판은 지붕 쪽으로만** 상자를 키운다(v1.6.91). 회피 상자가 판을 빼먹으면 역 이름
     * 위에 행선판이 얹히고, 방향을 잘못 접으면 엉뚱한 쪽이 비어 이름이 또 밀린다 —
     * 둘 다 사용자 확정 규칙(*"텍스트가 겹쳐서 안 보이는 일 없도록"*) 위반이다.
     *
     * 값은 (왼, 위, 오른, 아래). 제 몸 −y(지붕)가 가는 화면 방향에만 [LOCO_BOARD_H] 가 붙는다.
     */
    @Test
    fun `행선판은 지붕 쪽만 키운다`() {
        val side = LOCO_BOX_H / 2f
        val roof = side + LOCO_BOARD_H
        // 좌우로 달리면 지붕은 화면 위 — 위쪽만 커진다.
        assertEquals(roof, locoHalf(Heading.RIGHT, wake = false, board = true)[1], 0f)
        assertEquals(roof, locoHalf(Heading.LEFT, wake = false, board = true)[1], 0f)
        // 아래로 달리면 지붕은 화면 오른쪽, 위로 달리면 화면 왼쪽.
        assertEquals(roof, locoHalf(Heading.DOWN, wake = false, board = true)[2], 0f)
        assertEquals(roof, locoHalf(Heading.UP, wake = false, board = true)[0], 0f)
        // 판이 없으면 네 방향 다 반높이 그대로다.
        for (h in Heading.values()) {
            val n = locoHalf(h, wake = false, board = false)
            assertEquals("$h", 2, n.count { it == side })
        }
    }

    /*
     * ── 열번 읽는 방향은 **화면 기준 한 벌** (v1.6.98) ──────────────────────
     * 사용자와 합의: **가로로 달리는 열차는 왼쪽→오른쪽, 세로로 달리는 열차는 위→아래.**
     * (역 이름과 같은 방향이다.)
     *
     * v1.6.91 은 *"거꾸로만 아니면 된다"* 였다 — `UP = −90` 을 그대로 두는 바람에 가로 화면
     * 세로변에서 위로 달리는 열차의 열번이 **아래→위**로 읽혔고, 같은 변의 두 열차가 서로
     * 반대로 읽혔다. 이제 [locoTextDeg] 가 화면 각을 **0 또는 +90 딱 두 값**으로 접는다.
     */

    /** 화면에 얹은 실제 각(−180, 180]. 사람이 읽는 각도다. */
    private fun screen(h: Heading, mapDeg: Float): Float {
        var s = (locoTextDeg(h, mapDeg) + mapDeg) % 360f
        if (s > 180f) s -= 360f
        if (s <= -180f) s += 360f
        return s
    }

    /** 가로 화면(`mapDeg = 0`) — 좌우로 달리면 0°, 위아래로 달리면 **둘 다** +90°. */
    @Test
    fun `가로 화면 열번은 가로 좌우 세로 위아래`() {
        assertEquals(0f, screen(Heading.RIGHT, 0f), 0f)
        assertEquals(0f, screen(Heading.LEFT, 0f), 0f)
        assertEquals(90f, screen(Heading.UP, 0f), 0f)     // v1.6.97 까지 −90(아래→위) 이었다
        assertEquals(90f, screen(Heading.DOWN, 0f), 0f)
    }

    /**
     * 세로 화면(`mapDeg = 90`) — 지도가 통째로 돌아 **가로/세로가 맞바뀐다**.
     * 지도에서 좌우로 달리던 열차(윗변·아랫변)가 화면에서는 세로라 위→아래로 읽힌다.
     */
    @Test
    fun `세로 화면 열번도 가로 좌우 세로 위아래`() {
        assertEquals(90f, screen(Heading.RIGHT, 90f), 0f)
        assertEquals(90f, screen(Heading.LEFT, 90f), 0f)
        assertEquals(0f, screen(Heading.UP, 90f), 0f)
        assertEquals(0f, screen(Heading.DOWN, 90f), 0f)
    }

    /**
     * 어떤 회전에서도 화면 각은 **0 아니면 +90** — 이 한 줄이 "거꾸로도 거울도 없다"이다.
     * (−90 이 없다는 것이 v1.6.98 에서 새로 잠근 몫이다: 아래→위로 읽히는 열번이 사라졌다.)
     */
    @Test
    fun `어느 회전에서도 화면 각은 0 아니면 90`() {
        for (deg in floatArrayOf(0f, 90f, 180f, 270f, -90f)) for (h in Heading.values()) {
            val s = screen(h, deg)
            assertTrue("$h@$deg = $s", abs(s) < 1e-3f || abs(s - 90f) < 1e-3f)
        }
    }

    /*
     * ── **바퀴는 늘 선로를 본다** (v1.6.96) ────────────────────────────────
     * 사용자: *"외선,내선에서보면 바퀴가 선로쪽으로 안되어있는 열차 아이콘이 있는데?"*
     *
     * v1.6.91 의 `locoFlip(heading, mapDeg)` 는 *"화면에서 배가 하늘을 보는가"* 만 봤다 —
     * 글자가 거꾸로 서는 문제만 보고 만든 기준이라 **선로가 어느 쪽인지**를 아예 안 봤고,
     * 여덟 경우 중 넷에서 바퀴가 허공을 봤다(v1.6.95 `R02` 실측). 아래 세 건이 그 자리를
     * 잠근다 — 이제 판정은 `배 벡터 · 선로 벡터 > 0` 하나뿐이다.
     */

    /** 뒤집기까지 반영한 **바퀴가 보는 쪽**(지도 좌표) — [drawLoco] 가 실제로 그리는 방향이다. */
    private fun wheels(h: Heading, railX: Float, railY: Float): Pair<Float, Float> {
        val (bx, by) = locoBelly(h)
        return if (locoFlip(h, railX, railY)) -bx to -by else bx to by
    }

    /** 본선 네 변의 접선(인덱스가 커지는 쪽 = 내선 = 시계). */
    private val edges = listOf(
        "윗변" to (1f to 0f), "오른쪽변" to (0f to 1f),
        "아랫변" to (-1f to 0f), "왼쪽변" to (0f to -1f),
    )

    /**
     * ## v1.6.98 보조설비 배치 — 네 변 × **내선·외선 여덟 경우**
     *
     * 사용자가 준 외선 운전실 화면 사진 그대로다:
     * **가로 변은 열차가 선로 위**(윗변이면 루프 밖, 아랫변이면 루프 안) ·
     * **세로 변은 열차가 루프 바깥**.
     *
     * ⚠ **차선은 한 줄뿐이다.** 반대 방향을 한 차선 밖에 세워 봤더니 그 열차들이 선로에서
     * 떠 보였다(사용자: *"떠다니는데? 아니지?"*) — 그래서 내선·외선이 **같은 자리**에 서고
     * 방향은 머리가 말한다. 여덟 경우의 선로 쪽 벡터가 넷뿐인 이유다.
     *
     * 잠그는 것 셋:
     *  ① 바퀴 = 선로 쪽 (규칙 4)
     *  ② 자리는 열차 쪽으로만 물러난다 — **역 이름 쪽으로 내려가는 칸이 하나도 없다**
     *  ③ 가로 변에서는 **뒤집힘이 없다** = 몸통이 늘 바로 선다(사용자가 사진으로 지목한 자리)
     */
    @Test
    fun `본선 네 변 내선 외선 여덟 경우 모두 바퀴가 선로를 본다`() {
        for ((name, tan) in edges) for (inner in listOf(false, true)) {
            val (tx, ty) = tan
            val (ox, oy) = mainTrainSide(tx, ty)          // 열차 쪽 = 계단이 오르는 쪽
            val rx = -ox                                   // 선로 쪽 = 그 반대
            val ry = -oy
            val h = headingFor(tx, ty, inner)
            val tag = "$name ${if (inner) "내선" else "외선"}($h)"
            // ① 바퀴가 선로를 본다
            val w = wheels(h, rx, ry)
            assertEquals("$tag 바퀴 x", rx, w.first, 0f)
            assertEquals("$tag 바퀴 y", ry, w.second, 0f)
            // ② 가로 변은 늘 화면 위 · 세로 변은 늘 루프 바깥
            val horiz = name == "윗변" || name == "아랫변"
            if (horiz) assertSide("$tag 열차 쪽", 0f, -1f, ox to oy)
            else assertSide("$tag 열차 쪽", ty, -tx, ox to oy)
            // ③ 가로 변에서는 몸통이 바로 선다 — 뒤집히면 배가 하늘을 본다
            if (horiz) assertFalse("$tag 가로 변인데 뒤집혔다", locoFlip(h, rx, ry))
        }
    }

    /**
     * **가로 변은 열차가 늘 선로 위**(v1.6.98) — 사용자 사진의 규칙 절반이다.
     * 윗변이면 루프 밖, 아랫변이면 루프 안이지만 화면에서는 둘 다 `(0, −1)` 한 값이다.
     * 이 한 줄이 *"아래 변 외선이 배를 하늘로 든 기관차"* 를 없앤 자리다.
     */
    @Test
    fun `가로 변 열차는 늘 선로 위`() {
        assertSide("윗변", 0f, -1f, mainTrainSide(1f, 0f))
        assertSide("아랫변", 0f, -1f, mainTrainSide(-1f, 0f))
    }

    /** **세로 변은 열차가 늘 루프 바깥** — 역 이름이 안쪽 자리를 가져간다. */
    @Test
    fun `세로 변 열차는 늘 루프 바깥`() {
        assertSide("오른쪽변", 1f, 0f, mainTrainSide(0f, 1f))    // 오른쪽 = 바깥
        assertSide("왼쪽변", -1f, 0f, mainTrainSide(0f, -1f))    // 왼쪽 = 바깥
    }

    /**
     * **계단으로 올라간 열차의 받침선은 늘 바퀴 밑**(v1.6.98).
     *
     * 겹침을 피해 선로에서 한 칸 물러난 열차는 그냥 두면 허공에 뜬다 — 사용자 확정
     * *"떠 있는 열차 금지"*. 그래서 `MainLineMap` 이 중심에서 **선로 쪽(`-out`)** 으로
     * 기관차 반높이만큼 내려간 자리에 짧은 초록 선분을 깐다. 그 자리가 실제로 바퀴가
     * 보는 쪽인지를 여기서 잠근다 — 어긋나면 받침선이 지붕 위에 깔린다.
     */
    @Test
    fun `계단 받침선은 늘 바퀴 밑에 깔린다`() {
        for ((name, tan) in edges) for (inner in listOf(false, true)) {
            val (tx, ty) = tan
            val (ox, oy) = mainTrainSide(tx, ty)
            val footX = -ox                                // 받침선이 깔리는 쪽 = 선로 쪽
            val footY = -oy
            val h = headingFor(tx, ty, inner)
            val tag = "$name ${if (inner) "내선" else "외선"} 받침선"
            assertSide(tag, footX, footY, wheels(h, footX, footY))
        }
    }

    /*
     * ── 전체 보기는 **복선** (v1.7.4) ──────────────────────────────────────
     * 사용자: *"전체 보기를 할때 열차들이 내선,외선 열차 아이콘들이 서로 올라타고 그러는데
     * 외선은 노선 바깥 내선은 노선 안쪽으로 다니게 하면 어떨까? 지금 내선클릭해서보면
     * 괜찮고 외선 클릭해서 보면 괜찮은데..전체를 보면 열차 아이콘들이 어색해"*
     *
     * 답: **전체 보기만 선로를 두 줄로** 긋고(바깥 외선 · 안쪽 내선) 각자 제 선로 위에 세운다.
     * 단독 보기(내선/외선)는 선로가 한 줄이라 종전 그대로다 — `innerLane` 기본값이 `false`.
     */

    /**
     * **복선 여덟 경우** — 네 변 × 내선·외선. 잠그는 것 셋:
     *  ① 가로 변은 **둘 다 선로 위**(윗변 내선·아랫변 외선이 두 선로 사이에 든다)
     *  ② 세로 변은 **외선이 루프 바깥 · 내선이 루프 안쪽**(선로가 달라 서로 안 겹친다)
     *  ③ 어느 경우든 **바퀴가 제 선로를 본다** = 떠 있는 열차가 없다
     */
    @Test
    fun `복선 네 변 내선 외선 여덟 경우 모두 제 선로 위에 바로 선다`() {
        for ((name, tan) in edges) for (inner in listOf(false, true)) {
            val (tx, ty) = tan
            val (ox, oy) = mainTrainSide(tx, ty, innerLane = inner)
            val h = headingFor(tx, ty, inner)
            val tag = "$name ${if (inner) "내선" else "외선"}($h) 복선"
            val horiz = name == "윗변" || name == "아랫변"
            // ①② 자리
            if (horiz) assertSide("$tag 열차 쪽", 0f, -1f, ox to oy)
            else if (inner) assertSide("$tag 열차 쪽", -ty, tx, ox to oy)   // 루프 안쪽
            else assertSide("$tag 열차 쪽", ty, -tx, ox to oy)              // 루프 바깥
            // ③ 바퀴 = 선로 쪽(= 열차 쪽의 반대)
            assertSide("$tag 바퀴", -ox, -oy, wheels(h, -ox, -oy))
            // 가로 변에서는 여전히 뒤집힘이 없다
            if (horiz) assertFalse("$tag 가로 변인데 뒤집혔다", locoFlip(h, -ox, -oy))
        }
    }

    /**
     * **세로 변에서만 내선이 갈라진다** — 복선의 전부다. 가로 변은 단선·복선이 같은 값이라
     * 윗변 내선·아랫변 외선이 **두 선로 사이**에 서고, 세로 변은 두 방향이 정반대로 갈라져
     * 서로의 자리를 아예 안 넘본다.
     */
    @Test
    fun `복선은 세로 변에서만 내선이 갈라진다`() {
        for ((name, tan) in edges) {
            val (tx, ty) = tan
            val single = mainTrainSide(tx, ty)
            val outer = mainTrainSide(tx, ty, innerLane = false)
            val inner = mainTrainSide(tx, ty, innerLane = true)
            // 외선은 단선과 같은 자리 — 바깥 선로가 v1.7.3 의 그 선로다
            assertSide("$name 외선 = 단선", single.first, single.second, outer)
            if (name == "윗변" || name == "아랫변")
                assertSide("$name 내선 = 단선", single.first, single.second, inner)
            else assertSide("$name 내선 = 반대", -single.first, -single.second, inner)
        }
    }

    /**
     * **두 선로는 동심**이라야 한다 — 안쪽 반지름 = 바깥 반지름 − 간격.
     *
     * 이 한 줄이 지키는 것 둘: ① 두 곡선 사이가 **어디서나 같은 간격**(모서리 포함)이라
     * 복선으로 읽힌다 ② 직선 구간의 길이·범위가 **정확히 같아져** 같은 역이 두 선로에서
     * 서로 마주 본다(`Loop.sOf` 가 직선을 등분하므로 `hLen`·`vLen` 이 같으면 자리도 같다).
     * `MainLineMap` 의 `rOut = rIn + gap` 이 그 식이고, 여기서 그 산수를 잠근다.
     */
    @Test
    fun `복선 두 선로는 동심이라 직선 구간이 정확히 겹친다`() {
        // 폰 전체 보기 실측값(dp): 캔버스 815 × 335 · trainPad 46.5 · namePad 56 · 간격 30.6
        val w = 815f; val h = 335f; val tp = 46.5f; val np = 56f; val gap = 30.6f
        val rIn = maxOf(28f - gap / 2f, 8f)
        val rOut = rIn + gap
        assertEquals("안쪽 반지름", 12.7f, rIn, 1e-3f)
        assertEquals("바깥 반지름", 43.3f, rOut, 1e-3f)
        // 바깥: (tp, tp)~(w−tp, h−np) 반지름 rOut / 안쪽: 사방 gap 안으로, 반지름 rIn
        val hOut = (w - tp) - tp - 2f * rOut
        val hInn = (w - tp - gap) - (tp + gap) - 2f * rIn
        val vOut = (h - np) - tp - 2f * rOut
        val vInn = (h - np - gap) - (tp + gap) - 2f * rIn
        assertEquals("직선 가로 길이", hOut, hInn, 1e-3f)
        assertEquals("직선 세로 길이", vOut, vInn, 1e-3f)
        // 시작 x 도 같다 — 그래서 k 번째 역이 두 선로에서 같은 x 에 선다
        assertEquals("직선 시작 x", tp + rOut, (tp + gap) + rIn, 1e-3f)
    }

    /**
     * **틈에는 기관차 한 대가 든다** — 두 선로 사이(윗변 내선·아랫변 외선의 자리)가
     * `차선 오프셋 + 타 열차(0.7배) 반높이 + 선로 반굵기 + 2dp` 다. 이 산수가 틀어지면
     * 틈에 선 열차가 반대편 선로를 밟는다(= 사용자가 v1.6.98 에서 물린 "떠 있는 열차").
     */
    @Test
    fun `복선 간격은 타 열차 한 대가 옆 선로를 안 밟는 값이다`() {
        /** `MainLineMap.laneGap` 의 식 그대로(dp). */
        fun gapOf(badge: Float, k: Float, rail: Float) =
            badge + (LOCO_BOX_H / 2f) * k + rail / 2f + 2f
        // 폰: 차선 14 · 배수 0.7 · 선로 7.5 / 펼침: 18 · 0.7 × (54/46) · 9
        assertEquals("폰", 30.6f, gapOf(14f, 0.7f, 7.5f), 1e-3f)
        assertEquals("펼침", 37.2375f, gapOf(18f, 0.7f * (54f / 46f), 9f), 1e-3f)
        // 기관차 상자 윗날이 반대편 선로 안쪽 면에 안 닿는다
        for (t in listOf(
            Triple(14f, 0.7f, 7.5f), Triple(18f, 0.7f * (54f / 46f), 9f))) {
            val gap = gapOf(t.first, t.second, t.third)
            assertTrue("$t", t.first + (LOCO_BOX_H / 2f) * t.second <= gap - t.third / 2f)
        }
    }

    /** `−0.0f` 과 `0.0f` 은 `equals` 로는 다르다 — 벡터 비교는 늘 성분으로 본다. */
    private fun assertSide(tag: String, x: Float, y: Float, got: Pair<Float, Float>) {
        assertEquals("$tag x", x, got.first, 0f)
        assertEquals("$tag y", y, got.second, 0f)
    }

    /**
     * 모서리 호는 **가까운 변의 배치 규칙**을 따른다 — 잣대가 [headingFor] 와 같아야
     * 머리와 차선이 한 순간에 같이 접힌다(따로 접히면 호 위에 배가 하늘을 보는 칸이 생긴다).
     */
    @Test
    fun `모서리 호 배치는 머리 방향과 같은 순간에 접힌다`() {
        for (t in listOf(0.866f to 0.5f, 0.5f to 0.866f, -0.866f to -0.5f, -0.5f to -0.866f)) {
            val horizSide = mainTrainSide(t.first, t.second).second == -1f
            val horizHead = headingFor(t.first, t.second, true)
                .let { it == Heading.RIGHT || it == Heading.LEFT }
            assertEquals("$t", horizHead, horizSide)
        }
    }

    /** 지선 카드는 두 차선 다 기관차가 **제 선로 위**에 앉는다 — 바퀴는 늘 아래(+y). */
    @Test
    fun `지선 두 차선 다 바퀴가 아래 선로를 본다`() {
        for (toSindorim in listOf(true, false)) {
            val h = headingFor(1f, 0f, toSindorim)
            val w = wheels(h, 0f, 1f)
            assertEquals("$h 바퀴 x", 0f, w.first, 0f)
            assertEquals("$h 바퀴 y", 1f, w.second, 0f)
        }
    }

    /** 선로가 배 반대쪽이면 뒤집고, 같은 쪽이면 안 뒤집는다 — 네 머리 방향 모두. */
    @Test
    fun `선로가 반대쪽일 때만 몸통을 뒤집는다`() {
        for (h in Heading.values()) {
            val (bx, by) = locoBelly(h)
            assertFalse("$h", locoFlip(h, bx, by))
            assertTrue("$h", locoFlip(h, -bx, -by))
        }
    }
}
