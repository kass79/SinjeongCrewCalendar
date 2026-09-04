package com.sinjeong.crewcalendar

import com.sinjeong.crewcalendar.presentation.live.Heading
import com.sinjeong.crewcalendar.presentation.live.headingFor
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 증기기관차 아이콘의 **머리 방향**을 잠근다 — 사용자가 이 그림을 넣은 이유가
 * *"방향이 헷갈려!"* 하나이므로, 머리가 반대로 돌면 기능이 통째로 거짓말이 된다.
 *
 * 화면 좌표라 y 는 **아래가 양수**다. 본선 순환선의 접선은 `Loop.at()` 이 주고 늘
 * **내선(인덱스가 커지는 쪽 = 시계)** 을 가리킨다. 실행법은 [PatternTest] KDoc 참고.
 */
class LocoTest {

    /** 윗변은 왼→오. 내선이면 머리도 오른쪽. */
    @Test
    fun `본선 윗변 내선은 오른쪽`() {
        assertEquals(Heading.RIGHT, headingFor(1f, 0f, true))
    }

    /** 같은 윗변이라도 외선은 반대로 달린다. */
    @Test
    fun `본선 윗변 외선은 왼쪽`() {
        assertEquals(Heading.LEFT, headingFor(1f, 0f, false))
    }

    /** 아랫변은 오→왼. 내선인데도 왼쪽인 것이 이 그림의 값어치다(배지로는 못 읽는 정보). */
    @Test
    fun `본선 아랫변 내선은 왼쪽`() {
        assertEquals(Heading.LEFT, headingFor(-1f, 0f, true))
    }

    /** 오른쪽 변은 위→아래. 화면 y 가 아래로 커지므로 아래쪽. */
    @Test
    fun `본선 오른쪽 변 내선은 아래쪽`() {
        assertEquals(Heading.DOWN, headingFor(0f, 1f, true))
    }

    /** 왼쪽 변은 아래→위. */
    @Test
    fun `본선 왼쪽 변 내선은 위쪽`() {
        assertEquals(Heading.UP, headingFor(0f, -1f, true))
    }

    /**
     * 모서리 호는 **가까운 변 기준** — 긴 쪽 성분이 이긴다. 오른위 모서리를 막 지난
     * 30도 지점(접선 ≈ (0.87, 0.5))은 아직 윗변 쪽이라 오른쪽,
     * 60도(≈ (0.5, 0.87))는 이미 오른쪽 변 쪽이라 아래쪽이다.
     */
    @Test
    fun `모서리 호는 가까운 변을 따른다`() {
        assertEquals(Heading.RIGHT, headingFor(0.866f, 0.5f, true))
        assertEquals(Heading.DOWN, headingFor(0.5f, 0.866f, true))
    }

    /** 지선 카드는 신도림이 오른쪽 끝 — 신도림행은 늘 오른쪽. */
    @Test
    fun `지선 신도림행은 오른쪽`() {
        assertEquals(Heading.RIGHT, headingFor(1f, 0f, true))
    }
}
