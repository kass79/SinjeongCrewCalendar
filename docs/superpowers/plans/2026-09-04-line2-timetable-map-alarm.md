# 2호선 시간표 내장 · 지연 계산 · 지도 가독성/이동 · 편승 알람 한 줄 — 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 서울시 역별 열차 시간표를 앱에 내장해 "+N분 지연 / 다음 역 N분 후"를 계산하고, 본선 지도에 방향 필터·큰 글자·자연스러운 열차 이동을 넣고, 편승 알람 알림에 실시간 한 줄을 붙인다.

**Architecture:** (C) `tools/fetch_line2_timetable.py`가 47역 시간표를 CSV 자산으로 굽고, 순수 Kotlin `Line2Timetable`이 파싱·지연·다음역·역간시간을 계산한다(JUnit). (B) `MainLineMap`은 필터 상태와 열번별 `Animatable`로 그리기만 바꾼다. (D) `DeadheadAlarm`은 예약 레코드에 열번 후보를 함께 저장했다가 발화 시 1회 조회해 알림을 갱신한다.

**Tech Stack:** Python 3(표준 라이브러리만), Kotlin/Compose(Canvas·Animatable), JUnit4(`tools/runtests.ps1`), 서울 열린데이터광장 API.

## Global Constraints

- 설계서: `docs/superpowers/specs/2026-09-04-widget-map-delay-design.md` B·C·D절.
- 다른 에이전트가 **위젯**(`widget/DutyWidget*.kt`, `widget/WidgetStrip.kt`, `util/DutyPalette.kt`, `util/MonthImage.kt`, 위젯 XML, `AndroidManifest.xml`)을 동시에 만진다 → 이 계획은 `AndroidManifest.xml`을 건드리지 않는다. `widget/DeadheadAlarm.kt`는 이 계획 몫이다.
- 버전·`docs/project-notes.md`·릴리즈 빌드는 코디네이터. 커밋은 태스크마다 바뀐 파일만 `git add <경로>`, push 금지.
- 테스트: `.\gradlew.bat :app:compileDebugUnitTestKotlin` 후 `powershell -ExecutionPolicy Bypass -File tools\runtests.ps1`. 기준선 211(위젯 에이전트가 늘리는 중이라 절대값 대신 **회귀 0**을 본다). 도메인 파일은 안드로이드 import 0(JUnitCore 최소 classpath).
- 에뮬레이터: 위젯 에이전트가 먼저 쓴다. **B의 화면 검증은 마지막 태스크에서 몰아서** 한다(그때 `adb devices`와 `adb shell dumpsys activity top | findstr crewcalendar`로 비었는지 확인). 심사 계정 유지, 근무변경 금지.
- API 키: `BranchLive.API_KEYS` 마지막 항목(2026-09-04 추가분)을 스크립트가 쓴다. 키 값을 로그·보고·커밋 메시지에 적지 마라.
- 사용자 확정(스킬 표) 불변. `rememberSaveable` 함정 — 새 상태는 `remember`.

---

### Task 1: 시간표 내려받기 스크립트 → CSV 자산

**Files:**
- Create: `tools/fetch_line2_timetable.py`
- Create(산출): `app/src/main/assets/timetable/line2.csv`

**Interfaces:**
- Produces: CSV(UTF-8, LF). 1행 주석 `# fetched=2026-09-04 stations=47 rows=N`, 2행 주석(열 설명 + inout 확인 결과), 3행부터 `weekTag,inout,stationIdx,trainNo,arriveSec,leftSec`. `weekTag` 1평일/2토/3휴일, `inout` 1내선/2외선(Step 3에서 실데이터로 확인), `stationIdx` = `STATIONS` 순서(0..46), 시각은 **자정 기준 초**(`25:00:00` → 90000), 없는 값(`00:00:00`)은 `-1`.
- `STATIONS` = `Line2Stations.MAIN` 43개 그대로(시청…충정로) + `도림천, 양천구청, 신정네거리, 까치산`.

- [ ] **Step 1: 스크립트 작성**

