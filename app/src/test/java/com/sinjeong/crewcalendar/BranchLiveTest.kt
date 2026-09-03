package com.sinjeong.crewcalendar

import com.sinjeong.crewcalendar.presentation.live.BranchLive
import com.sinjeong.crewcalendar.presentation.live.TrainMark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 서울 열린데이터광장 실시간 위치·도착 응답 파싱과 지선 판정을 잠근다.
 * 깨지면 오늘 상세시트의 열차 지도가 조용히 빈 채로 뜬다(빈 상태가 정상 동작이라 눈치채기 어렵다).
 *
 * ⚠ **네트워크를 타지 않는다.** 아래 JSON 은 2026-08-22 실호출 응답에서 형식만 남기고 줄인 것이다.
 * 실행법은 [PatternTest] KDoc 참고.
 */
class BranchLiveTest {

    /** 실호출 응답 그대로: 값이 null 인 필드(beginRow)·따옴표 없는 숫자(rowNum)가 섞여 온다. */
    private val positions = """
    {"errorMessage":{"status":200,"code":"INFO-000","message":"정상 처리되었습니다.","total":3},"realtimePositionList":[
    {"beginRow":null,"rowNum":1,"subwayId":"1002","subwayNm":"2호선","statnNm":"도림천","trainNo":"5553","updnLine":"1","statnTid":"1002000234","statnTnm":"신도림지선","trainSttus":"1","lstcarAt":"0"},
    {"beginRow":null,"rowNum":2,"subwayId":"1002","subwayNm":"2호선","statnNm":"도림천","trainNo":"5556","updnLine":"0","statnTid":"1002002344","statnTnm":"까치산","trainSttus":"1","lstcarAt":"0"},
    {"beginRow":null,"rowNum":3,"subwayId":"1002","subwayNm":"2호선","statnNm":"신도림","trainNo":"6114","updnLine":"0","statnTid":"1002000211","statnTnm":"성수종착","trainSttus":"1","lstcarAt":"0"}
    ]}
    """.trimIndent()

    @Test
    fun `위치 응답에서 열차 3건을 뽑는다`() {
        val rows = BranchLive.parsePositions(positions)
        assertEquals(3, rows.size)                 // errorMessage 객체는 subwayId 가 없어 걸러진다
        assertEquals("5553", rows[0].trainNo)
        assertEquals("도림천", rows[0].statnNm)
        assertEquals("신도림지선", rows[0].statnTnm)   // "지선" 접미사가 붙어서 온다
        assertEquals("1", rows[0].trainSttus)
    }

    /**
     * 이 테스트의 핵심: **종착역명 변형과 본선 열차 배제**.
     * "신도림지선"은 신도림행으로 읽어야 하고, 같은 지선 역에 있어도 종착이 "성수종착"인
     * 본선 열차(6114)는 노선도에 올리면 안 된다.
     */
    @Test
    fun `지선 열차만 골라 방향과 위치를 잡는다`() {
        val marks = BranchLive.branchTrains(BranchLive.parsePositions(positions))
        assertEquals(2, marks.size)
        assertTrue(marks.none { it.trainNo == "6114" })   // 본선 열차는 제외

        val up = marks.first { it.trainNo == "5553" }
        assertTrue(up.toSindorim)                          // "신도림지선" → 신도림 방면
        assertEquals(3f, up.position, 0.001f)              // 도림천(3) 도착(sttus=1)
        assertEquals("도림천 도착", up.statusText)

        val down = marks.first { it.trainNo == "5556" }
        assertEquals(false, down.toSindorim)               // "까치산" → 까치산 방면
        assertEquals(3f, down.position, 0.001f)
    }

    @Test
    fun `도착 응답에서 열번과 남은 초를 뽑는다`() {
        val json = """
        {"errorMessage":{"status":200,"code":"INFO-000","message":"정상 처리되었습니다.","total":1},"realtimeArrivalList":[
        {"rowNum":1,"subwayId":"1002","updnLine":"외선","statnNm":"양천구청","barvlDt":"10","btrainNo":"5553","bstatnNm":"신도림지선","arvlMsg2":"양천구청 도착","arvlCd":"1"}
        ]}
        """.trimIndent()
        val rows = BranchLive.parseArrivals(json)
        assertEquals(1, rows.size)
        // ⚠ `"trainNo":"` 로는 `"btrainNo":"` 가 안 잡힌다(앞에 따옴표가 와야 한다) — 그 반대도 마찬가지.
        assertEquals("5553", rows[0].trainNo)
        assertEquals("신도림지선", rows[0].destName)
        assertEquals(10, rows[0].etaSec)
    }

    /**
     * 도착정보 융합: 위치 API는 역 단위 이벤트만 주고 도착 API는 초 단위 ETA를 준다.
     * 양천구청 10초 전 = 양천구청(2.0)에 거의 붙은 자리로 당겨진다 —
     * 이게 열차가 역과 역 사이를 미끄러지듯 움직이는 근거다.
     *
     * ⚠ **앞으로만 당긴다**(v1.6.70). 종전엔 1.2역 이내면 뒤로도 끌었고, 그래서 도림천에 있는
     * 열차를 양천구청으로 되돌려 놓는 실제 오배치가 났다(아래 `묵은 도착행` 테스트가 그 자리다).
     */
    @Test
    fun `양천구청 도착 초로 상행 위치를 정밀화한다`() {
        // 신정네거리를 갓 출발한 상행(1.15) — 보정이 앞으로 당기는 정상 경로
        val marks = BranchLive.branchTrains(BranchLive.parsePositions(
            """[{"subwayId":"1002","statnNm":"신정네거리","trainNo":"5553","updnLine":"1","statnTnm":"신도림지선","trainSttus":"2"},""" +
            """{"subwayId":"1002","statnNm":"도림천","trainNo":"5556","updnLine":"0","statnTnm":"까치산","trainSttus":"1"}]"""))
        val refined = BranchLive.refineWithArrivals(
            marks,
            BranchLive.parseArrivals(
                """[{"btrainNo":"5553","bstatnNm":"신도림지선","barvlDt":"10","arvlCd":"3"}]"""),
        )
        val t = refined.first { it.trainNo == "5553" }
        assertEquals(1.923f, t.position, 0.01f)   // 10초 ÷ 신정↔양천 130초 = 0.077역 앞
        assertEquals("양천구청 10초 전", t.statusText)

        // 까치산행(5556)은 상행이 아니라 손대지 않는다 — 하행에 상행 ETA를 먹이면 안 된다
        assertEquals(3f, refined.first { it.trainNo == "5556" }.position, 0.001f)
    }

