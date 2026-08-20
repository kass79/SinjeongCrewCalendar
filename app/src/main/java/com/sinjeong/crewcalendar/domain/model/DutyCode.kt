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
    /**
     * 충당 계열 접두어(`충당`·`대기충당`·`교체`). null이 아니면 raw는 `"충당 9"` 꼴이고
     * **타입·번호·지선여부는 뒤따르는 다이아 기준**이다(출근시각·행로표·열번이 그대로 따라온다).
     * 색만 대기(노랑)로 되돌린다 → [colorType].
     */
    val fill: String? = null,
) {
    val isWorkDay: Boolean
        get() = type in setOf(
            DutyType.MAIN_DAY, DutyType.MAIN_NIGHT, DutyType.STANDBY,
            DutyType.BRANCH, DutyType.BRANCH_NIGHT, DutyType.BRANCH_STANDBY, DutyType.OFFICE,
        )

    val isRest: Boolean get() = type == DutyType.REST || type == DutyType.BRANCH_REST

    val isNight: Boolean get() = type == DutyType.MAIN_NIGHT || type == DutyType.BRANCH_NIGHT

    /** 밤샘 근무 → 익일 비번 발생 (야간 다이아 + 야간 대기조 대11~13·지대11) */
    val isOvernight: Boolean
        get() = isNight ||
            ((type == DutyType.STANDBY || type == DutyType.BRANCH_STANDBY) && (number ?: 0) >= 11)

    /**
     * 색 기준 타입. 충당 계열은 어떤 다이아를 대신 뛰든 **대기(노랑)** 로 보여야 한다 —
     * 달력·동료근무·동료 탭·공유 이미지 색 매핑은 전부 이 값을 쓴다.
     */
    val colorType: DutyType get() = if (fill != null) DutyType.STANDBY else type

    /** 시각표·행로표 조회용 원본. 충당 계열이면 대신 뛰는 다이아 쪽("지3")만 남긴다 */
    val diaRaw: String get() = if (fill != null) raw.substringAfter(' ').trim() else raw

    /** 달력 셀 표시 텍스트 — 지선은 "지" 접두사를 떼고 표시 (기존 앱 방식) */
    val display: String
        get() = when {
            // "충당 9" → "충당9". 달력 칸·동료근무 칸이 좁아 공백 한 칸도 아깝다
            fill != null -> raw.replace(" ", "")
            // 4조2교대·통상근무 낱말 코드는 한 글자로 (v1.6.24 사용자 요청).
            // 달력·동료근무 칸이 좁아 "주간/야간/비번/휴무"는 답답했다. 비번 `~`는 승무 3종 표기와 같다.
            raw in SHORT_LABELS -> SHORT_LABELS.getValue(raw)
            type == DutyType.POST_NIGHT -> "~"
            isBranch && raw.startsWith("지") && type != DutyType.SPECIAL && type != DutyType.ETC ->
                raw.removePrefix("지")
            else -> raw
        }

    /** 공간이 넉넉한 곳(상단 요약·상세 시트)용 — 한 글자로 줄인 낱말 코드만 원래대로 되돌린다 */
    val displayLong: String get() = if (raw in SHORT_LABELS || fill != null) raw else display

    companion object {

        /** 낱말 근무코드 → 한 글자 표기. `parse` 결과 타입(색)은 그대로 두고 **표시만** 바꾼다 */
        private val SHORT_LABELS = mapOf(
            "주간" to "주", "야간" to "야", "비번" to "~", "휴무" to "휴",
        )

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

        /**
         * 근무변경 항목 중 **실제로 쉬는 것** — 전부 `REST`(옅은 붉은색)로 묶는다.
         * 종전엔 `CHANGE_OPTIONS` 폴백에 걸려 `SPECIAL`(야간 보라)로 빠져서
         * 연차·병가·돌봄휴가가 달력·동료근무·공유 이미지에서 야간과 같은 보라로 보였다.
         *
         * 일부러 뺀 것:
         *  · `충당`·`대기충당`·`교체`(대기 근무)·`지근`(지선 근무)·`교육`·`회행` — 출근하는 날이다
         *  · `비번` — 야간 다음날이라 야간과 한 덩어리로 읽혀야 해서 보라 유지 (v1.6.21 사용자 선택)
         */
        private val REST_OPTIONS = setOf(
            "운휴", "연차", "보상", "촉연", "대휴", "장휴", "청휴", "학습", "만휴",
            "돌봄휴가", "동행휴가", "병가", "공가", "가연차", "작연차",
        )

        /** 근무변경 항목 → 타입 매핑 (목록에 없으면 일반 파싱) */
        /**
         * 다이아를 붙여 저장할 수 있는 근무변경 항목 — 실제로는 "그 다이아를 대신 뛰는" 근무다.
         * `"충당 9"`·`"대기충당 지3"`·`"교체 45"` 꼴로 저장하고 [parse]가 다이아 부분을 그대로 해석한다.
         */
        val FILL_OPTIONS = setOf("충당", "대기충당", "교체")

        /**
         * 근무선택 그리드 **표시 순서** — 다이아 번호순으로 정렬한 인덱스 목록.
         * 주간 1~29 → 야간 33~51 → 대기 대1~13 → 운휴 휴1~29.
         * 지선도 같은 규칙: 지1~지8 → 지10~지14 → 지대 → 지휴.
         *
         * ⚠ **돌려주는 값은 반드시 원래 시퀀스 인덱스다.** 교번 offset을
         * `Pattern.offsetFor(date, index)`가 이 인덱스로 계산하므로, 정렬된 자리 번호를 넘기면
         * 고른 다이아와 다른 근무표가 저장된다(사용자 전원의 근무가 어긋남).
         *
         * **익일 비번(`38비`·`지11비`·`대11비`)은 목록에서 뺀다** (v1.6.36 사용자 요청 —
         * *"`~` 이게 비번인데 이건 굳이 없어도 될꺼같애"*). [display]가 전부 `~` 한 글자라
         * 어느 야간의 비번인지 보이지도 않으면서 본선 108칸 중 22칸을 먹고 있었다.
         * 야간 다이아를 고르면 다음날 비번은 교번 순환이 알아서 붙이므로 직접 고를 이유가 없다.
         *
         * ⚠ 뺀 만큼 **그 날짜에서 그 offset을 곧바로 고를 수는 없다.** 오늘이 비번인 사람은
         * 전날 칸을 눌러 야간 다이아를 고르면 완전히 같은 결과가 된다(시트 안내문 그대로).
         */
        fun displayOrder(sequence: List<String>): List<Int> =
            sequence.indices
                .filter { parse(sequence[it]).type != DutyType.POST_NIGHT }
                .sortedBy { orderKey(sequence[it]) }

        private fun orderKey(raw: String): Int {
            val s = raw.removePrefix("지")
            val post = s.endsWith("비")
            val body = s.removeSuffix("비")
            val rank = when {
                body.startsWith("휴") -> 2                // 운휴
                body.startsWith("대") -> 1                // 대기
                else -> 0                                 // 주간·야간 다이아 (번호가 이어져 1~29 → 33~51)
            }
            val n = body.filter(Char::isDigit).toIntOrNull() ?: 999
            return (rank * 1000 + n) * 2 + if (post) 1 else 0
        }

        private val OVERRIDE_TYPES = mapOf(
            "충당" to DutyType.STANDBY, "대기충당" to DutyType.STANDBY, "교체" to DutyType.STANDBY,
            "비번" to DutyType.POST_NIGHT,
            "지근" to DutyType.BRANCH, "지휴" to DutyType.BRANCH_REST,
            // 4조2교대·통상근무 (교번 번호가 없는 낱말 코드). "비번"·"휴무"는 위/아래에서 이미 처리됨
            "주간" to DutyType.MAIN_DAY, "야간" to DutyType.MAIN_NIGHT,
        )

        fun parse(raw: String?): DutyCode {
            val s = raw?.trim()?.removeSuffix(".0") ?: return DutyCode("", DutyType.ETC)
            if (s.isEmpty()) return DutyCode("", DutyType.ETC)
            // "충당 9" = 9번 다이아 대행 → 다이아를 그대로 파싱해 타입·번호·지선여부를 물려받는다.
            // 그래야 출근시각·행로표·열번·야간 익일 비번이 자동으로 따라온다. 색만 colorType이 대기로 돌린다.
            // 첫 토큰이 FILL_OPTIONS 일 때만 걸리므로 옛 데이터("충당" 단독, "대3 4")는 아래 경로 그대로.
            if (' ' in s) {
                val head = s.substringBefore(' ')
                val dia = s.substringAfter(' ').trim()
                if (head in FILL_OPTIONS && dia.isNotEmpty()) {
                    val d = parse(dia)
                    return if (d.type == DutyType.ETC) DutyCode(s, DutyType.STANDBY, fill = head)
                    else d.copy(raw = s, fill = head)
                }
            }
            OVERRIDE_TYPES[s]?.let { return DutyCode(s, it, isBranch = s.startsWith("지")) }
            if (s in REST_OPTIONS) return DutyCode(s, DutyType.REST)
            if (s in CHANGE_OPTIONS) return DutyCode(s, DutyType.SPECIAL)
            return when {
                s == "~" -> DutyCode(s, DutyType.POST_NIGHT)
                s == "주" -> DutyCode(s, DutyType.OFFICE)
                s.startsWith("지대") -> {
                    val postNight = s.endsWith("비") // 지대11비 = 야간대기 익일 비번
                    val n = s.removePrefix("지대").removeSuffix("비").toIntOrNull()
                    if (postNight) DutyCode(s, DutyType.POST_NIGHT, n, isBranch = true)
                    else DutyCode(s, DutyType.BRANCH_STANDBY, n, isBranch = true)
                }
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
