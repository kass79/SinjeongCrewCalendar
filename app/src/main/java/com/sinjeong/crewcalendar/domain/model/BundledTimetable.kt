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

    /**
     * 편승 권장 **탑승** 시각 — 규칙 확정 대기 중이라 지금은 항상 null이다(v1.6.26).
     *
     * ⚠ 처음엔 "출근시각 −10~−19분"으로 구현했다가 되돌렸다. 사용자 정정:
     * 기준은 출근시각이 아니라 **행로표의 신도림역 근무시작 출발시각**이고,
     * 지선은 그 출발 5분 전이 기준이다. 정확한 규칙은 확정 대기.
     *
     * 확정되면 여기만 채우고 `MainCalendarScreen`의 `DeadheadAlarmChip` 호출 한 줄을 되살리면 된다.
     * 예약·취소·부팅복구 인프라(`DeadheadAlarm`)와 UI는 이미 완성돼 있다.
     *
     * 채울 때 쓸 수 있는 기준시각 데이터 (v1.6.26 실측):
     *  · 지선  `Bundled.BRANCH_WEEKDAY/HOLIDAY[다이아].firstLeg` = `"8:13#10:41"` 의 앞부분.
     *          13개 다이아 평일·휴일 전부 **출근 +30분 정확히** (예외 0건).
     *  · 본선  `MainLegs.forDay(n, holiday)[0]` / `MainLegs.forNight(n, combo)[0]`.
     *          출근과의 간격이 **45분 또는 60분으로 갈린다** — 출근시각으로 역산하면 안 된다.
     *  · 기준시각이 없는 근무: 대기 계열 12종(지대1·2·11, 대1~6·11~13)과
     *          본선 야간 33·34·35의 휴휴 조합(운휴대기).
     */
    @Suppress("UNUSED_PARAMETER")
    fun recommend(date: LocalDate, signOn: String?): LocalTime? = null
}