    /**
     * v1.6.70 — **묵은 도착행이 열차를 뒤로 끌지 못한다.** 실호출로 잡은 오배치를 그대로 재생한다.
     *
     * 2026-08-25 18:32~18:36, 30초 간격. 열차 `5651` 하나를 두 API가 이렇게 말했다:
     * ```
     * 18:32:48 위치=양천구청 출발   도착=cd2 출발/eta10
     * 18:33:49 위치=도림천 도착     도착=cd0 진입/eta10   ← 출발 → 진입으로 되돌아간다
     * 18:34:49 위치=도림천 도착     도착=cd1 도착/eta10
     * 18:35:50 위치=신도림 도착     도착=cd2 출발/eta10   ← 3분째 같은 자리를 말한다
     * ```
     * 종전 코드는 도림천(3.0) 구간에서 `|1.92 − 3.0| = 1.08 ≤ 1.2` 라 이 묵은 행을 받아들여
     * **열차를 양천구청으로 되돌려 그렸다.** 이제 두 겹으로 막는다 — cd 0·1·2 버리기 + 앞으로만.
     */
    @Test
    fun `묵은 도착행이 열차를 뒤로 끌지 않는다`() {
        val at도림천 = BranchLive.branchTrains(BranchLive.parsePositions(
            """[{"subwayId":"1002","statnNm":"도림천","trainNo":"5651","updnLine":"1","statnTnm":"신도림지선","trainSttus":"1"}]"""))
        assertEquals(3f, at도림천.single().position, 0.001f)

        listOf("2", "0", "1").forEach { cd ->
            val r = BranchLive.refineWithArrivals(at도림천, BranchLive.parseArrivals(
                """[{"btrainNo":"5651","bstatnNm":"신도림지선","barvlDt":"10","arvlCd":"$cd"}]"""))
            assertEquals("cd=$cd 는 그 역을 이미 지난 행이다", 3f, r.single().position, 0.001f)
            assertEquals("도림천 도착", r.single().statusText)
        }
        // cd를 안 주는 응답이라도(구 스키마) 뒤로는 못 끈다 — 두 번째 자물쇠
        val noCd = BranchLive.refineWithArrivals(at도림천, BranchLive.parseArrivals(
            """[{"btrainNo":"5651","bstatnNm":"신도림지선","barvlDt":"10"}]"""))
        assertEquals(3f, noCd.single().position, 0.001f)
    }

    /**
     * v1.6.70 — `barvlDt`에 **정차 시간이 섞여 있다**(실측: 신정네거리에 서 있는 `5653`의
     * 양천구청 ETA가 180초, 실주행 130초보다 50초 많다). 그대로 믿으면 역에 정차한 열차를
     * 구간 한복판(0.5역)으로 끌어 내리고, 그러면 ③의 접근 판정(0.85~2.0)까지 같이 빗나간다.
     */
    @Test
    fun `정차 시간이 섞인 ETA가 열차를 역 뒤로 못 내린다`() {
        val 신정네거리정차 = BranchLive.branchTrains(BranchLive.parsePositions(
            """[{"subwayId":"1002","statnNm":"신정네거리","trainNo":"5653","updnLine":"1","statnTnm":"신도림지선","trainSttus":"1"}]"""))
        assertEquals(1f, 신정네거리정차.single().position, 0.001f)
        val r = BranchLive.refineWithArrivals(신정네거리정차, BranchLive.parseArrivals(
            """[{"btrainNo":"5653","bstatnNm":"신도림지선","barvlDt":"180","arvlCd":"5"}]"""))
        assertEquals(1f, r.single().position, 0.001f)                 // 0.5로 안 내려간다
        assertTrue(BranchLive.approachingYangcheon(r))                // ③ 5초 갱신이 그대로 걸린다
    }

    /**
     * v1.6.70 — **신정네거리(1)로도 같은 융합**을 한다. 승무원은 양천구청에서 편승 열차를 타는데
     * 신정네거리가 바로 앞 역이라, 한 정거장 앞에서부터 초 단위로 보인다.
     *
     * 상한이 역마다 다르다: 까치산→그 역까지의 실측 주행시간(양천구청 230초 · 신정네거리 100초).
     */
    @Test
    fun `신정네거리 도착 초로도 상행 위치를 정밀화한다`() {
        val rows = BranchLive.parsePositions(
            """[{"subwayId":"1002","statnNm":"까치산","trainNo":"5601","updnLine":"1","statnTnm":"신도림지선","trainSttus":"2"}]""")
        val marks = BranchLive.branchTrains(rows)          // 까치산 출발 → 0.15
        val refined = BranchLive.refineWithArrivals(
            marks,
            BranchLive.parseArrivals("""[{"btrainNo":"5601","bstatnNm":"신도림지선","barvlDt":"50"}]"""),
            1,
        )
        val t = refined.single()
        assertEquals(0.5f, t.position, 0.01f)              // 50초 ÷ 까치↔신정 100초 = 0.5역 앞
        assertEquals("신정네거리 50초 전", t.statusText)

        // 상한(100초) 밖은 0~1 역변환이 표현 못 하는 자리 — 손대지 않는다
        val far = BranchLive.refineWithArrivals(
            marks,
            BranchLive.parseArrivals("""[{"btrainNo":"5601","bstatnNm":"신도림지선","barvlDt":"150"}]"""),
            1,
        )
        assertEquals(0.15f, far.single().position, 0.01f)
        assertEquals("까치산 출발", far.single().statusText)
    }

