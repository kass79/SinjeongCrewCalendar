package com.sinjeong.crewcalendar

import com.sinjeong.crewcalendar.domain.model.DocCell
import com.sinjeong.crewcalendar.domain.model.Meal
import com.sinjeong.crewcalendar.domain.model.MenuDoc
import com.sinjeong.crewcalendar.domain.model.MenuHwpx
import com.sinjeong.crewcalendar.domain.model.MenuOcr
import com.sinjeong.crewcalendar.domain.model.MenuTable
import com.sinjeong.crewcalendar.domain.model.OcrWord
import com.sinjeong.crewcalendar.domain.model.WeeklyMenu
import com.sinjeong.crewcalendar.domain.model.menuEmoji
import com.sinjeong.crewcalendar.domain.model.weekStartOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 구내식당 주간식단표(v1.6.80)의 계산 근거를 고정한다.
 *
 * 여기서 깨지면 **틀린 메뉴가 사람을 밥 먹으러 움직이게 한다** — 특히 주 경계 판정.
 * 실행법은 [PatternTest] KDoc 참고(JUnitCore 직접 실행).
 */
class MenuTest {

    // ── 주 경계 ─────────────────────────────────────────────

    /**
     * **일요일은 그 주의 마지막 날이다.** 로케일 기본(일요일 시작)을 쓰면 여기서 하루 밀리고,
     * 그러면 일요일에 앱이 다음 주 표를 "이번 주"라고 보여준다.
     */
    @Test fun sunday_belongs_to_the_week_that_started_last_monday() {
        assertEquals(LocalDate.of(2026, 8, 24), weekStartOf(LocalDate.of(2026, 8, 30))) // 일
        assertEquals(LocalDate.of(2026, 8, 24), weekStartOf(LocalDate.of(2026, 8, 24))) // 월
        assertEquals(LocalDate.of(2026, 8, 24), weekStartOf(LocalDate.of(2026, 8, 29))) // 토
        assertEquals(LocalDate.of(2026, 8, 31), weekStartOf(LocalDate.of(2026, 8, 31))) // 다음 월
    }

    /** 월 경계를 걸친 주(8/31 월 ~ 9/6 일)도 한 주다 — 문서 하나로 다뤄져야 한다 */
    @Test fun a_week_spanning_two_months_stays_one_week() {
        val start = weekStartOf(LocalDate.of(2026, 9, 3))
        assertEquals(LocalDate.of(2026, 8, 31), start)
        assertEquals(LocalDate.of(2026, 9, 6), WeeklyMenu.empty(start).weekEnd)
        // 그 주의 모든 날이 같은 주 시작일을 가리킨다
        (0L..6L).forEach { assertEquals(start, weekStartOf(start.plusDays(it))) }
    }

    /** 해 넘김도 같은 규칙 — 2026-12-28(월) 주가 2027-01-03(일)까지 간다 */
    @Test fun a_week_spanning_two_years_stays_one_week() {
        assertEquals(LocalDate.of(2026, 12, 28), weekStartOf(LocalDate.of(2027, 1, 3)))
    }

    /**
     * 【핵심 회귀】 **일요일에 다음 주 표를 올려도 그날 메뉴가 유지된다.**
     *
     * 사용자 확정: *"주간 식단표가 일요일쯤 나오니까.. 2개 식단표가 필요하긴 한데.."*
     * 한 장만 들고 덮어쓰는 구조였으면 이 시나리오에서 일요일 점심이 화면에서 사라진다.
     */
    @Test fun uploading_next_week_on_sunday_keeps_todays_menu() {
        val sunday = LocalDate.of(2026, 8, 30)
        val thisWeek = weekStartOf(sunday)                 // 8/24
        val nextWeek = thisWeek.plusWeeks(1)               // 8/31

        val store = mutableMapOf<LocalDate, WeeklyMenu>()
        store[thisWeek] = WeeklyMenu.empty(thisWeek).withCell(6, Meal.LUNCH, "잡곡밥\n김치찌개")
        // 일요일에 관리자가 다음 주 표를 올린다 — 키가 다르므로 기존 문서를 건드리지 않는다
        store[nextWeek] = WeeklyMenu.empty(nextWeek).withCell(0, Meal.LUNCH, "비빔밥")

        assertEquals("잡곡밥\n김치찌개", store[thisWeek]!!.cell(6, Meal.LUNCH))
        assertEquals(2, store.size)
        assertTrue(store.containsKey(nextWeek))
    }

