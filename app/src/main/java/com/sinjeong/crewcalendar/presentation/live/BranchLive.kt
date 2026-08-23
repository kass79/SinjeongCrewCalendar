package com.sinjeong.crewcalendar.presentation.live

import android.util.Log
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

    /** 양천구청 상행 도착까지 남은 초(x)를 0~2 위치로 역변환 */
    fun posFromYangcheonSec(x: Float, toSindorim: Boolean): Float {
        val seg = if (toSindorim) SEG_UP else SEG_DN
        var remain = x
        var p = 2f
        while (remain > 0f && p > 0f) {
            val idx = (p - 0.001f).toInt().coerceIn(0, 3)
            val segSec = seg[idx].toFloat()
            val here = (p - idx) * segSec
            if (remain <= here) { p -= remain / segSec; remain = 0f }
            else { remain -= here; p = idx.toFloat() }
        }
        return p.coerceIn(0f, 2f)
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

/** 실시간 도착 1건 (realtimeStationArrival 스키마 중 쓰는 필드만) */
internal data class ArrivalRow(val trainNo: String, val destName: String, val etaSec: Int)

/** 화면 한 벌 */
internal data class Snapshot(
    val trains: List<TrainMark> = emptyList(),
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
    )

    // ⚠ HTTPS(443)가 안 열려 있다 — 이 호스트만 cleartext 허용(res/xml/network_security_config.xml).
    private const val BASE = "http://swopenapi.seoul.go.kr/api/subway"

    private const val MIN_INTERVAL_MS = 15_000L   // 중복 호출 흡수(한도 보호)
    private const val STALE_KEEP_MS = 120_000L    // 실패 시 직전 성공 데이터 유지 한도

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

    /** 안전망 2단계: 지선 전용역의 열차는 종착역명 무관하게 표시 */
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
                    "상" in r.updnLine -> true
                    "하" in r.updnLine -> false
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
            val posv = if (toSindorim) BranchLine.posFromYangcheonSec(r.etaSec.toFloat(), true)
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

    /** 위치 API(역 단위) + 도착 API(초 단위)를 융합해 양천구청 접근 상행 열차를 정밀화 */
    internal fun refineWithArrivals(trains: List<TrainMark>, yang: List<ArrivalRow>): List<TrainMark> {
        if (trains.isEmpty() || yang.isEmpty()) return trains
        val etaByNo = yang.filter { norm(it.destName).contains("신도림") }
            .associate { it.trainNo to it.etaSec }
        return trains.map { t ->
            val eta = etaByNo[t.trainNo] ?: return@map t
            val up2 = BranchLine.SEG_UP[0] + BranchLine.SEG_UP[1]
            if (!t.toSindorim || eta !in 1..up2) return@map t
            val refined = BranchLine.posFromYangcheonSec(eta.toFloat(), true).coerceIn(0f, 2f)
            // 위치 API 좌표와 1.2역 이상 어긋나면 오데이터로 보고 무시
            if (kotlin.math.abs(refined - t.position) <= 1.2f)
                t.copy(position = refined, statusText = "양천구청 ${eta}초 전")
            else t
        }
    }

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
        // 안전망 상한이지 정상 경로가 아니다 — 회차가 끝나면 **복귀 열차(+5/+1)가** 홀드를 걷어낸다.
        // 2026-08-23 20:45~21:20 실측 6회차의 API 실종 구간: 신도림 2:42 / 4:13 / 4:59,
        // 까치산 5:14 / 6:20 / **7:36**(최대). 종전 8분은 최대치와 24초밖에 안 떨어져 있었다.
        // 12분 = 실무 회차 상한 8분 + 관측된 API 보고 지연(양끝 1~3분). 더 늘리면 API가 통째로
        // 조용할 때 이미 떠난 열차가 종착에 눌어붙는다.
        val turnHoldMs = 12 * 60_000L

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
            if (nowMs - since > turnHoldMs) return@removeIf true
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

    private suspend fun fetchYangcheonArrivals() =
        fetch("realtimeStationArrival/0/12/${URLEncoder.encode("양천구청", "UTF-8")}").map(::parseArrivals)

    /**
     * 스냅샷 1회 = API 2회. 15초 내 재호출은 캐시 반환(한도 보호).
     *
     * ⚠ 호출자는 반드시 **컴포지션에 묶인 코루틴**에서 부를 것 — 상세시트가 닫히면
     * [BranchLiveMap]의 LaunchedEffect가 취소되며 폴링이 함께 멎는다.
     */
    suspend fun loadSnapshot(force: Boolean = false): Snapshot {
        val nowMs = System.currentTimeMillis()
        if (!force) lastSnapshot?.let { if (nowMs - lastFetchAt < MIN_INTERVAL_MS) return it }
        lastFetchAt = nowMs
        var snap = retainLastGood(loadFromSeoulApi())
        // ⚠ 순서 고정: 회차 공백 메꾸기(실측 열번) → 머리 전환(+5/+1) → 겹침 정리.
        //   [ensureFleet] KDoc 참고 — 뒤집으면 회차 중 아이콘이 사라진다.
        snap = snap.copy(trains = squashOverlaps(applyTurnaround(ensureFleet(snap.trains, nowMs))))
        lastSnapshot = snap
        Log.i(TAG, "스냅샷: 열차 ${snap.trains.size}대 · 입고 ${snap.inbound.size}건" +
            (snap.error?.let { " · $it" } ?: ""))
        return snap
    }

    /** 실패한 갱신에서 화면이 텅 비지 않도록 2분 이내 직전 성공 데이터를 유지 */
    private fun retainLastGood(snap: Snapshot): Snapshot {
        if (snap.error == null) {
            if (snap.trains.isNotEmpty()) lastGood = snap
            return snap
        }
        val lg = lastGood ?: return snap
        if (System.currentTimeMillis() - lg.fetchedAtMillis > STALE_KEEP_MS) return snap
        val ageSec = ((System.currentTimeMillis() - lg.fetchedAtMillis) / 1000).toInt()
        return lg.copy(error = "${snap.error} · 직전(${ageSec}초 전) 데이터 유지 중")
    }

    private suspend fun loadFromSeoulApi(): Snapshot = try {
        coroutineScope {
            val posD = async { fetchPositions() }
            val yangD = async { fetchYangcheonArrivals() }
            val pos = posD.await()
            val yang = yangD.await()
            val posRows = pos.getOrDefault(emptyList())
            val yangRows = yang.getOrDefault(emptyList())

            Snapshot(
                // 머리 전환은 여기서 하지 않는다 — [ensureFleet]가 **회차 전 열번**을 봐야 한다.
                // [loadSnapshot]이 ensureFleet → applyTurnaround 순으로 이어 붙인다.
                trains = run {
                    val strict = refineWithArrivals(branchTrains(posRows), yangRows)
                    val merged = (strict + branchTrainsLoose(posRows, strict)).distinctBy { it.trainNo }
                    merged.ifEmpty { trainsFromArrivals(yangRows) }
                },
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
