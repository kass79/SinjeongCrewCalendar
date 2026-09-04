package com.sinjeong.crewcalendar.widget

import com.sinjeong.crewcalendar.domain.model.DutyCode
import com.sinjeong.crewcalendar.domain.model.DutyType

/**
 * 위젯 한 칸. `KEY_WEEK` 한 레코드 = `요일|일자|근무|빨강|타입|시각|epochDay`.
 * v1.6.88에서 타입·시각을, **v1.6.92에서 [epochDay]** 를 붙였다 — 옛 레코드는 뒤 칸이 없는 대로 읽는다.
 * time 은 접두어 포함("출근 07:47" / "편승 12:36" / "").
 *
 * ## 왜 칸마다 날짜를 싣나 (v1.6.92 ⑤)
 *
 * 종전엔 칸에 날짜가 없어 **첫 칸을 무조건 오늘로 칠했다.** 자정 갱신(WorkManager)이 도즈에
 * 밀리면 그림은 어제 것 그대로인데 첫 칸만 "오늘"로 강조돼 **어제 근무를 오늘로 읽는다** —
 * 이 앱에서 그건 곧 지각이다. 칸에 날짜가 있으면 ① 오늘 강조가 스스로 옳은 칸을 찾아가고
 * ② 어느 칸도 오늘이 아니면 위젯이 "갱신 필요"라고 말할 수 있다.
 */
data class Cell(
    val dow: String, val day: String, val duty: String, val red: Boolean,
    val type: DutyType?, val time: String,
    /** 이 칸이 가리키는 날 (`LocalDate.toEpochDay()`). 옛 레코드는 null */
    val epochDay: Long? = null,
)

/**
 * **위젯 칸 전용** 표기 — 4x1 은 칸이 ≈36dp 뿐이라 여섯 글자가 안 들어간다.
 *
 * 종전엔 워커가 [DutyCode.display] 를 그대로 넣어 `대기충당지2` · `돌봄휴가` 가
 * `대기충…` 로 잘렸다 — 승무원에게 실질 정보인 **다이아(`지2`)가 통째로 안 보였다**(v1.6.93).
 *
 * | 저장값 | 달력 칸 | **위젯 칸** |
 * |---|---|---|
 * | `대기충당 지2` | `대기충당`⏎`지2` | `대기`⏎`지2` |
 * | `돌봄휴가` | `돌봄휴가` | `돌봄` |
 * | `1` · `지2` · `~` | 그대로 | 그대로 |
 *
 * 두 줄 규칙은 [DutyCode.gridLabel] 그대로 쓰고, 윗줄만 두 글자로 줄인다.
 *
 * ⚠ [SHORT] 는 동료 탭 격자의 `MATE_SHORT`(`DutyMatrix.kt`)와 **같은 내용의 사본**이다.
 * 그쪽은 *"이 파일에 private 으로 두는 것이 곧 [DutyCode.display] 를 안 건드린다는 보증"* 이라고
 * 못 박아 뒀으므로 공용으로 끌어내지 않았다. 셋 중 하나를 고치면 여기도 같이 고칠 것.
 */
private val SHORT = mapOf("돌봄휴가" to "돌봄", "동행휴가" to "동행", "대기충당" to "대기")

fun cellLabel(duty: DutyCode): String {
    val g = duty.gridLabel
    val head = g.substringBefore('\n')
    val short = SHORT[head] ?: head
    return if ('\n' in g) short + "\n" + g.substringAfter('\n') else short
}

private val SEP = Regex("[|;]")

fun encodeStrip(cells: List<Cell>): String = cells.joinToString(";") { c ->
    listOf(
        c.dow, c.day, c.duty, if (c.red) "1" else "0", c.type?.name.orEmpty(), c.time,
        c.epochDay?.toString().orEmpty(),
    ).joinToString("|") { it.replace(SEP, "") }
}

fun decodeStrip(s: String): List<Cell> = s.split(";").mapNotNull { rec ->
    val p = rec.split("|")
    if (p.size < 4) return@mapNotNull null
    Cell(
        dow = p[0], day = p[1], duty = p[2], red = p[3] == "1",
        type = p.getOrNull(4)?.let { n -> DutyType.entries.firstOrNull { it.name == n } },
        time = p.getOrElse(5) { "" },
        epochDay = p.getOrNull(6)?.toLongOrNull(),
    )
}