    /** 지난 주 문서는 이번 주 키로 못 꺼낸다 = 화면에 지난주 메뉴가 뜰 길이 없다 */
    @Test fun last_weeks_document_is_never_reachable_as_this_week() {
        val today = LocalDate.of(2026, 8, 26)
        val thisWeek = weekStartOf(today)
        val lastWeek = thisWeek.minusWeeks(1)
        val store = mapOf(lastWeek to WeeklyMenu.empty(lastWeek).withCell(0, Meal.LUNCH, "옛날 메뉴"))
        assertNull(store[thisWeek])
    }

    // ── 21칸 모델 ───────────────────────────────────────────

    @Test fun cell_index_is_day_major() {
        val m = WeeklyMenu.empty(LocalDate.of(2026, 8, 24))
            .withCell(0, Meal.BREAKFAST, "월조")
            .withCell(6, Meal.DINNER, "일석")
        assertEquals(21, m.cells.size)
        assertEquals("월조", m.cells[0])
        assertEquals("일석", m.cells[20])
        assertEquals("일석", m.cell(6, Meal.DINNER))
        assertEquals(2, m.filledCells)
        assertFalse(m.isBlank)
    }

    @Test fun items_drop_blank_lines_and_trim() {
        val m = WeeklyMenu.empty(LocalDate.of(2026, 8, 24))
            .withCell(1, Meal.LUNCH, " 잡곡밥 \n\n  북어국\n포기김치\n  ")
        assertEquals(listOf("잡곡밥", "북어국", "포기김치"), m.items(1, Meal.LUNCH))
    }

    // ── 이모지 ─────────────────────────────────────────────

    /** 국물이 먼저다 — `계란국`은 달걀이 아니라 국이다 */
    @Test fun emoji_prefers_the_more_specific_keyword() {
        assertEquals("🍲", menuEmoji("계란국"))
        assertEquals("🥚", menuEmoji("달걀장조림"))
        assertEquals("🥬", menuEmoji("포기김치"))
        assertEquals("🍜", menuEmoji("잔치국수"))     // `국`이 아니라 `국수`
        assertEquals("🍖", menuEmoji("돈까스"))
        assertEquals("🍚", menuEmoji("잡곡밥"))
        assertEquals("🍲", menuEmoji("된장찌개"))
    }

    /** 못 맞히면 **null** — 사용자 확정: 엉뚱한 이모지보다 없는 게 낫다 */
    @Test fun emoji_is_null_when_nothing_matches() {
        assertNull(menuEmoji("깻잎지"))
        assertNull(menuEmoji("단무지"))
        assertNull(menuEmoji(""))
    }

    /** 한 글자 `김`·`배`·`차`를 뺀 결과가 실제로 안전한지 */
    @Test fun single_char_keywords_do_not_misfire() {
        assertEquals("🥬", menuEmoji("배추김치"))     // 김치 (배·김에 먼저 걸리면 안 된다)
        assertEquals("🍲", menuEmoji("차돌된장국"))   // 국 (차 → 🍵 가 아니다)
        assertEquals("🍙", menuEmoji("김자반"))       // 자반김은 밥반찬 — 구이(🍖)로 새면 안 된다
    }

    // ── 기간 파싱 ───────────────────────────────────────────

    @Test fun period_parses_the_two_digit_year_form() {
        assertEquals(
            LocalDate.of(2026, 8, 24),
            MenuOcr.parseWeekStart("※ 기간 : '26. 8. 24 ~ '26. 8. 30"),
        )
    }

    @Test fun period_normalises_any_day_of_that_week_to_monday() {
        // 인식이 24를 26으로 잘못 읽어도 같은 주면 같은 답
        assertEquals(LocalDate.of(2026, 8, 24), MenuOcr.parseWeekStart("기간 2026. 8. 26 ~"))
        assertEquals(LocalDate.of(2026, 8, 24), MenuOcr.parseWeekStart("기간 2026년 8월 30일"))
    }

