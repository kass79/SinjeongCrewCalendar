package com.sinjeong.crewcalendar.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
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
import androidx.glance.unit.ColorProvider
import com.sinjeong.crewcalendar.MainActivity
import com.sinjeong.crewcalendar.util.dutyPalette

/**
 * 오늘부터 7일 근무 스트립 홈 위젯.
 * 데이터는 DutyWidgetWorker 가 주기적으로 GlanceState(Preferences)에 저장하고,
 * 여기서는 그 값을 그리기만 한다. (위젯에서 직접 Firestore 접근·공휴일 계산 금지)
 */
class DutyWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    /*
     * v1.6.22: SizeMode.Exact → Responsive.
     *
     * Exact 는 런처가 알려준 크기(minWidth/maxWidth·minHeight/maxHeight 조합)로 레이아웃을
     * 만드는데, 그 조합에는 **실제로 렌더되지 않는 크기**가 섞인다. 실측(에뮬 1080x2400):
     *   접힘 세로 → 266dp x 104dp / 465dp x 62.9dp 두 벌을 만든다.
     * 두 번째가 문제였다 — 폭이 넓어 "요일+날짜 11.5sp / 근무 16sp" 큰 글자로 그려지는데
     * 높이는 62.9dp뿐이라 내용(약 79dp)이 안 들어가 **근무 코드 줄이 통째로 잘린다.**
     * 폭이 넓어질수록(펼침·가로) 런처가 바로 그 판을 고르므로 "펼치면 오히려 잘리는" 증상이 됐다.
     * v1.6.12 의 판단식은 폭(`폭/7 < 46dp`)만 봐서 높이 부족을 아예 못 본다.
     *
     * Responsive 는 여기 적은 크기로만 레이아웃을 만들고, 런처는 **실제 크기가 그 값 이상일 때만**
     * 그 판을 고른다. 즉 각 판을 "제 크기에서 안 잘리게" 만들어 두면 그 이상에서도 안 잘린다.
     * 높이를 기준에 넣은 게 핵심이고, 덤으로 2x1(TINY) 구간이 생긴다.
     */
    override val sizeMode =
        SizeMode.Responsive(setOf(TINY, MID_SHORT, MID, FOUR_SHORT, FOUR, WIDE_SHORT, WIDE))

    companion object {
        /** 요일\|일자\|근무\|빨강\|타입\|시각 (직렬화는 [encodeStrip]/[decodeStrip]). 비면 빈 상태 */
        val KEY_WEEK = stringPreferencesKey("week_strip")

        /** 예: "오늘 출근 07:47" (없으면 빈 문자열) */
        val KEY_SUB = stringPreferencesKey("today_sub")

        /*
         * v1.6.88: 4x1 전용 판(FOUR)을 끼워 넣었다.
         *
         * `SizeMode.Responsive`에서 `LocalSize.current`는 **여기 적은 판 크기 중 하나**이지
         * 위젯의 실폭이 아니다(에뮬 로그로 확인: 5개 판이 그대로 다 찍힌다). 그런데 폰 5열
         * 그리드에서 4칸은 약 285dp라 340dp짜리 WIDE 에 못 닿고 **190dp짜리 MID 를 고른다** —
         * 즉 3x1 과 4x1 이 같은 판을 쓰게 돼 둘을 구별할 수가 없었다.
         * 260dp 판을 하나 끼우면 3칸(≈213dp)은 MID, 4칸(≈285dp)은 FOUR 로 갈린다.
         */

        /** 2x1 — 7칸 스트립이 안 들어간다. 오늘 근무 + 시각 한 줄만 */
        private val TINY = DpSize(120.dp, 44.dp)

        /** 3x1 — 오늘·내일·모레 3칸. 실측 Pixel 5열 그리드에서 3칸 = 약 213dp */
        private val MID_SHORT = DpSize(190.dp, 48.dp)
        private val MID = DpSize(190.dp, 84.dp)

        /** 4x1(폰) — 7칸 스트립. 칸 37dp라 요일을 떼고 날짜만 */
        private val FOUR_SHORT = DpSize(260.dp, 48.dp)
        private val FOUR = DpSize(260.dp, 84.dp)

        /** 4x1(펼침·태블릿) 이상 — 칸 48dp라 "월 13"이 들어간다 */
        private val WIDE_SHORT = DpSize(340.dp, 48.dp)
        private val WIDE = DpSize(340.dp, 84.dp)
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            // 앱과 **같은 강조색**을 쓴다(v1.6.41). 인자 없는 `GlanceTheme`는 Android 12+ 에서
            // 배경화면 다이나믹 컬러를 집어 와, 앱의 오늘 칸은 초록인데 위젯의 오늘 칸만 파랑이었다
            // (에뮬 실측). 앱 스킴(`presentation.theme`)을 그대로 물려 준다.
            GlanceTheme(
                colors = androidx.glance.material3.ColorProviders(
                    light = com.sinjeong.crewcalendar.presentation.theme.LightColors,
                    dark = com.sinjeong.crewcalendar.presentation.theme.DarkColors,
                ),
            ) {
                val prefs = currentState<androidx.datastore.preferences.core.Preferences>()
                val cells = decodeStrip(prefs[KEY_WEEK].orEmpty())
                val sub = prefs[KEY_SUB].orEmpty()

                val size = LocalSize.current
                // 어느 판을 그릴지는 **판 크기**로 정한다(= 런처가 실폭으로 이미 고른 결과).
                // 글자 크기 판정만 배율을 먹인다(설계서 A-4): 배율이 커지면 같은 칸에 든 글자가
                // 커져 잘리므로, 폭을 배율로 나눈 [effW]로 요일을 떼고([narrow]) 한 단계 줄인다([small]).
                val fs = LocalContext.current.resources.configuration.fontScale.coerceIn(0.8f, 2f)
                val effW = size.width / fs                     // 글자가 커진 만큼 "좁아진" 폭
                val wide = size.width >= MID_SHORT.width       // 3칸 이상 그릴 폭이 되나
                val tall = size.height >= MID.height           // 부제 한 줄이 더 들어가나
                val narrow = effW / 7 < 46.dp                  // 칸이 좁으면 요일을 뗀다
                val small = fs >= 1.3f                         // 배율 1.3+ 는 글자 한 단계 축소

                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.surface)
                        .cornerRadius(20.dp)
                        .padding(horizontal = 8.dp, vertical = if (tall) 6.dp else 3.dp)
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
                                maxLines = 1,
                                style = TextStyle(
                                    color = GlanceTheme.colors.onSurfaceVariant,
                                    fontSize = if (!wide) (if (small) 8.5.sp else 10.sp)
                                    else if (small) 11.5.sp else 13.sp,
                                ),
                            )
                        }
                        return@Column
                    }

                    // 2x1(TINY) / 3x1(MID) / 4x1 이상(FOUR·WIDE)
                    if (size.width < MID_SHORT.width) {
                        Compact2(cells[0], sub, small)
                        return@Column
                    }
                    if (size.width < FOUR_SHORT.width) {
                        ThreeDays(cells.take(3), small, tall)
                        return@Column
                    }

                    // 부제는 높이가 남을 때만. 안 그러면 근무 코드 줄이 밀려 잘린다(v1.6.21 버그).
                    if (tall && sub.isNotBlank()) {
                        Text(
                            sub,
                            maxLines = 1,
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurface,
                                fontSize = if (narrow) (if (small) 10.5.sp else 12.sp)
                                else if (small) 11.5.sp else 13.sp,
                                fontWeight = FontWeight.Bold,
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
                                DayCell(c, i == 0, narrow, tall, small, GlanceModifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 2x1 — 오늘 근무(크게) + 시각 한 줄("출근 07:47"/"편승 12:36").
     * 시각이 없으면 부제(`sub` — 오늘/내일 구분이 이미 들어 있다).
     */
    @androidx.compose.runtime.Composable
    private fun Compact2(today: Cell, sub: String, small: Boolean) {
        val pal = today.type?.let(::dutyPalette)
        val bg = if (pal != null && pal.first != 0) ColorProvider(Color(pal.first)) else GlanceTheme.colors.surface
        val fg = if (pal != null) ColorProvider(Color(pal.second)) else GlanceTheme.colors.onSurface
        Box(
            modifier = GlanceModifier.fillMaxSize().background(bg).cornerRadius(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    today.duty.ifBlank { "·" },
                    maxLines = 1,
                    style = TextStyle(
                        color = fg,
                        fontSize = if (small) 17.sp else 19.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                val line = today.time.ifBlank { sub }
                if (line.isNotBlank()) Text(
                    line,
                    maxLines = 1,
                    style = TextStyle(color = fg, fontSize = if (small) 9.sp else 10.5.sp),
                )
            }
        }
    }

    /** 3x1 — 오늘·내일·모레 3칸. 칸이 넓으니 요일을 붙인다(narrow=false). */
    @androidx.compose.runtime.Composable
    private fun ThreeDays(cells: List<Cell>, small: Boolean, tall: Boolean) {
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            cells.forEachIndexed { i, c ->
                Box(modifier = GlanceModifier.defaultWeight().padding(horizontal = 2.dp)) {
                    DayCell(c, i == 0, narrow = false, tall = tall, small = small, GlanceModifier.fillMaxWidth())
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun DayCell(
        cell: Cell,
        isToday: Boolean,
        narrow: Boolean,
        tall: Boolean,
        small: Boolean,
        modifier: GlanceModifier,
    ) {
        // 오늘 칸은 primary 로 확실히 튀게. 나머지는 **앱 달력과 같은 근무색**([dutyPalette], v1.6.88) —
        // 타입을 모르는 옛 레코드나 ETC(배경 투명)는 종전 inverseOnSurface 로 떨어진다.
        val pal = cell.type?.let(::dutyPalette)
        val bgProvider = when {
            isToday -> GlanceTheme.colors.primary
            pal != null && pal.first != 0 -> ColorProvider(Color(pal.first))
            else -> GlanceTheme.colors.inverseOnSurface
        }
        val fg = when {
            isToday -> GlanceTheme.colors.onPrimary
            pal != null -> ColorProvider(Color(pal.second))
            else -> GlanceTheme.colors.onSurface
        }
        Column(
            modifier = modifier
                .background(bgProvider)
                .cornerRadius(10.dp)
                .padding(vertical = if (tall) 4.dp else 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 요일+날짜를 한 줄로 합쳐 근무 글자를 키울 세로 여유를 만든다.
            // 좁힌 위젯에서는 요일을 떼고(날짜만) 글자를 줄여 한 줄에 들어가게 한다.
            Text(
                if (narrow) cell.day else "${cell.dow} ${cell.day}",
                maxLines = 1,
                style = TextStyle(
                    color = if (cell.red && !isToday) GlanceTheme.colors.error else fg,
                    fontSize = if (narrow) (if (small) 8.sp else 9.sp)
                    else if (tall) (if (small) 10.sp else 11.5.sp)
                    else if (small) 9.sp else 10.5.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                cell.duty.ifBlank { "·" },
                maxLines = 1,
                style = TextStyle(
                    color = fg,
                    fontSize = if (narrow) (if (small) 9.5.sp else 11.sp)
                    else if (tall) (if (small) 14.5.sp else 16.sp)
                    else if (small) 13.5.sp else 15.sp,
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
