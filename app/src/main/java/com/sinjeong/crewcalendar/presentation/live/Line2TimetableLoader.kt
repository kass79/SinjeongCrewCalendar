package com.sinjeong.crewcalendar.presentation.live

import android.content.Context
import com.sinjeong.crewcalendar.domain.model.Line2Timetable

/** assets/timetable/line2.csv 를 한 번만 읽는다. 실패하면 null(지연 표시만 빠진다). */
object Line2TimetableLoader {
    @Volatile private var cached: Line2Timetable? = null
    /** "2026-09-04" — 설정 화면 "열차 시간표 …판" 표시용 */
    @Volatile var fetchedLabel: String = ""
        private set
    fun get(ctx: Context): Line2Timetable? = cached ?: synchronized(this) {
        cached ?: runCatching {
            val text = ctx.assets.open("timetable/line2.csv").bufferedReader().use { it.readText() }
            fetchedLabel = Regex("fetched=(\\S+)").find(text)?.groupValues?.get(1).orEmpty()
            Line2Timetable.parse(text)
        }.getOrNull().also { cached = it }
    }
}
