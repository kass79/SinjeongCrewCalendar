package com.sinjeong.crewcalendar.presentation.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.sinjeong.crewcalendar.domain.model.LEAVE_CHIPS
import com.sinjeong.crewcalendar.domain.model.LeaveQuota
import com.sinjeong.crewcalendar.domain.model.leaveCountText
import com.sinjeong.crewcalendar.domain.model.leaveLeft
import com.sinjeong.crewcalendar.domain.model.leaveUsedInApp
import java.time.LocalDate

/**
 * 휴가 한 종류의 개수 적기(v1.7.20 ④) — 근무변경 시트의 휴가 칩을 **길게 누르면**,
 * 또 설정 > `휴가 개수` 표에서 줄을 누르면 뜬다. 받은 개수를 비우고 저장하면 지운다.
 * 저장은 폰에만(`ThemeController.setLeaveQuota`).
 */
@Composable
internal fun LeaveQuotaDialog(
    code: String,
    year: Int,
    quota: LeaveQuota?,
    usedInApp: Int,
    onSave: (LeaveQuota?) -> Unit,
    onDismiss: () -> Unit,
) {
    var granted by remember { mutableStateOf(quota?.granted?.toString().orEmpty()) }
    var outside by remember { mutableStateOf(quota?.usedOutside?.takeIf { it != 0 }?.toString().orEmpty()) }
    fun digits(s: String) = s.filter(Char::isDigit).take(3)
    val g = granted.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$code · ${year}년", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = granted, onValueChange = { granted = digits(it) },
                    label = { Text("올해 받은 개수") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    value = outside, onValueChange = { outside = digits(it) },
                    label = { Text("앱 밖에서 이미 쓴 개수") }, singleLine = true,
                    supportingText = { Text("앱을 쓰기 전에 썼거나 앱에 안 적은 날") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Text(
                    buildAnnotatedString {
                        append("앱에서 쓴 날 ${usedInApp}일")
                        if (g != null) {
                            val left = leaveLeft(LeaveQuota(g, outside.toIntOrNull() ?: 0), usedInApp)
                            append(" · 남음 ")
                            withStyle(SpanStyle(color = leftColor(left), fontWeight = FontWeight.ExtraBold)) {
                                append(leaveCountText(left))
                            }
                        }
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    "앱에서 쓴 날 = ${year}년 근무변경 중 이 휴가인 날. 되돌리거나 다른 근무로 바꾸면 저절로 빠집니다. 이 폰에만 저장됩니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(g?.let { LeaveQuota(it, outside.toIntOrNull() ?: 0) })
            }) { Text("저장") }
        },
        dismissButton = {
            Row {
                if (quota != null) TextButton(onClick = { onSave(null) }) { Text("지우기") }
                TextButton(onClick = onDismiss) { Text("취소") }
            }
        },
    )
}

@Composable
internal fun leftColor(left: Int) =
    if (left < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface

/**
 * 설정 > `휴가 개수` — 모든 휴가를 한 표로(받은 개수·밖에서 쓴 개수·앱에서 쓴 날·남음).
 * 줄을 누르면 [LeaveQuotaDialog]. 해는 ‹ › 로 옮긴다(12월에 내년 개수를 미리 적을 때).
 */
@Composable
internal fun LeaveTableDialog(
    quotas: Map<Int, Map<String, LeaveQuota>>,
    overrides: Map<LocalDate, String>,
    onSet: (year: Int, code: String, quota: LeaveQuota?) -> Unit,
    onDismiss: () -> Unit,
) {
    var year by remember { mutableIntStateOf(LocalDate.now().year) }
    var edit by remember { mutableStateOf<String?>(null) }
    val yq = quotas[year].orEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("휴가 개수", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { year-- }) { Text("‹ ${year - 1}") }
                    Text(
                        "${year}년", Modifier.weight(1f), textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold,
                    )
                    TextButton(onClick = { year++ }) { Text("${year + 1} ›") }
                }
                Text(
                    "줄을 누르면 받은 개수를 적습니다. 받은 개수를 적은 휴가만 근무변경 칩에 남은 개수가 보입니다. 이 폰에만 저장됩니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                TableRow("휴가", "받은\n개수", "밖에서\n쓴 날", "앱에서\n쓴 날", "남음", header = true)
                LEAVE_CHIPS.forEach { code ->
                    val q = yq[code]
                    val used = leaveUsedInApp(overrides, code, year)
                    val left = q?.let { leaveLeft(it, used) }
                    HorizontalDivider()
                    TableRow(
                        code,
                        q?.granted?.toString() ?: "—",
                        q?.usedOutside?.toString() ?: "—",
                        "$used",
                        left?.let(::leaveCountText) ?: "—",
                        leftIsNegative = (left ?: 0) < 0,
                        modifier = Modifier.clickable { edit = code },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
    edit?.let { code ->
        LeaveQuotaDialog(
            code = code, year = year, quota = yq[code],
            usedInApp = leaveUsedInApp(overrides, code, year),
            onSave = { onSet(year, code, it); edit = null },
            onDismiss = { edit = null },
        )
    }
}

@Composable
private fun TableRow(
    a: String, b: String, c: String, d: String, e: String,
    header: Boolean = false,
    leftIsNegative: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val w = if (header) FontWeight.ExtraBold else FontWeight.Normal
    val style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium
    val lines = if (header) 2 else 1
    Row(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(a, Modifier.weight(1.5f), fontWeight = FontWeight.Bold, style = style, maxLines = 1)
        listOf(b, c, d).forEach {
            Text(it, Modifier.weight(1f), fontWeight = w, style = style, textAlign = TextAlign.Center, maxLines = lines)
        }
        Text(
            e, Modifier.weight(1f), style = style, textAlign = TextAlign.Center, maxLines = 1,
            fontWeight = FontWeight.ExtraBold,
            color = if (leftIsNegative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}
