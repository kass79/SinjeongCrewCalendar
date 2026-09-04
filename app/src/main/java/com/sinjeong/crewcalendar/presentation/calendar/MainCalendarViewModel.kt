package com.sinjeong.crewcalendar.presentation.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sinjeong.crewcalendar.domain.model.Bundled
import com.sinjeong.crewcalendar.domain.model.CrewGroup
import com.sinjeong.crewcalendar.domain.model.CrewRole
import com.sinjeong.crewcalendar.domain.model.DaySchedule
import com.sinjeong.crewcalendar.domain.model.Notice
import com.sinjeong.crewcalendar.domain.model.countsAsRestDay
import com.sinjeong.crewcalendar.domain.model.User
import com.sinjeong.crewcalendar.domain.model.WeeklyMenu
import com.sinjeong.crewcalendar.domain.model.weekStartOf
import com.sinjeong.crewcalendar.domain.repository.MenuRepository
import com.sinjeong.crewcalendar.domain.repository.NoticeRepository
import com.sinjeong.crewcalendar.domain.repository.UserRepository
import com.sinjeong.crewcalendar.domain.usecase.GetMonthScheduleUseCase
import com.sinjeong.crewcalendar.domain.usecase.SelectDutyPositionUseCase
import com.sinjeong.crewcalendar.domain.usecase.UpdateDayUseCase
import com.sinjeong.crewcalendar.domain.usecase.WeeklyHours
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

