package com.sinjeong.crewcalendar.widget

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
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
 * 계산 규칙은 [com.sinjeong.crewcalendar.domain.model.BundledTimetable.advise] 한 곳에 있다.
 * 여기서는 **자동 예약을 절대 하지 않는다** — 버튼만 띄우고 켜는 판단은 사용자 몫.
 *
 * 알림 문구([Alarm.text])를 시각과 함께 저장하는 이유: 지선은 "양천구청역 도착",
 * 본선은 "편승 탑승"이라 문구가 갈리는데, 재부팅 후 [rearmAll]은 근무 데이터를 알지 못한다.
 */
object DeadheadAlarm {
    const val ACTION = "com.sinjeong.crewcalendar.DEADHEAD"
    const val ACTION_DISMISS = "com.sinjeong.crewcalendar.DEADHEAD_DISMISS"

    /** 알람 전용 채널(v1.6.32 신설) — 알람 볼륨·알람 사운드. 브리핑은 계속 [BriefingAlarm.CHANNEL] */
    const val CHANNEL = "crew_alarm_channel"

    /** 소리를 반복할 최대 시간. 기본 알람앱들처럼 이만큼 지나면 저절로 멈춘다 */
    internal const val RING_MS = 90_000L

    private const val KEY = "deadhead_alarms" // "yyyy-MM-dd|구간|HH:mm|문구" 집합

    /** 전반사업 = 1 / 후반사업 = 2. 같은 날짜에 두 건이 따로 걸린다(v1.6.29). */
    const val LEG_FIRST = 1
    const val LEG_SECOND = 2

    /** 예약 1건 */
    data class Alarm(val at: LocalTime, val text: String)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** 지난 날짜는 읽을 때 걷어낸다 — 목록이 무한히 자라지 않게 */
    private fun entries(ctx: Context): Map<Pair<LocalDate, Int>, Alarm> =
        prefs(ctx).getStringSet(KEY, emptySet()).orEmpty()
            .mapNotNull { s ->
                runCatching {
                    // v1.6.29에서 "날짜|시각|문구" → "날짜|구간|시각|문구"로 늘렸다.
                    // 옛 저장분은 둘째 칸이 시각(`:` 포함)이라 그것으로 구분해 전반으로 읽는다.
                    val p = s.split('|', limit = 4)
                    val legacy = ':' in p[1]
                    val leg = if (legacy) LEG_FIRST else p[1].toInt()
                    val at = LocalTime.parse(if (legacy) p[1] else p[2])
                    (LocalDate.parse(p[0]) to leg) to Alarm(at, p.getOrElse(if (legacy) 2 else 3) { "" })
                }.getOrNull()
            }
            .filter { it.first.first >= LocalDate.now() }
            .toMap()

    private fun save(ctx: Context, m: Map<Pair<LocalDate, Int>, Alarm>) =
        prefs(ctx).edit()
            .putStringSet(
                KEY,
                m.map { (k, v) -> "${k.first}|${k.second}|${v.at}|${v.text}" }.toSet(),
            )
            .apply()

    /** 그 날짜·구간에 예약된 시각 (없으면 null) — 상세시트 칩 상태가 이 값 하나로 정해진다 */
    fun scheduledAt(ctx: Context, date: LocalDate, leg: Int = LEG_FIRST): LocalTime? =
        entries(ctx)[date to leg]?.at

    /** 예약(또는 시각 변경 재예약). 이미 지난 시각이면 아무것도 하지 않고 false */
    fun schedule(ctx: Context, date: LocalDate, leg: Int, at: LocalTime, text: String): Boolean {
        val a = Alarm(at, text)
        if (!arm(ctx, date, leg, a)) return false
        save(ctx, entries(ctx) + ((date to leg) to a))
        return true
    }

    fun cancel(ctx: Context, date: LocalDate, leg: Int = LEG_FIRST) {
        ctx.getSystemService(AlarmManager::class.java)?.cancel(pending(ctx, date, leg))
        save(ctx, entries(ctx) - (date to leg))
    }

    /** 부팅 후 재등록 ([BriefingReceiver]가 BOOT_COMPLETED에서 호출) */
    fun rearmAll(ctx: Context) = entries(ctx).forEach { (k, a) -> arm(ctx, k.first, k.second, a) }

