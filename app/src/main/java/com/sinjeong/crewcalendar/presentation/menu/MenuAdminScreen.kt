package com.sinjeong.crewcalendar.presentation.menu

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.sinjeong.crewcalendar.data.menu.MenuHwp
import com.sinjeong.crewcalendar.domain.model.Meal
import com.sinjeong.crewcalendar.domain.model.MenuHwpx
import com.sinjeong.crewcalendar.domain.model.MenuOcr
import com.sinjeong.crewcalendar.domain.model.MenuTable
import com.sinjeong.crewcalendar.domain.model.OcrWord
import com.sinjeong.crewcalendar.domain.model.WeeklyMenu
import com.sinjeong.crewcalendar.domain.model.weekStartOf
import com.sinjeong.crewcalendar.domain.repository.AdminWriteResult
import com.sinjeong.crewcalendar.domain.repository.MenuRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import javax.inject.Inject

/**
 * 관리자 · 주간식단표 올리기 (v1.6.80).
 *
 * 사진(갤러리·촬영)이나 PDF 를 넣으면 **기기 안에서** 글자를 읽어 21칸을 채우고,
 * 관리자가 손으로 고친 뒤 [저장]을 눌러야 비로소 전 직원에게 보인다.
 *
 * ## 인식은 실패해도 된다
 *
 * 코팅된 표를 찍은 사진이라 빛 반사가 있고, 그래서 **빈 칸으로 두고 사람이 채우는 구조**로 만들었다.
 * 인식이 통째로 실패해도 21칸을 손으로 넣어 100% 완성할 수 있다.
 *
 * ## 왜 다운로드형(unbundled) ML Kit 인가
 *
 * `play-services-mlkit-text-recognition-korean` = 한국어 모델이 **구글 플레이 서비스** 쪽에 있고
 * APK 에는 얇은 접착제만 들어간다. 이 화면은 관리자 한 사람만 쓰는데 모델 4MB 를 전 직원 APK 에
 * 태울 이유가 없다(현재 release 28MB). 대신 첫 사용 때 모델이 내려받아지는 짧은 대기가 있다.
 */
@HiltViewModel
class MenuAdminViewModel @Inject constructor(
    private val repo: MenuRepository,
) : ViewModel() {

    var weekStart by mutableStateOf(weekStartOf(LocalDate.now()))
        private set
    var cells by mutableStateOf(List(WeeklyMenu.CELLS) { "" })
        private set
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
    /** 인식 직후 몇 칸이 찼는지 — 화면 안내와 검증 보고에 쓴다 */
    var recognized by mutableStateOf<Int?>(null)

    fun shiftWeek(weeks: Long) { weekStart = weekStart.plusWeeks(weeks) }
    fun setCell(day: Int, meal: Meal, text: String) {
        cells = cells.toMutableList().also { it[day * WeeklyMenu.MEALS + meal.ordinal] = text }
    }
    fun clearAll() { cells = List(WeeklyMenu.CELLS) { "" }; recognized = null }

    /**
     * 사진·PDF·**한글파일** 어느 것이든 여기 하나로 들어온다(v1.6.81 ④).
     * 무엇인지는 **파일 앞머리 몇 바이트**로 가른다 — 확장자·MIME 은 기기마다 안 맞는 일이 잦다
     * ([sniff] 주석).
     */
    fun load(ctx: Context, uri: Uri) = viewModelScope.launch {
        busy = true
        val result = runCatching { withContext(Dispatchers.IO) { readTable(ctx, uri) } }
        busy = false
        result.onSuccess { (newCells, weekFromText, kind) ->
            cells = newCells
            recognized = newCells.count { it.isNotBlank() }
            weekFromText?.let { weekStart = it }
            val what = if (kind == DocKind.HWP || kind == DocKind.HWPX) "한글파일에서" else "글자인식으로"
            message = if (recognized == 0)
                "$what 21칸을 채우지 못했습니다 — 아래에서 직접 채워 주세요."
            else "$what 21칸 중 ${recognized}칸을 채웠습니다. 틀린 곳을 눌러 고치세요."
        }.onFailure {
            message = "읽기 실패: ${it.message ?: "알 수 없는 오류"} — 직접 채워 주세요."
        }
    }

    suspend fun alreadyExists(): Boolean = repo.exists(weekStart)

    fun save(onDone: () -> Unit) = viewModelScope.launch {
        busy = true
        val r = repo.save(weekStart, cells)
        busy = false
        message = when (r) {
            AdminWriteResult.OK -> "${weekStart.monthValue}월 ${weekStart.dayOfMonth}일 주 식단표를 올렸습니다."
            AdminWriteResult.DENIED -> "서버가 거부했습니다 — 보안 규칙을 확인하세요."
            AdminWriteResult.FAILED -> "저장 실패 — 인터넷 연결을 확인하세요."
        }
        if (r == AdminWriteResult.OK) onDone()
    }
}

