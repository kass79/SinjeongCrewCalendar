package com.sinjeong.crewcalendar.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import com.sinjeong.crewcalendar.domain.model.*
import com.sinjeong.crewcalendar.domain.repository.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

private fun LocalDate.key() = toString() // yyyy-MM-dd

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
) : UserRepository {
    override val currentUid: String? get() = auth.currentUser?.uid

    override fun observeMe(): Flow<User?> {
        val uid = currentUid ?: return flowOf(null)
        return db.collection("users").document(uid).snapshots().map { snap ->
            if (!snap.exists()) null else User(
                uid = uid,
                name = snap.getString("name").orEmpty(),
                email = snap.getString("email").orEmpty(),
                role = snap.getString("role")?.let { runCatching { CrewRole.valueOf(it) }.getOrNull() } ?: CrewRole.DRIVER_MAIN,
                patternId = snap.getString("patternId"),
                patternOffset = (snap.getLong("patternOffset") ?: 0L).toInt(),
                visibleToOthers = snap.getBoolean("visibleToOthers") ?: true,
                fcmToken = snap.getString("fcmToken"),
                googleCalendarId = snap.getString("googleCalendarId"),
                annualLeaveRemaining = (snap.getLong("annualLeaveRemaining") ?: 0L).toInt(),
                promotedLeaveRemaining = (snap.getLong("promotedLeaveRemaining") ?: 0L).toInt(),
            )
        }
    }

    override suspend fun upsert(user: User) {
        db.collection("users").document(user.uid).set(
            mapOf(
                "name" to user.name, "email" to user.email, "role" to user.role.name,
                "patternId" to user.patternId, "patternOffset" to user.patternOffset,
                "visibleToOthers" to user.visibleToOthers, "fcmToken" to user.fcmToken,
                "googleCalendarId" to user.googleCalendarId,
                "annualLeaveRemaining" to user.annualLeaveRemaining,
                "promotedLeaveRemaining" to user.promotedLeaveRemaining,
            )
        ).await()
    }

    override suspend fun searchByName(query: String): List<User> =
        db.collection("users")
            .orderBy("name").startAt(query).endAt(query + "\uf8ff")
            .limit(20).get().await()
            .documents.map { d ->
                User(uid = d.id, name = d.getString("name").orEmpty(),
                    visibleToOthers = d.getBoolean("visibleToOthers") ?: true)
            }.filter { it.visibleToOthers }

    override suspend fun updateFcmToken(token: String) {
        val uid = currentUid ?: return
        db.collection("users").document(uid).update("fcmToken", token).await()
    }

    override suspend fun updatePatternPosition(patternId: String, offset: Int) {
        val uid = currentUid ?: return
        db.collection("users").document(uid)
            .update(mapOf("patternId" to patternId, "patternOffset" to offset)).await()
    }
}

@Singleton
class PatternRepositoryImpl @Inject constructor(
    private val db: FirebaseFirestore,
) : PatternRepository {
    private fun docToPattern(id: String, data: Map<String, Any?>): Pattern = Pattern(
        id = id,
        name = data["name"] as? String ?: "",
        role = (data["role"] as? String)?.let { runCatching { CrewRole.valueOf(it) }.getOrNull() } ?: CrewRole.DRIVER_MAIN,
        sequence = (data["sequence"] as? List<*>)?.map { it.toString() } ?: emptyList(),
        anchorDate = (data["anchorDate"] as? String)?.let(LocalDate::parse) ?: LocalDate.of(2026, 1, 1),
        revision = data["revision"] as? String ?: "",
        createdBy = data["createdBy"] as? String ?: "",
    )

    override fun observePattern(patternId: String): Flow<Pattern?> {
        // 번들 패턴(오프라인 기본값): Firestore에 개정본이 있으면 그걸 우선
        val bundled = when (patternId) {
            Bundled.BRANCH_PATTERN.id -> Bundled.BRANCH_PATTERN
            Bundled.MAIN_PATTERN.id -> Bundled.MAIN_PATTERN
            else -> null
        }
        return db.collection("patterns").document(patternId).snapshots()
            .map { s -> if (s.exists()) docToPattern(s.id, s.data ?: emptyMap()) else bundled }
            .catch { emit(bundled) }
    }

    override suspend fun getPatterns(role: CrewRole?): List<Pattern> {
        var q: Query = db.collection("patterns")
        if (role != null) q = q.whereEqualTo("role", role.name)
        return q.get().await().documents.map { docToPattern(it.id, it.data ?: emptyMap()) }
    }

    override suspend fun save(pattern: Pattern) {
        db.collection("patterns").document(pattern.id.ifEmpty { db.collection("patterns").document().id })
            .set(
                mapOf(
                    "name" to pattern.name, "role" to pattern.role.name,
                    "sequence" to pattern.sequence, "anchorDate" to pattern.anchorDate.toString(),
                    "revision" to pattern.revision, "createdBy" to pattern.createdBy,
                )
            ).await()
    }
}

