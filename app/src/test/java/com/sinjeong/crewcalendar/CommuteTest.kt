package com.sinjeong.crewcalendar

import com.sinjeong.crewcalendar.presentation.live.ArrivalRow
import com.sinjeong.crewcalendar.presentation.live.BranchLive
import com.sinjeong.crewcalendar.presentation.live.COMMUTE_MAX
import com.sinjeong.crewcalendar.presentation.live.CommuteStation
import com.sinjeong.crewcalendar.presentation.live.atStationText
import com.sinjeong.crewcalendar.presentation.live.boundOf
import com.sinjeong.crewcalendar.presentation.live.commuteApproaching
import com.sinjeong.crewcalendar.presentation.live.commuteAtStation
import com.sinjeong.crewcalendar.presentation.live.commuteLabel
import com.sinjeong.crewcalendar.presentation.live.commuteOnOf
import com.sinjeong.crewcalendar.presentation.live.commuteOptions
import com.sinjeong.crewcalendar.presentation.live.decodeCommute
import com.sinjeong.crewcalendar.presentation.live.encodeCommute
import com.sinjeong.crewcalendar.presentation.live.etaText
import com.sinjeong.crewcalendar.presentation.live.lineName
import com.sinjeong.crewcalendar.presentation.live.positionText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 출퇴근 역 실시간(v1.7.9 ⑦)의 파싱·필터·표기·저장을 잠근다.
 *
 * ⚠ **네트워크를 타지 않는다.** 아래 JSON 은 **2026-09-06 19:51 까치산 실호출 응답 그대로**다
 * (필드 순서·값 전부 원본). 까치산이 **2호선(1002)·5호선(1005)** 둘로 갈리는 것이 이 설계의
 * 핵심이고, 5호선 하행에 `마천행`·`하남검단산행` 두 행선이 섞여 오는 것도 실제 응답이다.
 */
class CommuteTest {

    /** 2026-09-06 19:51:11~31 까치산 realtimeStationArrival 실응답(5건) */
    private val kkachisan = """
    {"errorMessage":{"status":200,"code":"INFO-000","message":"정상 처리되었습니다.","link":"","developerMessage":"","total":5},"realtimeArrivalList":[
    {"rowNum":1,"subwayId":"1005","updnLine":"상행","trainLineNm":"방화행 - 화곡방면","statnNm":"까치산","btrainSttus":"일반","barvlDt":"60","btrainNo":"5622","bstatnNm":"방화","arvlMsg2":"까치산 전역출발","arvlMsg3":"신정(은행정)","arvlCd":"3","lstcarAt":"0"},
    {"rowNum":2,"subwayId":"1005","updnLine":"상행","trainLineNm":"방화행 - 화곡방면","statnNm":"까치산","btrainSttus":"일반","barvlDt":"360","btrainNo":"5134","bstatnNm":"방화","arvlMsg2":"6분 후 (오목교(목동운동장앞))","arvlMsg3":"오목교(목동운동장앞)","arvlCd":"99","lstcarAt":"0"},
    {"rowNum":3,"subwayId":"1005","updnLine":"하행","trainLineNm":"마천행 - 신정(은행정)방면","statnNm":"까치산","btrainSttus":"일반","barvlDt":"120","btrainNo":"5645","bstatnNm":"마천","arvlMsg2":"전역 도착","arvlMsg3":"화곡","arvlCd":"5","lstcarAt":"0"},
    {"rowNum":4,"subwayId":"1002","updnLine":"내선","trainLineNm":"까치산행 - 까치산방면","statnNm":"까치산","btrainSttus":"일반","barvlDt":"480","btrainNo":"5670","bstatnNm":"까치산","arvlMsg2":"8분 후","arvlMsg3":"도림천","arvlCd":"99","lstcarAt":"0"},
    {"rowNum":5,"subwayId":"1005","updnLine":"하행","trainLineNm":"하남검단산행 - 신정(은행정)방면","statnNm":"까치산","btrainSttus":"일반","barvlDt":"480","btrainNo":"5139","bstatnNm":"하남검단산","arvlMsg2":"8분 후 (마곡)","arvlMsg3":"마곡","arvlCd":"99","lstcarAt":"0"}
    ]}
    """.trimIndent()

