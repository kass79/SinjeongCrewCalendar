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

/**
 * 구내식당 주간식단표 (v1.6.80). 문서 ID = **주 시작일(월요일)** — 여러 주가 나란히 산다.
 *
 * 왜 여러 주인지는 [com.sinjeong.crewcalendar.domain.model.WeeklyMenu] KDoc 참고
 * (다음 주 표가 **일요일쯤** 나오는데 한 장만 들고 있으면 그날 점심이 사라진다).
 *
 * 오프라인 캐시는 **따로 만들지 않는다** — Firestore 안드로이드 SDK 는 로컬 영속화가 기본 켜짐이라
 * 스냅샷 리스너가 마지막으로 받은 문서를 오프라인에서 그대로 돌려준다. 지하 터널에서 쓰는 앱이라
 * 이 동작이 곧 요구사항이고, 별도 SharedPreferences 미러는 두 벌을 어긋나게 만들 뿐이다.
 */
interface MenuRepository {
    /** [from] 이후로 시작하는 주들 (주 시작일 → 21칸). 오래된 주는 [save] 가 청소한다 */
    fun observeFrom(from: LocalDate): Flow<Map<LocalDate, List<String>>>

    /**
     * 관리자 저장. 같은 주 문서가 있으면 통째로 덮어쓴다(화면이 먼저 확인을 받는다).
     * @param source 21칸을 채운 경로(`pdf`/`hwp`/`photo`/`paste`/`manual`) — 저장할 때만 남기는
     *   진단 값이다(v1.6.85). 읽는 쪽([observeFrom])은 `cells` 만 본다.
     */
    suspend fun save(weekStart: LocalDate, cells: List<String>, source: String): AdminWriteResult =
        AdminWriteResult.FAILED

    /** 이미 그 주 문서가 있나 — 편집 화면의 "덮어쓸까요?" 확인용 */
    suspend fun exists(weekStart: LocalDate): Boolean = false
}

interface RosterRepository {
    fun observeUsers(): Flow<List<RosterEntry>>
    /** 해당 월 근무변경 전체: uid → (날짜 → 변경 근무) */
    fun observeMonthOverrides(month: YearMonth): Flow<Map<String, Map<LocalDate, String>>>

    /** 관리자 대리 등록/수정 — users/{사번}에 로그인 사용자와 같은 스키마로 write. 서버 미연동이면 FAILED */
    suspend fun adminUpsert(entry: RosterEntry): AdminWriteResult = AdminWriteResult.FAILED
    suspend fun adminDelete(uid: String): AdminWriteResult = AdminWriteResult.FAILED
}


/**
 * 관리자 공지 (v1.6.89). `notices/{id}` — 문서 하나가 공지 하나다.
 *
 * 식단표([MenuRepository])와 같은 모양이고 오프라인 캐시를 따로 두지 않는 이유도 같다 —
 * Firestore SDK 의 로컬 영속화가 마지막 스냅샷을 그대로 돌려준다.
 */
interface NoticeRepository {
    /** 오늘 기간 안의 공지(최신순). 오류 시 직전 값 유지(식단표와 같은 규칙) */
    fun observeActive(today: LocalDate): Flow<List<Notice>>

    /** 관리자 저장. [Notice.id] 가 비면 새 문서(자동 ID). 서버 미연동이면 FAILED */
    suspend fun save(n: Notice): AdminWriteResult = AdminWriteResult.FAILED
    suspend fun delete(id: String): AdminWriteResult = AdminWriteResult.FAILED
}
