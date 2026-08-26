package com.sinjeong.crewcalendar.presentation.calendar

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 행로표 원본 뷰어 — assets/routes/{asset}.webp, 핀치 확대(1~6배)·드래그 이동.
 *
 * v1.6.9에서 넣은 세로화면 자동 90도 회전은 **v1.6.16에서 기본 꺼짐**으로 바꿨다
 * (사용자: "세로로 하면 보기 불편해"). 기본은 원본 방향 + 화면 폭 맞춤이고,
 * 제목줄 오른쪽 회전 버튼으로 필요할 때만 돌린다. 가로형 이미지일 때만 버튼이 뜬다.
 *
 * @param header 제목 바로 아래 칸. 비워 두면 아무것도 안 그린다(행로표·시각표는 그대로).
 *   침실배정표(v1.6.74)가 평평/평휴/휴휴/휴평 전환 칩을 여기에 끼운다 — 4종을 각각 다른
 *   다이얼로그로 만드는 대신 `asset`만 갈아 끼우면 되도록.
 */
@Composable
fun RouteImageDialog(
    asset: String,
    title: String,
    onDismiss: () -> Unit,
    header: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val src = remember(asset) {
        runCatching {
            context.assets.open("routes/$asset.webp").use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            var scale by remember { mutableFloatStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }
            var rotated by remember(asset) { mutableStateOf(false) }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val landscapeImage = src != null && src.width > src.height
                // 원본(src)은 remember(asset)가 계속 붙들고 있으므로 recycle 하지 않는다.
                val bitmap = remember(src, rotated) {
                    if (src != null && rotated && src.width > src.height) {
                        Bitmap.createBitmap(
                            src, 0, 0, src.width, src.height,
                            Matrix().apply { postRotate(90f) }, true,
                        )
                    } else {
                        src
                    }
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = title,
                        contentScale = ContentScale.Fit,
                        filterQuality = FilterQuality.High,
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(1f, 6f)
                                    offset = if (scale > 1f) offset + pan else Offset.Zero
                                }
                            }
                            .graphicsLayer(
                                scaleX = scale, scaleY = scale,
                                translationX = offset.x, translationY = offset.y,
                            ),
                    )
                } else {
                    Text(
                        "행로표 이미지를 찾을 수 없습니다",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(
                    Modifier.align(Alignment.TopStart).padding(start = 20.dp, top = 18.dp, end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // 제목만 오른쪽 버튼(회전·닫기) 자리를 비켜준다. 아래 header는 버튼보다 낮은 줄이라
                    // 폭을 다 써도 된다 — 글자배율 1.5 · 360dp에서 칩 넉 장이 들어가려면 이 폭이 필요하다.
                    Text(title, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(end = 84.dp))
                    header()
                }
                Row(Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                    // 가로형 표만 돌릴 값어치가 있다 — 세로형(근무시각표 등)은 버튼을 숨긴다
                    if (landscapeImage) IconButton(onClick = {
                        rotated = !rotated
                        scale = 1f; offset = Offset.Zero
                    }) {
                        Icon(
                            Icons.Default.ScreenRotation,
                            if (rotated) "원래 방향으로" else "90도 회전",
                            tint = if (rotated) MaterialTheme.colorScheme.primary
                            else LocalContentColor.current,
                        )
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "닫기") }
                }
            }
        }
    }
}

/**
 * 행로표 원본을 상세시트 안에 인라인 표시 — 탭하면 전체화면 확대.
 * bleed: 부모 Column의 가로 패딩을 이만큼 되밀어(음수 패딩) 이미지만 화면 끝까지 넓힌다.
 *        바깥에 보고하는 폭은 그대로라 형제 항목(제목·칩·메모·버튼)은 영향받지 않는다.
 * zoom:  1f = 가용 폭에 딱 맞춤(가로 스크롤 없음, 기존 동작). 1f 초과면 그 배율로 키우고
 *        넘치는 폭은 가로 스크롤. 행로표가 ~2.13:1 가로형이라 폭을 키워야 세로가 커진다.
 * vStretch: 세로만 늘리는 배율(원본 비율 왜곡). 폭을 못 키우는 펼침 패널에서 줄 높이를 벌려고
 *        쓴다(v1.6.10, 펼침 1.4f). 가로 스크롤이 안 생기니 "전체 폭 한눈에"가 유지된다.
 *        전체화면 다이얼로그는 다른 컴포저블이라 왜곡이 새어나가지 않는다.
 */
@Composable
fun RouteImageInline(
    asset: String,
    bleed: Dp = 0.dp,
    zoom: Float = 1f,
    vStretch: Float = 1f,
    onExpand: () -> Unit,
) {
    val context = LocalContext.current
    val bitmap = remember(asset) {
        runCatching {
            context.assets.open("routes/$asset.webp").use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }
    if (bitmap != null) {
        BoxWithConstraints(
            Modifier
                .layout { measurable, constraints ->
                    val extra = if (constraints.hasBoundedWidth) bleed.roundToPx() * 2 else 0
                    val p = measurable.measure(constraints.copy(maxWidth = constraints.maxWidth + extra))
                    layout(p.width - extra, p.height) { p.place(-extra / 2, 0) }
                }
                .clip(RoundedCornerShape(10.dp)),
        ) {
            val w = maxWidth * zoom
            val h = w * bitmap.height / bitmap.width * vStretch
            Box(Modifier.horizontalScroll(rememberScrollState())) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "행로표 원본",
                    // h를 원본 비율로 직접 계산하므로 FillBounds여도 왜곡되지 않는다.
                    // horizontalScroll(무한 폭 제약) 안에서는 fillMaxWidth/aspectRatio를 못 써 크기를 명시한다
                    contentScale = ContentScale.FillBounds,
                    filterQuality = FilterQuality.High,
                    modifier = Modifier.size(w, h).clickable { onExpand() },
                )
            }
            // v1.6.29: 우하단 ZoomOutMap 아이콘 삭제(사용자 요청 "터치하면 커지는데?").
            // 힌트일 뿐이었고 가로 스크롤 위치에 따라 표 셀("야간 8:00")을 덮었다(남은 이슈 1번).
            // 이미지 아무 데나 탭하면 전체화면이 열리는 동작은 그대로다.
        }
    }
}
