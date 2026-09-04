package com.sinjeong.crewcalendar.presentation.live

import android.util.Log
import com.sinjeong.crewcalendar.domain.model.Line2Stations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/*
 * 2호선 신정지선 실시간 열차 위치 — **편승지키미(kass79/SinjeongShuttle2) 코드 이식**(v1.6.43).
 *
 * 원본: `data/SubwayRepository.kt` + `domain/BranchLine.kt`. 편승지키미 앱을 실행하는 게 아니라
 * 로직을 통째로 옮겼다 — 사용자 폰에 편승지키미가 없어도 똑같이 동작한다.
 *
 * 이식하며 바꾼 것은 **의존성뿐**이다. 원본은 OkHttp + Gson을 썼지만 이 저장소는 새 의존성을
 * 안 넣는 게 규칙이라([Weather] 참고) HttpURLConnection + 정규식 파싱으로 갈아끼웠다.
 * 응답이 중첩 없는 평평한 객체 배열이라 [Weather]가 쓰는 것과 같은 정규식으로 충분하다.
 * (org.json 은 유닛테스트에서 `RuntimeException: Stub!` 이라 이 저장소에선 금지 — Weather.kt KDoc)
 *
 * 이식하며 **뺀 것**(원본에서 이미 죽어 있거나 이 앱에 쓸 데가 없는 것):
 *  · 예비망 프록시 + `PositionCalculator` — 자체 키 5개가 다 소진돼야 도는 경로. 캘린더는
 *    상세시트를 열었을 때만 호출해 원본보다 훨씬 덜 쓴다.
 *  · `ghostAt`(원본에서 호출부 0건) · `ensureTerminusDwellers`(항등함수) · `debugDump`(디버그 화면)
 *  · `DelayMonitor`/`Timetable` — 지도 그리기에 안 쓰인다(원본 `LineMapCard`의 `delay` 파라미터도
 *    본문에서 한 번도 안 읽혔다).
 */

/** 화면 왼쪽부터 까치산(0) → 신정네거리(1) → 양천구청(2) → 도림천(3) → 신도림(4) */
internal object BranchLine {
    val stations = listOf("까치산", "신정네거리", "양천구청", "도림천", "신도림")

    // 구간별 실측 주행시간(초). 인덱스 i = 역 i ↔ 역 i+1.
    // ⚠ 이 값이 화면 이동 속도를 정한다 — 실차 감각과 어긋나면 여기만 손보면 된다(보정 손잡이).
    val SEG_UP = intArrayOf(100, 130, 123, 132)   // 신도림 방면(상행)
    val SEG_DN = intArrayOf(100, 130, 123, 110)   // 까치산 방면(하행)

    const val SUBWAY_ID_LINE2 = "1002"

    /**
     * 역 [at]의 상행 도착까지 남은 초(x)를 0~[at] 위치로 역변환.
     *
     * v1.6.70에서 양천구청 전용이던 것을 역 인덱스로 일반화했다. **일반형을 그대로 둔다** —
     * 지금 호출되는 [at]은 2(양천구청)뿐이지만, 신정네거리(1) 호출을 되살리는 게 두 줄이다
     * (껐다 켜는 이유는 `BranchLive.loadFromSeoulApi` 주석).
     */
    fun posFromStationSec(x: Float, at: Int, toSindorim: Boolean): Float {
        val seg = if (toSindorim) SEG_UP else SEG_DN
        var remain = x
        var p = at.toFloat()
        while (remain > 0f && p > 0f) {
            val idx = (p - 0.001f).toInt().coerceIn(0, 3)
            val segSec = seg[idx].toFloat()
            val here = (p - idx) * segSec
            if (remain <= here) { p -= remain / segSec; remain = 0f }
            else { remain -= here; p = idx.toFloat() }
        }
        return p.coerceIn(0f, at.toFloat())
    }
}

/** 노선도 위 열차 1개. position 0.0(까치산) ~ 4.0(신도림) 연속 좌표 */
internal data class TrainMark(
    val trainNo: String,
    val toSindorim: Boolean,
    val position: Float,
    val statusText: String,
)

/** 본선에서 신도림으로 들어오는 입고 열차 */
internal data class InboundTrain(val trainNo: String, val etaSec: Int)

/** 실시간 위치 1건 (realtimePosition 스키마 중 쓰는 필드만) */
internal data class PositionRow(
    val subwayId: String,
    val statnNm: String,     // 현재 역
    val trainNo: String,
    val updnLine: String,
    val statnTnm: String,    // 종착역명 ("신도림지선"·"까치산종착" 등 변형이 온다)
    val trainSttus: String,  // 0진입 1도착 2출발 3전역출발
)

/**
 * 실시간 도착 1건 (realtimeStationArrival 스키마 중 쓰는 필드만).
 *
 * [arvlCd] 0진입 1도착 2출발 3전역출발 4전역진입 5전역도착 99운행중 — **0·1·2는 그 역에 이미
 * 닿았거나 지나간 열차**라 접근 정보가 없다. 융합에서 왜 그걸 버려야 하는지는 [refineWithArrivals].
 */
internal data class ArrivalRow(
    val trainNo: String, val destName: String, val etaSec: Int, val arvlCd: String = "",
)

/**
 * **본선(순환선) 열차** 한 대 (v1.6.84). 지선 [TrainMark] 와 좌표계가 달라 따로 둔다 —
 * 지선은 0~4의 연속 좌표지만 본선은 43역 순환이라 `역 인덱스 + 미세 오프셋`이다.
 */
internal data class MainTrainMark(
    val trainNo: String,
    /** [Line2Stations.MAIN] 에서의 자리 0..42 */
    val stationIdx: Int,
    /** true = 내선(시계 · 역 순서대로) / false = 외선(반시계) */
    val inner: Boolean,
    /**
     * 역 기준 미세 위치(−0.6 ~ +0.6) — 진입/도착/출발/전역출발.
     *
     * ⚠ **진행 방향으로 부호가 붙어 있다** — 내선(인덱스 증가)은 `전역출발 = −0.6`,
     * 외선(인덱스 감소)은 `+0.6` 이다. 그래서 `stationIdx + offset` 은 방향과 무관하게
     * "지나온 역과 다음 역 사이 어디쯤"을 바로 가리킨다.
     */
    val offset: Float,
    val statusText: String,
    /** 종착역명 원본(꼬리 포함) — 툴팁에 그대로 보여 준다 */
    val destName: String,
    /** API 가 준 현재 역명(정규화 후) — 시간표 대조용. [statusText] 는 상태 낱말이 붙어 못 쓴다 */
    val statnNm: String = "",
    /** API 원본 상태 코드 `0진입 1도착 2출발 3전역출발` — 시간표의 어느 시각에 견줄지 고른다 */
    val trainSttus: String = "",
)

