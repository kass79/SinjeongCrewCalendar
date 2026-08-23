package com.sinjeong.crewcalendar.domain.model

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 근무선택 1단계 소속 (본선 기관사·차장은 같은 108칸 순환, 시작점만 다름).
 *
 * ## 부서(운용/관제)는 소속이 아니라 **사람의 표시 속성**이다 (v1.6.60)
 *
 * v1.6.54는 *"운용이랑 관제랑 완전 다른근무야"*를 받아 부서를 [CrewGroup] 두 값으로 갈랐다.
 * 그런데 소속을 가르면 **동료 탭 필터 칩까지 갈린다**(7 → 8개). 사용자가 실제로 써 보고
 * 되돌리기로 확정했다: *"운용/관제 탭을 그냥 4조2교대 탭으로 하나로 만들어 주고"*.
 *
 * | 개념 | 어디에 사는가 | 하는 일 |
 * |---|---|---|
 * | **소속** | [SHIFT_4_2] 하나 | 근무 계산·저장(`patternId`)·동료 탭 필터. 운용 13 + 관제 16 = **29명이 한 칩** |
 * | **부서** | 이름 ∈ [BundledRoster.SHIFT_CONTROL] | **표시 전용.** 배지 글자(`운`/`관`)를 고를 때만 본다 |
 * | **조** (A~D) | `patternOffset` (= [ShiftTeam.offset]) | **근무를 정하는 유일한 값** |
 *
 * 부서를 저장하지 않으므로 `patternId`가 하나로 돌아왔고([Bundled.groupFor]), 부서를 바꿔도
 * 근무는 한 칸도 안 움직인다 — 애초에 근무 계산이 부서를 안 본다.
 * 합치는 자리는 [teamBadge] 한 곳뿐이다(v1.6.54 표기 `운A`·`관D` 그대로 유지 — 사용자 확정).
 *
 * ⚠ **[SHIFT_4_2]는 enum 이름을 한 번도 안 바꿨다.** 동료 저장 키가 `"이름|${'$'}{group.name}"`
 * (`mateKey`)라 이름을 갈면 저장된 동료가 통째로 유령이 된다.
 */
enum class CrewGroup(val label: String, val role: CrewRole) {
    BRANCH("신정지선", CrewRole.DRIVER_BRANCH),
    MAIN_DRIVER("본선 기관사", CrewRole.DRIVER_MAIN),
    MAIN_CONDUCTOR("본선 차장", CrewRole.CONDUCTOR),
    /** 운용 + 기지관제 29명. 부서는 [teamBadge]가 이름으로 갈라 배지에만 붙인다(v1.6.60). */
    SHIFT_4_2("4조2교대", CrewRole.OPERATION),
    OFFICE_DAY("통상근무", CrewRole.OFFICE_STAFF),
}

/**
 * 화면 표기 `운A`·`관D` — **부서와 조를 합치는 유일한 자리**(v1.6.54 사용자 확정 표기, 괄호 없이 두 글자).
 *
 * 부서는 [BundledRoster.isControl] 한 곳에서만 판별한다: 기지관제 명단에 이름이 있으면 `관`,
 * 아니면 `운`. 한 사람이 두 부서에 동시에 속할 수 없으므로 **이름이 곧 부서의 근거**다
 * (v1.6.54는 `CrewGroup`으로 갈라 두었는데, 그러면 필터 칩까지 갈라진다 — [CrewGroup] 주석).
 *
 * 4조2교대가 아니면 null. 정렬 키로도 쓴다 — `관A`…`관D` < `운A`…`운D`라 **부서 → 조** 순으로
 * 묶인다(29명이 이름순으로만 섞이면 같은 조를 눈으로 찾아야 한다).
 */
fun teamBadge(group: CrewGroup, offset: Int, name: String): String? =
    if (group != CrewGroup.SHIFT_4_2) null
    else ShiftTeam.ofOffset(offset)
        ?.let { "${if (BundledRoster.isControl(name)) "관" else "운"}${it.name}" }

