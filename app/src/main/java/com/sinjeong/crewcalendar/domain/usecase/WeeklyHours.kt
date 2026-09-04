package com.sinjeong.crewcalendar.domain.usecase

import com.sinjeong.crewcalendar.domain.model.Bundled
import com.sinjeong.crewcalendar.domain.model.DaySchedule
import com.sinjeong.crewcalendar.domain.model.DutyCode
import com.sinjeong.crewcalendar.domain.model.DutyType
import com.sinjeong.crewcalendar.domain.model.RouteTable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * **주52시간 주별 근무시간** (v1.6.89) — 달력 상태(`List<DaySchedule>`)에서 그 달의 주별 합계를 만든다.
 *
 * 저장소도 쿼리도 없다. 근무변경·메모를 저장하면 달력 상태가 바뀌고 이 값이 곧바로 따라 나온다.
 *
 * ## 계산 규칙 (사용자 확정 — 설계서 `2026-09-04-widget-map-delay-design.md` E절)
 *
 * | 근무 | 시간 |
 * |---|---|
 * | 본선 주간·야간, 지선 | 행로표 **`계`** ([TrainAssignment.totalWorkTime]) 그대로 |
 * | 대기(대1~13 · 지대) | 시각표 **출퇴근 차** (대1 7:00~16:00 = 9.0h) |
 * | 휴무·비번·연차·보상·대휴·촉연·기타휴가 | 0 |
 * | 휴일 운휴 다이아(본선 주간 26~29) | 0 — 열차가 안 다니는 날이다 |
 * | 충당·대기충당·교체·지근 + 다이아 | **채운 근무**의 시간 |
 * | 교육·회행·번호 없는 지근·직접입력 | **미정** = `null` → [Week.excluded] 에 표시명 |
 *
 * ⚠ **야간은 시작일에 전부** 붙는다. 익일 비번(`~`)은 0이라 다음 주로 새지 않는다 —
 * 근무표가 야간 한 덩어리를 시작일 기준으로 세는 것과 같다.
 *
 * ⚠ 안드로이드 import 0 (순수 도메인) — 테스트가 JUnitCore로 곧바로 돈다.
 */
object WeeklyHours {

    /** 주52시간. 이 값을 **넘는** 주만 빨강 */
    const val LIMIT_MIN = 52 * 60

    /**
     * 그 달의 한 주(월~일).
     * [from]/[to]는 **표시용**이라 그 달 안으로 잘린다(라벨의 `(1~6일)`이 여기서 나온다).
     * [minutes]는 그와 달리 **월~일 7일 전부**를 센다 — 달 경계 주를 그 달 안에서만 세면
     * 52시간을 넘겨도 초과가 안 뜬다(v1.6.92 ⑦).
     * [partial]은 7일 중 근무를 모르는 날이 있다는 뜻(인접 달을 아직 못 읽음) → **초과 판정에서 뺀다.**
     * [excluded]는 시간 미정이라 0으로 둔 근무의 표시명 — 중복 없이 나온 순서대로.
     */
    data class Week(
        val index: Int,
        val from: LocalDate,
        val to: LocalDate,
        val minutes: Int,
        val excluded: List<String>,
        val partial: Boolean = false,
    ) {
        /** 52시간을 넘겼다고 **단정할 수 있는가**. 부분 집계면 넘겨도 단정하지 않는다 */
        val over: Boolean get() = !partial && minutes > LIMIT_MIN
    }

    /**
     * 시간을 하나라도 계산할 수 있는 근무표인가 (v1.6.92 ⑥).
     *
     * 통상근무(`주간`)·4조2교대(`주간`/`야간`)는 낱말 근무라 [DutyCode.parse]가 번호를 못 만들고,
     * 행로표·시각표 어느 갈래에도 안 걸려 **모든 근무일이 미정**이 된다 → 매주 `0.0h`.
     * 0.0h를 사실처럼 보여 주는 것이 아무것도 안 보여 주는 것보다 나쁘므로 **줄 자체를 감춘다.**
     * 근무일이 아예 없는 달(전부 휴가)은 0h가 맞는 답이라 그대로 보여 준다.
     */
    fun computable(days: List<DaySchedule>): Boolean {
        val work = days.filter { it.duty.isWorkDay }
        return work.isEmpty() || work.any { minutesOf(it) != null }
    }

