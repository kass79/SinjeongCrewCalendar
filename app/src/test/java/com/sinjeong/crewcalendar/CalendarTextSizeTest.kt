package com.sinjeong.crewcalendar

import com.sinjeong.crewcalendar.presentation.calendar.CalendarTextSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 달력 칸 **글꼴 크기 단계**(v1.7.15 ⑦) — 카스: *"달력숫자,메모 글꼴크기 설정할수 있게 해줘"*.
 *
 * 잠그는 것 넷: ① **기본값은 종전 크기**(배수 정확히 1f) ② 저장·복원(모르는 값·빈 값 → 기본)
 * ③ 단계별 sp 값 ④ **어느 배율에서든 네 단계가 서로 다르다**(v1.7.17 ①).
 *
 * ⚠ 이 하네스에는 **Compose 가 없다**(`tools/runtests.ps1`) — 그래서 `TextUnit` 이 아니라
 * `Float`(sp 숫자)을 읽는다. `CalendarStyle.kt` 의 **최상위** 프로퍼티(`CLAY_PALETTE`)는
 * `Color(...)` 를 부르므로 상수를 최상위로 옮기면 클래스 초기화가 터진다(`CalendarStyleTest` 참고).
 */
class CalendarTextSizeTest {

    /**
     * **기본값 `보통` 의 배수는 정확히 1f 다.**
     *
     * 이 한 줄이 "아무것도 안 고른 사용자의 화면이 한 픽셀도 안 바뀐다"의 근거다 —
     * `6f * 1f` 는 IEEE754 에서 **비트까지 같은 값**이라 `dateStep.spOf(6f, 1f)` 가 종전
     * `6.sp` 와 같은 `TextUnit` 을 만든다. 0.999 같은 "거의 1" 로 바꾸면 그 순간 회귀다.
     */
    @Test
    fun `기본은 보통이고 배수가 정확히 1이다`() {
        assertEquals(CalendarTextSize.NORMAL, CalendarTextSize.of(null))
        assertEquals(1f, CalendarTextSize.NORMAL.factor, 0f)
        assertEquals(1f, CalendarTextSize.NORMAL.factorAt(1f), 0f)
        // 시스템 배율이 얼마든 `보통` 은 곱을 안 건드린다(상한도 안 먹는다)
        assertEquals(1f, CalendarTextSize.NORMAL.factorAt(1.5f), 0f)
        assertEquals(1f, CalendarTextSize.NORMAL.factorAt(2f), 0f)
        // 종전 sp 그대로
        assertEquals(6f, CalendarTextSize.NORMAL.spOf(CalendarTextSize.DATE_SP, 1f), 0f)
        assertEquals(8f, CalendarTextSize.NORMAL.spOf(CalendarTextSize.DATE_SP_BIG, 1f), 0f)
        assertEquals(9.5f, CalendarTextSize.NORMAL.spOf(CalendarTextSize.MEMO_SP, 1f), 0f)
        assertEquals(11f, CalendarTextSize.NORMAL.spOf(CalendarTextSize.MEMO_SP_BIG, 1f), 0f)
    }

    /**
     * 저장·복원 — 설정에서 고른 값이 `theme` 저장소(`cal_date_size`·`cal_memo_size`)에 `.name`
     * 으로 앉았다가 그대로 돌아온다. **모르는 값·빈 값·`null` 은 기본**이다: 옛 판에서 올라온
     * 기기, 저장이 날아간 기기, enum 이름을 나중에 잘못 바꾼 경우가 다 여기로 떨어진다.
     */
    @Test
    fun `단계는 저장값에서 그대로 복원된다`() {
        CalendarTextSize.entries.forEach { assertEquals(it, CalendarTextSize.of(it.name)) }
        assertEquals(CalendarTextSize.NORMAL, CalendarTextSize.of(null))      // 처음 켠 기기
        assertEquals(CalendarTextSize.NORMAL, CalendarTextSize.of(""))        // 저장이 비었다
        assertEquals(CalendarTextSize.NORMAL, CalendarTextSize.of("large"))   // 대소문자가 다르다
        assertEquals(CalendarTextSize.NORMAL, CalendarTextSize.of("HUGE"))    // 없어진 단계
        assertEquals(CalendarTextSize.NORMAL, CalendarTextSize.of("1.2"))     // 숫자를 저장한 옛 판
    }

    /**
     * **단계별 sp 값**(시스템 배율 1.0). 화면에서 실제로 그려지는 숫자다 —
     * 날짜 숫자는 폰 6sp / 펼침 8sp, 메모는 폰 9.5sp / 펼침 11sp 가 기준이다.
     *
     * | 단계 | 배수 | 날짜(폰) | 날짜(펼침) | 메모(폰) | 메모(펼침) |
     * |---|---|---|---|---|---|
     * | 작게 | 0.85 | 5.10 | 6.80 | 8.075 | 9.35 |
     * | 보통 | 1.00 | **6.00** | **8.00** | **9.50** | **11.00** |
     * | 크게 | 1.20 | 7.20 | 9.60 | 11.40 | 13.20 |
     * | 아주 크게 | 1.40 | 8.40 | 11.20 | 13.30 | 15.40 |
     */
    @Test
    fun `단계별 sp 값이 표와 같다`() {
        val date = listOf(5.10f, 6.00f, 7.20f, 8.40f)
        val dateBig = listOf(6.80f, 8.00f, 9.60f, 11.20f)
        val memo = listOf(8.075f, 9.50f, 11.40f, 13.30f)
        val memoBig = listOf(9.35f, 11.00f, 13.20f, 15.40f)
        CalendarTextSize.entries.forEachIndexed { i, s ->
            assertEquals(date[i], s.spOf(CalendarTextSize.DATE_SP, 1f), 0.001f)
            assertEquals(dateBig[i], s.spOf(CalendarTextSize.DATE_SP_BIG, 1f), 0.001f)
            assertEquals(memo[i], s.spOf(CalendarTextSize.MEMO_SP, 1f), 0.001f)
            assertEquals(memoBig[i], s.spOf(CalendarTextSize.MEMO_SP_BIG, 1f), 0.001f)
        }
    }

