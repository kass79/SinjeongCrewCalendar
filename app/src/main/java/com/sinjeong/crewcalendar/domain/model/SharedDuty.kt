package com.sinjeong.crewcalendar.domain.model

import java.time.LocalDate

/**
 * **휴가 종류 가리기** (v1.7.19) — 동료에게 보내는 근무변경 값에서 **휴가 종류만** 지운다.
 *
 * 카스 원문(2026-09-19): *"그냥 설정에서 바꾼내용(휴가)는 안보이게 하나만 넣으면 어떨까?"* —
 * 가리는 범위를 물었을 때 **"`휴가` 로만 보이기"** 를 골랐다.
 *
 * | 저장값(내 폰) | 동료가 받는 값 |
 * |---|---|
 * | 연차·보상·촉연·대휴·장휴·청휴·학습·만휴·돌봄휴가·동행휴가·병가·공가·가연차·(옛)작연차 | **`휴가`** |
 * | 직접입력(앱이 모르는 자유 글자 — `병원`·`대전 출장` …) | **`기타`** |
 * | 다이아 번호·`충당 지2`·`대기충당 …`·`교체 …`·`지근`·`운휴`·`지휴`·`교육`·`회행`·`휴5`·`~` | 그대로 |
 *
 * **바뀌는 것은 서버로 나가는 값 하나뿐이다** — 내 달력·위젯·공유 월이미지·주52·휴무 개수는
 * 로컬 저장값(원래 낱말)을 그대로 보므로 한 글자도 안 바뀐다. 거치는 자리는
 * `FirestoreScheduleRepository.publishOverride` **한 곳**이다.
 *
 * 안드로이드 임포트 0 — `SharedDutyTest` 가 잠근다.
 */

/**
 * 휴가류를 덮는 두 글자. `DutyCode.parse("휴가")` 가 [DutyType.REST] 라 **동료 탭에서 다른
 * 휴가류와 똑같은 빨강·똑같은 라벨**이고 폭도 2.00 units(상한 `UNIFORM_UNITS` 3.24) 안이다.
 * 옛 버전 앱도 같은 `parse` 를 쓰므로 그대로 그린다.
 */
const val SHARED_LEAVE = "휴가"

/**
 * 직접입력을 덮는 두 글자. `parse` 가 [DutyType.ETC] 라 **지금 자유 글자가 그려지는 모습과
 * 똑같다**(투명 바탕 + 회색 글자) — 동료 탭 표시 규칙을 새로 만들 필요가 없었다.
 */
const val SHARED_FREE = "기타"

/**
 * 서버로 나갈 `dutyRaw`. [maskLeave] 가 false 면 **원본 그대로**다.
 *
 * 판정은 [DutyCode] 의 실제 분류를 쓴다 — 휴가류는 [DutyCode.LEAVE_OPTIONS](= `REST_OPTIONS`
 * 파생), 직접입력은 "낱말표([DutyCode.WORD_CODES])에도 없고 번호도 다이아도 없는 값".
 * **낱말 목록을 여기 두 벌로 적지 않는다.**
 *
 * 멱등이다 — 이미 가린 값(`휴가`·`기타`)은 그대로 돌아온다(재게시가 여러 번 돌아도 안전).
 */
fun sharedDutyRaw(dutyRaw: String, maskLeave: Boolean): String {
    if (!maskLeave) return dutyRaw
    val s = dutyRaw.trim()
    if (s.isEmpty()) return dutyRaw
    // 이미 가린 값 — 다시 판정하지 않는다(`휴가` 는 아래 낱말표에 없어서 `기타` 로 떨어진다)
    if (s == SHARED_LEAVE || s == SHARED_FREE) return s
    if (s in DutyCode.LEAVE_OPTIONS) return SHARED_LEAVE
    val d = DutyCode.parse(s)
    // 앱이 만들 수 있는 값인가 — 낱말표(`운휴`·`교육`·`주간` …) · 번호 붙은 다이아(`휴5`·`지대11비`) ·
    // 충당 계열(`충당 지2`). 셋 다 아니면 사람이 직접 친 글자다.
    val known = s in DutyCode.WORD_CODES || d.number != null || d.fill != null
    return if (known) dutyRaw else SHARED_FREE
}

/** 재게시 창 — 지난 기록까지 거슬러 올라가는 날수(오늘−31 ~ 미래). */
const val REPUBLISH_BACK_DAYS = 31L

/**
 * **다시 올릴 날짜 고르기**(v1.7.19 재게시). 스위치를 바꿨을 때·업데이트 후 첫 실행에
 * 이미 서버에 있는 기록을 지금 설정대로 맞추는데, **가리기가 값을 바꾸는 날만** 고른다
 * (= 휴가류·직접입력). 나머지는 켜든 끄든 서버 값이 같아 쓸 이유가 없다.
 *
 * 스위치를 **켤 때나 끌 때나 대상이 같다** — 둘 다 "원본과 가린 값이 다른 날"이라 방향을
 * 따로 계산하지 않는다. **삭제는 절대 하지 않는다**(지우면 동료 화면에서 그 날이 원래 패턴으로
 * 되살아나 거짓이 된다).
 *
 * [overrides] 는 `날짜 → 저장된 dutyRaw`(로컬 전체). 빈 값은 애초에 서버에 없다(패턴 복귀).
 */
fun republishDates(overrides: Map<LocalDate, String>, today: LocalDate): List<LocalDate> {
    val from = today.minusDays(REPUBLISH_BACK_DAYS)
    return overrides
        .filter { (date, raw) ->
            !date.isBefore(from) && raw.isNotBlank() && sharedDutyRaw(raw, maskLeave = true) != raw
        }
        .keys.sorted()
}
