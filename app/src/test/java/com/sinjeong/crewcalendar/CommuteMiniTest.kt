package com.sinjeong.crewcalendar

import com.sinjeong.crewcalendar.presentation.live.ArrivalRow
import com.sinjeong.crewcalendar.presentation.live.BLACK_ARGB
import com.sinjeong.crewcalendar.presentation.live.COMMUTE_HERE
import com.sinjeong.crewcalendar.presentation.live.COMMUTE_LINE_FALLBACK_ARGB
import com.sinjeong.crewcalendar.presentation.live.COMMUTE_NEAR
import com.sinjeong.crewcalendar.presentation.live.COMMUTE_SLOTS
import com.sinjeong.crewcalendar.presentation.live.CommuteStation
import com.sinjeong.crewcalendar.presentation.live.LINE_ARGB
import com.sinjeong.crewcalendar.presentation.live.LINE_NAMES
import com.sinjeong.crewcalendar.presentation.live.LINE_NUMS
import com.sinjeong.crewcalendar.presentation.live.PositionRow
import com.sinjeong.crewcalendar.presentation.live.StationRow
import com.sinjeong.crewcalendar.presentation.live.WHITE_ARGB
import com.sinjeong.crewcalendar.presentation.live.approachFromHigher
import com.sinjeong.crewcalendar.presentation.live.chipInkArgb
import com.sinjeong.crewcalendar.presentation.live.commuteLead
import com.sinjeong.crewcalendar.presentation.live.commuteOffset
import com.sinjeong.crewcalendar.presentation.live.commuteDestLine
import com.sinjeong.crewcalendar.presentation.live.commuteStatusLine
import com.sinjeong.crewcalendar.presentation.live.commuteStops
import com.sinjeong.crewcalendar.presentation.live.commuteTrains
import com.sinjeong.crewcalendar.presentation.live.stepCommute
import com.sinjeong.crewcalendar.presentation.live.contrastRatio
import com.sinjeong.crewcalendar.presentation.live.destText
import com.sinjeong.crewcalendar.presentation.live.frKey
import com.sinjeong.crewcalendar.presentation.live.lineArgb
import com.sinjeong.crewcalendar.presentation.live.lineNumOf
import com.sinjeong.crewcalendar.presentation.live.normStop
import com.sinjeong.crewcalendar.presentation.live.posDirMatches
import com.sinjeong.crewcalendar.presentation.live.sttusText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 출퇴근 역 **미니 노선**의 열차 자리·호선 색·역 차례를 잠근다.
 *
 * ## ⚠ v1.7.15 ① 에서 자료가 통째로 바뀌었다 — **도착 예보 → 실시간 위치**
 *
 * 카스: *"출근역 살펴보니까 **실시간 지하철위치가 아닌거 같은데?**"* → *"난 실시간 위치를 원하지"*.
 * v1.7.14 까지는 전광판 문장(`arvlMsg2` = `"3번째 전역"`·`"8분 후"`)을 정규식으로 읽어 칸을
 * **추정**했고 모르면 맨 왼쪽에 놓았다. 그래서 5역 밖 열차가 두 역 앞처럼 보였다.
 * 이제 `realtimePosition` 의 `statnNm` 을 다섯 칸 이름과 **글자로 견주고**, 어느 칸에도
 * 없으면 **아예 안 그린다.** 그 판정을 여기서 잠근다.
 *
 * 위치 추정 함수 다섯(`commuteSlot`·`commuteAdvance`·`commuteApproaching`·`commuteAtStation`·
 * `positionText`)과 그 테스트는 **지웠다** — 남겨 두면 다시 쓴다.
 */
class CommuteMiniTest {

    /* ── 칸 ──────────────────────────────────────────────────── */

    @Test
    fun `칸은 다섯이고 등록역이 한가운데다`() {
        assertEquals(2, COMMUTE_NEAR)
        assertEquals(5, COMMUTE_SLOTS)
        assertEquals(2, COMMUTE_HERE)
        // 앞 2 · 등록역 · 뒤 2 — 카스가 든 `김포공항-송정-마곡-발산-우장산` 이 딱 이 꼴이다.
        assertEquals(COMMUTE_SLOTS, COMMUTE_NEAR * 2 + 1)
    }