```python
#!/usr/bin/env python3
"""서울 열린데이터광장 역별 열차 시간표 → app/src/main/assets/timetable/line2.csv
사용: python tools/fetch_line2_timetable.py [--key KEY]   (키 생략 시 BranchLive.kt 마지막 키)
"""
import argparse, json, re, sys, time, urllib.parse, urllib.request, datetime, pathlib

ROOT = pathlib.Path(__file__).resolve().parents[1]
STATIONS = ["시청","을지로입구","을지로3가","을지로4가","동대문역사문화공원","신당","상왕십리","왕십리","한양대","뚝섬",
    "성수","건대입구","구의","강변","잠실나루","잠실","잠실새내","종합운동장","삼성","선릉","역삼","강남","교대","서초","방배",
    "사당","낙성대","서울대입구","봉천","신림","신대방","구로디지털단지","대림","신도림","문래","영등포구청","당산","합정",
    "홍대입구","신촌","이대","아현","충정로",
    "도림천","양천구청","신정네거리","까치산"]

def key_from_source():
    src = (ROOT / "app/src/main/java/com/sinjeong/crewcalendar/presentation/live/BranchLive.kt").read_text("utf-8")
    return re.findall(r'"([0-9a-f]{30})"', src)[-1]

def get(url):
    err = None
    for i in range(3):
        try:
            with urllib.request.urlopen(url, timeout=20) as r:
                return json.loads(r.read().decode("utf-8"))
        except Exception as e:
            err = e; time.sleep(1 + i)
    raise err

def station_code(key, name):
    d = get(f"http://openapi.seoul.go.kr:8088/{key}/json/SearchInfoBySubwayNameService/1/9/{urllib.parse.quote(name)}")
    for r in d["SearchInfoBySubwayNameService"]["row"]:
        if r["LINE_NUM"] == "02호선":
            return r["STATION_CD"]
    raise SystemExit(f"2호선 코드 없음: {name}")

def sec(hms):
    h, m, s = (int(x) for x in hms.split(":"))
    return -1 if (h, m, s) == (0, 0, 0) else h * 3600 + m * 60 + s

def timetable(key, code, week, inout):
    d = get(f"http://openapi.seoul.go.kr:8088/{key}/json/SearchSTNTimeTableByIDService/1/1000/{code}/{week}/{inout}")
    body = d.get("SearchSTNTimeTableByIDService")
    return body.get("row", []) if body else []     # INFO-200 = 데이터 없음

def main():
    ap = argparse.ArgumentParser(); ap.add_argument("--key"); a = ap.parse_args()
    key = a.key or key_from_source()
    codes = {}
    for name in STATIONS:
        codes[name] = station_code(key, name); time.sleep(0.2)
    out = []
    for i, name in enumerate(STATIONS):
        for week in (1, 2, 3):
            for inout in (1, 2):
                rows = timetable(key, codes[name], week, inout)
                out += [(week, inout, i, r["TRAIN_NO"], sec(r["ARRIVETIME"]), sec(r["LEFTTIME"])) for r in rows]
                print(f"{name} w{week} io{inout}: {len(rows)}", file=sys.stderr)
                time.sleep(0.2)
    out.sort()
    dst = ROOT / "app/src/main/assets/timetable/line2.csv"
    dst.parent.mkdir(parents=True, exist_ok=True)
    with dst.open("w", encoding="utf-8", newline="\n") as f:
        f.write(f"# fetched={datetime.date.today()} stations={len(STATIONS)} rows={len(out)}\n")
        f.write("# columns=weekTag,inout,stationIdx,trainNo,arriveSec,leftSec ; weekTag 1=weekday 2=sat 3=holiday ; inout 1=inner(내선) 2=outer(외선)\n")
        for w, io, si, tn, ar, lf in out:
            f.write(f"{w},{io},{si},{tn},{ar},{lf}\n")
    print(f"wrote {dst} rows={len(out)} bytes={dst.stat().st_size}")

if __name__ == "__main__":
    main()
```

- [ ] **Step 2: 실행** — `python tools/fetch_line2_timetable.py` (약 282회, 1~2분). Expected: 마지막 줄 `wrote … rows=N bytes=B`, `B ≤ 600000`. 지선 4역 등에서 0건이 나올 수 있다 — 정상(보고에 어느 조합이 0건인지 적어라).

- [ ] **Step 3: inout 뜻 확인** — CSV에서 `stationIdx=38`(홍대입구)·`weekTag=1`·`trainNo=2006` 행의 `inout` 값을 본다. 2006은 신도림→성수(내선 방향, 2026-09-04 실호출로 확인한 값)다. 그 값이 1이면 그대로, 2이면 CSV 2행 주석과 Task 2의 `inoutOf`를 뒤집어라. 확인 결과를 2행 주석 끝에 `checked=2006@홍대입구→inout=<값>`으로 남긴다.

- [ ] **Step 4: 커밋** — `git add tools/fetch_line2_timetable.py app/src/main/assets/timetable/line2.csv && git commit -m "2호선 47역 역별 열차 시간표 내장(서울시 API, 2026-09-04판) + 수집 스크립트"`

---

### Task 2: `Line2Timetable` — 파서·지연·다음역·역간시간 (순수 Kotlin)

