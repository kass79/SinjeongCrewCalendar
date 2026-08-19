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

    /** 알람 권장 결과. [at]이 null이면 알람을 걸 수 없고 [text]가 그 사유다. */
    data class Advice(val at: LocalTime?, val text: String)

    /**
     * 본선에서 "출근 → 전반시작" 간격이 이 값이면 **신도림역에서 교대**하는 근무다(= 편승 필요).
     *
     * ⚠ 이 45분이 B(편승 알람)와 C(기지 출고 — 알람 없음)를 가르는 **유일한 판별 기준**이다.
     * 행로표가 스캔 이미지라 역명을 코드로 읽을 수 없어서 데이터에서 찾아낸 대리 지표이고,
     * v1.6.27에서 아래 네 갈래로 검증했다(전 다이아 127건 = 주간 평일29·휴일25 + 야간 73):
     *
     *  ① 행로표 스캔 직접 판독 — 45분인 `wd_12`(8:07)·`wd_20`(9:49)·`pp_38`(19:53)·
     *     `hp_43`(20:12)·`pp_45`(21:03)는 전부 그 시각이 **신도림** 열에 찍혀 있고,
     *     60분인 `wd_9`(8:02)·`hol_4`(8:30)는 **신정기지** 열에 ○(출고) 표시다.
     *  ② 열번 상관 — `RouteTable`의 전반 첫 열번이 5xxx·6xxx(회송=출고)인 다이아와
     *     간격 60분인 다이아가 **127건 전부 일치**(불일치 0건). 45분 쪽은 전부 2xxx 영업열차.
     *  ③ 행로표의 편승 점선은 전부 **정확히 15분**(양천구청↔신도림)이고 통계칸 `편승` 합계와
     *     맞아떨어진다(`wd_12` = 15분×3 = 0:45). 15분은 사용자 확정 창 10~19분의 정중앙이다.
     *  ④ 45분 다이아의 편승 탑승시각(전반시작 −10~19분)은 출근 +26~35분인데,
     *     이는 지선의 "출근 +30분에 양천구청 출발"과 같은 준비시간 구조다.
     *
     * "동대문승무원과 교대"·"대림승무원과 교대"는 교대 **장소**가 아니라 상대 사업소 이름이다
     * (①에서 `wd_12`·`wd_20`·`pp_45` 스캔으로 확인 — 실제 교대 지점은 셋 다 신도림).
     * 군자기지는 후반·근무 중간에만 나오고 전반시작 지점인 다이아는 없다.
     */
    private const val SINDORIM_GAP_MIN = 45

    /**
     * 편승 열차를 고르는 창 — 신도림 출발 **10~21분 전** (v1.6.29 사용자 확정).
     *
     * v1.6.27까지 19분 → v1.6.28 20분 → **v1.6.29 21분**. 19분이던 시절 "1분 차이로 창을 벗어난"
     * 야간 7조합 중 20분으로 6조합(`37 평평·평휴`, `42 평평·평휴`, `50 휴휴·휴평`)이 살아났고,
     * 마지막 하나였던 **`46 휴평`(신도림 21:41 출발 / 앞 열차 21:20 = 21분 전)**이 21분으로 살아난다.
     * 이로써 v1.6.27에서 "편승 창이 비어 알람 없음"이던 야간 조합은 **0건**이다.
     *
     * 창을 넓혀도 다른 다이아의 권장시각은 안 바뀐다 — `maxOrNull()`(가장 늦은 편)이라
     * 창의 늦은 쪽 끝([WINDOW_LATE_MIN])이 그대로면 이미 열차가 있던 다이아는 같은 열차를 고른다.
     * `PatternTest.widenedWindow_never_moves_an_existing_recommendation`이 전 다이아로 증명한다.
     */
    private const val WINDOW_EARLY_MIN = 21
    private const val WINDOW_LATE_MIN = 10

    private fun LocalTime.mins() = hour * 60 + minute

    private fun hm(t: LocalTime) = "%d:%02d".format(t.hour, t.minute)

    /** `"8:13"` → LocalTime. `"25:20"` 같은 24시+ 표기와 빈 값은 null (알람을 걸 수 없다) */
    private fun time(raw: String?): LocalTime? {
        val p = raw?.split(':')?.mapNotNull { it.trim().toIntOrNull() }?.takeIf { it.size == 2 } ?: return null
        return if (p[0] in 0..23 && p[1] in 0..59) LocalTime.of(p[0], p[1]) else null
    }

    /** 그 날 양천구청역 신도림행 출발시각 전체 */
    private fun departures(holiday: Boolean): List<LocalTime> =
        ROWS.flatMap { r -> (if (holiday) r.holiday else r.weekday).map { LocalTime.of(r.hour, it) } }

    /**
     * 그 날 그 근무의 알람 권장 시각. 세 갈래다(v1.6.27 사용자 확정):
     *
     *  · **지선** — 양천구청에서 바로 승무를 시작하므로 편승이 없다. 전반시작 **5분 전 도착**.
     *  · **본선 신도림 교대**([SINDORIM_GAP_MIN]) — 양천구청에서 신도림 출발 10~20분 전에
     *    떠나는 편승 열차 중 **가장 늦은 편**.
     *  · **기지 출고** — 사용자 요청으로 **알람 없음**(신정기지·군자기지 모두).
     *
     * 판별이 애매하면 알람을 걸지 않는다 — 틀린 시각을 주는 것이 최악이다.
     */
    fun advise(duty: DutyCode, date: LocalDate): Advice {
        val holiday = Bundled.isHolidayTimetable(date)
        val row = Bundled.timeRowFor(duty, date)

        if (duty.type == DutyType.STANDBY || duty.type == DutyType.BRANCH_STANDBY)
            return Advice(null, "대기 근무는 맡은 열차가 없어 알람을 걸 수 없습니다.")

        // A. 지선 — firstLeg("8:13#10:41")의 앞이 곧 양천구청 출발시각이다. 역산하지 않는다.
        if (duty.isBranch) {
            val start = time(row?.firstLeg?.substringBefore('#'))
                ?: return Advice(null, "이 근무는 승무 시작시각이 없어 알람을 걸 수 없습니다.")
            val at = start.minusMinutes(5)
            return Advice(at, "양천구청역 ${hm(at)} 도착 (${hm(start)} 출발 5분 전)")
        }

        // 본선 — 전반시작이 신도림인지 기지인지가 갈림길
        val n = duty.number
        if (duty.type == DutyType.MAIN_NIGHT && n != null && RouteTable.isStandbyOnly(n, Bundled.comboOf(date)))
            return Advice(null, "운휴대기 근무라 맡은 열차가 없습니다.")
        val legs = when (duty.type) {
            DutyType.MAIN_DAY -> n?.let { MainLegs.forDay(it, holiday) }
            DutyType.MAIN_NIGHT -> n?.let { MainLegs.forNight(it, Bundled.comboOf(date)) }
            else -> null
        }
        val start = time(legs?.firstOrNull())
        val signOn = time(row?.signOn)
        if (start == null || signOn == null)
            return Advice(null, "이 근무는 사업시각이 없어 알람을 걸 수 없습니다. 행로표를 확인하세요.")

        if (start.mins() - signOn.mins() != SINDORIM_GAP_MIN)
            return Advice(null, "기지에서 열차를 끌고 나오는 근무(출고)라 편승 알람이 없습니다.")

        val at = departures(holiday)
            .filter { it.mins() in (start.mins() - WINDOW_EARLY_MIN)..(start.mins() - WINDOW_LATE_MIN) }
            .maxOrNull()
            ?: return Advice(
                null,
                "신도림 ${hm(start)} 출발에 맞춰 탈 양천구청역 열차가 " +
                    "${WINDOW_LATE_MIN}~${WINDOW_EARLY_MIN}분 전 구간에 없습니다. 행로표를 확인하세요.",
            )
        return Advice(at, "양천구청역 ${hm(at)} 편승 탑승 (신도림 ${hm(start)} 출발)")
    }
}