    @Test fun period_is_null_when_there_is_no_date() {
        assertNull(MenuOcr.parseWeekStart("주간식단표 서울교통공사 신정차량사업소 구내식당"))
        assertNull(MenuOcr.parseWeekStart(""))
    }

    // ── 좌표 → 21칸 ─────────────────────────────────────────

    /**
     * 실제 표를 흉내 낸 좌표. 열 7개(x=200부터 150씩) × 행 3개(y=300부터 260씩)이고
     * 머리글·기간·하단 안내가 표 밖에 섞여 있다.
     */
    private fun word(t: String, cx: Float, cy: Float, w: Float = 60f, h: Float = 24f) =
        OcrWord(t, cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)

    private fun sampleTable(): List<OcrWord> {
        val words = mutableListOf<OcrWord>()
        // 표 바깥 — 절대 칸에 들어가면 안 되는 것들
        words += word("주간식단표", 600f, 60f)
        words += word("기간", 500f, 110f)
        words += word("'26.", 600f, 110f)
        words += word("식단가", 400f, 1400f)
        // 열 머리글
        val dayX = (0..6).map { 200f + it * 150f }
        listOf("월", "화", "수", "목", "금", "토", "일").forEachIndexed { i, d ->
            words += word(d, dayX[i], 200f, w = 26f)
        }
        // 행 머리글 (왼쪽 병합 칸)
        val mealY = listOf(300f, 560f, 820f)
        listOf("조식", "중식", "석식").forEachIndexed { i, m -> words += word(m, 80f, mealY[i]) }
        // 내용: 월요일 조식 두 줄, 수요일 중식 한 줄(띄어쓴 두 낱말), 일요일 석식 한 줄
        words += word("잡곡밥", dayX[0], 260f)
        words += word("포기김치", dayX[0], 300f)
        words += word("콩나물", dayX[2] - 25f, 540f, w = 44f)
        words += word("무침", dayX[2] + 25f, 542f, w = 40f)
        words += word("비빔밥", dayX[6], 800f)
        return words
    }

    @Test fun words_land_in_the_right_cell_by_coordinates() {
        val cells = MenuOcr.toCells(sampleTable())
        assertEquals(21, cells.size)
        val m = WeeklyMenu(LocalDate.of(2026, 8, 24), cells)
        assertEquals(listOf("잡곡밥", "포기김치"), m.items(0, Meal.BREAKFAST))
        // 같은 줄로 묶여 한 메뉴가 된다 (열이 갈리면 `콩나물`·`무침`이 따로 앉는다)
        assertEquals(listOf("콩나물 무침"), m.items(2, Meal.LUNCH))
        assertEquals(listOf("비빔밥"), m.items(6, Meal.DINNER))
        // 표 밖 글자(제목·기간·식단가)는 어느 칸에도 안 들어간다
        assertTrue(cells.none { it.contains("주간식단표") || it.contains("식단가") || it.contains("기간") })
        assertEquals(3, m.filledCells)
    }

    /**
     * 빛 반사로 머리글 일부가 안 읽혀도 된다 — 낱말의 **뜻이 곧 번호**라 두 개만 있으면
     * 나머지 열·행을 직선으로 채운다. (`화`·`토`만 남기고, 끼니는 `조식`·`석식`만 남긴다)
     */
    @Test fun missing_headers_are_interpolated_from_two_anchors() {
        val kept = sampleTable().filter {
            it.text !in setOf("월", "수", "목", "금", "일", "중식")
        }
        val m = WeeklyMenu(LocalDate.of(2026, 8, 24), MenuOcr.toCells(kept))
        assertEquals(listOf("잡곡밥", "포기김치"), m.items(0, Meal.BREAKFAST))
        assertEquals(listOf("콩나물 무침"), m.items(2, Meal.LUNCH))
        assertEquals(listOf("비빔밥"), m.items(6, Meal.DINNER))
    }