/** 화면 한 벌 */
internal data class Snapshot(
    val trains: List<TrainMark> = emptyList(),
    /** 본선 열차 — **같은 위치 스냅샷에서 걸러낸 것**이라 API 호출이 늘지 않는다 */
    val mainTrains: List<MainTrainMark> = emptyList(),
    val inbound: List<InboundTrain> = emptyList(),
    val fetchedAtMillis: Long = 0L,
    val error: String? = null,
)

private const val TAG = "BranchLive"

/**
 * 서울 열린데이터광장 실시간 지하철 API 클라이언트.
 *
 * 스냅샷 1회 = **API 2회**(2호선 전체 위치 1 + 양천구청 도착 1).
 * 도착정보는 양천구청 접근 중인 상행 열차의 위치를 초 단위로 정밀화하는 데만 쓴다.
 */
internal object BranchLive {

    /**
     * 인증키 — **편승지키미가 쓰던 것을 그대로 재사용**(사용자 승인 완료).
     * 무료 키는 일 1,000회 한도라 소진되면 다음 키로 자동 로테이션한다.
     * 모자라면 data.seoul.go.kr에서 더 발급받아 아래에 줄만 추가하면 된다.
     *
     * 공개 저장소에 코드로 박는 방식은 [Weather]의 기상청 `SERVICE_KEY`와 같은 패턴이다
     * (무료·읽기전용·개인정보 없음 — 사용자 확인 후 채택된 이 저장소의 기존 관례).
     */
    private val API_KEYS = listOf(
        "6b566451616b617334384f5669746f",
        "45704844746b61733831656d434162",
        "416a6775766b6173333545706a6863",
        "7658747a4e6b617337344261674f67",
        "68436e55736b61733636524f4d4d6c",
        "6e624e7a7a6b617338347179415373",   // v1.6.70 추가 — 하루 한도 5000 → 6000회
        "556359626a6b61733932635a505845",   // 2026-09-04 사용자 제공 — 6000 → 7000회 (역별 시간표 조회 스크립트도 이 키 사용)
    )

    // ⚠ HTTPS(443)가 안 열려 있다 — 이 호스트만 cleartext 허용(res/xml/network_security_config.xml).
    private const val BASE = "http://swopenapi.seoul.go.kr/api/subway"

    /**
     * 갱신 주기 — **적응형**(v1.6.70, 값은 v1.6.72에서 15/5 → **10/4초**).
     * 평소 10초, 편승 열차가 양천구청으로 다가오는 동안만 4초. 판정은 [approachingYangcheon].
     * 한도 산정은 `docs/project-notes.md` **v1.6.72 절**(실이용자 50명 전제 — 282명이 아니다).
     *
     * ⚠ **[BranchLiveMap]의 폴링 눈금(2초)과 맞물려 있다.** 이 상수는 "중복 호출을 흡수하는
     * 하한"이라 실제 호출 시각은 *이 값 이상이 되는 첫 폴링 눈금*이다. 그래서 눈금이
     * 두 값의 **최대공약수**여야 한다 — `gcd(10, 4) = 2`초. 한쪽만 바꾸면 조용히 어긋난다:
     * 종전 5초 눈금에 4초를 넣으면 첫 눈금이 5초라 **실제 간격이 5초로 반올림**된다.
     */
    private const val IDLE_INTERVAL_MS = 10_000L
    private const val NEAR_INTERVAL_MS = 4_000L

    /**
     * 주기 판정에 주는 **여유** — 눈금의 절반. 없으면 **경계에서 동전 던지기가 된다.**
     *
     * 딱 맞아떨어지는 눈금(주기 10초 = 5번째 눈금)의 도착 시각은 `예정시각 + 지터`인데,
     * `lastFetchAt`도 그 직전 fetch 눈금의 지터를 품고 있다. 두 지터의 대소에 따라 경계 눈금이
     * 통과하기도(10.0초) 밀리기도(12.0초) 한다 — v1.6.72 실측에서 **10.03~12.10초로 갈렸다.**
     *
     * 여유가 `0 < slack < 눈금`이면 판정이 결정적이 된다: 한 눈금 이른 자리는 `주기 − 2초`라
     * 여전히 걸리고, 제 눈금은 지터가 ±1초 안이면 항상 통과한다. 절반(1초)이 양쪽 여백이
     * 가장 넓다. **눈금을 바꾸면 이 값도 같이 봐야 한다.**
     */
    internal const val TICK_SLACK_MS = 1_000L
    private const val STALE_KEEP_MS = 120_000L    // 실패 시 직전 성공 데이터 유지 한도

    /**
     * 회차 홀드 상한. 안전망이지 정상 경로가 아니다 — 회차가 끝나면 **복귀 열차(+5/+1)가** 걷어낸다.
     * 2026-08-23 20:45~21:20 실측 6회차의 API 실종 구간: 신도림 2:42 / 4:13 / 4:59,
     * 까치산 5:14 / 6:20 / **7:36**(최대). 종전 8분은 최대치와 24초밖에 안 떨어져 있었다.
     * 12분 = 실무 회차 상한 8분 + 관측된 API 보고 지연(양끝 1~3분). 더 늘리면 API가 통째로
     * 조용할 때 이미 떠난 열차가 종착에 눌어붙는다.
     *
     * 2026-08-25 18:36 재확인(30초 폴링): `5651`이 18:36:20 신도림 도착을 마지막으로 위치 API에서
     * 사라졌다 — 실종 구간의 존재와 성격은 v1.6.56 관측 그대로다.
     */
    private const val TURN_HOLD_MS = 12 * 60_000L

    @Volatile private var keyIdx = 0
    @Volatile private var quotaBlockedUntil = 0L
    @Volatile private var lastSnapshot: Snapshot? = null
    @Volatile private var lastGood: Snapshot? = null
    @Volatile private var lastFetchAt = 0L
    private val turningTrains = ConcurrentHashMap<String, Pair<Long, Boolean>>()

    /* ── 파싱 (순수 함수 — BranchLiveTest가 잠근다) ───────────────── */

    // ⚠ 닫는 중괄호도 escape 한다. 안드로이드(API 34+) ICU 엔진은 맨 `}`에
    //   PatternSyntaxException 을 던진다 — Weather.kt에서 실제로 물린 자리다.
    private val ROW = Regex("\\{[^{}]*\\}")

    private fun field(o: String, key: String) =
        Regex("\"$key\":\"([^\"]*)\"").find(o)?.groupValues?.get(1)

