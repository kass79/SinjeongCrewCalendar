package com.sinjeong.crewcalendar.presentation.notice

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.sinjeong.crewcalendar.presentation.calendar.CalendarPalette

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
 *
 * ⚠ [pal] 을 받는 이유(v1.7.7 A8): 이 배너는 **달력 위**에 얹힌다. 종전엔 `surfaceVariant` 를
 * 직접 써서, 앱이 다크인데 달력만 크림인 클레이에서 **이 배너만 어두운 판**으로 남았다
 * (증거 `_최종점검_v1.7.6\F29b_확대_클레이다크_공지배너만_다크색.png`). 기본 팔레트 값은
 * 종전에 여기서 쓰던 색 그대로라 **기본 스타일 화면은 한 픽셀도 안 바뀐다.**
 */
@Composable
internal fun NoticeBanner(notices: List<Notice>, pal: CalendarPalette, modifier: Modifier = Modifier) {
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
            .background(pal.noticeBg)
            .clickable { open = n }
            .padding(start = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Campaign,
            null,
            Modifier.size(22.dp),
            // 기본은 종전대로 **강조색** 확성기(픽셀 회귀 0). 클레이는 크림 판이라 글자와 같은
            // 잉크로 맞춘다 — 2호선 초록 확성기가 노란 판 위에서 겉돈다.
            tint = if (pal.clay) pal.noticeInk else MaterialTheme.colorScheme.primary,
        )
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(
                n.title,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = pal.noticeInk,
            )
            if (n.body.isNotBlank()) Text(
                n.body,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = pal.noticeInk,
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
                tint = pal.noticeInk,
            )
        }
    }

    open?.let { full ->
        AlertDialog(
            onDismissRequest = { open = null },
            title = { Text(full.title, fontWeight = FontWeight.ExtraBold) },
            // M3 AlertDialog 의 text 슬롯은 **스스로 스크롤하지 않는다**(v1.6.93). 공지 본문은
            // 관리자가 얼마든지 길게 쓸 수 있어, 긴 글이면 아래 [확인] 이 화면 밖으로 나가
            // 대화상자를 닫을 길이 사라졌다(뒤로가기만 남는다). MenuAdminScreen 과 같은 처방.
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) { Text(full.body) }
            },
            confirmButton = { TextButton(onClick = { open = null }) { Text("확인") } },
        )
    }
}
