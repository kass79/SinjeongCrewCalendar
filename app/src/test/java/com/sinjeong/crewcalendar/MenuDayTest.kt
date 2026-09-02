package com.sinjeong.crewcalendar

import com.sinjeong.crewcalendar.domain.model.Meal
import com.sinjeong.crewcalendar.domain.model.MealSlot
import com.sinjeong.crewcalendar.domain.model.MenuIcon
import com.sinjeong.crewcalendar.domain.model.mainDish
import com.sinjeong.crewcalendar.domain.model.menuIcon
import com.sinjeong.crewcalendar.domain.model.nextMealAt
import com.sinjeong.crewcalendar.domain.model.soupDish
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 식단표 **하루씩 보기**(v1.6.82)의 계산 근거를 고정한다.
 *
 * 여기서 깨지면 **엉뚱한 끼니가 크게 뜬다** — 아침 8시에 석식을 펼쳐 놓는 식으로.
 * 실행법은 [PatternTest] KDoc 참고(JUnitCore 직접 실행).
 */
class MenuDayTest {

    private fun at(d: String, t: String) = nextMealAt(LocalDateTime.parse("${d}T$t"))

    // ── 다음 끼니 판정 (경계값) ──────────────────────────────
    // 수요일 2026-09-02 하루를 시각만 바꿔 가며 훑는다.

    @Test
    fun `배식 시작 전이면 그 끼니가 다음 끼니`() {
        assertEquals(MealSlot(D, Meal.BREAKFAST), at(DS, "00:00"))
        assertEquals(MealSlot(D, Meal.BREAKFAST), at(DS, "07:29"))
    }

    @Test
    fun `배식 중이면 지금 그 끼니`() {
        assertEquals(MealSlot(D, Meal.BREAKFAST), at(DS, "07:30"))
        assertEquals(MealSlot(D, Meal.BREAKFAST), at(DS, "08:59"))
        assertEquals(MealSlot(D, Meal.LUNCH), at(DS, "11:40"))
        assertEquals(MealSlot(D, Meal.DINNER), at(DS, "18:00"))
    }

    /** 끝 시각에 **닿는 순간** 넘어간다 — 09:00은 조식이 끝난 시각이다. */
    @Test
    fun `끼니 끝 시각이면 다음 끼니로 넘어간다`() {
        assertEquals(MealSlot(D, Meal.LUNCH), at(DS, "09:00"))
        assertEquals(MealSlot(D, Meal.LUNCH), at(DS, "09:01"))
        assertEquals(MealSlot(D, Meal.DINNER), at(DS, "13:00"))
        assertEquals(MealSlot(D, Meal.DINNER), at(DS, "13:01"))
    }

    @Test
    fun `석식이 지나면 내일 조식`() {
        assertEquals(MealSlot(D.plusDays(1), Meal.BREAKFAST), at(DS, "19:00"))
        assertEquals(MealSlot(D.plusDays(1), Meal.BREAKFAST), at(DS, "19:01"))
        assertEquals(MealSlot(D.plusDays(1), Meal.BREAKFAST), at(DS, "23:59"))
    }

    /** 일요일 밤 → **다음 주 월요일** 조식. 주 경계를 따로 다루지 않아도 날짜가 넘어간다. */
    @Test
    fun `일요일 밤이면 다음 주 월요일 조식`() {
        val sun = LocalDate.parse("2026-09-06")   // 일
        val mon = LocalDate.parse("2026-09-07")   // 다음 주 월
        assertEquals(MealSlot(mon, Meal.BREAKFAST), at("2026-09-06", "23:00"))
        assertEquals(java.time.DayOfWeek.SUNDAY, sun.dayOfWeek)
        assertEquals(java.time.DayOfWeek.MONDAY, mon.dayOfWeek)
    }

    // ── 아이콘 매핑 ─────────────────────────────────────────
    // **구체적인 것이 먼저 걸려야 한다.** 실제 2026-08-31 주 식단표에서 뽑았다.

    @Test
    fun `국물은 재료보다 먼저 걸린다`() {
        assertEquals(MenuIcon.SOUP, menuIcon("두부김치국"))     // 김치가 아니라 국
        assertEquals(MenuIcon.SOUP, menuIcon("달걀실파장국"))   // 달걀(EGG)이 아니라 장국
        assertEquals(MenuIcon.SOUP, menuIcon("꼬치어묵국"))     // 어묵(FISH)이 아니라 국
        assertEquals(MenuIcon.SOUP, menuIcon("순두부찌게"))     // `찌게`는 원본 표의 흔한 오기
        assertEquals(MenuIcon.SOUP, menuIcon("동태얼큰매운탕"))
        assertEquals(MenuIcon.SOUP, menuIcon("차돌된장찌게"))   // 차돌(BEEF)이 아니라 찌개
        assertEquals(MenuIcon.SALAD, menuIcon("콩나물무침"))    // 나물무침 — `국`이 없다
    }

    /** `탕수육`은 국이 아니다 — `탕`보다 `탕수`가 위에 있어야 한다. */
    @Test
    fun `탕수는 국물보다 먼저 걸린다`() {
        assertEquals(MenuIcon.UTENSILS, menuIcon("꿔바로우찹쌀탕수육"))
        assertEquals(MenuIcon.UTENSILS, menuIcon("고구마튀김"))
        // 단 `치킨까스`는 닭이 먼저다 — 재료(닭)가 조리법(까스)보다 알려 주는 게 많다
        assertEquals(MenuIcon.DRUMSTICK, menuIcon("치킨까스"))
    }

