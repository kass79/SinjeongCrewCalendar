package com.sinjeong.crewcalendar.presentation.roster

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sinjeong.crewcalendar.R
import com.sinjeong.crewcalendar.domain.model.*
import com.sinjeong.crewcalendar.presentation.theme.DutyColors
import java.time.LocalDate

/*
 * 날짜 × 사람 근무 매트릭스.
 *
 * 쓰는 곳은 동료 탭([com.sinjeong.crewcalendar.presentation.mates.MatesScreen]) 하나다.
 * v1.6.39에서 동료근무 화면(RosterScreen)을 동료 탭으로 합치면서 두 번째 호출부가 없어졌고,
 * 그때 크기 한 벌(`MatrixMetrics.Dense`)도 같이 지웠다 — 한 화면이면 한 모양이면 된다.
 * 파일을 따로 둔 건 그려내는 법(밴드·레일·칩 계산)과 화면 조립을 갈라 두기 위해서다.
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
        /**
         * 칩 안쪽 좌우 여백. 알약이라 좌우 끝은 어차피 곡선이라 글자가 못 오고,
         * 가장 넓은 3글자 코드(`대34` — 굵은 숫자 두 개)까지 기준 크기로 들어가야 해서 1dp다.
         */
        val CHIP_PAD_H = 1.dp

        /**
         * 동료 탭 — 이 앱에서 매트릭스를 그리는 **유일한 크기 한 벌**.
         *
         * v1.6.17의 44dp는 "인원이 적으니 넉넉하게"로 정했는데 411dp 화면에 날짜가 **7.5일**밖에
         * 안 들어와 "글자가 커서 최적화가 안 됐다"는 피드백을 받았다(v1.6.38).
         * 이 화면의 목적은 **남과 날짜를 비교하는 것**이라 보이는 날짜 수가 글자 크기보다 중요하다.
         * → 종전 동료근무(32dp)와 44dp의 중간인 **36dp**. 411dp에서 9.5일.
         * v1.6.39부터는 소속 전체(최대 98명)도 이 크기로 그린다 — 종전 동료근무 32dp보다
         * 오히려 커서 가독성 후퇴는 없다.
         *
         * 코드 13sp는 그냥 고른 값이 아니다 — [DutyChip]의 계산식상 3글자 코드(`대11`·`휴28`)의
         * 필요 폭은 `2.24 × codeSp`이고 칸의 가용 폭은 `cellW − 5`다.
         * 36dp면 가용 31sp ≥ 2.24 × 13 = 29.1 → **3글자까지 전부 기준 크기 한 줄**로 들어간다.
         * (14sp면 31.4 > 31로 넘쳐 축소가 걸린다. 13sp가 이 칸 폭의 상한이다.)
         * v1.6.49부터 이 값은 **표 전체의 글자 크기**다 — 칸마다 따로 줄이지 않는다.
         */
        val Roomy = MatrixMetrics(
            cellW = 36.dp, nameW = 68.dp,
            codeSp = 13.sp, nameSp = 12.sp, daySp = 10.5.sp, dowSp = 8.5.sp,
            cellH = 28.dp, rowPadV = 2.dp,
        )
    }
}

/**
 * **근무 종별 색의 단일 출처.** 달력 칩·동료 그리드·근무선택 그리드가 전부 여기를 쓴다.
 *
 * v1.6.33에서 `MainCalendarScreen`에 있던 같은 내용의 `dutyChipColors`를 지우고 합쳤다.
 * 색을 두 벌로 두면 한쪽만 고치는 사고가 난다(v1.6.17이 [DutyMatrix]를 뽑은 이유 그대로).
 *
 * ⚠ 넘길 값은 [DutyCode.type]이 아니라 **[DutyCode.colorType]** 이다 — 대행("충당 9" 꼴)은
 * 시각·행로표를 원래 근무에서 가져오되 색만 대기(노랑)로 되돌려야 한다.
 * 월 이미지(`MonthImage.dutyColors`)는 Compose 밖이라 Int 사본을 따로 들고 있다.
 */
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
    // 0.16 → 0.24 (v1.6.24). 칩이 칸을 거의 채워서 옅은 띠는 스크롤 중에 놓치기 쉬웠다.
    date == today -> MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
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
            val w = 2.5.dp.toPx()
            drawRect(rail, topLeft = Offset(0f, 0f), size = Size(w, size.height))
            drawRect(rail, topLeft = Offset(size.width - w, 0f), size = Size(w, size.height))
        }
    }
}