**Files:**
- Create: `app/src/main/java/com/sinjeong/crewcalendar/domain/model/Line2Timetable.kt`
- Create: `app/src/main/java/com/sinjeong/crewcalendar/presentation/live/Line2TimetableLoader.kt` (assets 읽기 — 안드로이드)
- Modify: `app/src/main/java/com/sinjeong/crewcalendar/domain/model/Bundled.kt:266` 근처에 `fun isPublicHoliday(date: LocalDate) = PUBLIC_HOLIDAYS.containsKey(date)` 추가(없으면)
- Test: `app/src/test/java/com/sinjeong/crewcalendar/Line2TimetableTest.kt`

**Interfaces:**
```kotlin
class Line2Timetable {
    data class Key(val weekTag: Int, val inout: Int, val trainNo: String)
    data class Stop(val stationIdx: Int, val arriveSec: Int, val leftSec: Int)   // -1 = 없음
    companion object {
        fun parse(csv: String): Line2Timetable
        fun weekTagOf(date: LocalDate): Int          // 일·공휴일=3, 토=2, 그 외 1
        fun inoutOf(inner: Boolean): Int             // 내선=1 (Task 1 Step 3 결과에 맞춘다)
        val STATIONS: List<String>                   // Line2Stations.MAIN + 지선 4역
        fun stationIdx(name: String): Int            // Line2Stations.norm 적용, 없으면 -1
        fun serviceClock(now: LocalDateTime): Pair<LocalDate, Int>   // 새벽 0~3시는 전날 24시+
    }
    fun stops(weekTag: Int, inout: Int, trainNo: String): List<Stop>
    fun delayMinutes(weekTag: Int, inout: Int, trainNo: String, stationName: String, trainSttus: String, nowSec: Int): Int?
    fun secondsToNextStop(weekTag: Int, inout: Int, trainNo: String, stationName: String, delayMin: Int, nowSec: Int): Int?
    fun segmentSeconds(weekTag: Int, inout: Int, trainNo: String, stationName: String): Int   // 모르면 120
}
```
- 사건 시각: `0 진입 → arriveSec − 30`, `1 도착 → arriveSec`, `2 출발 → leftSec`, `3 전역출발 → 직전 Stop.leftSec`. 값이 −1이면 다른 쪽(arrive↔left)으로 대체, 둘 다 없으면 null. `delay = round((nowSec − 사건)/60)`.

- [ ] **Step 1: 테스트**

```kotlin
package com.sinjeong.crewcalendar

import com.sinjeong.crewcalendar.domain.model.Line2Timetable
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class Line2TimetableTest {
    // 홍대입구(38) 07:00:00 도착·07:00:30 출발 → 신촌(39) 07:02:00/07:02:30 → 이대(40) 07:04:00 (내선 2006, 평일)
    private val csv = """
        # fetched=2026-09-04 stations=47 rows=4
        # columns=...
        1,1,38,2006,25200,25230
        1,1,39,2006,25320,25350
        1,1,40,2006,25440,-1
        1,2,38,2513,90000,90030
    """.trimIndent()
    private val tt = Line2Timetable.parse(csv)

    @Test fun `파싱 — 주석 무시, 열번별 시각순`() {
        assertEquals(3, tt.stops(1, 1, "2006").size)
        assertEquals(38, tt.stops(1, 1, "2006")[0].stationIdx)
    }
    @Test fun `도착 사건 지연`() { assertEquals(2, tt.delayMinutes(1, 1, "2006", "홍대입구", "1", 25200 + 120)) }
    @Test fun `출발 사건 정시`() { assertEquals(0, tt.delayMinutes(1, 1, "2006", "홍대입구", "2", 25230 + 20)) }
    @Test fun `전역출발은 직전 역 출발시각 기준`() { assertEquals(1, tt.delayMinutes(1, 1, "2006", "신촌", "3", 25230 + 60)) }
    @Test fun `빠르면 음수`() { assertEquals(-1, tt.delayMinutes(1, 1, "2006", "홍대입구", "1", 25200 - 60)) }
    @Test fun `모르는 열번은 null`() { assertNull(tt.delayMinutes(1, 1, "9999", "홍대입구", "1", 25200)) }
    @Test fun `API 꼬리 붙은 역명도 잡는다`() { assertEquals(0, tt.delayMinutes(1, 1, "2006", "홍대입구역", "1", 25200)) }
    @Test fun `다음 역까지 초 — 지연 반영`() { assertEquals(120 + 60 - 30, tt.secondsToNextStop(1, 1, "2006", "홍대입구", 1, 25230 + 30)) }
    @Test fun `역간 소요, 없으면 120`() {
        assertEquals(90, tt.segmentSeconds(1, 1, "2006", "홍대입구"))
        assertEquals(120, tt.segmentSeconds(1, 1, "2006", "이대"))
    }
    @Test fun `25시 표기는 초로 접혀 있다`() { assertEquals(90000, tt.stops(1, 2, "2513")[0].arriveSec) }
    @Test fun `주 구분 — 토 2, 일 3, 평일 1, 공휴일 3`() {
        assertEquals(2, Line2Timetable.weekTagOf(LocalDate.of(2026, 9, 5)))
        assertEquals(3, Line2Timetable.weekTagOf(LocalDate.of(2026, 9, 6)))
        assertEquals(1, Line2Timetable.weekTagOf(LocalDate.of(2026, 9, 4)))
        assertEquals(3, Line2Timetable.weekTagOf(LocalDate.of(2026, 9, 25)))
    }
    @Test fun `새벽 1시는 전날 25시`() {
        val (d, s) = Line2Timetable.serviceClock(LocalDateTime.of(2026, 9, 5, 1, 0))
        assertEquals(LocalDate.of(2026, 9, 4), d); assertEquals(25 * 3600, s)
    }
}
```

