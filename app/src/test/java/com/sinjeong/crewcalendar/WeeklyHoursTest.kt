package com.sinjeong.crewcalendar

import com.sinjeong.crewcalendar.domain.model.DaySchedule
import com.sinjeong.crewcalendar.domain.model.DutyCode
import com.sinjeong.crewcalendar.domain.usecase.WeeklyHours
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** 주52시간 주별 근무시간(월~일) 계산 — 설계서 E절. */
class WeeklyHoursTest {
    private fun d(y: Int, m: Int, day: Int, raw: String) = DaySchedule(LocalDate.of(y, m, day), DutyCode.parse(raw))

    @Test fun `본선 주간 계 그대로`() {           // 2026-09-04(금) 평일, 1번 계 10:04
        assertEquals(10 * 60 + 4, WeeklyHours.minutesOf(d(2026, 9, 4, "1")))
    }

    @Test fun `휴무 비번 연차는 0`() {
        assertEquals(0, WeeklyHours.minutesOf(d(2026, 9, 4, "휴5")))
        assertEquals(0, WeeklyHours.minutesOf(d(2026, 9, 4, "~")))
        assertEquals(0, WeeklyHours.minutesOf(d(2026, 9, 4, "연차")))
    }

    @Test fun `대기는 출퇴근 차`() {               // 대1 7:00~16:00
        assertEquals(9 * 60, WeeklyHours.minutesOf(d(2026, 9, 4, "대1")))
    }

    @Test fun `야간 대기는 자정을 넘겨 센다`() {    // 대11 17:00~익일 7:30
        assertEquals(14 * 60 + 30, WeeklyHours.minutesOf(d(2026, 9, 4, "대11")))
    }

    @Test fun `충당은 채운 근무의 시간`() {
        assertEquals(WeeklyHours.minutesOf(d(2026, 9, 4, "1")), WeeklyHours.minutesOf(d(2026, 9, 4, "충당 1")))
    }

    @Test fun `휴일 운휴 다이아는 0`() {       // 2026-09-20(일) 26번 — 휴일 행로표·시각표에 없다(달력도 `운휴`)
        assertEquals(0, WeeklyHours.minutesOf(d(2026, 9, 20, "26")))
        assertEquals(9 * 60 + 55, WeeklyHours.minutesOf(d(2026, 9, 21, "26")))  // 평일은 계 그대로
    }

    @Test fun `교육은 미정`() { assertNull(WeeklyHours.minutesOf(d(2026, 9, 4, "교육"))) }

    @Test fun `야간은 시작일 주에 전부`() {         // 2026-09-09(수) 야간 44(평평) → 그 주에 계 전부, 9/10(~)은 0
        val days = listOf(d(2026, 9, 9, "44"), d(2026, 9, 10, "~"))
        val w = WeeklyHours.compute(YearMonth.of(2026, 9), days)
        val week = w.first { it.from <= LocalDate.of(2026, 9, 9) && it.to >= LocalDate.of(2026, 9, 9) }
        assertEquals(WeeklyHours.minutesOf(d(2026, 9, 9, "44")), week.minutes)
    }

    @Test fun `주는 월~일이고 달 안으로 잘린다`() {  // 2026-09-01은 화요일 → 1주 = 1~6일
        val w = WeeklyHours.compute(YearMonth.of(2026, 9), emptyList())
        assertEquals(LocalDate.of(2026, 9, 1), w[0].from); assertEquals(LocalDate.of(2026, 9, 6), w[0].to)
        assertEquals(LocalDate.of(2026, 9, 28), w.last().from); assertEquals(LocalDate.of(2026, 9, 30), w.last().to)
        assertEquals("1주(1~6일) 0.0h", WeeklyHours.label(w[0]))
    }

    @Test fun `온전한 주는 날짜 범위를 안 적는다`() {
        val w = WeeklyHours.compute(YearMonth.of(2026, 9), listOf(d(2026, 9, 7, "1")))
        assertEquals("2주 10.1h", WeeklyHours.label(w[1]))
    }

