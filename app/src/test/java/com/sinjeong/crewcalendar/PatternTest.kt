package com.sinjeong.crewcalendar

import com.sinjeong.crewcalendar.domain.model.Bundled
import com.sinjeong.crewcalendar.domain.model.BundledRoster
import com.sinjeong.crewcalendar.domain.model.BundledStaff
import com.sinjeong.crewcalendar.domain.model.BundledTimetable
import com.sinjeong.crewcalendar.domain.model.CrewGroup
import com.sinjeong.crewcalendar.domain.model.DutyCode
import com.sinjeong.crewcalendar.domain.model.DutyType
import com.sinjeong.crewcalendar.domain.model.MainLegs
import com.sinjeong.crewcalendar.domain.model.NightCombo
import com.sinjeong.crewcalendar.domain.model.RouteTable
import com.sinjeong.crewcalendar.domain.model.ShiftTeam
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime


/**
 * v1.6.14에서 추가된 두 근무형태의 계산 근거를 고정한다. 깨지면 근무표가 통째로 틀어진다.
 *
 * ⚠ `./gradlew test`는 이 저장소 경로(07_프로젝트)의 한글 때문에 포크된 테스트 워커가
 * 클래스를 못 찾아 ClassNotFoundException 으로 죽는다 — 코드가 아니라 환경 문제다.
 * 실행은 컴파일 후 JUnitCore를 직접 띄운다(9 tests OK 확인):
 *   ./gradlew :app:compileDebugUnitTestKotlin
 *   java -cp "app/build/intermediates/classes/debugUnitTest/transformDebugUnitTestClassesWithAsm/dirs;\
 *     app/build/intermediates/runtime_app_classes_jar/debug/bundleDebugClassesToRuntimeJar/classes.jar;\
 *     <junit-4.13.2.jar>;<hamcrest-core-1.3.jar>;<kotlin-stdlib.jar>" \
 *     org.junit.runner.JUnitCore com.sinjeong.crewcalendar.PatternTest
 */
class PatternTest {

    private val shift = Bundled.SHIFT_PATTERN
    private val office = Bundled.OFFICE_PATTERN

    /** 사용자 확정 기준점: 2026-08-10(월) A조 = 비번 */
    @Test fun shiftTeamA_isPostNight_on_2026_08_10() {
        assertEquals("비번", shift.dutyOn(LocalDate.of(2026, 8, 10), ShiftTeam.A.offset).raw)
    }

    @Test fun shiftPattern_cycles_every_4_days() {
        val expected = listOf("비번", "휴무", "주간", "야간")
        (0..11).forEach { i ->
            assertEquals(
                expected[i % 4],
                shift.dutyOn(LocalDate.of(2026, 8, 10).plusDays(i.toLong()), 0).raw,
            )
        }
    }

    @Test fun shiftTeams_are_offset_by_one_day() {
        val date = LocalDate.of(2026, 8, 10)
        (1..3).forEach { team ->
            assertEquals(
                shift.dutyOn(date.plusDays(team.toLong()), 0).raw,
                shift.dutyOn(date, team).raw,
            )
        }
        // 같은 날 네 조가 서로 다른 근무 = 4조2교대 성립 조건
        assertEquals(4, (0..3).map { shift.dutyOn(date, it).raw }.toSet().size)
    }

    /**
     * ★ v1.6.24 핵심 고정값 — 사용자가 직접 확인해 준 2026-08-16 실근무.
     * 종전 배치(A0·B1·C2·D3)는 B·D가 뒤바뀌어 이 표를 못 맞췄다.
     */
    @Test fun shiftTeams_match_user_verified_2026_08_16() {
        val d = LocalDate.of(2026, 8, 16)
        mapOf(
            ShiftTeam.A to "주간", ShiftTeam.B to "휴무",
            ShiftTeam.C to "비번", ShiftTeam.D to "야간",
        ).forEach { (team, expected) ->
            assertEquals(team.label, expected, shift.dutyOn(d, team.offset).raw)
        }
        // 4조2교대 명단도 같은 offset 을 써야 동료근무 표가 근무선택과 일치한다
        val byTeam = BundledRoster.SHIFT_4_2.groupBy({ it.second }, { it.first })
        listOf(ShiftTeam.A to "황태상", ShiftTeam.B to "윤종대",
               ShiftTeam.C to "최용재", ShiftTeam.D to "임종호").forEach { (team, name) ->
            assertTrue("$name ${team.label}", byTeam[team.offset]?.contains(name) == true)
        }
    }

