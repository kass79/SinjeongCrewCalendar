package com.sinjeong.crewcalendar.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.sinjeong.crewcalendar.data.local.LocalScheduleRepository
import com.sinjeong.crewcalendar.data.local.LocalUserRepository
import com.sinjeong.crewcalendar.domain.model.Bundled
import com.sinjeong.crewcalendar.domain.model.CrewGroup
import com.sinjeong.crewcalendar.domain.model.CrewRole
import com.sinjeong.crewcalendar.domain.model.Schedule
import com.sinjeong.crewcalendar.domain.model.User
import com.sinjeong.crewcalendar.domain.repository.RosterEntry
import com.sinjeong.crewcalendar.domain.repository.RosterRepository
import com.sinjeong.crewcalendar.domain.repository.ScheduleRepository
import com.sinjeong.crewcalendar.domain.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firebase(Firestore) 공유 저장소 — google-services.json이 있을 때만 DI로 활성화.
 *
 * 공유 정책 (사용자 확정):
 *  - 근무선택(users)·근무변경(rosterOverrides) = 전체 공개 → 동료근무 매트릭스 실데이터
 *  - 메모 = 서버에 올리지 않음(폰에만 저장) → 유출 원천 차단
 *  - 내 달력의 진실은 여전히 로컬(오프라인 완전 동작), 서버는 공유용 미러
 *
 * ponytail: 인증 = 익명 로그인(사내 신뢰 기반, 문서 잠금은 필요해지면 추가),
 *           기기 변경 시 본인 메모·과거기록은 이전 안 됨(알려진 한계).
 */

/** 익명 인증 보장 — 오프라인 등 실패 시 false (쓰기는 다음 기회에 재시도됨) */
private suspend fun ensureAuth(): Boolean = runCatching {
    val auth = FirebaseAuth.getInstance()
    if (auth.currentUser == null) auth.signInAnonymously().await()
    true
}.getOrDefault(false)

@Singleton
class FirestoreUserRepository @Inject constructor(
    private val local: LocalUserRepository,
) : UserRepository {
    private val db get() = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // 체험판(로컬)에서 이미 로그인한 사용자도 앱 시작 시 한 번 자동 미러
        scope.launch { runCatching { local.observeMe().first()?.let { publish(it) } } }
    }

    override val currentUid: String? get() = local.currentUid
    override fun observeMe(): Flow<User?> = local.observeMe()
    override suspend fun searchByName(query: String): List<User> = local.searchByName(query)

    override suspend fun upsert(user: User) {
        local.upsert(user)
        publish(user)
    }

    override suspend fun updatePatternPosition(patternId: String, offset: Int) {
        local.updatePatternPosition(patternId, offset)
        local.observeMe().first()?.let { publish(it) }
    }

    suspend fun logout() = local.logout()

    override suspend fun register(user: User) {
        local.register(user)
        // 서버 미러: local.register의 upsert는 로컬만 저장하므로 여기서 한 번 더 서버로 publish
        local.observeMe().first()?.let { runCatching { upsert(it) } }
    }
    override suspend fun signOut() = local.signOut()

    /** 내 근무선택을 공용 명단에 미러 (숨김 설정이면 제거) */
    private suspend fun publish(user: User) {
        runCatching {
            if (!ensureAuth()) return
            val doc = db.collection("users").document(user.uid)
            if (!user.visibleToOthers) { doc.delete(); return }
            doc.set(
                mapOf(
                    "name" to user.name,
                    "role" to user.role.name,
                    "patternId" to user.patternId,
                    "patternOffset" to user.patternOffset,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ) // await 안 함 — 오프라인이면 Firestore가 큐잉 후 자동 전송
        }
    }
}

