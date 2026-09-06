package com.sinjeong.crewcalendar

import com.sinjeong.crewcalendar.domain.model.Bundled
import com.sinjeong.crewcalendar.domain.model.BundledRoster
import com.sinjeong.crewcalendar.domain.model.CrewGroup
import com.sinjeong.crewcalendar.domain.model.CrewRole
import com.sinjeong.crewcalendar.domain.model.Mate
import com.sinjeong.crewcalendar.domain.model.ReviewerAccount
import com.sinjeong.crewcalendar.domain.repository.RosterEntry
import com.sinjeong.crewcalendar.presentation.mates.MatesHeader
import com.sinjeong.crewcalendar.presentation.roster.MatrixPerson
import com.sinjeong.crewcalendar.presentation.roster.mergeRoster
import com.sinjeong.crewcalendar.presentation.roster.onlyMyRow
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

    // ── v1.7.10 ③ 로그인 기본값 줄 무시 ─────────────────────────────────
    //
    // 2026-09-07 카스: *"한상무 근무가 오늘 휴17인거 같은데? 동료 근무엔 오류?"* ·
    // *"정재헌은 통상근무일껄?"*. 내장 명단은 맞았다(9/7 승무 3종 전원 오차 0). 진범은
    // `LoginScreen.AuthViewModel.submitCredential` 이 **근무선택 전에** 만들던 `users` 문서다 —
    // `patternOffset = 0` · 기관사면 지선 · 차장이면 본선차장이 박혀 나가고, `mergeRoster` 는
    // live 를 내장보다 앞세우므로(v1.6.87) 한상무가 **지선 0 인 김학진의 근무**를 뒤집어썼다.
    // 실측으로 이 서명에 걸린 사람은 다섯(한상무·정재헌·박소영·송민지·조한빈)이고
    // `users` 54개 전부 `addedBy` 가 없었다(관리자 대리등록 0건).

    /** ㉮ 서명 + 내장에 있는 이름 → **내장값이 나온다**. `uid` 는 살아남는다 */
    @Test fun login_default_row_gives_way_to_the_bundled_row() {
        // 한상무·정재헌은 기관사 기본값(지선 0), 차장 셋은 차장 기본값(본선차장 0)으로 박혀 있었다
        val fake = listOf(
            live("한상무", CrewGroup.BRANCH, 0), live("정재헌", CrewGroup.BRANCH, 0),
            live("박소영", CrewGroup.MAIN_CONDUCTOR, 0), live("송민지", CrewGroup.MAIN_CONDUCTOR, 0),
            live("조한빈", CrewGroup.MAIN_CONDUCTOR, 0),
        )
        val rows = mergeRoster(null, fake, emptyList())
        val expected = mapOf(
            "한상무" to (CrewGroup.MAIN_DRIVER to 88),   // 본선기관사 88 = 9/7 `휴17`
            "정재헌" to (CrewGroup.OFFICE_DAY to 0),      // 통상근무
            "박소영" to (CrewGroup.MAIN_CONDUCTOR to 36),
            "송민지" to (CrewGroup.MAIN_CONDUCTOR to 67),
            "조한빈" to (CrewGroup.MAIN_CONDUCTOR to 105),
        )
        expected.forEach { (name, go) ->
            val row = rows.single { it.name == name }
            assertEquals(name, go.first, row.group)
            assertEquals(name, go.second, row.offset)
            // 줄을 버리지 않고 고쳐 쓴다 — `uid` 가 빠지면 근무변경이 조용히 사라진다
            assertEquals(name, "uid_$name", row.uid)
        }
        assertEquals("가짜 줄이 내장 줄로 대체될 뿐 총원은 안 변한다", bundledOnly.size, rows.size)
    }

    /** ㉯ 같은 서명이라도 **내장에 없는 이름은 살아남는다** (조건 ③ — 빠뜨리면 통째로 사라진다) */
    @Test fun login_default_signature_keeps_a_name_that_is_not_in_the_bundled_roster() {
        val newcomer = live("가나다라", CrewGroup.BRANCH, 0)
        val rows = mergeRoster(null, listOf(newcomer), emptyList())
        val row = rows.single { it.name == "가나다라" }
        assertEquals(CrewGroup.BRANCH, row.group)
        assertEquals(0, row.offset)
        assertEquals("uid_가나다라", row.uid)
        assertEquals(bundledOnly.size + 1, rows.size)
        // 내장 명단에 없다 = 조건 ③ 미충족
        assertEquals(emptyList<Any>(), BundledRoster.realEntriesFor("가나다라"))
    }

    /** ㉰ 서명이 아닌 live 줄(차장 70 · 지선 5 · 본선 0)은 **종전대로 이긴다** */
    @Test fun a_row_that_was_actually_chosen_still_wins() {
        // offset 이 0 이 아니면 서명이 아니다
        val conductor70 = mergeRoster(null, listOf(live("한상무", CrewGroup.MAIN_CONDUCTOR, 70)), emptyList())
        assertEquals(CrewGroup.MAIN_CONDUCTOR, conductor70.single { it.name == "한상무" }.group)
        assertEquals(70, conductor70.single { it.name == "한상무" }.offset)

        val branch5 = mergeRoster(null, listOf(live("한상무", CrewGroup.BRANCH, 5)), emptyList())
        assertEquals(CrewGroup.BRANCH, branch5.single { it.name == "한상무" }.group)
        assertEquals(5, branch5.single { it.name == "한상무" }.offset)

        // offset 이 0 이어도 소속이 기본값 둘(지선·본선차장)이 아니면 서명이 아니다
        listOf(CrewGroup.MAIN_DRIVER, CrewGroup.SHIFT_4_2, CrewGroup.OFFICE_DAY).forEach { g ->
            val rows = mergeRoster(null, listOf(live("한상무", g, 0)), emptyList())
            val row = rows.single { it.name == "한상무" }
            assertEquals(g.name, g, row.group)
            assertEquals(g.name, 0, row.offset)
        }
    }

    /** ㉱ 견습 3명의 live 줄은 **계속 이긴다** — 그들의 내장 offset 은 감시값이라 서명이 안 걸린다 */
    @Test fun trainee_rows_are_never_treated_as_a_login_default() {
        val trainees = listOf("김성민", "김충현", "원두환")
        // 실측: 그들이 고른 값은 offset 0 이 아니었다. 그래도 **0 이어도 안전해야** 한다 —
        // 내장 줄이 `미배정`(감시값)이라 `realEntriesFor` 가 비고, 그러면 조건 ③ 이 안 선다.
        trainees.forEach { n -> assertEquals(n, emptyList<Any>(), BundledRoster.realEntriesFor(n)) }
        // 견습이 정말로 지선 0 을 골라도 그 선택이 `미배정`으로 되덮이지 않는다
        val rows = mergeRoster(null, trainees.map { live(it, CrewGroup.BRANCH, 0) }, emptyList())
        trainees.forEach { n ->
            val row = rows.single { it.name == n }
            assertEquals(n, CrewGroup.BRANCH, row.group)
            assertEquals(n, 0, row.offset)
            assertNull("$n 감시값이 남았다", BundledRoster.noDutyLabel(row.offset))
        }
        assertEquals(bundledOnly.size, rows.size)
    }

    /**
     * ⚠ 진짜 **김학진(지선 0)·홍대종(차장 0)** 은 서명에 걸리지만 **내장값이 같은 값**이라
     * 화면이 한 칸도 안 바뀐다. `uid` 도 그대로 남는다(줄을 버리지 않고 고쳐 쓰기 때문).
     */
    @Test fun real_branch_zero_row_is_unchanged() {
        listOf("김학진" to CrewGroup.BRANCH, "홍대종" to CrewGroup.MAIN_CONDUCTOR).forEach { (n, g) ->
            val rows = mergeRoster(null, listOf(live(n, g, 0)), emptyList())
            val row = rows.single { it.name == n }
            assertEquals(n, g, row.group)
            assertEquals(n, 0, row.offset)
            assertEquals(n, "uid_$n", row.uid)
            assertEquals(n, listOf(g to 0), BundledRoster.realEntriesFor(n))
        }
    }

    /** 동명이인은 어느 내장 줄인지 못 고른다 → 가짜 줄만 빠지고 내장 두 줄이 둘 다 산다 */
    @Test fun login_default_row_of_a_namesake_is_dropped_not_guessed() {
        val rows = mergeRoster(null, listOf(live("김지환", CrewGroup.BRANCH, 0)), emptyList())
        assertEquals(2, BundledRoster.realEntriesFor("김지환").size)
        assertEquals(2, rows.count { it.name == "김지환" })
        assertEquals(
            listOf(CrewGroup.MAIN_DRIVER, CrewGroup.MAIN_CONDUCTOR),
            rows.filter { it.name == "김지환" }.map { it.group },
        )
        assertEquals(bundledOnly.size, rows.size)
    }

    /**
     * ① 로그인 경로 잠금 — `FirestoreUserRepository.publish` 가 보는 것과 **같은 순수 함수**다.
     * `AuthViewModel.submitCredential` 이 만드는 모양(`role`·`patternId`·`offset 0`) 그대로 넣는다.
     */
    @Test fun login_default_user_is_not_published() {
        val branch = Bundled.BRANCH_PATTERN.id
        val main = Bundled.MAIN_PATTERN.id
        // 기관사 기본값 · 차장 기본값 — 둘 다 막힌다
        assertEquals(true, BundledRoster.isLoginDefaultUser("한상무", CrewRole.DRIVER_BRANCH, branch, 0))
        assertEquals(true, BundledRoster.isLoginDefaultUser("정재헌", CrewRole.DRIVER_BRANCH, branch, 0))
        assertEquals(true, BundledRoster.isLoginDefaultUser("박소영", CrewRole.CONDUCTOR, main, 0))
        // 근무선택을 마친 뒤(offset ≠ 0)는 종전대로 올라간다
        assertEquals(false, BundledRoster.isLoginDefaultUser("한상무", CrewRole.DRIVER_BRANCH, branch, 88))
        assertEquals(false, BundledRoster.isLoginDefaultUser("한상무", CrewRole.DRIVER_MAIN, main, 88))
        // 본선기관사로 골라 offset 이 0 인 경우도 서명이 아니다(소속이 기본값 둘이 아니다)
        assertEquals(false, BundledRoster.isLoginDefaultUser("김철수", CrewRole.DRIVER_MAIN, main, 0))
        // 내장 명단에 없는 사람은 막지 않는다 — 심사 계정도 여기 걸리면 안 된다
        assertEquals(false, BundledRoster.isLoginDefaultUser("가나다라", CrewRole.DRIVER_BRANCH, branch, 0))
        assertEquals(
            false,
            BundledRoster.isLoginDefaultUser(
                ReviewerAccount.NAME, CrewRole.DRIVER_BRANCH, branch, 0,
            ),
        )
        // 견습(감시값)도 막지 않는다 — 막으면 본인이 고른 값이 서버에 안 남는다
        assertEquals(false, BundledRoster.isLoginDefaultUser("김성민", CrewRole.DRIVER_BRANCH, branch, 0))
    }

    /**
     * ④ **하단 탭 넷** — `달력 · 동료 · 즐겨찾기 · 설정`(v1.7.10, 카스 지정).
     *
     * 아이콘은 못 잠근다(테스트 하네스에 Compose 가 없어 `ImageVector` 를 못 든다) —
     * 그래서 [BottomTabs] 가 **문자열만** 들고 있고 `Tab` 이 아이콘만 든다.
     * 즐겨찾기가 **동료 바로 뒤**인 것이 요청의 핵심이라 자리까지 못 박는다.
     */
    @Test fun bottom_tabs_are_calendar_mates_favorites_settings() {
        assertEquals(
            listOf("calendar" to "달력", "mates" to "동료", "favorites" to "즐겨찾기", "settings" to "설정"),
            BottomTabs.ORDER,
        )
        // 즐겨찾기는 동료와 설정 사이 — 세 번째 자리다
        val routes = BottomTabs.ORDER.map { it.first }
        assertEquals(2, routes.indexOf(BottomTabs.FAVORITES))
        assertEquals(routes.indexOf(BottomTabs.MATES) + 1, routes.indexOf(BottomTabs.FAVORITES))
        assertEquals(routes.indexOf(BottomTabs.FAVORITES) + 1, routes.indexOf(BottomTabs.SETTINGS))
        // 라우트는 서로 달라야 한다 — 같으면 네비게이션이 조용히 한 탭으로 합쳐진다
        assertEquals(4, routes.toSet().size)
        // 라벨의 단일 출처(`Tab.label` 이 이 함수로 읽는다)
        assertEquals("즐겨찾기", BottomTabs.labelOf(BottomTabs.FAVORITES))
        assertEquals("동료", BottomTabs.labelOf(BottomTabs.MATES))
        // 목록에 없는 라우트는 터지지 않고 그대로 보인다
        assertEquals("없는탭", BottomTabs.labelOf("없는탭"))
    }

    /**
     * ④ **즐겨찾기 0명 판정** — 내 행은 필터와 무관하게 늘 들어 있어 목록이 비지 않는다.
     * 이 판정이 없으면 즐겨찾기 탭이 **내 한 줄만 뜬 채 아무 설명 없는 화면**이 된다.
     */
    @Test fun favorites_with_nobody_saved_is_only_my_row() {
        val me = MatrixPerson("강성진 (나)", CrewGroup.MAIN_DRIVER, 16, isMe = true)
        val mate = MatrixPerson("박희수", CrewGroup.BRANCH, 5)
        assertEquals(true, onlyMyRow(listOf(me)))
        assertEquals(true, onlyMyRow(emptyList()))
        assertEquals(false, onlyMyRow(listOf(me, mate)))
        assertEquals(false, onlyMyRow(listOf(mate)))
    }
}
