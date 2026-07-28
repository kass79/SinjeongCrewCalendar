package com.sinjeong.crewcalendar.widget

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sinjeong.crewcalendar.MainActivity
import com.sinjeong.crewcalendar.R
import com.sinjeong.crewcalendar.domain.usecase.GetMonthScheduleUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth

/**
 * 매일 저녁(20:00) 내일 근무 미리 알림.
 * 설정에서 끌 수 있고(notify_tomorrow), 내일이 휴일·비번이면 알림하지 않는다.
 */
@HiltWorker
class DutyNotifyWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val getMonthSchedule: GetMonthScheduleUseCase,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        runCatching { BriefingAlarm.arm(context, getMonthSchedule) } // 하루 1회 브리핑 알람 재등록
        runCatching {
            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("notify_tomorrow", true)) return Result.success()
            if (Build.VERSION.SDK_INT >= 33 &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) return Result.success()

            val tomorrow = LocalDate.now().plusDays(1)
            val day = getMonthSchedule(YearMonth.from(tomorrow)).first()
                .firstOrNull { it.date == tomorrow } ?: return Result.success()
            if (!day.duty.isWorkDay) return Result.success()

            val text = buildString {
                append(day.duty.display)
                day.signOn?.let { append("  ·  출근 $it") }
            }
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            NotificationManagerCompat.from(context).notify(
                1001,
                NotificationCompat.Builder(context, "duty_channel")
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("내일 근무")
                    .setContentText(text)
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .build(),
            )
        }
        return Result.success() // 알림 실패는 재시도할 필요 없음
    }
}
