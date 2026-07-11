package com.sinjeong.crewcalendar

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SinjeongApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("duty_channel", "근무 알림", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "교환 요청, 근무 변경, 출근 알림"
            }
        )
    }
}
