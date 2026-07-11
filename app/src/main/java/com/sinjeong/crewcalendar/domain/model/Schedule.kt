package com.sinjeong.crewcalendar.domain.model

import java.time.LocalDate

/**
 * 날짜별 확정 배정. 패턴 계산값을 덮어쓰는 층:
 * 관리자 근무표 업로드(xlsx) / 개인 수정이 여기에 기록된다.
 */
data class Schedule(
    val id: String = "",              // "{uid}_{yyyy-MM-dd}"
    val uid: String = "",
    val date: LocalDate = LocalDate.MIN,
    val dutyRaw: String = "",         // "14", "휴3", "대2", "지13", "~" ...
    val memo: String = "",
    /** 배정 출처 */
    val source: Source = Source.PATTERN,
    /** 수정된 경우 원본 코드 */
    val originalDutyRaw: String? = null,
    val updatedAtEpochMs: Long = 0,
) {
    enum class Source { PATTERN, ROSTER_UPLOAD, MANUAL }
    val duty: DutyCode get() = DutyCode.parse(dutyRaw)
}

/** 달력 한 칸에 뿌릴 계산 결과 (패턴 + 오버라이드 + 메모 + 시각 + 공휴일 병합) */
data class DaySchedule(
    val date: LocalDate,
    val duty: DutyCode,
    val memo: String = "",
    val hasGoogleEvent: Boolean = false,
    val isOverridden: Boolean = false,
    /** 근무변경 전 패턴 원래 코드 (2줄 표시용) */
    val originalDutyRaw: String? = null,
    /** 출근시각 표시 문자열 (다이아 없으면 null) */
    val signOn: String? = null,
    /** 법정공휴일 이름 (날짜 빨강) */
    val holidayName: String? = null,
    /** 기념일 이름 (이름만 빨강, 근무는 평일) */
    val memorialName: String? = null,
    val seasonalTerm: String? = null,  // 소서, 초복 …
)