    /** 좁은 칸 표기(v1.6.24): 낱말 코드는 한 글자. 색을 정하는 `type`·원본 `raw`는 그대로다 */
    @Test fun wordCodes_display_as_single_char() {
        mapOf("주간" to "주", "야간" to "야", "비번" to "~", "휴무" to "휴").forEach { (raw, short) ->
            val code = DutyCode.parse(raw)
            assertEquals(raw, short, code.display)
            assertEquals(raw, raw, code.displayLong)
        }
        assertEquals(DutyType.MAIN_DAY, DutyCode.parse("주간").type)
        assertEquals(DutyType.MAIN_NIGHT, DutyCode.parse("야간").type)
        assertEquals(DutyType.POST_NIGHT, DutyCode.parse("비번").type)   // 보라
        assertEquals(DutyType.REST, DutyCode.parse("휴무").type)         // 빨강
    }

    @Test fun officePattern_weekday_day_weekend_rest() {
        // 2026-08-10(월) ~ 08-14(금) 주간, 08-15(토)·08-16(일) 휴무
        (0..4).forEach {
            assertEquals("주간", office.dutyOn(LocalDate.of(2026, 8, 10).plusDays(it.toLong()), 0).raw)
        }
        assertEquals("휴무", office.dutyOn(LocalDate.of(2026, 8, 16), 0).raw)
    }

    @Test fun officePattern_rests_on_public_and_substitute_holidays() {
        assertEquals("휴무", office.dutyOn(LocalDate.of(2026, 8, 15), 0).raw) // 광복절(토)
        assertEquals("휴무", office.dutyOn(LocalDate.of(2026, 8, 17), 0).raw) // 대체휴일(월)
        assertEquals("주간", office.dutyOn(LocalDate.of(2026, 8, 18), 0).raw) // 화요일 정상근무
        assertEquals("휴무", office.dutyOn(LocalDate.of(2026, 3, 2), 0).raw)  // 삼일절 대체휴일(월)
    }

    /** 회귀: 승무원 3종 패턴은 restOnHolidays=false 라 공휴일에도 그대로 돌아야 한다 */
    @Test fun crewPatterns_unaffected_by_holidays() {
        assertEquals("9", Bundled.MAIN_PATTERN.dutyOn(LocalDate.of(2026, 7, 1), 0).raw)
        assertEquals("지3", Bundled.BRANCH_PATTERN.dutyOn(LocalDate.of(2026, 7, 1), 0).raw)
        assertTrue(Bundled.MAIN_PATTERN.dutyOn(LocalDate.of(2026, 8, 15), 0).raw.isNotBlank())
    }

    @Test fun every_group_has_pattern_and_roster() {
        CrewGroup.entries.forEach { g ->
            val p = Bundled.patternFor(g)
            assertTrue(g.name, p.sequence.isNotEmpty())
            assertNotNull(g.name, BundledRoster.forGroup(g))
        }
        // 저장된 patternId 로 다시 소속을 찾을 수 있어야 설정·동료화면이 "미선택"으로 안 떨어진다
        Bundled.ALL_PATTERNS.forEach { assertNotNull(it.id, Bundled.groupFor(it.id)) }
    }

    /** 동료근무 화면에 뜨는 이름은 전화조회(BundledStaff)에도 있어야 한다 — 두 파일이 어긋나면 조용히 실패 */
    @Test fun new_group_names_resolve_to_phone_numbers() {
        listOf(CrewGroup.SHIFT_4_2, CrewGroup.OFFICE_DAY).forEach { g ->
            BundledRoster.forGroup(g).forEach { (name, _) ->
                assertNotNull("$name 전화번호 없음", BundledStaff.phoneFor(name, false))
            }
        }
    }

    /**
     * v1.6.19: 26년 8월 발행 근무표 전수 대조로 확정된 교번 표본.
     * 승무 3종 224명 × 31일 오차 0 — 이 6칸이 어긋나면 offset 입력이 다시 틀어진 것이다.
     */
    @Test fun august2026_verified_offsets() {
        val main = Bundled.MAIN_PATTERN
        assertEquals("19", main.dutyOn(LocalDate.of(2026, 8, 15), 51).raw)   // 정정된 본선 차장
        assertEquals("14", main.dutyOn(LocalDate.of(2026, 8, 2), 51).raw)
        assertEquals("휴29", main.dutyOn(LocalDate.of(2026, 8, 1), 43).raw)  // 추가 3명(기관사)
        assertEquals("휴25", main.dutyOn(LocalDate.of(2026, 8, 1), 28).raw)
        assertEquals("휴19", main.dutyOn(LocalDate.of(2026, 8, 1), 83).raw)
        assertEquals("17", main.dutyOn(LocalDate.of(2026, 8, 1), 88).raw)    // 추가 1명(차장)
        assertEquals("49", main.dutyOn(LocalDate.of(2026, 8, 1), 89).raw)    // 4조2교대→본선 이동
    }

