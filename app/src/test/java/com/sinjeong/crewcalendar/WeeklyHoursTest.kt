package com.sinjeong.crewcalendar

import com.sinjeong.crewcalendar.domain.model.DaySchedule
import com.sinjeong.crewcalendar.domain.model.DutyCode
import com.sinjeong.crewcalendar.domain.usecase.WeeklyHours
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
