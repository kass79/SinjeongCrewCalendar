package com.sinjeong.crewcalendar.presentation.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sinjeong.crewcalendar.domain.model.Bundled
import com.sinjeong.crewcalendar.domain.model.CrewRole
import com.sinjeong.crewcalendar.domain.model.User
import com.sinjeong.crewcalendar.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val userRepo: UserRepository,
) : ViewModel() {
    val user: StateFlow<User?> = userRepo.observeMe()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** 이름+사번 로그인 → 이후 근무선택·근무변경·메모가 이 사번으로 기록된다 */
    fun login(name: String, employeeNo: String) {
        viewModelScope.launch {
            userRepo.upsert(
                User(
                    uid = employeeNo.trim(),
                    name = name.trim(),
                    role = CrewRole.DRIVER_BRANCH,
                    patternId = Bundled.BRANCH_PATTERN.id,
                    patternOffset = 0,
                )
            )
        }
    }
}

@Composable
fun LoginScreen(onLogin: (name: String, employeeNo: String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var empNo by remember { mutableStateOf("") }
    val valid = name.trim().length >= 2 && empNo.trim().length >= 4

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(horizontal = 32.dp).widthIn(max = 400.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("신정승무 캘린더", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            Text(
                "이름과 사번을 입력하면 시작합니다.\n근무선택·메모가 이 사번으로 기록됩니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("이름") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = empNo, onValueChange = { empNo = it.filter { c -> c.isDigit() } },
                label = { Text("사번") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onLogin(name, empNo) },
                enabled = valid,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) { Text("시작하기", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) }
            Text(
                "※ 체험판은 데이터를 이 폰에만 저장합니다. 서버 연동 시 동료들과 자동 공유됩니다.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
