# 위젯 2x1·3x1·4x1 + 근무색 + 글자 배율 대응 — 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 홈 위젯을 런처 목록에 2x1·3x1·4x1 세 항목으로 내고, 칸 색을 앱 달력과 같게 하며, 폰 글자 배율(1.0~1.5)에서 잘림 0으로 만든다.

**Architecture:** 기존 `DutyWidget`(Glance, `SizeMode.Responsive`) 하나를 그대로 두고 리시버만 3개로 늘린다(같은 위젯 클래스). 데이터는 `DutyWidgetWorker`가 쓰는 `KEY_WEEK` 문자열 한 곳만 확장하고, 파싱/직렬화는 안드로이드 의존 없는 `WidgetStrip.kt`로 뽑아 JUnit이 잠근다. 색은 `util/MonthImage.kt`의 `dutyColors`를 `util/DutyPalette.kt`로 옮겨 공유한다.

**Tech Stack:** Kotlin, Jetpack Glance(appwidget), WorkManager, JUnit4 (JUnitCore 직접 실행 — `tools/runtests.ps1`).

## Global Constraints

- 설계서: `docs/superpowers/specs/2026-09-04-widget-map-delay-design.md` A절. 위젯에 "추가 줄"(침실 등)은 **넣지 않는다**(3번 결정). 2x1의 시각 한 줄은 설계에 포함된 내용이다.
- 버전(`app/build.gradle.kts`)은 **올리지 않는다** — 릴리즈는 코디네이터가 한다. `docs/project-notes.md`도 건드리지 않는다.
- 다른 에이전트가 `presentation/live/*`, `domain/model/Line2Timetable*`, `widget/DeadheadAlarm.kt`, `tools/fetch_line2_timetable.py`를 동시에 수정한다 → 이 계획은 `widget/DutyWidget.kt`, `widget/DutyWidgetWorker.kt`, 신규 `widget/WidgetStrip.kt`, `widget/DutyWidgetReceivers.kt`, `util/DutyPalette.kt`, `util/MonthImage.kt`(색 함수 이동만), `res/xml/duty_widget_info*.xml`, `res/layout/widget_duty_preview*.xml`, `res/values/strings.xml`, `AndroidManifest.xml`(위젯 리시버 2개 추가만), 테스트 파일만 만진다.
- 커밋은 태스크마다, 바뀐 파일만 `git add <경로>`. `git add -A` 금지. push 금지(코디네이터).
- 테스트: `.\gradlew.bat :app:compileDebugUnitTestKotlin` 후 `powershell -ExecutionPolicy Bypass -File tools\runtests.ps1`. 기준선 **211 OK**. `gradlew test`는 한글 경로에서 실패한다.
- 에뮬레이터 emulator-5554: debug 빌드·심사 계정 로그인 유지. 근무변경·로그인 변경 금지. 끝나면 `wm size reset`·`wm density reset`·`settings put system font_scale 1.0`.
- 기존 4x1 위젯을 이미 놓은 사용자가 있다 → `DutyWidgetReceiver` 클래스명·`duty_widget_info.xml` 파일명은 **바꾸지 않는다**.

---

### Task 1: `WidgetStrip` — 레코드 확장(타입·시각)과 순수 파서

**Files:**
- Create: `app/src/main/java/com/sinjeong/crewcalendar/widget/WidgetStrip.kt`
- Modify: `app/src/main/java/com/sinjeong/crewcalendar/widget/DutyWidgetWorker.kt:42-49` (strip 생성)
- Modify: `app/src/main/java/com/sinjeong/crewcalendar/widget/DutyWidget.kt:78,92-95` (Cell → WidgetStrip.Cell)
- Test: `app/src/test/java/com/sinjeong/crewcalendar/WidgetStripTest.kt`

**Interfaces:**
- Produces: `data class Cell(val dow: String, val day: String, val duty: String, val red: Boolean, val type: DutyType?, val time: String)`; `fun encodeStrip(cells: List<Cell>): String`; `fun decodeStrip(s: String): List<Cell>` (4칸 옛 레코드도 읽는다 — type=null, time="").
- `time`은 **접두어 포함** 문자열: 출근이면 `"출근 07:47"`, 출근이 없고 편승 알람 권장 시각이 있으면 `"편승 12:36"`, 둘 다 없으면 `""`.
- Consumes: `DutyType`(domain), `BundledTimetable.advise(duty, date).at`(편승 시각), `DaySchedule.signOn`(출근 시각 문자열 "7:47").

