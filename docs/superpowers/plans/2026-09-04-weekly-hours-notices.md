# 주별 근무시간(주52시간) · 관리자 공지 카드 — 구현 계획 (2차, v1.6.89 / 101)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 월 메모 화면 맨 위에 그 달의 주별 근무시간(월~일, 52h 초과 빨강)을 보여 주고, 관리자가 쓴 공지를 달력 위 카드로 전원에게 보여 준다.

**Architecture:** (E) 순수 함수 `WeeklyHours.compute(month, days)`가 달력 상태(`List<DaySchedule>`)에서 주별 합계를 만들고 `MemoListSheet`가 헤더 아래 한두 줄로 그린다 — 저장소·쿼리 없음. (F) Firestore `notices` 컬렉션 + `NoticeRepository`(식단표 `MenuRepository`와 같은 모양) + 설정의 관리자 항목 "공지 쓰기" + 달력 상단 `NoticeBanner`(닫은 id는 로컬 기억).

**Tech Stack:** Kotlin/Compose, Firestore, JUnit4(`tools/runtests.ps1`). 설계서 `docs/superpowers/specs/2026-09-04-widget-map-delay-design.md` E·F절.

## Global Constraints

- 두 파트는 **서로 다른 에이전트가 동시에** 한다. E는 `domain/usecase/WeeklyHours.kt`(신규)·`presentation/calendar/MemoListSheet.kt`·테스트만. F는 `domain/model/Notice.kt`(신규)·`domain/repository/Repositories.kt`(인터페이스 추가)·`data/remote/FirestoreRepositories.kt`·`di/AppModule.kt`(바인딩)·`presentation/notice/*`(신규)·`presentation/settings/SettingsScreen.kt`(항목 1개)·`MainActivity.kt`(라우트 1개)·`presentation/calendar/MainCalendarScreen.kt`(배너 삽입 — **한 곳만**, 최소 diff)·`firestore.rules`·테스트. **겹치는 파일 없음** — 상대 파일은 건드리지 마라.
- 버전·`docs/project-notes.md`·릴리즈·`firebase deploy`·규칙 배포는 코디네이터. 커밋은 태스크마다 바뀐 파일만 `git add <경로>`, push 금지.
- 테스트: `.\gradlew.bat :app:compileDebugUnitTestKotlin` → `powershell -ExecutionPolicy Bypass -File tools\runtests.ps1`, 기준선 **234건**, 회귀 0. 도메인 파일은 안드로이드 import 0.
- 에뮬 emulator-5554(debug, 심사 계정): E가 먼저 쓴다(메모 시트 스크린샷 2장). F는 배너·관리자 화면 검증을 **마지막**에, `adb shell dumpsys window | findstr mCurrentFocus`로 비었을 때. 근무변경·로그인 변경 금지. **심사 계정으로는 관리자 화면에 못 들어간다**(암호) → 관리자 쓰기 경로는 컴파일·유닛 테스트로만 확인하고, 배너 표시는 코디네이터가 콘솔에서 공지 문서를 1건 넣어 준 뒤 확인한다(F Task 4).
- 사용자 확정(스킬 표) 불변. `rememberSaveable` 함정 — 새 상태는 `remember`.

---

## E. 주별 근무시간

### Task E1: `WeeklyHours` 순수 계산 + 테스트

**Files:**
- Create: `app/src/main/java/com/sinjeong/crewcalendar/domain/usecase/WeeklyHours.kt`
- Test: `app/src/test/java/com/sinjeong/crewcalendar/WeeklyHoursTest.kt`

