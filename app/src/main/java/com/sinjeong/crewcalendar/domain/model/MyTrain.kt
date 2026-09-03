package com.sinjeong.crewcalendar.domain.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * **내 열번** — 지금 내가 타고 있을(또는 다음에 탈) 열차 번호 (v1.6.84).
 *
 * 본선 순환선 지도([MainLineMap])가 "내 열차"를 크게 그리는 데 쓴다.
 * 안드로이드를 하나도 안 부른다 — `MyTrainTest` 가 그대로 잠근다.
 *
 * ## ⚠ 열번별 시각을 **추정하지 않는다** (이 파일에서 가장 중요한 결정)
 *
 * 이 앱이 가진 것은 두 가지뿐이다:
 *  · [RouteTable] — 그 근무가 **어떤 열번들을 순서대로 잡는지** (시각 없음)
 *  · [MainLegs] — 전반/후반 **사업 시각 네 개**뿐 (`[전반시작, 전반종료, 후반시작, 후반종료]`)
 *
 * 즉 "14:51에는 2057" 같은 **열번별 시각표는 앱에 없다.** 사업 시간을 열번 수로 나눠
 * 추정할 수는 있지만 그건 지어내는 것이고, 회차·편승·주박이 섞인 실제 행로와 어긋난다.
 * 틀린 열번을 크게 강조하는 것은 아무것도 강조하지 않는 것보다 나쁘다.
 *
 * **그래서 이렇게 한다**: 이 함수는 지금 사업의 **열번 후보 목록**([MyTrain.nos])만 주고,
 * 그중 **실제로 실시간 API 에 살아 있는 열번**을 지도가 고른다. 서울시 API 가 "지금 어느
 * 열번이 굴러가는지"의 진실이므로, 추정 대신 **관측**으로 좁히는 셈이다.
 * 그 열번들이 하나도 안 보이면 아직 출고 전이거나 이미 입고한 것이고, 화면은 그렇게 말한다.
 */
data class MyTrain(
    /** 지금(또는 곧) 사업의 네 자리 열번들 — 행로표에 적힌 **순서 그대로**. */
    val nos: List<String>,
    /** true = 지금이 사업 시간 안 / false = 아직 시작 전 */
    val riding: Boolean,
    /** [riding] 이 false 일 때 그 사업 시작 시각 */
    val startAt: LocalTime?,
    /** 그 사업을 어디서 잡나 — `신도림`·`신정기지`·`군자기지`. 모르면 null */
    val place: String?,
    /** 그 사업이 **익일**인가(야간 후반) */
    val nextDay: Boolean,
)

/** 한 사업(전반 또는 후반)의 열번 다발과 시각. */
private data class Leg(
    val nos: List<String>,
    val start: LocalDateTime,
    val end: LocalDateTime,
)

/**
 * 지금 [now] 기준 내 열차 상황. **null = 오늘 본선에서 맡은 열차가 없다**
 * (휴무·비번·대기·운휴대기·지선 근무, 그리고 마지막 사업까지 끝난 뒤).
 *
 * 지선 근무도 null 이다 — 지선 열차는 이 지도가 아니라 [BranchLine] 지도의 몫이고,
 * 본선 순환선 위에 지선 열번을 얹으면 있지도 않은 자리에 점이 찍힌다.
 */
fun myTrainAt(duty: DutyCode, date: LocalDate, now: LocalDateTime): MyTrain? {
    val legs = mainLegsOf(duty, date) ?: return null
    // 지금 안에 들어 있는 사업이 먼저 — 없으면 아직 오지 않은 것 중 가장 이른 것
    legs.firstOrNull { !now.isBefore(it.start) && now.isBefore(it.end) }?.let { leg ->
        return MyTrain(leg.nos, riding = true, startAt = null, place = placeOf(leg),
            nextDay = leg.start.toLocalDate() != date)
    }
    val next = legs.filter { now.isBefore(it.start) }.minByOrNull { it.start } ?: return null
    return MyTrain(
        next.nos, riding = false, startAt = next.start.toLocalTime(), place = placeOf(next),
        nextDay = next.start.toLocalDate() != date,
    )
}

private fun placeOf(leg: Leg) = leg.nos.firstOrNull()?.let(BundledTimetable::boardingPlace)

/**
 * 그 날 그 근무의 본선 사업 둘(전반·후반). 본선 근무가 아니면 null.
 *
 * 열번이 하나도 없는 사업(운휴대기·주박처럼 네 자리 열번이 없는 칸)은 빼고, 둘 다 비면 null.
 */
private fun mainLegsOf(duty: DutyCode, date: LocalDate): List<Leg>? {
    val n = duty.number ?: return null
    if (duty.isBranch) return null                       // 지선은 이 지도의 몫이 아니다
    val night = duty.type == DutyType.MAIN_NIGHT
    if (!night && duty.type != DutyType.MAIN_DAY) return null   // 대기·휴무·비번·통상근무
    val combo = if (night) Bundled.comboOf(date) else null
    if (night && combo != null && RouteTable.isStandbyOnly(n, combo)) return null   // 운휴대기

    val holiday = Bundled.isHolidayTimetable(date)
    val assign = if (night) RouteTable.forMainNight(n, combo!!) else RouteTable.forMainDay(n, holiday)
    val times = if (night) MainLegs.forNight(n, combo!!) else MainLegs.forDay(n, holiday)
    if (assign == null || times == null || times.size < 4) return null

    val legs = listOfNotNull(
        leg(assign.firstHalf, times[0], times[1], date, secondOfNight = false),
        leg(assign.secondHalf, times[2], times[3], date, secondOfNight = night),
    )
    return legs.ifEmpty { null }
}

