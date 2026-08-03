package com.sinjeong.crewcalendar.domain.model

import java.time.DayOfWeek
import java.time.LocalDate

/** 근무선택 1단계 소속 (본선 기관사·차장은 같은 108칸 순환, 시작점만 다름) */
enum class CrewGroup(val label: String, val role: CrewRole) {
    BRANCH("신정지선", CrewRole.DRIVER_BRANCH),
    MAIN_DRIVER("본선 기관사", CrewRole.DRIVER_MAIN),
    MAIN_CONDUCTOR("본선 차장", CrewRole.CONDUCTOR),
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

    fun patternFor(group: CrewGroup): Pattern = when (group) {
        CrewGroup.BRANCH -> BRANCH_PATTERN
        else -> MAIN_PATTERN
    }

    fun groupFor(patternId: String?): CrewGroup? = when (patternId) {
        BRANCH_PATTERN.id -> CrewGroup.BRANCH
        MAIN_PATTERN.id -> CrewGroup.MAIN_DRIVER
        else -> null
    }

    /* ── 2026 공휴일 / 기념일 / 절기 ─────────────────────── */

    private fun d(m: Int, day: Int) = LocalDate.of(2026, m, day)

    /** 법정공휴일: 날짜 빨강 + 휴일 다이아 시각 적용 */
    val PUBLIC_HOLIDAYS: Map<LocalDate, String> = mapOf(
        d(1, 1) to "신정", d(2, 16) to "설연휴", d(2, 17) to "설날", d(2, 18) to "설연휴",
        d(3, 1) to "삼일절", d(3, 2) to "대체휴일", d(5, 5) to "어린이날",
        d(5, 24) to "부처님오신날", d(5, 25) to "대체휴일", d(6, 6) to "현충일",
        d(8, 15) to "광복절", d(8, 17) to "대체휴일",
        d(9, 24) to "추석연휴", d(9, 25) to "추석", d(9, 26) to "추석연휴",
        d(10, 3) to "개천절", d(10, 5) to "대체휴일", d(10, 9) to "한글날",
        d(12, 25) to "성탄절",
    )

    /** 기념일: 이름만 빨강, 근무는 평일 (제헌절 등) */
    val MEMORIAL_DAYS: Map<LocalDate, String> = mapOf(d(7, 17) to "제헌절")

    /** 절기/복날 표시 */
    val SEASONAL_TERMS: Map<LocalDate, String> = mapOf(
        d(7, 7) to "소서", d(7, 15) to "초복", d(7, 23) to "대서", d(7, 25) to "중복",
        d(8, 7) to "입추", d(8, 14) to "말복",
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
            DutyType.BRANCH, DutyType.BRANCH_NIGHT, DutyType.BRANCH_STANDBY ->
                (if (hol) BRANCH_HOLIDAY else BRANCH_WEEKDAY)[duty.raw]
            DutyType.STANDBY -> STANDBY[duty.raw]
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
