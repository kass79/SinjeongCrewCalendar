package com.sinjeong.crewcalendar

import com.sinjeong.crewcalendar.presentation.live.ArrivalRow
import com.sinjeong.crewcalendar.presentation.live.BranchLive
import com.sinjeong.crewcalendar.presentation.live.COMMUTE_MAX
import com.sinjeong.crewcalendar.presentation.live.CommuteStation
import com.sinjeong.crewcalendar.presentation.live.StationRow
import com.sinjeong.crewcalendar.presentation.live.bareStation
import com.sinjeong.crewcalendar.presentation.live.boundOf
import com.sinjeong.crewcalendar.presentation.live.commuteChipLabel
import com.sinjeong.crewcalendar.presentation.live.commuteLabel
import com.sinjeong.crewcalendar.presentation.live.commuteOnOf
import com.sinjeong.crewcalendar.presentation.live.commuteOptions
import com.sinjeong.crewcalendar.presentation.live.commuteStops
import com.sinjeong.crewcalendar.presentation.live.decodeCommute
import com.sinjeong.crewcalendar.presentation.live.encodeCommute
import com.sinjeong.crewcalendar.presentation.live.lineName
import com.sinjeong.crewcalendar.presentation.live.stationQueries
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

    /* ── v1.7.14 ① 이름에 `역` 을 붙여도 찾아진다 ─────────────── */

    /**
     * 카스: *"출퇴근역 **검색에 마곡, 이면 마곡역으로까지 검색**되게 해줘!"*
     *
     * 도착 API 는 `마곡` 으로만 답한다 — 2026-09-07 실호출에서
     * `realtimeStationArrival/마곡` 은 4건, **`.../마곡역` 은 `INFO-200`(0건)** 이었다.
     * 그래서 **두 꼴을 차례로** 시도하고 **호출은 최대 2회**다.
     */
    @Test
    fun `조회 후보는 원문 먼저 그다음 역 붙이거나 뗀 꼴 - 최대 둘`() {
        assertEquals(listOf("마곡", "마곡역"), stationQueries("마곡"))
        assertEquals(listOf("마곡역", "마곡"), stationQueries("마곡역"))
        // 앞뒤 공백은 떼고, 이름은 **한 글자도 안 줄인다**(확정 표)
        assertEquals(listOf("구로디지털단지", "구로디지털단지역"), stationQueries("  구로디지털단지 "))
        // 빈 값이면 조회 자체를 안 한다
        assertEquals(emptyList<String>(), stationQueries(""))
        assertEquals(emptyList<String>(), stationQueries("   "))
        // `역` 한 글자는 떼면 빈 이름이라 후보가 하나뿐이다
        assertEquals(listOf("역"), stationQueries("역"))
        // **어떤 입력이든 둘을 안 넘는다** — 과다 호출 금지
        listOf("마곡", "마곡역", "역곡", "서울역", "동대문역사문화공원", "역", "a").forEach {
            assertTrue(it, stationQueries(it).size <= 2)
        }
    }

    /**
     * ⚠ **`서울역` 은 이름이 `역` 으로 끝나는 진짜 역이다**(1·4호선·경의선·공항철도 —
     * 2026-09-07 역 목록 799행 전수에서 `역` 으로 끝나는 이름은 이 하나뿐이었다).
     * `서울` 은 **GTX-A 의 다른 역**이라 무턱대고 떼면 딴 역을 부른다.
     * 그래서 [stationQueries] 는 **떼는 것이 아니라 원문을 먼저 시도**한다.
     */
    @Test
    fun `서울역은 원문이 먼저라 다른 역으로 안 샌다`() {
        assertEquals("서울역", stationQueries("서울역").first())
        assertEquals(listOf("서울역", "서울"), stationQueries("서울역"))
    }

    @Test
    fun `대조용 이름은 꼬리 역만 뗀다`() {
        assertEquals("마곡", bareStation("마곡역"))
        assertEquals("마곡", bareStation(" 마곡 "))
        assertEquals("역곡", bareStation("역곡"))       // 앞의 `역` 은 안 건드린다
        assertEquals("역", bareStation("역"))           // 한 글자는 그대로(빈 이름 방지)
        assertEquals("", bareStation(""))
    }

    /* ── v1.7.14 ② 칩에서 방향 빼기 ─────────────────────────── */

    /**
     * 카스: *"출퇴근역 아이콘에 **하행,내선 이런 정보는 안해도** 될꺼같애..그래야 **가로 크기가
     * 줄어들듯**"* — **v1.7.13 ⑧ 을 되무르는 것이고 카스의 결정이다.**
     *
     * ⚠ **설정 목록([commuteLabel])은 방향을 그대로 둔다** — 거기서는 같은 역 두 줄을 갈라
     * **지워야** 하므로 글자가 같으면 무엇을 지우는지 알 수 없다.
     */
    @Test
    fun `칩 글자에는 방향이 없고 설정 목록에는 있다`() {
        val down = CommuteStation("마곡", "1005", "하행")
        val up = CommuteStation("마곡", "1005", "상행")
        assertEquals("5호선 마곡", commuteChipLabel(down))
        assertEquals("5호선 마곡", commuteChipLabel(up))
        // 칩 두 개의 글자는 이제 **같다** — 무엇을 고른 상태인지는 칩 색(v1.7.14 ④)이 말한다.
        assertEquals(commuteChipLabel(down), commuteChipLabel(up))
        // 설정 목록은 종전대로 갈린다
        assertEquals("5호선 마곡 · 하행", commuteLabel(down))
        assertEquals("5호선 마곡 · 상행", commuteLabel(up))
        assertTrue(commuteLabel(down) != commuteLabel(up))
        // 역 이름은 여전히 한 글자도 안 줄인다
        assertEquals(
            "2호선 구로디지털단지",
            commuteChipLabel(CommuteStation("구로디지털단지", "1002", "내선")),
        )
        assertEquals("9999 어딘가", commuteChipLabel(CommuteStation("어딘가", "9999", "상행")))
    }

    /* ── v1.7.15 ② 저장 형식 — 다섯 칸 이름 + 진행 방향 ─────── */

    /**
     * 카스: *"마곡역이면 **김포공항-송정-마곡-발산-우장산** 이렇게 표시해주고 역명까지"* ·
     * *"상행을 고르면 **열차가 반대방향으로 가면 되지**"*.
     *
     * 다섯 이름은 **등록할 때 한 번** 얻어 저장값에 담고(볼 때마다 안 부른다), 차례는
     * **지리 오름차순 고정**이라 방향이 무엇이든 글자가 같다. 뒤집는 것은 마지막 칸
     * (`fromHigher`)이 정하는 **기관차 머리**다.
     */
    @Test
    fun `다섯 칸 이름과 방향이 붙은 저장 문자열 왕복`() {
        val list = listOf(
            CommuteStation(
                "마곡", "1005", "상행",
                listOf("김포공항", "송정", "마곡", "발산", "우장산"), fromHigher = true,
            ),
            CommuteStation(
                "까치산", "1002", "내선",
                listOf("양천구청", "신정네거리", "까치산", "", ""), fromHigher = false,
            ),
        )
        val s = encodeCommute(list)
        assertEquals(
            "마곡|1005|상행|김포공항,송정,마곡,발산,우장산|1;" +
                "까치산|1002|내선|양천구청,신정네거리,까치산,,|0",
            s,
        )
        assertEquals(list, decodeCommute(s))
    }

    /** 같은 역·같은 이름인데 **방향만 반대**면 마지막 칸 하나만 다르다. */
    @Test
    fun `차례는 방향이 달라도 같고 마지막 칸만 갈린다`() {
        val stops = listOf("김포공항", "송정", "마곡", "발산", "우장산")
        val up = encodeCommute(listOf(CommuteStation("마곡", "1005", "상행", stops, true)))
        val down = encodeCommute(listOf(CommuteStation("마곡", "1005", "하행", stops, false)))
        assertEquals("마곡|1005|상행|김포공항,송정,마곡,발산,우장산|1", up)
        assertEquals("마곡|1005|하행|김포공항,송정,마곡,발산,우장산|0", down)
        // 이름 다섯 칸은 **글자 그대로 같다** — 화면에서 뒤집지 않는다는 뜻이다.
        assertEquals(up.split("|")[3], down.split("|")[3])
    }

    /**
     * ⚠ **옛 저장값에 화면이 안 죽는다 — 다만 이름은 안 받는다.**
     *
     * v1.7.14 의 넷째 칸은 **방향에 따라 뒤집힌 이웃 넷**이라 지금 규칙(지리 오름차순 고정)으로
     * 읽으면 상행 역이 거꾸로 그려진다. 조용히 틀리느니 **다시 등록하게** 두는 쪽을 골랐다
     * (화면은 *"역을 다시 등록해 주세요"* 라고 말한다).
     */
    @Test
    fun `옛 저장값은 역만 살고 이름은 안 받는다`() {
        // v1.7.9~v1.7.13 꼴(방향 있음 · 이름 없음)
        val old = "마곡|1005|하행;까치산|1002|내선;까치산|1005|상행;신도림|1002|외선"
        val back = decodeCommute(old)
        assertEquals(4, back.size)
        assertEquals(listOf("마곡", "까치산", "까치산", "신도림"), back.map { it.name })
        assertEquals(listOf("하행", "내선", "상행", "외선"), back.map { it.updnLine })
        back.forEach { assertEquals(emptyList<String>(), it.stops) }
        // 이름이 없으면 다시 저장해도 **글자가 그대로** — 형식이 조용히 늘지 않는다
        assertEquals(old, encodeCommute(back))
        // v1.7.14 꼴(이웃 넷)도 이름 없음으로 떨어진다 — 칸이 넷이라 다섯 규칙을 못 넘는다
        val v1714 = "마곡|1005|하행|김포공항,송정,발산,우장산"
        assertEquals(1, decodeCommute(v1714).size)
        assertEquals(emptyList<String>(), decodeCommute(v1714).first().stops)
        assertEquals(false, decodeCommute(v1714).first().fromHigher)
        // 옛 꼴과 새 꼴이 한 줄에 섞여 있어도 각자 제대로 읽힌다
        val mixed = "마곡|1005|하행|김포공항,송정,마곡,발산,우장산|0;신도림|1002|외선"
        assertEquals(
            listOf(listOf("김포공항", "송정", "마곡", "발산", "우장산"), emptyList()),
            decodeCommute(mixed).map { it.stops },
        )
    }

    @Test
    fun `이름 칸이 다섯이 아니면 버린다 - 잘린 값에 화면이 안 죽는다`() {
        // 넷·여섯은 통째로 버리고 역만 살린다(칸 수가 어긋나면 어느 자리인지 알 수 없다)
        assertEquals(emptyList<String>(), decodeCommute("마곡|1005|하행|가,나,다,라|0").first().stops)
        assertEquals(emptyList<String>(), decodeCommute("마곡|1005|하행|가,나,다,라,마,바|0").first().stops)
        // 모르는 방향 글자는 오름차순(false)으로 떨어진다 — 예외를 안 던진다
        assertEquals(false, decodeCommute("마곡|1005|하행|가,나,다,라,마|ㅁ").first().fromHigher)
        // 칸이 여섯 이상인 손댄 값도 앞 다섯만 읽고 산다
        assertEquals(1, decodeCommute("마곡|1005|하행|가,나,다,라,마|1|덤").size)
        assertEquals(true, decodeCommute("마곡|1005|하행|가,나,다,라,마|1|덤").first().fromHigher)
        // 역명에 쉼표가 있어도 줄이 안 깨진다(저장할 때 지운다)
        val s = encodeCommute(
            listOf(CommuteStation("가,나", "1002", "내선", listOf("다,라", "", "가,나", "", ""))),
        )
        assertEquals("가나|1002|내선|다라,,가나,,|0", s)
        assertEquals(listOf("다라", "", "가나", "", ""), decodeCommute(s).first().stops)
    }

    /* ── v1.7.15 ② 차례는 지리 순서 하나로 고정 ──────────────── */

    /** 2026-09-07 `SearchSTNBySubwayLineInfo/05호선` 실응답에서 뽑은 앞머리(차례가 섞여 온다) */
    private val line5 = listOf(
        StationRow("515", "발산"), StationRow("510", "방화"), StationRow("513", "송정"),
        StationRow("511", "개화산"), StationRow("516", "우장산"), StationRow("512", "김포공항"),
        StationRow("514", "마곡"),
    )

    /**
     * 카스의 예 그대로: *"마곡역이면 **김포공항-송정-마곡-발산-우장산**"*.
     * ⚠ **방향 인자가 없다** — v1.7.14 의 `commuteNeighbors(rows, name, approachFromHigher)` 가
     * 상행에서 차례를 뒤집던 것을 카스가 물렸다. 이제 `FR_CODE` 오름차순 하나뿐이다.
     */
    @Test
    fun `다섯 칸은 늘 FR_CODE 오름차순이고 가운데가 등록역이다`() {
        val got = commuteStops(line5, "마곡")
        assertEquals(listOf("김포공항", "송정", "마곡", "발산", "우장산"), got)
        assertEquals("마곡", got[2])                       // COMMUTE_HERE
        // `역` 을 붙여 쳐도 같은 줄을 찾는다
        assertEquals(got, commuteStops(line5, "마곡역"))
        // 끝 역은 모자라는 자리가 빈칸이고 **개수는 늘 다섯**이다
        assertEquals(listOf("", "", "방화", "개화산", "김포공항"), commuteStops(line5, "방화"))
        // 모르는 역·빈 목록은 빈 목록(예외 없음)
        assertEquals(emptyList<String>(), commuteStops(line5, "없는역"))
        assertEquals(emptyList<String>(), commuteStops(emptyList(), "마곡"))
    }
}
