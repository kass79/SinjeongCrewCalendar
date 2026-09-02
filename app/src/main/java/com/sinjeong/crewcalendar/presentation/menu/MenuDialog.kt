package com.sinjeong.crewcalendar.presentation.menu

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import com.sinjeong.crewcalendar.domain.model.soupDish
import com.sinjeong.crewcalendar.domain.model.weekStartOf
import com.sinjeong.crewcalendar.presentation.theme.LocalDutyColors
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 주간식단표 — **하루씩 보기**.
 *
 * ## v1.6.83 — "그냥 약간 형광펜을 칠했으면 해"
 *
 * 사용자 원문: *"주간식단표 디자인 구려.. 텍스트도 글씨체도 별로고... 메인 텍스트 2개가 너무 커..
 * 그냥 약간 형광펜을 칠했으면 해... 예를 들어 조식을 세로로 6개 나열하는데 루시드 아이콘을 적절히
 * 넣어주면 되지.."*
 *
 * 그래서 **강조 장치를 전부 걷어내고 형광펜 하나만 남겼다.**
 *
 * | | v1.6.82 | v1.6.83 중간안(폐기) | **v1.6.83 확정** |
 * |---|---|---|---|
 * | 목록 | 메인 1줄만 크게 + 나머지 작게 | 국·메인 칩 2줄 + 반찬 회색 한 줄 묶음 | **전 항목 같은 크기 세로 나열** |
 * | 강조 | 21sp ExtraBold | 22sp Bold + 옅은 칩 | **글자 뒤 형광펜 띠뿐** (크기·굵기 그대로) |
 * | 접기 | 다음 끼니만 펼침 | 200ms 펼침 | **없음** — 셋 다 펼쳐 두고 세로 스크롤 |
 * | 아이콘 | 줄마다 | 강조 2줄만 | **줄마다 20dp**, 매칭 실패는 `dot` |
 *
 * 크기 차등·볼드·칩·테두리·그림자·애니메이션을 **전부 뺐다**. 남은 강조가 형광펜 하나뿐이라
 * 화면이 "목록"으로 읽힌다. 다음 끼니 섹션만 맨 위로 올리고 그 **헤더만** 끼니 색으로 옅게 칠한다.
 *
 * ## 형광펜은 글자 뒤에만 ([HighlighterText])
 *
 * 줄 전체를 칠하면 그건 형광펜이 아니라 색 블록이다. `TextLayoutResult` 로 **줄마다 글자가 실제로
 * 놓인 폭**(`getLineLeft`~`getLineRight`)을 재서 그만큼만 칠한다. 그래서 이름이 2줄로 접히면
 * 띠도 2줄이 되고, 짧은 이름은 짧게 칠해진다. 띠는 글자 높이의 70%이고 살짝 아래로 치우쳐 있다
 * (진짜 형광펜이 글자 아랫부분을 지나가는 모양). `sp` 로 재므로 배율 1.5에서도 같이 커진다.
 *
 * ## 왜 표를 버렸나 (v1.6.82)
 *
 * v1.6.80~81의 포스터는 **21칸을 한 화면에 우겨 넣는** 물건이었다(빈티지/파스텔 2종 + 핀치 확대).
 * 21칸이 다 보이지만 정작 "지금 뭐 먹지"에 답하려면 눈이 오늘 행을 찾아 훑어야 했다.
 * 이 화면은 **하루 3칸**만 보여 주고 다음 끼니를 맨 위에 둔다. 나머지 요일은 좌우로 넘긴다.
 *
 * ## 아이콘 — Lucide **SVG만 벡터 드로어블로** 가져왔다(라이브러리 없음)
 *
 * `com.composables:icons-lucide-android` 는 아이콘 1,666개가 통째로 dex에 실리고(R8 꺼짐)
 * compose-foundation 1.9를 요구해 BOM을 강제로 끌어올린다. 필요한 21개만 손수 넣으면 +32KB다.
 */
