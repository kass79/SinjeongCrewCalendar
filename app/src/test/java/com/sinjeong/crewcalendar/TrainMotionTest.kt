package com.sinjeong.crewcalendar

import com.sinjeong.crewcalendar.domain.model.CATCH_UP
import com.sinjeong.crewcalendar.domain.model.MOTION_KEEP_MS
import com.sinjeong.crewcalendar.domain.model.TrainMotion
import com.sinjeong.crewcalendar.domain.model.pruneMotions
import com.sinjeong.crewcalendar.domain.model.stepMotion
import com.sinjeong.crewcalendar.domain.model.unfold
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 본선 지도 **열차 이동 모델**(v1.7.5) — 사용자 원문:
 * *"열차아이콘 움직임이 뒤로는 없어야 되잖아..보니까 정차했을때 약간 뒤로 움직임이 있네..
 * 출발을 했으면 일정한 속도로 다음역까지 앞으로 가줘야지..자연스럽게 갑자기 슬라이딩 하면
 * 안되지"*
 */
class TrainMotionTest {

    private val seg = 100f   // 계산이 눈으로 보이는 값 — 1초 = 0.01 구간

    private fun at(pos: Float, t: Long = 0L, holding: Boolean = false) =
        TrainMotion(pos, t, holding)

    /* ── ① 등속 전진 ─────────────────────────────────────── */

    /** 10초면 `10 / 100 = 0.1` 구간. 내선은 인덱스가 커지고 외선은 작아진다. */
    @Test fun 등속으로_전진한다() {
        val inner = stepMotion(at(5.15f), 5.15f, false, true, seg, 10_000L)
        assertEquals(5.25f, inner.pos, 1e-4f)
        val outer = stepMotion(at(5f - 0.15f), 4.85f, false, false, seg, 10_000L)
        assertEquals(4.75f, outer.pos, 1e-4f)
    }

    /** 속도는 **거리와 무관**하다 — 두 걸음으로 나눠 걸어도 합이 같다(급가속 없음). */
    @Test fun 두_걸음으로_나눠도_같은_거리() {
        val a = stepMotion(at(5.15f), 5.15f, false, true, seg, 10_000L)
        val b = stepMotion(a, 5.15f, false, true, seg, 20_000L)
        assertEquals(5.35f, b.pos, 1e-4f)
    }

    /* ── ② 다음 역 0.95 상한 ─────────────────────────────── */

    /** 예측은 다음 역 직전에서 **멈춘다** — 있지도 않은 도착을 그리지 않는다. */
    @Test fun 예측은_다음역_0_95_까지만() {
        // 5.15 에서 60초(0.6 구간) → 5.75 여야 하지만 6 을 향하므로 5.95 상한에 안 걸린다.
        assertEquals(5.75f, stepMotion(at(5.15f), 5.15f, false, true, seg, 60_000L).pos, 1e-4f)
        // 200초(2 구간)면 6 을 넘어야 하지만 **5.95 에서 선다.**
        assertEquals(5.95f, stepMotion(at(5.15f), 5.15f, false, true, seg, 200_000L).pos, 1e-4f)
        // 외선은 반대쪽 0.05 — 4.85 에서 출발해 4.05 에서 선다.
        assertEquals(4.05f, stepMotion(at(4.85f), 4.85f, false, false, seg, 200_000L).pos, 1e-4f)
    }

    /* ── ③ 도착(1)이면 역에 정지 ─────────────────────────── */

    /** 사용자가 본 그 버그 — **정차 중에는 한 발도 안 움직인다.** */
    @Test fun 도착이면_역에_선다() {
        val m = stepMotion(at(6f, holding = true), 6f, true, true, seg, 60_000L)
        assertEquals(6f, m.pos, 1e-4f)
        assertTrue(m.holding)
        // 도착이 계속 와도 마찬가지(폴링 눈금마다 같은 값이 온다)
        assertEquals(6f, stepMotion(m, 6f, true, true, seg, 120_000L).pos, 1e-4f)
    }

    /** 0.95 까지 기어간 예측에 도착이 오면 **앞으로** 역에 붙는다(뒤로 물러나지 않는다). */
    @Test fun 도착은_앞쪽으로만_붙인다() {
        val creeping = at(5.95f, 0L)
        val m = stepMotion(creeping, 6f, true, true, seg, 3_000L)
        assertTrue("도착이 앞이면 붙는다", m.pos > 5.95f)
        assertTrue("역을 넘지 않는다", m.pos <= 6f)
        // 이미 6 에 서 있는데 API 가 **지나온 역 5** 의 도착을 다시 주면 → 무시
        assertEquals(6f, stepMotion(at(6f, holding = true), 5f, true, true, seg, 5_000L).pos, 1e-4f)
    }

