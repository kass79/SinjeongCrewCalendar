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
    // 근무선택은 `SelectDutyPositionUseCase` → [upsert] 한 길뿐이다. v1.6.63에서 아무도 안 부르던
    // `updatePatternPosition`을 지웠다 — 교번 구간(`User.patternSegments`)을 건드리지 않고
    // patternId만 갈아 끼우는 자리라 남겨 두면 달력이 조용히 틀어지는 뒷문이 된다.

    /**
     * 로그인 완료: 신원 저장 + 잠금해제.
     * v1.6.16에서 PIN 단계를 없앴다 — 이름+사번 확인이 끝이고, 저장된 사용자가 있으면 자동 로그인.
     */
    suspend fun register(user: User)
    /** 로그아웃: 이름·사번 삭제 + 잠금 (근무기록·메모는 유지) */
    suspend fun signOut()
}

interface PatternRepository {
    fun observePattern(patternId: String): Flow<Pattern?>
    suspend fun getPatterns(role: CrewRole? = null): List<Pattern>
    suspend fun save(pattern: Pattern)
}

interface ScheduleRepository {
    /** 오버라이드 문서 스트림 (해당 월) */
    fun observeOverrides(uid: String, month: YearMonth): Flow<List<Schedule>>
    suspend fun saveOverride(schedule: Schedule)
    suspend fun deleteOverride(uid: String, date: LocalDate)
    suspend fun getOverridesFor(uid: String, month: YearMonth): List<Schedule>
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
    /** 키는 이름+소속 — 그룹 간 동명이인(김지환·박두원·이용석)이 서로를 덮어쓰지 않게 (v1.6.16) */
    suspend fun upsert(mate: Mate)
    suspend fun remove(mate: Mate)
}

/** 동료근무 실시간 공유 — 로그인 근무자 전체 (Firebase 없으면 빈 목록) */
data class RosterEntry(
    val uid: String,
    val name: String,
    val group: CrewGroup,
    val patternOffset: Int,
    /** 관리자가 대리 등록한 사람이면 "admin". 본인이 직접 가입하면 그 write가 문서를 덮어써 null이 된다 */
    val addedBy: String? = null,
)

/**
 * 관리자 대리등록/삭제 결과. **[DENIED] 와 [FAILED] 를 합치면 안 된다** —
 * 서버가 규칙으로 거부한 것("이미 본인이 가입한 사번")을 통신 실패로 안내하면 사용자가
 * 인터넷을 확인하러 간다(v1.6.66 까지 실제로 그랬다). 문구는 AdminViewModel 참조.
 */
enum class AdminWriteResult { OK, DENIED, FAILED }

interface RosterRepository {
    fun observeUsers(): Flow<List<RosterEntry>>
    /** 해당 월 근무변경 전체: uid → (날짜 → 변경 근무) */
    fun observeMonthOverrides(month: YearMonth): Flow<Map<String, Map<LocalDate, String>>>

    /** 관리자 대리 등록/수정 — users/{사번}에 로그인 사용자와 같은 스키마로 write. 서버 미연동이면 FAILED */
    suspend fun adminUpsert(entry: RosterEntry): AdminWriteResult = AdminWriteResult.FAILED
    suspend fun adminDelete(uid: String): AdminWriteResult = AdminWriteResult.FAILED
}