@OptIn(ExperimentalLayoutApi::class)
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
                        Text(
                            "식단표", fontFamily = MenuFont, fontSize = 20.sp, lineHeight = 30.sp,
                            fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f),
                        )
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

                // ── 머리글: 큰 날짜 + (다음 주가 있을 때만) 다음 주 ─────
                val nextWeekPage = days.indexOf(thisWeek.plusWeeks(1))
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, top = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // ⚠ `Row` 로 두면 배율 1.5 · 360dp 에서 `내일` 이 `내` 로 **잘린다**(실측) —
                    // 날짜가 먼저 폭을 다 먹고 뒤 글자에 30dp 남짓만 남는다. `FlowRow` 면 자리가
                    // 모자랄 때 아랫줄로 내려갈 뿐 잘리지 않는다(글자 수를 세는 상수가 필요 없다).
                    FlowRow(
                        Modifier.weight(1f).padding(end = 6.dp),
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Text(
                            dayLabel(shownDate),
                            fontFamily = MenuFont, fontSize = 20.sp, lineHeight = 30.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        dayRelative(shownDate, today)?.let {
                            Text(
                                " $it",
                                fontFamily = MenuFont, fontSize = 12.sp, lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier.align(Alignment.Bottom).padding(bottom = 4.dp),
                            )
                        }
                    }
                    // 주가 둘 있고 지금 이번 주를 보고 있을 때만. 되돌아오는 길은 요일 칩·‹ 가 맡는다.
                    if (nextWeekPage > 0 && pager.currentPage < nextWeekPage) Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { scope.launch { pager.animateScrollToPage(nextWeekPage) } }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "다음 주",
                            fontFamily = MenuFont, fontSize = 14.sp, lineHeight = 21.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary, maxLines = 1,
                        )
                        LucideIcon(
                            R.drawable.ic_lucide_chevron_right, null, 16.dp,
                            MaterialTheme.colorScheme.primary,
                        )
                    }
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
                // 칩은 **지금 보고 있는 날이 속한 주**의 월~일이다.
                val base = days.indexOf(weekStartOf(shownDate))
                val duty = LocalDutyColors.current
                val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                Row(
                    Modifier
                        // 카드 기둥(520dp) + 좌우 단추 폭만큼으로 묶고 가운데 둔다 — 폴드 펼침(700dp)에서
                        // 칩만 화면 끝까지 벌어지면 아래 카드와 줄이 안 맞는다.
                        .widthIn(max = 616.dp).fillMaxWidth()
                        .align(Alignment.CenterHorizontally)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
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
                            val isToday = days.getOrNull(page) == today
                            Column(
                                Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth().height(32.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        // **오늘**은 끼니 색이 아니라 앱 강조색으로 채운다.
                                        .background(if (isToday) todayChipColor(dark) else Color.Transparent)
                                        .clickable { scope.launch { pager.animateScrollToPage(page) } },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        label,
                                        fontFamily = MenuFont, fontSize = 14.sp,
                                        fontWeight = if (on || isToday) FontWeight.Medium
                                        else FontWeight.Normal,
                                        // 달력과 **같은 규칙**: 토=파랑 · 일=빨강
                                        color = when {
                                            i == 6 -> duty.sunday
                                            i == 5 -> duty.saturday
                                            on || isToday -> MaterialTheme.colorScheme.onSurface
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                }
                                Spacer(Modifier.height(3.dp))
                                // 고른 칩 아래 얇은 막대. "오늘"(칩 채움)과 "고른 날"(막대)이
                                // 서로 다른 표시라 둘이 겹쳐도 무엇이 무엇인지 읽힌다.
                                Box(
                                    Modifier
                                        .width(if (on) 18.dp else 0.dp).height(2.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
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
                    val isCurrent = page == pager.currentPage
                    // **다음 끼니 섹션이 맨 위**. 그 날이 아닌 페이지는 조식·중식·석식 순서 그대로
                    // 두고 헤더도 안 칠한다 — 날마다 순서가 바뀌면 넘길 때 눈이 헤맨다.
                    val hero = if (date == next.date) next.meal else null
                    val order = if (hero == null) Meal.entries
                    else listOf(hero) + Meal.entries.filter { it != hero }

                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        // 폴드 펼침(≈700dp)에서는 **3끼를 나란히**. 목록 형식·형광펜 규칙은 동일.
                        val wide = maxWidth >= 640.dp
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Column(
                                Modifier
                                    .widthIn(max = if (wide) 1000.dp else 520.dp)
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    // 공유 이미지는 **지금 보고 있는 쪽**만 뜬다. 이웃 페이지도 같이
                                    // 기록하면 마지막에 그려진 옆날이 나간다.
                                    .drawWithContent {
                                        if (isCurrent) {
                                            layer.record { this@drawWithContent.drawContent() }
                                            drawLayer(layer)
                                        } else drawContent()
                                    },
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                // ⚠ 이 줄은 **공유 이미지의 제목**이다. 머리글의 큰 날짜는 기록되는
                                // 층 밖이라 그림에 안 들어간다 — 지우면 카톡으로 받은 사람이 언제
                                // 것인지 모른다(v1.6.82가 같은 이유로 넣어 둔 줄).
                                Text(
                                    "구내식당 · ${dayLabel(date)}",
                                    fontFamily = MenuFont, fontSize = 12.sp, lineHeight = 18.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 2.dp),
                                )
                                if (wide) Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    order.forEach { meal ->
                                        MealSection(
                                            meal = meal,
                                            items = menu?.items(dayIdx, meal).orEmpty(),
                                            isNext = meal == hero,
                                            narrow = true,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                } else order.forEach { meal ->
                                    MealSection(
                                        meal = meal,
                                        items = menu?.items(dayIdx, meal).orEmpty(),
                                        isNext = meal == hero,
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
}

/**
 * 이 화면의 글꼴.
 *
 * ⚠ **Pretendard 로 바꿀 자리** — 사용자가 요청한 글꼴이다. `Pretendard-Regular.ttf` ·
 * `Pretendard-Medium.ttf` 를 `res/font/` 에 넣고 이 한 줄만 갈아 끼우면 이 화면 전체가 따라온다:
 * `FontFamily(Font(R.font.pretendard_regular, FontWeight.Normal),`
 * ` Font(R.font.pretendard_medium, FontWeight.Medium))`.
 * 다른 화면은 안 건드린다 — 전면 적용은 사용자가 보고 결정한다.
 *
 * 이번 세션에는 **못 넣었다**: 폰트 파일을 받아 오는 것이 안전 정책상 **사용자 허가가 필요한
 * 내려받기**라 임의로 할 수 없었다. 크기·굵기·행간(아래 값들)은 지정대로 다 반영돼 있다.
 */
private val MenuFont = FontFamily.SansSerif

/** 항목 글자 크기·행간(1.5배). 형광펜 띠도 이 값으로 잰다 — 한 곳에서만 바꾼다. */
private val ITEM_SIZE = 16.sp
private val ITEM_LINE = 24.sp

/** `8/31 (월)` — 머리글의 큰 날짜이자 공유 이미지의 제목. */
private fun dayLabel(date: LocalDate): String =
    "${date.monthValue}/${date.dayOfMonth} (${WeeklyMenu.DAY_LABELS[date.dayOfWeek.value - 1]})"

/** 큰 날짜 옆에 붙는 한마디 — 없으면 null(그냥 날짜만 나온다). */
private fun dayRelative(date: LocalDate, today: LocalDate): String? = when (date) {
    today -> "오늘"
    today.plusDays(1) -> "내일"
    else -> if (weekStartOf(date) > weekStartOf(today)) "다음 주" else null
}

/**
 * 끼니 한 칸 — **항목 전부를 같은 크기로 세로 나열**한다. 접기도 크기 차등도 없다.
 *
 * 강조는 국·메인 두 줄의 **글자 뒤 형광펜**뿐이다([HighlighterText]). [isNext] 면 헤더만
 * 끼니 색으로 옅게 칠해 "지금 이거"임을 표시한다 — 목록 자체는 세 끼니가 완전히 같은 모양이다.
 */
@Composable
private fun MealSection(
    meal: Meal,
    items: List<String>,
    isNext: Boolean,
    /** 폴드 3열처럼 카드가 좁을 때 — 헤더에서 배식 시각을 접는다(아래 주석) */
    narrow: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val marker = markerColor(meal, dark)
    val main = mainDish(items)
    // 국이 메인과 같은 줄일 수 있다(`미역국` 한 줄뿐인 칸 — 메인 대체값이 첫 줄이라 겹친다).
    // 그러면 한 줄만 칠해진다. 같은 줄을 두 번 칠할 일은 없다.
    val soup = soupDish(items)?.takeIf { it != main }
    val shape = RoundedCornerShape(12.dp)

    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            // 카드 배경은 화면색보다 **아주 살짝** 다른 톤 — 명도차 1.09(라이트)·1.10(다크) 실측.
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (isNext) marker.copy(alpha = headerAlpha(dark)) else Color.Transparent)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LucideIcon(mealIconRes(meal), null, 20.dp, MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text(
                meal.label,
                fontFamily = MenuFont, fontSize = 14.sp, lineHeight = 21.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(8.dp))
            // 폴드 3열(카드 ≈211dp)에서는 **"다음 끼니"와 배식 시각이 같이는 안 들어간다** —
            // `07:30–09:…` 로 잘렸다(실측). 둘 중 시각을 접는다: 그 카드는 이미 헤더 색으로
            // "지금 이거"라고 말하고 있고, 배식 시각은 옆 두 카드에 그대로 있다.
            if (isNext && narrow) Spacer(Modifier.weight(1f)) else Text(
                meal.time.replace('~', '–'),
                fontFamily = MenuFont, fontSize = 12.sp, lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (isNext) Text(
                "다음 끼니",
                fontFamily = MenuFont, fontSize = 12.sp, lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.padding(start = 6.dp),
            )
        }

        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)) {
            if (items.isEmpty()) Text(
                "메뉴가 없어요",
                fontFamily = MenuFont, fontSize = ITEM_SIZE, lineHeight = ITEM_LINE,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ) else items.forEach { item ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    // 매칭 실패는 **빈칸이 아니라 `dot`** — 자리를 비우면 줄 정렬이 들쭉날쭉해진다.
                    LucideIcon(
                        iconRes(menuIcon(item)) ?: R.drawable.ic_lucide_dot, null, 20.dp,
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        // 이름이 2줄로 접혀도 아이콘은 **첫 줄**에 붙어 있어야 목록으로 읽힌다.
                        Modifier.padding(top = 2.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    HighlighterText(
                        item,
                        if (item == main || item == soup) marker.copy(alpha = markerAlpha(dark))
                        else Color.Transparent,
                    )
                }
            }
        }
    }
}

/**
 * 형광펜으로 그은 한 줄.
 *
 * 줄 전체가 아니라 **글자가 실제로 놓인 폭만** 칠한다 — `TextLayoutResult` 로 줄마다
 * `getLineLeft`~`getLineRight` 를 재기 때문에, 이름이 2줄로 접히면 띠도 2줄이 되고 짧은 이름은
 * 짧게 칠해진다(줄 전체를 칠하면 형광펜이 아니라 색 블록이다).
 *
 * 세로 위치는 **글자 높이 기준**으로 잡는다. 줄 상자(`lineHeight` = 글자의 1.5배)에서 위아래
 * 여백을 걷어 낸 글자 자리를 구하고, 그 아래쪽 70%에 걸치도록 놓되 밑선보다 조금 더 내린다 —
 * 진짜 형광펜이 글자 아랫부분을 지나가는 모양이다. 전부 `sp`·`dp` 로 재므로 배율 1.5에서도
 * 띠가 글자와 같이 커진다(어긋나지 않는다).
 */
@Composable
private fun HighlighterText(name: String, marker: Color) {
    var layout by remember(name) { mutableStateOf<TextLayoutResult?>(null) }
    val d = LocalDensity.current
    val fontPx = with(d) { ITEM_SIZE.toPx() }
    val padPx = with(d) { 4.dp.toPx() }
    val radiusPx = with(d) { 2.dp.toPx() }

    Text(
        name,
        fontFamily = MenuFont, fontSize = ITEM_SIZE, lineHeight = ITEM_LINE,
        color = MaterialTheme.colorScheme.onSurface,
        onTextLayout = { layout = it },
        modifier = Modifier.drawBehind {
            if (marker.alpha == 0f) return@drawBehind
            val l = layout ?: return@drawBehind
            for (i in 0 until l.lineCount) {
                val lineTop = l.getLineTop(i)
                val lineBottom = l.getLineBottom(i)
                // 줄 상자에서 위아래 여백을 걷어 낸 = 글자가 놓인 자리
                val slack = ((lineBottom - lineTop) - fontPx) / 2f
                val bottom = lineBottom - slack + fontPx * 0.10f    // 밑선보다 살짝 아래로
                val top = bottom - fontPx * 0.70f                   // 글자 높이의 70%
                val left = l.getLineLeft(i) - padPx
                val right = l.getLineRight(i) + padPx
                if (right <= left) continue
                drawRoundRect(
                    color = marker,
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top),
                    cornerRadius = CornerRadius(radiusPx, radiusPx),
                )
            }
        },
    )
}

/** Lucide 벡터 하나. */
@Composable
private fun LucideIcon(
    res: Int,
    desc: String?,
    size: Dp = 24.dp,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier: Modifier = Modifier,
) = Icon(painterResource(res), desc, modifier.size(size), tint)

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
 * 형광펜 색 — **마커펜 색**이지 글자색이 아니다(그래서 v1.6.82의 진한 강조색과 값이 다르다).
 * 다크에서는 **더 밝은 색을 더 옅게** 칠한다([markerAlpha]) — 어두운 면에 진한 색을 얹으면
 * 띠가 얼룩처럼 보이고 글자가 묻힌다.
 *
 * 명암비 실측(WCAG, 띠 = 색@알파 over `surfaceVariant`) — 여섯 조합 전부 AA(4.5:1) 통과:
 * | | 형광펜 위 글자 | 헤더 위 글자 | 헤더 위 시간(보조색) |
 * |---|---|---|---|
 * | 라이트 조식 | 13.78 | 14.46 | 7.41 |
 * | 라이트 중식 | 12.55 | 13.81 | 7.08 |
 * | 라이트 석식 | 12.31 | 13.68 | 7.01 |
 * | 다크 조식 | 7.10 | 9.45 | 5.78 |
 * | 다크 중식 | 7.75 | 9.85 | 6.02 |
 * | 다크 석식 | 7.89 | 9.98 | 6.10 |
 */
private fun markerColor(meal: Meal, dark: Boolean): Color = when {
    meal == Meal.BREAKFAST && !dark -> Color(0xFFFFC93C)
    meal == Meal.BREAKFAST -> Color(0xFFFFD84D)
    meal == Meal.LUNCH && !dark -> Color(0xFFFF9A3C)
    meal == Meal.LUNCH -> Color(0xFFFFB35C)
    !dark -> Color(0xFF8FA0FF)
    else -> Color(0xFFA9B6FF)
}

/** 형광펜 알파 — 다크에서 낮춘다(사용자 사양: "다크모드에서는 알파를 낮추고 색을 밝게"). */
private fun markerAlpha(dark: Boolean) = if (dark) 0.22f else 0.30f

/** 다음 끼니 헤더의 옅은 칠 — 형광펜보다 더 옅다(헤더는 배경이지 강조가 아니다). */
private fun headerAlpha(dark: Boolean) = if (dark) 0.12f else 0.16f

/**
 * "오늘" 요일 칩 채움색.
 *
 * ⚠ 스킴의 `primaryContainer`(`#A8F2C1` / `#005229`)를 **그대로 쓰면 안 된다.** 그 위에 얹히는
 * 달력 규칙 색(일=빨강 `#C4302B`/`#FF8A80`)이 4.24:1 · 4.11:1 로 AA에 못 미친다(실측).
 * 한 단계씩 물려 4.5:1을 넘긴 값이다 — 라이트 일 4.59 · 토 5.30 / 다크 일 4.98 · 토 5.40.
 * **요일 글자색은 달력과 똑같이 두고 칩 쪽을 양보한다**(글자색을 손대면 두 화면이 어긋난다).
 */
private fun todayChipColor(dark: Boolean): Color =
    if (dark) Color(0xFF00441F) else Color(0xFFC6F5D8)

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
                fontFamily = MenuFont, fontSize = ITEM_SIZE, lineHeight = ITEM_LINE,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "구내식당 표가 올라오면 여기에 바로 보입니다.",
                fontFamily = MenuFont, fontSize = 14.sp, lineHeight = 21.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (isAdmin) FilledTonalButton(onClick = onUpload) {
                Text("식단표 올리기", fontFamily = MenuFont, fontWeight = FontWeight.Medium)
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
