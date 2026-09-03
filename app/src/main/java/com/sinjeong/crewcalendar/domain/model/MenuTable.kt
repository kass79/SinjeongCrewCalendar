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

    /**
     * 칸 안의 줄을 다듬는다 — 빈 줄·앞뒤 공백과 **머리글 줄**([isHeaderLine])을 버리고,
     * 규칙 상한([MAX_CELL])에서 자른다.
     *
     * ⚠ **다섯 경로(사진 ML Kit · 사진 AI · PDF · hwp/hwpx · 붙여넣기)가 전부 여기를 지난다** —
     * 머리글 가드를 이 한 곳에만 두는 이유다(v1.6.85). 경로마다 한 벌씩 두면 v1.6.82 가 겪은 것처럼
     * 한쪽만 고쳐 어긋난다. 호출 그래프는 [isHeaderLine] 주석에.
     */
    internal fun tidy(raw: String): String =
        raw.split('\n').map { it.trim() }.filterNot(::isHeaderLine)
            .joinToString("\n").take(MAX_CELL)

    /** 머리글 줄에 붙는 시간·기간 부스러기 — `조식(07:30~09:00)` · `’26.8.31~9.6` */
    private const val TIME_CHARS = "\\d:~()\\[\\].,\\-–—∼〜'’‘"

    /** `9월 1일(화)` · `9/1(화)` · `8.31` 처럼 **날짜로 시작하는** 줄 */
    private val DATE_HEAD = Regex("^\\d{1,2}[월./-]\\d{1,2}일?")

    /** 줄 맨 앞의 머리글 낱말 — `구 분` · `조식` · `조식(07:30~09:00)` */
    private val LABEL_HEAD = Regex("^(구분|조식|중식|석식|아침|점심|저녁)")

    /** 글자 없이 숫자와 시간 기호뿐인 토막 — `(07:30` · `~09:00)` · `’26.8.31~9.6` */
    private val TIME_TAIL = Regex("^[$TIME_CHARS]*$")

    /** 시각(`07:30`)이나 기간(`~`)을 나타내는 표시가 줄에 들어 있나 */
    private fun hasClock(s: String) = s.any { it == ':' || it == '~' || it == '∼' || it == '〜' }

    /**
     * 이 줄이 **표의 머리글·안내 줄**인가 — 어느 경로로 들어왔든 메뉴 항목이 될 수 없는 줄.
     *
     * ## 왜 이 가드가 있나 (v1.6.85)
     *
     * 실서버 `menus/2026-08-31` 이 한 칸씩 밀려 있었다. 조식 첫 줄에 `9월 1일(화) 9월` 이
     * **메뉴 항목으로** 들어가 그 뒤가 통째로 밀린 것이다(v1.6.81 구 파서로 올린 문서 — 지금 파서의
     * 구멍은 아니다). 파서가 아무리 정확해도 표 모양이 조금만 달라지면 머리칸은 다시 샐 수 있으므로,
     * **마지막에 한 번 거르는 그물**을 둔다.
     *
     * ## 다섯 경로가 전부 [tidy] 를 지난다 (grep 전수 확인)
     *
     * | 경로 | 거쳐 가는 곳 |
     * |---|---|
     * | 사진 ML Kit | `MenuAdminScreen.readTable` → [MenuOcr.toCells] → `tidy` |
     * | PDF 글자층 | `MenuPdf.read` → [MenuOcr.toCells] → `tidy` |
     * | 사진 AI | `MenuAi.parse` → `tidy` |
     * | hwp/hwpx | `MenuHwp`/`MenuHwpx` → [toCells] → [cellsFromTable] → `tidy` |
     * | 붙여넣기 | `MenuPaste.toCells` → (탭)[cellsFromTable] / (목록)`walk` → 둘 다 `tidy` |
     *
     * ## 진짜 메뉴를 지우지 않는다
     *
     * 걸리는 것은 **날짜로 시작하는 줄 · 요일 한 글자 · 머리글 낱말+시간 · 숫자와 기호뿐인 줄 ·
     * `※` 안내** 다섯 뿐이다. 실파일 21칸의 메뉴(`두부김치국`·`달걀실파장국`·`샌드위치`·
     * `중화풍잡채덮밥` …)는 한 줄도 안 걸린다(`MenuTest` 로 잠갔다).
     *
     * ⚠ 날짜 머리글은 **뒤에 다른 글자가 붙어 있어도 줄째 버린다**(`8월 31일(월) 샌드위치`).
     * 날짜 토막만 떼면 남은 글자가 진짜 메뉴인지 머리글 부스러기인지 알 방법이 없고,
     * 잘못 남긴 한 줄이 다시 21칸을 밀어 버린다 — 한 줄을 잃는 쪽이 싸다.
     */
    internal fun isHeaderLine(line: String): Boolean {
        // 공백을 전부 지우고 본다 — `구 분`·`8월 31일(월)` 처럼 표는 머리글 안에 공백을 넣는다.
        // ⚠ 줄바꿈 없는 공백(U+00A0)은 `Char.isWhitespace()` 가 **false** 라 따로 적어 준다
        //   (`MenuOcr.parseWeekStart` 가 겪은 것과 같은 함정).
        val flat = line.filterNot { it.isWhitespace() || it == '\u00A0' }
        if (flat.isEmpty()) return true
        if (flat.startsWith("※")) return true
        if (DATE_HEAD.containsMatchIn(flat)) return true
        if (flat.length == 1 && flat in DAY_CHARS) return true
        // `조식` 단독, 또는 `조식 (07:30~09:00)` 처럼 **시계가 붙은** 머리글.
        // ⚠ 꼬리에 시계를 요구한다. 안 걸면 `조식0-1` 같은 진짜 항목까지 삼킨다(테스트가 잡았다).
        LABEL_HEAD.find(flat)?.let { m ->
            val tail = flat.substring(m.value.length)
            if (tail.isEmpty() || (hasClock(tail) && TIME_TAIL.matches(tail))) return true
        }
        // `(07:30` · `~09:00)` · `’26.8.31~9.6` — 한글이 한 글자도 없는 시각·기간 토막
        return TIME_TAIL.matches(flat) && flat.any { it.isDigit() } && hasClock(flat)
    }

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
