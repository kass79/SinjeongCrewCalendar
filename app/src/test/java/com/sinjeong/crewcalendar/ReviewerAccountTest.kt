package com.sinjeong.crewcalendar

import com.sinjeong.crewcalendar.domain.model.Bundled
import com.sinjeong.crewcalendar.domain.model.BundledStaff
import com.sinjeong.crewcalendar.domain.model.CrewRole
import com.sinjeong.crewcalendar.domain.model.ReviewerAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 플레이 심사용 계정(v1.6.82 ⑥)의 근거를 고정한다.
 *
 * ⚠ 여기 값이 바뀌면 **플레이 콘솔 `앱 액세스 권한` 도 같이 바꿔야 한다.** 안 바꾸면 다음 심사가
 * v1.6.81 과 같은 이유(*"제공된 로그인 정보로 앱에 액세스할 수 없음"*)로 또 거부된다.
 */
class ReviewerAccountTest {

    @Test fun reviewer_credentials_pass() {
        assertTrue(ReviewerAccount.matches("구글심사", "00000000"))
    }

    /**
     * 폰 키보드가 붙이는 **앞뒤 공백은 봐준다.** 이 계정이 있는 이유가 "심사관이 로그인에
     * 실패하지 않는 것" 이라, 끝 공백 하나로 또 거부당하면 만든 의미가 없다.
     */
    @Test fun surrounding_whitespace_is_forgiven() {
        assertTrue(ReviewerAccount.matches("구글심사 ", "00000000"))
        assertTrue(ReviewerAccount.matches(" 구글심사", " 00000000 "))
    }

    /** 그 밖의 차이는 전부 거부하고 평소대로 명단 대조로 내려간다 */
    @Test fun near_misses_are_rejected() {
        assertFalse(ReviewerAccount.matches("구글 심사", "00000000"))   // 가운데 공백
        assertFalse(ReviewerAccount.matches("구글심사원", "00000000"))
        assertFalse(ReviewerAccount.matches("심사", "00000000"))
        assertFalse(ReviewerAccount.matches("Google심사", "00000000"))
        assertFalse(ReviewerAccount.matches("구글심사", "0000000"))      // 7자리
        assertFalse(ReviewerAccount.matches("구글심사", "000000000"))    // 9자리
        assertFalse(ReviewerAccount.matches("구글심사", "00000001"))
        assertFalse(ReviewerAccount.matches("", ""))
    }

    /** 심사 계정은 **명단에 없다** — 명단은 동료 탭·동명이인 배정·통계의 근거라 손대지 않았다 */
    @Test fun reviewer_is_not_in_the_staff_roster() {
        assertNull(BundledStaff.validate(ReviewerAccount.NAME, ReviewerAccount.EMP_NO))
        // 이름만·사번만 맞아도 명단에는 없다
        assertNull(BundledStaff.validate(ReviewerAccount.NAME, "21715160"))
        assertNull(BundledStaff.validate("강성진", ReviewerAccount.EMP_NO))
    }

    /**
     * 【핵심】 **동료에게 안 보인다.** `visibleToOthers = false` 면
     * `FirestoreUserRepository.publish` 가 `users` 컬렉션 문서를 쓰지 않고 지운다 —
     * 동료 탭 목록·매트릭스·통계·공유 이미지·대리등록은 전부 그 컬렉션 하나만 읽으므로
     * 서버에 문서가 없으면 어디에도 나타날 수 없다.
     */
    @Test fun reviewer_is_never_published_to_other_crew() {
        assertFalse(ReviewerAccount.user().visibleToOthers)
    }

    /** 심사관이 기능을 볼 수 있어야 한다 — 신정지선 기관사라 근무·행로표·알람·침실 칩이 다 나온다 */
    @Test fun reviewer_sees_a_real_looking_calendar() {
        val u = ReviewerAccount.user()
        assertEquals("00000000", u.uid)
        assertEquals("구글심사", u.name)
        assertEquals(CrewRole.DRIVER_BRANCH, u.role)
        assertEquals(Bundled.BRANCH_PATTERN.id, u.patternId)
        assertEquals(0, u.patternOffset)
        // 근무선택·근무변경·메모가 막히지 않았는지 — 저장 바닥이 없어야 다 만질 수 있다
        assertNull(u.frozenUntil)
    }
}
