package com.sinjeong.crewcalendar

import com.sinjeong.crewcalendar.domain.model.DaySchedule
import com.sinjeong.crewcalendar.domain.model.DutyCode
import com.sinjeong.crewcalendar.presentation.calendar.memoFirstLine
import com.sinjeong.crewcalendar.presentation.calendar.memoMatches
import com.sinjeong.crewcalendar.presentation.calendar.recentMemoPhrases
import com.sinjeong.crewcalendar.presentation.calendar.routeSampleSize
import com.sinjeong.crewcalendar.presentation.calendar.week52Tail
import com.sinjeong.crewcalendar.domain.usecase.WeeklyHours
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * v1.6.82 순수 로직 두 가지를 잠근다.
 *
 * ⚠ `./gradlew test`는 저장소 경로(`07_프로젝트`)의 한글 때문에 죽는다 — `PatternTest` 주석 참고.
 * 실행: `./gradlew :app:compileDebugUnitTestKotlin` 후 JUnitCore 직접 실행.
 */
class MemoImageTest {

    /* ── 행로표 이미지 다운샘플 (플레이 콘솔 메모리 권장 조치) ────────────── */

    /** 원본이 목표보다 작거나 같으면 절대 줄이지 않는다 — 줄이면 그 자리에서 뭉갠다. */
    @Test fun sampleSize_neverShrinksBelowRequest() {
        assertEquals(1, routeSampleSize(srcW = 1000, reqW = 1080))
        assertEquals(1, routeSampleSize(srcW = 1080, reqW = 1080))
        // 딱 2배여야 2가 된다(2배 줄여도 목표를 채우므로)
        assertEquals(2, routeSampleSize(srcW = 2160, reqW = 1080))
        // 2배에서 1px 모자라면 여전히 1 — 반내림이 아니라 "목표 이상 보장"이다
        assertEquals(1, routeSampleSize(srcW = 2159, reqW = 1080))
    }

    /** 2의 거듭제곱만 나온다(BitmapFactory가 그 외 값은 내림해 버린다). */
    @Test fun sampleSize_isPowerOfTwo() {
        assertEquals(4, routeSampleSize(srcW = 5000, reqW = 1080))
        assertEquals(8, routeSampleSize(srcW = 9000, reqW = 1080))
        for (src in intArrayOf(1, 100, 999, 2258, 12345, 40000)) {
            val s = routeSampleSize(src, 1080)
            assertEquals("2의 거듭제곱이어야 한다: $s", 0, s and (s - 1))
        }
    }

    /**
     * **현재 자산은 전부 1이다** — 확대 선명도가 한 픽셀도 안 줄어든다는 회귀 잠금.
     * 목표 폭 = 화면 폭 × 확대 상한 6배. 접힘 1080px → 6480, 폴드 펼침 1968px → 11808.
     * 가장 큰 자산이 `tt_work` 2258px라 6480의 절반(3240)에도 못 미친다.
     */
    @Test fun sampleSize_isOneForEveryShippedAsset() {
        val widest = 2258            // tt_work.webp (2258x2928) — 자산 162장 중 최대
        assertEquals(1, routeSampleSize(widest, 1080 * 6))   // 접힘
        assertEquals(1, routeSampleSize(widest, 1968 * 6))   // 폴드 펼침
        assertEquals(1, routeSampleSize(2000, 1080 * 6))     // 본선 행로표
        // 인라인(핀치 없음)도 마찬가지 — 접힘 표시 폭 1895px, 펼침 패널 984px×1.4
        assertEquals(1, routeSampleSize(2000, 1895))
        assertEquals(1, routeSampleSize(2000, 1378))
    }

    @Test fun sampleSize_guardsAgainstBadInput() {
        assertEquals(1, routeSampleSize(srcW = 0, reqW = 1080))
        assertEquals(1, routeSampleSize(srcW = -1, reqW = 1080))
        assertEquals(1, routeSampleSize(srcW = 2000, reqW = 0))
    }

    /* ── 메모 모아보기 첫 줄 ──────────────────────────────────────────── */

    @Test fun memoFirstLine_takesFirstNonBlankLine() {
        assertEquals("연차", memoFirstLine("연차"))
        assertEquals("연차", memoFirstLine("연차\n오전 반차 아님"))
        // 앞에 빈 줄을 넣고 쓰는 사람이 있다 — 건너뛴다
        assertEquals("병원", memoFirstLine("\n\n  병원\n9시"))
        assertEquals("병원", memoFirstLine("   \n병원"))
    }

    @Test fun memoFirstLine_trimsAndHandlesEmpty() {
        assertEquals("가족 모임", memoFirstLine("  가족 모임  \n장소 미정"))
        assertEquals("", memoFirstLine(""))
        assertEquals("", memoFirstLine("\n \n\t"))
    }

    /** 윈도우 줄바꿈(\r\n)이 섞여도 \r 이 남지 않는다 — 붙여넣기로 들어올 수 있다. */
    @Test fun memoFirstLine_handlesCrlf() {
        assertEquals("교육", memoFirstLine("교육\r\n9시 본사"))
    }

    /* ── v1.6.99 메모 업그레이드 ─────────────────────────────────────── */

