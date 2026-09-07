package com.sinjeong.crewcalendar.presentation.live

/*
 * 출퇴근 역 실시간 (v1.7.9 ⑦) — **순수 로직만.**
 *
 * 사용자 원문(2026-09-06): *"설정에 출퇴큰 자주이용하는 역을 등록을 하면 행로표 위쪽에 전후
 * 3정거장 위치정보를 볼수있는 역 칸을 만드는건 어때? … 그 역을 클릭하면 위치정보를 볼수있으니"*
 *
 * 안드로이드·Compose 를 한 줄도 안 쓴다 — 테스트 하네스가 JUnitCore 직접 실행이라 클래스패스에
 * `android.jar` 가 없다(`tools/runtests.ps1`). 화면은 [CommuteBar], 네트워크는
 * [BranchLive.arrivalsAt] 를 **그대로 재사용**한다(새 의존성·새 키·새 서버 없음).
 *
 * ⚠ **도착 API 는 "역으로 다가오는 열차"만 준다** — 지나간 열차는 응답에 없다. 그래서
 * "전후 3정거장"은 **"앞으로 들어오는 열차 최대 3대"**로 만든다(사용자 동의 완료).
 */

/**
 * 등록 상한. v1.7.9 는 **4**(사용자 예시가 마곡·까치산 2·까치산 5·신도림 넉 장이었다)였고
 * v1.7.13 에서 카스가 **5** 로 올렸다 — *"최대 5개까지 선택할수있었으면 해!"*.
 *
 * ⚠ **칩 줄은 여전히 한 줄이다**(v1.7.9 카스 확정 — 두 줄 접기 금지). 다섯째 칩은 오른쪽으로
 * 밀려 가로 스크롤로 본다. 늘리는 것은 이 상수 하나이고 저장·복원·안내 문구가 전부 이 값을
 * 읽는다([encodeCommute]·[decodeCommute]·`ThemeController.setCommuteStations`·`CommuteBar`·
 * `SettingsScreen`) — 숫자를 다시 적어 넣지 말 것.
 */
internal const val COMMUTE_MAX = 5

/** 한 번에 보여 주는 다가오는 열차 수 */
internal const val COMMUTE_ROWS = 3

/**
 * 등록한 출퇴근 역 하나 = **역 × 호선 × 방향**.
 *
 * ⚠ **[updnLine] 은 `"0"`/`"1"` 이 아니라 낱말이다.** 실시간 **위치**(`realtimePosition`)는
 * 숫자를 주지만 실시간 **도착**(`realtimeStationArrival`)은 `상행`·`하행`(직선 노선) ·
 * `내선`·`외선`(2호선 순환)을 준다 — 2026-09-06 19:51 까치산 실호출로 확인했다.
 * 저장값이므로 낱말을 그대로 담는다(응답과 글자 그대로 견줘야 필터가 맞다).
 *
 * 같은 이름 다른 노선을 [subwayId] 가 가른다 — **까치산은 2호선(1002)·5호선(1005) 둘**이고
 * 이 설계의 핵심 사례다.
 */
internal data class CommuteStation(
    val name: String,
    val subwayId: String,
    val updnLine: String,
)

/* ── 저장 문자열 ─────────────────────────────────────────────────
 *
 * `역명|subwayId|updnLine` 을 `;` 로 이은 **한 줄**. JSON 라이브러리를 새로 넣지 않는다
 * (이 저장소 관례 — `Weather.kt` KDoc). 역명에 `|`·`;` 가 들어간 실례는 없지만 넣어 두면
 * 줄이 통째로 깨지므로 저장할 때 지운다. 읽기는 **깨진 칸을 조용히 버린다** — 옛 값·손댄 값이
 * 있어도 화면이 안 죽는다.
 */

private fun clean(s: String) = s.replace("|", "").replace(";", "").trim()

internal fun encodeCommute(list: List<CommuteStation>): String =
    list.take(COMMUTE_MAX).joinToString(";") {
        "${clean(it.name)}|${clean(it.subwayId)}|${clean(it.updnLine)}"
    }

