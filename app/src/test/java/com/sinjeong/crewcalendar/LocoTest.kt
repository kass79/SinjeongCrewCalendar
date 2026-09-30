package com.sinjeong.crewcalendar

import com.sinjeong.crewcalendar.presentation.live.Heading
import com.sinjeong.crewcalendar.presentation.live.LOCO_BOARD_H
import com.sinjeong.crewcalendar.presentation.live.LOCO_BOX_H
import com.sinjeong.crewcalendar.presentation.live.LOCO_RING_H
import com.sinjeong.crewcalendar.presentation.live.LOCO_WHEEL_BOTTOM
import com.sinjeong.crewcalendar.domain.model.Line2Stations
import com.sinjeong.crewcalendar.presentation.live.headingFor
import com.sinjeong.crewcalendar.presentation.live.HALF_PI
import com.sinjeong.crewcalendar.presentation.live.cornerInsetPx
import com.sinjeong.crewcalendar.presentation.live.labelGapDp
import com.sinjeong.crewcalendar.presentation.live.labelLeadDp
import com.sinjeong.crewcalendar.presentation.live.labelSideOffDp
import com.sinjeong.crewcalendar.presentation.live.labelTilted
import com.sinjeong.crewcalendar.presentation.live.locoBelly
import com.sinjeong.crewcalendar.presentation.live.locoFlip
import com.sinjeong.crewcalendar.presentation.live.locoHalf
import com.sinjeong.crewcalendar.presentation.live.locoTextDeg
import com.sinjeong.crewcalendar.presentation.live.mainTrainSide
import com.sinjeong.crewcalendar.presentation.live.HEAD_LADDER
import com.sinjeong.crewcalendar.presentation.live.HEAD_MIN_K
import com.sinjeong.crewcalendar.presentation.live.HeadSpec
import com.sinjeong.crewcalendar.presentation.live.MapFit
import com.sinjeong.crewcalendar.presentation.live.firstFitting
import com.sinjeong.crewcalendar.presentation.live.mapCenterFit
import com.sinjeong.crewcalendar.presentation.live.mapCenterNeedPx
import com.sinjeong.crewcalendar.presentation.live.mapPosRoomPx
import com.sinjeong.crewcalendar.presentation.live.screenBandPadPx
import com.sinjeong.crewcalendar.presentation.live.mineRest
import com.sinjeong.crewcalendar.presentation.live.mineTitle
import com.sinjeong.crewcalendar.presentation.live.noMineText
import com.sinjeong.crewcalendar.presentation.live.nosBrief
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
     * **바퀴는 배율이 얼마든 선로 겉면에 앉는다**(v1.7.13b ③) — `MainLineMap.badgeOff` 가
     * 이제 상수가 아니라 `바퀴 아랫날([LOCO_WHEEL_BOTTOM]) × 배율 + 선로 반굵기` 다.
     *
     * 종전 `14dp`(펼침 18)는 **1.0배** 기관차에 눈으로 맞춘 값이라 배율을 내릴수록 기관차가
     * 선로에서 떴다(0.7배 실측 0.8dp · 0.55배면 3.5dp). 이 테스트가 그 틈을 0 으로 못 박는다
     * — 확정 표 *"떠 있는 열차 금지"* · *"바퀴는 늘 선로 쪽"*.
     */
    @Test
    fun `바퀴는 어느 배율에서든 선로 겉면에 앉는다`() {
        /** `MainLineMap.badgeOff` 의 식 그대로(dp). */
        fun offOf(k: Float, locoScale: Float, rail: Float) =
            LOCO_WHEEL_BOTTOM * locoScale * k + rail / 2f
        // 폰 전체 0.55 · 폰 단독 0.64 · 펼침 전체 · 펼침 단독
        assertEquals("폰 전체", 11.175f, offOf(0.55f, 1f, 7.5f), 1e-3f)
        assertEquals("폰 단독", 12.39f, offOf(0.64f, 1f, 7.5f), 1e-3f)
        assertEquals("펼침 전체", 13.2158f, offOf(0.55f, 54f / 46f, 9f), 1e-3f)
        assertEquals("펼침 단독", 14.6426f, offOf(0.64f, 54f / 46f, 9f), 1e-3f)
        // 어떤 배율에서도 (오프셋 − 바퀴 아랫날) 이 정확히 선로 반굵기 = 겉면에 딱 앉는다
        for (k in floatArrayOf(0.4f, 0.55f, 0.64f, 0.82f, 1f))
            for (ls in floatArrayOf(1f, 54f / 46f))
                for (rail in floatArrayOf(7.5f, 9f))
                    assertEquals(
                        "k=$k ls=$ls rail=$rail",
                        rail / 2f,
                        offOf(k, ls, rail) - LOCO_WHEEL_BOTTOM * ls * k,
                        1e-3f,
                    )
    }

    /**
     * **틈에는 기관차 한 대가 든다** — 두 선로 사이(윗변 내선·아랫변 외선의 자리)가
     * `차선 오프셋 + 타 열차 반높이 + 선로 반굵기 + 2dp` 다. 이 산수가 틀어지면
     * 틈에 선 열차가 반대편 선로를 밟는다(= 사용자가 v1.6.98 에서 물린 "떠 있는 열차").
     *
     * ⚠ v1.7.13b ③ 부터 **차선 오프셋도 배율을 따라간다** — 그래서 틈이 두 번 줄었다
     * (44.18 → **36.12dp**). 그만큼 두 선로가 붙고 루프가 커져 역 간격이 넓어진다.
     */
    @Test
    fun `복선 간격은 타 열차 한 대가 옆 선로를 안 밟는 값이다`() {
        /** `MainLineMap.laneGap` 의 식 그대로(dp) — 차선 오프셋도 같은 [k] 를 본다. */
        fun gapOf(k: Float, locoScale: Float, rail: Float): Float {
            val badge = LOCO_WHEEL_BOTTOM * locoScale * k + rail / 2f
            return badge + (LOCO_RING_H + LOCO_BOARD_H) * locoScale * k + rail / 2f + 2f
        }
        assertEquals("폰", 36.12f, gapOf(0.55f, 1f, 7.5f), 1e-3f)
        assertEquals("펼침", 42.2496f, gapOf(0.55f, 54f / 46f, 9f), 1e-3f)
        // 기관차 상자 윗날이 반대편 선로 안쪽 면에 안 닿는다
        for (ls in floatArrayOf(1f, 54f / 46f)) {
            val rail = if (ls == 1f) 7.5f else 9f
            val badge = LOCO_WHEEL_BOTTOM * ls * 0.55f + rail / 2f
            assertTrue(
                "ls=$ls",
                badge + (LOCO_BOX_H / 2f) * ls * 0.55f <= gapOf(0.55f, ls, rail) - rail / 2f,
            )
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

    /**
     * **접힘 세로 지도 가운데 맞추기**(v1.7.14 ⑩ · **v1.7.20 개정**) — 잣대는 선로 + 열차 차선 덩어리,
     * 한도는 실제 여유, 모자라면 루프를 깎는다(`mapCenterFit` KDoc).
     *
     * 값은 전부 **에뮬 실측**이다(접힘 1080×2520 · density 420 · 전체 보기):
     * 상태바 142 · namePad 147 · 헤더 95 · trainPad 91 · 차선 51.7 · 선로 반굵기 9.84px.
     * ⚠ v1.7.14~v1.7.19 의 `coerceIn(0, 20dp)` 는 **뒤집혔다**(배율 2.0 에서 9.5px 치우침 ·
     * 배율 1.5 에서 칩이 역명을 물었다).
     */
    @Test
    fun `가운데 맞추기는 선로와 열차 차선 덩어리를 가운데에 둔다`() {
        // 선로만 가운데면 (289 − 186)/2 = 51.5 — 덩어리는 차선 몫 (51.7 − 9.84)/2 = 20.9 를 더 민다.
        assertEquals(72, mapCenterNeedPx(142 + 147, 95 + 91, laneOutPx = 51.7f, railHalfPx = 9.84f))
        // 차선이 선로 반굵기와 같으면 v1.7.14 식(선로 가운데)과 같다.
        assertEquals(52, mapCenterNeedPx(289, 186, laneOutPx = 9.84f, railHalfPx = 9.84f))
    }

    @Test
    fun `옮길 한도는 칩 알약과 아랫변 역명 사이 실제 여유다`() {
        // 배율 1.3 실측: 알약 104 · hug 11 · 모서리 뺀 역명 깊이 104 · 틈 8 → (142−104)/2 + 11 + 43 − 8
        assertEquals(65, mapPosRoomPx(142, 104, 11, 147, 104, 8))
        // 배율이 커져 알약이 자라면 한도가 준다(배율 2.0: 줄 165 · 알약 149).
        assertEquals(54, mapPosRoomPx(165, 149, 11, 147, 104, 8))
    }

    @Test
    fun `여유 안이면 옮기기만 하고 모자라면 남은 몫의 두 배를 깎는다`() {
        assertEquals(MapFit(40), mapCenterFit(40, posRoomPx = 65, negRoomPx = 0, canShrink = true))
        // 배율 1.3 실측 — 65 옮기고 (72 − 65) × 2 = 14 를 헤더 쪽에서 깎는다.
        assertEquals(MapFit(65, shrinkHeadPx = 14), mapCenterFit(72, 65, 0, canShrink = true))
        // 깎는 끝(역명이 서는 데까지)에 걸리면 거기서 멈춘다 — 남은 몫은 치우친다(배율 2.0).
        assertEquals(MapFit(54, shrinkHeadPx = 12), mapCenterFit(84, 54, 0, true, maxShrinkPx = 12))
        // 헤더 쪽(음수)도 같은 규칙이다 — 여유만큼 옮기고 상태바 쪽에서 깎는다.
        assertEquals(MapFit(-4, shrinkStatPx = 12), mapCenterFit(-10, 50, 4, canShrink = true))
        assertEquals(MapFit(-3), mapCenterFit(-3, 50, 4, canShrink = true))
        // 가로 그림(펼침)은 깎지 않고 여유 안에서만 옮긴다.
        assertEquals(MapFit(30), mapCenterFit(100, 30, 30, canShrink = false))
        assertEquals(MapFit(-30), mapCenterFit(-100, 30, 30, canShrink = false))
        // 여유가 음수로 잡혀도(칩이 이미 역명에 닿은 화면) 거꾸로 밀지 않는다.
        assertEquals(MapFit(0, shrinkHeadPx = 20), mapCenterFit(10, -5, 0, canShrink = true))
    }

    /**
     * **보이는 화면 띠에 맞추는 여백**(v1.7.20 ⑤) — 창이 놓인 자리로 잰다. 값은 에뮬 실측이다.
     * ⚠ v1.6.x~v1.7.20 첫 판은 상태바 높이를 위 여백으로 **또** 빼고 아래는 44dp 하한을 뺐다 —
     * 접힘 선로가 보이는 화면 가운데보다 110px 아래였고 아래 열차가 제스처바 밑에 들어갔다.
     */
    @Test
    fun `지도 기둥은 상태바 아래부터 제스처바 위까지만 쓴다`() {
        // 접힘 1080×2520 · 420: 창 y=136 · 높이 2520(화면 전체) · 상태바 136 · 제스처바 63.
        assertEquals(0f to 199f, screenBandPadPx(136f, 2520f, 136f, 2520f - 63f))
        // 펼침 1968×2184 · 450: 창 y=124 · 높이 2185 · 제스처바 90.
        assertEquals(0f to 215f, screenBandPadPx(124f, 2185f, 124f, 2184f - 90f))
        // 창이 상태바 뒤까지 덮는 기기(창 y=0) — 위는 상태바만큼, 아래는 제스처바만큼.
        assertEquals(136f to 63f, screenBandPadPx(0f, 2520f, 136f, 2457f))
        // 창이 이미 보이는 자리 안에 있으면 뺄 것이 없다(음수로 넓히지 않는다).
        assertEquals(0f to 0f, screenBandPadPx(150f, 2200f, 136f, 2457f))
    }

    /* ── v1.7.20 헤더 — 내 열차 토막 · 사다리 ─────────────────────────────── */

    @Test
    fun `내 열차 토막은 번호 방향 행선이고 행로표 번호는 다를 때만 괄호`() {
        assertEquals("내 열차 2489 · 외선 · 홍대입구행", mineTitle("2489", "2489", false, "홍대입구행"))
        assertEquals("내 열차 8340(행로표 2340) · 내선", mineTitle("8340", "2340", true, null))
    }

    @Test
    fun `뒤 토막은 사다리가 현재 역부터 덜고 숨김 안내는 남긴다`() {
        val full = HeadSpec()
        assertEquals("다음 역 3분 후 · 신림 진입", mineRest(false, 150, "신림 진입", false, full))
        assertEquals("곧 도착 · 신림 도착", mineRest(false, 0, "신림 도착", false, full))
        assertEquals("(외선 화면에 있음) · 다음 역 3분 후 · 신림 진입",
            mineRest(false, 150, "신림 진입", true, full))
        assertEquals("(내선 화면에 있음) · 다음 역 3분 후",
            mineRest(true, 150, "신림 진입", true, HeadSpec(status = false)))
        assertEquals("(내선 화면에 있음)",
            mineRest(true, 150, "신림 진입", true, HeadSpec(status = false, next = false)))
        // 모르는 값은 조용히 빠진다(지어내지 않는다).
        assertEquals("신림 진입", mineRest(false, null, "신림 진입", false, full))
    }

    @Test
    fun `내 열차가 없는 날의 글줄은 종전 그대로다`() {
        assertEquals("내 열차 미검출(운행 전/후) · 오늘 열번 2401·2425 외 2개",
            noMineText(listOf("2401", "2425", "2009", "2011"), take = 2))
        assertEquals("내 열차 미검출(운행 전/후) · 오늘 열번 2401 외 3개",
            noMineText(listOf("2401", "2425", "2009", "2011"), take = 1))
        assertEquals(null, noMineText(emptyList(), take = 2))
        assertEquals("5668·5669", nosBrief(listOf("5668", "5669")))
    }

    /**
     * 사다리 차례 = **제목 → 날짜 → 초 → 오늘 열번 목록 → 현재 역 → 다음 역 → 나머지 글자 축소 → 기준 시각**.
     * 각 칸은 앞 칸의 부분집합이고, 필드 어디에도 내 열차 토막·지연 알약이 없다(구조로 못 박음).
     * ⚠ v1.6.94~v1.7.19 는 **글자 전체 0.88배가 첫 칸**이었다(내 열차까지 줄었다) — v1.7.20 에서 뒤집힘.
     */
    @Test
    fun `헤더 사다리는 제목 날짜 초 열번 순으로 덜고 축소는 맨 끝이다`() {
        val l = HEAD_LADDER
        assertEquals(9, l.size)
        assertEquals(HeadSpec(), l[0])
        assertFalse(l[1].title); assertTrue(l[1].date)
        assertFalse(l[2].date); assertTrue(l[2].seconds)
        assertFalse(l[3].seconds); assertEquals(2, l[3].take)
        assertEquals(1, l[4].take); assertTrue(l[4].status)
        assertFalse(l[5].status); assertTrue(l[5].next)
        assertFalse(l[6].next); assertEquals(1f, l[6].k, 0f)
        assertEquals(HEAD_MIN_K, l[7].k, 0f); assertTrue(l[7].clock)
        assertFalse(l[8].clock)                                // ⑧ 기준 시각 — 맨 끝
        // 앞 칸의 부분집합 — 한 번 덜어 낸 것은 다시 안 나온다.
        for (i in 1 until l.size) {
            val a = l[i - 1]; val b = l[i]
            assertTrue(i.toString(), (!b.title || a.title) && (!b.date || a.date) && (!b.seconds || a.seconds) &&
                b.take <= a.take && (!b.status || a.status) && (!b.next || a.next) && b.k <= a.k &&
                (!b.clock || a.clock))
        }
        // 하나도 안 들어가면 가장 짧은 칸.
        assertEquals(l.last(), firstFitting(l) { false })
        assertEquals(l[3], firstFitting(l) { !it.seconds })
    }

    /**
     * **43개 역 이름이 전부 한 기울기다**(v1.7.18 — v1.7.15 ⑤·v1.7.17 ② 규칙을 뒤집는다).
     *
     * 카스(2026-09-09): *"역명과 노선을 더 붙여주고 **구의, 강변, 잠실나루, 잠실, 당산,
     * 영등포구청, 문래, 신도림, 대림도 전체 텍스트 기울기에 맞게 같이** 하는게 최적화게
     * 좋지 않을까?"* — v1.7.17 까지는 가로 변 + 오른쪽 위 두 역(`건대입구`·`구의`)만
     * 기울였고, 그 예외가 곧 *"왜 여기만 누웠나"* 였다.
     *
     * 네 변의 역 목록은 `Line2Test.둘레 네 변 배치가 사진과 같다` 가 이미 잠근다.
     */
    @Test
    fun `세로 변까지 43개가 모두 기울고 밖 차선일 때만 가로다`() {
        val start = Line2Stations.MAIN.indexOf("합정")
        fun tilted(outside: Boolean) = (0 until 43)
            .filter { labelTilted(it, topN = 17, rightN = 5, bottomN = 16, sideLaneOutside = outside) }
            .map { Line2Stations.MAIN[(it + start) % 43] }
            .toSet()

        // 루프 안쪽에 적는 화면(접힘·펼침 전체 보기, 단독 보기) — **43개 전부**
        val inside = tilted(false)
        for (n in listOf("합정", "홍대입구", "뚝섬", "한양대", "건대입구", "구의",
                         "강변", "잠실나루", "잠실",
                         "대림", "신도림", "문래", "영등포구청", "당산"))
            assertTrue(n, n in inside)
        assertEquals(43, inside.size)

        // 가로 × 전체 보기(세로 변 이름이 루프 **밖** 차선) — 세로 변 열은 **가로**다.
        // v1.7.7 D1 이 실측으로 잡아 둔 배치 — 얇은 띠(폰 세로의 절반)를 대각선이 관통한다.
        val outside = tilted(true)
        for (n in listOf("건대입구", "구의", "강변", "잠실나루", "잠실",
                         "대림", "신도림", "문래", "영등포구청", "당산"))
            assertFalse(n, n in outside)
        assertEquals(33, outside.size)
        // 가로 변은 두 화면이 똑같다.
        assertEquals(outside, inside.filter { it in outside }.toSet())
    }

    /**
     * **역명↔선로 거리는 어느 화면이든 12dp 한 값**이다(v1.7.18 — [labelGapDp] KDoc).
     *
     * 카스: *"**역명과 노선을 더 붙여주고** …"* v1.7.17 은 `16dp / 접힘 전체 안쪽만 26dp`
     * 였다. 그 26dp 예외가 필요했던 이유(왼쪽 위 모서리에서 `합정`↔`당산` 다툼)는
     * [cornerInsetPx] 와 [labelTilted] 가 없앴다.
     *
     * ⚠ **`Dp` 값 자체는 여기서 못 잰다**(하네스에 Compose 가 없다 — `MapArgb` 가 `Long`
     * 인 것과 같은 사정). 그래서 [labelGapDp] 를 **숫자를 돌려주는 순수 함수**로 빼서
     * 잠근다. 실제 흰 여백(px)은 `docs/project-notes.md` v1.7.18 실측표가 근거다.
     */
    @Test
    fun `역명 거리는 어느 화면이든 한 값이다`() {
        for (dual in listOf(false, true))
            for (big in listOf(false, true))
                for (bottom in listOf(false, true))
                    assertEquals("dual=$dual big=$big bottom=$bottom", 14f,
                        labelGapDp(dual = dual, big = big, bottom = bottom), 0f)
    }

    /**
     * **모서리 간격은 이웃 두 변 간격의 평균**이 된다(v1.7.18 — [cornerInsetPx]).
     *
     * 카스: *"전체를 보면 내선에서 **성수와 건대입구, 합정과 당산 사이가 너무 짧아져서**
     * 최적화가 안되있는거 같은데? 역사이를 조금 더 늘려주면 좋지!"*
     *
     * 값은 실화면 실측 기하로 잠근다(디버그 훅 `LBLPURE` 가 찍은 `loopIn` 좌표·반지름):
     *  · **접힘 × 전체 보기**(1080×2400 · density 420) 안쪽 선로 `hLen=1723 vLen=362 r=26.2`
     *  · **펼침 × 전체 보기**(1968×2184 · density 450) 안쪽 선로 `hLen=1395 vLen=734 r=53`
     *  · **단독 보기**는 선로가 한 줄이라 반지름이 넉넉해 **0** — 배치가 v1.7.17 그대로다.
     */
    @Test
    fun `모서리 간격은 이웃 두 변 간격의 평균이 된다`() {
        fun check(hLen: Float, vLen: Float, r: Float): Triple<Float, Float, Float> {
            val i = cornerInsetPx(hLen, vLen, r, nH = 17, nV = 5)
            val sH = (hLen - 2f * i) / 16f
            val sV = (vLen - 2f * i) / 4f
            return Triple(i, 2f * i + HALF_PI * r, (sH + sV) / 2f)
        }
        // 접힘 × 전체 보기 — 모서리가 호 41px 뿐이던 것이 91px 로 벌어진다
        val fold = check(1722.6f, 361.6f, 26.2f)
        assertEquals(25.0f, fold.first, 0.5f)
        assertEquals("모서리 = 이웃 평균", fold.third, fold.second, 0.5f)

        // 펼침 × 전체 보기
        val big = check(1395f, 734f, 53f)
        assertEquals(22.5f, big.first, 0.5f)
        assertEquals("모서리 = 이웃 평균", big.third, big.second, 0.5f)

        // 단독 보기(접힘·펼침) — 호가 이미 평균보다 길어 **0**. v1.7.17 배치가 그대로다.
        assertEquals(0f, cornerInsetPx(1818f, 457f, 73.5f, nH = 17, nV = 5), 0f)
        assertEquals(0f, cornerInsetPx(1514f, 852f, 112.4f, nH = 17, nV = 5), 0f)
    }

    /**
     * **모서리 짝은 제자리에서 갈린다**(v1.7.18 ②-b — [labelLeadDp] · [labelSideOffDp]).
     *
     * 카스(접힘 화면을 보고): *"접혔을때 건대입구가 성수와 구의 사이에 있어야 하는데?"*
     *
     * `성수`(윗변 끝)와 `건대입구`(오른변 첫)는 같은 −35° 로 **나란한 두 띠**에 앉는다.
     * 띠 사이 법선 간격이 `글자 높이 + 여백` 보다 좁으면 제자리에서 겹쳐 한쪽이 밀리는데,
     * 접힘 전체 보기 실측이 **23px ↔ 필요 40px** 이었다. 두 앵커를 벌린 결과가 **44px** 다.
     *
     * 여기서 잠그는 것은 그 산수의 두 인자다(간격 자체는 `TextMeasurer` 가 있어야 잰다 —
     * 실측표는 `docs/project-notes.md` v1.7.18 ②-b 절):
     *  · 윗변 **모서리 쪽 끝**(`성수`, k = topN−1)만 **−4dp** — `big` 을 안 탄다.
     *  · 세로 변 **위 끝 두 역**(`건대입구` k = topN · `당산` k = loopN−1)만 **8dp**.
     */
    @Test
    fun `모서리 짝만 앵커를 벌린다`() {
        // 윗변: 모서리 쪽 끝(성수)만 반대로 4dp, 나머지는 접힘 4 · 펼침 8dp 그대로.
        for (big in listOf(false, true)) {
            val normal = if (big) 8f else 4f
            for (k in listOf(0, 1, 8, 15)) // 합정 … 뚝섬
                assertEquals("k=$k big=$big", normal, labelLeadDp(k, topN = 17, big = big), 0f)
            assertEquals("성수 big=$big", -4f, labelLeadDp(16, topN = 17, big = big), 0f)
        }
        // 세로 변: 위 끝 두 역만 8dp. 아래 끝(잠실 21 · 대림 38)은 4dp 그대로 —
        // 아랫변 이름이 루프 밖에 살아 다툴 상대가 없고, 더 내리면 아래 선로를 문다.
        assertEquals("건대입구", 8f, labelSideOffDp(17, topN = 17, loopN = 43), 0f)
        assertEquals("당산", 8f, labelSideOffDp(42, topN = 17, loopN = 43), 0f)
        for (k in listOf(18, 19, 20, 21, 38, 39, 40, 41)) // 구의 … 잠실 · 대림 … 영등포구청
            assertEquals("k=$k", 4f, labelSideOffDp(k, topN = 17, loopN = 43), 0f)
    }
}