    /** 같은 소속에 같은 offset이 둘이면 둘 중 하나가 틀린 것 — 8월 대조에서 실제로 잡힌 유형 */
    @Test fun no_duplicate_offsets_within_crew_group() {
        listOf(CrewGroup.MAIN_DRIVER, CrewGroup.MAIN_CONDUCTOR, CrewGroup.BRANCH).forEach { g ->
            val list = BundledRoster.forGroup(g)
            val len = Bundled.patternFor(g).length
            assertEquals("$g offset 중복", list.size, list.map { it.second }.toSet().size)
            assertEquals("$g 이름 중복", list.size, list.map { it.first }.toSet().size)
            assertTrue("$g offset 범위 초과", list.all { it.second in 0 until len })
        }
    }

    @Test fun no_duplicate_employee_numbers() {
        val all = BundledStaff.DRIVERS + BundledStaff.CONDUCTORS + BundledStaff.OFFICE
        assertEquals(all.size, all.map { it.second }.toSet().size)   // 사번 중복 0
    }

    /* ── v1.6.16: 6/3 지방선거일 · 7/17 제헌절을 휴일 다이아로 (현업 확정) ───────── */

    private val election = LocalDate.of(2026, 6, 3)
    private val constitution = LocalDate.of(2026, 7, 17)

    @Test fun electionDay_and_constitutionDay_are_public_holidays() {
        assertTrue(Bundled.PUBLIC_HOLIDAYS.containsKey(election))
        assertTrue(Bundled.PUBLIC_HOLIDAYS.containsKey(constitution))
        assertTrue(Bundled.isHolidayTimetable(election))
        assertTrue(Bundled.isHolidayTimetable(constitution))
        // 제헌절은 기념일 목록에서 빠졌다(양쪽에 있으면 이름이 두 번 붙는다)
        assertTrue(Bundled.MEMORIAL_DAYS.isEmpty())
    }

    /** 두 날의 출근시각이 평일값이 아니라 휴일값이어야 한다 — 틀리면 지각·결근 */
    @Test fun newHolidays_use_holiday_signOn_times() {
        listOf(election, constitution).forEach { d ->
            assertEquals("지4 $d", "9:45", Bundled.signOn(DutyCode.parse("지4"), d))
            assertEquals("본선9 $d", "8:01", Bundled.signOn(DutyCode.parse("9"), d))
        }
        // 하루 전은 여전히 평일값
        assertEquals("10:11", Bundled.signOn(DutyCode.parse("지4"), election.minusDays(1)))
        assertEquals("7:02", Bundled.signOn(DutyCode.parse("9"), constitution.minusDays(1)))
    }

    /** 전날 야간 다이아의 평/휴 조합도 같이 바뀐다 */
    @Test fun nightCombo_flips_around_new_holidays() {
        assertEquals(NightCombo.PH, Bundled.comboOf(election.minusDays(1)))   // 6/2(화) → 6/3 휴일
        assertEquals(NightCombo.HP, Bundled.comboOf(election))                // 6/3 휴일 → 6/4(목) 평일
        assertEquals(NightCombo.PH, Bundled.comboOf(constitution.minusDays(1))) // 7/16(목) → 7/17 휴일
        assertEquals(NightCombo.HH, Bundled.comboOf(constitution))            // 7/17 휴일 → 7/18(토)
    }

    @Test fun officePattern_rests_on_new_holidays() {
        assertEquals("휴무", office.dutyOn(election, 0).display)
        assertEquals("휴무", office.dutyOn(constitution, 0).display)
        assertEquals("주간", office.dutyOn(election.minusDays(1), 0).display)
    }