    /**
     * 그 달의 주(월~일) 목록. 1일이 속한 주의 월요일부터 7일씩. [Week.index]는 1부터.
     *
     * [days]에 **인접 달이 섞여 있어도 된다** — 경계 주는 거기서 나머지 날을 채운다.
     * 못 채우면 [Week.partial]이 켜진다.
     */
    fun compute(month: YearMonth, days: List<DaySchedule>): List<Week> {
        val first = month.atDay(1)
        val last = month.atEndOfMonth()
        val byDate = days.associateBy { it.date }
        val weeks = mutableListOf<Week>()
        var monday = first.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        while (monday <= last) {
            var minutes = 0
            var missing = false
            val excluded = LinkedHashSet<String>()
            // 합계는 월~일 7일 전부 — 표시 범위(from/to)와 달리 달 경계에서 자르지 않는다.
            for (i in 0L..6L) {
                val day = byDate[monday.plusDays(i)]
                if (day == null) { missing = true; continue }
                val m = minutesOf(day)
                if (m == null) excluded += day.duty.display else minutes += m
            }
            weeks += Week(
                index = weeks.size + 1,
                from = maxOf(monday, first),
                to = minOf(monday.plusDays(6), last),
                minutes = minutes,
                excluded = excluded.toList(),
                partial = missing,
            )
            monday = monday.plusDays(7)
        }
        return weeks
    }

    /** 하루 근무시간(분). 야간은 시작일에 전부. 모르면 null(→ [Week.excluded]) */
    fun minutesOf(day: DaySchedule): Int? = minutesOf(day.duty, day.date)

    private fun minutesOf(duty: DutyCode, date: LocalDate): Int? {
        // ① 쉬는 날 — 휴가류는 전부 REST 로 파싱돼 여기서 걸린다(`DutyCode.REST_OPTIONS`).
        if (duty.type == DutyType.REST || duty.type == DutyType.BRANCH_REST ||
            duty.type == DutyType.POST_NIGHT
        ) return 0
        // 묶음 이름 `기타휴가`는 저장될 수 없는 값이지만 들어오면 쉬는 날로 본다.
        if (duty.raw == DutyCode.ETC_GROUP) return 0

        // ② 충당·대기충당·교체·지근 — **대신 뛰는 다이아**로 계산한다. 번호가 없으면 미정.
        //    `DutyCode.parse` 가 이미 타입·번호를 물려주지만 깨진 다이아(`지근 xxx`)는 ETC 로 남는다.
        if (duty.fill != null) {
            val dia = DutyCode.parse(duty.diaRaw)
            return if (dia.type == DutyType.ETC) null else minutesOf(dia, date)
        }

        // ③ 행로표 `계` — 있으면 그것이 정답이다(운전+준비+대기+편승+정리+야간 합산).
        val n = duty.number
        val holiday = Bundled.isHolidayTimetable(date)
        val route = when (duty.type) {
            DutyType.MAIN_DAY -> n?.let { RouteTable.forMainDay(it, holiday) }
            // 운휴대기(33~35 휴휴)도 그 표의 계를 그대로 쓴다 — 대기만 해도 근무시간이다.
            DutyType.MAIN_NIGHT -> n?.let { RouteTable.forMainNight(it, Bundled.comboOf(date)) }
            DutyType.BRANCH, DutyType.BRANCH_NIGHT -> n?.let { RouteTable.forBranch(it, holiday) }
            else -> null
        }
        hhmm(route?.totalWorkTime)?.let { return it }

        // ④ 행로표가 없는 근무(대기 대N·지대N) — 시각표 출퇴근 차.
        Bundled.timeRowFor(duty, date)?.let { row ->
            val on = hhmm(row.signOn)
            val off = hhmm(row.signOff)
            // 야간 대기(대11 17:00~7:30)는 퇴근이 이튿날이라 음수가 나온다 → 하루를 더한다.
            if (on != null && off != null) return Math.floorMod(off - on, 24 * 60)
        }

        // ⑤ **휴일 운휴 다이아**(본선 주간 26~29) — 휴일 시각표에도 행로표에도 없는 근무일이다.
        //    열차가 안 다니는 날이라 0이지 미정이 아니다(달력 칸이 그 자리에 `운휴`라고 적는다).
        //    ⚠ 같은 조건이 `MainCalendarScreen`·`MonthImage` 의 `운휴` 표기에도 있다 — 세 곳이 같아야 한다.
        if (duty.isWorkDay && n != null && holiday) return 0

        // ⑥ 교육·회행·번호 없는 지근·직접입력 — 시간 미정.
        return null
    }

    /** `"1주 50.1h"`. 달에 걸쳐 잘린 주만 날짜 범위를 붙인다 — `"1주(1~6일) 12.5h"` */
    fun label(w: Week): String {
        val span = if (ChronoUnit.DAYS.between(w.from, w.to) == 6L) ""
        else "(${w.from.dayOfMonth}~${w.to.dayOfMonth}일)"
        return "${w.index}주$span ${String.format(Locale.US, "%.1f", w.minutes / 60.0)}h"
    }

    /** `"10:04"`·`"25:20"` → 분. 24시+ 표기(`MyTrain.legTime` 과 같은 규칙)도 그대로 읽는다 */
    private fun hhmm(raw: String?): Int? {
        val p = raw?.split(':')?.mapNotNull { it.trim().toIntOrNull() }?.takeIf { it.size == 2 } ?: return null
        val (h, m) = p
        return if (h < 0 || m !in 0..59) null else h * 60 + m
    }
}
