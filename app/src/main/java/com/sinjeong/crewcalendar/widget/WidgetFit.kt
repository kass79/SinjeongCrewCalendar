package com.sinjeong.crewcalendar.widget

import kotlin.math.min

/**
 * **위젯 칸 글자 크기 — 칸의 실제 크기(dp)에서 계산한다**(v1.7.20 ③).
 *
 * 카스(2026-09-30, 폴드7 커버 · 시스템 글자 크게): *"위젯 숫자가 너무 작아보이네?"*
 *
 * v1.7.19 까지는 글자 크기가 **글자배율로 정한 계단**이었다 — 폭을 배율로 나눠 칸이 46dp 보다 좁으면
 * 날짜 8~9sp·근무 9.5~11sp 로 떨구고, 배율 1.3 이상이면 **한 단계 더** 줄였다. 그래서 큰 글자를 쓰는
 * 사람일수록 가장 작아졌고(카스 7칸 위젯: 칸 33dp 에 근무 12dp), 칸 높이도 절반만 썼다.
 *
 * 이제는 **칸 크기 → 글자 크기(dp)** 다. 배율과 무관하게 칸을 채운다(부르는 쪽이 `dp ÷ 배율` 로
 * sp 를 만들어 실제 크기가 이 값과 같아진다).
 *  · 높이: 날짜 한 줄 + 근무 한 줄이 칸 안(위아래 [PAD])에 들어가게, 글자 크기 합을 [DATE_SHARE] 로 가른다
 *    → 날짜 ≈ 칸 높이의 24%, 근무 ≈ 42%.
 *  · 폭: 글자 수로 어림한 폭([textEm])이 칸의 [WIDTH_USE] 를 넘지 않게 — 넘으면 그 칸만 줄인다
 *    (`휴23` 은 `38` 보다 작다. 달력 칩과 같은 규칙).
 *  · 두 줄 라벨(`대기`⏎`지2`)은 근무 몫을 두 줄로 나눈다.
 *  · **하한 = 종전 크기**([legacySp]) — 종전 크기가 이 칸에 들어가던 자리에서는 그 밑으로 안 내려간다
 *    ("모든 배율에서 지금 이상"). 종전이 이미 넘치던 자리(배율 2.0 의 7칸 등 — 근무 줄이 잘렸다)는
 *    하한을 버리고 칸에 맞춘다.
 *
 * 날짜 크기는 **칸마다 같다**(근무를 한글 한 줄로 보고 나눈다) — 근무만 칸마다 폭에 따라 달라진다.
 * 안드로이드 임포트 0 — `WidgetStripTest` 가 잠근다.
 */
object WidgetFit {
    /** 글줄 높이 ÷ 글자 크기 — 한글이 든 줄(대체 글꼴). 에뮬 실측 1.448 (굵게 · includeFontPadding) */
    const val LINE_HANGUL = 1.45f

    /** 숫자·기호만 든 줄. 에뮬 실측 1.352 */
    const val LINE_PLAIN = 1.36f

    /** 글자 크기 합 중 날짜 몫(날짜 : 근무 = 38 : 62) */
    const val DATE_SHARE = 0.38f

    /** 칸 안 위아래 여백(dp) — 글줄 상자 안에 글꼴 여백이 이미 있어 1dp 면 글자가 칸 테두리에 안 닿는다 */
    const val PAD = 1f

    /** 폭 가드 — 어림 폭이 칸의 97% 까지 */
    const val WIDTH_USE = 0.97f

    /** 부제(`내일 출근 7:02`) 글자 상한 = 위젯 높이의 19% — 배율 2.0 에서 부제가 칸 높이를 먹던 것을 막는다 */
    const val SUB_CAP = 0.19f

    /**
     * 글자 폭 어림(em). 한글 0.96 · 숫자 0.6 · 빈칸 0.3 · 나머지(`~` 등) 0.7.
     * 에뮬 실측(굵게) 한글 0.914 · 숫자 0.571 · `~` 0.648 — 삼성 글꼴 몫으로 조금 넉넉히 잡았다.
     */
    fun textEm(s: String): Float = s.sumOf { c ->
        when {
            c in '가'..'힣' -> 0.96
            c.isDigit() -> 0.6
            c == ' ' -> 0.3
            else -> 0.7
        }
    }.toFloat()

    fun lineOf(s: String): Float = if (s.any { it in '가'..'힣' }) LINE_HANGUL else LINE_PLAIN

    /** 날짜 줄·근무 줄 글자 크기(dp) */
    data class Fonts(val date: Float, val duty: Float)

    /**
     * [w]×[h] dp 칸에 [date] 한 줄과 [label](줄바꿈이면 여러 줄)을 넣을 글자 크기.
     * [floorDate]·[floorDuty] = 종전 크기(dp) — **들어가면** 그 밑으로 안 내려간다.
     */
    fun fit(
        w: Float, h: Float, date: String, label: String,
        floorDate: Float = 0f, floorDuty: Float = 0f,
    ): Fonts {
        val budget = h - 2 * PAD
        val lines = label.split('\n')
        val ld = lineOf(date)
        val lu = lines.maxOf(::lineOf)
        val dateW = w * WIDTH_USE / textEm(date)
        val dutyW = w * WIDTH_USE / lines.maxOf(::textEm)
        val k = budget / (ld * DATE_SHARE + LINE_HANGUL * (1 - DATE_SHARE))
        var d = min(k * DATE_SHARE, dateW)
        // 하한의 폭 검사는 여유([WIDTH_USE]) 없이 — 종전 크기는 실제로 그려지던 크기다.
        if (floorDate > d && floorDate * textEm(date) <= w && ld * floorDate + lu * lines.size * floorDuty <= budget) d = floorDate
        var u = min((budget - ld * d) / (lu * lines.size), dutyW)
        if (floorDuty > u && floorDuty * lines.maxOf(::textEm) <= w && ld * d + lu * lines.size * floorDuty <= budget) u = floorDuty
        return Fonts(d.coerceAtLeast(MIN), u.coerceAtLeast(MIN))
    }

    private const val MIN = 5f

    /**
     * 종전(v1.7.19) 칸 글자 크기(sp) `날짜 to 근무(한 줄)` — **하한으로만** 쓴다.
     * [narrow]·[tall]·[small] 은 종전 판정 그대로(`DutyWidget`). 두 줄 근무는 종전처럼 × 0.62.
     */
    fun legacySp(narrow: Boolean, tall: Boolean, small: Boolean): Pair<Float, Float> = when {
        narrow -> if (small) 8f to 9.5f else 9f to 11f
        tall -> if (small) 10f to 14.5f else 11.5f to 16f
        else -> if (small) 9f to 13.5f else 10.5f to 15f
    }

    /** 종전 2x1 판 `시각 줄 to 근무` (sp) */
    fun legacyCompactSp(small: Boolean): Pair<Float, Float> = if (small) 9f to 17f else 10.5f to 19f
}
