package com.sinjeong.crewcalendar

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.sinjeong.crewcalendar.widget.DutyNotifyWorker
import com.sinjeong.crewcalendar.widget.DutyWidgetWorker
import dagger.hilt.android.HiltAndroidApp
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class SinjeongApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("duty_channel", "근무 알림", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "교환 요청, 근무 변경, 출근 알림"
            }
        )
        scheduleBackgroundWork()
    }

    /** 위젯 데이터 갱신(즉시 1회 + 6시간 주기) + 내일 근무 알림(매일 20:00) */
    private fun scheduleBackgroundWork() {
        val wm = WorkManager.getInstance(this)
        wm.enqueueUniqueWork(
            "widget_now", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<DutyWidgetWorker>().build(),
        )
        wm.enqueueUniquePeriodicWork(
            "widget_refresh", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DutyWidgetWorker>(6, TimeUnit.HOURS).build(),
        )
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(20, 0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        wm.enqueueUniquePeriodicWork(
            "duty_notify", ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<DutyNotifyWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
                .build(),
        )
    }
}
