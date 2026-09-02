package com.sinjeong.crewcalendar.domain.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 식단표 **하루씩 보기**의 순수 계산 — 다음 끼니 판정 · 메뉴 아이콘 매핑 · 국/핵심 반찬 판정.
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
 * 그 칸의 **국** — 국·찌개·탕 첫 줄. 없으면 null(국 없는 칸이 실제로 있다 — 실파일 월요일 조식은
 * `샌드위치·두유·누룽지·포기김치`다).
 *
 * 판정을 [menuIcon] 에 맡기는 것이 요점이다. `두부김치국`·`달걀실파장국`·`꼬치어묵국`이 국이고
 * `탕수육`이 국이 아니라는 규칙은 이미 거기 한 벌만 있다 — 두 벌이 되면 한쪽만 고쳐 어긋난다.
 */
fun soupDish(items: List<String>): String? = items.firstOrNull { menuIcon(it) == MenuIcon.SOUP }

/**
 * 그 칸의 **핵심 반찬** — 형광펜이 칠해지는 두 줄 중 국이 아닌 쪽(v1.6.84).
 *
 * ## 왜 "국 다음 첫 줄"을 버렸나
 *
 * v1.6.82~83은 **자리**로 골랐다(밥·국을 건너뛴 첫 줄). 그런데 실파일 수요일 조식이
 * `잡곡밥 · 순두부찌게 · 새송이메란조림 · 고기산적조림 · 가지나물 · 포기김치` 인데
 * 자리로 고르면 버섯 반찬 `새송이메란조림` 이 뽑힌다 — 그 칸의 주요리는 `고기산적조림` 이다
 * (사용자 지적: *"핵심 2가지는 국+핵심 반찬"*). 표는 주요리를 몇째 줄에 적을지 정해 두지 않는다.
 *
 * ## 점수 규칙 (사용자 확정 순서)
 *
 * | | 점수 | 예 |
 * |---|---|---|
 * | 단백질 주요리([PROTEIN]) | **+3** | 고기·돈육·소고기·닭·오리·생선명·오징어·낙지·새우·계란 |
 * | 조리법 주요리([MAIN_METHOD]) | **+2** | 불고기·구이·까스·튀김·전·찜·조림·볶음 |
 * | 한 그릇 밥([ONE_BOWL]) | **+2** | 덮밥·비빔밥·볶음밥 |
 * | 곁들이([SIDEISH])**뿐**일 때 | **−2** | 채소·버섯·나물·무침·샐러드·김치 |
 *
 * 밥([MenuIcon.RICE])과 국([MenuIcon.SOUP])은 애초에 후보가 아니다(국은 옆 줄이 이미 칠한다).
 * **동점이면 위쪽 줄**이 이긴다.
 *
 * ⚠ **한 그릇 밥이 조리법 주요리와 같은 층인 것이 핵심이다.** 실파일 금요일 중식이
 * `중화풍잡채덮밥 · 달걀실파장국 · 꿔바로우찹쌀탕수육 …` 인데, 덮밥을 아래 층에 두면 곁들이
 * `꿔바로우찹쌀탕수육` 이 올라온다(v1.6.83이 `ONE_BOWL` 예외로 막아 둔 바로 그 칸이다).
 * 같은 층 + 위쪽 줄 우선이면 **덮밥이 이긴다** — 예외 없이 규칙 하나로 같은 답이 나온다.
 *
 * ⚠ 감점은 **긍정 신호가 하나도 없을 때만** 준다. `청경채소고기불고기` 처럼 주요리 이름에
 * 채소가 섞이는 일이 흔한데, 그때마다 깎으면 진짜 주요리가 곁들이에게 진다.
 *
 * 실파일 21칸 전건의 기대값은 `MenuDayTest` 가 박아 두었다.
 */
fun mainDish(items: List<String>): String? =
    // maxByOrNull 은 최대가 여럿이면 **첫 번째**를 준다 = "동점이면 위쪽 줄"
    items.filter { dishScore(it) != null }.maxByOrNull { dishScore(it)!! }
        ?: items.firstOrNull()