internal fun decodeCommute(saved: String?): List<CommuteStation> =
    saved.orEmpty().split(";").mapNotNull { part ->
        val f = part.split("|")
        if (f.size != 3) return@mapNotNull null
        val (n, id, up) = f.map { it.trim() }
        if (n.isBlank() || id.isBlank() || up.isBlank()) null else CommuteStation(n, id, up)
    }.distinct().take(COMMUTE_MAX)

/* ── 호선 이름 ───────────────────────────────────────────────── */

/**
 * `subwayId` → 사람이 읽는 호선 이름. **응답에 실제로 오는 것 위주**고, 모르는 id 는
 * 숫자를 그대로 돌려준다(수도권 전 노선이 대상이라 표를 완벽히 채울 수 없다).
 *
 * ⚠ **여기 있는 id 는 [LINE_ARGB] 에도 있어야 한다** — 두 표의 키가 어긋나면 이름은 뜨는데
 * 기관차만 회색으로 떨어진다(v1.7.12 에서 실제로 여섯 id 가 그랬다). `CommuteMiniTest` 가
 * **키 전수 대조**로 잠근다. 그래서 `internal` 이다(테스트가 키를 읽어야 한다).
 */
internal val LINE_NAMES = mapOf(
    "1001" to "1호선", "1002" to "2호선", "1003" to "3호선", "1004" to "4호선",
    "1005" to "5호선", "1006" to "6호선", "1007" to "7호선", "1008" to "8호선",
    "1009" to "9호선", "1032" to "GTX-A", "1061" to "중앙선", "1063" to "경의중앙선",
    "1065" to "공항철도", "1067" to "경춘선", "1069" to "수인분당선", "1071" to "수인선",
    "1075" to "수인분당선", "1077" to "신분당선", "1078" to "공항철도", "1081" to "경강선",
    "1092" to "우이신설선", "1093" to "서해선", "1094" to "신림선", "1095" to "동북선",
)

internal fun lineName(subwayId: String) = LINE_NAMES[subwayId] ?: subwayId

/* ── 등록 화면: (호선 × 방향) 조합 뽑기 ─────────────────────── */

/**
 * 등록 화면 칩 하나 = 응답에 실제로 있던 **(호선 × 방향)** 조합.
 *
 * [bound] 는 `trainLineNm`("방화행 - 화곡방면")의 **뒤쪽 방면 낱말**이다. 앞쪽 행선은 같은
 * 방향에서도 열차마다 갈린다 — 5호선 하행에 `마천행`·`하남검단산행` 이 함께 온다(실호출 확인).
 * 방면은 그 방향에서 하나뿐이라 칩이 조용히 둘로 갈리지 않는다.
 */
internal data class CommuteOption(
    val subwayId: String,
    val updnLine: String,
    val bound: String,
) {
    val label: String get() = "${lineName(subwayId)} · $bound"
    fun toStation(name: String) = CommuteStation(name.trim(), subwayId, updnLine)
}

/** `"방화행 - 화곡방면"` → `"화곡방면"`. `-` 가 없으면 통째로, 그것도 비면 방향 낱말. */
internal fun boundOf(trainLineNm: String, updnLine: String): String =
    trainLineNm.substringAfter(" - ", "").trim()
        .ifBlank { trainLineNm.trim() }
        .ifBlank { updnLine }

/**
 * 응답 → 고를 수 있는 조합들. **운행 종료(`INFO-200`)면 빈 목록**이고 그건 오류가 아니다 —
 * 부르는 쪽이 "지금은 오는 열차가 없어 방향을 고를 수 없습니다"라고 안내만 한다.
 */
internal fun commuteOptions(rows: List<ArrivalRow>): List<CommuteOption> =
    rows.filter { it.subwayId.isNotBlank() && it.updnLine.isNotBlank() }
        .groupBy { it.subwayId to it.updnLine }
        .map { (k, v) -> CommuteOption(k.first, k.second, boundOf(v.first().trainLineNm, k.second)) }
        .sortedWith(compareBy({ it.subwayId }, { it.updnLine }))

