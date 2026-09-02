package com.sinjeong.crewcalendar.domain.model

import java.time.LocalDate

/**
 * 날짜별 확정 배정. 패턴 계산값을 덮어쓰는 층:
 * 관리자 근무표 업로드(xlsx) / 개인 수정이 여기에 기록된다.
 */
data class Schedule(
    val id: String = "",              // "{uid}_{yyyy-MM-dd}"
    val uid: String = "",
    val date: LocalDate = LocalDate.MIN,
    val dutyRaw: String = "",         // "14", "휴3", "대2", "지13", "~" ...
    val memo: String = "",
    /** 배정 출처 */
    val source: Source = Source.PATTERN,
    /** 수정된 경우 원본 코드 */
    val originalDutyRaw: String? = null,
    val updatedAtEpochMs: Long = 0,
) {
    enum class Source { PATTERN, ROSTER_UPLOAD, MANUAL }
    val duty: DutyCode get() = DutyCode.parse(dutyRaw)
}

/** 달력 한 칸에 뿌릴 계산 결과 (패턴 + 오버라이드 + 메모 + 시각 + 공휴일 병합) */
data class DaySchedule(
    val date: LocalDate,
    val duty: DutyCode,
    val memo: String = "",
    val hasGoogleEvent: Boolean = false,
    val isOverridden: Boolean = false,
    /** 근무변경 전 패턴 원래 코드 (2줄 표시용) */
    val originalDutyRaw: String? = null,
    /** 출근시각 표시 문자열 (다이아 없으면 null) */
    val signOn: String? = null,
    /** 법정공휴일 이름 (날짜 빨강) */
    val holidayName: String? = null,
    /** 기념일 이름 (이름만 빨강, 근무는 평일) */
    val memorialName: String? = null,
    val seasonalTerm: String? = null,  // 소서, 초복 …
)

/**
 * 이 날이 **월 휴무 개수에 들어가는가** (v1.6.83).
 *
 * ## 규칙 (사용자 확정) — *"휴무를 지정근무로 바꿀 때만 줄어드는 거야"*
 *
 * 휴무 날에 근무변경을 해도 [duty] 만 바뀔 뿐 **그 달에 배정받은 휴무가 사라진 것은 아니다.**
 * `충당 9` 로 나가는 것은 *그 휴무에 나가는 것*이라 개수가 줄면 안 된다. 딱 하나 예외가
 * **`지근`(지정근무)** — 이건 휴무를 근무일로 바꿔 쓴 것이라 그날은 휴무가 아니게 된다.
 *
 * | 그날 | 개수 |
 * |---|---|
 * | 휴무 그대로 | **센다** |
 * | 휴무 → `충당`·`대기충당`·`교체` | **센다** (휴무에 나갔을 뿐) |
 * | 휴무 → 연차·교육 등 그 밖의 변경 | **센다** |
 * | 휴무 → **`지근`** | **뺀다** ← 여기 하나뿐이다 |
 * | 근무일 → 휴무(대체휴무 등) | 안 센다 (패턴 기준) |
 *
 * 근무선택으로 **패턴 자체가 바뀌면** 개수도 따라 바뀌는 것이 맞다(그건 배정이 바뀐 것이다).
 *
 * ⚠ 판정은 [DutyCode.fill] 접두어가 `"지근"` 인지로 한다 — `DutyCode.FILL_OPTIONS` 네 개 중
 * 지근만이다. 접두어 없는 변경(연차·교육)은 `fill` 이 null 이라 자동으로 "센다" 쪽이다.
 *
 * ⚠ 휴무 개수를 세는 자리는 두 곳이다(앱바 칩 `CalendarUiState.restDayCount` · 공유 이미지
 * `MonthImage`). **둘 다 이 한 곳을 통과해야 한다** — 각자 세면 화면과 공유 그림의 숫자가 갈린다.
 */
val DaySchedule.countsAsRestDay: Boolean
    get() {
        val base = if (isOverridden) originalDutyRaw?.let(DutyCode::parse) ?: duty else duty
        return base.isRest && !(isOverridden && duty.fill == "지근")
    }