    /**
     * v1.6.25 — 충당 계열 + 다이아 저장 형식("충당 9"). 깨지면 출근시각·행로표·열번이 통째로 사라진다.
     * 핵심은 **타입·번호는 다이아 기준, 색만 대기(STANDBY)** 라는 두 갈래다.
     */
    @Test fun fillCode_inherits_dia_but_keeps_standby_color() {
        val main = DutyCode.parse("충당 9")
        assertEquals(DutyType.MAIN_DAY, main.type)
        assertEquals(DutyType.STANDBY, main.colorType)      // 색은 대기 노랑 유지
        assertEquals(9, main.number)
        assertEquals("충당", main.fill)
        assertEquals("9", main.diaRaw)
        assertEquals("충당9", main.display)                  // 좁은 칸
        assertEquals("충당 9", main.displayLong)             // 상세시트·알림
        assertTrue(main.isWorkDay)

        val branch = DutyCode.parse("대기충당 지3")
        assertEquals(DutyType.BRANCH, branch.type)
        assertEquals(DutyType.STANDBY, branch.colorType)
        assertTrue(branch.isBranch)
        assertEquals("지3", branch.diaRaw)

        // 야간 다이아 대행 → 익일 자동 비번(UpdateDayUseCase.isOvernight)이 살아 있어야 한다
        val night = DutyCode.parse("교체 45")
        assertEquals(DutyType.MAIN_NIGHT, night.type)
        assertTrue(night.isOvernight)
        assertEquals(DutyType.STANDBY, night.colorType)

        // 출근시각이 실제로 다이아 기준으로 붙는다
        val weekday = LocalDate.of(2026, 8, 18)             // 화요일(평일)
        assertEquals(
            Bundled.signOn(DutyCode.parse("9"), weekday),
            Bundled.signOn(main, weekday),
        )
        assertNotNull(Bundled.signOn(branch, weekday))

        // 이전 형식 호환: 다이아 없는 "충당"·기존 서버 데이터 "대3 4"는 종전 그대로
        val bare = DutyCode.parse("충당")
        assertEquals(DutyType.STANDBY, bare.type)
        assertEquals(null, bare.fill)
        assertEquals("충당", bare.display)
        val legacy = DutyCode.parse("대3 4")
        assertEquals(DutyType.STANDBY, legacy.type)
        assertEquals(null, legacy.fill)
        assertEquals("대3 4", legacy.display)
        // 알 수 없는 다이아가 붙어도 충당 색·표기는 유지(깨진 데이터 방어)
        assertEquals(DutyType.STANDBY, DutyCode.parse("충당 없는다이아").type)
    }

    /**
     * 출근 알람 세 갈래를 손계산으로 고정한다 (v1.6.27 사용자 확정 규칙).
     *
     * 2026-08-18은 화요일(평일), 익일도 평일이라 야간 조합은 평평(PP).
     */
    @Test fun alarm_advice_has_three_branches() {
        val weekday = LocalDate.of(2026, 8, 18)

        // A. 지선 — 전반시작(양천구청 출발) 8:13 → 5분 전 도착
        val branch = BundledTimetable.advise(DutyCode.parse("지1"), weekday)
        assertEquals(LocalTime.of(8, 8), branch.at)
        assertTrue(branch.text, branch.text.contains("양천구청역 8:08 도착"))

        // B. 본선 신도림 교대 — 주간 12번 전반시작 8:07 → 창 7:48~7:57의 마지막 편 7:53
        val day = BundledTimetable.advise(DutyCode.parse("12"), weekday)
        assertEquals(LocalTime.of(7, 53), day.at)
        assertTrue(day.text, day.text.contains("신도림 8:07 출발"))
        // 야간 38번(평평) 전반시작 19:53 → 창 19:34~19:43의 마지막 편 19:41
        assertEquals(LocalTime.of(19, 41), BundledTimetable.advise(DutyCode.parse("38"), weekday).at)
        // 충당 대행도 대신 뛰는 다이아를 그대로 따라간다
        assertEquals(LocalTime.of(19, 41), BundledTimetable.advise(DutyCode.parse("충당 38"), weekday).at)

        // C. 기지 출고(간격 60분) — 알람 없음. 주간 9번은 행로표에서 신정기지 ○(출고) 확인된 다이아
        val depot = BundledTimetable.advise(DutyCode.parse("9"), weekday)
        assertEquals(null, depot.at)
        assertTrue(depot.text, depot.text.contains("출고"))

        // 기준시각이 없는 근무 — 대기 계열·운휴대기
        assertEquals(null, BundledTimetable.advise(DutyCode.parse("대3"), weekday).at)
        assertEquals(null, BundledTimetable.advise(DutyCode.parse("지대1"), weekday).at)
        val hhSat = LocalDate.of(2026, 8, 22) // 토 → 일 = 휴휴, 33~35는 운휴대기
        assertEquals(NightCombo.HH, Bundled.comboOf(hhSat))
        assertEquals(null, BundledTimetable.advise(DutyCode.parse("33"), hhSat).at)

        // 4조2교대·통상근무는 출근시각 자체가 없다 = 아이콘이 안 뜨는 근거
        assertEquals(null, Bundled.timeRowFor(DutyCode.parse("주간"), weekday))
        assertEquals(null, Bundled.timeRowFor(DutyCode.parse("주"), weekday))
    }