/* ── 보는 화면: 다가오는 열차 고르기 ────────────────────────── */

/**
 * **이미 그 역에 닿았거나 지나간** 상태코드. `0` 진입 · `1` 도착 · `2` 출발.
 * 나머지(`3` 전역출발 · `4` 전역진입 · `5` 전역도착 · `99` 운행중)가 다가오는 열차다.
 */
private val PASSED = setOf("0", "1", "2")

private fun ArrivalRow.matches(s: CommuteStation) =
    subwayId == s.subwayId && updnLine == s.updnLine

/** 그 역·그 호선·그 방향으로 **다가오는** 열차 (가까운 순, 최대 [COMMUTE_ROWS] 대) */
internal fun commuteApproaching(
    rows: List<ArrivalRow>, s: CommuteStation, max: Int = COMMUTE_ROWS,
): List<ArrivalRow> =
    rows.filter { it.matches(s) && it.arvlCd !in PASSED }.sortedBy { it.etaSec }.take(max)

/**
 * 지금 그 역에 **진입(0)·도착(1)** 중인 열차 한 대(없으면 null).
 *
 * 다가오는 목록에서는 뺐지만 맨 위 한 줄로는 보여 준다 — 승강장에 서 있거나 개찰구 앞인
 * 승무원에게 *"지금 들어오고 있다"* 는 가장 실행 가능한 정보다. **출발(2)은 안 센다** —
 * 이미 떠난 열차라 알려 줄 값이 없다.
 */
internal fun commuteAtStation(rows: List<ArrivalRow>, s: CommuteStation): ArrivalRow? =
    rows.firstOrNull { it.matches(s) && (it.arvlCd == "0" || it.arvlCd == "1") }

internal fun atStationText(arvlCd: String) = if (arvlCd == "0") "지금 진입" else "지금 도착"

/** 남은 초 → 사람 말. 0 이하면 `곧 도착`, 1분 미만이면 초만(`0분 30초`는 안 읽힌다). */
internal fun etaText(sec: Int): String = when {
    sec <= 0 -> "곧 도착"
    sec < 60 -> "${sec}초"
    else -> "${sec / 60}분 ${sec % 60}초"
}

/** `"6분 후 (오목교…)"` · `"5분 30초 후"` — 남은 시간을 되풀이하는 문장인가 */
private val TIME_MSG = Regex("(분|초)\\s*후")

/**
 * 열차 한 줄의 **위치** 글.
 *
 * ⚠ **v1.7.12 ① 부터 화면은 이 글을 안 쓴다** — 위치를 글자 대신 [commuteSlot] 이 낸 칸에
 * 기관차를 세워서 말한다. 함수는 `CommuteTest` 가 잠근 `arvlMsg2` 두 종류(위치 문장 / 남은
 * 시간 문장)의 **판독 규칙**이라 남겨 둔다 — [commuteSlot] 이 같은 글을 다르게 읽으므로
 * 지우면 그 규칙의 근거가 사라진다.
 *
 * `arvlMsg2` 는 승강장 전광판 문장인데 두 종류가 섞여 온다:
 *  · **위치**를 말하는 것 — `"까치산 전역출발"` · `"전역 도착"` · `"3번째 전역"` → 그대로 쓴다.
 *  · **남은 시간**을 말하는 것 — `"6분 후 (오목교(목동운동장앞))"` · `"5분 30초 후"`.
 *    이건 아랫줄 [etaText] 와 같은 말이라 **`arvlMsg3`(그 열차가 지금 있는 역)** 으로 바꾼다.
 *    에뮬 실측에서 `"5분 30초 후"` / `"5분 30초 · 까치산행"` 두 줄이 나란히 떴다 —
 *    한 줄을 통째로 버리는 셈이라 그 자리에 **지금 어디인지**를 넣는 게 맞다.
 */
internal fun positionText(r: ArrivalRow): String {
    val msg = r.arvlMsg2.trim()
    val here = r.arvlMsg3.trim()
    if (here.isNotBlank() && TIME_MSG.containsMatchIn(msg)) return here
    return msg.ifBlank { here }.ifBlank { "위치 확인 중" }
}

