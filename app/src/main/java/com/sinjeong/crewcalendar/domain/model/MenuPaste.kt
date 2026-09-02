package com.sinjeong.crewcalendar.domain.model

/**
 * **글자를 붙여넣어 21칸 채우기** — 마지막 안전망 (v1.6.82 ②-3).
 *
 * 한글파일·PDF 는 표 칸을 그대로 읽으므로 여기까지 올 일이 거의 없다. 그래도
 * 파일이 아예 안 열리는 날(새 형식·손상·공유가 사진뿐)에 관리자가 **손으로 21칸을 다시 치는 일**은
 * 없어야 한다. 어디서 복사해 오든 붙여넣으면 나눌 수 있는 만큼 나눠 준다.
 *
 * ## 무엇을 알아듣는가
 *
 * | 붙여넣은 모양 | 어떻게 나뉘나 |
 * |---|---|
 * | **탭이 든 글자**(한글·엑셀에서 표를 복사) | 탭=칸, 줄=행 → [MenuTable] 이 그대로 처리 |
 * | 칸 사이가 **빈 줄**로 나뉜 목록 | 빈 줄이 칸 경계 |
 * | `조식`·`중식`·`석식` 머리글이 섞인 목록 | 머리글이 나오면 그 끼니로, 요일은 처음부터 |
 * | `월`·`8/31(월)` 같은 요일 머리글이 섞인 목록 | 그 요일 칸으로 곧장 |
 *
 * ## 무엇을 못 알아듣는가 (일부러 안 찍는다)
 *
 * 칸 경계가 **어디에도 없는** 줄줄이 목록(예: 조식 7칸 40줄이 빈 줄 없이 이어진 것)은
 * 사람이 봐도 어디서 끊기는지 알 수 없다. `잡곡밥`으로 끊자니 실파일에 `샌드위치`·
 * `중화풍잡채덮밥`으로 시작하는 칸이 있고, 김치로 끊자니 `두부김치국`이 칸 **가운데**에 있다.
 * 잘못 나눈 21칸을 지우는 것이 빈 칸을 채우는 것보다 오래 걸린다 —
 * [MenuOcr] 와 같은 판단이라 **못 나누면 나눈 만큼만** 주고 화면이 몇 칸인지 알려 준다.
 */
object MenuPaste {

    /** 붙여넣은 글자 → 21칸. 못 나눈 자리는 빈 문자열 */
    fun toCells(raw: String): List<String> {
        val text = raw.replace("\r\n", "\n").replace('\r', '\n')
        if (text.isBlank()) return List(WeeklyMenu.CELLS) { "" }
        // ① 탭이 있으면 표를 복사해 온 것이다 — 격자를 그대로 세워 MenuTable 에 넘긴다
        if (text.contains('\t')) tsvCells(text)?.let { return it }
        return walk(text)
    }

    /** 탭·줄 = 격자. [MenuTable.cellsFromTable] 이 방향(요일=열/행)까지 알아서 가른다 */
    private fun tsvCells(text: String): List<String>? {
        val grid = text.split('\n').map { it.split('\t') }
        val cells = grid.flatMapIndexed { r, row ->
            row.mapIndexed { c, v -> DocCell(r, c, 1, 1, v.trim()) }
        }
        return MenuTable.cellsFromTable(cells)?.takeIf { list -> list.count { it.isNotBlank() } >= 2 }
    }

    /**
     * 줄을 위에서 아래로 훑으며 칸을 쌓는다.
     *
     * - **빈 줄** = 칸 끝(다음 요일로).
     * - **끼니 머리글**(`조식`…) = 그 끼니로 옮기고 요일을 월요일로 되감는다.
     * - **요일 머리글**(`월`·`8/31(월)`) = 그 요일로 곧장 (표를 세로로 옮겨 적은 경우).
     * - 이미 찬 칸에 또 쓰게 되면 **다음 끼니**로 넘긴다 — 요일 머리글만 세 벌 반복한 목록이 풀린다.
     */
    private fun walk(text: String): List<String> {
        val out = MutableList(WeeklyMenu.CELLS) { "" }
        var meal = 0
        var day = 0
        var open: MutableList<String>? = null

        fun flush() {
            val lines = open ?: return
            open = null
            if (lines.isEmpty()) return
            if (day >= WeeklyMenu.DAYS) { day = 0; meal++ }
            if (meal >= WeeklyMenu.MEALS) return
            var i = meal
            // 이미 찬 칸이면 아래 끼니를 찾는다 (요일 머리글이 세 벌 반복된 목록)
            while (i < WeeklyMenu.MEALS && out[day * WeeklyMenu.MEALS + i].isNotBlank()) i++
            if (i >= WeeklyMenu.MEALS) return
            meal = i
            out[day * WeeklyMenu.MEALS + meal] = MenuTable.tidy(lines.joinToString("\n"))
            day++
        }

        for (rawLine in text.split('\n')) {
            val line = rawLine.trim()
            when {
                line.isEmpty() -> flush()
                MenuTable.mealIndexIn(line) >= 0 && line.length <= 20 -> {
                    flush(); meal = MenuTable.mealIndexIn(line); day = 0
                }
                MenuTable.dayIndexIn(line) >= 0 -> {
                    flush(); day = MenuTable.dayIndexIn(line)
                }
                else -> (open ?: mutableListOf<String>().also { open = it }).add(line)
            }
        }
        flush()
        return out
    }
}
