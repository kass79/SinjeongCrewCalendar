package com.sinjeong.crewcalendar.presentation.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sinjeong.crewcalendar.domain.model.Bundled
import com.sinjeong.crewcalendar.domain.model.CrewGroup
import com.sinjeong.crewcalendar.domain.model.CrewRole
import com.sinjeong.crewcalendar.domain.model.DaySchedule
import com.sinjeong.crewcalendar.domain.model.User
import com.sinjeong.crewcalendar.domain.repository.UserRepository
import com.sinjeong.crewcalendar.domain.usecase.GetMonthScheduleUseCase
import com.sinjeong.crewcalendar.domain.usecase.SelectDutyPositionUseCase
import com.sinjeong.crewcalendar.domain.usecase.UpdateDayUseCase
import com.sinjeong.crewcalendar.presentation.theme.ThemeController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/**
 * 근무선택 피커 상태: 1단계(소속) → 2단계(근무 그리드).
 *
 * v1.6.63에서 **적용 시작일**이 붙었다. 새 단계를 만들지 않고 2단계 안에 버튼 두 개로 넣은 이유:
 * 고르는 값이 `그리드가 기준으로 삼는 날짜` 하나뿐이라 화면을 늘릴 값어치가 없고,
 * 버튼을 누르는 즉시 바로 위 안내문이 `9월 1일 내 근무를 고르세요`로 바뀌어
 * **어느 날 기준인지**가 고를 때 눈앞에 있다(여기가 이 기능에서 제일 틀리기 쉬운 자리다).
 */
data class DutyPickerState(
    /** 피커를 연 날 (보통 오늘) */
    val today: LocalDate,
    /** null이면 1단계(소속 선택) */
    val group: CrewGroup? = null,
    /** true면 [nextMonthFirst]부터만 적용, false면 지금 교번 자체를 교체(종전 동작) */
    val scheduled: Boolean = false,
) {
    val nextMonthFirst: LocalDate get() = today.withDayOfMonth(1).plusMonths(1)

    /** 적용 시작일 = 그리드·A~D조 카드가 근무를 계산하는 기준 날짜 */
    val date: LocalDate get() = if (scheduled) nextMonthFirst else today
}

data class CalendarUiState(
    val month: YearMonth = YearMonth.now(),
    val days: List<DaySchedule> = emptyList(),
    val user: User? = null,
    val selectedDate: LocalDate? = null,
    val picker: DutyPickerState? = null,
    /** 근무변경 시트가 열려 있는 날짜 */
    val changeDate: LocalDate? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
) {
    /** 이달 휴일 갯수 (앱바 칩) */
    val restDayCount: Int get() = days.count { it.duty.isRest }

    /** 현재 소속 (근무선택 1단계 기본 표시용) */
    val currentGroup: CrewGroup?
        get() = when {
            user?.patternId == Bundled.MAIN_PATTERN.id ->
                if (user.role == CrewRole.CONDUCTOR) CrewGroup.MAIN_CONDUCTOR else CrewGroup.MAIN_DRIVER
            else -> Bundled.groupFor(user?.patternId)
        }
}

