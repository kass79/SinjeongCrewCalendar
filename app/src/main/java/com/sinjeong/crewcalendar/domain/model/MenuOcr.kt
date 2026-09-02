package com.sinjeong.crewcalendar.domain.model

import java.time.LocalDate
import kotlin.math.abs

/**
 * 글자인식 결과를 **주간식단표 21칸**으로 앉히는 알맹이 (v1.6.80).
 *
 * ML Kit 도, 안드로이드 타입도 안 쓴다 — 좌표가 붙은 낱말 목록만 받는 순수 함수라
 * `MenuOcrTest`(JUnitCore)가 그대로 잠근다. 화면·모델 쪽 접착은 `MenuAdminScreen`이 맡는다.
 *
 * ## 왜 줄바꿈이 아니라 **좌표**인가
 *
 * 표는 7열 × 3행이다. 인식기는 "한 줄"을 표 전체 폭에 걸쳐 이어 붙이기도 하고
 * (`잡곡밥  현미밥  흑미밥 …`) 칸마다 끊기도 한다 — 어느 쪽인지는 사진마다 다르다.
 * 그래서 **줄 단위를 믿지 않고 낱말 하나하나의 중심좌표**로 열·행을 정한다.
 * (침실배정표 PDF 를 좌표·괘선으로 풀었던 것과 같은 이유 — `BundledRooms.kt` 주석 참고.)
 *
 * ## 기준점(anchor)은 표가 스스로 알려 준다
 *
 * - **행**: `조식`·`중식`·`석식` 이 왼쪽 병합 칸에 세로 가운데로 앉아 있다 → y 기준점 3개.
 * - **열**: 머리글 `월 화 수 목 금 토 일` → x 기준점 7개.
 *
 * 코팅된 표를 찍은 사진이라 **빛 반사로 일부가 안 읽힌다.** 그래서 기준점이 다 안 잡혀도
 * 되게 만들었다: 낱말의 뜻이 곧 번호이므로(`수`=2번째 열, `석식`=3번째 행) **2개만 잡히면
 * 최소제곱 직선으로 나머지를 채운다.** 그래도 2개가 안 되면 **통째로 포기하고 빈 21칸**을
 * 돌려준다 — 엉뚱하게 흩뿌린 21칸을 관리자가 지우는 것보다 빈 칸을 채우는 게 빠르다.
 */
data class OcrWord(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val cx: Float get() = (left + right) / 2f
    val cy: Float get() = (top + bottom) / 2f
    val height: Float get() = bottom - top
}

object MenuOcr {

    private val DAY_CHARS = listOf("월", "화", "수", "목", "금", "토", "일")
    private val MEAL_LABELS = listOf("조식", "중식", "석식")

    /** `8월 31일(월)` 처럼 **괄호 안에 요일 한 글자** — 실파일 머리글의 실제 모양 */
    internal val DAY_IN_PAREN = Regex("[(\\[]\\s*([월화수목금토일])\\s*[)\\]]")

    /** 기준점 → 등간격 직선. `null` = 기준점이 2개 미만이라 세울 수 없음 */
    internal data class Axis(val origin: Float, val step: Float) {
        fun at(index: Int) = origin + step * index
        /** 좌표가 몇 번째 칸인가 (반올림). 범위 밖이면 -1 */
        fun indexOf(v: Float, count: Int): Int {
            if (step == 0f) return -1
            val i = Math.round((v - origin) / step)
            return if (i in 0 until count) i else -1
        }
    }

    /**
     * (번호, 좌표) 표본에서 등간격 직선 `좌표 = origin + step*번호` 를 최소제곱으로 뽑는다.
     * 표본 2개면 두 점을 지나는 직선이고, 7개면 빛 반사로 조금 틀어진 것이 평균으로 눌린다.
     */
    internal fun fitAxis(samples: List<Pair<Int, Float>>): Axis? {
        val pts = samples.distinctBy { it.first }
        if (pts.size < 2) return null
        val n = pts.size
        val sx = pts.sumOf { it.first.toDouble() }
        val sy = pts.sumOf { it.second.toDouble() }
        val sxx = pts.sumOf { it.first.toDouble() * it.first }
        val sxy = pts.sumOf { it.first.toDouble() * it.second }
        val denom = n * sxx - sx * sx
        if (abs(denom) < 1e-6) return null
        val step = (n * sxy - sx * sy) / denom
        val origin = (sy - step * sx) / n
        if (step <= 0.0) return null
        return Axis(origin.toFloat(), step.toFloat())
    }

