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
    /**
     * **앞 2역 · 뒤 2역**의 이름 — 미니 노선에 그대로 적는다(v1.7.14 ⑤).
     * 차례는 **화면에 놓이는 차례**(왼쪽부터)이고 등록역은 빠져 있다:
     * `[왼왼, 왼, (등록역), 오른, 오른오른]` 에서 괄호를 뺀 **넷**이다.
     * 마곡(5호선 하행)이면 `["김포공항", "송정", "발산", "우장산"]`.
     *
     * ⚠ **등록할 때 딱 한 번** 채운다([commuteNeighbors]) — 볼 때마다 부르지 않는다.
     * 못 얻었으면 **빈 목록**이고 그때는 미니 노선이 v1.7.13 처럼 이름 없이 점만 찍는다
     * (옛 저장값도 여기로 떨어진다 — 아래 [decodeCommute] 참고).
     * 끝 역이라 이웃이 모자라면 그 자리는 **빈 문자열**이다(칸은 남기고 글자만 안 적는다).
     */
    val neighbors: List<String> = emptyList(),
)

/* ── 저장 문자열 ─────────────────────────────────────────────────
 *
 * `역명|subwayId|updnLine|이웃4개(쉼표)` 를 `;` 로 이은 **한 줄**. JSON 라이브러리를 새로
 * 넣지 않는다(이 저장소 관례 — `Weather.kt` KDoc). 역명에 `|`·`;`·`,` 가 들어간 실례는
 * 없지만 넣어 두면 줄이 통째로 깨지므로 저장할 때 지운다. 읽기는 **깨진 칸을 조용히 버린다** —
 * 옛 값·손댄 값이 있어도 화면이 안 죽는다.
 *
 * ⚠ **넷째 칸은 v1.7.14 ⑤ 에서 늘었다.** 옛 저장값은 셋뿐이라 그 꼴도 그대로 읽는다:
 *  · v1.7.9~v1.7.13 꼴 `마곡|1005|하행` → 이웃 없음(미니 노선이 이름 없이 뜬다)
 *  · v1.7.14 꼴 `마곡|1005|하행|김포공항,송정,발산,우장산`
 * 셋째 칸(방향)이 빈 **더 옛 꼴**은 v1.7.9 부터 버려 왔고 그대로 둔다.
 */

private fun clean(s: String) = s.replace("|", "").replace(";", "").trim()

private fun cleanName(s: String) = clean(s).replace(",", "")

internal fun encodeCommute(list: List<CommuteStation>): String =
    list.take(COMMUTE_MAX).joinToString(";") { s ->
        val head = "${cleanName(s.name)}|${clean(s.subwayId)}|${clean(s.updnLine)}"
        // 이웃이 없으면 **셋째 칸까지만** 적는다 — 옛 저장값과 글자가 같아 형식이 안 늘어난다.
        if (s.neighbors.isEmpty()) head
        else head + "|" + s.neighbors.joinToString(",") { cleanName(it) }
    }

internal fun decodeCommute(saved: String?): List<CommuteStation> =
    saved.orEmpty().split(";").mapNotNull { part ->
        val f = part.split("|")
        if (f.size !in 3..4) return@mapNotNull null
        val n = f[0].trim(); val id = f[1].trim(); val up = f[2].trim()
        if (n.isBlank() || id.isBlank() || up.isBlank()) return@mapNotNull null
        // 이웃은 **정확히 넷**일 때만 받는다 — 세 칸짜리 옛 값·잘린 값은 빈 목록으로 떨어진다.
        val near = f.getOrNull(3)?.split(",")?.map { it.trim() }?.takeIf { it.size == COMMUTE_NEAR * 2 }
        CommuteStation(n, id, up, near.orEmpty())
    }.distinct().take(COMMUTE_MAX)

