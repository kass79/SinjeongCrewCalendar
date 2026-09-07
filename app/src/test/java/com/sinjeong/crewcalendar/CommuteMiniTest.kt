package com.sinjeong.crewcalendar

import com.sinjeong.crewcalendar.presentation.live.ArrivalRow
import com.sinjeong.crewcalendar.presentation.live.BLACK_ARGB
import com.sinjeong.crewcalendar.presentation.live.COMMUTE_HERE
import com.sinjeong.crewcalendar.presentation.live.COMMUTE_LINE_FALLBACK_ARGB
import com.sinjeong.crewcalendar.presentation.live.COMMUTE_NEAR
import com.sinjeong.crewcalendar.presentation.live.COMMUTE_SLOTS
import com.sinjeong.crewcalendar.presentation.live.LINE_ARGB
import com.sinjeong.crewcalendar.presentation.live.LINE_NAMES
import com.sinjeong.crewcalendar.presentation.live.LINE_NUMS
import com.sinjeong.crewcalendar.presentation.live.StationRow
import com.sinjeong.crewcalendar.presentation.live.WHITE_ARGB
import com.sinjeong.crewcalendar.presentation.live.approachFromHigher
import com.sinjeong.crewcalendar.presentation.live.chipInkArgb
import com.sinjeong.crewcalendar.presentation.live.commuteAdvance
import com.sinjeong.crewcalendar.presentation.live.commuteNeighbors
import com.sinjeong.crewcalendar.presentation.live.commuteSlot
import com.sinjeong.crewcalendar.presentation.live.contrastRatio
import com.sinjeong.crewcalendar.presentation.live.frKey
import com.sinjeong.crewcalendar.presentation.live.lineArgb
import com.sinjeong.crewcalendar.presentation.live.lineNumOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 출퇴근 역 **미니 노선**(v1.7.12 ①)의 칸 좌표·호선 색·앞뒤 2역 차례를 잠근다.
 *
 * ⚠ **v1.7.14 ⑤ 에서 자가 통째로 바뀌었다** — 칸이 6개(왼쪽 끝 `5번째 전역+` … 오른쪽 끝
 * 등록역)에서 **5개**가 되고 **등록역이 한가운데**([COMMUTE_HERE] = 2)로 왔다. 그래서 열차는
 * `0..2` 에만 서고 오른쪽 두 칸은 **열차가 이 다음에 갈 역**이다.
 *
 * `arvlMsg2` 는 승강장 전광판 문장이라 꼴이 여러 가지고, **모르는 꼴이 와도 절대 예외가 나면
 * 안 된다** — 상세시트가 통째로 죽는다.
 */
class CommuteMiniTest {

    /** 열차가 설 수 있는 마지막 칸 = 등록한 역 칸. v1.7.13 까지는 `COMMUTE_SLOTS - 1` 이었다. */
    private val last = COMMUTE_HERE

    @Test
    fun `칸은 다섯이고 등록역이 한가운데다`() {
        assertEquals(2, COMMUTE_NEAR)
        assertEquals(5, COMMUTE_SLOTS)
        assertEquals(2, COMMUTE_HERE)
        // 앞 2 · 등록역 · 뒤 2 — 카스가 든 `김포공항-송정-마곡-발산-우장산` 이 딱 이 꼴이다.
        assertEquals(COMMUTE_SLOTS, COMMUTE_NEAR * 2 + 1)
    }

    /* ── arvlCd 우선 ─────────────────────────────────────────── */

    @Test
    fun `진입 0 도착 1 은 글자와 무관하게 역 점 위`() {
        assertEquals(last, commuteSlot("3번째 전역", "0"))
        assertEquals(last, commuteSlot("6분 후", "1"))
        assertEquals(last, commuteSlot("", "0"))
    }

    @Test
    fun `출발 2 도 좌표는 나온다 - 화면이 거를 뿐 함수는 안 죽는다`() {
        // `commuteApproaching` 이 2 를 빼지만 함수 자체는 어떤 값이 와도 0..last 를 돌려준다.
        assertTrue(commuteSlot("당역 출발", "2") in 0..last)
        assertTrue(commuteSlot("", "2") in 0..last)
    }

    /* ── arvlMsg2 글자꼴 ─────────────────────────────────────── */

