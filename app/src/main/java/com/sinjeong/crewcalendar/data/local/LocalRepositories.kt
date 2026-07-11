package com.sinjeong.crewcalendar.data.local

import android.content.Context
import com.sinjeong.crewcalendar.domain.model.*
import com.sinjeong.crewcalendar.domain.repository.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

    private fun load(): User = User(
        uid = "local",
        name = prefs.getString("name", "게스트") ?: "게스트",
        role = runCatching { CrewRole.valueOf(prefs.getString("role", null) ?: "") }
            .getOrDefault(CrewRole.DRIVER_BRANCH),
        // 첫 실행 기본값: 지선 패턴 (2026-07-01 = 지3 라인)
        patternId = prefs.getString("patternId", Bundled.BRANCH_PATTERN.id),
        patternOffset = prefs.getInt("patternOffset", 0),
        visibleToOthers = prefs.getBoolean("visible", true),
    )

    private val state = MutableStateFlow<User?>(load())

    override val currentUid: String get() = "local"

    override fun observeMe(): Flow<User?> = state

    override suspend fun upsert(user: User) {
        prefs.edit()
            .putString("name", user.name)
            .putString("role", user.role.name)
            .putString("patternId", user.patternId)
            .putInt("patternOffset", user.patternOffset)
            .putBoolean("visible", user.visibleToOthers)
            .apply()
        state.value = user.copy(uid = "local")
    }

    override suspend fun searchByName(query: String): List<User> = emptyList()

    override suspend fun updateFcmToken(token: String) = Unit

    override suspend fun updatePatternPosition(patternId: String, offset: Int) {
        val cur = state.value ?: load()
        upsert(cur.copy(patternId = patternId, patternOffset = offset))
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
    override fun observePattern(patternId: String): Flow<Pattern?> = flowOf(
        when (patternId) {
            Bundled.BRANCH_PATTERN.id -> Bundled.BRANCH_PATTERN
            Bundled.MAIN_PATTERN.id -> Bundled.MAIN_PATTERN
            else -> null
        }
    )

    override suspend fun getPatterns(role: CrewRole?): List<Pattern> =
        listOf(Bundled.BRANCH_PATTERN, Bundled.MAIN_PATTERN)

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