@HiltViewModel
class MainCalendarViewModel @Inject constructor(
    private val getMonthSchedule: GetMonthScheduleUseCase,
    private val selectDutyPosition: SelectDutyPositionUseCase,
    private val updateDay: UpdateDayUseCase,
    private val userRepo: UserRepository,
    val themeController: ThemeController,
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.now())
    private val selectedDate = MutableStateFlow<LocalDate?>(null)
    private val picker = MutableStateFlow<DutyPickerState?>(null)
    private val changeDate = MutableStateFlow<LocalDate?>(null)
    private val error = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val days: StateFlow<List<DaySchedule>> = month
        .flatMapLatest { m ->
            getMonthSchedule(m).catch { e ->
                error.value = e.message ?: "근무표를 불러오지 못했습니다"
                emit(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // 달력 위 오늘 카드 전용 `cardDays`(오늘·다음달 두 달치 합본)는 카드와 함께 v1.6.45에서 제거.
    // 되살리려면 `git show da7cacf`.

    val uiState: StateFlow<CalendarUiState> = combine(
        month, days, userRepo.observeMe(), selectedDate, picker, changeDate, error,
    ) { arr ->
        @Suppress("UNCHECKED_CAST")
        CalendarUiState(
            month = arr[0] as YearMonth,
            days = arr[1] as List<DaySchedule>,
            user = arr[2] as User?,
            selectedDate = arr[3] as LocalDate?,
            picker = arr[4] as DutyPickerState?,
            changeDate = arr[5] as LocalDate?,
            isLoading = (arr[1] as List<*>).isEmpty() && arr[6] == null,
            error = arr[6] as String?,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    fun moveMonth(delta: Long) { month.update { it.plusMonths(delta) } }
    fun goToday() { month.value = YearMonth.now(); selectedDate.value = null }
    fun selectDate(date: LocalDate?) { selectedDate.value = date }

    // ── 근무선택 (핵심): ① 소속 → ② 근무 ─────────────────
    fun openDutyPicker(date: LocalDate = LocalDate.now()) {
        selectedDate.value = null
        picker.value = DutyPickerState(date)
    }

    fun pickGroup(group: CrewGroup) { picker.update { it?.copy(group = group) } }
    fun backToGroupStep() { picker.update { it?.copy(group = null) } }
    fun closeDutyPicker() { picker.value = null }
    fun setPickerScheduled(scheduled: Boolean) { picker.update { it?.copy(scheduled = scheduled) } }

    /**
     * 적용 시작일의 근무를 해당 소속 패턴의 patternIndex 칸으로 지정.
     * `바로 적용`이면 종전대로 전체 재계산, `다음 달 1일부터`면 그 날부터만 새 교번이다.
     */
    fun confirmDutyPosition(group: CrewGroup, patternIndex: Int) {
        val p = picker.value ?: return
        viewModelScope.launch {
            runCatching { selectDutyPosition(p.date, group, patternIndex, p.scheduled) }
                .onFailure { error.value = it.message ?: "근무선택 실패" }
                // 예약은 그 날이 오기 전엔 화면이 하나도 안 바뀐다 → 됐는지 알 길이 없다.
                // 스낵바로 한 번 알리고, 상시 확인·취소는 설정 화면에 둔다.
                .onSuccess {
                    if (p.scheduled) error.value =
                        "${p.date.format(DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN))}부터 " +
                            "${group.label}로 바뀝니다 · 취소는 설정 › 근무 패턴"
                }
            picker.value = null
        }
    }

    // ── 근무변경 (하루만, 패턴 유지) ──────────────────────
    fun openDutyChange(date: LocalDate) {
        selectedDate.value = null
        changeDate.value = date
    }

    fun closeDutyChange() { changeDate.value = null }

    /** [newCode] null = 변경없음(원래 근무로 되돌리기) */
    fun changeDuty(date: LocalDate, newCode: String?) {
        val day = uiState.value.days.firstOrNull { it.date == date } ?: return
        viewModelScope.launch {
            runCatching { updateDay.changeDuty(day, newCode) }
                .onFailure { error.value = "근무 수정 실패: ${it.message}" }
            changeDate.value = null
        }
    }

    fun saveMemo(date: LocalDate, memo: String) {
        val day = uiState.value.days.firstOrNull { it.date == date } ?: return
        viewModelScope.launch {
            runCatching { updateDay.saveMemo(day, memo) }
                .onFailure { error.value = "메모 저장 실패: ${it.message}" }
        }
    }

    /** 우상단 달 아이콘: 다크/라이트 전환 */
    fun toggleTheme(currentlyDark: Boolean) = themeController.toggle(currentlyDark)

    fun dismissError() { error.value = null }
}