/* ── 미니 노선 한 줄 (v1.7.12 ①) ─────────────────────────────
 *
 * 카스: *"세로칸은 최소화 해서 일자로 보여주면 좋지"* → 다가오는 열차를 글자 목록으로 쌓지 않고
 * **가로 미니 노선 한 줄** 위에 기관차를 세운다(시안 "제안 A"). 왼쪽 끝이 [COMMUTE_SLOTS]−1
 * 번째 전역, 오른쪽 끝이 **등록한 그 역**이다.
 */

/**
 * 미니 노선 칸 수 — 왼쪽 끝(`5번째 전역`) 0 … 오른쪽 끝(등록한 역) 5. **점 여섯 개.**
 *
 * v1.7.12 ① 은 4(3번째 전역까지)였다. 카스(2026-09-07): *"미니 노선이 3번째 전역까지 가
 * 최선인거야? **5칸 전해도 될꺼같은데?**"* — 4칸에서는 3정거장보다 먼 열차가 전부 맨 왼쪽에
 * 뭉쳤다(`N번째 전역` 은 `3`·`4`·`5` 가 흔히 온다).
 *
 * ⚠ **여기가 가로 하한이다.** 점이 하나 늘 때마다 칸 간격이 줄고 기관차(몸통 22dp)가 이웃 칸을
 * 밟는다. 7 로 올리려면 먼저 실측하라 — v1.7.12 ③ 실측(접힘 411dp·배율 1.0)에서 6칸 간격이
 * 몸통의 약 2배였고 **배율 1.5 에서 1.3배**까지 좁아졌다.
 */
internal const val COMMUTE_SLOTS = 6

/**
 * `"3번째 전역"` · `"[2]번째 전역 (오목교)"` · `"2 번째  전역"` — 숫자와 `번째` 사이에
 * **닫는 대괄호와 공백**이 끼는 꼴을 허용한다(전광판 문장이라 노선마다 꼴이 다르다).
 */
private val NTH_BEFORE = Regex("(\\d+)\\s*\\]?\\s*번째\\s*전역")

/**
 * `"전역 도착"`(한 정거장 전) · **`"전전역 출발"`(두 정거장 전)** — `전` 을 **세어서** 칸을 낸다.
 *
 * ⚠ `전전역` 은 2026-09-06 v1.7.9 설계 때 못 본 꼴이고 **v1.7.12 검증에서 5호선 마곡 실화면에
 * 떴다**(`전전역 출발`). `전역` 만 보면 두 정거장 전 열차를 한 정거장 전에 세운다.
 *
 * 앞의 `(?:^|[^가-힣])` 는 역 이름 꼬리가 `전` 으로 끝나는 경우(`…전역`)를 안 집으려는 것이다 —
 * `"까치산 전역출발"` 은 앞이 공백이라 걸리고, 붙어 있는 이름은 안 걸려 [arvlCd] 로 떨어진다.
 */
private val PREV_STATIONS = Regex("(?:^|[^가-힣])(전{1,3})역")

