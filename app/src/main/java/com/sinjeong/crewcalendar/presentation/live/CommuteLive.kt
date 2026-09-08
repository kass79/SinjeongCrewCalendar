package com.sinjeong.crewcalendar.presentation.live

import com.sinjeong.crewcalendar.domain.model.DEFAULT_SEG_SEC
import com.sinjeong.crewcalendar.domain.model.TrainMotion
import com.sinjeong.crewcalendar.domain.model.stepMotion

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

/**
 * 등록한 출퇴근 역 하나 = **역 × 호선 × 방향**.
 *
 * ⚠ **[updnLine] 은 `"0"`/`"1"` 이 아니라 낱말이다.** 실시간 **위치**(`realtimePosition`)는
 * 숫자를 주지만 실시간 **도착**(`realtimeStationArrival`)은 `상행`·`하행`(직선 노선) ·
 * `내선`·`외선`(2호선 순환)을 준다 — 2026-09-06 19:51 까치산 실호출로 확인했다.
 * 저장값이므로 **낱말을 그대로** 담고, 위치 응답과 견줄 때 [posDirMatches] 가 번역한다.
 *
 * 같은 이름 다른 노선을 [subwayId] 가 가른다 — **까치산은 2호선(1002)·5호선(1005) 둘**이고
 * 이 설계의 핵심 사례다.
 */
internal data class CommuteStation(
    val name: String,
    val subwayId: String,
    val updnLine: String,
    /**
     * 미니 노선 **다섯 칸의 역 이름** — 앞 2역 · **등록역** · 뒤 2역(v1.7.15 ②).
     * 차례는 **지리 순서 하나로 고정**(`FR_CODE` 오름차순)이고 [COMMUTE_HERE] 가 등록역이다.
     * 마곡이면 방향과 무관하게 늘 `["김포공항","송정","마곡","발산","우장산"]` 이다.
     *
     * ⚠ **v1.7.14 는 이웃 넷만 담고 방향에 따라 차례를 뒤집었다.** 카스가 그것을 물렸다 —
     * *"상행을 고르면 **열차가 반대방향으로 가면 되지**"*. 차례는 고정이고 뒤집는 것은
     * **기관차 머리**([fromHigher])다.
     *
     * ⚠ **등록할 때 딱 한 번** 채운다([commuteStops]) — 볼 때마다 부르지 않는다.
     * 못 얻었으면 **빈 목록**이고 그때는 화면이 *"역을 다시 등록해 주세요"* 라고 말한다:
     * 이름이 없으면 위치 응답의 `statnNm` 을 견줄 상대가 없어 **열차를 아예 못 그린다.**
     * 끝 역이라 칸이 모자라면 그 자리는 **빈 문자열**이다(칸은 남기고 글자만 안 적는다).
     */
    val stops: List<String> = emptyList(),
    /**
     * 이 방향 열차가 **큰 `FR_CODE` 쪽에서 오나**(v1.7.15 ②) — 등록할 때
     * [approachFromHigher] 가 도착 응답의 `statnFid`/`statnId` 로 정한다.
     *
     * [stops] 가 오름차순 고정이므로 이 값이 곧 **화면에서의 진행 방향**이다:
     * `true` = 오른쪽 → 왼쪽(마곡 · 송정방면), `false` = 왼쪽 → 오른쪽(마곡 · 발산방면).
     *
     * ⚠ **방향 낱말에서 유도하면 안 된다** — 실호출로 확인했다(2026-09-07 20:27~28):
     * 5호선 `상행`(방화행)은 `FR_CODE` **내림차순**인데 2호선 `내선`은 **오름차순**이다
     * (신도림 내선 `statnFid=…233 → statnTid=…235`). 낱말과 지리 방향의 관계는 노선마다
     * 다르므로 **응답의 코드**로만 정한다.
     */
    val fromHigher: Boolean = false,
)

