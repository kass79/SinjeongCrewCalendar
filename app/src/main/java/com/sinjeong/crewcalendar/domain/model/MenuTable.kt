package com.sinjeong.crewcalendar.domain.model

/**
 * 문서(한글파일)에서 읽은 **표를 주간식단표 21칸으로 앉히는** 알맹이 (v1.6.81 ④).
 *
 * ## 왜 이게 사진·PDF 글자인식보다 나은가
 *
 * `MenuOcr`은 글자 **좌표**로 열·행을 추정한다 — 코팅된 표를 찍은 사진이라 빛 반사가 있고,
 * 기준점을 못 잡으면 통째로 포기한다. 한글파일(.hwp/.hwpx)에는 **표 칸이 그대로 들어 있어서**
 * 추정이 필요 없다. 칸마다 격자 주소(`행·열`)와 병합 크기가 붙어 오므로 **읽으면 곧 정답**이다.
 * 사용자: *"주간식단표 한글파일 업로드도 가능하게 해, hwp"*.
 *
 * ## 표 방향을 스스로 알아낸다
 *
 * 구내식당 표는 대개 `요일 = 열 / 끼니 = 행`인데 그 반대인 문서도 있다. 그래서 방향을 못 박지 않고
 * **머리칸이 어떻게 늘어서 있는지**로 판정한다:
 *  · 요일(`월`…`일`) 머리칸이 **한 행에** 몰려 있고 끼니(`조식`…) 머리칸이 **한 열에** 몰려 있으면
 *    → 요일 = 열, 끼니 = 행
 *  · 그 반대면 → 요일 = 행, 끼니 = 열
 *  · 둘 다 아니면 **포기하고 빈 21칸**을 준다(관리자가 손으로 채운다). 엉뚱하게 앉힌 21칸을
 *    지우는 것보다 빈 칸을 채우는 게 빠르다 — `MenuOcr`과 같은 판단이다.
 *
 * 병합 칸도 그대로 받는다: 머리칸이 **덮는 행·열 범위**를 교차시켜 그 안에 있는 칸을 내용으로 본다
 * (`조식`이 두 행에 걸쳐 병합돼 있어도 두 행이 다 그 끼니다).
 */
data class DocCell(
    val row: Int,
    val col: Int,
    val rowSpan: Int,
    val colSpan: Int,
    val text: String,
) {
    val rows: IntRange get() = row until row + maxOf(1, rowSpan)
    val cols: IntRange get() = col until col + maxOf(1, colSpan)
}

/**
 * 문서 한 장에서 뽑아낸 것.
 * @param tables 표 여러 장(표가 아닌 그림·글상자는 안 들어온다)
 * @param text 문서 전체 글자 — `MenuOcr.parseWeekStart`가 `※ 기간 : …`을 찾는 데 쓴다
 */
data class MenuDoc(val tables: List<List<DocCell>>, val text: String)

object MenuTable {

    private val DAY_CHARS = listOf("월", "화", "수", "목", "금", "토", "일")

    /** 끼니 머리글. 사업소마다 `조식`/`아침`처럼 말이 갈려 둘 다 받는다 */
    private val MEAL_WORDS = listOf(
        listOf("조식", "아침"),
        listOf("중식", "점심"),
        listOf("석식", "저녁"),
    )

    /** 한 칸에 담을 수 있는 최대 글자 수 — `firestore.rules`의 칸 길이 제한과 같은 값 */
    const val MAX_CELL = 300

    /**
     * 표 여러 장 중 **가장 많이 채워지는 한 장**을 골라 21칸으로. 어느 표도 못 앉히면 21칸 전부 빈 문자열.
     * (문서에 표가 여럿일 수 있다 — 머리말 표·범례 표 따위. 식단표가 늘 가장 잘 채워진다.)
     */
    fun toCells(doc: MenuDoc): List<String> =
        doc.tables.mapNotNull(::cellsFromTable)
            .maxByOrNull { list -> list.count { it.isNotBlank() } }
            ?: List(WeeklyMenu.CELLS) { "" }

