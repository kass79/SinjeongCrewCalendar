package com.sinjeong.crewcalendar.domain.usecase

import com.sinjeong.crewcalendar.domain.model.Bundled
import com.sinjeong.crewcalendar.domain.model.DaySchedule
import com.sinjeong.crewcalendar.domain.model.DutyCode
import com.sinjeong.crewcalendar.domain.model.NightCombo
import java.time.LocalDate
import javax.inject.Inject

data class TodayDuty(
    val date: LocalDate,
    val duty: DutyCode,
    /** 출근시각 표시 문자열 */
    val signOn: String?,
    /** 시각 상세 (전반/후반사업 포함) */
    val timeRow: Bundled.TimeRow?,
    /** 야간이면 당일→익일 조합 */
    val nightCombo: NightCombo?,
    val memo: String,
)

/** 오늘(또는 임의 날짜) 근무 + 시각 조회. 위젯/메인 요약 카드 공용 */
class GetTodayDutyUseCase @Inject constructor() {
    operator fun invoke(day: DaySchedule): TodayDuty {
        val row = Bundled.timeRowFor(day.duty, day.date)
        val combo = if (day.duty.isNight) Bundled.comboOf(day.date) else null
        return TodayDuty(day.date, day.duty, row?.signOn, row, combo, day.memo)
    }
}
