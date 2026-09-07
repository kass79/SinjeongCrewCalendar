package com.sinjeong.crewcalendar

import com.sinjeong.crewcalendar.presentation.live.COMMUTE_LINE_FALLBACK_ARGB
import com.sinjeong.crewcalendar.presentation.live.COMMUTE_SLOTS
import com.sinjeong.crewcalendar.presentation.live.LINE_ARGB
import com.sinjeong.crewcalendar.presentation.live.LINE_NAMES
import com.sinjeong.crewcalendar.presentation.live.commuteAdvance
import com.sinjeong.crewcalendar.presentation.live.commuteSlot
import com.sinjeong.crewcalendar.presentation.live.lineArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 출퇴근 역 **미니 노선**(v1.7.12 ①)의 칸 좌표와 호선 색을 잠근다.
 *
 * 칸은 `0`(5번째 전역 이상) … `5`(등록한 역)이다. `arvlMsg2` 는 승강장 전광판 문장이라 꼴이
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
        // `commuteApproaching` 이 2 를 빼지만 함수 자체는 어떤 값이 와도 0..last 를 돌려준다.
        assertTrue(commuteSlot("당역 출발", "2") in 0..last)
        assertTrue(commuteSlot("", "2") in 0..last)
    }

    /* ── arvlMsg2 글자꼴 ─────────────────────────────────────── */

    @Test
    fun `N번째 전역 은 5 빼기 N`() {
        // v1.7.12 ③ — 칸이 4개(3번째 전역까지)에서 **6개(5번째 전역까지)** 로 넓어졌다.
        // 카스: *"미니 노선이 3번째 전역까지 가 최선인거야? 5칸 전해도 될꺼같은데?"*
        assertEquals(0, commuteSlot("5번째 전역", "99"))
        assertEquals(1, commuteSlot("4번째 전역", "99"))
        assertEquals(2, commuteSlot("3번째 전역", "99"))
        assertEquals(3, commuteSlot("2번째 전역", "99"))
        assertEquals(4, commuteSlot("1번째 전역", "99"))
        assertEquals(3, commuteSlot("[2]번째 전역 (오목교(목동운동장앞))", "99"))
        assertEquals(3, commuteSlot("2 번째  전역", "99")) // 사이 공백이 섞여도 같은 칸
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
    fun `0번째 전역 은 역 점 위 - 5 빼기 0`() {
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
    fun `이름 표와 색 표의 키가 한 벌이다`() {
        // v1.7.12 ① 은 여섯 id(1061·1069·1071·1078·1094·1095)에 **이름만 있고 색이 없어**
        // 그 노선 기관차가 조용히 회색으로 떨어졌다 — 화면이 죽지 않아 눈으로만은 못 잡는다.
        // LINE_NAMES·LINE_ARGB 가 `internal` 인 이유가 이 대조다(둘 다 KDoc 에 적혀 있다).
        assertEquals("이름만 있고 색이 없는 id", emptySet<String>(), LINE_NAMES.keys - LINE_ARGB.keys)
        assertEquals("색만 있고 이름이 없는 id", emptySet<String>(), LINE_ARGB.keys - LINE_NAMES.keys)
    }

    @Test
    fun `모든 호선 색이 완전 불투명하다`() {
        // 알파가 빠지면(0x00…) 기관차가 통째로 안 보인다.
        listOf("1001", "1002", "1003", "1004", "1005", "1006", "1007", "1008", "1009",
            "1032", "1063", "1065", "1067", "1075", "1077", "1081", "1092", "1093", "9999")
            .forEach { assertEquals(it, 0xFFL, (lineArgb(it) ushr 24) and 0xFFL) }
    }

    /* ── 칸 사이 보간 (v1.7.13 ①) ────────────────────────────
     *
     * 카스: *"역으로 다가오는 열차아이콘이 안움직이는데?"* — [commuteAdvance] 가 남은 초로
     * 칸 사이를 메운다. 확정 표 "열차 이동 = 시간 기반 등속 전진"의 세 조항을 잠근다.
     */

    @Test
    fun `처음 본 열차는 그 칸에서 시작한다`() {
        assertEquals(0f, commuteAdvance(null, 0, 300, 0f), 1e-4f)
        assertEquals(3f, commuteAdvance(null, 3, 120, 9f), 1e-4f)  // dt 가 있어도 튀지 않는다
    }

    @Test
    fun `시간이 흐르면 등속으로 앞으로 간다`() {
        // 칸 2 · 남은 300초 → 남은 칸 3개를 300초에 = **한 칸에 100초**.
        assertEquals(2.01f, commuteAdvance(2f, 2, 300, 1f), 1e-4f)
        assertEquals(2.10f, commuteAdvance(2f, 2, 300, 10f), 1e-4f)
        // 1초 눈금을 열 번 밟아도 같은 자리에 온다(누적).
        var p = 2f
        repeat(10) { p = commuteAdvance(p, 2, 300, 1f) }
        assertEquals(2.10f, p, 1e-3f)
    }

    /**
     * ⚠ **v1.7.13 ① 을 처음 만들 때 낸 버그를 잠근다.** 자리를 "칸 + 흐른 초"로 매번 다시
     * 계산했더니 15초 폴링마다 계산값이 칸으로 되감겨, 뒷걸음은 안 하지만 **13초를 붙박여**
     * 있었다(에뮬 실측 2026-09-07 03:58, 5호선 마곡). 걸음을 더하는 지금 꼴은 안 멎는다.
     */
    @Test
    fun `폴링이 와도 자리가 안 멎는다`() {
        var p = commuteAdvance(null, 0, 300, 0f)                   // 조회 ① 눈금 300초
        repeat(15) { p = commuteAdvance(p, 0, 300, 1f) }           // 15초 흐름
        assertEquals(0.25f, p, 1e-3f)                              // 한 칸 60초 → 0.25칸
        val after = commuteAdvance(p, 0, 285, 1f)                  // 조회 ② 같은 칸·줄어든 눈금
        assertTrue("폴링 뒤에도 이어 가야 한다 ($p → $after)", after > p)
    }

    @Test
    fun `다음 칸을 절대 안 넘는다`() {
        // 응답이 늦어 걸음이 커져도 예측은 한 칸에서 선다 — 있지도 않은 도착을 안 그린다.
        assertEquals(3f, commuteAdvance(2f, 2, 300, 99_999f), 1e-4f)
        assertEquals(1f, commuteAdvance(0.5f, 0, 60, 600f), 1e-4f)
        // 남은 초가 0 이하로 와도 마찬가지(0 나눗셈 없음).
        assertEquals(3f, commuteAdvance(2f, 2, 0, 5f), 1e-4f)
        assertEquals(3f, commuteAdvance(2f, 2, -5, 3f), 1e-4f)
    }

    @Test
    fun `앞으로만 간다 - 뒤로는 한 픽셀도 안 물러난다`() {
        // 흐른 시간이 0 이거나 음수(시계 되돌림)면 제자리.
        assertEquals(2.15f, commuteAdvance(2.15f, 2, 285, 0f), 1e-4f)
        assertEquals(2.15f, commuteAdvance(2.15f, 2, 285, -9f), 1e-4f)
        // 칸 자체가 뒤로 온 이상한 응답에도 끌어내리지 않는다(그 자리에 선다).
        assertEquals(3.4f, commuteAdvance(3.4f, 1, 400, 0f), 1e-4f)
        assertEquals(3.4f, commuteAdvance(3.4f, 1, 400, 30f), 1e-4f)
    }

    @Test
    fun `도착 진입이면 역 점 위에 선다`() {
        // arvlCd 0·1 은 commuteSlot 이 이미 마지막 칸을 준다 — 거기서는 더 안 움직인다.
        assertEquals(last.toFloat(), commuteAdvance(null, last, 0, 0f), 1e-4f)
        assertEquals(last.toFloat(), commuteAdvance(null, last, 30, 999f), 1e-4f)
        assertEquals(last.toFloat(), commuteAdvance(2.5f, last, 30, 0f), 1e-4f)
    }

    @Test
    fun `자리는 늘 0 과 마지막 칸 사이다 - 예외 0`() {
        val etas = listOf(-10, 0, 1, 37, 300, 4000, Int.MAX_VALUE)
        val dts = listOf(-5f, 0f, 0.5f, 15f, 600f, 1e9f)
        val prevs = listOf<Float?>(null, 0f, 2.4f, last.toFloat())
        for (s in -2..COMMUTE_SLOTS + 1) for (e in etas) for (t in dts) for (p in prevs) {
            val v = commuteAdvance(p, s, e, t)
            assertTrue("prev=$p slot=$s eta=$e dt=$t → $v", v >= 0f && v <= last.toFloat())
            assertTrue("prev=$p slot=$s eta=$e dt=$t → $v", !v.isNaN())
        }
    }
}
