package com.sinjeong.crewcalendar.domain.model

import java.time.LocalDate

/**
 * **내 열차 행선판** — 지붕 위에 무엇이라고 쓸 것인가 (v1.7.5).
 * 순수 Kotlin(안드로이드 0줄) — `HeadboardTest` 가 잠근다.
 *
 * ## 왜 새로 만들었나 (2026-09-05 사용자 실측)
 *
 * `51` 다이아(휴휴) 밤에 내 열차 **`8401`** 위에 앱이 **`성수행`** 을 달았는데 실제는
 * **홍대입구행**이었다. 행선판 글자를 API `statnTnm` 에서 가져왔기 때문이다 —
 * 그 필드는 **타절·입고 열차에서 전부 `성수종착`** 이라 못 믿는다(v1.7.2 절 ②-2 실측:
 * 본선 34대가 접두를 가리지 않고 전부 `성수종착`, 입고 대역 `6333` 조차 그랬다).
 *
 * 사용자 처방이 그대로 이 파일의 규칙이다:
 *
 * > *"잘 모르겠으면 성수행 <- 이런 표시를 안해도 괜찮어~"*
 * > *"행로표에 정보도 있다. 오늘 51다이아 홍대입구역 주박, 50다이아 신도림역 주박"*
 *
 * ## 3단 규칙 — 위에서 갈리면 아래는 안 본다
 *
 *  1. **행로표 표지.** [RouteTable] 의 전반/후반 문자열을 `·` 로 쪼개, 이 열차가 앉은 칸
 *     **뒤에 처음 나오는 표지**를 본다 — `X역주박`·`X주박` → `X행`,
 *     `(군자입고)` → `성수행`, `(신정입고)` → `신도림행`.
 *     표지 **앞의 열차들 모두**에 같은 값이 붙는다: `2401·2425·홍대입구역주박·2009` 이면
 *     `2401` 도 `2425` 도 홍대입구행이다(한 물리 열차가 번호만 바꾸며 주박역까지 간다).
 *     **반(전반/후반)을 넘지 않는다** — 표지 뒤의 `2009`(다음 날 아침 첫차)는 다른 운행이다.
 *  2. **라이브 열번 접두**(도메인 사전 "열번 체계"): `4`·`6` = 신정 도착 → `신도림행`,
 *     `3`·`5` = 군자 도착 → `성수행`.
 *  3. **모르면 `null` — 행선판을 아예 안 그린다.** 2xxx 순환·7xxx/8xxx 중간 타절이 여기다.
 *     내 열차는 노란 몸통·흰 테·연기·갈매기로 이미 구분되므로 **틀린 행선을 다는 것보다
 *     아무것도 안 다는 편이 낫다**(`MyTrain` KDoc 의 "추정하지 않는다" 와 같은 결정).
 *
 * ⚠ **API 행선(`statnTnm`)은 쓰지 않는다.** 접두로도 추정하지 않는다(2·7·8 은 null 이다) —
 * 실측 `8401` 이 아까는 신도림행, 뒤에는 홍대입구행이었고 API 는 둘 다 `성수종착` 이었다.
 *
 * ⚠ **지선 카드(`LineMap.kt`)의 `신도림행`/`까치산행` 은 그대로다** — 지선은 왕복 두 역뿐이라
 * API 행선이 정확하다. 여기 규칙은 본선 순환선 전용이다.
 */

/** 토막 안의 **첫 네 자리 숫자** — `2306(동대문교대)`·`33DIA#5925출고교대` 에서 열번만 뽑는다. */
private val FOUR_IN = Regex("\\d{4}")

/**
 * 토막 하나가 말하는 행선. 표지가 없으면 null.
 *
 * ⚠ `역주박` 을 `주박` 보다 **먼저** 본다 — `홍대입구역주박` 을 `주박` 으로 자르면
 * `홍대입구역행` 이 된다.
 * ⚠ `(군자입출고)` 는 표지가 **아니다** — 들어갔다 다시 나오는 칸이라 행선이 안 정해진다
 * (`"군자입출고".contains("군자입고")` 는 false 다 — 글자가 하나 끼어 있다).
 */
private fun markerDest(tok: String): String? = when {
    tok.contains("군자입고") -> "성수행"
    tok.contains("신정입고") -> "신도림행"
    tok.contains("역주박") -> tok.substringBefore("역주박").takeIf { it.isNotEmpty() }?.plus("행")
    tok.contains("주박") -> tok.substringBefore("주박").takeIf { it.isNotEmpty() }?.plus("행")
    else -> null
}

/** 라이브 열번 접두만 보는 2단계 — 신정 도착 `4`·`6` / 군자 도착 `3`·`5`. 나머지는 null. */
private fun prefixDest(liveNo: String): String? = when {
    isDepotBoundSinjeong(liveNo) -> "신도림행"
    normNo(liveNo)?.first() in setOf('3', '5') -> "성수행"
    else -> null
}

/**
 * **내 열차 지붕에 달 행선** — 모르면 `null`(달지 않는다). 위 KDoc 의 3단 규칙 그대로.
 *
 * @param liveNo 실시간 API 가 준 열번(`8401`). 행로표 번호(`2401`)와 다를 수 있어
 *   **뒤 세 자리**([runKey])로 견준다(v1.7.2 — 글자 그대로 견주면 못 찾는다).
 */
fun myDestination(duty: DutyCode, date: LocalDate, liveNo: String): String? {
    // 지선 근무는 여기서 답하지 않는다 — 지선 왕복 열번(`5527`)에 본선 접두 규칙을 먹이면
    // 있지도 않은 `성수행` 이 달린다. 지선 행선은 지선 카드가 API 로 정확히 안다.
    if ((DutyCode.effectiveNight(duty, date)?.first ?: duty).isBranch) return null
    val live = normNo(liveNo) ?: return null
    val a = routeAssignment(duty, date) ?: return null
    for (half in listOf(a.firstHalf, a.secondHalf)) {
        val toks = half.split('·').map(String::trim)
        // ⚠ [sameRun] 이 아니라 [runKey] 다. `sameRun` 은 지선 왕복 `55xx` ↔ 본선 `25xx` 충돌
        // 때문에 라이브 접두 `5` 를 **행선을 모르면 거부**하는데, 여기는 위에서 지선 근무를
        // 이미 걸러 냈으므로 그 충돌이 일어날 수 없다. 거부하면 `5xxx`(군자 도착 외선)가
        // 제 행로표 칸을 못 찾아 2단계 접두 규칙까지 통째로 못 간다.
        val i = toks.indexOfFirst { t ->
            FOUR_IN.find(t)?.value?.let { runKey(it) == runKey(live) } == true
        }
        if (i < 0) continue
        // 이 열차가 앉은 칸부터 훑는다 — `1925(군자입고)` 처럼 표지가 제 칸에 붙은 경우가 있다.
        return toks.drop(i).firstNotNullOfOrNull(::markerDest) ?: prefixDest(liveNo)
    }
    // 행로표에 없는 열차 = 내 운행이 아니다. 접두만으로 추정하지 않는다.
    return null
}
