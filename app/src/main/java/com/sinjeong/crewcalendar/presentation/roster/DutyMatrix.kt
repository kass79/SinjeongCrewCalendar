package com.sinjeong.crewcalendar.presentation.roster

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sinjeong.crewcalendar.R
import com.sinjeong.crewcalendar.domain.model.*
import com.sinjeong.crewcalendar.presentation.theme.DutyColors
import java.time.LocalDate
import java.time.YearMonth

/*
 * 날짜 × 사람 근무 매트릭스 (공용).
 *
 * 두 화면이 같은 컴포저블을 쓰고 크기만 다르다 — 중복 구현하면 한쪽만 고치는 사고가 난다.
 *   · 동료근무(RosterScreen) = 사업소 전체 명단     → MatrixMetrics.Dense
 *   · 동료 탭(MatesScreen)   = 저장한 동료 + 본인   → MatrixMetrics.Roomy
 */

/** 동료 식별 키 — 이름만으로는 그룹 간 동명이인 3쌍(김지환·박두원·이용석)이 충돌한다 */
fun mateKey(name: String, group: CrewGroup) = "$name|${group.name}"

/** 매트릭스 한 행 = 사람 하나 */
data class MatrixPerson(
    val name: String,
    val group: CrewGroup,
    val offset: Int,
    val isMe: Boolean = false,
    /** 로그인 근무자(사번) — 근무변경 실시간 반영 대상 */
    val uid: String? = null,
)

/** 즐겨찾기·전화번호는 " (나)" 꼬리표 없는 실제 이름으로 매칭한다 */
val MatrixPerson.cleanName: String get() = name.removeSuffix(" (나)").trim()

val MatrixPerson.key: String get() = mateKey(cleanName, group)

/** 로그인 사용자를 매트릭스 행으로. 소속은 patternId + 차장 여부로 결정 */
fun meAsPerson(user: User?): MatrixPerson? = user?.let { u ->
    val group = Bundled.groupFor(u.patternId)?.let { grp ->
        if (u.role == CrewRole.CONDUCTOR) CrewGroup.MAIN_CONDUCTOR else grp
    } ?: CrewGroup.BRANCH
    MatrixPerson("${u.name} (나)", group, u.patternOffset, isMe = true, uid = u.uid)
}

/**
 * 칸·글자 크기 한 벌.
 * 사람 수가 화면 밀도를 정한다 — 282명을 훑는 화면과 대여섯 명을 비교하는 화면은 달라야 한다.
 */
data class MatrixMetrics(
    val cellW: Dp,
    val nameW: Dp,
    val codeSp: TextUnit,
    val nameSp: TextUnit,
    val daySp: TextUnit,
    val dowSp: TextUnit,
    /**
     * 근무 칩 높이 — **고정**이다(v1.6.23). 종전엔 글자 높이 + 위아래 여백이라
     * 글자가 줄어든 칸만 칩이 짧아져서 한 행 안에서 칩 크기가 들쭉날쭉했다.
     */
    val cellH: Dp,
    val rowPadV: Dp,
) {
    /** 칩 안에서 글자가 실제로 쓸 수 있는 폭 = 칸 − 칸 좌우 여백 − 칩 안쪽 여백 */
    val codeAvailW: Dp get() = cellW - CELL_GAP * 2 - CHIP_PAD_H * 2

    companion object {
        /** 칩 사이 간격 — 라운드가 커진 만큼 붙어 보이지 않게 1 → 1.5dp */
        val CELL_GAP = 1.5.dp
        /** 칩 안쪽 좌우 여백 */
        val CHIP_PAD_H = 2.dp

        /** 동료근무 — 사업소 전체(282명). v1.6.16의 26dp는 "읽기 힘들다"는 피드백을 받았다 */
        val Dense = MatrixMetrics(
            cellW = 32.dp, nameW = 62.dp,
            codeSp = 11.sp, nameSp = 11.sp, daySp = 10.sp, dowSp = 8.5.sp,
            cellH = 22.dp, rowPadV = 1.5.dp,
        )

        /** 동료 탭 — 저장한 동료 + 본인. 인원이 적으니 칸을 넉넉히 잡아 날짜 비교가 편하게 */
        val Roomy = MatrixMetrics(
            cellW = 44.dp, nameW = 80.dp,
            codeSp = 15.sp, nameSp = 13.5.sp, daySp = 12.5.sp, dowSp = 10.sp,
            cellH = 36.dp, rowPadV = 2.5.dp,
        )
    }
}

