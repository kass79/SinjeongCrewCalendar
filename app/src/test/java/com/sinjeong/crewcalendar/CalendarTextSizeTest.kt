package com.sinjeong.crewcalendar

import com.sinjeong.crewcalendar.presentation.calendar.CalendarTextSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 달력 칸 **글꼴 크기 단계**(v1.7.15 ⑦ · 다이아는 v1.7.18 ③) — 카스:
 * *"달력숫자,메모 글꼴크기 설정할수 있게 해줘"* ·
 * *"달력 날짜 숫자크기와 메모 크기가 큰 차이가 없는듯? 그리고 근무 다이아 크기는 없네?"*.
 *
 * 잠그는 것 다섯: ① **기본값은 종전 크기**(배수 정확히 1f) ② 저장·복원(모르는 값·빈 값 → 기본)
 * ③ 네 배수가 정확히 `0.85 / 1 / 1.3 / 1.6` ④ 단계별 sp·dp 값 ⑤ **시스템 배율이 배수를 못 누른다**
 * (v1.7.18 ③ — 배율 10개에서 네 단계가 늘 서로 다르고 **간격 비가 배율과 무관**).
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
     * `6f * 1f` 는 IEEE754 에서 **비트까지 같은 값**이라 `dateStep.spOf(6f)` 가 종전
     * `6.sp` 와 같은 `TextUnit` 을 만든다. 0.999 같은 "거의 1" 로 바꾸면 그 순간 회귀다.
     */
    @Test
    fun `기본은 보통이고 배수가 정확히 1이다`() {
        assertEquals(CalendarTextSize.NORMAL, CalendarTextSize.of(null))
        assertEquals(1f, CalendarTextSize.NORMAL.factor, 0f)
        // 종전 sp 그대로 — 날짜·메모·근무 칩 여섯 기준 전부
        assertEquals(6f, CalendarTextSize.NORMAL.spOf(CalendarTextSize.DATE_SP), 0f)
        assertEquals(8f, CalendarTextSize.NORMAL.spOf(CalendarTextSize.DATE_SP_BIG), 0f)
        assertEquals(9.5f, CalendarTextSize.NORMAL.spOf(CalendarTextSize.MEMO_SP), 0f)
        assertEquals(11f, CalendarTextSize.NORMAL.spOf(CalendarTextSize.MEMO_SP_BIG), 0f)
        assertEquals(11.5f, CalendarTextSize.NORMAL.spOf(CalendarTextSize.DUTY_SP), 0f)
        assertEquals(13f, CalendarTextSize.NORMAL.spOf(CalendarTextSize.DUTY_SP_BIG), 0f)
        assertEquals(10f, CalendarTextSize.NORMAL.spOf(CalendarTextSize.DUTY_SP_SMALL), 0f)
        assertEquals(11.5f, CalendarTextSize.NORMAL.spOf(CalendarTextSize.DUTY_SP_SMALL_BIG), 0f)
        // 칩 폭도 종전 그대로(34dp / 펼침 42dp)
        assertEquals(34f, CalendarTextSize.NORMAL.spOf(CalendarTextSize.DUTY_CHIP_DP), 0f)
        assertEquals(42f, CalendarTextSize.NORMAL.spOf(CalendarTextSize.DUTY_CHIP_DP_BIG), 0f)
    }

    /**
     * 저장·복원 — 설정에서 고른 값이 `theme` 저장소(`cal_date_size`·`cal_memo_size`·`cal_duty_size`)
     * 에 `.name` 으로 앉았다가 그대로 돌아온다. **모르는 값·빈 값·`null` 은 기본**이다: 옛 판에서
     * 올라온 기기, 저장이 날아간 기기, enum 이름을 나중에 잘못 바꾼 경우가 다 여기로 떨어진다.
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
     * **네 배수가 정확히 `0.85 / 1 / 1.3 / 1.6` 이다**(v1.7.18 ③).
     *
     * v1.7.17 까지는 `1.2 / 1.4` 였고 카스가 *"큰 차이가 없는듯?"* 이라 했다.
     * `작게` 0.85 와 `보통` 1 은 카스 확정으로 **그대로 둔다**(v1.7.15 ⑦ ③ *"했으면 그냥 나둬"*).
     */
    @Test
    fun `네 배수가 표와 같다`() {
        assertEquals(0.85f, CalendarTextSize.SMALL.factor, 0f)
        assertEquals(1f, CalendarTextSize.NORMAL.factor, 0f)
        assertEquals(1.3f, CalendarTextSize.LARGE.factor, 0f)
        assertEquals(1.6f, CalendarTextSize.XLARGE.factor, 0f)
        // 표시 순서 = 커지는 순서
        assertEquals(
            listOf("작게", "보통", "크게", "아주 크게"),
            CalendarTextSize.entries.map { it.label },
        )
        assertTrue(
            "배수가 커지는 순서가 아니다",
            CalendarTextSize.entries.map { it.factor }.zipWithNext().all { (a, b) -> a < b },
        )
    }

    /**
     * **단계별 sp 값**(화면에서 실제로 그려지는 숫자 — 시스템 배율은 `sp` 가 따로 얹는다).
     * 날짜 숫자는 폰 6sp / 펼침 8sp, 메모는 9.5 / 11sp, 근무 칩은 11.5 / 13sp
     * (세 글자 이상은 10 / 11.5sp)가 기준이고 칩 폭은 34 / 42dp 다.
     *
     * | 단계 | 배수 | 날짜(폰) | 메모(폰) | 칩글자(폰) | 칩폭(폰) |
     * |---|---|---|---|---|---|
     * | 작게 | 0.85 | 5.10 | 8.075 | 9.775 | 28.9 |
     * | 보통 | 1.00 | **6.00** | **9.50** | **11.50** | **34.0** |
     * | 크게 | 1.30 | 7.80 | 12.35 | 14.95 | 44.2 |
     * | 아주 크게 | 1.60 | 9.60 | 15.20 | 18.40 | 54.4 |
     */
    @Test
    fun `단계별 sp 값이 표와 같다`() {
        val expected = mapOf(
            CalendarTextSize.DATE_SP to listOf(5.10f, 6.00f, 7.80f, 9.60f),
            CalendarTextSize.DATE_SP_BIG to listOf(6.80f, 8.00f, 10.40f, 12.80f),
            CalendarTextSize.MEMO_SP to listOf(8.075f, 9.50f, 12.35f, 15.20f),
            CalendarTextSize.MEMO_SP_BIG to listOf(9.35f, 11.00f, 14.30f, 17.60f),
            CalendarTextSize.DUTY_SP to listOf(9.775f, 11.50f, 14.95f, 18.40f),
            CalendarTextSize.DUTY_SP_BIG to listOf(11.05f, 13.00f, 16.90f, 20.80f),
            CalendarTextSize.DUTY_SP_SMALL to listOf(8.50f, 10.00f, 13.00f, 16.00f),
            CalendarTextSize.DUTY_CHIP_DP to listOf(28.90f, 34.00f, 44.20f, 54.40f),
            CalendarTextSize.DUTY_CHIP_DP_BIG to listOf(35.70f, 42.00f, 54.60f, 67.20f),
        )
        expected.forEach { (base, want) ->
            CalendarTextSize.entries.forEachIndexed { i, s ->
                assertEquals("기준 $base · ${s.name}", want[i], s.spOf(base), 0.001f)
            }
        }
    }

    /**
     * **시스템 글자배율은 단계를 못 누른다**(v1.7.18 ③ — 이 파일의 핵심 자물쇠).
     *
     * 카스(2026-09-09, 폴드7 을 글자 크게 놓고 씀): *"달력 날짜 숫자크기와 메모 크기가
     * **큰 차이가 없는듯?**"*. v1.7.15~16 은 **곱 상한 1.8**, v1.7.17 은 **폭 상한**
     * (`MAX_EXTRA_SCALE / fontScale`)이라 **배율이 클수록 단계 차이가 작아졌다** —
     * 배율 2.0 에서 `크게` 는 1.15, `아주 크게` 는 1.3 으로 눌렸다. 정작 이 설정이 가장
     * 필요한 사람(= 카스)이 가장 밋밋한 화면을 봤다.
     *
     * 이제 [CalendarTextSize.spOf] 는 배율을 아예 안 받는다. 아래는 그 결과를 화면 쪽에서
     * 확인하는 자물쇠 — **그려지는 크기 `spOf(base) × 배율`** 이 어느 배율에서든 ㉠ 네 값이
     * 서로 다르고 ㉡ 커지는 순서이며 ㉢ **이웃 단계의 비가 배율과 무관하게 같다**(㉢ 이 깨지면
     * 배율에 따라 누르는 규칙이 되살아난 것이다 — 되돌리지 말 것).
     */
    @Test
    fun `어느 배율에서든 네 단계 간격이 그대로다`() {
        val scales = listOf(0.85f, 1f, 1.15f, 1.3f, 1.5f, 1.7f, 1.8f, 2f, 2.5f, 3f)
        val bases = listOf(
            CalendarTextSize.DATE_SP, CalendarTextSize.DATE_SP_BIG,
            CalendarTextSize.MEMO_SP, CalendarTextSize.MEMO_SP_BIG,
            CalendarTextSize.DUTY_SP, CalendarTextSize.DUTY_SP_BIG,
        )
        // 이웃 단계 비 — 배율과 무관한 상수여야 한다
        val want = CalendarTextSize.entries.map { it.factor }
            .zipWithNext().map { (a, b) -> b / a }
        scales.forEach { fs ->
            bases.forEach { base ->
                val drawn = CalendarTextSize.entries.map { it.spOf(base) * fs }
                assertEquals("배율 $fs · 기준 $base 가 뭉쳤다: $drawn", 4, drawn.toSet().size)
                assertTrue(
                    "배율 $fs · 기준 $base 순서가 뒤집혔다: $drawn",
                    drawn.zipWithNext().all { (a, b) -> a < b },
                )
                drawn.zipWithNext().map { (a, b) -> b / a }.forEachIndexed { i, got ->
                    assertEquals(
                        "배율 $fs · 기준 $base 에서 ${i}번 간격이 눌렸다: $got",
                        want[i], got, 0.0001f,
                    )
                }
            }
        }
    }

    /**
     * **`작게`·`보통` 은 어느 배율에서도 그대로다.**
     *
     * 시스템 글자배율은 접근성 설정이라 앱이 임의로 눌러 **글자를 작게 만들면 안 된다**.
     * 배수가 배율을 안 보는 지금 구조에서는 자동으로 성립하지만, 누가 배율 인자를 다시
     * 들여올 때 여기서 걸린다.
     */
    @Test
    fun `작게와 보통은 배율과 무관하다`() {
        listOf(0.85f, 1f, 1.5f, 2f, 3f).forEach { fs ->
            assertEquals(
                "배율 $fs 에서 작게가 움직였다",
                CalendarTextSize.DATE_SP * 0.85f * fs,
                CalendarTextSize.SMALL.spOf(CalendarTextSize.DATE_SP) * fs, 0.0001f,
            )
            assertEquals(
                "배율 $fs 에서 보통이 움직였다",
                CalendarTextSize.MEMO_SP * fs,
                CalendarTextSize.NORMAL.spOf(CalendarTextSize.MEMO_SP) * fs, 0f,
            )
        }
    }
}