    private fun pending(ctx: Context, date: LocalDate, leg: Int, a: Alarm? = null) = PendingIntent.getBroadcast(
        // 전반은 옛 requestCode를 그대로 둔다 — 업데이트 전에 걸어 둔 알람도 계속 취소된다
        ctx, date.toEpochDay().toInt() + if (leg == LEG_SECOND) 1_000_000 else 0,
        // 취소는 extras를 안 보는 filterEquals(action+컴포넌트)로 매칭되므로 at 없이도 정확히 걸린다
        Intent(ctx, DeadheadReceiver::class.java).setAction(ACTION)
            .putExtra("date", date.toString())
            .putExtra("leg", leg)
            .putExtra("at", a?.at?.toString())
            .putExtra("text", a?.text),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /**
     * 실제로 알람을 건다. **정확 알람 권한이 없으면 걸지 않고 false** — v1.6.31까지 있던
     * `setWindow` 폴백은 Doze에서 통째로 밀려 "예약됨인데 안 울림"을 만들었다([AlarmPermission] 주석).
     */
    private fun arm(ctx: Context, date: LocalDate, leg: Int, a: Alarm): Boolean {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return false
        if (!AlarmPermission.canExact(ctx)) return false
        val whenAt = LocalDateTime.of(date, a.at)
        if (!whenAt.isAfter(LocalDateTime.now())) return false
        val ms = whenAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pending(ctx, date, leg, a))
        return true
    }

