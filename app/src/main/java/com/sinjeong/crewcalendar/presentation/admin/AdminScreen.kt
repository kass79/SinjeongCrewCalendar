package com.sinjeong.crewcalendar.presentation.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sinjeong.crewcalendar.domain.model.Bundled
import com.sinjeong.crewcalendar.domain.model.BundledStaff
import com.sinjeong.crewcalendar.domain.model.CrewGroup
import com.sinjeong.crewcalendar.domain.repository.RosterEntry
import com.sinjeong.crewcalendar.domain.repository.RosterRepository
import com.sinjeong.crewcalendar.presentation.calendar.DutyPickerSheet
import com.sinjeong.crewcalendar.presentation.calendar.DutyPickerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.time.LocalDate
import javax.inject.Inject

/**
 * 관리자 잠금. 암호는 앱 코드에 평문으로 두지 않고 SHA-256 해시만 비교한다.
 * 세션은 프로세스 메모리에만 있어 앱을 완전히 종료하면 자동으로 잠긴다.
 *
 * 주의(보안 한계): 이 잠금은 "앱 화면"만 막는다. firestore.rules 가
 * `users/{uid}` 를 로그인만 하면 쓰게 허용하고 있어 서버 자체는 막히지 않는다.
 */
object AdminGate {
    private const val HASH = "13ef98978575d14e12ed90eea62b94af3806299192be4cc5a29ca235214f9356"

    var unlocked by mutableStateOf(false)
        private set