- [ ] **Step 2: 실패 확인** — 컴파일 실패.

- [ ] **Step 3: 구현** — `domain/model/Line2Timetable.kt`(안드로이드 import 0):

```kotlin
package com.sinjeong.crewcalendar.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToInt

/** 서울시 역별 열차 시간표(assets/timetable/line2.csv) — 지연·다음 역·역간 소요시간. 순수 Kotlin. */
class Line2Timetable private constructor(private val rows: Map<Key, List<Stop>>) {
    data class Key(val weekTag: Int, val inout: Int, val trainNo: String)
    data class Stop(val stationIdx: Int, val arriveSec: Int, val leftSec: Int)

    fun stops(weekTag: Int, inout: Int, trainNo: String): List<Stop> = rows[Key(weekTag, inout, trainNo)].orEmpty()

    private fun stopAt(weekTag: Int, inout: Int, trainNo: String, stationName: String): Pair<List<Stop>, Int>? {
        val list = stops(weekTag, inout, trainNo)
        val idx = stationIdx(stationName)
        val i = list.indexOfFirst { it.stationIdx == idx }
        return if (i < 0) null else list to i
    }

    fun delayMinutes(weekTag: Int, inout: Int, trainNo: String, stationName: String, trainSttus: String, nowSec: Int): Int? {
        val (list, i) = stopAt(weekTag, inout, trainNo, stationName) ?: return null
        val s = list[i]
        fun pick(primary: Int, fallback: Int) = if (primary >= 0) primary else fallback.takeIf { it >= 0 }
        val event = when (trainSttus) {
            "0" -> pick(s.arriveSec, s.leftSec)?.minus(30)
            "1" -> pick(s.arriveSec, s.leftSec)
            "2" -> pick(s.leftSec, s.arriveSec)
            "3" -> list.getOrNull(i - 1)?.let { pick(it.leftSec, it.arriveSec) }
            else -> null
        } ?: return null
        return ((nowSec - event) / 60.0).roundToInt()
    }

    fun secondsToNextStop(weekTag: Int, inout: Int, trainNo: String, stationName: String, delayMin: Int, nowSec: Int): Int? {
        val (list, i) = stopAt(weekTag, inout, trainNo, stationName) ?: return null
        val next = list.getOrNull(i + 1) ?: return null
        val arrive = if (next.arriveSec >= 0) next.arriveSec else next.leftSec
        if (arrive < 0) return null
        return arrive + delayMin * 60 - nowSec
    }

    fun segmentSeconds(weekTag: Int, inout: Int, trainNo: String, stationName: String): Int {
        val (list, i) = stopAt(weekTag, inout, trainNo, stationName) ?: return 120
        val here = list[i]; val next = list.getOrNull(i + 1) ?: return 120
        val from = if (here.leftSec >= 0) here.leftSec else here.arriveSec
        val to = if (next.arriveSec >= 0) next.arriveSec else next.leftSec
        return if (from >= 0 && to > from) to - from else 120
    }

    companion object {
        val STATIONS: List<String> = Line2Stations.MAIN + listOf("도림천", "양천구청", "신정네거리", "까치산")
        fun stationIdx(name: String): Int = STATIONS.indexOf(Line2Stations.norm(name))
        /** Task 1 Step 3에서 확인한 값으로 맞춘다. 기본: 내선 = 1 */
        fun inoutOf(inner: Boolean): Int = if (inner) 1 else 2
        fun weekTagOf(date: LocalDate): Int = when {
            date.dayOfWeek == DayOfWeek.SUNDAY || Bundled.isPublicHoliday(date) -> 3
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
            for (line in csv.lineSequence()) {
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
```

