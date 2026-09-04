package com.sinjeong.crewcalendar

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * 디버그 빌드용 App Check 공급자(v1.6.86).
 *
 * Play Integrity 는 플레이스토어가 서명·설치한 앱만 인정하므로 에뮬레이터·디버그 서명에서는
 * 절대 통과하지 못한다. 그래서 디버그 빌드는 **디버그 공급자**를 쓴다 — 로그캣에 찍히는
 * 토큰을 콘솔 App Check 의 "디버그 토큰"에 등록해야 실제 토큰이 발급된다.
 *
 * 이 파일은 `src/debug` 소스셋이라 **릴리즈 APK 에는 들어가지 않는다**
 * (`debugImplementation(libs.firebase.appcheck.debug)` 과 짝).
 */
internal fun appCheckFactory(): AppCheckProviderFactory = DebugAppCheckProviderFactory.getInstance()