/** `9월 1일` — 달을 흘려 읽으면 한 칸이 어긋나는 자리라 `9/1`을 쓰지 않는다(v1.6.63) */
internal val MD: DateTimeFormatter = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN)

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
    /** `근무 저장`으로 확정해 둔 마지막 날 (v1.6.69). null이면 저장한 적 없음 */
    val frozenUntil: LocalDate? = null,
) {
    val nextMonthFirst: LocalDate get() = today.withDayOfMonth(1).plusMonths(1)

    /** 근무선택이 손댈 수 있는 가장 이른 날 = 저장해 둔 날의 다음 날 */
    val thawFrom: LocalDate? get() = frozenUntil?.plusDays(1)

    /**
     * 구간 시작일. `null`이면 "처음부터"(= `바로 적용`, 옛 형식으로 저장).
     *
     * 저장은 **바닥만 올린다** — 종전 두 선택지를 그대로 두되 저장한 날보다 앞설 수 없게 한다.
     * 저장이 없으면 v1.6.63과 한 글자도 다르지 않다(`null` / [nextMonthFirst]).
     */
    val applyFrom: LocalDate? get() = when {
        scheduled -> maxOf(nextMonthFirst, thawFrom ?: nextMonthFirst)
        else -> thawFrom
    }

    /**
     * 두 선택지가 같은 날로 붙었는가 = 고를 것이 없다 → 버튼 줄 대신 안내문 한 줄.
     * `8월 31일까지 저장`처럼 **달 경계에서 저장한 보통의 경우**가 전부 여기 걸린다.
     */
    val applyChoiceFixed: Boolean get() = thawFrom?.let { it >= nextMonthFirst } == true

    /**
     * 그리드·A~D조 카드가 근무를 계산하는 **기준 날짜** — 사용자가 새 교번표에서 읽어 고르는 날.
     * 시작일이 과거면(저장이 지난 날짜에 걸린 경우) 오늘을 기준으로 고른다.
     */
    val date: LocalDate get() = applyFrom?.coerceAtLeast(today) ?: today
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
    /** 이달 휴일 갯수 (앱바 칩) — 충당으로 나가도 안 줄고 **지근으로 바꿀 때만** 준다([countsAsRestDay]) */
    val restDayCount: Int get() = days.count { it.countsAsRestDay }

    /**
     * 다 불러왔는데 한 칸도 없다 = 그릴 것이 없다(로그인 전·근무 미선택).
     * 종전엔 이 상태가 [isLoading]과 구분되지 않아 **빠져나올 수 없는 로딩 원**이 됐다(v1.6.92 ③).
     */
    val isEmpty: Boolean get() = !isLoading && days.isEmpty()

    /** 이 달이 공휴일표 밖이면 신정·설날·추석이 조용히 평일로 계산된다 → 화면이 말하게 한다(v1.6.92 ①) */
    val holidayTableMissing: Boolean get() = !Bundled.holidayTableCovers(month)

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
    menuRepo: MenuRepository,
    noticeRepo: NoticeRepository,
    val themeController: ThemeController,
) : ViewModel() {

    /**
     * 구내식당 주간식단표 (v1.6.80) — 주 시작일(월) → 한 주치.
     *
     * `uiState`의 `combine`에 끼워 넣지 않고 따로 둔다. 그 combine은 7갈래를 배열로 받아
     * 인덱스로 꺼내는 자리라 한 갈래만 늘려도 **전부 한 칸씩 밀 위험**이 있고, 식단표는
     * 달력 계산과 아무 관계가 없다.
     *
     * **질의 하한이 이번 주 월요일**이라 지난 주 문서는 애초에 앱까지 오지도 않는다 —
     * "지난주 메뉴를 보여주면 절대 안 된다"를 화면 로직이 아니라 구조로 막는다.
     * (자정을 넘겨 앱이 켜져 있으면 하한이 한 주 낡을 수 있어, 화면도 열릴 때마다
     *  `weekStartOf(오늘)`을 다시 구해 그 키로만 꺼낸다.)
     *
     * ## 왜 `Result?` 인가 (v1.6.93)
     *
     * 종전엔 초기값도 `emptyMap()`, 오류 폴백도 `emptyMap()` 이라 화면이 **셋을 구분할 수 없었다**:
     * ① 아직 못 받음 ② 오프라인·조회 실패 ③ 정말 이번 주 표가 안 올라옴. 그래서 비행기 모드에서도
     * `이번 주 식단표가 아직 없어요` 라고 **사실이 아닌 말**을 했다(관리자에겐 "올려라"는 뜻이 된다).
     * `null` = 미수신 · `failure` = 오프라인 · `success` = 받음 셋으로 갈라 화면이 각각 다르게 말한다.
     * 표준 [Result] 라 새 타입을 만들지 않았다.
     */
    val menus: StateFlow<Result<Map<LocalDate, WeeklyMenu>>?> =
        menuRepo.observeFrom(weekStartOf(LocalDate.now()))
            .map { m -> Result.success(m.mapValues { (start, cells) -> WeeklyMenu(start, cells) }) }
            .catch { emit(Result.failure(it)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * 관리자 공지 (v1.6.89) — 기간 안의 것만, 최신순. 달력 맨 위 배너가 그중 1건을 그린다.
     *
     * 식단표와 같은 이유로 `uiState` 의 combine 에 안 끼운다 — 달력 계산과 아무 관계가 없고,
     * 7갈래 배열을 인덱스로 꺼내는 자리라 한 갈래만 늘려도 전부 한 칸씩 밀 위험이 있다.
     */
    val notices: StateFlow<List<Notice>> =
        noticeRepo.observeActive(LocalDate.now())
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val month = MutableStateFlow(YearMonth.now())
    private val selectedDate = MutableStateFlow<LocalDate?>(null)
    private val picker = MutableStateFlow<DutyPickerState?>(null)
    private val changeDate = MutableStateFlow<LocalDate?>(null)
    private val error = MutableStateFlow<String?>(null)

    /**
     * 한 달치 로드 결과에 **대상 달을 실어** 보낸다 (v1.6.92 ②).
     *
     * `flatMapLatest{}.stateIn()`은 달을 넘기는 순간 **옛 달 값을 그대로 들고 있는다** —
     * 그런데 격자는 이미 새 `month`로 그려지므로 9월 격자에 8월 근무가 앉는다(로드가 실패하면
     * 계속 남는다). 값에 달을 붙여 `month`와 같을 때만 그리게 하면 그 구간이 구조적으로 사라진다.
     */
    private data class MonthDays(val month: YearMonth, val days: List<DaySchedule>)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val days: StateFlow<MonthDays?> = month
        .flatMapLatest { m ->
            getMonthSchedule(m)
                .map { MonthDays(m, it) }
                .catch { e ->
                    error.value = e.message ?: "근무표를 불러오지 못했습니다"
                    emit(MonthDays(m, emptyList()))
                }
        }
        // null = **아직 한 번도 안 왔다**. 로딩을 "목록이 비었나"로 파생시키면 사용자가 없을 때
        // (빈 목록 + 에러 없음) 영영 참이라 로딩 원에서 못 빠져나온다(v1.6.92 ③).
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * 주52시간 주별 근무시간 — **메모 시트가 열려 있는 동안에만** 흐른다(`WhileSubscribed(0)`).
     *
     * 달 경계 주(월~일이 두 달에 걸친 주)를 그 달 안에서만 세면 52시간을 넘겨도 초과가 안 뜬다
     * (v1.6.92 ⑦). 그래서 앞뒤 달을 같이 읽어 **주 전체**를 합산한다 — 표시 범위는 그 달 기준
     * 그대로다. 시간을 하나도 계산할 수 없는 소속(통상근무·4조2교대의 낱말 근무)은 빈 목록 =
     * 화면이 줄 자체를 감춘다(v1.6.92 ⑥ — 0.0h를 사실처럼 보여 주는 게 더 나쁘다).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val weeklyHours: StateFlow<List<WeeklyHours.Week>> = month
        .flatMapLatest { m ->
            combine(
                getMonthSchedule(m.minusMonths(1)).onStart { emit(emptyList()) },
                getMonthSchedule(m),
                getMonthSchedule(m.plusMonths(1)).onStart { emit(emptyList()) },
            ) { prev, cur, next ->
                if (WeeklyHours.computable(cur)) WeeklyHours.compute(m, prev + cur + next) else emptyList()
            }
        }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), emptyList())

    // 달력 위 오늘 카드 전용 `cardDays`(오늘·다음달 두 달치 합본)는 카드와 함께 v1.6.45에서 제거.
    // 되살리려면 `git show da7cacf`.

    val uiState: StateFlow<CalendarUiState> = combine(
        month, days, userRepo.observeMe(), selectedDate, picker, changeDate, error,
    ) { arr ->
        @Suppress("UNCHECKED_CAST")
        val m = arr[0] as YearMonth
        // **보고 있는 달의 것일 때만** 그린다. 전환 중(옛 달 값)엔 빈 격자 + 로딩 표시.
        val loaded = (arr[1] as MonthDays?)?.takeIf { it.month == m }
        CalendarUiState(
            month = m,
            days = loaded?.days.orEmpty(),
            user = arr[2] as User?,
            selectedDate = arr[3] as LocalDate?,
            picker = arr[4] as DutyPickerState?,
            changeDate = arr[5] as LocalDate?,
            isLoading = loaded == null,
            error = arr[6] as String?,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    fun moveMonth(delta: Long) { month.update { it.plusMonths(delta) } }
    fun goToday() { month.value = YearMonth.now(); selectedDate.value = null }
    fun selectDate(date: LocalDate?) { selectedDate.value = date }

    // ── 근무선택 (핵심): ① 소속 → ② 근무 ─────────────────
    fun openDutyPicker(date: LocalDate = LocalDate.now()) {
        selectedDate.value = null
        // 저장해 둔 날이 있으면 피커가 열리는 순간부터 시작일 바닥이 그 다음 날로 잡힌다
        picker.value = DutyPickerState(date, frozenUntil = uiState.value.user?.frozenUntil)
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
            runCatching { selectDutyPosition(p.date, group, patternIndex, p.applyFrom) }
                .onFailure { error.value = it.message ?: "근무선택 실패" }
                // 예약은 그 날이 오기 전엔 화면이 하나도 안 바뀐다 → 됐는지 알 길이 없다.
                // 스낵바로 한 번 알리고, 상시 확인·취소는 설정 화면에 둔다.
                .onSuccess {
                    p.applyFrom?.let { from ->
                        error.value = "${from.format(MD)}부터 ${group.label}로 바뀝니다 · 취소는 설정 › 근무 패턴"
                    }
                }
            picker.value = null
        }
    }

    /**
     * **근무 저장**(v1.6.69) — [until]까지의 근무를 지금 값으로 확정한다.
     *
     * 저장하는 것은 **날짜 하나**뿐이다(`User.frozenUntil`). 날짜별로 근무를 복사하지 않으므로
     * 저장 직후 달력에 보이는 근무는 한 칸도 달라지지 않고, 바탕색만 연녹색이 된다.
     * 이후 `근무선택`은 [until] 다음 날부터만 적용된다(`DutyPickerState.applyFrom`).
     */
    fun freezeDuties(until: LocalDate) {
        val u = uiState.value.user ?: return
        viewModelScope.launch {
            runCatching { userRepo.upsert(u.copy(frozenUntil = until)) }
                .onFailure { error.value = "근무 저장 실패: ${it.message}" }
                .onSuccess {
                    error.value = "${until.withDayOfMonth(1).format(MD)} ~ ${until.dayOfMonth}일 " +
                        "근무를 저장했습니다 · 해제는 설정 › 내 정보"
                }
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
