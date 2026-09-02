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
 * 식단표 **하루씩 보기**의 계산 근거를 고정한다.
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

    // ── 아이콘 매핑 (v1.6.84 재설계) ─────────────────────────
    // **뒤에 붙는 조리법이 재료를 이긴다.** 실제 2026-08-31 주 식단표에서 뽑았다.

    @Test
    fun `국물은 재료보다 먼저 걸린다`() {
        assertEquals(MenuIcon.SOUP, menuIcon("두부김치국"))     // 김치가 아니라 국
        assertEquals(MenuIcon.SOUP, menuIcon("달걀실파장국"))   // 달걀(EGG)이 아니라 장국
        assertEquals(MenuIcon.SOUP, menuIcon("꼬치어묵국"))     // 어묵(FISH)이 아니라 국
        assertEquals(MenuIcon.SOUP, menuIcon("순두부찌게"))     // 찌게는 원본 표의 흔한 오기
        assertEquals(MenuIcon.SOUP, menuIcon("동태얼큰매운탕"))
        assertEquals(MenuIcon.SOUP, menuIcon("차돌된장찌게"))   // 차돌(BEEF)이 아니라 찌개
        assertEquals(MenuIcon.SEASONED, menuIcon("콩나물무침")) // 무침 — 국이 없다
    }

    /** 탕수육은 국이 아니고, 잔치국수도 국이 아니다 — 탕·국보다 위에 있어야 한다. */
    @Test
    fun `탕수와 국수는 국물보다 먼저 걸린다`() {
        assertEquals(MenuIcon.FRIED, menuIcon("꿔바로우찹쌀탕수육"))
        assertEquals(MenuIcon.FRIED, menuIcon("고구마튀김"))
        assertEquals(MenuIcon.NOODLE, menuIcon("잔치국수"))
        assertEquals(MenuIcon.NOODLE, menuIcon("얼큰칼국수"))
        // 단 수제비국은 국이다 — 국물 규칙이 수제비보다 위다
        assertEquals(MenuIcon.SOUP, menuIcon("김치수제비국"))
    }

    /**
     * v1.6.84의 뼈대. **뒤에 붙는 조리법이 재료를 이긴다** — 종전엔 고등어무조림이 생선이었고
     * 순대깻잎순볶음이 고기였다. 조리법이 하나도 안 걸릴 때만 재료 아이콘으로 내려간다.
     */
    @Test
    fun `조리법이 재료를 이긴다`() {
        assertEquals(MenuIcon.BRAISE, menuIcon("고등어무조림"))
        assertEquals(MenuIcon.BRAISE, menuIcon("닭간장조림"))
        assertEquals(MenuIcon.BRAISE, menuIcon("새송이메란조림"))
        assertEquals(MenuIcon.STIRFRY, menuIcon("순대깻잎순볶음"))
        assertEquals(MenuIcon.STIRFRY, menuIcon("들기름김치볶음"))   // 김치가 아니라 볶음
        assertEquals(MenuIcon.STEAM, menuIcon("돈사태매콤떡찜"))
        assertEquals(MenuIcon.STEAM, menuIcon("계란찜"))
        assertEquals(MenuIcon.SEASONED, menuIcon("오이고추쌈장무침"))  // 쌈이 아니라 무침
        assertEquals(MenuIcon.GRILL, menuIcon("적어소금구이"))
        assertEquals(MenuIcon.GRILL, menuIcon("가자미구이"))
        assertEquals(MenuIcon.JEON, menuIcon("고기완자전"))
        assertEquals(MenuIcon.FRIED, menuIcon("치킨까스"))            // 닭이 아니라 까스
        assertEquals(MenuIcon.BEEF, menuIcon("오징어돈육불고기"))     // 불고기는 조리법이자 고기
        // 재료로 내려가는 경우 — 조리법이 하나도 안 걸린다
        assertEquals(MenuIcon.FISH, menuIcon("삼치"))
        assertEquals(MenuIcon.TOFU, menuIcon("연두부*양념장"))
    }

    /** `A*B` 는 **앞 토막이 주인공**. v1.6.83에선 통짜 contains 라 아래 셋이 전부 점이었다. */
    @Test
    fun `별표 앞쪽이 주인공`() {
        assertEquals(MenuIcon.BRAISE, menuIcon("우엉호두조림*포기김치"))  // 김치가 아니라 조림
        assertEquals(MenuIcon.WRAP, menuIcon("상추쌈*쌈장"))
        assertEquals(MenuIcon.JEON, menuIcon("동그랑땡전*케찹"))
        assertEquals(MenuIcon.FRIED, menuIcon("새우까스*양파D"))
        assertEquals(MenuIcon.WRAP, menuIcon("쌈무*부추생채"))
        // 앞이 하나도 안 걸리면 뒤를 본다
        assertEquals(MenuIcon.KIMCHI, menuIcon("???*포기김치"))
    }

    /** 한 그릇 밥은 **맨 위** — 잡채(면)·볶음에 먼저 걸리면 안 된다. */
    @Test
    fun `한 그릇 밥이 맨 먼저 걸린다`() {
        assertEquals(MenuIcon.RICE, menuIcon("중화풍잡채덮밥"))
        assertEquals(MenuIcon.RICE, menuIcon("김치볶음밥"))
        assertEquals(MenuIcon.RICE, menuIcon("잡곡밥"))
        assertEquals(MenuIcon.RICE, menuIcon("누룽지"))
        assertEquals(MenuIcon.NOODLE, menuIcon("잡채"))            // 덮밥이 아니면 면이다
    }

    @Test
    fun `김치 계열은 모두 김치 아이콘`() {
        assertEquals(MenuIcon.KIMCHI, menuIcon("포기김치"))
        assertEquals(MenuIcon.KIMCHI, menuIcon("깍두기"))
        assertEquals(MenuIcon.KIMCHI, menuIcon("열무김치"))
        assertEquals(MenuIcon.KIMCHI, menuIcon("양파장아짜"))
        assertEquals(MenuIcon.KIMCHI, menuIcon("깍둑단무지"))
        assertEquals(MenuIcon.KIMCHI, menuIcon("배추겉절이"))
        assertEquals(MenuIcon.SPROUT, menuIcon("무도라지생채"))
        assertEquals(MenuIcon.SPROUT, menuIcon("브로컬리숙회"))
        assertEquals(MenuIcon.SALAD, menuIcon("황도그린샐러드"))   // 황도(APPLE)보다 샐러드
        assertEquals(MenuIcon.SANDWICH, menuIcon("샌드위치"))
        assertEquals(MenuIcon.MILK, menuIcon("두유"))
    }

    /**
     * ⚠ **이 테스트가 A1의 잠금이다** — 실파일 21칸 124항목에서 폴백 점이 **0개**여야 한다.
     * 사용자 지적: *"아이콘 없는 항목 0개로. 없으면 니가 만들어라."*
     * 여기서 깨지면 새 메뉴에 규칙이 모자란 것이니 `DayMenu` 의 `ICON_RULES` 에 줄을 더한다.
     */
    @Test
    fun `실파일 21칸에 점이 하나도 없다`() {
        val 점 = REAL_WEEK.flatMap { it.second }.filter { menuIcon(it) == null }
        assertEquals("아이콘 매핑 누락: " + 점, emptyList<String>(), 점)
        assertEquals(21, REAL_WEEK.size)
        assertEquals(124, REAL_WEEK.sumOf { it.second.size })
    }

    /** 빈 문자열만은 여전히 점이다(칸이 비면 자리를 지켜야 줄 정렬이 안 흐트러진다). */
    @Test
    fun `빈 이름은 아이콘이 없다`() {
        assertNull(menuIcon(""))
    }

    // ── 국 + 핵심 반찬 (v1.6.84) ────────────────────────────
    // 강조는 **국 1 + 핵심 반찬 1** 두 줄뿐이라, 잘못 집으면 티가 크다.

    /**
     * ⚠ **이 테스트가 A2의 잠금이다** — 실파일 21칸 전부의 (국, 핵심 반찬) 기대값.
     * v1.6.83은 자리로 골라 수요일 조식에서 버섯 반찬 새송이메란조림을 집었다
     * (사용자 지적: 그 칸의 주요리는 고기산적조림이다).
     */
    @Test
    fun `실파일 21칸의 국과 핵심 반찬`() {
        assertEquals(21, REAL_WEEK.size)
        REAL_WEEK.forEach { (expect, items) ->
            val (칸, 국, 핵심) = expect.split("|")
            assertEquals(칸 + " 국", if (국.isEmpty()) "—" else 국, soupDish(items) ?: "—")
            assertEquals(칸 + " 핵심", 핵심, mainDish(items))
        }
    }

    /** 한 그릇 밥은 조리법 주요리와 **같은 층**이고, 동점이면 위쪽 줄이 이긴다. */
    @Test
    fun `한 그릇 밥은 밥이라도 핵심이다`() {
        assertEquals("김치볶음밥", mainDish(listOf("김치볶음밥", "미소장국", "단무지")))
        assertEquals("비빔밥", mainDish(listOf("비빔밥", "콩나물국")))
        // 그냥 밥은 여전히 건너뛴다 — 이걸 올리면 매일 잡곡밥이 칠해진다
        assertEquals("제육볶음", mainDish(listOf("잡곡밥", "제육볶음", "김치")))
    }

    @Test
    fun `밥과 국뿐이면 첫 줄이 핵심`() {
        assertEquals("잡곡밥", mainDish(listOf("잡곡밥", "미역국")))
        assertNull(mainDish(emptyList()))
    }

    @Test
    fun `국은 국물 아이콘이 붙는 첫 줄`() {
        assertEquals("두부김치국", soupDish(listOf("잡곡밥", "두부김치국", "고기완자전")))
        assertEquals("차돌된장찌게", soupDish(listOf("잡곡밥", "차돌된장찌게", "새우까스*양파D")))
        assertEquals("동태얼큰매운탕", soupDish(listOf("잡곡밥", "동태얼큰매운탕", "청경채소고기불고기")))
    }

    /** 국이 **없는 칸이 실제로 있다** — 실파일 월요일 조식. 없으면 null 이고 핵심만 칠해진다. */
    @Test
    fun `국이 없는 칸도 있다`() {
        val 월요일조식 = listOf("샌드위치", "두유", "누룽지", "포기김치")
        assertNull(soupDish(월요일조식))
        assertEquals("샌드위치", mainDish(월요일조식))
        // 탕수육은 국이 아니다 — 여기서 걸리면 국 자리에 튀김이 앉는다
        assertNull(soupDish(listOf("꿔바로우찹쌀탕수육", "짜사이채무침")))
        assertNull(soupDish(emptyList()))
    }

    private companion object {
        const val DS = "2026-09-02"                      // 수요일
        val D: LocalDate = LocalDate.parse(DS)

        /**
         * 실파일 `식단표_0831.pdf` **21칸 124항목** — PDF 괘선으로 행/열을 잘라 뽑은 원문 그대로다
         * (2026.8.31 ~ 2026.9.6 주). `칸|국|핵심 반찬` 은 v1.6.84 규칙의 기대값이다.
         */
        val REAL_WEEK: List<Pair<String, List<String>>> = listOf(
            "월 조식||샌드위치" to listOf("샌드위치", "두유", "누룽지", "포기김치"),
            "화 조식|북어해장국|계란찜" to listOf("잡곡밥", "북어해장국", "계란찜", "느타리버섯야채볶음", "치커리토마토샐러드", "포기김치"),
            "수 조식|순두부찌게|고기산적조림" to listOf("잡곡밥", "순두부찌게", "새송이메란조림", "고기산적조림", "가지나물", "포기김치"),
            "목 조식|소고기미역국|적어소금구이" to listOf("잡곡밥", "소고기미역국", "적어소금구이", "오이생채", "열무나물", "포기김치"),
            "금 조식|두부김치국|고기완자전" to listOf("잡곡밥", "두부김치국", "고기완자전", "멸치볶음", "숙주나물", "깍두기"),
            "토 조식|냉이된장국|삼치무조림" to listOf("잡곡밥", "냉이된장국", "삼치무조림", "비엔나감자조림", "연두부*양념장", "포기김치"),
            "일 조식|우거지된장국|돈육낙지불고기" to listOf("잡곡밥", "우거지된장국", "돈육낙지불고기", "청경채나물", "마늘쫑볶음", "포기김치"),
            "월 중식|햄김치국|가자미구이" to listOf("잡곡밥", "햄김치국", "가자미구이", "닭가슴살샐러드", "브로컬리숙회", "깍두기"),
            "화 중식|꼬치어묵국|순대깻잎순볶음" to listOf("잡곡밥", "꼬치어묵국", "순대깻잎순볶음", "두부구이*양념장", "부추생채", "들기름김치볶음"),
            "수 중식|콩나물맑은국|오징어돈육불고기" to listOf("잡곡밥", "콩나물맑은국", "오징어돈육불고기", "상추쌈*쌈장", "미역오이초무침", "포기김치"),
            "목 중식|시래기들깨된장국|해몰모듬굴소스볶음" to listOf("잡곡밥", "시래기들깨된장국", "해몰모듬굴소스볶음", "사과적채샐러드", "오이고추쌈장무침", "우엉호두조림*포기김치"),
            "금 중식|달걀실파장국|중화풍잡채덮밥" to listOf("중화풍잡채덮밥", "달걀실파장국", "꿔바로우찹쌀탕수육", "짜사이채무침", "깍둑단무지", "포기김치"),
            "토 중식|느타리들깨무채국|쭈삼매콤불고기" to listOf("잡곡밥", "느타리들깨무채국", "쭈삼매콤불고기", "가지굴소스볶음", "배추겉절이", "깍두기"),
            "일 중식|차돌된장찌게|새우까스*양파D" to listOf("잡곡밥", "차돌된장찌게", "새우까스*양파D", "무말랭이고춧잎무침", "오이깍둑무침", "포기김치"),
            "월 석식|얼갈이된장국|고등어무조림" to listOf("잡곡밥", "얼갈이된장국", "고등어무조림", "동그랑땡전*케찹", "참나물오이생채", "포기김치"),
            "화 석식|감자호박된장국|닭간장조림" to listOf("잡곡밥", "감자호박된장국", "닭간장조림", "미나리무생채", "콩나물무침", "포기김치"),
            "수 석식|콩지비김치국|돈사태매콤떡찜" to listOf("잡곡밥", "콩지비김치국", "돈사태매콤떡찜", "황도그린샐러드", "미역멸치볶음", "깍두기"),
            "목 석식|배추콩가루국|청양풍파채불고기" to listOf("잡곡밥", "배추콩가루국", "청양풍파채불고기", "쌈무*부추생채", "양파장아짜", "열무김치"),
            "금 석식|김치수제비국|치킨까스" to listOf("잡곡밥", "김치수제비국", "치킨까스", "오이생채", "청경채두부무침", "포기김치"),
            "토 석식|동태얼큰매운탕|청경채소고기불고기" to listOf("잡곡밥", "동태얼큰매운탕", "청경채소고기불고기", "열무나물", "어묵야채볶음", "포기김치"),
            "일 석식|감자다시마국|꽁치캔김치조림" to listOf("잡곡밥", "감자다시마국", "꽁치캔김치조림", "스팸야채볶음", "무도라지생채", "깍두기"),
        )
    }
}