/** 핵심 반찬 후보 점수. 밥·국은 후보가 아니라 null. */
internal fun dishScore(name: String): Int? {
    val s = name.replace(" ", "").substringBefore('*')
    val bowl = ONE_BOWL.any { it in s }
    val icon = menuIcon(name)
    if (!bowl && (icon == MenuIcon.RICE || icon == MenuIcon.SOUP)) return null
    var v = 0
    if (PROTEIN.any { it in s }) v += 3
    if (MAIN_METHOD.any { it in s }) v += 2
    if (bowl) v += 2
    // 긍정 신호가 하나도 없을 때만 감점 — 주요리 이름에 섞인 채소로 깎지 않는다
    if (v == 0 && SIDEISH.any { it in s }) v -= 2
    return v
}

/** 한 그릇으로 끼니가 되는 밥 — 이름에 `밥` 이 있어도 주요리다. */
private val ONE_BOWL = listOf("덮밥", "비빔밥", "볶음밥")

/**
 * 단백질 주요리 신호. `멸치`·`어묵` 은 **일부러 뺐다** — `미역멸치볶음`·`어묵야채볶음` 은
 * 밑반찬이지 그 칸의 주요리가 아니다(실파일 수요일 석식·토요일 석식에서 실제로 겨룬다).
 */
private val PROTEIN = listOf(
    "고기", "돈육", "돈", "돼지", "소고기", "차돌", "사태", "제육", "삼겹",
    "닭", "오리", "오징어", "낙지", "쭈꾸미", "쭈삼", "새우", "전복",
    "계란", "달걀", "생선", "고등어", "삼치", "꽁치", "가자미", "동태", "임연수", "순대",
)

/** 조리법 주요리 신호 — "불에 올려 접시로 나오는 것". */
private val MAIN_METHOD = listOf(
    "불고기", "구이", "까스", "가스", "튀김", "전", "찜", "조림", "볶음", "탕수", "강정",
)

/** 곁들이 신호 — 위 둘이 하나도 안 걸릴 때만 −2. */
private val SIDEISH = listOf(
    "나물", "무침", "생채", "샐러드", "김치", "깍두기", "겉절이", "장아찌", "장아짜", "단무지",
    "숙회", "쌈", "버섯", "채소", "야채", "미역", "다시마", "도라지", "우엉", "연근",
)

/**
 * 메뉴 아이콘 종류. 리소스 id가 아니라 **뜻**이다 — 화면 쪽에서 그림을 붙인다.
 *
 * v1.6.84에서 **조리법 아이콘 9종**(김치·조림·볶음·찜·쌈·무침·전·나물·구이)과 밥·면·두부를
 * 더했다. 종전엔 이것들이 전부 폴백 점(`dot`)이라 실파일 21칸에서 여섯 줄이 점이었다
 * (사용자: *"없으면 니가 만들어라"*). Lucide 에 없는 한식 범주는 Lucide 규격(24×24 · stroke 2 ·
 * round cap/join · 단색)에 맞춰 직접 그렸다 — `res/drawable/ic_menu_*.xml`.
 */
enum class MenuIcon {
    SOUP, NOODLE, RICE,
    FISH, BEEF, DRUMSTICK, EGG, TOFU,
    SALAD, SPROUT, KIMCHI, WRAP, SEASONED,
    BRAISE, STIRFRY, STEAM, GRILL, JEON, FRIED,
    SANDWICH, MILK, APPLE,
}

