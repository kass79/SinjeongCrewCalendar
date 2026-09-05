package com.sinjeong.crewcalendar

import com.sinjeong.crewcalendar.domain.model.Bundled
import com.sinjeong.crewcalendar.domain.model.DutyCode
import com.sinjeong.crewcalendar.domain.model.NightCombo
import com.sinjeong.crewcalendar.domain.model.RouteTable
import com.sinjeong.crewcalendar.domain.model.myDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * **내 열차 행선판**(`Headboard.kt`)을 잠근다 — 깨지면 화면이 **틀린 행선을 확신 있게** 단다.
 *
 * 근거는 2026-09-05 사용자 실측: `51` 다이아 밤 내 열차 `8401` 에 앱이 `성수행` 을 달았지만
 * 실제는 **홍대입구행**이었다(행로표 51 휴휴 전반 `2401·2425·홍대입구역주박·2009`).
 * API `statnTnm` 은 그 시각 본선 전부가 `성수종착` 이라 못 믿는다.
 */
class HeadboardTest {

    /** 토(휴일) → 일(휴일). [NightCombo.HH] 다 — 사용자가 실측한 그 날이다. */
    private val SAT: LocalDate = LocalDate.parse("2026-09-05")

    /** 수 → 목. [NightCombo.PP]. */
    private val WED: LocalDate = LocalDate.parse("2026-09-02")

    // ── 전제: 테스트가 딛고 선 행로표 실값 ────────────────────

    @Test
    fun `전제 실값 확인`() {
        assertEquals(NightCombo.HH, Bundled.comboOf(SAT))
        assertEquals(NightCombo.PP, Bundled.comboOf(WED))
        assertEquals(
            "2401·2425·홍대입구역주박·2009",
            RouteTable.forMainNight(51, NightCombo.HH)!!.firstHalf,
        )
        assertEquals("2039", RouteTable.forMainNight(51, NightCombo.HH)!!.secondHalf)
        assertEquals(
            "2399·2423·신도림역주박·2005",
            RouteTable.forMainNight(50, NightCombo.HH)!!.firstHalf,
        )
        assertEquals(
            "2501·2523·신도림역주박·2005",
            RouteTable.forMainNight(50, NightCombo.PP)!!.firstHalf,
        )
        assertEquals("2340·2372·2404", RouteTable.forMainNight(44, NightCombo.HH)!!.firstHalf)
        assertEquals("2039·2073·1925(군자입고)", RouteTable.forMainDay(1, true)!!.firstHalf)
    }

    // ── ① 행로표 표지가 답을 알면 그것 ────────────────────────

    /**
     * 사용자 실측 그대로 — 앱이 `성수행` 을 달았던 자리다.
     * 행로표 번호 `2401` 이 API 에 **`8401`**(외선 중간 타절 대역)로 떠 있었다.
     */
    @Test
    fun `주박 표지 앞의 열차는 모두 그 역행이다`() {
        val d = DutyCode.parse("51")
        assertEquals("홍대입구행", myDestination(d, SAT, "8401"))
        assertEquals("홍대입구행", myDestination(d, SAT, "8425"))
        // 행로표 번호 그대로 떠도 같은 답
        assertEquals("홍대입구행", myDestination(d, SAT, "2401"))
        assertEquals("홍대입구행", myDestination(d, SAT, "2425"))
    }

    @Test
    fun `50 다이아는 신도림역 주박이다`() {
        // 사용자가 말한 그 날(휴휴)의 실제 열번
        assertEquals("신도림행", myDestination(DutyCode.parse("50"), SAT, "2399"))
        assertEquals("신도림행", myDestination(DutyCode.parse("50"), SAT, "8423"))
        // 평평 칸의 `2501·2523` 도 같은 주박 표지를 본다
        assertEquals("신도림행", myDestination(DutyCode.parse("50"), WED, "2501"))
        assertEquals("신도림행", myDestination(DutyCode.parse("50"), WED, "2523"))
    }

    /** `1925(군자입고)` — 표지가 **제 칸에 붙어** 있고, 앞의 열차들도 같이 성수행이다. */
    @Test
    fun `군자입고 칸은 성수행이다`() {
        val d = DutyCode.parse("1")
        val sun = LocalDate.parse("2026-09-06")        // 휴일 주간표
        assertEquals("성수행", myDestination(d, sun, "1925"))
        assertEquals("성수행", myDestination(d, sun, "2039"))
        assertEquals("성수행", myDestination(d, sun, "2073"))
    }

    /** `(군자입출고)` 는 들어갔다 다시 나오는 칸이라 **표지가 아니다.** */
    @Test
    fun `군자입출고는 표지가 아니다`() {
        // 평일 주간 13번 `2082·2136·2929(군자입출고)` — 전부 2xxx 라 3단계 null 로 떨어진다
        assertNull(myDestination(DutyCode.parse("13"), WED, "2082"))
        assertNull(myDestination(DutyCode.parse("13"), WED, "2929"))
    }

    /** 표지 **뒤**의 열차(주박 다음 날 아침 첫차)는 그 표지를 안 본다. */
    @Test
    fun `주박 뒤 열차는 표지의 영향이 없다`() {
        assertNull(myDestination(DutyCode.parse("51"), SAT, "2009"))
    }

    /** 후반 열차는 **전반 표지**를 안 본다 — 반을 넘지 않는다. */
    @Test
    fun `후반 열차는 전반 표지의 영향이 없다`() {
        assertNull(myDestination(DutyCode.parse("51"), SAT, "2039"))
        assertNull(myDestination(DutyCode.parse("51"), SAT, "8039"))
    }

    // ── ② 표지가 없으면 접두 ─────────────────────────────────

    @Test
    fun `표지가 없으면 접두 4·6은 신도림행`() {
        val d = DutyCode.parse("44")
        assertEquals("신도림행", myDestination(d, SAT, "4340"))
        assertEquals("신도림행", myDestination(d, SAT, "6372"))
    }

    @Test
    fun `표지가 없으면 접두 3·5는 성수행`() {
        val d = DutyCode.parse("44")
        assertEquals("성수행", myDestination(d, SAT, "3340"))
        assertEquals("성수행", myDestination(d, SAT, "5404"))
    }

    // ── ③ 그래도 모르면 안 단다 ──────────────────────────────

    /** 사용자: *"잘 모르겠으면 성수행 <- 이런 표시를 안해도 괜찮어~"* */
    @Test
    fun `표지도 접두도 없으면 null 이다`() {
        val d = DutyCode.parse("44")
        assertNull(myDestination(d, SAT, "8340"))       // 외선 중간 타절
        assertNull(myDestination(d, SAT, "2340"))       // 순환 기본
        assertNull(myDestination(d, SAT, "7372"))       // 내선 중간 타절
    }

    /** 행로표에 없는 열차는 접두조차 안 본다 — 내 운행이 아니다. */
    @Test
    fun `내 운행이 아니면 null 이다`() {
        assertNull(myDestination(DutyCode.parse("44"), SAT, "4111"))
    }

    /** 지선 근무는 이 지도의 몫이 아니다 — 지선 왕복 `5xxx` 에 접두 규칙을 먹이면 안 된다. */
    @Test
    fun `지선 근무는 null 이다`() {
        assertNull(myDestination(DutyCode.parse("지1"), WED, "5527"))
    }

    /** 열차를 안 잡는 근무(휴무·대기)는 행로표가 없다. */
    @Test
    fun `행로표가 없는 근무는 null 이다`() {
        assertNull(myDestination(DutyCode.parse("휴1"), SAT, "8401"))
    }
}
