package com.sinjeong.crewcalendar

import com.sinjeong.crewcalendar.presentation.live.BranchLive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
}
