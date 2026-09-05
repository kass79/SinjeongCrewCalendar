package com.sinjeong.crewcalendar.domain.model

/**
 * **열번 판정 한 곳** (v1.7.2) — 행로표에 적힌 열번과 실시간 API 가 주는 열번이
 * **같은 운행인가**를 정한다. 순수 Kotlin(안드로이드 0줄) — `TrainNoTest` 가 잠근다.
 *
 * ## 왜 필요했나 (2026-09-05 실측)
 *
 * 사용자가 `44` 다이아(야간 토→일, 전반 `2340·2372·2404`)로 본선 지도를 열었는데
 * **내 열차 미검출**이었다. 그 시각 API 에 `2340` 은 없고 **`8340`** 이 한양대에 떠 있었다 —
 * *같은 운행인데 앞자리만 다른 번호*였다. 종전 코드는 `trainNo == no` 한 줄이라 못 잡았다.
 *
 * ## 사용자가 준 열번 체계
 *
 *  · **본선 순환 기본 `2xxx`** — 내선(시계) 끝자리 **짝수**, 외선(반시계) 끝자리 **홀수**.
 *    한 바퀴 돌아 기준역(시청 등)을 지날 때마다 다음 번호로 갱신한다.
 *  · **기지 입·출고·타절은 별도 대역** —
 *    **신정 도착**(신도림행) 내선 `4xxx` / 외선 `6xxx`,
 *    **군자 도착**(성수행) 내선 `3xxx` / 외선 `5xxx`,
 *    **중간 주박·타절**(서울대입구행·을지로입구행·삼성행 등 막차대) `7xxx` 등.
 *  · **`8xxx`** 도 같은 운행의 다른 접두다(사용자 실측 `8340`). **뜻은 미확인 —
 *    사용자 확인 필요.** 2026-09-05 19:59 실호출 36대 중 12대가 `8xxx` 였고 전부 본선이었다.
 *  · **핵심: 같은 운행은 뒤 세 자리가 같다.** `2340`·`4340`·`6340`·`8340` 은 한 운행이고
 *    앞자리는 운행 종류다.
 *
 * ## 접두 충돌 — `5` 하나가 위험하다
 *
 * 지선 왕복 열번(`55xx`~`57xx`)과 본선 `25xx`(예: `2501`)는 **뒤 세 자리가 겹친다.**
 * `5501` 을 `2501` 로 받아 주면 지선 열차를 본선 지도에 내 열차로 세운다. 그래서 `5` 는
 * **행선이 성수·군자 계열일 때만** 본선 후보로 인정하고, 까치산·신도림이거나 **행선을
 * 모르면 거부**한다. 반대로 후보가 **지선(`5xxx`)** 이면 **정확히 같은 번호만** 받는다
 * (지선 접두 변형이 있는지 미확인 — 있으면 사용자 확인 뒤 넓힌다).
 */

/** 네 자리 열번으로 정규화 — 앞의 `0`·`S` 를 벗긴다. 네 자리 숫자가 아니면 null. */
private fun normNo(no: String): String? {
    val t = no.trim().trimStart('S', 's', '0')
    return t.takeIf { it.length == 4 && it.all(Char::isDigit) }
}

/** **운행 열쇠 = 뒤 세 자리.** 같은 운행은 접두가 달라도 이 값이 같다. */
fun runKey(no: String): String = (normNo(no) ?: no.trim()).takeLast(3)

/**
 * **신정기지 도착(신도림행) 열번인가** — 접두 `4`(내선) 또는 `6`(외선).
 *
 * 실시간 API 의 행선 필드(`statnTnm`)는 늦게 바뀐다. 2026-09-05 19:59 실호출에서는 본선
 * 34대가 **전부 `성수종착`** 이었다 — 입고 열차조차 행선만으로는 못 가른다. 접두가 먼저 말한다.
 */
fun isDepotBoundSinjeong(no: String): Boolean = depotBoundInner(no) != null

/** 신정 도착 열번의 방향 — `4xxx` 내선 · `6xxx` 외선. 신정 도착이 아니면 null. */
fun depotBoundInner(no: String): Boolean? = when (normNo(no)?.first()) {
    '4' -> true
    '6' -> false
    else -> null
}

/** 본선 후보가 받아 주는 라이브 접두. `1`(성수지선)·`0` 은 없고, `5` 는 [sameRun] 이 따로 본다. */
private val MAIN_PREFIXES = setOf('2', '3', '4', '6', '7', '8', '9')

/**
 * **[candidate](행로표 열번)와 [live](API 열번)가 같은 운행인가.**
 *
 * @param liveDest API 가 준 행선(`statnTnm`·`destName`). `5xxx` 충돌을 가릴 때만 쓴다.
 */
