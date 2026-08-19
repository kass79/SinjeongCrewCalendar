package com.sinjeong.crewcalendar.di

import android.content.Context
import com.google.firebase.FirebaseApp
import com.sinjeong.crewcalendar.data.local.*
import com.sinjeong.crewcalendar.data.remote.FirestoreRosterRepository
import com.sinjeong.crewcalendar.data.remote.FirestoreScheduleRepository
import com.sinjeong.crewcalendar.data.remote.FirestoreUserRepository
import com.sinjeong.crewcalendar.data.remote.LocalRosterRepository
import com.sinjeong.crewcalendar.domain.repository.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton

/**
 * 저장소 바인딩 — google-services.json 유무로 자동 전환.
 *
 * ★ json 없음(체험판): 전부 로컬(SharedPreferences). 네트워크 불필요.
 * ★ json 있음(정식): 근무선택·근무변경을 Firestore로 공유(동료근무 실데이터),
 *   내 달력·메모·스냅샷은 여전히 로컬(메모는 서버에 안 올라감).
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    private fun firebaseOn(context: Context) = FirebaseApp.getApps(context).isNotEmpty()

    @Provides @Singleton
    fun userRepo(
        @ApplicationContext context: Context,
        local: LocalUserRepository,
        remote: Provider<FirestoreUserRepository>,
    ): UserRepository = if (firebaseOn(context)) remote.get() else local

    @Provides @Singleton
    fun scheduleRepo(
        @ApplicationContext context: Context,
        local: LocalScheduleRepository,
        remote: Provider<FirestoreScheduleRepository>,
    ): ScheduleRepository = if (firebaseOn(context)) remote.get() else local

    @Provides @Singleton
    fun rosterRepo(
        @ApplicationContext context: Context,
        local: LocalRosterRepository,
        remote: Provider<FirestoreRosterRepository>,
    ): RosterRepository = if (firebaseOn(context)) remote.get() else local

    @Provides @Singleton fun patternRepo(impl: LocalPatternRepository): PatternRepository = impl
    @Provides @Singleton fun holidayRepo(impl: LocalHolidayRepository): HolidayRepository = impl
    @Provides @Singleton fun snapshotRepo(impl: LocalSnapshotRepository): SnapshotRepository = impl
    @Provides @Singleton fun mateRepo(impl: LocalMateRepository): MateRepository = impl
}