**Interfaces:**
```kotlin
object WeeklyHours {
    data class Week(val index: Int, val from: LocalDate, val to: LocalDate, val minutes: Int, val excluded: List<String>)
    /** 그 달의 주(월~일) 목록. from/to는 그 달 안으로 잘린다. excluded = 시간 미정으로 0 처리한 근무의 표시명(중복 제거) */
    fun compute(month: YearMonth, days: List<DaySchedule>): List<Week>
    /** 하루 근무시간(분). 야간은 시작일에 전부. 모르면 null(→ excluded) */
    fun minutesOf(day: DaySchedule): Int?
    fun label(w: Week): String   // "1주 50.1h" / 월 걸친 주는 "1주(1~6일) 12.5h"
    const val LIMIT_MIN = 52 * 60
}
```
- `minutesOf` 규칙(설계서 E절 그대로):
  1. `duty.type`이 `REST`·`BRANCH_REST`·`POST_NIGHT`면 0.
  2. `duty.fill != null`(충당·대기충당·교체)이면 `DutyCode.parse(duty.diaRaw)`로 채운 근무를 만들어 그 근무로 계산. 번호가 없으면 null.
  3. 행로표 `계`가 있으면 그것: 본선 주간 `RouteTable.forMainDay(n, Bundled.isHolidayTimetable(date))`, 본선 야간 `RouteTable.forMainNight(n, Bundled.comboOf(date))`(`isStandbyOnly`도 그 표의 계 그대로), 지선 `RouteTable.forBranch(n, holiday)`. `totalWorkTime` `"H:MM"` → 분.
  4. 없으면 `Bundled.timeRowFor(duty, date)`의 `signOff − signOn`(24시+ 표기는 `MyTrain.kt`의 `legTime`처럼 접는다; 음수면 +24h). 대기(대N·지대N)가 여기로 온다.
  5. 그래도 없으면 — 휴가류(`연차·보상·대휴·촉연·기타휴가`)는 0, 나머지(`교육·회행·지근·직접입력` 등)는 null(→ excluded에 `duty.display`).
- `compute`: 그 달 1일이 속한 주의 월요일부터 7일씩. 각 주의 `from/to`를 달 안으로 잘라 합산. `index`는 1부터.

- [ ] **Step 1: 테스트**

```kotlin
package com.sinjeong.crewcalendar

import com.sinjeong.crewcalendar.domain.model.DaySchedule
import com.sinjeong.crewcalendar.domain.model.DutyCode
import com.sinjeong.crewcalendar.domain.usecase.WeeklyHours
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class WeeklyHoursTest {
    private fun d(y: Int, m: Int, day: Int, raw: String) = DaySchedule(LocalDate.of(y, m, day), DutyCode.parse(raw))

    @Test fun `본선 주간 계 그대로`() {           // 2026-09-04(금) 평일, 1번 계 10:04
        assertEquals(10 * 60 + 4, WeeklyHours.minutesOf(d(2026, 9, 4, "1")))
    }
    @Test fun `휴무 비번 연차는 0`() {
        assertEquals(0, WeeklyHours.minutesOf(d(2026, 9, 4, "휴5")))
        assertEquals(0, WeeklyHours.minutesOf(d(2026, 9, 4, "~")))
        assertEquals(0, WeeklyHours.minutesOf(d(2026, 9, 4, "연차")))
    }
    @Test fun `대기는 출퇴근 차`() {               // 대1 7:00~16:00
        assertEquals(9 * 60, WeeklyHours.minutesOf(d(2026, 9, 4, "대1")))
    }
    @Test fun `충당은 채운 근무의 시간`() {
        assertEquals(WeeklyHours.minutesOf(d(2026, 9, 4, "1")), WeeklyHours.minutesOf(d(2026, 9, 4, "충당 1")))
    }
    @Test fun `교육은 미정`() { assertNull(WeeklyHours.minutesOf(d(2026, 9, 4, "교육"))) }
    @Test fun `야간은 시작일 주에 전부`() {           // 2026-09-09(수) 야간 14 → 그 주에 계 전부, 9/10(~)은 0
        val days = listOf(d(2026, 9, 9, "14"), d(2026, 9, 10, "~"))
        val w = WeeklyHours.compute(YearMonth.of(2026, 9), days)
        val week = w.first { it.from <= LocalDate.of(2026, 9, 9) && it.to >= LocalDate.of(2026, 9, 9) }
        assertEquals(WeeklyHours.minutesOf(d(2026, 9, 9, "14")), week.minutes)
    }
    @Test fun `주는 월~일이고 달 안으로 잘린다`() {   // 2026-09-01은 화요일 → 1주 = 1~6일
        val w = WeeklyHours.compute(YearMonth.of(2026, 9), emptyList())
        assertEquals(LocalDate.of(2026, 9, 1), w[0].from); assertEquals(LocalDate.of(2026, 9, 6), w[0].to)
        assertEquals(LocalDate.of(2026, 9, 28), w.last().from); assertEquals(LocalDate.of(2026, 9, 30), w.last().to)
        assertEquals("1주(1~6일) 0.0h", WeeklyHours.label(w[0]))
    }
    @Test fun `미정 근무는 excluded에 표시명으로`() {
        val w = WeeklyHours.compute(YearMonth.of(2026, 9), listOf(d(2026, 9, 2, "교육")))
        assertEquals(listOf("교육"), w[0].excluded)
    }
}
```

