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

    override suspend fun updatePatternPosition(patternId: String, offset: Int) {
        val cur = state.value ?: return // 로그인 전에는 근무선택 불가
        upsert(cur.copy(patternId = patternId, patternOffset = offset))
    }

    override suspend fun register(user: User) {
        upsert(user)          // 이름·사번·패턴 저장
        unlocked.value = true
    }

    override suspend fun signOut() {
        // "pin"은 v1.6.15까지 쓰던 잔재 — 남아 있으면 같이 지운다
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

    /**
     * 저장 키 = "이름|소속". v1.6.15까지는 이름 하나였는데, 그룹 간 동명이인(김지환·박두원·이용석)에
     * ★을 달면 같은 이름의 다른 소속 행을 덮어써 동료근무에서 사라졌다.
     */
    private fun key(mate: Mate) = "${mate.name}|${mate.group.name}"

    /**
     * 없어진 소속 이름 → 지금 소속. `SHIFT_CONTROL`은 v1.6.54~59에만 있던 값이다(v1.6.60에서
     * 부서를 소속에서 빼고 4조2교대로 다시 합쳤다 — `CrewGroup` 주석).
     *
     * 안 두면 `CrewGroup.valueOf`가 던져서 관제 동료가 통째로 **신정지선**으로 되읽히고(근무가 딴판),
     * 키까지 옛것으로 남아 ★을 토글하거나 지우면 같은 사람이 **두 줄**로 늘어난다
     * (`LazyColumn`의 key 중복 → 크래시).
     */
    private val RENAMED_GROUPS = mapOf("SHIFT_CONTROL" to CrewGroup.SHIFT_4_2.name)

    /**
     * 옛 저장분을 "이름|지금소속" 한 벌로 1회 이관 — 기존 즐겨찾기·수동등록이 날아가지 않게.
     *  ① 이름-only 키 (v1.6.15 이전)
     *  ② 없어진 소속 이름이 박힌 키 (`이름|SHIFT_CONTROL`, v1.6.60)
     * 키와 본문(`group`)을 **같이** 고친다 — 본문만 두면 `loadAll`이 옛 소속으로 되읽는다.
     */
    private fun migrateLegacyKeys() {
        val stale = prefs.all.filterKeys { !it.contains('|') || it.substringAfter('|') in RENAMED_GROUPS }
        if (stale.isEmpty()) return
        val e = prefs.edit()
        stale.forEach { (k, value) ->
            runCatching {
                val o = JSONObject(value as String)
                val old = if ('|' in k) k.substringAfter('|') else o.optString("group")
                val group = RENAMED_GROUPS[old]
                    ?: runCatching { CrewGroup.valueOf(old).name }.getOrDefault(CrewGroup.BRANCH.name)
                e.putString("${k.substringBefore('|')}|$group", o.put("group", group).toString())
                    .remove(k)
            }
        }
        e.apply()
    }

    private fun loadAll(): List<Mate> =
        prefs.all.mapNotNull { (k, value) ->
            runCatching {
                val o = JSONObject(value as String)
                Mate(
                    name = k.substringBefore('|'),
                    group = runCatching { CrewGroup.valueOf(o.optString("group")) }
                        .getOrDefault(CrewGroup.BRANCH),
                    patternOffset = o.optInt("offset"),
                    favGroup = o.optString("fav").takeIf { it.isNotBlank() }
                        ?.let { runCatching { FavGroup.valueOf(it) }.getOrNull() },
                )
            }.getOrNull()
        }.sortedBy { it.name }

    private val state: MutableStateFlow<List<Mate>>

    init {
        migrateLegacyKeys()
        state = MutableStateFlow(loadAll())
    }

    override fun observeMates(): Flow<List<Mate>> = state

    override suspend fun upsert(mate: Mate) {
        val o = JSONObject()
            .put("group", mate.group.name)
            .put("offset", mate.patternOffset)
            .put("fav", mate.favGroup?.name ?: "")
        prefs.edit().putString(key(mate), o.toString()).apply()
        state.value = loadAll()
    }

    override suspend fun remove(mate: Mate) {
        prefs.edit().remove(key(mate)).apply()
        state.value = loadAll()
    }
}