    /**
     * 예외 → **사람이 읽는 한 줄**. 카드 빈 상태에 그대로 찍히는 문구다(v1.6.46).
     *
     * 종전엔 [Snapshot.error]를 화면이 아예 안 읽어서, 비행기 모드·서버 오류·한도 소진 어느 쪽이든
     * 열차 0대 → `"실시간 조회 중…"` 에 **영원히 머물렀다**. 사용자는 고장인지 알 수 없었다.
     * 원문 예외 메시지(`Unable to resolve host …`)는 logcat에만 남기고 여기서 사람 말로 바꾼다.
     */
    internal fun humanError(t: Throwable): String {
        val m = t.message.orEmpty()
        return when {
            // 한도 문구는 fetch()가 이미 사람 말로 만들어 던진다
            "한도" in m -> m
            t is java.net.UnknownHostException || t is java.net.ConnectException ||
                "resolve host" in m || "Network is unreachable" in m -> "인터넷 연결 안 됨"
            t is java.net.SocketTimeoutException || "timed out" in m -> "응답이 없어요 · 다시 시도"
            else -> "실시간 정보를 못 받았어요"
        }
    }

    /** 응답 상태코드: INFO-000 외에는 에러(ERROR-337 = 일일 한도 초과) */
    internal fun apiError(json: String): String? {
        val code = field(json, "code") ?: return null
        if (code == "INFO-000") return null
        return "${field(json, "message") ?: code} ($code)"
    }

    internal fun parsePositions(json: String): List<PositionRow> =
        ROW.findAll(json).mapNotNull { m ->
            val o = m.value
            PositionRow(
                subwayId = field(o, "subwayId") ?: return@mapNotNull null,
                statnNm = field(o, "statnNm") ?: return@mapNotNull null,
                trainNo = field(o, "trainNo")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null,
                updnLine = field(o, "updnLine").orEmpty(),
                statnTnm = field(o, "statnTnm").orEmpty(),
                trainSttus = field(o, "trainSttus") ?: "9",
            )
        }.toList()

    internal fun parseArrivals(json: String): List<ArrivalRow> =
        ROW.findAll(json).mapNotNull { m ->
            val o = m.value
            ArrivalRow(
                trainNo = field(o, "btrainNo")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null,
                destName = field(o, "bstatnNm").orEmpty(),
                etaSec = field(o, "barvlDt")?.toIntOrNull() ?: return@mapNotNull null,
                arvlCd = field(o, "arvlCd").orEmpty(),
            )
        }.toList()

    /* ── 지선 판정 ───────────────────────────────────────────────── */

    /** "신도림(지선)"·"까치산역"·"성수종착" 같은 표기 변형 흡수 */
    private fun norm(name: String) = name.substringBefore("(").replace("역", "").trim()

    private fun stationIdx(name: String): Int {
        val n = norm(name)
        val exact = BranchLine.stations.indexOf(n)
        if (exact >= 0) return exact
        return BranchLine.stations.indexOfFirst { n.contains(it) }
    }

    /** 종착역명이 지선 종착인가 — 1=신도림계열, -1=까치산계열, 0=아님 */
    private fun destKind(tnm: String): Int {
        val n = norm(tnm)
        return when {
            n.contains("까치산") -> -1
            n.contains("신도림") -> 1
            else -> 0
        }
    }

    /**
     * **신정지선 영업 열번인가**(v1.6.58) — 종착역명만으로 못 가르는 자리를 열번이 가른다.
     *
     * `destKind("신도림") = 1` 이라 신도림에서 **종착하는 본선 열차**가 신도림에 닿는 순간
     * [branchTrains]가 지선 상행으로 올렸고, [applyTurnaround]가 +5를 먹여
     * **실재하지 않는 지선 열차**를 세웠다(`4376` → `4381`). 지선 5역 중 신도림만 본선과
     * 공유하는 역이라 거기서만 새던 구멍이다.
     *
     * 범위 근거 두 갈래(서로 무관한 출처):
     *
     * ① **행로표**([RouteTable]) — 지선 다이아의 영업 열번은 `5501`~`5720`이 전부다
     *    (주간 지1~지8 `5527`~`5672`, 야간 지10~지14 `5501`~`5720`).
     *    · 야간 다이아 꼬리의 `5901`·`5902`·`5903`·`5904`·`5905`·`5906`·`5907(회송)` = 지선 막차 입고 회송
     *    · 본선 다이아 전반 첫 열번 `5922`·`5928`·`5930`·`5932`·`5934`·`5949`·`5951`·`5953`·`5955`·`5961`
     *      = 본선 신정기지 출고 회송 — 지선 선로를 타지만 **승객이 못 탄다.**
     *    → `59xx`는 양쪽 다 회송이라 편승 지도에 올리지 않는다. 그래서 상한이 `5899`다.
     *
     * ② **실호출**(2026-08-23 `realtimePosition/2호선` 폴링) — 지선 전용 4역(까치산·신정네거리·
     *    양천구청·도림천)에 실재한 열차는 **전부 5xxx**였고, 종착이 `"신도림"`인데 지선 전용역엔
     *    한 번도 안 온 열차는 **전부 4xxx**(`4376`·`4397`·`4398`·`4408`)였다.
     *    상세 기록은 `docs/project-notes.md`.
     *
     * ⚠ [inboundFromPositions]에는 걸지 않는다 — 거기는 본선 입고 열차를 **일부러** 쓴다.
     */
    private fun isBranchNo(no: String) = (no.toIntOrNull() ?: 0) in 5000..5899