    /**
     * v1.6.70 — 두 역이 같은 열차를 말하면 **양천구청이 이긴다**(보드 지점이라 더 중요).
     * 순서로 보장한다: 신정네거리를 먼저 걸고 양천구청을 나중에 걸어 덮어쓴다.
     *
     * ⚠ 실호출(2026-08-25 18:3x)에서 **신정네거리 행이 한 역 묵어서 오는 것**을 봤다
     * (5651이 위치 API로는 양천구청 도착인데 신정네거리 목록엔 `진입`으로 남아 있었다).
     * 이 우선순위가 그 묵은 값을 덮는 자리다 — 뒤집히면 지나간 역을 말하게 된다.
     */
    @Test
    fun `두 역이 겹치면 양천구청이 이긴다`() {
        // loadFromSeoulApi 와 같은 순서: 신정네거리(1) → 양천구청(2)
        fun fuse(marks: List<TrainMark>, sin: String, yang: String) = BranchLive.refineWithArrivals(
            BranchLive.refineWithArrivals(marks, BranchLive.parseArrivals(sin), 1),
            BranchLive.parseArrivals(yang), 2).single()

        val 까치산출발 = BranchLive.branchTrains(BranchLive.parsePositions(
            """[{"subwayId":"1002","statnNm":"까치산","trainNo":"5601","updnLine":"1","statnTnm":"신도림지선","trainSttus":"2"}]"""))
        val t = fuse(까치산출발,
            """[{"btrainNo":"5601","bstatnNm":"신도림지선","barvlDt":"50","arvlCd":"3"}]""",   // → 0.5
            """[{"btrainNo":"5601","bstatnNm":"신도림지선","barvlDt":"100","arvlCd":"3"}]""")  // → 1.23
        assertEquals(1.231f, t.position, 0.01f)            // 양천구청 값이 신정네거리 값을 덮는다
        assertEquals("양천구청 100초 전", t.statusText)

        // 실제로 겪는 충돌은 이쪽이다 — **묵은 신정네거리 행 vs 지금 양천구청에 있는 열차**.
        // (2026-08-25 실측: 5651이 위치 API로는 양천구청인데 신정네거리 목록엔 `진입`으로 남아 있었다)
        val 양천구청도착 = BranchLive.branchTrains(BranchLive.parsePositions(
            """[{"subwayId":"1002","statnNm":"양천구청","trainNo":"5601","updnLine":"1","statnTnm":"신도림지선","trainSttus":"1"}]"""))
        val u = fuse(양천구청도착,
            """[{"btrainNo":"5601","bstatnNm":"신도림지선","barvlDt":"10","arvlCd":"0"}]""",   // 묵은 행
            """[]""")
        assertEquals(2f, u.position, 0.001f)               // 신정네거리로 되돌아가지 않는다
        assertEquals("양천구청 도착", u.statusText)
    }

    /**
     * v1.6.70 적응형 갱신의 판정(주기 값은 v1.6.72에서 10초 ↔ 4초).
     * 창은 **신정네거리 진입(0.85) ~ 양천구청 도착(2.0)**.
     * 넓히면 한도가 새고, 좁히면 승강장에서 열차를 눈으로 찾는 구간을 놓친다.
     */
    @Test
    fun `양천구청 접근 판정이 창 밖을 안 센다`() {
        fun up(pos: Float) = listOf(TrainMark("5601", true, pos, ""))
        assertFalse(BranchLive.approachingYangcheon(up(0.84f)))   // 신정네거리 진입 전
        assertTrue(BranchLive.approachingYangcheon(up(0.85f)))    // 신정네거리 진입(idx − 0.15)
        assertTrue(BranchLive.approachingYangcheon(up(1.99f)))
        assertFalse(BranchLive.approachingYangcheon(up(2f)))      // 양천구청 도착 = 편승 끝
        // 까치산행은 편승 대상이 아니다 — 하행 때문에 주기를 당기면 한도만 샌다
        assertFalse(BranchLive.approachingYangcheon(listOf(TrainMark("5602", false, 1.5f, ""))))
        assertFalse(BranchLive.approachingYangcheon(emptyList()))
    }

    /**
     * **주기 값 자체를 잠근다**(v1.6.72: 15/5 → 10/4초). `docs/project-notes.md`의 한도 계산표가
     * 이 두 숫자에 통째로 얹혀 있다 — 조용히 바뀌면 문서가 거짓말이 되고 한도가 샌다.
     *
     * 그리고 **`LineMap`의 폴링 tick(2초)이 두 값의 최대공약수여야 한다.** 실제 호출은
     * "주기 이상이 되는 첫 tick"에 일어나므로 나누어떨어지지 않으면 그 눈금으로 반올림된다
     * (종전 5초 tick에 4초를 넣으면 실제 간격이 5초가 된다 — 한쪽만 고치면 나는 버그).
     */
    @Test
    fun `접근 여부로 갱신 주기가 갈리고 폴링 눈금과 맞물린다`() {
        val idle = BranchLive.pollIntervalMs(listOf(TrainMark("5601", true, 0.5f, "")))
        val near = BranchLive.pollIntervalMs(listOf(TrainMark("5601", true, 1.5f, "")))
        assertEquals(10_000L, idle)
        assertEquals(4_000L, near)
        assertEquals(10_000L, BranchLive.pollIntervalMs(emptyList()))
        assertTrue(near < idle)
        val tickMs = 2_000L                                   // LineMap 폴링 LaunchedEffect
        assertEquals(0L, idle % tickMs)
        assertEquals(0L, near % tickMs)
        // 여유는 **한 눈금 미만**이어야 한 눈금 이른 자리가 여전히 걸리고, **0보다 커야**
        // 경계 눈금이 지터로 밀리지 않는다. 이 창을 벗어나면 실측 간격이 주기와 달라진다.
        assertTrue(BranchLive.TICK_SLACK_MS > 0L)
        assertTrue(BranchLive.TICK_SLACK_MS < tickMs)
    }

    @Test
    fun `INFO-000은 정상 나머지는 에러 문구`() {
        assertNull(BranchLive.apiError(positions))
        assertEquals(
            "일일 트래픽 제한을 넘었습니다. (ERROR-337)",
            BranchLive.apiError(
                """{"errorMessage":{"status":500,"code":"ERROR-337","message":"일일 트래픽 제한을 넘었습니다."}}"""),
        )
    }