@Singleton
class FirestoreScheduleRepository @Inject constructor(
    private val local: LocalScheduleRepository,
) : ScheduleRepository {
    private val db get() = FirebaseFirestore.getInstance()

    // 내 달력 읽기는 전부 로컬 (메모 포함, 오프라인 완전 동작)
    override fun observeOverrides(uid: String, month: YearMonth) = local.observeOverrides(uid, month)
    override suspend fun getOverridesFor(uid: String, month: YearMonth) = local.getOverridesFor(uid, month)

    override suspend fun saveOverride(schedule: Schedule) {
        local.saveOverride(schedule)
        // 근무변경만 공유 (메모는 서버에 안 올림). 패턴 복귀(dutyRaw 빈값)면 공유 문서 제거
        runCatching {
            if (!ensureAuth()) return@runCatching
            val doc = db.collection("rosterOverrides").document("${schedule.uid}_${schedule.date}")
            if (schedule.dutyRaw.isBlank()) doc.delete()
            else doc.set(
                mapOf(
                    "uid" to schedule.uid,
                    "date" to schedule.date.toString(),
                    "dutyRaw" to schedule.dutyRaw,
                    "originalDutyRaw" to (schedule.originalDutyRaw ?: ""),
                )
            )
        }
    }

    override suspend fun deleteOverride(uid: String, date: LocalDate) {
        local.deleteOverride(uid, date)
        runCatching {
            if (ensureAuth()) db.collection("rosterOverrides").document("${uid}_$date").delete()
        }
    }
}

@Singleton
class FirestoreRosterRepository @Inject constructor() : RosterRepository {
    private val db get() = FirebaseFirestore.getInstance()

    override fun observeUsers(): Flow<List<RosterEntry>> = flow {
        ensureAuth()
        emitAll(callbackFlow {
            val reg = db.collection("users").addSnapshotListener { snap, _ ->
                trySend(snap?.documents?.mapNotNull { d ->
                    val name = d.getString("name") ?: return@mapNotNull null
                    val group =
                        if (d.getString("role") == CrewRole.CONDUCTOR.name) CrewGroup.MAIN_CONDUCTOR
                        // 옛 관제 전용 id(bundled-control42)도 4조2교대로 되읽힌다(v1.6.60, `Bundled.groupFor`)
                        else Bundled.groupFor(d.getString("patternId")) ?: CrewGroup.BRANCH
                    RosterEntry(
                        d.id, name, group, (d.getLong("patternOffset") ?: 0L).toInt(),
                        addedBy = d.getString("addedBy"),
                    )
                } ?: emptyList())
            }
            awaitClose { reg.remove() }
        })
    }

    override fun observeMonthOverrides(month: YearMonth): Flow<Map<String, Map<LocalDate, String>>> = flow {
        ensureAuth()
        emitAll(callbackFlow {
            val reg = db.collection("rosterOverrides")
                .whereGreaterThanOrEqualTo("date", month.atDay(1).toString())
                .whereLessThanOrEqualTo("date", month.atEndOfMonth().toString())
                .addSnapshotListener { snap, _ ->
                    val map = snap?.documents.orEmpty()
                        .groupBy { it.getString("uid") ?: "" }
                        .mapValues { (_, docs) ->
                            docs.mapNotNull { d ->
                                runCatching {
                                    LocalDate.parse(d.getString("date")) to (d.getString("dutyRaw") ?: "")
                                }.getOrNull()
                            }.toMap()
                        }
                    trySend(map)
                }
            awaitClose { reg.remove() }
        })
    }

    /**
     * 관리자 대리 등록 — 앱을 아직 안 깐 동료의 근무를 대신 올린다.
     * 문서 스키마는 FirestoreUserRepository.publish 와 동일 + addedBy.
     * 본인이 나중에 직접 가입하면 publish 가 같은 문서를 통째로 set 해 addedBy 가 사라진다(=본인 소유로 승격).
     */
    override suspend fun adminUpsert(entry: RosterEntry): Boolean = runCatching {
        if (!ensureAuth()) return false
        db.collection("users").document(entry.uid).set(
            mapOf(
                "name" to entry.name,
                "role" to entry.group.role.name,
                "patternId" to Bundled.patternFor(entry.group).id,
                "patternOffset" to entry.patternOffset,
                "addedBy" to "admin",
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
        true
    }.getOrDefault(false)

    override suspend fun adminDelete(uid: String): Boolean = runCatching {
        if (!ensureAuth()) return false
        db.collection("users").document(uid).delete().await()
        true
    }.getOrDefault(false)
}

/** 오프라인(로컬) 모드용 — 공유 데이터 없음 */
@Singleton
class LocalRosterRepository @Inject constructor() : RosterRepository {
    override fun observeUsers(): Flow<List<RosterEntry>> = flowOf(emptyList())
    override fun observeMonthOverrides(month: YearMonth): Flow<Map<String, Map<LocalDate, String>>> =
        flowOf(emptyMap())
}