/**
 * 4조2교대 근무조.
 *
 * offset은 **ordinal이 아니다** — A0 · B3 · C2 · D1 이다(v1.6.24).
 * 근거: 사용자 제공 2026-08-16 실근무표 (A=주간 · B=휴무 · C=비번 · D=야간).
 * anchor(2026-08-10)에서 6일 뒤, sequence=[비번,휴무,주간,야간] 이므로
 * floorMod(6 + off, 4) 가 각 조의 근무를 가리켜야 한다 →
 *   A 주간=idx2 → off 0 / B 휴무=idx1 → off 3 / C 비번=idx0 → off 2 / D 야간=idx3 → off 1.
 * 종전 0/1/2/3 배치는 A·C만 맞고 B·D가 서로 뒤바뀌어 있었다.
 */
enum class ShiftTeam(val offset: Int) { A(0), B(3), C(2), D(1);
    /** 공식 문서(근무계획표) 표기는 `반`이 아니라 **`조`** 다 — `A조`. */
    val label: String get() = "${name}조"

    companion object {
        /** `patternOffset` → 조. 4일 순환이라 0~3이 A~D에 일대일로 대응한다. */
        fun ofOffset(offset: Int): ShiftTeam? = entries.firstOrNull { it.offset == offset }
    }
}

/** 야간 다이아 당일→익일 조합 (토요일 = 휴일 시각 확정) */
enum class NightCombo(val label: String) { PP("평평"), PH("평휴"), HH("휴휴"), HP("휴평") }

/**
 * 25.03.04 개정 시각표 + 교번 순서 + 2026 공휴일 번들 데이터.
 * 앱 최초 실행부터 오프라인으로 동작하는 기본값이며,
 * 개정(연 1회)은 관리자가 Firestore에 올린 데이터가 이 값을 덮어쓴다.
 */
object Bundled {

    const val REVISION = "25.03.04"

    /* ── 패턴 (교번 순서) ─────────────────────────────── */

    /** 지선 29칸 (PDF 순서표 + 26년 7월 근무표 검증) — anchor 2026-07-01 = 지3 */
    val BRANCH_PATTERN = Pattern(
        id = "bundled-branch",
        name = "신정지선",
        role = CrewRole.DRIVER_BRANCH,
        sequence = listOf(
            "지3", "지대2", "지12", "지12비", "지휴5", "지2", "지8", "지휴7", "지5", "지14",
            "지14비", "지휴2", "지7", "지11", "지11비", "지휴3", "지6", "지대11", "지대11비", "지휴6",
            "지1", "지10", "지10비", "지휴1", "지대1", "지4", "지13", "지13비", "지휴4",
        ),
        anchorDate = LocalDate.of(2026, 7, 1),
        revision = REVISION,
    )

    /** 본선 108칸 (26년 7월 근무표에서 재구성) — anchor 2026-07-01 = "9" */
    val MAIN_PATTERN = Pattern(
        id = "bundled-main",
        name = "본선",
        role = CrewRole.DRIVER_MAIN,
        sequence = listOf(
            "9", "38", "38비", "휴21", "12", "28", "휴19", "대3", "35", "35비",
            "휴4", "17", "49", "49비", "휴11", "7", "40", "40비", "휴13", "2",
            "대11", "대11비", "휴12", "13", "27", "휴20", "대5", "34", "34비", "휴1",
            "3", "42", "42비", "휴9", "5", "50", "50비", "휴18", "15", "25",
            "휴28", "22", "44", "44비", "휴3", "16", "43", "43비", "휴17", "대1",
            "33", "33비", "휴10", "10", "39", "39비", "휴22", "6", "26", "휴25",
            "대13", "대13비", "휴26", "1", "20", "51", "51비", "휴5", "대6", "47",
            "47비", "휴16", "18", "23", "휴29", "4", "36", "36비", "휴15", "11",
            "46", "46비", "휴2", "14", "37", "37비", "휴27", "대2", "29", "휴24",
            "8", "휴14", "21", "대12", "대12비", "휴6", "19", "41", "41비", "휴8",
            "24", "48", "48비", "휴7", "대4", "45", "45비", "휴23",
        ),
        anchorDate = LocalDate.of(2026, 7, 1),
        revision = REVISION,
    )