    /**
     * v1.6.46 — 실패는 **사람 말로** 화면에 뜬다. 종전엔 `Snapshot.error`를 카드가 아예 안 읽어
     * 비행기 모드든 한도 소진이든 `"실시간 조회 중…"` 에 영원히 머물렀다.
     */
    @Test
    fun `실패 사유를 사람 말로 바꾼다`() {
        assertEquals("인터넷 연결 안 됨",
            BranchLive.humanError(java.net.UnknownHostException("Unable to resolve host \"swopenapi.seoul.go.kr\"")))
        assertEquals("인터넷 연결 안 됨",
            BranchLive.humanError(java.io.IOException("Network is unreachable")))
        assertEquals("응답이 없어요 · 다시 시도",
            BranchLive.humanError(java.net.SocketTimeoutException("timed out")))
        // 한도 문구는 fetch()가 이미 사람 말로 만들어 던진다 — 그대로 통과시킨다
        assertEquals("모든 키 일일 한도 초과 (자정 리셋)",
            BranchLive.humanError(Exception("모든 키 일일 한도 초과 (자정 리셋)")))
        // 정체 모를 오류(HTTP 500·JSON 깨짐)도 침묵하지 않는다
        assertEquals("실시간 정보를 못 받았어요",
            BranchLive.humanError(java.io.IOException("...HTTP response code: 500")))
    }

    /**
     * 한도 응답이면 **다음 키로 넘어가고**(5개 로테이션), 일시 오류면 재시도만 한다.
     * 이 판정이 틀리면 키가 하나 소진된 채로 온종일 실패하거나(로테이션 안 함),
     * 잠깐 끊긴 네트워크에 키 5개를 헛돌린다.
     */
    @Test
    fun `한도 응답만 다음 키로 넘어간다`() {
        // 서울 열린데이터광장 실제 응답 문구 (apiError가 만든 그대로)
        assertTrue(BranchLive.isQuotaError("일일 트래픽 제한을 넘었습니다. (ERROR-337)"))
        assertTrue(BranchLive.isQuotaError("인증키 호출 횟수를 초과하였습니다"))
        // 일시 오류는 키를 넘기지 않는다
        assertFalse(BranchLive.isQuotaError("Unable to resolve host \"swopenapi.seoul.go.kr\""))
        assertFalse(BranchLive.isQuotaError("timed out"))
    }

    /** 회차: 까치산(0)에 닿은 까치산행은 열번 +1로 신도림행 대기가 된다(승무 실무 규칙). */
    @Test
    fun `종착 도착은 머리 전환으로 바뀐다`() {
        val at = BranchLive.parsePositions(
            """[{"subwayId":"1002","statnNm":"까치산","trainNo":"5556","updnLine":"0","statnTnm":"까치산","trainSttus":"1"}]""")
        val turned = BranchLive.applyTurnaround(BranchLive.branchTrains(at))
        assertEquals(1, turned.size)
        assertEquals("5557", turned[0].trainNo)
        assertTrue(turned[0].toSindorim)
        assertEquals(0f, turned[0].position, 0.001f)
    }

    /* ── 회차 중 아이콘 유지 (v1.6.56) ──────────────────────────────────────
     *
     * 사용자 신고: *"신도림역에 도착하면 운전실을 바꾸어서 … 그때 멈춰 있을땐 왜 열차 아이콘이
     * 사라져? 까치산역에도 마찬가지고"*.
     *
     * 아래 두 테스트는 **2026-08-23 20:45~20:57 실호출 응답을 15초 간격으로 받아 적은 것을
     * 시간순 그대로 재생**한다(값은 관측 그대로, 형식만 줄임). 관측 사실:
     *
     * | 종착 | 진입 | API 실종 구간 | 복귀 열번 |
     * |---|---|---|---|
     * | 신도림 | 20:46:44 `5677` | 20:48:09~20:50:31 (**2분 42초**) | 20:50:41 `5682` (**+5**) |
     * | 까치산 | 20:50:41 `5680` | 20:51:42~20:56:36 (**4분 54초**) | 20:56:46 `5681` (**+1**) |
     *
     * 즉 API는 회차 내내 그 열차를 안 준다 — 그동안 화면을 채우는 것이 회차 홀드 아이콘이다.
     */

    /** `realtimePosition` 행 하나(쓰는 필드만) */
    private fun row(no: String, stn: String, dest: String, sttus: String, updn: String) =
        """{"subwayId":"1002","subwayNm":"2호선","statnNm":"$stn","trainNo":"$no",""" +
            """"updnLine":"$updn","statnTid":"1002000234","statnTnm":"$dest","trainSttus":"$sttus"}"""

    /** 실제 화면이 받는 것과 같은 순서: 회차 공백 메꾸기 → 머리 전환 → 겹침 정리 */
    private fun pipeline(rows: List<String>, nowMs: Long) =
        BranchLive.squashOverlaps(
            BranchLive.applyTurnaround(
                BranchLive.ensureFleet(
                    BranchLive.branchTrains(
                        BranchLive.parsePositions("""{"list":[${rows.joinToString(",")}]}""")),
                    nowMs)))

    /** 회차 기억은 `object`에 남아 테스트끼리 샌다 — 만료 시각을 한 번 던져 비운다. */
    @Before
    fun clearTurnMemory() {
        BranchLive.ensureFleet(emptyList(), Long.MAX_VALUE / 2)
    }

