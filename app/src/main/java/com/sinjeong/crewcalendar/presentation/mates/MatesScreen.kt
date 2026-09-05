package com.sinjeong.crewcalendar.presentation.mates

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sinjeong.crewcalendar.domain.model.*
import com.sinjeong.crewcalendar.domain.repository.MateRepository
import com.sinjeong.crewcalendar.domain.repository.RosterEntry
import com.sinjeong.crewcalendar.domain.repository.RosterRepository
import com.sinjeong.crewcalendar.domain.repository.UserRepository
import com.sinjeong.crewcalendar.presentation.calendar.RouteImageDialog
import com.sinjeong.crewcalendar.presentation.roster.*
import com.sinjeong.crewcalendar.presentation.theme.LocalDutyColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class MatesViewModel @Inject constructor(
    private val mateRepo: MateRepository,
    userRepo: UserRepository,
    private val rosterRepo: RosterRepository,
) : ViewModel() {
    /**
     * 보고 있는 구간 = **오늘부터 한 달**을 0으로 두고 한 달씩 민 값(v1.6.42 ⑦).
     * v1.6.39에서 월 이동 화살표를 없앴는데 사용자가 다시 요청했다:
     * *"한달보여주는건 좋은데 다음달 넘어가는 기능이 없네?"*
     * 달(月) 단위가 아니라 **구간** 단위다 — 1이면 9/21~10/20처럼 오늘 날짜를 유지한 채 밀린다.
     * 0 아래로는 안 간다(*"과거는 필요 없다"* — 지난 근무는 달력 탭에서 본다).
     * 위로도 [MAX_PERIOD](1년)에서 막힌다(v1.6.61) — 그 근거는 상수 주석에 있다.
     */
    private val _period = MutableStateFlow(0)
    val period: StateFlow<Int> = _period.asStateFlow()

    fun movePeriod(delta: Int) { _period.update { (it + delta).coerceIn(0, MAX_PERIOD) } }
    val mates: StateFlow<List<Mate>> = mateRepo.observeMates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val user: StateFlow<User?> = userRepo.observeMe()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Firebase 연동 시: 로그인 근무자 실데이터 (없으면 빈 목록 → 내장 명단만) */
    val liveUsers: StateFlow<List<RosterEntry>> = rosterRepo.observeUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 근무변경 실시간 반영 (Firebase 미연동이면 빈 맵).
     *
     * 표시 범위가 **오늘부터 한 달**이라 달을 두 개 걸친다(v1.6.39). `observeMonthOverrides`는
     * 월 단위라 이번 달·다음 달 두 벌을 받아 uid별로 합쳐야 한다 — 한 달치만 보면
     * 경계 너머(다음 달 초)의 근무변경이 통째로 빠져 **원래 교번**이 그려진다. 조용히 틀리는 자리다.
     *
     * 달이 바뀌는 순간은 신경 쓰지 않는다. 화면의 `today`도 진입 시점에 고정되므로
     * 자정을 넘겨 앱을 계속 켜 두면 둘이 같이 하루 낡을 뿐 서로 어긋나지는 않는다.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val monthOverrides: StateFlow<Map<String, Map<LocalDate, String>>> = _period.flatMapLatest { p ->
        // 구간은 길이가 정확히 한 달이라 걸치는 달은 언제나 두 개다(시작 달 + 그 다음 달)
        val ym = YearMonth.now().plusMonths(p.toLong())
        combine(
            rosterRepo.observeMonthOverrides(ym),
            rosterRepo.observeMonthOverrides(ym.plusMonths(1)),
        ) { thisMonth, nextMonth ->
            (thisMonth.keys + nextMonth.keys).associateWith {
                (thisMonth[it] ?: emptyMap()) + (nextMonth[it] ?: emptyMap())
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** 등록: 오늘 근무 위치(patternIndex)로 offset 계산 — 근무선택과 같은 원리 */
    fun addMate(name: String, group: CrewGroup, todayPatternIndex: Int) {
        val pattern = Bundled.patternFor(group)
        val days = ChronoUnit.DAYS.between(pattern.anchorDate, LocalDate.now()).toInt()
        val offset = Math.floorMod(todayPatternIndex - days, pattern.length)
        viewModelScope.launch { mateRepo.upsert(Mate(name.trim(), group, offset)) }
    }

    /**
     * 수정: 저장 키가 "이름|소속"이라 둘 중 하나라도 바뀌면 **옛 키를 먼저 지워야** 유령 행이 안 남는다.
     * ★즐겨찾기 그룹은 그대로 옮긴다 — 이름 오타를 고쳤다고 ★이 풀리면 안 된다.
     */
    fun editMate(old: Mate, name: String, group: CrewGroup, todayPatternIndex: Int) {
        val offset = Bundled.patternFor(group).offsetFor(LocalDate.now(), todayPatternIndex)
        viewModelScope.launch {
            if (old.name != name.trim() || old.group != group) mateRepo.remove(old)
            mateRepo.upsert(Mate(name.trim(), group, offset, old.favGroup))
        }
    }

    /**
     * 즐겨찾기 지정/해제. 동료로 등록 안 된 사람(내장 명단·로그인 근무자·본인)은 이 시점에 Mate로 만든다.
     * 해제는 favGroup=null — Mate는 지우지 않는다(수동 등록분 보호).
     * 매칭은 **이름+소속** — 이름만으로 찾으면 동명이인(김지환 기관사/차장)이 서로를 덮어쓴다.
     */
    fun setFav(name: String, group: CrewGroup, offset: Int, fav: FavGroup?) {
        val existing = mates.value.find { it.name == name && it.group == group }
        viewModelScope.launch {
            mateRepo.upsert(existing?.copy(favGroup = fav) ?: Mate(name, group, offset, fav))
        }
    }

    fun remove(mate: Mate) {
        viewModelScope.launch { mateRepo.remove(mate) }
    }

    companion object {
        /**
         * 앞으로 갈 수 있는 마지막 구간 = **1년 뒤**(v1.6.61).
         *
         * v1.6.60까지 [movePeriod]는 아래로만 막혀 있었다(`coerceAtLeast(0)`) — `‹`는
         * `enabled = period > 0`으로 꺼지는데 `›`는 무제한이라, 몇십 초만 눌러도 10년 뒤로 간다.
         * 실측(에뮬레이터에서 초당 3~25회로 900회 넘게 연타): 크래시·ANR·메모리 누수는 **없었지만**
         * 헤더가 `10/24 ~ 11/23`처럼 **연도를 안 적어서 몇 년 뒤인지 알 길이 없었다.**
         * 왼쪽에만 한계가 있는 건 그 자체로 비대칭 결함이라 어차피 막아야 한다.
         *
         * **1년으로 정한 근거 — 그 너머는 앱이 책임질 수 없는 값이다:**
         *  · 교번표·명단은 해마다 개정된다(v1.6.54·57의 `4·7 개정 행로표`가 그 예).
         *  · 승무원이 실제로 확인하는 범위(다음 몇 달)를 한참 넘는다.
         * 상한에 닿으면 `›`가 `‹`와 같은 방식으로 **꺼진다** — 눌러도 안 움직이는 대신 못 누른다.
         *
         * ⚠ **이 상수만으로는 공휴일표 밖을 못 막는다**(v1.7.7 A4). 통상근무(`restOnHolidays`)는
         * 공휴일을 휴무로 덮으므로 [Bundled.PUBLIC_HOLIDAYS] 밖의 해로 넘어가면 설날·추석이
         * **근무일로 그려진다** — 순환 교번은 멀쩡히 계산되니 화면은 정상으로 보이고 값만 틀리는,
         * 조용히 틀리는 자리다. v1.6.61에 12를 고를 때는 표가 2026년치뿐이라 12구간이 이미 표
         * 밖이었는데, v1.6.92에서 표가 2027년까지 늘어 **지금(2026-09) 기준으로는 12구간도 표 안**
         * 이다. 어느 쪽이든 상수 하나로 맞출 수 없는 값이라(오늘 날짜가 흐르면 경계가 움직인다)
         * `›`는 **다음 구간의 마지막 날을 [Bundled.holidayTableCovers]에 직접 물어보고** 막는다.
         */
        const val MAX_PERIOD = 12
    }
}

/**
 * 소속 칩 배치 — **`전체` 하나가 세로로 서고, 오른쪽에 2행 3열 격자**(v1.6.60 사용자 지정).
 *
 *     ┌──────┬─────────────┬───────────┬────────────┐
 *     │      │ 본선 기관사  │ 본선 차장 │ ★즐겨찾기  │
 *     │ 전체 ├─────────────┼───────────┼────────────┤
 *     │      │  신정지선   │ 통상근무  │  4조2교대  │
 *     └──────┴─────────────┴───────────┴────────────┘
 *
 * 사용자 원문: *"전체 탭을 세로로 하고 기관사 차장 즐겨찾기 / 신정지선 통상근무 4조2교대"*.
 * `전체`는 필터가 아니라 **필터 해제**라 나머지 여섯과 성격이 다르다 — 종전엔 첫 줄 맨 앞에
 * 끼워 넣어 그 차이가 안 보였는데, 세로 한 칸으로 빼니 배치만으로 구분된다.
 * 높이는 `IntrinsicSize.Min` + `fillMaxHeight`로 **격자 두 행 + 행간을 그대로 따라간다** —
 * 못 박은 dp가 아니라서 칩 글자·행 높이를 바꿔도 저절로 맞는다.
 *
 * v1.6.54가 `4조2교대`를 `운용`·`관제`로 갈라 칩이 8개였는데, 사용자가 써 보고
 * *"운용/관제 탭을 그냥 4조2교대 탭으로 하나로"*라고 되돌렸다 — 부서는 사람별 배지
 * (`운A`·`관D`)로만 남는다(`CrewGroup`·`teamBadge` 주석). 그래서 이 칩 하나에 **29명이 다 온다.**
 *
 * `null` = ★즐겨찾기(저장한 동료 + 본인). 나머지는 그 소속 전원.
 * **아무 칩도 안 눌린 상태 = 전체 명단**이고 그게 진입 기본값이다(v1.6.42 ⑤ —
 * *"동료탭에 들어가면 먼저 전체를 보여줘야지..?"*). 칩은 **필터로만** 동작한다:
 * 누르면 그 갈래만, 눌린 칩을 다시 누르거나 `전체`를 누르면 해제되어 전체로 돌아온다.
 */
private val CATEGORY_GRID: List<List<CrewGroup?>> = listOf(
    listOf(CrewGroup.MAIN_DRIVER, CrewGroup.MAIN_CONDUCTOR, null),
    listOf(CrewGroup.BRANCH, CrewGroup.OFFICE_DAY, CrewGroup.SHIFT_4_2),
)

/**
 * 격자 칩 라벨 — `null`은 소속이 아니라 ★즐겨찾기 보기다.
 *
 * 소속 글자는 [CrewGroup.shortLabel]이 준다. v1.6.77엔 `기관사`/`차장` 매핑이 **여기 안에**
 * 있었는데, v1.6.78에서 근무선택 화면도 같은 글자를 쓰게 되면서 enum 옆으로 옮겼다 —
 * 같은 매핑이 두 군데 있으면 한쪽만 고쳐 어긋난다. 여기 남는 건 ★즐겨찾기 한 줄뿐이다.
 * (`label`·enum 이름·저장값은 여전히 무변경 — [CrewGroup.shortLabel] 주석)
 */
private fun chipLabel(g: CrewGroup?) = g?.shortLabel ?: "★즐겨찾기"

/**
 * 격자 **열 폭**은 그 열의 두 라벨 중 넓은 쪽이 정한다 — 두 행의 칸이 세로로 어긋나면 격자가 아니다.
 * (행마다 제 라벨 폭으로 나누면 1열이 `본선 기관사`/`신정지선`으로 폭이 달라져 계단이 된다.)
 */
private val COLUMN_WEIGHTS: List<Float> =
    List(CATEGORY_GRID[0].size) { c -> CATEGORY_GRID.maxOf { chipWeight(chipLabel(it[c])) } }

/**
 * 칩 글자 크기 — **sp가 아니라 dp**다(v1.6.42 ⑥, [MatesChip] 주석에 이유).
 * v1.6.49에 11 → 13dp, **v1.6.60에 13 → 15dp**(사용자: *"좀 더 텍스트를 탭 크기에 최적화 되게
 * 몇단계 더 키워서 가독성을 높여줘"*).
 *
 * 13dp에서 15dp로 두 단계를 올릴 수 있었던 건 [MatesChip]을 M3 `FilterChip`에서
 * **`Surface` 직조로 바꿔 좌우 여백을 26 → 16dp로 되찾았기 때문**이다. 칩 넷이 한 줄에 서는
 * 배치라 여백 10dp × 4 = 40dp가 통째로 글자로 돌아왔다([chipWeight] 실산).
 * [chipWeight]가 같은 상수를 쓰므로 **여기만 고치면 폭 배분이 따라온다.**
 * ⚠ 올리려면 360dp 실화면을 반드시 볼 것 — 모델만 보면 놓친다(v1.6.60에 15dp/14.5dp
 * 두 번 다 `본선 차즁`·`4조2교디`로 잘린 뒤에야 여백이 26dp인 걸 알았다).
 */
private const val CHIP_FONT_DP = 15f

/** [MatesChip] 좌우 안쪽 여백(한쪽). M3 `FilterChip`의 실측 13.5dp를 8dp로 줄인 값 — 아래 실산의 전제 */
private val CHIP_PAD_H = 8.dp

/**
 * 칩이 **자기 글자가 필요한 만큼만** 폭을 가져가게 하는 가중치 (v1.6.54).
 *
 * 종전처럼 `weight(1f)` 균등 분배면 짧은 칩이 남는 폭을 똑같이 가져가 긴 칩이 잘린다
 * (v1.6.49 `★즐겨찾기`, v1.6.54 `본선 차ㅈ`·`운:` — 둘 다 360dp 에뮬레이터 실측).
 * 비례 분배는 **줄 전체의 실제 필요 폭 합이 화면에 들어가기만 하면 어느 칩도 안 잘린다** —
 * 남는 폭도 같은 비율로 나눠 갖기 때문이다.
 *
 * 필요 폭 = 좌우 여백 [CHIP_PAD_H]×2 = **16dp** + 글자크기 × (한글 1자 / 그 밖 **0.6**자).
 * ⚠ 0.6은 v1.6.60에 0.5에서 올린 값이다 — 0.5는 Roboto 숫자(0.568em)를 과소평가해
 * `4조2교대`(숫자 둘)가 `통상근무`와 같은 폭을 받아 **`4조2교디`로 끝 글자가 잘렸다**(실측).
 *
 * 360dp 실산(좌우 여백 10dp씩 → 340dp, `전체`와 격자 사이 4dp, 격자 열 사이 4dp × 2):
 *  · `전체`(2.0자) 16 + 30 = **46**
 *  · 1열 max(`본선 기관사` 5.6, `신정지선` 4.0) → 16 + 84 = **100**
 *  · 2열 max(`본선 차장` 4.6, `통상근무` 4.0) → 16 + 69 = **85**
 *  · 3열 max(`★즐겨찾기` 5.0, `4조2교대` 4.2) → 16 + 75 = **91**
 *  합 322 + 칸 사이 12 = **334 ≤ 340** ✓ (여유 6dp)
 * 상한: `(340 − 12 − 16×4) ÷ (2.0 + 5.6 + 4.6 + 5.0)` = 264 ÷ 17.2 = **15.3dp**.
 *
 * ★그룹 하위 필터 줄(4칸)도 같은 함수를 쓴다:
 * `★전체`(3.0) + `동호회 N`(4.2) + `우리 조 N`(4.8) + `기타 N`(3.2) = 15.2자
 *  → 16×4 + 15×15.2 = **292** + 칸 사이 12 = 304 ≤ 340 ✓ (인원수가 두 자리여도 331로 들어간다)
 *
 * 칩 글자는 `dp.toSp()`라 **글자배율을 키워도 이 계산이 안 흔들린다**.
 */
private fun chipWeight(label: String): Float =
    CHIP_PAD_H.value * 2 + CHIP_FONT_DP * label.sumOf { if (it.code < 128) 0.6 else 1.0 }.toFloat()

/**
 * 구간 헤더 글자 한 곳 (v1.7.7 A4).
 *
 * ⚠ **왜 `object` 안에 있나** — 테스트 하네스(`tools/runtests.ps1`의 JUnitCore)에는 Compose가
 * 없다. 이 파일의 최상위 `val`([CHIP_PAD_H]·[CHIP_H]가 `Dp`)이 파일 클래스 `MatesScreenKt`의
 * static 초기화에 들어 있어서, 최상위 함수로 두면 **테스트가 부르는 순간 `NoClassDefFoundError`**
 * 로 터진다. `presentation/live/MapStyle.kt`의 `MapArgb`와 같은 처방이다.
 */
internal object MatesHeader {
    /**
     * `9/6 ~ 10/5` · `12/6 ~ 2027.1/5` · `2027.1/5 ~ 2/3`.
     *
     * **연도를 붙이는 이유**: `›`로 열두 번 밀면 내년 구간인데 종전 글자는 `1/5 ~ 2/3`뿐이라
     * 몇 년 뒤인지 알 길이 없었다([MatesViewModel.MAX_PERIOD] 주석의 v1.6.61 실측과 같은 자리).
     *
     * **달라질 때만 한 번 붙인다** — 시작은 올해와 다를 때, 끝은 시작과 다를 때.
     * 한 번만 붙여도 안 붙은 쪽은 **바로 앞 값과 같은 해**로 읽혀 모호하지 않고, 무엇보다
     * **헤더 폭이 그만큼밖에 없다.** 폴드7 접힘(411dp)·배율 1.5 실산(글자폭 = 숫자 0.568em ·
     * `/` 0.45 · `.`와 공백 0.26 · `~` 0.52 · 한글 1.0):
     *  · 한 번 붙인 최장 `2027.11/20 ~ 12/19` = 9.02em × 18dp = **162dp** → 제목 몫 68dp 남음 ✓
     *  · 두 번 붙인 최장 `2026.12/20 ~ 2027.1/19` = 10.98em × 18dp = **198dp** → 33dp만 남아
     *    제목 `동료`(51dp)가 `동…`으로 잘린다.
     * 헤더의 다른 칸은 못 줄인다 — `‹ ›` 60dp · 여백 8dp · `265명` 48.7dp · `동료 추가` 36dp.
     */
    fun periodLabel(start: LocalDate, end: LocalDate, today: LocalDate): String {
        fun md(d: LocalDate) = "${d.monthValue}/${d.dayOfMonth}"
        val head = if (start.year != today.year) "${start.year}." else ""
        val tail = if (end.year != start.year) "${end.year}." else ""
        return "$head${md(start)} ~ $tail${md(end)}"
    }
}

/**
 * 동료 탭 — v1.6.39에서 상단바 `동료근무`(RosterScreen)를 흡수한 **통합 화면**.
 *
 * 종전엔 같은 매트릭스를 두 화면이 그렸다. 상단바 동료근무는 사업소 전 인원, 하단 동료 탭은
 * 저장한 동료. 그런데 즐겨찾기를 지정하려면 동료근무로 들어가야 했고 거기 이미 동료가 다 나오니
 * 사용자 눈엔 같은 화면이 둘이었다("헤더에 동료근무를 없애야 할듯").
 * → 카테고리 칩 6개(소속 5 + ★즐겨찾기)로 갈라 한 화면에 넣었다.
 * ★즐겨찾기를 고르면 그 아래에 **★그룹 하위 필터**(전체·동호회·우리 조·기타)가 한 줄 더 펼쳐진다
 * (v1.6.40 복원 — v1.6.39에서 2행 3열로 정리하며 빠졌던 것). 다른 칩을 고르면 도로 접힌다.
 *
 * **첫 화면 = 전체 명단**(v1.6.42 ⑤). 칩은 고르는 것이 아니라 **거는 것**이고, 아무것도 안 걸린
 * 상태가 기본이다. 종전 기본값이던 ★즐겨찾기는 담은 동료가 없으면 빈 화면으로 시작했다
 * (*"동료탭에 들어가면 먼저 전체를 보여줘야지..?"*).
 *
 * 표시 범위는 **구간 시작일부터 한 달** — v1.6.38의 "오늘~말일"을 대체한다. 말일이 가까우면
 * 칸이 두세 개만 남아 화면 대부분이 비던 문제를 없앤다. 대신 **달 경계를 넘으므로**:
 *  · 헤더 날짜는 달이 바뀌는 칸에 `9/1`처럼 월을 적고(v1.6.39), 그 칸 **왼쪽에 회색 세로선**을
 *    헤더부터 마지막 행까지 긋는다(v1.6.64, `DutyMatrix`의 `columnBand`). 글자만으로는 옆 날짜와
 *    크기·색이 같아 묻혔다 — 표가 두 달로 갈려 보이려면 경계는 선이어야 한다.
 *  · 근무변경은 두 달치를 합쳐 받는다([MatesViewModel.monthOverrides]).
 *  · **‹ › 구간 이동**은 v1.6.39에서 지웠다가 v1.6.42 ⑦에서 되살렸다. 달(月) 이동이 아니라
 *    구간 이동이다 — › 한 번에 `8/21~9/20` → `9/21~10/20`. 과거 방향은 오늘 구간에서 막는다
 *    ([MatesViewModel.period]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatesScreen(viewModel: MatesViewModel = hiltViewModel()) {
    val mates by viewModel.mates.collectAsStateWithLifecycle()
    val user by viewModel.user.collectAsStateWithLifecycle()
    val liveUsers by viewModel.liveUsers.collectAsStateWithLifecycle()
    val monthOverrides by viewModel.monthOverrides.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    /** 선택된 소속 칩. null = 소속 필터 없음 */
    var category by remember { mutableStateOf<CrewGroup?>(null) }
    /** ★즐겨찾기 칩. `category`와 배타 — **둘 다 꺼져 있으면 전체 명단**(진입 기본값, v1.6.42 ⑤) */
    var favMode by remember { mutableStateOf(false) }
    /** ★그룹 하위 필터. `null` = 전체. ★즐겨찾기를 고른 동안에만 쓰인다(셋째 줄). */
    var favFilter by remember { mutableStateOf<FavGroup?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var sheetTarget by remember { mutableStateOf<MatrixPerson?>(null) }
    var editTarget by remember { mutableStateOf<Mate?>(null) }
    /**
     * 근무 칸을 눌러 연 **행로표** (v1.6.81 ②). `(자산이름, 제목)`. null이면 닫힘.
     * 사용자: *"즐겨찾기 이름 옆에 근무를 찍으면 행로표가 보이는게 구현하기 어려움?"*
     * 즐겨찾기뿐 아니라 **목록의 모든 사람·모든 날짜**가 같은 동작이다 — 규칙이 하나면 헷갈리지 않는다.
     */
    var routeTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    val ctx = LocalContext.current

    val duty = LocalDutyColors.current
    // ⚠ `MatrixMetrics.Roomy`를 직접 쓰지 말 것 — 이름 열이 dp 고정이라 글자배율 1.5부터
    // 세 글자 이름이 말줄임된다(v1.6.68). 배율을 태운 값은 여기서만 나온다.
    val m = rememberMatrixMetrics()
    val period by viewModel.period.collectAsStateWithLifecycle()
    // 진입 시점에 한 번만 읽는다 — 아래 셋(구간 날짜·헤더 글자·`›` 잠금)이 **같은 오늘**을 봐야
    // 자정을 넘겨 앱을 켜 둬도 서로 어긋나지 않는다([MatesViewModel.monthOverrides] 주석과 같은 이유).
    val today = remember { LocalDate.now() }
    // 한 구간 = 시작일부터 한 달. `plusMonths(1)`이라 2월이면 28일, 8월이면 31칸.
    // period 0이면 시작일이 오늘, 1이면 한 달 뒤(9/21~10/20) — 헤더 표기와 같은 값이다.
    val dates = remember(period, today) {
        val start = today.plusMonths(period.toLong())
        val end = start.plusMonths(1)
        generateSequence(start) { it.plusDays(1) }.takeWhile { it < end }.toList()
    }
    // **다음 구간의 마지막 날** — `›`를 열어 둘지 정한다(A4). 구간 시작은 언제나 `오늘 + p개월`이라
    // `dates.first().plusMonths(1)`로 계산하면 안 된다: 1/31 기준이면 전자는 3/31, 후자는 3/28로
    // 갈린다(`plusMonths`의 말일 보정). 위 `dates`와 **글자 그대로 같은 규칙**을 쓴다.
    val nextPeriodEnd = remember(period, today) {
        today.plusMonths(period + 1L).plusMonths(1).minusDays(1)
    }
    // 이 구간에서 **공휴일표가 모르는 해**(있으면). 구간이 한 달이라 걸치는 해는 처음·끝 둘뿐이다.
    val holidayGapYear = remember(dates) {
        listOf(dates.first(), dates.last()).firstOrNull { !Bundled.holidayTableCovers(it) }?.year
    }
    // 첫 칸이 언제나 구간 시작일이라 가로 시작 위치는 0 — 헤더와 전 행이 이 하나를 공유해 같이 움직인다.
    val hScroll = rememberScrollState()

    val q = query.trim()
    val me = meAsPerson(user)
    val favKeys = remember(mates) {
        mates.filter { it.favGroup != null }.map { mateKey(it.name, it.group) }.toSet()
    }
    /**
     * 이름+소속 → **사번**. ★즐겨찾기 행이 근무변경을 받으려면 이 값이 있어야 한다.
     *
     * [Mate]는 사번을 저장하지 않는다(이름·소속·offset·★그룹뿐). 그래서 ★ 목록은 `uid = null`인
     * 행을 만들었고, [MatrixRow]의 `overrides`는 `p.uid`로만 조회되므로
     * **동료의 근무변경이 ★즐겨찾기 화면에서만 통째로 사라졌다.** 게다가 빈 칸이 되는 게 아니라
     * 조용히 **원래 교번값**으로 그려져 틀린 근무가 됐다 — 에뮬 실측(2026-09-04, 박희수):
     * `전체`에서는 `~`(근무변경 표시 있음), ★즐겨찾기에서는 `지7`(표시 없음).
     * 사용자 신고 *"로그인하면 다른 동료 근무가 보이지 않는것"* 의 정체다.
     *
     * ⚠ **사번을 [Mate]에 넣어 저장하지 않는다.** 저장하면 그 값이 낡는다(재가입·사번 정정 때
     * 갱신할 길이 없고, 저장 키는 여전히 이름+소속이다). `users` 명단이 사번의 단일 출처이므로
     * **매번 거기서 되찾는다** — 명단에 없는 사람(수동등록·내장명단)은 종전대로 `null`이고,
     * 그건 서버에 근무변경이 있을 수 없는 사람이라 맞는 값이다.
     */
    val uidByKey = remember(liveUsers) {
        liveUsers.associate { mateKey(it.name, it.group) to it.uid }
    }

    // 소속 칩용 전체 명단. 합성 규칙은 [mergeRoster] 한 곳에만 있다(테스트가 그 함수를 직접 돌린다).
    val roster = remember(user, mates, liveUsers) { mergeRoster(me, liveUsers, mates) }

    // ★그룹별 인원수 (셋째 줄 칩에 붙는다). 본인 행은 필터와 무관하게 늘 보이므로 세지 않는다 —
    // 세면 `우리 조 3`인데 동료는 둘만 보이는 어긋남이 생긴다.
    val favCounts = remember(mates, me) {
        FavGroup.entries.associateWith { g ->
            mates.count { it.favGroup == g && mateKey(it.name, it.group) != me?.key }
        }
    }

    val rows = if (favMode) {
        // ★즐겨찾기 = 저장한 동료 + 본인. ★그룹이 지정된 사람이 위로, 본인은 항상 맨 위
        // (내 근무가 모든 비교의 기준선이다 — ★그룹 필터를 걸어도 빠지지 않는다).
        listOfNotNull(me?.takeIf { q.isEmpty() || it.cleanName.contains(q) }) +
            mates.filter { q.isEmpty() || it.name.contains(q) }
                .filter { favFilter == null || it.favGroup == favFilter }
                .filter { mateKey(it.name, it.group) != me?.key }
                .sortedWith(compareBy({ it.favGroup == null }, { it.favGroup?.ordinal ?: 9 }, { it.name }))
                // `uid`를 반드시 실어 보낸다 — 이게 없으면 이 화면에서만 근무변경이 사라진다([uidByKey])
                .map {
                    MatrixPerson(
                        it.name, it.group, it.patternOffset,
                        uid = uidByKey[mateKey(it.name, it.group)],
                    )
                }
    } else {
        // `category == null` = 전체(필터 없음). 한 줄로 두 경우를 다 본다 — 소속 칩은 필터일 뿐이다.
        // 전체일 땐 소속이 섞이므로 정렬 키에 소속을 끼워 같은 소속끼리 붙여 놓는다.
        // 4조2교대 29명은 소속 안에서 **부서 → 조**로 한 번 더 묶는다 — 이름순으로만 섞이면
        // 같은 조를 눈으로 찾아야 한다(v1.6.54). `teamBadge`가 `관A`…`관D` < `운A`…`운D`를 주므로
        // 그 한 키로 둘이 같이 정렬된다. 4조2교대가 아니면 null이라 다른 소속은 종전대로 이름순이다.
        roster.filter { (category == null || it.group == category) && (q.isEmpty() || it.name.contains(q)) }
            .sortedWith(
                compareBy({ !it.isMe }, { it.key !in favKeys }, { it.group.ordinal },
                    { teamBadge(it.group, it.offset, it.cleanName) ?: "" }, { it.name }),
            )
    }

    // 칩을 바꾸면 명단이 통째로 달라지는데 세로 위치는 그대로라 중간부터 보였다 — "나" 행이 화면 밖.
    // 가로(날짜) 스크롤은 헤더와 공유하는 상태라 건드리지 않는다.
    val listState = rememberLazyListState()
    LaunchedEffect(category, favMode, favFilter) { listState.scrollToItem(0) }
    // 구간을 옮기면 가로 위치도 처음으로 — 안 하면 › 를 눌렀는데 화면은 구간 중간부터 보인다
    LaunchedEffect(period) { hScroll.scrollTo(0) }
    // ★에서 나오면 셋째 줄이 사라지므로 필터도 같이 풀어 둔다 —
    // 안 풀면 ★로 돌아왔을 때 보이지 않던 필터가 걸린 채라 "동료가 없어졌다"가 된다.
    LaunchedEffect(favMode) { if (!favMode) favFilter = null }

    Scaffold(
        topBar = {
            // 달력 헤더와 같은 컴팩트 방식(44dp). 기본 `TopAppBar`를 쓰면 AppRoot의 Scaffold가
            // 이미 얹은 상태바 인셋 **위에 한 번 더** 얹혀(64dp + 상태바) 화면 위쪽 근 90dp가
            // 통째로 비었다 — 실화면에서 잡은 것. 날짜를 한 줄이라도 더 보여 주는 게 맞다.
            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(
                    Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    /*
                     * 폭이 모자라면 **제목이 먼저 양보한다**(달력 헤더 `HEAD_LADDER`와 같은 순서).
                     * 이 줄에서 대신 말해 주는 데가 있는 건 제목뿐이다 — 하단 탭에 `동료`가 늘 떠
                     * 있다. 날짜·인원수·버튼은 대신 말해 줄 곳이 없으므로 안 줄인다.
                     * `weight`이 없으면 **줄 맨 끝에 놓이는 `동료 추가` 버튼이 통째로 밀려나
                     * 사라진다**(Row는 앞에서부터 재고 남은 폭만 뒤에 준다).
                     *
                     * 아래 `Spacer`(오른쪽 정렬용)와 남는 폭을 **4 : 1**로 나눈다. 제목이 필요한
                     * 폭은 `17sp × 한글 2자 × 배율 1.5` = **51dp**이고, 폴드7 접힘(411dp)·배율 1.5
                     * 에서 가장 긴 라벨(연도 붙은 `2027.11/20 ~ 12/19` ≈ 162dp)일 때 남는 폭이
                     * **68dp**다 — 4/5면 54dp라 제목이 살고, 1 : 1이면 34dp라 `동…`으로 잘린다.
                     */
                    Text(
                        "동료", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(4f, fill = false),
                    )
                    Spacer(Modifier.width(4.dp))
                    // ⑦ 구간 이동. `‹`는 오늘 구간에서 꺼진다 — 과거로는 안 간다(v1.6.42).
                    IconButton(
                        onClick = { viewModel.movePeriod(-1) },
                        enabled = period > 0,
                        modifier = Modifier.size(30.dp),
                    ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "이전 구간", Modifier.size(22.dp)) }
                    // 해가 달라지면 연도가 붙는다 — 규칙과 근거는 [MatesHeader.periodLabel](v1.7.7 A4).
                    Text(
                        MatesHeader.periodLabel(dates.first(), dates.last(), today),
                        fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, softWrap = false,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // `›`도 `‹`와 똑같이 꺼진다(v1.6.61). 종전엔 오른쪽만 무제한이라 연타하면
                    // 몇십 년 뒤까지 갔다.
                    // **한 번 더 막는 것이 공휴일표**(v1.7.7 A4): 다음 구간의 마지막 날이 표 밖이면
                    // 12구간에 닿기 전이라도 거기서 멈춘다. 표 밖은 통상근무 휴무가 안 덮여
                    // 설날·추석이 조용히 근무일로 그려진다([MatesViewModel.MAX_PERIOD]에 근거).
                    IconButton(
                        onClick = { viewModel.movePeriod(1) },
                        enabled = period < MatesViewModel.MAX_PERIOD &&
                            Bundled.holidayTableCovers(nextPeriodEnd),
                        modifier = Modifier.size(30.dp),
                    ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "다음 구간", Modifier.size(22.dp)) }
                    Spacer(Modifier.width(4.dp))
                    // v1.6.21의 섹션 인원수를 여기로 옮겼다 — 한 번에 한 소속만 보이므로
                    // 목록 안 섹션 머리글은 칩과 같은 말을 두 번 하는 셈이 된다.
                    Text(
                        "${rows.size}명",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, softWrap = false,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    /*
                     * **동료 추가는 FAB이 아니라 상단바 버튼이다**(v1.7.7 D6).
                     *
                     * FAB은 `Scaffold` 위에 떠 있어 265명 목록을 **스크롤하는 동안 화면 한가운데
                     * 행의 근무 칸을 덮었다**(배율 1.5 실측 — 김영득 행). 목록 아래 여백
                     * (`contentPadding = 88.dp`)은 **끝까지 내렸을 때만** 듣는 처방이라 이 증상엔
                     * 아무 효과가 없다. 그래서 여백을 늘리는 대신 **데이터를 가릴 자리 자체를
                     * 없앴다** — 상단바는 목록 밖이라 무엇을 덮을 일이 없다.
                     * 동작(`showAdd = true`)은 그대로다.
                     */
                    IconButton(
                        onClick = { showAdd = true },
                        modifier = Modifier.size(36.dp),
                    ) { Icon(Icons.Default.PersonAdd, "동료 추가", Modifier.size(22.dp)) }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // 검색칸 — M3 `OutlinedTextField`의 **껍데기만** 빌리고 알맹이는 `BasicTextField`로
            // 짠다(v1.6.48). 기본 `OutlinedTextField`는 높이가 56dp에 못 박혀 있고 modifier로
            // 줄이면 내부 여백이 그대로라 글자가 잘린다 — `contentPadding`을 열어 주는 길은
            // 껍데기(`OutlinedTextFieldDefaults.DecorationBox`)를 직접 부르는 것뿐이다.
            //   **56dp → 42dp.** 입력 글자 14sp와 placeholder 13sp가 따로 놀던 것도 **둘 다 13sp**로
            //   맞췄다(사용자: *"검색 칸이 너무 크지 않아? 텍스트 크기를 동일한 크기가 좋을꺼 같은데?"*).
            //   42dp 밑으로는 안 내린다 — 장갑 낀 손으로 누르는 화면이라 여기가 터치 타깃 하한이다.
            //   글자배율을 키우면 `heightIn(min)`이라 칸이 알아서 자란다(고정 height였다면 잘렸다).
            // ⚠ 커서색은 `onSurface`로 못 박는다 — 기본 `primary`는 포커스 테두리와 같은 초록이라
            //   커서가 테두리에 묻힌다(v1.6.47에서 근무변경 검색칸에 한 처리와 같다).
            val searchSource = remember { MutableInteractionSource() }
            BasicTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                interactionSource = searchSource,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).heightIn(min = 42.dp),
            ) { field ->
                OutlinedTextFieldDefaults.DecorationBox(
                    value = query,
                    innerTextField = field,
                    enabled = true, singleLine = true,
                    visualTransformation = VisualTransformation.None,
                    interactionSource = searchSource,
                    placeholder = { Text("이름으로 검색", fontSize = 13.sp) },
                    // 지우기는 `trailingIcon`이 아니라 **`suffix` 슬롯**에 넣는다(실측으로 갈린 것).
                    // `trailingIcon` 슬롯은 M3가 48dp 최소칸(`IconDefaultSizeModifier`)을 박아 놔서
                    // 지움이 뜨는 순간 칸이 42 → **48dp로 도로 뛴다**. `suffix`의 하한은 24dp라
                    // 42dp 안에 들어온다. `TextButton`도 같은 이유로 못 쓴다(터치 타깃 48dp 강제).
                    // ⚠ `lineHeight`를 같이 주는 이유는 헤더와 같다 — 12sp 글자가 M3 기본 24sp
                    //   줄 높이를 끌고 오면 그것만으로 24dp를 먹는다.
                    // 누르는 면은 여백 포함 **48×40dp**(실측) — 42dp 칸 안에 들어오면서 손가락
                    //   하나는 충분히 받는다. `trailingIcon`이었다면 이 40dp 위에 48dp 최소칸이
                    //   덧씌워져 칸이 부풀었다.
                    suffix = if (query.isNotEmpty()) {
                        {
                            Text(
                                "지움", fontSize = 12.sp, lineHeight = 12.sp * 1.4,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .clickable { query = "" }
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                            )
                        }
                    } else null,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    container = {
                        OutlinedTextFieldDefaults.Container(
                            enabled = true, isError = false,
                            interactionSource = searchSource,
                            shape = RoundedCornerShape(999.dp),
                        )
                    },
                )
            }
            // `전체`(세로 한 칸) + 2행 3열 격자([CATEGORY_GRID]). 가로 스크롤이 아니라 격자라
            // 일곱 칸이 항상 다 보인다. 칸 폭은 균등이 아니라 [chipWeight] = **글자가 필요한 폭**에
            // 비례하고, 격자 열은 두 행 중 넓은 라벨이 정한다([COLUMN_WEIGHTS]) — 그래야 세로가 맞는다.
            // 좌우 여백 12 → 10dp, 칸 사이 5 → 4dp: 키운 글자(15dp)가 360dp에 들어가도록 되찾은 폭.
            // ⚠ `height(IntrinsicSize.Min)`이 `전체` 칩을 격자 두 행 높이에 맞춘다 — 고정 dp가
            //   아니라서 칩 글자를 키워 행이 높아져도 따라온다.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // `전체` = 필터 해제. 진입 기본값(아무것도 안 걸린 상태)이 곧 이 칩이라
                // 소속을 눌렀다가 전체 명단으로 돌아올 길이 화면에 생긴다(v1.6.49).
                MatesChip(
                    "전체", category == null && !favMode,
                    Modifier.weight(chipWeight("전체")).fillMaxHeight(),
                ) {
                    category = null
                    favMode = false
                }
                Column(
                    // 격자가 품은 칸 사이 여백(4dp × 2)은 가중치에 없으므로 더해 준다 —
                    // 안 더하면 격자만 그만큼 좁아져 1열(`본선 기관사`)이 먼저 쪼그라든다.
                    Modifier.weight(COLUMN_WEIGHTS.sum() + 8f),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    CATEGORY_GRID.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            row.forEachIndexed { c, g ->
                                val mod = Modifier.weight(COLUMN_WEIGHTS[c]).height(CHIP_H)
                                // 눌린 칩을 다시 누르면 해제 = 전체로 복귀(v1.6.42 ⑤)
                                if (g == null) MatesChip(chipLabel(null), favMode, mod) {
                                    favMode = !favMode
                                    if (favMode) category = null
                                } else MatesChip(chipLabel(g), category == g, mod) {
                                    category = if (category == g) null else g
                                    favMode = false
                                }
                            }
                        }
                    }
                }
            }

            // 셋째 줄 = ★그룹 하위 필터. **★즐겨찾기를 고른 동안에만** 나온다(v1.6.40 복원).
            // 항상 세 줄이면 "깔끔하게 2행 3열"이라는 v1.6.39 요청과 정면으로 어긋나고,
            // 소속 칩(98명 명단)에는 ★그룹이라는 개념 자체가 없어 걸 것도 없다.
            // 위 격자 밖에 두는 이유: 안에 넣으면 `spacedBy(5.dp)`가 숨어 있는 동안에도
            // 5dp를 차지해 화면이 그만큼 빈 채로 남는다.
            AnimatedVisibility(
                visible = favMode,
                enter = expandVertically(tween(180)) + fadeIn(tween(180)),
                exit = shrinkVertically(tween(180)) + fadeOut(tween(120)),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp).padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // `전체`는 인원수를 안 붙인다 — 상단바가 이미 `N명`으로 같은 말을 하고 있다.
                    // ★를 붙이는 이유: 첫 줄에 생긴 `전체`(= 필터 해제, 전 인원)와 **다른 것**이다.
                    // 이건 ★로 담은 사람 안에서의 전체라, 같은 이름이면 눌러 보고 나서야 안다(v1.6.49).
                    MatesChip(
                        "★전체", favFilter == null,
                        Modifier.weight(chipWeight("★전체")).height(CHIP_H),
                    ) { favFilter = null }
                    FavGroup.entries.forEach { g ->
                        val label = "${g.label} ${favCounts[g] ?: 0}"
                        MatesChip(
                            label, favFilter == g,
                            Modifier.weight(chipWeight(label)).height(CHIP_H),
                        ) { favFilter = g }
                    }
                }
            }

            // 빈 상태는 **왜 비었는지 + 무엇을 하면 채워지는지**를 같이 적는다(v1.6.41 ③).
            // 조건 순서가 곧 우선순위다: 검색어 > ★그룹 필터 > 아무도 안 담음 > 그 소속에 사람 없음.
            //
            // `onlyMe` = ★즐겨찾기 화면에 내 행 하나만 남은 상태(담은 동료 0명 또는 ★그룹 필터가 0명).
            // **내 행은 필터와 무관하게 늘 들어 있어 `rows`가 절대 비지 않는다** — 따로 안 잡으면
            // 한 줄만 덩그러니 뜨고 아무 설명이 없다. 검색 중일 때는 제외(내가 검색에 걸린 정상 결과다).
            val onlyMe = favMode && q.isEmpty() && rows.none { !it.isMe }
            if (onlyMe || rows.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when {
                    q.isNotEmpty() -> EmptyHint(
                        "'$q' 와 맞는 이름이 없습니다",
                        "이름 일부만 넣어도 찾습니다. 다른 소속에 있는 사람이면 위 칩을 바꿔 보세요.",
                        "검색 지우기",
                    ) { query = "" }
                    favFilter != null -> EmptyHint(
                        "★${favFilter?.label}에 담긴 동료가 없습니다",
                        "이름을 눌러 뜨는 시트에서 ★그룹을 골라 담으면 여기 모입니다.",
                        "전체 보기",
                    ) { favFilter = null }
                    favMode -> EmptyHint(
                        "아직 담은 동료가 없습니다",
                        "★즐겨찾기를 다시 눌러 전체 명단으로 돌아간 뒤 이름을 누르면 ★로 담을 수 있습니다.\n" +
                            "담으면 내 근무와 날짜별로 나란히 비교됩니다.",
                        "전체 보기",
                    ) { favMode = false }
                    else -> EmptyHint("표시할 사람이 없습니다", "명단이 갱신되면 자동으로 나타납니다.")
                }
            } else {
                // 공휴일표 밖 구간 안내(v1.7.7 A4) — **달력의 `HolidayTableBanner`와 같은 문구**다.
                // 두 화면이 다른 말을 하면 어느 쪽을 믿을지 알 수 없다.
                // `›`가 표 경계에서 멈추므로 여기 걸리는 건 **오늘이 이미 표 끝자락**일 때(표가
                // 2027년까지면 2027-12 즈음)뿐이지만, 그 때 아무 말도 안 하면 통상근무의 설날·추석이
                // 조용히 근무일로 그려진다 — 조용히 틀리느니 모른다고 말한다.
                holidayGapYear?.let { y ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            "${y}년 공휴일 정보 없음 (직접 확인 필요)",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
                MatrixDateHeader(dates, m, hScroll, duty)
                HorizontalDivider()
                // 아래 여백 88dp는 **FAB 자리였다** — 그 FAB을 상단바로 올렸으므로(v1.7.7 D6)
                // 비켜 줄 것이 없다. 하단 탭바 인셋은 `AppRoot`의 `Scaffold`가 이미 빼 준다.
                LazyColumn(state = listState) {
                    items(rows.size, key = { rows[it].key }) { i ->
                        val p = rows[i]
                        MatrixRow(p, dates, m, hScroll, duty,
                            isFav = p.key in favKeys,
                            overrides = p.uid?.let { monthOverrides[it] } ?: emptyMap(),
                            zebra = i % 2 == 1,
                            onNameClick = { sheetTarget = p },
                            onDutyClick = { date, code ->
                                // 행로표가 있는 다이아만 연다. 휴무·비번·대기처럼 표가 없는 근무는
                                // 다이얼로그를 빈 채로 띄우지 않고 **토스트 한 줄**로 끝낸다
                                // (없는 걸 보여 주려고 화면을 덮는 건 손해다).
                                val asset = RouteTable.assetFor(code, date)
                                if (asset == null) Toast.makeText(
                                    ctx, "행로표가 없는 근무예요", Toast.LENGTH_SHORT,
                                ).show()
                                else routeTarget = asset to
                                    "${date.monthValue}/${date.dayOfMonth} " +
                                    "${p.displayName} · ${code.displayLong} 다이아 행로표"
                            })
                    }
                }
            }
        }
    }

    sheetTarget?.let { person ->
        val mate = mates.find { it.name == person.cleanName && it.group == person.group }
        // 내장 명단에 있는 이름은 근무가 BundledRoster 값이라 고칠 게 없다 — 수동 등록분만 수정 가능
        val manual = mate != null &&
            BundledRoster.forGroup(mate.group).none { it.first == mate.name }
        PersonSheet(
            person,
            fav = mate?.favGroup,
            onSetFav = { viewModel.setFav(person.cleanName, person.group, person.offset, it) },
            onDismiss = { sheetTarget = null },
            // 본인 행은 지울 게 없다
            onRemove = if (mate != null && !person.isMe) ({ viewModel.remove(mate) }) else null,
            onEdit = if (manual && !person.isMe) ({ editTarget = mate }) else null,
        )
    }

    // 달력 상세시트가 쓰는 **그 뷰어 그대로**다(핀치 확대·회전·폴드 펼침 잘림 처리 전부 포함).
    // 새 뷰어를 만들지 않았다 — v1.6.77이 고쳐 둔 자리를 두 벌로 만들면 한쪽만 낡는다.
    routeTarget?.let { (asset, title) ->
        RouteImageDialog(asset = asset, title = title, onDismiss = { routeTarget = null })
    }

    if (showAdd) {
        AddMateSheet(
            onAdd = { name, group, idx -> viewModel.addMate(name, group, idx); showAdd = false },
            onDismiss = { showAdd = false },
        )
    }

    editTarget?.let { target ->
        AddMateSheet(
            onAdd = { name, group, idx ->
                viewModel.editMate(target, name, group, idx); editTarget = null
            },
            onDismiss = { editTarget = null },
            edit = target,
        )
    }
}

