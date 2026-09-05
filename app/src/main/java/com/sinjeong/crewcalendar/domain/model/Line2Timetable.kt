package com.sinjeong.crewcalendar.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToInt

/**
 * 서울시 역별 열차 시간표(assets/timetable/line2.csv) — 지연·다음 역·역간 소요시간. 순수 Kotlin.
 *
 * 자료는 서울 열린데이터광장 `SearchSTNTimeTableByIDService` 를 `tools/fetch_line2_timetable.py`
 * 로 한 번에 구워 둔 것이다(본선 43역 + 신정지선 4역 × 평일/토/휴일 × 내선/외선).
 * **앱은 이 파일을 읽기만 한다** — 다이아가 개정되면 코디네이터가 스크립트를 다시 돌린다.
 *
 * 시각은 자정 기준 **초**이고 자정을 넘긴 편(`25:00:00`)은 90000처럼 24시를 넘겨 적는다.
 * 없는 값(API 의 `00:00:00` = 시·종착역의 반대쪽 칸)은 **−1**.
 */
class Line2Timetable private constructor(private val rows: Map<Key, List<Stop>>) {
    data class Key(val weekTag: Int, val inout: Int, val trainNo: String)
    data class Stop(val stationIdx: Int, val arriveSec: Int, val leftSec: Int)

    fun stops(weekTag: Int, inout: Int, trainNo: String): List<Stop> = rows[Key(weekTag, inout, trainNo)].orEmpty()

    /** `(주중구분, 내외선, 뒤 세 자리)` → 그 운행으로 적힌 자산 열번들. [runStops] 의 색인. */
    private val byRun: Map<Triple<Int, Int, String>, List<String>> =
        rows.keys.groupBy({ Triple(it.weekTag, it.inout, runKey(it.trainNo)) }, { it.trainNo })

    /**
     * 이 **운행**의 정차 목록 — 라이브 열번의 **접두가 달라도** 찾는다(v1.7.2).
     *
     * ⚠ 자산 CSV(`assets/timetable/line2.csv`)의 열번은 `1xxx`(성수지선)·`2xxx`(본선)·
     * `5xxx`(신정지선)뿐이다(2026-09-05 grep: `2340` 132행 · `4340`·`6340`·`8340` **0행**).
     * 라이브 `8340` 을 글자 그대로 찾으면 늘 빈손이라 **지연·다음 역·이동 속도가 조용히
     * 사라진다.** 그래서 [sameRun] 으로 같은 운행을 찾는다.
     *
     * 후보가 여럿이면(지선 `5501` ↔ 본선 `2501` 처럼) **[nowSec] 에 가장 가까운 행**.
     * `nowSec < 0` 이면 첫 번째.
     */
    private fun runStops(weekTag: Int, inout: Int, trainNo: String, dest: String?, nowSec: Int): List<Stop> {
        stops(weekTag, inout, trainNo).let { if (it.isNotEmpty()) return it }
        val cands = byRun[Triple(weekTag, inout, runKey(trainNo))].orEmpty()
            .filter { sameRun(it, trainNo, dest) }
        if (cands.isEmpty()) return emptyList()
        if (cands.size == 1 || nowSec < 0) return stops(weekTag, inout, cands.first())
        return cands.map { stops(weekTag, inout, it) }
            .minByOrNull { l -> l.minOf { kotlin.math.abs(eventSec(it) - nowSec) } }
            .orEmpty()
    }

    private fun stopAt(
        weekTag: Int, inout: Int, trainNo: String, stationName: String,
        nowSec: Int = -1, dest: String? = null,
    ): Pair<List<Stop>, Int>? {
        val list = runStops(weekTag, inout, trainNo, dest, nowSec)
        val idx = stationIdx(stationName)
        val i = list.indexOfFirst { it.stationIdx == idx }
        return if (i < 0) null else list to i
    }

