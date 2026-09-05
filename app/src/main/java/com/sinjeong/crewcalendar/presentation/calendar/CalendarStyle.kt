package com.sinjeong.crewcalendar.presentation.calendar

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import com.sinjeong.crewcalendar.presentation.theme.DutyColors
import com.sinjeong.crewcalendar.presentation.theme.LocalDutyColors

/**
 * 달력 탭의 **색 한 벌** — v1.7.6.
 *
 * 사용자 원문: *"마지막으로 설정에 달력 스타일도 클레이로 되게 만들어줘!"*
 * (v1.7.0 실시간 지도 클레이의 뒷이야기 — 앱 전체 클레이는 "판이 커진다"며 접었고 **달력 탭만**이다.)
 *
 * ## 규칙 — 되돌리지 말 것
 *
 * 1. **[CalendarStyle.DEFAULT] 는 v1.7.5 화면과 픽셀 하나까지 같다.** 기본값이라 한 칸이라도
 *    어긋나면 클레이를 고르지도 않은 사람의 달력이 바뀐다. 그래서 이 팔레트의 DEFAULT 값은
 *    **테마에서 뽑는다**(`MaterialTheme` · [LocalDutyColors]) — 옛 코드가 그 자리에 쓰던 식
 *    그대로다. 알파 상수는 [CalendarArgb] 가 잠근다(`CalendarStyleTest`).
 * 2. **근무 칩 색의 뜻은 안 바뀐다.** 주간(초록)·야간(보라)·비번(연보라)·휴무(빨강)·대기(노랑)·
 *    지선(하늘)의 구분은 [CLAY_DUTY] 에서도 **같은 색상군**이고, 채도만 낮추고 글자를 진하게
 *    했다. 색이 곧 근무 종별이라 계열을 바꾸면 승무원이 달력을 못 읽는다.
 * 3. **클레이는 밝은 스타일 고정** — 앱이 다크여도 달력은 크림이다(지도 클레이와 같은 이유).
 *    그래서 클레이는 [LocalDutyColors] 를 **안 본다**: 다크에서 그걸 보면 `#005229` 같은
 *    짙은 칩이 크림 바탕에 얹혀 글자가 죽는다.
 * 4. **달력 탭 밖은 안 건드린다.** 하단 탭바·상세시트·메모 모아보기·근무선택 시트·동료·식단·
 *    공지·설정은 종전 테마 그대로다. 위젯과 공유 이미지(`MonthImage`)도 그대로 — 다음 후보다.
 *
 * ## ⚠ 색 값이 [CalendarArgb] 에 **숫자로** 사는 이유
 *
 * 유닛테스트 하네스(`tools/runtests.ps1`)에는 **Compose 가 없다.** `Color(...)` 를 최상위에 두면
 * 클래스 초기화가 터진다(`presentation/live/MapStyle.kt` 와 같은 사정). 그래서 값은 `Long` 으로
 * [CalendarArgb] 에 두고 팔레트가 그 숫자를 [Color] 로 싼다. `const` 가 아니라 `val` 인 것도
 * 일부러다 — `const` 는 테스트 바이트코드에 **인라인**되어 나중에 값을 고쳐도 테스트가 안 깨진다.
 */
enum class CalendarStyle(val label: String) {
    DEFAULT("기본"), CLAY("클레이");

    companion object {
        /**
         * 저장값 → 스타일. 모르는 값·`null` 은 **기본**이다.
         * 순수 함수 — `CalendarStyleTest` 가 잠근다(안드로이드가 안 낀다).
         */
        fun of(saved: String?): CalendarStyle = entries.firstOrNull { it.name == saved } ?: DEFAULT
    }
}

internal object CalendarArgb {

