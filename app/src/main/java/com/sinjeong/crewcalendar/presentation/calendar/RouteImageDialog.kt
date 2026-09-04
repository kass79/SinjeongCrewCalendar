package com.sinjeong.crewcalendar.presentation.calendar

import android.content.Context
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** 핀치 확대 상한. 목표 해상도 계산과 제스처 클램프가 **같은 값**을 써야 한다. */
private const val MAX_ZOOM = 6f

/**
 * 원본 폭이 [reqW] px 이상으로 남는 **가장 큰 2의 거듭제곱** 축소 배율(1·2·4…).
 *
 * [BitmapFactory.Options.inSampleSize]는 2의 거듭제곱만 의미가 있고, "요청 크기보다 작아지지 않는
 * 선까지만 줄인다"가 안드로이드 공식 권장 계산이다. 여기서는 **폭 하나로만** 판정한다 —
 * 표시할 때 원본 비율을 지키므로(`Fit` / 비율대로 계산한 높이) 세로 요구는 가로에 비례하고,
 * 세로만 늘리는 [RouteImageInline]의 `vStretch`는 호출부가 [reqW]에 곱해 넘긴다.
 */
fun routeSampleSize(srcW: Int, reqW: Int): Int {
    if (srcW <= 0 || reqW <= 0) return 1
    var s = 1
    while (srcW / (s * 2) >= reqW) s *= 2
    return s
}

/**
 * `assets/routes/{asset}.webp` 를 **필요한 만큼만** 디코딩한다 (플레이 콘솔 메모리 권장 조치).
 *
 * 종전엔 두 자리(전체화면 뷰어·상세시트 인라인)가 각자 `BitmapFactory.decodeStream(it)`으로
 * **원본 크기 ARGB_8888**을 통째로 올렸다. 실측 최악값: `tt_work`(2258x2928) **25.2MB**,
 * 본선 행로표(2000x1421) **10.8MB**. 인라인과 전체화면이 동시에 살아 있고 90도 회전 사본까지
 * 만들면 한 표에 30MB가 넘는다.
 *
 * 두 가지를 한다 — **해상도는 한 픽셀도 안 버린다**:
 *  1. `inJustDecodeBounds`로 원본 크기를 먼저 재고 [routeSampleSize]로 축소 배율을 고른다.
 *     목표 폭은 호출부가 **표시 폭 × 확대 상한([MAX_ZOOM])**으로 준다. 지금 자산은 화면 폭의
 *     2.15배(`tt_work` 접힘)가 최대라 **전부 `inSampleSize = 1`** — 즉 확대 선명도는 종전과
 *     완전히 같다. 앞으로 화면의 12배가 넘는 자산이 들어오면 그때 자동으로 절반이 된다.
 *  2. `inPreferredConfig = RGB_565` — **메모리가 정확히 절반**(픽셀당 4 → 2바이트)이 된다.
 *     자산 162장 전부 알파 없는 VP8(파일 헤더 전수 확인: `VP8X`/알파 플래그 0건)이라
 *     투명도를 잃을 것이 없고, 표 이미지는 평면 색·검은 글자뿐이라 밴딩이 생길 그라디언트가 없다.
 *     ⚠ 사진처럼 그라디언트가 있는 자산을 넣게 되면 이 줄을 다시 봐야 한다.
 */
/**
 * 행로표 자산이 **실제로 들어 있나** (v1.6.92 ⑧).
 *
 * [RouteTable.assetFor]는 이름을 **계산**할 뿐 파일이 있는지 보지 않는다 —
 * 야간(`pp_$n`)·본선 평일(`wd_$n`)은 번호 범위를 안 재서, 근무변경 직접입력으로 범위 밖 숫자를
 * 넣으면 있지도 않은 이름이 나온다. 화면이 **이름만 보고** 갈라지면 그 순간 디코딩이 null이라
 * 그림도 없고, 사업시각·열번·운휴 안내가 있는 else 가지도 건너뛴 채 **아무것도 안 남았다.**
 * 이름이 아니라 파일 유무로 갈라야 시각 정보가 늘 남는다.
 */
fun routeAssetExists(context: Context, asset: String?): Boolean =
    asset != null && runCatching { context.assets.open("routes/$asset.webp").close() }.isSuccess

private fun decodeRoute(context: Context, asset: String, reqW: Int): Bitmap? = runCatching {
    val path = "routes/$asset.webp"
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
    val opts = BitmapFactory.Options().apply {
        inSampleSize = routeSampleSize(bounds.outWidth, reqW)
        inPreferredConfig = Bitmap.Config.RGB_565
    }
    context.assets.open(path).use { BitmapFactory.decodeStream(it, null, opts) }
}.getOrNull()

