package com.sinjeong.crewcalendar

import com.sinjeong.crewcalendar.domain.model.BundledRoster
import com.sinjeong.crewcalendar.domain.model.CrewGroup
import com.sinjeong.crewcalendar.domain.model.Mate
import com.sinjeong.crewcalendar.domain.repository.RosterEntry
import com.sinjeong.crewcalendar.presentation.mates.MatesHeader
import com.sinjeong.crewcalendar.presentation.roster.mergeRoster
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    /**
     * 동명이인이 **다른 소속**으로 로그인해도 두 내장 줄은 손대지 않는다(세 줄이 된다).
     *
     * live 소속은 v1.7.9에 `BRANCH` → `MAIN_DRIVER` 로 바꿨다 — 9월 근무표에서 **박두원(기관사)이
     * 지선으로 옮겨** 이제 내장 두 줄이 `BRANCH`+`MAIN_CONDUCTOR` 다. 종전 값으로 두면
     * live 가 같은 소속의 내장 줄을 덮어 두 줄이 되어 규칙이 아니라 명단 때문에 깨진다.
     */
    @Test fun namesake_logging_in_elsewhere_adds_a_row_instead_of_deleting_one() {
        val rows = mergeRoster(null, listOf(live("박두원", CrewGroup.MAIN_DRIVER, 4)), emptyList())
        assertEquals(3, rows.count { it.name == "박두원" })
        assertEquals(bundledOnly.size + 1, rows.size)
    }

    /**
     * ③ live 줄이 없는 사람은 내장 명단 그대로 남는다.
     * 강성진의 기대값은 v1.7.9에 `BRANCH/0` → `MAIN_DRIVER/16` 으로 바뀌었다 —
     * 9월 근무표에서 지선 → 본선으로 옮겼다(명단이 바뀐 것이지 규칙이 바뀐 게 아니다).
     */
    @Test fun bundled_row_without_a_live_row_survives() {
        val rows = mergeRoster(null, listOf(live("박희수", CrewGroup.MAIN_DRIVER, 3)), emptyList())
        val 강성진 = rows.single { it.name == "강성진" }
        assertEquals(CrewGroup.MAIN_DRIVER, 강성진.group)
        assertEquals(16, 강성진.offset)
    }

    /**
     * ④ **견습은 본인이 고른 근무가 이긴다** — v1.7.9 ⑧-3 에서 뜻이 한 겹 늘었다.
     *
     * ⑧-2 까지 견습은 내장 명단에 **아예 없었고** 이 테스트는 *"live 만 있어도 줄이 생긴다"* 를
     * 쟀다. ⑧-3 부터는 [BundledRoster.UNASSIGNED] 자리표시자로 **명단에 세워 두되 근무 칸만
     * 비운다** — 그래서 이제 재는 것은 *"본인이 고르면 자리표시자가 사라지고 고른 교번이 쓰인다"* 다
     * (카스 확정 2026-09-07 *"본인이 로그인해서 근무선택한 다이아"*).
     *
     * 규칙 자체([mergeRoster]의 `liveNames` 대조)는 **한 줄도 안 바꿨다** — 열두 명 다
     * 동명이인이 아니라 이름만으로 지워진다.
     */
    @Test fun trainee_row_gives_way_to_the_offset_they_pick() {
        val trainees = listOf("김성민", "김충현", "원두환")
        // 아무도 안 골랐을 때: 명단에 있고 offset 은 감시값이라 근무 칸이 비어 보인다
        trainees.forEach { n ->
            val row = bundledOnly.single { it.name == n }
            assertEquals(n, CrewGroup.MAIN_DRIVER, row.group)
            assertEquals(n, BundledRoster.UNASSIGNED, row.offset)
            assertEquals(n, "미배정", BundledRoster.noDutyLabel(row.offset))
        }
        // 본인이 로그인해 근무를 고르면 그 줄이 이긴다 — 한 줄뿐이고 교번은 고른 값이다
        val rows = mergeRoster(null, trainees.map { live(it, CrewGroup.MAIN_DRIVER, 7) }, emptyList())
        trainees.forEach { n ->
            val row = rows.single { it.name == n }
            assertEquals(n, 7, row.offset)
            assertEquals(n, "uid_$n", row.uid)
            assertNull("$n 감시값이 남았다", BundledRoster.noDutyLabel(row.offset))
        }
        assertEquals("자리표시자 3줄을 live 3줄이 대체 — 총원 불변", bundledOnly.size, rows.size)
        // 소속을 바꿔 골라도 마찬가지다(내장 줄은 이름으로 지워진다)
        val moved = mergeRoster(null, listOf(live("선철호", CrewGroup.BRANCH, 5)), emptyList())
        assertEquals(1, moved.count { it.name == "선철호" })
        assertEquals(CrewGroup.BRANCH, moved.single { it.name == "선철호" }.group)
        assertEquals(5, moved.single { it.name == "선철호" }.offset)
        assertEquals(bundledOnly.size, moved.size)
    }

    /** 육아휴직 2명도 같은 규칙 — 자리표시자 글자만 `휴직`이고 고르면 그 값이 이긴다 */
    @Test fun onLeave_row_gives_way_to_the_offset_they_pick() {
        listOf("김주식", "이한솔").forEach { n ->
            val row = bundledOnly.single { it.name == n }
            assertEquals(n, CrewGroup.MAIN_CONDUCTOR, row.group)
            assertEquals(n, BundledRoster.ON_LEAVE, row.offset)
            assertEquals(n, "휴직", BundledRoster.noDutyLabel(row.offset))
        }
        val rows = mergeRoster(null, listOf(live("이한솔", CrewGroup.MAIN_CONDUCTOR, 12)), emptyList())
        assertEquals(1, rows.count { it.name == "이한솔" })
        assertEquals(12, rows.single { it.name == "이한솔" }.offset)
        assertEquals(bundledOnly.size, rows.size)
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
