package com.sinjeong.crewcalendar.presentation.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// ── 2호선 그린 시드 팔레트 (디자인 시안 v1과 동일 토큰) ──
private val LightColors = lightColorScheme(
    primary = Color(0xFF14713F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA8F2C1),
    onPrimaryContainer = Color(0xFF00210E),
    surface = Color(0xFFFBFDF8),
    onSurface = Color(0xFF191D19),
    surfaceVariant = Color(0xFFEFF4EC),
    onSurfaceVariant = Color(0xFF454D46),
    outline = Color(0xFFC3CABF),
    error = Color(0xFFB3271E),
    errorContainer = Color(0xFFFFDAD5),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF85DBA3),
    onPrimary = Color(0xFF00391B),
    primaryContainer = Color(0xFF005229),
    onPrimaryContainer = Color(0xFFA8F2C1),
    surface = Color(0xFF131712),
    onSurface = Color(0xFFE0E4DC),
    surfaceVariant = Color(0xFF1B211B),
    onSurfaceVariant = Color(0xFFADB5AA),
    outline = Color(0xFF3C443C),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF521811),
)

/** 근무 종별 시맨틱 컬러 — M3 스킴 밖의 도메인 토큰 */
data class DutyColors(
    val main: Color, val onMain: Color,
    val rest: Color, val onRest: Color,
    val standby: Color, val onStandby: Color,
    val branch: Color, val onBranch: Color,
    val off: Color, val onOff: Color,
    val night: Color, val onNight: Color,
    val sunday: Color, val saturday: Color,
)

private val LightDutyColors = DutyColors(
    // 주간(녹)·휴일(빨)·대기(노) 배경 더 옅게 (v1.3.1 사용자 요청) — 글자색은 유지해 대비 확보
    main = Color(0xFFE8FAF0), onMain = Color(0xFF00210E),
    rest = Color(0xFFFFF0EC), onRest = Color(0xFFB3271E),
    standby = Color(0xFFFFF8E8), onStandby = Color(0xFF755B00),
    branch = Color(0xFFCDF4FA), onBranch = Color(0xFF006874),
    // 비번 = 야간과 같은 보라 계열, 채도만 반으로 (v1.6.21 사용자 선택).
    // 야간 다음날이 비번이라 두 칸이 한 덩어리로 보여야 근무 흐름이 읽힌다 →
    // 배경 밝기는 야간과 같게 두고(L* 95.5 vs 95.6) 채도로만 가른다(C 5.9 vs 7.0 / 글자 C 30.7 vs 56.6).
    // 배경을 더 밝게 하면 달력 칸(#F5F8F1 상당) 위에서 칩이 사라지고, 더 어둡게 하면 야간보다 진해 보인다.
    off = Color(0xFFF5F0FB), onOff = Color(0xFF74679A),   // 명암비 4.52:1 (AA)
    // 야간 = 은은한 보라 (v8 확정)
    night = Color(0xFFF6F0FD), onNight = Color(0xFF7A5AB8),
    sunday = Color(0xFFC4302B), saturday = Color(0xFF2A5DB0),
)

private val DarkDutyColors = DutyColors(
    main = Color(0xFF005229), onMain = Color(0xFFA8F2C1),
    rest = Color(0xFF521811), onRest = Color(0xFFFFB4AB),
    standby = Color(0xFF443300), onStandby = Color(0xFFF2C14B),
    branch = Color(0xFF004F58), onBranch = Color(0xFF82D3E0),
    // 비번 = 야간보다 한 단계 어둡고 채도 낮은 딥퍼플 (배경 L* 19.4 vs 25.7, C 17.5 vs 34.1).
    // 다크에서 "연하다" = 존재감이 약하다 → 종전 회록(#2B322B)이 야간보다 어두웠던 관계를 그대로 유지.
    off = Color(0xFF332B44), onOff = Color(0xFFC2B7D6),   // 명암비 7.03:1 (AAA)
    // 야간 = 딥퍼플 (v8 확정)
    night = Color(0xFF463366), onNight = Color(0xFFCFBCFF),
    sunday = Color(0xFFFF8A80), saturday = Color(0xFF8AB4F8),
)

val LocalDutyColors = staticCompositionLocalOf { LightDutyColors }

@Composable
fun SinjeongTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true, // Android 12+ 배경화면 다이나믹 컬러
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    val dutyColors = if (darkTheme) DarkDutyColors else LightDutyColors

    androidx.compose.runtime.CompositionLocalProvider(LocalDutyColors provides dutyColors) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}
