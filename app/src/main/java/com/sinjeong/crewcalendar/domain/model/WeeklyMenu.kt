package com.sinjeong.crewcalendar.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * 구내식당 주간식단표 (v1.6.80).
 *
 * 원본은 사업소 구내식당이 매주 벽에 붙이는 **7일 × 3끼니 = 21칸** 표다.
 * 관리자가 사진·PDF를 올리면 글자인식(MenuOcr)이 21칸을 채우고, 관리자가 손으로 고쳐
 * 저장하면 그때 비로소 전 직원에게 보인다. **사진 원본은 저장하지 않는다** — 텍스트만 올린다
 * (원본 표에 "불법유출 시 처벌" 문구가 있고, 저장소 비용도 들 이유가 없다).
 *
 * ## 왜 "이번 주 한 장"이 아니라 **주 단위로 여러 장**인가 (사용자 확정)
 *
 * > *"주간 식단표가 일요일쯤 나오니까.. 2개 식단표가 필요하긴 한데.."*
 *
 * 식단표 기간은 **월~일**인데 다음 주 것이 **일요일쯤** 나온다. 한 장만 들고 있으면
 * 관리자가 일요일에 다음 주 표를 올리는 순간 **그날(일요일) 점심이 화면에서 사라진다.**
 * 그래서 Firestore 문서 ID 를 **주 시작일(월요일)** 로 잡아 여러 주를 나란히 둔다
 * (`menus/2026-08-24`, `menus/2026-08-31`). 새 주를 올려도 지난 주 문서는 건드리지 않는다.
 */
enum class Meal(val label: String, val time: String, val emoji: String) {
    BREAKFAST("조식", "07:30~09:00", "☀️"),
    LUNCH("중식", "11:40~13:00", "🍱"),
    DINNER("석식", "17:30~19:00", "🌙"),
}

/**
 * 한 주치 식단.
 *
 * @param weekStart **월요일**. 이게 곧 Firestore 문서 ID(yyyy-MM-dd)다.
 * @param cells 21칸. `index = 요일(0=월 … 6=일) * 3 + Meal.ordinal`.
 *   한 칸 안의 메뉴 여러 줄은 `\n` 으로 잇는다(빈 칸은 빈 문자열).
 */
data class WeeklyMenu(
    val weekStart: LocalDate,
    val cells: List<String>,
) {
    val weekEnd: LocalDate get() = weekStart.plusDays(6)

    fun cell(day: Int, meal: Meal): String = cells.getOrElse(day * MEALS + meal.ordinal) { "" }

    /** 한 칸의 메뉴 줄 목록 — 빈 줄·앞뒤 공백은 버린다. */
    fun items(day: Int, meal: Meal): List<String> =
        cell(day, meal).split('\n').map { it.trim() }.filter { it.isNotEmpty() }

    fun withCell(day: Int, meal: Meal, text: String): WeeklyMenu =
        copy(cells = cells.toMutableList().also { it[day * MEALS + meal.ordinal] = text })

    /** 한 칸이라도 글자가 있으면 "표가 있다"로 본다 */
    val isBlank: Boolean get() = cells.none { it.isNotBlank() }

    /** 채워진 칸 수 — 인식 정확도 보고와 편집 화면 안내에 쓴다 */
    val filledCells: Int get() = cells.count { it.isNotBlank() }

    companion object {
        const val DAYS = 7
        const val MEALS = 3
        const val CELLS = DAYS * MEALS

        fun empty(weekStart: LocalDate) = WeeklyMenu(weekStart, List(CELLS) { "" })

        /** 요일 머리글 — 표와 같은 월요일 시작 */
        val DAY_LABELS = listOf("월", "화", "수", "목", "금", "토", "일")
    }
}

/**
 * 그 날짜가 속한 주의 **월요일**.
 *
 * ⚠ 일요일은 **그 주의 마지막 날**이지 다음 주 첫날이 아니다 — `WeekFields` 기본값(로케일에 따라
 * 일요일 시작)에 기대면 한국 식단표(월~일)와 하루씩 어긋난다. 그래서 로케일을 안 타는
 * [TemporalAdjusters.previousOrSame] 로 못 박는다.
 */
