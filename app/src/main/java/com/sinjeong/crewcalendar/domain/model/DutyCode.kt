package com.sinjeong.crewcalendar.domain.model

/**
 * 근무 코드 파서/모델.
 * 근무표(xlsx) 셀 값 그대로를 파싱한다:
 *  - "3", "14"      → 본선 교번 (주간 1~29 / 야간 33~51)
 *  - "44비"          → 야간 익일 비번
 *  - "휴5"           → 휴일
 *  - "대2"           → 대기조
 *  - "지13" "지대1" "지휴4" "지13비" → 지선 계열 (지10~지14 = 지선 야간)
 *  - "~"             → 비번(표기용)
 *  - "주"            → 주간 내근
 *  - 연차/교육/병가 등 근무변경 항목 → 대응 타입 (SPECIAL 등)
 */
enum class DutyType {
    MAIN_DAY, MAIN_NIGHT, POST_NIGHT, REST, STANDBY,
    BRANCH, BRANCH_NIGHT, BRANCH_STANDBY, BRANCH_REST,
    OFFICE, SPECIAL, ETC,
}

data class DutyCode(
    val raw: String,
    val type: DutyType,
    /** 교번/휴번/대기 번호. 없으면 null */
    val number: Int? = null,
    /** 지선 여부 */
    val isBranch: Boolean = false,
) {
    val isWorkDay: Boolean
        get() = type in setOf(
            DutyType.MAIN_DAY, DutyType.MAIN_NIGHT, DutyType.STANDBY,
            DutyType.BRANCH, DutyType.BRANCH_NIGHT, DutyType.BRANCH_STANDBY, DutyType.OFFICE,
        )

    val isRest: Boolean get() = type == DutyType.REST || type == DutyType.BRANCH_REST

    val isNight: Boolean get() = type == DutyType.MAIN_NIGHT || type == DutyType.BRANCH_NIGHT

    /** 달력 셀 표시 텍스트 — 지선은 "지" 접두사를 떼고 표시 (기존 앱 방식) */
    val display: String
        get() = when {
            type == DutyType.POST_NIGHT -> "~"
            isBranch && raw.startsWith("지") && type != DutyType.SPECIAL && type != DutyType.ETC ->
                raw.removePrefix("지")
            else -> raw
        }

    companion object {
        /** 본선 야간 다이아 번호 범위 (익일 비번 발생) */
        val NIGHT_RANGE = 33..51

        /** 지선 야간 시작 번호 (지10~지14) */
        const val BRANCH_NIGHT_FROM = 10

        /** 근무변경 선택 목록 (기존 앱과 동일 순서) */
        val CHANGE_OPTIONS = listOf(
            "충당", "대기충당", "교체", "운휴", "비번", "지근", "지휴",
            "연차", "보상", "촉연", "대휴", "장휴", "청휴", "학습", "만휴",
            "돌봄휴가", "동행휴가", "교육", "병가", "공가", "회행", "가연차", "작연차",
        )

        /** 근무변경 항목 → 타입 매핑 (목록에 없으면 일반 파싱) */
        private val OVERRIDE_TYPES = mapOf(
            "충당" to DutyType.STANDBY, "대기충당" to DutyType.STANDBY, "교체" to DutyType.STANDBY,
            "운휴" to DutyType.POST_NIGHT, "비번" to DutyType.POST_NIGHT,
            "지근" to DutyType.BRANCH, "지휴" to DutyType.BRANCH_REST,
        )

        fun parse(raw: String?): DutyCode {
            val s = raw?.trim()?.removeSuffix(".0") ?: return DutyCode("", DutyType.ETC)
            if (s.isEmpty()) return DutyCode("", DutyType.ETC)
            OVERRIDE_TYPES[s]?.let { return DutyCode(s, it, isBranch = s.startsWith("지")) }
            if (s in CHANGE_OPTIONS) return DutyCode(s, DutyType.SPECIAL)
            return when {
                s == "~" -> DutyCode(s, DutyType.POST_NIGHT)
                s == "주" -> DutyCode(s, DutyType.OFFICE)
                s.startsWith("지대") -> DutyCode(s, DutyType.BRANCH_STANDBY, s.removePrefix("지대").removeSuffix("비").toIntOrNull(), isBranch = true)
                s.startsWith("지휴") -> DutyCode(s, DutyType.BRANCH_REST, s.removePrefix("지휴").toIntOrNull(), isBranch = true)
                s.startsWith("지") -> {
                    val body = s.removePrefix("지")
                    val postNight = body.endsWith("비")
                    val n = body.removeSuffix("비").toIntOrNull()
                    when {
                        postNight -> DutyCode(s, DutyType.POST_NIGHT, n, isBranch = true)
                        n != null && n >= BRANCH_NIGHT_FROM -> DutyCode(s, DutyType.BRANCH_NIGHT, n, isBranch = true)
                        else -> DutyCode(s, DutyType.BRANCH, n, isBranch = true)
                    }
                }
                s.startsWith("휴") -> DutyCode(s, DutyType.REST, s.removePrefix("휴").toIntOrNull())
                s.startsWith("대") -> {
                    val postNight = s.endsWith("비")
                    val n = s.removePrefix("대").removeSuffix("비").toIntOrNull()
                    if (postNight) DutyCode(s, DutyType.POST_NIGHT, n) else DutyCode(s, DutyType.STANDBY, n)
                }
                s.endsWith("비") && s.dropLast(1).toIntOrNull() != null ->
                    DutyCode(s, DutyType.POST_NIGHT, s.dropLast(1).toInt())
                s.toIntOrNull() != null -> {
                    val n = s.toInt()
                    if (n in NIGHT_RANGE) DutyCode(s, DutyType.MAIN_NIGHT, n) else DutyCode(s, DutyType.MAIN_DAY, n)
                }
                else -> DutyCode(s, DutyType.ETC)
            }
        }
    }
}