fun sameRun(candidate: String, live: String, liveDest: String? = null): Boolean {
    val c = normNo(candidate) ?: return false
    val l = normNo(live) ?: return false
    if (c.takeLast(3) != l.takeLast(3)) return false
    if (c == l) return true
    // 후보가 지선이면 접두 변형을 인정하지 않는다 — 지선에 변형이 있는지 아직 모른다.
    if (c.first() == '5') return false
    return when (l.first()) {
        in MAIN_PREFIXES -> true
        // 지선 왕복 `55xx~57xx` 과 본선 `25xx` 가 겹친다 — 행선이 본선 종착일 때만 받는다.
        '5' -> liveDest != null && (liveDest.contains("성수") || liveDest.contains("군자"))
        else -> false
    }
}

/**
 * [pickRun] 이 견주는 **라이브 한 대** — 부르는 쪽의 행 타입(`MainTrainMark`·`PositionRow`·
 * `TrainMark`)에서 필요한 네 값만 옮겨 담는다. 고른 뒤 원래 행은 `trainNo` 로 되찾는다
 * (한 스냅샷에 같은 열번이 두 줄로 오지는 않는다).
 */
data class LiveRef(
    val trainNo: String,
    /** API 행선(`statnTnm`·`destName`) — 접두 `5` 충돌을 가릴 때 [sameRun] 이 본다. */
    val dest: String? = null,
    /** API 가 준 지금 역명 — 시간표 근접 판정용. 모르면 빈 문자열(그 단계를 건너뛴다). */
    val station: String = "",
    /** true = 내선. 시간표의 어느 판(`inout`)을 볼지 고른다. */
    val inner: Boolean = true,
)

/** [pickRun] 이 시간표 근접으로 가를 때 봐 주는 지연 — **±15분**. 넘으면 다른 운행으로 본다. */
const val PICK_SLACK_SEC = 15 * 60

/**
 * **한 후보(몸통)에 라이브가 여럿 맞으면 하나만 고른다** (v1.7.3).
 *
 * [sameRun] 은 *뒤 세 자리*만 보므로 `2372` 와 `8372` 가 동시에 살아 있으면 **둘 다** 맞다고
 * 한다 — v1.7.2 의 본선 지도는 그래서 둘 다 노랗게 칠했다. 한 몸통은 물리 열차 하나다.
 * **서로 다른 몸통**(전반 `340` · 후반 `042`)은 각각 제 열차를 고르므로 여전히 둘 다 강조된다.
 *
 * 우선순위 — 위에서 갈리면 아래는 안 본다:
 *  1. **번호가 정확히 같은 것** (`2372` 후보에 라이브 `2372` 가 있으면 그것)
 *  2. **시간표에서 그 운행의 지금 자리에 가장 가까운 것** — [timetableLookup] 이 준
 *     *그 운행이 이 라이브가 선 역을 지나는 예정 시각*이 [nowSec] 에 가장 붙은 것.
 *     지연 **±[PICK_SLACK_SEC]** 밖이면 후보에서 뺀다.
 *     (시간표는 역→시각으로 짜여 있어 *시각 거리*로 재지만, 운행은 한 방향으로만 가므로
 *     역 인덱스 거리와 순서가 같다. 실측 2026-09-05 20:22:57 — 토요일 내선 `2372` 는
 *     선릉 20:19:30·역삼 20:22:00 예정이고 라이브 `8372` 가 **선릉**에 있었다: +3.5분.)
 *  3. 그래도 못 가르면 **접두가 작은 것**(행로표의 `2xxx` 에 가장 가깝다).
 *
 * @param lives 지금 API 에 살아 있는 열차 전부. 후보와 안 맞는 것은 여기서 걸러진다.
 * @param nowSec 자정 기준 초(`Line2Timetable.serviceClock`). 음수면 2단계를 건너뛴다.
 * @param timetableLookup `(후보 열번, 라이브) → 예정 시각(초)`. null 이면 2단계를 건너뛴다.
 */
fun pickRun(
    candidate: String,
    lives: List<LiveRef>,
    nowSec: Int = -1,
    timetableLookup: ((String, LiveRef) -> Int?)? = null,
): LiveRef? {
    val fits = lives.filter { sameRun(candidate, it.trainNo, it.dest) }
    if (fits.size <= 1) return fits.firstOrNull()
    val c = normNo(candidate)
    fits.firstOrNull { normNo(it.trainNo) == c }?.let { return it }
    if (nowSec >= 0 && timetableLookup != null) {
        fits.mapNotNull { l -> timetableLookup(candidate, l)?.let { l to kotlin.math.abs(it - nowSec) } }
            .filter { it.second <= PICK_SLACK_SEC }
            .minByOrNull { it.second }
            ?.let { return it.first }
    }
    // 뒤 세 자리는 이미 다 같으니 네 자리 문자열 최소 = 접두 최소다.
    return fits.minByOrNull { normNo(it.trainNo) ?: it.trainNo }
}