/**
 * 빈 화면 안내 한 벌 — **제목 한 줄 + 무엇을 하면 채워지는지 + (있으면) 그걸 바로 하는 버튼**.
 * 일러스트는 안 넣는다. 화면이 비었을 때 필요한 건 그림이 아니라 다음 행동이다.
 */
@Composable
private fun EmptyHint(
    title: String,
    body: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        Modifier.padding(horizontal = 28.dp).padding(bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            title, textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold,
        )
        Text(
            body, textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (action != null && onAction != null) {
            Spacer(Modifier.height(2.dp))
            FilledTonalButton(onClick = onAction) { Text(action, fontSize = 13.sp) }
        }
    }
}

/** 격자 칩 한 칸 높이. `전체` 칩은 이 값을 안 쓴다 — 격자 두 행을 따라 늘어난다(`IntrinsicSize.Min`) */
private val CHIP_H = 34.dp

/**
 * 동료 탭 칩 한 벌 — 소속 격자와 ★그룹 줄이 **같은 컴포저블**을 쓴다. 두 벌로 두면 한쪽만 고친다.
 *
 * 글자 크기는 **sp가 아니라 dp**다(v1.6.42 ⑥). 칩 높이가 못박혀 있어서 글자만 시스템 배율을
 * 따라가면 넘친다 — fontScale 1.3에서 `★즐겨찾기`의 위아래가 잘렸다(에뮬 실측).
 * `dp.toSp()`가 배율을 되나눠 주므로 글꼴 설정이 뭐든 칩 안에 그대로 들어간다.
 * 근무 코드 칩([com.sinjeong.crewcalendar.presentation.roster.MatrixMetrics])이 칸 폭을 dp로
 * 잡고 글자를 거기 맞추는 것과 같은 이유·같은 규칙이다.
 *
 * ⚠ **M3 `FilterChip`이 아니라 `Surface` 직조다**(v1.6.60). `FilterChip`은 좌우 여백이
 * **약 26dp로 못박혀 있고 열 방법이 없어서**(`contentPadding` 인자가 없다), 칩 넷이 한 줄에
 * 서는 이 화면에서 360dp 폭의 30%를 여백으로 먹었다. 그 탓에 글자가 13dp를 못 넘겼다
 * (14.5dp에서 `4조2교디`로 잘림 — 실측). 좌우 [CHIP_PAD_H] 8dp로 직접 그려 **40dp를 글자로
 * 되돌려** 15dp가 됐다(사용자: *"탭 크기에 최적화 되게 몇단계 더 키워서 가독성을 높여줘"*).
 * 색·모양은 `FilterChip`과 같게 맞췄다(선택 = `secondaryContainer` 채움 / 해제 = 테두리만).
 * 같은 방식의 선례가 이미 있다 — `DutyMatrix.FavLabel`.
 *
 * ⚠ **`lineHeight`를 같이 못 박는다.** 안 주면 M3 기본 스타일의 20**sp**가 따라와
 * 글자만 dp로 잡은 보람 없이 큰 글자배율에서 줄 높이가 칩을 넘는다(헤더에서 겪은 것과 같은 함정).
 */
