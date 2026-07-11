package com.sinjeong.crewcalendar.domain.usecase

import com.sinjeong.crewcalendar.domain.model.*
import com.sinjeong.crewcalendar.domain.repository.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.time.YearMonth
import javax.inject.Inject

/**
 * 한 달 근무 계산: 패턴 순환값 위에 schedules 오버라이드를 덮고,
 * 출근시각(번들 시각표)·공휴일·절기를 병합한다.
 */
class GetMonthScheduleUseCase @Inject constructor(
    private val userRepo: UserRepository,
    private val patternRepo: PatternRepository,
    private val scheduleRepo: ScheduleRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(month: YearMonth): Flow<List<DaySchedule>> =
        userRepo.observeMe().flatMapLatest { user ->
            if (user == null) return@flatMapLatest flowOf(emptyList())
            val patternFlow = user.patternId?.let { patternRepo.observePattern(it) } ?: flowOf(null)
            combine(
                patternFlow,
                scheduleRepo.observeOverrides(user.uid, month),
            ) { pattern, overrides ->
                val byDate = overrides.associateBy { it.date }
                (1..month.lengthOfMonth()).map { d ->
                    val date = month.atDay(d)
                    val ov = byDate[date]
                    val patternDuty = pattern?.dutyOn(date, user.patternOffset) ?: DutyCode.parse(null)
                    val isChanged = ov != null && ov.source != Schedule.Source.PATTERN && ov.dutyRaw.isNotBlank()
                    val duty = if (isChanged) ov!!.duty else patternDuty
                    DaySchedule(
                        date = date,
                        duty = duty,
                        memo = ov?.memo.orEmpty(),
                        isOverridden = isChanged,
                        originalDutyRaw = if (isChanged) patternDuty.raw else null,
                        signOn = Bundled.signOn(duty, date),
                        holidayName = Bundled.PUBLIC_HOLIDAYS[date],
                        memorialName = Bundled.MEMORIAL_DAYS[date],
                        seasonalTerm = Bundled.SEASONAL_TERMS[date],
                    )
                }
            }
        }
}