/* ── ① 역 이름에 `역` 을 붙여 쳐도 찾아진다 (v1.7.14) ─────────────
 *
 * 카스: *"출퇴근역 **검색에 마곡, 이면 마곡역으로까지 검색**되게 해줘!"*
 *
 * 도착 API 는 역명을 **접미 없이** 받는 경우가 많다 — 실호출(2026-09-07)에서
 * `realtimeStationArrival/마곡` 은 4건, **`.../마곡역` 은 `INFO-200`(0건)** 이었다.
 * 그래서 입력을 그대로 한 번, 안 되면 `역` 을 붙이거나 떼고 **한 번 더** — 최대 2회다.
 */

/**
 * 조회에 써 볼 이름들 — **최대 두 개**(과다 호출 금지). 첫째는 늘 **사용자가 친 그대로**다.
 *
 * · `마곡` → `["마곡", "마곡역"]` · `마곡역` → `["마곡역", "마곡"]`
 * · 빈 값·공백뿐 → **빈 목록**(부르는 쪽이 조회 자체를 안 한다)
 * · `역` 한 글자 → `["역"]`(떼면 빈 이름이라 후보가 하나뿐이다)
 *
 * ⚠ **역 이름을 줄이는 함수가 아니다**(확정 표) — `구로디지털단지` 는 통째로 남는다.
 * ⚠ **`서울역` 을 `서울` 로 바꿔 버리면 안 된다** — 서울역은 이름이 `역` 으로 끝나는
 *   진짜 역이고(1·4호선·경의선·공항철도), `서울` 은 **GTX-A 의 다른 역**이다. 그래서
 *   **떼는 것이 아니라 두 꼴을 차례로 시도**한다 — 원문이 먼저라 서울역은 첫 번에 걸린다.
 */
internal fun stationQueries(input: String): List<String> {
    val n = input.trim()
    if (n.isEmpty()) return emptyList()
    val alt = if (n.endsWith("역")) n.dropLast(1) else n + "역"
    return listOf(n, alt).filter { it.isNotBlank() }.distinct()
}

/**
 * 역 목록 대조용 **꼬리표 뗀 이름**. 서울 열린데이터의 역 목록은 `마곡`·`서울역` 처럼
 * **공식 이름**을 주므로, 사용자가 `마곡역` 을 쳤어도 같은 줄을 찾게 한다.
 *
 * ⚠ 이것도 **조회·대조 전용**이다. 저장·표시는 [CommuteStation.name] 그대로다.
 */
internal fun bareStation(name: String) = name.trim().let {
    if (it.length > 1 && it.endsWith("역")) it.dropLast(1) else it
}

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

/**
 * 칩·설정 목록에 쓰는 **한 줄 이름** — `"5호선 마곡 · 하행"`(v1.7.13b ①).
 *
 * 카스: *"방향을 칩에 적어주면 좋지.."* — v1.7.12 까지는 `"5호선 마곡"` 뿐이라 **같은 역을
 * 방향만 달리 두 개 등록하면 칩 두 개의 글자가 똑같았다**(카스 화면에 `5호선 마곡` 이 나란히
 * 둘 떴다). [CommuteStation.updnLine] 에 이미 방향이 들어 있으니 저장값을 늘릴 것이 없다.
 *
 * ⚠ **역 이름은 줄이지 않는다**(확정 표 — `구로디지털단지` 를 `구로` 로 쓰면 다른 역이다).
 * 글자가 길어지면 칩 줄이 오른쪽으로 밀릴 뿐이고, 그 줄은 v1.7.9 확정대로 **한 줄 · 가로
 * 스크롤**이다(두 줄 접기 금지).
 *
 * ⚠ [CommuteStation.updnLine] 은 **낱말**이다(`상행`·`하행`·`내선`·`외선` — v1.7.9 실측).
 * 빈 값은 [decodeCommute] 가 이미 버리지만, 옛 저장값·손댄 값이 들어와도 **가운뎃점만 빼고**
 * 역 이름은 그대로 남긴다.
 */
internal fun commuteLabel(s: CommuteStation): String =
    "${lineName(s.subwayId)} ${s.name}" +
        if (s.updnLine.isBlank()) "" else " · ${s.updnLine.trim()}"

