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
 * | 휴무 → **`지근 9`**·**맨 `지근`** | **뺀다** ← 여기 하나뿐이다 (다이아 유무 무관 — v1.7.10) |
 * | **`운휴` 로 바꾼 날을 다시 `지근`** | **뺀다** (카스: *"휴무를 지근으로 바꾸면 −1"*) |
 * | 근무일 → 휴무(대체휴무 등) | 안 센다 (패턴 기준) |
 * | **근무일 → `운휴`·`지휴`** | **센다** ← v1.7.9에 더했다 ([REST_OVERRIDES]) |
 * | 근무일 → 연차·대휴·병가 등 그 밖의 휴가 | 안 센다 |
 * | **휴일 운휴 다이아**(본선 주간 26~29 가 토·일·공휴일에 걸린 날) | **센다** ← v1.7.11 |
 * | 그 날을 **`지근`** 으로 | **뺀다** (카스: *"만약 그쪽에 지근으로 지정하면 빼는거지"*) |
 * | 그 날을 **`연차`** 로 | **센다** (원래 휴무였으니 위 `휴무 → 연차` 줄과 같다) |
 *
 * 근무선택으로 **패턴 자체가 바뀌면** 개수도 따라 바뀌는 것이 맞다(그건 배정이 바뀐 것이다).
 *
 * ## 휴일 운휴 다이아 (v1.7.11 — 카스가 v1.7.10 판단을 뒤집었다)
 *
 * 2026-09-07 카스: *"주말에 토,일 주간 26,27,28,29 운휴는 휴일로 안잡힌듯 ..? 휴무 개수가
 * 안맞네?"* · *"만약 그쪽에 지근으로 지정하면 빼는거지.."*
 *
 * v1.7.10 까지 [REST_OVERRIDES] 아래에 이렇게 적혀 있었다 — **이 판단이 뒤집힌 것이다**:
 *
 * > *⚠ 달력 칸이 **본선 주간 26~29 휴일**에 `운휴`라고 적는 것은 **근무일 표기**라 여기와
 * > 무관하다(그 날의 `raw` 는 `"26"` 이고 `isOverridden` 도 false 다).*
 *
 * 글자만 보면 맞는 말이었지만(그 날은 배정상 근무일이다), 카스에게는 **열차가 안 다녀 쉬는
 * 날 = 휴무**다 — [REST_OVERRIDES] 로 더한 `운휴`·`지휴` 와 같은 뜻이고 이쪽은 근무변경조차
 * 필요 없이 시각표가 이미 그렇게 말하고 있다. 그래서 `base.isRest` 자리를
 * **`base.isRest || Bundled.isHolidayIdleDia(base, date)`** 로 넓혔다.
 *
 * ⚠ **`duty` 가 아니라 [Bundled.isHolidayIdleDia] 에 `base` 를 넘긴다.** 근무변경된 날의
 * `base` 는 원래 다이아(`"26"`)다. `duty` 로 물으면 `지근` 으로 바꾼 순간 조건이 저절로
 * 사라져 우연히 맞는 것처럼 보이지만, **`연차` 로 바꾸면 안 세어 버린다** — 원래 휴무였던
 * 날이라 위 표의 `휴무 → 연차` 줄대로 **세야** 한다. `지근` 을 빼는 것은 아래 `지근` 절이
 * 이미 하는 일이고, 그 하나가 카스의 *"지근으로 지정하면 빼는거지"* 를 그대로 만든다.
 *
 * ⚠ 판정은 **`fill ?: raw`** 가 `"지근"` 인지로 한다 — [DutyCode.colorType] 이 `충당` 주황을
 * 가리는 꼴과 **똑같은 대칭**이다(v1.7.10). `fill` 만 보면 **다이아를 붙인 `지근 9` 만 빠지고
 * 다이아 없는 맨 `지근` 은 그대로 세어진다** — 맨 `지근` 은 `DutyCode.parse` 가 `OVERRIDE_TYPES`
 * 로 떨어뜨려 `fill` 이 null 이기 때문이다. v1.7.9 에서 맨 `지근` 이 저장·표시 가능해지자
 * (*"아직 근무를 모를때 다이아 없이 저장을 하면 그냥 지근"*) 바로 이 구멍으로 −1 이 안 됐다.
 * 접두어 없는 다른 변경(연차·교육)은 `fill` 도 null 이고 `raw` 도 `"지근"` 이 아니라
 * 자동으로 "센다" 쪽이다.
 *
 * ⚠ 휴무 개수를 세는 자리는 두 곳이다(앱바 칩 `CalendarUiState.restDayCount` · 공유 이미지
 * `MonthImage`). **둘 다 이 한 곳을 통과해야 한다** — 각자 세면 화면과 공유 그림의 숫자가 갈린다.
 */
val DaySchedule.countsAsRestDay: Boolean
    get() {
        val base = if (isOverridden) originalDutyRaw?.let(DutyCode::parse) ?: duty else duty
        // 원래 쉬는 날인가 — 휴무(`휴N`) 이거나 **휴일 운휴 다이아**(26~29 × 토·일·공휴일, v1.7.11)
        val baseWasRest = base.isRest || Bundled.isHolidayIdleDia(base, date)
        // 휴무 → 운휴처럼 **양쪽 다 참**인 날도 `count {}` 라 한 번만 센다
        return (baseWasRest && !(isOverridden && (duty.fill ?: duty.raw) == "지근")) ||
            (isOverridden && duty.fill == null && duty.raw in REST_OVERRIDES)
    }

/**
 * **근무일을 이걸로 바꾸면 휴무 개수가 는다**(v1.7.9). 2026-09-06 사용자 원문:
 * *"운휴,지휴로 바꿔도 휴무갯수에 플러스 해야해!"*
 *
 * 이 둘은 휴가가 아니라 **그날의 휴무 배정 자체가 생긴 것**이다(운휴 = 열차가 안 다녀 쉬는 날,
 * 지휴 = 지선 휴일). 연차·대휴·병가처럼 *배정된 근무일을 휴가로 쓴* 것과 다르므로 [countsAsRestDay]
 * 표의 `근무일 → 휴무` 줄(안 센다)에서 이 둘만 빠져나온다.
 *
 * ⚠ **사용자가 말한 둘뿐이다** — 연차·대휴 등을 여기 넣으려면 사용자에게 먼저 물을 것.
 * ⚠ `fill == null` 을 같이 보는 이유: `충당 운휴` 같은 저장값은 없지만, 접두어가 붙은 값은
 *   `raw` 가 `"충당 …"` 이라 어차피 안 걸린다 — 규칙을 글로 못 박아 두는 가드다.
 * ⚠ ~~달력 칸이 **본선 주간 26~29 휴일**에 `운휴`라고 적는 것은 **근무일 표기**라 여기와 무관하다
 *   (그 날의 `raw` 는 `"26"` 이고 `isOverridden` 도 false 다).~~
 *   → **v1.7.11 에서 카스가 뒤집었다** (*"주말에 토,일 주간 26,27,28,29 운휴는 휴일로 안잡힌듯
 *   ..? 휴무 개수가 안맞네?"*). 그 날도 휴무로 센다 — 다만 이 집합이 아니라
 *   [Bundled.isHolidayIdleDia] 가 잡고, [countsAsRestDay] 의 `base.isRest` 쪽에서 걸린다.
 *   이 집합은 여전히 **근무변경으로 찍은 낱말 둘**뿐이다(늘리려면 사용자에게 먼저 물을 것).
 */
private val REST_OVERRIDES = setOf("운휴", "지휴")