/** 넣은 파일이 무엇인가 */
enum class DocKind { IMAGE, PDF, HWP, HWPX }

/**
 * **파일 앞머리 8바이트로 종류를 가른다**(v1.6.81 ④).
 *
 * 확장자·MIME 으로 가르지 않는 이유: 기기·파일앱마다 `.hwp` 의 MIME 이 제각각이고
 * (`application/x-hwp`·`application/haansofthwp`·`application/octet-stream`…),
 * `content://` URI 는 이름조차 안 주는 경우가 있다. 파일 선택기 필터는 **모든 형식**으로 넓게 잡고
 * **실제 내용**으로 판정하는 것이 기기 편차에 안 흔들린다.
 * (필터 문자열을 여기 적지 않는 이유: 별표+빗금 조합이 코틀린 주석을 닫아 버린다. 실제 값은
 *  아래 `pickDoc.launch(...)` 한 줄에 있다.)
 *
 * | 앞머리 | 무엇 |
 * |---|---|
 * | `%PDF` | PDF |
 * | `D0 CF 11 E0 A1 B1 1A E1` | 복합문서 = **.hwp**(5.0) |
 * | `PK 03 04` | zip = **.hwpx** (아니면 표가 안 나와 빈 21칸으로 떨어진다) |
 * | 그 밖 | 사진 → 글자인식 |
 */
private fun sniff(ctx: Context, uri: Uri): DocKind {
    val head = runCatching {
        ctx.contentResolver.openInputStream(uri)?.use { s -> ByteArray(8).also { s.read(it) } }
    }.getOrNull() ?: return DocKind.IMAGE
    return when {
        head.size >= 4 && head[0] == '%'.code.toByte() && head[1] == 'P'.code.toByte() &&
            head[2] == 'D'.code.toByte() && head[3] == 'F'.code.toByte() -> DocKind.PDF
        MenuHwp.looksLikeHwp(head) -> DocKind.HWP
        MenuHwpx.looksLikeZip(head) -> DocKind.HWPX
        else -> DocKind.IMAGE
    }
}

/** 사진/PDF/한글파일 → (21칸, 기간에서 읽은 주 시작일, 무엇이었는지) */
private suspend fun readTable(ctx: Context, uri: Uri): Triple<List<String>, LocalDate?, DocKind> {
    val kind = sniff(ctx, uri)
    // ── 한글파일: 표 칸을 직접 읽는다. 글자인식이 낄 자리가 없어 **정확도가 100%** 다 ──
    if (kind == DocKind.HWP || kind == DocKind.HWPX) {
        val doc = ctx.contentResolver.openInputStream(uri)?.use { s ->
            if (kind == DocKind.HWP) MenuHwp.read(s) else MenuHwpx.read(s)
        } ?: error("파일을 열 수 없습니다")
        return Triple(MenuTable.toCells(doc), MenuOcr.parseWeekStart(doc.text), kind)
    }

    val image = if (kind == DocKind.PDF) InputImage.fromBitmap(renderPdfFirstPage(ctx, uri), 0)
    // 사진은 파일 경로로 넘긴다 — ML Kit 가 EXIF 회전을 알아서 편다(직접 비트맵을 만들면
    // 세로로 찍은 사진이 눕는다).
    else InputImage.fromFilePath(ctx, uri)

    // Task→코루틴 변환은 이미 있는 `kotlinx-coroutines-play-services` 로 끝난다(새 의존성 0).
    val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
    val text = recognizer.process(image).await()

    val words = text.textBlocks
        .flatMap { it.lines }
        .flatMap { it.elements }
        .mapNotNull { e ->
            val b = e.boundingBox ?: return@mapNotNull null
            OcrWord(e.text, b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat())
        }
    return Triple(MenuOcr.toCells(words), MenuOcr.parseWeekStart(text.text), kind)
}

