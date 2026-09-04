package com.sinjeong.crewcalendar.widget

import com.sinjeong.crewcalendar.domain.model.DutyType

/**
 * 위젯 한 칸. `KEY_WEEK` 한 레코드 = `요일|일자|근무|빨강|타입|시각|epochDay`.
 * v1.6.88에서 타입·시각을, **v1.6.92에서 [epochDay]** 를 붙였다 — 옛 레코드는 뒤 칸이 없는 대로 읽는다.
 * time 은 접두어 포함("출근 07:47" / "편승 12:36" / "").
 *
 * ## 왜 칸마다 날짜를 싣나 (v1.6.92 ⑤)
 *
 * 종전엔 칸에 날짜가 없어 **첫 칸을 무조건 오늘로 칠했다.** 자정 갱신(WorkManager)이 도즈에
 * 밀리면 그림은 어제 것 그대로인데 첫 칸만 "오늘"로 강조돼 **어제 근무를 오늘로 읽는다** —
 * 이 앱에서 그건 곧 지각이다. 칸에 날짜가 있으면 ① 오늘 강조가 스스로 옳은 칸을 찾아가고
 * ② 어느 칸도 오늘이 아니면 위젯이 "갱신 필요"라고 말할 수 있다.
 */
data class Cell(
    val dow: String, val day: String, val duty: String, val red: Boolean,
    val type: DutyType?, val time: String,
    /** 이 칸이 가리키는 날 (`LocalDate.toEpochDay()`). 옛 레코드는 null */
    val epochDay: Long? = null,
)

private val SEP = Regex("[|;]")

fun encodeStrip(cells: List<Cell>): String = cells.joinToString(";") { c ->
    listOf(
        c.dow, c.day, c.duty, if (c.red) "1" else "0", c.type?.name.orEmpty(), c.time,
        c.epochDay?.toString().orEmpty(),
    ).joinToString("|") { it.replace(SEP, "") }
}

fun decodeStrip(s: String): List<Cell> = s.split(";").mapNotNull { rec ->
    val p = rec.split("|")
    if (p.size < 4) return@mapNotNull null
    Cell(
        dow = p[0], day = p[1], duty = p[2], red = p[3] == "1",
        type = p.getOrNull(4)?.let { n -> DutyType.entries.firstOrNull { it.name == n } },
        time = p.getOrElse(5) { "" },
        epochDay = p.getOrNull(6)?.toLongOrNull(),
    )
}