    @Test fun `미정 근무는 excluded에 표시명으로`() {
        val w = WeeklyHours.compute(YearMonth.of(2026, 9), listOf(d(2026, 9, 2, "교육")))
        assertEquals(listOf("교육"), w[0].excluded)
    }

    /* ── v1.6.92 ⑦: 달에 걸친 주는 인접 달까지 합산 ─────────────────── */

    /**
     * 2026-09-01은 화요일 → 1주는 **8/31(월)~9/6(일)**.
     * 종전엔 9/1~9/6만 세서 8/31 근무가 통째로 빠졌다 — 화면은 "월~일 합계"라고 단언하는데.
     */
    @Test fun `경계 주는 앞 달 날짜까지 센다`() {
        val prev = d(2026, 8, 31, "1")                       // 월 · 본선 1번 평일 계 10:04
        val cur = d(2026, 9, 1, "2")                         // 화
        val only = WeeklyHours.compute(YearMonth.of(2026, 9), listOf(cur))
        val both = WeeklyHours.compute(YearMonth.of(2026, 9), listOf(prev, cur))
        assertEquals(WeeklyHours.minutesOf(prev)!! + WeeklyHours.minutesOf(cur)!!, both[0].minutes)
        assertTrue("앞 달을 못 읽었으면 부분 집계", only[0].partial)
        // 표시 범위는 그 달 기준 그대로 — 라벨이 달라지면 안 된다
        assertEquals(LocalDate.of(2026, 9, 1), both[0].from)
        assertEquals(only[0].from, both[0].from)
    }

    /** 부분 집계는 52시간을 넘겨도 **초과라고 단정하지 않는다** — 덜 찬 합계로 빨강을 켤 수 없다 */
    @Test fun `부분 집계는 초과 판정에서 뺀다`() {
        val big = WeeklyHours.LIMIT_MIN + 60
        assertTrue(WeeklyHours.Week(1, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 6), big, emptyList()).over)
        assertFalse(
            WeeklyHours.Week(1, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 6), big, emptyList(), partial = true).over,
        )
    }

    /** 온전한 주는 7일이 다 있으면 부분 집계가 아니다 */
    @Test fun `7일이 다 있으면 부분 집계가 아니다`() {
        val days = (7..13).map { d(2026, 9, it, "휴5") }      // 9/7(월)~9/13(일)
        val w = WeeklyHours.compute(YearMonth.of(2026, 9), days)
        assertFalse(w[1].partial)
        assertTrue(w[0].partial)                             // 1주는 8/31이 없다
    }

    /* ── v1.6.92 ⑥: 시간을 계산할 수 없는 소속은 줄 자체를 감춘다 ────────── */

    @Test fun `통상근무 4조2교대는 계산 불가`() {
        // 낱말 근무는 번호가 없어 어느 갈래에도 안 걸린다 → 매주 0_0h 였던 자리
        assertNull(WeeklyHours.minutesOf(d(2026, 9, 7, "주간")))
        assertNull(WeeklyHours.minutesOf(d(2026, 9, 7, "야간")))
        assertFalse(WeeklyHours.computable(listOf(d(2026, 9, 7, "주간"), d(2026, 9, 8, "야간"), d(2026, 9, 9, "비번"))))
    }

    @Test fun `본선 지선은 계산 가능`() {
        assertTrue(WeeklyHours.computable(listOf(d(2026, 9, 7, "1"))))
        assertTrue(WeeklyHours.computable(listOf(d(2026, 9, 7, "지2"))))
        // 근무일이 하나도 없는 달(전부 휴가)은 0h가 맞는 답이라 그대로 보여 준다
        assertTrue(WeeklyHours.computable(listOf(d(2026, 9, 7, "연차"), d(2026, 9, 8, "휴5"))))
    }
}
