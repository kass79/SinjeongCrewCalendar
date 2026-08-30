package com.sinjeong.crewcalendar.presentation.menu

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.sinjeong.crewcalendar.domain.model.WeeklyMenu
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import kotlin.math.max

/**
 * 주간식단표 전체화면 뷰어 (v1.6.80).
 *
 * ## 왜 [com.sinjeong.crewcalendar.presentation.calendar.RouteImageDialog] 를 안 쓰는가
 *
 * 그쪽은 `assets/routes` 아래 webp **비트맵 전용**이다(코틀린 블록주석은 중첩돼서
 * 여기에 별표 경로를 그대로 쓰면 주석이 안 닫힌다 — 실제로 한 번 깨졌다).
 * 식단표는 앱이 그리는 벡터라
 *  ① 몇 배로 키워도 글자가 뭉개지지 않고 ② 다크모드·글자배율을 테마에서 그대로 받는다.
 * 비트맵으로 먼저 굽고 그 뷰어에 넘기면 이 둘을 통째로 버리게 된다.
 * 그리고 v1.6.77 이 폴드 펼침 잘림을 고쳐 둔 그 파일을 **아예 건드리지 않는다** — 회귀 위험 0.
 * 공유는 이쪽에서 [rememberGraphicsLayer] 로 화면에 그려진 그대로를 떠서 근무표 공유와
 * 같은 `cache/share` + FileProvider 경로에 얹는다(같은 방식이라 새 인프라가 없다).
 *
 * ## 확대·이동
 *
 * 핀치로 1~4배, 손가락 하나로 끌어 이동한다. 세로 스크롤 컨테이너를 쓰지 않는 이유 —
 * 스크롤 제스처와 이동 제스처가 서로 먹어서 확대 상태에서 아래쪽 칸에 손이 안 닿는다.
 * [detectTransformGestures] 하나로 둘 다 받고 내용 크기로 범위를 잘라 낸다.
 */
@Composable
fun MenuDialog(
    /** 주 시작일(월) → 21칸. 이번 주·다음 주만 들어온다 */
    weeks: Map<LocalDate, WeeklyMenu>,
    thisWeek: LocalDate,
    style: MenuStyle,
    isAdmin: Boolean,
    onUpload: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var showNext by remember { mutableStateOf(false) }
    val nextWeek = thisWeek.plusWeeks(1)
    val hasNext = weeks.containsKey(nextWeek)
    val shown = if (showNext && hasNext) weeks[nextWeek] else weeks[thisWeek]

    // v1.6.77 이 밝힌 것과 같은 이유로 인셋을 Dialog **밖에서** 읽는다 — 안쪽에선 0으로 보인다.
    val bars = WindowInsets.systemBars.asPaddingValues()
    val overshoot = bars.calculateTopPadding() + bars.calculateBottomPadding()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            val layer = rememberGraphicsLayer()
            Column(Modifier.fillMaxSize().padding(bottom = overshoot)) {
                Box(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(start = 20.dp, top = 18.dp, end = 12.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            "주간식단표", fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(end = 96.dp),
                        )
                        // 다음 주 표가 **이미 올라와 있을 때만** 전환 칩이 보인다.
                        // 없는 주를 고르게 두면 빈 화면으로 떨어져 "고장난 것"처럼 보인다.
                        if (hasNext) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = !showNext, onClick = { showNext = false },
                                label = { Text("이번 주", fontWeight = FontWeight.Bold) },
                            )
                            FilterChip(
                                selected = showNext, onClick = { showNext = true },
                                label = { Text("다음 주", fontWeight = FontWeight.Bold) },
                            )
                        }
                    }
                    Row(Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                        if (shown != null) IconButton(onClick = {
                            scope.launch {
                                runCatching {
                                    shareMenu(ctx, layer.toImageBitmap().asAndroidBitmap(), shown.weekStart)
                                }
                            }
                        }) { Icon(Icons.Default.Share, "식단표 공유") }
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "닫기") }
                    }
                }

                if (shown == null || shown.isBlank) {
                    MenuEmptyNotice(isAdmin, onUpload, Modifier.fillMaxSize())
                } else {
                    BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
                        val vw = constraints.maxWidth.toFloat()
                        val vh = constraints.maxHeight.toFloat()
                        val posterW = with(LocalDensity.current) { constraints.maxWidth.toDp() }
                        var scale by remember { mutableFloatStateOf(1f) }
                        var offset by remember { mutableStateOf(Offset.Zero) }
                        var content by remember { mutableStateOf(IntSize.Zero) }
                        // 주를 바꾸면 길이가 달라지므로 위치를 처음으로 되돌린다
                        LaunchedEffect(showNext) { scale = 1f; offset = Offset.Zero }

                        Box(
                            Modifier.fillMaxSize().pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(1f, 4f)
                                    val maxX = max(0f, content.width * scale - vw)
                                    val maxY = max(0f, content.height * scale - vh)
                                    offset = Offset(
                                        (offset.x + pan.x).coerceIn(-maxX, 0f),
                                        (offset.y + pan.y).coerceIn(-maxY, 0f),
                                    )
                                }
                            },
                        ) {
                            MenuPoster(
                                menu = shown,
                                style = style,
                                modifier = Modifier
                                    .width(posterW)
                                    // 부모(Box)의 최대 높이를 넘어 자기 내용만큼 재지게 한다.
                                    // 없으면 21칸이 화면 높이에 눌려 아래쪽이 통째로 잘린다.
                                    .wrapContentHeight(Alignment.Top, unbounded = true)
                                    .graphicsLayer(
                                        scaleX = scale, scaleY = scale,
                                        translationX = offset.x, translationY = offset.y,
                                        transformOrigin = TransformOrigin(0f, 0f),
                                    )
                                    .onSizeChanged { content = it }
                                    // ⚠ `graphicsLayer` **뒤**에 와야 한다 — 확대·이동이 적용되기 전
                                    // 원본 크기 그대로가 기록돼 공유 이미지가 늘 온전한 포스터가 된다.
                                    .drawWithContent {
                                        layer.record { this@drawWithContent.drawContent() }
                                        drawLayer(layer)
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 이번 주 표가 아직 없을 때. **지난주 메뉴는 절대 안 보여준다** —
 * 틀린 정보로 사람을 움직이게 하느니 없다고 말하는 게 낫다.
 */
@Composable
fun MenuEmptyNotice(isAdmin: Boolean, onUpload: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("🍚", fontSize = 40.sp)
            Text(
                "이번 주 식단표가 아직 없어요",
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "구내식당 표가 올라오면 여기에 바로 보입니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isAdmin) androidx.compose.material3.FilledTonalButton(onClick = onUpload) {
                Text("식단표 올리기", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** 근무표 공유(`cache/share/duty_*.png`)와 **같은 폴더·같은 FileProvider**를 쓴다. */
private fun shareMenu(ctx: Context, bmp: Bitmap, weekStart: LocalDate) {
    val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
    val f = File(dir, "menu_$weekStart.png")
    FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
    ctx.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            "식단표 공유",
        )
    )
}
