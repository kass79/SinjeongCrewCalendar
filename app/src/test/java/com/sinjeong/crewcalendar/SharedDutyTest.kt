package com.sinjeong.crewcalendar

import com.sinjeong.crewcalendar.domain.model.Bundled
import com.sinjeong.crewcalendar.domain.model.DutyCode
import com.sinjeong.crewcalendar.domain.model.DutyType
import com.sinjeong.crewcalendar.domain.model.REPUBLISH_BACK_DAYS
import com.sinjeong.crewcalendar.domain.model.SHARED_FREE
import com.sinjeong.crewcalendar.domain.model.SHARED_LEAVE
import com.sinjeong.crewcalendar.domain.model.republishDates
import com.sinjeong.crewcalendar.domain.model.sharedDutyRaw
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * **휴가 종류 가리기**(v1.7.19) — 서버로 나가는 `dutyRaw` 한 값만 바꾸는 순수 함수.
 * 카스 확정: *"휴가 로만 보이기"*. 근무성 변경(충당·교체·지근·운휴·지휴·교육·회행)은 그대로.
 */
class SharedDutyTest {

    /** 휴가류 낱말 전부 → `휴가`. 목록은 `DutyCode.LEAVE_OPTIONS`(= REST_OPTIONS − 운휴) 파생이다 */
    @Test fun every_leave_word_becomes_휴가() {
        // 낱말이 늘거나 줄면 여기서 먼저 걸린다(근무변경 항목을 고칠 때 같이 보라는 뜻)
        assertEquals(
            setOf(
                "연차", "보상", "촉연", "대휴", "장휴", "청휴", "학습", "만휴",
                "돌봄휴가", "동행휴가", "병가", "공가", "가연차", "작연차",
            ),
            DutyCode.LEAVE_OPTIONS,
        )
        DutyCode.LEAVE_OPTIONS.forEach {
            assertEquals(it, SHARED_LEAVE, sharedDutyRaw(it, maskLeave = true))
        }
        // `운휴`·`지휴` 는 휴가가 아니라 그 날의 휴무 배정이다 — 가리지 않는다(휴무 개수 +1 규칙)
        listOf("운휴", "지휴").forEach {
            assertEquals(it, it, sharedDutyRaw(it, maskLeave = true))
        }
    }

    /** 근무성 코드는 한 글자도 안 바뀐다 — 다이아·충당 계열·낱말 근무·패턴 휴무·비번 전부 */
    @Test fun work_codes_pass_through() {
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
        kept.forEach { assertEquals(it, it, sharedDutyRaw(it, maskLeave = true)) }

        // 내장 패턴 4종의 시퀀스 전수 — 패턴 근무가 가려지는 일은 절대 없어야 한다
        Bundled.ALL_PATTERNS.flatMap { it.sequence }.forEach {
            assertEquals(it, it, sharedDutyRaw(it, maskLeave = true))
        }
    }

    /**
     * 직접입력(자유 글자) → `기타`. 첫 글자가 휴·지·대·주라도 가려진다 —
     * `parse` 가 헐렁해서(`대전 출장` → STANDBY) 타입만 보면 새는 자리다.
     */
    @Test fun free_text_becomes_기타() {
        listOf("병원", "abc", "연차 오전", "대전 출장", "휴가원 제출", "지각", "주말 특근", "12시 출근")
            .forEach { assertEquals(it, SHARED_FREE, sharedDutyRaw(it, maskLeave = true)) }
        // 맨 `휴`·`지`·`대` 한 글자도 자유 글자다 — 앱이 저장하는 휴무는 `휴5`(번호) 또는
        // `휴무`(낱말)뿐이라 이 한 글자는 사람이 직접 친 것이다(`display` KDoc 의 깨진 값 `지` 참고).
        listOf("휴", "지", "대").forEach {
            assertEquals(it, SHARED_FREE, sharedDutyRaw(it, maskLeave = true))
        }
    }

