package com.sinjeong.crewcalendar.domain.model

import kotlin.math.ceil
import kotlin.math.floor

/**
 * **본선 지도 열차 이동 모델**(v1.7.5) — 순수 Kotlin(안드로이드·Compose 0줄).
 * `TrainMotionTest` 가 잠근다.
 *
 * ## 왜 갈아엎었나 (2026-09-06 사용자 실측)
 *
 * > *"열차아이콘 움직임이 뒤로는 없어야 되잖아..보니까 정차했을때 약간 뒤로 움직임이 있네..
 * > 출발을 했으면 일정한 속도로 다음역까지 앞으로 가줘야지..자연스럽게 갑자기 슬라이딩 하면
 * > 안되지"*
 *
 * v1.6.88~v1.7.4 는 API 상태(진입 −0.15 · 도착 0 · 출발 +0.15 · 전역출발 −0.6)로 **목표 자리**를
 * 정하고 `Animatable` 로 1초 만에 미끄러뜨렸다. 목표가 **화면 위치보다 뒤**로 오면 그대로 뒤로
 * 미끄러진다:
 *
 *  · **정차 중 뒷걸음.** 도착(1)이 계속 오는 동안에도 예측이 다음 역 95% 까지 기어가고,
 *    그 상태에서 도착 목표(= 역)가 다시 오면 화면이 **뒤로 물러났다**. 사용자가 본 그것이다.
 *  · **급슬라이딩.** 한 눈금을 건너뛰거나 전역출발(−0.6)이 늦게 오면 1초 tween 이 남은 거리를
 *    통째로 메워 훅 밀렸다 — 거리가 클수록 **빨라진다**(등속의 반대다).
 *
 * ## 새 모델 — 지선 카드가 v1.6.70 부터 쓰던 그 방식
 *
 * 위치는 **시간의 함수**다. 프레임마다 [stepMotion] 을 한 걸음 돌린다:
 *
 *  1. **등속 전진.** 속도는 `1구간 ÷ [segSec]` 하나뿐(시간표의 그 운행·그 구간 예정 시각 차,
 *     없으면 [DEFAULT_SEG_SEC] 초). 거리가 멀든 가깝든 화면 속도가 안 변한다.
 *  2. **다음 역 직전 [CREEP_MARGIN] 까지만.** 예측이 실측을 앞질러 있지도 않은 도착을
 *     그리지 않는다.
 *  3. **도착(1)이면 역에 정지.** 목표가 앞이면 그 역까지 붙이고, **뒤면 무시**한다.
 *  4. **API 보정은 앞쪽으로만.** 새 목표가 지금 위치보다 뒤면 버리고([backOff]), 앞이면
 *     **등속의 [CATCH_UP] 배**를 상한으로 따라잡는다 — 훅 밀리지 않는다.
 *  5. **순환 경계**(42 → 0)는 [unfold] 가 **펼친 좌표**로 옮겨 푼다. 반 바퀴를 넘는 차이는
 *     "뒤로 42.7" 이 아니라 "앞으로 0.3" 이다.
 *
 * 좌표는 **진행 방향으로 커진다** — 내선은 역 인덱스가 증가([MainTrainMark.inner]), 외선은
 * 감소다. 그래서 `dir` 한 글자로 앞뒤가 갈리고 규칙이 방향마다 두 벌이 되지 않는다.
 */

/** 시간표에 없는 구간의 역간 소요(초) — 사용자 지정값(v1.7.5). */
internal const val DEFAULT_SEG_SEC = 110f

/** 예측이 다음 역에 **못 미치게** 남겨 두는 몫 — 0.95 지점까지만 간다. */
internal const val CREEP_MARGIN = 0.05f

/** API 보정 따라잡기 속도 상한 = 등속의 몇 배인가. 이 값이 "급슬라이딩 금지" 그 자체다. */
internal const val CATCH_UP = 2f

/** 본선 순환선 역 수. [unfold] 의 한 바퀴. */
internal const val LOOP_STATIONS = 43

/**
 * 열차 한 대의 화면 위치 상태. **펼친 좌표**([pos])라 순환 경계를 넘어도 단조롭게 커진다 —
 * 그릴 때만 `43` 으로 접는다([folded]).
 *
 * @param at [pos] 를 계산한 시각(ms). 다음 걸음의 `dt` 가 여기서 나온다.
 * @param holding 마지막 API 상태가 **도착(1)** 이었나 — 참이면 역에 서 있다.
 */
internal data class TrainMotion(
    val pos: Float,
    val at: Long,
    val holding: Boolean,
) {
    /** 화면에 그릴 자리 — 한 바퀴로 접은 `0 ≤ x < 43`. */
    val folded: Float get() = ((pos % LOOP_STATIONS) + LOOP_STATIONS) % LOOP_STATIONS
}