    /* ── 기본(DEFAULT) — v1.7.5 의 알파. 색 자체는 테마에서 온다 ───────────── */
    /** 칸 바탕 `surfaceVariant` 에 얹는 알파 */
    val PlainAlpha = 0.55f
    /** 근무 저장된 칸 `primaryContainer` (v1.6.69) */
    val FrozenAlpha = 0.45f
    /** 오늘 칸 `primary` 물들이기 (v1.6.41 — 꽉 채우면 주간 근무칩이 묻힌다) */
    val TodayTintAlpha = 0.10f
    /** 칸 테두리 `outline` */
    val BorderAlpha = 0.18f
    /** 날짜 숫자 뒤 옅은 사각형 `onSurface` */
    val DateBadgeAlpha = 0.07f
    /** 근무변경 원래 근무(취소선) `onSurfaceVariant` */
    val StrikeAlpha = 0.75f

    /* ── 클레이(CLAY) — 지도 클레이(`MapArgb`)와 같은 팔레트 계열 ──────────── */
    /** 화면·헤더 바탕 크림 (지도 클레이 `ClayBg` 와 같은 값) */
    val ClayBg = 0xFFF6F1E7L
    /** 칸 바탕 위 */
    val ClayCell = 0xFFFFFDF8L
    /** 칸 바탕 아래 — 세로로 살짝 어두워진다(클레이의 두께감) */
    val ClayCellBottom = 0xFFF3EDE1L
    val ClayCellBorder = 0xFFE6DFD0L
    /** 그림자 — **블러 없이** 아래로 2dp 오프셋 복제(지도 클레이와 같은 방식) */
    val ClayShadow = 0xFF8A7A5AL
    val ClayShadowAlpha = 0.18f
    /** 글자 (지도 클레이 `ClayLabel` 과 같은 값) */
    val ClayText = 0xFF4E463BL
    /** 보조 글자 — 요일 평일·취소선·잘림 점 */
    val ClayTextDim = 0xFF8C8172L
    /** 일요일·공휴일 (지도 클레이 성수 빨강과 같은 값) */
    val ClaySunday = 0xFFE4573FL
    /** 토요일 (지도 클레이 운전취급역 파랑과 같은 값) */
    val ClaySaturday = 0xFF3F87C9L
    /** 오늘 테두리 */
    val ClayToday = 0xFF5CC98AL
    /** 오늘 날짜 배지 — 흰 글자가 살게 [ClayToday] 보다 진하다(5.3:1) */
    val ClayTodayBadge = 0xFF1F7A4CL
    /** 근무 저장된 날 바탕 민트 */
    val ClaySaved = 0xFFE4F5EAL
    /** 고른 칸 */
    val ClaySelected = 0xFFEFE7D8L
    /** 날짜 숫자 뒤 사각형 */
    val ClayDateBadge = 0xFFF0EADDL
    /** 취소선 */
    val ClayStrike = 0xFF7C7263L
    /** 강조 — `근무선택` 테두리·근무변경 모서리 접힘 (지도 클레이 신도림 초록) */
    val ClayAccent = 0xFF2E9A5EL
    /** 야간 초승달 */
    val ClayMoon = 0xFFE09600L

    /* 근무 칩 — **같은 색상군의 파스텔**. 배경 채도만 낮추고 글자는 진하게(전부 4.6:1 이상) */
    val ClayDutyMain = 0xFFDDF0E2L
    val ClayDutyOnMain = 0xFF1B6B3FL
    val ClayDutyNight = 0xFFEDE6F5L
    val ClayDutyOnNight = 0xFF5B4194L
    val ClayDutyOff = 0xFFEFEAF4L
    val ClayDutyOnOff = 0xFF6A5E8EL
    val ClayDutyRest = 0xFFFBE4DCL
    val ClayDutyOnRest = 0xFFB23A22L
    val ClayDutyStandby = 0xFFF8EDD2L
    val ClayDutyOnStandby = 0xFF86660FL
    val ClayDutyBranch = 0xFFDCEEF3L
    val ClayDutyOnBranch = 0xFF1B6771L
}

/**
 * 달력 한 장이 쓰는 **색 전부**. 헤더·요일 줄·칸·칩이 같은 한 벌을 본다.
 *
 * 그림 파일이 아니라 **값**이라 반응형이 안 깨진다 — 칸 높이·글자 크기·메모 줄 수·라벨 축소는
 * 종전 코드가 그대로 정한다.
 */