    /**
     * **까치산 회차** — v1.6.55가 실패하던 자리.
     *
     * 5680이 까치산에 닿는 바로 그 순간(20:50:41) 신도림에서 회차를 마친 `5682`가 나타난다.
     * v1.6.55는 회차 **후** 열번(`5681`)을 기억해 두고 "회차 완료 = +1(`5682`)이 보이면"으로
     * 판정했기 때문에, **남남인 실차 5682**가 그 기억을 즉시 지웠다. 그 뒤 API가 5680을 끊자
     * 홀드가 없어 아이콘이 4분 54초 동안 통째로 사라졌다.
     */
    @Test
    fun `까치산 회차 4분 54초 동안 아이콘이 안 사라진다`() {
        val t0 = 1_800_000_000_000L      // 20:50:41 = 5680 까치산 진입

        val arrive = pipeline(listOf(
            row("5680", "까치산", "까치산", "0", "0"),              // 진입 — 회차 시작
            row("5679", "신정네거리", "신도림지선", "2", "1"),
            row("5682", "신도림지선", "까치산", "2", "0")), t0)     // ← 기억을 지우던 장본인
        assertEquals("5681", arrive.single { it.position <= 0.15f }.trainNo)
        assertEquals(3, arrive.size)

        // 20:51:42 ~ 20:56:36 — API가 5680을 하나도 안 준다. 마지막 실측은 20:51:32(t0+51초).
        listOf(10, 60, 180, 294).forEach { sec ->
            val blind = pipeline(listOf(
                row("5679", "양천구청", "신도림지선", "1", "1"),
                row("5682", "도림천", "까치산", "1", "0")), t0 + 51_000L + sec * 1000L)
            val icon = blind.firstOrNull { it.trainNo == "5681" }
            assertNotNull("실종 +${sec}초: 까치산 회차 아이콘이 사라졌다", icon)
            assertEquals(0f, icon!!.position, 0.001f)          // 까치산에 세워 둔다
            assertTrue(icon.toSindorim)                        // 머리를 신도림 쪽으로 돌렸다
            assertEquals("회차 · 까치산 대기", icon.statusText)  // 정차 빨간 점 + `↻ 회차` 배지
        }

        // 20:56:46 — 5681로 실제 출발. 홀드는 **즉시** 빠지고 실측 열차 하나만 남는다.
        val back = pipeline(listOf(
            row("5681", "까치산", "신도림지선", "2", "1"),
            row("5679", "신도림지선", "신도림지선", "1", "1"),
            row("5682", "신정네거리", "까치산", "0", "0")), t0 + 365_000L)
        assertEquals(1, back.count { it.trainNo == "5681" })    // 홀드와 실차가 겹쳐 보이면 안 된다
        assertEquals(0.15f, back.first { it.trainNo == "5681" }.position, 0.001f)  // 까치산 출발
    }

    /** **신도림 회차** — 같은 구조. 열번은 +5(`5677` → `5682`)로 관측됐다. */
    @Test
    fun `신도림 회차 2분 42초 동안 아이콘이 안 사라진다`() {
        val t0 = 1_800_000_000_000L      // 20:47:14 = 5677 신도림 도착(마지막 실측은 20:47:59)

        val arrive = pipeline(listOf(
            row("5677", "신도림지선", "신도림지선", "1", "1"),      // ⚠ statnNm 이 "신도림지선"으로 온다
            row("5680", "양천구청", "까치산", "2", "0"),
            row("5679", "까치산", "신도림지선", "2", "1")), t0)
        assertEquals("5682", arrive.single { it.position >= 3.85f }.trainNo)

        // 20:48:09 ~ 20:50:31 — API가 5677을 안 준다(마지막 실측 20:47:59 = t0+45초).
        listOf(10, 60, 152).forEach { sec ->
            val blind = pipeline(listOf(
                row("5680", "신정네거리", "까치산", "0", "0"),
                row("5679", "까치산", "신도림지선", "2", "1")), t0 + 45_000L + sec * 1000L)
            val icon = blind.firstOrNull { it.trainNo == "5682" }
            assertNotNull("실종 +${sec}초: 신도림 회차 아이콘이 사라졌다", icon)
            assertEquals(4f, icon!!.position, 0.001f)
            assertFalse(icon.toSindorim)                       // 머리를 까치산 쪽으로 돌렸다
            assertEquals("회차 · 신도림 대기", icon.statusText)
        }

        // 20:50:41 — 5682로 실제 출발.
        val back = pipeline(listOf(
            row("5682", "신도림지선", "까치산", "2", "0"),
            row("5680", "까치산", "까치산", "0", "0"),
            row("5679", "신정네거리", "신도림지선", "2", "1")), t0 + 207_000L)
        assertEquals(1, back.count { it.trainNo == "5682" })
        assertEquals(3.85f, back.first { it.trainNo == "5682" }.position, 0.001f)
    }

    /**
     * 홀드는 **무한정이 아니다.** 12분이 지나면 접는다 — 실측 최대 실종 구간(까치산 7분 36초)에
     * 4분 남짓 여유를 둔 값이다. 더 늘리면 이미 떠난 열차가 종착에 눌어붙는다.
     */
    @Test
    fun `홀드는 12분을 넘기면 접는다`() {
        val t0 = 1_800_000_000_000L
        pipeline(listOf(row("5680", "까치산", "까치산", "1", "0")), t0)
        // 실측 최대(7분 36초)에는 아직 붙어 있어야 한다
        val live = pipeline(listOf(row("5679", "양천구청", "신도림지선", "1", "1")), t0 + 456_000L)
        assertTrue("실측 최대 실종 구간에서 아이콘이 사라졌다", live.any { it.trainNo == "5681" })

        val stale = pipeline(listOf(row("5679", "양천구청", "신도림지선", "1", "1")), t0 + 12 * 60_000L + 1)
        assertTrue("12분이 지나도 회차 아이콘이 남아 있다", stale.none { it.trainNo == "5681" })
    }

    /* ── 콜드 스타트 (v1.6.70 ⑤) ─────────────────────────────────────────────
     *
     * 사용자: *"신도림, 까치산에 정차해 있으면 가끔씩 안보이는 이유는 뭐야?"*
     *
     * v1.6.56이 **경과 세션 안에서는** 고쳤지만, `turningTrains`가 `object` 메모리라
     * **프로세스가 죽으면 통째로 사라진다.** 앱을 새로 띄운 순간 이미 회차 중이던 열차는
     * 위치 API가 안 주고(2026-08-25 재확인) 기억도 없어 **그릴 근거가 0**이었다 —
     * v1.6.56이 *"콜드 스타트에선 안 그린다"* 로 적어 둔 그 자리가 사용자가 겪은 "가끔씩"이다.
     *
     * 아래 테스트는 **프로세스가 죽는 순간을 그대로 재현한다**: 관측 → 직렬화 → 기억 소거
     * (= 프로세스 종료) → 복원 → 홀드 아이콘이 살아 있는가.
     */

    /** 프로세스가 죽었다 — 회차 기억을 통째로 날린다(만료 시각을 던져 비우는 것과 같다). */
    private fun killProcess() = BranchLive.ensureFleet(emptyList(), Long.MAX_VALUE / 2)