`presentation/live/Line2TimetableLoader.kt`:

```kotlin
package com.sinjeong.crewcalendar.presentation.live

import android.content.Context
import com.sinjeong.crewcalendar.domain.model.Line2Timetable

/** assets/timetable/line2.csv 를 한 번만 읽는다. 실패하면 null(지연 표시만 빠진다). */
object Line2TimetableLoader {
    @Volatile private var cached: Line2Timetable? = null
    /** "2026-09-04" — 설정 화면 "열차 시간표 …판" 표시용 */
    @Volatile var fetchedLabel: String = ""
        private set
    fun get(ctx: Context): Line2Timetable? = cached ?: synchronized(this) {
        cached ?: runCatching {
            val text = ctx.assets.open("timetable/line2.csv").bufferedReader().use { it.readText() }
            fetchedLabel = Regex("fetched=(\\S+)").find(text)?.groupValues?.get(1).orEmpty()
            Line2Timetable.parse(text)
        }.getOrNull().also { cached = it }
    }
}
```

- [ ] **Step 4: 통과** — 회귀 0 + 신규 12건.

- [ ] **Step 5: 커밋** — `git add app/src/main/java/com/sinjeong/crewcalendar/domain/model/Line2Timetable.kt app/src/main/java/com/sinjeong/crewcalendar/presentation/live/Line2TimetableLoader.kt app/src/main/java/com/sinjeong/crewcalendar/domain/model/Bundled.kt app/src/test/java/com/sinjeong/crewcalendar/Line2TimetableTest.kt && git commit -m "Line2Timetable — 시간표 파서·지연·다음역·역간시간(순수 Kotlin) + 로더"`

---

### Task 3: 지도 — 방향 필터 칩 · 글자 확대 · 상단 지연 문구

**Files:**
- Modify: `app/src/main/java/com/sinjeong/crewcalendar/presentation/live/BranchLive.kt:101-118, 332-360` (`MainTrainMark`에 `statnNm`·`trainSttus` 추가)
- Modify: `app/src/test/java/com/sinjeong/crewcalendar/BranchLiveTest.kt` (`MainTrainMark(...)` 생성자 호출 갱신)
- Modify: `app/src/main/java/com/sinjeong/crewcalendar/presentation/live/MainLineMap.kt` (`MainLineMapDialog` 245~331, `CabScreen` 335~, `mineLine`/`CabHeader` 399~, `CabStatusBar` 457~, `Chip` 498~, 글자 크기를 쓰는 곳 6군데)

**Interfaces:**
- `enum class DirFilter { INNER, OUTER, ALL }` (MainLineMap.kt 안, internal).
- 상태(`CabScreen` 상위 `MainLineMapDialog`에서): `var filter by remember { mutableStateOf<DirFilter?>(null) }` — `null` = 아직 사용자가 안 눌렀음 → 그릴 때 `filter ?: mineMark?.let { if (it.inner) DirFilter.INNER else DirFilter.OUTER } ?: DirFilter.ALL`. 칩을 누르면 값이 들어가고 그 뒤로는 내 열차가 바뀌어도 유지.
- 그리기 대상: `trains.filter { eff == DirFilter.ALL || (eff == DirFilter.INNER) == it.inner || it.trainNo == mineMark?.trainNo }`.
- 글자: `filtered = eff != DirFilter.ALL`. 역명 `labelSp = if (filtered) (if (big) 16f else 13.5f) else (if (big) 14f else 11.5f)`, 열번 `badgeSp = if (filtered) (if (big) 14.5f else 12f) else (if (big) 13f else 10.5f)`. 두 값을 `CabScreen`에서 만들어 라벨/배지 그리는 함수에 인자로 넘긴다(기존 `if (big) … else …` 6군데를 이 두 변수로 교체).
- 상단 문구: `mineLine(mineMark, candidates, delay: Int?, nextSec: Int?)`.

- [ ] **Step 1: `MainTrainMark` 확장** — `data class MainTrainMark(..., val destName: String, val statnNm: String = "", val trainSttus: String = "")`. `mainTrains()`에서 `statnNm = name, trainSttus = r.trainSttus`. 기본값이 있어 `BranchLiveTest`는 그대로 컴파일된다. 회귀 0. 커밋 `"MainTrainMark에 역명·상태 원본 필드"`.