/** 오늘 열 근처로 맞춰 두는 가로 스크롤 상태 — 헤더와 전 행이 하나를 공유해 같이 움직인다 */
@Composable
fun rememberMatrixScroll(month: YearMonth, cellW: Dp): ScrollState {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    LaunchedEffect(month, cellW) {
        if (month == YearMonth.now()) {
            val px = with(density) { (cellW * (LocalDate.now().dayOfMonth - 3)).toPx() }
            scroll.scrollTo(px.toInt().coerceAtLeast(0))
        } else scroll.scrollTo(0)
    }
    return scroll
}

/** 근무 종별 색 — 달력 칩(`dutyChipColors`)·월 이미지와 같은 매핑 */
fun dutyCellColors(type: DutyType, duty: DutyColors, fallback: Color): Pair<Color, Color> =
    when (type) {
        DutyType.MAIN_DAY, DutyType.OFFICE -> duty.main to duty.onMain
        DutyType.MAIN_NIGHT, DutyType.BRANCH_NIGHT, DutyType.SPECIAL -> duty.night to duty.onNight
        DutyType.POST_NIGHT -> duty.off to duty.onOff
        DutyType.REST, DutyType.BRANCH_REST -> duty.rest to duty.onRest
        DutyType.STANDBY, DutyType.BRANCH_STANDBY -> duty.standby to duty.onStandby
        DutyType.BRANCH -> duty.main to duty.onMain
        DutyType.ETC -> Color.Transparent to fallback
    }

/**
 * 열 강조 밴드 — 헤더와 모든 데이터 행에 같은 색을 깔아 **세로로 관통하는 띠**를 만든다(v1.6.21).
 * 종전엔 헤더 글자색만 달라서 "오늘 이 사람 뭐 하지"를 눈으로 세로 추적해야 했다.
 * 반투명이라 근무 칩 색을 덮지 않고 칩 사이 여백에만 얹힌다. 폭 비용 0dp.
 */
@Composable
private fun columnTint(date: LocalDate, today: LocalDate, duty: DutyColors): Color = when {
    date == today -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    Bundled.PUBLIC_HOLIDAYS.containsKey(date) || date.dayOfWeek.value == 7 ->
        duty.sunday.copy(alpha = 0.09f)
    date.dayOfWeek.value == 6 -> duty.saturday.copy(alpha = 0.09f)
    else -> Color.Transparent
}

/**
 * 열 배경 + 오늘 열 좌우 세로 레일.
 * 칩이 칸을 거의 꽉 채워서 반투명 밴드만으론 오늘이 잘 안 보인다 — 레일을 같이 그어
 * 헤더부터 마지막 행까지 이어지는 세로 기둥을 만든다.
 */
@Composable
private fun Modifier.columnBand(date: LocalDate, today: LocalDate, duty: DutyColors): Modifier {
    val tint = columnTint(date, today, duty)
    val rail = MaterialTheme.colorScheme.primary
    val isToday = date == today
    return this.drawBehind {
        if (tint != Color.Transparent) drawRect(tint)
        if (isToday) {
            val w = 1.5.dp.toPx()
            drawRect(rail, topLeft = Offset(0f, 0f), size = Size(w, size.height))
            drawRect(rail, topLeft = Offset(size.width - w, 0f), size = Size(w, size.height))
        }
    }
}

/**
 * 이름 열과 본문 사이 경계선 — 가로로 스크롤해도 "어느 행인지"를 붙잡아 주는 고정 기둥.
 * 이름 Row가 아니라 **행 전체**에 그린다. 이름 Row는 글자 높이라 칸보다 짧아 선이 점선처럼 끊긴다.
 */