    /**
     * **본선 순환선 열차 전부** (v1.6.84) — [branchTrains] 와 **같은 `rows`** 를 쓴다.
     * 위치 API 는 이미 2호선 전체를 주고 있었고 지선만 걸러 쓰고 있었을 뿐이라,
     * 여기서 나머지를 쓰는 데 **API 호출이 한 번도 늘지 않는다**(키 한도 산정 그대로).
     *
     * ## 방향 — `updnLine` 은 글자가 아니라 **"0"·"1"** 이다
     *
     * 2026-09-03 01:00 실호출로 확인했다: `updnLine` 실값은 `"0"`(8대)·`"1"`(8대)뿐이고
     * `"상행"`·`"내선"` 같은 글자는 오지 않는다. 어느 쪽이 내선인지는 실데이터로 맞췄다 —
     * `7523` 이 `문래` 에서 종착 `신도림` 인데 `updnLine=1` 이었다. 내선 순서가
     * `… 대림 → 신도림 → 문래 …` 이므로 문래에서 신도림으로 가는 것은 **역순 = 외선**이다
     * (`8527` 을지로3가 → 종착 을지로입구, `updnLine=1` 도 같은 결론).
     * → **`"0"` = 내선(시계) · 그 밖 = 외선(반시계).**
     *
     * ⚠ 지선 열차는 뺀다. 지선 전용역([Line2Stations.BRANCH_ONLY])에 있는 열차와
     * 본선 43역에 매칭이 안 되는 이름은 그리지 않는다 — 성수지선 `1725`(신설동)·
     * 신정지선 `5719`(도림천)가 실제로 그렇게 걸러진다.
     */
    internal fun mainTrains(rows: List<PositionRow>): List<MainTrainMark> =
        rows.asSequence()
            .filter { it.subwayId == BranchLine.SUBWAY_ID_LINE2 }
            .mapNotNull { r ->
                val name = Line2Stations.norm(r.statnNm)
                if (name in Line2Stations.BRANCH_ONLY) return@mapNotNull null
                val idx = Line2Stations.MAIN.indexOf(name)
                if (idx < 0) return@mapNotNull null
                val inner = r.updnLine.trim() == "0"
                MainTrainMark(
                    trainNo = r.trainNo,
                    stationIdx = idx,
                    inner = inner,
                    // ⚠ **부호는 진행 방향을 따라간다** (v1.6.85 수정). 인덱스가 커지는 쪽으로
                    // 가는 것은 내선뿐이라, 외선은 같은 상태에서 **반대쪽**에 놓여야 한다 —
                    // 안 그러면 외선 열차가 다음 역이 아니라 지나온 역 쪽으로 그려진다.
                    offset = when (r.trainSttus) {
                        "0" -> -0.15f      // 진입   (다음 역 바로 앞)
                        "1" -> 0f          // 도착   (역 위)
                        "2" -> 0.15f       // 출발   (역을 막 벗어남)
                        "3" -> -0.6f       // 전역 출발(두 역 사이)
                        else -> 0f
                    } * (if (inner) 1f else -1f),
                    statusText = name + when (r.trainSttus) {
                        "0" -> " 진입"; "1" -> " 도착"; "2" -> " 출발"
                        "3" -> " 접근 중"; else -> " 부근"
                    },
                    destName = Line2Stations.norm(r.statnTnm),
                    statnNm = name,
                    trainSttus = r.trainSttus,
                )
            }
            .distinctBy { it.trainNo }
            .toList()

    /** trainSttus(0진입 1도착 2출발 3전역출발) → 역 인덱스 주변의 미세 위치 */
    private fun posOf(idx: Int, dir: Float, sttus: String) = when (sttus) {
        "0" -> idx - dir * 0.15f
        "1" -> idx.toFloat()
        "2" -> idx + dir * 0.15f
        "3" -> idx - dir * 0.6f
        else -> idx.toFloat()
    }.coerceIn(0f, (BranchLine.stations.size - 1).toFloat())

    /** 1차: 종착역명이 지선 종착인 2호선 열차 */
    internal fun branchTrains(rows: List<PositionRow>): List<TrainMark> =
        rows.asSequence()
            .filter { it.subwayId == BranchLine.SUBWAY_ID_LINE2 && isBranchNo(it.trainNo) }
            .filter { destKind(it.statnTnm) != 0 }
            .mapNotNull { r ->
                val idx = stationIdx(r.statnNm)
                if (idx < 0) return@mapNotNull null   // 본선 구간 주행 중이면 지도 제외
                val toSindorim = destKind(r.statnTnm) == 1
                val st = BranchLine.stations[idx] + when (r.trainSttus) {
                    "0" -> " 진입"; "1" -> " 도착"; "2" -> " 출발"
                    "3" -> " 접근 중"; else -> " 부근"
                }
                TrainMark(r.trainNo, toSindorim,
                    posOf(idx, if (toSindorim) 1f else -1f, r.trainSttus), st)
            }
            .distinctBy { it.trainNo }
            .toList()

    // 지선에만 있는 역: 여기 있으면 종착역명이 어떻게 오든 지선 열차다
    private val BRANCH_ONLY = setOf("까치산", "신정네거리", "양천구청", "도림천")

    /**
     * 안전망 2단계: 지선 전용역의 열차는 종착역명 무관하게 표시.
     *
     * ⚠ 방향 폴백은 **[destKind] 가 답을 준 다음**이다 — 종착역명이 가장 믿을 만하고,
     * `updnLine` 은 그게 비어 있을 때만 본다. 이 순서를 바꾸면 지선 동작이 회귀한다.
     *
     * ⚠ `updnLine` 실값은 **`"0"`·`"1"`** 이다(v1.6.84 실호출). 종전의 `"상" in updnLine` ·
     * `"하" in updnLine` 은 **한 번도 참이 된 적이 없는 죽은 판정**이었다 — 늘 마지막
     * `else -> true` 로 떨어져 하행 열차도 신도림 방면으로 그렸다. `"0"` = 상행(신도림 방면) ·
     * `"1"` = 하행(까치산 방면)으로 고친다(v1.6.85). `BranchLiveTest` 가 두 방향을 잠근다.
     */
    internal fun branchTrainsLoose(rows: List<PositionRow>, strict: List<TrainMark>): List<TrainMark> {
        val have = strict.map { it.trainNo }.toSet()
        return rows.asSequence()
            .filter { it.subwayId == BranchLine.SUBWAY_ID_LINE2 && isBranchNo(it.trainNo) && it.trainNo !in have }
            .filter { norm(it.statnNm) in BRANCH_ONLY }
            .map { r ->
                val idx = BranchLine.stations.indexOf(norm(r.statnNm))
                val up = when {
                    destKind(r.statnTnm) == 1 -> true
                    destKind(r.statnTnm) == -1 -> false
                    r.updnLine.trim() == "0" -> true     // 상행 = 신도림 방면
                    r.updnLine.trim() == "1" -> false    // 하행 = 까치산 방면
                    else -> true
                }
                TrainMark(r.trainNo, up, posOf(idx, if (up) 1f else -1f, r.trainSttus),
                    BranchLine.stations[idx] + " 부근")
            }.toList()
    }

    /** 안전망 3단계: 위치 API가 지선 열차를 하나도 못 주면 양천구청 도착정보로 합성 */
    internal fun trainsFromArrivals(yang: List<ArrivalRow>): List<TrainMark> =
        yang.mapNotNull { r ->
            if (!isBranchNo(r.trainNo)) return@mapNotNull null   // 회송·본선은 편승 대상이 아니다
            val dest = norm(r.destName)
            val toSindorim = dest.contains("신도림")
            if (!toSindorim && !dest.contains("까치산")) return@mapNotNull null
            val up2 = BranchLine.SEG_UP[0] + BranchLine.SEG_UP[1]
            if (r.etaSec <= 0 || r.etaSec > up2) return@mapNotNull null
            val posv = if (toSindorim) BranchLine.posFromStationSec(r.etaSec.toFloat(), 2, true)
                       else (2f + r.etaSec.toFloat() / up2 * 2f).coerceIn(2f, 4f)
            TrainMark(r.trainNo, toSindorim, posv.coerceIn(0f, 4f), "양천구청 ${r.etaSec}초 전")
        }.distinctBy { it.trainNo }