- [ ] **Step 2: 필터 칩** — `Chip`에 `onClick: (() -> Unit)? = null` 추가(`Modifier.clickable`은 `onClick != null`일 때만). `CabStatusBar(..., filter: DirFilter, onFilter: (DirFilter) -> Unit)` 오른쪽 끝에 `내선`·`외선`·`전체` 세 칩(선택된 것은 `fill = true`). `CabScreen`이 걸러진 목록을 `Canvas`에 넘긴다.

- [ ] **Step 3: 글자 크기 인자화** — 위 `labelSp`/`badgeSp` 적용. SAT 겹침 판정은 측정값 기반이라 자동 반영된다.

- [ ] **Step 4: 상단 지연 문구** — `CabScreen`에서:

```kotlin
    val ctx = LocalContext.current
    val tt = remember { Line2TimetableLoader.get(ctx) }
    val (delay, nextSec) = remember(mineMark?.trainNo, mineMark?.statnNm, mineMark?.trainSttus, nowMillis / 15_000) {
        val m = mineMark; val t = tt
        if (m == null || t == null) null to null else {
            val (d, sec) = Line2Timetable.serviceClock(LocalDateTime.now())
            val w = Line2Timetable.weekTagOf(d); val io = Line2Timetable.inoutOf(m.inner)
            val dl = t.delayMinutes(w, io, m.trainNo, m.statnNm, m.trainSttus, sec)
            dl to dl?.let { t.secondsToNextStop(w, io, m.trainNo, m.statnNm, it, sec) }
        }
    }
```

`mineLine` 끝에 `delay?.let { when { it > 0 -> " · +${it}분 지연"; it < 0 -> " · ${-it}분 빠름"; else -> " · 정시" } }.orEmpty()` + `nextSec?.let { if (it <= 0) " · 곧 도착" else " · 다음 역 ${(it + 59) / 60}분 후" }.orEmpty()`.

- [ ] **Step 5: 컴파일·회귀 0·커밋** `"본선 지도 — 방향 필터 칩(기본 내 열차 방향)·글자 확대·상단 지연/다음 역 문구"`.

---

### Task 4: 지도 — 열차 이동(8-b: 1초 미끄러짐 + 다음 역 직전까지 전진)

**Files:**
- Modify: `MainLineMap.kt` `CabScreen` 358~367(`animateFloatAsState` → `Animatable` + creep)

**Interfaces:**
- 위치 좌표 = `stationIdx + offset`(0..42.6, 순환 43). 내선은 인덱스 **증가**, 외선은 **감소** 방향.
- 순환 처리: 목표와 현재의 차가 21.5를 넘으면 ±43 보정한 "펼친" 좌표로 애니메이션하고, 그릴 때 `((v % 43) + 43) % 43`.

- [ ] **Step 1: 열번별 Animatable + creep**

```kotlin
                val anims = remember { mutableMapOf<String, Animatable<Float, AnimationVector1D>>() }
                val scope = rememberCoroutineScope()
                LaunchedEffect(trains) {   // 새 스냅샷마다
                    val alive = trains.map { it.trainNo }.toSet()
                    anims.keys.retainAll(alive)
                    trains.forEach { t ->
                        val target = t.stationIdx + t.offset
                        val a = anims.getOrPut(t.trainNo) { Animatable(target) }
                        val diff = target - a.value
                        val goal = when { diff > 21.5f -> target - 43f; diff < -21.5f -> target + 43f; else -> target }
                        scope.launch {
                            a.animateTo(goal, tween(1000, easing = LinearEasing))
                            // 8-b: 다음 스냅샷 전까지 역간 소요시간으로 다음 역 95% 지점까지 전진
                            val seg = tt?.segmentSeconds(weekTag, Line2Timetable.inoutOf(t.inner), t.trainNo, t.statnNm) ?: 120
                            val dir = if (t.inner) 1f else -1f
                            val nextStop = if (t.inner) kotlin.math.floor(goal) + 1f else kotlin.math.ceil(goal) - 1f
                            val creepTo = nextStop - dir * 0.05f
                            val remain = kotlin.math.abs(creepTo - a.value)
                            if (remain > 0.01f) a.animateTo(creepTo, tween((seg * 1000 * remain).toInt().coerceIn(1000, 240_000), easing = LinearEasing))
                        }
                    }
                }
                val placed = trains.map { t -> t to (((anims[t.trainNo]?.value ?: (t.stationIdx + t.offset)) % 43f) + 43f) % 43f }
```

`tt`·`weekTag`는 Task 3의 값을 `CabScreen`에서 내려준다. 같은 `Animatable`에 새 `animateTo`가 오면 이전 애니메이션은 취소된다(Compose 규약). `placed`는 기존 `Pair<MainTrainMark, Float>` 형태를 유지해 아래 그리기 코드는 안 바뀐다.