private fun Modifier.nameColumnEdge(color: Color, x: Float) = drawBehind {
    drawLine(color, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
}

/** 헤더: 왼쪽 이름칸 + 가로 스크롤되는 날짜·요일 */
@Composable
fun MatrixDateHeader(
    month: YearMonth,
    m: MatrixMetrics,
    hScroll: ScrollState,
    duty: DutyColors,
) {
    val today = LocalDate.now()
    val edge = MaterialTheme.colorScheme.outline
    val nameEdgeX = with(LocalDensity.current) { m.nameW.toPx() }
    Row {
        Box(
            Modifier.width(m.nameW)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .nameColumnEdge(edge, nameEdgeX)
                .padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Text(
                "이름", fontSize = m.dowSp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(Modifier.horizontalScroll(hScroll)) {
            (1..month.lengthOfMonth()).forEach { d ->
                val date = month.atDay(d)
                val isToday = date == today
                val hol = Bundled.PUBLIC_HOLIDAYS.containsKey(date)
                Column(
                    Modifier.width(m.cellW)
                        .background(
                            if (isToday) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        // 주말·공휴일은 헤더에도 같은 띠를 얹어 본문 밴드와 하나로 이어진다
                        .columnBand(date, today, duty)
                        .padding(vertical = 1.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val c = when {
                        isToday -> MaterialTheme.colorScheme.onPrimaryContainer
                        hol || date.dayOfWeek.value == 7 -> duty.sunday
                        date.dayOfWeek.value == 6 -> duty.saturday
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text("$d", fontSize = m.daySp, fontWeight = FontWeight.Bold, color = c)
                    Text(
                        listOf("월", "화", "수", "목", "금", "토", "일")[date.dayOfWeek.value - 1],
                        fontSize = m.dowSp, color = c,
                    )
                }
            }
        }
    }
}

/** 한 사람의 한 달 근무 행 */
@Composable
fun MatrixRow(
    p: MatrixPerson,
    month: YearMonth,
    m: MatrixMetrics,
    hScroll: ScrollState,
    duty: DutyColors,
    isFav: Boolean = false,
    /** 근무변경 실시간 반영 (날짜 → 변경 근무, Firebase 연동 시) */
    overrides: Map<LocalDate, String> = emptyMap(),
    /** 홀짝 줄무늬 — 사람이 많을 때 가로로 눈이 미끄러지는 걸 막는다 */
    zebra: Boolean = false,
    onNameClick: () -> Unit = {},
) {
    val pattern = Bundled.patternFor(p.group)
    val today = LocalDate.now()
    val edge = MaterialTheme.colorScheme.outline
    // 본인 행은 모든 비교의 기준선이라 행 전체를 옅게 깐다(종전엔 이름 글자만 파랑)
    val rowTint = when {
        p.isMe -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        zebra -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
        else -> Color.Transparent
    }
    val nameEdgeX = with(LocalDensity.current) { m.nameW.toPx() }
    Row(
        Modifier.background(rowTint).nameColumnEdge(edge, nameEdgeX),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.width(m.nameW).clickable { onNameClick() }.padding(horizontal = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 말줄임 대신 자동 축소 — `강민성 (나)`가 잘리지 않는다
            AutoFitText(
                p.name,
                base = m.nameSp, min = m.nameSp * 0.62f,
                color = if (p.isMe) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (isFav) Text("★", fontSize = m.dowSp, color = duty.onStandby)
        }
        Row(Modifier.horizontalScroll(hScroll)) {
            (1..month.lengthOfMonth()).forEach { d ->
                val date = month.atDay(d)
                val code = overrides[date]?.let { DutyCode.parse(it) } ?: pattern.dutyOn(date, p.offset)
                val (bg, fg) = dutyCellColors(code.type, duty, MaterialTheme.colorScheme.onSurfaceVariant)
                Box(
                    Modifier.width(m.cellW).height(m.cellH + m.rowPadV * 2)
                        .columnBand(date, today, duty)
                        .padding(
                            horizontal = MatrixMetrics.CELL_GAP,
                            vertical = m.rowPadV,
                        ),
                ) {
                    DutyChip(code.display.ifBlank { "·" }, bg, fg, m)
                }
            }
        }
    }
}

/**
 * 근무 코드 칩 — 표 전체가 **같은 크기의 알약**이고 글자는 그 안에 세로 가운데 정렬된다(v1.6.23).
 *
 * 종전 문제("텍스트가 최적화 안 되어 있다"):
 *  1. 칩 높이가 글자 높이를 따라가서 줄어든 칸만 칩이 짧고 위로 붙어 보였다.
 *  2. `AutoFitText`가 칸마다 **따로** 측정해 줄여서 같은 표 안에 서너 가지 글자 크기가 섞였다.
 *     게다가 한 번 줄면 되돌아오지 않아(`fit`은 감소만 한다) LazyColumn이 행을 재활용하거나
 *     가로 스크롤 중 폭 0으로 한 번 측정되면 그 칸만 영구히 작아졌다 — 재현이 들쭉날쭉했던 이유.
 *
 * 그래서 **측정 대신 계산**한다. 글자 폭을 한글 1em · 숫자/기호 0.56em으로 근사해 필요한 폭을
 * 구하고 칸에 들어가면 그냥 기준 크기를 쓴다. 실제 근무코드는 길어야 3글자(`휴16`·`대13`)라
 * **표의 거의 모든 칸이 정확히 같은 크기**가 된다. 4글자짜리 근무변경 코드(`돌봄휴가`·`대기충당`)만
 * 한 줄로 찌그러뜨리는 대신 **두 줄**로 접어 훨씬 크게 읽힌다.
 */
@Composable
private fun DutyChip(text: String, bg: Color, fg: Color, m: MatrixMetrics) {
    // sp ↔ dp 환산을 Density에 맡겨야 시스템 글자 크기 배율(fontScale)이 반영된다
    val availSp = with(LocalDensity.current) { m.codeAvailW.toSp().value }
    // 한글·한자는 정사각(1em), 숫자·`~`·영문은 그 절반 남짓
    val units = text.sumOf { if (it.code >= 0x1100) 1.0 else 0.56 }.toFloat()
    val needed = units * m.codeSp.value
    val (size, lines) = when {
        needed <= availSp -> m.codeSp to 1
        // 3글자 이상이 안 들어가면 두 줄이 한 줄로 줄이는 것보다 크게 읽힌다
        units >= 3f -> minOf(m.codeSp.value * 0.75f, availSp / (units / 2f)).sp to 2
        else -> (availSp / units).sp to 1
    }
    // 자동 줄바꿈에 맡기면 `돌봄휴가`가 `돌봄휴 / 가`로 3:1이 된다 → 반씩 직접 끊는다
    val shown = if (lines == 2) text.chunked((text.length + 1) / 2).joinToString("\n") else text
    Surface(
        color = bg, contentColor = fg,
        // 라운드 5dp → 알약(높이의 절반). 칸이 좁아 각진 사각형은 표가 딱딱해 보였다.
        shape = RoundedCornerShape(percent = 50),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(Modifier.padding(horizontal = MatrixMetrics.CHIP_PAD_H), contentAlignment = Alignment.Center) {
            Text(
                shown,
                fontSize = size, lineHeight = size * 1.05,
                fontWeight = FontWeight.ExtraBold,
                color = fg,
                maxLines = lines, softWrap = lines > 1,
                overflow = TextOverflow.Visible,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * 칸 폭에 맞을 때까지 글자를 줄이는 한 줄 텍스트.
 * 달력 칩(`MainCalendarScreen`)과 같은 방식 — 말줄임이 뜨는 순간 hasVisualOverflow가 false로
 * 떨어지므로 `isLineEllipsized`도 같이 본다.
 */
@Composable
fun AutoFitText(
    text: String,
    base: TextUnit,
    min: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
    align: TextAlign = TextAlign.Start,
) {
    var fit by remember(text, base) { mutableStateOf(base) }
    Text(
        text,
        modifier = modifier,
        fontSize = fit, lineHeight = fit * 1.1,
        fontWeight = FontWeight.ExtraBold,
        color = color,
        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, textAlign = align,
        onTextLayout = {
            val cut = it.hasVisualOverflow || (it.lineCount > 0 && it.isLineEllipsized(0))
            if (cut && fit > min) fit *= 0.92f
        },
    )
}

/**
 * 나눔손글씨 펜(OFL 1.1). 이 라벨 전용 — 앱 전체 글꼴은 그대로 둔다.
 * 손글씨체라 같은 sp에서도 본문보다 작아 보여 18sp로 잡았다(labelMedium 12sp 대비).
 */
private val PenFamily = FontFamily(Font(R.font.nanum_pen_script))

/** `★ 즐겨찾기 ▾` — 지정돼 있으면 `★ 즐겨찾기 · 우리 조 ▾` */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FavLabel(fav: FavGroup?, onClick: () -> Unit) {
    val duty = com.sinjeong.crewcalendar.presentation.theme.LocalDutyColors.current
    val on = fav != null
    val tint = if (on) duty.onStandby else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (on) duty.standby else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = tint,
    ) {
        Row(
            Modifier.padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Icon(
                if (on) Icons.Default.Star else Icons.Outlined.StarOutline,
                null,
                Modifier.size(18.dp),
                tint = tint,
            )
            Text(
                "즐겨찾기" + (fav?.let { " · ${it.label}" } ?: ""),
                fontFamily = PenFamily,
                fontSize = 18.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
            )
            Icon(Icons.Default.ArrowDropDown, "즐겨찾기 그룹 선택", Modifier.size(20.dp), tint = tint)
        }
    }
}

/**
 * 이름을 탭했을 때 뜨는 시트 — 전화·문자·★그룹, 그리고 동료 탭에서는 삭제까지.
 * 두 화면이 공유한다(`onRemove`가 null이면 삭제 항목이 안 뜬다).
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PersonSheet(
    person: MatrixPerson,
    fav: FavGroup?,
    onSetFav: (FavGroup?) -> Unit,
    onDismiss: () -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val duty = com.sinjeong.crewcalendar.presentation.theme.LocalDutyColors.current
    val cleanName = person.cleanName
    val phone = BundledStaff.phoneFor(cleanName, person.group == CrewGroup.MAIN_CONDUCTOR)
    var favMenu by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(cleanName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Box {
                // 별 아이콘만 있으면 "이게 즐겨찾기"라는 걸 알 수 없다 → 글자와 화살표를 한 덩어리로.
                FavLabel(fav = fav, onClick = { favMenu = true })
                DropdownMenu(expanded = favMenu, onDismissRequest = { favMenu = false }) {
                    FavGroup.entries.forEach { g ->
                        DropdownMenuItem(
                            text = { Text("★ ${g.label}" + if (fav == g) " ✓" else "") },
                            onClick = { onSetFav(g); favMenu = false },
                        )
                    }
                    if (fav != null) DropdownMenuItem(
                        text = { Text("즐겨찾기 해제") },
                        onClick = { onSetFav(null); favMenu = false },
                    )
                }
            }
            Text(
                person.group.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (phone != null) {
                Text(
                    phone, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    modifier = Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = {
                            clipboard.setText(AnnotatedString(phone))
                            android.widget.Toast.makeText(context, "복사됨", android.widget.Toast.LENGTH_SHORT).show()
                        },
                    ),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = {
                        runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:" + phone))) }
                        onDismiss()
                    }, modifier = Modifier.weight(1f)) { Text("전화") }
                    OutlinedButton(onClick = {
                        runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:" + phone))) }
                        onDismiss()
                    }, modifier = Modifier.weight(1f)) { Text("문자") }
                }
                Text("판독본 번호예요. 틀리면 관리자에게 알려주세요.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("등록된 전화번호가 없습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onRemove != null) {
                TextButton(onClick = { onRemove(); onDismiss() }) {
                    Text("동료 삭제", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
