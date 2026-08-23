package com.sinjeong.crewcalendar

import com.sinjeong.crewcalendar.presentation.live.BranchLive
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
     * 두 소스가 1.2역 넘게 어긋나면 오데이터로 보고 무시한다. 여기선 3.0 vs 1.92 = 1.08 이라
     * **통과**하는 쪽이다(경계 바로 안쪽 — 이 값이 흔들리면 보정이 통째로 꺼진다).
     */
    @Test
    fun `양천구청 도착 초로 상행 위치를 정밀화한다`() {
        val marks = BranchLive.branchTrains(BranchLive.parsePositions(positions))
        val refined = BranchLive.refineWithArrivals(
            marks,
            BranchLive.parseArrivals(
                """[{"btrainNo":"5553","bstatnNm":"신도림지선","barvlDt":"10"}]"""),
        )
        val t = refined.first { it.trainNo == "5553" }
        assertEquals(1.923f, t.position, 0.01f)   // 10초 ÷ 신정↔양천 130초 = 0.077역 앞
        assertEquals("양천구청 10초 전", t.statusText)

        // 까치산행(5556)은 상행이 아니라 손대지 않는다 — 하행에 상행 ETA를 먹이면 안 된다
        assertEquals(3f, refined.first { it.trainNo == "5556" }.position, 0.001f)
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
}