    /** 운행 종료·결과 0건일 때 서울 열린데이터가 주는 정상 응답 */
    private val info200 = """
    {"status":500,"code":"INFO-200","message":"해당하는 데이터가 없습니다.","link":"","developerMessage":"","total":0}
    """.trimIndent()

    private fun rows() = BranchLive.parseArrivals(kkachisan)

    /* ── 파싱 ────────────────────────────────────────────────── */

    @Test
    fun `도착 응답에서 새 필드 여섯 종을 뽑는다`() {
        val r = rows()
        assertEquals(5, r.size)
        val first = r[0]
        assertEquals("5622", first.trainNo)
        assertEquals("1005", first.subwayId)
        assertEquals("상행", first.updnLine)          // ⚠ 낱말이다 — "0"/"1" 이 아니다
        assertEquals("방화행 - 화곡방면", first.trainLineNm)
        assertEquals("까치산 전역출발", first.arvlMsg2)
        assertEquals("신정(은행정)", first.arvlMsg3)
        assertEquals("방화", first.destName)          // bstatnNm
        assertEquals(60, first.etaSec)                // barvlDt
        assertEquals("3", first.arvlCd)
    }

    @Test
    fun `INFO-200 은 오류가 아니라 빈 목록이다`() {
        assertNull(BranchLive.apiError(info200))      // 오류로 던지면 재시도 루프가 호출을 두 배로 쓴다
        assertTrue(BranchLive.parseArrivals(info200).isEmpty())
        assertTrue(commuteOptions(BranchLive.parseArrivals(info200)).isEmpty())
    }

    /** 새 필드가 다 기본값이어도 지선 입고 경로가 쓰는 옛 네 필드는 그대로여야 한다 */
    @Test
    fun `ArrivalRow 옛 생성자 자리는 안 바뀌었다`() {
        val old = ArrivalRow("2340", "성수", 90, "3")
        assertEquals("2340", old.trainNo)
        assertEquals("성수", old.destName)
        assertEquals(90, old.etaSec)
        assertEquals("3", old.arvlCd)
        assertEquals("", old.subwayId)
    }

    /* ── 등록 화면: 호선 × 방향 조합 ─────────────────────────── */

    @Test
    fun `까치산은 2호선과 5호선으로 갈린다`() {
        val opts = commuteOptions(rows())
        // 1002 내선 · 1005 상행 · 1005 하행 = 셋. 5호선 하행의 마천·하남검단산은 **한 칩**이다
        assertEquals(3, opts.size)
        assertEquals(listOf("1002", "1005", "1005"), opts.map { it.subwayId })
        assertEquals(listOf("내선", "상행", "하행"), opts.map { it.updnLine })
        assertEquals("2호선 · 까치산방면", opts[0].label)
        assertEquals("5호선 · 화곡방면", opts[1].label)
        assertEquals("5호선 · 신정(은행정)방면", opts[2].label)
    }

    @Test
    fun `조합을 고르면 역명과 함께 저장된다`() {
        val s = commuteOptions(rows()).first { it.subwayId == "1005" && it.updnLine == "상행" }
            .toStation(" 까치산 ")
        assertEquals(CommuteStation("까치산", "1005", "상행"), s)
    }

    @Test
    fun `방면 낱말은 trainLineNm 뒤쪽이고 없으면 방향 낱말로 갈음한다`() {
        assertEquals("화곡방면", boundOf("방화행 - 화곡방면", "상행"))
        assertEquals("방화행", boundOf("방화행", "상행"))
        assertEquals("내선", boundOf("", "내선"))
    }

    @Test
    fun `모르는 호선 id 는 숫자를 그대로 보여 준다`() {
        assertEquals("2호선", lineName("1002"))
        assertEquals("5호선", lineName("1005"))
        assertEquals("9999", lineName("9999"))
    }