- [ ] **Step 1: 실패하는 테스트 작성**

```kotlin
package com.sinjeong.crewcalendar

import com.sinjeong.crewcalendar.domain.model.DutyType
import com.sinjeong.crewcalendar.widget.Cell
import com.sinjeong.crewcalendar.widget.decodeStrip
import com.sinjeong.crewcalendar.widget.encodeStrip
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetStripTest {
    @Test fun `6칸 레코드 왕복`() {
        val cells = listOf(
            Cell("금", "4", "~", false, DutyType.POST_NIGHT, ""),
            Cell("토", "5", "휴2", true, DutyType.REST, ""),
            Cell("일", "6", "14", true, DutyType.MAIN_DAY, "출근 07:47"),
        )
        assertEquals(cells, decodeStrip(encodeStrip(cells)))
    }

    @Test fun `옛 4칸 레코드도 읽는다`() {
        val old = "화|28|5|0;수|29|휴3|1"
        assertEquals(
            listOf(Cell("화", "28", "5", false, null, ""), Cell("수", "29", "휴3", true, null, "")),
            decodeStrip(old),
        )
    }

    @Test fun `깨진 레코드는 버리고 나머지는 산다`() {
        assertEquals(1, decodeStrip("화|28;수|29|휴3|1").size)
    }

    @Test fun `구분자가 값에 들어와도 깨지지 않는다`() {
        val c = Cell("금", "4", "충당|지6;", false, DutyType.STANDBY, "")
        assertEquals("충당지6", decodeStrip(encodeStrip(listOf(c)))[0].duty)
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `.\gradlew.bat :app:compileDebugUnitTestKotlin` → Expected: `Unresolved reference: Cell` 컴파일 실패.

- [ ] **Step 3: 구현**

`widget/WidgetStrip.kt` (안드로이드 import 0):

```kotlin
package com.sinjeong.crewcalendar.widget

import com.sinjeong.crewcalendar.domain.model.DutyType

/**
 * 위젯 한 칸. `KEY_WEEK` 한 레코드 = `요일|일자|근무|빨강|타입|시각`.
 * v1.6.88에서 뒤 두 칸(타입·시각)을 붙였다 — 4칸짜리 옛 레코드는 type=null·time="" 로 읽는다.
 * time 은 접두어 포함("출근 07:47" / "편승 12:36" / "").
 */
data class Cell(
    val dow: String, val day: String, val duty: String, val red: Boolean,
    val type: DutyType?, val time: String,
)

private val SEP = Regex("[|;]")

fun encodeStrip(cells: List<Cell>): String = cells.joinToString(";") { c ->
    listOf(c.dow, c.day, c.duty, if (c.red) "1" else "0", c.type?.name.orEmpty(), c.time)
        .joinToString("|") { it.replace(SEP, "") }
}

fun decodeStrip(s: String): List<Cell> = s.split(";").mapNotNull { rec ->
    val p = rec.split("|")
    if (p.size < 4) return@mapNotNull null
    Cell(
        dow = p[0], day = p[1], duty = p[2], red = p[3] == "1",
        type = p.getOrNull(4)?.let { n -> DutyType.entries.firstOrNull { it.name == n } },
        time = p.getOrElse(5) { "" },
    )
}
```

`DutyWidgetWorker.kt` 42~49줄을 아래로 교체:

```kotlin
        val dow = DateTimeFormatter.ofPattern("E", Locale.KOREAN)
        fun hhmm(h: Int, m: Int) = "%02d:%02d".format(h % 24, m)
        val cells = week.map { date ->
            val d = byDate[date]
            val red = date.dayOfWeek == DayOfWeek.SUNDAY || d?.holidayName != null
            val signOn = d?.signOn?.split(":")?.mapNotNull { it.trim().toIntOrNull() }?.takeIf { it.size == 2 }
            val time = when {
                signOn != null -> "출근 " + hhmm(signOn[0], signOn[1])
                d != null -> runCatching {
                    com.sinjeong.crewcalendar.domain.model.BundledTimetable.advise(d.duty, date).at
                }.getOrNull()?.let { "편승 " + hhmm(it.hour, it.minute) }.orEmpty()
                else -> ""
            }
            Cell(date.format(dow), date.dayOfMonth.toString(), d?.duty?.display.orEmpty(), red, d?.duty?.type, time)
        }
        val strip = encodeStrip(cells)
