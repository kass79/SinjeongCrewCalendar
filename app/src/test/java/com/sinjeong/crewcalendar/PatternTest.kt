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
import com.sinjeong.crewcalendar.domain.model.teamBadge
import com.sinjeong.crewcalendar.widget.signOnAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime


/**
 * v1.6.14에서 추가된 두 근무형태의 계산 근거를 고정한다. 깨지면 근무표가 통째로 틀어진다.
 *
 * ⚠ `./gradlew test`는 이 저장소 경로(07_프로젝트)의 한글 때문에 포크된 테스트 워커가
 * 클래스를 못 찾아 ClassNotFoundException 으로 죽는다 — 코드가 아니라 환경 문제다.
 * 실행은 컴파일 후 JUnitCore를 직접 띄운다(v1.6.35 기준 32 tests OK):
 *   ./gradlew :app:compileDebugUnitTestKotlin
 *   java -cp "app/build/tmp/kotlin-classes/debugUnitTest;app/build/tmp/kotlin-classes/debug;\
 *     <junit-4.13.2.jar>;<hamcrest-core-1.3.jar>;<kotlin-stdlib.jar>" \
 *     org.junit.runner.JUnitCore com.sinjeong.crewcalendar.PatternTest
 *
 * ⚠ **`kotlin-classes`(코틀린 컴파일 산출물)를 써야 한다.** 종전에 적어둔
 * `intermediates/classes/.../transformDebugUnitTestClassesWithAsm/dirs` + `runtime_app_classes_jar`는
 * `assemble`류를 돌려야 갱신되는 자리라, 컴파일만 하고 그 경로로 돌리면 **옛 클래스가 실행돼**
 * 방금 고친 코드가 반영 안 된 채 엉뚱한 실패가 나온다(v1.6.35에서 실제로 3건 헛failure).
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

    /**
     * ★ v1.6.54 핵심 고정값 — **2026년 8월 근무계획표 실측표**를 8개 조 전부에 대해 잠근다.
     *
     * 관제 16명을 등록하면서 "관제도 조 글자만 같으면 운용과 같은 근무"라는 전제를 세웠다.
     * 그 전제가 틀리면 관제 16명의 근무가 통째로 틀리므로, 근무계획표 `전체` 시트에서 읽은
     * 8/1~8/10 열 그대로를 여기에 박아 둔다(`휴가`·`지근` 같은 개인 사유 덮어쓰기 날짜는 뺐다 —
     * 순환이 아니라 그날만의 예외라 패턴으로 재현할 수 없다).
     */
    @Test fun shiftAndControlTeams_match_august2026_plan() {
        // 근무계획표 표기(주/야/비/휴) → 앱 raw
        val plan = mapOf('주' to "주간", '야' to "야간", '비' to "비번", '휴' to "휴무")
        // 8/1(토)부터 10일. '·' = 휴가·지근 등 개인 사유로 덮인 날 (검증 제외)
        //                             1234567890
        val byTeam = mapOf(
            ShiftTeam.A to "야비휴주야비휴주야비",
            ShiftTeam.B to "··비휴주야비휴주야",
            ShiftTeam.C to "휴주야비·주야비휴주",
            ShiftTeam.D to "비·주야비휴주야비휴",
        )
        val start = LocalDate.of(2026, 8, 1)
        var checked = 0
        byTeam.forEach { (team, row) ->
            row.forEachIndexed { i, ch ->
                val expected = plan[ch] ?: return@forEachIndexed
                val date = start.plusDays(i.toLong())
                // 운용·관제 **두 부서 모두** 같은 값이어야 한다 — 조 글자만이 근무를 정한다
                listOf(CrewGroup.SHIFT_4_2, CrewGroup.SHIFT_CONTROL).forEach { g ->
                    assertEquals(
                        "${g.label}${team.label} $date",
                        expected, Bundled.patternFor(g).dutyOn(date, team.offset).raw,
                    )
                    checked++
                }
            }
        }
        assertEquals("대조한 칸 수", 72, checked) // (10+8+9+9)일 × 2부서
    }

    /**
     * **부서(CrewGroup)와 조(patternOffset)는 끝까지 따로 둔다** (v1.6.54 사용자 확정 설계).
     *
     * 합쳐지는 곳은 화면 표기 [teamBadge] 하나뿐이고, 근무를 정하는 것은 조 글자뿐이다.
     * 이 테스트가 깨지면 부서 이동이 근무를 흔드는 구조로 되돌아간 것이다.
     */
    @Test fun department_and_team_stay_separate() {
        // ① 두 부서의 순환 데이터가 완전히 같다 (id·이름만 다름)
        val op = Bundled.patternFor(CrewGroup.SHIFT_4_2)
        val ctrl = Bundled.patternFor(CrewGroup.SHIFT_CONTROL)
        assertEquals(op.sequence, ctrl.sequence)
        assertEquals(op.anchorDate, ctrl.anchorDate)
        assertTrue("두 부서가 patternId를 공유하면 저장 때 부서가 사라진다", op.id != ctrl.id)

        // ② 부서를 바꿔도 근무는 한 칸도 안 바뀐다
        (0..40).forEach { d ->
            val date = LocalDate.of(2026, 8, 1).plusDays(d.toLong())
            ShiftTeam.entries.forEach { t ->
                assertEquals("$date ${t.label}", op.dutyOn(date, t.offset).raw, ctrl.dutyOn(date, t.offset).raw)
            }
        }

        // ③ 표기는 괄호 없이 두 글자, 부서 글자 + 조 글자
        assertEquals("운A", teamBadge(CrewGroup.SHIFT_4_2, ShiftTeam.A.offset))
        assertEquals("관D", teamBadge(CrewGroup.SHIFT_CONTROL, ShiftTeam.D.offset))
        assertEquals("관B", teamBadge(CrewGroup.SHIFT_CONTROL, ShiftTeam.B.offset))
        // 4조2교대가 아닌 소속엔 조 개념이 없다 → 배지도 없다(정렬 키도 여기에 기댄다)
        listOf(CrewGroup.BRANCH, CrewGroup.MAIN_DRIVER, CrewGroup.MAIN_CONDUCTOR, CrewGroup.OFFICE_DAY)
            .forEach { assertNull(it.name, teamBadge(it, 0)) }
        // 공식 문서 표기: `반`이 아니라 `조`
        assertEquals("A조", ShiftTeam.A.label)

        // ④ 하위호환 — 옛 `4조2교대` 저장값(patternId)은 기본적으로 운용으로 되읽힌다.
        //    부서가 갈리기 전 4조2교대 인원은 전원 운용이었다.
        assertEquals(CrewGroup.SHIFT_4_2, Bundled.groupFor("bundled-shift42"))
        assertEquals(CrewGroup.SHIFT_CONTROL, Bundled.groupFor("bundled-control42"))

        // ⑤ 이름을 주면 **관제 인원은 관제로 되돌린다**. 관제 직원도 부서가 갈리기 전엔
        //    고를 수 있는 게 `4조2교대` 하나뿐이라 이미 그 값으로 등록돼 있다(Firestore 실데이터).
        //    안 하면 동료 탭에 운용·관제 두 줄로 뜬다 — v1.6.54 에뮬레이터에서 박영무로 실제로 잡혔다.
        assertEquals(CrewGroup.SHIFT_CONTROL, Bundled.groupFor("bundled-shift42", "박영무"))
        assertEquals(CrewGroup.SHIFT_CONTROL, Bundled.groupFor("bundled-shift42", " 남궁용권 "))
        assertEquals(CrewGroup.SHIFT_4_2, Bundled.groupFor("bundled-shift42", "황태상"))  // 운용은 그대로
        assertEquals(CrewGroup.SHIFT_4_2, Bundled.groupFor("bundled-shift42", "박태호"))  // 명단 밖도 운용
        // 이름 보정은 옛 id에만 건다 — 다른 소속은 이름으로 흔들리면 안 된다
        assertEquals(CrewGroup.BRANCH, Bundled.groupFor("bundled-branch", "박영무"))
        assertEquals(CrewGroup.MAIN_DRIVER, Bundled.groupFor("bundled-main", "박영무"))
        // 동료 저장 키(`이름|enum이름`)도 그대로여야 저장된 동료가 유령이 되지 않는다
        assertEquals("SHIFT_4_2", CrewGroup.SHIFT_4_2.name)
    }

    /** 관제 16명이 4개 조에 4명씩 들어갔는지 — 한 명이라도 빠지면 그 사람 근무가 안 뜬다 */
    @Test fun controlRoster_has_sixteen_across_four_teams() {
        val list = BundledRoster.forGroup(CrewGroup.SHIFT_CONTROL)
        assertEquals("기지관제 인원", 16, list.size)
        assertEquals("이름 중복", 16, list.map { it.first }.toSet().size)
        val byTeam = list.groupBy({ it.second }, { it.first })
        listOf(ShiftTeam.A to "백형록", ShiftTeam.B to "나원필",
               ShiftTeam.C to "이형호", ShiftTeam.D to "이광우").forEach { (team, name) ->
            assertEquals("관제${team.label} 인원", 4, byTeam[team.offset]?.size)
            assertTrue("$name 관제${team.label}", byTeam[team.offset]?.contains(name) == true)
        }
        // 운용과 이름이 겹치면 한쪽이 잘못 들어간 것이다
        val op = BundledRoster.forGroup(CrewGroup.SHIFT_4_2).map { it.first }.toSet()
        assertEquals("운용·관제 겹치는 이름", emptySet<String>(), op intersect list.map { it.first }.toSet())
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

    /**
     * v1.6.36 — 지선 대기는 `지`를 떼지 않는다. 떼면 본선 대기와 글자가 똑같아져
     * 달력·동료근무·위젯·공유 이미지에서 `대1`이 본선인지 지선인지 구분이 안 됐다.
     * **표시만** 바꾼 것이라 저장값(raw)·시각표 조회 키(diaRaw)는 그대로여야 한다.
     */
    @Test fun branchStandby_keeps_its_ji_prefix() {
        val weekday = LocalDate.of(2026, 8, 18)             // 화요일(평일)
        listOf("지대1", "지대2", "지대11").forEach { raw ->
            val c = DutyCode.parse(raw)
            assertEquals(raw, DutyType.BRANCH_STANDBY, c.type)
            assertEquals(raw, raw, c.display)            // "대1"이 아니라 "지대1"
            assertEquals(raw, raw, c.displayLong)
            assertEquals(raw, raw, c.diaRaw)             // 시각표 키는 그대로
            assertNotNull(raw, Bundled.timeRowFor(c, weekday))
        }
        // 본선 대기는 종전 그대로 — 두 계열이 이제 화면에서 갈린다
        listOf("대1", "대2", "대11").forEach { assertEquals(it, it, DutyCode.parse(it).display) }
        // 지선 주간·야간 다이아는 번호대가 본선과 갈려 헷갈릴 일이 없어 계속 "지"를 뗀다
        assertEquals("3", DutyCode.parse("지3").display)
        assertEquals("12", DutyCode.parse("지12").display)
        // 지대11의 익일 비번은 여전히 `~` (POST_NIGHT 분기가 먼저다)
        assertEquals("~", DutyCode.parse("지대11비").display)
        // 충당 대행 표기도 지선 대기를 그대로 달고 간다
        assertEquals("대기충당지대11", DutyCode.parse("대기충당 지대11").display)
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

    /**
     * 동료근무 화면에 뜨는 이름은 전화조회(BundledStaff)에도 있어야 한다 — 두 파일이 어긋나면 조용히 실패.
     *
     * ⚠ 아래 2명은 **비상연락망(2026-06-16)에 애초에 없는 사람**이다. v1.6.24에서 사번표를 근거로
     * "구분 미상"에서 소속이 확정돼 `BundledRoster`에 들어왔지만, 연락망이 그보다 앞선 자료라
     * 전화번호만 비어 있다. 이름·사번은 `BundledStaff`에 있어 **로그인은 정상**이고,
     * 화면에는 "등록된 전화번호가 없습니다"가 뜬다(사무·2호선 인원과 같은 처리).
     * → 코드 결함이 아니라 **원본 자료의 공백**이다. 새 연락망을 받으면 채우고 이 목록을 비울 것.
     */
    @Test fun new_group_names_resolve_to_phone_numbers() {
        val knownMissingPhone = setOf("김대호", "이영란")
        val missing = mutableListOf<String>()
        listOf(CrewGroup.SHIFT_4_2, CrewGroup.SHIFT_CONTROL, CrewGroup.OFFICE_DAY).forEach { g ->
            BundledRoster.forGroup(g).forEach { (name, _) ->
                // 이름이 어긋나면(오타 등) phoneFor 가 null 을 주므로 여기서 같이 걸린다
                if (BundledStaff.phoneFor(name, false) == null) missing += name
            }
        }
        assertEquals("전화번호 공백 명단이 달라졌다", knownMissingPhone, missing.toSet())
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
        // v1.6.24부터 `display`는 한 글자(휴/주)다 — 패턴을 보는 검사라 원본 `raw`로 확인한다
        assertEquals("휴무", office.dutyOn(election, 0).raw)
        assertEquals("휴무", office.dutyOn(constitution, 0).raw)
        assertEquals("주간", office.dutyOn(election.minusDays(1), 0).raw)
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
     * v1.6.46 — 격자 칸(달력 칩·동료 탭·공유 이미지) 라벨. 사용자 지적:
     * *"대기 충당이 지2면 캘린더에도 표시해줘야지"* — 한 줄 `대기충당지2`(6글자)가 34dp 칩에서
     * 자동축소 하한(7sp)까지 줄어도 넘쳐 흘러 다이아가 안 읽혔다. 충당 계열만 두 줄로 접는다.
     *
     * ⚠ 저장값·조회 키는 그대로여야 한다 — [DutyCode.diaRaw]가 행로표·편승알람 키다.
     */
    @Test fun gridLabel_folds_only_fill_codes() {
        listOf("대기충당 지2" to "대기충당\n지2", "충당 9" to "충당\n9", "교체 45" to "교체\n45")
            .forEach { (raw, expected) ->
                val c = DutyCode.parse(raw)
                assertEquals(raw, expected, c.gridLabel)
                // 표시만 바꾼다: 저장값·조회 키·색은 불변
                assertEquals(raw, raw, c.raw)
                assertEquals(raw, raw.substringAfter(' '), c.diaRaw)
                assertEquals(raw, DutyType.STANDBY, c.colorType)
                // 폭 넉넉한 곳(위젯·알림·상세시트)은 줄바꿈 없는 한 줄 그대로
                assertTrue(raw, '\n' !in c.display && '\n' !in c.displayLong)
            }
        // 충당 계열이 아니면 display 그대로 — 한 줄이다
        listOf("지대11", "휴5", "3", "돌봄휴가", "충당").forEach {
            val c = DutyCode.parse(it)
            assertEquals(it, c.display, c.gridLabel)
        }
    }

    /**
     * 출근 알람 세 갈래를 손계산으로 고정한다 (v1.6.27 사용자 확정 규칙,
     * 세 번째 갈래 "기지 출고"는 v1.6.34에서 알람 없음 → **출고 50분 전**으로 바뀌었다).
     *
     * 2026-08-18은 화요일(평일), 익일도 평일이라 야간 조합은 평평(PP).
     */
    @Test fun alarm_advice_has_three_branches() {
        val weekday = LocalDate.of(2026, 8, 18)

        // A. 지선 — 전반시작(양천구청 출발) 8:13 → 5분 전 도착
        val branch = BundledTimetable.advise(DutyCode.parse("지1"), weekday)
        assertEquals(LocalTime.of(8, 8), branch.at)
        assertTrue(branch.text, branch.text.contains("양천구청역 8:08 도착"))

        // B. 본선 신도림 교대 — 주간 12번 전반시작 8:07 → 창 7:46~7:57의 마지막 편 7:53 → 알람 7:48
        val day = BundledTimetable.advise(DutyCode.parse("12"), weekday)
        assertEquals(LocalTime.of(7, 48), day.at)
        assertTrue(day.text, day.text.contains("양천구청역 7:53 편승"))
        assertTrue(day.text, day.text.contains("신도림 8:07 출발"))
        assertTrue(day.text, day.text.contains("알림 7:48"))
        // 야간 38번(평평) 전반시작 19:53 → 편승 19:41 → 알람 19:36
        assertEquals(LocalTime.of(19, 36), BundledTimetable.advise(DutyCode.parse("38"), weekday).at)
        // 충당 대행도 대신 뛰는 다이아를 그대로 따라간다
        assertEquals(LocalTime.of(19, 36), BundledTimetable.advise(DutyCode.parse("충당 38"), weekday).at)

        // C. 기지 출고(간격 60분) — v1.6.34부터 **출고 50분 전**. 사용자 확정 예시 그대로:
        //    주간 9번(행로표에서 신정기지 ○출고 확인) 전반시작 8:02 → 알람 7:12
        val depot = BundledTimetable.advise(DutyCode.parse("9"), weekday)
        assertEquals(LocalTime.of(7, 12), depot.at)
        assertTrue(depot.depot)
        assertEquals("신정기지 8:02 출고 · 알림 7:12", depot.text)

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
     * v1.6.29 — 편승 창을 10~20분 → **10~21분**으로 넓힌 결과를 손계산으로 고정한다.
     *
     * v1.6.27(창 19분)에서 "알람 없음"이던 야간 7조합이 **전부 살아난다** —
     * 6조합은 v1.6.28(20분)에서, 마지막 `46 휴평`(21:41 출발 / 앞 열차 21:20)이 이번 21분에서.
     * 이제 "편승 창이 비어 알람 없음"인 야간 조합은 **0건**이다.
     */
    @Test fun widenedWindow_revives_all_seven_night_combos() {
        val pp = LocalDate.of(2026, 8, 19)   // 수 → 목  : 평평
        val ph = LocalDate.of(2026, 8, 21)   // 금 → 토  : 평휴
        val hh = LocalDate.of(2026, 8, 22)   // 토 → 일  : 휴휴
        val hp = LocalDate.of(2026, 8, 23)   // 일 → 월  : 휴평
        assertEquals(NightCombo.PP, Bundled.comboOf(pp))
        assertEquals(NightCombo.PH, Bundled.comboOf(ph))
        assertEquals(NightCombo.HH, Bundled.comboOf(hh))
        assertEquals(NightCombo.HP, Bundled.comboOf(hp))

        // 다이아, 날짜, (신도림 출발 → 되살아난 편승시각), 몇 분 전인가
        listOf(
            Triple("37", pp, (LocalTime.of(18, 40) to LocalTime.of(18, 20)) to 20L),
            Triple("37", ph, (LocalTime.of(18, 40) to LocalTime.of(18, 20)) to 20L),
            Triple("42", pp, (LocalTime.of(20, 30) to LocalTime.of(20, 10)) to 20L),
            Triple("42", ph, (LocalTime.of(20, 30) to LocalTime.of(20, 10)) to 20L),
            Triple("50", hh, (LocalTime.of(22, 35) to LocalTime.of(22, 15)) to 20L),
            Triple("50", hp, (LocalTime.of(22, 35) to LocalTime.of(22, 15)) to 20L),
            // v1.6.29에서 마지막으로 살아난 하나 — 21분 전이라 20분 창에는 못 들어왔다
            Triple("46", hp, (LocalTime.of(21, 41) to LocalTime.of(21, 20)) to 21L),
        ).forEach { (dia, date, spec) ->
            val (times, gap) = spec
            val (start, expected) = times
            val a = BundledTimetable.advise(DutyCode.parse(dia), date)
            // v1.6.30: 알람은 편승 열차 출발 5분 전
            assertEquals("$dia ${Bundled.comboOf(date)}", expected.minusMinutes(5), a.at)
            assertTrue(a.text, a.text.contains("양천구청역 ${expected.hour}:%02d 편승".format(expected.minute)))
            assertTrue(a.text, a.text.contains("신도림 ${start.hour}:%02d 출발".format(start.minute)))
            assertEquals("$dia 는 ${gap}분 전이어야 한다", gap, java.time.Duration.between(expected, start).toMinutes())
        }
    }

    /**
     * 창을 넓혀도 **이미 알람이 있던 다이아의 권장시각은 한 건도 안 바뀐다**는 것을 전수 증명한다.
     *
     * 근거: 고르는 방식이 `maxOrNull`(가장 늦은 편)이고 창의 **늦은 쪽 끝(10분)은 그대로**라
     * 창을 앞으로 넓혀도 더 늦은 열차가 새로 들어올 수 없다. 이 테스트는 그 논증을
     * 옛 창(10~19분)을 재현해 실제 데이터로 확인한다 — 깨지면 창 확대가 선택을 바꾼 것이다.
     */
    @Test fun widenedWindow_never_moves_an_existing_recommendation() {
        fun oldWindowPick(start: LocalTime, holiday: Boolean): LocalTime? {
            val s = start.hour * 60 + start.minute
            return BundledTimetable.ROWS.flatMap { r ->
                (if (holiday) r.holiday else r.weekday).map { LocalTime.of(r.hour, it) }
            }.filter { (it.hour * 60 + it.minute) in (s - 19)..(s - 10) }.maxOrNull()
        }

        var checked = 0
        val added = mutableListOf<String>()
        listOf(
            LocalDate.of(2026, 8, 19), LocalDate.of(2026, 8, 21),
            LocalDate.of(2026, 8, 22), LocalDate.of(2026, 8, 23),
        ).forEach { date ->
            val holiday = Bundled.isHolidayTimetable(date)
            (1..51).forEach { n ->
                val advice = BundledTimetable.advise(DutyCode.parse("$n"), date)
                if (advice.depot) return@forEach // 기지 출고(v1.6.34)는 편승 창과 무관하다
                val start = MainLegs.forDay(n, holiday)?.firstOrNull()
                    ?: MainLegs.forNight(n, Bundled.comboOf(date))?.firstOrNull()
                val startT = start?.split(":")?.takeIf { it.size == 2 }
                    ?.let { p -> p[0].toIntOrNull()?.takeIf { it in 0..23 }?.let { h -> LocalTime.of(h, p[1].toInt()) } }
                    ?: return@forEach
                val old = oldWindowPick(startT, holiday)
                // 옛 창엔 없었는데 새로 생긴 건 v1.6.28~29에서 살아난 7조합뿐이어야 한다
                if (old == null) {
                    if (advice.at != null) added += "$n ${Bundled.comboOf(date)}"
                    return@forEach
                }
                // 옛 창에 열차가 있었다면 새 창도 같은 열차를 골라야 한다
                if (advice.at != null) {
                    // 고른 편승 열차는 그대로여야 한다 (알람만 v1.6.30에서 5분 앞당겨졌다)
                    assertEquals("$n / $date 권장시각이 바뀌었다", old.minusMinutes(5), advice.at)
                    checked++
                }
            }
        }
        assertTrue("검사 표본이 비었다 — 테스트가 무의미해졌다", checked > 50)
        assertEquals(
            "새로 생긴 알람이 알려진 7조합과 다르다",
            listOf("37 PP", "42 PP", "37 PH", "42 PH", "50 HH", "46 HP", "50 HP").sorted(),
            added.sorted(),
        )
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
     * **본선 퇴근시각([Bundled.TimeRow.signOff]) = [MainLegs] 후반종료** — 두 벌로 나뉜 값이
     * 갈리지 않게 잠근다.
     *
     * v1.6.31이 사용자 실근무 확인으로 `MainLegs`의 평일 4·7 후반만 바로잡고 `TimeRow`는
     * 그대로 둬서, 앱이 같은 값을 두 군데에 다르게 들고 있었다(4 = 17:16/17:21, 7 = 17:21/17:16).
     * v1.6.33에서 행로표 파일럿 실판독으로 `TimeRow`를 맞추고 이 잠금을 건다.
     * 127건 전부 성립하므로 예외 목록은 없다 — 깨지면 한쪽만 고쳤다는 뜻이다.
     */
    @Test fun signOff_equals_mainLegs_second_half_end() {
        var checked = 0
        listOf(
            Triple(Bundled.MAIN_DAY_WEEKDAY, MainLegs.WEEKDAY, "평"),
            Triple(Bundled.MAIN_DAY_HOLIDAY, MainLegs.HOLIDAY, "휴"),
        ).forEach { (times, legs, tag) ->
            times.forEach { (n, row) ->
                assertEquals("주간$n($tag) 퇴근", legs.getValue(n)[3], row.signOff)
                checked++
            }
        }
        Bundled.MAIN_NIGHT.forEach { (n, byCombo) ->
            byCombo.forEach { (combo, onOff) ->
                val leg = MainLegs.forNight(n, combo) ?: return@forEach // 33~35 휴휴 = 운휴대기
                assertEquals("야간$n(${combo.label}) 퇴근", leg[3], onOff.second)
                checked++
            }
        }
        assertEquals("검사한 다이아 수", 127, checked)
    }

    /**
     * ★ v1.6.54 — **평일 4·7 다이아 개정본**(2026-08-23 배포 `4 7개정 행로표` PDF)을 통째로 잠근다.
     *
     * 개정으로 **후반이 서로 맞바뀌었다.** v1.6.31~33의 옛 값(4 = 14:01/17:16 · 7 = 15:36/17:21)은
     * 개정 **전** 기준이라 그 시절 주석·기억을 근거로 되돌리면 안 된다. 전반은 개정 전후가 같다.
     *
     * PDF 실판독(2쪽 4번 교번 18-49 / 3쪽 7번 교번 24-48):
     *  · 4번 전반 편승 7:15 → 7:30 발 2057·2121·2605·5922 ▽10:45 /
     *        후반 편승 15:21 → 신도림 15:36 발 2297·2335 → 17:06 → 퇴근 17:21. 계 10:56.
     *  · 7번 전반 편승 7:26 → 7:41 발 2060·2114·6928 ▽9:19 /
     *        후반 편승 13:46 → 신도림 14:01 발 2261·2295·2333 → 17:01 → 퇴근 17:16. 계 10:41.
     *
     * 세 파일이 같이 움직여야 한다 — 하나만 고치면 앱이 같은 값을 두 벌로 들고 있게 된다
     * (`signOff_equals_mainLegs_second_half_end`·`mainSecondLeg_starts_are_handover_points`가
     *  나머지 짝을 각각 전수로 잠근다).
     */
    @Test fun weekday_4_and_7_match_revised_route_sheet_2026_08_23() {
        assertEquals(listOf("7:30", "10:45", "15:36", "17:21"), MainLegs.WEEKDAY.getValue(4))
        assertEquals(listOf("7:41", "9:19", "14:01", "17:16"), MainLegs.WEEKDAY.getValue(7))

        RouteTable.forMainDay(4, false)!!.let {
            assertEquals("2057·2121·2605·5922", it.firstHalf)
            assertEquals("2297·2335", it.secondHalf)
            assertEquals("10:56", it.totalWorkTime)
        }
        RouteTable.forMainDay(7, false)!!.let {
            assertEquals("2060·2114·6928", it.firstHalf)
            assertEquals("2261·2295·2333", it.secondHalf)
            assertEquals("10:41", it.totalWorkTime)
        }

        // 출근은 안 바뀐다(전반이 그대로라 전반시작 −45분 = 신도림 교대)
        assertEquals("6:45" to "17:21", Bundled.MAIN_DAY_WEEKDAY.getValue(4).let { it.signOn to it.signOff })
        assertEquals("6:56" to "17:16", Bundled.MAIN_DAY_WEEKDAY.getValue(7).let { it.signOn to it.signOff })

        // 후반 편승 알람도 따라 바뀐다 (`mainSecondLeg_alarm_table`의 손계산과 같은 값)
        val weekday = LocalDate.of(2026, 8, 18) // 화
        assertEquals(LocalTime.of(15, 16), BundledTimetable.advise(DutyCode.parse("4"), weekday, true).at)
        assertEquals(LocalTime.of(13, 46), BundledTimetable.advise(DutyCode.parse("7"), weekday, true).at)

        // 개정 대상은 4·7뿐 — 이웃 다이아가 딸려 움직이지 않았는지 확인한다
        assertEquals(listOf("7:18", "10:33", "14:06", "15:51"), MainLegs.WEEKDAY.getValue(3))
        assertEquals(listOf("7:47", "10:55", "15:20", "17:05"), MainLegs.WEEKDAY.getValue(5))
        assertEquals(listOf("7:52", "11:00", "15:26", "17:26"), MainLegs.WEEKDAY.getValue(6))
        assertEquals(listOf("7:57", "11:05", "15:41", "17:25"), MainLegs.WEEKDAY.getValue(8))
    }

    /**
     * **`signOnAt`의 24시+ 표기 규칙을 잠근다** (v1.6.33에 브리핑·위젯 2벌을 한 벌로 통합).
     *
     * 출근 브리핑 예약과 위젯 부제·경계 갱신이 같은 함수를 쓴다. `LocalTime.parse`로 바꾸는
     * 순간 야간 `"25:20"`에서 예외가 나고 브리핑이 통째로 안 걸린다.
     */
    @Test fun signOnAt_handles_24plus_night_notation() {
        val d = LocalDate.of(2026, 8, 20)
        assertEquals(d.atTime(7, 47), signOnAt(d, "7:47"))
        assertEquals("25:20 = 익일 01:20", d.plusDays(1).atTime(1, 20), signOnAt(d, "25:20"))
        assertEquals("24:00 = 익일 자정", d.plusDays(1).atStartOfDay(), signOnAt(d, "24:00"))
        assertNull(signOnAt(d, null))
        assertNull("출근시각이 없는 날은 예약하지 않는다", signOnAt(d, ""))
        assertNull(signOnAt(d, "휴무"))
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

    /**
     * **후반사업 편승 알람(v1.6.29)의 근거를 잠근다.**
     *
     * ① 지선 주간 다이아의 **후반시작시각은 전부 다른 지선 다이아의 사업 종료시각과 정확히 맞물린다.**
     *    (지1 후반 12:51 = 지6 전반 종료 12:51 / 지3 후반 14:51 = 지1 후반 종료 14:51 …)
     *    지선은 양천구청에서 인수인계하므로 그 맞물림 지점이 곧 **양천구청**이고,
     *    그래서 전반과 같은 "5분 전 도착" 규칙을 후반에도 쓸 수 있다. 깨지면 규칙 근거가 사라진다.
     * ② 본선 후반은 v1.6.30에서 신도림 교대인 다이아만 켜졌다
     *    (근거는 [mainSecondLeg_starts_are_handover_points] · [mainSecondLeg_alarm_table]).
     * ③ 야간은 후반이 익일이라 이 날짜 알람으로 못 건다.
     */
    @Test fun secondLeg_alarm_only_for_branch_day_duties() {
        listOf(Bundled.BRANCH_WEEKDAY, Bundled.BRANCH_HOLIDAY).forEach { table ->
            // 그 표 안의 모든 사업 종료시각 = 인수인계 지점 후보
            val ends = table.values.flatMap { r ->
                listOfNotNull(
                    r.firstLeg?.split('#', '-')?.getOrNull(1),
                    r.secondLeg?.split('#', '-')?.getOrNull(1),
                )
            }.map { it.trim('▼') }.toSet()
            table.forEach { (dia, r) ->
                if (r.overnight || r.secondLeg == null) return@forEach // 야간·대기는 제외
                val start = r.secondLeg!!.split('#', '-')[0]
                assertTrue("$dia 후반시작 $start 이 인수인계 지점이 아니다", start in ends)
            }
        }

        val weekday = LocalDate.of(2026, 8, 18) // 화 = 평일
        // 지선 주간 — 지1 후반 12:51 → 12:46 도착
        val branch = BundledTimetable.advise(DutyCode.parse("지1"), weekday, second = true)
        assertEquals(LocalTime.of(12, 46), branch.at)
        assertTrue(branch.text, branch.text.contains("양천구청역 12:46 도착"))

        // 본선 주간 12번 — v1.6.30에서 켜졌다(신도림 16:50 출발 → 편승 16:40 → 알람 16:35)
        val main = BundledTimetable.advise(DutyCode.parse("12"), weekday, second = true)
        assertEquals(LocalTime.of(16, 35), main.at)
        assertTrue(main.text, main.text.contains("신도림 16:50 출발"))

        // 야간(지선·본선 모두) — 후반이 익일이라 못 건다
        assertEquals(null, BundledTimetable.advise(DutyCode.parse("지10"), weekday, second = true).at)
        assertEquals(null, BundledTimetable.advise(DutyCode.parse("38"), weekday, second = true).at)

        // 전반은 종전 그대로 (후반 인자를 붙여도 기본값이 안 바뀐 것을 확인)
        assertEquals(LocalTime.of(8, 8), BundledTimetable.advise(DutyCode.parse("지1"), weekday).at)
        assertEquals(LocalTime.of(7, 48), BundledTimetable.advise(DutyCode.parse("12"), weekday).at)
    }

    /**
     * **본선 후반 = 신도림 교대라는 근거를 전수로 잠근다** (v1.6.30 — 깨지면 틀린 시각을 준다).
     *
     * 후반 첫 열번을 **마지막으로 굴리는 다른 다이아**를 찾아 그 종료시각과 후반시작을 견주면,
     * 두 값이 우연이 아닌 고정 간격으로 맞물린다. 지선 후반을 v1.6.29에서 확정한 방법과 같다.
     *
     * | 맞물리는 상대 | 평일 | 휴일 |
     * |---|---|---|
     * | 앞 다이아의 **전반종료**(신도림 교대 시각) | Δ0 | Δ+15 |
     * | 앞 다이아의 **후반종료**(=퇴근, 교대 후 편승 15분) | Δ+15 | Δ+30 |
     *
     * 휴일만 15분씩 밀려 있는 것이 **휴일 표의 후반시작이 양천구청 편승 출발시각**이라는
     * 관측(행로표 `hol_2`·`hol_16`·`hol_25` 스캔)과 정확히 맞아떨어진다.
     */
    @Test fun mainSecondLeg_starts_are_handover_points() {
        fun mins(t: String) = t.split(":").let { it[0].toInt() * 60 + it[1].toInt() }
        fun head(s: String) = Regex("^(\\d{4})").find(s.split('·').first().trim())?.value
        fun tail(s: String) = Regex("^(\\d{4})").find(s.split('·').last().trim())?.value

        // v1.6.30에서 뺐던 평일 4·7을 v1.6.31에서 다시 넣는다 — 시각표를 행로표 쪽으로 바로잡아
        // 둘 다 맞물리고, 앞 다이아가 4·7이라 빠져 있던 평일 19·20도 같이 살아난다.
        var chained = 0
        listOf(false to 0 to 15, true to 15 to 30).forEach { spec ->
            val (pair, toSecondEnd) = spec
            val (hol, toFirstEnd) = pair
            val legs = if (hol) MainLegs.HOLIDAY else MainLegs.WEEKDAY
            legs.keys.sorted().forEach { n ->
                val me = RouteTable.forMainDay(n, hol)!!
                val t = head(me.secondHalf) ?: return@forEach // 텍스트로 시작 = 신도림 아님
                var hit = false
                legs.keys.forEach other@{ o ->
                    if (o == n) return@other
                    val ot = RouteTable.forMainDay(o, hol)!!
                    val oLegs = legs.getValue(o)
                    listOf(tail(ot.firstHalf) to (oLegs[1] to toFirstEnd),
                           tail(ot.secondHalf) to (oLegs[3] to toSecondEnd)).forEach { (last, exp) ->
                        if (last != t) return@forEach
                        val (endAt, delta) = exp
                        val d = mins(endAt) - mins(legs.getValue(n)[2])
                        if (d < -60 || d > 60) return@forEach // 같은 열번이 하루 두 번 도는 우연은 버린다
                        assertEquals(
                            "${if (hol) "휴일" else "평일"}$n 후반 $t 이 $o 번과 어긋난다",
                            delta.toLong(), d.toLong(),
                        )
                        hit = true
                    }
                }
                if (hit) chained++
            }
        }
        // 4·7 교정 후 맞물리는 다이아 수 (v1.6.31에서 4·7·19·20이 더해졌다).
        // 맞물리지 않는 나머지는 앞 승무원이 군자 소속이라 우리 표에 아예 없는 경우다.
        assertEquals("맞물리는 다이아 수", 41, chained)
    }

    /**
     * **본선 후반 알람 전수표를 손계산으로 고정한다** (v1.6.30).
     *
     * 편승 42건 / 출고 9건 / 제외 3건 (v1.6.31에서 평일 4·7이 제외 → 켜짐,
     * v1.6.34에서 제외 12건 중 출고 9건이 [depotAlarmIsFiftyMinutesBeforeRollout]로 옮겨 갔다).
     * **편승 42건은 그대로다** — 이 테스트가 그 불변을 잠근다. 제외는 사유가 구체적으로 보여야 한다.
     * 표본 시각은 행로표 스캔으로 눈으로도 확인한 것들이다
     * (`wd_12` 16:50 · `wd_29` 19:10 신도림 / `hol_2` 13:48 · `hol_16` 16:09 · `hol_25` 18:14).
     */
    @Test fun mainSecondLeg_alarm_table() {
        val weekday = LocalDate.of(2026, 8, 18) // 화
        val holiday = LocalDate.of(2026, 8, 23) // 일
        assertTrue(Bundled.isHolidayTimetable(holiday))

        fun at(n: Int, date: LocalDate) = BundledTimetable.advise(DutyCode.parse("$n"), date, second = true)

        // ── 손계산 대조 (신도림 출발 → 창 10~21분 전 마지막 편 → 그 5분 전) ──
        // 평일 12: 신도림 16:50 → 16:29~16:40 → 16:40 → 알람 16:35
        assertEquals(LocalTime.of(16, 35), at(12, weekday).at)
        // 평일 29: 신도림 19:10 → 18:49~19:00 → 19:00 → 알람 18:55
        assertEquals(LocalTime.of(18, 55), at(29, weekday).at)
        // 평일 23: 신도림 17:40 → 17:19~17:30 → 17:30 → 알람 17:25
        assertEquals(LocalTime.of(17, 25), at(23, weekday).at)
        // ── 평일 4·7은 2026-08-23 `4 7개정 행로표`로 후반이 서로 맞바뀌었다 (v1.6.54) ──
        // v1.6.31~33은 4 = 13:46 · 7 = 15:16이었다. 개정 전 기준이므로 되돌리지 말 것.
        // 평일 4: 신도림 15:36 → 15:15~15:26 → 15:21 → 알람 15:16
        assertEquals(LocalTime.of(15, 16), at(4, weekday).at)
        assertTrue(at(4, weekday).text, at(4, weekday).text.contains("신도림 15:36 출발"))
        // 평일 7: 신도림 14:01 → 13:40~13:51 → 13:51 → 알람 13:46
        assertEquals(LocalTime.of(13, 46), at(7, weekday).at)
        assertTrue(at(7, weekday).text, at(7, weekday).text.contains("신도림 14:01 출발"))
        // 휴일 2: 표 13:33 + 15 = 신도림 13:48 → 13:27~13:38 → 13:31 → 알람 13:26
        assertEquals(LocalTime.of(13, 26), at(2, holiday).at)
        assertTrue(at(2, holiday).text, at(2, holiday).text.contains("신도림 13:48 출발"))
        // 휴일 16: 15:54 + 15 = 16:09 → 15:48~15:59 → 15:51 → 알람 15:46
        assertEquals(LocalTime.of(15, 46), at(16, holiday).at)
        // 휴일 25: 17:59 + 15 = 18:14 → 17:53~18:04 → 18:01 → 알람 17:56
        assertEquals(LocalTime.of(17, 56), at(25, holiday).at)

        // ── 제외 다이아: 알람 없음 + 구체적 사유 ──
        // v1.6.34에서 **출고 9건이 여기서 빠져 나갔다**(→ [depotAlarmIsFiftyMinutesBeforeRollout]).
        // 남은 셋은 편승·교대라 여전히 못 건다.
        listOf(
            Triple(1, weekday, "군자기지 편승"),
            Triple(1, holiday, "군자기지"), Triple(21, holiday, "성수 교대"),
        ).forEach { (n, date, why) ->
            val a = at(n, date)
            assertEquals("$n / $date 는 알람이 없어야", null, a.at)
            assertTrue("$n / $date 사유: ${a.text}", a.text.contains(why))
        }

        // ── 켜진 수 / 제외 수 ──
        // **편승 42건은 v1.6.34에서도 한 건도 안 바뀐다** — 출고는 `depot` 표시로 따로 센다.
        fun count(depot: Boolean) =
            MainLegs.WEEKDAY.keys.count { at(it, weekday).let { a -> a.at != null && a.depot == depot } } +
                MainLegs.HOLIDAY.keys.count { at(it, holiday).let { a -> a.at != null && a.depot == depot } }
        assertEquals("후반 편승 알람이 켜진 본선 주간 다이아 수", 42, count(depot = false))
        assertEquals("후반 기지 출고 알람 다이아 수 (v1.6.34)", 9, count(depot = true))
        assertEquals("본선 주간 조합 수", 54, MainLegs.WEEKDAY.size + MainLegs.HOLIDAY.size)

        // 야간 후반은 그대로 익일이라 없다
        MainLegs.NIGHT.keys.forEach { n ->
            assertEquals("야간 $n", null, at(n, weekday).at)
        }
    }

    /**
     * **기지 출고 알람 = 출고시각 50분 전** (v1.6.34 사용자 확정) — 대상 전건을 손계산으로 잠근다.
     *
     * v1.6.27~33은 출고에 알람이 없었다("알람 없음 + 사유 표시"). 사용자가 다시 필요하다며
     * **50분 전**으로 확정했다. 원문 예시: *"평일 9번 신정기지 출고 8:02 → 알람 7:12"*.
     *
     * | 구간 | 대상 | 기지 |
     * |---|---|---|
     * | 전반 | 11건 (평일 2·5·6·8·9 / 휴일 4·8·12·13·14·15) | 전부 신정기지(첫 열번 5xxx·6xxx) |
     * | 후반 | 9건 (평일 6·13·14·16·17·21 / 휴일 3·5·20) | 신정 6 · 군자 3 |
     *
     * 출고가 **아닌** 사유로 빠진 셋(평일 1 군자기지 편승 / 휴일 1 군자→성수 편승 / 휴일 21 성수 교대)은
     * 대상이 아니다 — 편승·교대는 어디서 몇 시에 열차를 잡는지 표에 없다.
     */
    @Test fun depotAlarmIsFiftyMinutesBeforeRollout() {
        val weekday = LocalDate.of(2026, 8, 18) // 화
        val holiday = LocalDate.of(2026, 8, 23) // 일
        fun adv(n: Int, date: LocalDate, second: Boolean = false) =
            BundledTimetable.advise(DutyCode.parse("$n"), date, second)

        // ── 전반: 출고시각(= MainLegs 전반시작) − 50분. 손계산 표본 ──
        listOf(
            Triple(9, weekday, "8:02" to LocalTime.of(7, 12)),   // 사용자 확정 예시
            Triple(2, weekday, "7:23" to LocalTime.of(6, 33)),
            Triple(5, weekday, "7:47" to LocalTime.of(6, 57)),
            Triple(4, holiday, "8:30" to LocalTime.of(7, 40)),
            Triple(15, holiday, "10:39" to LocalTime.of(9, 49)),
        ).forEach { (n, date, spec) ->
            val (out, alarm) = spec
            val a = adv(n, date)
            assertEquals("$n / $date", alarm, a.at)
            assertTrue("$n / $date", a.depot)
            assertEquals("신정기지 $out 출고 · 알림 ${alarm.hour}:%02d".format(alarm.minute), a.text)
        }

        // ── 후반: 출고시각(= MainLegs 후반시작, 휴일 15분 보정 없음) − 50분 ──
        listOf(
            Triple(6, weekday, Triple("신정기지", "15:26", LocalTime.of(14, 36))),
            Triple(13, weekday, Triple("군자기지", "16:47", LocalTime.of(15, 57))),
            Triple(14, weekday, Triple("군자기지", "17:54", LocalTime.of(17, 4))),
            Triple(21, weekday, Triple("신정기지", "18:07", LocalTime.of(17, 17))),
            Triple(3, holiday, Triple("군자기지", "15:59", LocalTime.of(15, 9))),
            Triple(5, holiday, Triple("신정기지", "14:03", LocalTime.of(13, 13))),
            Triple(20, holiday, Triple("신정기지", "16:47", LocalTime.of(15, 57))),
        ).forEach { (n, date, spec) ->
            val (base, out, alarm) = spec
            val a = adv(n, date, second = true)
            assertEquals("$n / $date 후반", alarm, a.at)
            assertTrue("$n / $date 후반", a.depot)
            assertEquals("$base $out 출고 · 알림 ${alarm.hour}:%02d".format(alarm.minute), a.text)
        }

        // ── 대상 전수: 전반 11건 · 후반 9건, 그 밖엔 한 건도 없어야 ──
        val first = mutableListOf<String>()
        val second = mutableListOf<String>()
        listOf(weekday to MainLegs.WEEKDAY, holiday to MainLegs.HOLIDAY).forEach { (date, legs) ->
            val tag = if (date == holiday) "휴" else "평"
            legs.keys.sorted().forEach { n ->
                if (adv(n, date).depot) first += "$tag$n"
                if (adv(n, date, second = true).depot) second += "$tag$n"
            }
        }
        assertEquals(listOf("평2", "평5", "평6", "평8", "평9", "휴4", "휴8", "휴12", "휴13", "휴14", "휴15"), first)
        assertEquals(listOf("평6", "평13", "평14", "평16", "평17", "평21", "휴3", "휴5", "휴20"), second)

        // 야간은 전건 간격 45분(신도림 교대)이라 전반 출고가 없고, 후반은 익일이라 아예 안 건다
        MainLegs.NIGHT.keys.forEach { n ->
            listOf(weekday, holiday).forEach { d ->
                assertTrue("야간 $n / $d 전반", !adv(n, d).depot)
                assertTrue("야간 $n / $d 후반", !adv(n, d, second = true).depot)
            }
        }

        // 출고가 아닌 사유 셋은 그대로 알람 없음
        assertEquals(null, adv(1, weekday, second = true).at)
        assertEquals(null, adv(1, holiday, second = true).at)
        assertEquals(null, adv(21, holiday, second = true).at)

        // 지선·대기는 출고 표시가 붙지 않는다
        assertTrue(!BundledTimetable.advise(DutyCode.parse("지1"), weekday).depot)
        assertTrue(!BundledTimetable.advise(DutyCode.parse("대3"), weekday).depot)
    }

    /**
     * **알람은 편승 열차 출발 5분 전** (v1.6.30 사용자 확정).
     *
     * 사용자 원문: *"34다이아 신도림 17:44분 출발 근무면 양천구청역에서 17시30분 편승 맞으니까
     * 5분전 17시25분 알람을 예약해줘야지"*.
     * 지선은 이미 "전반시작 5분 전 도착"이라 **두 번 빼지 않는다**는 것도 같이 잠근다.
     */
    @Test fun alarmIsFiveMinutesBeforeTheDeadheadTrain() {
        val weekday = LocalDate.of(2026, 8, 18)
        // 34번(야간·평평) 전반시작 = 신도림 17:44 → 창 17:23~17:34 → 편승 17:30 → 알람 17:25
        val a = BundledTimetable.advise(DutyCode.parse("34"), weekday)
        assertEquals(LocalTime.of(17, 25), a.at)
        assertTrue(a.text, a.text.contains("양천구청역 17:30 편승"))
        assertTrue(a.text, a.text.contains("신도림 17:44 출발"))
        assertTrue(a.text, a.text.contains("알림 17:25"))

        // 지선은 그대로 5분 — 지1 전반 8:13 → 8:08 (8:03이 아니다)
        assertEquals(LocalTime.of(8, 8), BundledTimetable.advise(DutyCode.parse("지1"), weekday).at)
        assertEquals(LocalTime.of(12, 46), BundledTimetable.advise(DutyCode.parse("지1"), weekday, true).at)

        // 편승 계열은 전 다이아에서 "알람 = 문구 속 편승시각 − 5분"이 성립해야 한다
        var seen = 0
        listOf(LocalDate.of(2026, 8, 19), LocalDate.of(2026, 8, 22)).forEach { d ->
            (1..51).forEach { n ->
                listOf(false, true).forEach { second ->
                    val adv = BundledTimetable.advise(DutyCode.parse("$n"), d, second)
                    val board = Regex("양천구청역 (\\d+):(\\d\\d) 편승").find(adv.text) ?: return@forEach
                    val b = LocalTime.of(board.groupValues[1].toInt(), board.groupValues[2].toInt())
                    assertEquals("$n/$d/$second", b.minusMinutes(5), adv.at)
                    seen++
                }
            }
        }
        assertTrue("표본이 비었다", seen > 40)
    }

    /**
     * v1.6.35 근무선택 그리드 번호순 정렬 — **표시 순서만 바뀌고 근무표는 한 칸도 안 움직인다**를 잠근다.
     * 정렬된 칸이 원래 시퀀스 인덱스를 안 들고 다니면 `Pattern.offsetFor`가 딴 offset을 뱉어
     * 사용자 전원의 근무표가 통째로 어긋난다 — 이 앱에서 가장 비싼 사고다.
     */
    @Test fun dutyGridOrderIsDisplayOnly() {
        val pick = LocalDate.of(2026, 8, 20)          // 사용자가 근무선택을 누른 날
        val month = (1..31).map { LocalDate.of(2026, 8, it) }
        var cells = 0

        listOf(Bundled.MAIN_PATTERN, Bundled.BRANCH_PATTERN).forEach { p ->
            val order = DutyCode.displayOrder(p.sequence)
            val shown = order.toSet()
            // 겹친 칸 없이, 빠진 칸은 **익일 비번뿐** (v1.6.36에서 그리드에서 뺐다)
            assertEquals(p.name, order.size, shown.size)
            assertEquals(
                p.name,
                p.sequence.indices.filter { DutyCode.parse(p.sequence[it]).type == DutyType.POST_NIGHT },
                (p.sequence.indices - shown).sorted(),
            )
            assertTrue(p.name, order.none { DutyCode.parse(p.sequence[it]).display == "~" })

            // ★ 전수 대조는 **숨긴 칸까지 137칸 전부** 돈다 — 안 보인다고 offset이 달라지면 안 된다
            p.sequence.indices.forEach { i ->
                cells++
                val dia = p.sequence[i]
                // 정렬 전 그리드에서 같은 다이아가 있던 칸. 값이 i와 같아야 = 중복 다이아가 없다는 뜻
                val before = p.sequence.indexOf(dia)
                assertEquals("$dia 중복", before, i)

                val offBefore = p.offsetFor(pick, before)   // 정렬 전에 그 다이아를 골랐다면
                val offAfter = p.offsetFor(pick, i)         // 정렬·숨김 후 그 칸을 골랐을 때
                assertEquals("$dia offset", offBefore, offAfter)
                // 고른 날 근무가 실제로 그 다이아이고, 8월 전체 근무표가 정렬 전과 완전히 같다
                assertEquals("$dia", dia, p.dutyOn(pick, offAfter).raw)
                month.forEach { d ->
                    assertEquals("$dia $d", p.dutyOn(d, offBefore).raw, p.dutyOn(d, offAfter).raw)
                }
            }

            // 숨긴 비번의 offset은 **사라지지 않는다** — 전날 칸에서 짝 야간을 고르면 완전히 같다.
            // "비번을 골라야만 되는 사람"이 없다는 근거이자, 숨김을 되돌릴지 판단하는 기준.
            (p.sequence.indices - shown).forEach { i ->
                val night = p.sequence[Math.floorMod(i - 1, p.length)]
                assertEquals("${p.sequence[i]} 짝", p.sequence[i], "${night}비")
                val viaNight = p.offsetFor(pick.minusDays(1), p.sequence.indexOf(night))
                assertEquals("${p.sequence[i]} 우회", p.offsetFor(pick, i), viaNight)
                assertEquals("${p.sequence[i]} 우회결과", p.sequence[i], p.dutyOn(pick, viaNight).raw)
            }
        }
        assertEquals(137, cells)                      // 본선 108 + 지선 29

        // 사용자 요구 순서 그대로인지 — 주간 1~29 → 야간 33~51 → 대기 → 운휴 (비번 없음)
        val main = DutyCode.displayOrder(Bundled.MAIN_PATTERN.sequence).map { Bundled.MAIN_PATTERN.sequence[it] }
        assertEquals(86, main.size)                   // 108 − 비번 22
        assertEquals((1..29).map { "$it" }, main.take(29))
        assertEquals((33..51).map { "$it" }, main.subList(29, 48))
        assertEquals(
            listOf("대1", "대2", "대3", "대4", "대5", "대6", "대11", "대12", "대13"),
            main.subList(48, 57),
        )
        assertEquals((1..29).map { "휴$it" }, main.drop(57))

        // 지선: 지1~지8 → 지10~지14 → 지대 → 지휴
        val br = DutyCode.displayOrder(Bundled.BRANCH_PATTERN.sequence).map { Bundled.BRANCH_PATTERN.sequence[it] }
        assertEquals(23, br.size)                     // 29 − 비번 6
        assertEquals((1..8).map { "지$it" }, br.take(8))
        assertEquals((10..14).map { "지$it" }, br.subList(8, 13))
        assertEquals(listOf("지대1", "지대2", "지대11"), br.subList(13, 16))
        assertEquals((1..7).map { "지휴$it" }, br.drop(16))
    }

    /**
     * v1.6.36 ④ — 휴휴 야간 33·34·35(운휴대기)의 기본 출근시각.
     * 사용자 확정: *"근무하면 보통 야간, 미지정이면 대기 출근."* → 대기 대11~13과 같은 17/18/19시.
     *
     * 이 조합만 [MainLegs]에 사업시각이 없어서(`forNight` = null) 상세시트가 행로표
     * (`hh_33`~`hh_35`, "운휴대기"라고만 적힌 스캔)만 띄우고 **출근시각을 한 줄도 안 보여줬다.**
     * 시각 자체는 이미 [Bundled.MAIN_NIGHT]에 있었으므로 달력·위젯·브리핑은 종전에도 나왔다.
     * 여기서 잠그는 것: 시각값 3건 + "사업시각 없음"(=상세시트 출근·종료 분기 조건) + 알람 제외.
     */
    @Test fun standbyOnly_nights_have_default_signOn() {
        val hh = LocalDate.of(2026, 8, 22)                   // 토 → 일 = 휴휴
        assertEquals(NightCombo.HH, Bundled.comboOf(hh))
        mapOf(33 to "17:00", 34 to "18:00", 35 to "19:00").forEach { (n, on) ->
            val c = DutyCode.parse("$n")
            assertEquals("$n", on, Bundled.signOn(c, hh))    // 달력·위젯·브리핑이 쓰는 값
            assertNotNull("$n", signOnAt(hh, Bundled.signOn(c, hh)))
            // 사업시각·행로표 열번이 없다 = 상세시트가 "출근/종료"만 적는 조건
            assertNull("$n", MainLegs.forNight(n, NightCombo.HH))
            assertTrue("$n", RouteTable.isStandbyOnly(n, NightCombo.HH))
            // 대기 성격이라 편승·출고 알람 대상이 아니다 (v1.6.30 판단 유지)
            assertNull("$n", BundledTimetable.advise(c, hh).at)
        }
        // 평일·휴평 조합은 종전 그대로 사업시각이 있다 — 이 분기가 휴휴에만 걸린다는 근거
        assertNotNull(MainLegs.forNight(33, NightCombo.PP))
        assertNotNull(MainLegs.forNight(35, NightCombo.HP))
        // 36 이상은 휴휴에도 사업시각이 있다
        assertNotNull(MainLegs.forNight(36, NightCombo.HH))
    }

    /**
     * 근무변경 시트 목록(v1.6.42 ④ — 휴가 3묶음 아코디언을 걷어내고 22종 평면 격자로).
     * **저장값이 바뀌지 않는 것**이 요점이다: 화면에 뜨는 칩이 곧 저장되는 문자열이고,
     * 묶음 이름(`기타휴가`)처럼 근무코드가 아닌 항목이 목록에 섞여 있으면 안 된다.
     */
    @Test fun changeOptions_are_all_real_codes_and_unique() {
        val opts = DutyCode.CHANGE_OPTIONS
        assertEquals(21, opts.size)                       // v1.6.47 ⑤ `비번` 제거로 22 → 21
        assertEquals(opts.size, opts.toSet().size)        // 중복 없음
        assertTrue(DutyCode.ETC_GROUP !in opts)           // 묶음 이름은 코드가 아니다 → 목록에도 없다
        // 고르면 그 문자열이 그대로 저장·재파싱된다
        opts.forEach { assertEquals(it, it, DutyCode.parse(it).raw) }
        // 휴가 계열은 전부 옅은 붉은색(REST, v1.6.23) — 야간 보라로 새지 않는다
        listOf("연차", "촉연", "가연차", "대휴", "보상", "장휴", "학습", "만휴",
               "청휴", "돌봄휴가", "동행휴가", "병가", "공가")
            .forEach { assertEquals(it, DutyType.REST, DutyCode.parse(it).colorType) }
    }

    /**
     * v1.6.47 ④ — `기타휴가` 묶음. **1단계 목록은 파생이라 항목을 잃을 수 없다**는 것이 요점이다
     * (v1.6.40에서 목록을 두 벌로 두면 한쪽만 고치는 사고가 난다고 못 박은 그 규칙).
     * 묶음 이름 자체는 저장될 수 없고, 하위 9종은 전부 진짜 근무코드다.
     */
    @Test fun etcLeaveGroup_folds_nine_codes_without_losing_any() {
        val top = DutyCode.CHANGE_TOP
        assertEquals(13, top.size)                              // 4열 4줄, 스크롤 없이 한 화면
        assertEquals(DutyCode.ETC_GROUP, top.last())            // 묶음 칩은 맨 끝 한 칸
        assertEquals(top.size, top.toSet().size)
        // 접힌 9종은 1단계에 없고, 1단계 + 하위 = 고를 수 있는 전부 (잃은 항목 0)
        assertTrue(DutyCode.CHANGE_ETC.none { it in top })
        assertEquals(
            DutyCode.CHANGE_OPTIONS.toSet(),
            (top - DutyCode.ETC_GROUP).toSet() + DutyCode.CHANGE_ETC,
        )
        // 하위는 전부 실제 근무코드 = 고르면 그대로 저장된다. 묶음 이름은 코드가 아니다
        DutyCode.CHANGE_ETC.forEach {
            assertTrue(it, it in DutyCode.CHANGE_OPTIONS)
            assertEquals(it, DutyType.REST, DutyCode.parse(it).colorType)
        }
        assertTrue(DutyCode.ETC_GROUP !in DutyCode.CHANGE_OPTIONS)
        // 순서 보존: 1단계는 CHANGE_OPTIONS 순서 그대로다
        assertEquals(DutyCode.CHANGE_OPTIONS.filter { it in top }, top - DutyCode.ETC_GROUP)
    }

    /**
     * v1.6.47 ⑤ — `비번` 제거(사용자: *"근무변경에 비번은 없어도 될꺼같은데?"*).
     * **고를 수만 없고 파싱·표시는 그대로**여야 한다. 야간 다이아 다음날 자동으로 붙는 값이라
     * 여기가 깨지면 밤샘 근무 익일 칸이 통째로 사라진다. `작연차`(v1.6.41)와 같은 처리다.
     */
    @Test fun retiredCode_bibeon_is_unpickable_but_still_parses() {
        assertTrue("비번" !in DutyCode.CHANGE_OPTIONS)      // 시트 목록의 유일한 출처
        assertTrue("비번" !in DutyCode.CHANGE_TOP)
        assertTrue("비번" !in DutyCode.CHANGE_ETC)
        assertEquals(DutyType.POST_NIGHT, DutyCode.parse("비번").type)
        assertEquals(DutyType.POST_NIGHT, DutyCode.parse("비번").colorType)  // 보라 유지(v1.6.21)
        assertEquals("~", DutyCode.parse("비번").display)
        // 자동 비번의 진짜 경로(교번 순환의 `44비`)는 애초에 이 목록과 무관하다
        assertEquals(DutyType.POST_NIGHT, DutyCode.parse("44비").type)
        assertTrue(DutyCode.parse("44").isOvernight)
    }

    /**
     * v1.6.47 ③ — `지근`(**지정근무**)도 다이아를 고를 수 있다.
     * 사용자: *"근무변경에서 지근은 다이아 선택이 없네?"* / *"지근은 지선이 아니고 지정근무야~
     * 어떤 근무가 선택될지 몰라!"* → 지선 전용이 아니라 충당 계열과 **같은** 소속 → 다이아 흐름.
     *
     * ⚠ 핵심은 **색이 노랑으로 새지 않는 것**이다. `colorType`이 `fill != null`로 갈렸다면
     * 다이아를 고른 순간 지근이 대기 노랑으로 돌변했다 — 노랑 강제는 충당 3종에만 걸린다.
     */
    @Test fun jigeun_takes_a_dia_but_never_turns_standby_yellow() {
        assertTrue("지근" in DutyCode.FILL_OPTIONS)

        val branch = DutyCode.parse("지근 지2")
        assertEquals("지근", branch.fill)
        assertEquals("지2", branch.diaRaw)                  // 행로표·편승알람 조회 키
        assertEquals(DutyType.BRANCH, branch.type)
        assertEquals(DutyType.BRANCH, branch.colorType)     // ⚠ 노랑 아님
        assertEquals("지근\n지2", branch.gridLabel)
        assertTrue(branch.isBranch)

        // 본선 다이아도 그대로 — 지정근무는 어느 소속이 지정될지 모른다
        val night = DutyCode.parse("지근 34")
        assertEquals("34", night.diaRaw)
        assertEquals(DutyType.MAIN_NIGHT, night.type)
        assertEquals(DutyType.MAIN_NIGHT, night.colorType)  // 고른 다이아의 제 색
        assertTrue(night.isOvernight)                       // 익일 자동 비번이 따라온다
        assertEquals("지근\n34", night.gridLabel)
        val weekday = LocalDate.of(2026, 8, 18)
        assertEquals(
            Bundled.signOn(DutyCode.parse("34"), weekday),
            Bundled.signOn(night, weekday),
        )

        // 옛 데이터 호환: 단독 `지근`은 한 글자도 안 바뀐다
        val bare = DutyCode.parse("지근")
        assertEquals(null, bare.fill)
        assertEquals(DutyType.BRANCH, bare.type)
        assertEquals(DutyType.BRANCH, bare.colorType)
        assertEquals(bare.display, bare.gridLabel)          // 한 줄 그대로
        // 깨진 다이아가 붙어도 지근의 타입으로 떨어진다(대기 노랑으로 새지 않는다)
        assertEquals(DutyType.BRANCH, DutyCode.parse("지근 없는다이아").colorType)

        // 충당 3종은 종전대로 노랑
        listOf("충당 9", "대기충당 지3", "교체 45")
            .forEach { assertEquals(it, DutyType.STANDBY, DutyCode.parse(it).colorType) }
    }

    /**
     * `작연차` 제거(v1.6.41). **고를 수는 없지만 이미 저장된 기록은 그대로 보여야 한다.**
     * 목록에서만 빼고 파싱은 남기는 것이 이 항목의 전부라, 둘을 한 번에 잠근다.
     */
    @Test fun retiredCode_jagyeoncha_is_unpickable_but_still_parses() {
        assertTrue("작연차" !in DutyCode.CHANGE_OPTIONS)   // 시트 목록의 유일한 출처
        // 저장분 표시: 휴가색(REST) + 글자 그대로. 여기가 깨지면 옛 기록이 야간 보라로 돌변한다
        assertEquals(DutyType.REST, DutyCode.parse("작연차").type)
        assertEquals(DutyType.REST, DutyCode.parse("작연차").colorType)
        assertEquals("작연차", DutyCode.parse("작연차").display)
        assertTrue(DutyCode.parse("작연차").isRest)
    }

    /**
     * 동료 탭 격자에 나올 수 있는 **모든 라벨** 전수 조사 — `저장값 → 표시 라벨` 쌍.
     *
     * 범위: 내장 패턴 4종 시퀀스 + 근무변경으로 고를 수 있는 전부 + 퇴역값 + 충당 계열 4종 ×
     * 고를 수 있는 다이아 전부. 표시값은 동료 탭 전용 `mateLabel`(DutyMatrix.kt, private이라
     * 여기 옮겨 적는다 — **규칙이 바뀌면 양쪽을 같이 고칠 것**).
     */
    private fun mateGridLabels(): List<Pair<String, String>> {
        val raws = linkedSetOf<String>()
        Bundled.ALL_PATTERNS.forEach { raws += it.sequence }
        raws += DutyCode.CHANGE_OPTIONS
        raws += listOf("작연차", "비번")                       // 고를 수 없지만 저장돼 있을 수 있다
        val dias = Bundled.ALL_PATTERNS.flatMap { p ->
            DutyCode.displayOrder(p.sequence).map { p.sequence[it] }
        }
        DutyCode.FILL_OPTIONS.forEach { f -> dias.forEach { d -> raws += "$f $d" } }

        // DutyMatrix.MATE_SHORT 사본 — 네 글자 라벨을 두 글자로 줄여 표 전체 글자를 키운다(v1.6.52)
        val short = mapOf("돌봄휴가" to "돌봄", "동행휴가" to "동행", "대기충당" to "대기")
        return raws.map { raw ->
            val c = DutyCode.parse(raw)
            raw to when {
                c.fill != null -> "${short[c.fill] ?: c.fill}\n${c.diaRaw}"
                c.type == DutyType.BRANCH || c.type == DutyType.BRANCH_NIGHT ||
                    c.type == DutyType.BRANCH_REST -> c.raw
                else -> short[c.display] ?: c.display
            }
        }
    }

    private fun labelUnits(s: String) =
        s.split('\n').maxOf { line -> line.sumOf { if (it.code >= 0x1100) 1.0 else 0.62 } }

    /**
     * **동료 탭 격자가 한 크기로 유지되는 근거**(v1.6.51~52). `DutyMatrix.UNIFORM_UNITS = 3.24`는
     * 격자에 나올 수 있는 **모든** 라벨의 줄별 최대 폭을 전수 조사해 박은 값이고,
     * 표 전체 글자 크기가 그 하나로만 정해진다. 여기보다 넓은 라벨이 새로 생기면 그 칸만
     * 잘리거나 눌려서 **"크기가 다르다"는 지적이 다시 나온다**(v1.6.49·v1.6.50이 그렇게 거부됐다).
     *
     * 반대로 여기가 **더 작아지면** 표 전체 글자를 그만큼 키울 수 있다 — v1.6.52가 4.00 → 3.24로
     * 내려 7.36 → 9.09sp를 얻은 길이다. 값이 바뀌면 `UNIFORM_UNITS`도 같이 고칠 것.
     */
    @Test fun mateGrid_every_label_line_fits_uniform_units() {
        val widest = mateGridLabels().map { it.second }.maxByOrNull(::labelUnits)!!
        // 3.24 = `지대11`. 넘으면 DutyMatrix.UNIFORM_UNITS도 같이 고쳐야 한다
        assertEquals(widest.replace("\n", "/"), 3.24, labelUnits(widest), 0.001)
    }

    /**
     * **서로 다른 근무가 격자에서 같은 글자로 보이면 안 된다**(v1.6.52 축약의 안전장치).
     *
     * `돌봄휴가`→`돌봄`처럼 라벨을 줄이면 다른 근무와 겹칠 수 있다. 줄일 때마다 눈으로 확인하지
     * 말고 여기서 잡는다 — 근무코드가 새로 추가돼도 마찬가지다.
     *
     * 뜻이 같아 겹쳐도 되는 것만 예외로 둔다:
     *  · `~` — 비번은 어느 다이아 다음이든 같은 비번 하나다(`44비`·`지13비`·`비번` …).
     *
     * ⚠ `휴`·`휴1`~`휴28` 예외는 **v1.6.53에서 없앴다.** 지선 휴일이 `지휴5`로 오게 `mateLabel`에
     * BRANCH_REST를 더해 충돌 자체가 사라졌으므로, 예외를 남겨 두면 같은 실수가 다시 숨는다.
     * **여기에 예외를 늘리지 말 것** — 겹치면 라벨을 고치는 것이 답이다.
     */
    @Test fun mateGrid_labels_do_not_collide() {
        val benign = Regex("^~$")
        val collisions = mateGridLabels()
            .groupBy({ it.second }, { it.first })
            .filterValues { it.distinct().size > 1 }
            .filterKeys { !benign.matches(it) }
            .mapValues { it.value.distinct() }
        assertEquals(emptyMap<String, List<String>>(), collisions)

        // 줄인 세 라벨이 실제로 한 근무씩만 가리키는지 (예외 목록에 숨지 않게 따로 못 박는다)
        val byLabel = mateGridLabels().groupBy({ it.second }, { it.first })
        listOf("돌봄" to "돌봄휴가", "동행" to "동행휴가", "대기" to "대기충당").forEach { (s, raw) ->
            assertEquals(s, listOf(raw), byLabel.getValue(s).distinct())
        }
        // 충당 계열 윗줄 축약이 대기 근무(`대1`·`대2`·`대11`)와 같은 글자가 아닌 것도 확인
        assertEquals("대기\n지2", mateGridLabels().first { it.first == "대기충당 지2" }.second)
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