    /**
     * **B/C 판별의 근거를 잠근다** — 이 테스트가 깨지면 편승 알람이 틀린 시각을 줄 수 있다.
     *
     * 판별 기준은 "출근 → 전반시작" 간격 45분(신도림 교대) / 60분(기지 출고)인데,
     * 행로표가 스캔 이미지라 역명을 코드로 확인할 수 없다. 대신 **전반 첫 열번**이
     * 5xxx·6xxx(회송 = 출고)인지로 교차검증한다 — 두 지표는 전 다이아에서 일치해야 한다.
     * (v1.6.27 실측: 주간 평일 29 + 휴일 25 + 야간 73 = 127건 전부 일치, 불일치 0건)
     */
    @Test fun deadhead_gap_matches_depot_train_number() {
        fun mins(t: String) = t.split(":").let { it[0].toInt() * 60 + it[1].toInt() }
        fun isDepot(firstHalf: String) =
            firstHalf.substringBefore('·').let { it.toIntOrNull() != null && it.first() in "56" }

        var checked = 0
        fun check(tag: String, signOn: String, legStart: String, firstHalf: String) {
            val gap = mins(legStart) - mins(signOn)
            assertTrue("$tag 간격은 45/60 둘 중 하나여야 (실제 $gap)", gap == 45 || gap == 60)
            assertEquals("$tag — 간격 60분과 출고 열번이 어긋난다", gap == 60, isDepot(firstHalf))
            checked++
        }

        listOf(
            Triple(Bundled.MAIN_DAY_WEEKDAY, MainLegs.WEEKDAY, false),
            Triple(Bundled.MAIN_DAY_HOLIDAY, MainLegs.HOLIDAY, true),
        ).forEach { (times, legs, hol) ->
            times.forEach { (n, row) ->
                check("주간$n(${if (hol) "휴" else "평"})", row.signOn, legs.getValue(n)[0],
                    RouteTable.forMainDay(n, hol)!!.firstHalf)
            }
        }
        Bundled.MAIN_NIGHT.forEach { (n, byCombo) ->
            byCombo.forEach { (combo, onOff) ->
                val leg = MainLegs.forNight(n, combo) ?: return@forEach // 33~35 휴휴 = 운휴대기
                check("야간$n(${combo.label})", onOff.first, leg[0],
                    RouteTable.forMainNight(n, combo)!!.firstHalf)
            }
        }
        assertEquals("검사한 다이아 수", 127, checked)
    }

    /**
     * 편승 기준시각 후보 데이터의 성질을 고정한다 (사용자 규칙 확정용 근거, v1.6.26 실측).
     *
     *  · 지선 `firstLeg` 시작 = **출근 +30분 정확히** (평일·휴일 13개 다이아 전부, 예외 0건)
     *  · 본선 `MainLegs` 전반시작 − 출근 = **45분 또는 60분** → 출근시각으로 역산 불가
     */
    @Test fun deadhead_basis_candidates_have_expected_shape() {
        fun mins(t: String) = t.split(":").let { it[0].toInt() * 60 + it[1].toInt() }

        listOf(Bundled.BRANCH_WEEKDAY, Bundled.BRANCH_HOLIDAY).forEach { table ->
            table.forEach { (dia, row) ->
                val start = row.firstLeg?.substringBefore('#')
                if (start == null) {
                    assertTrue("$dia 은 대기 계열이라 사업시각이 없어야", dia.startsWith("지대"))
                } else {
                    assertEquals("$dia 출근+30 규칙", 30, mins(start) - mins(row.signOn))
                }
            }
        }

        val gaps = listOf(
            Bundled.MAIN_DAY_WEEKDAY to MainLegs.WEEKDAY,
            Bundled.MAIN_DAY_HOLIDAY to MainLegs.HOLIDAY,
        ).flatMap { (times, legs) ->
            times.map { (n, row) ->
                val leg = legs[n]
                assertNotNull("본선 $n 사업시각", leg)
                mins(leg!![0]) - mins(row.signOn)
            }
        }
        assertEquals("본선은 간격이 45/60 두 갈래", setOf(45, 60), gaps.toSet())
    }

    /** 본선 주간 26~29는 휴일 시각표에 없다 = 그날 운휴. 상세시트 안내 분기의 근거 */
    @Test fun mainDay_26to29_have_no_holiday_timetable() {
        assertEquals((1..25).toSet(), Bundled.MAIN_DAY_HOLIDAY.keys)
        (26..29).forEach { n ->
            assertEquals("$n", null, Bundled.timeRowFor(DutyCode.parse("$n"), election))
            assertNotNull("$n", Bundled.timeRowFor(DutyCode.parse("$n"), election.minusDays(1)))
        }
    }
}
