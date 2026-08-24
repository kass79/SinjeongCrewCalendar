package com.sinjeong.crewcalendar.domain.usecase

import com.sinjeong.crewcalendar.domain.model.*
import com.sinjeong.crewcalendar.domain.repository.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import javax.inject.Inject

/**
 * 한 달 근무 계산: 패턴 순환값 위에 schedules 오버라이드를 덮고,
 * 출근시각(번들 시각표)·공휴일·절기를 병합한다.
 *
 * 월별 기록 보존: 지난 달은 처음 본 시점에 스냅샷으로 동결 —
 * 이후 근무선택(패턴 위치)이 바뀌어도 과거 월 기록은 변하지 않는다.
 */
class GetMonthScheduleUseCase @Inject constructor(
    private val userRepo: UserRepository,
    private val patternRepo: PatternRepository,
    private val scheduleRepo: ScheduleRepository,
    private val snapshotRepo: SnapshotRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(month: YearMonth): Flow<List<DaySchedule>> =
        userRepo.observeMe().flatMapLatest { user ->
            if (user == null) return@flatMapLatest flowOf(emptyList())
            val patternFlow = user.patternId?.let { patternRepo.observePattern(it) } ?: flowOf(null)
            combine(
                patternFlow,
                scheduleRepo.observeOverrides(user.uid, month),
            ) { pattern, overrides -> Triple(user, pattern, overrides) }
                .map { (u, pattern, overrides) ->
                    val isPast = month < YearMonth.now()
                    var snapshot = if (isPast) snapshotRepo.load(u.uid, month) else null

                    // 교번 변경 구간(v1.6.63): 날짜마다 그 날 유효한 구간의 패턴·오프셋으로 계산한다.
                    // 구간이 없으면(옛 형식 · `바로 적용`) 종전 그대로 pattern/patternOffset 한 벌 —
                    // 기존 사용자의 달력이 한 칸도 달라지지 않는다.
                    //
                    // ⚠ 달력·위젯·브리핑·편승 알람·공유 이미지가 **전부 이 UseCase 하나**로 근무를 뽑는다
                    //   (`DutyWidgetWorker`·`Briefing`·`DutyNotifyWorker`가 이걸 주입받는다).
                    //   여기 한 곳만 구간을 존중하면 계산 경로 전체가 따라온다.
                    val byId = if (u.patternSegments.isEmpty()) emptyMap()
                    else patternRepo.getPatterns().associateBy { it.id }

                    val byDate = overrides.associateBy { it.date }
                    val days = (1..month.lengthOfMonth()).map { d ->
                        val date = month.atDay(d)
                        val ov = byDate[date]
                        val fromPattern =
                            if (u.patternSegments.isEmpty()) pattern?.dutyOn(date, u.patternOffset)
                            else u.segmentOn(date).let { s -> byId[s.patternId]?.dutyOn(date, s.patternOffset) }
                        val patternDuty = snapshot?.get(date)?.let { DutyCode.parse(it) }
                            ?: fromPattern
                            ?: DutyCode.parse(null)
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

                    // 지난 달 최초 계산 시 동결 저장 (오버라이드 반영된 최종값 기준)
                    if (isPast && snapshot == null && pattern != null) {
                        snapshotRepo.save(u.uid, month, days.associate { it.date to it.duty.raw })
                    }
                    days
                }
        }
}