internal data class CalendarPalette(
    val style: CalendarStyle,
    /** 달력 화면 바탕 */
    val screenBg: Color,
    /** 월 헤더 바탕 */
    val headerBg: Color,
    /** 월 헤더 글자·아이콘(헤더 [androidx.compose.material3.Surface] 의 `contentColor`) */
    val headerInk: Color,
    /** 글자 — 날짜 숫자·출근시각·메모 */
    val text: Color,
    /** 보조 글자 — 요일 평일·메모 잘림 점 */
    val textDim: Color,
    /** 칸 바탕 */
    val cellBg: Color,
    /** 칸 바탕 세로 그라데이션 — **기본은 `null`** 이라 종전 단색 경로 그대로 간다 */
    val cellBrush: Brush?,
    val cellBorder: Color,
    /** 칸 그림자 — **기본은 `null`** 이라 한 줄도 안 그린다 */
    val shadow: Color?,
    /** 근무 저장된 날(v1.6.69) */
    val savedBg: Color,
    /** 고른 칸 */
    val selectedBg: Color,
    /** 오늘 칸에 얹는 물 */
    val todayTint: Color,
    val todayBorder: Color,
    val todayBadge: Color,
    val onToday: Color,
    /** 날짜 숫자 뒤 옅은 사각형 */
    val dateBadge: Color,
    /** 근무변경 원래 근무(취소선) */
    val strike: Color,
    /** `근무선택` 테두리·글자 */
    val accent: Color,
    /** 근무변경 모서리 접힘 */
    val corner: Color,
    /** 야간 초승달 */
    val moon: Color,
    /** 근무 종별 칩 — **뜻은 그대로**, 클레이는 채도만 낮춘 같은 색상군 */
    val duty: DutyColors,
    /** 날씨 칩이 어두운 판을 쓸까 — 클레이는 늘 밝은 판이다 */
    val darkChips: Boolean,
) {
    /** 클레이인가 — 그림자·그라데이션 같은 **클레이 전용 획**을 켜는 스위치다. */
    val clay: Boolean get() = style == CalendarStyle.CLAY
}

/** 클레이 근무 칩 — [LocalDutyColors] 의 라이트 판과 **같은 색상군**, 채도만 낮췄다. */
internal val CLAY_DUTY = DutyColors(
    main = Color(CalendarArgb.ClayDutyMain), onMain = Color(CalendarArgb.ClayDutyOnMain),
    rest = Color(CalendarArgb.ClayDutyRest), onRest = Color(CalendarArgb.ClayDutyOnRest),
    standby = Color(CalendarArgb.ClayDutyStandby), onStandby = Color(CalendarArgb.ClayDutyOnStandby),
    branch = Color(CalendarArgb.ClayDutyBranch), onBranch = Color(CalendarArgb.ClayDutyOnBranch),
    off = Color(CalendarArgb.ClayDutyOff), onOff = Color(CalendarArgb.ClayDutyOnOff),
    night = Color(CalendarArgb.ClayDutyNight), onNight = Color(CalendarArgb.ClayDutyOnNight),
    sunday = Color(CalendarArgb.ClaySunday), saturday = Color(CalendarArgb.ClaySaturday),
)