    /* ── ① 방향: 위치 API 의 숫자 ↔ 도착 API 의 낱말 ──────────
     *
     * 2026-09-07 20:27~28 실호출로 **같은 순간 두 API 를 나란히** 불러 열번으로 맞대 봤다:
     *   5호선 `5656` 도착 `상행` ↔ 위치 `"0"`   ·  5호선 `5703` 도착 `하행` ↔ 위치 `"1"`
     *   2호선 `8414`·`6416` 도착 `내선` ↔ 위치 `"0"` · 2호선 `4439`·`7443` 도착 `외선` ↔ `"1"`
     * **여섯 대에 예외 0건**이다.
     */

    @Test
    fun `위치 0 은 상행 내선 이고 1 은 하행 외선 이다`() {
        assertTrue(posDirMatches("0", "상행"))
        assertTrue(posDirMatches("0", "내선"))
        assertTrue(posDirMatches("1", "하행"))
        assertTrue(posDirMatches("1", "외선"))
        assertFalse(posDirMatches("1", "상행"))
        assertFalse(posDirMatches("1", "내선"))
        assertFalse(posDirMatches("0", "하행"))
        assertFalse(posDirMatches("0", "외선"))
        // 공백이 섞여도 같은 판정
        assertTrue(posDirMatches(" 0 ", " 내선 "))
    }

    /**
     * ⚠ **모르면 안 그린다.** 방향을 모르는 채로 그리느니 비우는 쪽이 맞다 —
     * 이번 회차가 고치는 것이 바로 "모르면 아무 데나 놓기"다.
     */
    @Test
    fun `모르는 방향 낱말과 빈 값은 아무것도 안 맞는다`() {
        assertFalse(posDirMatches("0", ""))
        assertFalse(posDirMatches("", "상행"))
        assertFalse(posDirMatches("", ""))
        assertFalse(posDirMatches("0", "위쪽"))
        assertFalse(posDirMatches("9", "상행"))
    }

    /* ── ① 역 이름 대조 ──────────────────────────────────────── */

    /**
     * 위치 응답은 괄호 별칭을 달고 오는데(`굽은다리(강동구민회관앞)`·`신정(은행정)`)
     * 역 목록 API 의 `STATION_NM` 은 **괄호 없이** 온다 — 2026-09-07 `05호선` 56행 전수에
     * 괄호가 든 이름이 **0개**였다.
     */
    @Test
    fun `괄호 별칭은 떼고 역 은 안 뗀다`() {
        assertEquals("굽은다리", normStop("굽은다리(강동구민회관앞)"))
        assertEquals("신정", normStop("신정(은행정)"))
        assertEquals("오목교", normStop("오목교(목동운동장앞)"))
        assertEquals("마곡", normStop(" 마곡 "))
        // ⚠ `서울역` 을 `서울`(GTX-A 의 다른 역)로 바꾸지 않는다
        assertEquals("서울역", normStop("서울역"))
        assertEquals("", normStop(""))
    }

    @Test
    fun `종착역명은 꼬리를 떼고 행 을 붙인다`() {
        assertEquals("성수행", destText("성수종착"))
        assertEquals("신도림행", destText("신도림지선"))
        assertEquals("방화행", destText("방화"))
        assertEquals("까치산행", destText("까치산행"))     // 이미 `행` 이면 두 번 안 붙인다
        assertEquals("신정행", destText("신정(은행정)"))
        assertEquals("", destText(""))
        assertEquals("", destText("   "))
    }

    @Test
    fun `상태 낱말은 본선 지도와 같은 표다`() {
        assertEquals("진입", sttusText("0"))
        assertEquals("도착", sttusText("1"))
        assertEquals("출발", sttusText("2"))
        assertEquals("접근 중", sttusText("3"))
        assertEquals("운행 중", sttusText("99"))
        assertEquals("운행 중", sttusText(""))
        assertEquals("운행 중", sttusText("abc"))
    }

    /* ── ② 미세 위치는 **진행 방향**으로 붙는다 ─────────────── */