/**
 * 사업 한 칸 → [Leg]. 열번이 없으면 null.
 *
 * ⚠ **야간 후반은 익일 아침**이다([secondOfNight]) — `39 평평` 후반 `6:00~7:45` 는
 * 그 날 새벽이 아니라 **다음 날** 새벽이다. `(익일)` 표시가 화면 곳곳에 붙어 있는 그 규칙이다.
 */
private fun leg(
    raw: String, from: String, to: String, date: LocalDate, secondOfNight: Boolean,
): Leg? {
    val nos = trainNumbers(raw)
    if (nos.isEmpty()) return null
    val base = if (secondOfNight) date.plusDays(1) else date
    val s = legTime(from, base) ?: return null
    var e = legTime(to, base) ?: return null
    if (!e.isAfter(s)) e = e.plusDays(1)      // 자정을 넘겨 끝나는 사업(`23:19~25:00`)
    return Leg(nos, s, e)
}

/**
 * `"20:45"`·`"25:00"`·`"0:20"` → 그 날의 시각.
 *
 * ⚠ [MainLegs] 에는 **24시를 넘긴 표기**가 그대로 들어 있다(`"25:00"`·`"24:20"`·`"24:50"`).
 * 그건 "다음 날 1:00"이라는 뜻이므로 하루를 넘겨 접는다. `BundledTimetable.time()` 은
 * 이 표기에 null 을 주므로 여기서 따로 읽는다(그쪽은 알람용이라 24시 넘김이 필요 없었다).
 */
private fun legTime(raw: String, base: LocalDate): LocalDateTime? {
    val p = raw.split(':').mapNotNull { it.trim().toIntOrNull() }.takeIf { it.size == 2 } ?: return null
    val (h, m) = p
    if (h < 0 || m !in 0..59) return null
    return LocalDateTime.of(base, LocalTime.of(h % 24, m)).plusDays((h / 24).toLong())
}

/**
 * 사업 칸에서 **네 자리 열번만** 순서대로.
 *
 * ⚠ 야간표 첫 칸은 **인수인계 주석**일 수 있다 —
 * `"33DIA#5925출고교대·2015·2057"` 의 첫 토막은 열번이 아니라 누구에게서 넘겨받는지를 적은
 * 설명이고, 이 근무가 실제로 잡는 첫 열차는 `2015` 다(v1.6.78이 알람에서 물렸던 바로 그 자리).
 * 그래서 `·` 로 쪼갠 뒤 **네 자리 숫자인 토막만** 남긴다 — 잣대는 알람과 같은
 * [BundledTimetable.FOUR_DIGITS] 한 벌이다. `"군자기지 편승"`·`"홍대입구역주박"` 같은
 * 설명 토막과 `"2306(동대문교대)"` 같은 괄호 붙은 토막은 자연히 빠진다.
 */
internal fun trainNumbers(raw: String): List<String> =
    raw.split('·').map { it.trim() }.filter { BundledTimetable.FOUR_DIGITS.matches(it) }

/**
 * 오늘 이 근무가 잡는 **네 자리 열번 전부**(전반 + 후반) — **본선·지선을 가리지 않는다**.
 *
 * [myTrainAt] 은 "지금 어느 사업인지"까지 따지느라 지선을 걸러 내지만, 지도의 **강조**는
 * 그럴 필요가 없다. 사용자가 확정한 규칙은 하나다 — *"오늘 근무 열번이 어디에 있든 강조하라."*
 * 지선 열번은 대개 지선 전용역에 있어 순환선 지도에 안 나타나지만, **신도림에 와 있으면
 * 본선 역이라 그대로 잡힌다.** 그때 강조되는 편이 아무 말 없는 것보다 낫다.
 *
 * ⚠ 여기서도 **시각을 추정하지 않는다** — 열번 후보만 주고, 그중 실제로 API 에 살아 있는
 * 것을 지도가 고른다(이 파일 위쪽 KDoc 의 그 결정 그대로다).
 */
fun dutyTrainNumbers(duty: DutyCode, date: LocalDate): List<String> {
    val n = duty.number ?: return emptyList()
    val holiday = Bundled.isHolidayTimetable(date)
    val assign = when {
        duty.isBranch -> RouteTable.forBranch(n, holiday)
        duty.type == DutyType.MAIN_NIGHT ->
            Bundled.comboOf(date)?.let { RouteTable.forMainNight(n, it) }
        duty.type == DutyType.MAIN_DAY -> RouteTable.forMainDay(n, holiday)
        else -> null
    } ?: return emptyList()
    return (trainNumbers(assign.firstHalf) + trainNumbers(assign.secondHalf)).distinct()
}
