package com.sinjeong.crewcalendar

import com.sinjeong.crewcalendar.domain.model.Meal
import com.sinjeong.crewcalendar.domain.model.MenuOcr
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
}
