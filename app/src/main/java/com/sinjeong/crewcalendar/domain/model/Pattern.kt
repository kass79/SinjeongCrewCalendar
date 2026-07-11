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
    val revision: String = "",
    val createdBy: String = "",        // 관리자 uid
) {
    val length: Int get() = sequence.size

    /** anchorDate 기준 offset 라인의 특정 날짜 근무코드 */
    fun dutyOn(date: LocalDate, offset: Int): DutyCode {
        if (sequence.isEmpty()) return DutyCode.parse(null)
        val days = ChronoUnit.DAYS.between(anchorDate, date).toInt()
        val idx = Math.floorMod(days + offset, sequence.size)
        return DutyCode.parse(sequence[idx])
    }
}