/**
 * **칩 글자 — 방향을 뺀다**(v1.7.14 ②). 카스: *"출퇴근역 아이콘에 **하행,내선 이런 정보는
 * 안해도** 될꺼같애..그래야 **가로 크기가 줄어들듯**"*.
 *
 * ⚠ **바로 앞 회차(v1.7.13 ⑧)에서 내가 넣은 것을 카스가 되무르는 것이다** — 실수가 아니라
 * 카스의 결정이라 이력을 지우지 않고 남긴다. v1.7.13 이 방향을 넣은 이유는 *"같은 역을
 * 방향만 달리 둘 등록하면 칩 글자가 똑같다"* 였고, 그 이유 자체는 아직 참이다.
 *
 * 그래서 **설정 목록은 [commuteLabel] 그대로 방향을 남긴다** — 거기서는 같은 역 두 줄을
 * 갈라 **지워야** 하므로 글자가 같으면 어느 것을 지우는지 알 수 없다. 칩은 눌러서 펼치면
 * 어느 방향인지 카드 안(종착·위치)이 바로 말해 주고, 칩 줄은 **가로가 자원**이라 뺀다.
 */
internal fun commuteChipLabel(s: CommuteStation): String = "${lineName(s.subwayId)} ${s.name}"

/**
 * 출퇴근 역 줄 **전체 스위치** 저장값 읽기(v1.7.13b ②) — 카스: *"출퇴근역은 전체 끄기 켜기
 * 스위치가 있으면 좋을거 같은데?"*
 *
 * **기본값은 켜짐**(지금까지의 동작 그대로)이고, 저장은 [CommuteStation] 목록과 **같은 저장소**
 * (`theme` prefs, 키 `commute_on`)다. 모르는 값·`null` 은 켜짐 — `MapStyle.of` 와 같은 태도다.
 *
 * ⚠ **끄는 것과 지우는 것은 다르다.** 끄면 `commute_stations` 는 **한 글자도 안 건드린다** —
 * 다시 켜면 등록이 그대로 돌아온다(카스가 "끄기"라 했지 "지우기"라 하지 않았다).
 */
