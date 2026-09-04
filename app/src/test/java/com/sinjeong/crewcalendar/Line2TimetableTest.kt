package com.sinjeong.crewcalendar

import com.sinjeong.crewcalendar.domain.model.Line2Timetable
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 시간표 파서·지연 계산을 소형 fixture 로 잠근다.
 *
 * ⚠ `./gradlew test`는 이 저장소 경로(07_프로젝트)의 한글 때문에 죽는다 —
 * 컴파일 후 `tools\runtests.ps1`(JUnitCore 직접)로 돌린다.
 */
class Line2TimetableTest {
    // 홍대입구(38) 07:00:00 도착·07:00:30 출발 → 신촌(39) 07:02:00/07:02:30 → 이대(40) 07:04:00 (내선 2006, 평일)
    private val csv = """
        # fetched=2026-09-04 stations=47 rows=4
        # columns=...
        1,1,38,2006,25200,25230
        1,1,39,2006,25320,25350
        1,1,40,2006,25440,-1
        1,2,38,2513,90000,90030
    """.trimIndent()
    private val tt = Line2Timetable.parse(csv)

    @Test fun `파싱 — 주석 무시, 열번별 시각순`() {
        assertEquals(3, tt.stops(1, 1, "2006").size)
        assertEquals(38, tt.stops(1, 1, "2006")[0].stationIdx)
    }
    @Test fun `도착 사건 지연`() { assertEquals(2, tt.delayMinutes(1, 1, "2006", "홍대입구", "1", 25200 + 120)) }
    @Test fun `출발 사건 정시`() { assertEquals(0, tt.delayMinutes(1, 1, "2006", "홍대입구", "2", 25230 + 20)) }
    @Test fun `전역출발은 직전 역 출발시각 기준`() { assertEquals(1, tt.delayMinutes(1, 1, "2006", "신촌", "3", 25230 + 60)) }
    @Test fun `빠르면 음수`() { assertEquals(-1, tt.delayMinutes(1, 1, "2006", "홍대입구", "1", 25200 - 60)) }
    @Test fun `모르는 열번은 null`() { assertNull(tt.delayMinutes(1, 1, "9999", "홍대입구", "1", 25200)) }
    @Test fun `API 꼬리 붙은 역명도 잡는다`() { assertEquals(0, tt.delayMinutes(1, 1, "2006", "홍대입구역", "1", 25200)) }
    /** 다음 역 도착 = `다음 역 ARRIVETIME + 지연 − now` (설계서 C절). 신촌 07:02:00 + 1분 지연 − 07:01:00 */
    @Test fun `다음 역까지 초 — 지연 반영`() {
        assertEquals(25320 + 60 - (25230 + 30), tt.secondsToNextStop(1, 1, "2006", "홍대입구", 1, 25230 + 30))
    }
    @Test fun `역간 소요, 없으면 120`() {
        assertEquals(90, tt.segmentSeconds(1, 1, "2006", "홍대입구"))
        assertEquals(120, tt.segmentSeconds(1, 1, "2006", "이대"))
    }
    @Test fun `25시 표기는 초로 접혀 있다`() { assertEquals(90000, tt.stops(1, 2, "2513")[0].arriveSec) }
    @Test fun `주 구분 — 토 2, 일 3, 평일 1, 공휴일 3`() {
        assertEquals(2, Line2Timetable.weekTagOf(LocalDate.of(2026, 9, 5)))
        assertEquals(3, Line2Timetable.weekTagOf(LocalDate.of(2026, 9, 6)))
        assertEquals(1, Line2Timetable.weekTagOf(LocalDate.of(2026, 9, 4)))
        assertEquals(3, Line2Timetable.weekTagOf(LocalDate.of(2026, 9, 25)))
    }
    @Test fun `새벽 1시는 전날 25시`() {
        val (d, s) = Line2Timetable.serviceClock(LocalDateTime.of(2026, 9, 5, 1, 0))
        assertEquals(LocalDate.of(2026, 9, 4), d); assertEquals(25 * 3600, s)
    }
}
