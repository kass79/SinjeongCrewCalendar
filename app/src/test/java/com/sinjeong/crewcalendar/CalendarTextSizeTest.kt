package com.sinjeong.crewcalendar

import com.sinjeong.crewcalendar.presentation.calendar.CalendarTextSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 달력 칸 **글꼴 크기 단계**(v1.7.15 ⑦) — 카스: *"달력숫자,메모 글꼴크기 설정할수 있게 해줘"*.
 *
 * 잠그는 것 셋: ① **기본값은 종전 크기**(배수 정확히 1f) ② 저장·복원(모르는 값·빈 값 → 기본)
 * ③ 단계별 sp 값과 **시스템 글자배율과의 곱 상한**.
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
     * **시스템 글자배율과 곱해진다** — 안드로이드 설정 1.5배 사용자가 여기서 `크게` 를 고르면
     * `1.5 × 1.2 = 1.8` 이다. 곱이 [CalendarTextSize.MAX_SCALE](**1.8**)를 넘으면 거기서 멎는다.
     *
     * 근거는 `CalendarStyle.kt` 의 KDoc: 출시 점검표가 매번 확인하는 배율이 **1.5** 이고 그 위
     * 한 단(`크게` 1.2)까지가 실측 여유다(폰 5주 달, 출근시각 아래 86dp 에 메모 한 줄 20.5dp → 4줄).
     *
     * ⚠ **상한이 먹어도 `보통` 밑으로는 안 내려간다** — 사용자가 고르지도 않은 `작게` 가 되는 것은
     * 잘못이다. 그래서 하한이 1f 다.
     */
    @Test
    fun `시스템 배율과의 곱은 상한에서 멎는다`() {
        assertEquals(1.8f, CalendarTextSize.MAX_SCALE, 0f)
        // 배율 1.0 — 네 단계가 전부 그대로 산다
        CalendarTextSize.entries.forEach {
            assertEquals(it.factor, it.factorAt(1f), 0f)
        }
        // 배율 1.5 — `크게` 는 곱 1.8 이라 딱 상한, `아주 크게` 는 2.1 이라 1.2 로 잘린다
        assertEquals(1.2f, CalendarTextSize.LARGE.factorAt(1.5f), 0.0001f)
        assertEquals(1.2f, CalendarTextSize.XLARGE.factorAt(1.5f), 0.0001f)
        // 배율 2.0 — 곱 상한이 1.8 이라 키우는 두 단계가 0.9 로 내려가야 하지만 **1f 에서 멎는다**
        assertEquals(1f, CalendarTextSize.LARGE.factorAt(2f), 0f)
        assertEquals(1f, CalendarTextSize.XLARGE.factorAt(2f), 0f)
        // `작게` 는 상한과 무관하다(줄이는 쪽은 칸을 안 깨뜨린다)
        assertEquals(0.85f, CalendarTextSize.SMALL.factorAt(2f), 0f)
        // 배율이 1 밑(작은 글꼴 사용자)이어도 배수를 **부풀리지 않는다**
        assertEquals(1.4f, CalendarTextSize.XLARGE.factorAt(0.85f), 0f)
        // 어떤 배율에서도 곱은 상한을 안 넘는다
        listOf(1f, 1.15f, 1.3f, 1.5f, 1.8f, 2f, 2.5f).forEach { fs ->
            CalendarTextSize.entries.forEach { s ->
                assertTrue(
                    "곱 상한 초과: $s x $fs",
                    s.factorAt(fs) * fs <= CalendarTextSize.MAX_SCALE + 0.0001f || s.factorAt(fs) <= 1f,
                )
            }
        }
    }
}
