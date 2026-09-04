package com.sinjeong.crewcalendar.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * 런처 위젯 목록의 2x1 항목. **위젯 내용은 [DutyWidget]이 크기로 정한다** — 리시버만 다르고
 * 그리는 클래스는 같다(v1.6.88). 목록에 크기별 항목을 내려면 `appwidget-provider` XML이
 * 따로 있어야 하고, XML은 리시버에 붙으므로 리시버를 늘리는 것 말고는 방법이 없다.
 *
 * 기존 [DutyWidgetReceiver]는 **이름을 바꾸지 않는다** — 이미 4x1을 놓아 둔 사용자의 위젯이
 * 리시버 이름으로 묶여 있어서, 바꾸면 홈 화면에서 통째로 사라진다.
 */
class DutyWidgetReceiver2 : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DutyWidget()

    override fun onUpdate(
        context: Context,
        appWidgetManager: android.appwidget.AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        androidx.work.WorkManager.getInstance(context).enqueue(
            androidx.work.OneTimeWorkRequestBuilder<DutyWidgetWorker>().build()
        )
    }
}

/** 런처 위젯 목록의 3x1 항목. 자세한 내용은 [DutyWidgetReceiver2]. */
class DutyWidgetReceiver3 : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DutyWidget()

    override fun onUpdate(
        context: Context,
        appWidgetManager: android.appwidget.AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        androidx.work.WorkManager.getInstance(context).enqueue(
            androidx.work.OneTimeWorkRequestBuilder<DutyWidgetWorker>().build()
        )
    }
}
