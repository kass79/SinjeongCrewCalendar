package com.sinjeong.crewcalendar.domain.model

import java.time.LocalDate

/**
 * 관리자 공지 (v1.6.89) — Firestore `notices/{id}`.
 *
 * 달력 맨 위 카드로 **전원에게** 한 건씩 보이는 짧은 알림이다. 식단표([WeeklyMenu])와 같은 자리에
 * 있는 관리자 쓰기 경로지만 훨씬 단순하다 — 문서 하나가 곧 공지 하나고, 기간이 지나면 저절로
 * 사라진다(지우는 것은 관리자 선택).
 *
 * 기간을 `from`/`to` **문자열 날짜**로 두는 이유: Firestore 규칙이 정규식으로 모양을 검사할 수
 * 있고(`firestore.rules` 의 notices 절), 문서를 콘솔에서 손으로 넣을 때도 사람이 읽을 수 있다.
 *
 * @param id Firestore 문서 ID. **새로 쓸 때는 빈 문자열** — 저장소가 자동 ID를 뽑는다.
 * @param createdAt 서버 시각(epoch ms). 배너가 "최신 1건"을 고르는 기준. 방금 쓴 문서는
 *   서버 타임스탬프가 아직 안 채워져 0 일 수 있다(잠깐 뒤 스냅샷이 채운다).
 */
data class Notice(
    val id: String,
    val title: String,
    val body: String,
    val from: LocalDate,
    val to: LocalDate,
    val createdAt: Long,
) {
    fun isActive(today: LocalDate) = !today.isBefore(from) && !today.isAfter(to)

    companion object {
        /** 화면 입력 상한 — `firestore.rules` 의 notices 절과 **같은 수**여야 한다 */
        const val MAX_TITLE = 40
        const val MAX_BODY = 500
    }
}