    /**
     * 4조2교대 (운용조·기지관제) — 주간 → 야간 → 비번 → 휴무 4일 순환.
     * 조별 offset은 `ShiftTeam.offset` (A0·B3·C2·D1) — ordinal이 아니다.
     *
     * anchor 계산 근거: 사용자 확정 기준점이 "2026-08-10(월) A조 = 비번".
     * anchorDate 를 2026-08-10 으로 두면 그 날 days=0, A조는 offset 0 이므로
     * idx = floorMod(0 + 0, 4) = 0 → sequence[0] 이 곧 "비번"이어야 한다.
     * 여기서 주간→야간→비번→휴무 순환을 "비번"부터 이어 적으면 [비번, 휴무, 주간, 야간].
     * 검산: A조 8/10=비번(0) 8/11=휴무(1) 8/12=주간(2) 8/13=야간(3) 8/14=비번 → 주기 유지.
     * 8/16 검산(사용자 실데이터): A=주간 · B=휴무 · C=비번 · D=야간 — `ShiftTeam` 주석 참조.
     */
    val SHIFT_PATTERN = Pattern(
        id = "bundled-shift42",
        name = "운용",
        role = CrewRole.OPERATION,
        sequence = listOf("비번", "휴무", "주간", "야간"),
        anchorDate = LocalDate.of(2026, 8, 10),
        revision = REVISION,
    )

    /**
     * **퇴역 id — 지우면 안 된다**(v1.6.60). v1.6.54~59가 관제를 별도 소속으로 두던 동안
     * 근무선택을 한 관제 직원의 Firestore `users.patternId`에 `bundled-control42`가 남아 있다.
     * `GetMonthScheduleUseCase`가 **저장된 id로 패턴을 되찾으므로**([ALL_PATTERNS] 조회),
     * 여기서 빼면 그 사람 달력이 통째로 빈다. 본인이 근무선택을 다시 하면 `bundled-shift42`가 덮인다.
     *
     * 내용은 [SHIFT_PATTERN]의 `copy()`라 순환값이 두 벌로 갈릴 일이 구조적으로 없다.
     * 부서 판별에는 더 이상 안 쓴다 — 이름으로 안다([teamBadge]).
     */
    val CONTROL_PATTERN = SHIFT_PATTERN.copy(id = "bundled-control42", name = "관제")

    /**
     * 통상근무 (사무실·소장/부사업소장·지도과·관리과) — 월~금 주간, 토·일·공휴일 휴무.
     * 순환은 "주간" 1칸뿐이고 쉬는 날은 `restOnHolidays`가 덮는다.
     * 토·일 2칸을 시퀀스에 넣지 않은 이유: `isHolidayTimetable`이 이미 토·일+공휴일이라
     * 7칸 시퀀스를 두면 주말 규칙이 두 군데로 갈라진다. 대체공휴일도 PUBLIC_HOLIDAYS만 갱신하면 따라온다.
     * 조 구분이 없으므로 offset은 항상 0 (근무선택 2단계 없음).
     */
    val OFFICE_PATTERN = Pattern(
        id = "bundled-office",
        name = "통상근무",
        role = CrewRole.OFFICE_STAFF,
        sequence = listOf("주간"),
        anchorDate = LocalDate.of(2026, 8, 10),
        restOnHolidays = true,
        revision = REVISION,
    )

    /** 내장 패턴 전체 — 패턴을 추가하면 여기만 늘리면 저장소 조회가 따라온다 */
    val ALL_PATTERNS = listOf(BRANCH_PATTERN, MAIN_PATTERN, SHIFT_PATTERN, CONTROL_PATTERN, OFFICE_PATTERN)

    fun patternFor(group: CrewGroup): Pattern = when (group) {
        CrewGroup.BRANCH -> BRANCH_PATTERN
        CrewGroup.SHIFT_4_2 -> SHIFT_PATTERN
        CrewGroup.OFFICE_DAY -> OFFICE_PATTERN
        else -> MAIN_PATTERN
    }