    /* ── 보는 화면: 호선·방향 필터 ──────────────────────────── */

    @Test
    fun `같은 이름 다른 노선은 등록한 호선만 남는다`() {
        val line2 = commuteApproaching(rows(), CommuteStation("까치산", "1002", "내선"))
        assertEquals(listOf("5670"), line2.map { it.trainNo })

        val line5up = commuteApproaching(rows(), CommuteStation("까치산", "1005", "상행"))
        assertEquals(listOf("5622", "5134"), line5up.map { it.trainNo })   // 가까운 순

        val line5dn = commuteApproaching(rows(), CommuteStation("까치산", "1005", "하행"))
        assertEquals(listOf("5645", "5139"), line5dn.map { it.trainNo })
    }

    @Test
    fun `다가오는 열차는 최대 세 대이고 가까운 순이다`() {
        val many = (1..6).map {
            ArrivalRow("t$it", "방화", it * 100, "99", "1005", "상행")
        }.reversed()
        val got = commuteApproaching(many, CommuteStation("까치산", "1005", "상행"))
        assertEquals(listOf("t1", "t2", "t3"), got.map { it.trainNo })
    }

    /* ── 이미 지난 열차(arvlCd 0·1·2) ───────────────────────── */

    @Test
    fun `진입 도착 출발은 다가오는 목록에서 빠진다`() {
        val s = CommuteStation("까치산", "1005", "상행")
        val mixed = listOf(
            ArrivalRow("enter", "방화", 0, "0", "1005", "상행"),
            ArrivalRow("arrive", "방화", 0, "1", "1005", "상행"),
            ArrivalRow("leave", "방화", 0, "2", "1005", "상행"),
            ArrivalRow("coming", "방화", 200, "99", "1005", "상행"),
        )
        assertEquals(listOf("coming"), commuteApproaching(mixed, s).map { it.trainNo })
    }

    @Test
    fun `진입 도착은 맨 위 한 줄로 남고 출발은 아예 안 센다`() {
        val s = CommuteStation("까치산", "1005", "상행")
        assertEquals(
            "enter",
            commuteAtStation(listOf(ArrivalRow("enter", "방화", 0, "0", "1005", "상행")), s)?.trainNo,
        )
        assertEquals(
            "arrive",
            commuteAtStation(listOf(ArrivalRow("arrive", "방화", 0, "1", "1005", "상행")), s)?.trainNo,
        )
        // 출발(2)은 이미 떠난 열차 — 위 줄에도 안 뜬다
        assertNull(commuteAtStation(listOf(ArrivalRow("leave", "방화", 0, "2", "1005", "상행")), s))
        // 다른 호선의 진입 열차를 끌어오지 않는다
        assertNull(commuteAtStation(listOf(ArrivalRow("x", "까치산", 0, "0", "1002", "내선")), s))
        assertEquals("지금 진입", atStationText("0"))
        assertEquals("지금 도착", atStationText("1"))
    }

    /* ── 표기 ───────────────────────────────────────────────── */

    @Test
    fun `남은 시간 표기`() {
        assertEquals("곧 도착", etaText(0))
        assertEquals("곧 도착", etaText(-30))
        assertEquals("45초", etaText(45))
        assertEquals("1분 0초", etaText(60))
        assertEquals("6분 0초", etaText(360))
        assertEquals("8분 5초", etaText(485))
    }