    @Test
    fun `진입은 진행 방향 앞 출발은 뒤다`() {
        // 왼쪽 → 오른쪽(forward)
        assertEquals(-0.15f, commuteOffset("0", true), 1e-6f)
        assertEquals(0f, commuteOffset("1", true), 1e-6f)
        assertEquals(0.15f, commuteOffset("2", true), 1e-6f)
        assertEquals(-0.6f, commuteOffset("3", true), 1e-6f)
        // 오른쪽 → 왼쪽이면 **부호가 통째로 뒤집힌다**
        assertEquals(0.15f, commuteOffset("0", false), 1e-6f)
        assertEquals(0f, commuteOffset("1", false), 1e-6f)
        assertEquals(-0.15f, commuteOffset("2", false), 1e-6f)
        assertEquals(0.6f, commuteOffset("3", false), 1e-6f)
        // 모르는 코드는 역 위(0) — 예외 없음
        assertEquals(0f, commuteOffset("99", true), 1e-6f)
        assertEquals(0f, commuteOffset("", false), 1e-6f)
    }

    /* ── ① 열차 고르기 ──────────────────────────────────────── */

    /** 마곡(5호선) 다섯 칸 — 지리 오름차순 고정. `상행` 은 오른쪽에서 온다(`fromHigher`). */
    private val magokUp = CommuteStation(
        "마곡", "1005", "상행",
        listOf("김포공항", "송정", "마곡", "발산", "우장산"), fromHigher = true,
    )
    private val magokDown = magokUp.copy(updnLine = "하행", fromHigher = false)

    private fun pos(
        no: String, statn: String, updn: String, sttus: String = "1",
        line: String = "1005", dest: String = "방화", lstcar: String = "0",
    ) = PositionRow(line, statn, no, updn, dest, sttus, lstcarAt = lstcar)

    /**
     * 2026-09-07 20:27:49 `realtimePosition/5호선` 실응답에서 마곡 부근 세 대를 그대로 옮겼다
     * (+ 다섯 칸 밖 한 대). `5656` 은 같은 순간 마곡 도착 응답에도 `상행` 으로 떠 있었다.
     */
    private val line5rows = listOf(
        pos("5656", "발산", "0", "1"),                       // 상행 · 발산 도착
        pos("5177", "발산", "1", "1", dest = "하남검단산"),    // 하행 · 발산 도착
        pos("5703", "김포공항", "1", "1", dest = "마천"),      // 하행 · 김포공항 도착
        pos("5190", "굽은다리(강동구민회관앞)", "0", "3"),      // **다섯 칸 밖** — 안 그린다
        pos("2340", "발산", "0", "1", line = "1002"),         // 다른 호선 — 안 그린다
    )

    @Test
    fun `같은 호선 같은 방향 그리고 다섯 칸 안 인 열차만 그린다`() {
        val up = commuteTrains(line5rows, magokUp)
        assertEquals(listOf("5656"), up.map { it.trainNo })
        assertEquals(3, up[0].slot)                          // 발산 = 오른쪽 첫 칸
        assertEquals("도착", up[0].status)
        assertEquals("방화행", up[0].dest)

        val down = commuteTrains(line5rows, magokDown)
        assertEquals(listOf("5177", "5703"), down.map { it.trainNo })
        assertEquals(listOf(3, 0), down.map { it.slot })
    }

    /**
     * ⚠ **이번 회차가 고치는 거짓말** — v1.7.14 는 `commuteSlot` 이 `coerceIn(0, here)` 라
     * 다섯 칸 밖 열차를 **맨 왼쪽 칸에 밀어 넣었다**(5역 밖이 두 역 앞처럼 보였다).
     */
    @Test
    fun `다섯 칸 밖 열차는 맨 왼쪽이 아니라 아예 안 그려진다`() {
        val far = listOf(
            pos("5190", "굽은다리(강동구민회관앞)", "0"),
            pos("5192", "여의도", "0"),
            pos("5194", "군자", "0"),
        )
        assertEquals(emptyList<String>(), commuteTrains(far, magokUp).map { it.trainNo })
        // 한 대만 칸 안에 있으면 **그 한 대만** 나온다
        assertEquals(
            listOf("5656"),
            commuteTrains(far + pos("5656", "송정", "0"), magokUp).map { it.trainNo },
        )
    }

    /** 괄호 별칭이 붙은 위치 이름도 칸 이름과 만난다(`신정(은행정)` ↔ `신정`). */
    @Test
    fun `괄호 별칭 이름도 칸을 찾는다`() {
        val s = CommuteStation(
            "목동", "1005", "상행",
            listOf("까치산", "신정", "목동", "오목교", "양평"), fromHigher = true,
        )
        val got = commuteTrains(listOf(pos("5601", "신정(은행정)", "0")), s)
        assertEquals(listOf("5601"), got.map { it.trainNo })
        assertEquals(1, got[0].slot)
    }

