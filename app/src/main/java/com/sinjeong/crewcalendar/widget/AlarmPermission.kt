package com.sinjeong.crewcalendar.widget

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * 알람이 **실제로 울리기 위한 조건 두 가지**를 한 곳에서 본다.
 *
 * 1. **정확 알람("알람 및 리마인더", `SCHEDULE_EXACT_ALARM`)** — 안드로이드 14부터 기본 꺼짐이다.
 * 2. **알림** — 꺼져 있으면 알람이 제때 깨어나도 화면에 아무것도 안 뜬다.
 *
 * ### 왜 폴백(`setWindow`)을 버렸나 (v1.6.32)
 *
 * v1.6.27~31은 권한이 없으면 `setWindow`(부정확 창)로 대신 걸고 **예약 성공을 돌려줬다.**
 * 실사용자(v1.6.31)가 "예약됨"까지 만들고도 알람을 못 받은 원인이 이것이다.
 * API 36 에뮬레이터 실측:
 *
 * - 등록은 되지만 `flags=0x0` = `ALLOW_WHILE_IDLE`가 없다 → **Doze(폰이 자는 새벽)에 통째로 밀린다.**
 *   출근 알람은 정확히 그 시간대에 울려야 하는데 하필 그때 못 울리는 것.
 * - 깨어 있어도 창(`window`) 안에서 배칭돼 **정시에 안 온다**(실측 5분 이상 지연·최대 ±5분).
 * - 권한을 끄는 순간 시스템이 이미 걸린 알람을 **`exact_alarm_permission_revoked`로 지워 버린다** —
 *   앱은 그 사실을 모르고 계속 "예약됨"으로 표시했다.
 *
 * 즉 폴백은 "울릴 수도 있다"가 아니라 **"이 용도로는 못 쓴다"** 였다. 그래서 지금은
 * 권한이 없으면 [DeadheadAlarm.schedule]·[BriefingAlarm.arm]이 **예약을 아예 안 한다.**
 * "예약됨인데 안 울림"이 최악이기 때문. 대신 부르는 쪽에서 [warning]을 띄워 설정으로 보낸다.
 */
object AlarmPermission {

    /** 정확 알람을 걸 수 있나 (API 30 이하는 권한 개념 자체가 없어 항상 true) */
    fun canExact(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 31 ||
            ctx.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true

    /**
     * 알림이 켜져 있나. `checkSelfPermission`이 아니라 `areNotificationsEnabled()`를 보는 이유 —
     * 권한은 허용해 놓고 설정에서 앱 알림만 꺼 둔 경우까지 잡아야 한다.
     */
    fun canNotify(ctx: Context): Boolean = NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    /**
     * 잠금화면 위 전체화면 알람을 띄울 수 있나. 안드로이드 14부터 사용자가 끌 수 있다.
     * **이건 예약을 막지 않는다** — 꺼져 있어도 소리·진동은 `FLAG_INSISTENT`로 그대로 울리고
     * 알림만 heads-up으로 낮아진다. 그래서 [warning]이 아니라 [notice] 쪽이다.
     */
    fun canFullScreen(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 34 ||
            ctx.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() != false

    /** 알람을 **못 울리게 막고 있는** 것에 대한 안내. 다 켜져 있으면 null */
    fun warning(ctx: Context): String? = when {
        !canExact(ctx) ->
            "정시에 알람을 받으려면 휴대폰 설정에서 \"알람 및 리마인더\"를 켜야 합니다.\n" +
                "꺼져 있으면 예약을 해도 알람이 울리지 않습니다."
        !canNotify(ctx) ->
            "이 앱의 알림이 꺼져 있습니다.\n알림을 켜야 알람이 화면에 뜹니다."
        else -> null
    }

    /** 막지는 않지만 알람이 약해지는 것. [warning]이 없을 때만 보면 된다 */
    fun notice(ctx: Context): String? =
        if (canFullScreen(ctx)) null
        else "\"전체 화면 알림\"이 꺼져 있어 잠금화면에 알람 화면이 뜨지 않습니다.\n" +
            "소리와 진동은 그대로 울립니다."

    /** 지금 막고 있는(또는 약하게 만드는) 바로 그 설정 화면으로 보낸다. 없는 기기면 앱 정보로 물러난다. */
    fun openSettings(ctx: Context) {
        val pkg = Uri.parse("package:${ctx.packageName}")
        val first = when {
            !canExact(ctx) && Build.VERSION.SDK_INT >= 31 ->
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg)
            canNotify(ctx) && Build.VERSION.SDK_INT >= 34 ->
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg)
            else ->
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        }
        listOf(first, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)).firstOrNull {
            runCatching { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        }
    }
}