    /** 꺼 두면 전부 그대로. 빈 값도 그대로(빈 값은 패턴 복귀 = 서버 문서 삭제라 애초에 안 올라간다) */
    @Test fun mask_off_changes_nothing() {
        listOf("연차", "병가", "병원", "충당 지2", "휴5", "", "   ").forEach {
            assertEquals(it, it, sharedDutyRaw(it, maskLeave = false))
        }
        assertEquals("", sharedDutyRaw("", maskLeave = true))
        assertEquals("   ", sharedDutyRaw("   ", maskLeave = true))
    }

    /** 멱등 — 재게시가 여러 번 돌아도 `휴가`·`기타` 가 다시 뭉개지지 않는다 */
    @Test fun masking_is_idempotent() {
        listOf(SHARED_LEAVE, SHARED_FREE).forEach {
            assertEquals(it, sharedDutyRaw(it, maskLeave = true))
            assertEquals(it, sharedDutyRaw(sharedDutyRaw(it, true), true))
        }
        assertEquals(SHARED_LEAVE, sharedDutyRaw(sharedDutyRaw("병가", true), true))
        assertEquals(SHARED_FREE, sharedDutyRaw(sharedDutyRaw("병원", true), true))
    }

    /**
     * **동료 탭이 두 낱말을 이미 그릴 줄 안다** — 표시 규칙을 새로 만들지 않은 근거.
     * `휴가` 는 `parse` 가 [DutyType.REST] 로 주므로 연차·병가와 **같은 빨강·같은 라벨**이고,
     * `기타` 는 [DutyType.ETC] 라 **지금 직접입력이 그려지는 모습과 똑같다**(투명 바탕·회색 글자).
     */
    @Test fun masked_words_render_like_existing_ones() {
        val leave = DutyCode.parse(SHARED_LEAVE)
        assertEquals(DutyType.REST, leave.type)
        assertEquals(DutyCode.parse("연차").colorType, leave.colorType)   // dutyCellColors 같은 칸
        assertEquals(SHARED_LEAVE, leave.display)                        // 라벨 `휴가`
        assertEquals(SHARED_LEAVE, leave.gridLabel)
        assertEquals(null, leave.number)                                 // `휴5` 처럼 번호로 읽히지 않는다
        assertEquals(DutyType.ETC, DutyCode.parse(SHARED_FREE).type)
        assertEquals(DutyCode.parse("병원").colorType, DutyCode.parse(SHARED_FREE).colorType)

        // 동료 탭 폭 상한(DutyMatrix.UNIFORM_UNITS = 3.24) 안에 든다 — 표 전체 글자가 안 작아진다
        listOf(SHARED_LEAVE, SHARED_FREE).forEach {
            assertEquals(it, 2.0, labelUnits(it), 0.001)
            assertTrue("$it 이 UNIFORM_UNITS(3.24)를 넘는다", labelUnits(it) <= 3.24)
        }
        // 다른 근무의 라벨과 글자가 겹치지 않는다(겹치면 격자에서 구별이 안 된다)
        val others = (DutyCode.CHANGE_OPTIONS + Bundled.ALL_PATTERNS.flatMap { it.sequence })
            .map { DutyCode.parse(it).display }
        listOf(SHARED_LEAVE, SHARED_FREE).forEach {
            assertTrue("$it 이 기존 라벨과 겹친다", it !in others)
        }
    }

    /** `PatternTest.labelUnits` 와 같은 글자폭 모델(한글 1.0 · 그 밖 0.62) */
    private fun labelUnits(s: String) =
        s.split('\n').maxOf { line -> line.sumOf { if (it.code >= 0x1100) 1.0 else 0.62 } }

    /**
     * 재게시 대상 — **(오늘−31)~미래** 중 **가리기가 값을 바꾸는 날만**.
     * 켤 때·끌 때 대상이 같고, 삭제 대상은 애초에 없다.
     */
    @Test fun republish_picks_only_changed_rows_in_window() {
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
        // 이미 가려진 값은 다시 올릴 게 없다(멱등) — 스위치를 여러 번 눌러도 쓰기가 안 쌓인다
        assertEquals(
            emptyList<LocalDate>(),
            republishDates(mapOf(today to SHARED_LEAVE, today.plusDays(1) to SHARED_FREE), today),
        )
    }
}