@Composable
private fun MatesChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val sp = with(LocalDensity.current) { CHIP_FONT_DP.dp.toSp() }
    Surface(
        onClick = onClick,
        modifier = modifier,
        // 반지름은 **격자 한 칸 높이의 절반으로 못 박는다**(percent = 50이 아니라). 세로로 선
        // `전체` 칩은 높이가 두 배라 percent면 지름이 같이 커져 알약이 아니라 **덩어리**가 된다 —
        // 고정 dp라야 일곱 칸의 모서리가 같은 곡률로 보인다.
        shape = RoundedCornerShape(CHIP_H / 2),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        border = if (selected) null
        else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Box(
            Modifier.fillMaxSize().padding(horizontal = CHIP_PAD_H),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                fontSize = sp, lineHeight = sp * 1.25,
                maxLines = 1, softWrap = false,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * 동료 추가: 이름 → 소속 → 오늘 근무 선택 (근무선택과 동일 원리로 전체 자동 계산).
 * `edit`가 있으면 같은 시트가 **수정 모드**로 뜬다 — 값이 채워져 있고, 근무 칸을 탭해도 바로
 * 저장되지 않고 선택만 된다(이름만 고칠 수도 있으니 [저장] 버튼으로 확정).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddMateSheet(
    onAdd: (String, CrewGroup, Int) -> Unit,
    onDismiss: () -> Unit,
    edit: Mate? = null,
) {
    val duty = LocalDutyColors.current
    val today = remember { LocalDate.now() }
    var name by remember { mutableStateOf(edit?.name ?: "") }
    var group by remember { mutableStateOf(edit?.group ?: CrewGroup.BRANCH) }
    // 지금 저장된 근무 칸을 미리 선택해 둔다. 소속을 바꾸면 교번표가 통째로 달라지므로 다시 골라야 한다.
    var picked by remember(group) {
        mutableStateOf(
            if (edit != null && group == edit.group) {
                val p = Bundled.patternFor(group)
                Math.floorMod(
                    ChronoUnit.DAYS.between(p.anchorDate, today).toInt() + edit.patternOffset,
                    p.length,
                )
            } else -1,
        )
    }

    /*
     * ⚠ **반쯤 펼친 상태를 건너뛰고, 안이 스크롤된다**(v1.6.93). 종전엔 `skipPartiallyExpanded`
     * 가 없어 시트가 화면 절반에서 멈춰 섰는데, 그 안에 300dp 짜리 근무 격자가 들어 있어
     * 글자배율을 조금만 키워도 격자 아래 [저장] 이 화면 밖으로 나갔다 — **고르고도 저장을 못 했다.**
     * 격자는 자기 높이(`heightIn(max = 300.dp)`)가 정해져 있어 바깥 스크롤과 충돌하지 않는다.
     */
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                if (edit == null) "동료 추가" else "동료 수정",
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold,
            )
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("이름") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            // 소속 5종이라 SegmentedButton 한 줄엔 글자가 안 들어간다 → 접히는 칩
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CrewGroup.entries.forEach { g ->
                    FilterChip(
                        selected = group == g, onClick = { group = g },
                        label = { Text(g.label, fontSize = 11.sp) },
                    )
                }
            }
            Text(
                "오늘 이 동료의 근무를 고르세요 — 나머지 날짜는 교번 순서로 자동 계산됩니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val pattern = Bundled.patternFor(group)
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.heightIn(max = 300.dp),
            ) {
                items(pattern.sequence.size) { i ->
                    val code = DutyCode.parse(pattern.sequence[i])
                    val (bg, fg) = dutyCellColors(code.colorType, duty, MaterialTheme.colorScheme.onSurfaceVariant)
                    Surface(
                        onClick = {
                            if (edit != null) picked = i
                            else if (name.trim().length >= 2) onAdd(name, group, i)
                        },
                        color = bg, contentColor = fg,
                        shape = RoundedCornerShape(9.dp),
                        border = when {
                            i == picked -> BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary)
                            name.trim().length < 2 ->
                                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            else -> null
                        },
                    ) {
                        Text(
                            code.display,
                            modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold, maxLines = 1,
                        )
                    }
                }
            }
            if (edit == null && name.trim().length < 2) Text(
                "이름을 먼저 입력하면 근무를 선택할 수 있습니다",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (edit != null) {
                if (picked < 0) Text(
                    "소속을 바꿨습니다 — 오늘 근무를 다시 골라주세요.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Button(
                    onClick = { onAdd(name, group, picked) },
                    enabled = name.trim().length >= 2 && picked >= 0,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("저장") }
            }
        }
    }
}