    /**
     * ⚠ **이름을 못 얻은 등록값은 빈 목록**이다(옛 저장값) — 견줄 상대가 없다.
     * 화면은 그때 *"역을 다시 등록해 주세요"* 라고 말한다.
     */
    @Test
    fun `다섯 칸 이름이 없으면 아무것도 안 그린다`() {
        val old = CommuteStation("마곡", "1005", "상행")
        assertEquals(emptyList<String>(), commuteTrains(line5rows, old).map { it.trainNo })
        val short = old.copy(stops = listOf("김포공항", "송정", "마곡", "발산"))
        assertEquals(emptyList<String>(), commuteTrains(line5rows, short).map { it.trainNo })
    }

    @Test
    fun `자리는 늘 0 과 마지막 칸 사이고 예외가 없다`() {
        val last = (COMMUTE_SLOTS - 1).toFloat()
        val sttuses = listOf("", "0", "1", "2", "3", "9", "99", "abc")
        for (name in magokUp.stops) for (c in sttuses) for (s in listOf(magokUp, magokDown)) {
            val got = commuteTrains(listOf(pos("t", name, if (s.fromHigher) "0" else "1", c)), s)
            got.forEach {
                assertTrue("$name/$c → ${it.pos}", it.pos in 0f..last && !it.pos.isNaN())
            }
        }
        // 끝 칸에서 **진행 방향 밖으로** 나가려 해도 눌러 앉힌다(하행 = 왼→오른, 우장산 출발).
        val edge = commuteTrains(listOf(pos("t", "우장산", "1", "2")), magokDown)
        assertEquals(4f, edge[0].pos, 1e-6f)
        // 반대 방향은 그 자리에서 **뒤로** 벌어진다(상행 = 오른→왼, 우장산 출발 → 4 − 0.15).
        val back = commuteTrains(listOf(pos("t", "우장산", "0", "2")), magokUp)
        assertEquals(3.85f, back[0].pos, 1e-6f)
    }

    @Test
    fun `같은 열번이 두 줄로 와도 한 대만 그린다`() {
        val dup = listOf(pos("5656", "발산", "0"), pos("5656", "송정", "0"))
        assertEquals(1, commuteTrains(dup, magokUp).size)
    }

    /* ── 오른쪽 두 줄이 말할 한 대 ──────────────────────────── */

    /**
     * 지나간 열차는 안 고른다 — `상행`(오른쪽에서 옴)이면 **등록역 칸 오른쪽**이 다가오는 쪽이다.
     */
    @Test
    fun `오른쪽 줄은 다가오는 쪽에서 가장 가까운 열차다`() {
        val up = commuteTrains(
            listOf(pos("far", "우장산", "0"), pos("near", "발산", "0"), pos("gone", "송정", "0")),
            magokUp,
        )
        assertEquals("near", commuteLead(up, fromHigher = true)?.trainNo)
        val down = commuteTrains(
            listOf(pos("far", "김포공항", "1"), pos("near", "송정", "1"), pos("gone", "발산", "1")),
            magokDown,
        )
        assertEquals("near", commuteLead(down, fromHigher = false)?.trainNo)
        // 등록역 칸에 선 열차가 있으면 그것이 가장 가깝다
        val here = commuteTrains(listOf(pos("here", "마곡", "0"), pos("far", "우장산", "0")), magokUp)
        assertEquals("here", commuteLead(here, fromHigher = true)?.trainNo)
        // 지나간 쪽에만 있으면 고를 것이 없다(빈 상태 문구로 떨어진다)
        val passed = commuteTrains(listOf(pos("gone", "송정", "0")), magokUp)
        assertNull(commuteLead(passed, fromHigher = true))
        assertNull(commuteLead(emptyList(), fromHigher = false))
    }

    /* ── 호선 색 ─────────────────────────────────────────────── */

    @Test
    fun `아는 호선은 서울 공식 노선색`() {
        assertEquals(0xFF0052A4L, lineArgb("1001")) // 1호선 남색
        assertEquals(0xFF00A84DL, lineArgb("1002")) // 2호선 초록
        assertEquals(0xFF996CACL, lineArgb("1005")) // 5호선 보라
        assertEquals(0xFF747F00L, lineArgb("1007")) // 7호선 올리브
        assertEquals(0xFFD4003BL, lineArgb("1077")) // 신분당 빨강
        assertEquals(0xFF81A914L, lineArgb("1093")) // 서해
    }

