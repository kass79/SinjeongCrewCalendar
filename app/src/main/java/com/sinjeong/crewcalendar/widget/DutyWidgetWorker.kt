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
        val strip = week.joinToString(";") { date ->
            val d = byDate[date]
            val red = date.dayOfWeek == DayOfWeek.SUNDAY || d?.holidayName != null
            // 구분자와 충돌할 값은 애초에 없지만, 들어와도 파싱이 깨지지 않게 제거
            val duty = d?.duty?.display.orEmpty().replace(Regex("[|;]"), "")
            "${date.format(dow)}|${date.dayOfMonth}|$duty|${if (red) 1 else 0}"
        }
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
 * 위젯 부제 한 줄. 오늘 출근시각을 지났으면(또는 오늘이 비번·휴무라 출근이 없으면) 내일을 보여준다.
 * 모레까지는 안 본다 — 이틀 뒤 정보는 위젯 스트립 7칸에 이미 보인다.
 */
internal fun subLine(today: LocalDate, byDate: Map<LocalDate, com.sinjeong.crewcalendar.domain.model.DaySchedule>): String {
    val todayOn = signOnAt(today, byDate[today]?.signOn)
    if (todayOn != null && LocalDateTime.now().isBefore(todayOn)) return "오늘 출근 ${byDate[today]?.signOn}"

    val t = byDate[today.plusDays(1)] ?: return ""
    t.signOn?.let { return "내일 출근 $it" }
    return when {
        t.duty.type == DutyType.POST_NIGHT -> "내일 비번"
        t.duty.isRest -> "내일 휴무"
        t.duty.display.isNotBlank() -> "내일 ${t.duty.displayLong}"
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