`DaySchedule` 생성자 기본값이 위와 다르면(필수 인자 추가) 테스트의 `d()`만 맞춘다.

- [ ] **Step 2: 실패 확인 → Step 3: 구현(위 규칙 그대로, 안드로이드 import 0) → Step 4: 통과(회귀 0 + 8건) → Step 5: 커밋** `"WeeklyHours — 주별 근무시간 계산(월~일, 야간 시작일 귀속, 미정 근무 표시)"`

### Task E2: 메모 시트 헤더에 표시

**Files:** Modify `presentation/calendar/MemoListSheet.kt:70-82`(헤더 Row 아래에 한 블록)

- [ ] 헤더 `Row` 바로 아래에:

```kotlin
            val weeks = remember(month, days) { WeeklyHours.compute(month, days) }
            FlowRow(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                weeks.forEach { w ->
                    val over = w.minutes > WeeklyHours.LIMIT_MIN
                    Text(
                        WeeklyHours.label(w) + if (w.excluded.isNotEmpty()) " (${w.excluded.joinToString("·")} 미포함)" else "",
                        fontSize = 12.5.sp, fontWeight = if (over) FontWeight.ExtraBold else FontWeight.Bold,
                        color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
```

`FlowRow`는 `androidx.compose.foundation.layout.FlowRow`(`@OptIn(ExperimentalLayoutApi::class)`). 메모가 0건이어도 주별 시간은 보인다(빈 안내문 위).

- [ ] 에뮬 검증: 메모 모아보기 시트 스크린샷 접힘·배율1.5 (`_미리보기_v1.6.89/H01_주별근무시간.png`, `H02_배율1.5.png`) — 심사 계정 9월(지선 패턴)로 값이 나오는지, 52h 초과 주가 있으면 빨간지. 원복 `font_scale 1.0`.
- [ ] 커밋 `"메모 모아보기 맨 위에 주별 근무시간(52h 초과 빨강)"`.

---

## F. 관리자 공지 카드

### Task F1: 모델·저장소·규칙

**Files:**
- Create `domain/model/Notice.kt`:
```kotlin
data class Notice(val id: String, val title: String, val body: String, val from: LocalDate, val to: LocalDate, val createdAt: Long) {
    fun isActive(today: LocalDate) = !today.isBefore(from) && !today.isAfter(to)
}
```
- Modify `domain/repository/Repositories.kt`:
```kotlin
interface NoticeRepository {
    /** 오늘 기간 안의 공지(최신순). 오류 시 직전 값 유지(식단표와 같은 규칙) */
    fun observeActive(today: LocalDate): Flow<List<Notice>>
    suspend fun save(n: Notice): AdminWriteResult
    suspend fun delete(id: String): AdminWriteResult
}
```
- Modify `data/remote/FirestoreRepositories.kt`: `class FirestoreNoticeRepository @Inject constructor() : NoticeRepository` — 컬렉션 `notices`, 문서 `{title, body, from:"yyyy-MM-dd", to:"yyyy-MM-dd", createdAt: Timestamp, author:"admin"}`, `observeActive`는 `whereGreaterThanOrEqualTo("to", today.toString())` 후 클라이언트에서 `from <= today` 필터, `addSnapshotListener { snap, e -> if (e != null) { Log.w(...); return@addSnapshotListener } … }`. `save`는 `set(id)`(id 비면 `document().id`), `delete`. `AdminWriteResult`는 식단표와 같은 타입.
- Modify `di/AppModule.kt`: `@Binds`(식단표 바인딩 옆에 같은 형태).
- Modify `firestore.rules`: `menus` 블록 뒤에