    @Test
    fun `N번째 전역 은 2 빼기 N`() {
        // v1.7.14 ⑤ — 칸이 여섯에서 **다섯**이 되고 등록역이 가운데(2)로 왔다.
        // 카스: *"그냥 전후 두단계로 하자."* 그래서 2역보다 먼 열차는 **다 맨 왼쪽**이다.
        assertEquals(2, commuteSlot("0번째 전역", "99"))
        assertEquals(1, commuteSlot("1번째 전역", "99"))
        assertEquals(0, commuteSlot("2번째 전역", "99"))
        assertEquals(0, commuteSlot("3번째 전역", "99"))
        assertEquals(0, commuteSlot("5번째 전역", "99"))
        assertEquals(1, commuteSlot("[1]번째 전역 (오목교(목동운동장앞))", "99"))
        assertEquals(1, commuteSlot("1 번째  전역", "99")) // 사이 공백이 섞여도 같은 칸
    }

    @Test
    fun `칸을 넘는 숫자는 가장 먼 칸으로 떨어진다`() {
        assertEquals(0, commuteSlot("12번째 전역", "99"))
        assertEquals(0, commuteSlot("99999999999999번째 전역", "99")) // Int 범위 밖 — 안 죽는다
    }

    @Test
    fun `숫자 없는 전역 은 한 정거장 전`() {
        assertEquals(last - 1, commuteSlot("전역 도착", "5"))
        assertEquals(last - 1, commuteSlot("전역 진입", "4"))
        assertEquals(last - 1, commuteSlot("까치산 전역출발", "3"))
    }

    @Test
    fun `전전역 은 두 정거장 전 - 2026-09-07 5호선 마곡 실화면 꼴`() {
        // v1.7.9 설계 때 못 본 꼴. `전역` 만 보면 두 정거장 전 열차를 한 정거장 전에 세운다.
        assertEquals(last - 2, commuteSlot("전전역 출발", "3"))
        assertEquals(last - 2, commuteSlot("전전역 도착", "5"))
        // 세 정거장 전은 칸 밖이라 맨 왼쪽(0)에 모인다.
        assertEquals(0, commuteSlot("전전전역 출발", "3"))
        // 역 이름에 붙어 있으면 안 집는다 — arvlCd 로 떨어진다.
        assertEquals(last - 1, commuteSlot("무슨전역 출발", "3"))
    }

    @Test
    fun `당역 은 역 점 위`() {
        assertEquals(last, commuteSlot("당역 도착", "99"))
        assertEquals(last, commuteSlot("당역 진입", "99"))
    }

    @Test
    fun `남은 시간 문장이면 상태코드가 위치를 말한다`() {
        // "5분 30초 후" 에는 위치가 없다 — arvlCd 3·4·5 가 전역임을 알려 준다.
        assertEquals(last - 1, commuteSlot("5분 30초 후", "5"))
        assertEquals(last - 1, commuteSlot("6분 후 (오목교(목동운동장앞))", "3"))
        // 99(운행중)는 아직 멀다 — 맨 왼쪽
        assertEquals(0, commuteSlot("8분 후", "99"))
        assertEquals(0, commuteSlot("8분 후 (마곡)", "99"))
    }

