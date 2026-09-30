package com.sinjeong.crewcalendar

import com.sinjeong.crewcalendar.domain.model.DutyCode
import com.sinjeong.crewcalendar.domain.model.DutyType
import com.sinjeong.crewcalendar.widget.Cell
import com.sinjeong.crewcalendar.widget.cellLabel
import com.sinjeong.crewcalendar.widget.decodeStrip
import com.sinjeong.crewcalendar.widget.encodeStrip
import org.junit.Assert.assertEquals
import com.sinjeong.crewcalendar.widget.WidgetFit
import org.junit.Test

class WidgetStripTest {
    @Test fun `6칸 레코드 왕복`() {
        val cells = listOf(
            Cell("금", "4", "~", false, DutyType.POST_NIGHT, ""),
            Cell("토", "5", "휴2", true, DutyType.REST, ""),
            Cell("일", "6", "14", true, DutyType.MAIN_DAY, "출근 07:47"),
        )
        assertEquals(cells, decodeStrip(encodeStrip(cells)))
    }

    @Test fun `옛 4칸 레코드도 읽는다`() {
        val old = "화|28|5|0;수|29|휴3|1"
        assertEquals(
            listOf(Cell("화", "28", "5", false, null, ""), Cell("수", "29", "휴3", true, null, "")),
            decodeStrip(old),
        )
    }

    @Test fun `깨진 레코드는 버리고 나머지는 산다`() {
        assertEquals(1, decodeStrip("화|28;수|29|휴3|1").size)
    }

    @Test fun `구분자가 값에 들어와도 깨지지 않는다`() {
        val c = Cell("금", "4", "충당|지6;", false, DutyType.STANDBY, "")
        assertEquals("충당지6", decodeStrip(encodeStrip(listOf(c)))[0].duty)
    }

    /* ── v1.6.92 ⑤: 칸마다 날짜를 실어 신선도를 판정한다 ─────────────── */

    @Test fun `날짜가 왕복한다`() {
        val e = java.time.LocalDate.of(2026, 9, 5).toEpochDay()
        val cells = listOf(Cell("토", "5", "휴2", true, DutyType.REST, "", e))
        assertEquals(e, decodeStrip(encodeStrip(cells))[0].epochDay)
        assertEquals(cells, decodeStrip(encodeStrip(cells)))
    }

    /** 날짜 없는 옛 레코드는 그대로 읽히고 `epochDay = null` — 워커가 한 번 돌 때까지 종전 동작 */
    @Test fun `날짜 없는 옛 레코드는 null`() {
        assertEquals(null, decodeStrip("화|28|5|0|MAIN_DAY|출근 07:47")[0].epochDay)
        assertEquals(null, decodeStrip("화|28|5|0")[0].epochDay)
    }

    /**
     * 낡은 스트립을 알아본다 — 자정 갱신이 밀렸을 때 **어제 근무를 오늘로 강조**하던 자리.
     * 위젯은 `cells.indexOfFirst { epochDay == 오늘 }` 로 오늘 칸을 찾고, −1이면 강조를 안 건다.
     */
    @Test fun `오늘 칸을 날짜로 찾는다`() {
        val today = java.time.LocalDate.of(2026, 9, 5)
        fun strip(start: java.time.LocalDate) = (0L..6L).map {
            val d = start.plusDays(it)
            Cell("·", "${d.dayOfMonth}", "1", false, DutyType.MAIN_DAY, "", d.toEpochDay())
        }
        // 어제 만들어진 스트립: 오늘은 1번 칸 (첫 칸이 아니다)
        assertEquals(1, strip(today.minusDays(1)).indexOfFirst { it.epochDay == today.toEpochDay() })
        // 일주일 넘게 묵은 스트립: 오늘 칸이 없다 → 강조 없음 + "갱신 필요"
        assertEquals(-1, strip(today.minusDays(9)).indexOfFirst { it.epochDay == today.toEpochDay() })
    }

    /* ── v1.6.93 ⑧: 4x1 칸(≈36dp)에 긴 근무명이 안 들어간다 ─────────── */