    /** 본선 → 신도림 입고 열차: 남은 역 수 × 110초로 ETA 근사 */
    private val MAINLINE_AWAY = mapOf("대림" to 1, "구로디지털단지" to 2, "신대방" to 3, "신림" to 4)

    internal fun inboundFromPositions(rows: List<PositionRow>): List<InboundTrain> =
        rows.asSequence()
            .filter { it.subwayId == BranchLine.SUBWAY_ID_LINE2 }
            .filter { norm(it.statnTnm) == "신도림" && norm(it.statnNm) in MAINLINE_AWAY }
            .map { r ->
                val away = MAINLINE_AWAY[norm(r.statnNm)] ?: 1
                InboundTrain(r.trainNo,
                    (away * 110 - if (r.trainSttus == "2") 40 else 0).coerceAtLeast(30))
            }
            .filter { it.etaSec <= 600 }
            .distinctBy { it.trainNo }
            .sortedBy { it.etaSec }
            .toList()

    /**
     * 위치 API(역 단위) + 도착 API(초 단위)를 융합해 **역 [at] 접근 상행 열차**를 정밀화.
     *
     * [at] = 2 양천구청(편승 보드 지점) · 1 신정네거리(그 바로 앞 역).
     *
     * ⚠ **지금 실제로 도는 건 `at = 2`(양천구청) 하나뿐이다.** `at = 1`(신정네거리)은 호출을
     * v1.6.71에서 껐다 — 이유와 되살리는 법은 [loadFromSeoulApi] 주석에 있다. 역 번호를
     * 인자로 받는 이 형태는 **그대로 남긴다**(되살리기가 두 줄이고, 테스트도 이 형태를 건다).
     * 두 역을 같이 걸면 같은 열차에 대해 **나중에 부른 쪽이 이긴다** — 양천구청을 나중에
     * 걸어 **양천구청을 신뢰**한다(보드 지점이라 더 중요).
     *
     * ⚠⚠ **`arvlCd` 0·1·2(진입·도착·출발) 행은 버린다**(v1.6.70 — 실호출로 잡은 오배치).
     * 도착 API는 열차가 그 역을 지난 뒤에도 **같은 행을 몇 분씩 되풀이해 준다.**
     * 2026-08-25 18:32~18:36 실측(30초 간격), 열차 `5651` 한 대:
     * ```
     * 18:32:48 위치=양천구청 출발   도착행=cd2 출발/eta10   ← 여기까지만 맞다
     * 18:33:49 위치=도림천 도착     도착행=cd0 진입/eta10   ← 출발 → 진입으로 되돌아간다
     * 18:34:49 위치=도림천 도착     도착행=cd1 도착/eta10
     * 18:35:50 위치=**신도림 도착** 도착행=cd2 출발/eta10   ← 3분째 같은 자리를 말한다
     * ```
     * eta 10초를 그대로 믿으면 도림천에 있는 열차를 **양천구청으로 끌어다 놓는다**
     * (1.2역 가드가 |1.92 − 3.0| = 1.08 이라 못 막는다 — 종전에 실제로 새던 구멍).
     * 반대로 접근 중인 행(cd 3·4·5·99)은 eta가 **330 → 320 → 180 → 150 → 140** 으로
     * 제대로 내려온다. 융합이 원래 노리던 것이 이쪽이고, 이제 이쪽만 쓴다.
     * 0·1·2를 버려도 잃는 게 없다 — 그 순간의 자리는 위치 API가 이미 역 단위로 정확히 준다.
     */
    internal fun refineWithArrivals(
        trains: List<TrainMark>, arrivals: List<ArrivalRow>, at: Int = 2,
    ): List<TrainMark> {
        if (trains.isEmpty() || arrivals.isEmpty()) return trains
        val etaByNo = arrivals
            .filter { norm(it.destName).contains("신도림") && it.arvlCd !in AT_STATION_CD }
            .associate { it.trainNo to it.etaSec }
        // ETA 상한 = 까치산에서 그 역까지 걸리는 시간(양천구청 230초 · 신정네거리 100초).
        // 넘으면 0~at 역변환이 표현할 수 없는 자리라 손대지 않는다.
        val maxSec = BranchLine.SEG_UP.take(at).sum()
        return trains.map { t ->
            val eta = etaByNo[t.trainNo] ?: return@map t
            if (!t.toSindorim || eta !in 1..maxSec) return@map t
            val refined = BranchLine.posFromStationSec(eta.toFloat(), at, true)
            /*
             * **앞으로만 당긴다**(v1.6.70). 위치 API가 "어느 역"의 진실이고, 도착 ETA는 그 위에
             * 역과 역 사이를 채우는 값이다 — 뒤로 끄는 보정은 전부 오데이터였다.
             *
             * `barvlDt`는 **그 역에서의 정차 시간까지 포함**한다(2026-08-25 실측: `5653`이
             * 신정네거리에 **정차 중**인데 양천구청 ETA가 180초 — 실주행 130초보다 50초 많다).
             * 그대로 역변환하면 역에 서 있는 열차를 구간 한복판(0.5역 뒤)으로 끌어다 놓는다.
             * 그러면 ③의 접근 판정(0.85~2.0)도 같이 빗나가 짧은 주기가 안 걸린다.
             *
             * 앞으로만 당기면 ETA가 충분히 작아진 **구간 후반부터** 보정이 걸린다 —
             * 양천구청에 다가올수록 정밀해진다는 원래 목적 그대로고, 뒤로 튀는 일이 없다.
             * 상한 1.2역은 그대로 둔다(터무니없이 낙관적인 ETA 차단).
             */
            if (refined >= t.position && refined - t.position <= 1.2f)
                t.copy(position = refined, statusText = "${BranchLine.stations[at]} ${eta}초 전")
            else t
        }
    }

    /** 그 역에 이미 닿았거나 지나간 도착행 — 접근 정보가 없다([refineWithArrivals] 참고) */
    private val AT_STATION_CD = setOf("0", "1", "2")

    /** 회차 열번 규칙(승무 실무): 신도림 회차 = 열번 +5, 까치산 회차 = 열번 +1 */
    private fun turnNo(no: String, add: Int) = no.toIntOrNull()?.let { (it + add).toString() } ?: no