    internal fun notifyNow(ctx: Context, dateStr: String?, leg: Int, at: String?, body: String?) {
        val date = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: LocalDate.now()
        save(ctx, entries(ctx) - (date to leg)) // 발송했으면 예약 해제 — 칩이 계속 "예약됨"으로 남지 않게
        if (Build.VERSION.SDK_INT >= 33 &&
            ActivityCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        // 문구는 예약할 때 계산해 둔 것을 그대로 쓴다 (지선 "도착" / 본선 "편승 탑승"이 갈린다)
        val text = body?.takeIf { it.isNotBlank() } ?: "양천구청역 ${at.orEmpty()}"
        val id = 1100 + (date.toEpochDay() % 100).toInt() + if (leg == LEG_SECOND) 200 else 0

        // 전체화면 알람 화면. FLAG_ACTIVITY_NEW_TASK가 없으면 알림에서 못 띄운다.
        val ring = PendingIntent.getActivity(
            ctx, id,
            Intent(ctx, AlarmRingActivity::class.java)
                .putExtra("id", id).putExtra("text", text)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val dismiss = PendingIntent.getBroadcast(
            ctx, id + 500,
            Intent(ctx, DeadheadReceiver::class.java).setAction(ACTION_DISMISS).putExtra("id", id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("출근 알람")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // 전체 화면 알림이 허용돼 있으면 잠금화면 위로 알람 화면이 바로 뜨고,
            // 안 돼 있으면 시스템이 알아서 heads-up으로 낮춘다 — 그래도 소리는 아래 INSISTENT로 계속 울린다.
            .setFullScreenIntent(ring, true)
            .setContentIntent(ring)
            .addAction(0, "해제", dismiss)
            .setDeleteIntent(dismiss)
            .setAutoCancel(true)
            // 안전장치: 시스템이 이 시간에 알림을 지우고 → 소리도 함께 끊긴다. 좀비 사운드가 생길 수 없다.
            .setTimeoutAfter(RING_MS)
            .build()
        // 폰 기본 알람처럼 "지울 때까지" 소리·진동 반복. 소리는 시스템이 재생하므로
        // MediaPlayer·포그라운드 서비스가 필요 없고, 알림이 사라지는 모든 경로에서 확실히 멈춘다.
        n.flags = n.flags or Notification.FLAG_INSISTENT
        NotificationManagerCompat.from(ctx).notify(id, n)
    }
}

/**
 * 알람 화면 — 잠금화면 위에 뜨는 전체화면.
 *
 * ### 왜 이 화면이 소리를 직접 내나 (v1.6.32 실측으로 뒤집은 설계)
 *
 * 처음엔 알림의 `FLAG_INSISTENT`에만 맡겼다(재생기를 시스템이 들고 있어 좀비 사운드가 구조적으로
 * 불가능하다는 게 이유였다). **API 36 에뮬레이터에서 실제로 재 보니 그게 안 됐다** —
 * 알림이 90초 `timeoutAfter`(`timeout=PT1M30S`로 등록된 것까지 확인)보다 훨씬 먼저 사라지면서
 * 소리가 **7초·43초 만에** 끊겼다(`RingtonePlayer: stopAsync`). 새벽 출근 알람이 7초 울리고
 * 마는 것은 이 기능의 존재 이유를 무너뜨린다. **"플랫폼이 알아서 해 주겠지"를 실측이 부정했다.**
 *
 * 그래서 **화면이 떠 있는 동안은 이 화면이 `MediaPlayer`로 직접 반복 재생**한다:
 * - 겹쳐 울리지 않게 `onCreate`에서 알림을 먼저 지운다(알림의 역할은 이 화면을 띄우는 것까지).
 * - 좀비 사운드 방지는 **세 겹** — `onDestroy`(뒤로가기·종료 포함 모든 경로에서 프레임워크가 부른다)
 *   + [RING_MS] 뒤 자동 `finish()` + 프로세스가 죽으면 재생기도 같이 죽는다.
 * - `FLAG_KEEP_SCREEN_ON`으로 우는 동안 화면이 안 꺼진다.
 *
 * 전체 화면 알림이 거부돼 이 화면이 못 뜨는 기기에서는 알림의 `FLAG_INSISTENT`가 그대로 남아
 * (짧더라도) 소리·진동이 울린다 — 그래서 알림 쪽 플래그도 함께 유지한다.
 */
class AlarmRingActivity : ComponentActivity() {

    private var player: MediaPlayer? = null
    private val autoStop = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val id = intent.getIntExtra("id", 0)
        val text = intent.getStringExtra("text").orEmpty()
        NotificationManagerCompat.from(this).cancel(id) // 소리가 두 겹으로 겹치지 않게
        startRinging()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            LocalTime.now().let { "%d:%02d".format(it.hour, it.minute) },
                            fontSize = 68.sp, fontWeight = FontWeight.ExtraBold,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("출근 알람", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(20.dp))
                        Text(text, fontSize = 18.sp, textAlign = TextAlign.Center, lineHeight = 26.sp)
                        Spacer(Modifier.height(48.dp))
                        Button(
                            onClick = { finish() }, // 소리는 onDestroy가 끈다
                            Modifier.fillMaxWidth().height(64.dp),
                        ) { Text("해제", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold) }
                    }
                }
            }
        }
    }

    /** 알람 볼륨(USAGE_ALARM)으로 기본 알람음을 반복 재생. 실패해도 화면은 그대로 뜬다. */
    private fun startRinging() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        if (uri != null) {
            player = runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    setDataSource(this@AlarmRingActivity, uri)
                    isLooping = true
                    prepare()
                    start()
                }
            }.getOrNull()
        }
        autoStop.postDelayed({ finish() }, DeadheadAlarm.RING_MS)
    }

    override fun onDestroy() {
        super.onDestroy()
        autoStop.removeCallbacksAndMessages(null)
        player?.let { p -> runCatching { p.stop() }; p.release() }
        player = null
    }
}

/** 편승 알람 발화 — 조회할 스케줄이 없어(시각을 인텐트에 담아 둠) 워커 없이 바로 알린다 */
class DeadheadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            // 알림의 [해제] 버튼 / 밀어서 지우기. 알림을 지우면 시스템이 소리도 함께 끊는다.
            DeadheadAlarm.ACTION_DISMISS ->
                NotificationManagerCompat.from(context).cancel(intent.getIntExtra("id", 0))
            DeadheadAlarm.ACTION -> DeadheadAlarm.notifyNow(
                context,
                intent.getStringExtra("date"),
                intent.getIntExtra("leg", DeadheadAlarm.LEG_FIRST), // 옛 알람은 leg가 없다 → 전반
                intent.getStringExtra("at"),
                intent.getStringExtra("text"),
            )
        }
    }
}
