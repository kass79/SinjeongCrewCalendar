package com.sinjeong.crewcalendar.domain.model

/** 직명 */
enum class CrewRole {
    DRIVER_MAIN, CONDUCTOR, DRIVER_BRANCH, STANDBY_DRIVER, STANDBY_CONDUCTOR,
    /** 운용조·기지관제 (4조2교대) */ OPERATION,
    /** 사무실·소장/부사업소장·지도과·관리과 (통상근무) */ OFFICE_STAFF,
}

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
    /** 구글 캘린더 동기화 대상 calendarId (null이면 비활성) */
    val googleCalendarId: String? = null,
    /** 연차/촉연 등 잔여일수 */
    val annualLeaveRemaining: Int = 0,
    val promotedLeaveRemaining: Int = 0,
)
