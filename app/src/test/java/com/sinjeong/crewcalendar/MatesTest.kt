package com.sinjeong.crewcalendar

import com.sinjeong.crewcalendar.domain.model.BundledRoster
import com.sinjeong.crewcalendar.domain.model.CrewGroup
import com.sinjeong.crewcalendar.domain.model.Mate
import com.sinjeong.crewcalendar.domain.repository.RosterEntry
import com.sinjeong.crewcalendar.presentation.mates.MatesHeader
import com.sinjeong.crewcalendar.presentation.roster.mergeRoster
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 동료 탭 전체 명단 합성 규칙([mergeRoster]) — v1.6.87.
 *
 * 앱에서 자기 소속을 고른 사람이 **두 줄**로 뜨던 것을 막는다. 내장 명단([BundledRoster])은
 * 옛 소속·옛 교번이고 uid가 없어 근무변경도 안 붙는 **틀린 줄**이라, 같은 이름의 live 줄이
 * 있으면 소속이 달라도 버린다. 단 **동명이인**(김지환·박두원·이용석)은 이름만 보고 지우면
 * 다른 사람이 증발하므로 종전대로 `이름+소속`으로만 지운다.
 *
 * 뒤쪽에 **구간 헤더 글자**([MatesHeader.periodLabel], v1.7.7 A4) 3건이 붙어 있다 — 같은 화면의
 * 순수 규칙이라 파일을 따로 만들지 않았다.
 *
 * 실행법은 [PatternTest] KDoc 참고(JUnitCore 직접 실행 — `gradlew test`는 한글 경로에서 죽는다).
 */
class MatesTest {

    private fun live(name: String, group: CrewGroup, offset: Int = 0, addedBy: String? = null) =
        RosterEntry("uid_$name", name, group, offset, addedBy)

    /** live·수동등록이 하나도 없을 때의 내장 명단 인원 = 기준선 */
    private val bundledOnly = mergeRoster(null, emptyList(), emptyList())

    @Test fun bundled_only_is_every_roster_row() {
        assertEquals(
            CrewGroup.entries.sumOf { BundledRoster.forGroup(it).size },
            bundledOnly.size,
        )
    }

    /**
     * ① 소속이 달라도 이름이 같으면 내장 줄이 사라진다 — 박희수(내장 신정지선 → live 본선 기관사).
     * 사용자 확정: *"본인이 앱에서 고른 소속과 근무로 가야지"*.
     */
    @Test fun live_row_hides_bundled_row_of_a_different_group() {
        val rows = mergeRoster(null, listOf(live("박희수", CrewGroup.MAIN_DRIVER, 3)), emptyList())
        val 박희수 = rows.filter { it.name == "박희수" }
        assertEquals("박희수는 한 줄만 보여야 한다", 1, 박희수.size)
        assertEquals(CrewGroup.MAIN_DRIVER, 박희수.single().group)
        assertEquals("uid_박희수", 박희수.single().uid)
        // 내장 줄 하나가 빠지고 live 줄 하나가 들어왔으니 총원은 그대로
        assertEquals(bundledOnly.size, rows.size)
    }

    /** 관리자 대리등록(`addedBy = "admin"`) 줄도 같은 `users` 문서다 — live로 똑같이 취급한다. */
    @Test fun admin_added_live_row_hides_bundled_row_too() {
        val rows = mergeRoster(
            null,
            listOf(live("차병철", CrewGroup.SHIFT_4_2, 2, addedBy = "admin")),
            emptyList(),
        )
        assertEquals(1, rows.count { it.name == "차병철" })
        assertEquals(CrewGroup.SHIFT_4_2, rows.single { it.name == "차병철" }.group)
    }

    /**
     * ② 동명이인 예외 — 기관사 김지환이 로그인해도 **차장 김지환은 남아야 한다.**
     * 이름만 보고 지우면 다른 사람이 명단에서 증발한다.
     */
    @Test fun namesake_keeps_the_other_persons_bundled_row() {
        val rows = mergeRoster(null, listOf(live("김지환", CrewGroup.MAIN_DRIVER, 52)), emptyList())
        val 김지환 = rows.filter { it.name == "김지환" }
        assertEquals("기관사·차장 두 줄이 그대로", 2, 김지환.size)
        assertEquals(
            setOf(CrewGroup.MAIN_DRIVER, CrewGroup.MAIN_CONDUCTOR),
            김지환.map { it.group }.toSet(),
        )
        // live 줄이 대체한 건 같은 소속(기관사) 하나뿐
        assertEquals("uid_김지환", 김지환.single { it.group == CrewGroup.MAIN_DRIVER }.uid)
        assertNull(김지환.single { it.group == CrewGroup.MAIN_CONDUCTOR }.uid)
        assertEquals(bundledOnly.size, rows.size)
    }

    /** 동명이인이 **다른 소속**으로 로그인해도 두 내장 줄은 손대지 않는다(세 줄이 된다). */
    @Test fun namesake_logging_in_elsewhere_adds_a_row_instead_of_deleting_one() {
        val rows = mergeRoster(null, listOf(live("박두원", CrewGroup.BRANCH, 4)), emptyList())
        assertEquals(3, rows.count { it.name == "박두원" })
        assertEquals(bundledOnly.size + 1, rows.size)
    }

