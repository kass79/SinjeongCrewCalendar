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

    /**
     * 자산 CSV 열번은 `2xxx` 인데 라이브는 `8340` 처럼 온다(v1.7.2 실측) — [sameRun] 으로 잇는다.
     * 못 이으면 지연·다음 역·이동 속도가 **조용히 사라진다.**
     */
    @Test fun `접두가 달라도 같은 운행의 시간표를 찾는다`() {
        assertEquals(2, tt.delayMinutes(1, 1, "8006", "홍대입구", "1", 25200 + 120))
        assertEquals(2, tt.delayMinutes(1, 1, "4006", "홍대입구", "1", 25200 + 120))
        assertEquals(90, tt.segmentSeconds(1, 1, "6006", "홍대입구"))
    }

    /** 지선 `5xxx` 는 본선 `25xx` 와 뒤 세 자리가 겹친다 — 행선이 본선 종착일 때만 받는다. */
    @Test fun `접두 5 는 행선을 봐야 본선 운행으로 받는다`() {
        assertNull(tt.delayMinutes(1, 2, "5513", "홍대입구", "1", 90000))
        assertEquals(0, tt.delayMinutes(1, 2, "5513", "홍대입구", "1", 90000, "성수종착"))
    }

    /**
     * [pickRun] 이 같은 몸통 둘을 가를 때 쓰는 시각 — [arriveSecAt] 과 달리 **지나간 정차도 준다**
     * (늦은 열차가 아직 그 역에 서 있는지를 보려는 것이라 미래만 보면 늘 빈손이다, v1.7.3).
     */
    @Test fun `schedSecAt 은 지나간 정차도 지금에 가장 가까운 것으로 준다`() {
        assertEquals(25200, tt.schedSecAt(1, 1, "2006", "홍대입구", 25400))   // 이미 지난 도착
        assertEquals(25320, tt.schedSecAt(1, 1, "8006", "신촌", 25400))       // 접두가 달라도 찾는다
        assertNull(tt.schedSecAt(1, 1, "8006", "신도림", 25200))              // 안 지나는 역
    }

    /** 입고 안내가 쓰는 신도림 도착 시각 — 지나간 정차는 안 준다. */
    @Test fun `역 도착 시각은 지금 이후 첫 번째`() {
        assertEquals(25320, tt.arriveSecAt(1, 1, "8006", "신촌", 25200))
        assertNull(tt.arriveSecAt(1, 1, "8006", "홍대입구", 25400))
        assertNull(tt.arriveSecAt(1, 1, "8006", "신도림", 25200))
    }
    /**
     * v1.6.88 안전장치 — 기기 시계가 틀리면(GMT 에뮬 실측 "534분 빠름") 시간표와 몇 시간씩
     * 어긋난다. 그런 숫자는 보여 주지 않는 편이 낫다. **경계 120분은 살려 둔다.**
     */
    @Test fun `말이 안 되는 지연은 null, 경계 120분은 값`() {
        assertEquals(120, tt.delayMinutes(1, 1, "2006", "홍대입구", "1", 25200 + 120 * 60))
        assertEquals(-120, tt.delayMinutes(1, 1, "2006", "홍대입구", "1", 25200 - 120 * 60))
        assertNull(tt.delayMinutes(1, 1, "2006", "홍대입구", "1", 25200 + 121 * 60))
        assertNull(tt.delayMinutes(1, 1, "2006", "홍대입구", "1", 25200 - 121 * 60))
    }
    @Test fun `API 꼬리 붙은 역명도 잡는다`() { assertEquals(0, tt.delayMinutes(1, 1, "2006", "홍대입구역", "1", 25200)) }
    /** 다음 역 도착 = `다음 역 ARRIVETIME + 지연 − now` (설계서 C절). 신촌 07:02:00 + 1분 지연 − 07:01:00 */
    @Test fun `다음 역까지 초 — 지연 반영`() {
        assertEquals(25320 + 60 - (25230 + 30), tt.secondsToNextStop(1, 1, "2006", "홍대입구", 1, 25230 + 30))
    }
    /** 모르는 구간의 기본값은 v1.7.5 에서 **120 → 110초**(사용자 지정, `DEFAULT_SEG_SEC`). */
    @Test fun `역간 소요, 없으면 110`() {
        assertEquals(90, tt.segmentSeconds(1, 1, "2006", "홍대입구"))
        assertEquals(110, tt.segmentSeconds(1, 1, "2006", "이대"))
    }
    @Test fun `25시 표기는 초로 접혀 있다`() { assertEquals(90000, tt.stops(1, 2, "2513")[0].arriveSec) }
    @Test fun `주 구분 — 토 2, 일 3, 평일 1, 공휴일 3`() {
        assertEquals(2, Line2Timetable.weekTagOf(LocalDate.of(2026, 9, 5)))
        assertEquals(3, Line2Timetable.weekTagOf(LocalDate.of(2026, 9, 6)))
        assertEquals(1, Line2Timetable.weekTagOf(LocalDate.of(2026, 9, 4)))
        assertEquals(3, Line2Timetable.weekTagOf(LocalDate.of(2026, 9, 25)))
    }
    /** 알람 알림 둘째 줄 — 순수 함수라 여기서 잠근다(안드로이드를 안 부른다). */
    @Test fun `알림 한 줄 문구`() {
        val row = com.sinjeong.crewcalendar.presentation.live.PositionRow("1002", "홍대입구", "2333", "1", "성수", "2")
        assertEquals("2333열차 지금 홍대입구 출발 · +2분 지연", com.sinjeong.crewcalendar.widget.liveLine(row, 2))
        assertEquals("2333열차 지금 홍대입구 출발", com.sinjeong.crewcalendar.widget.liveLine(row, null))
    }
    @Test fun `새벽 1시는 전날 25시`() {
        val (d, s) = Line2Timetable.serviceClock(LocalDateTime.of(2026, 9, 5, 1, 0))
        assertEquals(LocalDate.of(2026, 9, 4), d); assertEquals(25 * 3600, s)
    }
}