    /**
     * 기준점이 하나도 안 잡히면 **21칸 전부 비운다.** 엉뚱하게 흩뿌리면 관리자가
     * 채우는 것보다 지우는 데 더 오래 걸린다.
     */
    @Test fun no_anchors_means_all_cells_empty_not_garbage() {
        val junk = listOf(word("잡곡밥", 200f, 300f), word("포기김치", 350f, 560f))
        val cells = MenuOcr.toCells(junk)
        assertEquals(21, cells.size)
        assertTrue(cells.all { it.isBlank() })
        assertTrue(MenuOcr.toCells(emptyList()).all { it.isBlank() })
    }

    /** 등간격 직선 맞추기 — 두 점이면 그 두 점을 지나고, 흩어진 점은 평균으로 눌린다 */
    @Test fun axis_fit_needs_two_points_and_averages_noise() {
        assertNull(MenuOcr.fitAxis(listOf(0 to 100f)))
        val a = MenuOcr.fitAxis(listOf(1 to 350f, 5 to 950f))!!
        assertEquals(200f, a.origin, 0.01f)
        assertEquals(150f, a.step, 0.01f)
        assertEquals(2, a.indexOf(500f, 7))
        assertEquals(-1, a.indexOf(-500f, 7))
        assertNotNull(MenuOcr.fitAxis(listOf(0 to 200f, 1 to 352f, 2 to 498f)))
    }

    // ── 한글파일(.hwp/.hwpx) 표 → 21칸 (v1.6.81 ④) ─────────────
    //
    // ⚠ 여기서 `MenuHwp`(.hwp)는 **부르지 않는다** — 그쪽만 `kr.dogfoot:hwplib` 을 쓰는데
    //   JUnitCore 직접 실행의 클래스패스에는 그 jar 가 없다. 두 형식이 **같은 [MenuTable] 로
    //   합류**하므로 여기서 잠그는 것이 곧 .hwp 의 21칸 배치도 잠그는 것이고,
    //   .hwp 는 실기기(에뮬)에서 진짜 파일로 확인한다(docs/project-notes.md).

    /** 표 한 칸 만들기 도우미 */
    private fun dc(r: Int, c: Int, t: String, rs: Int = 1, cs: Int = 1) = DocCell(r, c, rs, cs, t)

    /** 구내식당 표의 보통 모양: 머리행 = 요일, 첫 열 = 끼니 */
    private fun normalTable(): List<DocCell> = buildList {
        add(dc(0, 0, "구분"))
        listOf("월", "화", "수", "목", "금", "토", "일").forEachIndexed { i, d ->
            add(dc(0, i + 1, "8/${24 + i}($d)"))
        }
        listOf("조식", "중식", "석식").forEachIndexed { m, label ->
            add(dc(m + 1, 0, label))
            for (d in 0..6) add(dc(m + 1, d + 1, "$label${d}밥\n$label${d}국"))
        }
    }

    @Test fun a_normal_menu_table_maps_to_21_cells() {
        val cells = MenuTable.cellsFromTable(normalTable())!!
        assertEquals(21, cells.size)
        // index = 요일*3 + 끼니
        assertEquals("조식0밥\n조식0국", cells[0 * 3 + 0])   // 월 조식
        assertEquals("석식0밥\n석식0국", cells[0 * 3 + 2])   // 월 석식
        assertEquals("중식6밥\n중식6국", cells[6 * 3 + 1])   // 일 중식
        assertTrue(cells.none { it.isBlank() })
    }

    /**
     * **뒤집힌 표도 읽는다** — 요일이 행, 끼니가 열인 문서가 있다.
     * 방향은 못 박지 않고 머리칸이 어떻게 늘어서 있는지로 판정한다.
     */
    @Test fun a_transposed_table_maps_to_the_same_21_cells() {
        val flipped = normalTable().map { DocCell(it.col, it.row, it.colSpan, it.rowSpan, it.text) }
        val cells = MenuTable.cellsFromTable(flipped)!!
        assertEquals("조식0밥\n조식0국", cells[0 * 3 + 0])
        assertEquals("중식6밥\n중식6국", cells[6 * 3 + 1])
    }

