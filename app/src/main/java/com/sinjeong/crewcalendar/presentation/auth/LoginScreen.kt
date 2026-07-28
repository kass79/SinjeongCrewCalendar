package com.sinjeong.crewcalendar.presentation.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import com.sinjeong.crewcalendar.R
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sinjeong.crewcalendar.domain.model.Bundled
import com.sinjeong.crewcalendar.domain.model.BundledStaff
import com.sinjeong.crewcalendar.domain.model.CrewRole
import com.sinjeong.crewcalendar.domain.model.User
import com.sinjeong.crewcalendar.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AuthMode { CREDENTIAL, SET_PIN, PIN }

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val userRepo: UserRepository,
) : ViewModel() {
    val user: StateFlow<User?> = userRepo.observeMe()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val savedName: String? = userRepo.savedName()

    private val _mode = MutableStateFlow(
        if (userRepo.hasPin() && savedName != null) AuthMode.PIN else AuthMode.CREDENTIAL
    )
    val mode: StateFlow<AuthMode> = _mode.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var pendingUser: User? = null

    /** 1단계: 이름+사번을 명단과 대조 → 통과 시 PIN 설정 화면으로 */
    fun submitCredential(name: String, empNo: String) {
        val staff = BundledStaff.validate(name, empNo)
        if (staff == null) {
            _error.value = "명단에 없는 이름·사번입니다. 사번을 확인해주세요."
            return
        }
        pendingUser = User(
            uid = empNo.trim(),
            name = name.trim(),
            role = if (staff.isConductor) CrewRole.CONDUCTOR else CrewRole.DRIVER_BRANCH,
            patternId = if (staff.isConductor) Bundled.MAIN_PATTERN.id else Bundled.BRANCH_PATTERN.id,
            patternOffset = 0,
        )
        _error.value = null
        _mode.value = AuthMode.SET_PIN
    }

    /** 2단계: 4자리 PIN 설정 → 저장 + 잠금해제 (observeMe가 user 방출 → 화면 전환) */
    fun setPin(pin: String, pinConfirm: String) {
        if (pin.length != 4 || !pin.all { it.isDigit() }) {
            _error.value = "4자리 숫자를 입력하세요."
            return
        }
        if (pin != pinConfirm) {
            _error.value = "비밀번호가 일치하지 않습니다."
            return
        }
        val u = pendingUser ?: return
        _error.value = null
        viewModelScope.launch { userRepo.registerWithPin(u, pin) }
    }

    /** 재실행 시: PIN 검증 후 잠금해제 */
    fun unlock(pin: String) {
        if (userRepo.unlockWithPin(pin)) _error.value = null
        else _error.value = "비밀번호가 일치하지 않습니다."
    }

    /** 다른 사람으로 로그인 → 신원·PIN 삭제 후 이름+사번부터 다시 */
    fun useAnotherAccount() {
        viewModelScope.launch { userRepo.signOut() }
        _mode.value = AuthMode.CREDENTIAL
        _error.value = null
    }

    fun clearError() { _error.value = null }
}

@Composable
fun LoginScreen(viewModel: AuthViewModel = hiltViewModel()) {
    val mode by viewModel.mode.collectAsState()
    val error by viewModel.error.collectAsState()

    // 키보드가 떴거나 세로가 짧은 화면(폴드 펼침·가로)에선 히어로·여백을 걷어내 입력칸 자리 확보
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val shortScreen = LocalConfiguration.current.screenHeightDp < 640
    val compactTop = imeVisible || shortScreen

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFFFDE7EE), Color(0xFFEDE9FB))),
        ),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier.widthIn(max = 400.dp)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp)
                .padding(top = if (compactTop) 12.dp else 40.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!compactTop) Image(
                painterResource(R.drawable.login_hero),
                contentDescription = null,
                modifier = Modifier.size(130.dp).clip(RoundedCornerShape(28.dp)),
            )
            Text(
                "신정승무 캘린더",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF1D3FA8),
            )

            when (mode) {
                AuthMode.CREDENTIAL -> CredentialCard(error, viewModel::submitCredential)
                AuthMode.SET_PIN -> SetPinCard(error, viewModel::setPin)
                AuthMode.PIN -> PinCard(
                    viewModel.savedName, error, viewModel::unlock, viewModel::useAnotherAccount,
                )
            }

            if (!compactTop) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "© 2026  KANG SUNGJIN",
                    fontSize = 11.sp,
                    letterSpacing = 3.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1D3FA8).copy(alpha = 0.55f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "All Rights Reserved",
                    fontSize = 9.sp,
                    letterSpacing = 1.5.sp,
                    color = Color(0xFF8A8496),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun loginFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    focusedTextColor = Color(0xFF232030),
    unfocusedTextColor = Color(0xFF232030),
    focusedBorderColor = Color(0xFF1D3FA8),
    unfocusedBorderColor = Color(0xFFD9CFE4),
    focusedLabelColor = Color(0xFF1D3FA8),
    unfocusedLabelColor = Color(0xFF8A8496),
)

@Composable
private fun HintText(text: String) = Text(
    text,
    style = MaterialTheme.typography.bodyMedium,
    color = Color(0xFF5A5566),
)

@Composable
private fun ErrorText(error: String?) {
    if (error != null) Text(
        error,
        style = MaterialTheme.typography.bodySmall,
        color = Color(0xFFC62828),
    )
}

@Composable
private fun PrimaryButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF1D3FA8),
            contentColor = Color.White,
        ),
    ) { Text(label, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) }
}

/** 4자리 숫자만 허용 */
private fun pinFilter(s: String) = s.filter { it.isDigit() }.take(4)

@Composable
private fun ColumnScope.CredentialCard(
    error: String?,
    onSubmit: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var empNo by remember { mutableStateOf("") }
    val valid = name.trim().length >= 2 && empNo.trim().length >= 4

    HintText("이름과 사번으로 처음 로그인해요.")
    OutlinedTextField(
        value = name, onValueChange = { name = it },
        label = { Text("이름") }, singleLine = true,
        colors = loginFieldColors(), shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = empNo, onValueChange = { empNo = it.filter { c -> c.isDigit() } },
        label = { Text("사번") }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = loginFieldColors(), shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    )
    ErrorText(error)
    PrimaryButton("확인", valid) { onSubmit(name, empNo) }
}

@Composable
private fun ColumnScope.SetPinCard(
    error: String?,
    onSet: (String, String) -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    HintText("4자리 비밀번호를 만들어 주세요.\n다음부터 이 번호로 로그인해요.")
    PinField("비밀번호", pin) { pin = it }
    PinField("비밀번호 확인", confirm) { confirm = it }
    ErrorText(error)
    PrimaryButton("설정", pin.length == 4 && confirm.length == 4) { onSet(pin, confirm) }
}

@Composable
private fun ColumnScope.PinCard(
    savedName: String?,
    error: String?,
    onUnlock: (String) -> Unit,
    onUseAnother: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }

    HintText("${savedName ?: ""}님, 비밀번호를 입력하세요.")
    PinField("비밀번호", pin) { pin = it }
    ErrorText(error)
    PrimaryButton("로그인", pin.length == 4) { onUnlock(pin) }
    TextButton(onClick = onUseAnother) {
        Text("다른 사람으로 로그인", color = Color(0xFF1D3FA8))
    }
}

@Composable
private fun PinField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = { onChange(pinFilter(it)) },
        label = { Text(label) }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        colors = loginFieldColors(), shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}
