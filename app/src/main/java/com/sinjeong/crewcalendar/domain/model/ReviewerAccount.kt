package com.sinjeong.crewcalendar.domain.model

/**
 * **구글 플레이 심사용 계정** (v1.6.82 ⑥).
 *
 * ## 왜 필요한가
 *
 * 이 앱의 로그인은 [BundledStaff.validate] 로 **실직원 명단(이름+사번)** 을 대조한다. 그래서
 * 심사관에게 줄 수 있는 계정이 없었고, 플레이 프로덕션 심사가 v1.6.81(93)을
 * *"제공된 로그인 정보로 앱에 액세스할 수 없음"* 으로 거부했다. 실직원의 이름·사번을 구글에
 * 넘기는 것은 부적절하므로 **전용 계정 하나**를 둔다.
 *
 * ## 명단에 가짜 직원을 넣지 않는다
 *
 * `BundledStaff.ALL` 은 동료 탭·동명이인 A/B 배정·통계의 근거다. 거기에 가짜 한 명을 끼우면
 * 그 모든 계산이 한 명분씩 틀어진다. 그래서 명단은 한 글자도 건드리지 않고 **로그인 앞단에서만**
 * 갈라 낸다.
 *
 * ## 어떻게 격리되나 — 새 필드 없이 [User.visibleToOthers] 하나로
 *
 * 이 계정은 `visibleToOthers = false` 로 만든다. 그러면
 * `FirestoreUserRepository.publish` 가 **`users` 컬렉션에 쓰지 않고 지운다** — 즉
 * 동료 탭 목록·근무 매트릭스·동명이인 처리·통계·공유 이미지·관리자 대리등록 어디에도
 * 나타날 수 없다. 그 화면들은 전부 `users` 컬렉션 하나만 읽기 때문이다.
 * **화면마다 필터를 다는 것보다 이 한 줄이 안 새는 길이다** — 필터는 새 화면이 생길 때마다
 * 빠뜨릴 수 있지만 서버에 문서가 없으면 빠뜨릴 화면이 없다.
 * 앱 어디에도 `visibleToOthers` 를 켜는 스위치가 없으므로 심사관이 되돌릴 수도 없다.
 *
 * 관리자 화면·식단표 업로드는 `AdminGate` 의 SHA-256 화면잠금이 막는다 — 심사관은 그 암호를
 * 모르고, 이 계정이라 해서 따로 열리지 않는다(읽기 전용 화면은 전부 정상 동작).
 *
 * ⚠ **이 값은 플레이 콘솔 `앱 액세스 권한` 섹션에 등록돼 있다. 바꾸면 콘솔도 같이 바꿔야 하고,
 * 안 바꾸면 다음 심사가 같은 이유로 또 거부된다.**
 */
object ReviewerAccount {

    const val NAME = "구글심사"
    const val EMP_NO = "00000000"

    /**
     * 심사 계정인가.
     *
     * 앞뒤 공백만 털고 **정확히 일치**해야 한다. 공백을 터는 이유는 이 계정이 존재하는 목적
     * 자체가 *"심사관이 로그인에 실패하지 않는 것"* 이기 때문이다 — 폰 키보드가 자동으로 붙이는
     * 끝 공백 하나로 다시 거부당하면 이 계정을 만든 의미가 없다. 그 밖의 차이(`구글 심사`,
     * `구글심사원`, 자리수가 다른 사번)는 전부 거부하고 평소대로 명단 대조로 내려간다.
     */
    fun matches(name: String, empNo: String): Boolean =
        name.trim() == NAME && empNo.trim() == EMP_NO

    /** 심사관에게 보일 계정. 소속은 신정지선 기관사 — 근무·행로표·알람·침실 칩이 다 나온다 */
    fun user(): User = User(
        uid = EMP_NO,
        name = NAME,
        role = CrewRole.DRIVER_BRANCH,
        patternId = Bundled.BRANCH_PATTERN.id,
        patternOffset = 0,
        // ⬇ 이 한 줄이 격리 전부다 (위 KDoc)
        visibleToOthers = false,
    )
}