```

`DutyWidget.kt`: 78줄 `private data class Cell(...)` 삭제, 92~95줄을 `val cells = decodeStrip(prefs[KEY_WEEK].orEmpty())`로 교체. `DayCell`·`Compact`의 파라미터 타입은 그대로 `Cell`(이제 WidgetStrip의 것).

- [ ] **Step 4: 통과 확인** — `.\gradlew.bat :app:compileDebugUnitTestKotlin` 후 `powershell -ExecutionPolicy Bypass -File tools\runtests.ps1` → Expected: `OK (215 tests)` (211 + 4).

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/com/sinjeong/crewcalendar/widget/WidgetStrip.kt app/src/main/java/com/sinjeong/crewcalendar/widget/DutyWidgetWorker.kt app/src/main/java/com/sinjeong/crewcalendar/widget/DutyWidget.kt app/src/test/java/com/sinjeong/crewcalendar/WidgetStripTest.kt
git commit -m "위젯 레코드에 근무 타입·시각 추가 — WidgetStrip 순수 파서(옛 4칸 호환)"
```

---

### Task 2: 근무색 공유 — `DutyPalette`

**Files:**
- Create: `app/src/main/java/com/sinjeong/crewcalendar/util/DutyPalette.kt`
- Modify: `app/src/main/java/com/sinjeong/crewcalendar/util/MonthImage.kt:14-24` (함수 이동, 호출부는 `dutyPalette(...)`)
- Test: `app/src/test/java/com/sinjeong/crewcalendar/WidgetStripTest.kt` (테스트 1건 추가)

**Interfaces:**
- Produces: `fun dutyPalette(t: DutyType): Pair<Int, Int>` — `[배경 ARGB, 글자 ARGB]`, 라이트 톤. ETC는 배경 0(투명).

- [ ] **Step 1: 테스트 추가**

```kotlin
    @Test fun `모든 근무 타입에 색이 있다`() {
        for (t in DutyType.entries) {
            val (bg, fg) = com.sinjeong.crewcalendar.util.dutyPalette(t)
            org.junit.Assert.assertTrue("$t fg", fg != 0)
            if (t != DutyType.ETC) org.junit.Assert.assertTrue("$t bg", bg != 0)
        }
    }
```

- [ ] **Step 2: 실패 확인** — 컴파일 실패(`dutyPalette` 없음).

- [ ] **Step 3: 구현** — `MonthImage.kt`의 `private fun dutyColors(t: DutyType)` 본문을 `util/DutyPalette.kt`의 `fun dutyPalette(t: DutyType): Pair<Int, Int>`로 그대로 옮기고(주석 포함), `MonthImage.kt`에서는 `dutyColors(` 호출을 `dutyPalette(`로 바꾼다. 다른 파일에 `dutyColors` 참조가 없는지 `grep`으로 확인.

- [ ] **Step 4: 통과 확인** — `OK (216 tests)`.

- [ ] **Step 5: 커밋** — `git add app/src/main/java/com/sinjeong/crewcalendar/util/DutyPalette.kt app/src/main/java/com/sinjeong/crewcalendar/util/MonthImage.kt app/src/test/java/com/sinjeong/crewcalendar/WidgetStripTest.kt && git commit -m "근무색 표를 DutyPalette로 분리(공유 이미지·위젯 공용)"`

---

### Task 3: 세 레이아웃 + 근무색 + 글자 배율

**Files:**
- Modify: `app/src/main/java/com/sinjeong/crewcalendar/widget/DutyWidget.kt` (provideGlance 본문, Compact, DayCell)

**Interfaces:**
- Consumes: `decodeStrip`, `dutyPalette`.
- 크기 판정(**폭은 dp / fontScale 로 나눠 본다** — `effW`):
  - `effW < 190.dp` → `Compact2(today, sub, small)` (2x1)
  - `190.dp <= effW < 340.dp` → `ThreeDays(cells.take(3), small, tall)` (3x1)
  - else → 7칸 스트립(기존) + 부제(높이 되면) (4x1)

- [ ] **Step 1: 배율 읽기와 판정 교체** — `provideGlance` 안 `val size = LocalSize.current` 아래를:

```kotlin
                val fs = LocalContext.current.resources.configuration.fontScale.coerceIn(0.8f, 2f)
                val effW = size.width / fs                       // 글자가 커진 만큼 "좁아진" 폭
                val tall = size.height >= MID.height
                val narrow = effW / 7 < 46.dp
                val small = fs >= 1.3f                            // 배율 1.3+ 는 한 단계 축소
```

기존 `strip`·`narrow` 판정은 위로 대체. `Text` 크기: `small`이면 기존 값 −1.5sp(16→14.5, 11.5→10, 15→13.5, 10.5→9, 9→8, 11→9.5).

