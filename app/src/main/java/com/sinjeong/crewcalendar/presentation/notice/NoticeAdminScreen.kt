package com.sinjeong.crewcalendar.presentation.notice

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sinjeong.crewcalendar.domain.model.Notice
import com.sinjeong.crewcalendar.domain.repository.AdminWriteResult
import com.sinjeong.crewcalendar.domain.repository.NoticeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * 관리자 · 공지 쓰기 (v1.6.89).
 *
 * 식단표 관리 화면([com.sinjeong.crewcalendar.presentation.menu.MenuAdminScreen])과 같은 자리·같은
 * 잠금(`AdminGate`)을 쓴다. 저장하면 기간 안에는 **전원의 달력 맨 위**에 카드로 뜬다.
 *
 * 목록은 [NoticeRepository.observeActive] 라 **기간이 지난 공지는 여기서도 사라진다** — 배너에서
 * 이미 안 보이는 것을 관리자가 굳이 지울 이유가 없어 따로 질의를 만들지 않았다.
 */
@HiltViewModel
class NoticeAdminViewModel @Inject constructor(
    private val repo: NoticeRepository,
) : ViewModel() {

    val notices: StateFlow<List<Notice>> = repo.observeActive(LocalDate.now())
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var title by mutableStateOf("")
    var body by mutableStateOf("")
    var from by mutableStateOf(LocalDate.now())
        private set
    var to by mutableStateOf(LocalDate.now().plusDays(7))
        private set
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)

    /** 시작일이 종료일을 넘어가면 종료일을 따라 올린다 — 기간이 뒤집힌 공지는 영영 안 뜬다 */
    fun pickFrom(d: LocalDate) { from = d; if (to.isBefore(d)) to = d }
    fun pickTo(d: LocalDate) { to = if (d.isBefore(from)) from else d }

    fun save() = viewModelScope.launch {
        busy = true
        val r = repo.save(Notice("", title.trim(), body.trim(), from, to, 0L))
        busy = false
        message = when (r) {
            AdminWriteResult.OK -> "공지를 올렸습니다."
            AdminWriteResult.DENIED -> "서버가 거부했습니다 — 보안 규칙을 확인하세요."
            AdminWriteResult.FAILED -> "저장 실패 — 인터넷 연결을 확인하세요."
        }
        if (r == AdminWriteResult.OK) { title = ""; body = "" }
    }

    fun delete(id: String) = viewModelScope.launch {
        busy = true
        val r = repo.delete(id)
        busy = false
        message = when (r) {
            AdminWriteResult.OK -> "공지를 지웠습니다."
            AdminWriteResult.DENIED -> "서버가 거부했습니다 — 보안 규칙을 확인하세요."
            AdminWriteResult.FAILED -> "삭제 실패 — 인터넷 연결을 확인하세요."
        }
    }
}

private fun dateLabel(d: LocalDate) = "${d.monthValue}월 ${d.dayOfMonth}일"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoticeAdminScreen(onBack: () -> Unit, vm: NoticeAdminViewModel = hiltViewModel()) {
    val notices by vm.notices.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    /** 날짜 선택기가 열려 있으면 true=시작일 / false=종료일. ⚠ `remember` — CLAUDE.md 함정 */
    var picking by remember { mutableStateOf<Boolean?>(null) }
    var confirmDelete by remember { mutableStateOf<Notice?>(null) }

    LaunchedEffect(vm.message) { vm.message?.let { snackbar.showSnackbar(it); vm.message = null } }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
                },
                title = { Text("관리자 · 공지 쓰기", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "올린 공지는 기간 안에 모두의 달력 맨 위에 카드로 뜹니다. " +
                    "받는 사람이 [X]로 닫으면 그 공지는 그 사람 폰에서 다시 안 뜹니다. " +
                    "기간이 지나면 저절로 사라지고 이 목록에서도 빠집니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp),
            )

            OutlinedTextField(
                value = vm.title,
                onValueChange = { if (it.length <= Notice.MAX_TITLE) vm.title = it },
                label = { Text("제목") },
                singleLine = true,
                supportingText = { Text("${vm.title.length}/${Notice.MAX_TITLE}") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = vm.body,
                onValueChange = { if (it.length <= Notice.MAX_BODY) vm.body = it },
                label = { Text("내용") },
                minLines = 3,
                maxLines = 8,
                supportingText = { Text("${vm.body.length}/${Notice.MAX_BODY}") },
                modifier = Modifier.fillMaxWidth(),
            )

            Text("보일 기간", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.weight(1f)) {
                    Text("${dateLabel(vm.from)}부터")
                }
                OutlinedButton(onClick = { picking = false }, modifier = Modifier.weight(1f)) {
                    Text("${dateLabel(vm.to)}까지")
                }
            }

            Button(
                onClick = vm::save,
                enabled = vm.title.isNotBlank() && !vm.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("올리기") }
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())

            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Text(
                "지금 떠 있는 공지",
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
            )

            if (notices.isEmpty()) Text(
                "떠 있는 공지가 없습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            notices.forEach { n ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(n.title, fontWeight = FontWeight.Bold)
                        Text(
                            "${dateLabel(n.from)} ~ ${dateLabel(n.to)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { confirmDelete = n }) {
                        Icon(Icons.Default.Delete, "삭제", tint = MaterialTheme.colorScheme.error)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // 날짜는 Material3 기본 선택기 그대로 — UTC 밀리초라 epochDay 왕복이 정확하다(시각 성분 없음).
    picking?.let { isFrom ->
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (if (isFrom) vm.from else vm.to).toEpochDay() * 86_400_000L,
        )
        DatePickerDialog(
            onDismissRequest = { picking = null },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        val d = LocalDate.ofEpochDay(Math.floorDiv(it, 86_400_000L))
                        if (isFrom) vm.pickFrom(d) else vm.pickTo(d)
                    }
                    picking = null
                }) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { picking = null }) { Text("취소") } },
        ) { DatePicker(state) }
    }

    confirmDelete?.let { n ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("공지 삭제") },
            text = { Text("${n.title} 공지를 지웁니다. 모두의 달력에서 사라집니다.") },
            confirmButton = {
                TextButton(onClick = { vm.delete(n.id); confirmDelete = null }) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("취소") } },
        )
    }
}
