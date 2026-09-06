package com.sinjeong.crewcalendar

import com.sinjeong.crewcalendar.presentation.live.COMMUTE_LINE_FALLBACK_ARGB
import com.sinjeong.crewcalendar.presentation.live.COMMUTE_SLOTS
import com.sinjeong.crewcalendar.presentation.live.commuteSlot
import com.sinjeong.crewcalendar.presentation.live.lineArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 출퇴근 역 **미니 노선**(v1.7.12 ①)의 칸 좌표와 호선 색을 잠근다.
 *
 * 칸은 `0`(3번째 전역) … `3`(등록한 역)이다. `arvlMsg2` 는 승강장 전광판 문장이라 꼴이
 * 여러 가지고, **모르는 꼴이 와도 절대 예외가 나면 안 된다** — 상세시트가 통째로 죽는다.
 */
class CommuteMiniTest {

    private val last = COMMUTE_SLOTS - 1

    /* ── arvlCd 우선 ─────────────────────────────────────────── */

    @Test
    fun `진입 0 도착 1 은 글자와 무관하게 역 점 위`() {
        assertEquals(last, commuteSlot("3번째 전역", "0"))
        assertEquals(last, commuteSlot("6분 후", "1"))
        assertEquals(last, commuteSlot("", "0"))
    }

    @Test
    fun `출발 2 도 좌표는 나온다 - 화면이 거를 뿐 함수는 안 죽는다`() {
        // `commuteApproaching` 이 2 를 빼지만 함수 자체는 어떤 값이 와도 0..3 을 돌려준다.
        assertTrue(commuteSlot("당역 출발", "2") in 0..last)
        assertTrue(commuteSlot("", "2") in 0..last)
    }

    /* ── arvlMsg2 글자꼴 ─────────────────────────────────────── */

    @Test
    fun `N번째 전역 은 3 빼기 N`() {
        assertEquals(0, commuteSlot("3번째 전역", "99"))
        assertEquals(1, commuteSlot("2번째 전역", "99"))
        assertEquals(2, commuteSlot("1번째 전역", "99"))
        assertEquals(1, commuteSlot("[2]번째 전역 (오목교(목동운동장앞))", "99"))
        assertEquals(1, commuteSlot("2 번째  전역", "99")) // 사이 공백이 섞여도 같은 칸
    }

    @Test
    fun `칸을 넘는 숫자는 가장 가까운 칸으로 떨어진다`() {
        assertEquals(0, commuteSlot("5번째 전역", "99"))
        assertEquals(0, commuteSlot("12번째 전역", "99"))
        assertEquals(0, commuteSlot("99999999999999번째 전역", "99")) // Int 범위 밖 — 안 죽는다
    }

    @Test
    fun `숫자 없는 전역 은 한 정거장 전`() {
        assertEquals(last - 1, commuteSlot("전역 도착", "5"))
        assertEquals(last - 1, commuteSlot("전역 진입", "4"))
        assertEquals(last - 1, commuteSlot("까치산 전역출발", "3"))
    }

    @Test
    fun `전전역 은 두 정거장 전 - 2026-09-07 5호선 마곡 실화면 꼴`() {
        // v1.7.9 설계 때 못 본 꼴. `전역` 만 보면 두 정거장 전 열차를 한 정거장 전에 세운다.
        assertEquals(last - 2, commuteSlot("전전역 출발", "3"))
        assertEquals(last - 2, commuteSlot("전전역 도착", "5"))
        assertEquals(last - 3, commuteSlot("전전전역 출발", "3"))
        // 역 이름에 붙어 있으면 안 집는다 — arvlCd 로 떨어진다.
        assertEquals(last - 1, commuteSlot("무슨전역 출발", "3"))
    }

    @Test
    fun `당역 은 역 점 위`() {
        assertEquals(last, commuteSlot("당역 도착", "99"))
        assertEquals(last, commuteSlot("당역 진입", "99"))
    }

    @Test
    fun `남은 시간 문장이면 상태코드가 위치를 말한다`() {
        // "5분 30초 후" 에는 위치가 없다 — arvlCd 3·4·5 가 전역임을 알려 준다.
        assertEquals(last - 1, commuteSlot("5분 30초 후", "5"))
        assertEquals(last - 1, commuteSlot("6분 후 (오목교(목동운동장앞))", "3"))
        // 99(운행중)는 아직 멀다 — 맨 왼쪽
        assertEquals(0, commuteSlot("8분 후", "99"))
        assertEquals(0, commuteSlot("8분 후 (마곡)", "99"))
    }

    @Test
    fun `예상 밖 글자와 빈 값에도 0부터 3 안이고 예외가 없다`() {
        val weird = listOf(
            "", "   ", "???", "전 역", "번째", "0번째 전역", "-3번째 전역",
            "출발", "🚃", "3번째전역이 아님", "N번째 전역",
        )
        val codes = listOf("", "0", "1", "2", "3", "4", "5", "99", "7", "abc")
        weird.forEach { m -> codes.forEach { c ->
            assertTrue("$m / $c", commuteSlot(m, c) in 0..last)
        } }
    }

    @Test
    fun `0번째 전역 은 역 점 위 - 3 빼기 0`() {
        assertEquals(last, commuteSlot("0번째 전역", "99"))
    }

    /* ── 호선 색 ─────────────────────────────────────────────── */

    @Test
    fun `아는 호선은 서울 공식 노선색`() {
        assertEquals(0xFF0052A4L, lineArgb("1001")) // 1호선 남색
        assertEquals(0xFF00A84DL, lineArgb("1002")) // 2호선 초록
        assertEquals(0xFF996CACL, lineArgb("1005")) // 5호선 보라
        assertEquals(0xFF747F00L, lineArgb("1007")) // 7호선 올리브
        assertEquals(0xFFD4003BL, lineArgb("1077")) // 신분당 빨강
        assertEquals(0xFF81A914L, lineArgb("1093")) // 서해
    }

    @Test
    fun `모르는 id 는 기본 회색 - 앱이 안 죽는다`() {
        assertEquals(COMMUTE_LINE_FALLBACK_ARGB, lineArgb("9999"))
        assertEquals(COMMUTE_LINE_FALLBACK_ARGB, lineArgb(""))
        assertEquals(COMMUTE_LINE_FALLBACK_ARGB, lineArgb("1002 "))  // 공백 섞임 = 모르는 값
        assertNotEquals(COMMUTE_LINE_FALLBACK_ARGB, lineArgb("1002"))
    }

    @Test
    fun `모든 호선 색이 완전 불투명하다`() {
        // 알파가 빠지면(0x00…) 기관차가 통째로 안 보인다.
        listOf("1001", "1002", "1003", "1004", "1005", "1006", "1007", "1008", "1009",
            "1032", "1063", "1065", "1067", "1075", "1077", "1081", "1092", "1093", "9999")
            .forEach { assertEquals(it, 0xFFL, (lineArgb(it) ushr 24) and 0xFFL) }
    }
}
