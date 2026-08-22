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
import com.sinjeong.crewcalendar.presentation.theme.BrandGreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val userRepo: UserRepository,
) : ViewModel() {
    val user: StateFlow<User?> = userRepo.observeMe()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * 로그인 = 이름+사번 명단 대조 한 단계가 전부(v1.6.16에서 PIN 만들기 제거).
     * 통과하면 바로 저장·잠금해제 → observeMe가 user를 방출해 달력으로 넘어간다.
     */
    fun submitCredential(name: String, empNo: String) {
        val staff = BundledStaff.validate(name, empNo)
        if (staff == null) {
            _error.value = "명단에 없는 이름·사번입니다. 사번을 확인해주세요."
            return
        }
        _error.value = null
        viewModelScope.launch {
            userRepo.register(
                User(
                    uid = empNo.trim(),
                    name = name.trim(),
                    role = if (staff.isConductor) CrewRole.CONDUCTOR else CrewRole.DRIVER_BRANCH,
                    patternId = if (staff.isConductor) Bundled.MAIN_PATTERN.id else Bundled.BRANCH_PATTERN.id,
                    patternOffset = 0,
                )
            )
        }
    }

    fun clearError() { _error.value = null }
}

@Composable
fun LoginScreen(viewModel: AuthViewModel = hiltViewModel()) {
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
            // 로그인은 Scaffold 밖이라 인셋을 아무도 안 준다 — 키보드가 뜨거나 짧은 화면이면
            // 히어로가 빠지면서 제목이 상태바 시계 위로 올라붙었다. 배경 그라데이션은
            // Box에 있어 그대로 전체를 덮고, 내용만 안전영역 안으로 들인다.
            Modifier.widthIn(max = 400.dp)
                .safeDrawingPadding()
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
                color = BrandGreen,
            )

            CredentialCard(error, viewModel::submitCredential)

            // 저작권은 항상 표시한다.
            // compactTop(키보드·짧은 화면)에서는 지우지 않고 여백·자간만 줄여 자리만 아낀다.
            Spacer(Modifier.height(if (compactTop) 2.dp else 10.dp))
            Text(
                "© 2026  KANG SUNG JIN",
                fontSize = if (compactTop) 11.sp else 12.sp,
                letterSpacing = if (compactTop) 1.5.sp else 2.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = BrandGreen,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "ALL RIGHTS RESERVED",
                fontSize = if (compactTop) 9.5.sp else 10.5.sp,
                letterSpacing = if (compactTop) 1.sp else 1.8.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF4A4458),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun loginFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    focusedTextColor = Color(0xFF232030),
    unfocusedTextColor = Color(0xFF232030),
    focusedBorderColor = BrandGreen,
    unfocusedBorderColor = Color(0xFFD9CFE4),
    focusedLabelColor = BrandGreen,
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
            containerColor = BrandGreen,
            contentColor = Color.White,
        ),
    ) { Text(label, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) }
}

@Composable
private fun ColumnScope.CredentialCard(
    error: String?,
    onSubmit: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var empNo by remember { mutableStateOf("") }
    val valid = name.trim().length >= 2 && empNo.trim().length >= 4

    HintText("이름과 사번만 넣으면 바로 시작해요.")
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
    PrimaryButton("시작하기", valid) { onSubmit(name, empNo) }
}
