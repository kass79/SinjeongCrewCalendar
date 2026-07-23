package com.sinjeong.crewcalendar.util

import android.content.Context
import android.graphics.*
import androidx.core.content.FileProvider
import com.sinjeong.crewcalendar.domain.model.DaySchedule
import com.sinjeong.crewcalendar.domain.model.DutyType
import java.io.File
import java.io.FileOutputStream
import java.time.YearMonth

/** 근무 종별 [배경, 글자] 색 (라이트 톤과 동일) */
private fun dutyColors(t: DutyType): Pair<Int, Int> = when (t) {
    DutyType.MAIN_DAY, DutyType.OFFICE -> 0xFFE8FAF0.toInt() to 0xFF00512B.toInt()
    DutyType.MAIN_NIGHT, DutyType.BRANCH_NIGHT, DutyType.SPECIAL -> 0xFFF6F0FD.toInt() to 0xFF7A5AB8.toInt()
    DutyType.POST_NIGHT -> 0xFFE5E9E1.toInt() to 0xFF565D55.toInt()
    DutyType.REST, DutyType.BRANCH_REST -> 0xFFFFF0EC.toInt() to 0xFFB3271E.toInt()
    DutyType.STANDBY, DutyType.BRANCH_STANDBY -> 0xFFFFF8E8.toInt() to 0xFF755B00.toInt()
    DutyType.BRANCH -> 0xFFE8FAF0.toInt() to 0xFF00512B.toInt()
    DutyType.ETC -> 0x00000000 to 0xFF888888.toInt()
}

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
    val restCount = days.count { it.duty.isRest }
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
        day.holidayName?.let { txt(it, left + cellW - 10, top + 34f, 19f, 0xFFC4302B.toInt(), right = true) }
        // 근무 칩
        val label = day.duty.display
        if (label.isNotBlank()) {
            val (bg, fg) = dutyColors(day.duty.type)
            val chipW = cellW * 0.62f; val chipH = 52f
            val cx = left + cellW / 2; val cy = top + 82f
            if (bg != 0) {
                p.color = bg
                c.drawRoundRect(cx - chipW / 2, cy - chipH / 2, cx + chipW / 2, cy + chipH / 2, 12f, 12f, p)
            }
            txt(label, cx, cy + 13f, if (label.length >= 3) 30f else 36f, fg, bold = true, center = true)
        }
        day.signOn?.let { txt(it, left + cellW / 2, top + 128f, 24f, 0xFF6B6B78.toInt(), center = true) }
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
