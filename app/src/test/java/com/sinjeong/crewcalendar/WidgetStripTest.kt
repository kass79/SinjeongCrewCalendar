package com.sinjeong.crewcalendar

import com.sinjeong.crewcalendar.domain.model.DutyCode
import com.sinjeong.crewcalendar.domain.model.DutyType
import com.sinjeong.crewcalendar.widget.Cell
import com.sinjeong.crewcalendar.widget.cellLabel
import com.sinjeong.crewcalendar.widget.decodeStrip
import com.sinjeong.crewcalendar.widget.encodeStrip
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetStripTest {
    @Test fun `6칸 레코드 왕복`() {
        val cells = listOf(
            Cell("금", "4", "~", false, DutyType.POST_NIGHT, ""),
            Cell("토", "5", "휴2", true, DutyType.REST, ""),
            Cell("일", "6", "14", true, DutyType.MAIN_DAY, "출근 07:47"),
        )
        assertEquals(cells, decodeStrip(encodeStrip(cells)))
    }

    @Test fun `옛 4칸 레코드도 읽는다`() {
        val old = "화|28|5|0;수|29|휴3|1"
        assertEquals(
            listOf(Cell("화", "28", "5", false, null, ""), Cell("수", "29", "휴3", true, null, "")),
            decodeStrip(old),
        )
    }

    @Test fun `깨진 레코드는 버리고 나머지는 산다`() {
        assertEquals(1, decodeStrip("화|28;수|29|휴3|1").size)
    }

    @Test fun `구분자가 값에 들어와도 깨지지 않는다`() {
        val c = Cell("금", "4", "충당|지6;", false, DutyType.STANDBY, "")
        assertEquals("충당지6", decodeStrip(encodeStrip(listOf(c)))[0].duty)
    }

    /* ── v1.6.92 ⑤: 칸마다 날짜를 실어 신선도를 판정한다 ─────────────── */

    @Test fun `날짜가 왕복한다`() {
        val e = java.time.LocalDate.of(2026, 9, 5).toEpochDay()
        val cells = listOf(Cell("토", "5", "휴2", true, DutyType.REST, "", e))
        assertEquals(e, decodeStrip(encodeStrip(cells))[0].epochDay)
        assertEquals(cells, decodeStrip(encodeStrip(cells)))
    }

    /** 날짜 없는 옛 레코드는 그대로 읽히고 `epochDay = null` — 워커가 한 번 돌 때까지 종전 동작 */
    @Test fun `날짜 없는 옛 레코드는 null`() {
        assertEquals(null, decodeStrip("화|28|5|0|MAIN_DAY|출근 07:47")[0].epochDay)
        assertEquals(null, decodeStrip("화|28|5|0")[0].epochDay)
    }

    /**
     * 낡은 스트립을 알아본다 — 자정 갱신이 밀렸을 때 **어제 근무를 오늘로 강조**하던 자리.
     * 위젯은 `cells.indexOfFirst { epochDay == 오늘 }` 로 오늘 칸을 찾고, −1이면 강조를 안 건다.
     */
    @Test fun `오늘 칸을 날짜로 찾는다`() {
        val today = java.time.LocalDate.of(2026, 9, 5)
        fun strip(start: java.time.LocalDate) = (0L..6L).map {
            val d = start.plusDays(it)
            Cell("·", "${d.dayOfMonth}", "1", false, DutyType.MAIN_DAY, "", d.toEpochDay())
        }
        // 어제 만들어진 스트립: 오늘은 1번 칸 (첫 칸이 아니다)
        assertEquals(1, strip(today.minusDays(1)).indexOfFirst { it.epochDay == today.toEpochDay() })
        // 일주일 넘게 묵은 스트립: 오늘 칸이 없다 → 강조 없음 + "갱신 필요"
        assertEquals(-1, strip(today.minusDays(9)).indexOfFirst { it.epochDay == today.toEpochDay() })
    }

    /* ── v1.6.93 ⑧: 4x1 칸(≈36dp)에 긴 근무명이 안 들어간다 ─────────── */

    /**
     * 충당 계열은 **두 줄**로, 네 글자 휴가류는 **두 글자**로. 종전엔 [DutyCode.display] 를
     * 그대로 넣어 `대기충당지2` 여섯 글자가 `대기충…` 으로 잘려 **다이아가 통째로 안 보였다.**
     */
    @Test fun `위젯 칸 표기는 긴 근무명을 접는다`() {
        fun label(raw: String) = cellLabel(DutyCode.parse(raw))
        assertEquals("대기\n지2", label("대기충당 지2"))
        assertEquals("돌봄", label("돌봄휴가"))
        assertEquals("동행", label("동행휴가"))
        // 짧은 건 손대지 않는다 — 달력과 같은 글자여야 한다.
        assertEquals("14", label("14"))
        assertEquals("휴2", label("휴2"))
        // 어느 표기든 **한 줄에 세 글자를 안 넘는다**(칸 폭이 그만큼뿐이다).
        for (line in label("대기충당 지2").split('\n')) {
            org.junit.Assert.assertTrue(line, line.length <= 3)
        }
    }

    /** 줄바꿈이 섞여도 직렬화가 안 깨진다 — 구분자는 `|`·`;` 뿐이다. */
    @Test fun `두 줄 표기가 왕복한다`() {
        val c = Cell("월", "7", "대기\n지2", false, DutyType.STANDBY, "출근 07:47", 20_000L)
        assertEquals(c, decodeStrip(encodeStrip(listOf(c)))[0])
    }

    @Test fun `모든 근무 타입에 색이 있다`() {
        for (t in DutyType.entries) {
            val (bg, fg) = com.sinjeong.crewcalendar.util.dutyPalette(t)
            org.junit.Assert.assertTrue("$t fg", fg != 0)
            if (t != DutyType.ETC) org.junit.Assert.assertTrue("$t bg", bg != 0)
        }
    }
}
