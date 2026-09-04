package com.sinjeong.crewcalendar.presentation.roster

import com.sinjeong.crewcalendar.domain.model.BundledRoster
import com.sinjeong.crewcalendar.domain.model.CrewGroup
import com.sinjeong.crewcalendar.domain.model.Mate
import com.sinjeong.crewcalendar.domain.repository.RosterEntry

/*
 * 동료 명단을 **합치는 규칙**만 있는 파일. 그리는 법은 [DutyMatrix.kt]에 있다.
 *
 * 왜 갈랐나(v1.6.87): `DutyMatrixKt`의 정적 초기화가 Compose `Dp`와 폰트 리소스를 잡아서,
 * 이 규칙이 거기 있으면 **테스트에서 클래스 로드 자체가 실패한다** — 이 저장소는 한글 경로 탓에
 * `gradlew test`를 못 쓰고 JUnitCore를 최소 classpath로 직접 띄우는 게 유일한 실행 경로다.
 * 패키지는 그대로라 부르는 쪽 import는 한 줄도 바뀌지 않았다.
 */

/** 동료 식별 키 — 이름만으로는 그룹 간 동명이인 3쌍(김지환·박두원·이용석)이 충돌한다 */
fun mateKey(name: String, group: CrewGroup) = "$name|${group.name}"

/** 즐겨찾기·전화번호는 " (나)" 꼬리표 없는 실제 이름으로 매칭한다 */
val MatrixPerson.cleanName: String get() = name.removeSuffix(" (나)").trim()

val MatrixPerson.key: String get() = mateKey(cleanName, group)

/**
 * 동료 탭 전체 명단 합성 — **나 → 로그인 근무자(live) → 수동등록 동료 → 내장 명단** 순으로
 * 겹치는 것을 지운다. 합치는 규칙은 여기 한 곳에만 둔다.
 *
 * ## live 줄이 있으면 내장 명단 줄은 **소속이 달라도** 버린다 (v1.6.87)
 * 종전 중복 제거 기준은 `이름+소속`([mateKey])이었다. 그래서 **앱에서 자기 소속을 고른 사람**은
 * 두 줄로 떴다 — 내장 줄은 옛 소속·옛 교번이고 `uid`가 없어 근무변경도 안 붙는 **틀린 줄**이다
 * (에뮬 실측 9명: 강성진·김형준·문성진·박경훈·박형렬·박희수·서상훈·정재헌·차병철).
 *
 * 사용자 확정(2026-09-04): *"본인이 앱에서 고른 소속과 근무로 가야지.. 아마 박희수는 본선으로
 * 강민성은 4조2교대로 갔을꺼야..둘이 근무를 바꾸어서..그리고 김성민·김충현·원두환, 선철호,
 * 장도영 등은 기관사 견습이라..곧 기관사가 될꺼야..본인이 근무를 고를 꺼야"* → **live가 정답**이고,
 * 견습은 내장 명단에 넣지 않는다(로그인해서 본인이 고르면 live 줄로 나타난다).
 *
 * ## 예외 — 동명이인은 종전대로 `이름+소속`으로만 지운다
 * 이름만 보고 지우면 **다른 사람**이 사라진다(기관사 김지환이 로그인하면 차장 김지환이 명단에서
 * 증발한다). 동명이인 판정은 [BundledRoster.dupSuffix]가 명단에서 이미 자동으로 도출해 둔 것을
 * 그대로 쓴다 — `A`/`B` 접미가 붙는 이름이 곧 "내장 명단에 두 줄 이상인 이름"이다.
 *
 * 관리자 대리등록(`RosterEntry.addedBy == "admin"`) 줄도 같은 `users` 문서라 live로 똑같이 다룬다.
 * 반면 **수동등록 동료([Mate])는 live가 아니다** — 내장 줄을 지울 권한이 없고 종전 규칙 그대로다.
 */
fun mergeRoster(
    me: MatrixPerson?,
    liveUsers: List<RosterEntry>,
    mates: List<Mate>,
): List<MatrixPerson> {
    val taken = mutableSetOf<String>()
    me?.let { taken += it.key }
    val live = liveUsers.filter { mateKey(it.name, it.group) !in taken && it.uid != me?.uid }
        .map {
            taken += mateKey(it.name, it.group)
            MatrixPerson(it.name, it.group, it.patternOffset, isMe = false, uid = it.uid)
        }
    val manual = mates.filter { mateKey(it.name, it.group) !in taken }
        .map {
            taken += mateKey(it.name, it.group)
            MatrixPerson(it.name, it.group, it.patternOffset, isMe = false)
        }
    // 내 줄도 live다 — 로그인 직후 `users` 문서가 아직 안 돌아왔어도 내 내장 줄이 남으면 안 된다.
    val liveNames = liveUsers.mapTo(mutableSetOf()) { it.name.trim() }
    me?.let { liveNames += it.cleanName }
    val bundled = CrewGroup.entries.flatMap { g ->
        BundledRoster.forGroup(g)
            .filterNot { (name, _) ->
                mateKey(name, g) in taken ||
                    (name in liveNames && BundledRoster.dupSuffix(name, g) == null)
            }
            .map { (name, off) -> MatrixPerson(name, g, off, isMe = false) }
    }
    // ⚠ **마지막 `distinctBy`가 크래시 방지선이다**(v1.6.60). 위 `taken` 대조는 네 갈래
    // *사이의* 중복만 막는다 — **한 갈래 안에 같은 이름+소속이 둘 있으면 그대로 통과한다.**
    // `live`의 `filter`는 `map`이 `taken`을 채우기 전에 전부 평가되므로 특히 무방비다:
    // Firestore `users`에 같은 사람이 사번 두 개로 들어가면(테스트 계정·사번 변경 등)
    // `LazyColumn(key = rows[it].key)`가 **`Key ... was already used`로 앱을 죽인다.**
    // v1.6.60에 에뮬레이터 검증 중 실제로 재현했다. 화면이 죽는 것보다 한 줄만 보이는 게 낫다.
    return (listOfNotNull(me) + live + manual + bundled).distinctBy { it.key }
}
