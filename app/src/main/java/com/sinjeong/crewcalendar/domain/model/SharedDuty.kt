package com.sinjeong.crewcalendar.domain.model

import java.time.LocalDate

/**
 * **휴가는 나만 보기** (v1.7.19) — 휴가로 바꾼 날은 **서버에 아예 올리지 않는다.**
 *
 * 카스 원문(2026-09-19): *"설정에 본인이 근무에 휴가를 변경해도 본인만 볼수있는 그런것만 만들면
 * 되겠지?"* — 범위를 물었을 때 **"본인만 보기(완전히 숨김)"** 를 골랐다.
 * (하루 앞선 초안은 `휴가` 두 글자로 덮어 올리는 것이었고, 같은 날 이 쪽으로 뒤집혔다.)
 *
 * | 저장값(내 폰) | 동료가 보는 것 |
 * |---|---|
 * | 연차·보상·촉연·대휴·장휴·청휴·학습·만휴·돌봄휴가·동행휴가·병가·공가·가연차·(옛)작연차 | **원래 근무**(안 올린다 = 서버 문서 삭제) |
 * | 직접입력(앱이 모르는 자유 글자 — `병원`·`대전 출장` …) | **원래 근무**(같이 숨긴다) |
 * | 다이아 번호·`충당 지2`·`대기충당 …`·`교체 …`·`지근`·`운휴`·`지휴`·`교육`·`회행`·`휴5`·`~` | 그대로 보인다 |
 *
 * **내 달력·위젯·공유 월이미지·주52·휴무 개수는 한 글자도 안 바뀐다** — 로컬 저장값은 원본 그대로고
 * 바뀌는 것은 **서버에 올리느냐 마느냐** 하나뿐이다. 거치는 자리는
 * `FirestoreScheduleRepository.publishOverride` **한 곳**이다.
 *
 * 안드로이드 임포트 0 — `SharedDutyTest` 가 잠근다.
 */

/**
 * 서버로 나갈 `dutyRaw`, **`null` 이면 올리지 않는다**(= 그 날짜 공유 문서를 지운다).
 * [hideLeave] 가 false 면 전부 원본 그대로다.
 *
 * 판정은 [DutyCode] 의 실제 분류를 쓴다 — 휴가류는 [DutyCode.LEAVE_OPTIONS](= `REST_OPTIONS`
 * 파생), 직접입력은 "낱말표([DutyCode.WORD_CODES])에도 없고 번호도 다이아도 없는 값".
 * **낱말 목록을 여기 두 벌로 적지 않는다.**
 */
fun sharedDutyRaw(dutyRaw: String, hideLeave: Boolean): String? {
    if (!hideLeave) return dutyRaw
    val s = dutyRaw.trim()
    // 빈 값은 애초에 안 올라간다(패턴 복귀 = 문서 삭제). 부르는 쪽이 먼저 가르지만 여기서도 같은 답.
    if (s.isEmpty()) return null
    if (s in DutyCode.LEAVE_OPTIONS) return null
    val d = DutyCode.parse(s)
    // 앱이 만들 수 있는 값인가 — 낱말표(`운휴`·`교육`·`주간` …) · 번호 붙은 다이아(`휴5`·`지대11비`) ·
    // 충당 계열(`충당 지2`). 셋 다 아니면 사람이 직접 친 글자라 같이 숨긴다.
    val known = s in DutyCode.WORD_CODES || d.number != null || d.fill != null
    return if (known) dutyRaw else null
}

/** 재게시 창 — 지난 기록까지 거슬러 올라가는 날수(오늘−31 ~ 미래). */
const val REPUBLISH_BACK_DAYS = 31L

/**
 * **다시 맞출 날짜 고르기**(v1.7.19 재게시). 스위치를 바꿨을 때·업데이트 후 첫 실행에 이미
 * 서버에 있는 기록을 지금 설정대로 맞추는데, **숨김 대상인 날만** 고른다(= 휴가류·직접입력).
 * 나머지는 켜든 끄든 서버 값이 같아 건드릴 이유가 없다.
 *
 * **켤 때나 끌 때나 대상이 같다** — 켜져 있으면 그 날짜 문서를 **지우고**, 꺼져 있으면 **다시 올린다**
 * (부르는 쪽이 [sharedDutyRaw] 결과로 가른다). 방향을 따로 계산하지 않는다.
 *
 * [overrides] 는 `날짜 → 저장된 dutyRaw`(로컬 전체). 빈 값은 애초에 서버에 없다(패턴 복귀).
 */
fun republishDates(overrides: Map<LocalDate, String>, today: LocalDate): List<LocalDate> {
    val from = today.minusDays(REPUBLISH_BACK_DAYS)
    return overrides
        .filter { (date, raw) ->
            !date.isBefore(from) && raw.isNotBlank() && sharedDutyRaw(raw, hideLeave = true) == null
        }
        .keys.sorted()
}
