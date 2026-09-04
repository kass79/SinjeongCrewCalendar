package com.sinjeong.crewcalendar.presentation.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sinjeong.crewcalendar.domain.model.DaySchedule
import com.sinjeong.crewcalendar.domain.usecase.WeeklyHours
import com.sinjeong.crewcalendar.presentation.roster.dutyCellColors
import com.sinjeong.crewcalendar.presentation.theme.LocalDutyColors
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 메모 첫 줄. 달력 칸은 두 줄까지 보여 주지만 **모아보기 목록은 한 줄**이라
 * 여러 줄 메모(v1.6.82에서 상세시트 메모칸이 8줄까지 자란다)의 첫 **내용 있는** 줄만 뽑는다.
 * 앞에 빈 줄을 넣고 쓰는 사람이 있어 공백 줄은 건너뛴다. 전부 공백이면 빈 문자열.
 */
fun memoFirstLine(memo: String): String =
    memo.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: ""

/**
 * **메모 모아보기** (v1.6.82 사용자 요청) — 이 달에 메모가 있는 날을 `날짜 · 근무 · 메모 첫 줄`로
 * 모아 보여 주고, 누르면 그 날 상세로 간다.
 *
 * 달력 칸의 메모는 폭이 54dp라 12자쯤에서 잘린다. 잘린 날을 하나씩 눌러 확인하는 대신
 * 한 화면에서 훑으라고 만든 자리다. 진입점은 달력 헤더의 [androidx.compose.material.icons.Icons] `EditNote` 버튼.
 *
 * 목록 자체는 `state.days`(그 달 전체)를 그대로 거른다 — 새 저장소도, 새 쿼리도 없다.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MemoListSheet(
    month: YearMonth,
    days: List<DaySchedule>,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val rows = days.filter { it.memo.isNotBlank() }.sortedBy { it.date }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${month.monthValue}월 메모",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${rows.size}건",
                    fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // 주52시간 — 그 달의 주별 근무시간(월~일, 52.0h 초과는 빨강). 달력 상태에서 파생이라
            // 근무변경·메모를 저장하면 곧바로 다시 계산된다(저장소·쿼리 없음).
            // `FlowRow` 라 글자배율을 키우면 잘리지 않고 스스로 아랫줄로 접힌다.
            val weeks = remember(month, days) { WeeklyHours.compute(month, days) }
            FlowRow(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                weeks.forEach { w ->
                    val over = w.minutes > WeeklyHours.LIMIT_MIN
                    val tail =
                        if (w.excluded.isEmpty()) "" else " (${w.excluded.joinToString("·")} 미포함)"
                    Text(
                        WeeklyHours.label(w) + tail,
                        fontSize = 12.5.sp,
                        fontWeight = if (over) FontWeight.ExtraBold else FontWeight.Bold,
                        color = if (over) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (rows.isEmpty()) {
                Text(
                    "이 달에 적어 둔 메모가 없습니다.\n날짜를 눌러 메모를 적어 보세요.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    textAlign = TextAlign.Center,
                )
            } else {
                LazyColumn {
                    items(rows, key = { it.date.toEpochDay() }) { day ->
                        MemoRow(day, onClick = { onPick(day.date) })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoRow(day: DaySchedule, onClick: () -> Unit) {
    val duty = LocalDutyColors.current
    val (chipBg, chipFg) = dutyCellColors(day.duty.colorType, duty, MaterialTheme.colorScheme.onSurfaceVariant)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 날짜 — 요일까지 적는다. 목록에서 "언제"가 제일 먼저 읽혀야 한다.
        Text(
            day.date.format(DateTimeFormatter.ofPattern("d일 (E)", Locale.KOREAN)),
            fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
            color = if (day.holidayName != null || day.date.dayOfWeek.value == 7) duty.sunday
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 1, softWrap = false,
            // 글자 배율 1.5배에서 "12일 (수)"가 62dp에 안 들어가 잘렸다(v1.6.86 점검 #4).
            // 고정폭 대신 최소폭 — 배율이 커지면 칸이 같이 늘어난다.
            modifier = Modifier.widthIn(min = 62.dp),
        )
        // 근무 — 달력 칸과 **같은 색 규칙**(dutyCellColors)이라 색으로도 같은 날을 찾는다.
        Surface(color = chipBg, contentColor = chipFg, shape = RoundedCornerShape(7.dp)) {
            Box(Modifier.width(42.dp).padding(vertical = 3.dp), contentAlignment = Alignment.Center) {
                Text(
                    day.duty.gridLabel.replace('\n', ' '),
                    fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            memoFirstLine(day.memo),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}