    @Test
    fun `모르는 id 는 기본 회색 - 앱이 안 죽는다`() {
        assertEquals(COMMUTE_LINE_FALLBACK_ARGB, lineArgb("9999"))
        assertEquals(COMMUTE_LINE_FALLBACK_ARGB, lineArgb(""))
        assertEquals(COMMUTE_LINE_FALLBACK_ARGB, lineArgb("1002 "))  // 공백 섞임 = 모르는 값
        assertNotEquals(COMMUTE_LINE_FALLBACK_ARGB, lineArgb("1002"))
    }

    @Test
    fun `이름 표와 색 표의 키가 한 벌이다`() {
        // v1.7.12 ① 은 여섯 id(1061·1069·1071·1078·1094·1095)에 **이름만 있고 색이 없어**
        // 그 노선 기관차가 조용히 회색으로 떨어졌다 — 화면이 죽지 않아 눈으로만은 못 잡는다.
        // LINE_NAMES·LINE_ARGB 가 `internal` 인 이유가 이 대조다(둘 다 KDoc 에 적혀 있다).
        assertEquals("이름만 있고 색이 없는 id", emptySet<String>(), LINE_NAMES.keys - LINE_ARGB.keys)
        assertEquals("색만 있고 이름이 없는 id", emptySet<String>(), LINE_ARGB.keys - LINE_NAMES.keys)
    }

    @Test
    fun `모든 호선 색이 완전 불투명하다`() {
        // 알파가 빠지면(0x00…) 기관차가 통째로 안 보인다.
        listOf("1001", "1002", "1003", "1004", "1005", "1006", "1007", "1008", "1009",
            "1032", "1063", "1065", "1067", "1075", "1077", "1081", "1092", "1093", "9999")
            .forEach { assertEquals(it, 0xFFL, (lineArgb(it) ushr 24) and 0xFFL) }
    }

    /* ── ④ 칩 글자색 대비 (v1.7.14) ──────────────────────────── */

    /**
     * 카스: *"마곡을 선택했다면 **5호선 마곡 아이콘을 5호선 색으로** 해줘야지"* — 고른 칩
     * 바탕이 호선 색이 되면 글자색을 흰색으로 못 박을 수 없다.
     */
    @Test
    fun `대비비 산수가 WCAG 값과 맞는다`() {
        assertEquals(21.0, contrastRatio(WHITE_ARGB, BLACK_ARGB), 1e-6)
        assertEquals(1.0, contrastRatio(0xFF123456L, 0xFF123456L), 1e-9)
        // 5호선 보라 위 흰 글자가 **4.5:1 에 못 미친다** — v1.7.14 ④ 의 출발점이다.
        assertEquals(4.12, contrastRatio(0xFF996CACL, WHITE_ARGB), 0.01)
        assertEquals(5.10, contrastRatio(0xFF996CACL, BLACK_ARGB), 0.01)
    }

    @Test
    fun `칩 글자색은 흰 검 중 대비가 큰 쪽이다`() {
        assertEquals(BLACK_ARGB, chipInkArgb(0xFF996CACL))   // 5호선 보라 → 검정 5.10
        assertEquals(WHITE_ARGB, chipInkArgb(0xFF0052A4L))   // 1호선 남색 → 흰   7.66
        assertEquals(WHITE_ARGB, chipInkArgb(0xFF003DA5L))   // 경강     → 흰   9.50
        assertEquals(BLACK_ARGB, chipInkArgb(0xFF00A84DL))   // 2호선 초록 → 검정 6.71
    }

    /**
     * **호선 색 스물넷 + 기본 회색이 전부 AA(4.5:1)를 넘는다.** 넘지 않는 색이 새로 들어오면
     * 여기가 먼저 깨진다 — 화면으로는 못 잡는 종류의 결함이다(작은 글자라 눈에 안 띈다).
     */
    @Test
    fun `모든 호선 색에서 칩 글자가 AA 를 넘는다`() {
        (LINE_ARGB.values + COMMUTE_LINE_FALLBACK_ARGB).forEach { c ->
            val r = contrastRatio(c, chipInkArgb(c))
            assertTrue("#%06X → %.2f:1".format(c and 0xFFFFFF, r), r >= 4.5)
        }
        // 실측 하한은 8호선 `#E6186C`(검정 4.71:1) 이다 — 여유가 0.21 뿐이니 색을 바꾸면 다시 재라.
        assertEquals(4.71, contrastRatio(0xFFE6186CL, chipInkArgb(0xFFE6186CL)), 0.01)
    }