/**
 * 메뉴 이름 → 아이콘. 위에서부터 **처음 걸리는 것**을 쓰므로 **순서가 곧 우선순위**다.
 *
 * ## 규칙의 뼈대 — **뒤에 붙는 조리법이 결정한다**
 *
 * `…조림`→조림 · `…볶음`→볶음 · `…무침`→무침 · `…찜`→찜 · `…전`→전 · `…구이`→구이 ·
 * `…김치/깍두기/석박지/겉절이/장아찌`→김치 · `…나물/생채/숙회`→나물 · `…쌈`→쌈.
 * **재료 아이콘(생선·고기·계란…)은 조리법이 하나도 안 걸렸을 때만** 쓴다 — 그래서
 * `고등어무조림` 은 생선이 아니라 조림이고, `순대깻잎순볶음` 은 고기가 아니라 볶음이다.
 *
 * ## 순서에 걸린 함정 (전부 실파일에서 실제로 물린 것)
 *
 *  1. 한 그릇 밥(`덮밥`·`비빔밥`·`볶음밥`)이 **맨 위** — `중화풍잡채덮밥` 이 `잡채`(면)에,
 *     `김치볶음밥` 이 `볶음` 에 먼저 걸리는 걸 막는다. 셋 다 겹칠 데 없는 합성어라 위험이 없다.
 *  2. `탕수` 가 국물보다 **위** — 탕수육·꿔바로우는 국이 아니라 튀김이다.
 *  3. `국수`·`우동`·`잡채` 가 `국` 보다 **위** — 안 그러면 `잔치국수` 가 국이 된다.
 *  4. `전복` 이 `전` 보다 **위** — 전복죽이 부침개가 되지 않게.
 *  5. `찌게` 는 원본 표의 흔한 오기라 `찌개` 와 같이 둔다.
 *  6. 한 글자 `김`·`배`·`차` 류는 v1.6.80 과 같은 이유로 안 넣는다(`김치`·`배추`·`차돌` 에 오발).
 *
 * ## `A*B` 는 앞쪽이 주인공
 *
 * `상추쌈*쌈장`·`우엉호두조림*포기김치`·`동그랑땡전*케찹` 처럼 별표로 곁들임을 붙인 줄이 흔하다.
 * **앞 토막으로 먼저 맞히고**, 앞이 하나도 안 걸릴 때만 뒤 토막을 본다.
 * 통짜 `contains` 였던 v1.6.83에선 `우엉호두조림*포기김치` 가 어느 규칙에도 안 걸려 점이 됐다.
 *
 * ## 점(`dot`)이 나오면 그건 **매핑 누락 신호**다
 *
 * 폴백 점은 남겨 뒀지만(자리를 비우면 줄 정렬이 깨진다) 실파일 21칸 124항목에서는 **0개**이고
 * `MenuDayTest` 가 그걸 잠근다. 새 주 메뉴에서 점이 보이면 규칙이 모자란 것이니
 * **[ICON_RULES] 에 줄을 더해라** — 아이콘을 새로 그려야 하면 `res/drawable/ic_menu_*.xml`
 * 옆에 같은 규격(24×24 · stroke 2 · round)으로 하나 그리고 [MenuIcon] 에 값을 더한다.
 */
fun menuIcon(name: String): MenuIcon? {
    val s = name.replace(" ", "")
    for (part in s.split('*')) {
        val hit = ICON_RULES.firstOrNull { (k, _) -> k in part }
        if (hit != null) return hit.second
    }
    return null
}