/**
 * 열차 한 대의 **미니 노선 칸 좌표** `0..[COMMUTE_SLOTS]-1`.
 *
 * `5` = 등록한 역 위 · `4` = 전역 · `3` = 2번째 전역 · `2` = 3번째 전역 · `1` = 4번째 전역 ·
 * `0` = **5번째 전역 또는 그보다 멀리**(그래서 왼쪽 끝 라벨이 `5번째 전역+` 이다 — [CommuteBar]).
 *
 * 읽는 순서(먼저 맞는 것이 이긴다):
 *  1. **[arvlCd] `0`(진입)·`1`(도착) → 역 점 위**(v1.7.9 확정 — 이 둘만 `지금 도착`으로 따로 센다).
 *  2. `당역` 이 든 문장 → 역 점 위.
 *  3. `N번째 전역` → `5 − N`. **`7번째 전역` 처럼 칸을 넘으면 가장 먼 칸(0)으로 떨어뜨린다.**
 *  4. 숫자 없는 `전역` → `전` 을 센다: `"전역 도착"`·`"까치산 전역출발"` 한 정거장 전 ·
 *     **`"전전역 출발"` 두 정거장 전**(2026-09-07 5호선 마곡 실화면에서 확인한 꼴이다).
 *  5. [arvlCd] `3`(전역출발)·`4`(전역진입)·`5`(전역도착) → 한 정거장 전. 글자가
 *     `"5분 30초 후"` 처럼 남은 시간만 말할 때 상태코드가 대신 위치를 말해 준다.
 *  6. **그 밖에는 0** — `"8분 후"`·빈 문자열·모르는 글자꼴. 아직 멀리 있다는 뜻이라
 *     역 점 위에 세우는 것보다 안전하다. **절대 예외를 던지지 않는다.**
 *
 * ⚠ 그래서 **0 은 "정확히 5번째 전역" 이 아니다** — `N ≥ 5` 와 위치를 아예 안 말하는 문장이
 * 다 같이 앉는 **바닥 칸**이다. 라벨에 `+` 를 붙여 둔 이유이고, 떼면 그 뭉침이 다시 조용해진다.
 *
 * ⚠ 여기 기관차엔 **열번을 넣지 않는다.** 확정 표의 *"열차 아이콘 = 열번 상자"* 는 실시간
 * **지도**(본선·지선) 규칙인데, 도착 API 응답에는 열번(`btrainNo`)이 오긴 해도 이 칸이 말하는
 * 것은 *"내가 탈 열차가 몇 정거장 앞"* 이라 열번이 정보가 아니다(지도처럼 내 열번을 찾는
 * 화면이 아니다). 넣을 자리도 없다 — 몸통이 22dp 라 4자리가 안 든다.
 */
internal fun commuteSlot(arvlMsg2: String, arvlCd: String): Int {
    val last = COMMUTE_SLOTS - 1
    if (arvlCd == "0" || arvlCd == "1") return last
    val msg = arvlMsg2.trim()
    if (msg.contains("당역")) return last
    NTH_BEFORE.find(msg)?.let { m ->
        val n = m.groupValues[1].toIntOrNull() ?: return 0
        return (last - n).coerceIn(0, last)
    }
    PREV_STATIONS.find(msg)?.let { return (last - it.groupValues[1].length).coerceIn(0, last) }
    if (arvlCd == "3" || arvlCd == "4" || arvlCd == "5") return last - 1
    return 0
}

