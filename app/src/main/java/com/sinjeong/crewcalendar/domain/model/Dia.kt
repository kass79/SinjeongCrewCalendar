package com.sinjeong.crewcalendar.domain.model

import java.time.LocalTime

/** 다이아가 적용되는 날 (시각표 개정 기준: 평일/토요일/휴일) */
enum class DayKind { WEEKDAY, SATURDAY, HOLIDAY }

/**
 * 야간 다이아(33~51)는 당일/익일 조합별로 시각이 다르다.
 * 평평 / 평토 / 토휴 / 휴휴 / 휴평
 */
enum class NightVariant { WD_WD, WD_SAT, SAT_HOL, HOL_HOL, HOL_WD }

/** 다이아 구간(승무/휴게/회송 등) */
data class DiaLeg(
    val start: LocalTime,
    val end: LocalTime,
    /** 익일 여부 (야간 후반) */
    val endNextDay: Boolean = false,
    val fromStation: String = "",
    val toStation: String = "",
    val trainNo: String? = null,
    val kind: LegKind = LegKind.DRIVE,
) { enum class LegKind { DRIVE, BREAK, DEADHEAD, STANDBY } }

/**
 * 다이아(행로). 예: 평일 다이아 14
 *  출근 07:47 → 전반 08:32~10:58 (신정→군자) → 휴게 → 후반 17:54~19:03 → 종료
 */
data class Dia(
    val id: String = "",              // e.g. "wd-14", "hol-3", "night-44-wd_sat"
    val number: Int = 0,              // 교번 숫자 (대기조는 별도 표기)
    val dayKind: DayKind = DayKind.WEEKDAY,
    val nightVariant: NightVariant? = null,
    val isNight: Boolean = false,
    val isStandby: Boolean = false,   // 대기조 (대1~대13)
    val isBranch: Boolean = false,    // 지선 (지1~지14)
    val signOn: LocalTime = LocalTime.MIN,   // 출근
    val signOff: LocalTime = LocalTime.MIN,  // 종료
    val signOffNextDay: Boolean = false,
    val legs: List<DiaLeg> = emptyList(),
    /** 개정 버전 (예: "25.03.04") — 시각표 개정 시 새 문서로 교체 */
    val revision: String = "",
) {
    /** 구속시간(분) */
    val boundMinutes: Int
        get() {
            val off = signOff.toSecondOfDay() / 60 + if (signOffNextDay) 24 * 60 else 0
            return off - signOn.toSecondOfDay() / 60
        }
}