    /** 표 한 장 → 21칸. 요일·끼니 머리칸을 못 찾으면 null */
    fun cellsFromTable(table: List<DocCell>): List<String>? {
        val dayHit = HashMap<Int, DocCell>()
        val mealHit = HashMap<Int, DocCell>()
        for (c in table) {
            dayIndexIn(c.text).takeIf { it >= 0 }?.let { dayHit.putIfAbsent(it, c) }
            mealIndexIn(c.text).takeIf { it >= 0 }?.let { mealHit.putIfAbsent(it, c) }
        }
        // 방향을 정하려면 양쪽 다 최소 2칸이 필요하다 (하나로는 행인지 열인지 알 수 없다)
        if (dayHit.size < 2 || mealHit.size < 2) return null

        val daysAcross = dayHit.values.map { it.row }.distinct().size == 1   // 요일이 한 행에
        val daysDown = dayHit.values.map { it.col }.distinct().size == 1     // 요일이 한 열에
        val mealsDown = mealHit.values.map { it.col }.distinct().size == 1   // 끼니가 한 열에
        val mealsAcross = mealHit.values.map { it.row }.distinct().size == 1 // 끼니가 한 행에
        val daysAreColumns = when {
            daysAcross && mealsDown -> true
            daysDown && mealsAcross -> false
            else -> return null   // 머리칸이 흩어져 있다 = 이 표가 아니거나 못 읽는 모양
        }

        val out = MutableList(WeeklyMenu.CELLS) { "" }
        for (d in 0 until WeeklyMenu.DAYS) {
            val dh = dayHit[d] ?: continue
            for (m in 0 until WeeklyMenu.MEALS) {
                val mh = mealHit[m] ?: continue
                // 머리칸이 덮는 범위의 교차점 = 그 요일·그 끼니의 내용 칸(들)
                val rows = if (daysAreColumns) mh.rows else dh.rows
                val cols = if (daysAreColumns) dh.cols else mh.cols
                out[d * WeeklyMenu.MEALS + m] = table
                    .filter { it !== dh && it !== mh && it.row in rows && it.col in cols }
                    .sortedWith(compareBy({ it.row }, { it.col }))
                    .joinToString("\n") { it.text }
                    .let(::tidy)
            }
        }
        return out
    }

    /** 칸 안의 줄을 다듬는다 — 빈 줄·앞뒤 공백을 버리고, 규칙 상한([MAX_CELL])에서 자른다 */
    internal fun tidy(raw: String): String =
        raw.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
            .joinToString("\n").take(MAX_CELL)

    /**
     * 이 칸이 요일 머리칸인가 → 0(월)~6(일), 아니면 -1.
     *
     * `월`·`월요일`·`8/24(월)`·`8. 24.\n월` 을 다 받는다 — **한글만 남겼을 때 한 글자**면 요일이다.
     * `잡곡밥`(3자)·`북어국`(3자) 같은 메뉴는 자동으로 걸러진다.
     */
    internal fun dayIndexIn(cell: String): Int {
        // ⚠ **괄호 규칙이 먼저다**(v1.6.82). 실파일 머리칸은 `8월 31일(월)` 이라 한글만 남기면
        // `월일월` 세 글자가 되어 아래 규칙에 **하나도 안 걸렸다.** 그래서 v1.6.81 은 요일 머리칸을
        // 0개 찾았고 `cellsFromTable`이 곧바로 null → 한글파일이 **빈 21칸**을 돌려줬다.
        // 사용자가 겪은 *"텍스트를 너무 인식 못한다"* 의 실체(사진·PDF·한글파일 공통 원인)다.
        MenuOcr.DAY_IN_PAREN.find(cell)?.let { return DAY_CHARS.indexOf(it.groupValues[1]) }
        val hangul = cell.filter { it in '가'..'힣' }.replace("요일", "")
        return if (hangul.length == 1) DAY_CHARS.indexOf(hangul) else -1
    }

    /**
     * 이 칸이 끼니 머리칸인가 → 0(조식)~2(석식), 아니면 -1.
     *
     * ⚠ **길이를 건다.** 안 걸면 메뉴가 잔뜩 든 칸에 우연히 `중식`이 섞였을 때 그 칸이
     * 머리칸으로 잡혀 표가 통째로 어긋난다. `조식(07:30~09:00)`은 15자라 넉넉히 들어온다.
     */
    internal fun mealIndexIn(cell: String): Int {
        val s = cell.replace(" ", "").replace("\n", "")
        if (s.isEmpty() || s.length > 20) return -1
        return MEAL_WORDS.indexOfFirst { words -> words.any { s.contains(it) } }
    }
}
