package com.sinjeong.crewcalendar.domain.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 순환 근무 패턴.
 * 예: 본선 29칸 순환 ["7","11","~","휴3","6","대11","~", ...]
 * anchorDate 에 sequence[ (offset) ] 근무가 배정된 것으로 보고 이후 날짜를 계산한다.
 */
data class Pattern(
    val id: String = "",
    val name: String = "",             // "본선 기관사", "지선", "대기조(차장)" ...
    val role: CrewRole = CrewRole.DRIVER_MAIN,
    val sequence: List<String> = emptyList(),  // 근무코드 raw 문자열 순환
    val anchorDate: LocalDate = LocalDate.of(2026, 1, 1),
    /**
     * true면 토·일·법정공휴일(대체공휴일 포함)을 순환값 대신 "휴무"로 덮는다 — 통상근무 전용.
     * 공휴일은 순환 주기로 표현할 수 없어 `Bundled.PUBLIC_HOLIDAYS` 조회가 필요하다.
     */
    val restOnHolidays: Boolean = false,
    val revision: String = "",
    val createdBy: String = "",        // 관리자 uid
) {
    val length: Int get() = sequence.size

    /**
     * anchorDate 기준 offset 라인의 특정 날짜 근무코드.
     * `restOnHolidays` 덮어쓰기를 여기서 하는 이유: 달력·동료근무 매트릭스·위젯·동료(Mate)·
     * 관리자 미리보기가 전부 이 함수 하나로 근무를 뽑는다. 상위(UseCase)에서만 덮으면
     * 동료근무 화면과 위젯에는 공휴일이 근무일로 남는다.
     * 부작용·I/O 없이 상수 테이블만 읽으므로 (date, offset) → DutyCode 는 여전히 순수함수.
     */
    fun dutyOn(date: LocalDate, offset: Int): DutyCode {
        if (restOnHolidays && Bundled.isHolidayTimetable(date)) return DutyCode.parse("휴무")
        if (sequence.isEmpty()) return DutyCode.parse(null)
        val days = ChronoUnit.DAYS.between(anchorDate, date).toInt()
        val idx = Math.floorMod(days + offset, sequence.size)
        return DutyCode.parse(sequence[idx])
    }

    /** dutyOn의 역함수 — "date의 근무 = sequence[index]" 를 만족하는 offset */
    fun offsetFor(date: LocalDate, index: Int): Int =
        Math.floorMod(index - ChronoUnit.DAYS.between(anchorDate, date).toInt(), length)
}