- [ ] **Step 2: 2x1 `Compact2`** — 기존 `Compact` 교체:

```kotlin
    /** 2x1 — 오늘 근무 + 시각 한 줄("출근 07:47"/"편승 12:36"). 시각이 없으면 부제(sub). */
    @androidx.compose.runtime.Composable
    private fun Compact2(today: Cell, sub: String, small: Boolean) {
        val pal = today.type?.let(::dutyPalette)
        val bg = if (pal != null && pal.first != 0) ColorProvider(Color(pal.first)) else GlanceTheme.colors.surface
        val fg = if (pal != null) ColorProvider(Color(pal.second)) else GlanceTheme.colors.onSurface
        Box(
            modifier = GlanceModifier.fillMaxSize().background(bg).cornerRadius(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(today.duty.ifBlank { "·" }, maxLines = 1,
                    style = TextStyle(color = fg, fontSize = if (small) 17.sp else 19.sp, fontWeight = FontWeight.Bold))
                val line = today.time.ifBlank { sub }
                if (line.isNotBlank()) Text(line, maxLines = 1,
                    style = TextStyle(color = fg, fontSize = if (small) 9.sp else 10.5.sp))
            }
        }
    }
```

(`ColorProvider` = `androidx.glance.unit.ColorProvider`, `Color` = `androidx.compose.ui.graphics.Color`; 시그니처가 다르면 `androidx.glance.color.ColorProvider(Color)` 를 쓴다 — 컴파일러가 알려 준다.)

- [ ] **Step 3: 3x1 `ThreeDays`** — `DayCell` 3개를 `Row`에 `defaultWeight()`로. `DayCell`은 배경을 `dutyPalette(cell.type)`로(오늘 칸은 기존대로 `primary`), 글자색도 팔레트 글자색(오늘은 `onPrimary`). `red && !isToday`이면 요일·날짜만 `error` 색 유지.

```kotlin
    @androidx.compose.runtime.Composable
    private fun ThreeDays(cells: List<Cell>, small: Boolean, tall: Boolean) {
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            cells.forEachIndexed { i, c ->
                Box(modifier = GlanceModifier.defaultWeight().padding(horizontal = 2.dp)) {
                    DayCell(c, i == 0, narrow = false, tall = tall, small = small, GlanceModifier.fillMaxWidth())
                }
            }
        }
    }
```

`DayCell(cell, isToday, narrow, tall, small, modifier)`의 배경/글자:

```kotlin
        val pal = cell.type?.let(::dutyPalette)
        val bgProvider = when {
            isToday -> GlanceTheme.colors.primary
            pal != null && pal.first != 0 -> ColorProvider(Color(pal.first))
            else -> GlanceTheme.colors.inverseOnSurface
        }
        val fgProvider = when {
            isToday -> GlanceTheme.colors.onPrimary
            pal != null -> ColorProvider(Color(pal.second))
            else -> GlanceTheme.colors.onSurface
        }
```

- [ ] **Step 4: 분기 연결** — `provideGlance`: `cells.isEmpty()` 처리 뒤 `when { effW < 190.dp -> Compact2(cells[0], sub, small); effW < 340.dp -> ThreeDays(cells.take(3), small, tall); else -> 7칸(기존 Row + 부제) }`. 4x1의 `DayCell` 호출에 `small` 전달.

- [ ] **Step 5: 컴파일·설치·확인** — `.\gradlew.bat :app:installDebug`. 에뮬 홈에 4x1 위젯을 놓고 손으로 2칸·3칸으로 줄여 세 판이 바뀌는지 확인(리시버는 Task 4). 스크린샷 `C:\Users\admin\Downloads\_미리보기_v1.6.88\W01_4x1_배율1.0.png` 등. 배율 1.5: `adb shell settings put system font_scale 1.5` 후 같은 세 판. 펼침: `adb shell wm size 1968x2184` + `adb shell wm density 450`.

- [ ] **Step 6: 커밋** — `git add app/src/main/java/com/sinjeong/crewcalendar/widget/DutyWidget.kt && git commit -m "위젯 2x1·3x1·4x1 레이아웃 + 근무색 + 글자 배율 대응"`

---

### Task 4: 런처 목록에 세 항목 — 리시버·XML·미리보기