    @Test
    fun `예상 밖 글자와 빈 값에도 0부터 등록역 칸 안이고 예외가 없다`() {
        val weird = listOf(
            "", "   ", "???", "전 역", "번째", "0번째 전역", "-3번째 전역",
            "출발", "🚃", "3번째전역이 아님", "N번째 전역",
        )
        val codes = listOf("", "0", "1", "2", "3", "4", "5", "99", "7", "abc")
        weird.forEach { m -> codes.forEach { c ->
            assertTrue("$m / $c", commuteSlot(m, c) in 0..last)
        } }
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
        // 5호선 보라 위 흰 글자가 **4.5:1 에 못 미친다** — 이번 ④ 의 출발점이다.
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

    /* ── ⑤ 앞뒤 2역 (v1.7.14) ───────────────────────────────── */

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
     * 그가 등록한 것은 `마곡 · 하행`(발산 방면 = 큰 번호 쪽으로 간다)이라 열차가 **작은 번호
     * 쪽에서** 온다 → `fromHigher = false` → 오름차순.
     */
    @Test
    fun `마곡의 앞뒤 2역은 김포공항 송정 발산 우장산`() {
        assertEquals(
            listOf("김포공항", "송정", "발산", "우장산"),
            commuteNeighbors(line5, "마곡", approachFromHigher = false),
        )
    }

    @Test
    fun `역 을 붙여 쳐도 같은 줄을 찾는다`() {
        assertEquals(
            listOf("김포공항", "송정", "발산", "우장산"),
            commuteNeighbors(line5, "마곡역", approachFromHigher = false),
        )
    }

    /** 반대 방향이면 **열차가 늘 왼쪽에서 오도록** 차례를 뒤집는다. */
    @Test
    fun `상행이면 차례가 뒤집힌다`() {
        assertEquals(
            listOf("우장산", "발산", "송정", "김포공항"),
            commuteNeighbors(line5, "마곡", approachFromHigher = true),
        )
    }

    @Test
    fun `끝 역은 모자라는 자리가 빈칸이다`() {
        assertEquals(
            listOf("", "", "개화산", "김포공항"),
            commuteNeighbors(line5, "방화", approachFromHigher = false),
        )
    }

    /**
     * ⚠ **갈래를 가르는 것이 이 함수의 핵심이다.** 본선 역(가지번호 없음)은 지선 줄을 안 본다 —
     * 신도림 뒤가 `도림천`(234-1)이 아니라 **`문래`(235)** 여야 한다.
     */
    @Test
    fun `본선 역은 지선 줄을 건너뛴다`() {
        assertEquals(
            listOf("구로디지털단지", "대림", "문래", "영등포구청"),
            commuteNeighbors(line2, "신도림", approachFromHigher = false),
        )
    }

    /** 반대로 **지선 역**은 제 갈래(같은 큰 번호)만 본다 — 까치산은 종점이라 뒤가 비어야 한다. */
    @Test
    fun `지선 역은 제 갈래만 보고 종점 뒤는 빈칸이다`() {
        assertEquals(
            listOf("양천구청", "신정네거리", "", ""),
            commuteNeighbors(line2, "까치산", approachFromHigher = false),
        )
        assertEquals(
            listOf("", "신도림", "양천구청", "신정네거리"),
            commuteNeighbors(line2, "도림천", approachFromHigher = false),
        )
    }

    @Test
    fun `접두가 다르면 다른 갈래다`() {
        // 5호선 까치산(518) 뒤는 마천지선 `P549` 가 아니다 — 이 표본에는 519가 없어 빈칸이다.
        assertEquals(
            listOf("우장산", "화곡", "", ""),
            commuteNeighbors(line5, "까치산", approachFromHigher = false),
        )
    }

    @Test
    fun `모르는 역이면 빈 목록이고 예외가 없다`() {
        assertEquals(emptyList<String>(), commuteNeighbors(line5, "없는역", false))
        assertEquals(emptyList<String>(), commuteNeighbors(emptyList(), "마곡", false))
        assertEquals(emptyList<String>(), commuteNeighbors(line5, "", false))
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
     */
    @Test
    fun `statnFid 가 크면 큰 번호 쪽에서 온다`() {
        val up = ArrivalRow(
            "5120", "방화", 420, "99", "1005", "상행",
            statnId = "1005000514", statnFid = "1005000515",
        )
        assertTrue(approachFromHigher(up))
        val down = up.copy(statnFid = "1005000513")
        assertEquals(false, approachFromHigher(down))
        // 값이 없거나 숫자가 아니면 오름차순(false) — 카스가 든 예의 차례다.
        assertEquals(false, approachFromHigher(null))
        assertEquals(false, approachFromHigher(ArrivalRow("x", "방화", 1, "99")))
        assertEquals(false, approachFromHigher(up.copy(statnFid = "abc")))
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

    /* ── 칸 사이 보간 (v1.7.13 ①) ────────────────────────────
     *
     * 카스: *"역으로 다가오는 열차아이콘이 안움직이는데?"* — [commuteAdvance] 가 남은 초로
     * 칸 사이를 메운다. 확정 표 "열차 이동 = 시간 기반 등속 전진"의 세 조항을 잠근다.
     */

    @Test
    fun `처음 본 열차는 그 칸에서 시작한다`() {
        assertEquals(0f, commuteAdvance(null, 0, 300, 0f), 1e-4f)
        assertEquals(1f, commuteAdvance(null, 1, 120, 9f), 1e-4f)  // dt 가 있어도 튀지 않는다
    }

    @Test
    fun `시간이 흐르면 등속으로 앞으로 간다`() {
        // 칸 0 · 남은 200초 → 남은 칸 2개를 200초에 = **한 칸에 100초**.
        assertEquals(0.01f, commuteAdvance(0f, 0, 200, 1f), 1e-4f)
        assertEquals(0.10f, commuteAdvance(0f, 0, 200, 10f), 1e-4f)
        // 1초 눈금을 열 번 밟아도 같은 자리에 온다(누적).
        var p = 0f
        repeat(10) { p = commuteAdvance(p, 0, 200, 1f) }
        assertEquals(0.10f, p, 1e-3f)
    }

    /**
     * ⚠ **v1.7.13 ① 을 처음 만들 때 낸 버그를 잠근다.** 자리를 "칸 + 흐른 초"로 매번 다시
     * 계산했더니 15초 폴링마다 계산값이 칸으로 되감겨, 뒷걸음은 안 하지만 **13초를 붙박여**
     * 있었다(에뮬 실측 2026-09-07 03:58, 5호선 마곡). 걸음을 더하는 지금 꼴은 안 멎는다.
     */
    @Test
    fun `폴링이 와도 자리가 안 멎는다`() {
        var p = commuteAdvance(null, 0, 300, 0f)                   // 조회 ① 눈금 300초
        repeat(15) { p = commuteAdvance(p, 0, 300, 1f) }           // 15초 흐름
        assertEquals(0.10f, p, 1e-3f)                              // 한 칸 150초 → 0.10칸
        val after = commuteAdvance(p, 0, 285, 1f)                  // 조회 ② 같은 칸·줄어든 눈금
        assertTrue("폴링 뒤에도 이어 가야 한다 ($p → $after)", after > p)
    }

    @Test
    fun `다음 칸을 절대 안 넘는다`() {
        // 응답이 늦어 걸음이 커져도 예측은 한 칸에서 선다 — 있지도 않은 도착을 안 그린다.
        assertEquals(1f, commuteAdvance(0f, 0, 300, 99_999f), 1e-4f)
        assertEquals(1f, commuteAdvance(0.5f, 0, 60, 600f), 1e-4f)
        // 남은 초가 0 이하로 와도 마찬가지(0 나눗셈 없음).
        assertEquals(1f, commuteAdvance(0f, 0, 0, 5f), 1e-4f)
        assertEquals(1f, commuteAdvance(0f, 0, -5, 3f), 1e-4f)
        // 등록역 칸([COMMUTE_HERE])은 절대 못 넘는다 — 지나간 열차를 그릴 자리가 없다.
        assertEquals(last.toFloat(), commuteAdvance(1.5f, 1, 10, 99_999f), 1e-4f)
    }

    @Test
    fun `앞으로만 간다 - 뒤로는 한 픽셀도 안 물러난다`() {
        // 흐른 시간이 0 이거나 음수(시계 되돌림)면 제자리.
        assertEquals(1.15f, commuteAdvance(1.15f, 1, 285, 0f), 1e-4f)
        assertEquals(1.15f, commuteAdvance(1.15f, 1, 285, -9f), 1e-4f)
        // 칸 자체가 뒤로 온 이상한 응답에도 끌어내리지 않는다(그 자리에 선다).
        assertEquals(1.4f, commuteAdvance(1.4f, 0, 400, 0f), 1e-4f)
        assertEquals(1.4f, commuteAdvance(1.4f, 0, 400, 30f), 1e-4f)
    }

    @Test
    fun `도착 진입이면 역 점 위에 선다`() {
        // arvlCd 0·1 은 commuteSlot 이 이미 등록역 칸을 준다 — 거기서는 더 안 움직인다.
        assertEquals(last.toFloat(), commuteAdvance(null, last, 0, 0f), 1e-4f)
        assertEquals(last.toFloat(), commuteAdvance(null, last, 30, 999f), 1e-4f)
        assertEquals(last.toFloat(), commuteAdvance(1.5f, last, 30, 0f), 1e-4f)
    }

    @Test
    fun `자리는 늘 0 과 등록역 칸 사이다 - 예외 0`() {
        val etas = listOf(-10, 0, 1, 37, 300, 4000, Int.MAX_VALUE)
        val dts = listOf(-5f, 0f, 0.5f, 15f, 600f, 1e9f)
        val prevs = listOf<Float?>(null, 0f, 1.4f, last.toFloat())
        for (s in -2..COMMUTE_SLOTS + 1) for (e in etas) for (t in dts) for (p in prevs) {
            val v = commuteAdvance(p, s, e, t)
            assertTrue("prev=$p slot=$s eta=$e dt=$t → $v", v >= 0f && v <= last.toFloat())
            assertTrue("prev=$p slot=$s eta=$e dt=$t → $v", !v.isNaN())
        }
    }
}