    fun tryUnlock(input: String): Boolean {
        val h = MessageDigest.getInstance("SHA-256")
            .digest(input.trim().toByteArray())
            .joinToString("") { "%02x".format(it) }
        if (h == HASH) unlocked = true
        return unlocked
    }
}

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val repo: RosterRepository,
) : ViewModel() {
    /** 관리자가 대리 등록한 사람만 (본인 가입자는 addedBy 가 없다) */
    val members: StateFlow<List<RosterEntry>> = repo.observeUsers()
        .map { list -> list.filter { it.addedBy != null }.sortedBy { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _msg = MutableStateFlow<String?>(null)
    val msg: StateFlow<String?> = _msg
    fun clearMsg() { _msg.value = null }

    fun save(entry: RosterEntry) = viewModelScope.launch {
        _msg.value = if (repo.adminUpsert(entry)) "${entry.name} 등록됐습니다."
        else "저장 실패 — 인터넷 연결을 확인하세요."
    }

    fun delete(entry: RosterEntry) = viewModelScope.launch {
        _msg.value = if (repo.adminDelete(entry.uid)) "${entry.name} 삭제됐습니다."
        else "삭제 실패 — 인터넷 연결을 확인하세요."
    }
}

private data class Draft(
    val name: String = "",
    val empNo: String = "",
    val group: CrewGroup? = null,
    val offset: Int = 0,
    /** 수정 모드면 원래 사번 (사번은 문서 ID라 못 바꾼다) */
    val editingUid: String? = null,
)

/**
 * 관리자 화면 — 앱을 아직 안 깐 동료의 이름·사번·소속·근무 다이아를 대신 등록한다.
 * 저장되면 로그인 사용자와 같은 users/{사번} 문서가 되어 동료근무 화면에 바로 합류한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(onBack: () -> Unit, viewModel: AdminViewModel = hiltViewModel()) {
    val members by viewModel.members.collectAsStateWithLifecycle()
    val msg by viewModel.msg.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf<Draft?>(null) }
    var confirmDelete by remember { mutableStateOf<RosterEntry?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(msg) { msg?.let { snackbar.showSnackbar(it); viewModel.clearMsg() } }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
                },
                title = { Text("관리자 · 동료 대리등록", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { draft = Draft() },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("동료 등록") },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Text(
                "앱을 아직 설치하지 않은 동료의 근무를 대신 올립니다. " +
                    "등록하면 모두의 [동료근무] 화면에 바로 나타납니다. " +
                    "본인이 나중에 직접 로그인하면 그 사람 계정으로 자동 전환됩니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            HorizontalDivider()
            if (members.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    Text("등록한 동료가 없습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            LazyColumn {
                items(members.size, key = { members[it].uid }) { i ->
                    val m = members[i]
                    val todayDuty = Bundled.patternFor(m.group).dutyOn(LocalDate.now(), m.patternOffset).display
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(m.name, fontWeight = FontWeight.Bold)
                            Text(
                                "사번 ${m.uid} · ${m.group.label} · 오늘 ${todayDuty.ifBlank { "-" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = {
                            draft = Draft(m.name, m.uid, m.group, m.patternOffset, editingUid = m.uid)
                        }) { Icon(Icons.Default.Edit, "수정") }
                        IconButton(onClick = { confirmDelete = m }) {
                            Icon(Icons.Default.Delete, "삭제", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
                item { Spacer(Modifier.height(88.dp)) }
            }
        }
    }

    draft?.let { d ->
        MemberEditor(
            draft = d,
            onChange = { draft = it },
            onSave = { viewModel.save(it); draft = null },
            onDismiss = { draft = null },
        )
    }

    confirmDelete?.let { m ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("${m.name} 삭제") },
            text = { Text("동료근무 화면에서 사라집니다. 본인이 직접 가입한 사람이면 다시 나타납니다.") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(m); confirmDelete = null }) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("취소") } },
        )
    }
}

/** 등록·수정 폼. 근무 다이아는 달력과 똑같은 DutyPickerSheet(소속 → 근무 2단계)를 재사용한다. */
@Composable
private fun MemberEditor(
    draft: Draft,
    onChange: (Draft) -> Unit,
    onSave: (RosterEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    var picker by remember { mutableStateOf<DutyPickerState?>(null) }
    val today = remember { LocalDate.now() }
    val staff = BundledStaff.validate(draft.name, draft.empNo)
    val nameFilled = draft.name.isNotBlank() && draft.empNo.isNotBlank()
    val dutyLabel = draft.group?.let {
        "${it.label} · 오늘 ${Bundled.patternFor(it).dutyOn(today, draft.offset).display.ifBlank { "-" }}"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (draft.editingUid == null) "동료 등록" else "동료 수정") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = draft.name, onValueChange = { onChange(draft.copy(name = it)) },
                    label = { Text("이름") }, singleLine = true,
                )
                OutlinedTextField(
                    value = draft.empNo,
                    onValueChange = { s -> onChange(draft.copy(empNo = s.filter { it.isDigit() })) },
                    label = { Text("사번") }, singleLine = true,
                    enabled = draft.editingUid == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                if (nameFilled && staff == null) Text(
                    "명단에 없는 이름·사번입니다. 사번을 확인해주세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                OutlinedButton(
                    onClick = { picker = DutyPickerState(today, draft.group) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(dutyLabel ?: "근무 다이아 선택") }
            }
        },
        confirmButton = {
            TextButton(
                enabled = staff != null && draft.group != null,
                onClick = {
                    val g = draft.group ?: return@TextButton
                    onSave(RosterEntry(draft.empNo.trim(), draft.name.trim(), g, draft.offset, "admin"))
                },
            ) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )

    picker?.let { p ->
        DutyPickerSheet(
            picker = p,
            currentGroup = draft.group,
            currentOffset = draft.offset,
            onPickGroup = { picker = p.copy(group = it) },
            onBack = { picker = p.copy(group = null) },
            onPick = { g, index ->
                onChange(draft.copy(group = g, offset = Bundled.patternFor(g).offsetFor(today, index)))
                picker = null
            },
            onDismiss = { picker = null },
        )
    }
}

/** 설정 화면에서 쓰는 관리자 암호 입력 다이얼로그 */
@Composable
fun AdminPasswordDialog(onUnlocked: () -> Unit, onDismiss: () -> Unit) {
    var pw by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("관리자 암호") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pw,
                    onValueChange = { pw = it; wrong = false },
                    label = { Text("암호") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = wrong,
                )
                if (wrong) Text(
                    "암호가 틀렸습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = pw.isNotBlank(),
                onClick = { if (AdminGate.tryUnlock(pw)) onUnlocked() else wrong = true },
            ) { Text("확인") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