    /* ── ② 다섯 칸 이름 — 지리 순서 고정 ────────────────────── */

    /** 2026-09-07 `SearchSTNBySubwayLineInfo/05호선` 실응답에서 뽑은 앞머리 아홉 역 */
    private val line5 = listOf(
        StationRow("510", "방화"), StationRow("511", "개화산"), StationRow("512", "김포공항"),
        StationRow("513", "송정"), StationRow("514", "마곡"), StationRow("515", "발산"),
        StationRow("516", "우장산"), StationRow("517", "화곡"), StationRow("518", "까치산"),
        // 마천지선은 접두가 `P` 라 갈래가 다르다(실응답 그대로)
        StationRow("P549", "둔촌동"), StationRow("P550", "올림픽공원"),
    )

    /** 2호선 — 본선 `2xx` 과 신정지선 `234-N` 이 섞여 온다(실응답 그대로) */
    private val line2 = listOf(
        StationRow("231", "신대방"), StationRow("232", "구로디지털단지"), StationRow("233", "대림"),
        StationRow("234", "신도림"), StationRow("234-1", "도림천"), StationRow("234-2", "양천구청"),
        StationRow("234-3", "신정네거리"), StationRow("234-4", "까치산"),
        StationRow("235", "문래"), StationRow("236", "영등포구청"), StationRow("237", "당산"),
    )

    /**
     * 카스의 예 그대로: *"마곡역이면 **김포공항-송정-마곡-발산-우장산**"*.
     *
     * ⚠ **v1.7.14 의 `approachFromHigher` 인자가 없어졌다** — 카스가 물렸다:
     * *"상행을 고르면 **열차가 반대방향으로 가면 되지**"*. 차례는 `FR_CODE` 오름차순 하나다.
     */
    @Test
    fun `마곡의 다섯 칸은 김포공항 송정 마곡 발산 우장산`() {
        assertEquals(
            listOf("김포공항", "송정", "마곡", "발산", "우장산"),
            commuteStops(line5, "마곡"),
        )
        // `역` 을 붙여 쳐도 같은 줄을 찾는다
        assertEquals(commuteStops(line5, "마곡"), commuteStops(line5, "마곡역"))
    }

    @Test
    fun `끝 역은 모자라는 자리가 빈칸이고 개수는 늘 다섯이다`() {
        assertEquals(listOf("", "", "방화", "개화산", "김포공항"), commuteStops(line5, "방화"))
        assertEquals(COMMUTE_SLOTS, commuteStops(line5, "방화").size)
    }

    /**
     * ⚠ **갈래를 가르는 것이 이 함수의 핵심이다.** 본선 역(가지번호 없음)은 지선 줄을 안 본다 —
     * 신도림 뒤가 `도림천`(234-1)이 아니라 **`문래`(235)** 여야 한다.
     */
    @Test
    fun `본선 역은 지선 줄을 건너뛴다`() {
        assertEquals(
            listOf("구로디지털단지", "대림", "신도림", "문래", "영등포구청"),
            commuteStops(line2, "신도림"),
        )
    }

    /** 반대로 **지선 역**은 제 갈래(같은 큰 번호)만 본다 — 까치산은 종점이라 뒤가 비어야 한다. */
    @Test
    fun `지선 역은 제 갈래만 보고 종점 뒤는 빈칸이다`() {
        assertEquals(
            listOf("양천구청", "신정네거리", "까치산", "", ""),
            commuteStops(line2, "까치산"),
        )
        assertEquals(
            listOf("", "신도림", "도림천", "양천구청", "신정네거리"),
            commuteStops(line2, "도림천"),
        )
    }

    @Test
    fun `접두가 다르면 다른 갈래다`() {
        // 5호선 까치산(518) 뒤는 마천지선 `P549` 가 아니다 — 이 표본에는 519가 없어 빈칸이다.
        assertEquals(
            listOf("우장산", "화곡", "까치산", "", ""),
            commuteStops(line5, "까치산"),
        )
    }