    /**
     * `patternId` → 소속. **하위호환 지점**.
     *
     * v1.6.54~59가 잠깐 쓰던 관제 전용 id([CONTROL_PATTERN])도 **4조2교대로 되읽는다** —
     * 부서는 이제 소속이 아니라 이름으로 판별하는 표시 속성이라([teamBadge]) id로 가를 것이 없다.
     * 그 사이에 근무선택을 한 관제 직원의 저장값이 그대로 살아 있으므로 이 한 줄이 곧 마이그레이션이고,
     * 두 id가 같은 소속·같은 순환을 가리키므로 근무는 한 칸도 안 바뀐다.
     * 되읽지 못하는 id는 종전대로 null이다.
     */
    fun groupFor(patternId: String?): CrewGroup? = when (patternId) {
        BRANCH_PATTERN.id -> CrewGroup.BRANCH
        MAIN_PATTERN.id -> CrewGroup.MAIN_DRIVER
        SHIFT_PATTERN.id, CONTROL_PATTERN.id -> CrewGroup.SHIFT_4_2
        OFFICE_PATTERN.id -> CrewGroup.OFFICE_DAY
        else -> null
    }

    /* ── 2026 공휴일 / 기념일 / 절기 ─────────────────────── */

    private fun d(m: Int, day: Int) = LocalDate.of(2026, m, day)

    /** 법정공휴일: 날짜 빨강 + 휴일 다이아 시각 적용 */
    val PUBLIC_HOLIDAYS: Map<LocalDate, String> = mapOf(
        d(1, 1) to "신정", d(2, 16) to "설연휴", d(2, 17) to "설날", d(2, 18) to "설연휴",
        d(3, 1) to "삼일절", d(3, 2) to "대체휴일", d(5, 5) to "어린이날",
        d(5, 24) to "부처님오신날", d(5, 25) to "대체휴일",
        d(6, 3) to "지방선거", d(6, 6) to "현충일", d(7, 17) to "제헌절",
        d(8, 15) to "광복절", d(8, 17) to "대체휴일",
        d(9, 24) to "추석연휴", d(9, 25) to "추석", d(9, 26) to "추석연휴",
        d(10, 3) to "개천절", d(10, 5) to "대체휴일", d(10, 9) to "한글날",
        d(12, 25) to "성탄절",
    )

    /**
     * 기념일: 이름만 빨강, 근무는 평일.
     * 2026년은 해당 없음 — 제헌절(7/17)·지방선거일(6/3)은 현업 확인 결과 실제로 휴일 다이아로
     * 운행해 v1.6.16에서 PUBLIC_HOLIDAYS로 옮겼다. 표시 경로(memorialName)는 그대로 남겨 둔다.
     */
    val MEMORIAL_DAYS: Map<LocalDate, String> = emptyMap()

    /**
     * 절기/복날 표시 — 2026년 24절기 전체 + 삼복.
     *
     * 출처: https://uncle.tools/manse/solar-terms/2026 (절입시각 분 단위) 를 기준으로,
     * https://naragara.com/4489 과 대조해 24건 중 23건 일치를 확인했다.
     * 유일한 불일치인 백로는 절입이 **9/7 23:41**(자정 19분 전)이라 반올림한 자료들이
     * 9/8로 적는다 — 절입시각을 분 단위로 싣는 https://www.sazasaju.com/blog/2026-jeolip-time-table
     * 도 9/7 23:41 이므로 9/7 로 넣는다.
     *
     * 이름은 모두 2글자 — 기존 6건과 같고, 달력 칸이 좁아 HolidayTag가 축소되는 걸 피한다.
     * 공휴일과 겹치는 5/5(어린이날·입하) · 6/6(현충일·망종)은 달력이
     * `holidayName ?: memorialName ?: seasonalTerm` 순으로 고르므로 공휴일 이름만 나온다.
     */
    val SEASONAL_TERMS: Map<LocalDate, String> = mapOf(
        d(1, 5) to "소한", d(1, 20) to "대한",
        d(2, 4) to "입춘", d(2, 19) to "우수",
        d(3, 5) to "경칩", d(3, 20) to "춘분",
        d(4, 5) to "청명", d(4, 20) to "곡우",
        d(5, 5) to "입하", d(5, 21) to "소만",
        d(6, 6) to "망종", d(6, 21) to "하지",
        d(7, 7) to "소서", d(7, 15) to "초복", d(7, 23) to "대서", d(7, 25) to "중복",
        d(8, 7) to "입추", d(8, 14) to "말복", d(8, 23) to "처서",
        d(9, 7) to "백로", d(9, 23) to "추분",
        d(10, 8) to "한로", d(10, 23) to "상강",
        d(11, 7) to "입동", d(11, 22) to "소설",
        d(12, 7) to "대설", d(12, 22) to "동지",
    )