/**
 * **근무변경(수동)된 날 표시 — 오른쪽 아래 모서리 접힘**(v1.6.41).
 *
 * 종전엔 `✱ 수동변경됨`이 상세시트 안에만 있어서 달력에서는 날짜를 하나하나 열어 봐야 했다.
 * 칸이 좁아 글자·아이콘은 못 넣는다 → **폭·높이 비용 0dp**로 칸 위에 겹쳐 그린다
 * (`drawWithContent`라 내용 위에 얹히고, 앞선 `clip`의 둥근 모서리에 잘려 접힌 종이처럼 보인다).
 *
 * 달력 칸의 취소선 원래근무(`~~13~~`)는 그대로 둔다 — 저건 "무엇이었는지"고 이건 "바뀌었다"다.
 */
fun Modifier.changedCorner(color: Color, size: Dp) = drawWithContent {
    drawContent()
    val s = size.toPx()
    val (w, h) = this.size.width to this.size.height
    drawPath(
        Path().apply { moveTo(w, h - s); lineTo(w, h); lineTo(w - s, h); close() },
        color,
    )
}

/**
 * 이름 열과 본문 사이 경계선 — 가로로 스크롤해도 "어느 행인지"를 붙잡아 주는 고정 기둥.
 * 이름 Row가 아니라 **행 전체**에 그린다. 이름 Row는 글자 높이라 칸보다 짧아 선이 점선처럼 끊긴다.
 */
