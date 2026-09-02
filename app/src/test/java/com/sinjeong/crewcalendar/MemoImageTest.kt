package com.sinjeong.crewcalendar

import com.sinjeong.crewcalendar.presentation.calendar.memoFirstLine
import com.sinjeong.crewcalendar.presentation.calendar.routeSampleSize
import org.junit.Assert.assertEquals
import org.junit.Test

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
}