    /**
     * 머리글 `월`~`일` 낱말인가. `8월`·`24일`처럼 숫자가 붙은 건 제외한다.
     *
     * ⚠ **괄호 규칙이 먼저다**(v1.6.82). 실파일 머리글은 `8월 31일(월)` 인데, 여기엔 `8월`의 `월`과
     * `31일`의 `일`이 섞여 있어 "한글 한 글자" 규칙으로는 **아예 안 걸린다**. 그래서 v1.6.80~81 은
     * 요일 기준점을 하나도 못 잡았고 `fitAxis`가 null → **21칸이 통째로 비었다.** 사용자가 겪은
     * *"텍스트를 너무 인식 못한다"* 의 실체가 이것이다(사진·PDF·한글파일 **세 경로 모두** 같은 원인).
     * 괄호 안 한 글자는 오직 요일 머리글에만 나오므로 메뉴 이름과 부딪히지 않는다.
     */
    private fun dayIndexOf(raw: String): Int {
        DAY_IN_PAREN.find(raw)?.let { return DAY_CHARS.indexOf(it.groupValues[1]) }
        val s = raw.trim().trim('(', ')', '[', ']', '.', '·', '/')
        if (s.length !in 1..3 || s.any { it.isDigit() }) return -1
        val core = s.removeSuffix("요일")
        if (core.length != 1) return -1
        return DAY_CHARS.indexOf(core)
    }

    /** `조식`·`중식(07:30~09:00)` 같은 낱말인가 */
    private fun mealIndexOf(raw: String): Int {
        val s = raw.replace(" ", "")
        return MEAL_LABELS.indexOfFirst { s.contains(it) }
    }

    /**
     * 끼니 기준점 = 왼쪽 머리칸 **덩어리의 세로 가운데** (v1.6.82).
     *
     * ⚠ `조식` 낱말 하나의 y 를 그대로 쓰면 안 된다. 실제 머리칸은
     * `조식` / `(07:30` / `~09:00)` **세 줄**이고 `조식`은 그 중 맨 윗줄이라, 기준점이 칸 가운데보다
     * 한 줄(약 17pt) 위로 뜬다. 그러면 반올림 경계가 통째로 밀려 **칸 아래쪽 메뉴가 다음 끼니로
     * 넘어간다**(실파일 실측: 조식 칸 맨 아랫줄이 중식으로 감). 줄 수는 사업소 표마다 다르므로
     * 상수로 뺄 수 없다 — 덩어리로 묶어 가운데를 잡는 것이 유일하게 안 틀리는 방법이다.
     *
     * @param leftBound 첫 요일 칸의 왼쪽 — 이보다 왼쪽이 `구 분` 머리칸이다.
     */
    internal fun mealAnchors(words: List<OcrWord>, leftBound: Float): List<Pair<Int, Float>> {
        val label = words.filter { it.cx < leftBound && it.text.isNotBlank() }.sortedBy { it.cy }
        val blocks = mutableListOf<MutableList<OcrWord>>()
        for (w in label) {
            val last = blocks.lastOrNull()
            // 줄 간격 2배보다 멀면 다른 칸이다 (하단 안내문이 석식 칸에 붙는 것을 막는다)
            if (last != null && w.top - last.maxOf { it.bottom } <= w.height * 2f) last.add(w)
            else blocks.add(mutableListOf(w))
        }
        val fromBlocks = blocks.mapNotNull { b ->
            val m = mealIndexOf(b.joinToString("") { it.text })
            if (m < 0) null else m to (b.minOf { it.top } + b.maxOf { it.bottom }) / 2f
        }
        // 머리칸이 왼쪽에 없는 사진(잘려 찍힌 것)에서는 종전대로 낱말 위치를 그대로 쓴다
        return if (fromBlocks.distinctBy { it.first }.size >= 2) fromBlocks
        else words.mapNotNull { w -> mealIndexOf(w.text).takeIf { it >= 0 }?.let { it to w.cy } }
    }

    /**
     * 낱자로 흩어진 글자를 **한 줄 안의 낱말 덩어리**로 잇는다 (v1.6.82).
     *
     * PDF 글자층은 글리프 단위로 나온다 — `8` `월` `31` `일` `(` `월` `)`. 그대로 두면 `월` 하나가
     * 요일 기준점으로 잡히고, 같은 `월`이 `9월 1일(화)` 칸에도 있어 **기준점이 뒤엉킨다.**
     * 사진 인식(ML Kit)은 이미 낱말 단위라 이 함수를 거치지 않는다.
     */
    fun groupRuns(words: List<OcrWord>): List<OcrWord> {
        val sorted = words.filter { it.text.isNotBlank() }.sortedWith(compareBy({ it.cy }, { it.left }))
        val out = mutableListOf<OcrWord>()
        for (w in sorted) {
            val last = out.lastOrNull()
            if (last != null && abs(last.cy - w.cy) <= last.height * 0.4f &&
                w.left - last.right <= last.height * 1.2f && w.left >= last.left
            ) {
                // 글자 폭의 1/4보다 벌어져 있으면 원래 띄어쓰기였다
                val sep = if (w.left - last.right > last.height * 0.28f) " " else ""
                out[out.lastIndex] = last.copy(
                    text = last.text + sep + w.text,
                    left = minOf(last.left, w.left), right = maxOf(last.right, w.right),
                    top = minOf(last.top, w.top), bottom = maxOf(last.bottom, w.bottom),
                )
            } else out += w
        }
        return out
    }

