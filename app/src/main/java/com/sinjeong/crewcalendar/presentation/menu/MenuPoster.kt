package com.sinjeong.crewcalendar.presentation.menu

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sinjeong.crewcalendar.domain.model.Meal
import com.sinjeong.crewcalendar.domain.model.WeeklyMenu
import com.sinjeong.crewcalendar.domain.model.menuEmoji

/**
 * 주간식단표 포스터 2종 (v1.6.80).
 *
 * ## 왜 이미지가 아니라 **앱이 직접 그리는가**
 *
 * 사용자가 견본을 AI 이미지 생성으로 만들어 왔는데 그 안의 한글이 여럿 틀려 있었다
 * (`충무식→홍무식` · `얼큰국→얼콘국` · `달걀장조림→달결장조림` · `신정차량사업소→산정차량사업스`).
 * 견본은 **레이아웃·색·구성의 참고**일 뿐이고, 글자는 관리자가 확정한 텍스트를 그대로 렌더한다.
 *
 * ## 왜 세로 흐름인가
 *
 * 원본은 7열 × 3행 표지만 360dp 폰에서 4열로 나누면 칸 하나가 85dp — 메뉴 4~6줄이 안 들어간다.
 * 그래서 **하루씩 아래로** 흐르게 하고 끼니 머리글을 날마다 되풀이한다. 1배에서 그냥 읽히고,
 * 확대는 글자배율을 크게 쓰는 사람을 위한 덤이다(벡터라 몇 배로 키워도 선명하다).
 *
 * ## 글꼴
 *
 * 빈티지 = [FontFamily.Serif] → 안드로이드 시스템 **Noto Serif CJK KR**(명조).
 * 파스텔 = [FontFamily.Default] → Noto Sans CJK KR.
 * **폰트 파일을 새로 넣지 않았다** — APK 증가 0바이트다. 자세한 판단 근거는 docs/project-notes.md.
 */
enum class MenuStyle(val label: String) { VINTAGE("빈티지"), PASTEL("파스텔") }

/** 설정 > 화면 > 식단표 스타일. 값 하나라 기존 `settings` SharedPreferences 를 그대로 쓴다. */
const val MENU_STYLE_KEY = "menu_style"

fun menuStyleOf(ctx: Context): MenuStyle =
    runCatching {
        MenuStyle.valueOf(
            ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getString(MENU_STYLE_KEY, null) ?: MenuStyle.VINTAGE.name
        )
    }.getOrDefault(MenuStyle.VINTAGE)

fun setMenuStyle(ctx: Context, style: MenuStyle) {
    ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
        .edit().putString(MENU_STYLE_KEY, style.name).apply()
}

/**
 * 포스터 한 벌의 색. 라이트/다크를 **짝으로** 정한다 — 다크에서 글자가 사라진 사고(v1.6.40)를
 * 되풀이하지 않으려고 배경만 바꾸는 일이 없게 한 곳에 모았다.
 */
private data class Palette(
    val paper: Color,
    val ink: Color,
    val headerBg: Color,
    val headerFg: Color,
    val dayBg: Color,
    val dayFg: Color,
    val cardBg: Color,
    val cardBorder: Color,
    val mealFg: Color,
    val ribbonBg: Color,
    val ribbonFg: Color,
)

private fun vintagePalette(dark: Boolean) = if (dark) Palette(
    paper = Color(0xFF241E17), ink = Color(0xFFF0E2C8),
    headerBg = Color(0xFF7A2E1C), headerFg = Color(0xFFFDF3DF),
    dayBg = Color(0xFF6B2A1A), dayFg = Color(0xFFFDF3DF),
    cardBg = Color(0xFF2F2720), cardBorder = Color(0xFF9C6B33),
    mealFg = Color(0xFFE0A85C),
    ribbonBg = Color(0xFF7A2E1C), ribbonFg = Color(0xFFFDF3DF),
) else Palette(
    paper = Color(0xFFFBF2DE), ink = Color(0xFF4A2A18),
    headerBg = Color(0xFF6E2B1A), headerFg = Color(0xFFFDF3DF),
    dayBg = Color(0xFF7C3520), dayFg = Color(0xFFFDF3DF),
    cardBg = Color(0xFFFFF8EA), cardBorder = Color(0xFFD9A05B),
    mealFg = Color(0xFF9A5A1E),
    ribbonBg = Color(0xFF7C3520), ribbonFg = Color(0xFFFDF3DF),
)