    @Test
    fun `콜드 스타트에서 회차 기억을 되살린다`() {
        val t0 = 1_800_000_000_000L
        // ① 세션 1: 5651이 신도림에 도착하는 것을 봤다
        pipeline(listOf(row("5651", "신도림지선", "신도림지선", "1", "1")), t0)
        val saved = BranchLive.turnMemory()
        assertTrue("관측한 회차 열차가 저장 문자열에 없다", saved.contains("5651"))

        // ② 앱이 죽었다가 90초 뒤 새로 뜬다 — 위치 API는 회차 중인 열차를 **안 준다**
        killProcess()
        assertTrue("기억이 안 지워졌다면 이 테스트는 아무것도 안 잠근다",
            pipeline(emptyList(), t0 + 90_000L).isEmpty())

        // ③ 복원하면 같은 열차가 같은 자리에 선다(+5 = 5656, `회차 · 신도림 대기`)
        killProcess()
        BranchLive.restoreTurnMemory(saved, t0 + 90_000L)
        val cold = pipeline(emptyList(), t0 + 90_000L)
        assertEquals(listOf("5656"), cold.map { it.trainNo })
        assertEquals(4f, cold.single().position, 0.001f)
        assertEquals("회차 · 신도림 대기", cold.single().statusText)
    }

    /**
     * 복원이 **유령을 만들면 안 된다.** 두 가지로 막는다:
     *  · 12분([TURN_HOLD_MS])이 지난 기억은 복원 자체를 안 한다.
     *  · 회차를 마치고 돌아온 열차(+5)가 보이면 첫 폴링에서 홀드가 걷힌다.
     */
    @Test
    fun `복원한 회차 기억이 유령으로 남지 않는다`() {
        val t0 = 1_800_000_000_000L
        pipeline(listOf(row("5651", "신도림지선", "신도림지선", "1", "1")), t0)
        val saved = BranchLive.turnMemory()

        // ① 12분이 지난 기억은 되살리지 않는다
        killProcess()
        BranchLive.restoreTurnMemory(saved, t0 + 12 * 60_000L + 1)
        assertTrue("만료된 기억이 되살아났다", pipeline(emptyList(), t0 + 12 * 60_000L + 1).isEmpty())

        // ② 복원했는데 회차가 이미 끝나 있었다면(+5가 실차로 보인다) 즉시 걷힌다 — 중복 아이콘 0
        killProcess()
        BranchLive.restoreTurnMemory(saved, t0 + 200_000L)
        val back = pipeline(listOf(row("5656", "도림천", "까치산", "1", "0")), t0 + 200_000L)
        assertEquals(listOf("5656"), back.map { it.trainNo })
        assertEquals(3f, back.single().position, 0.001f)      // 종착이 아니라 도림천에 있다

        // ③ 깨진 문자열·빈 문자열에 안 죽는다(저장소가 오래된 형식일 수 있다)
        killProcess()
        BranchLive.restoreTurnMemory("", t0)
        BranchLive.restoreTurnMemory("쓰레기,5651:없음:1,5651", t0)
        assertTrue(pipeline(emptyList(), t0).isEmpty())
    }

    /** 저장 형식 왕복 — 열번·목격시각·어느 종착인지가 그대로 살아 돌아온다. */
    @Test
    fun `회차 기억 직렬화 왕복`() {
        val t0 = 1_800_000_000_000L
        pipeline(listOf(row("5651", "신도림지선", "신도림지선", "1", "1"),
                        row("5680", "까치산", "까치산", "1", "0")), t0)
        val saved = BranchLive.turnMemory()
        killProcess()
        BranchLive.restoreTurnMemory(saved, t0 + 60_000L)
        // 신도림 홀드 → +5, 까치산 홀드 → +1. 양쪽 종착이 따로 살아난다.
        assertEquals(setOf("5656", "5681"), pipeline(emptyList(), t0 + 60_000L).map { it.trainNo }.toSet())
    }

    /* ── 본선 열차 배제 (v1.6.58) ───────────────────────────────────────────
     *
     * 2026-08-23 `realtimePosition/2호선` 실호출에서 본선 열차가 지선 지도에 섞였다.
     * `4376`은 `statnNm`·`statnTnm`이 둘 다 `"신도림"`인 **본선 입고 열차**인데
     * `destKind("신도림")=1` 이라 지선 상행으로 올라왔고, `applyTurnaround`가 +5를 먹여
     * `4381`이라는 **있지도 않은 열차**를 신도림에 세웠다. 양천구청에서 편승을 기다리는
     * 사람에게는 **오지 않을 열차**다 — 이 앱에서 가장 나쁜 실패다.
     *
     * 지선 열차는 같은 신도림에서도 `statnNm`이 `"신도림지선"`으로 오고, 열번이 5xxx다.
     */

    /** 실호출 응답 그대로(형식만 줄임): 본선 3대 + 지선 2대가 한 응답에 섞여 온다. */
    private val mixed = """
    {"errorMessage":{"status":200,"code":"INFO-000","message":"정상 처리되었습니다.","total":5},"realtimePositionList":[
    {"subwayId":"1002","subwayNm":"2호선","statnNm":"신도림","trainNo":"4376","updnLine":"0","statnTid":"1002000234","statnTnm":"신도림","trainSttus":"0"},
    {"subwayId":"1002","subwayNm":"2호선","statnNm":"신림","trainNo":"4398","updnLine":"0","statnTid":"1002000234","statnTnm":"신도림","trainSttus":"1"},
    {"subwayId":"1002","subwayNm":"2호선","statnNm":"강변","trainNo":"4408","updnLine":"0","statnTid":"1002000234","statnTnm":"신도림","trainSttus":"2"},
    {"subwayId":"1002","subwayNm":"2호선","statnNm":"신도림지선","trainNo":"5689","updnLine":"1","statnTid":"1002000234","statnTnm":"신도림지선","trainSttus":"1"},
    {"subwayId":"1002","subwayNm":"2호선","statnNm":"양천구청","trainNo":"5692","updnLine":"0","statnTid":"1002002344","statnTnm":"까치산","trainSttus":"1"}
    ]}
    """.trimIndent()

