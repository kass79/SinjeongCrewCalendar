package com.sinjeong.crewcalendar

import com.sinjeong.crewcalendar.presentation.weather.Wx
import com.sinjeong.crewcalendar.presentation.weather.parseUltraSrtFcst
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
}
