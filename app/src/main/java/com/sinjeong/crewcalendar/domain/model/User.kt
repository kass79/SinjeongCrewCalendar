package com.sinjeong.crewcalendar.domain.model

/** 직명 */
enum class CrewRole { DRIVER_MAIN, CONDUCTOR, DRIVER_BRANCH, STANDBY_DRIVER, STANDBY_CONDUCTOR, OPERATION }

data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val role: CrewRole = CrewRole.DRIVER_MAIN,
    /** 소속 근무조 라인 (교번표의 행 = 순환 시작 오프셋) */
    val patternId: String? = null,
    val patternOffset: Int = 0,
    /** 동료 화면에 내 근무 공개 여부 */
    val visibleToOthers: Boolean = true,
    /** FCM 토큰 (교환요청 푸시용) */
    val fcmToken: String? = null,
    /** 구글 캘린더 동기화 대상 calendarId (null이면 비활성) */
    val googleCalendarId: String? = null,
    /** 연차/촉연 등 잔여일수 */
    val annualLeaveRemaining: Int = 0,
    val promotedLeaveRemaining: Int = 0,
)