/* ── 저장 문자열 ─────────────────────────────────────────────────
 *
 * `역명|subwayId|updnLine|역5개(쉼표)|fromHigher` 를 `;` 로 이은 **한 줄**. JSON 라이브러리를
 * 새로 넣지 않는다(이 저장소 관례 — `Weather.kt` KDoc). 역명에 `|`·`;`·`,` 가 들어간 실례는
 * 없지만 넣어 두면 줄이 통째로 깨지므로 저장할 때 지운다. 읽기는 **깨진 칸을 조용히 버린다** —
 * 옛 값·손댄 값이 있어도 화면이 안 죽는다.
 *
 * ⚠ **v1.7.15 ② 에서 칸이 다섯으로 늘었다. 옛 네 칸 값은 이름을 안 받는다** — 그 꼴의 넷째
 * 칸은 **방향에 따라 뒤집힌 이웃 넷**이라 지금 규칙(지리 오름차순 고정)으로 읽으면 상행 역이
 * 거꾸로 그려진다. 조용히 틀리느니 **다시 등록하게** 두는 쪽을 골랐다:
 *  · v1.7.9~v1.7.13 꼴 `마곡|1005|하행`                         → 이름 없음
 *  · v1.7.14 꼴 `마곡|1005|하행|김포공항,송정,발산,우장산`        → 이름 없음(넷이라 안 받는다)
 *  · v1.7.15 꼴 `마곡|1005|상행|김포공항,송정,마곡,발산,우장산|1` → 그대로 읽는다
 * 셋째 칸(방향)이 빈 **더 옛 꼴**은 v1.7.9 부터 버려 왔고 그대로 둔다.
 */

private fun clean(s: String) = s.replace("|", "").replace(";", "").trim()

private fun cleanName(s: String) = clean(s).replace(",", "")

internal fun encodeCommute(list: List<CommuteStation>): String =
    list.take(COMMUTE_MAX).joinToString(";") { s ->
        val head = "${cleanName(s.name)}|${clean(s.subwayId)}|${clean(s.updnLine)}"
        // 이름을 못 얻었으면 **셋째 칸까지만** 적는다 — 옛 저장값과 글자가 같아 형식이 안 는다.
        if (s.stops.size != COMMUTE_SLOTS) head
        else head + "|" + s.stops.joinToString(",") { cleanName(it) } +
            "|" + if (s.fromHigher) "1" else "0"
    }

