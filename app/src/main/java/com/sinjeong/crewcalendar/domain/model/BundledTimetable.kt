package com.sinjeong.crewcalendar.domain.model

/** 신정지선(양천구청역) 신도림행 외선 시각표 — 편승 참고용. 시 -> 분 목록 (평일/휴일). */
object BundledTimetable {
    val TITLE = "신정지선 편승시각표"
    val SUBTITLE = "양천구청역 · 신도림행(외선)"
    data class Row(val hour: Int, val weekday: List<Int>, val holiday: List<Int>)
    val ROWS: List<Row> = listOf(
        Row(5,  listOf(36,52), listOf(36,52)),
        Row(6,  listOf(6,20,37,54), listOf(6,19,37,54)),
        Row(7,  listOf(5,15,24,34,44,53), listOf(5,15,24,34,44,53)),
        Row(8,  listOf(4,13,22,32,42,51), listOf(4,13,22,32,42,51)),
        Row(9,  listOf(1,11,20,30,40,50), listOf(1,11,20,30,40,50)),
        Row(10, listOf(1,12,20,30,41,51), listOf(1,10,20,30,41,51)),
        Row(11, listOf(1,10,20,30,41,51), listOf(1,10,20,30,41,51)),
        Row(12, listOf(1,11,21,31,41,51), listOf(1,11,21,31,41,51)),
        Row(13, listOf(1,11,21,31,41,51), listOf(1,11,21,31,41,51)),
        Row(14, listOf(1,11,21,31,41,51), listOf(1,11,21,31,41,51)),
        Row(15, listOf(1,11,21,31,41,51), listOf(1,11,21,31,41,51)),
        Row(16, listOf(1,11,21,30,40,50), listOf(1,11,21,30,40,50)),
        Row(17, listOf(0,10,20,30,41,50), listOf(0,10,20,30,41,50)),
        Row(18, listOf(1,10,20,31,40,50), listOf(1,10,20,31,40,50)),
        Row(19, listOf(0,10,20,30,41,50), listOf(0,10,20,30,41,50)),
        Row(20, listOf(0,10,22,30,42,52), listOf(1,12,23,32,41,52)),
        Row(21, listOf(2,11,20,32,42,53), listOf(2,11,20,32,42,53)),
        Row(22, listOf(5,15,26,38,48), listOf(5,15,26,37,52)),
        Row(23, listOf(2,17,31,48), listOf(8,23,39,53)),
    )
}