    /**
     * **배율이 얼마든 네 단계는 서로 다른 값이다**(v1.7.17 ① — 이 파일의 핵심 자물쇠).
     *
     * 카스 보고(2026-09-09, 폴드7 을 글자 크게 놓고 씀): *"달력 날짜, 숫자 크기, 달력 메모크기
     * 적용이 잘안되는듯?"*. v1.7.16 까지의 **곱 상한 1.8** 은 배율 1.5 에서 `크게`=`아주 크게`,
     * 배율 1.8 이상에서 `보통`=`크게`=`아주 크게` 로 단계를 뭉갰다(에뮬 실측: fs 2.0 의 세 단계가
     * 모두 `dateSp=8.0 memoSp=11.0`). 그래서 **곱이 아니라 폭**에 상한을 건다.
     *
     * 이 테스트가 깨지면 그 증상이 돌아온 것이다 — 상한을 곱 쪽으로 되돌리지 말 것.
     */
    @Test
    fun `어느 배율에서든 네 단계는 서로 다르다`() {
        val scales = listOf(0.85f, 1f, 1.15f, 1.3f, 1.5f, 1.7f, 1.8f, 2f, 2.5f, 3f)
        scales.forEach { fs ->
            val f = CalendarTextSize.entries.map { it.factorAt(fs) }
            assertEquals("배율 $fs 에서 단계가 뭉쳤다: $f", 4, f.toSet().size)
            // 순서도 지킨다 — 작게 < 보통 < 크게 < 아주 크게
            assertTrue("배율 $fs 순서가 뒤집혔다: $f", f.zipWithNext().all { (a, b) -> a < b })
            // 그려지는 sp 도 네 값이 서로 다르다(날짜·메모 · 폰·펼침 네 기준 모두)
            listOf(
                CalendarTextSize.DATE_SP, CalendarTextSize.DATE_SP_BIG,
                CalendarTextSize.MEMO_SP, CalendarTextSize.MEMO_SP_BIG,
            ).forEach { base ->
                val sp = CalendarTextSize.entries.map { it.spOf(base, fs) }
                assertEquals("배율 $fs · 기준 $base sp 가 뭉쳤다: $sp", 4, sp.toSet().size)
            }
        }
    }

    /**
     * **상한은 키우는 두 단계에만 걸린다** — `작게`·`보통` 은 배율이 얼마든 그대로다.
     *
     * `보통` 이 그대로여야 하는 이유: 아무것도 안 고른 사람의 달력이 v1.7.14 와 같아야 하고
     * (배율 1.0 뿐 아니라 **모든 배율에서**), 시스템 글자배율은 접근성 설정이라
     * 앱이 임의로 눌러 **글자를 작게 만들면 안 된다**.
     */
    @Test
    fun `상한은 키우는 단계에만 걸린다`() {
        assertEquals(1.5f, CalendarTextSize.MAX_EXTRA_SCALE, 0f)
        listOf(0.85f, 1f, 1.5f, 2f, 3f).forEach { fs ->
            assertEquals("배율 $fs 에서 작게가 움직였다", 0.85f, CalendarTextSize.SMALL.factorAt(fs), 0f)
            assertEquals("배율 $fs 에서 보통이 움직였다", 1f, CalendarTextSize.NORMAL.factorAt(fs), 0f)
        }
        // 한계 배율까지는 고른 배수가 **그대로** 산다(1.0 화면은 v1.7.16 과 픽셀 0 차이)
        listOf(1f, 1.15f, 1.3f, 1.5f).forEach { fs ->
            CalendarTextSize.entries.forEach {
                assertEquals("배율 $fs 에서 ${it.name} 이 눌렸다", it.factor, it.factorAt(fs), 0.0001f)
            }
        }
        // 한계 배율 위에서는 **폭만** 줄고, 그래도 `보통`(1f)보다는 크다
        listOf(1.7f, 2f, 2.5f, 3f).forEach { fs ->
            listOf(CalendarTextSize.LARGE, CalendarTextSize.XLARGE).forEach {
                val v = it.factorAt(fs)
                assertTrue("배율 $fs · ${it.name} 이 보통 이하다: $v", v > 1f)
                assertTrue("배율 $fs · ${it.name} 이 안 눌렸다: $v", v < it.factor)
            }
        }
        // 폭은 배율 1.5 에서 그린 절대 크기에서 **더 안 자란다**
        listOf(1.7f, 2f, 3f).forEach { fs ->
            CalendarTextSize.entries.filter { it.factor > 1f }.forEach {
                val extra = (it.factorAt(fs) - 1f) * fs
                assertEquals("배율 $fs · ${it.name} 폭이 1.5배 자리를 벗어났다",
                    (it.factor - 1f) * CalendarTextSize.MAX_EXTRA_SCALE, extra, 0.0001f)
            }
        }
    }
}
