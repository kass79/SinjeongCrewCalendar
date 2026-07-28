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
import java.time.DayOfWeek
import java.time.LocalDate
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
        val todaySignOn = byDate[today]?.signOn

        val manager = GlanceAppWidgetManager(context)
        manager.getGlanceIds(DutyWidget::class.java).forEach { id ->
            updateAppWidgetState(context, id) { prefs ->
                prefs[DutyWidget.KEY_WEEK] = if (hasDuty) strip else ""
                prefs[DutyWidget.KEY_SUB] = todaySignOn?.let { "오늘 출근 $it" }.orEmpty()
            }
            DutyWidget().update(context, id)
        }
        Result.success()
    }.getOrElse { Result.retry() }
}
