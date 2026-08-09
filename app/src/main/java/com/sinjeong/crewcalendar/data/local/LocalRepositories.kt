package com.sinjeong.crewcalendar.data.local

import android.content.Context
import com.sinjeong.crewcalendar.domain.model.*
import com.sinjeong.crewcalendar.domain.repository.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 오프라인 체험판 저장소 (Firebase 연결 전).
 * 모든 데이터를 SharedPreferences에 저장한다 — 로그인 없이 전 기능 동작.
 * Firebase 연동 시 di/AppModule 바인딩만 Firestore 구현으로 교체하면 된다.
 */
@Singleton
class LocalUserRepository @Inject constructor(
    @ApplicationContext context: Context,
) : UserRepository {
    private val prefs = context.getSharedPreferences("local_user", Context.MODE_PRIVATE)

    /** 이름+사번 로그인 전에는 null → 로그인 화면 게이트 */
    private fun load(): User? {
        val name = prefs.getString("name", "") ?: ""
        val empNo = prefs.getString("empNo", "") ?: ""
        if (name.isBlank() || empNo.isBlank()) return null
        return User(
            uid = empNo,
            name = name,
            role = runCatching { CrewRole.valueOf(prefs.getString("role", null) ?: "") }
                .getOrDefault(CrewRole.DRIVER_BRANCH),
            patternId = prefs.getString("patternId", Bundled.BRANCH_PATTERN.id),
            patternOffset = prefs.getInt("patternOffset", 0),
            visibleToOthers = prefs.getBoolean("visible", true),
        )
    }

    private val state = MutableStateFlow<User?>(load())

    /** 자동 로그인: 저장된 사용자가 있으면 앱 시작 시 바로 잠금해제(PIN 재입력 없음) */
    private val unlocked = MutableStateFlow(state.value != null)

    override val currentUid: String get() = state.value?.uid ?: "local"

    /** 잠금해제 전에는 null → 로그인 화면 게이트 (최초 로그인 / 로그아웃 후) */
    override fun observeMe(): Flow<User?> =
        combine(state, unlocked) { u, unl -> if (unl) u else null }

    override suspend fun upsert(user: User) {
        prefs.edit()
            .putString("name", user.name)
            .putString("empNo", user.uid)
            .putString("role", user.role.name)
            .putString("patternId", user.patternId)
            .putInt("patternOffset", user.patternOffset)
            .putBoolean("visible", user.visibleToOthers)
            .apply()
        state.value = user
    }

    /** 기존 호출부 호환 — 로그아웃은 signOut()으로 일원화 */
    suspend fun logout() = signOut()

    override suspend fun searchByName(query: String): List<User> = emptyList()

    override suspend fun updateFcmToken(token: String) = Unit

    override suspend fun updatePatternPosition(patternId: String, offset: Int) {
        val cur = state.value ?: return // 로그인 전에는 근무선택 불가
        upsert(cur.copy(patternId = patternId, patternOffset = offset))
    }

    override fun hasPin(): Boolean = !prefs.getString("pin", null).isNullOrBlank()

    override fun savedName(): String? = prefs.getString("name", null)?.takeIf { it.isNotBlank() }

    override suspend fun registerWithPin(user: User, pin: String) {
        prefs.edit().putString("pin", pin).apply()
        upsert(user)          // 이름·사번·패턴 저장
        unlocked.value = true
    }

    override fun unlockWithPin(pin: String): Boolean {
        val ok = prefs.getString("pin", null) == pin && pin.isNotBlank()
        if (ok) unlocked.value = true
        return ok
    }

    override suspend fun signOut() {
        prefs.edit().remove("name").remove("empNo").remove("pin").apply()
        unlocked.value = false
        state.value = null
    }
}

@Singleton
class LocalScheduleRepository @Inject constructor(
    @ApplicationContext context: Context,
) : ScheduleRepository {
    private val prefs = context.getSharedPreferences("local_schedules", Context.MODE_PRIVATE)

    private fun loadAll(): Map<LocalDate, Schedule> =
        prefs.all.mapNotNull { (key, value) ->
            runCatching {
                val o = JSONObject(value as String)
                val date = LocalDate.parse(key)
                date to Schedule(
                    id = "local_$key", uid = "local", date = date,
                    dutyRaw = o.optString("dutyRaw"),
                    memo = o.optString("memo"),
                    source = runCatching { Schedule.Source.valueOf(o.optString("source")) }
                        .getOrDefault(Schedule.Source.MANUAL),
                    originalDutyRaw = o.optString("originalDutyRaw").ifBlank { null },
                )
            }.getOrNull()
        }.toMap()

    private val state = MutableStateFlow(loadAll())

    override fun observeOverrides(uid: String, month: YearMonth): Flow<List<Schedule>> =
        state.map { all -> all.values.filter { YearMonth.from(it.date) == month } }

    override suspend fun saveOverride(schedule: Schedule) {
        val o = JSONObject()
            .put("dutyRaw", schedule.dutyRaw)
            .put("memo", schedule.memo)
            .put("source", schedule.source.name)
            .put("originalDutyRaw", schedule.originalDutyRaw ?: "")
        prefs.edit().putString(schedule.date.toString(), o.toString()).apply()
        state.value = state.value + (schedule.date to schedule)
    }

    override suspend fun deleteOverride(uid: String, date: LocalDate) {
        prefs.edit().remove(date.toString()).apply()
        state.value = state.value - date
    }

    override suspend fun getOverridesFor(uid: String, month: YearMonth): List<Schedule> =
        state.value.values.filter { YearMonth.from(it.date) == month }
}