    /**
     * 실시간 상태([trainSttus])를 시간표의 **사건 시각**에 견줘 지연을 분으로 돌려준다.
     *
     * `0 진입 → 도착 30초 전 · 1 도착 → ARRIVETIME · 2 출발 → LEFTTIME · 3 전역출발 → 직전 역 LEFTTIME`.
     * 한쪽 시각이 없으면(−1) 다른 쪽으로 대체하고, 둘 다 없거나 열번·역을 모르면 `null`(표시 생략).
     *
     * ⚠ **|지연| > [SANE_DELAY_MIN] 분이면 `null`.** 그만한 지연은 2호선에서 나오지 않는다 —
     * 나왔다면 기기 시계가 틀렸거나(GMT 에뮬 실측 "534분 빠름") 시간표 판이 다이아와 어긋난
     * 것이고, 둘 다 숫자를 보여 주는 쪽이 안 보여 주는 쪽보다 나쁘다.
     */
    fun delayMinutes(
        weekTag: Int, inout: Int, trainNo: String, stationName: String, trainSttus: String,
        nowSec: Int, dest: String? = null,
    ): Int? {
        val (list, i) = stopAt(weekTag, inout, trainNo, stationName, nowSec, dest) ?: return null
        val s = list[i]
        fun pick(primary: Int, fallback: Int) = if (primary >= 0) primary else fallback.takeIf { it >= 0 }
        val event = when (trainSttus) {
            "0" -> pick(s.arriveSec, s.leftSec)?.minus(30)
            "1" -> pick(s.arriveSec, s.leftSec)
            "2" -> pick(s.leftSec, s.arriveSec)
            "3" -> list.getOrNull(i - 1)?.let { pick(it.leftSec, it.arriveSec) }
            else -> null
        } ?: return null
        return ((nowSec - event) / 60.0).roundToInt().takeIf { kotlin.math.abs(it) <= SANE_DELAY_MIN }
    }

    /** 다음 역 도착까지 남은 **초**(지연을 그대로 얹는다). 0 이하면 곧 도착. API 호출 0회. */
    fun secondsToNextStop(
        weekTag: Int, inout: Int, trainNo: String, stationName: String, delayMin: Int,
        nowSec: Int, dest: String? = null,
    ): Int? {
        val (list, i) = stopAt(weekTag, inout, trainNo, stationName, nowSec, dest) ?: return null
        val next = list.getOrNull(i + 1) ?: return null
        val arrive = if (next.arriveSec >= 0) next.arriveSec else next.leftSec
        if (arrive < 0) return null
        return arrive + delayMin * 60 - nowSec
    }

    /**
     * 이 운행이 **[stationName] 에 도착**하는 시각(자정 기준 초) — [nowSec] 이후 첫 번째.
     * 지연은 안 얹는다(부르는 쪽이 [delayMinutes] 로 더한다). 모르면 null.
     *
     * 신정 입고 안내가 "역 수 × 110초" 근사 대신 이 값을 먼저 쓴다(v1.7.2).
     */
    fun arriveSecAt(
        weekTag: Int, inout: Int, trainNo: String, stationName: String, nowSec: Int,
        dest: String? = null,
    ): Int? {
        val idx = stationIdx(stationName).takeIf { it >= 0 } ?: return null
        // ⚠ **지나간 정차는 버린다.** 순환선 한 운행이 같은 역을 두 번 스치는 판이 있어
        // 그냥 첫 행을 잡으면 이미 지난 시각으로 "0분 후"를 말한다. 60초는 도착 직후 여유.
        return runStops(weekTag, inout, trainNo, dest, nowSec)
            .filter { it.stationIdx == idx }
            .map(::eventSec).filter { it >= nowSec - 60 }
            .minOrNull()
    }

    /**
     * 이 운행이 [stationName] 을 지나는 예정 시각 중 [nowSec] 에 **가장 가까운** 것(자정 기준 초).
     *
     * [arriveSecAt] 과 달리 **지나간 정차도 본다** — 늦은 열차가 아직 그 역에 서 있는지를
     * 보려는 것이라 미래만 보면 늘 빈손이다. [pickRun] 이 같은 몸통 라이브 둘을 가를 때 쓴다(v1.7.3).
     */
    fun schedSecAt(
        weekTag: Int, inout: Int, trainNo: String, stationName: String, nowSec: Int,
        dest: String? = null,
    ): Int? {
        val idx = stationIdx(stationName).takeIf { it >= 0 } ?: return null
        return runStops(weekTag, inout, trainNo, dest, nowSec)
            .filter { it.stationIdx == idx }
            .map(::eventSec).filter { it >= 0 }
            .minByOrNull { kotlin.math.abs(it - nowSec) }
    }

