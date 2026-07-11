package com.sinjeong.crewcalendar.presentation.theme

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** 테마 모드: 기본은 폰 시스템 설정, 앱 내 토글로 강제 전환 (선택 저장) */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

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

    /** 우상단 달 아이콘: 현재 보이는 테마의 반대로 강제 전환 */
    fun toggle(currentlyDark: Boolean) {
        set(if (currentlyDark) ThemeMode.LIGHT else ThemeMode.DARK)
    }

    fun set(mode: ThemeMode) {
        _mode.value = mode
        prefs.edit().putString("mode", mode.name).apply()
    }
}