    /** 휴일 다이아 적용 여부: 토·일·법정공휴일 */
    fun isHolidayTimetable(date: LocalDate): Boolean =
        date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY ||
            PUBLIC_HOLIDAYS.containsKey(date)

    /** 야간 다이아 당일→익일 조합 자동 판별 */
    fun comboOf(date: LocalDate): NightCombo {
        val today = isHolidayTimetable(date)
        val next = isHolidayTimetable(date.plusDays(1))
        return when {
            !today && !next -> NightCombo.PP
            !today -> NightCombo.PH
            next -> NightCombo.HH
            else -> NightCombo.HP
        }
    }

    /* ── 시각표 ──────────────────────────────────────── */

    /**
     * 다이아 시각 1행. 시각은 표기 그대로 문자열 (야간 "25:20" 등 24시+ 표기 유지)
     * legs = "전반 시작#종료" / "후반 시작-종료" (▼ = 주박)
     */
    data class TimeRow(
        val signOn: String,
        val signOff: String,
        val overnight: Boolean = false,
        val firstLeg: String? = null,
        val secondLeg: String? = null,
    )

    /** 지선 평일 (출근+30분 = 승무시작 규칙 검증됨) */
    val BRANCH_WEEKDAY: Map<String, TimeRow> = mapOf(
        "지1" to TimeRow("7:43", "14:51", false, "8:13#10:41", "12:51-14:51"),
        "지2" to TimeRow("7:47", "16:11", false, "8:17#10:44", "14:41-16:11"),
        "지3" to TimeRow("7:52", "16:50", false, "8:22#10:51", "14:51-16:50"),
        "지4" to TimeRow("10:11", "17:44", false, "10:41#12:41", "15:15-17:44"),
        "지5" to TimeRow("10:14", "18:10", false, "10:44#12:45", "16:11-18:10"),
        "지6" to TimeRow("10:21", "19:20", false, "10:51#12:51", "16:50-19:20"),
        "지7" to TimeRow("12:11", "19:44", false, "12:41#14:41", "17:44-19:44"),
        "지8" to TimeRow("12:15", "19:41", false, "12:45#15:15", "18:10-19:41"),
        "지10" to TimeRow("18:50", "6:50", true, "19:20#22:10", "05:10-06:50"),
        "지11" to TimeRow("19:11", "6:54", true, "19:41#22:31", "05:21-06:54"),
        "지12" to TimeRow("19:14", "8:13", true, "19:44#22:52▼", "06:45-08:13"),
        "지13" to TimeRow("21:40", "8:17", true, "22:10#25:13▼", "06:50-08:17"),
        "지14" to TimeRow("22:01", "8:22", true, "22:31#25:20▼", "06:54-08:22"),
        "지대1" to TimeRow("8:00", "17:00"),
        "지대2" to TimeRow("9:00", "18:00"),
        "지대11" to TimeRow("18:00", "8:30", true),
    )

