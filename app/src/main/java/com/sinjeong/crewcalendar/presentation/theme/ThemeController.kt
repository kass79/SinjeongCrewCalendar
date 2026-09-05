package com.sinjeong.crewcalendar.presentation.theme

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** 테마 모드: 기본은 폰 시스템 설정, 앱 내 토글로 강제 전환 (선택 저장) */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * 실시간 지도 스타일 — 설정 > 화면 > **지도 스타일**(v1.7.0).
 *
 * 사용자 원문: *"설정에 이런 클레이 디자인도 선택할수있게 가능?"*
 *
 * **[CAB] 이 기본값이다** — v1.6.99 까지의 운전실 보조설비 화면 그대로다. [CLAY] 는 색·두께·
 * 그림자만 다르고 **배치 규칙은 한 줄도 안 바뀐다**(색 한 벌은 `presentation/live/MapStyle.kt`).
 *
 * ⚠ 이 enum 은 `theme` 저장소(테마와 **같은 저장소·같은 방식**)에 `.name` 으로 저장된다 —
 * 이름을 바꾸면 사용자 선택이 조용히 기본값으로 돌아간다.
 */
enum class MapStyle(val label: String) {
    CAB("운전실 남색"), CLAY("클레이");

    companion object {
        /**
         * 저장값 → 스타일. 모르는 값·`null` 은 **기본(운전실 남색)** 이다.
         * 순수 함수 — `MapStyleTest` 가 잠근다(안드로이드가 안 낀다).
         */
        fun of(saved: String?): MapStyle = entries.firstOrNull { it.name == saved } ?: CAB
    }
}

@Singleton
class ThemeController @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("theme", Context.MODE_PRIVATE)

    private val _mode = MutableStateFlow(
        // 기본 라이트모드 (확정) — 달 아이콘/설정에서 다크·시스템 전환
        runCatching { ThemeMode.valueOf(prefs.getString("mode", null) ?: "LIGHT") }
            .getOrDefault(ThemeMode.LIGHT)
    )
    val mode: StateFlow<ThemeMode> = _mode

    /**
     * 지도 스타일(v1.7.0) — **테마와 같은 저장소**(`theme` SharedPreferences)에 산다.
     * 값이 바뀌면 흐름을 보는 화면이 곧바로 다시 그려진다(앱 재시작 없음).
     */
    private val _mapStyle = MutableStateFlow(MapStyle.of(prefs.getString("map_style", null)))
    val mapStyle: StateFlow<MapStyle> = _mapStyle

    fun setMapStyle(style: MapStyle) {
        _mapStyle.value = style
        prefs.edit().putString("map_style", style.name).apply()
    }

    /** 우상단 달 아이콘: 현재 보이는 테마의 반대로 강제 전환 */
    fun toggle(currentlyDark: Boolean) {
        set(if (currentlyDark) ThemeMode.LIGHT else ThemeMode.DARK)
    }

    fun set(mode: ThemeMode) {
        _mode.value = mode
        prefs.edit().putString("mode", mode.name).apply()
    }
}