/**
 * PDF 1쪽을 비트맵으로. 안드로이드 내장 [PdfRenderer] 라 새 의존성이 0이다(minSdk 26 에서 쓸 수 있다).
 * 가로 2000px 로 키워 렌더한다 — 원본 크기 그대로면 글자가 작아 인식률이 뚝 떨어진다.
 */
private fun renderPdfFirstPage(ctx: Context, uri: Uri): Bitmap {
    val pfd: ParcelFileDescriptor = ctx.contentResolver.openFileDescriptor(uri, "r")
        ?: error("PDF를 열 수 없습니다")
    pfd.use { fd ->
        PdfRenderer(fd).use { renderer ->
            require(renderer.pageCount > 0) { "빈 PDF입니다" }
            renderer.openPage(0).use { page ->
                val w = 2000
                val h = (w.toLong() * page.height / page.width).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                android.graphics.Canvas(bmp).drawColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return bmp
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuAdminScreen(onBack: () -> Unit, vm: MenuAdminViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Pair<Int, Meal>?>(null) }
    var confirmOverwrite by remember { mutableStateOf(false) }
    // 촬영 결과를 받을 파일 — 캐시라 자동으로 청소된다. cache/share 와 섞지 않으려고 menu/ 로 나눈다.
    val shotUri = remember {
        val dir = File(ctx.cacheDir, "menu").apply { mkdirs() }
        FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", File(dir, "shot.jpg"))
    }

    LaunchedEffect(vm.message) { vm.message?.let { snackbar.showSnackbar(it); vm.message = null } }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.load(ctx, it) }
    }
    // ⚠ **필터를 모든 형식으로 넓게 잡는다**(v1.6.81 ④). 한글파일의 MIME 은 기기·파일앱마다 제각각이라
    // (`application/x-hwp`·`application/haansofthwp`·`application/octet-stream` …) 좁게 걸면
    // **선택기에 hwp 가 아예 안 보이는 기기**가 생긴다. 무엇인지는 고른 뒤 앞머리 바이트로 가른다.
    val pickDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.load(ctx, it) }
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) vm.load(ctx, shotUri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
                },
                title = { Text("관리자 · 주간식단표", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "한글파일(.hwp/.hwpx)이 가장 정확합니다 — 표 칸을 그대로 읽어 글자인식이 낄 자리가 " +
                    "없습니다. 사진·PDF는 글자를 읽어 21칸을 채웁니다. 원본 파일은 저장하지 않고 " +
                    "글자만 올라갑니다. 틀린 칸은 눌러서 고치고 마지막에 [저장]을 눌러야 모두에게 보입니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalButton(
                    onClick = { pickImage.launch("image/*") },
                    enabled = !vm.busy, modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                ) { Text("갤러리", fontSize = 12.sp) }
                FilledTonalButton(
                    onClick = { runCatching { takePhoto.launch(shotUri) } },
                    enabled = !vm.busy, modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                ) { Text("촬영", fontSize = 12.sp) }
                FilledTonalButton(
                    onClick = { pickDoc.launch(arrayOf("*/*")) },
                    enabled = !vm.busy, modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                ) { Text("PDF·한글", fontSize = 12.sp) }
            }
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp))

            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            // ── 어느 주에 저장되는가 ────────────────────────────
            Text("저장될 주", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { vm.shiftWeek(-1) }) { Text("◀ 이전 주") }
                Text(
                    "${vm.weekStart.monthValue}월 ${vm.weekStart.dayOfMonth}일(월)" +
                        " ~ ${vm.weekStart.plusDays(6).monthValue}월 ${vm.weekStart.plusDays(6).dayOfMonth}일(일)",
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    fontWeight = FontWeight.Bold, fontSize = 13.sp,
                )
                TextButton(onClick = { vm.shiftWeek(1) }) { Text("다음 주 ▶") }
            }
            val today = remember { weekStartOf(LocalDate.now()) }
            Text(
                when {
                    vm.weekStart == today -> "이번 주 — 저장하면 바로 모두에게 보입니다."
                    vm.weekStart == today.plusWeeks(1) -> "다음 주 — 다음 주 월요일부터 기본으로 보입니다."
                    vm.weekStart < today -> "⚠ 지난 주입니다. 지난 주 식단표는 아무에게도 보이지 않습니다."
                    else -> "아직 먼 주입니다. 주를 다시 확인하세요."
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (vm.weekStart < today) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "21칸 확인 (${vm.cells.count { it.isNotBlank() }}/21 채움)",
                    fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { vm.clearAll() }) { Text("모두 지우기") }
            }

            // ── 7일 × 3끼니 격자 ────────────────────────────────
            for (d in 0 until WeeklyMenu.DAYS) {
                val date = vm.weekStart.plusDays(d.toLong())
                Text(
                    "${date.monthValue}/${date.dayOfMonth} ${WeeklyMenu.DAY_LABELS[d]}",
                    fontWeight = FontWeight.ExtraBold, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Meal.entries.forEach { meal ->
                        val text = vm.cells[d * WeeklyMenu.MEALS + meal.ordinal]
                        Column(
                            Modifier.weight(1f)
                                .heightIn(min = 84.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (text.isBlank()) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                )
                                .clickable { editing = d to meal }
                                .padding(6.dp),
                            verticalArrangement = Arrangement.spacedBy(1.dp),
                        ) {
                            Text(
                                meal.label, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text.ifBlank { "비어 있음\n눌러서 입력" },
                                fontSize = 10.sp, lineHeight = 13.sp,
                                color = if (text.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { scope.launch { if (vm.alreadyExists()) confirmOverwrite = true else vm.save(onBack) } },
                enabled = !vm.busy && vm.cells.any { it.isNotBlank() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("저장하고 모두에게 보이기", fontWeight = FontWeight.ExtraBold) }
            Spacer(Modifier.height(24.dp))
        }
    }

    editing?.let { (d, meal) ->
        val date = vm.weekStart.plusDays(d.toLong())
        var draft by remember(d, meal) {
            mutableStateOf(vm.cells[d * WeeklyMenu.MEALS + meal.ordinal])
        }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = {
                Text("${date.monthValue}/${date.dayOfMonth}(${WeeklyMenu.DAY_LABELS[d]}) ${meal.label}")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "메뉴 한 줄에 하나씩. 엔터로 줄을 바꿉니다.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = draft, onValueChange = { draft = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
                        placeholder = { Text("잡곡밥\n북어국\n포기김치") },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.setCell(d, meal, draft.trim()); editing = null }) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("취소") } },
        )
    }

    if (confirmOverwrite) AlertDialog(
        onDismissRequest = { confirmOverwrite = false },
        title = { Text("이미 올라와 있습니다") },
        text = {
            Text(
                "${vm.weekStart.monthValue}월 ${vm.weekStart.dayOfMonth}일 주 식단표가 이미 있습니다. " +
                    "지금 화면의 내용으로 덮어쓸까요? 다른 주는 그대로 남습니다."
            )
        },
        confirmButton = {
            TextButton(onClick = { confirmOverwrite = false; vm.save(onBack) }) {
                Text("덮어쓰기", fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = { TextButton(onClick = { confirmOverwrite = false }) { Text("취소") } },
    )
}
