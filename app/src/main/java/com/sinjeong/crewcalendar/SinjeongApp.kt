package com.sinjeong.crewcalendar

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.sinjeong.crewcalendar.widget.BriefingAlarm
import com.sinjeong.crewcalendar.widget.DeadheadAlarm
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
        installAppCheck()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("duty_channel", "근무 알림", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "교환 요청, 근무 변경, 출근 알림"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(BriefingAlarm.CHANNEL, "출근 브리핑", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "출근 1시간 전 근무·날씨 브리핑"
            }
        )
        // 편승 알람만 "진짜 알람" — 폰 기본 알람앱처럼 알람 볼륨으로 소리가 반복된다(v1.6.32).
        // 채널은 만든 뒤에 소리·중요도를 못 바꾸므로 기존 채널을 고치지 않고 새 ID로 팠다.
        // 브리핑은 일반 알림 그대로다(매 출근 1시간 전마다 시끄러우면 과하다).
        nm.createNotificationChannel(
            NotificationChannel(DeadheadAlarm.CHANNEL, "출근 알람", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "편승·출근 알람 — 알람 볼륨으로 소리와 진동이 반복됩니다"
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    // USAGE_ALARM이라야 미디어·알림 볼륨이 아니라 **알람 볼륨**을 따르고
                    // 방해금지의 "알람 허용"(기본 켜짐)에도 걸린다.
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                enableVibration(true)
                // 한 번 분량만 적는다 — 반복은 알림의 FLAG_INSISTENT가 맡는다.
                // 채널 진동은 시스템이 대신 울려 줘서 VIBRATE 권한이 필요 없다(v1.6.18에서 뺀 그대로).
                vibrationPattern = longArrayOf(0, 700, 700)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
        scheduleBackgroundWork()
        BriefingAlarm.requestRearm(this) // 앱 실행 시 다음 출근 알람 재등록
    }

    /**
     * App Check 공급자 설치(v1.6.86). Firebase AI Logic(식단표 사진 인식)이 **2026-11-02 부터**
     * App Check 를 의무화해서 미리 붙여 둔다.
     *
     * - 콘솔은 아직 **미적용(unenforced)** — 토큰을 못 받아도 앱 동작에는 아무 영향이 없다.
     *   **콘솔 강제 전환은 지표 확인 후, 코디네이터만.**
     * - 공급자는 빌드 타입별 소스셋(`src/debug`·`src/release`)의 `appCheckFactory()` 가 고른다.
     * - `google-services.json` 없는 오프라인 체험판 빌드는 FirebaseApp 자체가 없으므로 건너뛴다
     *   (`AppModule.firebaseOn` 과 같은 판정).
     */
    private fun installAppCheck() {
        if (FirebaseApp.getApps(this).isEmpty()) return
        runCatching { FirebaseAppCheck.getInstance().installAppCheckProviderFactory(appCheckFactory()) }
            .onFailure { Log.w("AppCheck", "App Check 공급자 설치 실패 — 미적용 상태라 동작에는 영향 없음", it) }
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
