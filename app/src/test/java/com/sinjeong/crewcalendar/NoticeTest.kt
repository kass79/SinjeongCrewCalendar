package com.sinjeong.crewcalendar

import com.sinjeong.crewcalendar.domain.model.Notice
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 관리자 공지의 기간 판정 — 배너가 뜰지 말지를 정하는 유일한 규칙 */
class NoticeTest {

    private val n = Notice(
        id = "x",
        title = "테스트 공지",
        body = "본문",
        from = LocalDate.of(2026, 9, 4),
        to = LocalDate.of(2026, 9, 11),
        createdAt = 0L,
    )

    @Test fun `기간 안이면 뜬다`() {
        assertTrue(n.isActive(LocalDate.of(2026, 9, 7)))
    }

    /** 양 끝은 **포함**이다 — 시작일 아침과 종료일 저녁에도 보여야 한다 */
    @Test fun `시작일 종료일 경계 포함`() {
        assertTrue(n.isActive(LocalDate.of(2026, 9, 4)))
        assertTrue(n.isActive(LocalDate.of(2026, 9, 11)))
    }

    @Test fun `기간 밖이면 안 뜬다`() {
        assertFalse(n.isActive(LocalDate.of(2026, 9, 3)))
        assertFalse(n.isActive(LocalDate.of(2026, 9, 12)))
    }
}
