package com.sinjeong.crewcalendar.domain.model

import java.time.LocalDate

/**
 * **남은 휴가 세기**(v1.7.20 ④) — 카스(2026-09-30):
 * *"근무변경하면 연차,촉연,대휴,장휴, 본인 숫자갯수를 입력해서 휴가를 쓰면 하나씩 빼고 그럼 남은 휴가를
 * 쉽게 볼수있잖아?"* → 입력은 **"올해 받은 개수"**, 보이는 곳은 **"근무변경 칩에"** →
 * *"다른 휴가들도 갯수 해줘.. 연차 휴가를 꾹 누르면 올해받은 개수 적고, 쓴개수 적는걸로"*.
 *
 * 남음 = **받은 개수 − 앱 밖에서 쓴 개수 − 그 해 내 근무변경이 그 휴가인 날 수**.
 * 카운터를 따로 깎지 않고 **로컬 근무변경에서 매번 센다** — 근무변경을 취소·변경하면 저절로 맞는다.
 * 해는 **달력 날짜의 해**(12월에 내년 1월을 고치면 내년 개수)이고 해마다 새로 적는다(해별 저장 키).
 *
 * **폰에만 저장한다**(`theme` prefs `leave_quota_{해}` — `ThemeController`). 서버(`users`·
 * `rosterOverrides`)로 나가는 경로가 없다. `휴가는 나만 보기`·월 휴무 개수와 서로 영향 없다.
 * 안드로이드 임포트 0 — `LeaveBalanceTest` 가 잠근다.
 */
data class LeaveQuota(
    /** 올해 받은 개수 */
    val granted: Int,
    /** 앱 밖에서 이미 쓴 개수(앱 쓰기 전·앱에 안 적은 날) */
    val usedOutside: Int = 0,
)

/**
 * 개수를 셀 수 있는 휴가 — **근무변경 칩 순서 그대로**, 휴가류([DutyCode.LEAVE_OPTIONS])만.
 * 운휴·지휴(휴무 배정)·충당 계열·지근·교육·회행은 빠진다. `작연차`(옛 기록 전용, 고를 수 없음)도 빠진다.
 * ⚠ 낱말을 여기 다시 적지 말 것 — 두 목록 파생이다.
 */
val LEAVE_CHIPS: List<String> = DutyCode.CHANGE_OPTIONS.filter { it in DutyCode.LEAVE_OPTIONS }

/** [year] 해(1/1~12/31)에 내 근무변경이 [code] 인 날 수. [overrides] = 날짜 → 저장된 `dutyRaw` */
fun leaveUsedInApp(overrides: Map<LocalDate, String>, code: String, year: Int): Int =
    overrides.count { (date, raw) -> date.year == year && raw.trim() == code }

/** 남은 개수(음수 가능 — 화면이 빨간 `−1` 로 그린다) */
fun leaveLeft(quota: LeaveQuota, usedInApp: Int): Int = quota.granted - quota.usedOutside - usedInApp

/** 칩·표에 쓰는 숫자 — 음수는 진짜 빼기 기호(`−1`) */
fun leaveCountText(n: Int): String = if (n < 0) "−${-n}" else "$n"

/** 한 해치 저장 한 줄 `연차:15:3;대휴:2:0` (휴가 이름에 `:`·`;` 가 없다) */
fun encodeLeaveQuotas(m: Map<String, LeaveQuota>): String =
    m.entries.joinToString(";") { (code, q) -> "$code:${q.granted}:${q.usedOutside}" }

/** 깨진 칸은 버리고 나머지는 산다 */
fun decodeLeaveQuotas(s: String?): Map<String, LeaveQuota> =
    s.orEmpty().split(";").mapNotNull { rec ->
        val p = rec.split(":")
        val g = p.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
        p[0].takeIf { it.isNotBlank() }?.let { it to LeaveQuota(g, p.getOrNull(2)?.toIntOrNull() ?: 0) }
    }.toMap()
