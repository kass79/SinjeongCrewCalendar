package com.sinjeong.crewcalendar.domain.usecase

import com.sinjeong.crewcalendar.domain.model.DaySchedule
import com.sinjeong.crewcalendar.domain.model.Schedule
import com.sinjeong.crewcalendar.domain.repository.ScheduleRepository
import com.sinjeong.crewcalendar.domain.repository.UserRepository
import javax.inject.Inject

/**
 * 하루 단위 수정: 근무변경(패턴은 유지, 그 날만 오버라이드) + 메모.
 * 오버라이드 문서 하나가 dutyRaw(빈 문자열이면 패턴값 사용)와 memo를 함께 가진다.
 */
class UpdateDayUseCase @Inject constructor(
    private val userRepo: UserRepository,
    private val scheduleRepo: ScheduleRepository,
) {
    /** 근무변경. [newCode]가 null이면 "변경없음(원래 근무)" = 패턴값으로 되돌리기 */
    suspend fun changeDuty(day: DaySchedule, newCode: String?) {
        val uid = userRepo.currentUid ?: error("로그인이 필요합니다")
        if (newCode == null) {
            if (day.memo.isBlank()) scheduleRepo.deleteOverride(uid, day.date)
            else scheduleRepo.saveOverride(baseDoc(uid, day).copy(dutyRaw = "", source = Schedule.Source.PATTERN, originalDutyRaw = null))
            return
        }
        scheduleRepo.saveOverride(
            baseDoc(uid, day).copy(
                dutyRaw = newCode,
                source = Schedule.Source.MANUAL,
                originalDutyRaw = day.originalDutyRaw ?: day.duty.raw,
            )
        )
    }

    /** 메모 저장 (근무변경 상태는 그대로 유지) */
    suspend fun saveMemo(day: DaySchedule, memo: String) {
        val uid = userRepo.currentUid ?: error("로그인이 필요합니다")
        if (memo.isBlank() && !day.isOverridden) {
            scheduleRepo.deleteOverride(uid, day.date)
            return
        }
        scheduleRepo.saveOverride(baseDoc(uid, day).copy(memo = memo))
    }

    private fun baseDoc(uid: String, day: DaySchedule) = Schedule(
        id = "${uid}_${day.date}",
        uid = uid,
        date = day.date,
        dutyRaw = if (day.isOverridden) day.duty.raw else "",
        memo = day.memo,
        source = if (day.isOverridden) Schedule.Source.MANUAL else Schedule.Source.PATTERN,
        originalDutyRaw = day.originalDutyRaw,
        updatedAtEpochMs = System.currentTimeMillis(),
    )
}