    /**
     * 충당 계열은 **두 줄**로, 네 글자 휴가류는 **두 글자**로. 종전엔 [DutyCode.display] 를
     * 그대로 넣어 `대기충당지2` 여섯 글자가 `대기충…` 으로 잘려 **다이아가 통째로 안 보였다.**
     */
    @Test fun `위젯 칸 표기는 긴 근무명을 접는다`() {
        fun label(raw: String) = cellLabel(DutyCode.parse(raw))
        assertEquals("대기\n지2", label("대기충당 지2"))
        assertEquals("돌봄", label("돌봄휴가"))
        assertEquals("동행", label("동행휴가"))
        // 짧은 건 손대지 않는다 — 달력과 같은 글자여야 한다.
        assertEquals("14", label("14"))
        assertEquals("휴2", label("휴2"))
        // 어느 표기든 **한 줄에 세 글자를 안 넘는다**(칸 폭이 그만큼뿐이다).
        for (line in label("대기충당 지2").split('\n')) {
            org.junit.Assert.assertTrue(line, line.length <= 3)
        }
    }

    /** 줄바꿈이 섞여도 직렬화가 안 깨진다 — 구분자는 `|`·`;` 뿐이다. */
    @Test fun `두 줄 표기가 왕복한다`() {
        val c = Cell("월", "7", "대기\n지2", false, DutyType.STANDBY, "출근 07:47", 20_000L)
        assertEquals(c, decodeStrip(encodeStrip(listOf(c)))[0])
    }

    @Test fun `모든 근무 타입에 색이 있다`() {
        for (t in DutyType.entries) {
            val (bg, fg) = com.sinjeong.crewcalendar.util.dutyPalette(t)
            org.junit.Assert.assertTrue("$t fg", fg != 0)
            if (t != DutyType.ETC) org.junit.Assert.assertTrue("$t bg", bg != 0)
        }
    }

    /* ── v1.7.20 ③: 위젯 글자 크기는 칸 크기에서 (`WidgetFit`) ─────────────── */

    /** `DutyWidget` 의 칸 줄 높이·칸 폭 계산을 그대로 옮긴 것(판 W×H · 배율 fs · 칸 수 n). */
    private class Geo(val W: Float, val H: Float, val fs: Float) {
        val tall = H >= 84f
        val three = W < 260f
        val narrow = (W / fs) / 7 < 46f
        val small = fs >= 1.3f
        val n = if (three) 3 else 7
        val cellW = (W - 16f) / n - 2 * (if (three) 2f else 1f)
        val sub = minOf((if (narrow) (if (small) 10.5f else 12f) else if (small) 11.5f else 13f) * fs, H * WidgetFit.SUB_CAP)
        val rowH = H - 2 * (if (tall) 6f else 3f) - if (tall) sub * WidgetFit.LINE_HANGUL + 3f else 0f
        val date = if (narrow && !three) "30" else "수 30"
        val legacy = WidgetFit.legacySp(narrow && !three, tall, small).let { it.first * fs to it.second * fs }
    }

    private val buckets = listOf(190f to 48f, 190f to 84f, 260f to 48f, 260f to 84f, 340f to 48f, 340f to 84f)
    private val scales = listOf(1.0f, 1.15f, 1.3f, 1.5f, 1.7f, 2.0f)
    private val labels = listOf("38", "1", "휴23", "~", "돌봄", "지대11", "대기\n지2")

    private fun height(date: String, label: String, f: WidgetFit.Fonts): Float {
        val lines = label.split('\n')
        return WidgetFit.lineOf(date) * f.date + lines.maxOf(WidgetFit::lineOf) * lines.size * f.duty
    }