    /** 끼니 이름이 두 행에 걸쳐 **병합**돼 있으면 그 두 행이 다 그 끼니다 */
    @Test fun a_merged_meal_label_covers_every_row_it_spans() {
        val t = listOf(
            dc(0, 1, "월"), dc(0, 2, "화"),
            dc(1, 0, "조식", rs = 2), dc(1, 1, "흑미밥"), dc(1, 2, "잡곡밥"),
            dc(2, 1, "북어국"), dc(2, 2, "미역국"),
            dc(3, 0, "중식"), dc(3, 1, "제육볶음"), dc(3, 2, "돈까스"),
        )
        val cells = MenuTable.cellsFromTable(t)!!
        assertEquals("흑미밥\n북어국", cells[0 * 3 + 0])   // 월 조식 — 병합된 두 행이 합쳐진다
        assertEquals("잡곡밥\n미역국", cells[1 * 3 + 0])   // 화 조식
        assertEquals("제육볶음", cells[0 * 3 + 1])         // 월 중식
        assertTrue(cells[0 * 3 + 2].isBlank())            // 석식 머리칸이 없다 → 빈 칸
    }

    /** 식단표가 아닌 표는 **포기한다**(null) — 엉뚱하게 앉히면 지우는 게 더 오래 걸린다 */
    @Test fun a_table_without_day_and_meal_headers_is_refused() {
        val t = listOf(dc(0, 0, "성명"), dc(0, 1, "사번"), dc(1, 0, "홍길동"), dc(1, 1, "12345"))
        assertNull(MenuTable.cellsFromTable(t))
        // 요일만 있고 끼니가 없어도 포기한다
        assertNull(MenuTable.cellsFromTable(listOf(dc(0, 0, "월"), dc(0, 1, "화"), dc(1, 0, "1"))))
    }

    /** 표가 여럿이면 **가장 많이 채워지는 한 장**을 고른다(머리말 표·범례 표에 안 속는다) */
    @Test fun the_best_filled_table_wins_when_a_document_has_several() {
        val junk = listOf(dc(0, 0, "성명"), dc(0, 1, "사번"))
        val doc = MenuDoc(listOf(junk, normalTable()), "")
        assertEquals("조식0밥\n조식0국", MenuTable.toCells(doc)[0])
        // 쓸 만한 표가 하나도 없으면 21칸 전부 빈 문자열(수동 편집으로 떨어진다)
        assertTrue(MenuTable.toCells(MenuDoc(listOf(junk), "")).all { it.isBlank() })
    }

    /**
     * 머리칸 판정 — 한글만 남겼을 때 **한 글자**면 요일이다.
     * 끼니는 길이를 걸어, 메뉴가 잔뜩 든 칸에 `중식`이 섞여도 머리칸으로 오인하지 않는다.
     */
    @Test fun header_cells_are_told_apart_from_menu_cells() {
        assertEquals(0, MenuTable.dayIndexIn("월"))
        assertEquals(0, MenuTable.dayIndexIn("8/24(월)"))
        assertEquals(0, MenuTable.dayIndexIn("월요일"))
        assertEquals(6, MenuTable.dayIndexIn("8. 30.\n일"))
        assertEquals(-1, MenuTable.dayIndexIn("잡곡밥"))
        assertEquals(-1, MenuTable.dayIndexIn(""))

        assertEquals(0, MenuTable.mealIndexIn("조식"))
        assertEquals(0, MenuTable.mealIndexIn("조식(07:30~09:00)"))
        assertEquals(0, MenuTable.mealIndexIn("아침"))       // 사업소마다 말이 갈린다
        assertEquals(2, MenuTable.mealIndexIn("석식"))
        assertEquals(-1, MenuTable.mealIndexIn("잡곡밥\n북어국\n포기김치\n중식보다 긴 메뉴 목록입니다"))
    }