    @Test
    fun `종류별로 제 아이콘이 붙는다`() {
        assertEquals(MenuIcon.FISH, menuIcon("고등어무조림"))
        assertEquals(MenuIcon.FISH, menuIcon("새우까스*양파D"))
        assertEquals(MenuIcon.FISH, menuIcon("오징어돈육불고기"))    // 오징어가 돈육보다 먼저
        assertEquals(MenuIcon.BEEF, menuIcon("청양풍파채불고기"))
        assertEquals(MenuIcon.BEEF, menuIcon("동그랑땡전*케찹"))
        assertEquals(MenuIcon.DRUMSTICK, menuIcon("닭간장조림"))
        assertEquals(MenuIcon.EGG, menuIcon("계란찜"))
        assertEquals(MenuIcon.SALAD, menuIcon("황도그린샐러드"))   // 황도(APPLE)보다 샐러드
        assertEquals(MenuIcon.SALAD, menuIcon("배추겉절이"))
        assertEquals(MenuIcon.SALAD, menuIcon("무도라지생채"))
        assertEquals(MenuIcon.SANDWICH, menuIcon("샌드위치"))
        assertEquals(MenuIcon.MILK, menuIcon("두유"))
        assertEquals(MenuIcon.WHEAT, menuIcon("잡곡밥"))
        assertEquals(MenuIcon.WHEAT, menuIcon("누룽지"))
        assertEquals(MenuIcon.WHEAT, menuIcon("중화풍잡채덮밥"))
    }

    /** 못 맞히면 **없이** 둔다 — 화면에선 작은 점이다. 김치·장아찌엔 규칙이 없다. */
    @Test
    fun `모르면 아이콘이 없다`() {
        assertNull(menuIcon("포기김치"))
        assertNull(menuIcon("깍두기"))
        assertNull(menuIcon("양파장아짜"))
        assertNull(menuIcon("스팸야채볶음"))
        assertNull(menuIcon(""))
    }

    // ── 메인 요리 고르기 ────────────────────────────────────

    @Test
    fun `메인 요리는 밥과 국 다음 첫 항목`() {
        val items = listOf("잡곡밥", "햄김치국", "가자미구이", "닭가슴살샐러드", "브로컬리숙회", "깍두기")
        assertEquals("가자미구이", mainDish(items))
    }

    @Test
    fun `밥과 국뿐이면 첫 줄이 메인`() {
        assertEquals("잡곡밥", mainDish(listOf("잡곡밥", "미역국")))
        assertNull(mainDish(emptyList()))
    }

    /**
     * v1.6.83. **덮밥은 밥이 아니라 메인이다.** 실파일 금요일 중식이 `중화풍잡채덮밥`으로 시작하는데
     * 이걸 밥으로 보고 넘기면 곁들이 `꿔바로우찹쌀탕수육`이 메인 자리에 올라온다.
     */
    @Test
    fun `한 그릇 밥은 밥이라도 메인이다`() {
        val 금요일중식 = listOf(
            "중화풍잡채덮밥", "달걀실파장국", "꿔바로우찹쌀탕수육", "짜사이채무침", "깍둑단무지", "포기김치",
        )
        assertEquals("중화풍잡채덮밥", mainDish(금요일중식))
        assertEquals("김치볶음밥", mainDish(listOf("김치볶음밥", "미소장국", "단무지")))
        assertEquals("비빔밥", mainDish(listOf("비빔밥", "콩나물국")))
        // 그냥 밥은 여전히 건너뛴다 — 이걸 메인으로 올리면 매일 "잡곡밥"이 크게 뜬다
        assertEquals("제육볶음", mainDish(listOf("잡곡밥", "제육볶음", "김치")))
    }

    // ── 국 고르기 (v1.6.83) ─────────────────────────────────
    // 강조는 **국 1 + 메인 1** 두 줄뿐이라, 국을 잘못 집으면 나머지가 통째로 회색 줄로 밀린다.

    @Test
    fun `국은 국물 아이콘이 붙는 첫 줄`() {
        val 월요일중식 = listOf(
            "잡곡밥", "햄김치국", "가자미구이", "닭가슴살샐러드", "브로컬리숙회", "깍두기",
        )
        assertEquals("햄김치국", soupDish(월요일중식))
        assertEquals("가자미구이", mainDish(월요일중식))
        // 재료 이름이 섞여 있어도 국이 이긴다(menuIcon 규칙 한 벌만 쓴다)
        assertEquals("두부김치국", soupDish(listOf("잡곡밥", "두부김치국", "고기완자전")))
        assertEquals("차돌된장찌게", soupDish(listOf("잡곡밥", "차돌된장찌게", "새우까스*양파D")))
        assertEquals("동태얼큰매운탕", soupDish(listOf("잡곡밥", "동태얼큰매운탕", "청경채소고기불고기")))
    }

    /** 국이 **없는 칸이 실제로 있다** — 실파일 월요일 조식. 없으면 null 이고 메인만 강조된다. */
    @Test
    fun `국이 없는 칸도 있다`() {
        val 월요일조식 = listOf("샌드위치", "두유", "누룽지", "포기김치")
        assertNull(soupDish(월요일조식))
        assertEquals("샌드위치", mainDish(월요일조식))
        // `탕수육`은 국이 아니다 — 여기서 걸리면 국 자리에 튀김이 앉는다
        assertNull(soupDish(listOf("꿔바로우찹쌀탕수육", "짜사이채무침")))
        assertNull(soupDish(emptyList()))
    }

    private companion object {
        const val DS = "2026-09-02"                      // 수요일
        val D: LocalDate = LocalDate.parse(DS)
    }
}
