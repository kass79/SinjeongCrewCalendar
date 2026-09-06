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
 * ### v1.7.9 ⑧-3(2026-09-07) — **"넣지 않는다" 만 바뀌었다**
 * 카스 답: 견습 기관사 10명은 *"견습기관사(지금은 본선기관사 아무곳이나) 곧 배정 받음"* ·
 * *"본인이 로그인해서 근무선택한 다이아"*, 육아휴직 2명은 *"휴직으로 넣고 본인이 로그인해서
 * 근무선택하면 그 다이아로"*. → 이제 **이름은 내장 명단에 세우되 근무 칸만 비운다**
 * ([BundledRoster.UNASSIGNED] `미배정` · [BundledRoster.ON_LEAVE] `휴직`).
 * **위 *"본인이 고른 것이 정답"* 원칙은 그대로다** — 열두 명 중 누구든 로그인해 근무를 고르면
 * `users` 줄이 생기고 아래 `liveNames` 대조가 감시값 줄을 **이름으로** 지운다(동명이인 아님).
 * 그래서 감시값은 **아무도 안 골랐을 때만** 보이는 자리표시자다. 이 규칙은 한 줄도 안 바꿨다
 * (`MatesTest.trainee_row_gives_way_to_the_offset_they_pick` 가 잠근다).
 *
 * v1.7.9(2026-09-06) 에 위 *"갔을꺼야"* 가 **확정**됐다 — 박희수는 지선([BundledRoster.BRANCH] 5),
 * 강민성은 4조2교대 B조([BundledRoster.SHIFT_4_2])로 내장 명단이 따라갔다. 그래서 이 두 사람은
 * 이제 live 가 없어도 제 소속으로 뜬다(두 줄로 뜨던 아홉 명 중 여섯이 이번에 정리됐다).
 *
 * ## 예외 — 동명이인은 종전대로 `이름+소속`으로만 지운다
 * 이름만 보고 지우면 **다른 사람**이 사라진다(기관사 김지환이 로그인하면 차장 김지환이 명단에서
 * 증발한다). 동명이인 판정은 [BundledRoster.dupSuffix]가 명단에서 이미 자동으로 도출해 둔 것을
 * 그대로 쓴다 — `A`/`B` 접미가 붙는 이름이 곧 "내장 명단에 두 줄 이상인 이름"이다.
 *
 * 관리자 대리등록(`RosterEntry.addedBy == "admin"`) 줄도 같은 `users` 문서라 live로 똑같이 다룬다.
 * 반면 **수동등록 동료([Mate])는 live가 아니다** — 내장 줄을 지울 권한이 없고 종전 규칙 그대로다.
 *
 * ## ⚠ v1.7.10 ③ — *"고른 적 없는 줄"* 은 live 가 아니다 (2026-09-04 확정과 **다른 이야기**)
 * 위 *"본인이 고른 것이 정답"* 은 **그대로다.** 이번에 걸러 내는 것은 본인이 **고른 적이 없는데도**
 * 로그인만으로 만들어져 있던 줄뿐이다([BundledRoster.isLoginDefaultRow] 가 그 서명을 판정한다).
 * 고른 줄은 한 줄도 안 건드린다 — 소속이 달라도, 내장값과 어긋나도 종전대로 이긴다.
 *
 * 걸린 줄은 **버리지 않고 내장값으로 고쳐 쓴다**([withoutLoginDefault]). 버리면 `uid` 가 같이
 * 사라져 그 사람 행에서 **근무변경(rosterOverrides)이 안 붙고 ★즐겨찾기 uid 조회도 빈다**
 * (`MatesScreen.uidByKey` → `MatrixRow.overrides`). 이름이 동명이인이라 어느 내장 줄인지
 * 못 고를 때만 줄을 버린다 — 그때는 내장 두 줄이 `dupSuffix` 예외로 그대로 살아남는다.
 */
fun mergeRoster(
    me: MatrixPerson?,
    liveUsers: List<RosterEntry>,
    mates: List<Mate>,
): List<MatrixPerson> {
    val chosen = liveUsers.mapNotNull(::withoutLoginDefault)
    val taken = mutableSetOf<String>()
    me?.let { taken += it.key }
    val live = chosen.filter { mateKey(it.name, it.group) !in taken && it.uid != me?.uid }
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
    // ⚠ [chosen] 이다(원본 `liveUsers` 가 아니다) — 동명이인이라 버린 줄은 이름을 가리면 안 된다.
    val liveNames = chosen.mapTo(mutableSetOf()) { it.name.trim() }
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

/**
 * 로그인 기본값 서명([BundledRoster.isLoginDefaultRow])에 걸린 live 줄을 **내장 명단 줄로 고쳐 쓴다.**
 * 서명이 아니면 원본 그대로 돌려주고, 고쳐 쓸 내장 줄을 하나로 못 고르면 `null`(=그 줄을 버린다).
 *
 * `uid` 와 `name` 은 **그대로 실어 나른다** — `uid` 가 빠지면 그 사람 행에서 근무변경이 조용히
 * 사라진다(v1.6.86 ★즐겨찾기 사고와 같은 자리).
 *
 * 동명이인(김지환·박두원·이용석)은 이름만으로 어느 내장 줄인지 못 고른다 — 찍으면 절반은 틀린
 * 소속·교번을 사실처럼 그린다. 그래서 **줄을 버린다**: `liveNames` 대조가 `dupSuffix != null` 인
 * 이름을 이미 예외로 두므로 내장 두 줄이 둘 다 제 값으로 살아남는다(한 줄 잃는 것이 아니라
 * 가짜 한 줄이 빠지는 것이다).
 *
 * ⚠ 실제 지선 0(김학진)·차장 0(홍대종)은 **자기 내장 줄로 되돌아온다** — 값이 같아 무변화이고
 * `uid` 도 남는다. 서명이 그 두 사람에게 무해하다는 근거가 이것이다.
 */
private fun withoutLoginDefault(e: RosterEntry): RosterEntry? {
    if (!BundledRoster.isLoginDefaultRow(e.name, e.group, e.patternOffset)) return e
    val (group, offset) = BundledRoster.realEntriesFor(e.name).singleOrNull() ?: return null
    return e.copy(group = group, patternOffset = offset)
}