/**
 * 열차 한 대를 **한 걸음** 앞으로 옮긴다 — 미니 노선 연속 좌표 `0f..[COMMUTE_SLOTS]-1f`(v1.7.13 ①).
 *
 * 카스: *"역으로 다가오는 **열차아이콘이 안움직이는데?**"* — v1.7.12 는 15초 폴링 때마다
 * 칸을 뛰고 그 사이엔 멈춰 있었다. 재료는 이미 응답에 있다: **남은 초**([etaSec] = `barvlDt`,
 * 화면 오른쪽의 `N분 M초`)가 1초씩 준다. 그 값으로 칸 사이를 메우면 **API 를 더 안 부른다.**
 *
 * ## 규칙 — 확정 표 *"열차 이동 = 시간 기반 등속 전진"*(v1.7.5)을 그대로 따른다
 *
 *  ⓐ **앞으로만 간다.** [dtSec] 이 음수면 0 이고, 새 응답이 뒤를 가리켜도 [prev] 를 들고 버틴다.
 *  ⓑ **다음 칸을 안 넘는다.** 예측이 실측을 앞질러 있지도 않은 도착을 그리지 않는다
 *     (`CREEP_MARGIN` 과 같은 취지 · 여기는 칸이 6개뿐이라 **한 칸이 상한**이다).
 *  ⓒ **도착·진입이면 역 점 위에 선다** — [commuteSlot] 이 이미 마지막 칸을 주므로 그대로 멈춘다.
 *
 * ## ⚠ **자리를 눈금에서 다시 계산하지 말 것** — 그러면 폴링마다 멎는다
 *
 * 처음엔 `칸 + 남은칸 × 조회뒤흐른초 ÷ 눈금` 으로 **매번 다시 계산**했다. 에뮬 실측(2026-09-07
 * 03:58:41~54, 5호선 마곡)에서 15초 동안 `0.075 → 0.232` 로 잘 흐르다가 **다음 폴링에서
 * 계산값이 0 으로 되감겼고**, ⓐ 가 뒷걸음은 막았지만 자리가 `0.232` 에 **13초를 붙박여** 있었다.
 * 15초 중 2초만 움직이는 셈이라 카스가 말한 그 "안 움직인다"가 그대로 돌아온다.
 *
 * 그래서 [prev] 에 **걸음을 더한다**(v1.7.5 `stepMotion` 과 같은 꼴). 속도만 폴링마다 새로
 * 잡는다 — `남은 초 ÷ 남은 칸` = **한 칸에 몇 초**. 칸이 정수로 되감겨도 자리는 안 되감긴다.
 *
 * ⚠ **`TrainMotion.stepMotion` 을 그대로는 못 쓴다.** 그쪽은 43역 **순환** 좌표라 `unfold` 가
 * 반 바퀴(여기선 3칸)를 넘는 차이를 "뒤로"로 접는다 — 0번 칸 열차가 5번 칸을 목표로 받으면
 * **−1 칸(뒤)** 이 된다(실산: `d = 5 → 5 > 3 → d − 6 = −1`). 순환이 아닌 6칸 자에는 안 맞아
 * 여기 순수 함수를 따로 두고 `CommuteMiniTest` 가 잠근다.
 *
 * @param prev 직전 걸음의 자리(처음 본 열차면 `null` — 그때는 [slot] 에서 시작한다).
 * @param slot [commuteSlot] 이 낸 칸.
 * @param etaSec 지금 응답의 남은 초(`barvlDt`) — **속도만** 여기서 나온다.
 * @param dtSec 직전 걸음 뒤로 흐른 초(1초 눈금이면 1). 음수는 0 으로 본다.
 */
internal fun commuteAdvance(prev: Float?, slot: Int, etaSec: Int, dtSec: Float): Float {
    val last = (COMMUTE_SLOTS - 1).toFloat()
    if (slot >= COMMUTE_SLOTS - 1) return last                      // ⓒ 도착 — 역 점 위
    if (prev == null) return slot.toFloat().coerceIn(0f, last)      // 처음 본 열차
    /** 한 칸에 몇 초 — 남은 칸을 남은 초에 간다고 본 **등속** 하나. 하한 1초(0 나눗셈 방지). */
    val secPerSlot = (etaSec.toFloat() / (COMMUTE_SLOTS - 1 - slot)).coerceAtLeast(1f)
    // ⓑ 상한은 다음 칸. 다만 [prev] 가 이미 그보다 앞이면(칸이 뒤로 온 이상한 응답)
    // 끌어내리지 않고 **그 자리에 선다** — ⓐ.
    val stop = maxOf(minOf(slot + 1f, last), prev)
    return (prev + dtSec.coerceAtLeast(0f) / secPerSlot).coerceIn(0f, stop)
}