- [ ] **Step 2: 오류 상태** — `snap.error != null && trains.isEmpty()`이면 `anims.clear()`. 다이얼로그가 닫히면 `LaunchedEffect`가 함께 취소된다(별도 처리 불필요).

- [ ] **Step 3: 컴파일·회귀 0·커밋** `"본선 지도 — 열차 이동을 1초 보간 + 역간시간 기반 전진으로"`.

---

### Task 5: 편승 알람에 실시간 한 줄

**Files:**
- Modify: `app/src/main/java/com/sinjeong/crewcalendar/widget/DeadheadAlarm.kt` (`Alarm`, `decode/encode`, `schedule`, `pending`, `notifyNow`, `DeadheadReceiver`)
- Modify: 예약 호출부 — `grep -rn "DeadheadAlarm.schedule(" app/src/main/java`(`MainCalendarScreen.kt`). 거기서 `duty`·`date`를 갖고 있으니 `dutyTrainNumbers(duty, date)`를 넘긴다.
- Modify: `app/src/main/java/com/sinjeong/crewcalendar/presentation/live/BranchLive.kt` — `suspend fun locate(trainNos: List<String>): PositionRow?` 추가
- Create: `app/src/main/java/com/sinjeong/crewcalendar/widget/LiveLine.kt` (`liveLine` 순수 함수)
- Test: `app/src/test/java/com/sinjeong/crewcalendar/DeadheadAlarmTest.kt`(2건 추가), `Line2TimetableTest.kt`(1건 추가)

**Interfaces:**
- 저장 형식 `"yyyy-MM-dd|구간|HH:mm|문구|열번1,열번2"` (5번째 칸 선택). `decode`는 4칸도 읽는다(열번 빈 목록).
- `internal fun liveLine(row: PositionRow, delayMin: Int?): String` = `"${row.trainNo}열차 지금 ${Line2Stations.norm(row.statnNm)} ${상태}"` + (`delayMin`이 있으면 ` · +N분 지연`/` · 정시`/` · N분 빠름`). 상태: `0 진입 / 1 도착 / 2 출발 / 3 전역 출발 / 그 외 운행 중`.

- [ ] **Step 1: 테스트 추가** (`DeadheadAlarmTest`)

```kotlin
    @Test fun `5칸 레코드에 열번 후보가 실린다`() {
        val a = DeadheadAlarm.Alarm(LocalTime.of(12, 36), "양천구청역 12:36 도착", listOf("5581", "5586"))
        val s = DeadheadAlarm.encode(LocalDate.of(2026, 9, 6) to DeadheadAlarm.LEG_SECOND, a)
        assertEquals(a, DeadheadAlarm.decode(s)!!.second)
    }
    @Test fun `4칸 옛 레코드는 열번 없이 읽힌다`() {
        assertEquals(emptyList<String>(), DeadheadAlarm.decode("2026-09-06|2|12:36|문구")!!.second.trainNos)
    }
```

`Line2TimetableTest`에:

```kotlin
    @Test fun `알림 한 줄 문구`() {
        val row = com.sinjeong.crewcalendar.presentation.live.PositionRow("1002", "홍대입구", "2333", "1", "성수", "2")
        assertEquals("2333열차 지금 홍대입구 출발 · +2분 지연", com.sinjeong.crewcalendar.widget.liveLine(row, 2))
        assertEquals("2333열차 지금 홍대입구 출발", com.sinjeong.crewcalendar.widget.liveLine(row, null))
    }
```

- [ ] **Step 2: 구현**
  - `data class Alarm(val at: LocalTime, val text: String, val trainNos: List<String> = emptyList())`
  - `encode`: `"${key.first}|${key.second}|${a.at}|${a.text}|${a.trainNos.joinToString(",")}"`; `decode`: `split('|', limit = 5)`, 5번째가 있으면 `,`로 나눠 빈 것 제외. 옛 3칸(`legacy`) 분기는 그대로.
  - `schedule(ctx, date, leg, at, text, trainNos: List<String> = emptyList())`; `pending`에 `putExtra("nos", a?.trainNos?.joinToString(","))`.
  - `widget/LiveLine.kt`:

