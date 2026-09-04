package com.sinjeong.crewcalendar.widget

import com.sinjeong.crewcalendar.domain.model.DutyType

/**
 * 위젯 한 칸. `KEY_WEEK` 한 레코드 = `요일|일자|근무|빨강|타입|시각`.
 * v1.6.88에서 뒤 두 칸(타입·시각)을 붙였다 — 4칸짜리 옛 레코드는 type=null·time="" 로 읽는다.
 * time 은 접두어 포함("출근 07:47" / "편승 12:36" / "").
 */
data class Cell(
    val dow: String, val day: String, val duty: String, val red: Boolean,
    val type: DutyType?, val time: String,
)

private val SEP = Regex("[|;]")

fun encodeStrip(cells: List<Cell>): String = cells.joinToString(";") { c ->
    listOf(c.dow, c.day, c.duty, if (c.red) "1" else "0", c.type?.name.orEmpty(), c.time)
        .joinToString("|") { it.replace(SEP, "") }
}

fun decodeStrip(s: String): List<Cell> = s.split(";").mapNotNull { rec ->
    val p = rec.split("|")
    if (p.size < 4) return@mapNotNull null
    Cell(
        dow = p[0], day = p[1], duty = p[2], red = p[3] == "1",
        type = p.getOrNull(4)?.let { n -> DutyType.entries.firstOrNull { it.name == n } },
        time = p.getOrElse(5) { "" },
    )
}
