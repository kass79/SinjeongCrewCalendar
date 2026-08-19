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

    /**
     * 알람은 **편승 열차 출발 5분 전**에 울린다 (v1.6.30 사용자 확정).
     *
     * v1.6.29까지는 편승 열차 출발시각 그대로 걸었는데, 그러면 알림을 받는 순간 이미 열차가
     * 떠나는 시점이라 무의미하다. 사용자 원문: *"34다이아 신도림 17:44분 출발 근무면
     * 양천구청역에서 17시30분 편승 맞으니까 5분전 17시25분 알람을 예약해줘야지"*.
     * 지선(양천구청에서 바로 승무 시작)은 이미 "전반시작 5분 전 도착" 규칙이라 그대로 두고,
     * **편승 계열(본선 전반·후반)에만** 이 5분을 뺀다 — 지선에 두 번 적용되지 않게.
     * 열차를 고르는 창([WINDOW_EARLY_MIN]~[WINDOW_LATE_MIN])은 건드리지 않았다.
     */
    private const val BOARD_EARLY_MIN = 5L

    /**
     * **본선 후반사업이 신도림 교대가 아닌 다이아 → 사유** (v1.6.30). 여기 없는 다이아 = 신도림 교대.
     *
     * 근거는 이 파일 맨 아래 주석. 값은 그대로 사용자에게 보여 주는 사유 문구다.
     */
    private val SECOND_NOT_SINDORIM_WEEKDAY: Map<Int, String> = mapOf(
        1 to "군자기지 편승", // 후반 열번 "군자기지 편승·2265·2299"
        4 to "행로표와 시각표가 어긋남", // 행로표 후반 14:01인데 시각표는 15:36 (7번과 뒤바뀐 것으로 보임)
        6 to "신정기지 출고", // 6941 회송
        7 to "행로표와 시각표가 어긋남", // 행로표 후반 15:36인데 시각표는 14:01 (4번과 뒤바뀐 것으로 보임)
        13 to "군자기지 출고", // 1942 = 군자 입출고 회송
        14 to "군자기지 입출고", // 2952
        16 to "신정기지 출고", // 6945
        17 to "신정기지 출고", // 행로표 실물: 후반 17:24 신정기지 ○출고 → 5961 회송 (RouteTable의 전·후반 구분이 어긋나 있다)
        21 to "신정기지 출고", // 6953
    )

    private val SECOND_NOT_SINDORIM_HOLIDAY: Map<Int, String> = mapOf(
        1 to "군자기지에서 성수까지 편승",
        3 to "군자기지 입출고", // 2934
        5 to "신정기지 출고", // 5943
        20 to "신정기지 출고", // 6939
        21 to "성수 교대",
    )

    /**
     * **휴일 표의 `후반시작`은 신도림 출발이 아니라 양천구청 편승 출발시각이다** — 15분을 더해야 한다.
     *
     * 평일 표와 휴일 표가 서로 다른 기준을 쓴다(v1.6.30 행로표 스캔으로 확인).
     *  · 평일 12번: 편승 16:35 → 신도림 16:50, 시각표 후반시작 = **16:50**(신도림)
     *  · 휴일  2번: 편승 13:33 → 신도림 13:48, 시각표 후반시작 = **13:33**(양천구청)
     *  · 휴일 16번(15:54/16:09) · 휴일 25번(17:59/18:14)도 같다.
     * 열번 맞물림(다른 다이아의 종료시각과 Δ)도 휴일만 15분 어긋나 이 관측과 정확히 일치한다.
     */
    private const val HOLIDAY_SECOND_LEG_SHIFT_MIN = 15L

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
     * 신도림 [start] 출발에 맞춰 탈 편승 열차를 고르고([WINDOW_LATE_MIN]~[WINDOW_EARLY_MIN]분 전 중
     * 가장 늦은 편) **그 5분 전**을 알람으로 돌려준다. 전반·후반이 이 한 곳을 같이 쓴다.
     */
    private fun deadhead(start: LocalTime, holiday: Boolean): Advice {
        val board = departures(holiday)
            .filter { it.mins() in (start.mins() - WINDOW_EARLY_MIN)..(start.mins() - WINDOW_LATE_MIN) }
            .maxOrNull()
            ?: return Advice(
                null,
                "신도림 ${hm(start)} 출발에 맞춰 탈 양천구청역 열차가 " +
                    "${WINDOW_LATE_MIN}~${WINDOW_EARLY_MIN}분 전 구간에 없습니다. 행로표를 확인하세요.",
            )
        val alarm = board.minusMinutes(BOARD_EARLY_MIN)
        return Advice(alarm, "양천구청역 ${hm(board)} 편승 (신도림 ${hm(start)} 출발) · 알림 ${hm(alarm)}")
    }

    /**
     * 그 날 그 근무의 알람 권장 시각. **전반사업**([second] = false)은 세 갈래다(v1.6.27 사용자 확정):
     *
     *  · **지선** — 양천구청에서 바로 승무를 시작하므로 편승이 없다. 전반시작 **5분 전 도착**.
     *  · **본선 신도림 교대**([SINDORIM_GAP_MIN]) — 양천구청에서 신도림 출발 10~21분 전에
     *    떠나는 편승 열차 중 **가장 늦은 편**, 알람은 **그 5분 전**([BOARD_EARLY_MIN]).
     *  · **기지 출고** — 사용자 요청으로 **알람 없음**(신정기지·군자기지 모두).
     *
     * **후반사업**([second] = true)은 지선 주간(v1.6.29)에 더해 **본선 주간 중 신도림 교대인
     * 다이아**(v1.6.30)까지 계산한다. 신도림이 아닌 다이아는 [SECOND_NOT_SINDORIM_WEEKDAY] ·
     * [SECOND_NOT_SINDORIM_HOLIDAY]에 사유를 적어 두고 그 사유를 그대로 보여 준다.
     * 야간 후반은 익일 새벽이라 여전히 제외 — 근거는 이 파일 맨 아래 주석.
     *
     * 판별이 애매하면 알람을 걸지 않는다 — 틀린 시각을 주는 것이 최악이다.
     */
    fun advise(duty: DutyCode, date: LocalDate, second: Boolean = false): Advice {
        val holiday = Bundled.isHolidayTimetable(date)
        val row = Bundled.timeRowFor(duty, date)

        if (duty.type == DutyType.STANDBY || duty.type == DutyType.BRANCH_STANDBY)
            return Advice(null, "대기 근무는 맡은 열차가 없어 알람을 걸 수 없습니다.")

        // 야간 근무의 후반사업은 전부 **익일** 새벽이다. 예약 목록이 날짜 하나에 묶여 있어
        // 이 날짜의 알람으로는 못 건다(그리고 익일 새벽 시각은 편승 첫차보다 이른 경우가 많다).
        if (second && row?.overnight == true)
            return Advice(null, "야간 근무의 후반사업은 익일 새벽이라 이 날짜 알람으로는 걸 수 없습니다.")

        // A. 지선 — 사업시각("8:13#10:41" / "12:51-14:51")의 앞이 곧 양천구청 출발시각이다.
        //    후반도 같다: 지선 다이아의 후반시작은 전부 **다른 지선 다이아의 사업 종료시각과
        //    정확히 맞물린다**(지1 후반 12:51 = 지6 전반 종료 12:51 …). 인수인계 지점이 곧
        //    양천구청이므로 전반과 같은 "5분 전 도착" 규칙을 그대로 쓴다.
        //    `PatternTest.branch_second_leg_starts_are_handover_points`가 이 맞물림을 잠근다.
        if (duty.isBranch) {
            val leg = if (second) row?.secondLeg else row?.firstLeg
            val start = time(leg?.split('#', '-')?.firstOrNull())
                ?: return Advice(null, "이 근무는 승무 시작시각이 없어 알람을 걸 수 없습니다.")
            val at = start.minusMinutes(5)
            return Advice(at, "양천구청역 ${hm(at)} 도착 (${hm(start)} 출발 5분 전)")
        }

        // B'. 본선 후반 (v1.6.30) — 신도림 교대인 다이아만 계산하고, 아닌 다이아는 사유를 밝힌다.
        //     여기 오는 것은 주간 다이아뿐이다(야간은 위에서 "익일 새벽"으로 이미 걸렀다).
        if (second) {
            val num = duty.number
                ?: return Advice(null, "이 근무는 후반사업 다이아를 알 수 없어 알람을 걸 수 없습니다.")
            val why = (if (holiday) SECOND_NOT_SINDORIM_HOLIDAY else SECOND_NOT_SINDORIM_WEEKDAY)[num]
            if (why != null)
                return Advice(null, "후반사업이 신도림 교대가 아니라 ${why}(으)로 시작해 편승 알람이 없습니다.")
            val raw = time(MainLegs.forDay(num, holiday)?.getOrNull(2))
                ?: return Advice(null, "이 근무는 후반 사업시각이 없어 알람을 걸 수 없습니다. 행로표를 확인하세요.")
            // 휴일 표의 후반시작은 양천구청 편승 출발시각이라 신도림 출발은 그 15분 뒤다
            return deadhead(raw.plusMinutes(if (holiday) HOLIDAY_SECOND_LEG_SHIFT_MIN else 0L), holiday)
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

        return deadhead(start, holiday)
    }

    /* ─────────────────────────────────────────────────────────────────────────
     * ## 본선 후반사업 — 어디서 시작하는지 어떻게 갈랐나 (v1.6.30, 이번 판의 핵심)
     *
     * v1.6.29에서 "시작 지점이 제각각이라 자동 계산 불가"로 전부 껐던 것을 뒤집는다.
     * 사용자 지적대로 **계산 불가인 것만 걸러내고 나머지는 계산한다.**
     *
     * ### 1. 분류 기준 — [RouteTable]의 후반 첫 열번
     *
     * 전반에서 127건 전수 일치했던 그 신호가 후반에도 그대로 산다.
     *  · `2xxx`(29xx 제외) = **영업열차** → 이미 굴러가는 열차를 넘겨받는다 = **신도림 교대**
     *  · `5xxx`·`6xxx` = 신정기지 회송 → **기지 출고**
     *  · `19xx`·`29xx` = 군자기지 입출고 회송 → **군자기지 출고**
     *    (`43 HP` 전반의 `"…후반군자출고…"` + 후반 `"1936·2101"`이 직접 증거)
     *  · 텍스트(`"군자기지 편승"`·`"성수교대"`) = 적힌 그대로
     *
     * ### 2. 교차검증 ① 열번 맞물림 — 후반시작이 정말 교대 시각인가
     *
     * 후반 첫 열번을 **마지막으로 굴리는 다른 다이아**를 찾아 그 종료시각과 비교하면
     * 평일 19건·휴일 20건이 **예외 없이** 다음 두 값 중 하나로 떨어진다(±0분):
     *  · 앞 다이아의 **전반종료**와 맞물림 → 평일 Δ0 / 휴일 Δ+15
     *  · 앞 다이아의 **후반종료**(=퇴근, 신도림 교대 후 편승 15분)와 맞물림 → 평일 Δ+15 / 휴일 Δ+30
     * 지선 후반을 v1.6.29에서 확정한 방법과 같고, 우연히 이렇게 정렬될 수는 없다.
     * `PatternTest.mainSecondLeg_starts_are_handover_points`가 이 맞물림을 전수로 잠근다.
     *
     * ### 3. 교차검증 ② 행로표 스캔 직접 판독 (11건)
     *
     *  · `wd_12` 후반 = 편승 양천구청 16:35 → **신도림 16:50** 2296 출발, 시각표 값 16:50
     *  · `wd_2`(13:24→13:39) · `wd_19`(16:46→17:01) · `wd_20`(16:51→17:06) · `wd_29`(18:55→19:10)
     *    — 평일은 전부 시각표 값이 **신도림 출발**
     *  · `hol_2`(13:33→13:48) · `hol_16`(15:54→16:09) · `hol_25`(17:59→18:14)
     *    — 휴일은 전부 시각표 값이 **양천구청 편승 출발** → [HOLIDAY_SECOND_LEG_SHIFT_MIN]
     *  · `wd_17` 후반 = **신정기지 ○출고 17:24** → 5961 회송. [RouteTable]은 이 5961·2347을
     *    전반 칸에 넣어 두어 열번만 보면 `2395`(영업)로 잘못 읽힌다 → 제외 목록에 직접 넣었다.
     *  · `wd_4`(행로표 후반 14:01) · `wd_7`(행로표 후반 15:36)은 [MainLegs] 값이 서로 뒤바뀌어
     *    있다(15:36 / 14:01). 열번 맞물림도 바꿔 끼워야 맞는다 —
     *    **어느 쪽이 현행인지 확정 전이라 이 둘은 알람을 걸지 않는다.**
     *
     * ### 4. 야간은 그대로 제외
     *
     * 야간 후반은 전부 **익일** 새벽(5:30~5:52 다수)이라 이 날짜 알람으로 못 걸고,
     * 양천구청 신도림행 첫차가 5:36이라 편승 자체가 성립하지 않는 경우도 많다.
     *
     * ⚠ 판별이 애매하면 **켜지 않는다.** 틀린 시각으로 사람을 깨우는 것이 최악이다.
     * ───────────────────────────────────────────────────────────────────────── */
}