/**
 * 행로표 원본 뷰어 — assets/routes/{asset}.webp, 핀치 확대(1~6배)·드래그 이동.
 *
 * v1.6.9에서 넣은 세로화면 자동 90도 회전은 **v1.6.16에서 기본 꺼짐**으로 바꿨다
 * (사용자: "세로로 하면 보기 불편해"). 기본은 원본 방향 + 화면 폭 맞춤이고,
 * 제목줄 오른쪽 회전 버튼으로 필요할 때만 돌린다. 가로형 이미지일 때만 버튼이 뜬다.
 *
 * ## ⚠ 제목줄과 그림은 **겹치지 않는다** (v1.6.77 — 폴드 펼침에서 드러난 버그)
 *
 * v1.6.76까지는 제목·칩·닫기를 그림 **위에 겹쳐** 그렸다. 접힘(1080x2400, 화면비 0.45)에서는
 * `ContentScale.Fit`이 **폭에 맞춰** 그림을 줄이므로 위아래가 남고 그 빈 띠에 제목이 앉아
 * 아무 문제가 없었다. 그런데 폴드7 펼침(1968x2184, 화면비 **0.90**)은 세로형 표
 * (침실배정표 0.70 · 근무시각표 0.77 · 편승시각표 0.69)보다 화면이 넓어 `Fit`이 **높이에 맞추고**,
 * 그러면 그림이 화면 높이를 통째로 먹어 두 가지가 한꺼번에 깨졌다:
 *
 *  1. 제목·조합 칩이 **표의 머리글 위에 겹쳐** 둘 다 못 읽는다.
 *  2. 다이얼로그 창은 상태바만큼 아래로 밀린 채 높이는 화면 전체라, 표 **아랫부분(마지막 두 줄)이
 *     화면 밖으로 잘린다.** 1배에서 아예 못 보는 칸이 생긴다 — 실측 재현: 표 아래 괘선 소실.
 *
 * 그래서 겹침을 걷어내고 [Column]으로 **제목줄 → 남는 높이 전부가 그림**이 되게 했다.
 * 창이 화면보다 길게 재지는 문제는 아래 `overshoot` 주석 참고.
 * 가로형(행로표 1.41~2.13)은 두 화면 모두 폭에 맞춰 줄어드니 종전과 **그림 크기가 같다**.
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
    // 목표 폭 = **화면 폭 × 확대 상한**. `Fit`이 그리는 폭은 화면 폭을 넘지 않으므로 이 값은
    // 언제나 넉넉한 쪽(= 덜 줄이는 쪽)이다. 회전해도 원본 픽셀 수는 그대로라 값이 같다.
    val cfg = LocalConfiguration.current
    val density = LocalDensity.current
    val reqW = remember(cfg.screenWidthDp, density.density) {
        (cfg.screenWidthDp * density.density * MAX_ZOOM).toInt()
    }
    val src = remember(asset, reqW) { decodeRoute(context, asset, reqW) }
    // ⚠ **`overshoot`가 없으면 세로형 표의 아랫부분이 화면 밖으로 잘린다**(v1.6.77 실측).
    //
    // 컴포즈 `Dialog`는 `usePlatformDefaultWidth = false`로도 **폭**만 화면에 맞춘다. 창 높이는
    // 내용을 따라가는데, 그 내용(`fillMaxSize`)은 **화면 전체 높이**로 재지고 창 자체는
    // 상태바 아래에 놓인다 — `dumpsys window`가 그대로 보여 준다:
    //   `Requested w=1969 h=2185` 인데 `frame=[0,124][1968,2094]`.
    // 결국 내용이 보이는 창보다 **상태바+제스처바(124+90px)만큼 길어** 밑동이 화면 밖으로 나간다.
    // 접힘에선 그림이 폭에 맞춰 줄어 위아래가 남으니 그 초과분이 빈 띠에 먹혀 안 보였고,
    // 펼침(화면비 0.90 > 표 0.70)에선 그림이 높이를 꽉 채워 **표 마지막 줄이 잘렸다.**
    //
    // 그래서 그만큼을 아래에서 덜어낸다. 인셋은 **`Dialog` 밖(= 이 자리)에서 읽어야** 실제 값이
    // 나온다 — 다이얼로그 창은 데코가 인셋을 이미 먹어서 안쪽에선 0으로 보인다
    // (`safeDrawingPadding`·`DialogProperties.decorFitsSystemWindows` 둘 다 안 통했다. 실측).
    val bars = WindowInsets.systemBars.asPaddingValues()
    val overshoot = bars.calculateTopPadding() + bars.calculateBottomPadding()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            var scale by remember { mutableFloatStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }
            var rotated by remember(asset) { mutableStateOf(false) }
            val landscapeImage = src != null && src.width > src.height
            Column(Modifier.fillMaxSize().padding(bottom = overshoot)) {
                // 제목줄 — 종전의 겹침 배치를 그대로 옮겨 왔다(제목은 버튼 자리를 비켜주고,
                // header 칩 줄은 버튼보다 낮아 폭을 다 쓴다). 달라진 건 이 줄이 **그림 위가 아니라
                // 그림 위쪽에** 선다는 것뿐이라 360dp·배율 1.5의 칩 넉 장 계산은 그대로 유효하다.
                Box(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(start = 20.dp, top = 18.dp, end = 12.dp, bottom = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
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
                // 남는 높이 **전부**가 그림 몫이다 — 제목줄이 먹은 만큼만 줄고, 어느 화면비에서도
                // `Fit`이 잘리지 않는다. 핀치(1~6배)·드래그는 이 상자 안에서 종전 그대로.
                Box(Modifier.fillMaxWidth().weight(1f)) {
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
                                        scale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
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
    // ⚠ 디코딩이 [BoxWithConstraints] **안으로** 들어왔다 — 여기서만 실제 표시 폭을 알 수 있다.
    // 인라인에는 핀치가 없다(탭하면 전체화면이 열린다). 그래서 목표 폭은 "그려질 폭" 그대로면
    // 충분하고, 세로만 늘리는 `vStretch`가 요구하는 세로 해상도는 같은 비율로 폭에 곱해 넘긴다.
    // 자산이 없으면 [decodeRoute]가 null → 아무것도 안 그린다(자리도 안 먹는다. 종전과 같다).
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
        val reqW = with(LocalDensity.current) { (w * vStretch).roundToPx() }
        val bitmap = remember(asset, reqW) { decodeRoute(context, asset, reqW) }
        if (bitmap != null) {
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
        } else {
            // 파일은 있는데 디코딩이 실패한 경우(손상·메모리). 자리를 비워 두면 사용자는
            // "행로표가 없는 근무"인지 "앱이 못 그린 것"인지 알 길이 없다(v1.6.92 ⑧).
            Text(
                "행로표 이미지를 불러오지 못했습니다",
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