    /**
     * 종착 도착 = 즉시 머리 전환. 종착 승강장은 1선이라 같은 열번은 하나만 남긴다.
     *
     * [ensureFleet] **뒤에** 돌기 때문에 실측 열차와 회차 홀드 아이콘에 같은 규칙이 걸린다.
     * 둘이 같은 열번으로 만나면(홀드가 미처 안 풀렸는데 실차가 돌아온 경우) `distinctBy`가
     * **앞에 오는 실측 열차**를 남긴다 — `trains + holdIcons` 순서가 그 보험이다.
     */
    internal fun applyTurnaround(trains: List<TrainMark>): List<TrainMark> = trains.map { t ->
        when {
            !t.toSindorim && t.position <= 0.15f ->
                t.copy(trainNo = turnNo(t.trainNo, 1), toSindorim = true, position = 0f,
                    statusText = "회차 · 까치산 대기")
            t.toSindorim && t.position >= 3.85f ->
                t.copy(trainNo = turnNo(t.trainNo, 5), toSindorim = false, position = 4f,
                    statusText = "회차 · 신도림 대기")
            else -> t
        }
    }.distinctBy { it.trainNo }

    /**
     * 회차 공백 메꾸기: 서울 API는 종착에서 회차하는 동안(운전실 교대 3~8분) 그 열차를 안 준다.
     * 직전까지 종착에 있던 **실측** 열차가 사라지면 그 자리에 회차 대기 아이콘을 유지한다
     * (추정 생성이 아니라 방금까지 있던 실제 열차 추적이라 유령이 아니다).
     *
     * ⚠ **[applyTurnaround] 앞에서** 돌아야 한다(v1.6.56). 여기 기억하는 열번은 API가 준
     * **회차 전** 번호고, +5/+1 로 바꾸는 일은 뒤따르는 [applyTurnaround]가 실차와 똑같은
     * 규칙으로 홀드 아이콘에도 해 준다. 순서가 뒤집히면 기억이 회차 **후** 번호로 쌓여
     * "회차 완료" 판정이 지선의 다른 실차 번호와 겹친다 — v1.6.55까지 아이콘이 사라지던 원인.
     */
    internal fun ensureFleet(trains: List<TrainMark>, nowMs: Long): List<TrainMark> {
        // 종착에 **들어온** 열차만 회차 대상이다(떠나는 열차는 아니다 — 신도림 출발도 pos 3.85라
        // 방향을 안 보면 같이 걸린다). 볼 때마다 시각을 갱신해 홀드 시계가 "눈에서 놓친 뒤"부터
        // 흐르게 한다 — 종전 putIfAbsent 는 종착에 보이던 시간까지 홀드 예산에서 깎아먹었다.
        trains.forEach { t ->
            if (!t.toSindorim && t.position <= 0.3f) turningTrains[t.trainNo] = nowMs to false
            if (t.toSindorim && t.position >= 3.7f) turningTrains[t.trainNo] = nowMs to true
        }

        val curNoStr = trains.map { it.trainNo }.toSet()
        turningTrains.entries.removeIf { (no, v) ->
            val (since, atSindorim) = v
            if (nowMs - since > TURN_HOLD_MS) return@removeIf true
            // 회차 완료 = **열번을 바꾼 그 열차**가 API에 다시 나타났다. [applyTurnaround]와 같은
            // 규칙 하나만 쓴다(2026-08-23 실측: 신도림 5677 → 5682(+5), 까치산 5680 → 5681(+1)).
            // 종전의 `+5 또는 +3` 추가 추측은 지선에 동시에 떠 있는 **다른 실차 번호**와 겹쳐
            // 회차 중인 열차의 기억을 지워 버렸다(실측 충돌: 까치산 홀드 5681 ↔ 실차 5682).
            if (turnNo(no, if (atSindorim) 5 else 1) in curNoStr) return@removeIf true
            // 같은 열번이 종착 아닌 곳에 보이면 애초에 회차가 아니었다
            no in curNoStr && trains.none {
                it.trainNo == no && (it.position <= 0.3f || it.position >= 3.7f)
            }
        }

        // 홀드 아이콘 = **들어온 방향 그대로 종착에 세운 실측 열차**. 열번 +5/+1 과
        // `회차 · … 대기` 문구는 뒤따르는 [applyTurnaround]가 실차와 한 규칙으로 붙인다.
        val holdIcons = turningTrains.entries.mapNotNull { (no, v) ->
            if (no in curNoStr) null
            else if (v.second) TrainMark(no, true, 4f, "신도림 도착")
            else TrainMark(no, false, 0f, "까치산 도착")
        }
        return trains + holdIcons
    }

    /* ── 회차 기억을 프로세스 밖으로 (v1.6.70 ⑤) ───────────────────── */

    /**
     * **콜드 스타트 구멍**(v1.6.70). [turningTrains]는 `object`의 메모리라 프로세스가 죽으면 사라진다.
     * 그래서 앱을 새로 띄운 순간 **이미 회차 중이던 열차는 그릴 근거가 통째로 없어** 안 보였다
     * (v1.6.56이 *"콜드 스타트에선 안 그린다"* 라고 적어 둔 바로 그 자리 — 사용자가 말한 "가끔씩").
     *
     * 2026-08-25 18:32~18:38 실호출로 **다른 근거가 없다는 것까지** 확인했다:
     *  · `realtimeStationArrival/신도림` — 지선 열차가 **한 건도 안 나온다**(전부 본선 성수행).
     *  · `realtimeStationArrival/까치산` — 5호선 열차가 섞여 오고(`5683/마천`·`5154/방화`,
     *    열번대가 지선과 겹친다!) 종착에 서 있는 지선 열차는 안 준다.
     *  · `realtimeStationArrival/양천구청` — 방향마다 **가장 가까운 한 대**만 준다. 회차 중인
     *    열차 자리는 방금 지나간 열차의 묵은 행이 차지하고 있었다.
     * → 종착에 서 있는 열차를 말해 주는 API는 없다. **유일한 근거는 우리가 직접 본 것**이고,
     *   그래서 그 기억만 디스크에 잠깐 남겼다가 되살린다. 없는 열차를 지어내지 않는다.
     *
     * 형식: `열번:마지막목격ms:종착(1=신도림)` 을 쉼표로. 저장·복원은 [BranchLiveMap]이 한다
     * (여기를 안드로이드 클래스에서 자유롭게 둬야 유닛테스트가 [ensureFleet]를 그대로 돌린다).
     */
    internal fun turnMemory(): String = turningTrains.entries.joinToString(",") { (no, v) ->
        "$no:${v.first}:${if (v.second) 1 else 0}"
    }