    @Test
    fun `본선 신도림 종착 열차는 지선 지도에 안 올라온다`() {
        val rows = BranchLive.parsePositions(mixed)
        assertEquals(5, rows.size)

        val marks = BranchLive.branchTrains(rows)
        assertEquals(listOf("5689", "5692"), marks.map { it.trainNo }.sorted())

        // 안전망 2단계(지선 전용역 열차)도 본선을 주워 오면 안 된다
        assertTrue(BranchLive.branchTrainsLoose(rows, marks).none { it.trainNo.startsWith("4") })

        // 회차 개명까지 태워도 유령 `4381`이 안 생긴다 — 화면이 실제로 받는 순서 그대로
        val drawn = BranchLive.squashOverlaps(
            BranchLive.applyTurnaround(BranchLive.ensureFleet(marks, 1_800_000_000_000L)))
        assertTrue("본선 열차가 지도에 올라왔다",
            drawn.none { it.trainNo == "4376" || it.trainNo == "4381" })
        assertTrue("지선 실차가 같이 사라졌다", drawn.any { it.trainNo == "5692" })
    }

    /** 같은 응답의 **본선 입고 안내**는 그대로여야 한다 — 거기는 본선 열차를 일부러 쓴다. */
    @Test
    fun `본선 입고 안내는 필터에 안 걸린다`() {
        val inbound = BranchLive.inboundFromPositions(BranchLive.parsePositions(mixed))
        assertEquals(listOf("4398"), inbound.map { it.trainNo })   // 신림 = 4역 앞
        assertEquals(4 * 110, inbound[0].etaSec)
    }

    /**
     * 회송 열번(`59xx`)은 지선 선로를 타도 **승객이 못 탄다** — 편승 지도에서 뺀다.
     * 근거는 행로표(`RouteTable`): 지선 야간 다이아 꼬리 `5901`~`5907`(막차 입고)과
     * 본선 다이아 전반 첫 열번 `5922`·`5930`·`5949`·`5961`(신정기지 출고)이 전부 회송이다.
     */
    @Test
    fun `59xx 회송은 지도에 안 올라온다`() {
        val rows = BranchLive.parsePositions(
            """[{"subwayId":"1002","statnNm":"양천구청","trainNo":"5930","updnLine":"1","statnTnm":"신도림","trainSttus":"1"},""" +
            """{"subwayId":"1002","statnNm":"양천구청","trainNo":"5601","updnLine":"1","statnTnm":"신도림지선","trainSttus":"1"}]""")
        val marks = BranchLive.branchTrains(rows)
        assertEquals(listOf("5601"), marks.map { it.trainNo })                       // 영업 열번만
        assertTrue(BranchLive.branchTrainsLoose(rows, marks).none { it.trainNo == "5930" })
    }

    /** 안전망 3단계(양천구청 도착정보 합성)에도 같은 가드가 걸린다. */
    @Test
    fun `도착정보 합성도 지선 열번만 쓴다`() {
        val yang = BranchLive.parseArrivals(
            """[{"btrainNo":"5601","bstatnNm":"신도림지선","barvlDt":"60"},""" +
            """{"btrainNo":"4376","bstatnNm":"신도림","barvlDt":"90"}]""")
        assertEquals(listOf("5601"), BranchLive.trainsFromArrivals(yang).map { it.trainNo })
    }

    /** 경계값 — 행로표 실범위(5501~5720)와 회송 하한(5901)을 그대로 잠근다. */
    @Test
    fun `열번대 경계`() {
        fun ok(no: String) = BranchLive.branchTrains(BranchLive.parsePositions(
            """[{"subwayId":"1002","statnNm":"양천구청","trainNo":"$no","updnLine":"1","statnTnm":"신도림지선","trainSttus":"1"}]""")).isNotEmpty()
        assertTrue(ok("5501"))     // 야간 다이아 첫 영업 열번
        assertTrue(ok("5720"))     // 야간 다이아 마지막 영업 열번
        assertFalse(ok("5901"))    // 지선 막차 입고 회송
        assertFalse(ok("5922"))    // 본선 신정기지 출고 회송
        assertFalse(ok("4376"))    // 본선 신도림 종착
        assertFalse(ok("6114"))    // 본선 성수 계열
    }

    // ── 본선 열차 (v1.6.84) ─────────────────────────────────
    // 같은 위치 스냅샷에서 본선만 걸러 낸다 — API 호출은 늘지 않는다.

    /**
     * 2026-09-03 01:00 `realtimePosition/2호선` **실호출 응답**에서 뽑은 행들이다.
     * 그 시각 16대가 왔고 지선 둘(성수지선 1725/1726 · 신정지선 5719/5720)이 섞여 있었다.
     */
    private val REAL = """[
        {"subwayId":"1002","statnNm":"신설동","trainNo":"1725","updnLine":"1","statnTnm":"신설동","trainSttus":"1"},
        {"subwayId":"1002","statnNm":"도림천","trainNo":"5719","updnLine":"1","statnTnm":"신도림지선","trainSttus":"1"},
        {"subwayId":"1002","statnNm":"신정네거리","trainNo":"5720","updnLine":"0","statnTnm":"까치산","trainSttus":"2"},
        {"subwayId":"1002","statnNm":"성수종착","trainNo":"6508","updnLine":"0","statnTnm":"성수종착","trainSttus":"1"},
        {"subwayId":"1002","statnNm":"건대입구","trainNo":"6513","updnLine":"1","statnTnm":"성수종착","trainSttus":"1"},
        {"subwayId":"1002","statnNm":"문래","trainNo":"7523","updnLine":"1","statnTnm":"신도림","trainSttus":"1"},
        {"subwayId":"1002","statnNm":"을지로3가","trainNo":"8527","updnLine":"1","statnTnm":"을지로입구","trainSttus":"3"}
    ]"""

    @Test
    fun `본선 열차만 골라낸다`() {
        val m = BranchLive.mainTrains(BranchLive.parsePositions(REAL))
        val nos = m.map { it.trainNo }
        // 지선 전용역(신설동·도림천·신정네거리)에 있는 열차는 본선 지도에 없다
        assertFalse("1725" in nos)
        assertFalse("5719" in nos)
        assertFalse("5720" in nos)
        // 본선 역에 있는 열차는 남는다 — `성수종착` 꼬리도 흡수된다
        assertTrue("6508" in nos)
        assertTrue("6513" in nos)
        assertTrue("7523" in nos)
        assertTrue("8527" in nos)
        assertEquals(4, m.size)
        assertEquals(10, m.first { it.trainNo == "6508" }.stationIdx)   // 성수
    }

