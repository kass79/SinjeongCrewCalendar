package com.sinjeong.crewcalendar

import com.sinjeong.crewcalendar.domain.model.Line2Stations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2호선 본선 43역 데이터를 잠근다 (v1.6.84).
 *
 * 여기서 깨지면 **본선 지도의 열차가 전부 엉뚱한 자리에 찍힌다** — 인덱스가 곧 원 위의
 * 각도이기 때문이다. 실행법은 [PatternTest] KDoc 참고(JUnitCore 직접 실행).
 */
class Line2Test {

    @Test
    fun `본선은 43역이고 중복이 없다`() {
        assertEquals(43, Line2Stations.MAIN.size)
        assertEquals(43, Line2Stations.MAIN.toSet().size)
    }

    /** 순서가 곧 각도다 — 몇 군데를 못 박아 통째로 밀리는 것을 잡는다. */
    @Test
    fun `내선 순서가 고정돼 있다`() {
        assertEquals("시청", Line2Stations.MAIN.first())
        assertEquals("충정로", Line2Stations.MAIN.last())
        assertEquals("을지로입구", Line2Stations.MAIN[1])
        assertEquals("성수", Line2Stations.MAIN[10])
        assertEquals("잠실", Line2Stations.MAIN[15])
        assertEquals("강남", Line2Stations.MAIN[21])
        assertEquals("사당", Line2Stations.MAIN[25])
        assertEquals("신도림", Line2Stations.MAIN[33])
        assertEquals("홍대입구", Line2Stations.MAIN[38])
        // 내선은 대림 -> 신도림 -> 문래 순서다. 이 셋이 뒤집히면 방향 판정이 통째로 뒤집힌다.
        assertEquals(
            listOf("대림", "신도림", "문래"),
            Line2Stations.MAIN.subList(32, 35),
        )
    }

    /**
     * API 가 주는 실제 표기를 흡수한다. 2026-09-03 01:00 실호출에서 `statnNm` 이
     * `성수종착`·`성수지선` 으로 왔다 — 흡수를 안 하면 성수 열차가 지도에서 사라진다.
     */
    @Test
    fun `역명 꼬리를 떼고 견준다`() {
        assertEquals(10, Line2Stations.indexOfMain("성수종착"))
        assertEquals(10, Line2Stations.indexOfMain("성수지선"))
        assertEquals(10, Line2Stations.indexOfMain("성수"))
        assertEquals(33, Line2Stations.indexOfMain("신도림지선"))
        assertEquals(33, Line2Stations.indexOfMain("신도림역"))
        assertEquals(0, Line2Stations.indexOfMain("시청역"))
        assertEquals(4, Line2Stations.indexOfMain("동대문역사문화공원(2호선)"))
        // `역` 을 함부로 떼면 안 되는 이름 — 꼬리가 아니라 이름 한복판이다
        assertEquals(4, Line2Stations.indexOfMain("동대문역사문화공원"))
        // 옛 이름
        assertEquals(16, Line2Stations.indexOfMain("신천"))
        assertEquals("잠실새내", Line2Stations.MAIN[16])
    }

    /** 지선 전용역은 본선에 없다 — 있으면 순환선 위에 없는 점이 찍힌다. */
    @Test
    fun `지선 전용역은 본선에 없다`() {
        listOf("용답", "신답", "용두", "신설동", "도림천", "양천구청", "신정네거리", "까치산")
            .forEach {
                assertEquals("본선에 있으면 안 됨: " + it, -1, Line2Stations.indexOfMain(it))
                assertTrue(it + " 는 BRANCH_ONLY 여야 한다", it in Line2Stations.BRANCH_ONLY)
            }
        // 두 지선의 시작역은 본선이다 — 걸러 내면 안 된다
        assertFalse("성수" in Line2Stations.BRANCH_ONLY)
        assertFalse("신도림" in Line2Stations.BRANCH_ONLY)
        assertEquals(5, Line2Stations.SEONGSU_BRANCH.size)
        assertEquals(5, Line2Stations.SINJEONG_BRANCH.size)
        assertEquals("성수", Line2Stations.SEONGSU_BRANCH.first())
        assertEquals("신도림", Line2Stations.SINJEONG_BRANCH.first())
    }

    /**
     * **둥근 사각형 네 변의 역 배치** — 사용자가 준 운전실 보조설비 화면 사진 그대로다(v1.6.85).
     *
     * `MainLineMap` 은 [Line2Stations.MAIN] 을 `합정`(둘레 시작)부터 세어 둘레 자리를 만든다
     * (`k = (mainIdx − 합정자리 + 43) % 43`). 여기 목록이 곧 화면에 찍히는 순서이고,
     * **[Line2Stations.MAIN] 순서가 바뀌면 이 테스트가 먼저 깨진다** — 지도 전체가 밀리기 전에
     * 잡으라고 둔 것이다. 변 나누기(17·5·16·5)도 `MainLineMap` 의 상수와 같은 수다.
     */
    @Test
    fun `둘레 네 변 배치가 사진과 같다`() {
        val start = Line2Stations.MAIN.indexOf("합정")
        assertEquals(37, start)
        val perim = (0 until 43).map { Line2Stations.MAIN[(it + start) % 43] }

        assertEquals(
            listOf("합정", "홍대입구", "신촌", "이대", "아현", "충정로", "시청", "을지로입구",
                "을지로3가", "을지로4가", "동대문역사문화공원", "신당", "상왕십리", "왕십리",
                "한양대", "뚝섬", "성수"),
            perim.subList(0, 17),            // 윗변 왼→오
        )
        assertEquals(
            listOf("건대입구", "구의", "강변", "잠실나루", "잠실"),
            perim.subList(17, 22),           // 오른변 위→아래
        )
        assertEquals(
            listOf("잠실새내", "종합운동장", "삼성", "선릉", "역삼", "강남", "교대", "서초",
                "방배", "사당", "낙성대", "서울대입구", "봉천", "신림", "신대방", "구로디지털단지"),
            perim.subList(22, 38),           // 아랫변 오→왼
        )
        assertEquals(
            listOf("대림", "신도림", "문래", "영등포구청", "당산"),
            perim.subList(38, 43),           // 왼변 아래→위
        )
        // 네 변을 합치면 43역이 하나도 빠짐없이 한 번씩 나온다
        assertEquals(Line2Stations.MAIN.toSet(), perim.toSet())
    }

    @Test
    fun `모르는 이름은 마이너스 1`() {
        assertEquals(-1, Line2Stations.indexOfMain("서울역"))
        assertEquals(-1, Line2Stations.indexOfMain(""))
    }
}