    /**
     * 저장해 둔 회차 기억을 되살린다 — **관측했던 열차를 되돌리는 것이지 창작이 아니다.**
     *
     * · 12분([TURN_HOLD_MS]) 넘은 기억은 여기서 버린다 — 살아 있는 홀드와 같은 잣대다.
     * · `putIfAbsent` — 지금 눈에 보이는 관측이 항상 이긴다.
     * · 복원한 홀드도 **복귀 열차(+5/+1)가 첫 폴링에서 보이면 즉시 걷힌다**([ensureFleet]).
     *   그 사이 회차가 끝나 있었더라도 한 폴링 주기(10초) 안에 스스로 정리된다.
     */
    internal fun restoreTurnMemory(saved: String, nowMs: Long) {
        saved.split(",").forEach { e ->
            val p = e.split(":")
            val since = p.getOrNull(1)?.toLongOrNull() ?: return@forEach
            if (p.size == 3 && nowMs - since <= TURN_HOLD_MS)
                turningTrains.putIfAbsent(p[0], since to (p[2] == "1"))
        }
    }

    /** 같은 방향 0.45역 이내 중복은 같은 편성으로 보고 하나만. 종착은 회차가 겹치므로 예외. */
    internal fun squashOverlaps(trains: List<TrainMark>): List<TrainMark> {
        val out = mutableListOf<TrainMark>()
        for (t in trains.distinctBy { it.trainNo }) {
            val atTerminus = t.position <= 0.3f || t.position >= 3.7f
            val dup = !atTerminus && out.any { o ->
                o.toSindorim == t.toSindorim && o.position > 0.3f && o.position < 3.7f &&
                    kotlin.math.abs(o.position - t.position) < 0.45f
            }
            if (!dup) out += t
        }
        return out
    }

    /* ── 네트워크 ────────────────────────────────────────────────── */

    /**
     * 이 오류가 **일일 한도**인가 = 다음 키로 넘어갈 이유인가. 아니면 일시 오류라 재시도만 한다.
     * ([fetch]에 인라인으로 있던 판정 — BranchLiveTest가 실제 ERROR-337 응답 문구로 잠근다)
     */
    internal fun isQuotaError(msg: String) = "337" in msg || "제한" in msg || "초과" in msg

    /** 키 로테이션 호출: 한도(ERROR-337) 감지 시 다음 키, 전부 소진 시 5분 백오프 */
    private suspend fun fetch(pathAfterKey: String): Result<String> = withContext(Dispatchers.IO) {
        if (System.currentTimeMillis() < quotaBlockedUntil)
            return@withContext Result.failure(Exception("일일 호출 한도 초과 (자정 리셋)"))
        var lastErr: Throwable? = null
        var attempts = 0
        while (attempts < API_KEYS.size + 1) {   // +1 = 일시 오류 재시도 여유
            attempts++
            val key = API_KEYS[keyIdx % API_KEYS.size]
            // 한 줄 = 한 번의 한도 소모. 이 앱에서 가장 빡빡한 자원이라 실기기에서 셀 수 있어야 한다
            // (v1.6.71에서 호출을 3 → 2회로 줄인 근거도 이 로그로 확인했다). 키 값은 안 찍는다.
            Log.d(TAG, "API 호출: ${pathAfterKey.substringBefore("/")} · 키#${keyIdx % API_KEYS.size}")
            val r = runCatching {
                val conn = URL("$BASE/$key/json/$pathAfterKey").openConnection() as HttpURLConnection
                val body = conn.run {
                    connectTimeout = 5000
                    readTimeout = 5000
                    try { inputStream.bufferedReader().use { it.readText() } } finally { disconnect() }
                }
                apiError(body)?.let { error(it) }
                body
            }
            if (r.isSuccess) return@withContext r
            lastErr = r.exceptionOrNull()
            val m = lastErr?.message.orEmpty()
            when {
                isQuotaError(m) -> keyIdx++                                   // 한도 → 다음 키
                attempts >= 2 -> return@withContext Result.failure(lastErr!!) // 일시 오류는 1회만 재시도
            }
        }
        quotaBlockedUntil = System.currentTimeMillis() + 5 * 60_000L
        Result.failure(Exception("모든 키 일일 한도 초과 (자정 리셋)"))
    }

    private suspend fun fetchPositions() =
        fetch("realtimePosition/0/100/${URLEncoder.encode("2호선", "UTF-8")}").map(::parsePositions)

    private suspend fun fetchArrivals(station: String) =
        fetch("realtimeStationArrival/0/12/${URLEncoder.encode(station, "UTF-8")}").map(::parseArrivals)

    /**
     * **양천구청으로 다가오는 신도림행 열차가 있나** — 적응형 갱신 주기의 판정(v1.6.70).
     *
     * 기준: 신도림행(편승 대상) 중 위치가 **0.85 이상 2.0 미만**.
     *  · 0.85 = 신정네거리 `진입`이 찍히는 자리([posOf]의 `"0"` = idx − 0.15). 승무원이
     *    양천구청 승강장에서 열차를 눈으로 찾기 시작하는 시점 — 여기부터 초 단위가 필요하다.
     *  · 2.0 = 양천구청 도착. 지나가면 편승은 끝났으니 평소 주기로 돌아간다.
     *  · 까치산행(하행)은 세지 않는다 — 양천구청에서 잡아 타는 건 신도림행뿐이다.
     * 창(0.85~2.0)의 실주행 시간 = 0.15 × 100 + 130 ≈ **145초**. 상행 배차 6분 기준
     * 가동률 ≈ 40%다(한도 계산의 근거 — `docs/project-notes.md` v1.6.70).
     */
    internal fun approachingYangcheon(trains: List<TrainMark>) =
        trains.any { it.toSindorim && it.position >= 0.85f && it.position < 2f }

    /**
     * 이번 갱신에 쓸 주기. [approachingYangcheon] 하나로 갈린다 — 값은 테스트가 잠근다
     * (문서의 한도 계산표가 이 두 숫자에 얹혀 있어서, 조용히 바뀌면 표가 거짓말이 된다).
     */
    internal fun pollIntervalMs(trains: List<TrainMark>) =
        if (approachingYangcheon(trains)) NEAR_INTERVAL_MS else IDLE_INTERVAL_MS