fun weekStartOf(date: LocalDate): LocalDate =
    date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/**
 * 메뉴 이름 → 이모지. **못 맞히면 null** (사용자 확정: 엉뚱한 이모지가 붙는 것보다 없는 게 낫다).
 *
 * 위에서부터 처음 걸리는 것을 쓰므로 **순서가 곧 우선순위**다.
 * `김치`가 `김`보다, `국수`가 `국`보다 위에 있어야 한다.
 */
fun menuEmoji(name: String): String? {
    val s = name.replace(" ", "")
    return EMOJI_RULES.firstOrNull { (k, _) -> s.contains(k) }?.second
}

private val EMOJI_RULES: List<Pair<String, String>> = listOf(
    // ── 구체적인 것부터 ─────────────────────────────
    "김치" to "🥬", "깍두기" to "🥬", "겉절이" to "🥬", "장아찌" to "🥬",
    "김밥" to "🍙", "주먹밥" to "🍙", "김자반" to "🍙", "김구이" to "🍙",
    "국수" to "🍜", "라면" to "🍜", "우동" to "🍜", "짜장" to "🍜", "칼국수" to "🍜",
    "쫄면" to "🍜", "파스타" to "🍝", "스파게티" to "🍝",
    "탕수" to "🍖", "돈까스" to "🍖", "까스" to "🍖", "가스" to "🍖",
    "튀김" to "🍤", "강정" to "🍗", "치킨" to "🍗", "닭" to "🍗",
    "카레" to "🍛", "덮밥" to "🍛", "비빔밥" to "🍚",
    // 국물류 — `계란국`은 계란보다 국이 먼저 걸려야 국물로 읽힌다
    "찌개" to "🍲", "전골" to "🍲", "국밥" to "🍲", "육개장" to "🍲",
    "국" to "🍲", "탕" to "🍲",
    "죽" to "🥣", "스프" to "🥣", "수프" to "🥣",
    "만두" to "🥟", "어묵" to "🍢", "오뎅" to "🍢",
    "계란" to "🥚", "달걀" to "🥚", "메추리알" to "🥚",
    "두부" to "🍥",
    "고등어" to "🐟", "갈치" to "🐟", "삼치" to "🐟", "조기" to "🐟", "생선" to "🐟",
    "꽁치" to "🐟", "가자미" to "🐟", "코다리" to "🐟", "오징어" to "🦑", "새우" to "🦐",
    "제육" to "🍖", "불고기" to "🍖", "삼겹" to "🍖", "돼지" to "🍖", "소고기" to "🍖",
    "구이" to "🍖", "갈비" to "🍖", "떡갈비" to "🍖", "육전" to "🍖",
    "소시지" to "🌭", "비엔나" to "🌭", "햄" to "🌭", "베이컨" to "🥓",
    "샐러드" to "🥗", "야채" to "🥗", "채소" to "🥗", "쌈" to "🥬",
    "나물" to "🥬", "무침" to "🥬", "숙채" to "🥬",
    "감자" to "🥔", "고구마" to "🍠", "옥수수" to "🌽", "오이" to "🥒", "버섯" to "🍄",
    "떡볶이" to "🍢", "떡" to "🍡",
    "빵" to "🍞", "토스트" to "🍞", "샌드위치" to "🥪", "시리얼" to "🥣",
    "우유" to "🥛", "요구르트" to "🥛", "요거트" to "🥛", "두유" to "🥛",
    "주스" to "🧃", "음료" to "🧃", "식혜" to "🧃", "커피" to "☕",
    "사과" to "🍎", "바나나" to "🍌", "수박" to "🍉", "참외" to "🍈", "포도" to "🍇",
    "귤" to "🍊", "오렌지" to "🍊", "딸기" to "🍓", "토마토" to "🍅",
    "과일" to "🍎",
    // ── 마지막 그물 ────────────────────────────────
    "밥" to "🍚",
    // ⚠ 한 글자짜리 `김`·`배`·`차`는 **일부러 뺐다** — `김치`·`배추`·`차돌`처럼 뜻이 다른 말에
    //   먼저 걸려 엉뚱한 이모지를 붙인다. 사용자 확정: 틀린 이모지보다 없는 게 낫다.
)