    /**
     * 이 역 → 다음 역 소요 **초**. 지도의 열차 전진 속도가 이 값을 쓴다([stepMotion]).
     * 모르면 [DEFAULT_SEG_SEC](**110초**, v1.7.5 사용자 지정 — 종전 120).
     */
    fun segmentSeconds(
        weekTag: Int, inout: Int, trainNo: String, stationName: String,
        nowSec: Int = -1, dest: String? = null,
    ): Int {
        val fallback = DEFAULT_SEG_SEC.toInt()
        val (list, i) = stopAt(weekTag, inout, trainNo, stationName, nowSec, dest) ?: return fallback
        val here = list[i]; val next = list.getOrNull(i + 1) ?: return fallback
        val from = if (here.leftSec >= 0) here.leftSec else here.arriveSec
        val to = if (next.arriveSec >= 0) next.arriveSec else next.leftSec
        return if (from >= 0 && to > from) to - from else fallback
    }

    /** 이 정차의 대표 시각 — 도착이 있으면 도착, 없으면(시·종착역) 출발. 둘 다 없으면 −1. */
    private fun eventSec(s: Stop): Int = if (s.arriveSec >= 0) s.arriveSec else s.leftSec

    companion object {
        /** [delayMinutes] 가 믿어 주는 지연의 한계(분). 넘으면 시계·시간표가 어긋난 것으로 보고 표시를 접는다. */
        const val SANE_DELAY_MIN = 120

        /** 본선 43역 + 신정지선 4역. **순서가 CSV 의 `stationIdx`** 라 바꾸면 자산을 다시 구워야 한다. */
        val STATIONS: List<String> = Line2Stations.MAIN + listOf("도림천", "양천구청", "신정네거리", "까치산")
        fun stationIdx(name: String): Int = STATIONS.indexOf(Line2Stations.norm(name))

        /** 서울시 API 의 `INOUT_TAG` — 실데이터로 확인했다(자산 CSV 2행 주석의 `checked=`). */
        fun inoutOf(inner: Boolean): Int = if (inner) 1 else 2

        /** 시간표의 `WEEK_TAG` — 앱의 기존 휴일 판정과 같은 표를 본다. */
        fun weekTagOf(date: LocalDate): Int = when {
            date.dayOfWeek == DayOfWeek.SUNDAY || Bundled.PUBLIC_HOLIDAYS.containsKey(date) -> 3
            date.dayOfWeek == DayOfWeek.SATURDAY -> 2
            else -> 1
        }

        /** 새벽 0~3시는 전날 시간표의 24시+ 다. */
        fun serviceClock(now: LocalDateTime): Pair<LocalDate, Int> {
            val sec = now.toLocalTime().toSecondOfDay()
            return if (sec < 3 * 3600) now.toLocalDate().minusDays(1) to sec + 86400 else now.toLocalDate() to sec
        }

        fun parse(csv: String): Line2Timetable {
            val m = HashMap<Key, MutableList<Stop>>()
            // ⚠ 줄 끝을 반드시 턴다 — 이 저장소는 `core.autocrlf=true` 라 다시 클론하면 자산이
            // CRLF 로 풀린다. 안 털면 마지막 칸이 `"25230\r"` 이 돼 `toInt()` 가 던지고,
            // 로더의 runCatching 이 통째로 null 을 만들어 **지연 표시가 조용히 사라진다.**
            for (raw in csv.lineSequence()) {
                val line = raw.trim()
                if (line.isBlank() || line.startsWith("#")) continue
                val p = line.split(',')
                if (p.size < 6) continue
                val key = Key(p[0].toInt(), p[1].toInt(), p[3])
                m.getOrPut(key) { ArrayList() } += Stop(p[2].toInt(), p[4].toInt(), p[5].toInt())
            }
            // 열번 안에서는 시각 순(순환선이라 역 인덱스 순이 아니다)
            m.values.forEach { l -> l.sortBy { if (it.arriveSec >= 0) it.arriveSec else it.leftSec } }
            return Line2Timetable(m)
        }
    }
}