    /**
     * 스냅샷 1회 = **API 2회**(2호선 위치 1 + 양천구청 도착 1).
     * v1.6.70이 더했던 신정네거리 도착(3번째)은 v1.6.71에서 껐다 — [loadFromSeoulApi] 주석.
     * 주기 내 재호출은 캐시 반환(한도 보호) — 주기는 [pollIntervalMs]에 따라 10초/4초.
     *
     * ⚠ 호출자는 반드시 **컴포지션에 묶인 코루틴**에서 부를 것 — 상세시트가 닫히면
     * [BranchLiveMap]의 LaunchedEffect가 취소되며 폴링이 함께 멎는다.
     */
    suspend fun loadSnapshot(force: Boolean = false): Snapshot {
        val nowMs = System.currentTimeMillis()
        // 직전 스냅샷의 위치로 판정한다 — 최대 10초 묵은 값이지만, 접근 창이 145초라 놓치지 않는다.
        val interval = pollIntervalMs(lastSnapshot?.trains.orEmpty())
        if (!force) lastSnapshot?.let { if (nowMs - lastFetchAt < interval - TICK_SLACK_MS) return it }
        lastFetchAt = nowMs
        var snap = retainLastGood(loadFromSeoulApi())
        // ⚠ 순서 고정: 회차 공백 메꾸기(실측 열번) → 머리 전환(+5/+1) → 겹침 정리.
        //   [ensureFleet] KDoc 참고 — 뒤집으면 회차 중 아이콘이 사라진다.
        snap = snap.copy(trains = squashOverlaps(applyTurnaround(ensureFleet(snap.trains, nowMs))))
        lastSnapshot = snap
        // 주기는 **이번 호출을 통과시킨 값**이다 — logcat 타임스탬프 간격과 그대로 맞는다.
        // 열차 목록까지 남긴다 — 지도가 무엇을 그리는지(위치 융합·회차 홀드)를 실기기에서
        // 확인할 유일한 창이다. 시트가 열려 있는 동안 4~10초에 한 줄이라 시끄럽지 않다.
        Log.i(TAG, "스냅샷: 열차 ${snap.trains.size}대 · 입고 ${snap.inbound.size}건 " +
            "· 주기 ${interval / 1000}초" + (snap.error?.let { " · $it" } ?: "") +
            snap.trains.joinToString(", ", " [", "]") {
                "${it.trainNo}@${"%.2f".format(it.position)} ${it.statusText}"
            })
        return snap
    }

    /** 실패한 갱신에서 화면이 텅 비지 않도록 2분 이내 직전 성공 데이터를 유지 */
    private fun retainLastGood(snap: Snapshot): Snapshot {
        if (snap.error == null) {
            if (snap.trains.isNotEmpty()) lastGood = snap
            return snap
        }
        // **부분 실패**(도착 API 셋 중 하나만 죽음)면 방금 받은 위치가 옛 스냅샷보다 낫다.
        // 호출이 3개로 늘어난 v1.6.70에서 이 경우가 그만큼 잦아졌다.
        if (snap.trains.isNotEmpty()) return snap
        val lg = lastGood ?: return snap
        if (System.currentTimeMillis() - lg.fetchedAtMillis > STALE_KEEP_MS) return snap
        val ageSec = ((System.currentTimeMillis() - lg.fetchedAtMillis) / 1000).toInt()
        return lg.copy(error = "${snap.error} · 직전(${ageSec}초 전) 데이터 유지 중")
    }

    /**
     * 스냅샷 1회 = **API 2회**(위치 + 양천구청 도착).
     *
     * ### 신정네거리 도착(3번째 호출)은 v1.6.71에서 **껐다** — 왜
     *
     * v1.6.70이 한 정거장 앞(신정네거리)에서도 초 단위로 보이게 하려고 3번째 호출을 더했는데,
     * 그 작업이 실호출로 확인해 스스로 적어 둔 대로 **실효가 거의 없었다.**
     *
     *  1. `barvlDt`에 **정차 시간이 섞인다**(실측: 신정네거리에 서 있는 `5653`의 양천구청 ETA가
     *     180초 — 실주행 130초보다 50초 많다). 역변환한 좌표가 위치 API보다 **뒤**로 떨어져
     *     [refineWithArrivals]의 전진 가드에 그대로 막힌다.
     *  2. 접근 중 ETA도 `330 → 320 → 180 → 180 → 180`으로 **초 단위로 안 내려온다.**
     *  3. 도착 API는 열차가 그 역을 지난 뒤에도 **같은 행을 3분씩 되풀이한다**(잡음만 늘린다).
     *  4. "한 정거장 앞부터 초 단위"를 실제로 만들고 있는 건 **적응형 갱신(4초) + 등속 보간**이다.
     *  5. 그런데 이 한 줄이 **일일 한도의 1/3을 쓴다**(키 6개 = 6000회/일을 282명이 나눠 쓴다.
     *     하루 감당 278분 → 끄면 **417분**).
     *
     * ### 되살리는 법 — 두 줄
     *
     * API가 정확해지면(`barvlDt`에서 정차 시간이 빠지면) 아래를 되돌리면 그대로 산다.
     * [posFromStationSec][BranchLine.posFromStationSec]·[refineWithArrivals]는 역 번호를
     * 인자로 받는 **일반 함수 그대로 남겨 뒀다**(양천구청이 계속 쓴다). 테스트도 그대로 있다.
     * ```
     * val sinD = async { fetchArrivals("신정네거리") }          // ← 되살릴 줄 ①
     * val sinRows = sinD.await().getOrDefault(emptyList())
     * // 신정네거리(1) 먼저, 양천구청(2) 나중 — 겹치면 **양천구청이 이긴다**
     * val strict = refineWithArrivals(                          // ← 되살릴 줄 ②
     *     refineWithArrivals(branchTrains(posRows), sinRows, 1), yangRows, 2)
     * ```
     * (`error` 합치기에 `sin.exceptionOrNull()`도 같이 넣어야 한다.)
     */
    private suspend fun loadFromSeoulApi(): Snapshot = try {
        coroutineScope {
            val posD = async { fetchPositions() }
            val yangD = async { fetchArrivals("양천구청") }
            val pos = posD.await()
            val yang = yangD.await()
            val posRows = pos.getOrDefault(emptyList())
            val yangRows = yang.getOrDefault(emptyList())

            Snapshot(
                // 머리 전환은 여기서 하지 않는다 — [ensureFleet]가 **회차 전 열번**을 봐야 한다.
                // [loadSnapshot]이 ensureFleet → applyTurnaround 순으로 이어 붙인다.
                trains = run {
                    val strict = refineWithArrivals(branchTrains(posRows), yangRows, 2)
                    val merged = (strict + branchTrainsLoose(posRows, strict)).distinctBy { it.trainNo }
                    merged.ifEmpty { trainsFromArrivals(yangRows) }
                },
                mainTrains = mainTrains(posRows),
                inbound = inboundFromPositions(posRows),
                fetchedAtMillis = System.currentTimeMillis(),
                // 두 호출이 같은 이유로 죽으면 문구도 하나만 (`인터넷 연결 안 됨 · 인터넷 연결 안 됨` 방지).
                // 원문은 logcat으로 — 진단은 그쪽에서 한다.
                error = listOfNotNull(pos.exceptionOrNull(), yang.exceptionOrNull())
                    .onEach { Log.w(TAG, "조회 실패", it) }
                    .map(::humanError).distinct().joinToString(" · ").ifBlank { null },
            )
        }
    } catch (t: Throwable) {
        Log.w(TAG, "조회 실패", t)
        Snapshot(error = humanError(t), fetchedAtMillis = System.currentTimeMillis())
    }
}
