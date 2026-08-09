package com.sinjeong.crewcalendar

import com.sinjeong.crewcalendar.domain.model.Bundled
import com.sinjeong.crewcalendar.domain.model.BundledRoster
import com.sinjeong.crewcalendar.domain.model.BundledStaff
import com.sinjeong.crewcalendar.domain.model.CrewGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

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
        assertEquals("비번", shift.dutyOn(LocalDate.of(2026, 8, 10), 0).display)
    }

    @Test fun shiftPattern_cycles_every_4_days() {
        val expected = listOf("비번", "휴무", "주간", "야간")
        (0..11).forEach { i ->
            assertEquals(
                expected[i % 4],
                shift.dutyOn(LocalDate.of(2026, 8, 10).plusDays(i.toLong()), 0).display,
            )
        }
    }

    @Test fun shiftTeams_are_offset_by_one_day() {
        val date = LocalDate.of(2026, 8, 10)
        (1..3).forEach { team ->
            assertEquals(
                shift.dutyOn(date.plusDays(team.toLong()), 0).display,
                shift.dutyOn(date, team).display,
            )
        }
        // 같은 날 네 조가 서로 다른 근무 = 4조2교대 성립 조건
        assertEquals(4, (0..3).map { shift.dutyOn(date, it).display }.toSet().size)
    }

    @Test fun officePattern_weekday_day_weekend_rest() {
        // 2026-08-10(월) ~ 08-14(금) 주간, 08-15(토)·08-16(일) 휴무
        (0..4).forEach {
            assertEquals("주간", office.dutyOn(LocalDate.of(2026, 8, 10).plusDays(it.toLong()), 0).display)
        }
        assertEquals("휴무", office.dutyOn(LocalDate.of(2026, 8, 16), 0).display)
    }

    @Test fun officePattern_rests_on_public_and_substitute_holidays() {
        assertEquals("휴무", office.dutyOn(LocalDate.of(2026, 8, 15), 0).display) // 광복절(토)
        assertEquals("휴무", office.dutyOn(LocalDate.of(2026, 8, 17), 0).display) // 대체휴일(월)
        assertEquals("주간", office.dutyOn(LocalDate.of(2026, 8, 18), 0).display) // 화요일 정상근무
        assertEquals("휴무", office.dutyOn(LocalDate.of(2026, 3, 2), 0).display)  // 삼일절 대체휴일(월)
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

    @Test fun no_duplicate_employee_numbers() {
        val all = BundledStaff.DRIVERS + BundledStaff.CONDUCTORS + BundledStaff.OFFICE
        assertEquals(all.size, all.map { it.second }.toSet().size)   // 사번 중복 0
    }
}
