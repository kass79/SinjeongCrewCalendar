package com.sinjeong.crewcalendar

import com.sinjeong.crewcalendar.domain.model.BundledTimetable
import com.sinjeong.crewcalendar.domain.model.DutyCode
import com.sinjeong.crewcalendar.widget.DeadheadAlarm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * 편승 알람 예약 목록의 **문자열 직렬화**를 잠근다(v1.6.86 점검 #12).
 *
 * 이 왕복이 깨지면 켜 둔 알람이 통째로 사라지는데, 조용히 `null`로 버려지는 자리라
 * 화면에는 "예약 안 됨"으로만 보인다 — 그래서 값으로 못 박는다.
 *
 * ⚠ `./gradlew test`는 이 저장소 경로(07_프로젝트)의 한글 때문에 죽는다.
 * 실행은 컴파일 후 JUnitCore 직접 — 명령은 [PatternTest] 주석 참고.
 */
class DeadheadAlarmTest {

    private val date = LocalDate.of(2026, 9, 4)

    /** 새 4칸 형식 왕복 — 쓴 그대로 읽힌다. 문구엔 공백·한글·`·`가 들어간다. */
    @Test fun newFormat_roundTrips() {
        val key = date to DeadheadAlarm.LEG_SECOND
        val alarm = DeadheadAlarm.Alarm(LocalTime.of(19, 5), "양천구청역 19:10 편승 (신도림 19:36 출발) · 알림 19:05")

        val line = DeadheadAlarm.encode(key, alarm)
        assertEquals("2026-09-04|2|19:05|양천구청역 19:10 편승 (신도림 19:36 출발) · 알림 19:05", line)
        assertEquals(key to alarm, DeadheadAlarm.decode(line))
    }

    /**
     * v1.6.29 이전에 저장된 3칸(`날짜|시각|문구`)도 계속 읽힌다 — **전반**으로.
     * 둘째 칸에 `:`가 있는지로 구분한다.
     */
    @Test fun legacyThreeFieldEntry_readsAsFirstLeg() {
        val (key, alarm) = DeadheadAlarm.decode("2026-09-04|07:30|양천구청역 7:30 도착")!!

        assertEquals(date, key.first)
        assertEquals(DeadheadAlarm.LEG_FIRST, key.second)
        assertEquals(LocalTime.of(7, 30), alarm.at)
        assertEquals("양천구청역 7:30 도착", alarm.text)
    }

    /**
     * v1.6.88: 다섯째 칸에 **편승 열번 후보**가 실린다. 알람이 울릴 때 실시간 위치를 찾는 데 쓴다.
     * 후보가 없으면 칸 자체를 안 쓴다 — 옛 4칸 기록과 **글자까지 똑같이** 남기려는 것이다.
     */
    @Test fun `5칸 레코드에 열번 후보가 실린다`() {
        val a = DeadheadAlarm.Alarm(LocalTime.of(12, 36), "양천구청역 12:36 도착", listOf("5581", "5586"))
        val s = DeadheadAlarm.encode(LocalDate.of(2026, 9, 6) to DeadheadAlarm.LEG_SECOND, a)
        assertEquals(a, DeadheadAlarm.decode(s)!!.second)
    }

    /** 업데이트 전에 켜 둔 4칸 알람도 그대로 읽힌다(열번만 비어 있다). */
    @Test fun `4칸 옛 레코드는 열번 없이 읽힌다`() {
        assertEquals(emptyList<String>(), DeadheadAlarm.decode("2026-09-06|2|12:36|문구")!!.second.trainNos)
    }

    /** 깨진 줄은 예외를 던지지 않고 그 줄만 버린다 — 목록 전체가 날아가면 안 된다. */
    @Test fun brokenEntries_areDroppedNotThrown() {
        listOf("", "쓰레기", "2026-09-04", "2026-09-04|2", "날짜아님|2|19:05|문구", "2026-09-04|2|25:99|문구")
            .forEach { assertNull(it, DeadheadAlarm.decode(it)) }
    }

    /**
     * **비번(`50~`) 상세시트의 알람 칩은 야간 당일과 예약 한 벌을 함께 쓴다**(v1.6.97).
     *
     * v1.6.97부터 비번 시트가 야간 당일과 **같은 구성**(행로표·지선 실시간 카드·알람 칩)이 됐다.
     * 칩에 넘기는 값이 [DutyCode.effectiveNight]가 준 `(야간 근무, 야간 날짜)`라, 예약 키
     * (`fireDate` + leg)가 야간 당일 시트가 만드는 것과 **같은 값**이 된다 — 비번 날에서
     * 켜고 끄면 그 한 벌이 바뀔 뿐이다.
     *
     * ⚠ 여기를 `day.date`(= 비번 날짜)로 되돌리면 후반 알람이 **두 벌** 잡힌다(야간 당일이
     * 이미 익일로 걸어 둔 것 + 비번 날이 새로 거는 것). 그게 이 테스트가 막는 사고다.
     */
    @Test fun `비번 시트의 알람 키는 야간 당일과 한 벌이다`() {
        val night = LocalDate.of(2026, 9, 4)      // 50 야간
        val off = night.plusDays(1)               // 50~ 비번
        val nightDuty = DutyCode.parse("50")

        val (effDuty, effDate) = DutyCode.effectiveNight(DutyCode.parse("50비"), off)!!
        assertEquals(nightDuty.raw, effDuty.raw)
        assertEquals(night, effDate)

        // 예약 키의 날짜 부분 — 두 시트가 같은 값을 만든다(= prefs 한 칸을 함께 쓴다).
        fun fireDate(d: DutyCode, at: LocalDate, second: Boolean) =
            if (BundledTimetable.advise(d, at, second).nextDay) at.plusDays(1) else at
        assertEquals(fireDate(nightDuty, night, true), fireDate(effDuty, effDate, true))
        assertEquals(fireDate(nightDuty, night, false), fireDate(effDuty, effDate, false))
        // 후반은 **비번 날 아침**에 울린다 — 비번 날짜로 다시 걸면 그게 두 벌째다.
        assertEquals(off, fireDate(effDuty, effDate, true))
        assertEquals(night, fireDate(effDuty, effDate, false))
    }
}
