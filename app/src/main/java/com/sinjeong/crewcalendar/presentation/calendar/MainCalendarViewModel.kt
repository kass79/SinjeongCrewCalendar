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
import javax.inject.Inject

/** 근무선택 피커 상태: 1단계(소속) → 2단계(근무 그리드) */
data class DutyPickerState(
    val date: LocalDate,
    /** null이면 1단계(소속 선택) */
    val group: CrewGroup? = null,
)

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

    /**
     * 달력 위 **오늘 카드** 전용 근무 목록. 보고 있는 달([days])과 따로 두는 이유는 두 가지다:
     *  · 다른 달을 넘겨봐도 카드는 계속 오늘을 보여야 한다(카드가 사라지면 달력 칸 높이가 튄다).
     *  · 오늘 출근시각을 지나면 카드가 **내일**로 넘어가는데, 31일이면 내일이 다음 달이다
     *    → 두 달치를 합쳐 둔다(동료 탭 `monthOverrides`와 같은 이유·같은 방식).
     */
    val cardDays: StateFlow<List<DaySchedule>> = YearMonth.now().let { m ->
        combine(getMonthSchedule(m), getMonthSchedule(m.plusMonths(1))) { a, b -> a + b }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    /** 기준 날짜의 근무를 해당 소속 패턴의 patternIndex 칸으로 지정 → 전체 자동 재계산 */
    fun confirmDutyPosition(group: CrewGroup, patternIndex: Int) {
        val p = picker.value ?: return
        viewModelScope.launch {
            runCatching { selectDutyPosition(p.date, group, patternIndex) }
                .onFailure { error.value = it.message ?: "근무선택 실패" }
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
