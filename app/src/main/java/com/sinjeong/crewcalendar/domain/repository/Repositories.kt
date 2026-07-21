package com.sinjeong.crewcalendar.domain.repository

import com.sinjeong.crewcalendar.domain.model.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth

interface UserRepository {
    val currentUid: String?
    fun observeMe(): Flow<User?>
    suspend fun upsert(user: User)
    suspend fun searchByName(query: String): List<User>
    suspend fun updateFcmToken(token: String)
    /** 근무선택: 패턴 + 순환 오프셋 저장 → 전체 달력 자동 재계산 */
    suspend fun updatePatternPosition(patternId: String, offset: Int)

    /** 이 기기에 PIN이 저장돼 있는가 (재로그인 시 PIN 모드) */
    fun hasPin(): Boolean
    /** 저장된 로그인 이름 (PIN 모드 표시용) */
    fun savedName(): String?
    /** 첫 로그인 완료: 신원 저장 + PIN 저장 + 잠금해제 */
    suspend fun registerWithPin(user: User, pin: String)
    /** PIN 검증 후 잠금해제. 맞으면 true */
    fun unlockWithPin(pin: String): Boolean
    /** 로그아웃: 이름·사번·PIN 삭제 + 잠금 (근무기록·메모는 유지) */
    suspend fun signOut()
}

interface PatternRepository {
    fun observePattern(patternId: String): Flow<Pattern?>
    suspend fun getPatterns(role: CrewRole? = null): List<Pattern>
    suspend fun save(pattern: Pattern)
}

interface DiaRepository {
    /** 해당 날짜 성격(평/토/휴) + 교번의 다이아 조회 */
    suspend fun findDia(number: Int, dayKind: DayKind, nightVariant: NightVariant? = null, isBranch: Boolean = false): Dia?
    suspend fun getAll(dayKind: DayKind): List<Dia>
}

interface ScheduleRepository {
    /** 오버라이드 문서 스트림 (해당 월) */
    fun observeOverrides(uid: String, month: YearMonth): Flow<List<Schedule>>
    suspend fun saveOverride(schedule: Schedule)
    suspend fun deleteOverride(uid: String, date: LocalDate)
    suspend fun getOverridesFor(uid: String, month: YearMonth): List<Schedule>
}

interface HolidayRepository {
    suspend fun getHolidays(month: YearMonth): Map<LocalDate, Pair<String, Boolean>> // name, isPublic
}

/**
 * 월별 근무기록 스냅샷 — 지난 달은 그때 근무 그대로 보존.
 * (근무선택을 다시 해서 패턴 위치가 바뀌어도 과거 월 기록은 변하지 않는다)
 */
interface SnapshotRepository {
    suspend fun load(uid: String, month: YearMonth): Map<LocalDate, String>?
    suspend fun save(uid: String, month: YearMonth, duties: Map<LocalDate, String>)
    suspend fun savedMonths(uid: String): List<YearMonth>
}

/** 동료 (체험판: 수동 등록 · Firebase 연동 시 로그인 사용자끼리 자동 공유로 전환) */
interface MateRepository {
    fun observeMates(): Flow<List<Mate>>
    suspend fun upsert(mate: Mate)
    suspend fun remove(name: String)
}

/** 동료근무 실시간 공유 — 로그인 근무자 전체 (Firebase 없으면 빈 목록) */
data class RosterEntry(
    val uid: String,
    val name: String,
    val group: CrewGroup,
    val patternOffset: Int,
)

interface RosterRepository {
    fun observeUsers(): Flow<List<RosterEntry>>
    /** 해당 월 근무변경 전체: uid → (날짜 → 변경 근무) */
    fun observeMonthOverrides(month: YearMonth): Flow<Map<String, Map<LocalDate, String>>>
}

