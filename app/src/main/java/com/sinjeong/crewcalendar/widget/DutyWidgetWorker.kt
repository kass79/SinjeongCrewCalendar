package com.sinjeong.crewcalendar.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sinjeong.crewcalendar.domain.model.DutyType
import com.sinjeong.crewcalendar.domain.usecase.GetMonthScheduleUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 위젯 데이터 갱신 워커. WorkManager 주기 작업(6시간) + 앱 실행 시 1회 + onUpdate.
 * 오늘부터 7일치를 한 문자열로 직렬화해 GlanceState 에 저장한다.
 */
@HiltWorker
class DutyWidgetWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val getMonthSchedule: GetMonthScheduleUseCase,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = runCatching {
        val today = LocalDate.now()
        val week = (0L..6L).map { today.plusDays(it) }
        // 월 경계: 마지막 날이 다음 달이면 두 달을 합친다
        val days = getMonthSchedule(YearMonth.from(today)).first() +
            if (week.last().month != today.month) {
                getMonthSchedule(YearMonth.from(week.last())).first()
            } else emptyList()
        val byDate = days.associateBy { it.date }

        val dow = DateTimeFormatter.ofPattern("E", Locale.KOREAN)
        fun hhmm(h: Int, m: Int) = "%02d:%02d".format(h % 24, m)
        val cells = week.map { date ->
            val d = byDate[date]
            val red = date.dayOfWeek == DayOfWeek.SUNDAY || d?.holidayName != null
            val signOn = d?.signOn?.split(":")?.mapNotNull { it.trim().toIntOrNull() }?.takeIf { it.size == 2 }
            val time = when {
                signOn != null -> "출근 " + hhmm(signOn[0], signOn[1])
                d != null -> runCatching {
                    com.sinjeong.crewcalendar.domain.model.BundledTimetable.advise(d.duty, date).at
                }.getOrNull()?.let { "편승 " + hhmm(it.hour, it.minute) }.orEmpty()
                else -> ""
            }
            Cell(date.format(dow), date.dayOfMonth.toString(), d?.duty?.display.orEmpty(), red, d?.duty?.type, time)
        }
        val strip = encodeStrip(cells)
        // 근무 미선택·로그아웃이면 빈 문자열 → 위젯이 빈 상태 문구를 그린다
        val hasDuty = week.any { byDate[it]?.duty?.display?.isNotBlank() == true }

        val manager = GlanceAppWidgetManager(context)
        manager.getGlanceIds(DutyWidget::class.java).forEach { id ->
            updateAppWidgetState(context, id) { prefs ->
                prefs[DutyWidget.KEY_WEEK] = if (hasDuty) strip else ""
                prefs[DutyWidget.KEY_SUB] = subLine(today, byDate)
            }
            DutyWidget().update(context, id)
        }
        scheduleBoundaryRefresh(context, today, byDate[today]?.signOn)
        Result.success()
    }.getOrElse { Result.retry() }
}

/**
 * **출근시각 문자열 → 실제 시각. 이 패키지의 유일한 구현이다** (v1.6.33에 [Briefing]의 사본과 통합).
 *
 * `"7:47"` → 그 날 07:47. 야간 표기 `"25:20"`은 `atStartOfDay().plusHours(25)`라
 * **익일 01:20으로 자연히 넘어간다** — `LocalTime.parse`를 쓰면 여기서 죽는다.
 * 브리핑 예약(출근 1시간 전)과 위젯 부제·경계 갱신이 같은 값을 봐야 하므로 두 벌로 두지 말 것.
 */
internal fun signOnAt(date: LocalDate, s: String?): LocalDateTime? {
    val hm = s?.split(":")?.mapNotNull { it.trim().toIntOrNull() }?.takeIf { it.size == 2 } ?: return null
    return date.atStartOfDay().plusHours(hm[0].toLong()).plusMinutes(hm[1].toLong())
}

/**
 * **"지금 알려줄 날" = 오늘 / 내일.** 오늘 출근시각을 지났으면(또는 오늘이 비번·휴무라 출근이
 * 아예 없으면) 내일로 넘어간다. 모레까지는 안 본다.
 *
 * 위젯 부제([subLine])와 달력 상단 오늘 카드가 **같은 규칙을 봐야 해서** 여기 한 곳에만 둔다
 * (v1.6.41에 카드가 생기며 뽑아냈다 — 두 벌로 두면 위젯은 내일인데 카드는 오늘인 상태가 난다).
 */
internal fun focusDate(today: LocalDate, todaySignOn: String?): LocalDate =
    if (signOnAt(today, todaySignOn)?.isAfter(LocalDateTime.now()) == true) today else today.plusDays(1)

/**
 * 위젯 부제 한 줄. 보여줄 날은 [focusDate]가 정한다.
 */
internal fun subLine(today: LocalDate, byDate: Map<LocalDate, com.sinjeong.crewcalendar.domain.model.DaySchedule>): String {
    val date = focusDate(today, byDate[today]?.signOn)
    val head = if (date == today) "오늘" else "내일"
    val t = byDate[date] ?: return ""
    t.signOn?.let { return "$head 출근 $it" }
    return when {
        t.duty.type == DutyType.POST_NIGHT -> "$head 비번"
        t.duty.isRest -> "$head 휴무"
        t.duty.display.isNotBlank() -> "$head ${t.duty.displayLong}"
        else -> ""
    }
}

/**
 * 다음 경계(오늘 출근시각 / 자정)에 한 번 더 갱신. 워커가 실행될 때마다 다음 1건만 예약해 스스로 이어진다.
 * ponytail: WorkManager라 도즈에서 몇 분 늦을 수 있다 — 정확알람이 필요해지면 BriefingAlarm처럼 AlarmManager로.
 */
private fun scheduleBoundaryRefresh(context: Context, today: LocalDate, todaySignOn: String?) {
    val now = LocalDateTime.now()
    val next = listOfNotNull(signOnAt(today, todaySignOn), today.plusDays(1).atStartOfDay())
        .filter { it.isAfter(now) }
        .minOrNull() ?: return
    androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
        "widget_boundary", androidx.work.ExistingWorkPolicy.REPLACE,
        androidx.work.OneTimeWorkRequestBuilder<DutyWidgetWorker>()
            .setInitialDelay(
                java.time.Duration.between(now, next).toMinutes().coerceAtLeast(1),
                java.util.concurrent.TimeUnit.MINUTES,
            )
            .build(),
    )
}
