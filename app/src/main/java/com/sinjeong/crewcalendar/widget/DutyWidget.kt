package com.sinjeong.crewcalendar.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.sinjeong.crewcalendar.MainActivity

/**
 * 오늘부터 7일 근무 스트립 홈 위젯.
 * 데이터는 DutyWidgetWorker 가 주기적으로 GlanceState(Preferences)에 저장하고,
 * 여기서는 그 값을 그리기만 한다. (위젯에서 직접 Firestore 접근·공휴일 계산 금지)
 */
class DutyWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    companion object {
        /** "화|28|5|0;수|29|휴3|1;…" — 요일|일자|근무|빨강(일요일·공휴일). 비면 빈 상태 */
        val KEY_WEEK = stringPreferencesKey("week_strip")

        /** 예: "오늘 출근 07:47" (없으면 빈 문자열) */
        val KEY_SUB = stringPreferencesKey("today_sub")
    }

    private data class Cell(val dow: String, val day: String, val duty: String, val red: Boolean)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                val prefs = currentState<androidx.datastore.preferences.core.Preferences>()
                val cells = prefs[KEY_WEEK].orEmpty().split(";").mapNotNull { rec ->
                    val p = rec.split("|")
                    if (p.size == 4) Cell(p[0], p[1], p[2], p[3] == "1") else null
                }
                val sub = prefs[KEY_SUB].orEmpty()

                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.surface)
                        .cornerRadius(20.dp)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .clickable(actionStartActivity<MainActivity>()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (cells.isEmpty()) {
                        Box(
                            modifier = GlanceModifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "근무를 선택해 주세요",
                                style = TextStyle(
                                    color = GlanceTheme.colors.onSurfaceVariant,
                                    fontSize = 13.sp,
                                ),
                            )
                        }
                        return@Column
                    }

                    if (sub.isNotBlank()) {
                        Text(
                            sub,
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurface,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            modifier = GlanceModifier.padding(start = 2.dp, bottom = 3.dp),
                        )
                    }
                    // Glance 컨테이너는 자식 10개까지만 렌더한다.
                    // 칸 사이 Spacer 를 쓰면 7칸+6스페이서=13 이라 뒤 2칸이 잘리므로,
                    // 간격은 칸을 감싼 Box 의 padding 으로 준다(자식 7개).
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        cells.forEachIndexed { i, c ->
                            Box(modifier = GlanceModifier.defaultWeight().padding(horizontal = 1.dp)) {
                                DayCell(c, isToday = i == 0, modifier = GlanceModifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun DayCell(cell: Cell, isToday: Boolean, modifier: GlanceModifier) {
        // 오늘 칸은 primary 로 확실히 튀게. 나머지는 inverseOnSurface —
        // 라이트에서 흰색에 가깝고(밝은 카드) 다크에서는 배경보다 밝은 회색이라 양쪽 다 산뜻하다.
        val fg = if (isToday) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSurface
        Column(
            modifier = modifier
                .background(
                    if (isToday) GlanceTheme.colors.primary
                    else GlanceTheme.colors.inverseOnSurface
                )
                .cornerRadius(10.dp)
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 요일+날짜를 한 줄로 합쳐 근무 글자를 키울 세로 여유를 만든다.
            Text(
                "${cell.dow} ${cell.day}",
                style = TextStyle(
                    color = if (cell.red && !isToday) GlanceTheme.colors.error else fg,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            Text(
                cell.duty.ifBlank { "·" },
                style = TextStyle(
                    color = fg,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }
}

class DutyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DutyWidget()

    // 위젯 추가·주기 갱신(updatePeriodMillis) 시 즉시 데이터 채우기
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
