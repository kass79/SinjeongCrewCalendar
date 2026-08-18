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
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.sinjeong.crewcalendar.MainActivity
import com.sinjeong.crewcalendar.R
import com.sinjeong.crewcalendar.domain.usecase.GetMonthScheduleUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/**
 * 출근 1시간 전 브리핑 알림.
 *
 * 항상 "다음 알람 1건"만 등록한다 — 앱 실행 / 부팅 / 매일 20시 워커 / 알림 발송 직후에
 * 다시 채우므로 근무변경은 앱을 열면 자연히 반영된다.
 */
object BriefingAlarm {
    const val ACTION = "com.sinjeong.crewcalendar.BRIEFING"
    const val CHANNEL = "briefing_channel"
    private const val NOTIFY_ID = 1002
    private const val REQ = 2001

    fun enabled(ctx: Context) =
        ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("notify_briefing", true)

    /** 재등록 요청(설정 토글·앱 실행 등) — 워커로 넘겨 스케줄 조회를 백그라운드에서 처리 */
    fun requestRearm(ctx: Context) {
        WorkManager.getInstance(ctx).enqueue(OneTimeWorkRequestBuilder<BriefingWorker>().build())
    }

    /** 다음 7일 내 첫 근무일의 (출근 − 1시간)에 알람 1건 등록. 토글 off면 해제만 한다. */
    suspend fun arm(ctx: Context, getMonthSchedule: GetMonthScheduleUseCase) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            ctx, REQ, Intent(ctx, BriefingReceiver::class.java).setAction(ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (!enabled(ctx)) { am.cancel(pi); return }

        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val month = YearMonth.from(today)
        // 7일 창이 달을 넘어갈 수 있으므로 이번 달 + 다음 달
        val days = getMonthSchedule(month).first() + getMonthSchedule(month.plusMonths(1)).first()
        val at = days.asSequence()
            .filter { it.date >= today && it.date <= today.plusDays(7) && it.duty.isWorkDay }
            .mapNotNull { signOnAt(it.date, it.signOn)?.minusHours(1) }
            .firstOrNull { it.isAfter(now) }
            ?: run { am.cancel(pi); return }

        val ms = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        } else {
            // 14+ 에서 정확알람 권한이 없을 때: ±10분 창
            am.setWindow(AlarmManager.RTC_WAKEUP, ms - 600_000L, 1_200_000L, pi)
        }
    }

    /** 오늘 근무 브리핑 발송. 날씨는 실패해도 알림 자체는 반드시 나간다. */
    suspend fun notifyToday(ctx: Context, getMonthSchedule: GetMonthScheduleUseCase) {
        if (!enabled(ctx)) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ActivityCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val today = LocalDate.now()
        val day = getMonthSchedule(YearMonth.from(today)).first()
            .firstOrNull { it.date == today } ?: return
        if (!day.duty.isWorkDay) return
        val on = signOnAt(today, day.signOn) ?: return

        val text = buildString {
            append("오늘 ${day.duty.displayLong} · ${day.signOn} 출근")
            withContext(Dispatchers.IO) { fetchWeather(on) }?.let { append(" · $it") }
        }
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        NotificationManagerCompat.from(ctx).notify(
            NOTIFY_ID,
            NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("출근 1시간 전")
                .setContentText(text)
                // 워치 미러링에서 전문이 보이도록 BigText + 리마인더 카테고리
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }

    /** "7:47" → 그 날 07:47. 야간 표기 "25:20"은 익일로 자연히 넘어간다. */
    private fun signOnAt(date: LocalDate, s: String?): LocalDateTime? {
        val hm = s?.split(":")?.mapNotNull { it.trim().toIntOrNull() }?.takeIf { it.size == 2 } ?: return null
        return date.atStartOfDay().plusHours(hm[0].toLong()).plusMinutes(hm[1].toLong())
    }
}

/* ── 날씨 (Open-Meteo, 키 불필요) ─────────────────────────────── */

private const val LAT = 37.517   // 신정차량기지 인근
private const val LON = 126.865
// ponytail: DaySchedule에 퇴근시각이 없어 출근+9h로 근무창을 근사 — signOff가 노출되면 교체
private const val WINDOW_H = 9
private val HOUR_KEY = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

/** 출근~퇴근 시간대 요약 한 줄 ("24° 비 예보 — 우산 챙기세요"). 실패/타임아웃이면 null. */
private fun fetchWeather(from: LocalDateTime): String? = runCatching {
    val conn = URL(
        "https://api.open-meteo.com/v1/forecast?latitude=$LAT&longitude=$LON" +
            "&hourly=temperature_2m,precipitation_probability,weather_code" +
            "&timezone=Asia%2FSeoul&forecast_days=3",
    ).openConnection() as HttpURLConnection
    conn.connectTimeout = 5_000
    conn.readTimeout = 5_000
    val body = try { conn.inputStream.bufferedReader().use { it.readText() } } finally { conn.disconnect() }

    val h = JSONObject(body).getJSONObject("hourly")
    val times = h.getJSONArray("time")
    val key = from.truncatedTo(ChronoUnit.HOURS).format(HOUR_KEY)
    val i = (0 until times.length()).firstOrNull { times.getString(it) == key } ?: return@runCatching null

    val temps = h.getJSONArray("temperature_2m")
    val pops = h.getJSONArray("precipitation_probability")
    val codes = h.getJSONArray("weather_code")
    var pop = 0
    var code = 0
    for (k in i until minOf(i + WINDOW_H, times.length())) {
        pop = maxOf(pop, pops.optInt(k))
        code = maxOf(code, codes.optInt(k))   // WMO 코드는 대체로 심한 날씨일수록 큼 → 최댓값이 "최악"
    }
    val label = when {
        code >= 95 -> "뇌우"
        code in 71..77 || code in 85..86 -> "눈"
        code in 51..67 || code in 80..82 -> "비"
        code in 45..48 -> "안개"
        code in 1..3 -> "구름"
        else -> "맑음"
    }
    "${temps.getDouble(i).roundToInt()}° $label" + if (pop >= 60) " 예보 — 우산 챙기세요" else ""
}.getOrNull()

/* ── 알람 수신 / 실행 ─────────────────────────────────────────── */

/** 알람 발화 + 부팅 재등록. 스케줄 조회·네트워크는 기존 HiltWorker 경로로 넘긴다. */
class BriefingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 재부팅하면 편승 알람도 같이 날아간다 — 여기가 이미 BOOT_COMPLETED를 받으므로 얹어서 복구
        runCatching { DeadheadAlarm.rearmAll(context) }
        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<BriefingWorker>()
                .setInputData(workDataOf("notify" to (intent.action == BriefingAlarm.ACTION)))
                .build(),
        )
    }
}

/** notify=true면 오늘 브리핑 발송, 어느 경우든 다음 알람 1건을 다시 등록한다. */
@HiltWorker
class BriefingWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val getMonthSchedule: GetMonthScheduleUseCase,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        runCatching {
            if (inputData.getBoolean("notify", false)) {
                BriefingAlarm.notifyToday(context, getMonthSchedule)
            }
            BriefingAlarm.arm(context, getMonthSchedule)
        }
        return Result.success() // 알림 실패는 재시도할 필요 없음
    }
}
