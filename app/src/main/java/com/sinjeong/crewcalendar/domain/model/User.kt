package com.sinjeong.crewcalendar.domain.model

import java.time.LocalDate

/** 직명 */
enum class CrewRole {
    DRIVER_MAIN, CONDUCTOR, DRIVER_BRANCH, STANDBY_DRIVER, STANDBY_CONDUCTOR,
    /** 운용조·기지관제 (4조2교대) */ OPERATION,
    /** 사무실·소장/부사업소장·지도과·관리과 (통상근무) */ OFFICE_STAFF,
}

/**
 * "언제부터 어떤 교번" 한 구간 (v1.6.63).
 *
 * 기관사는 **신정지선 2개월 → 본선 교번 4~6개월** 주기로 소속·교번이 바뀌고, 바뀌는 시점은
 * 언제나 달 경계다(이번 달 말일까지 옛 근무, 다음 달 1일부터 새 근무). 새 교번표를 미리 받아
 * 다음 달 근무를 미리 넣어 보되 이번 달이 망가지면 안 되는 것이 이 구간의 존재 이유다.
 *
 * [from]이 [LocalDate.MIN]이면 "처음부터" — 목록의 첫 구간은 언제나 이 값이다.
 */
data class PatternSegment(
    /** 이 날짜부터 적용 (첫 구간은 [LocalDate.MIN]) */
    val from: LocalDate,
    val patternId: String?,
    val patternOffset: Int,
    val role: CrewRole,
)

data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val role: CrewRole = CrewRole.DRIVER_MAIN,
    /**
     * 소속 근무조 라인 (교번표의 행 = 순환 시작 오프셋).
     *
     * ⚠ [patternSegments]가 있을 때 이 두 필드는 **오늘 시점에 유효한 구간의 사본**이다.
     * 옛 형식(단일 교번)이자 **옛 버전 앱이 읽는 유일한 자리**라 새 형식으로 저장할 때도
     * 반드시 함께 갱신한다([withSegments]) — 사용자들이 동시에 업데이트하지 않는다.
     */
    val patternId: String? = null,
    val patternOffset: Int = 0,
    /**
     * 교번 변경 구간 목록. **비어 있으면 옛 형식** = "처음부터 [patternId] 하나".
     * 이미 배포돼 실사용 중인 사용자의 저장분이 전부 이 모양이고, 옛 형식이 읽히면
     * 달력이 한 칸도 달라지지 않는다([segmentOn]의 폴백).
     *
     * 무한정 쌓이지 않는 이유: `다음 달 1일부터`를 다시 지정하면 **아직 시작 안 한 구간은
     * 대체**되고 지난 구간만 남는다([withSegments]). 실무상 교번 변경은 1년에 2~3회이고,
     * 그마저 [MAX_SEGMENTS]에서 오래된 것부터 잘린다 — 잘려 나갈 만큼 오래된 달은
     * `SnapshotRepository`가 이미 그 달 근무를 동결 보존하고 있다.
     * 지난 구간을 지우지 않는 것이 기본이다 — 지우면 지난달 달력이 틀려진다.
     */
    val patternSegments: List<PatternSegment> = emptyList(),
    /** 동료 화면에 내 근무 공개 여부 */
    val visibleToOthers: Boolean = true,
    /** 구글 캘린더 동기화 대상 calendarId (null이면 비활성) */
    val googleCalendarId: String? = null,
    /** 연차/촉연 등 잔여일수 */
    val annualLeaveRemaining: Int = 0,
    val promotedLeaveRemaining: Int = 0,
) {
    companion object {
        /** 보존 상한 — 4~5년치. 넘치면 오래된 것부터 버린다(그 달들은 스냅샷이 이미 동결했다) */
        const val MAX_SEGMENTS = 12
    }
}

/**
 * **이 기능의 전부** — 그 날짜에 유효한 교번 하나를 고른다.
 * "그 날짜 이하의 시작일 중 가장 늦은 구간". 목록이 비면 옛 형식(단일 교번)으로 해석한다.
 */
fun User.segmentOn(date: LocalDate): PatternSegment =
    patternSegments.filter { it.from <= date }.maxByOrNull { it.from }
        ?: patternSegments.minByOrNull { it.from }
        ?: PatternSegment(LocalDate.MIN, patternId, patternOffset, role)

/** 아직 시작 안 한(예약된) 구간. 없으면 null — 설정 화면의 안내·취소가 이걸 본다 */
fun User.pendingSegment(today: LocalDate = LocalDate.now()): PatternSegment? =
    patternSegments.filter { it.from > today }.minByOrNull { it.from }

/**
 * 구간 목록을 갈아 끼우고 **옛 형식 필드를 오늘 구간으로 다시 맞춘다**.
 * 목록이 구간 하나뿐이면 옛 형식으로 되돌린다 — 저장 모양이 v1.6.62와 완전히 같아져
 * 옛 버전 앱은 물론 이 앱의 하위호환 경로도 그대로 탄다.
 */
fun User.withSegments(segments: List<PatternSegment>, today: LocalDate = LocalDate.now()): User {
    val sorted = segments.sortedBy { it.from }.takeLast(User.MAX_SEGMENTS)
    if (sorted.isEmpty()) return copy(patternSegments = emptyList())
    val now = sorted.filter { it.from <= today }.maxByOrNull { it.from } ?: sorted.first()
    return copy(
        patternId = now.patternId,
        patternOffset = now.patternOffset,
        role = now.role,
        patternSegments = if (sorted.size <= 1) emptyList() else sorted,
    )
}

/** `다음 달 1일부터` — 지난·현재 구간은 그대로 두고, 예약돼 있던 구간만 이걸로 대체 */
fun User.scheduleSegment(from: LocalDate, patternId: String?, offset: Int, role: CrewRole, today: LocalDate = LocalDate.now()): User {
    val kept = (patternSegments.ifEmpty { listOf(segmentOn(today)) }).filter { it.from <= today }
    return withSegments(kept + PatternSegment(from, patternId, offset, role), today)
}

/** 예약 취소 — 아직 시작 안 한 구간만 버린다(지난 달력은 손대지 않는다) */
fun User.cancelPendingSegments(today: LocalDate = LocalDate.now()): User {
    if (patternSegments.none { it.from > today }) return this
    return withSegments(patternSegments.filter { it.from <= today }, today)
}