    /** 칸 길이는 `firestore.rules` 상한(300자)에서 자른다 — 넘기면 서버가 통째로 거부한다 */
    @Test fun a_cell_is_capped_at_the_server_limit() {
        val long = (1..80).joinToString("\n") { "메뉴$it" }
        assertTrue(long.length > MenuTable.MAX_CELL)
        assertEquals(MenuTable.MAX_CELL, MenuTable.tidy(long).length)
        // 빈 줄·앞뒤 공백은 버린다
        assertEquals("흑미밥\n북어국", MenuTable.tidy("  흑미밥 \n\n  북어국\n \n"))
    }

    /**
     * **.hwpx 는 zip + XML 이라 자바 표준만으로 읽는다**(APK 증가 0바이트).
     * 여기서 진짜 zip 을 만들어 21칸까지 통째로 잠근다 — 특히 실제 hwpx 처럼
     * `hp:subList`(글자)를 `hp:cellAddr`(주소)보다 **먼저** 써서 그 함정을 재현한다.
     */
    @Test fun a_real_hwpx_zip_is_read_into_21_cells() {
        val days = listOf("월", "화", "수", "목", "금", "토", "일")
        val meals = listOf("조식", "중식", "석식")
        fun tc(row: Int, col: Int, lines: List<String>): String {
            val paras = lines.joinToString("") { "<hp:p><hp:run><hp:t>$it</hp:t></hp:run></hp:p>" }
            return "<hp:tc><hp:subList>$paras</hp:subList>" +
                "<hp:cellAddr colAddr=\"$col\" rowAddr=\"$row\"/>" +
                "<hp:cellSpan colSpan=\"1\" rowSpan=\"1\"/></hp:tc>"
        }
        val rows = StringBuilder()
        rows.append("<hp:tr>").append(tc(0, 0, listOf("구분")))
        days.forEachIndexed { i, d -> rows.append(tc(0, i + 1, listOf("8/${24 + i}($d)"))) }
        rows.append("</hp:tr>")
        meals.forEachIndexed { m, label ->
            rows.append("<hp:tr>").append(tc(m + 1, 0, listOf(label)))
            for (d in 0..6) rows.append(tc(m + 1, d + 1, listOf("$label$d-1", "$label$d-2")))
            rows.append("</hp:tr>")
        }
        val section = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<hs:sec xmlns:hs=\"http://www.hancom.co.kr/hwpml/2011/section\"" +
            " xmlns:hp=\"http://www.hancom.co.kr/hwpml/2011/paragraph\">" +
            "<hp:p><hp:run><hp:t>구내식당 주간식단표 기간 : '26. 8. 24 ~ '26. 8. 30</hp:t></hp:run></hp:p>" +
            "<hp:p><hp:run><hp:tbl rowCnt=\"4\" colCnt=\"8\">$rows</hp:tbl></hp:run></hp:p></hs:sec>"

        val zipped = java.io.ByteArrayOutputStream().also { out ->
            java.util.zip.ZipOutputStream(out).use { z ->
                z.putNextEntry(java.util.zip.ZipEntry("mimetype"))
                z.write("application/hwp+zip".toByteArray())
                z.putNextEntry(java.util.zip.ZipEntry("Contents/section0.xml"))
                z.write(section.toByteArray(Charsets.UTF_8))
            }
        }.toByteArray()

        val doc = MenuHwpx.read(java.io.ByteArrayInputStream(zipped))
        assertEquals(1, doc.tables.size)
        assertEquals(32, doc.tables[0].size)               // 4행 x 8열
        val cells = MenuTable.toCells(doc)
        assertEquals("조식0-1\n조식0-2", cells[0 * 3 + 0])  // 월 조식, 문단 두 줄이 그대로 두 줄
        assertEquals("석식6-1\n석식6-2", cells[6 * 3 + 2])  // 일 석식
        assertTrue(cells.none { it.isBlank() })
        // 기간 문구는 문서 전체 글자에서 읽어 주 시작일(월요일)이 된다
        assertEquals(LocalDate.of(2026, 8, 24), MenuOcr.parseWeekStart(doc.text))
        // zip 판별 — 앞머리 `PK`
        assertTrue(MenuHwpx.looksLikeZip(zipped.copyOf(8)))
        assertFalse(MenuHwpx.looksLikeZip("%PDF-1.7".toByteArray()))
    }
}
