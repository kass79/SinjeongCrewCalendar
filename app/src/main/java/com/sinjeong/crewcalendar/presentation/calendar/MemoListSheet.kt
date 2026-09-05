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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
 * **빠른 입력 칩** 문구 (v1.6.99 — *"메모기능을 조금 더 업그레이드 해줘!"*).
 *
 * 상세시트 메모칸 위에 한 줄로 깔리는, **최근에 실제로 쓴 메모 첫 줄**들이다. 승무원이 쓰는
 * 메모는 `연차`·`병원`·`아이 소풍`처럼 몇 개가 돌고 도는 말이라, 새 저장소를 만들지 않고
 * **이미 읽어 온 달들**([MainCalendarViewModel.recentMemos] 가 앞뒤 달까지 넘긴다)에서 모은다.
 *
 * 최신 날짜부터 훑어 **중복을 없애고** [limit] 개까지. 첫 줄이 빈 메모(공백만)는 뺀다.
 * 결과가 비면 화면이 칩 줄 자체를 감춘다.
 */
fun recentMemoPhrases(days: List<DaySchedule>, limit: Int = 8): List<String> =
    days.asSequence()
        .filter { it.memo.isNotBlank() }
        .sortedByDescending { it.date }
        .map { memoFirstLine(it.memo) }
        .filter { it.isNotEmpty() }
        .distinct()
        .take(limit)
        .toList()

/**
 * 메모 모아보기 **검색** 판정 (v1.6.99). 빈 검색어는 전부 통과 = 종전 목록 그대로.
 *
 * 첫 줄이 아니라 **메모 전체**를 본다 — 목록은 첫 줄만 보여 주지만 찾는 말이 둘째 줄에
 * 있는 일이 흔하다(상세시트 메모칸이 8줄까지 자란다). 대소문자는 무시한다.
 */
fun memoMatches(memo: String, query: String): Boolean {
    val q = query.trim()
    return q.isEmpty() || memo.contains(q, ignoreCase = true)
}

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
    /**
     * 주52시간 주별 근무시간. **비어 있으면 줄 자체를 안 그린다** —
     * 통상근무·4조2교대처럼 시간을 계산할 수 없는 소속이 매주 `0.0h`로 보이던 것을 막는다(v1.6.92 ⑥).
     * 계산은 [MainCalendarViewModel.weeklyHours]가 하고(달 경계 주는 인접 달까지 합산, v1.6.92 ⑦)
     * 여기서는 그리기만 한다.
     */
    weeks: List<WeeklyHours.Week>,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    // 검색어는 이 시트가 열려 있는 동안만 산다 — 닫으면 초기화되는 게 맞다(찾던 말이 다음에
    // 열 때 남아 목록이 비어 보이면 고장으로 읽힌다).
    // ⚠ `rememberSaveable`은 이 프로젝트에서 쓰지 않는다(CLAUDE.md 함정).
    var query by remember { mutableStateOf("") }
    val all = days.filter { it.memo.isNotBlank() }.sortedBy { it.date }
    val rows = all.filter { memoMatches(it.memo, query) }
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
            // 검색 (v1.6.99) — 제목 바로 아래 한 칸. 메모가 하나도 없는 달이면 감춘다
            // (칠 것이 없는 칸은 자리만 먹는다).
            if (all.isNotEmpty()) OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("메모 검색", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(18.dp)) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Close, "지우기", Modifier.size(18.dp))
                    }
                },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            )
            // 주52시간 — 그 달의 주별 근무시간(월~일, 52.0h 초과는 빨강).
            // 사용자 피드백(v1.6.90): 숫자만 있으면 뭔지 모른다 — "주 52시간" 기준임을 한 줄로 밝힌다.
            // ⚠ v1.6.99 — 안내 줄은 **`주 52시간 확인` 넉 자만**이다. 사용자:
            // *"주52시간 확인 옆에 월~일 근무시간 합계 ← 이건 빼도 될듯.. 1주,2주 좀 더
            // 정리되게 그리고 크게 중요하지 않으니 텍스트를 줄여줘도 될듯.."*
            // 뒤에 붙어 있던 `· 월~일 근무시간 합계 (넘는 주는 빨간색)`는 화면이 스스로
            // 말하는 것(빨간 주 + `초과` 꼬리표)을 글자로 한 번 더 설명하던 군더더기였다.
            if (weeks.isNotEmpty()) {
                Text(
                    "주 52시간 확인",
                    fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
                // `1주 40.5h · 2주 38.0h · …` — 가운뎃점으로 이어 **한 줄에 가지런히**.
                // `FlowRow` 라 글자배율을 키우면 잘리지 않고 스스로 아랫줄로 접힌다.
                // 꼬리표(`초과`·`일부만 집계`·`미포함`)는 남기되 **한 단 더 작게** 깔아
                // 숫자 줄의 리듬을 깨지 않는다.
                FlowRow(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    weeks.forEachIndexed { i, w ->
                        val tail =
                            (if (w.over) " 초과" else "") +
                                // 인접 달 근무를 아직 못 읽은 경계 주 — 합계가 덜 찼으니 초과라고 말하지 않는다
                                (if (w.partial) " · 일부만 집계" else "") +
                                if (w.excluded.isEmpty()) "" else " (${w.excluded.joinToString("·")} 미포함)"
                        Text(
                            buildAnnotatedString {
                                append(WeeklyHours.label(w))
                                if (tail.isNotEmpty()) withStyle(SpanStyle(fontSize = 9.5.sp)) {
                                    append(tail)
                                }
                            },
                            fontSize = 11.sp,
                            fontWeight = if (w.over) FontWeight.ExtraBold else FontWeight.Bold,
                            color = if (w.over) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (i < weeks.lastIndex) Text(
                            "·",
                            fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                }
            }
            if (rows.isEmpty()) {
                Text(
                    if (query.isNotBlank()) "'${query.trim()}' 를 포함한 메모가 없습니다"
                    else "이 달에 적어 둔 메모가 없습니다.\n날짜를 눌러 메모를 적어 보세요.",
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