private fun Modifier.nameColumnEdge(color: Color, x: Float) = drawBehind {
    drawLine(color, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
}

/**
 * 헤더: 왼쪽 이름칸 + 가로 스크롤되는 날짜·요일.
 * 그릴 날짜를 **그대로 받는다**(v1.6.39). 종전엔 `YearMonth` + `startDay`라 한 달 안에서만
 * 잘라 그릴 수 있었고, "오늘부터 한 달"은 달 경계를 넘는다.
 * 리스트를 받으면 그리는 쪽은 범위를 생각할 필요가 없어진다 — [MatrixRow]와 **같은 리스트**를
 * 넘기기만 하면 열이 어긋나지 않는다.
 */
@Composable
fun MatrixDateHeader(
    dates: List<LocalDate>,
    m: MatrixMetrics,
    hScroll: ScrollState,
    duty: DutyColors,
) {
    val today = LocalDate.now()
    val edge = MaterialTheme.colorScheme.outline
    val headerBg = MaterialTheme.colorScheme.surfaceVariant
    val nameEdgeX = with(LocalDensity.current) { m.nameW.toPx() }
    // 이름칸 바탕·경계선은 **행 전체**에 그린다(v1.6.48). `Box`에 걸면 글자 높이만큼만 칠해져
    // 날짜 칸보다 짧고 아래쪽에 바탕이 끊긴 홈이 남는다 — 420dpi 실측 68px vs 126px.
    // [nameColumnEdge]가 선을 행에 그리는 이유와 똑같은 문제다.
    Row(
        Modifier.drawBehind {
            drawRect(headerBg, size = Size(nameEdgeX, size.height))
            drawLine(edge, Offset(nameEdgeX, 0f), Offset(nameEdgeX, size.height), 1.dp.toPx())
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(m.nameW).padding(horizontal = 4.dp)) {
            Text(
                "이름", fontSize = m.dowSp, lineHeight = m.dowSp * 1.4,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(Modifier.horizontalScroll(hScroll)) {
            dates.forEach { date ->
                val isToday = date == today
                val hol = Bundled.PUBLIC_HOLIDAYS.containsKey(date)
                Column(
                    Modifier.width(m.cellW)
                        // 오늘 열 머리는 꽉 찬 primary — 세로 레일이 시작되는 지점을 못 놓치게(v1.6.24)
                        .background(
                            if (isToday) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        // 주말·공휴일은 헤더에도 같은 띠를 얹어 본문 밴드와 하나로 이어진다
                        .columnBand(date, today, duty),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val c = when {
                        isToday -> MaterialTheme.colorScheme.onPrimary
                        hol || date.dayOfWeek.value == 7 -> duty.sunday
                        date.dayOfWeek.value == 6 -> duty.saturday
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    // 달이 바뀜는 칸은 `9/1`로 적는다 — 범위가 두 달에 걸치므로
                    // 날짜만 있으면 31 다음 1이 뭐지 알 수 없다(v1.6.39).
                    //
                    // ⚠ **`lineHeight`를 반드시 같이 준다**(v1.6.48). `fontSize`만 줄이면 줄 높이는
                    // M3 기본 스타일(`bodyLarge`)의 **24sp 그대로**라, 10.5sp 날짜도 8.5sp 요일도
                    // 한 줄에 24dp씩 먹어 헤더가 48dp였다(420dpi 실측 126px = 글자 두 줄에 딸린
                    // 빈칸이 대부분). 사용자 지적 *"이름 날짜 밑에 요일이 있는데 거기도 칸이 넓네?"*.
                    // 글자 크기는 그대로 두고 줄 높이만 1.4배로 못 박아 26dp대로 내린다 —
                    // sp 배수라 글자배율을 키우면 칸도 같이 커진다(고정 dp였다면 잘렸을 자리).
                    Text(
                        if (date.dayOfMonth == 1) "${date.monthValue}/1" else "${date.dayOfMonth}",
                        fontSize = m.daySp, lineHeight = m.daySp * 1.4,
                        fontWeight = FontWeight.Bold, color = c,
                        maxLines = 1, softWrap = false,
                    )
                    Text(
                        listOf("월", "화", "수", "목", "금", "토", "일")[date.dayOfWeek.value - 1],
                        fontSize = m.dowSp, lineHeight = m.dowSp * 1.4, color = c,
                    )
                }
            }
        }
    }
}

/**
 * **동료 탭 전용** 칩 라벨 — 지선 주간·야간 다이아만 `지`를 되살린다(v1.6.48).
 *
 * [DutyCode.display]는 지선 다이아의 `지`를 뗀다(칸이 좁아 정한 기존 앱 방식). 달력은 **내 근무
 * 하나**만 보여 주니 그래도 됐지만, 이 화면은 여러 사람이 세로로 나란히 오는데 지선 `지1`과 본선
 * `1`번이 **글자도 색도 똑같아져** 구분이 안 됐다(사용자: *"1이랑 지선1이랑 구분이 잘 안가네?"*).
 * → 여기서만 [DutyCode.raw]를 그대로 쓴다.
 *
 * ⚠ [DutyCode.display]·[DutyCode.gridLabel]은 **손대지 않는다.** 달력·위젯·알림·근무표 공유
 * 이미지가 전부 그걸 쓰고, 사용자가 *"달력탭 말고 거기 만이라도"*라고 못 박았다.
 * 이 파일에 private으로 두는 것이 곧 그 보증이다 — 매트릭스를 그리는 곳은 동료 탭 하나뿐이다
 * (파일 첫 주석). [DutyCode]에 프로퍼티로 뒀다면 다음 사람이 달력에서도 부를 수 있다.
 *
 * 걸리는 건 지선 **주간(지1~지8)·야간(지10~지14)** 뿐이다. 지선 대기는 [DutyCode.display]가
 * 이미 `지대1`로 살려 두고(v1.6.36), 충당 계열 아랫줄은 [DutyCode.diaRaw]라 이미 `지2`로 온다.
 *
 * 폭: 가장 긴 `지14`가 [DutyChip] 글자폭 모델로 2.24 units — 이미 쓰이는 `대34`·`휴28`과
 * **같은 값**이라 36dp 칸에 13sp 한 줄로 그대로 들어간다(축소 경계 2.265, 두 줄 경계 3.6).
 */
private val DutyCode.mateLabel: String
    get() = if (fill == null && (type == DutyType.BRANCH || type == DutyType.BRANCH_NIGHT)) raw
    else gridLabel

/** 한 사람의 근무 행 — [dates]는 [MatrixDateHeader]에 넘긴 것과 **같은 리스트**여야 한다 */
@Composable
fun MatrixRow(
    p: MatrixPerson,
    dates: List<LocalDate>,
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
            dates.forEach { date ->
                val changed = overrides[date] != null
                val code = overrides[date]?.let { DutyCode.parse(it) } ?: pattern.dutyOn(date, p.offset)
                val (bg, fg) = dutyCellColors(code.colorType, duty, MaterialTheme.colorScheme.onSurfaceVariant)
                Box(
                    Modifier.width(m.cellW).height(m.cellH + m.rowPadV * 2)
                        .columnBand(date, today, duty)
                        .padding(
                            horizontal = MatrixMetrics.CELL_GAP,
                            vertical = m.rowPadV,
                        ),
                ) {
                    DutyChip(code.mateLabel.ifBlank { "·" }, bg, fg, m, changed)
                }
            }
        }
    }
}

/**
 * 표 전체가 쓰는 **한 줄 기준 라벨 폭** — 3글자 코드(`휴29`·`지14`·`대13`, 한글 1 + 숫자 0.62×2).
 * 실제로 나올 수 있는 한 줄 라벨을 전수 조사해 정한 값이다([DutyChip] 주석).
 */
private const val UNIFORM_UNITS = 2.24f

/** 이 폭을 넘는 라벨만 두 줄(`돌봄휴가`·`대기충당` 같은 4글자 근무변경 코드) */
private const val TWO_LINE_UNITS = 3.6f

/**
 * 근무 코드 칩 — 표 전체가 **같은 크기의 알약**이고 글자는 그 안에 세로 가운데 정렬된다(v1.6.23).
 *
 * 종전 문제("텍스트가 최적화 안 되어 있다"):
 *  1. 칩 높이가 글자 높이를 따라가서 줄어든 칸만 칩이 짧고 위로 붙어 보였다.
 *  2. `AutoFitText`가 칸마다 **따로** 측정해 줄여서 같은 표 안에 서너 가지 글자 크기가 섞였다.
 *     게다가 한 번 줄면 되돌아오지 않아(`fit`은 감소만 한다) LazyColumn이 행을 재활용하거나
 *     가로 스크롤 중 폭 0으로 한 번 측정되면 그 칸만 영구히 작아졌다 — 재현이 들쭉날쭉했던 이유.
 *
 * 그래서 **측정 대신 계산**한다. 글자 폭을 한글 1em · 숫자/기호 0.62em으로 근사한다.
 * v1.6.49부터는 "칸에 들어가면 기준 크기"가 아니라 **모든 칸이 무조건 한 크기**([UNIFORM_UNITS])다 —
 * 종전엔 긴 라벨만 그 칸에서 줄어들어 표에 서너 가지 크기가 섞였다. 기준 크기로 안 들어가는
 * 소수 라벨은 **가로로만 좁혀**(scaleX) 높이를 표와 맞춘다.
 * 4글자짜리 근무변경 코드(`돌봄휴가`·`대기충당`)만 **두 줄**로 접어 훨씬 크게 읽힌다.
 */
@Composable
private fun DutyChip(text: String, bg: Color, fg: Color, m: MatrixMetrics, changed: Boolean = false) {
    // ⚠ **계산은 전부 px 한 단위로 한다. sp ↔ dp를 오가면 안 된다**(v1.6.42 ⑥ — 실기기에서
    // `휴25`·`대13`이 잘린 진짜 원인). 종전 코드는 가용폭을 `Dp.toSp()`로 sp에 바꿔 비교했는데,
    // **API 34+의 `Dp.toSp()`는 안드로이드 14 비선형 글자배율 표를 탄다.** 큰 값일수록 배율이
    // 작아지는 표라 31dp는 "26.3sp"로 환산되는 반면(1.18배), 글자를 실제로 그리는
    // `TextUnit.toPx()`는 **선형**(value x fontScale x density = 1.3배)이다.
    // 둘을 섞으면 좁은 칸을 넓다고 믿는다 →
    //   fontScale 1.3 실측: 가용 81.4px인데 종전 계산은 89.8px짜리 글자를 그려 마지막 숫자가 잘렸다.
    //   (여유 5%를 줘도 85.3px로 여전히 넘쳤다 — 에뮬 420dpi에서 두 번 다 확인)
    // px로 통일하면 배율 표가 무엇이든 "그릴 폭 <= 칸 폭"이 그대로 성립한다.
    //
    // 0.95는 글자폭 모델(아래 units)의 오차 여유다. 실제 숫자 글리프는 0.568em이라 0.62 모델이
    // 이미 넉넉하지만, 기기 기본 글꼴이 바뀌면 그만큼이 사라진다.
    val d = LocalDensity.current
    val spToPx = d.fontScale * d.density
    val availPx = with(d) { m.codeAvailW.toPx() } * 0.95f
    val basePx = m.codeSp.value * spToPx
    // 한글·한자는 정사각(1em), 숫자·`~`·영문은 그 절반 남짓(ExtraBold라 넉넉히 잡는다)
    fun widthUnits(s: String) = s.sumOf { if (it.code >= 0x1100) 1.0 else 0.62 }.toFloat()
    // 줄바꿈이 이미 들어온 라벨 = 충당 계열(`대기충당`⏎`지2`, DutyCode.gridLabel). **그 자리에서** 접는다 —
    // 아래 chunked에 맡기면 `대기충당지2`가 `대기충`/`당지2`로 갈려 다이아가 두 줄에 걸쳐 깨진다.
    val preSplit = '\n' in text
    val units = widthUnits(text)
    // 두 줄은 **네 글자짜리 근무변경 코드**(`돌봄휴가`·`대기충당`, units 4.0)에서만 이득이다.
    // 반면 `지대11`(units 3.24)이 `지대`/`11`로 접히면 사용자 눈엔 "깨진 것"으로 보인다
    // (v1.6.39 지적) → 경계는 **3.6**, 3글자까지는 무조건 한 줄이다.
    val lines = if (preSplit || units >= TWO_LINE_UNITS) 2 else 1
    // 자동 줄바꿈에 맡기면 `돌봄휴가`가 `돌봄휴 / 가`로 3:1이 된다 → 반씩 직접 끊는다.
    // 줄바꿈이 이미 들어온 라벨(충당 계열)은 **그 자리에서** 접는다 — chunked에 맡기면
    // `대기충당지2`가 `대기충`/`당지2`로 갈려 다이아가 두 줄에 걸쳐 깨진다.
    val shown = if (lines == 2 && !preSplit) text.chunked((text.length + 1) / 2).joinToString("\n") else text
    // **표 전체가 한 크기다**(v1.6.49). 종전엔 칸마다 `availPx / units`로 따로 줄여서
    // `휴1`(13sp)·`지대1`(11.2sp)·`지대11`(9.1sp)이 한 화면에 섞였고, 글자배율이 1.0을 넘는
    // 순간엔 3글자 코드(`지12`·`휴21`) 전부가 2글자 코드보다 작아졌다
    // (사용자: *"야간이랑 주간이랑 텍스트 크기가 다른데?"*, *"휴1,지대1,지4 텍스트 크기가 다 다른데?"*).
    //
    // 기준은 **3글자 코드**([UNIFORM_UNITS] = `휴29`·`지14`·`대13` = 2.24 units)다. 실제 명단·
    // 근무변경에서 나올 수 있는 한 줄 라벨 125종 중 120종이 여기 이하고(본선 108칸 중 20칸이
    // `휴10`~`휴29`), 이게 36dp 칸에 들어가는 최대 크기가 곧 13sp다(2.24 × 13 = 29.1 ≤ 29.45).
    // → 크기는 **하나**로 정해지고, 칸이 좁아지는 큰 글자배율에서도 모든 칸이 같이 줄어든다.
    val sizePx =
        if (lines == 1) minOf(basePx, availPx / UNIFORM_UNITS)
        // 두 줄은 두 줄끼리 한 크기 — 종전엔 `충당⏎9`(9.75sp)와 `대기충당⏎지2`(7.36sp)가 갈렸다.
        // 세로 상한(칸 높이의 42%)이 없으면 큰 글자배율에서 아랫줄이 칸을 넘는다(v1.6.42 ⑥ 실측).
        else minOf(basePx * 0.75f, with(d) { m.cellH.toPx() } * 0.42f)
    // px → sp도 **선형 역산**이다. `Float.toSp()`를 쓰면 다시 비선형 표를 타서 어긋난다.
    val size = (sizePx / spToPx).sp
    // 기준 크기로도 안 들어가는 **소수 라벨**(`지대1`·`지대2`·`지대11`·`가연차`, 두 줄의 `대기충당`)은
    // 크기를 줄이는 대신 **가로로만 좁힌다**. 글자 높이가 표와 같아야 눈에 "같은 크기"로 보이고,
    // 폭은 어차피 종전 축소분과 같다(`지대11`: 종전 9.1sp 온폭 = 지금 13sp × 0.70). 잘림도 없다.
    // 폭 계산은 여기까지 전부 px 한 단위다 — 비율이라 sp↔dp 환산이 끼어들 자리가 없다.
    val maxLineUnits = if (lines == 2) shown.split('\n').maxOf(::widthUnits) else units
    val scaleX = (availPx / (maxLineUnits * sizePx)).coerceAtMost(1f)
    // ⚠ **글자는 알약 `Surface` 안이 아니라 위에 겹쳐 그린다**(v1.6.42 ⑥).
    // `Surface`는 shape로 내용을 잘라내는데 알약은 위아래 가장자리에서 폭이 급격히 좁아진다 —
    // 두 줄짜리 코드(`돌봄휴가`)는 바깥 줄이 그 곡선에 걸려 잘렸다(fs 1.3 실측: 가장자리에서
    // 좌우 7.5dp씩 먹혀 쓸 수 있는 폭이 33 → 18dp). 겹쳐 그리면 글자가 곡선을 살짝 넘어도
    // 칸 사이 여백(1.5dp) 안이라 눈에 띄지 않고, 잘림은 사라진다.
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            color = bg,
            // 라운드 5dp → 알약(높이의 절반). 칸이 좁아 각진 사각형은 표가 딱딱해 보였다.
            shape = RoundedCornerShape(percent = 50),
            // 근무변경된 날 = 강조색 테두리(v1.6.41). 알약에 모서리 접힘은 곡선 밖으로 삐져나온다.
            // 테두리는 칩 **안쪽**에 그려져 폭 비용 0dp — 옆 칸을 한 픽셀도 안 민다.
            border = if (changed) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
            modifier = Modifier.fillMaxSize(),
            content = {},
        )
        Text(
            shown,
            modifier = Modifier.padding(horizontal = MatrixMetrics.CHIP_PAD_H),
            // 가로 압축만 스타일로 준다(위 scaleX). 나머지 값은 아래 인자들이 그대로 이긴다.
            style = if (scaleX < 1f)
                LocalTextStyle.current.copy(textGeometricTransform = TextGeometricTransform(scaleX = scaleX))
            else LocalTextStyle.current,
            fontSize = size, lineHeight = size * 1.05,
            // Material3 기본 스타일(`bodyLarge`)이 딸려 보내는 0.5sp 자간은 폭 계산에 없는
            // 값이고, 글자가 작아질수록 비중이 커진다(3글자면 1.5sp) → 0으로 못 박는다.
            letterSpacing = 0.sp,
            fontWeight = FontWeight.ExtraBold,
            color = fg,
            maxLines = lines, softWrap = lines > 1,
            overflow = TextOverflow.Visible,
            textAlign = TextAlign.Center,
        )
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
        letterSpacing = 0.sp,
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
 * 이름을 탭했을 때 뜨는 시트 — 전화·문자·★그룹, 그리고 동료 탭에서는 수정·삭제까지.
 * 두 화면이 공유한다(`onEdit`/`onRemove`가 null이면 그 항목이 안 뜬다).
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PersonSheet(
    person: MatrixPerson,
    fav: FavGroup?,
    onSetFav: (FavGroup?) -> Unit,
    onDismiss: () -> Unit,
    onRemove: (() -> Unit)? = null,
    /** 수동 등록한 동료만 — 내장 명단에서 온 사람은 근무가 계산값이라 고칠 게 없다 */
    onEdit: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val duty = com.sinjeong.crewcalendar.presentation.theme.LocalDutyColors.current
    val cleanName = person.cleanName
    val phone = BundledStaff.phoneFor(cleanName, person.group == CrewGroup.MAIN_CONDUCTOR)
    var favMenu by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
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
            if (onEdit != null) {
                OutlinedButton(onClick = { onEdit(); onDismiss() }) { Text("동료 수정") }
            }
            if (onRemove != null) {
                // 바로 지우지 않는다 — ★ 바로 아래라 오탭이 잦았다(관리자 화면과 같은 확인 다이얼로그)
                TextButton(onClick = { confirmRemove = true }) {
                    Text("동료 삭제", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("$cleanName 삭제") },
            text = { Text("저장한 동료 목록에서 지웁니다. 다시 추가할 수 있습니다.") },
            confirmButton = {
                TextButton(onClick = { confirmRemove = false; onRemove?.invoke(); onDismiss() }) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("취소") } },
        )
    }
}
