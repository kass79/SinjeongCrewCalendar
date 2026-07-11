package com.sinjeong.crewcalendar.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.cornerRadius
import androidx.glance.currentState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * 오늘/내일 근무 홈 위젯.
 * 데이터는 DutyWidgetWorker 가 주기적으로 GlanceState(Preferences)에 저장하고,
 * 여기서는 그 값을 그리기만 한다. (위젯에서 직접 Firestore 접근 금지)
 */
class DutyWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    companion object {
        val KEY_TODAY = stringPreferencesKey("today_duty")      // 예: "14"
        val KEY_TODAY_SUB = stringPreferencesKey("today_sub")   // 예: "출근 07:47"
        val KEY_TOMORROW = stringPreferencesKey("tomorrow_duty")
        val KEY_TOMORROW_SUB = stringPreferencesKey("tomorrow_sub")
        val KEY_DATE = stringPreferencesKey("date_label")       // 예: "7/10 (금)"
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                val prefs = currentState<androidx.datastore.preferences.core.Preferences>()
                val today = prefs[KEY_TODAY] ?: "—"
                val todaySub = prefs[KEY_TODAY_SUB] ?: ""
                val tomorrow = prefs[KEY_TOMORROW] ?: "—"
                val tomorrowSub = prefs[KEY_TOMORROW_SUB] ?: ""
                val dateLabel = prefs[KEY_DATE] ?: ""

                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.surface)
                        .cornerRadius(20.dp)
                        .padding(14.dp),
                ) {
                    Text(
                        dateLabel,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 11.sp, fontWeight = FontWeight.Medium,
                        ),
                    )
                    Spacer(GlanceModifier.height(6.dp))
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        DutyBlock("오늘", today, todaySub, GlanceModifier.defaultWeight())
                        Spacer(GlanceModifier.width(10.dp))
                        DutyBlock("내일", tomorrow, tomorrowSub, GlanceModifier.defaultWeight())
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun DutyBlock(label: String, duty: String, sub: String, modifier: GlanceModifier) {
        Column(
            modifier = modifier
                .background(GlanceTheme.colors.primaryContainer)
                .cornerRadius(14.dp)
                .padding(10.dp),
        ) {
            Text(label, style = TextStyle(
                color = GlanceTheme.colors.onPrimaryContainer, fontSize = 10.sp))
            Text(duty, style = TextStyle(
                color = GlanceTheme.colors.onPrimaryContainer,
                fontSize = 22.sp, fontWeight = FontWeight.Bold))
            if (sub.isNotBlank()) Text(sub, style = TextStyle(
                color = GlanceTheme.colors.onPrimaryContainer, fontSize = 10.sp))
        }
    }
}

class DutyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DutyWidget()
}