/* ── 호선 색 ─────────────────────────────────────────────────
 *
 * 카스: *"아이콘을 좀 더 귀엽게 각호선 색상에 맞게"*. 값은 **서울 공식 노선색**이고
 * `Long` ARGB 로 산다 — 테스트 하네스에 Compose 가 없어 `Color` 를 못 쓴다(`MapArgb` 와 같은
 * 처방). **`const` 로 바꾸지 말 것** — 인라인돼 테스트가 값을 못 잠근다.
 *
 * ⚠ 표에 없는 id 는 [COMMUTE_LINE_FALLBACK_ARGB] 회색으로 떨어진다. 수도권 전 노선이 대상이라
 * 표를 완벽히 채울 수 없고, 새 노선이 생겨도 **앱이 죽으면 안 된다**([lineName] 과 같은 태도).
 *
 * ⚠ **키는 [LINE_NAMES] 와 한 벌이어야 한다.** v1.7.12 ① 은 여섯 id(`1061`·`1069`·`1071`·
 * `1078`·`1094`·`1095`)에 **이름만 있고 색이 없어** 그 노선 기관차가 조용히 회색으로 떨어졌다.
 * `CommuteMiniTest` 가 **키 전수 대조**로 잠근다 — 이름을 늘릴 때 색도 같이 늘려야 통과한다.
 *
 * ⚠ **김포골드라인은 여기 없다.** 서울 실시간도착 API 의 `subwayId` 코드 표에 아예 없는 노선이라
 * (김포시 골드라인운영㈜ 운영) 응답으로 올 일이 없다. v1.7.12 ① 이 `1032` 에 김포골드 색
 * `#A17E46` 을 달아 뒀는데 **`1032` 는 GTX-A** 다([LINE_NAMES] 가 처음부터 그렇게 적고 있었다).
 * **없는 노선에 색을 달아 두면 다음 사람이 또 헷갈린다** — 항목 자체를 두지 않는다.
 */
internal val LINE_ARGB = mapOf(
    "1001" to 0xFF0052A4L, // 1호선
    "1002" to 0xFF00A84DL, // 2호선
    "1003" to 0xFFEF7C1CL, // 3호선
    "1004" to 0xFF00A5DEL, // 4호선
    "1005" to 0xFF996CACL, // 5호선 (보라)
    "1006" to 0xFFCD7C2FL, // 6호선
    "1007" to 0xFF747F00L, // 7호선
    "1008" to 0xFFE6186CL, // 8호선
    "1009" to 0xFFBB8336L, // 9호선
    "1032" to 0xFFA38FE6L, // GTX-A (연보라 — 아래 ⚠ 참고)
    "1061" to 0xFF77C4A3L, // 중앙선   = 1063 경의중앙(같은 선로다)
    "1063" to 0xFF77C4A3L, // 경의중앙
    "1065" to 0xFF0090D2L, // 공항철도
    "1067" to 0xFF178C72L, // 경춘
    "1069" to 0xFFF5A200L, // 수인선   = 1075 수인분당
    "1071" to 0xFFF5A200L, // 수인선   = 1075 수인분당
    "1075" to 0xFFF5A200L, // 수인분당
    "1077" to 0xFFD4003BL, // 신분당
    "1078" to 0xFF0090D2L, // 공항철도 = 1065
    "1081" to 0xFF003DA5L, // 경강
    "1092" to 0xFFB7C452L, // 우이신설
    "1093" to 0xFF81A914L, // 서해
    "1094" to 0xFF6789CAL, // 신림선
    "1095" to 0xFFB21935L, // 동북선
)

/*
 * ⚠ **`1032` GTX-A 는 공식색을 안 쓴다 — 일부러 그랬다.**
 *
 * 공식 GTX-A 색은 `#9A6292`(자주빛 보라)인데 **5호선 `#996CAC` 와 거의 같은 색**이다
 * (CIEDE2000 ΔE = 6.6 · CIE76 ΔE = 10.8). 이 화면의 기관차 몸통은 22dp 짜리 작은 도형이라
 * 그 차이로는 두 호선이 안 갈린다. 카스(2026-09-07): *"골드색은 김포골드라인 **연보라색은
 * GTX-A** 5호선은 보라"* — **연보라**로 못 박았으므로 밝은 보라 `#A38FE6` 을 쓴다.
 * 5호선과 **ΔE00 = 13.9 · ΔE76 = 18.9**(L\* 52.4 → 64.3 · b\* −27.3 → −41.5)라 밝기와
 * 색상 둘 다로 갈린다. **공식색으로 되돌리지 말 것** — 되돌리면 5호선과 다시 뭉갠다.
 */

/** 모르는 `subwayId` 의 기본색 — 중성 회색(라이트·다크 양쪽에서 보인다). */
internal val COMMUTE_LINE_FALLBACK_ARGB = 0xFF8E8E93L

internal fun lineArgb(subwayId: String): Long = LINE_ARGB[subwayId] ?: COMMUTE_LINE_FALLBACK_ARGB
