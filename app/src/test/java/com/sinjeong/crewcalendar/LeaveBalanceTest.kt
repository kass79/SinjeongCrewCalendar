package com.sinjeong.crewcalendar

import com.sinjeong.crewcalendar.domain.model.LEAVE_CHIPS
import com.sinjeong.crewcalendar.domain.model.LeaveQuota
import com.sinjeong.crewcalendar.domain.model.decodeLeaveQuotas
import com.sinjeong.crewcalendar.domain.model.encodeLeaveQuotas
import com.sinjeong.crewcalendar.domain.model.leaveCountText
import com.sinjeong.crewcalendar.domain.model.leaveLeft
import com.sinjeong.crewcalendar.domain.model.leaveUsedInApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 남은 휴가 세기(v1.7.20 ④) — `domain/model/LeaveBalance.kt` */
class LeaveBalanceTest {
    private fun d(s: String) = LocalDate.parse(s)

    @Test fun `대상은 근무변경의 휴가 칩 — 운휴·지휴·충당 계열·교육·회행은 빠진다`() {
        for (c in listOf("연차", "보상", "촉연", "대휴", "장휴", "청휴", "학습", "만휴", "돌봄휴가", "동행휴가", "병가", "공가", "가연차"))
            assertTrue(c, c in LEAVE_CHIPS)
        for (c in listOf("운휴", "지휴", "충당", "대기충당", "교체", "지근", "교육", "회행", "작연차", "기타휴가"))
            assertFalse(c, c in LEAVE_CHIPS)
        assertEquals(13, LEAVE_CHIPS.size)
    }

    @Test fun `그 해 1월 1일부터 12월 31일까지만 센다`() {
        val ov = mapOf(
            d("2025-12-31") to "연차", d("2026-01-01") to "연차", d("2026-12-31") to "연차",
            d("2027-01-01") to "연차", d("2026-06-01") to "대휴",
        )
        assertEquals(2, leaveUsedInApp(ov, "연차", 2026))
        assertEquals(1, leaveUsedInApp(ov, "연차", 2025))
        assertEquals(1, leaveUsedInApp(ov, "연차", 2027))   // 12월에 내년 1월을 고치면 내년 개수
        assertEquals(1, leaveUsedInApp(ov, "대휴", 2026))
    }

    @Test fun `근무변경을 되돌리거나 바꾸면 저절로 맞는다`() {
        val before = mapOf(d("2026-03-02") to "연차", d("2026-03-03") to "연차")
        val q = LeaveQuota(15, 3)
        assertEquals(10, leaveLeft(q, leaveUsedInApp(before, "연차", 2026)))
        // 3일을 되돌림(패턴 복귀 = 빈 값 또는 기록 없음) · 2일을 대휴로 바꿈
        val after = mapOf(d("2026-03-02") to "대휴", d("2026-03-03") to "")
        assertEquals(12, leaveLeft(q, leaveUsedInApp(after, "연차", 2026)))
        assertEquals(1, leaveUsedInApp(after, "대휴", 2026))
    }

    @Test fun `다른 근무·충당 계열은 휴가로 안 센다`() {
        val ov = mapOf(
            d("2026-04-01") to "충당 지2", d("2026-04-02") to "운휴", d("2026-04-03") to "지휴",
            d("2026-04-04") to "38", d("2026-04-05") to "연차 ",   // 앞뒤 빈칸은 같은 연차
        )
        assertEquals(1, leaveUsedInApp(ov, "연차", 2026))
        assertEquals(0, leaveUsedInApp(ov, "충당", 2026))   // `충당 지2` 는 충당 칩 개수로도 안 센다(휴가 칩만 셈)
    }

    @Test fun `넘치면 음수 — 진짜 빼기 기호`() {
        assertEquals(-1, leaveLeft(LeaveQuota(2, 1), 2))
        assertEquals("−1", leaveCountText(-1))
        assertEquals("0", leaveCountText(0))
        assertEquals("12", leaveCountText(12))
    }

    @Test fun `저장 한 줄 왕복 · 안 적은 휴가는 없다 · 깨진 칸은 버린다`() {
        val m = mapOf("연차" to LeaveQuota(15, 3), "대휴" to LeaveQuota(2))
        assertEquals(m, decodeLeaveQuotas(encodeLeaveQuotas(m)))
        assertEquals(null, decodeLeaveQuotas(encodeLeaveQuotas(m))["병가"])
        assertEquals(emptyMap<String, LeaveQuota>(), decodeLeaveQuotas(null))
        assertEquals(mapOf("촉연" to LeaveQuota(4)), decodeLeaveQuotas("촉연:4;깨짐;장휴:x:1"))
    }
}
