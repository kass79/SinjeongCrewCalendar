package com.sinjeong.crewcalendar

import com.sinjeong.crewcalendar.domain.model.Bundled
import com.sinjeong.crewcalendar.domain.model.DutyCode
import com.sinjeong.crewcalendar.domain.model.MainLegs
import com.sinjeong.crewcalendar.domain.model.NightCombo
import com.sinjeong.crewcalendar.domain.model.RouteTable
import com.sinjeong.crewcalendar.domain.model.myTrainAt
import com.sinjeong.crewcalendar.domain.model.trainNumbers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * **내 열번 판정**(v1.6.84)을 잠근다 — 본선 순환선 지도가 "내 열차"를 크게 그리는 근거다.
 *
 * 기대값은 전부 [RouteTable]·[MainLegs] **실값을 읽어** 박았다(외운 값이 아니다).
 * 실행법은 [PatternTest] KDoc 참고(JUnitCore 직접 실행).
 */
class MyTrainTest {

    /** 수요일. 다음 날도 평일이라 야간 조합은 평평(PP)이다. */
    private val WED: LocalDate = LocalDate.parse("2026-09-02")

    private fun at(d: LocalDate, t: String): LocalDateTime =
        LocalDateTime.of(d, LocalTime.parse(t))

    // ── 전제: 테스트가 딛고 선 실값이 그대로인가 ──────────────

    @Test
    fun `전제 실값 확인`() {
        assertFalse("9월 2일은 평일이어야 한다", Bundled.isHolidayTimetable(WED))
        assertEquals(NightCombo.PP, Bundled.comboOf(WED))
        assertEquals(listOf("7:12", "9:40", "14:11", "15:56"), MainLegs.forDay(1, false))
        assertEquals("2045·2109·1923", RouteTable.forMainDay(1, false)!!.firstHalf)
        assertEquals("군자기지 편승·2265·2299", RouteTable.forMainDay(1, false)!!.secondHalf)
        assertEquals(listOf("20:01", "23:15", "6:00", "7:45"), MainLegs.forNight(39, NightCombo.PP))
        assertEquals(
            "33DIA#5925출고교대·2015·2057",
            RouteTable.forMainNight(39, NightCombo.PP)!!.secondHalf,
        )
    }

    // ── 열번 뽑기 ───────────────────────────────────────────

    /**
     * ⚠ 야간표 첫 칸은 **인수인계 주석**일 수 있다 — v1.6.78이 알람에서 물렸던 자리와 같다.
     * 네 자리 숫자인 토막만 남기면 설명 토막이 자연히 빠진다.
     */
    @Test
    fun `네 자리 열번만 순서대로 뽑는다`() {
        assertEquals(listOf("2045", "2109", "1923"), trainNumbers("2045·2109·1923"))
        // 설명 토막은 빠진다
        assertEquals(listOf("2265", "2299"), trainNumbers("군자기지 편승·2265·2299"))
        assertEquals(listOf("2015", "2057"), trainNumbers("33DIA#5925출고교대·2015·2057"))
        assertEquals(listOf("5925"), trainNumbers("2015열차교대·5925"))
        assertEquals(listOf("1936", "2101"), trainNumbers("군자편승·군자출고·1936·2101"))
        // 괄호가 붙은 토막도 네 자리가 아니라 빠진다
        assertEquals(listOf("6941", "2272"), trainNumbers("6941·2272·2306(동대문교대)"))
        // 주박 칸
        assertEquals(listOf("2488", "2514", "2004"), trainNumbers("2488·2514·홍대입구역주박·2004"))
        // 네 자리가 하나도 없는 칸
        assertEquals(emptyList<String>(), trainNumbers("운휴대기(33·34·35 공통)"))
        assertEquals(emptyList<String>(), trainNumbers("-"))
    }

    // ── 주간 본선 ───────────────────────────────────────────

    /** 전반 사업 중(7:12~9:40) — 후보는 전반 열번 셋. */
    @Test
    fun `전반 사업 중이면 전반 열번들`() {
        val m = myTrainAt(DutyCode.parse("1"), WED, at(WED, "08:00"))!!
        assertTrue(m.riding)
        assertEquals(listOf("2045", "2109", "1923"), m.nos)
        assertNull(m.startAt)
        assertFalse(m.nextDay)
        assertEquals("신도림", m.place)     // 2045 = 영업열차 -> 신도림 교대
    }

