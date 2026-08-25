package com.sinjeong.crewcalendar

import com.sinjeong.crewcalendar.presentation.weather.Wx
import com.sinjeong.crewcalendar.presentation.weather.gridFor
import com.sinjeong.crewcalendar.presentation.weather.parseUltraSrtFcst
import com.sinjeong.crewcalendar.presentation.weather.toGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 기상청 초단기예보(getUltraSrtFcst) 응답 파싱을 고정한다. 깨지면 달력에서 날씨가 조용히 사라진다
 * (조용히 사라지는 게 설계라서, 테스트가 없으면 깨진 걸 아무도 모른다).
 *
 * ⚠ **네트워크를 타지 않는다.** 아래 JSON 은 2026-08-20 nx=58·ny=126 실호출 응답에서
 * 형식만 남기고 줄인 것이다. 실행법은 [PatternTest] KDoc 참고.
 */
class WeatherTest {

    /**
     * 실제 응답은 category × fcstTime 이 평면 배열로 섞여 오고 **시각 순서도 보장되지 않는다.**
     * 그래서 일부러 늦은 시각(1700)을 앞에, 이른 시각(1600)을 뒤에 뒀다 —
     * 파서가 배열 순서가 아니라 가장 이른 fcstTime 을 고르는지 보는 게 이 테스트의 핵심이다.
     */
    @Test
    fun `가장 이른 예보시각의 흐림·기온을 고른다`() {
        val json = """
        {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},"body":{"dataType":"JSON","items":{"item":[
        {"baseDate":"20260820","baseTime":"1530","category":"T1H","fcstDate":"20260820","fcstTime":"1700","fcstValue":"31","nx":58,"ny":126},
        {"baseDate":"20260820","baseTime":"1530","category":"SKY","fcstDate":"20260820","fcstTime":"1700","fcstValue":"1","nx":58,"ny":126},
        {"baseDate":"20260820","baseTime":"1530","category":"PTY","fcstDate":"20260820","fcstTime":"1600","fcstValue":"0","nx":58,"ny":126},
        {"baseDate":"20260820","baseTime":"1530","category":"RN1","fcstDate":"20260820","fcstTime":"1600","fcstValue":"강수없음","nx":58,"ny":126},
        {"baseDate":"20260820","baseTime":"1530","category":"SKY","fcstDate":"20260820","fcstTime":"1600","fcstValue":"4","nx":58,"ny":126},
        {"baseDate":"20260820","baseTime":"1530","category":"T1H","fcstDate":"20260820","fcstTime":"1600","fcstValue":"27","nx":58,"ny":126},
        {"baseDate":"20260820","baseTime":"1530","category":"REH","fcstDate":"20260820","fcstTime":"1600","fcstValue":"75","nx":58,"ny":126}
        ]},"pageNo":1,"numOfRows":60,"totalCount":66}}}
        """.trimIndent()

        val w = parseUltraSrtFcst(json)
        assertEquals(Wx.CLOUDY, w?.wx)   // SKY=4 → 흐림 (1700 의 SKY=1 맑음에 속으면 안 된다)
        assertEquals(27, w?.tempC)
    }

    /** 강수형태(PTY)가 하늘상태(SKY)를 이긴다 — 눈 오는데 "맑음"을 그리면 안 된다. 영하 반올림도 같이 본다. */
    @Test
    fun `PTY가 SKY보다 우선이고 영하 기온을 반올림한다`() {
        val json = """
        {"response":{"body":{"items":{"item":[
        {"category":"PTY","fcstDate":"20261215","fcstTime":"0800","fcstValue":"3"},
        {"category":"SKY","fcstDate":"20261215","fcstTime":"0800","fcstValue":"1"},
        {"category":"T1H","fcstDate":"20261215","fcstTime":"0800","fcstValue":"-2.4"}
        ]}}}}
        """.trimIndent()

        val w = parseUltraSrtFcst(json)
        assertEquals(Wx.SNOW, w?.wx)
        assertEquals(-2, w?.tempC)
    }

