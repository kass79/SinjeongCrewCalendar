package com.sinjeong.crewcalendar.domain.model

import java.time.LocalDate
import java.time.LocalTime

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

    /** 편승 권장 탐색 창 — 출근시각 기준 "이만큼 전"에 양천구청역에서 타는 열차 (사용자 확정 규칙) */
    const val LEAD_MIN = 10
    const val LEAD_MAX = 19

    /**
     * 편승 권장 **탑승** 시각 (도착 기준 아님 — 소요시간 데이터가 없어도 되는 이유).
     *
     * `signOn`("7:43")에서 [LEAD_MAX]~[LEAD_MIN]분 전 창에 드는 양천구청역 신도림행 열차 중
     * **출근시각에 가장 가까운 것**(= 가장 늦은 것)을 고른다. 창 안에 열차가 없거나
     * 출근시각이 표(5~23시) 밖이면 null — 호출부는 이걸 "편승 정보 없음"으로 보여준다.
     *
     * 평일/휴일 구분은 `Bundled.isHolidayTimetable(date)` 재사용.
     */
    fun recommend(date: LocalDate, signOn: String?): LocalTime? {
        val hm = signOn?.split(":")?.mapNotNull { it.trim().toIntOrNull() }?.takeIf { it.size == 2 } ?: return null
        val on = hm[0] * 60 + hm[1]
        val holiday = Bundled.isHolidayTimetable(date)
        return ROWS.asSequence()
            .flatMap { r -> (if (holiday) r.holiday else r.weekday).asSequence().map { r.hour * 60 + it } }
            .filter { it in (on - LEAD_MAX)..(on - LEAD_MIN) }
            .maxOrNull()
            ?.let { LocalTime.of(it / 60, it % 60) }
    }
}
