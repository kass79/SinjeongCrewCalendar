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

/**
 * **이 앱의 UI 강조색 = 2호선 그린 하나다** (v1.6.41).
 *
 * ### 왜 secondary·tertiary 까지 직접 적어야 했나
 *
 * 종전엔 `primary` 계열만 적고 나머지는 `lightColorScheme()` 기본값에 맡겼다. 그런데 M3 기본값은
 * **베이스라인 보라 팔레트**(`secondaryContainer = #E8DEF8`)라, secondary 역할을 쓰는 컴포넌트가
 * 전부 연보라로 그려졌다 — 활성 `FilterChip`(동료 탭 소속·★그룹 칩), `SegmentedButton`(설정 테마·
 * 편승 평일/휴일), `FilledTonalButton`(달력 상단 `근무선택`), `NavigationBar` 선택 표시.
 * 그 연보라가 **야간·비번 근무색(#F6F0FD)과 같은 계열**이라 "근무색과 UI색이 헷갈린다"는
 * 지적의 실제 원인이었다.
 *
 * → `secondaryContainer`·`tertiaryContainer`를 **`primaryContainer`와 같은 값**으로 못박는다.
 *   호출부를 한 곳도 안 고치고 위 네 컴포넌트가 한꺼번에 같은 강조색이 된다.
 *   (색을 화면마다 지정하면 또 갈라진다 — 스킴 한 곳에서 끝내는 게 이 파일의 존재 이유다.)
 */
internal val LightColors = lightColorScheme(
    primary = Color(0xFF14713F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA8F2C1),
    onPrimaryContainer = Color(0xFF00210E),
    // secondary·tertiary = primary 와 같은 강조색. 기본 보라 팔레트를 덮는 것이 목적이다
    secondary = Color(0xFF14713F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFA8F2C1),
    onSecondaryContainer = Color(0xFF00210E),
    tertiary = Color(0xFF14713F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFA8F2C1),
    onTertiaryContainer = Color(0xFF00210E),
    inversePrimary = Color(0xFF85DBA3),   // 스낵바 액션 — 기본값은 보라(#D0BCFF)였다
    surface = Color(0xFFFBFDF8),
    onSurface = Color(0xFF191D19),
    surfaceVariant = Color(0xFFEFF4EC),
    onSurfaceVariant = Color(0xFF454D46),
    outline = Color(0xFFC3CABF),
    outlineVariant = Color(0xFFD7DED3),   // 구분선 — 기본값은 보라기 회색(#CAC4D0)
    error = Color(0xFFB3271E),
    errorContainer = Color(0xFFFFDAD5),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFF85DBA3),
    onPrimary = Color(0xFF00391B),
    primaryContainer = Color(0xFF005229),
    onPrimaryContainer = Color(0xFFA8F2C1),
    secondary = Color(0xFF85DBA3),
    onSecondary = Color(0xFF00391B),
    secondaryContainer = Color(0xFF005229),
    onSecondaryContainer = Color(0xFFA8F2C1),
    tertiary = Color(0xFF85DBA3),
    onTertiary = Color(0xFF00391B),
    tertiaryContainer = Color(0xFF005229),
    onTertiaryContainer = Color(0xFFA8F2C1),
    inversePrimary = Color(0xFF14713F),
    surface = Color(0xFF131712),
    onSurface = Color(0xFFE0E4DC),
    surfaceVariant = Color(0xFF1B211B),
    onSurfaceVariant = Color(0xFFADB5AA),
    outline = Color(0xFF3C443C),
    outlineVariant = Color(0xFF2C332C),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF521811),
)

/**
 * 로그인 화면 전용 고정 강조색 — 위 [LightColors]의 `primary`와 같은 값이다.
 * 로그인 배경은 다크에서도 밝은 파스텔 그라데이션으로 고정이라 스킴 색(다크에서 연두)을 쓰면
 * 흰 글자가 날아간다. 그래서 값만 나눠 쓰고 **색은 하나로 유지**한다.
 */
val BrandGreen = Color(0xFF14713F)

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

/**
 * @param dynamicColor **기본 off**(v1.6.41). 켜 두면 Android 12+ 에서 `primary`가 배경화면에서
 * 뽑혀 나온다 — 에뮬 기본 배경화면에서 실제로 파랑이 나왔고(오늘 칸·현재선택 테두리가 전부 파랑),
 * 보라 계열 배경화면이면 **야간 근무색과 같은 보라**가 된다. 근무 종별 색이 의미를 지는 앱이라
 * 강조색이 기기마다 달라지면 안 된다. 되살리려면 호출부에서 `dynamicColor = true`.
 */
@Composable
fun SinjeongTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
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