private fun pastelPalette(dark: Boolean) = if (dark) Palette(
    paper = Color(0xFF17181C), ink = Color(0xFFE8E6EC),
    headerBg = Color(0xFF6E3A4C), headerFg = Color(0xFFFFE7EF),
    dayBg = Color(0xFF2A2C33), dayFg = Color(0xFFE8E6EC),
    cardBg = Color(0xFF1F2126), cardBorder = Color(0xFF3D4048),
    mealFg = Color(0xFFB9A7C7),
    ribbonBg = Color(0xFF6E3A4C), ribbonFg = Color(0xFFFFE7EF),
) else Palette(
    paper = Color(0xFFFFFDFE), ink = Color(0xFF3A3540),
    headerBg = Color(0xFFF9C9D8), headerFg = Color(0xFF6B2D42),
    dayBg = Color(0xFFF3F1F6), dayFg = Color(0xFF3A3540),
    cardBg = Color(0xFFFFFFFF), cardBorder = Color(0xFFE7E2EC),
    mealFg = Color(0xFF7C6A8C),
    ribbonBg = Color(0xFFF9C9D8), ribbonFg = Color(0xFF6B2D42),
)

/** 파스텔 스타일의 요일별 색 + 미니 일러스트(이모지). 견본의 "요일마다 다른 파스텔"을 옮긴 것 */
private val PASTEL_DAY = listOf(
    Color(0xFFDFF3E3) to "🌱", Color(0xFFFDE2EC) to "🌸", Color(0xFFDCEBFA) to "☁️",
    Color(0xFFEBE2FA) to "🐰", Color(0xFFFDF0D5) to "⭐", Color(0xFFDDF1F5) to "🐟",
    Color(0xFFFAE3DA) to "🍀",
)
private val PASTEL_DAY_DARK = listOf(
    Color(0xFF24352A), Color(0xFF39262E), Color(0xFF23303C),
    Color(0xFF2C2740), Color(0xFF3A3324), Color(0xFF223339), Color(0xFF3A2B24),
)

/**
 * 포스터 본체. **크기를 스스로 정하지 않는다** — 부모가 준 폭을 그대로 쓰고 높이는 내용만큼 늘어난다.
 * 그래야 [MenuDialog] 의 확대·이동과 공유 이미지 캡처가 같은 한 벌을 쓴다.
 */