**Files:**
- Create: `app/src/main/java/com/sinjeong/crewcalendar/widget/DutyWidgetReceivers.kt`
- Create: `app/src/main/res/xml/duty_widget_info_2.xml`, `app/src/main/res/xml/duty_widget_info_3.xml`
- Create: `app/src/main/res/layout/widget_duty_preview_2.xml`, `app/src/main/res/layout/widget_duty_preview_3.xml` (기존 `widget_duty_preview.xml`을 복사해 칸 수만 1·3으로)
- Modify: `app/src/main/AndroidManifest.xml:64-75` (리시버 2개 추가), `app/src/main/res/values/strings.xml` (`widget_label_2`="근무 위젯 (오늘)", `widget_label_3`="근무 위젯 (3일)")
- Modify: `app/src/main/java/com/sinjeong/crewcalendar/widget/DutyWidgetWorker.kt:53-60` (세 리시버의 id 모두 갱신)

- [ ] **Step 1: 리시버**

```kotlin
package com.sinjeong.crewcalendar.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** 런처 목록의 2x1 항목. 위젯 내용은 [DutyWidget]이 크기로 정한다(리시버만 다르다). */
class DutyWidgetReceiver2 : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DutyWidget()
}

/** 런처 목록의 3x1 항목. */
class DutyWidgetReceiver3 : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DutyWidget()
}
```

기존 `DutyWidgetReceiver.onUpdate`(`DutyWidget.kt:228~`)가 워커를 즉시 돌린다면 같은 `onUpdate` 본문을 두 클래스에도 넣는다(복사 — 몇 줄).

- [ ] **Step 2: XML** — `duty_widget_info_2.xml`: 기존 파일 복사 후 `targetCellWidth="2"`, `previewLayout="@layout/widget_duty_preview_2"`, `description="@string/widget_label_2"`. `_3`은 `targetCellWidth="3"`. `minWidth/minHeight/minResize*`는 기존 값(110/40dp) 유지.

- [ ] **Step 3: 매니페스트** — 기존 `<receiver android:name=".widget.DutyWidgetReceiver" …>` 블록 아래에 같은 블록을 두 번 더(이름 `.widget.DutyWidgetReceiver2`/`3`, label `@string/widget_label_2`/`3`, resource `@xml/duty_widget_info_2`/`3`). **다른 리시버·액티비티 줄은 건드리지 않는다.**

- [ ] **Step 4: 워커** — `manager.getGlanceIds(DutyWidget::class.java)`가 세 리시버의 id를 모두 주는지 로그로 확인(`Log.d("DutyWidget", "ids=${ids.size}")`, 세 위젯을 다 놓은 상태에서 3이어야 한다). 안 주면 `AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, R::class.java))`를 세 리시버에 대해 모아 `manager.getGlanceIdBy(appWidgetId)`로 바꾼다. 어느 쪽을 썼는지 보고에 적어라.

- [ ] **Step 5: 확인** — `installDebug` 후 에뮬 홈 길게 누르기 → 위젯 → 앱에 **항목 3개**(2x1·3x1·4x1)가 보이는 스크린샷 `W07_위젯목록_3항목.png`. 각각 하나씩 놓은 화면 `W08_세개_배치.png`. 기존에 놓여 있던 4x1이 그대로인지.

- [ ] **Step 6: 커밋** — `git add app/src/main/java/com/sinjeong/crewcalendar/widget/DutyWidgetReceivers.kt app/src/main/res/xml/duty_widget_info_2.xml app/src/main/res/xml/duty_widget_info_3.xml app/src/main/res/layout/widget_duty_preview_2.xml app/src/main/res/layout/widget_duty_preview_3.xml app/src/main/AndroidManifest.xml app/src/main/res/values/strings.xml app/src/main/java/com/sinjeong/crewcalendar/widget/DutyWidgetWorker.kt && git commit -m "위젯 런처 목록 2x1·3x1 항목 추가(리시버 2개, 같은 DutyWidget)"`

---

### Task 5: 잘림 0 검증 표 + 원복

- [ ] 표: 크기(2x1/3x1/4x1) × 배율(1.0/1.3/1.5) × 접힘/펼침 = 18칸, 각 칸 "잘림 없음/있음" + 스크린샷 파일명. 잘리면 해당 `Text` 크기를 0.5sp씩 내려 다시 찍는다(코드 수정 후 커밋).
- [ ] 원복: `adb shell settings put system font_scale 1.0`, `adb shell wm size reset`, `adb shell wm density reset`. 테스트로 놓은 위젯은 그대로 둬도 된다.
- [ ] 보고: 태스크별 커밋 해시, 테스트 건수, 표, `getGlanceIds` 확인 결과, 못 한 것.
