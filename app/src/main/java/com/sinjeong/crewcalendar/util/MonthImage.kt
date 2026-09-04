package com.sinjeong.crewcalendar.util

import android.content.Context
import android.graphics.*
import androidx.core.content.FileProvider
import com.sinjeong.crewcalendar.domain.model.Bundled
import com.sinjeong.crewcalendar.domain.model.DaySchedule
import com.sinjeong.crewcalendar.domain.model.DutyType
import com.sinjeong.crewcalendar.domain.model.countsAsRestDay
import java.io.File
import java.io.FileOutputStream
import java.time.YearMonth

/** 그 달 근무표를 PNG로 그려 캐시에 저장, 공유용 content:// Uri 반환 */
fun renderMonthImage(context: Context, month: YearMonth, days: List<DaySchedule>, ownerName: String): android.net.Uri {
    val W = 1080
    val leading = month.atDay(1).dayOfWeek.value % 7   // 일=0
    val total = leading + month.lengthOfMonth()
    val rows = (total + 6) / 7
    val pad = 32f
    val headerH = 150f
    val dowH = 56f
    val cellW = (W - pad * 2) / 7f
    val cellH = 150f
    val gridTop = headerH + dowH
    val H = (gridTop + cellH * rows + pad).toInt()

    val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.drawColor(Color.WHITE)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    fun txt(s: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean = false, center: Boolean = false, right: Boolean = false) {
        p.color = color; p.textSize = size
        p.typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
        p.textAlign = if (center) Paint.Align.CENTER else if (right) Paint.Align.RIGHT else Paint.Align.LEFT
        c.drawText(s, x, y, p)
    }
    // 헤더
    // 앱바 칩과 **같은 규칙**으로 센다(충당은 안 줄고 지근만 준다). 여기서 따로 세면
    // 화면의 `휴 N개`와 공유 그림의 숫자가 갈린다.
    val restCount = days.count { it.countsAsRestDay }
    txt("${month.year % 100}년 ${month.monthValue}월", pad, 74f, 60f, 0xFF1B1B22.toInt(), bold = true)
    txt("${ownerName} 근무표 · 휴 ${restCount}개", pad, 122f, 34f, 0xFF6B6B78.toInt())
    // 요일
    val dows = listOf("일", "월", "화", "수", "목", "금", "토")
    for (i in 0..6) {
        val col = when (i) { 0 -> 0xFFC4302B.toInt(); 6 -> 0xFF2A5DB0.toInt(); else -> 0xFF6B6B78.toInt() }
        txt(dows[i], pad + cellW * i + cellW / 2, headerH + 40f, 30f, col, bold = true, center = true)
    }
    // 셀
    val byDate = days.associateBy { it.date }
    for (idx in 0 until total) {
        val r = idx / 7; val col = idx % 7
        val left = pad + cellW * col; val top = gridTop + cellH * r
        // 셀 테두리(옅게)
        p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f; p.color = 0x22000000
        c.drawRoundRect(left + 3, top + 3, left + cellW - 3, top + cellH - 3, 14f, 14f, p)
        p.style = Paint.Style.FILL
        if (idx < leading) continue
        val d = idx - leading + 1
        val day = byDate[month.atDay(d)] ?: continue
        val dow = (leading + d - 1) % 7
        val dcol = when { day.holidayName != null || dow == 0 -> 0xFFC4302B.toInt(); dow == 6 -> 0xFF2A5DB0.toInt(); else -> 0xFF444444.toInt() }
        txt("$d", left + 14, top + 40f, 30f, dcol, bold = true)
        // 공휴일·기념일·절기 이름을 날짜 옆 같은 줄에 (앱 DayCell의 nameTag/HolidayTag — MainCalendarScreen.kt:592·618).
        // 날짜 숫자 자리를 침범하면 들어갈 때까지 축소하는 것도 HolidayTag와 같은 방식.
        (day.holidayName ?: day.memorialName ?: day.seasonalTerm)?.let { name ->
            val avail = cellW - 10 - 54f          // 좌측 여백 + 날짜 숫자(30f 두 자리) 자리를 뺀 폭
            var s = 19f
            p.textSize = s; p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            while (p.measureText(name) > avail && s > 12f) { s *= 0.92f; p.textSize = s }
            // 공휴일·기념일은 빨강, 절기는 대기색(앱 nameColor = sunday / onStandby)
            val nc = if (day.holidayName != null || day.memorialName != null) 0xFFC4302B.toInt()
            else 0xFF755B00.toInt()
            txt(name, left + cellW - 10, top + 34f, s, nc, right = true)
        }
        // 근무 칩. 충당 계열만 두 줄(`대기충당`⏎`지2`)로 온다 — DutyCode.gridLabel 참고.
        val lines = day.duty.gridLabel.split('\n')
        val label = lines.last()
        if (lines.any { it.isNotBlank() }) {
            val (bg, fg) = dutyPalette(day.duty.colorType)
            val two = lines.size > 1
            // 두 줄이면 알약을 조금 키운다 — 좁은 알약에선 윗줄이 위쪽 곡선에 걸려 삐져나왔다(실측)
            val chipW = cellW * (if (two) 0.74f else 0.62f); val chipH = if (two) 62f else 52f
            val cx = left + cellW / 2; val cy = top + 82f
            if (bg != 0) {
                p.color = bg
                // 알약 = 라운드 높이의 절반 (앱 RoundedCornerShape(percent = 50), v1.6.23)
                c.drawRoundRect(cx - chipW / 2, cy - chipH / 2, cx + chipW / 2, cy + chipH / 2, chipH / 2, chipH / 2, p)
            }
            // 알약 폭에 맞을 때까지 축소. 캔버스엔 앱 칩 같은 자동축소가 없어 긴 라벨(`대기충당지2`·`돌봄휴가`)이
            // 알약 밖으로 흘러 옆 칸을 침범했다(v1.6.46). 두 줄이면 **아랫줄 다이아를 크게** — 앱 달력 칩과 같은 규칙.
            // margin = 알약 곡선 여유. 윗줄은 위쪽 곡선에 가까워 더 크게 잡는다.
            fun fitSize(s: String, base: Float, margin: Float): Float {
                p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                var ts = base
                p.textSize = ts
                while (p.measureText(s) > chipW - margin && ts > 12f) { ts *= 0.92f; p.textSize = ts }
                return ts
            }
            if (two) {
                // 두 줄 세로 배치: 윗줄 baseline cy-7 / 아랫줄 cy+21. 아랫줄을 28f 위로 더 키우면
                // 그 어센더가 윗줄 baseline을 침범해 글자가 겹친다(실측).
                txt(lines[0], cx, cy - 7f, fitSize(lines[0], 24f, 24f), fg, bold = true, center = true)
                txt(lines[1], cx, cy + 21f, fitSize(lines[1], 28f, 12f), fg, bold = true, center = true)
            } else {
                txt(label, cx, cy + 13f, fitSize(label, if (label.length >= 3) 30f else 36f, 12f), fg, bold = true, center = true)
            }
            // 야간 초승달 배지 — 앱은 Icons.Default.DarkMode 벡터(MainCalendarScreen.kt:660).
            // Canvas엔 벡터가 없어 원 두 개 차집합으로 직접 그린다. 위치도 앱과 같은 칩 왼쪽 위 모서리.
            // 공유 이미지는 배경이 흰색 고정(c.drawColor(WHITE))이라 라이트 값 0xFFE09600만 쓴다.
            if (day.duty.isNight) {
                val mr = 13f
                val mx = cx - chipW / 2; val my = cy - chipH / 2 + 2f
                val moon = Path().apply { addCircle(mx, my, mr, Path.Direction.CW) }
                val bite = Path().apply { addCircle(mx + mr * 0.45f, my - mr * 0.38f, mr * 0.95f, Path.Direction.CW) }
                moon.op(bite, Path.Op.DIFFERENCE)
                p.color = 0xFFE09600.toInt()
                c.drawPath(moon, p)
            }
        }
        // 출근시각. 없으면 휴일 운휴 다이아(본선 주간 26~29)인지 보고 그 자리를 채운다 —
        // 앱 MainCalendarScreen.kt:671~682과 같은 조건·같은 색.
        val so = day.signOn
        if (so != null) {
            txt(so, left + cellW / 2, top + 128f, 24f, 0xFF6B6B78.toInt(), center = true)
        } else if (day.duty.isWorkDay && day.duty.number != null && Bundled.isHolidayTimetable(day.date)) {
            txt("운휴", left + cellW / 2, top + 128f, 24f, 0xFFC4302B.toInt(), bold = true, center = true)
        }
    }
    // 저장
    val dir = File(context.cacheDir, "share").apply { mkdirs() }
    val f = File(dir, "duty_${month.year}_${month.monthValue}.png")
    FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
}

fun shareMonthImage(context: Context, uri: android.net.Uri) {
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(android.content.Intent.createChooser(send, "근무표 공유"))
}