@Composable
fun MenuPoster(
    menu: WeeklyMenu,
    style: MenuStyle,
    modifier: Modifier = Modifier,
) {
    // 앱 안 다크 토글이 시스템과 다를 수 있어 실제 배경 밝기로 판단한다(WeatherChip 과 같은 규칙)
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val p = if (style == MenuStyle.VINTAGE) vintagePalette(dark) else pastelPalette(dark)
    val serif = style == MenuStyle.VINTAGE
    val family = if (serif) FontFamily.Serif else FontFamily.Default
    val ms = menu.weekStart
    val me = menu.weekEnd

    Column(
        modifier
            .background(p.paper)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ── 머리 ────────────────────────────────────────────
        Box(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(if (serif) 6.dp else 22.dp))
                .background(p.headerBg)
                .padding(vertical = 10.dp, horizontal = 12.dp),
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "주간식단표", color = p.headerFg, fontFamily = family,
                    fontSize = 21.sp, fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    "WEEKLY MENU · ${ms.year}.${ms.monthValue}.${ms.dayOfMonth}" +
                        " - ${me.monthValue}.${me.dayOfMonth}",
                    color = p.headerFg.copy(alpha = 0.9f), fontFamily = family,
                    fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (serif) {
                RetroTrain(Modifier.size(46.dp, 24.dp))
                Spacer(Modifier.width(8.dp))
            } else {
                Text("🍽", fontSize = 18.sp)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                "서울교통공사 신정차량사업소 구내식당",
                color = p.ink, fontFamily = family,
                fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
        }

        // ── 하루씩 ──────────────────────────────────────────
        for (d in 0 until WeeklyMenu.DAYS) {
            val date = ms.plusDays(d.toLong())
            val dayBg = when {
                style == MenuStyle.VINTAGE -> p.dayBg
                dark -> PASTEL_DAY_DARK[d]
                else -> PASTEL_DAY[d].first
            }
            val dayFg = if (style == MenuStyle.VINTAGE) p.dayFg else p.ink
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(if (serif) 8.dp else 16.dp))
                    .background(dayBg)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${date.monthValue}월 ${date.dayOfMonth}일",
                    color = dayFg, fontFamily = family,
                    fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    WeeklyMenu.DAY_LABELS[d],
                    color = dayFg.copy(alpha = 0.85f), fontFamily = family,
                    fontSize = 13.sp, fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                if (style == MenuStyle.PASTEL) Text(PASTEL_DAY[d].second, fontSize = 15.sp)
            }

            for (meal in Meal.entries) {
                val items = menu.items(d, meal)
                Column(
                    Modifier.fillMaxWidth()
                        .padding(start = 6.dp)
                        .clip(RoundedCornerShape(if (serif) 6.dp else 14.dp))
                        .background(p.cardBg)
                        .border(1.dp, p.cardBorder, RoundedCornerShape(if (serif) 6.dp else 14.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        "${meal.emoji} ${meal.label} ${meal.time}",
                        color = p.mealFg, fontFamily = family,
                        fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                    )
                    if (items.isEmpty()) {
                        Text(
                            "-", color = p.ink.copy(alpha = 0.45f), fontFamily = family, fontSize = 12.sp,
                        )
                    } else items.forEach { item ->
                        // 이모지는 맞는 게 있을 때만. 없으면 불릿만 — 엉뚱한 그림보다 없는 게 낫다.
                        val e = menuEmoji(item)
                        Text(
                            "· $item" + if (e != null) " $e" else "",
                            color = p.ink, fontFamily = family,
                            fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }

        // ── 리본 ────────────────────────────────────────────
        Box(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(if (serif) 4.dp else 999.dp))
                .background(p.ribbonBg)
                .padding(vertical = 8.dp),
        ) {
            Text(
                if (serif) "정식 3,000원" else "🍽 정식 3,000원",
                color = p.ribbonFg, fontFamily = family,
                fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * 빈티지 머리의 레트로 전철 — 견본의 초록/크림 톤 일러스트를 도형 몇 개로 옮겼다.
 * (PNG 를 넣으면 APK 가 늘고 다크모드에서 배경이 튄다. 벡터라 어느 배율에서도 선명하다.)
 */
@Composable
private fun RetroTrain(modifier: Modifier = Modifier) {
    val body = Color(0xFF2F6B4F)
    val cream = Color(0xFFF5E7C6)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        // 차체
        drawRoundRect(
            color = body,
            topLeft = Offset(0f, h * 0.10f),
            size = Size(w, h * 0.66f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.22f),
        )
        // 창문 3개
        val winW = w * 0.20f
        val winH = h * 0.26f
        for (i in 0..2) {
            drawRoundRect(
                color = cream,
                topLeft = Offset(w * 0.08f + i * (winW + w * 0.06f), h * 0.22f),
                size = Size(winW, winH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.06f),
            )
        }
        // 아래 크림 띠
        drawRect(cream, Offset(0f, h * 0.60f), Size(w, h * 0.07f))
        // 바퀴 2개
        drawCircle(body, h * 0.13f, Offset(w * 0.22f, h * 0.85f))
        drawCircle(body, h * 0.13f, Offset(w * 0.74f, h * 0.85f))
        // 선로
        drawRect(body.copy(alpha = 0.55f), Offset(0f, h * 0.96f), Size(w, h * 0.05f))
    }
}