    @Test
    fun `위치 글은 arvlMsg2 를 쓰되 남은 시간 문장이면 지금 있는 역으로 바꾼다`() {
        // 위치를 말하는 문장은 그대로
        assertEquals("까치산 전역출발", positionText(rows()[0]))
        assertEquals("전역 도착", positionText(rows()[2]))
        // 남은 시간을 되풀이하는 문장은 arvlMsg3(지금 있는 역)로 — 아랫줄 etaText 와 겹친다
        assertEquals("오목교(목동운동장앞)", positionText(rows()[1]))   // "6분 후 (오목교…)"
        assertEquals("도림천", positionText(rows()[3]))                // "8분 후"
        assertEquals("마곡", positionText(rows()[4]))                  // "8분 후 (마곡)"
        assertEquals(                                                  // 에뮬 실측 문구
            "도림천",
            positionText(ArrivalRow("x", "까치산", 330, "99", arvlMsg2 = "5분 30초 후", arvlMsg3 = "도림천")),
        )
        // arvlMsg3 가 비면 되돌아가지 않는다 — 있는 글이라도 보여 준다
        assertEquals("8분 후", positionText(ArrivalRow("x", "까치산", 480, "99", arvlMsg2 = "8분 후")))
        assertEquals("도림천", positionText(ArrivalRow("x", "까치산", 0, "99", arvlMsg3 = "도림천")))
        assertEquals("위치 확인 중", positionText(ArrivalRow("x", "까치산", 0, "99")))
    }

    /* ── 저장 문자열 왕복 ───────────────────────────────────── */

    @Test
    fun `저장 문자열 왕복 — 카스가 든 네 역`() {
        val list = listOf(
            CommuteStation("마곡", "1005", "상행"),
            CommuteStation("까치산", "1002", "내선"),
            CommuteStation("까치산", "1005", "하행"),
            CommuteStation("신도림", "1002", "외선"),
        )
        val s = encodeCommute(list)
        assertEquals("마곡|1005|상행;까치산|1002|내선;까치산|1005|하행;신도림|1002|외선", s)
        assertEquals(list, decodeCommute(s))
    }

    @Test
    fun `빈 값과 깨진 값을 막는다`() {
        assertEquals(emptyList<CommuteStation>(), decodeCommute(null))
        assertEquals(emptyList<CommuteStation>(), decodeCommute(""))
        assertEquals(emptyList<CommuteStation>(), decodeCommute("까치산"))          // 칸 부족
        assertEquals(emptyList<CommuteStation>(), decodeCommute("까치산|1002"))     // 칸 부족
        assertEquals(emptyList<CommuteStation>(), decodeCommute("|1002|내선"))      // 역명 빈칸
        assertEquals(emptyList<CommuteStation>(), decodeCommute("까치산||내선"))     // 호선 빈칸
        // 깨진 칸만 버리고 성한 칸은 살린다
        assertEquals(
            listOf(CommuteStation("신도림", "1002", "외선")),
            decodeCommute("깨짐;신도림|1002|외선"),
        )
    }

    /**
     * v1.7.13 ④ — 카스: *"최대 5개까지 선택할수있었으면 해!"* (v1.7.9~12 는 4개였다).
     * 상한은 [COMMUTE_MAX] 한 곳이고 **저장·복원 양쪽**이 같이 자른다.
     */
    @Test
    fun `등록은 다섯 개까지다`() {
        assertEquals(5, COMMUTE_MAX)
        val six = (1..6).map { CommuteStation("역$it", "1002", "내선") }
        // 다섯은 그대로 살고 여섯째가 버려진다 — 저장할 때도, 읽을 때도.
        assertEquals(5, encodeCommute(six).split(";").size)
        assertEquals("역5", encodeCommute(six).split(";")[4].substringBefore("|"))
        val back = decodeCommute(six.joinToString(";") { "${it.name}|1002|내선" })
        assertEquals(5, back.size)
        assertEquals("역5", back.last().name)
        // 딱 다섯이면 한 칸도 안 버린다(경계).
        assertEquals(5, decodeCommute(encodeCommute(six.take(5))).size)
    }

    /* ── v1.7.13b ① 칩 글자 ─────────────────────────────────── */

