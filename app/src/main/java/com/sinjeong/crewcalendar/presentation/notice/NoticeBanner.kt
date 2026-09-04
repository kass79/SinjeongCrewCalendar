package com.sinjeong.crewcalendar.presentation.notice

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sinjeong.crewcalendar.domain.model.Notice

/** 닫은 공지 id — 편승 알람 예약과 같은 `settings` SharedPreferences 를 그대로 쓴다 */
private const val KEY_DISMISSED = "dismissed_notices"

/**
 * 달력 맨 위 관리자 공지 카드 (v1.6.89).
 *
 * [notices] 는 이미 **기간 안 · 최신순**이다([com.sinjeong.crewcalendar.domain.repository.NoticeRepository.observeActive]) —
 * 여기서는 아직 안 닫은 첫 건만 고른다. 보여 줄 게 없으면 **아무것도 그리지 않는다**(높이 0,
 * 달력 레이아웃 무변화).
 *
 * 닫기(X)는 그 id 를 이 폰에만 기억한다. 서버로 안 올린다 — 누가 무엇을 읽었는지는 앱이 알 일이
 * 아니고, 메모와 같은 "기기 전용" 원칙이다. **새 공지는 id 가 다르므로 다시 뜬다.**
 *
 * 상태를 밖에서 받지 않고 화면이 스스로 들고 있는 이유: `MainCalendarScreen` 은 이 배너를
 * **한 줄로만** 끼우기로 했다(2차 계획 Global Constraints).
 */
@Composable
fun NoticeBanner(notices: List<Notice>, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    // ⚠ `rememberSaveable` 금지(CLAUDE.md) — 값은 어차피 prefs 가 들고 있다.
    var dismissed by remember { mutableStateOf(prefs.getStringSet(KEY_DISMISSED, null).orEmpty()) }
    var open by remember { mutableStateOf<Notice?>(null) }

    val n = notices.firstOrNull { it.id !in dismissed } ?: return

    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { open = n }
            .padding(start = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Campaign,
            null,
            Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(
                n.title,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (n.body.isNotBlank()) Text(
                n.body,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = {
                // 살아 있는 공지 id 만 남긴다 — 기간 지난 것까지 이고 다닐 이유가 없다.
                val keep = (dismissed + n.id).intersect(notices.map { it.id }.toSet())
                dismissed = keep
                prefs.edit().putStringSet(KEY_DISMISSED, keep).apply()
            },
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                Icons.Default.Close,
                "공지 닫기",
                Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    open?.let { full ->
        AlertDialog(
            onDismissRequest = { open = null },
            title = { Text(full.title, fontWeight = FontWeight.ExtraBold) },
            text = { Text(full.body) },
            confirmButton = { TextButton(onClick = { open = null }) { Text("확인") } },
        )
    }
}