internal fun decodeCommute(saved: String?): List<CommuteStation> =
    saved.orEmpty().split(";").mapNotNull { part ->
        val f = part.split("|")
        if (f.size < 3) return@mapNotNull null
        val n = f[0].trim(); val id = f[1].trim(); val up = f[2].trim()
        if (n.isBlank() || id.isBlank() || up.isBlank()) return@mapNotNull null
        // 이름은 **다섯 칸 꼴일 때만** 받는다 — 옛 세 칸·네 칸 값은 빈 목록으로 떨어진다.
        val stops = if (f.size < 5) null
        else f[3].split(",").map { it.trim() }.takeIf { it.size == COMMUTE_SLOTS }
        CommuteStation(n, id, up, stops.orEmpty(), fromHigher = f.getOrNull(4)?.trim() == "1")
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
     * 이 방향 열차가 **큰 `FR_CODE` 쪽에서 오나** — 응답의 `statnFid`/`statnId` 가 말한다
     * ([approachFromHigher]). v1.7.14 는 이 값으로 **역 차례를 뒤집었고**, v1.7.15 ② 부터는
     * 차례를 고정한 채 **기관차 진행 방향**을 정한다([CommuteStation.fromHigher]).
     */
    val fromHigher: Boolean = false,
) {
    val label: String get() = "${lineName(subwayId)} · $bound"
    fun toStation(name: String, stops: List<String> = emptyList()) =
        CommuteStation(name.trim(), subwayId, updnLine, stops, fromHigher)
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

/* ── 미니 노선 한 줄 (v1.7.12 ①) ─────────────────────────────
 *
 * 카스: *"세로칸은 최소화 해서 일자로 보여주면 좋지"* → 다가오는 열차를 글자 목록으로 쌓지 않고
 * **가로 미니 노선 한 줄** 위에 기관차를 세운다(시안 "제안 A").
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
 * · **v1.7.14 ⑤ : 5칸 · 등록역이 가운데 · 역명을 적는다.**
 * · **v1.7.15 ① : 다섯 칸 전부에 열차가 설 수 있다.** 도착 예보를 버리고 실시간 **위치**로
 *   갔더니 등록역을 지나간 열차도 응답에 있다 — 오른쪽 두 칸이 이제 빈 자리가 아니다.
 *
 * ⚠ **여기가 가로 하한이다** — 이름 다섯이 나란히 눕는다. 7 로 올리면 이름이 겹친다
 *   (`CommuteBar` 가 글자를 줄여 막지만 하한 7sp 아래로는 못 간다).
 */
internal const val COMMUTE_SLOTS = COMMUTE_NEAR * 2 + 1

/** 등록한 역이 앉는 칸 = 한가운데. [CommuteStation.stops] 의 이 자리가 등록한 역이다. */
internal const val COMMUTE_HERE = COMMUTE_NEAR

/* ── ① 보는 화면은 실시간 **위치** 다 (v1.7.15) ─────────────────
 *
 * 카스: *"출근역 살펴보니까 **실시간 지하철위치가 아닌거 같은데?**"* → 사실대로 설명하자
 * *"난 실시간 위치를 원하지"*.
 *
 * ## v1.7.14 까지 무엇이 거짓이었나
 *
 * 도착 예보(`realtimeStationArrival`)에는 **위치 필드가 없다.** 승강장 전광판 문장
 * (`arvlMsg2` = `"3번째 전역"`·`"8분 후"`)을 정규식으로 읽어 **추정**했고, 문장이 위치를
 * 말하지 않으면 맨 왼쪽 칸에 놓았다. 게다가 칸이 앞뒤 2역으로 줄면서 `coerceIn(0, here)` 가
 * **5역 밖 열차까지 맨 왼쪽 칸에 밀어 넣어** 두 역 앞처럼 보였다.
 *
 * ## 지금 — 지도와 **같은 자료**를 본다
 *
 * `realtimePosition/{호선명}`([BranchLive.positionsOfLine])이 주는 `statnNm`(지금 역)을
 * 다섯 칸의 역 이름과 **글자로 견줘** 그 칸에 세운다. **어느 칸에도 없으면 안 그린다** —
 * 그게 이번에 고치는 거짓말이다. `"몇 분 후"` 는 위치 API 에 없으므로 사라지고
 * ([sttusText] 의 상태 낱말이 대신한다), 위치 추정 함수 다섯(`commuteSlot`·`commuteAdvance`·
 * `commuteApproaching`·`commuteAtStation`·`positionText`)은 **지웠다** — 남겨 두면 다시 쓴다.
 *
 * ## 실호출 근거 (2026-09-07 20:27~28 · 같은 순간 두 API 를 나란히 불렀다)
 *
 * ```
 * realtimeStationArrival/마곡  : updnLine "상행" · btrainNo 5656 · statnFid 1005000515(발산)
 * realtimePosition/5호선       : trainNo  5656  · updnLine "0"   · statnNm 발산 · trainSttus 1
 * realtimeStationArrival/마곡  : updnLine "하행" · btrainNo 5703 · statnFid 1005000513(송정)
 * realtimePosition/5호선       : trainNo  5703  · updnLine "1"   · statnNm 김포공항 · trainSttus 1
 * realtimeStationArrival/신도림: updnLine "내선" · btrainNo 8414 / 6416
 * realtimePosition/2호선       : trainNo  8414 / 6416 · updnLine "0"
 * realtimeStationArrival/신도림: updnLine "외선" · btrainNo 4439 / 7443
 * realtimePosition/2호선       : trainNo  4439 / 7443 · updnLine "1"
 * ```
 *
 * **같은 열번을 두 응답에서 맞대 본 여섯 대에 예외가 0건**이다 →
 * **위치 `"0"` = 도착 `상행`·`내선` / 위치 `"1"` = 도착 `하행`·`외선`.**
 */

/**
 * 위치 응답의 `updnLine`(**`"0"`/`"1"` 숫자**)이 저장된 **낱말**과 같은 방향인가.
 *
 * ⚠ 두 API 가 같은 필드 이름에 **다른 값 체계**를 쓴다 — 위 실호출 표가 근거다.
 * ⚠ **모르는 낱말이면 false** 다(글자가 그대로 같을 때만 통과). 방향을 모르는 채로 그리느니
 *   **안 그리는 쪽**이 맞다 — 이번 회차가 고치는 것이 바로 "모르면 아무 데나 놓기"다.
 */
internal fun posDirMatches(posUpdnLine: String, savedWord: String): Boolean {
    val p = posUpdnLine.trim()
    return when (savedWord.trim()) {
        "상행", "내선" -> p == "0"
        "하행", "외선" -> p == "1"
        else -> p.isNotEmpty() && p == savedWord.trim()
    }
}

/**
 * 역 이름 대조용 **꼬리표 뗀 꼴**. 위치 응답은 괄호 별칭을 달고 오는데
 * (`굽은다리(강동구민회관앞)`·`오목교(목동운동장앞)`·`신정(은행정)`) 역 목록 API 의
 * `STATION_NM` 은 **괄호 없이** 온다(2026-09-07 `SearchSTNBySubwayLineInfo/05호선` 56행 전수).
 *
 * ⚠ **`역` 은 안 뗀다** — 두 응답이 같은 표기 세계라 뗄 이유가 없고, 떼면 `서울역` 이
 * `서울`(GTX-A 의 다른 역)로 샌다([stationQueries] KDoc 의 그 함정이다).
 */
internal fun normStop(name: String) = name.substringBefore("(").trim()

/** 종착역명(`성수종착`·`신도림지선`·`방화`) → 화면에 적는 `방화행`. 빈 값이면 빈 문자열. */
internal fun destText(statnTnm: String): String {
    var s = normStop(statnTnm)
    for (tail in listOf("종착", "지선", "행")) if (s.length > tail.length && s.endsWith(tail)) {
        s = s.dropLast(tail.length)
    }
    return if (s.isBlank()) "" else "${s}행"
}

/**
 * `trainSttus` → **짧은 상태 낱말**. 값은 본선 지도([BranchLive.mainTrains])와 **같은 표**다 —
 * 같은 자료를 두 화면이 다른 말로 부르면 안 된다.
 * `0` 진입 · `1` 도착 · `2` 출발 · `3` 전역출발(= 두 역 사이) · 그 밖은 모름.
 */
internal fun sttusText(sttus: String) = when (sttus.trim()) {
    "0" -> "진입"; "1" -> "도착"; "2" -> "출발"; "3" -> "접근 중"; else -> "운행 중"
}

/**
 * 역 점 **주변의 미세 위치**(칸 단위) — 값은 `BranchLive.posOf` 와 **같은 표**다.
 * 부호는 **진행 방향**을 따라간다: 진입은 그 역 **앞**, 출발은 **뒤**, 전역출발은 두 역 사이.
 *
 * @param forward 화면에서 **왼쪽 → 오른쪽**으로 가면 true(= [CommuteStation.fromHigher] 의 반대).
 */
internal fun commuteOffset(sttus: String, forward: Boolean): Float {
    val base = when (sttus.trim()) {
        "0" -> -0.15f      // 진입 — 다음 역 바로 앞
        "1" -> 0f          // 도착 — 역 위
        "2" -> 0.15f       // 출발 — 역을 막 벗어남
        "3" -> -0.6f       // 전역 출발 — 두 역 사이
        else -> 0f
    }
    return base * (if (forward) 1f else -1f)
}

/** 미니 노선에 그릴 열차 한 대. [pos] 는 칸 좌표 `0f..COMMUTE_SLOTS-1f`. */
internal data class CommuteTrain(
    val trainNo: String,
    val slot: Int,
    val pos: Float,
    val status: String,
    val dest: String,
    /**
     * **역에 섰나**(v1.7.16 ②) — `trainSttus` 가 `0`(진입)·`1`(도착)이면 참이고,
     * 그동안 [stepCommute] 는 앞으로 **한 칸도 안 기어간다**(확정 표 v1.7.5 규칙 ⓒ).
     */
    val holding: Boolean = false,
    /** 응답의 `lstcarAt` 이 `"1"` — **막차**(v1.7.16 ⑥). */
    val lastCar: Boolean = false,
)

/**
 * 위치 응답 → **다섯 칸 위에 세울 열차들**(v1.7.15 ①).
 *
 * 거르는 순서: ① 같은 호선 ② 같은 방향([posDirMatches]) ③ `statnNm` 이 **다섯 칸 이름 중
 * 하나**([normStop] 로 견준다). ③ 을 못 넘으면 **그 열차는 아예 안 그린다** — 맨 왼쪽 칸에
 * 밀어 넣지 않는다(v1.7.14 까지의 거짓 표시).
 *
 * 이름을 못 얻은 등록값([CommuteStation.stops] 이 비었거나 다섯이 아님)은 견줄 상대가 없어
 * **빈 목록**이다 — 화면이 *"역을 다시 등록해 주세요"* 로 안내한다.
 *
 * 순수 함수 — `CommuteMiniTest` 가 잠근다.
 */
internal fun commuteTrains(rows: List<PositionRow>, s: CommuteStation): List<CommuteTrain> {
    if (s.stops.size != COMMUTE_SLOTS) return emptyList()
    val keys = s.stops.map { normStop(it) }
    val forward = !s.fromHigher
    val lastSlot = (COMMUTE_SLOTS - 1).toFloat()
    return rows.asSequence()
        .filter { it.subwayId.trim() == s.subwayId.trim() }
        .filter { posDirMatches(it.updnLine, s.updnLine) }
        .mapNotNull { r ->
            val here = normStop(r.statnNm)
            val slot = keys.indexOfFirst { it.isNotBlank() && it == here }
            if (slot < 0) return@mapNotNull null
            CommuteTrain(
                trainNo = r.trainNo,
                slot = slot,
                pos = (slot + commuteOffset(r.trainSttus, forward)).coerceIn(0f, lastSlot),
                status = sttusText(r.trainSttus),
                dest = destText(r.statnTnm),
                holding = r.trainSttus.trim() in AT_STOP_STTUS,
                lastCar = isLastCar(r.lstcarAt),
            )
        }
        .distinctBy { it.trainNo }
        .toList()
}

/** 역에 선 상태 — `0` 진입 · `1` 도착. [CommuteTrain.holding] 의 잣대다. */
private val AT_STOP_STTUS = setOf("0", "1")

/* ── ② 1초 보간 — **지도가 쓰는 그 함수**를 그대로 쓴다 (v1.7.16) ──
 *
 * 카스: *"부드럽게 보이는것으로 하고"*. v1.7.15 ① 이 실시간 위치로 갈아타면서 뺐던
 * 1초 눈금을 되살린다. 규칙은 **새로 안 만든다** — 확정 표 v1.7.5 *"열차 이동 = 시간 기반
 * 등속 전진"* 의 `stepMotion` 한 곳이 그대로 ⓐⓑⓒ 를 지킨다:
 *
 *  ⓐ **앞으로만** 간다 — 목표가 뒤면 버린다(`dir * (goal − creep) <= 0f` 가지).
 *  ⓑ **다음 칸을 안 넘는다** — 예측은 `nextStop − CREEP_MARGIN`(0.95 지점)에서 멈춘다.
 *  ⓒ **도착·진입이면 선다** — [CommuteTrain.holding] 이면 예측을 아예 안 돌린다.
 *
 * ## ⚠ 여기는 **시간표가 없다** — 순수 등속 가정이다
 *
 * 본선 지도와 지선 카드의 보간은 속도를 **그 운행·그 구간의 시간표**에서 얻는다
 * (`Line2Timetable.segmentSeconds` · `BranchLine.SEG_UP/SEG_DN`). 출퇴근 역은 **어느 호선이든
 * 등록될 수 있고 자산은 2호선 시간표뿐**이라 그 재료가 없다 — 그래서 속도는
 * [DEFAULT_SEG_SEC](110초) **하나**이고, 이것은 측정이 아니라 **가정**이다.
 *
 * 그 가정이 감당할 만한 이유: ① 로 실측이 **3초마다** 오므로 보간이 메우는 구간이 3초뿐이다
 * (v1.7.13 의 15초에서 5분의 1로 줄었다). 3초 × 1/110 = **한 칸의 2.7%** 라, 가정이 실제와
 * 두 배 틀려도 화면은 한 칸의 5% 안에서 움직이고 다음 실측이 곧바로 ⓐ 로 끌어당긴다.
 * ⚠ 그래도 **없는 값을 지어내는 것**은 맞다 — 110초를 노선별로 바꾸고 싶으면 그때는
 * 그 호선 시간표를 받아야 한다(카스에게 물을 자리).
 */

/**
 * 한 걸음. [prev] 가 없으면 목표 자리에서 시작한다(처음 본 열차가 훅 미끄러지지 않는다).
 *
 * @param forward 화면에서 **왼쪽 → 오른쪽**으로 가나(= `!CommuteStation.fromHigher`).
 *   `stepMotion` 의 `inner` 자리에 그대로 넣는다 — 둘 다 "좌표가 커지는 쪽"이라는 같은 뜻이다.
 */
internal fun stepCommute(
    prev: TrainMotion?, t: CommuteTrain, forward: Boolean, nowMs: Long,
): TrainMotion {
    val m = stepMotion(prev, t.pos, t.holding, forward, DEFAULT_SEG_SEC, nowMs)
    // 다섯 칸을 벗어나면 안 그리는 것이 규칙이라([commuteTrains]) 좌표도 칸 안에 가둔다.
    // ⚠ `TrainMotion.folded` 는 43역 순환용이라 여기서는 못 쓴다 — 5칸은 순환이 아니다.
    val cap = m.pos.coerceIn(0f, (COMMUTE_SLOTS - 1).toFloat())
    return if (cap == m.pos) m else m.copy(pos = cap)
}

/**
 * 오른쪽 **첫 줄**(굵은 글자) — `도착` · 막차면 `도착 · 막차`(v1.7.16 ⑥).
 *
 * ## 왜 막차가 여기인가
 *
 * 확정 표 *"열차 아이콘 = 열번 상자 · 열번을 아이콘 밖 배지로 빼지 말 것"* 이라 기관차
 * 몸통에는 못 적고, 지붕 위 행선판은 [LOCO_BOARD_H] 만큼 위로 더 먹어 **카드 밖으로 나간다**
 * (칸 위 여유가 실측 1.2dp 뿐이다 — `CommuteBar.LOCO_MIN_H` KDoc). 남는 자리는 이 두 줄이고,
 * 그중 **굵은 첫 줄**이 눈에 먼저 든다. 세로도 한 픽셀 안 는다.
 *
 * ⚠ **다가오는 한 대만** 말한다([commuteLead]) — 그 열차가 곧 탈 열차다. 다섯 칸의 다른
 * 열차가 막차인 경우는 안 적힌다(카스에게 물을 자리).
 */
internal fun commuteStatusLine(t: CommuteTrain): String =
    t.status + if (t.lastCar) " · 막차" else ""

/**
 * 오른쪽 **둘째 줄** — `방화행 · 20:54:27`. 뒤 토막이 **기준 시각**(v1.7.16 ③)이다.
 *
 * 이 카드에는 헤더가 없어 폰 시계도 없었다 — 그래서 **통신이 끊겨도 화면이 살아 있어 보였다.**
 * 여기에 서버의 `recptnDt` 를 놓으면 값이 멎는 것이 곧 신호다. 줄을 늘리지 않고 행선 뒤에
 * 붙이는 이유는 카드 높이가 카스가 두 번 깎아 낸 자리이기 때문이다(v1.7.13 ③ · v1.7.14 ③).
 */
internal fun commuteDestLine(dest: String, recptnDt: String): String =
    listOf(dest, recptnClock(recptnDt)).filter { it.isNotBlank() }.joinToString(" · ")

/**
 * 오른쪽 두 줄이 말할 **한 대** — 등록역으로 **다가오는 쪽**에서 가장 가까운 열차.
 * 지나간 열차(진행 방향 뒤쪽 칸)는 안 고른다. 없으면 `null` 이고 화면은 빈 상태 문구를 쓴다.
 */
internal fun commuteLead(trains: List<CommuteTrain>, fromHigher: Boolean): CommuteTrain? =
    trains.filter { if (fromHigher) it.slot >= COMMUTE_HERE else it.slot <= COMMUTE_HERE }
        .minByOrNull { kotlin.math.abs(it.slot - COMMUTE_HERE) }

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
 * 미니 노선 **다섯 칸의 이름** — 앞 2역 · **등록역** · 뒤 2역. 못 찾으면 **빈 목록**이고,
 * 끝 역이라 모자라는 자리는 **빈 문자열**이다.
 *
 * ## ② 차례는 **지리 순서 하나로 고정**이다 (v1.7.15)
 *
 * 카스: *"왼쪽에서 오는게 편할듯"* → 그러나 같은 대화에서 *"상행을 고르면 **열차가 반대방향으로
 * 가면 되지**"* · *"마곡역 **송정방면**으로 지정했을때 **열차가 반대방향으로 움직여야지**?"*.
 *
 * v1.7.14 는 `approachFromHigher` 로 **역 차례를 뒤집어** 열차가 늘 왼쪽에서 오게 했다.
 * 그러면 같은 역이 방향에 따라 좌우가 뒤바뀌어 지도(늘 같은 지리 배치)와 읽는 법이 달라진다.
 * 이제 차례는 **`FR_CODE` 오름차순 하나**이고, 뒤집는 것은 **기관차 머리와 몸**이다
 * ([CommuteStation.fromHigher] → `headingFor`/`locoFlip`, 본선 지도가 이미 쓰는 방식).
 *
 * ⚠ **오름차순을 고른 근거**: `FR_CODE` 가 곧 노선 위 차례이고(510 방화 … 514 마곡 … 558
 * 하남검단산), 카스가 든 예 `김포공항-송정-마곡-발산-우장산` 이 정확히 그 오름차순이다.
 *
 * @param rows 그 노선의 역 목록([StationRow]).
 * @param name 등록역 이름 — 사용자가 친 그대로 받고 [bareStation] 으로도 한 번 더 견준다
 *   (`마곡역` 으로 쳐도 `마곡` 줄을 찾는다). **정확일치가 먼저**라 `서울역` 이 `서울`(GTX-A)로
 *   새지 않는다.
 */
internal fun commuteStops(
    rows: List<StationRow>,
    name: String,
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
    val i = group.indexOfFirst { it.frCode.trim() == target.frCode.trim() }
    if (i < 0) return emptyList()
    return ((i - near)..(i + near)).map { group.getOrNull(it)?.name.orEmpty() }
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