    /**
     * 낱말 목록 → 21칸.
     *
     * @param words 인식된 낱말과 그 사각형. 좌표계는 픽셀이든 무엇이든 **단조 증가**면 된다.
     * @return 21칸(빈 칸은 빈 문자열). 기준점을 못 세우면 21칸 모두 빈 문자열.
     */
    fun toCells(words: List<OcrWord>): List<String> {
        val empty = List(WeeklyMenu.CELLS) { "" }
        if (words.isEmpty()) return empty

        val dayAnchors = words.mapNotNull { w -> dayIndexOf(w.text).takeIf { it >= 0 }?.let { it to w.cx } }
        val colAxis = fitAxis(dayAnchors) ?: return empty

        // 표 바깥(제목·기간·사업소명·하단 안내)을 잘라내는 테두리. 칸 한 칸의 절반만큼 여유를 준다.
        val leftBound = colAxis.at(0) - colAxis.step * 0.5f
        val rightBound = colAxis.at(WeeklyMenu.DAYS - 1) + colAxis.step * 0.5f
        val rowAxis = fitAxis(mealAnchors(words, leftBound)) ?: return empty
        val topBound = rowAxis.at(0) - rowAxis.step * 0.5f
        val bottomBound = rowAxis.at(WeeklyMenu.MEALS - 1) + rowAxis.step * 0.5f

        // (칸 → 그 칸에 떨어진 낱말들)
        val buckets = HashMap<Int, MutableList<OcrWord>>()
        for (w in words) {
            if (dayIndexOf(w.text) >= 0 || mealIndexOf(w.text) >= 0) continue   // 머리글 자신은 내용이 아니다
            if (w.text.isBlank()) continue
            if (w.cx < leftBound || w.cx > rightBound) continue
            if (w.cy < topBound || w.cy > bottomBound) continue
            val col = colAxis.indexOf(w.cx, WeeklyMenu.DAYS)
            val row = rowAxis.indexOf(w.cy, WeeklyMenu.MEALS)
            if (col < 0 || row < 0) continue
            buckets.getOrPut(col * WeeklyMenu.MEALS + row) { mutableListOf() }.add(w)
        }

        return List(WeeklyMenu.CELLS) { i -> buckets[i]?.let(::joinCell) ?: "" }
    }

    /**
     * 한 칸 안의 낱말들을 **줄로 묶는다**. 같은 줄 = y 가 글자높이의 60% 안쪽.
     * 줄은 위에서 아래로, 줄 안은 왼쪽에서 오른쪽으로. (`콩나물 무침` 처럼 띄어 쓴 메뉴가 한 줄이 된다)
     */
    private fun joinCell(words: List<OcrWord>): String {
        val sorted = words.sortedBy { it.cy }
        val medianH = sorted.map { it.height }.sorted()[sorted.size / 2].coerceAtLeast(1f)
        val tolerance = medianH * 0.6f
        val lines = mutableListOf<MutableList<OcrWord>>()
        for (w in sorted) {
            val last = lines.lastOrNull()
            if (last != null && abs(w.cy - last.map { it.cy }.average().toFloat()) <= tolerance) last.add(w)
            else lines.add(mutableListOf(w))
        }
        return lines.joinToString("\n") { line ->
            line.sortedBy { it.left }.joinToString(" ") { it.text.trim() }.trim()
        }.trim()
    }

    /**
     * `※ 기간 : '26. 8. 24 ~ '26. 8. 30` 에서 **주 시작일(월요일)** 을 뽑는다.
     *
     * 앞쪽 날짜 하나만 찾으면 되고, 그 날짜가 속한 주의 월요일로 정규화한다 —
     * 인식이 `24`를 `2A`로 읽어 하루 틀려도 같은 주면 같은 답이 나온다.
     * 두 자리 연도(`'26`)는 2000년대로 편다. 못 찾으면 null → 화면이 오늘 주를 기본값으로 쓴다.
     */
    fun parseWeekStart(fullText: String): LocalDate? {
        // ⚠ **먼저 공백을 전부 지운다**(v1.6.82). 글자를 낱자로 꺼내는 판독기는 숫자 사이에도 공백을
        // 넣는다 — 실파일에서 PDFBox 가 `’2  6 .  8 .   3 1` 로 내놓아 `\d{2,4}` 가 통째로 빗나갔다.
        // 사진 인식도 같은 이유로 `2 6` 이 되는 일이 있어 여기서 한 번에 막는다.
        val flat = fullText.filterNot { it.isWhitespace() }
        val m = Regex("(\\d{2,4})[.\\-/년](\\d{1,2})[.\\-/월](\\d{1,2})").find(flat)
            ?: return null
        val (ys, ms, ds) = m.destructured
        val year = ys.toInt().let { if (it < 100) 2000 + it else it }
        val month = ms.toInt()
        val day = ds.toInt()
        if (year !in 2000..2999 || month !in 1..12 || day !in 1..31) return null
        return runCatching { weekStartOf(LocalDate.of(year, month, day)) }.getOrNull()
    }
}