    private fun memo(m: Int, day: Int, memo: String) =
        DaySchedule(LocalDate.of(2026, m, day), DutyCode.parse("1"), memo = memo)

    /** 빠른 입력 칩: **최신순 · 중복 제거 · 상한**. 빈 첫 줄짜리 메모는 칩이 안 된다. */
    @Test fun recentMemoPhrases_newestFirstDistinctCapped() {
        val days = listOf(
            memo(8, 30, "연차"),
            memo(9, 2, "병원\n9시"),
            memo(9, 5, "연차"),          // 같은 말이 또 — 최신 것 하나만 남는다
            memo(9, 9, "   "),           // 공백뿐 = 첫 줄이 비어 칩이 안 된다
            memo(10, 1, "가족 모임"),     // 다음 달까지 모은다
        )
        assertEquals(listOf("가족 모임", "연차", "병원"), recentMemoPhrases(days))
        // 상한이 먹는다 — 최신 두 개만
        assertEquals(listOf("가족 모임", "연차"), recentMemoPhrases(days, limit = 2))
        assertEquals(emptyList<String>(), recentMemoPhrases(emptyList()))
    }

    /** 모아보기 검색: 빈 검색어는 전부 통과, 그 밖에는 **메모 전체**에서 부분 일치. */
    @Test fun memoMatches_filtersBySubstring() {
        assertTrue(memoMatches("연차\n오전만", ""))
        assertTrue(memoMatches("연차\n오전만", "   "))
        assertTrue(memoMatches("연차\n오전만", "연차"))
        // 첫 줄이 아니라 둘째 줄에 있어도 찾는다
        assertTrue(memoMatches("연차\n오전만", "오전"))
        assertTrue(memoMatches("Health checkup", "health"))   // 대소문자 무시
        assertTrue(memoMatches("연차", " 연차 "))              // 검색어 양끝 공백은 무시
        assertFalse(memoMatches("연차\n오전만", "병원"))
        assertFalse(memoMatches("", "연차"))
    }

    /* ── 주52 줄 꼬리표 (v1.7.5) ────────────────────────────── */

    private fun week(over: Boolean, partial: Boolean, excluded: List<String>) =
        WeeklyHours.Week(
            index = 1, from = LocalDate.parse("2026-09-07"), to = LocalDate.parse("2026-09-13"),
            minutes = if (over) 53 * 60 else 40 * 60, excluded = excluded, partial = partial,
        )

    /**
     * 사용자: *"주 52시간 확인 밑에 1주 ← 텍스트를 더 작게 해서 여기칸을 한줄로 해줘..
     * 2줄까지 잡아먹지마"* — 글자를 8sp 까지 줄여도 안 들어가면 이 순서로 꼬리를 덜어 낸다.
     */
    @Test fun week52Tail_shedsFromTheTail() {
        val w = week(over = true, partial = false, excluded = listOf("교육", "회행"))
        assertEquals(" 초과 (교육·회행 미포함)", week52Tail(w, 0))
        assertEquals(" 초과", week52Tail(w, 1))            // `(… 미포함)` 부터 뗀다
        assertEquals(" 초과", week52Tail(w, 2))
        assertEquals("", week52Tail(w, 3))                 // `초과` 는 색(빨강·굵게)으로만
    }

    /** 경계 주(`일부만 집계`)는 `※` 한 글자까지 줄어들되 **사라지지는 않는다.** */
    @Test fun week52Tail_partialShrinksToMark() {
        val w = week(over = false, partial = true, excluded = listOf("지근"))
        assertEquals(" · 일부만 집계 (지근 미포함)", week52Tail(w, 0))
        assertEquals(" · 일부만 집계", week52Tail(w, 1))
        assertEquals("※", week52Tail(w, 2))
        assertEquals("※", week52Tail(w, 3))
    }

    /** 아무 일도 없는 주는 어느 단계에서나 꼬리가 없다. */
    @Test fun week52Tail_plainWeekHasNoTail() {
        val w = week(over = false, partial = false, excluded = emptyList())
        (0..4).forEach { assertEquals("", week52Tail(w, it)) }
    }

    /**
     * **마지막 단(4)은 라벨의 날짜 범위까지 뗀다** — 배율 1.5 에서 5주 달이 8sp 로도 안 들어가
     * `…` 로 잘렸다(v1.7.5 실측). 꼬리표는 3 단과 같다.
     */
    @Test fun week52_lastRungDropsDateSpan() {
        val boundary = WeeklyHours.Week(
            index = 1, from = LocalDate.parse("2026-09-01"), to = LocalDate.parse("2026-09-06"),
            minutes = 1854, excluded = emptyList(), partial = false,
        )
        assertEquals("1주(1~6일) 30.9h", WeeklyHours.label(boundary))
        assertEquals("1주 30.9h", WeeklyHours.label(boundary, span = false))
        // 꽉 찬 주는 애초에 범위가 없어 두 값이 같다.
        val full = week(over = false, partial = false, excluded = emptyList())
        assertEquals(WeeklyHours.label(full), WeeklyHours.label(full, span = false))
        val w = week(over = true, partial = true, excluded = listOf("교육"))
        assertEquals(week52Tail(w, 3), week52Tail(w, 4))
    }
}