    /** 지선 휴일·토 */
    val BRANCH_HOLIDAY: Map<String, TimeRow> = mapOf(
        "지1" to TimeRow("7:43", "14:55", false, "8:13#10:25", "12:55-14:55"),
        "지2" to TimeRow("7:47", "16:21", false, "8:17#10:15", "14:21-16:21"),
        "지3" to TimeRow("7:52", "16:54", false, "8:22#10:20", "14:55-16:54"),
        "지4" to TimeRow("9:45", "17:14", false, "10:15#12:45", "15:15-17:14"),
        "지5" to TimeRow("9:50", "18:20", false, "10:20#12:21", "16:21-18:20"),
        "지6" to TimeRow("9:55", "18:54", false, "10:25#12:55", "16:54-18:54"),
        "지7" to TimeRow("11:51", "19:14", false, "12:21#14:21", "17:14-19:14"),
        "지8" to TimeRow("12:15", "20:05", false, "12:45#15:15", "18:20-20:05"),
        "지10" to TimeRow("18:24", "6:50", true, "18:54#21:25", "05:10-06:50"),
        "지11" to TimeRow("18:44", "6:54", true, "19:14#21:49", "05:21-06:54"),
        "지12" to TimeRow("19:35", "8:13", true, "20:05#22:41▼", "06:45-08:13"),
        "지13" to TimeRow("20:55", "8:17", true, "21:25#24:05▼", "06:50-08:17"),
        "지14" to TimeRow("21:19", "8:22", true, "21:49#24:16▼", "06:54-08:22"),
        "지대1" to TimeRow("8:00", "17:00"),
        "지대2" to TimeRow("9:00", "18:00"),
        "지대11" to TimeRow("18:00", "8:30", true),
    )

    /** 본선 주간 평일 (1~29) — 전반/후반 상세는 3·14만 확보, 나머지는 시각표 추가 입력 시 채움 */
    val MAIN_DAY_WEEKDAY: Map<Int, TimeRow> = mapOf(
        1 to TimeRow("6:27", "15:56"), 2 to TimeRow("6:23", "15:23"),
        3 to TimeRow("6:33", "15:51", false, "7:18#10:33", "14:06-15:51"),
        // 4·7 퇴근시각은 v1.6.54에서 다시 맞바꿨다 — "signOff = [MainLegs] 후반종료" 규칙을 지키려면
        // 후반이 맞바뀔 때 여기도 같이 따라와야 한다(`signOff_equals_mainLegs_second_half_end`가 잠금).
        // 근거: 2026-08-23 배포 `4 7개정 행로표` PDF 실판독 — 4 = 17:21 · 7 = 17:16.
        // 출근시각(6:45·6:56)은 전반이 안 바뀌어 그대로다(전반시작 −45분 = 신도림 교대).
        4 to TimeRow("6:45", "17:21"), 5 to TimeRow("6:47", "17:05"),
        6 to TimeRow("6:52", "17:26"), 7 to TimeRow("6:56", "17:16"),
        8 to TimeRow("6:57", "17:25"), 9 to TimeRow("7:02", "17:36"),
        10 to TimeRow("7:10", "18:55"), 11 to TimeRow("7:20", "18:42"),
        12 to TimeRow("7:22", "18:35"), 13 to TimeRow("7:36", "17:55"),
        14 to TimeRow("7:47", "19:03", false, "8:32#10:58", "17:54-19:03"),
        15 to TimeRow("7:49", "17:59"), 16 to TimeRow("7:50", "19:14"),
        17 to TimeRow("7:57", "19:14"), 18 to TimeRow("8:15", "18:24"),
        19 to TimeRow("8:36", "18:46"), 20 to TimeRow("9:04", "18:51"),
        21 to TimeRow("9:55", "20:08"), 22 to TimeRow("10:10", "20:56"),
        23 to TimeRow("10:15", "20:38"), 24 to TimeRow("10:20", "21:10"),
        25 to TimeRow("10:25", "20:16"), 26 to TimeRow("10:58", "20:33"),
        27 to TimeRow("11:36", "20:45"), 28 to TimeRow("12:06", "20:44"),
        29 to TimeRow("13:11", "20:55"),
    )

