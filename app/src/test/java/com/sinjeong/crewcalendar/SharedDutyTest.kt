package com.sinjeong.crewcalendar

import com.sinjeong.crewcalendar.domain.model.Bundled
import com.sinjeong.crewcalendar.domain.model.DutyCode
import com.sinjeong.crewcalendar.domain.model.REPUBLISH_BACK_DAYS
import com.sinjeong.crewcalendar.domain.model.republishDates
import com.sinjeong.crewcalendar.domain.model.sharedDutyRaw
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * **휴가는 나만 보기**(v1.7.19) — 서버에 **올리느냐 마느냐** 한 가지만 정하는 순수 함수.
 * 카스 확정: *"본인만 보기(완전히 숨김)"* · **기본값 꺼짐**(*"원하는 사람만 숨기게 설정해"*).
 * `null` = 안 올린다(= 그 날짜 공유 문서 삭제).
 * 근무성 변경(충당·교체·지근·운휴·지휴·교육·회행)은 켜도 종전대로 동료에게 보인다.
 */
class SharedDutyTest {

    /** 휴가류 낱말 전부 → `null`. 목록은 `DutyCode.LEAVE_OPTIONS`(= REST_OPTIONS − 운휴) 파생이다 */
    @Test fun every_leave_word_is_hidden() {
        // 낱말이 늘거나 줄면 여기서 먼저 걸린다(근무변경 항목을 고칠 때 같이 보라는 뜻)
        assertEquals(
            setOf(
                "연차", "보상", "촉연", "대휴", "장휴", "청휴", "학습", "만휴",
                "돌봄휴가", "동행휴가", "병가", "공가", "가연차", "작연차",
            ),
            DutyCode.LEAVE_OPTIONS,
        )
        DutyCode.LEAVE_OPTIONS.forEach { assertNull(it, sharedDutyRaw(it, hideLeave = true)) }
        // `운휴`·`지휴` 는 휴가가 아니라 그 날의 휴무 배정이다 — 숨기지 않는다(휴무 개수 +1 규칙)
        listOf("운휴", "지휴").forEach {
            assertEquals(it, it, sharedDutyRaw(it, hideLeave = true))
        }
    }

    /** 근무성 코드는 종전대로 올라간다 — 다이아·충당 계열·낱말 근무·패턴 휴무·비번 전부 */
    @Test fun work_codes_still_shared() {
        val kept = listOf(
            "1", "29", "33", "51",                       // 본선 주간·야간 다이아
            "44비", "~", "비번",                          // 익일 비번
            "휴5", "휴28", "휴무",                         // 패턴 휴무(휴가 아님)
            "대2", "대11", "대11비",                       // 대기
            "지1", "지14", "지대1", "지대11비", "지휴5",     // 지선
            "지근", "충당", "대기충당", "교체",              // 대신 뛰는 근무
            "충당 지2", "대기충당 지대11", "교체 45", "지근 34",
            "운휴", "교육", "회행",                        // 출근하는 날 / 휴무 배정
            "주간", "야간", "주",                          // 4조2교대·통상근무
        )
        kept.forEach { assertEquals(it, it, sharedDutyRaw(it, hideLeave = true)) }

        // 내장 패턴 4종의 시퀀스 전수 — 패턴 근무가 숨겨지는 일은 절대 없어야 한다
        Bundled.ALL_PATTERNS.flatMap { it.sequence }.forEach {
            assertEquals(it, it, sharedDutyRaw(it, hideLeave = true))
        }
    }

    /**
     * 직접입력(자유 글자)도 같이 숨긴다. 첫 글자가 휴·지·대·주라도 숨겨진다 —
     * `parse` 가 헐렁해서(`대전 출장` → STANDBY) 타입만 보면 새는 자리다.
     */
    @Test fun free_text_is_hidden() {
        listOf("병원", "abc", "연차 오전", "대전 출장", "휴가원 제출", "지각", "주말 특근", "12시 출근")
            .forEach { assertNull(it, sharedDutyRaw(it, hideLeave = true)) }
        // 맨 `휴`·`지`·`대` 한 글자도 자유 글자다 — 앱이 저장하는 휴무는 `휴5`(번호) 또는
        // `휴무`(낱말)뿐이라 이 한 글자는 사람이 직접 친 것이다(`display` KDoc 의 깨진 값 `지` 참고).
        listOf("휴", "지", "대").forEach { assertNull(it, sharedDutyRaw(it, hideLeave = true)) }
    }

    /**
     * **기본값(꺼짐)은 v1.7.18 과 완전히 같은 동작이다** — 카스: *"원하는 사람만 숨기게 설정해"*.
     * 저장될 수 있는 값 전수(패턴 4종 시퀀스 + 근무변경 전부 + 충당 계열 + 휴가류 + 직접입력 +
     * 빈 값)를 훑어 **한 글자도 안 바뀌는 것**을 못 박는다. 안 켠 사람의 서버 기록은 그대로다.
     */
    @Test fun switch_off_shares_everything_unchanged() {
        val all = Bundled.ALL_PATTERNS.flatMap { it.sequence } +
            DutyCode.CHANGE_OPTIONS + DutyCode.LEAVE_OPTIONS +
            DutyCode.FILL_OPTIONS.map { "$it 지2" } +
            listOf("병원", "대전 출장", "휴", "~", "비번", "작연차", "", "   ")
        all.forEach { assertEquals(it, it, sharedDutyRaw(it, hideLeave = false)) }

        // 켜져 있으면 빈 값도 `null`(안 올림) — 패턴 복귀와 같은 답이라 갈라질 일이 없다
        assertNull(sharedDutyRaw("", hideLeave = true))
        assertNull(sharedDutyRaw("   ", hideLeave = true))
    }

    /**
     * 재게시 대상 — **(오늘−31)~미래** 중 **숨김 대상인 날만**.
     * 켤 때(삭제)·끌 때(다시 올림) 대상이 같아 방향을 따로 계산하지 않는다.
     */
    @Test fun republish_picks_only_hidden_rows_in_window() {
        val today = LocalDate.of(2026, 9, 19)
        val rows = mapOf(
            today.minusDays(REPUBLISH_BACK_DAYS + 1) to "연차",   // 창 밖(32일 전) — 제외
            today.minusDays(REPUBLISH_BACK_DAYS) to "병가",       // 창 끝(31일 전) — 포함
            today.minusDays(3) to "충당 지2",                     // 근무성 — 제외
            today to "돌봄휴가",                                  // 포함
            today.plusDays(5) to "병원",                          // 직접입력 — 포함
            today.plusDays(6) to "지근",                          // 근무성 — 제외
            today.plusDays(7) to "",                             // 빈 값(서버에 없다) — 제외
            today.plusDays(400) to "대휴",                        // 미래 끝은 없다 — 포함
        )
        assertEquals(
            listOf(
                today.minusDays(REPUBLISH_BACK_DAYS), today, today.plusDays(5), today.plusDays(400),
            ),
            republishDates(rows, today),
        )
        assertEquals(emptyList<LocalDate>(), republishDates(emptyMap(), today))
        // 근무성 변경만 있는 사람은 스위치를 눌러도 서버 쓰기가 한 건도 안 생긴다
        assertEquals(
            emptyList<LocalDate>(),
            republishDates(mapOf(today to "교체 45", today.plusDays(1) to "운휴"), today),
        )
    }
}