    /* ── ④ 뒤로 가는 목표는 버린다 ───────────────────────── */

    /** 내선 5.5 에 있는데 목표가 5.2 로 오면 **등속 전진분만** 살고 목표는 버린다. */
    @Test fun 뒤로_가는_목표는_무시한다() {
        val m = stepMotion(at(5.5f), 5.2f, false, true, seg, 10_000L)
        assertEquals(5.6f, m.pos, 1e-4f)     // 5.5 + 0.1, 목표 5.2 는 안 봤다
        assertFalse(m.holding)
        // 외선도 대칭 — 4.5 에서 목표 4.8(뒤)이면 4.4 로 계속 간다.
        assertEquals(4.4f, stepMotion(at(4.5f), 4.8f, false, false, seg, 10_000L).pos, 1e-4f)
    }

    /** **어느 시각을 넣어도 뒤로 못 간다** — 목표를 계속 뒤로 흔들어도 단조 증가다. */
    @Test fun 목표를_흔들어도_단조_전진() {
        var m = at(5f)
        val jitter = listOf(5.2f, 4.9f, 5.4f, 5.0f, 5.6f, 5.1f)
        jitter.forEachIndexed { i, target ->
            val next = stepMotion(m, target, false, true, seg, (i + 1) * 5_000L)
            assertTrue("$target 에서 뒤로 갔다", next.pos >= m.pos)
            m = next
        }
    }

    /* ── ⑤ 따라잡기 속도 상한 ────────────────────────────── */

    /** 목표가 멀어도 **등속의 2배**를 못 넘는다 — 훅 밀리지 않는다. */
    @Test fun 따라잡기는_등속의_두_배가_상한() {
        // 10초(등속 0.1) → 상한 0.2. 목표가 3 구간 앞이어도 5.2 까지만 간다.
        val m = stepMotion(at(5f), 8f, false, true, seg, 10_000L)
        assertEquals(5f + CATCH_UP * 0.1f, m.pos, 1e-4f)
        // 목표가 상한 안이면 목표에 정확히 선다(넘어가지 않는다).
        assertEquals(5.15f, stepMotion(at(5f), 5.15f, false, true, seg, 10_000L).pos, 1e-4f)
    }

    /* ── ⑥ 순환 경계 42 → 0 ─────────────────────────────── */

    /** `42.9 → 0.2` 는 뒤로 42.7 이 아니라 **앞으로 0.3** 이다. */
    @Test fun 순환_경계를_앞으로_읽는다() {
        assertEquals(43.2f, unfold(42.9f, 0.2f), 1e-4f)
        assertEquals(-0.1f, unfold(0.2f, 42.9f), 1e-4f)
        assertEquals(5.1f, unfold(5f, 5.1f), 1e-4f)
    }

    /** 경계를 넘어도 등속이고, 그린 자리는 다시 `0..43` 으로 접힌다. */
    @Test fun 경계를_넘어도_등속이고_접혀_나온다() {
        // 내선 42.9 · 목표 0.2(= 펼치면 43.2) → 따라잡기 상한 0.2 만큼만 = 43.1
        val m = stepMotion(at(42.9f), 0.2f, false, true, seg, 10_000L)
        assertEquals(43.1f, m.pos, 1e-3f)
        assertEquals(0.1f, m.folded, 1e-3f)          // 그릴 땐 접는다
        // 외선(감소)이 0 을 지나 42 로 — 0.1 에서 목표 42.8 은 **앞으로 0.3** 이다.
        val o = stepMotion(at(0.1f), 42.8f, false, false, seg, 10_000L)
        assertEquals(-0.1f, o.pos, 1e-3f)
        assertEquals(42.9f, o.folded, 1e-3f)
        assertEquals(42.8f, stepMotion(o, 42.8f, false, false, seg, 20_000L).folded, 1e-3f)
    }

    /* ── 기억 유지 2분 ───────────────────────────────────── */

    @Test fun 사라진_열차_기억은_2분() {
        val led = hashMapOf("2401" to at(5f, 0L), "2403" to at(9f, 0L))
        pruneMotions(led, setOf("2401"), MOTION_KEEP_MS - 1)
        assertEquals(2, led.size)                     // 아직 둘 다 산다
        pruneMotions(led, setOf("2401"), MOTION_KEEP_MS + 1)
        assertEquals(setOf("2401"), led.keys)         // 살아 있는 것만 남는다
    }

    /** 처음 본 열차는 **목표 자리에서 시작**한다(0 에서 미끄러져 오지 않는다). */
    @Test fun 처음_본_열차는_목표에서_시작() {
        val m = stepMotion(null, 17.85f, false, true, seg, 12_345L)
        assertEquals(17.85f, m.pos, 1e-4f)
        assertEquals(12_345L, m.at)
    }
}