    /** 본선 주간 휴일·토 (26~29 운휴) */
    val MAIN_DAY_HOLIDAY: Map<Int, TimeRow> = mapOf(
        1 to TimeRow("6:25", "14:59"), 2 to TimeRow("7:07", "15:33"),
        3 to TimeRow("7:29", "18:08"), 4 to TimeRow("7:30", "16:08"),
        5 to TimeRow("7:40", "16:53"), 6 to TimeRow("7:43", "16:24"),
        7 to TimeRow("7:57", "17:23"), 8 to TimeRow("7:58", "16:28"),
        9 to TimeRow("8:01", "16:29"), 10 to TimeRow("8:19", "16:48"),
        11 to TimeRow("8:29", "16:58"), 12 to TimeRow("8:28", "17:03"),
        13 to TimeRow("8:52", "17:29"), 14 to TimeRow("9:17", "18:30"),
        15 to TimeRow("9:39", "17:39"), 16 to TimeRow("10:04", "17:52"),
        17 to TimeRow("10:37", "18:48"), 18 to TimeRow("10:53", "18:19"),
        19 to TimeRow("10:54", "19:25"), 20 to TimeRow("10:59", "19:42"),
        21 to TimeRow("10:59", "18:29"), 22 to TimeRow("11:18", "19:28"),
        23 to TimeRow("11:29", "19:20"), 24 to TimeRow("11:56", "19:37"),
        25 to TimeRow("12:10", "19:57"),
    )

    /** 본선 야간 (33~51) 조합별 [출근, 익일종료] — 휴휴 33~35는 대기형(사용자 확인) */
    val MAIN_NIGHT: Map<Int, Map<NightCombo, Pair<String, String>>> = mapOf(
        33 to mapOf(NightCombo.PP to ("16:36" to "6:30"), NightCombo.PH to ("16:36" to "6:37"), NightCombo.HH to ("17:00" to "7:30"), NightCombo.HP to ("15:29" to "6:30")),
        34 to mapOf(NightCombo.PP to ("16:59" to "7:33"), NightCombo.PH to ("16:59" to "7:30"), NightCombo.HH to ("18:00" to "8:30"), NightCombo.HP to ("15:53" to "7:33")),
        35 to mapOf(NightCombo.PP to ("17:24" to "8:47"), NightCombo.PH to ("17:24" to "7:53"), NightCombo.HH to ("19:00" to "9:30"), NightCombo.HP to ("16:29" to "7:42")),
        36 to mapOf(NightCombo.PP to ("17:51" to "7:56"), NightCombo.PH to ("17:51" to "8:00"), NightCombo.HH to ("15:29" to "8:29"), NightCombo.HP to ("16:39" to "7:56")),
        37 to mapOf(NightCombo.PP to ("17:55" to "8:36"), NightCombo.PH to ("17:55" to "6:54"), NightCombo.HH to ("15:53" to "7:31"), NightCombo.HP to ("17:19" to "7:45")),
        38 to mapOf(NightCombo.PP to ("19:08" to "7:42"), NightCombo.PH to ("19:08" to "7:31"), NightCombo.HH to ("16:29" to "7:30"), NightCombo.HP to ("18:51" to "8:03")),
        39 to mapOf(NightCombo.PP to ("19:16" to "7:45"), NightCombo.PH to ("19:16" to "6:45"), NightCombo.HH to ("16:39" to "8:07"), NightCombo.HP to ("18:57" to "8:10")),
        40 to mapOf(NightCombo.PP to ("19:33" to "8:03"), NightCombo.PH to ("19:33" to "8:07"), NightCombo.HH to ("17:19" to "8:40"), NightCombo.HP to ("18:20" to "8:20")),
        41 to mapOf(NightCombo.PP to ("19:44" to "8:10"), NightCombo.PH to ("19:44" to "8:29"), NightCombo.HH to ("18:51" to "7:53"), NightCombo.HP to ("18:37" to "8:36")),
        42 to mapOf(NightCombo.PP to ("19:45" to "8:20"), NightCombo.PH to ("19:45" to "8:40"), NightCombo.HH to ("18:57" to "8:00"), NightCombo.HP to ("18:42" to "8:49")),
        43 to mapOf(NightCombo.PP to ("19:55" to "8:57"), NightCombo.PH to ("19:55" to "8:43"), NightCombo.HH to ("18:20" to "8:43"), NightCombo.HP to ("19:27" to "8:47")),
        44 to mapOf(NightCombo.PP to ("20:00" to "9:15"), NightCombo.PH to ("20:00" to "8:57"), NightCombo.HH to ("18:37" to "8:57"), NightCombo.HP to ("19:38" to "8:57")),
        45 to mapOf(NightCombo.PP to ("20:18" to "8:49"), NightCombo.PH to ("20:18" to "9:01"), NightCombo.HH to ("18:42" to "9:01"), NightCombo.HP to ("19:50" to "8:50")),
        46 to mapOf(NightCombo.PP to ("20:24" to "8:50"), NightCombo.PH to ("20:24" to "9:19"), NightCombo.HH to ("19:27" to "9:19"), NightCombo.HP to ("20:56" to "9:36")),
        47 to mapOf(NightCombo.PP to ("20:51" to "9:36"), NightCombo.PH to ("20:51" to "9:29"), NightCombo.HH to ("19:38" to "9:29"), NightCombo.HP to ("21:07" to "9:15")),
        48 to mapOf(NightCombo.PP to ("22:34" to "7:18"), NightCombo.PH to ("22:34" to "7:16"), NightCombo.HH to ("21:38" to "7:16"), NightCombo.HP to ("21:38" to "7:18")),
        49 to mapOf(NightCombo.PP to ("22:45" to "7:15"), NightCombo.PH to ("22:45" to "7:28"), NightCombo.HH to ("21:45" to "7:28"), NightCombo.HP to ("21:45" to "7:15")),
        50 to mapOf(NightCombo.PP to ("22:46" to "7:17"), NightCombo.PH to ("22:46" to "7:28"), NightCombo.HH to ("21:50" to "7:28"), NightCombo.HP to ("21:50" to "7:17")),
        51 to mapOf(NightCombo.PP to ("22:57" to "7:27"), NightCombo.PH to ("22:57" to "7:25"), NightCombo.HH to ("21:57" to "7:25"), NightCombo.HP to ("21:57" to "7:27")),
    )

