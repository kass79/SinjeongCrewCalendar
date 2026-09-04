package com.sinjeong.crewcalendar

import com.sinjeong.crewcalendar.presentation.live.Heading
import com.sinjeong.crewcalendar.presentation.live.LOCO_BOARD_H
import com.sinjeong.crewcalendar.presentation.live.LOCO_BOX_H
import com.sinjeong.crewcalendar.presentation.live.headingFor
import com.sinjeong.crewcalendar.presentation.live.locoFlip
import com.sinjeong.crewcalendar.presentation.live.locoHalf
import com.sinjeong.crewcalendar.presentation.live.locoTextDeg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
     * ── 세로 화면에서 글자가 **거꾸로 안 선다** (v1.6.91) ──────────────────
     * 사용자: *"신도림 문래 그쪽에 들어가면 열번 숫자 텍스트가 거꾸로 보이는건 아니지?"*
     * 세로 창은 지도를 통째로 `rotationZ = 90f` 로 돌린다. 캔버스 글자 각도는 **지도 좌표**
     * 기준이라 아래로 달리는 열차(신도림~당산 세로변)가 화면에서 180° = 거꾸로 섰다.
     * [locoTextDeg] 가 화면 기준으로 정규화한다 — 아래 `screen()` 이 그 판정 그대로다.
     */

    /** 화면에 얹은 실제 각(−180, 180]. 사람이 읽는 각도다. */
    private fun screen(h: Heading, mapDeg: Float): Float {
        var s = (locoTextDeg(h, mapDeg) + mapDeg) % 360f
        if (s > 180f) s -= 360f
        if (s <= -180f) s += 360f
        return s
    }

    @Test
    fun `가로 화면 글자 각도는 v1_6_90 그대로`() {
        assertEquals(0f, locoTextDeg(Heading.RIGHT, 0f), 0f)
        assertEquals(0f, locoTextDeg(Heading.LEFT, 0f), 0f)
        assertEquals(-90f, locoTextDeg(Heading.UP, 0f), 0f)
        assertEquals(90f, locoTextDeg(Heading.DOWN, 0f), 0f)
    }

    /** 사용자가 잡아낸 그 자리 — 세로 화면 아래로 달리는 열차(신도림~당산 내선). */
    @Test
    fun `세로에서 아래로 달리는 열차 글자가 바로 선다`() {
        assertEquals(270f, locoTextDeg(Heading.DOWN, 90f), 0f)  // 90 + 180
        assertEquals(0f, screen(Heading.DOWN, 90f), 0f)         // 화면에서는 똑바로
    }

    /** 같은 세로변의 반대 방향(외선)은 종전 각이 이미 옳다 — 괜히 뒤집으면 안 된다. */
    @Test
    fun `세로에서 위로 달리는 열차는 안 건드린다`() {
        assertEquals(-90f, locoTextDeg(Heading.UP, 90f), 0f)
        assertEquals(0f, screen(Heading.UP, 90f), 0f)
    }

    /**
     * **경계 ±90 은 눕힌 채 둔다.** 옆으로 눕는 것은 역 이름과 같은 사정이라 읽을 수 있고,
     * 여기서 한 번 더 뒤집으면 도로 거꾸로 선다.
     */
    @Test
    fun `화면 90도 경계는 눕힌 채 둔다`() {
        assertEquals(0f, locoTextDeg(Heading.RIGHT, 90f), 0f)
        assertEquals(90f, screen(Heading.RIGHT, 90f), 0f)
        assertEquals(0f, locoTextDeg(Heading.RIGHT, -90f), 0f)
        assertEquals(-90f, screen(Heading.RIGHT, -90f), 0f)
    }

    /** 어떤 회전·어떤 머리 방향이든 화면 각은 (−90, 90] — 이 한 줄이 "거꾸로 없음"이다. */
    @Test
    fun `어느 회전에서도 화면 각이 90도를 안 넘는다`() {
        for (deg in floatArrayOf(0f, 90f, 180f, 270f, -90f)) for (h in Heading.values()) {
            val s = screen(h, deg)
            assertTrue("$h@$deg = $s", s > -90f - 1e-3f && s <= 90f + 1e-3f)
        }
    }

    /**
     * 몸통도 같이 본다 — 글자만 세우면 **바퀴를 하늘로 든 기관차**가 남는다.
     * 세로(90°)에서 배가 하늘을 보는 것은 [Heading.DOWN] 하나뿐이고, 가로(0°)에서는 아무도 아니다.
     */
    @Test
    fun `세로에서 아래로 달리는 열차만 몸통을 뒤집는다`() {
        assertTrue(locoFlip(Heading.DOWN, 90f))
        for (h in Heading.values()) assertFalse("$h", locoFlip(h, 0f))
        for (h in listOf(Heading.RIGHT, Heading.LEFT, Heading.UP)) {
            assertFalse("$h", locoFlip(h, 90f))
        }
    }
}