    /** 전반과 후반 사이(12:00) — 아직 안 탔으니 다음 사업을 말한다. */
    @Test
    fun `사업 사이면 다음 사업을 알려준다`() {
        val m = myTrainAt(DutyCode.parse("1"), WED, at(WED, "12:00"))!!
        assertFalse(m.riding)
        assertEquals(listOf("2265", "2299"), m.nos)     // 군자기지 편승 토막은 빠졌다
        assertEquals(LocalTime.of(14, 11), m.startAt)
        assertEquals("신도림", m.place)
    }

    /** 출근 전(6:00)이면 전반이 다음 사업이다. */
    @Test
    fun `출근 전이면 전반이 다음 사업`() {
        val m = myTrainAt(DutyCode.parse("1"), WED, at(WED, "06:00"))!!
        assertFalse(m.riding)
        assertEquals("2045", m.nos.first())
        assertEquals(LocalTime.of(7, 12), m.startAt)
    }

    /** 후반까지 끝나면(15:56 이후) 맡은 열차가 없다. */
    @Test
    fun `사업이 다 끝나면 없다`() {
        assertNull(myTrainAt(DutyCode.parse("1"), WED, at(WED, "20:00")))
    }

    // ── 야간 본선 (후반 = 익일 아침) ────────────────────────

    /**
     * ⚠ 야간 후반은 **익일**이다. `39 평평` 후반은 그 날 새벽이 아니라 **다음 날** 6:00~7:45.
     * 첫 토막이 인수인계 주석(`33DIA#5925출고교대`)이라 열번은 `2015` 부터다.
     */
    @Test
    fun `야간 후반은 익일 아침이다`() {
        val duty = DutyCode.parse("39")
        // 그 날 밤 전반 사업 중
        val first = myTrainAt(duty, WED, at(WED, "21:00"))!!
        assertTrue(first.riding)
        assertEquals(listOf("2429", "2463", "2493", "5952"), first.nos)
        assertFalse(first.nextDay)

        // 익일 아침 후반 사업 중
        val second = myTrainAt(duty, WED, at(WED.plusDays(1), "06:30"))!!
        assertTrue(second.riding)
        assertEquals(listOf("2015", "2057"), second.nos)
        assertTrue(second.nextDay)
        assertEquals("신도림", second.place)

        // 전반이 끝나고 후반 전(익일 새벽 3시)이면 후반을 다음 사업으로
        val between = myTrainAt(duty, WED, at(WED.plusDays(1), "03:00"))!!
        assertFalse(between.riding)
        assertEquals(LocalTime.of(6, 0), between.startAt)
        assertTrue(between.nextDay)
    }

    /** 기지 출고로 시작하는 후반은 장소가 기지다 — 알람과 **같은 규칙 한 벌**을 쓴다. */
    @Test
    fun `기지 출고 후반은 장소가 기지`() {
        // 33 휴평 후반 = "2015열차교대·5925" -> 5925 = 신정기지 출고
        val sat = LocalDate.parse("2026-09-05")          // 토(휴일) + 일(휴일) = 휴휴
        val hp = LocalDate.parse("2026-09-06")           // 일(휴일) + 월(평일) = 휴평
        assertEquals(NightCombo.HP, Bundled.comboOf(hp))
        val m = myTrainAt(DutyCode.parse("33"), hp, at(hp.plusDays(1), "06:00"))!!
        assertEquals(listOf("5925"), m.nos)
        assertEquals("신정기지", m.place)
        // 운휴대기(휴휴 33)는 맡은 열차가 없다
        assertEquals(NightCombo.HH, Bundled.comboOf(sat))
        assertTrue(RouteTable.isStandbyOnly(33, NightCombo.HH))
        assertNull(myTrainAt(DutyCode.parse("33"), sat, at(sat, "18:00")))
    }

    // ── 열차가 없는 근무 ────────────────────────────────────

    @Test
    fun `휴무 비번 대기는 맡은 열차가 없다`() {
        listOf("휴", "휴3", "~", "대5", "지대2", "주", "연차").forEach {
            assertNull(it + " 는 null 이어야 한다", myTrainAt(DutyCode.parse(it), WED, at(WED, "09:00")))
        }
    }

    /** 지선 근무는 **본선 지도의 몫이 아니다** — 순환선 위에 지선 열번을 얹으면 안 된다. */
    @Test
    fun `지선 근무는 본선 지도에 없다`() {
        listOf("지1", "지8", "지11").forEach {
            val d = DutyCode.parse(it)
            assertTrue(it + " 는 지선이어야 한다", d.isBranch)
            assertNull(myTrainAt(d, WED, at(WED, "09:00")))
        }
    }
}
