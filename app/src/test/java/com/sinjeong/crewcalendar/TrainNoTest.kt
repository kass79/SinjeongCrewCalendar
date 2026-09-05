package com.sinjeong.crewcalendar

import com.sinjeong.crewcalendar.domain.model.depotBoundInner
import com.sinjeong.crewcalendar.domain.model.isDepotBoundSinjeong
import com.sinjeong.crewcalendar.domain.model.runKey
import com.sinjeong.crewcalendar.domain.model.sameRun
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 열번 판정(`TrainNo.kt`)을 잠근다 — 깨지면 **내 열차가 조용히 미검출**이 되고
 * 신정 입고 안내가 사라진다(둘 다 "정상적으로 빈 화면"과 구별이 안 된다).
 *
 * 근거는 2026-09-05 사용자 실측: `44` 다이아 전반 `2340` 이 API 에 **`8340`** 으로 떠 있었다.
 */
class TrainNoTest {

    @Test
    fun `같은 운행은 뒤 세 자리가 같다`() {
        listOf("2340", "4340", "6340", "8340").forEach {
            assertTrue("$it 이 2340 과 같은 운행으로 안 잡혔다", sameRun("2340", it))
        }
        assertTrue(sameRun("2340", "3340"))     // 군자 도착 내선
        assertTrue(sameRun("2340", "7340"))     // 중간 주박·타절
        assertTrue(sameRun("2340", "9340"))
    }

    @Test
    fun `뒤 세 자리가 다르면 다른 운행이다`() {
        assertFalse(sameRun("2340", "2341"))    // 끝자리 = 내선·외선 구분
        assertFalse(sameRun("2340", "2350"))
        assertFalse(sameRun("2340", "8341"))
    }

    /** 지선 왕복 `55xx~57xx` 과 본선 `25xx` 는 뒤 세 자리가 겹친다 — 행선으로 가른다. */
    @Test
    fun `접두 5 는 행선이 본선 종착일 때만 인정한다`() {
        assertFalse("까치산행 지선 열차가 본선 내 열차가 됐다", sameRun("2501", "5501", "까치산"))
        assertFalse(sameRun("2501", "5501", "신도림지선"))
        assertTrue(sameRun("2501", "5501", "성수종착"))
        assertTrue(sameRun("2501", "5501", "군자"))
        assertFalse("행선을 모르면 거부해야 한다", sameRun("2501", "5501", null))
    }

    /** 후보가 지선이면 접두 변형을 인정하지 않는다(지선 변형 여부 미확인). */
    @Test
    fun `지선 후보는 정확히 같은 번호만`() {
        assertTrue(sameRun("5695", "5695"))
        assertFalse(sameRun("5695", "2695"))
        assertFalse(sameRun("5695", "8695", "성수종착"))
    }

    /** 성수지선 `1xxx` 는 본선 운행이 아니다 — 2026-09-05 실호출의 `1663`·`1664`. */
    @Test
    fun `성수지선 1xxx 는 본선 후보와 안 묶인다`() {
        assertFalse(sameRun("2663", "1663", "신설동"))
    }

    @Test
    fun `앞의 0 과 S 를 벗기고 견준다`() {
        assertTrue(sameRun("2340", "02340"))
        assertTrue(sameRun("2340", "S2340"))
        assertTrue(sameRun("02340", "8340"))
        assertEquals("340", runKey("S2340"))
        assertEquals("340", runKey("8340"))
    }

    @Test
    fun `네 자리가 아니면 어떤 것과도 안 묶인다`() {
        assertFalse(sameRun("2340", "340"))
        assertFalse(sameRun("2340", ""))
        assertFalse(sameRun("", "8340"))
        assertFalse(sameRun("2340", "83400"))
    }

    @Test
    fun `신정 도착은 접두 4 내선 · 6 외선`() {
        assertTrue(isDepotBoundSinjeong("4340"))
        assertTrue(isDepotBoundSinjeong("6341"))
        assertFalse(isDepotBoundSinjeong("2340"))
        assertFalse(isDepotBoundSinjeong("8340"))
        assertEquals(true, depotBoundInner("4340"))
        assertEquals(false, depotBoundInner("6341"))
        assertNull(depotBoundInner("2340"))
    }
}