```
    // ── notices/{id} — 관리자 공지 (v1.6.89). 앱 화면 잠금 + 모양 검사(관리자 강제 불가 — menus 주석 참조)
    match /notices/{id} {
      allow read: if signedIn();
      allow create, update: if signedIn()
        && request.resource.data.keys().hasAll(['title', 'body', 'from', 'to', 'createdAt', 'author'])
        && request.resource.data.keys().hasOnly(['title', 'body', 'from', 'to', 'createdAt', 'author'])
        && request.resource.data.title is string && request.resource.data.title.size() <= 40
        && request.resource.data.body is string && request.resource.data.body.size() <= 500
        && request.resource.data.from is string && request.resource.data.from.matches('^[0-9]{4}-[0-9]{2}-[0-9]{2}$')
        && request.resource.data.to is string && request.resource.data.to.matches('^[0-9]{4}-[0-9]{2}-[0-9]{2}$')
        && request.resource.data.createdAt is timestamp
        && request.resource.data.author == 'admin';
      allow delete: if signedIn();
    }
```
- Test: `NoticeTest.kt` — `Notice.isActive(today)` 3건(안·경계·밖).
- [ ] 컴파일·테스트·커밋 `"공지(notices) 모델·저장소·규칙"`.

### Task F2: 관리자 화면 "공지 쓰기"

**Files:** Create `presentation/notice/NoticeAdminScreen.kt`(+ `NoticeAdminViewModel`), Modify `MainActivity.kt`(라우트 `"noticeAdmin"`, `menuAdmin` 옆), `SettingsScreen.kt:393` 근처(항목 `"공지 쓰기"` — 부제 `"달력 맨 위에 전원에게 보이는 공지 (암호 필요)"`, 기존 두 항목과 같은 잠금 방식 `AdminGate`).

- [ ] 화면: 목록(제목·기간·삭제) + 작성 폼(제목 ≤40, 본문 ≤500, 시작일 기본 오늘, 종료일 기본 +7일, 날짜는 `DatePickerDialog`), 저장 결과 스낵바. 식단표 관리 화면의 구조·스타일을 따른다.
- [ ] 커밋 `"관리자 공지 쓰기 화면"`.

### Task F3: 달력 상단 배너

**Files:** Create `presentation/notice/NoticeBanner.kt`, Modify `presentation/calendar/MainCalendarScreen.kt`(달력 상단바와 요일 줄 사이 — 한 줄 삽입), `MainCalendarViewModel`(공지 Flow 노출, `observeActive(LocalDate.now())`).

- [ ] `NoticeBanner(notices: List<Notice>, dismissedIds: Set<String>, onDismiss: (String) -> Unit, onOpen: (Notice) -> Unit)`: 기간 안·안 닫은 것 중 최신 1건. 카드(`surfaceVariant`, 12dp): 왼쪽 `campaign` 아이콘, 제목(굵게) + 본문 2줄(`Ellipsis`), 오른쪽 X. 탭하면 전체 본문 다이얼로그. 닫은 id는 `SharedPreferences("settings")`의 `dismissed_notices`(StringSet)에 저장 — 새 공지는 다시 뜬다.
- [ ] 배너가 없으면 높이 0(레이아웃 변화 없음). 폴드 펼침·배율 1.5에서 잘림 없음.
- [ ] 커밋 `"달력 상단 공지 배너(닫기 기억)"`.

### Task F4: 검증

- [ ] 유닛 테스트 회귀 0. `installDebug`. 심사 계정은 관리자 암호가 없어 **쓰기 화면은 진입만 확인**(암호창까지, `N01_설정_공지쓰기_항목.png`).
- [ ] 배너 표시는 **코디네이터가 Firebase 콘솔에서 `notices` 문서 1건**을 넣은 뒤 확인한다 → 보고에 "코디네이터 문서 필요"라고 적고, 문서가 있으면 `N02_배너.png`·`N03_배너_전체보기.png`·`N04_닫은뒤.png`를 찍어라. 없으면 배너 없는 화면(`N05_배너없음.png`)만.
- [ ] 보고: 커밋 해시, 테스트 건수, 규칙 diff 요약(코디네이터 배포용), 스크린샷, 못 한 것.