internal fun commuteOnOf(saved: String?): Boolean = saved?.toBooleanStrictOrNull() ?: true

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
    /**
     * 이 방향 열차가 **큰 `FR_CODE` 쪽에서 오나**(v1.7.14 ⑤) — 응답의 `statnFid`/`statnId` 가
     * 말한다([approachFromHigher]). 미니 노선의 **역 차례**를 정하는 데만 쓴다.
     */
    val fromHigher: Boolean = false,
) {
    val label: String get() = "${lineName(subwayId)} · $bound"
    fun toStation(name: String, neighbors: List<String> = emptyList()) =
        CommuteStation(name.trim(), subwayId, updnLine, neighbors)
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
        .map { (k, v) ->
            CommuteOption(
                k.first, k.second, boundOf(v.first().trainLineNm, k.second),
                fromHigher = approachFromHigher(v.first()),
            )
        }
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
 * **앞뒤로 몇 역까지 보여 주나** — v1.7.14 ⑤. 카스: *"그냥 **전후 두단계**로 하자.
 * 마곡역이면 **김포공항-송정-마곡-발산-우장산** 이렇게 표시해주고 **역명까지 표시**해주자!"*
 */
internal const val COMMUTE_NEAR = 2

/**
 * 미니 노선 칸 수 — **점 다섯 개**이고 **등록한 역이 한가운데**([COMMUTE_HERE])다.
 *
 * ## 이력 (되돌리지 말 것 — 카스 결정이다)
 *
 * · v1.7.12 ① : 4칸(`3번째 전역`~등록역). 오른쪽 끝이 등록역이었다.
 * · v1.7.12 ③ : 6칸(`5번째 전역+`~등록역) — *"5칸 전해도 될꺼같은데?"*
 * · **v1.7.14 ⑤ : 5칸 · 등록역이 가운데 · 역명을 적는다.** 여섯 칸에는 이름이 없어
 *   *"몇 번째 전역"* 밖에 못 말했다. 카스가 **전후 2역씩**을 원했으므로 오른쪽 두 칸은
 *   **열차가 가고 나서 지날 역**이다 — 그래서 열차는 0..[COMMUTE_HERE] 에만 선다.
 *
 * ⚠ **여기가 가로 하한이다** — 이름 다섯이 나란히 눕는다. 7 로 올리면 이름이 겹친다
 *   (`CommuteBar` 가 글자를 줄여 막지만 하한 7sp 아래로는 못 간다).
 */
internal const val COMMUTE_SLOTS = COMMUTE_NEAR * 2 + 1

/** 등록한 역이 앉는 칸 = 한가운데. 열차는 이 칸을 **못 넘는다**(넘으면 이미 지나간 열차다). */
internal const val COMMUTE_HERE = COMMUTE_NEAR

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
 * 열차 한 대의 **미니 노선 칸 좌표** `0..[COMMUTE_HERE]`.
 *
 * `2` = 등록한 역 위 · `1` = 전역 · `0` = **2번째 전역 또는 그보다 멀리**.
 *
 * 읽는 순서(먼저 맞는 것이 이긴다):
 *  1. **[arvlCd] `0`(진입)·`1`(도착) → 역 점 위**(v1.7.9 확정 — 이 둘만 `지금 도착`으로 따로 센다).
 *  2. `당역` 이 든 문장 → 역 점 위.
 *  3. `N번째 전역` → `2 − N`. **`7번째 전역` 처럼 칸을 넘으면 가장 먼 칸(0)으로 떨어뜨린다.**
 *  4. 숫자 없는 `전역` → `전` 을 센다: `"전역 도착"`·`"까치산 전역출발"` 한 정거장 전 ·
 *     **`"전전역 출발"` 두 정거장 전**(2026-09-07 5호선 마곡 실화면에서 확인한 꼴이다).
 *  5. [arvlCd] `3`(전역출발)·`4`(전역진입)·`5`(전역도착) → 한 정거장 전. 글자가
 *     `"5분 30초 후"` 처럼 남은 시간만 말할 때 상태코드가 대신 위치를 말해 준다.
 *  6. **그 밖에는 0** — `"8분 후"`·빈 문자열·모르는 글자꼴. 아직 멀리 있다는 뜻이라
 *     역 점 위에 세우는 것보다 안전하다. **절대 예외를 던지지 않는다.**
 *
 * ⚠ **0 은 "정확히 2번째 전역" 이 아니다** — `N ≥ 2` 와 위치를 아예 안 말하는 문장이 다 같이
 * 앉는 **바닥 칸**이다(v1.7.14 ⑤ 지시: *"2역 밖이면 맨 끝 칸"*). v1.7.12~13 은 칸이 여섯이라
 * 왼쪽 끝 라벨에 `+` 를 달아 그 뭉침을 말했는데, 이제 그 자리에 **진짜 역 이름**이 서므로
 * `+` 를 못 붙인다 — 대신 **2역 밖 열차는 그 역 점 위에 선 것이 아니라 그 칸에 모인 것**이다.
 *
 * ⚠ **[COMMUTE_HERE] 오른쪽 칸(뒤 2역)에는 열차가 서지 않는다** — 도착 API 는 *"이 역으로
 * 다가오는 열차"* 만 주기 때문이다. 그 두 칸은 **내가 탈 열차가 이 다음에 갈 역**을 알려 주는
 * 자리다(카스가 원한 *"전후 두단계"*).
 *
 * ⚠ 여기 기관차엔 **열번을 넣지 않는다.** 확정 표의 *"열차 아이콘 = 열번 상자"* 는 실시간
 * **지도**(본선·지선) 규칙인데, 도착 API 응답에는 열번(`btrainNo`)이 오긴 해도 이 칸이 말하는
 * 것은 *"내가 탈 열차가 몇 정거장 앞"* 이라 열번이 정보가 아니다(지도처럼 내 열번을 찾는
 * 화면이 아니다). 넣을 자리도 없다 — 몸통이 25dp 라 4자리가 안 든다.
 */
internal fun commuteSlot(arvlMsg2: String, arvlCd: String): Int {
    val here = COMMUTE_HERE
    if (arvlCd == "0" || arvlCd == "1") return here
    val msg = arvlMsg2.trim()
    if (msg.contains("당역")) return here
    NTH_BEFORE.find(msg)?.let { m ->
        val n = m.groupValues[1].toIntOrNull() ?: return 0
        return (here - n).coerceIn(0, here)
    }
    PREV_STATIONS.find(msg)?.let { return (here - it.groupValues[1].length).coerceIn(0, here) }
    if (arvlCd == "3" || arvlCd == "4" || arvlCd == "5") return here - 1
    return 0
}

/**
 * 열차 한 대를 **한 걸음** 앞으로 옮긴다 — 미니 노선 연속 좌표 `0f..[COMMUTE_HERE]f`(v1.7.13 ①).
 *
 * 카스: *"역으로 다가오는 **열차아이콘이 안움직이는데?**"* — v1.7.12 는 15초 폴링 때마다
 * 칸을 뛰고 그 사이엔 멈춰 있었다. 재료는 이미 응답에 있다: **남은 초**([etaSec] = `barvlDt`,
 * 화면 오른쪽의 `N분 M초`)가 1초씩 준다. 그 값으로 칸 사이를 메우면 **API 를 더 안 부른다.**
 *
 * ## 규칙 — 확정 표 *"열차 이동 = 시간 기반 등속 전진"*(v1.7.5)을 그대로 따른다
 *
 *  ⓐ **앞으로만 간다.** [dtSec] 이 음수면 0 이고, 새 응답이 뒤를 가리켜도 [prev] 를 들고 버틴다.
 *  ⓑ **다음 칸을 안 넘는다.** 예측이 실측을 앞질러 있지도 않은 도착을 그리지 않는다
 *     (`CREEP_MARGIN` 과 같은 취지 · 여기는 **한 칸이 상한**이고 등록역 칸([COMMUTE_HERE])을 못 넘는다).
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
    val last = COMMUTE_HERE.toFloat()                               // 등록역 칸이 상한이다
    if (slot >= COMMUTE_HERE) return last                           // ⓒ 도착 — 역 점 위
    if (prev == null) return slot.toFloat().coerceIn(0f, last)      // 처음 본 열차
    /** 한 칸에 몇 초 — 남은 칸을 남은 초에 간다고 본 **등속** 하나. 하한 1초(0 나눗셈 방지). */
    val secPerSlot = (etaSec.toFloat() / (COMMUTE_HERE - slot)).coerceAtLeast(1f)
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

/* ── ④ 칩 글자색 — 호선 색 위에서 대비를 지킨다 (v1.7.14) ───────
 *
 * 카스: *"마곡을 선택했다면 **5호선 마곡 아이콘을 5호선 색으로** 해줘야지"* — "아이콘"은
 * **칩**이다(방향 글자를 빼라고 한 그 자리). 고른 칩 바탕이 호선 색이 되면 글자색을 흰색으로
 * 못 박을 수 없다: 5호선 보라(`#996CAC`) 위 흰 글자는 **4.12:1** 로 AA(4.5:1)에 못 미치고,
 * 1호선 남색(`#0052A4`) 위 검정 글자는 **2.74:1** 이다. 둘 중 **대비가 큰 쪽**을 고른다.
 */

/** sRGB 채널 하나의 상대휘도 성분(WCAG 2.1). */
private fun lin(c: Int): Double {
    val v = c / 255.0
    return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
}

/** ARGB `Long` 의 **상대휘도**(WCAG 2.1). 알파는 안 본다 — 이 화면 색은 전부 불투명이다. */
internal fun relLuminance(argb: Long): Double =
    0.2126 * lin(((argb shr 16) and 0xFF).toInt()) +
        0.7152 * lin(((argb shr 8) and 0xFF).toInt()) +
        0.0722 * lin((argb and 0xFF).toInt())

/** 두 색의 **대비비**(1.0 ~ 21.0). 보고에 적는 실측값이 이 함수와 같은 산수다. */
internal fun contrastRatio(a: Long, b: Long): Double {
    val la = relLuminance(a)
    val lb = relLuminance(b)
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}

internal const val WHITE_ARGB = 0xFFFFFFFFL
internal const val BLACK_ARGB = 0xFF000000L

/**
 * 호선 색 바탕 위에 **읽히는 글자색** — 흰색과 검정 중 대비가 큰 쪽.
 *
 * 실측(이 함수의 산수 그대로): 1호선 `#0052A4` → 흰 **7.66:1** · 2호선 `#00A84D` → 검정
 * **6.71:1** · 5호선 `#996CAC` → 검정 **5.09:1** · 7호선 `#747F00` → 검정 **4.78:1**.
 * **스물넷 전부 4.5:1 을 넘는다**(`CommuteMiniTest` 가 전수로 잠근다) — 넘지 않는 호선색이
 * 생기면 테스트가 먼저 깨진다.
 */
internal fun chipInkArgb(lineArgb: Long): Long =
    if (contrastRatio(lineArgb, WHITE_ARGB) >= contrastRatio(lineArgb, BLACK_ARGB)) WHITE_ARGB
    else BLACK_ARGB

/* ── ⑤ 앞뒤 2역 이름 (v1.7.14) ───────────────────────────────────
 *
 * 카스: *"마곡역이면 **김포공항-송정-마곡-발산-우장산** 이렇게 표시해주고 역명까지"*.
 *
 * ## 자료를 어디서 얻나 — 실호출로 확인한 것만 적는다(2026-09-07)
 *
 * · **도착 API 만으로는 모자란다.** `realtimeStationArrival/마곡` 한 줄에
 *   `statnId=1005000514` · `statnFid=1005000515` · `statnTid=1005000513` 이 온다 —
 *   **±1 뿐이고 이름이 아니라 코드**다. 2역 앞뒤는 이 값만으로 못 만든다.
 * · **역 목록 API 는 같은 인증키가 그대로 먹는다.** 호스트만 다르다:
 *   `openapi.seoul.go.kr:8088/{키}/json/SearchSTNBySubwayLineInfo/1/999/ / /05호선/`
 *   → 56행, 각 행에 `STATION_NM`(`마곡`)·`FR_CODE`(`514`)·`LINE_NUM`(`05호선`).
 *   **`FR_CODE` 가 노선 위 차례**다(510 방화 … 514 마곡 … 566). 실측으로 확인:
 *   `statnId` 뒤 세 자리(514)가 `FR_CODE` 와 **같은 값**이었다.
 * · 그래서 **역 목록 자산을 번들하지 않는다**(v1.7.9 확정 그대로) — 등록할 때 **한 번**
 *   불러 이웃 넷을 저장값에 담고, 볼 때는 한 번도 안 부른다.
 *
 * ## 지선·갈래를 어떻게 가르나 — `FR_CODE` 한 값이 다 말한다
 *
 * `FR_CODE` 는 `접두 + 번호 [+ "-" + 가지번호]` 다: `514`(5호선 본선) · `234-1`(2호선
 * 신정지선 도림천) · `P550`(5호선 마천지선) · `K314`(경의선). 그래서
 *  · 등록역에 **가지번호가 없으면**(본선) 같은 접두의 **가지번호 없는 줄만** 본다 —
 *    신도림 뒤가 `도림천`(234-1)이 아니라 **`문래`(235)** 가 된다.
 *  · 등록역에 **가지번호가 있으면**(지선) 같은 **큰 번호** 묶음만 본다 — 까치산(234-4)의
 *    앞이 `양천구청`·`신정네거리` 이고 **뒤는 비어 있다**(종점이라 맞다).
 * ⚠ 갈래가 갈리는 역(신도림·성수·강동)은 **본선 쪽**으로 붙는다. 카스가 지선 쪽을 원하면
 *   그때 물어야 하는 자리다(추측으로 바꾸지 말 것).
 */

/** 역 목록 API 한 줄 — 쓰는 필드 둘뿐이다. */
internal data class StationRow(val frCode: String, val name: String)

private val FR_CODE = Regex("^([^0-9]*)(\\d+)(?:-(\\d+))?")

/**
 * `FR_CODE` → `(접두, 번호, 가지번호)`. 못 읽는 꼴은 `(원문, 0, 0)` — **예외를 안 던진다**.
 * 정렬은 이 세 값 순이고, 접두가 다르면 **다른 갈래**라 아예 안 섞는다.
 */
internal fun frKey(frCode: String): Triple<String, Int, Int> {
    val t = frCode.trim()
    val m = FR_CODE.find(t) ?: return Triple(t, 0, 0)
    return Triple(
        m.groupValues[1],
        m.groupValues[2].toIntOrNull() ?: 0,
        m.groupValues[3].toIntOrNull() ?: 0,
    )
}

/**
 * 등록역의 **앞 2역 · 뒤 2역 이름 넷**(화면에 놓이는 차례). 못 찾으면 **빈 목록**이고,
 * 끝 역이라 모자라는 자리는 **빈 문자열**이다.
 *
 * @param rows 그 노선의 역 목록([StationRow]).
 * @param name 등록역 이름 — 사용자가 친 그대로 받고 [bareStation] 으로도 한 번 더 견준다
 *   (`마곡역` 으로 쳐도 `마곡` 줄을 찾는다). **정확일치가 먼저**라 `서울역` 이 `서울`(GTX-A)로
 *   새지 않는다.
 * @param approachFromHigher 열차가 **큰 `FR_CODE` 쪽에서** 온다면 true. 그러면 차례를 뒤집어
 *   **늘 왼쪽에서 열차가 다가오게** 한다(v1.7.5 확정 "앞으로만" 과 화면 방향을 맞춘다).
 *   카스가 든 예(`마곡` 5호선 **하행**)는 false 라 `김포공항-송정-마곡-발산-우장산` 그대로다.
 */
internal fun commuteNeighbors(
    rows: List<StationRow>,
    name: String,
    approachFromHigher: Boolean,
    near: Int = COMMUTE_NEAR,
): List<String> {
    val target = rows.firstOrNull { it.name.trim() == name.trim() }
        ?: rows.firstOrNull { bareStation(it.name) == bareStation(name) }
        ?: return emptyList()
    val tk = frKey(target.frCode)
    // 같은 갈래만 남긴다 — 위 KDoc "지선·갈래를 어떻게 가르나" 참고.
    val group = rows.filter {
        val k = frKey(it.frCode)
        k.first == tk.first && if (tk.third == 0) k.third == 0 else k.second == tk.second
    }.sortedWith(compareBy({ frKey(it.frCode).second }, { frKey(it.frCode).third }))
    val ordered = if (approachFromHigher) group.asReversed() else group
    val i = ordered.indexOfFirst { it.frCode.trim() == target.frCode.trim() }
    if (i < 0) return emptyList()
    return ((i - near)..(i + near)).filter { it != i }
        .map { ordered.getOrNull(it)?.name.orEmpty() }
}

/**
 * `subwayId` → 역 목록 API 의 `LINE_NUM` 낱말. **모르면 `null`** 이고 그때는 이웃을 아예
 * 안 부른다(이름 없는 미니 노선으로 떨어질 뿐 화면은 산다).
 *
 * 값은 2026-09-07 실호출로 **응답에 실제로 있는 낱말**을 확인해 적었다(799행 전수).
 * ⚠ `1095`(동북선)는 **아직 응답에 없다** — 개통 전이라 넣지 않는다([LINE_ARGB] 의
 * 김포골드라인과 같은 태도: 없는 것에 값을 달아 두면 다음 사람이 헷갈린다).
 */
internal val LINE_NUMS = mapOf(
    "1001" to "01호선", "1002" to "02호선", "1003" to "03호선", "1004" to "04호선",
    "1005" to "05호선", "1006" to "06호선", "1007" to "07호선", "1008" to "08호선",
    "1009" to "09호선", "1032" to "GTX-A", "1061" to "경의선", "1063" to "경의선",
    "1065" to "공항철도", "1067" to "경춘선", "1069" to "수인분당선", "1071" to "수인분당선",
    "1075" to "수인분당선", "1077" to "신분당선", "1078" to "공항철도", "1081" to "경강선",
    "1092" to "우이신설경전철", "1093" to "서해선", "1094" to "신림선",
)

internal fun lineNumOf(subwayId: String): String? = LINE_NUMS[subwayId]
