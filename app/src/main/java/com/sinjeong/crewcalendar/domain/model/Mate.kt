package com.sinjeong.crewcalendar.domain.model

import java.time.LocalDate

/** 즐겨찾기 그룹 (이름은 추후 설정에서 변경 가능하게) */
enum class FavGroup(val label: String) { CLUB("동호회"), TEAM("우리 조"), ETC("기타") }

/** 동료 — 소속 패턴 + 오프셋만 있으면 모든 날짜 근무를 계산할 수 있다 */
data class Mate(
    val name: String,
    val group: CrewGroup = CrewGroup.BRANCH,
    val patternOffset: Int = 0,
    val favGroup: FavGroup? = null,
) {
    fun dutyOn(date: LocalDate): DutyCode =
        Bundled.patternFor(group).dutyOn(date, patternOffset)
}
