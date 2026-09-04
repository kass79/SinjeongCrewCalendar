package com.sinjeong.crewcalendar.util

import com.sinjeong.crewcalendar.domain.model.DutyType

/**
 * 근무 종별 [배경, 글자] 색 (라이트 톤과 동일).
 *
 * v1.6.88에 `MonthImage.dutyColors`에서 뽑아냈다 — 공유 월 이미지와 홈 위젯이 **같은 표**를 봐야
 * "앱 달력 색과 다르다"는 어긋남이 안 난다. Compose 밖(Canvas·Glance)이라 ARGB Int 그대로 쓴다.
 */
fun dutyPalette(t: DutyType): Pair<Int, Int> = when (t) {
    DutyType.MAIN_DAY, DutyType.OFFICE -> 0xFFE8FAF0.toInt() to 0xFF00210E.toInt()
    DutyType.MAIN_NIGHT, DutyType.BRANCH_NIGHT, DutyType.SPECIAL -> 0xFFF6F0FD.toInt() to 0xFF7A5AB8.toInt()
    // 비번 = 야간과 같은 보라 계열(채도만 낮춤). Theme.kt의 LightDutyColors.off/onOff와 같은 값 — 같이 고칠 것
    DutyType.POST_NIGHT -> 0xFFF5F0FB.toInt() to 0xFF74679A.toInt()
    DutyType.REST, DutyType.BRANCH_REST -> 0xFFFFF0EC.toInt() to 0xFFB3271E.toInt()
    DutyType.STANDBY, DutyType.BRANCH_STANDBY -> 0xFFFFF8E8.toInt() to 0xFF755B00.toInt()
    DutyType.BRANCH -> 0xFFE8FAF0.toInt() to 0xFF00210E.toInt()
    DutyType.ETC -> 0x00000000 to 0xFF888888.toInt()
}