    /**
     * 카스 실기기(2026-09-30 캡처): 폴드7 커버 · 7칸 위젯 262dp → FOUR 판(260×84) · 배율 1.3.
     * 종전: 날짜 8sp·근무 9.5sp(× 1.3 = 10.4·12.35dp) — 칸 높이의 절반만 썼다.
     */
    @Test fun `카스 7칸 위젯 — 숫자가 커지고 날짜는 칸마다 같다`() {
        val g = Geo(260f, 84f, 1.3f)
        val num = WidgetFit.fit(g.cellW, g.rowH, g.date, "38", g.legacy.first, g.legacy.second)
        val rest = WidgetFit.fit(g.cellW, g.rowH, g.date, "휴23", g.legacy.first, g.legacy.second)
        org.junit.Assert.assertTrue("날짜 ${num.date}", num.date >= g.legacy.first * 1.1f)
        org.junit.Assert.assertTrue("근무 숫자 ${num.duty}", num.duty >= g.legacy.second * 1.6f)
        // `휴23` 은 폭이 모자라 숫자보다 작지만 종전보다는 크다
        org.junit.Assert.assertTrue("휴23 ${rest.duty}", rest.duty > g.legacy.second && rest.duty < num.duty)
        assertEquals(num.date, rest.date, 0.001f)
        // 날짜 ≈ 칸 높이의 24%, 근무 ≈ 42% 안팎
        org.junit.Assert.assertTrue(num.date / g.rowH in 0.22f..0.27f)
        org.junit.Assert.assertTrue(num.duty / g.rowH in 0.40f..0.47f)
    }

    /** 세로로 안 넘치고(두 줄 라벨 포함), 가로는 어림 폭 안 — 판 6 × 배율 6 × 라벨 7 */
    @Test fun `어느 판·배율·라벨도 칸을 넘지 않는다`() {
        for ((W, H) in buckets) for (fs in scales) for (lab0 in labels) {
            val g = Geo(W, H, fs)
            val lab = if (g.tall) lab0 else lab0.replace("\n", "")
            val f = WidgetFit.fit(g.cellW, g.rowH, g.date, lab, g.legacy.first, g.legacy.second)
            val tag = "${W}x$H fs$fs '$lab' → $f (칸 ${g.cellW}×${g.rowH})"
            org.junit.Assert.assertTrue(tag, height(g.date, lab, f) <= g.rowH - 2 * WidgetFit.PAD + 0.01f)
            org.junit.Assert.assertTrue(tag, f.duty * lab.split('\n').maxOf(WidgetFit::textEm) <= g.cellW + 0.01f)
            org.junit.Assert.assertTrue(tag, f.date * WidgetFit.textEm(g.date) <= g.cellW + 0.01f)
        }
    }

    /**
     * **모든 배율에서 지금 이상** — 종전 크기가 그 칸에 들어가던 자리면 새 크기는 그보다 작지 않다.
     * 종전이 이미 넘치던 자리(배율 1.7~2.0 의 7칸 등 — 근무 줄이 잘렸다)는 하한을 버리고 칸에 맞춘다.
     */
    @Test fun `종전 크기가 들어가던 자리에서는 종전보다 작아지지 않는다`() {
        var checked = 0
        for ((W, H) in buckets) for (fs in scales) for (lab0 in labels) {
            val g = Geo(W, H, fs)
            val two = '\n' in lab0 && g.tall
            val lab = if (g.tall) lab0 else lab0.replace("\n", "")
            val od = g.legacy.first
            val ou = g.legacy.second * if (two) 0.62f else 1f
            val oldFits = height(g.date, lab, WidgetFit.Fonts(od, ou)) <= g.rowH - 2 * WidgetFit.PAD &&
                ou * lab.split('\n').maxOf(WidgetFit::textEm) <= g.cellW && od * WidgetFit.textEm(g.date) <= g.cellW
            if (!oldFits) continue
            checked++
            val f = WidgetFit.fit(g.cellW, g.rowH, g.date, lab, od, ou)
            val tag = "${W}x$H fs$fs '$lab' 종전 $od/$ou → $f"
            org.junit.Assert.assertTrue(tag, f.date >= od - 0.001f && f.duty >= ou - 0.001f)
        }
        org.junit.Assert.assertTrue("검사한 조합 $checked", checked > 100)
    }

    @Test fun `2x1 은 근무를 크게, 시각 줄을 작게`() {
        // TINY_TALL 판(120×84) · 배율 1.0: 상자 104 × 72dp. 종전 근무 19sp·시각 10.5sp
        val f = WidgetFit.fit(104f, 72f, "출근 07:47", "38", 10.5f, 19f)
        org.junit.Assert.assertTrue("$f", f.duty > 19f && f.date >= 10.5f && f.duty > f.date * 1.4f)
        org.junit.Assert.assertTrue(f.date * WidgetFit.textEm("출근 07:47") <= 104f)
    }
}