    /**
     * ⚠ `updnLine` 은 글자가 아니라 **"0"·"1"** 이다(실호출 확인). `"0"` = 내선.
     * 근거: `7523` 이 문래에서 종착 신도림인데 `updnLine=1` — 내선 순서가
     * `대림 → 신도림 → 문래` 이므로 문래에서 신도림으로 가는 건 역순 = 외선이다.
     */
    @Test
    fun `내선 외선 판정`() {
        val m = BranchLive.mainTrains(BranchLive.parsePositions(REAL)).associateBy { it.trainNo }
        assertTrue("updnLine 0 은 내선", m.getValue("6508").inner)
        assertFalse("updnLine 1 은 외선", m.getValue("7523").inner)
        assertFalse(m.getValue("6513").inner)
        assertFalse(m.getValue("8527").inner)
        // trainSttus 3(전역 출발)은 **진행 방향 뒤쪽**에 놓인다. 8527 은 외선이라 부호가
        // 뒤집혀 +0.6 이다(v1.6.85 — 아래 `외선은 offset 부호가 뒤집힌다` 가 이유를 적었다).
        assertEquals(+0.6f, m.getValue("8527").offset, 0.001f)
        assertEquals(0f, m.getValue("7523").offset, 0.001f)
    }

    /**
     * **외선은 offset 부호가 뒤집힌다** (v1.6.85).
     *
     * `stationIdx` 는 [Line2Stations.MAIN] 의 자리이고 그 순서는 **내선**이다. 그러니
     * "전역 출발 = 아직 도착 안 함"은 내선에서만 `−0.6`(인덱스 작은 쪽)이고, 외선에서는
     * 인덱스가 **줄어드는** 쪽으로 달리므로 `+0.6` 이어야 지나온 역 쪽에 그려진다.
     * 부호를 안 뒤집으면 외선 열차가 **가야 할 역을 지나친 자리**에 찍힌다.
     *
     * 실응답 근거: `8527` 을지로3가 · 종착 을지로입구 · `updnLine=1`(외선) · `trainSttus=3`.
     * 내선 대비군은 같은 상태(`trainSttus=3`)에 `updnLine=0` 만 바꾼 행이다.
     */
    @Test
    fun `외선은 offset 부호가 뒤집힌다`() {
        val json = """{"list":[
            ${row("8527", "을지로3가", "을지로입구", "3", "1")},
            ${row("2039", "강남", "성수", "3", "0")},
            ${row("8531", "선릉", "성수", "0", "1")},
            ${row("2041", "역삼", "성수", "0", "0")}
        ]}"""
        val m = BranchLive.mainTrains(BranchLive.parsePositions(json)).associateBy { it.trainNo }
        assertEquals(+0.6f, m.getValue("8527").offset, 0.001f)   // 외선 전역출발
        assertEquals(-0.6f, m.getValue("2039").offset, 0.001f)   // 내선 전역출발
        assertEquals(+0.15f, m.getValue("8531").offset, 0.001f)  // 외선 진입
        assertEquals(-0.15f, m.getValue("2041").offset, 0.001f)  // 내선 진입
        // 부호가 붙어도 역 자리 자체는 그대로다
        assertEquals(2, m.getValue("8527").stationIdx)           // 을지로3가
        assertEquals(21, m.getValue("2039").stationIdx)          // 강남
    }

    /**
     * `branchTrainsLoose` 의 `updnLine` 폴백 — **`"0"` 상행(신도림 방면) · `"1"` 하행**.
     *
     * v1.6.84가 실호출로 확인했다: `updnLine` 실값은 `"0"`·`"1"` 뿐이라 종전의
     * `"상" in updnLine` 은 **한 번도 참이 된 적이 없었다** — 늘 마지막 `else -> true` 로
     * 떨어져 하행 열차까지 신도림 방면으로 그렸다.
     *
     * 이 폴백에 닿으려면 종착역명이 답을 못 줘야 한다(`destKind == 0`) — 아래 행은
     * 종착역명을 비워 그 상황을 만든다.
     */
    @Test
    fun `지선 폴백은 updnLine 0을 상행으로 본다`() {
        val rows = BranchLive.parsePositions(
            """{"list":[${row("5601", "양천구청", "", "2", "0")}]}""")
        val t = BranchLive.branchTrainsLoose(rows, emptyList()).single()
        assertTrue("updnLine 0 은 신도림 방면", t.toSindorim)
        assertEquals(2.15f, t.position, 0.001f)   // 양천구청(2)에서 신도림 쪽으로 출발
    }

    @Test
    fun `지선 폴백은 updnLine 1을 하행으로 본다`() {
        val rows = BranchLive.parsePositions(
            """{"list":[${row("5602", "양천구청", "", "2", "1")}]}""")
        val t = BranchLive.branchTrainsLoose(rows, emptyList()).single()
        assertFalse("updnLine 1 은 까치산 방면", t.toSindorim)
        assertEquals(1.85f, t.position, 0.001f)   // 양천구청(2)에서 까치산 쪽으로 출발
    }

    /** 종착역명이 답을 주면 [updnLine] 은 안 본다 — 폴백 순서가 뒤집히면 지선이 회귀한다. */
    @Test
    fun `지선 폴백보다 종착역명이 먼저다`() {
        val rows = BranchLive.parsePositions(
            """{"list":[${row("5603", "양천구청", "까치산종착", "1", "0")}]}""")
        val t = BranchLive.branchTrainsLoose(rows, emptyList()).single()
        // updnLine 은 "0"(상행)이지만 종착이 까치산이므로 **하행**이 이겨야 한다
        assertFalse(t.toSindorim)
    }

    @Test
    fun `같은 열번은 한 번만`() {
        // parsePositions 는 여는/닫는 중괄호 덩어리를 찾을 뿐이라 배열 문법이 아니어도 된다
        val dup = REAL + """{"subwayId":"1002","statnNm":"삼성","trainNo":"7523","updnLine":"1","statnTnm":"신도림","trainSttus":"1"}"""
        val m = BranchLive.mainTrains(BranchLive.parsePositions(dup))
        assertEquals(1, m.count { it.trainNo == "7523" })
    }
}
