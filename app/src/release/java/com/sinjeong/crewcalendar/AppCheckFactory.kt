package com.sinjeong.crewcalendar

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * 릴리즈 빌드용 App Check 공급자(v1.6.86) — Play Integrity.
 *
 * ⚠ 콘솔은 아직 **미적용(unenforced)** 이다. 지표(정상 요청 비율)를 본 뒤 강제로 바꾸는 것은
 *   **코디네이터만** 한다. 미적용 동안에는 토큰을 못 받아도 앱 동작에 영향이 없다.
 */
internal fun appCheckFactory(): AppCheckProviderFactory = PlayIntegrityAppCheckProviderFactory.getInstance()
