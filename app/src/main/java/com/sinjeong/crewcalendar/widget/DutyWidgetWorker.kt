package com.sinjeong.crewcalendar.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sinjeong.crewcalendar.domain.usecase.GetMonthScheduleUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 위젯 데이터 갱신 워커. WorkManager 주기 작업(예: 6시간) + 앱 실행 시 1회.
 */
@HiltWorker
class DutyWidgetWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val getMonthSchedule: GetMonthScheduleUseCase,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = runCatching {
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)
        val days = getMonthSchedule(YearMonth.from(today)).first() +
            if (tomorrow.month != today.month) getMonthSchedule(YearMonth.from(tomorrow)).first() else emptyList()

        val todayDay = days.firstOrNull { it.date == today }
        val tomorrowDay = days.firstOrNull { it.date == tomorrow }

        val manager = GlanceAppWidgetManager(context)
        val fmt = DateTimeFormatter.ofPattern("M/d (E)", Locale.KOREAN)
        manager.getGlanceIds(DutyWidget::class.java).forEach { id ->
            updateAppWidgetState(context, id) { prefs ->
                prefs[DutyWidget.KEY_DATE] = today.format(fmt)
                prefs[DutyWidget.KEY_TODAY] = todayDay?.duty?.display ?: "—"
                prefs[DutyWidget.KEY_TODAY_SUB] =
                    todayDay?.signOn?.let { "출근 $it" } ?: todayDay?.memo.orEmpty()
                prefs[DutyWidget.KEY_TOMORROW] = tomorrowDay?.duty?.display ?: "—"
                prefs[DutyWidget.KEY_TOMORROW_SUB] =
                    tomorrowDay?.signOn?.let { "출근 $it" } ?: tomorrowDay?.memo.orEmpty()
            }
            DutyWidget().update(context, id)
        }
        Result.success()
    }.getOrElse { Result.retry() }
}