    /** ③ live 줄이 없는 사람은 내장 명단 그대로 남는다 */
    @Test fun bundled_row_without_a_live_row_survives() {
        val rows = mergeRoster(null, listOf(live("박희수", CrewGroup.MAIN_DRIVER, 3)), emptyList())
        val 강성진 = rows.single { it.name == "강성진" }
        assertEquals(CrewGroup.BRANCH, 강성진.group)
        assertEquals(0, 강성진.offset)
    }

    /** ④ 견습(내장 명단에 없고 live만 있는 사람)은 그대로 보인다 — 본인이 근무를 고른다 */
    @Test fun trainee_with_only_a_live_row_is_kept() {
        val trainees = listOf("김성민", "김충현", "원두환")
        val rows = mergeRoster(null, trainees.map { live(it, CrewGroup.MAIN_DRIVER, 7) }, emptyList())
        trainees.forEach { assertEquals(it, 1, rows.count { r -> r.name == it }) }
        assertEquals(bundledOnly.size + trainees.size, rows.size)
        assertTrue(trainees.none { n -> BundledRoster.forGroup(CrewGroup.MAIN_DRIVER).any { it.first == n } })
    }

    /** 실측 9명 전원 — 소속을 바꿔 로그인하면 인원이 정확히 9줄 줄어든다(275 → 266) */
    @Test fun the_nine_measured_duplicates_collapse_to_one_row_each() {
        val nine = listOf(
            "강성진", "김형준", "문성진", "박경훈", "박형렬",
            "박희수", "서상훈", "정재헌", "차병철",
        )
        val rows = mergeRoster(null, nine.map { live(it, CrewGroup.SHIFT_4_2, 1) }, emptyList())
        nine.forEach { n ->
            assertEquals(n, 1, rows.count { it.name == n })
            assertEquals(n, CrewGroup.SHIFT_4_2, rows.single { it.name == n }.group)
        }
        assertEquals("live 9명이 내장 9줄을 대체 — 총원 불변", bundledOnly.size, rows.size)
    }

    /** 수동등록 동료는 종전대로 `이름+소속`이다 — live가 아니라 내장 줄을 지울 권한이 없다 */
    @Test fun manual_mate_does_not_hide_a_bundled_row_of_another_group() {
        val rows = mergeRoster(null, emptyList(), listOf(Mate("박희수", CrewGroup.MAIN_DRIVER, 3)))
        assertEquals(2, rows.count { it.name == "박희수" })
        assertEquals(bundledOnly.size + 1, rows.size)
    }

    /* ── v1.7.7 A4: 구간 헤더 글자([MatesHeader.periodLabel]) ──────────────────────
     *
     * 구간은 `오늘 + p개월`부터 한 달이고 헤더는 그 처음·끝을 적는다. v1.7.6까지 `M/D ~ M/D`
     * 뿐이라 `›`로 열두 번 민 화면이 **몇 년 뒤인지 알 길이 없었다**(`MatesViewModel.MAX_PERIOD`
     * 주석의 v1.6.61 실측). 아래 세 경우가 규칙 전부다 — 오늘(2026-09-06) 기준으로 잡는다.
     */

    /** ① 같은 해·올해 = 종전 그대로. 진입 기본값(구간 0)이라 이 글자가 가장 많이 보인다 */
    @Test fun period_label_keeps_the_bare_form_inside_this_year() {
        assertEquals(
            "9/6 ~ 10/5",
            MatesHeader.periodLabel(
                LocalDate.of(2026, 9, 6), LocalDate.of(2026, 10, 5), LocalDate.of(2026, 9, 6),
            ),
        )
    }

    /** ② 해를 넘는 구간(3구간) = **끝쪽에만** 붙는다. 시작은 올해라 안 붙어도 안 헷갈린다 */
    @Test fun period_label_marks_the_year_only_where_it_changes() {
        assertEquals(
            "12/6 ~ 2027.1/5",
            MatesHeader.periodLabel(
                LocalDate.of(2026, 12, 6), LocalDate.of(2027, 1, 5), LocalDate.of(2026, 9, 6),
            ),
        )
    }

    /** ③ 통째로 내년(4구간) = **시작에만** 붙는다. 끝은 시작과 같은 해라 되풀이하지 않는다 */
    @Test fun period_label_marks_the_year_once_when_the_whole_period_is_next_year() {
        assertEquals(
            "2027.1/6 ~ 2/5",
            MatesHeader.periodLabel(
                LocalDate.of(2027, 1, 6), LocalDate.of(2027, 2, 5), LocalDate.of(2026, 9, 6),
            ),
        )
    }

    /** 키 중복은 절대 남으면 안 된다 — `LazyColumn(key=)`가 앱을 죽인다(v1.6.60) */
    @Test fun no_duplicate_keys_even_with_a_doubled_live_user() {
        val rows = mergeRoster(
            null,
            listOf(live("박희수", CrewGroup.MAIN_DRIVER, 3), live("박희수", CrewGroup.MAIN_DRIVER, 9)),
            emptyList(),
        )
        assertEquals(rows.size, rows.map { it.name + "|" + it.group.name }.toSet().size)
    }
}
