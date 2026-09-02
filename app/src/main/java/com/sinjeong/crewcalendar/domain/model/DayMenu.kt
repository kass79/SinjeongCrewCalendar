package com.sinjeong.crewcalendar.domain.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 식단표 **하루씩 보기**(v1.6.82)의 순수 계산 — 다음 끼니 판정 · 메뉴 아이콘 매핑.
 *
 * 안드로이드를 하나도 안 부른다(리소스 id도 모른다). 그래서 JUnit 만으로 돌아간다
 * — 예전 `menuEmoji` 가 그랬던 것과 같은 이유다. 화면 쪽에서 [MenuIcon] → 드로어블로 옮긴다.
 */

/** 끼니 시각은 [Meal.time] (`"07:30~09:00"`) 하나가 원본이다 — 여기서 다시 적지 않는다. */
val Meal.startTime: LocalTime get() = LocalTime.parse(time.substringBefore('~'))
val Meal.endTime: LocalTime get() = LocalTime.parse(time.substringAfter('~'))

/** 어느 날 어느 끼니 한 칸. */
data class MealSlot(val date: LocalDate, val meal: Meal)

/**
 * 지금 시각 기준 **다음 끼니**.
 *
 * 끝 시각이 아직 안 지난 첫 끼니다 — 배식 중이면 그 끼니가 "다음"이고(11:50 → 중식),
 * 끝 시각에 닿는 순간 넘어간다(09:00 → 중식). 석식까지 끝나면 **내일 조식**이고,
 * 일요일 밤이면 그대로 다음 주 월요일 조식이 된다(주 경계를 따로 다루지 않는다).
 */
fun nextMealAt(now: LocalDateTime): MealSlot {
    val t = now.toLocalTime()
    val meal = Meal.entries.firstOrNull { t < it.endTime }
    return if (meal != null) MealSlot(now.toLocalDate(), meal)
    else MealSlot(now.toLocalDate().plusDays(1), Meal.BREAKFAST)
}

/**
 * 그 칸의 **메인 요리** — "국 다음 첫 항목"(사용자 표현). 밥·국을 건너뛴 첫 줄이고,
 * 다 건너뛰면(밥·국뿐인 칸) 첫 줄을 그대로 쓴다.
 */
fun mainDish(items: List<String>): String? =
    items.firstOrNull { menuIcon(it) != MenuIcon.WHEAT && menuIcon(it) != MenuIcon.SOUP }
        ?: items.firstOrNull()

/** 메뉴 아이콘 종류. 리소스 id가 아니라 **뜻**이다 — 화면 쪽에서 그림을 붙인다. */
enum class MenuIcon { SOUP, FISH, BEEF, DRUMSTICK, EGG, SALAD, SANDWICH, MILK, APPLE, WHEAT, UTENSILS }

/**
 * 메뉴 이름 → 아이콘. **못 맞히면 null** — 그 자리는 화면에서 작은 점이 된다
 * (사용자 확정: 엉뚱한 그림이 붙는 것보다 없는 게 낫다. v1.6.80 이모지 때와 같은 판단).
 *
 * 위에서부터 **처음 걸리는 것**을 쓰므로 **순서가 곧 우선순위**다. 함정 둘:
 *  - `두부김치국`·`달걀실파장국`·`꼬치어묵국`은 재료가 아니라 **국**이다 → 국물 규칙을 맨 위에.
 *  - 그런데 `탕수육`은 국이 아니다 → `탕수`만 국물보다 **더** 위로 올린다.
 * 한 글자 `김`·`배`·`차` 류는 v1.6.80 과 같은 이유로 넣지 않는다(`김치`·`배추`·`차돌`에 오발).
 */
fun menuIcon(name: String): MenuIcon? {
    val s = name.replace(" ", "")
    return ICON_RULES.firstOrNull { (k, _) -> s.contains(k) }?.second
}

private val ICON_RULES: List<Pair<String, MenuIcon>> = listOf(
    // ⚠ `탕`(국물)보다 먼저 — 탕수육·꿔바로우는 튀김이다
    "탕수" to MenuIcon.UTENSILS,
    // 국물 — 재료 이름이 섞여 있어도 국이 이긴다
    "찌개" to MenuIcon.SOUP, "찌게" to MenuIcon.SOUP,   // `찌게`는 원본 표의 흔한 오기
    "전골" to MenuIcon.SOUP, "장국" to MenuIcon.SOUP,
    "국" to MenuIcon.SOUP, "탕" to MenuIcon.SOUP,
    // 생선·해물
    "생선" to MenuIcon.FISH, "어묵" to MenuIcon.FISH, "오징어" to MenuIcon.FISH,
    "낙지" to MenuIcon.FISH, "삼치" to MenuIcon.FISH, "고등어" to MenuIcon.FISH,
    "꽁치" to MenuIcon.FISH, "가자미" to MenuIcon.FISH, "새우" to MenuIcon.FISH,
    // 고기
    "돈육" to MenuIcon.BEEF, "소고기" to MenuIcon.BEEF, "불고기" to MenuIcon.BEEF,
    "제육" to MenuIcon.BEEF, "삼겹" to MenuIcon.BEEF, "차돌" to MenuIcon.BEEF,
    "동그랑땡" to MenuIcon.BEEF,
    "닭" to MenuIcon.DRUMSTICK, "치킨" to MenuIcon.DRUMSTICK,
    "계란" to MenuIcon.EGG, "달걀" to MenuIcon.EGG, "메추리알" to MenuIcon.EGG,
    "샐러드" to MenuIcon.SALAD, "생채" to MenuIcon.SALAD, "나물" to MenuIcon.SALAD,
    "무침" to MenuIcon.SALAD, "겉절이" to MenuIcon.SALAD,
    "샌드위치" to MenuIcon.SANDWICH, "토스트" to MenuIcon.SANDWICH, "빵" to MenuIcon.SANDWICH,
    "두유" to MenuIcon.MILK, "요구르트" to MenuIcon.MILK, "요거트" to MenuIcon.MILK,
    "우유" to MenuIcon.MILK,
    "과일" to MenuIcon.APPLE, "사과" to MenuIcon.APPLE, "바나나" to MenuIcon.APPLE,
    "수박" to MenuIcon.APPLE, "참외" to MenuIcon.APPLE, "포도" to MenuIcon.APPLE,
    "오렌지" to MenuIcon.APPLE, "딸기" to MenuIcon.APPLE, "황도" to MenuIcon.APPLE,
    "귤" to MenuIcon.APPLE,
    "덮밥" to MenuIcon.WHEAT, "누룽지" to MenuIcon.WHEAT, "밥" to MenuIcon.WHEAT,
    "죽" to MenuIcon.WHEAT,
    "튀김" to MenuIcon.UTENSILS, "까스" to MenuIcon.UTENSILS,
)