@Singleton
class DiaRepositoryImpl @Inject constructor(
    private val db: FirebaseFirestore,
) : DiaRepository {
    private fun id(number: Int, dayKind: DayKind, variant: NightVariant?, isBranch: Boolean): String {
        val prefix = if (isBranch) "br" else when (dayKind) {
            DayKind.WEEKDAY -> "wd"; DayKind.SATURDAY -> "sat"; DayKind.HOLIDAY -> "hol"
        }
        return if (variant != null) "night-$number-${variant.name.lowercase()}" else "$prefix-$number"
    }

    override suspend fun findDia(number: Int, dayKind: DayKind, nightVariant: NightVariant?, isBranch: Boolean): Dia? {
        val snap = db.collection("dias").document(id(number, dayKind, nightVariant, isBranch)).get().await()
        if (!snap.exists()) return null
        val data = snap.data ?: return null
        return Dia(
            id = snap.id,
            number = (data["number"] as? Long ?: 0L).toInt(),
            dayKind = (data["dayKind"] as? String)?.let { runCatching { DayKind.valueOf(it) }.getOrNull() } ?: DayKind.WEEKDAY,
            nightVariant = (data["nightVariant"] as? String)?.let { runCatching { NightVariant.valueOf(it) }.getOrNull() },
            isNight = data["isNight"] as? Boolean ?: false,
            isStandby = data["isStandby"] as? Boolean ?: false,
            isBranch = data["isBranch"] as? Boolean ?: false,
            signOn = (data["signOn"] as? String)?.let(LocalTime::parse) ?: LocalTime.MIN,
            signOff = (data["signOff"] as? String)?.let(LocalTime::parse) ?: LocalTime.MIN,
            signOffNextDay = data["signOffNextDay"] as? Boolean ?: false,
            legs = (data["legs"] as? List<*>)?.mapNotNull { raw ->
                (raw as? Map<*, *>)?.let { m ->
                    DiaLeg(
                        start = (m["start"] as? String)?.let(LocalTime::parse) ?: return@let null,
                        end = (m["end"] as? String)?.let(LocalTime::parse) ?: return@let null,
                        endNextDay = m["endNextDay"] as? Boolean ?: false,
                        fromStation = m["fromStation"] as? String ?: "",
                        toStation = m["toStation"] as? String ?: "",
                        trainNo = m["trainNo"] as? String,
                        kind = (m["kind"] as? String)?.let { runCatching { DiaLeg.LegKind.valueOf(it) }.getOrNull() } ?: DiaLeg.LegKind.DRIVE,
                    )
                }
            } ?: emptyList(),
            revision = data["revision"] as? String ?: "",
        )
    }

    override suspend fun getAll(dayKind: DayKind): List<Dia> =
        db.collection("dias").whereEqualTo("dayKind", dayKind.name).get().await()
            .documents.mapNotNull { d -> findDia((d.getLong("number") ?: return@mapNotNull null).toInt(), dayKind) }
}

@Singleton
class ScheduleRepositoryImpl @Inject constructor(
    private val db: FirebaseFirestore,
) : ScheduleRepository {
    private fun col() = db.collection("schedules")

    override fun observeOverrides(uid: String, month: YearMonth): Flow<List<Schedule>> =
        col().whereEqualTo("uid", uid)
            .whereGreaterThanOrEqualTo("date", month.atDay(1).key())
            .whereLessThanOrEqualTo("date", month.atEndOfMonth().key())
            .snapshots()
            .map { qs ->
                qs.documents.map { d ->
                    Schedule(
                        id = d.id,
                        uid = d.getString("uid").orEmpty(),
                        date = LocalDate.parse(d.getString("date")),
                        dutyRaw = d.getString("dutyRaw").orEmpty(),
                        memo = d.getString("memo").orEmpty(),
                        source = d.getString("source")?.let { runCatching { Schedule.Source.valueOf(it) }.getOrNull() } ?: Schedule.Source.MANUAL,
                        originalDutyRaw = d.getString("originalDutyRaw"),
                    )
                }
            }

    override suspend fun saveOverride(schedule: Schedule) {
        val docId = "${schedule.uid}_${schedule.date.key()}"
        col().document(docId).set(
            mapOf(
                "uid" to schedule.uid, "date" to schedule.date.key(),
                "dutyRaw" to schedule.dutyRaw, "memo" to schedule.memo,
                "source" to schedule.source.name, "originalDutyRaw" to schedule.originalDutyRaw,
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            )
        ).await()
    }

    override suspend fun deleteOverride(uid: String, date: LocalDate) {
        col().document("${uid}_${date.key()}").delete().await()
    }

    override suspend fun getOverridesFor(uid: String, month: YearMonth): List<Schedule> =
        col().whereEqualTo("uid", uid)
            .whereGreaterThanOrEqualTo("date", month.atDay(1).key())
            .whereLessThanOrEqualTo("date", month.atEndOfMonth().key())
            .get().await().documents.map { d ->
                Schedule(
                    id = d.id, uid = uid,
                    date = LocalDate.parse(d.getString("date")),
                    dutyRaw = d.getString("dutyRaw").orEmpty(),
                    memo = d.getString("memo").orEmpty(),
                )
            }
}

@Singleton
class HolidayRepositoryImpl @Inject constructor(
    private val db: FirebaseFirestore,
) : HolidayRepository {
    private val cache = mutableMapOf<YearMonth, Map<LocalDate, Pair<String, Boolean>>>()

    override suspend fun getHolidays(month: YearMonth): Map<LocalDate, Pair<String, Boolean>> =
        cache.getOrPut(month) {
            db.collection("holidays")
                .whereGreaterThanOrEqualTo(com.google.firebase.firestore.FieldPath.documentId(), month.atDay(1).key())
                .whereLessThanOrEqualTo(com.google.firebase.firestore.FieldPath.documentId(), month.atEndOfMonth().key())
                .get().await().documents.associate { d ->
                    LocalDate.parse(d.id) to Pair(d.getString("name").orEmpty(), d.getBoolean("isPublicHoliday") ?: false)
                }
        }
}
