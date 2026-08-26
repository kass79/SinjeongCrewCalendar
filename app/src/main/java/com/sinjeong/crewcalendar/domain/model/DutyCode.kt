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
     * 다이아를 대신 뛰는 근무의 접두어(`충당`·`대기충당`·`교체`·`지근`). null이 아니면 raw는
     * `"충당 9"` 꼴이고 **타입·번호·지선여부는 뒤따르는 다이아 기준**이다
     * (출근시각·행로표·열번이 그대로 따라온다). 색 처리는 [colorType] 참고.
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
     * 색 기준 타입. **충당·대기충당·교체 3종만** 어떤 다이아를 대신 뛰든 **대기(노랑)** 로 보인다 —
     * 달력·동료근무·동료 탭·공유 이미지 색 매핑은 전부 이 값을 쓴다.
     *
     * ⚠ `지근`(지정근무)은 v1.6.47에서 다이아를 붙일 수 있게 됐지만 **노랑 강제 대상이 아니다.**
     * 조건이 `fill != null` 이었다면 다이아를 고른 순간 `지근`이 대기 노랑으로 돌변했다 —
     * 고른 다이아의 제 색(주간 초록·야간 보라 …)을 그대로 따르는 것이 맞다.
     */
    val colorType: DutyType get() = if (fill != null && fill in STANDBY_FILL) DutyType.STANDBY else type

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
            // 지선 대기만 "지"를 남긴다 — 떼면 본선 대기(`대1`·`대2`·`대11`)와 **글자가 똑같아진다**
            // (v1.6.36 사용자 요청: "신정지선 대1,대2,대11은 지대1,지대2,지대11 로 구분해주면 좋겠어!").
            // 주간·야간 다이아는 번호대가 본선과 갈려(지1~8·지10~14) 헷갈릴 일이 없어 그대로 뗀다.
            // ⚠ 시퀀스 문자열은 손대지 않는다 — 이미 `지대1`로 저장돼 있고 시각표 키도 그 값이다.
            type == DutyType.BRANCH_STANDBY -> raw
            isBranch && raw.startsWith("지") && type != DutyType.SPECIAL && type != DutyType.ETC ->
                raw.removePrefix("지")
            else -> raw
        }

    /** 공간이 넉넉한 곳(상단 요약·상세 시트)용 — 한 글자로 줄인 낱말 코드만 원래대로 되돌린다 */
    val displayLong: String get() = if (raw in SHORT_LABELS || fill != null) raw else display

    /**
     * **격자 칸 전용** 표기 (달력 칩·동료 탭·근무표 공유 이미지). 다이아를 붙인 항목만
     * `대기충당` ⏎ `지2` (`지근` ⏎ `34`) 두 줄로 접고, 나머지는 [display] 그대로 한 줄이다.
     *
     * 한 줄 `대기충당지2`(6글자)는 34dp 칩에서 자동축소 하한(7sp)까지 줄여도 넘쳐 흘러
     * **다이아가 안 읽혔다**(v1.6.46 사용자 지적: *"대기 충당이 지2면 캘린더에도 표시해줘야지"*).
     * 승무원에게 실질 정보는 "어느 다이아를 뛰는가"라 **아랫줄(다이아)이 크게** 온다 — 세 렌더러가
     * 전부 아랫줄에 큰 글자를 준다.
     *
     * ⚠ 저장값([raw])·[diaRaw]·[colorType]은 건드리지 않는다. 행로표·편승알람 조회 키는 [diaRaw]고
     * 색은 [colorType]이 늘 대기(노랑)로 되돌린다 — 표시만 바꾸는 값이다.
     * ⚠ 위젯·알림·상세시트는 폭이 넉넉해 [display]/[displayLong]을 그대로 쓴다(줄바꿈이 섞이면 안 된다).
     */
    val gridLabel: String get() = if (fill != null) "$fill\n$diaRaw" else display

    /**
     * **근무선택·다이아 격자 전용** 표기 — 익일 비번을 `44~`처럼 **어느 야간의 비번인지 보이게** 적는다
     * (v1.6.79. 사용자: *"근무선택에 야간의 비번날은 없네? 추가해줘~ / 평평 44 다이아라면 그 다음날은 44~"*).
     *
     * v1.6.36에서 비번을 목록에서 **뺐던 진짜 이유가 "필요 없다"가 아니라 "구분이 안 됐다"**이다
     * ([displayOrder] 주석 그대로: *"[display]가 전부 `~` 한 글자라 어느 야간의 비번인지
     * 보이지도 않으면서 본선 108칸 중 22칸을 먹고 있었다"*). 번호를 앞에 붙이면 그 문제가 사라지므로
     * 이번에 목록을 되살렸다 — **뺐던 판단을 뒤집은 게 아니라 그 전제를 없앤 것**이다.
     *
     * ⚠ **[display]는 절대 안 건드린다.** 달력 칸·동료근무·공유 이미지에서 비번은 `~` 한 글자여야
     * 한다(칸이 좁아 그렇게 정한 것 — [display] 주석). 그래서 격자 전용 값을 따로 둔다.
     * ⚠ 저장값([raw])도 그대로다 — 격자가 넘기는 건 글자가 아니라 **시퀀스 인덱스**다.
     *
     * 붙는 번호는 **그 야간 근무의 [display]** 라 시트 안에서 표기가 어긋나지 않는다:
     * `38비` → `38~` / `지11비` → `11~`(지선 격자는 야간도 `11`) / `대11비` → `대11~` / `지대11비` → `지대11~`.
     */
    val pickerLabel: String
        get() = if (type == DutyType.POST_NIGHT && raw.endsWith("비"))
            "${parse(raw.removeSuffix("비")).display}~" else display

    companion object {

        /** 낱말 근무코드 → 한 글자 표기. `parse` 결과 타입(색)은 그대로 두고 **표시만** 바꾼다 */
        private val SHORT_LABELS = mapOf(
            "주간" to "주", "야간" to "야", "비번" to "~", "휴무" to "휴",
        )

        /** 본선 야간 다이아 번호 범위 (익일 비번 발생) */
        val NIGHT_RANGE = 33..51

        /** 지선 야간 시작 번호 (지10~지14) */
        const val BRANCH_NIGHT_FROM = 10

        /**
         * 근무변경으로 **고를 수 있는 근무코드 전부**(묶음 이름은 여기 없다 — 저장값만 들어간다).
         * 순서가 곧 화면 순서다. 1단계 화면은 여기서 파생한 [CHANGE_TOP].
         *
         * ⚠ `비번`은 **v1.6.47에서 뺐다**(사용자: *"근무변경에 비번은 없어도 될꺼같은데?"*).
         * `작연차`(v1.6.41)와 같은 처리 — [OVERRIDE_TYPES]에는 그대로 남아 야간 다이아 다음날
         * 자동으로 붙는 비번이 계속 파싱·표시된다(보라 유지). 여기서 빠지면 **고를 수만** 없어진다.
         * `작연차`(v1.6.41, 사용자 확정 "작연차를 그냥 빼")도 같은 이유로 [REST_OPTIONS]에만 남아 있다.
         */
        val CHANGE_OPTIONS = listOf(
            "충당", "대기충당", "교체", "운휴", "지근", "지휴",
            "연차", "보상", "촉연", "대휴", "장휴", "청휴", "학습", "만휴",
            "돌봄휴가", "동행휴가", "교육", "병가", "공가", "회행", "가연차",
        )

        /** 묶음 칩 이름. 근무코드가 아니라 **저장될 수 없다** — 누르면 하위 9종 화면으로 갈린다. */
        const val ETC_GROUP = "기타휴가"

        /**
         * `기타휴가` 묶음 — 사용자가 **잘 안 쓴다고 지목한 9종**(v1.6.47).
         * 1단계에서 접어 두고 묶음 칩을 누르면 **화면을 통째로 갈아** 이 9종만 보여준다.
         *
         * ⚠ v1.6.40의 아코디언 부활이 아니다. 그건 펼치면 하위 칩이 **아래로 끼어들어**
         * 방금 누른 칩이 밀렸고 사용자가 명시적으로 거부해 v1.6.42에서 걷어냈다.
         * 여기서 쓰는 건 충당 계열이 v1.6.25부터 쓰던 **같은 자리 화면 교체**라 레이아웃이 안 밀린다.
         */
        val CHANGE_ETC = listOf(
            "장휴", "청휴", "학습", "만휴", "돌봄휴가", "동행휴가", "가연차", "병가", "공가",
        )

        /**
         * 근무변경 **1단계 화면 목록**(13칸). [CHANGE_OPTIONS]에서 [CHANGE_ETC] 9종을 빼고
         * 끝에 묶음 칩 하나를 붙인 것 — **파생이라 두 벌이 될 수 없다**
         * (목록을 따로 적으면 한쪽만 고치는 사고가 난다. v1.6.40 `CHANGE_TOP`이 그래서 파생이었다).
         */
        val CHANGE_TOP = CHANGE_OPTIONS.filterNot { it in CHANGE_ETC } + ETC_GROUP

        /**
         * 근무변경 항목 중 **실제로 쉬는 것** — 전부 `REST`(옅은 붉은색)로 묶는다.
         * 종전엔 `CHANGE_OPTIONS` 폴백에 걸려 `SPECIAL`(야간 보라)로 빠져서
         * 연차·병가·돌봄휴가가 달력·동료근무·공유 이미지에서 야간과 같은 보라로 보였다.
         *
         * 일부러 뺀 것:
         *  · `충당`·`대기충당`·`교체`(대기 근무)·`지근`(지정근무)·`교육`·`회행` — 출근하는 날이다
         *  · `비번` — 야간 다음날이라 야간과 한 덩어리로 읽혀야 해서 보라 유지 (v1.6.21 사용자 선택)
         *
         * ⚠ `작연차`는 v1.6.41에 [CHANGE_OPTIONS](=고를 수 있는 목록)에서 빠졌지만 **여기엔 남는다.**
         * 이미 저장된 기록을 계속 휴가색으로 그리기 위해서다 — 지우면 옛 `작연차`가 야간 보라로 돌변한다.
         */
        private val REST_OPTIONS = setOf(
            "운휴", "연차", "보상", "촉연", "대휴", "장휴", "청휴", "학습", "만휴",
            "돌봄휴가", "동행휴가", "병가", "공가", "가연차", "작연차",
        )

        /**
         * 다이아를 붙여 저장할 수 있는 근무변경 항목 — 실제로는 "그 다이아를 대신 뛰는" 근무다.
         * `"충당 9"`·`"대기충당 지3"`·`"교체 45"`·`"지근 34"` 꼴로 저장하고
         * [parse]가 다이아 부분을 그대로 해석한다(타입·번호·출근시각·행로표가 다이아를 따라온다).
         *
         * `지근`(지정근무)은 **v1.6.47에서 합류**했다 — 사용자: *"근무변경에서 지근은 다이아 선택이 없네?"*
         * 지정된 근무가 본선일지 지선일지 미리 알 수 없으므로 충당 계열과 **똑같이** 소속 → 다이아
         * 2단계를 거친다. 색만 다르다 → [STANDBY_FILL].
         */
        val FILL_OPTIONS = setOf("충당", "대기충당", "교체", "지근")

        /**
         * [FILL_OPTIONS] 중 **색을 대기(노랑)로 강제**하는 것들 — 대기 근무를 대신 뛰는 3종뿐이다.
         * `지근`은 여기 없다: 지정근무는 고른 다이아의 제 색(주간 초록·야간 보라 …)으로 보여야 한다.
         */
        private val STANDBY_FILL = setOf("충당", "대기충당", "교체")

        /**
         * 근무선택 그리드 **표시 순서** — 다이아 번호순으로 정렬한 인덱스 목록.
         * 주간 1~29 → 야간 33~51 → 대기 대1~13 → 운휴 휴1~29.
         * 지선도 같은 규칙: 지1~지8 → 지10~지14 → 지대 → 지휴.
         *
         * ⚠ **돌려주는 값은 반드시 원래 시퀀스 인덱스다.** 교번 offset을
         * `Pattern.offsetFor(date, index)`가 이 인덱스로 계산하므로, 정렬된 자리 번호를 넘기면
         * 고른 다이아와 다른 근무표가 저장된다(사용자 전원의 근무가 어긋남).
         *
         * **익일 비번(`38비`·`지11비`·`대11비`)도 목록에 있다** (v1.6.79 사용자 요청 —
         * *"근무선택에 야간의 비번날은 없네? 추가해줘~ 평평 44 다이아라면 그 다음날은 44~"*).
         *
         * v1.6.36에서 한 번 뺐었다(*"`~` 이게 비번인데 이건 굳이 없어도 될꺼같애"*). 그 근거는
         * *"[display]가 전부 `~` 한 글자라 어느 야간의 비번인지 보이지도 않으면서 본선 108칸 중
         * 22칸을 먹고 있었다"*였는데, **[DutyCode.pickerLabel]이 `44~`로 번호를 보여 주면서 그
         * 전제가 사라졌다.** 되살린 게 아니라 문제를 없애고 다시 넣은 것이다 —
         * ⚠ 다음 세션이 v1.6.36 주석만 보고 도로 빼지 말 것.
         *
         * 되살리면서 사라진 우회책: 종전엔 *"오늘이 비번인 사람은 전날 칸을 눌러 야간 다이아를
         * 고르라"*는 시트 안내문이 있었다. 이제 그 날짜에서 곧바로 고를 수 있어 안내문을 지웠다.
         *
         * 본선 86 → **108칸**(비번 22), 지선 23 → 29칸. 격자가 `LazyVerticalGrid`
         * (6열 · `heightIn(max = 340.dp)`)라 스크롤 길이만 늘고 배치는 안 깨진다.
         * [orderKey]가 이미 `비`를 **그 야간 다이아 바로 뒤**(`*2 + 1`)에 놓으므로 `44` 다음이 `44~`다.
         *
         * ⚠ **[postNight]가 기본 false인 이유 — 같은 격자를 `충당 계열 다이아 선택`도 쓴다.**
         * 거기까지 비번을 넣으면 `대기충당 지대11비` 같은 저장값이 생기는데,
         *  ① `비번을 대신 뛴다`는 말 자체가 성립하지 않고
         *  ② 동료 탭 격자 라벨이 `대기`⏎`지대11비`(4.24 units)가 돼 `DutyMatrix.UNIFORM_UNITS`
         *     (3.24)를 넘겨 **표 전체 글자가 작아지거나 그 칸이 잘린다**(v1.6.49·50이 그렇게 거부됐다).
         * 근무선택 2단계만 `postNight = true`로 부른다.
         */
        fun displayOrder(sequence: List<String>, postNight: Boolean = false): List<Int> =
            sequence.indices
                .filter { postNight || parse(sequence[it]).type != DutyType.POST_NIGHT }
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

        /** 근무변경 항목 → 타입 매핑 (목록에 없으면 일반 파싱) */
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
                    // 다이아가 깨진 데이터면 접두어 자체의 타입으로 떨어진다(`지근 xxx` → 지근과 같은 색)
                    return if (d.type == DutyType.ETC)
                        DutyCode(s, OVERRIDE_TYPES.getValue(head), isBranch = head.startsWith("지"), fill = head)
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