    /**
     * 카스: *"방향을 칩에 적어주면 좋지.."* — 종전 칩은 `5호선 마곡` 뿐이라 **같은 역을
     * 방향만 달리 둘 등록하면 글자가 똑같았다**(카스 화면에 나란히 둘 떴다).
     * 방향 낱말 네 가지(`상행`·`하행`·`내선`·`외선`)가 전부 갈리는지 잠근다.
     */
    @Test
    fun `칩 글자에 방향이 붙어 같은 역도 갈린다`() {
        assertEquals("5호선 마곡 · 하행", commuteLabel(CommuteStation("마곡", "1005", "하행")))
        assertEquals("5호선 마곡 · 상행", commuteLabel(CommuteStation("마곡", "1005", "상행")))
        assertEquals("2호선 까치산 · 내선", commuteLabel(CommuteStation("까치산", "1002", "내선")))
        assertEquals("2호선 까치산 · 외선", commuteLabel(CommuteStation("까치산", "1002", "외선")))
        // 같은 역·같은 호선인데 방향만 다른 둘 — 글자가 **달라야** 한다(이번 회차의 이유).
        val a = commuteLabel(CommuteStation("마곡", "1005", "하행"))
        val b = commuteLabel(CommuteStation("마곡", "1005", "상행"))
        assertTrue("방향만 다른 둘이 같은 글자다", a != b)
    }

    /**
     * **역 이름은 절대 줄이지 않는다**(확정 표) — 방향이 붙어도 그대로다.
     * 빈 방향·모르는 낱말·앞뒤 공백에도 줄이 안 깨진다.
     */
    @Test
    fun `역 이름은 안 줄이고 빈 방향은 가운뎃점을 뺀다`() {
        assertEquals(
            "2호선 구로디지털단지 · 내선",
            commuteLabel(CommuteStation("구로디지털단지", "1002", "내선")),
        )
        // 빈 방향(옛 저장값·손댄 값) — 가운뎃점 없이 역 이름만
        assertEquals("2호선 신도림", commuteLabel(CommuteStation("신도림", "1002", "")))
        assertEquals("2호선 신도림", commuteLabel(CommuteStation("신도림", "1002", "   ")))
        // 모르는 호선 id 는 숫자 그대로(lineName 규칙), 긴 방향 낱말도 통째로 남는다
        assertEquals(
            "9999 어딘가 · 아주긴방면낱말",
            commuteLabel(CommuteStation("어딘가", "9999", " 아주긴방면낱말 ")),
        )
    }

    /* ── v1.7.13b ② 전체 스위치 ─────────────────────────────── */

    /**
     * 카스: *"출퇴근역은 전체 끄기 켜기 스위치가 있으면 좋을거 같은데?"*
     *
     * **기본값은 켜짐**(지금까지의 동작 그대로)이고, 모르는 값도 켜짐으로 떨어진다.
     * ⚠ 끄기는 **지우기가 아니다** — 저장은 키가 따로라 목록(`commute_stations`)이 그대로다.
     */
    @Test
    fun `스위치 기본값은 켜짐이고 끄면 목록은 남는다`() {
        assertTrue("등록 전 기본값", commuteOnOf(null))
        assertTrue(commuteOnOf("true"))
        assertEquals(false, commuteOnOf("false"))
        // 모르는 값·빈 값·대소문자 뒤섞임은 전부 기본값(켜짐)으로 — 화면이 조용히 사라지면 안 된다
        for (bad in listOf("", " ", "FALSE", "0", "off", "네")) assertTrue(bad, commuteOnOf(bad))
        // 껐다 켜도 등록 목록은 저장 키가 달라 한 글자도 안 바뀐다
        val saved = encodeCommute(
            listOf(CommuteStation("마곡", "1005", "하행"), CommuteStation("마곡", "1005", "상행")),
        )
        assertEquals(false, commuteOnOf(false.toString()))
        assertEquals(2, decodeCommute(saved).size)
        assertTrue(commuteOnOf(true.toString()))
        assertEquals(listOf("하행", "상행"), decodeCommute(saved).map { it.updnLine })
    }

    @Test
    fun `역명에 구분자가 섞여도 줄이 안 깨진다`() {
        val s = encodeCommute(listOf(CommuteStation("가;나|다", "1002", "내선")))
        assertEquals("가나다|1002|내선", s)
        assertNotNull(decodeCommute(s).firstOrNull())
        assertEquals(1, decodeCommute(s).size)
    }
}