/**
 * [folded] 를 [ref] 근처의 **펼친 좌표**로 옮긴다 — 차이가 반 바퀴를 넘으면 한 바퀴를 더하거나 뺀다.
 * `42.9 → 0.2` 는 뒤로 42.7 이 아니라 **앞으로 0.3** 이다.
 */
internal fun unfold(ref: Float, folded: Float, loopN: Int = LOOP_STATIONS): Float {
    var d = folded - ref
    while (d > loopN / 2f) d -= loopN
    while (d < -loopN / 2f) d += loopN
    return ref + d
}

/** [v] 가 [limit] 를 [dir] 방향으로 넘지 않게 자른다(뒤로는 안 건드린다). */
private fun capForward(v: Float, limit: Float, dir: Float) =
    if (dir * (v - limit) > 0f) limit else v

/**
 * 한 걸음 — **이전 상태 + API 가 준 목표 + 흐른 시간** 에서 다음 화면 위치를 정한다.
 *
 * @param prev 직전 상태. `null` 이면 처음 본 열차라 **목표 자리에서 시작**한다(튀지 않는다).
 * @param target API 가 준 자리(`역 인덱스 + 상태 오프셋`, 접힌 좌표 0..42.x).
 * @param holding API 상태가 **도착(1)** 인가.
 * @param inner 내선(인덱스 증가)인가.
 * @param segSec 지금 달리는 구간의 역간 소요(초). 모르면 [DEFAULT_SEG_SEC].
 * @param nowMs 지금 시각(ms).
 */
internal fun stepMotion(
    prev: TrainMotion?,
    target: Float,
    holding: Boolean,
    inner: Boolean,
    segSec: Float,
    nowMs: Long,
): TrainMotion {
    if (prev == null) return TrainMotion(target, nowMs, holding)
    val dir = if (inner) 1f else -1f
    val goal = unfold(prev.pos, target)
    val dt = ((nowMs - prev.at).coerceAtLeast(0L)) / 1000f
    /** 흐른 시간이 벌어 준 거리(구간 비율). 속도는 이 한 줄이 전부다. */
    val step = dt / segSec.coerceAtLeast(1f)

    // ── ③ 도착 — 역에 선다 ───────────────────────────────────
    // 예측을 아예 안 돌린다. 목표(= 역)가 **앞**이면 따라잡기 속도로 붙이고, 뒤면 그 자리다.
    if (holding) {
        val p =
            if (dir * (goal - prev.pos) > 0f)
                capForward(prev.pos + dir * step * CATCH_UP, goal, dir)
            else prev.pos
        return TrainMotion(p, nowMs, true)
    }

    // ── ①② 등속 전진 — 다음 역 0.95 까지만 ──────────────────
    val nextStop = if (inner) floor(prev.pos) + 1f else ceil(prev.pos) - 1f
    val creep = capForward(prev.pos + dir * step, nextStop - dir * CREEP_MARGIN, dir)

    // ── ④ API 보정은 앞쪽으로만, 등속의 2배가 상한 ───────────
    // ⚠ 0.95 상한은 **예측에만** 건다. 목표가 그 너머면 그건 실측이라 그대로 따라간다.
    val p =
        if (dir * (goal - creep) <= 0f) creep                        // 목표가 뒤 → 버린다
        else capForward(prev.pos + dir * step * CATCH_UP, goal, dir) // 앞 → 2배 속도로 따라잡기
    return TrainMotion(p, nowMs, false)
}

/**
 * 목록에서 사라진 열차의 **기억을 얼마나 들고 있나**(ms) — `BranchLive.retainLastGood` 의
 * 2분(`STALE_KEEP_MS`)과 같은 값이다. 이 안에 다시 나타나면 **이어서** 달리고, 넘으면
 * 새 열차로 친다(처음 본 열차라 목표 자리에서 시작 — 훅 미끄러지지 않는다).
 *
 * ⚠ 기억을 들고 있을 뿐 **없는 열차를 그리지는 않는다** — API 가 안 주는 열차를 계속 그리면
 * 그건 지어낸 자리다(`MyTrain` KDoc 의 "추정하지 않는다" 와 같은 규칙).
 */
internal const val MOTION_KEEP_MS = 120_000L

/** 2분 넘게 안 보인 열차의 기억을 지운다 — 장부가 무한히 자라지 않게. */
internal fun <K> pruneMotions(ledger: MutableMap<K, TrainMotion>, alive: Set<K>, nowMs: Long) {
    ledger.entries.removeAll { (k, m) -> k !in alive && nowMs - m.at > MOTION_KEEP_MS }
}