    /** 대기조 (평일·휴일 동일) */
    val STANDBY: Map<String, TimeRow> = mapOf(
        "대1" to TimeRow("7:00", "16:00"), "대2" to TimeRow("8:00", "17:00"),
        "대3" to TimeRow("8:30", "17:30"), "대4" to TimeRow("9:00", "18:00"),
        "대5" to TimeRow("9:30", "18:30"), "대6" to TimeRow("10:00", "19:00"),
        "대11" to TimeRow("17:00", "7:30", true), "대12" to TimeRow("18:00", "8:30", true),
        "대13" to TimeRow("19:00", "9:30", true),
    )

    /* ── 조회 헬퍼 ────────────────────────────────────── */

    /** 해당 날짜의 다이아 시각 (달력 셀·상세·위젯 공용) */
    fun timeRowFor(duty: DutyCode, date: LocalDate): TimeRow? {
        val hol = isHolidayTimetable(date)
        return when (duty.type) {
            // diaRaw = 충당 계열이면 대신 뛰는 다이아("충당 지3" → "지3"), 아니면 raw 그대로
            DutyType.BRANCH, DutyType.BRANCH_NIGHT, DutyType.BRANCH_STANDBY ->
                (if (hol) BRANCH_HOLIDAY else BRANCH_WEEKDAY)[duty.diaRaw]
            DutyType.STANDBY -> STANDBY[duty.diaRaw]
            DutyType.MAIN_NIGHT -> duty.number?.let { n ->
                MAIN_NIGHT[n]?.get(comboOf(date))?.let { (on, off) -> TimeRow(on, off, overnight = true) }
            }
            DutyType.MAIN_DAY -> duty.number?.let { n ->
                (if (hol) MAIN_DAY_HOLIDAY else MAIN_DAY_WEEKDAY)[n]
            }
            else -> null
        }
    }

    /** 출근시각 문자열 (없으면 null) */
    fun signOn(duty: DutyCode, date: LocalDate): String? = timeRowFor(duty, date)?.signOn
}
