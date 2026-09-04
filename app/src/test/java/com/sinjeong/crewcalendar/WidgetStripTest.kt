package com.sinjeong.crewcalendar

import com.sinjeong.crewcalendar.domain.model.DutyType
import com.sinjeong.crewcalendar.widget.Cell
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

    @Test fun `모든 근무 타입에 색이 있다`() {
        for (t in DutyType.entries) {
            val (bg, fg) = com.sinjeong.crewcalendar.util.dutyPalette(t)
            org.junit.Assert.assertTrue("$t fg", fg != 0)
            if (t != DutyType.ETC) org.junit.Assert.assertTrue("$t bg", bg != 0)
        }
    }
}
