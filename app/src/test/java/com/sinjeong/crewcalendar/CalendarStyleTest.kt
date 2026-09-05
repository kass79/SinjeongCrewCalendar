package com.sinjeong.crewcalendar

import com.sinjeong.crewcalendar.presentation.calendar.CalendarArgb
import com.sinjeong.crewcalendar.presentation.calendar.CalendarStyle
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 달력 스타일(v1.7.6) — **기본값이 안 바뀌는 것**과 **고른 값이 살아 돌아오는 것** 둘을 잠근다.
 *
 * ⚠ 이 하네스에는 **Compose 가 없다**(`tools/runtests.ps1`) — 그래서 팔레트가 아니라
 * `CalendarArgb` 의 숫자를 읽는다. `CalendarPalette` 를 건드리면 클래스 초기화가 터진다
 * (`MapStyleTest` 가 `MapArgb` 만 보는 것과 같은 사정).
 */
class CalendarStyleTest {

    /**
     * **기본 스타일은 v1.7.5 그대로다.**
     *
     * 달력 색을 팔레트로 옮기면서 값이 한 칸이라도 어긋나면 **클레이를 고르지도 않은 사람의
     * 달력**이 바뀐다. 색 자체는 테마(`MaterialTheme`·`LocalDutyColors`)에서 오므로 여기서
     * 잠글 수 있는 것은 그 위에 얹던 **알파**다 — 아래 숫자는 v1.7.5 `MainCalendarScreen.kt`
     * 의 `DayCell` 에서 그대로 옮겨 적은 것이다.
     */
    @Test
    fun `기본 팔레트 알파는 v1_7_5 값과 같다`() {
        assertEquals(0.55f, CalendarArgb.PlainAlpha, 0f)       // surfaceVariant — 칸 바탕
        assertEquals(0.45f, CalendarArgb.FrozenAlpha, 0f)      // primaryContainer — 근무 저장
        assertEquals(0.10f, CalendarArgb.TodayTintAlpha, 0f)   // primary — 오늘 물들이기
        assertEquals(0.18f, CalendarArgb.BorderAlpha, 0f)      // outline — 칸 테두리
        assertEquals(0.07f, CalendarArgb.DateBadgeAlpha, 0f)   // onSurface — 날짜 숫자 뒤
        assertEquals(0.75f, CalendarArgb.StrikeAlpha, 0f)      // onSurfaceVariant — 취소선
    }

    /**
     * 저장·복원 — 설정에서 고른 값이 `theme` 저장소에 `.name` 으로 앉았다가 그대로 돌아온다.
     * **모르는 값·빈 값은 기본**이다: 옛 판에서 올라온 기기, 저장이 날아간 기기, enum 이름을
     * 나중에 잘못 바꾼 경우가 다 여기로 떨어진다 — 달력이 안 뜨는 것보다 낫다.
     */
    @Test
    fun `달력 스타일은 저장값에서 그대로 복원된다`() {
        CalendarStyle.entries.forEach { assertEquals(it, CalendarStyle.of(it.name)) }
        assertEquals(CalendarStyle.DEFAULT, CalendarStyle.of(null))    // 처음 켠 기기
        assertEquals(CalendarStyle.DEFAULT, CalendarStyle.of(""))      // 저장이 비었다
        assertEquals(CalendarStyle.DEFAULT, CalendarStyle.of("clay"))  // 대소문자가 다르다
        assertEquals(CalendarStyle.DEFAULT, CalendarStyle.of("PAPER")) // 없어진 스타일
    }
}
