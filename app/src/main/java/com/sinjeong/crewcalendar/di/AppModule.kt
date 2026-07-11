package com.sinjeong.crewcalendar.di

import com.sinjeong.crewcalendar.data.local.*
import com.sinjeong.crewcalendar.domain.repository.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * 저장소 바인딩.
 *
 * ★ 현재: 오프라인 체험판 — 모든 데이터를 폰(SharedPreferences)에 저장.
 *   시각표·교번순서·공휴일은 Bundled.kt에 내장되어 있어 네트워크 불필요.
 *
 * ★ Firebase 연동 시: google-services.json 추가 후 아래 바인딩을
 *   data/repository/FirestoreRepositories.kt 구현으로 교체 + FirebaseModule 복원.
 *   (기존 Firestore 구현은 파일로 남아있음)
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun bindUserRepo(impl: LocalUserRepository): UserRepository
    @Binds abstract fun bindPatternRepo(impl: LocalPatternRepository): PatternRepository
    @Binds abstract fun bindDiaRepo(impl: LocalDiaRepository): DiaRepository
    @Binds abstract fun bindScheduleRepo(impl: LocalScheduleRepository): ScheduleRepository
    @Binds abstract fun bindHolidayRepo(impl: LocalHolidayRepository): HolidayRepository
}
