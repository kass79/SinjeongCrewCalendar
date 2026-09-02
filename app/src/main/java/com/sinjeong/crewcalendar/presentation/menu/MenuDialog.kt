package com.sinjeong.crewcalendar.presentation.menu

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.sinjeong.crewcalendar.R
import com.sinjeong.crewcalendar.domain.model.Meal
import com.sinjeong.crewcalendar.domain.model.MenuIcon
import com.sinjeong.crewcalendar.domain.model.WeeklyMenu
import com.sinjeong.crewcalendar.domain.model.mainDish
import com.sinjeong.crewcalendar.domain.model.menuIcon
import com.sinjeong.crewcalendar.domain.model.nextMealAt
import com.sinjeong.crewcalendar.domain.model.weekStartOf
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 주간식단표 — **하루씩 보기** (v1.6.82에서 주간 포스터를 갈아엎었다).
 *
 * 사용자 원문: *"주간 전체 표(지금 포스터)는 빼고 새롭게… 다음끼니 자동강조는 괜찮을듯"*.
 *
 * ## 왜 표를 버렸나
 *
 * v1.6.80~81의 포스터는 **21칸을 한 화면에 우겨 넣는** 물건이었다(빈티지/파스텔 2종 + 핀치 확대).
 * 21칸이 다 보이지만 정작 "지금 뭐 먹지"에 답하려면 눈이 오늘 행을 찾아 훑어야 했다.
 * 이 화면은 **하루 3칸**만 보여 주고, 지금 시각 기준 **다음 끼니**를 펼쳐 둔다.
 * 나머지 요일은 좌우로 넘긴다(스와이프 + 요일 칩).
 *
 * ## 남은 것 / 사라진 것
 *
 * 공유(`cache/share` + FileProvider · `menu_{날짜}.png`)와 관리자 업로드 진입, "이번 주 없음"
 * 안내는 그대로다. 포스터 2종·설정의 **스타일 선택은 없앴다**(`MenuStyle`·`menu_style`).
 * 저장돼 있던 설정값은 그냥 안 읽는다 — 지우려고 마이그레이션 코드를 붙일 값어치가 없다.
 *
 * ## 아이콘 — Lucide **SVG만 벡터 드로어블로** 가져왔다(라이브러리 없음)
 *
 * `com.composables:icons-lucide-android` 는 아이콘 1,666개가 통째로 dex에 실리고(R8 꺼짐)
 * compose-foundation 1.9를 요구해 BOM을 강제로 끌어올린다. 필요한 20개만 손수 넣으면 +30KB다.
 * 변환은 `strokeColor=white` 고정 + [Icon] 의 `tint` 로 색을 입힌다.
 */