    /** 응답이 이상하면 null — 호출부는 이때 아무것도 그리지 않는다(지하 터널 대응의 핵심). */
    @Test
    fun `기온이 없거나 응답이 깨지면 null`() {
        // T1H 누락
        assertNull(
            parseUltraSrtFcst(
                """{"response":{"body":{"items":{"item":[
                {"category":"SKY","fcstDate":"20260820","fcstTime":"1600","fcstValue":"1"}]}}}}"""
            )
        )
        // 서비스키 오류 등으로 아예 다른 문서가 올 때
        assertNull(parseUltraSrtFcst("""{"OpenAPI_ServiceResponse":{"cmmMsgHeader":{"returnAuthMsg":"SERVICE_KEY_IS_NOT_REGISTERED_ERROR"}}}"""))
        assertNull(parseUltraSrtFcst(""))
    }

    /**
     * 위경도 → 기상청 격자 변환 검산(v1.6.37). **기상청 공개 격자표와 대조한 값이다.**
     *
     * 이 변환이 틀어지면 엉뚱한 동네 기온이 조용히 그려진다 — 화면만 보고는 절대 못 잡는다.
     * 신정차량기지(58,126)는 v1.6.36 까지 상수로 박혀 실호출로 검증됐던 값이라 기준점 노릇을 한다.
     */
    @Test
    fun `위경도를 기상청 격자로 바꾼다`() {
        assertEquals(58 to 126, toGrid(37.5145, 126.8455))   // 신정차량기지 — v1.6.36 실호출 검증값
        assertEquals(60 to 127, toGrid(37.5665, 126.9780))   // 서울시청(종로구)
        assertEquals(61 to 126, toGrid(37.5172, 127.0473))   // 강남구청
        assertEquals(61 to 129, toGrid(37.6542, 127.0568))   // 노원구청
        assertEquals(54 to 125, toGrid(37.4738, 126.6216))   // 인천 중구청
        assertEquals(98 to 76, toGrid(35.1796, 129.0756))    // 부산시청
        assertEquals(53 to 38, toGrid(33.4996, 126.5312))    // 제주시청
    }

    /** 격자가 5km라 양천구 안에서 몇 백 m 움직여도 같은 칸이다 — COARSE 권한으로 충분한 근거. */
    @Test
    fun `같은 5km 격자 안에서는 좌표가 흔들려도 같은 칸`() {
        assertEquals(58 to 126, toGrid(37.5170, 126.8664))   // 양천구청
        assertEquals(58 to 126, toGrid(37.5264, 126.8962))   // 영등포구청
        assertEquals(58 to 126, toGrid(37.5509, 126.8495))   // 강서구청
    }

    /**
     * 국내 격자(1..149 × 1..253) 밖이면 null → 호출부가 신정 폴백으로 간다.
     * 해외 좌표를 그대로 보내면 `resultCode 10` 이 와서 **날씨가 통째로 안 뜬다** —
     * v1.6.37 에뮬 확인에서 기본 좌표(마운틴뷰)가 1402,1265 로 나와 실제로 잡힌 자리다.
     */
    @Test
    fun `국내 격자 밖이면 null`() {
        assertNull(toGrid(37.421998, -122.084))   // 미국 마운틴뷰 = 안드로이드 에뮬 기본 좌표
        assertNull(toGrid(35.6895, 139.6917))     // 도쿄
        assertNull(toGrid(0.0, 0.0))              // 좌표를 못 받았을 때 흔한 쓰레기값
    }

    /**
     * 설정 > 날씨 > **날씨 기준 위치**(v1.6.68)의 격자 분기.
     *
     * 여기까지가 Context 없이 잠글 수 있는 전부다 — SharedPreferences 읽기(`wx_loc_fixed` 기본값)와
     * 권한 팝업 억제는 android.jar 스텁이라 이 실행 방식으로는 못 부른다(이 파일 KDoc 참고).
     */
    @Test
    fun `신정 고정은 좌표를 무시하고 현재 위치는 그대로 쓴다`() {
        assertEquals(58 to 126, gridFor(true, 35.1796, 129.0756))  // 부산에 있어도 고정이면 신정
        assertEquals(98 to 76, gridFor(false, 35.1796, 129.0756))  // 기본값 — 종전 동작 그대로
        assertEquals(58 to 126, gridFor(false, null, null))        // 권한 거부·위치 꺼짐 → 폴백
        assertEquals(58 to 126, gridFor(false, 37.421998, -122.084)) // 해외 좌표 → 폴백
    }
}