    @Test
    fun `모르는 역이면 빈 목록이고 예외가 없다`() {
        assertEquals(emptyList<String>(), commuteStops(line5, "없는역"))
        assertEquals(emptyList<String>(), commuteStops(emptyList(), "마곡"))
        assertEquals(emptyList<String>(), commuteStops(line5, ""))
    }

    @Test
    fun `FR_CODE 는 접두 번호 가지번호 셋으로 읽는다`() {
        assertEquals(Triple("", 514, 0), frKey("514"))
        assertEquals(Triple("", 234, 4), frKey("234-4"))
        assertEquals(Triple("P", 550, 0), frKey("P550"))
        assertEquals(Triple("K", 314, 0), frKey(" K314 "))
        // 못 읽는 꼴도 예외 없이 떨어진다
        assertEquals(Triple("???", 0, 0), frKey("???"))
        assertEquals(Triple("", 0, 0), frKey(""))
    }

    /**
     * 어느 쪽에서 오나 — 응답의 `statnFid`(이전역)와 `statnId`(이 역) 비교 하나다.
     * 2026-09-07 마곡 실호출: `statnId=1005000514` · `statnFid=1005000515`(발산) = **상행**.
     *
     * ⚠ v1.7.15 ② 부터 이 값은 **역 차례가 아니라 기관차 진행 방향**을 정한다.
     * ⚠ **낱말에서 유도하면 안 된다** — 5호선 `상행` 은 내림차순인데 2호선 `내선` 은 오름차순이다
     *   (신도림 내선 실응답 `statnFid=1002000233 < statnId=1002000234` → false).
     */
    @Test
    fun `statnFid 가 크면 큰 번호 쪽에서 온다`() {
        val up = ArrivalRow(
            "5656", "방화", 120, "5", "1005", "상행",
            statnId = "1005000514", statnFid = "1005000515",
        )
        assertTrue(approachFromHigher(up))
        val down = up.copy(statnFid = "1005000513")
        assertEquals(false, approachFromHigher(down))
        // 2호선 내선은 **오름차순**이다 — 같은 `"0"` 인데 5호선 상행과 지리 방향이 반대다.
        val inner = ArrivalRow(
            "8414", "성수", 10, "0", "1002", "내선",
            statnId = "1002000234", statnFid = "1002000233",
        )
        assertEquals(false, approachFromHigher(inner))
        assertTrue(approachFromHigher(inner.copy(updnLine = "외선", statnFid = "1002000235")))
        // 값이 없거나 숫자가 아니면 오름차순(false) — 카스가 든 예의 차례다.
        assertEquals(false, approachFromHigher(null))
        assertEquals(false, approachFromHigher(ArrivalRow("x", "방화", 1, "99")))
        assertEquals(false, approachFromHigher(up.copy(statnFid = "abc")))
    }

    /* ── v1.7.16 ② 1초 보간 — 규칙 셋을 잠근다 ─────────────────── */

    /**
     * 확정 표 v1.7.5 그대로다. 규칙은 [stepCommute] → `stepMotion` **한 곳**이고 여기서는
     * 그것이 출퇴근 칸(0..4)에서도 그대로 사는지만 본다.
     *
     * ⚠ 속도는 **순수 등속 가정**(`DEFAULT_SEG_SEC` 110초)이다 — 출퇴근 역은 어느 호선이든
     * 등록될 수 있고 자산은 2호선 시간표뿐이라 구간 소요를 모른다.
     */
    private fun train(pos: Float, holding: Boolean = false, no: String = "t") =
        commuteTrains(listOf(pos(no, "마곡", "1", if (holding) "1" else "2")), magokDown)
            .single().copy(pos = pos, holding = holding)

    @Test
    fun `보간 ⓐ 앞으로만 간다`() {
        val t = train(1.5f)
        // 왼쪽 → 오른쪽(하행)에서 목표가 **뒤**(1.2)면 버린다 — 뒤로 안 미끄러진다
        val m0 = stepCommute(null, t.copy(pos = 1.5f), forward = true, nowMs = 0L)
        val m1 = stepCommute(m0, t.copy(pos = 1.2f), forward = true, nowMs = 10_000L)
        assertTrue(m1.pos > 1.5f)
        // 오른쪽 → 왼쪽(상행)도 같다 — 좌표가 줄어드는 쪽이 "앞"이다
        val n0 = stepCommute(null, t.copy(pos = 2.5f), forward = false, nowMs = 0L)
        val n1 = stepCommute(n0, t.copy(pos = 2.8f), forward = false, nowMs = 10_000L)
        assertTrue(n1.pos < 2.5f)
    }

