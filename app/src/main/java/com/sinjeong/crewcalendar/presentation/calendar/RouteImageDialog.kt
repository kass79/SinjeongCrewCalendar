package com.sinjeong.crewcalendar.presentation.calendar

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
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
 * 세로 화면 + 가로형 이미지면 비트맵을 90도 돌려 표의 긴 변을 화면 긴 변에 맞춘다.
 * 행로표가 본선 ~2.13:1 / 지선 ~1.41:1 가로형이라 세로 화면에선 폭맞춤만으로는
 * 표가 납작하게 눌린다(접힘 기준 본선 약 2배 커짐). 펼침은 화면이 가로로 넓어 조건에서 빠진다.
 */
@Composable
fun RouteImageDialog(asset: String, title: String, onDismiss: () -> Unit) {
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
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val portrait = maxHeight > maxWidth
                // 원본(src)은 remember(asset)가 계속 붙들고 있으므로 recycle 하지 않는다.
                // 접힘↔펼침 전환 때 회전본만 다시 만들면 된다.
                val bitmap = remember(src, portrait) {
                    if (src != null && portrait && src.width > src.height) {
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
                Text(
                    title,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.align(Alignment.TopStart).padding(start = 20.dp, top = 18.dp),
                )
                IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                    Icon(Icons.Default.Close, "닫기")
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
 */
@Composable
fun RouteImageInline(asset: String, bleed: Dp = 0.dp, zoom: Float = 1f, onExpand: () -> Unit) {
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
            val h = w * bitmap.height / bitmap.width
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
            Icon(
                Icons.Default.ZoomOutMap, "크게 보기",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.38f), CircleShape)
                    .padding(4.dp)
                    .size(16.dp),
            )
        }
    }
}