private val ICON_RULES: List<Pair<String, MenuIcon>> = listOf(
    // ① 한 그릇 밥 — 가장 먼저(합성어라 오발 위험이 없다)
    "덮밥" to MenuIcon.RICE, "비빔밥" to MenuIcon.RICE, "볶음밥" to MenuIcon.RICE,
    // ② 탕수는 국물이 아니다
    "탕수" to MenuIcon.FRIED,
    // ③ 면 — `국` 보다 먼저(잔치국수·칼국수가 국이 되지 않게)
    "국수" to MenuIcon.NOODLE, "우동" to MenuIcon.NOODLE, "라면" to MenuIcon.NOODLE,
    "파스타" to MenuIcon.NOODLE, "잡채" to MenuIcon.NOODLE, "쫄면" to MenuIcon.NOODLE,
    "냉면" to MenuIcon.NOODLE, "짜장" to MenuIcon.NOODLE, "짬뽕" to MenuIcon.NOODLE,
    // ④ 국물 — 재료 이름이 섞여 있어도 국이 이긴다
    "찌개" to MenuIcon.SOUP, "찌게" to MenuIcon.SOUP, "전골" to MenuIcon.SOUP,
    "해장국" to MenuIcon.SOUP, "장국" to MenuIcon.SOUP,
    "국" to MenuIcon.SOUP, "탕" to MenuIcon.SOUP,
    // ⑤ 조리법 — **뒤에 붙는 말이 재료를 이긴다**
    "전복" to MenuIcon.FISH,                                    // `전` 보다 먼저
    "불고기" to MenuIcon.BEEF,
    "조림" to MenuIcon.BRAISE,
    "볶음" to MenuIcon.STIRFRY,
    "무침" to MenuIcon.SEASONED,
    "찜" to MenuIcon.STEAM,
    "튀김" to MenuIcon.FRIED, "까스" to MenuIcon.FRIED, "가스" to MenuIcon.FRIED,
    "강정" to MenuIcon.FRIED,
    "구이" to MenuIcon.GRILL, "스테이크" to MenuIcon.GRILL,
    "전" to MenuIcon.JEON, "부침" to MenuIcon.JEON, "빈대떡" to MenuIcon.JEON,
    "쌈" to MenuIcon.WRAP,
    "숙회" to MenuIcon.SPROUT, "생채" to MenuIcon.SPROUT, "나물" to MenuIcon.SPROUT,
    "샐러드" to MenuIcon.SALAD,
    "겉절이" to MenuIcon.KIMCHI, "깍두기" to MenuIcon.KIMCHI, "석박지" to MenuIcon.KIMCHI,
    "장아찌" to MenuIcon.KIMCHI, "장아짜" to MenuIcon.KIMCHI, "단무지" to MenuIcon.KIMCHI,
    "피클" to MenuIcon.KIMCHI, "김치" to MenuIcon.KIMCHI,
    "수제비" to MenuIcon.NOODLE, "떡볶이" to MenuIcon.NOODLE,
    // ⑥ 남은 밥
    "누룽지" to MenuIcon.RICE, "밥" to MenuIcon.RICE, "죽" to MenuIcon.RICE,
    // ⑦ 재료 — 조리법이 하나도 안 걸렸을 때만
    "생선" to MenuIcon.FISH, "어묵" to MenuIcon.FISH, "오징어" to MenuIcon.FISH,
    "낙지" to MenuIcon.FISH, "삼치" to MenuIcon.FISH, "고등어" to MenuIcon.FISH,
    "꽁치" to MenuIcon.FISH, "가자미" to MenuIcon.FISH, "새우" to MenuIcon.FISH,
    "동태" to MenuIcon.FISH, "멸치" to MenuIcon.FISH, "조기" to MenuIcon.FISH,
    "임연수" to MenuIcon.FISH,
    "돈육" to MenuIcon.BEEF, "소고기" to MenuIcon.BEEF, "제육" to MenuIcon.BEEF,
    "삼겹" to MenuIcon.BEEF, "차돌" to MenuIcon.BEEF, "동그랑땡" to MenuIcon.BEEF,
    "순대" to MenuIcon.BEEF, "스팸" to MenuIcon.BEEF, "햄" to MenuIcon.BEEF,
    "사태" to MenuIcon.BEEF, "고기" to MenuIcon.BEEF,
    "닭" to MenuIcon.DRUMSTICK, "치킨" to MenuIcon.DRUMSTICK, "오리" to MenuIcon.DRUMSTICK,
    "계란" to MenuIcon.EGG, "달걀" to MenuIcon.EGG, "메추리알" to MenuIcon.EGG,
    "두부" to MenuIcon.TOFU,
    "샌드위치" to MenuIcon.SANDWICH, "토스트" to MenuIcon.SANDWICH, "빵" to MenuIcon.SANDWICH,
    "두유" to MenuIcon.MILK, "요구르트" to MenuIcon.MILK, "요거트" to MenuIcon.MILK,
    "우유" to MenuIcon.MILK,
    "과일" to MenuIcon.APPLE, "사과" to MenuIcon.APPLE, "바나나" to MenuIcon.APPLE,
    "수박" to MenuIcon.APPLE, "참외" to MenuIcon.APPLE, "포도" to MenuIcon.APPLE,
    "오렌지" to MenuIcon.APPLE, "딸기" to MenuIcon.APPLE, "황도" to MenuIcon.APPLE,
    "귤" to MenuIcon.APPLE, "메론" to MenuIcon.APPLE, "멜론" to MenuIcon.APPLE,
)