    @Test
    fun `보간 ⓑ 다음 칸을 안 넘는다`() {
        val start = stepCommute(null, train(1.15f), forward = true, nowMs = 0L)
        // 110초 × 다섯 칸을 흘려도 예측은 2.0 을 못 넘는다(0.95 지점에서 멈춘다)
        val far = stepCommute(start, train(1.15f), forward = true, nowMs = 600_000L)
        assertEquals(1.95f, far.pos, 1e-4f)
        // 칸 밖(0..4)으로도 안 나간다 — 5칸은 순환이 아니라 끝이 있다
        val edge = stepCommute(
            stepCommute(null, train(3.9f), forward = true, nowMs = 0L),
            train(3.9f), forward = true, nowMs = 600_000L,
        )
        assertTrue(edge.pos <= (COMMUTE_SLOTS - 1).toFloat())
    }

    @Test
    fun `보간 ⓒ 도착·진입이면 역 자리에 선다`() {
        // `1`(도착)·`0`(진입) 은 holding — 예측을 아예 안 돌린다
        assertTrue(commuteTrains(listOf(pos("a", "마곡", "1", "1")), magokDown).single().holding)
        assertTrue(commuteTrains(listOf(pos("a", "마곡", "1", "0")), magokDown).single().holding)
        assertFalse(commuteTrains(listOf(pos("a", "마곡", "1", "2")), magokDown).single().holding)
        assertFalse(commuteTrains(listOf(pos("a", "마곡", "1", "3")), magokDown).single().holding)

        val hold = train(2f, holding = true)
        val m0 = stepCommute(null, hold, forward = true, nowMs = 0L)
        val m1 = stepCommute(m0, hold, forward = true, nowMs = 300_000L)
        assertEquals(2f, m1.pos, 1e-4f)          // 5분이 흘러도 역 위 그대로
    }

    /* ── v1.7.16 ③ 기준 시각 · ⑥ 막차 글줄 ──────────────────── */

    @Test
    fun `막차는 굵은 첫 줄에 붙는다`() {
        val plain = commuteTrains(listOf(pos("5656", "마곡", "1", "1")), magokDown).single()
        assertFalse(plain.lastCar)
        assertEquals("도착", commuteStatusLine(plain))

        val last = commuteTrains(
            listOf(pos("5656", "마곡", "1", "1", lstcar = "1")), magokDown).single()
        assertTrue(last.lastCar)
        assertEquals("도착 · 막차", commuteStatusLine(last))
    }

    @Test
    fun `둘째 줄은 행선과 기준 시각이다`() {
        assertEquals("방화행 · 14:20:45", commuteDestLine("방화행", "2026-09-08 14:20:45"))
        // 행선을 모르면 시각만 — 가운뎃점이 홀로 남지 않는다
        assertEquals("14:20:45", commuteDestLine("", "2026-09-08 14:20:45"))
        // 기준 시각을 못 받았으면 빗금이 그 자리를 지킨다(빈칸으로 두지 않는다)
        assertEquals("방화행 · --:--:--", commuteDestLine("방화행", ""))
    }

    @Test
    fun `호선 id 마다 역 목록 API 의 노선 낱말이 있다`() {
        assertEquals("05호선", lineNumOf("1005"))
        assertEquals("02호선", lineNumOf("1002"))
        assertEquals("GTX-A", lineNumOf("1032"))
        // 아직 응답에 없는 노선·모르는 id 는 null — 이웃 조회를 아예 안 한다(화면은 산다).
        assertNull(lineNumOf("1095"))
        assertNull(lineNumOf("9999"))
        // 이름 표에 있는 id 는 **동북선 하나만** 빠진다(개통 전이라 응답에 없다).
        assertEquals(setOf("1095"), LINE_NAMES.keys - LINE_NUMS.keys)
        assertEquals(emptySet<String>(), LINE_NUMS.keys - LINE_NAMES.keys)
    }
}