```kotlin
package com.sinjeong.crewcalendar.widget

import com.sinjeong.crewcalendar.domain.model.Line2Stations
import com.sinjeong.crewcalendar.presentation.live.PositionRow

/** 알람 알림 둘째 줄. 순수 함수 — Line2TimetableTest 가 잠근다. */
internal fun liveLine(row: PositionRow, delayMin: Int?): String {
    val st = when (row.trainSttus) { "0" -> "진입"; "1" -> "도착"; "2" -> "출발"; "3" -> "전역 출발"; else -> "운행 중" }
    val d = when { delayMin == null -> ""; delayMin > 0 -> " · +${delayMin}분 지연"; delayMin < 0 -> " · ${-delayMin}분 빠름"; else -> " · 정시" }
    return "${row.trainNo}열차 지금 ${Line2Stations.norm(row.statnNm)} $st$d"
}
```

  - `BranchLive.locate`:

```kotlin
    /** 알람 발화 시 1회: 후보 열번 중 지금 API 에 살아 있는 첫 열차. 실패·없음 → null */
    suspend fun locate(trainNos: List<String>): PositionRow? =
        fetchPositions().getOrNull()?.firstOrNull { it.trainNo in trainNos }
```

  - `notifyNow(ctx, dateStr, leg, at, body, nos: String?, done: () -> Unit)`: 알림 빌더 블록을 `private fun build(ctx, id, text, ring, dismiss, quiet: Boolean): Notification`으로 뽑고(`quiet`면 `setOnlyAlertOnce(true)`), 기존대로 먼저 `notify(id, build(..., quiet = false))`. 그 다음:

```kotlin
        val candidates = nos.orEmpty().split(',').filter { it.isNotBlank() }
        if (candidates.isEmpty()) { done(); return }
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val line = runCatching {
                kotlinx.coroutines.withTimeoutOrNull(3_000) {
                    val row = BranchLive.locate(candidates) ?: return@withTimeoutOrNull null
                    val tt = Line2TimetableLoader.get(ctx)
                    val (d, sec) = Line2Timetable.serviceClock(LocalDateTime.now())
                    val delay = tt?.delayMinutes(Line2Timetable.weekTagOf(d), Line2Timetable.inoutOf(row.updnLine.trim() == "0"),
                        row.trainNo, row.statnNm, row.trainSttus, sec)
                    liveLine(row, delay)
                }
            }.getOrNull()
            if (line != null) NotificationManagerCompat.from(ctx).notify(id, build(ctx, id, text + "\n" + line, ring, dismiss, quiet = true))
            done()
        }
```

  - `DeadheadReceiver.onReceive`의 `ACTION` 분기: `val pr = goAsync(); DeadheadAlarm.notifyNow(context, ..., intent.getStringExtra("nos")) { pr.finish() }`.
  - `AlarmRingActivity`는 인텐트의 `text`를 쓰므로 둘째 줄이 화면에도 나오게 `ring` 인텐트를 갱신본에서 다시 만든다(`build` 안에서 `text`로 생성).
  - 예약 호출부: `DeadheadAlarm.schedule(ctx, date, leg, at, text, dutyTrainNumbers(duty, date))` — 본선·지선 모두 같은 함수(`MyTrain.kt:152`).

- [ ] **Step 3: 테스트 회귀 0 + 신규 3건. 커밋** `"편승 알람 — 발화 시 실시간 위치·지연 한 줄(본선·지선), 예약 레코드에 열번 후보"`.

---

### Task 6: 설정 화면 시간표 판 표기 + 화면 검증 + 원복

- [ ] `presentation/settings/SettingsScreen.kt` 맨 아래 앱 정보 줄 근처에 `"열차 시간표 ${Line2TimetableLoader.fetchedLabel.ifBlank { "없음" }}판"` 한 줄(표시 전에 `Line2TimetableLoader.get(LocalContext.current)` 한 번 호출). 커밋 `"설정에 열차 시간표 판 표시"`.
- [ ] 에뮬 검증(위젯 에이전트가 끝난 뒤): `installDebug` → 지도 세로·가로·펼침에서 필터 3모드 스크린샷(`_미리보기_v1.6.88/M01_세로_내선.png` …), 겹침 0 육안, 상단 문구에 `지연/정시`·`다음 역` 표기, 열차 이동 연속 3장(2초 간격, `M10_이동_1.png`…), 알람: 상세시트에서 오늘 편승 알람을 **2분 뒤**로 켜고 `adb shell dumpsys notification --noredact | findstr /C:"열차 지금"`로 둘째 줄 확인 → 알람 해제. API 한도면 실응답 재생으로 대체하고 보고에 명시.
- [ ] 원복: 알람 예약 0건(`dumpsys alarm`에 crewcalendar DEADHEAD 없음), `wm size/density reset`, `font_scale 1.0`.
- [ ] 보고: 태스크별 커밋 해시, 테스트 건수(회귀 0), CSV 크기·행 수·0건 조합·inout 확인 결과, 스크린샷 목록, 못 한 것.
