package com.sinjeong.crewcalendar.widget

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sinjeong.crewcalendar.MainActivity
import com.sinjeong.crewcalendar.R
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 편승 알람 — 상세시트에서 사용자가 직접 켠 **그 날짜 1회성** 알람.
 *
 * 브리핑([BriefingAlarm])과 같은 AlarmManager + BroadcastReceiver 패턴이고 알림 채널도 공유한다.
 * 다른 점은 브리핑이 "항상 다음 1건"만 들고 있는 데 비해 편승은 날짜별로 켜 둔 것이 여러 건
 * 쌓인다는 것뿐. 그래서 예약 목록을 SharedPreferences("settings")에 문자열 집합으로 남기고
 * 부팅 후 [rearmAll]로 다시 건다(알람은 재부팅하면 날아간다).
 *
 * 앱은 어느 다이아가 실제로 편승이 필요한지 모른다(행로표가 스캔 이미지라 역명이 데이터로 없음).
 * 그래서 **자동 예약은 절대 하지 않는다** — 출근시각이 있는 날에 버튼만 띄우고 판단은 사용자 몫.
 */
object DeadheadAlarm {
    const val ACTION = "com.sinjeong.crewcalendar.DEADHEAD"
    private const val KEY = "deadhead_alarms" // "yyyy-MM-dd|HH:mm" 집합

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** 지난 날짜는 읽을 때 걷어낸다 — 목록이 무한히 자라지 않게 */
    private fun entries(ctx: Context): Map<LocalDate, LocalTime> =
        prefs(ctx).getStringSet(KEY, emptySet()).orEmpty()
            .mapNotNull { s ->
                runCatching {
                    LocalDate.parse(s.substringBefore('|')) to LocalTime.parse(s.substringAfter('|'))
                }.getOrNull()
            }
            .filter { it.first >= LocalDate.now() }
            .toMap()

    private fun save(ctx: Context, m: Map<LocalDate, LocalTime>) =
        prefs(ctx).edit().putStringSet(KEY, m.map { "${it.key}|${it.value}" }.toSet()).apply()

    /** 그 날짜에 예약된 편승 시각 (없으면 null) — 상세시트 아이콘 상태가 이 값 하나로 정해진다 */
    fun scheduledAt(ctx: Context, date: LocalDate): LocalTime? = entries(ctx)[date]

    /** 예약(또는 시각 변경 재예약). 이미 지난 시각이면 아무것도 하지 않고 false */
    fun schedule(ctx: Context, date: LocalDate, at: LocalTime): Boolean {
        if (!arm(ctx, date, at)) return false
        save(ctx, entries(ctx) + (date to at))
        return true
    }

    fun cancel(ctx: Context, date: LocalDate) {
        ctx.getSystemService(AlarmManager::class.java)?.cancel(pending(ctx, date))
        save(ctx, entries(ctx) - date)
    }

    /** 부팅 후 재등록 ([BriefingReceiver]가 BOOT_COMPLETED에서 호출) */
    fun rearmAll(ctx: Context) = entries(ctx).forEach { (d, t) -> arm(ctx, d, t) }

    private fun pending(ctx: Context, date: LocalDate, at: LocalTime? = null) = PendingIntent.getBroadcast(
        ctx, date.toEpochDay().toInt(),
        // 취소는 extras를 안 보는 filterEquals(action+컴포넌트)로 매칭되므로 at 없이도 정확히 걸린다
        Intent(ctx, DeadheadReceiver::class.java).setAction(ACTION)
            .putExtra("date", date.toString())
            .putExtra("at", at?.toString()),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun arm(ctx: Context, date: LocalDate, at: LocalTime): Boolean {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return false
        val whenAt = LocalDateTime.of(date, at)
        if (!whenAt.isAfter(LocalDateTime.now())) return false
        val ms = whenAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pi = pending(ctx, date, at)
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        } else {
            am.setWindow(AlarmManager.RTC_WAKEUP, ms - 300_000L, 600_000L, pi) // 정확알람 불가 시 ±5분
        }
        return true
    }

    internal fun notifyNow(ctx: Context, dateStr: String?, at: String?) {
        val date = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: LocalDate.now()
        save(ctx, entries(ctx) - date) // 발송했으면 예약 해제 — 아이콘이 계속 "예약됨"으로 남지 않게
        if (Build.VERSION.SDK_INT >= 33 &&
            ActivityCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val text = "양천구청역에서 ${at.orEmpty()} 신도림행 편승 탑승"
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        NotificationManagerCompat.from(ctx).notify(
            1100 + (date.toEpochDay() % 100).toInt(),
            NotificationCompat.Builder(ctx, BriefingAlarm.CHANNEL)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("편승 시각")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }
}

/** 편승 알람 발화 — 조회할 스케줄이 없어(시각을 인텐트에 담아 둠) 워커 없이 바로 알린다 */
class DeadheadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DeadheadAlarm.ACTION) return
        DeadheadAlarm.notifyNow(context, intent.getStringExtra("date"), intent.getStringExtra("at"))
    }
}