@Singleton
class LocalPatternRepository @Inject constructor() : PatternRepository {
    override fun observePattern(patternId: String): Flow<Pattern?> =
        flowOf(Bundled.ALL_PATTERNS.firstOrNull { it.id == patternId })

    override suspend fun getPatterns(role: CrewRole?): List<Pattern> = Bundled.ALL_PATTERNS

    override suspend fun save(pattern: Pattern) = Unit
}

@Singleton
class LocalDiaRepository @Inject constructor() : DiaRepository {
    override suspend fun findDia(number: Int, dayKind: DayKind, nightVariant: NightVariant?, isBranch: Boolean): Dia? = null
    override suspend fun getAll(dayKind: DayKind): List<Dia> = emptyList()
}

@Singleton
class LocalHolidayRepository @Inject constructor() : HolidayRepository {
    override suspend fun getHolidays(month: YearMonth): Map<LocalDate, Pair<String, Boolean>> =
        Bundled.PUBLIC_HOLIDAYS.filterKeys { YearMonth.from(it) == month }
            .mapValues { it.value to true } +
            Bundled.MEMORIAL_DAYS.filterKeys { YearMonth.from(it) == month }
                .mapValues { it.value to false }
}

/** 월별 근무기록: 지난 달을 처음 계산할 때 동결 저장 → 이후 근무선택이 바뀌어도 불변 */
@Singleton
class LocalSnapshotRepository @Inject constructor(
    @ApplicationContext context: Context,
) : SnapshotRepository {
    private val prefs = context.getSharedPreferences("month_snapshots", Context.MODE_PRIVATE)

    private fun key(uid: String, month: YearMonth) = "$uid|$month"

    override suspend fun load(uid: String, month: YearMonth): Map<LocalDate, String>? {
        val raw = prefs.getString(key(uid, month), null) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            o.keys().asSequence().associate { d -> LocalDate.parse(d) to o.getString(d) }
        }.getOrNull()
    }

    override suspend fun save(uid: String, month: YearMonth, duties: Map<LocalDate, String>) {
        val o = JSONObject()
        duties.forEach { (d, duty) -> o.put(d.toString(), duty) }
        prefs.edit().putString(key(uid, month), o.toString()).apply()
    }

    override suspend fun savedMonths(uid: String): List<YearMonth> =
        prefs.all.keys.filter { it.startsWith("$uid|") }
            .mapNotNull { runCatching { YearMonth.parse(it.substringAfter("|")) }.getOrNull() }
            .sortedDescending()
}

/** 동료 로컬 저장 (Firebase 연동 시 자동 공유로 교체) */
@Singleton
class LocalMateRepository @Inject constructor(
    @ApplicationContext context: Context,
) : MateRepository {
    private val prefs = context.getSharedPreferences("mates", Context.MODE_PRIVATE)

    private fun loadAll(): List<Mate> =
        prefs.all.mapNotNull { (name, value) ->
            runCatching {
                val o = JSONObject(value as String)
                Mate(
                    name = name,
                    group = runCatching { CrewGroup.valueOf(o.optString("group")) }
                        .getOrDefault(CrewGroup.BRANCH),
                    patternOffset = o.optInt("offset"),
                    favGroup = o.optString("fav").takeIf { it.isNotBlank() }
                        ?.let { runCatching { FavGroup.valueOf(it) }.getOrNull() },
                )
            }.getOrNull()
        }.sortedBy { it.name }

    private val state = MutableStateFlow(loadAll())

    override fun observeMates(): Flow<List<Mate>> = state

    override suspend fun upsert(mate: Mate) {
        val o = JSONObject()
            .put("group", mate.group.name)
            .put("offset", mate.patternOffset)
            .put("fav", mate.favGroup?.name ?: "")
        prefs.edit().putString(mate.name, o.toString()).apply()
        state.value = loadAll()
    }

    override suspend fun remove(name: String) {
        prefs.edit().remove(name).apply()
        state.value = loadAll()
    }
}