@Composable
fun MenuDialog(
    /** 주 시작일(월) → 21칸. 이번 주·다음 주만 들어온다 */
    weeks: Map<LocalDate, WeeklyMenu>,
    thisWeek: LocalDate,
    isAdmin: Boolean,
    onUpload: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    // v1.6.77 이 밝힌 것과 같은 이유로 인셋을 Dialog **밖에서** 읽는다 — 안쪽에선 0으로 보인다.
    val bars = WindowInsets.systemBars.asPaddingValues()
    val overshoot = bars.calculateTopPadding() + bars.calculateBottomPadding()

    val next = remember { nextMealAt(LocalDateTime.now()) }
    val today = remember { LocalDate.now() }

    /**
     * 넘겨 볼 수 있는 날들. 이번 주 7일 + (다음 주 표가 **이미 올라와 있으면**) 7일.
     * 없는 주까지 넘기게 두면 빈 화면으로 떨어져 "고장난 것"처럼 보인다.
     */
    val days = remember(weeks, thisWeek) {
        listOf(thisWeek, thisWeek.plusWeeks(1))
            .filter { weeks[it]?.isBlank == false }
            .flatMap { w -> (0L..6L).map { w.plusDays(it) } }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().padding(bottom = overshoot)) {
                if (days.isEmpty()) {
                    // 이번 주 표가 없음 — 헤더도 단순하게 두고 안내만 채운다
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, top = 14.dp, end = 6.dp)) {
                        Text("식단표", fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "닫기") }
                    }
                    MenuEmptyNotice(isAdmin, onUpload, Modifier.fillMaxSize())
                    return@Column
                }

                // 다음 끼니가 있는 날에서 시작한다. 그 날이 아직 안 올라온 주면(일요일 밤 + 다음 주
                // 표 없음) 가진 마지막 날로 붙인다.
                val start = days.indexOfFirst { it >= next.date }.let { if (it >= 0) it else days.lastIndex }
                val pager = rememberPagerState(initialPage = start) { days.size }
                val shownDate by remember { derivedStateOf { days[pager.currentPage] } }
                val layer = rememberGraphicsLayer()

                // ── 머리글 ──────────────────────────────────────────
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, top = 14.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "식단표", fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                    )
                    IconButton(onClick = {
                        scope.launch {
                            runCatching {
                                shareMenu(ctx, layer.toImageBitmap().asAndroidBitmap(), shownDate)
                            }
                        }
                    }) { LucideIcon(R.drawable.ic_lucide_share_2, "식단표 공유") }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "닫기") }
                }

                // ── 요일 칩 7개 + 좌우 이동 ─────────────────────────
                // 칩은 **지금 보고 있는 날이 속한 주**의 월~일이다. 주 경계를 넘어가면 칩도 같이
                // 다음 주로 넘어간다(주가 둘일 때만 넘어갈 수 있다).
                val base = days.indexOf(weekStartOf(shownDate))
                Row(
                    Modifier
                        // 카드 기둥(520dp) + 좌우 단추 폭만큼으로 묶고 가운데 둔다 — 폴드 펼침(700dp)에서
                        // 칩만 화면 끝까지 벌어지면 아래 카드와 줄이 안 맞는다.
                        .widthIn(max = 616.dp).fillMaxWidth()
                        .align(Alignment.CenterHorizontally)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                        enabled = pager.currentPage > 0,
                    ) { LucideIcon(R.drawable.ic_lucide_chevron_left, "앞 날", 22.dp) }
                    // 칩은 **직접 그린다**. material3 `FilterChip`은 라벨 한 글자에도 좌우 16dp를
                    // 물고 있어 411dp(폴드 접힘)에서 일곱 개가 안 들어간다 — `일`이 화면 밖으로
                    // 밀려 나갔다(실측). 일곱이 남은 폭을 **균등하게** 나누므로 360dp에서도,
                    // 글자배율 1.5에서도 일곱이 항상 다 보인다(가로 스크롤이 필요 없다).
                    Row(
                        Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        WeeklyMenu.DAY_LABELS.forEachIndexed { i, label ->
                            val page = base + i
                            val on = page == pager.currentPage
                            Box(
                                Modifier
                                    .weight(1f).height(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (on) MaterialTheme.colorScheme.secondaryContainer
                                        else Color.Transparent
                                    )
                                    .then(
                                        if (on) Modifier
                                        else Modifier.border(
                                            1.dp, MaterialTheme.colorScheme.outlineVariant,
                                            RoundedCornerShape(10.dp),
                                        )
                                    )
                                    .clickable { scope.launch { pager.animateScrollToPage(page) } },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (days[page] == today) FontWeight.ExtraBold
                                    else FontWeight.Normal,
                                    color = if (on) MaterialTheme.colorScheme.onSecondaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                        enabled = pager.currentPage < days.lastIndex,
                    ) { LucideIcon(R.drawable.ic_lucide_chevron_right, "다음 날", 22.dp) }
                }

                // ── 하루 3끼니 ──────────────────────────────────────
                HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().weight(1f)) { page ->
                    val date = days[page]
                    val menu = weeks[weekStartOf(date)]
                    val dayIdx = date.dayOfWeek.value - 1
                    val hero = if (date == next.date) next.meal else Meal.LUNCH
                    val isCurrent = page == pager.currentPage

                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Column(
                            Modifier
                                .widthIn(max = 520.dp)   // 폴드 펼침(700dp)에서 카드가 늘어지지 않게
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                                // 공유 이미지는 **지금 보고 있는 쪽**만 뜬다. 이웃 페이지도 같이
                                // 기록하면 마지막에 그려진 옆날이 나간다.
                                .drawWithContent {
                                    if (isCurrent) {
                                        layer.record { this@drawWithContent.drawContent() }
                                        drawLayer(layer)
                                    } else drawContent()
                                },
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            // 날짜는 머리글이 아니라 **이 페이지의 내용**이다 — 스와이프하면 같이 바뀜고,
                            // 공유 이미지에도 들어간다(날짜 없는 식단 사진은 받는 사람이 언제 것인지 모른다).
                            Text(
                                "구내식당 ${dayTitle(date, today)}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                            )
                            Meal.entries.forEach { meal ->
                                MealCard(
                                    meal = meal,
                                    items = menu?.items(dayIdx, meal).orEmpty(),
                                    isNext = date == next.date && meal == next.meal,
                                    initiallyOpen = meal == hero,
                                    key = date,
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
            }
        }
    }
}

/** `9/2 (수) · 오늘` — 지금 보고 있는 날이 언제인지 한 줄로. */
private fun dayTitle(date: LocalDate, today: LocalDate): String {
    val label = WeeklyMenu.DAY_LABELS[date.dayOfWeek.value - 1]
    val extra = when (date) {
        today -> " · 오늘"
        today.plusDays(1) -> " · 내일"
        else -> if (weekStartOf(date) > weekStartOf(today)) " · 다음 주" else ""
    }
    return "${date.monthValue}/${date.dayOfMonth} ($label)$extra"
}

/**
 * 끼니 한 칸.
 *
 * 펼치면 **메인 요리**(국 다음 첫 항목)를 큰 아이콘 + 큰 글씨로 세우고 나머지를 작게 줄 세운다.
 * 접히면 머리글과 메인 요리 한 줄만 남는다. 처음 펼쳐져 있는 것은 **다음 끼니** 하나뿐이다.
 *
 * @param key 날짜 — 페이저가 페이지를 재활용해도 그 날의 초기 상태로 돌아오게 하는 열쇠다.
 */
@Composable
private fun MealCard(
    meal: Meal,
    items: List<String>,
    isNext: Boolean,
    initiallyOpen: Boolean,
    key: LocalDate,
) {
    var open by remember(key, initiallyOpen) { mutableStateOf(initiallyOpen) }
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val (bg, fg) = mealColors(meal, dark)
    val main = mainDish(items)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .then(if (isNext) Modifier.border(2.dp, fg, RoundedCornerShape(16.dp)) else Modifier)
            .clickable { open = !open }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LucideIcon(mealIconRes(meal), null, 20.dp, fg)
            Spacer(Modifier.width(8.dp))
            Text(meal.label, fontWeight = FontWeight.ExtraBold, color = fg)
            Spacer(Modifier.width(8.dp))
            Text(
                meal.time,
                style = MaterialTheme.typography.labelSmall,
                color = fg.copy(alpha = 0.85f),
                modifier = Modifier.weight(1f),
            )
            if (isNext) Text(
                "다음 끼니",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = bg,
                modifier = Modifier
                    .clip(CircleShape).background(fg).padding(horizontal = 8.dp, vertical = 3.dp),
            )
            LucideIcon(
                if (open) R.drawable.ic_lucide_chevron_up else R.drawable.ic_lucide_chevron_down,
                if (open) "접기" else "펼치기", 18.dp, fg,
            )
        }

        val text = MaterialTheme.colorScheme.onSurface
        when {
            main == null -> Text(
                "메뉴가 없어요",
                style = MaterialTheme.typography.bodySmall,
                color = text.copy(alpha = 0.6f),
            )

            !open -> Text(main, color = text, fontWeight = FontWeight.Medium)

            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LucideIcon(iconRes(menuIcon(main)), null, 44.dp, fg)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        main, fontSize = 21.sp, lineHeight = 27.sp,
                        fontWeight = FontWeight.ExtraBold, color = text,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items.filter { it != main }.forEach { item ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LucideIcon(iconRes(menuIcon(item)), null, 16.dp, fg)
                            Spacer(Modifier.width(8.dp))
                            Text(item, style = MaterialTheme.typography.bodyMedium, color = text)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Lucide 벡터 하나. [res] 가 null 이면 **작은 점** — 매칭 안 된 메뉴 자리를 비워 두면 글자가
 * 들쭉날쭉해진다(사용자 확정: 틀린 그림보다 없는 게 낫다).
 */
@Composable
private fun LucideIcon(
    res: Int?,
    desc: String?,
    size: androidx.compose.ui.unit.Dp = 24.dp,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    if (res == null) Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        // 점은 아무리 큰 자리라도 8dp를 안 넘긴다 — 메인 요리 자리(44dp)에서 점이 같이 커지면
        // 아이콘이 아니라 커다란 불릿으로 보인다(실측).
        Box(Modifier.size(minOf(size / 4, 8.dp)).clip(CircleShape).background(tint.copy(alpha = 0.45f)))
    } else Icon(painterResource(res), desc, Modifier.size(size), tint)
}

private fun mealIconRes(meal: Meal) = when (meal) {
    Meal.BREAKFAST -> R.drawable.ic_lucide_sunrise
    Meal.LUNCH -> R.drawable.ic_lucide_sun
    Meal.DINNER -> R.drawable.ic_lucide_moon
}

private fun iconRes(icon: MenuIcon?) = when (icon) {
    MenuIcon.SOUP -> R.drawable.ic_lucide_soup
    MenuIcon.FISH -> R.drawable.ic_lucide_fish
    MenuIcon.BEEF -> R.drawable.ic_lucide_beef
    MenuIcon.DRUMSTICK -> R.drawable.ic_lucide_drumstick
    MenuIcon.EGG -> R.drawable.ic_lucide_egg
    MenuIcon.SALAD -> R.drawable.ic_lucide_salad
    MenuIcon.SANDWICH -> R.drawable.ic_lucide_sandwich
    MenuIcon.MILK -> R.drawable.ic_lucide_milk
    MenuIcon.APPLE -> R.drawable.ic_lucide_apple
    MenuIcon.WHEAT -> R.drawable.ic_lucide_wheat
    MenuIcon.UTENSILS -> R.drawable.ic_lucide_utensils
    null -> null
}

/**
 * 끼니별 (옅은 배경, 진한 아이콘·글씨). 라이트/다크를 **짝으로** 정한다 — 배경만 바꾸고 글자를
 * 안 따라가면 다크에서 글씨가 사라진다(v1.6.40 사고).
 */
private fun mealColors(meal: Meal, dark: Boolean): Pair<Color, Color> = when {
    meal == Meal.BREAKFAST && !dark -> Color(0xFFFFF3D4) to Color(0xFF8A6000)
    meal == Meal.BREAKFAST -> Color(0xFF352C0E) to Color(0xFFF3C64A)
    meal == Meal.LUNCH && !dark -> Color(0xFFFFE7D2) to Color(0xFFA34C0E)
    meal == Meal.LUNCH -> Color(0xFF37230E) to Color(0xFFFFA463)
    !dark -> Color(0xFFE4E7FA) to Color(0xFF3A4696)
    else -> Color(0xFF1E2240) to Color(0xFF9FABF8)
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
            Icon(
                painterResource(R.drawable.ic_lucide_calendar_off), null,
                Modifier.size(40.dp), MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "이번 주 식단표가 아직 없어요",
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "구내식당 표가 올라오면 여기에 바로 보입니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (isAdmin) FilledTonalButton(onClick = onUpload) {
                Text("식단표 올리기", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** 근무표 공유(`cache/share/duty_*.png`)와 **같은 폴더·같은 FileProvider**를 쓴다. */
private fun shareMenu(ctx: Context, bmp: Bitmap, date: LocalDate) {
    val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
    val f = File(dir, "menu_$date.png")
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
