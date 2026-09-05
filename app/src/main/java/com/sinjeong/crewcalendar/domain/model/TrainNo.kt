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