/** 클레이 — 크림 바탕·부드러운 칸·파스텔 칩. **배치는 기본과 똑같다.** */
internal val CLAY_PALETTE = CalendarPalette(
    style = CalendarStyle.CLAY,
    screenBg = Color(CalendarArgb.ClayBg),
    headerBg = Color(CalendarArgb.ClayBg),
    headerInk = Color(CalendarArgb.ClayText),
    text = Color(CalendarArgb.ClayText),
    textDim = Color(CalendarArgb.ClayTextDim),
    cellBg = Color(CalendarArgb.ClayCell),
    cellBrush = Brush.verticalGradient(
        listOf(Color(CalendarArgb.ClayCell), Color(CalendarArgb.ClayCellBottom)),
    ),
    cellBorder = Color(CalendarArgb.ClayCellBorder),
    shadow = Color(CalendarArgb.ClayShadow).copy(alpha = CalendarArgb.ClayShadowAlpha),
    savedBg = Color(CalendarArgb.ClaySaved),
    selectedBg = Color(CalendarArgb.ClaySelected),
    todayTint = Color(CalendarArgb.ClayToday).copy(alpha = 0.16f),
    todayBorder = Color(CalendarArgb.ClayToday),
    todayBadge = Color(CalendarArgb.ClayTodayBadge),
    onToday = Color.White,
    dateBadge = Color(CalendarArgb.ClayDateBadge),
    strike = Color(CalendarArgb.ClayStrike),
    accent = Color(CalendarArgb.ClayAccent),
    corner = Color(CalendarArgb.ClayAccent),
    moon = Color(CalendarArgb.ClayMoon),
    duty = CLAY_DUTY,
    darkChips = false,
)

/**
 * 지금 화면이 쓸 팔레트. [CalendarStyle.DEFAULT] 는 **테마에서 뽑는다** — 아래 식은 v1.7.5 의
 * `MainCalendarScreen` 이 그 자리에 쓰던 것을 글자 하나까지 옮긴 것이다(라이트·다크 자동 대응).
 */
@Composable
internal fun calendarPalette(style: CalendarStyle): CalendarPalette {
    if (style == CalendarStyle.CLAY) return CLAY_PALETTE
    val cs = MaterialTheme.colorScheme
    return CalendarPalette(
        style = CalendarStyle.DEFAULT,
        screenBg = cs.background,
        headerBg = cs.surface,
        headerInk = cs.onSurface,
        text = cs.onSurfaceVariant,
        textDim = cs.onSurfaceVariant,
        cellBg = cs.surfaceVariant.copy(alpha = CalendarArgb.PlainAlpha),
        cellBrush = null,
        cellBorder = cs.outline.copy(alpha = CalendarArgb.BorderAlpha),
        shadow = null,
        savedBg = cs.primaryContainer.copy(alpha = CalendarArgb.FrozenAlpha),
        selectedBg = cs.surfaceVariant,
        todayTint = cs.primary.copy(alpha = CalendarArgb.TodayTintAlpha),
        todayBorder = cs.primary,
        todayBadge = cs.primary,
        onToday = cs.onPrimary,
        dateBadge = cs.onSurface.copy(alpha = CalendarArgb.DateBadgeAlpha),
        strike = cs.onSurfaceVariant.copy(alpha = CalendarArgb.StrikeAlpha),
        accent = cs.primary,
        corner = cs.primary,
        // 라이트/다크 모두 보이는 노랑 — 배경 밝기로 고른다(다이나믹 컬러에도 대응)
        moon = if (cs.surface.luminance() > 0.5f) Color(0xFFE09600) else Color(0xFFFFD54F),
        duty = LocalDutyColors.current,
        darkChips = cs.surface.luminance() < 0.5f,
    )
}

/**
 * 클레이 그림자 — **블러 없이** 같은 모양을 아래로 [dy] 만큼 복제한다(지도 클레이와 같은 방식:
 * `BlurMaskFilter` 는 칸마다 레이어를 만든다). 기본 스타일이면 [Modifier] 를 **그대로 돌려준다** —
 * 노드 하나도 안 늘어나므로 v1.7.5 화면이 픽셀 단위로 그대로다.
 */
internal fun Modifier.clayDrop(pal: CalendarPalette, dy: Dp, radius: Dp): Modifier {
    val c = pal.shadow ?: return this
    return drawBehind {
        drawRoundRect(
            c,
            topLeft = Offset(0f, dy.toPx()),
            size = size,
            cornerRadius = CornerRadius(radius.toPx()),
        )
    }
}

/** 칸 바탕 — 클레이만 세로 그라데이션. 기본은 종전 **단색 경로 그대로**다. */
internal fun Modifier.cellFill(color: Color, brush: Brush?): Modifier =
    if (brush != null) background(brush) else background(color)
