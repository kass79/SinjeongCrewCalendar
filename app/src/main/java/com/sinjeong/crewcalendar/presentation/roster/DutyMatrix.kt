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
        /**
         * 칩 사이 간격 — v1.6.23에 1 → 1.5dp(라운드가 커져 붙어 보였다), **v1.6.60에 1.5 → 1.0dp**.
         *
         * 이 값도 [CHIP_PAD_H]와 똑같이 표 전체 글자 크기를 깎는다(좌우 2배로 빠진다).
         * 되돌린 근거: 옆 칸과의 실제 거리는 **간격 2dp + 알약 곡선이 물러난 폭**이고, 열마다
         * 세로 밴드(`columnBand`)가 깔려 있어 1dp 줄여도 칸 경계가 흐려지지 않는다(실화면 확인).
         * 대신 글자가 9.68 → **9.97sp**가 됐다(v1.6.61의 `cellW` 37dp에서는 10.26sp).
         * ⚠ 0.75dp 밑으로는 내리지 말 것 — 그 아래는 이웃 칸 글자끼리 3dp도 안 떨어진다.
         */
        val CELL_GAP = 1.dp
        /**
         * 칩 안쪽 좌우 여백 — **0dp**(v1.6.60에서 1 → 0).
         *
         * 표 전체 글자 크기가 `(cellW − CELL_GAP×2 − CHIP_PAD_H×2) × 0.95 ÷ UNIFORM_UNITS` 하나로
         * 정해지므로([UNIFORM_UNITS]), 여기 1dp는 **좌우 2dp = 글자 6.5%**를 그냥 먹고 있었다
         * (사용자: *"동료탭에 텍스트가 최적화 된거 맞아? 조금만 더 키울수는 없을까?"*).
         *  · 없애도 **글자는 알약 밖으로 안 나간다**: 35dp 폭 알약(높이 30dp, 반지름 15dp)은 글자가
         *    놓이는 세로 중앙 밴드(±3.7dp)에서도 폭이 34.08dp인데, 가장 넓은 라벨 `지대11`이 쓰는 폭은
         *    `35 × 0.95` = **33.25dp**로 그 안이다. 실제 숫자 글리프는 폭 모델(0.62em)보다 좁아
         *    (0.568em) 32.2dp라 더 여유가 있다.
         *  · 애초에 글자는 알약 `Surface` **위에 겹쳐** 그려 잘리지 않고([DutyChip] 주석),
         *    칸 사이엔 [CELL_GAP]이 따로 있어 옆 칸과 붙지도 않는다.
         * 다시 1dp로 올리면 표 전체가 10.26 → 9.68sp로 작아진다 — 올리기 전에 그 값부터 계산할 것.
         */
        val CHIP_PAD_H = 0.dp

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
         * ⚠ **`cellW`·`nameW`는 한 몸이다** — 이름칸을 뺀 나머지를 칸 폭으로 나눈 **내림**이
         * 곧 한 화면에 들어오는 날짜 수이므로, 이름칸에서 1dp를 줄이면 날짜 칸이 1/9dp씩 넓어진다.
         * **v1.6.61에 `nameW` 68 → 56dp · `cellW` 36 → 37dp.**
         *
         * `nameW`를 줄인 근거(v1.6.60의 "못 줄인다"를 사용자가 뒤집었다 —
         * *"남궁용권 은 특이한 1명 이라 그것만 줄여주면 되지.."*):
         * [com.sinjeong.crewcalendar.domain.model.BundledRoster] 전수 조사 결과 **262명 중 네 글자
         * 이름은 `남궁용권` 하나**고 나머지는 전부 세 글자(두 글자 `이슬` 1명). 즉 폭 기준을
         * 네 글자에 맞추는 것은 **한 사람 때문에 261명의 격자 글자를 깎는 일**이었다.
         * → 기준을 **`세 글자 + 배지`가 12sp로 온전히 들어가는 폭**으로 다시 잡았다:
         * 여백 2×2 + 이름 36(12sp × 3) + 배지 13.7 = 53.7 → **56dp**(★ 몫 2.3dp 여유).
         * 네 글자·`(나)`·★가 겹치는 줄은 [AutoFitText]가 **말줄임 없이 줄여서** 다 보여 준다
         * (실측: `남궁용권 관D` 9.34sp · `강민성 (나)` 11.04sp — 둘 다 하한 7.44sp 위).
         *
         * 이름 열 내역을 3dp씩 더 회수했다: 좌우 여백 3 → **2dp**(−2dp), ★ 8.5 → **7.0sp**(−1.5dp).
         * 배지는 안 줄였다 — `운A`/`관D`는 부서와 조를 나르는 **정보**라 8.5sp를 지킨다.
         *
         * `cellW` 37dp를 고른 근거 — **날짜 수는 세 기기 폭 모두에서 무변경**이고, 되레 여유가 늘었다:
         *   · 360dp: (360−56) ÷ 37 = 8.22 → **8일**(종전 8.11). 남는 폭 8dp
         *   · 393dp대: (393−56) ÷ 37 = 9.11 → **9일**(종전 9.03). 남는 폭 4dp — 종전엔 **1dp**였다
         *   · 411dp: (411−56) ÷ 37 = 9.59 → **9일**(종전 9.53). 남는 폭 22dp
         * 37.4dp까지 갈 수는 있지만 393dp에서 남는 폭이 0.4dp로 떨어져 px 반올림 한 번에 8일이 된다
         * (v1.6.60이 36.5dp를 거부한 것과 같은 이유). **날짜 수를 줄이는 건 이 화면의 존재 이유
         * (남과 날짜를 비교)를 깎는 일이라 사용자 확인 없이 키우지 말 것**(v1.6.38에서 44 → 36dp로
         * 내린 이유 그대로). 참고로 날짜를 하나 포기하면 360dp에서 37 → 43.4dp(7일)까지 갈 수 있다.
         *
         * 글자 크기 이력: [CHIP_PAD_H] 1 → 0dp · [CELL_GAP] 1.5 → 1dp(v1.6.60, 9.09 → 9.97sp),
         * 여기에 `cellW` 36 → 37dp(v1.6.61) → 가용폭 34.00 → **35.00** → **10.26sp**.
         * ⚠ 이름칸은 격자 글자의 **약한 레버**다 — 393dp에서 `nameW` 1dp = 격자 0.033sp뿐이라
         * 12dp를 통째로 회수해도 +3.0%다(v1.6.60의 여백 회수는 같은 노력으로 +9.7%였다).
         * 더 깎아 봐야 이름만 작아지므로 **56dp 밑으로는 내리지 말 것.**
         *
         * ⚠ **`codeSp`는 v1.6.51부터 실제 글자 크기가 아니라 상한일 뿐이다.** 진짜 크기는
         * [DutyChip]이 `가용폭 ÷ UNIFORM_UNITS`로 정한다 — 37dp 칸에서 **10.26sp**(배율 무관,
         * v1.6.52에서 7.36 → 9.09, v1.6.60에서 → 9.97, v1.6.61에서 → 10.26). 나올 수 있는 라벨 전부가 한 크기로
         * 들어가려면 가장 넓은 `지대11`(3.24 units)이 기준이 돼야 해서다. 13sp는 3글자 코드까지만
         * 재던 옛 값이고, 이제는 칸을 넓혔을 때만 의미를 갖는다. 여기를 낮춰 크기를 조절하려 들면
         * 배율이 낮은 기기에서만 글자가 더 작아져 **칸마다 크기가 갈린다** — 손대지 말 것.
         */
        /**
         * ⚠ **행 높이(`cellH` + `rowPadV`×2)는 "한 화면에 몇 명"을 정한다** — 날짜 수(가로)와
         * 완전히 다른 축이라 서로 영향을 주지 않는다. v1.6.60에 사용자 요청으로
         * 32 → **34dp**(`cellH` 28 → 30): *"한 화면에 18명정도 … 16명 정도로 나오게"*.
         * 34dp를 고른 이유는 **기기 두 종에서 다 16명**이기 때문이다(목록 높이 = 종전 행수 × 32dp):
         *  · 사용자 기기(18명 → 576dp): 576 ÷ 34 = 16.9 → **16명**
         *  · 검증 에뮬(17명 → 544dp): 544 ÷ 34 = 16.0 → **16명**
         * 36dp로 하면 에뮬에서 15명까지 떨어진다(하한 15명 지시에 걸린다).
         *
         * 행을 높여도 **글자는 안 커진다** — [DutyChip]의 크기 식에 높이 항이 아예 없다
         * (`min(codeSp, 가용폭 ÷ UNIFORM_UNITS)`, 둘 다 가로 값). 높이는 두 줄 라벨이
         * 들어가는지만 좌우한다: 2줄 × 1.05 × 10.26 = 21.5dp ≤ 30dp로 여전히 여유가 있다.
         */
        val Roomy = MatrixMetrics(
            cellW = 37.dp, nameW = 56.dp,
            codeSp = 13.sp, nameSp = 12.sp, daySp = 10.5.sp, dowSp = 8.5.sp,
            cellH = 30.dp, rowPadV = 2.dp,
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
 * **동료 탭 전용** 칩 라벨 — 지선 주간·야간 다이아(v1.6.48)와 휴일(v1.6.53)에 `지`를 되살린다.
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
 * 걸리는 건 지선 **주간(지1~지8)·야간(지10~지14)·휴일(지휴1~지휴7·지휴)** 이다. 지선 대기는
 * [DutyCode.display]가 이미 `지대1`로 살려 두고(v1.6.36), 충당 계열 아랫줄은 [DutyCode.diaRaw]라
 * 이미 `지2`로 온다.
 *
 * 폭: 가장 긴 `지휴5`가 [DutyChip] 글자폭 모델로 2.62 units — 표 전체 기준인
 * [UNIFORM_UNITS](3.24)보다 좁아 37dp 칸에 한 줄로 넉넉히 들어간다(`지14`는 2.24).
 *
 * ### v1.6.53 — 지선 휴일도 `지`를 되살린다
 *
 * v1.6.48이 되살린 대상이 주간·야간뿐이라 `지휴5`가 [DutyCode.display]의 `raw.removePrefix("지")`로
 * 떨어져 본선 `휴5`와 **글자가 같았다**(`지휴`↔`휴무`도 둘 다 `휴`). 색도 같은 회색이라
 * (`dutyCellColors`의 REST·BRANCH_REST) 지선 휴일인지 본선 휴일인지 구분할 길이 없었다 —
 * v1.6.52 전수 조사에서 "미수정 충돌 2종"으로 남겨 뒀던 건이다.
 *
 * 상한이 여전히 `지대11`(3.24)이라 **표 전체 글자 크기 9.09sp는 그대로다** — 크기 손해 0.
 *
 * ### v1.6.52 — 네 글자 라벨을 두 글자로 줄여 **표 전체 글자를 키웠다**
 *
 * 표는 크기가 하나뿐이고(v1.6.51) 그 크기는 **가장 넓은 라벨 하나**가 정한다. 그래서
 * `돌봄휴가`·`동행휴가`·`대기충당`(4.00 units) 셋이 나머지 572종을 전부 7.36sp로 끌어내리고
 * 있었다(사용자: *"텍스트 크기가 너무 작아진거 같기도 하고..?"*). 셋만 [MATE_SHORT]로 줄이니
 * 상한이 `지대11`(3.24)로 내려가 **9.09sp(+23%)** 가 됐다.
 * 색이 이미 휴가라고 말해 주므로 `휴가` 두 글자는 격자에서 정보가 아니다.
 */
private val DutyCode.mateLabel: String
    get() = when {
        // 충당 계열 두 줄 중 **윗줄만** 줄인다. 아랫줄(다이아)이 실질 정보라 그대로 둔다.
        fill != null -> "${MATE_SHORT[fill] ?: fill}\n$diaRaw"
        type == DutyType.BRANCH || type == DutyType.BRANCH_NIGHT ||
            type == DutyType.BRANCH_REST -> raw
        // [DutyCode.gridLabel]은 fill이 없으면 곧 display다 — 그 자리에 축약만 끼운다
        else -> MATE_SHORT[display] ?: display
    }

/**
 * **동료 탭 격자에서만** 쓰는 축약표 — 표 전체 글자 크기를 잡고 있던 네 글자 라벨을 두 글자로.
 *
 * | 저장값 | 달력·상세시트·위젯·공유이미지 | **동료 탭 격자** |
 * |---|---|---|
 * | `돌봄휴가` | `돌봄휴가` (그대로) | `돌봄` |
 * | `동행휴가` | `동행휴가` (그대로) | `동행` |
 * | `대기충당 지2` | `대기충당`⏎`지2` | `대기`⏎`지2` |
 *
 * ⚠ `대충`이 아니라 `대기`다 — `대충`은 한국어로 "대충 하다"로 읽힌다.
 * 대기 근무(`대1`·`대2`·`대11`)와는 **숫자 유무**로 갈리고, 충당 계열은 늘 아랫줄에 다이아가
 * 붙는 두 줄이라 문맥도 다르다.
 *
 * ⚠ 여기 항목을 늘릴 땐 `PatternTest.mateGrid_labels_do_not_collide`가 **다른 근무와 같은 글자로
 * 보이는 축약**을 잡는다. 줄인 뒤 [UNIFORM_UNITS]도 다시 재야 한다(같은 파일 전수 조사 테스트).
 */
private val MATE_SHORT = mapOf("돌봄휴가" to "돌봄", "동행휴가" to "동행", "대기충당" to "대기")

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
            // 좌우 여백 3 → 2dp(v1.6.61). 이름 열을 68 → 56dp로 좁히면서 되찾은 2dp는
            // 그대로 이름 몫이 된다 — 글자와 칸 경계선 사이는 2dp면 붙어 보이지 않는다(실화면 확인).
            Modifier.width(m.nameW).clickable { onNameClick() }.padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 말줄임 대신 자동 축소 — `강민성 (나)`도 `남궁용권 관D`도 잘리지 않고 작아지기만 한다.
            // v1.6.61에 이 장치가 **폭 기준 그 자체**가 됐다: 이름 열은 세 글자 이름에 맞춰 좁히고
            // (262명 중 261명이 세 글자 이하), 예외인 네 글자·`(나)` 줄은 여기서 흡수한다.
            AutoFitText(
                p.name,
                base = m.nameSp, min = m.nameSp * 0.62f,
                color = if (p.isMe) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            // 4조2교대만 붙는 부서+조 배지 `운A`·`관D` (v1.6.54, v1.6.60에도 그대로 유지).
            // 이름과 **따로** 그린다 — 이름 문자열에 이어 붙이면 `네글자이름 관D`가 한 덩어리로
            // 자동축소돼 이름까지 작아진다. 8.5sp 고정이라 56dp 이름 열에서 이름 몫은
            // 38.3dp가 남는다 — 세 글자 이름이 12sp 그대로 들어가는 폭이다(v1.6.61).
            // 부서(`운`/`관`)는 소속이 아니라 **이름**으로 정해진다(v1.6.60, `teamBadge`) — 필터 칩은
            // `4조2교대` 하나로 합쳤지만 사람별 배지는 그대로다.
            // ⚠ 격자 칸(`DutyChip`)이 아니라 이름 열이므로 표 단일 크기(10.26sp)와 무관하다.
            // ⚠ **배지는 v1.6.61에도 8.5sp 그대로다.** 이름 열을 좁히면서 여백과 ★은 깎았지만
            //   `운A`/`관D`는 부서와 조를 나르는 정보라 줄이면 읽기가 나빠진다(★은 장식이다).
            teamBadge(p.group, p.offset, p.cleanName)?.let {
                Text(
                    it, fontSize = m.dowSp, fontWeight = FontWeight.Bold,
                    maxLines = 1, softWrap = false,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // ★ 8.5 → **7.0sp**(v1.6.61). 획이 굵은 채움 글리프라 작아져도 그대로 보이고,
            // 되찾은 1.5dp가 `세 글자 + 배지 + ★` 줄의 이름을 9.34 → 10.16sp로 되살린다
            // (격자 글자 10.26sp보다 이름이 작아 보이는 역전을 막는 선).
            if (isFav) Text("★", fontSize = m.dowSp * 0.82f, color = duty.onStandby)
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
 * 표 전체가 쓰는 **단 하나의 글자 폭 기준** — 이 격자에 나올 수 있는 **모든** 라벨의 줄별 최대 폭.
 *
 * 전수 조사(`Bundled.ALL_PATTERNS` 4종 시퀀스 + [DutyCode.CHANGE_OPTIONS] + 퇴역값 `작연차`·`비번`
 * + 충당 계열 4종 × 고를 수 있는 다이아 전부 → `parse` → [DutyCode.mateLabel])로 얻은
 * 라벨 575종의 **줄별 최대 폭이 정확히 3.24 units**다(=`지대11`):
 *  · 한 줄 `지대11` = 3.24, 두 줄 `충당`⏎`지대11` 계열의 **아랫줄** = 3.24
 *  · 그 다음은 `가연차`·`작연차` 3.00 → `지대1`·`지대2` 2.62 → `지14`·`휴28`·`대13` 2.24
 *
 * v1.6.51까지는 `돌봄휴가`·`동행휴가`·`대기충당`(4.00) 셋이 상한이었고 그 셋 때문에 표 전체가
 * 7.36sp였다. v1.6.52에서 셋을 [MATE_SHORT]로 두 글자로 줄여 상한을 3.24로 내렸다.
 * **여기서 더 깎을 것은 없다** — `지대11`은 지선 대기 11번을 본선 `대11`과 구분하는 최소 표기고
 * (v1.6.36 사용자 요청), 한 글자만 더 빼도 다른 근무와 같은 글자가 된다.
 *
 * 이 값 하나로 표 전체 글자 크기가 정해진다: 37dp 칸의 가용폭 33.25 ÷ 3.24 = **10.26sp**
 * (v1.6.60에서 [MatrixMetrics.CHIP_PAD_H] 1 → 0dp, [MatrixMetrics.CELL_GAP] 1.5 → 1dp로
 * 칸 안 여백을 5 → 2dp까지 회수했고, v1.6.61이 이름 열 68 → 56dp를 칸 폭에 넘겨 37dp가 됐다:
 * 한 줄이든 두 줄이든, 글자배율이 얼마든 **모든 칸이 이 한 크기**다([DutyChip] 주석).
 */
private const val UNIFORM_UNITS = 3.24f

/**
 * 근무 코드 칩 — 표 전체가 **같은 크기의 알약**이고 글자는 그 안에 세로 가운데 정렬된다(v1.6.23).
 *
 * 종전 문제("텍스트가 최적화 안 되어 있다"):
 *  1. 칩 높이가 글자 높이를 따라가서 줄어든 칸만 칩이 짧고 위로 붙어 보였다.
 *  2. `AutoFitText`가 칸마다 **따로** 측정해 줄여서 같은 표 안에 서너 가지 글자 크기가 섞였다.
 *     게다가 한 번 줄면 되돌아오지 않아(`fit`은 감소만 한다) LazyColumn이 행을 재활용하거나
 *     가로 스크롤 중 폭 0으로 한 번 측정되면 그 칸만 영구히 작아졌다 — 재현이 들쭉날쭉했던 이유.
 *
 * 그래서 **측정 대신 계산**한다. 글자 폭을 한글 1em · 숫자/기호 0.62em으로 근사하고,
 * **가장 넓은 라벨 하나**([UNIFORM_UNITS])가 칸에 들어가는 크기를 표 전체에 못 박는다.
 * 칸마다 재지도, 접지도, 눌러 넣지도 않는다 — **크기는 하나뿐**이다(37dp·배율 무관 10.26sp).
 *
 * ⚠ **여기서 크기를 두 종류로 되돌리는 처방은 이미 두 번 거부됐다. 다시 꺼내지 말 것.**
 *  · **v1.6.49 `scaleX`**: 안 들어가는 소수 라벨(`지대1`·`지대11`·`가연차` …)을
 *    [TextGeometricTransform]으로 **가로만 눌러** 높이를 13sp에 맞췄다 → 사용자:
 *    *"지대11도 지1이랑 같은 크기로 보입니다 ← 같은 크기로 안보이는데?"*
 *    높이가 같아도 **가로로 눌린 글자는 눈에 작고 찌그러져 보인다** — 사람은 글자를 높이가 아니라
 *    획 굵기·자간·전체 덩어리로 읽는다.
 *  · **v1.6.50 두 줄 접기**: `지대`⏎`11`로 접고 한 줄 13sp / 두 줄 9.75sp를 줬다 → 여전히 불만.
 *    **크기가 두 종류면 그 자체로 "같은 크기"가 아니다.** 접는 것이 문제가 아니라 크기가 갈린 것이 문제였다.
 *
 * → **v1.6.51 확정**: 크기를 딱 하나로 통일한다. 그 대가로 표 전체가 13 → 7.36sp로 작아지는 것은
 * 사용자가 받아들였다(*"그냥 텍스트 크기를 전체적으로 같은크기로 해주면 안돼? 쫌 작게 하면 돼지?"*).
 * **"작아지니 안 된다"고 판단해 두 크기를 다시 만들지 말 것.**
 *
 * → **v1.6.52**: 크기가 작다는 불만은 **크기를 갈라서가 아니라 라벨을 줄여서** 푼다.
 * 표의 단일 크기는 가장 넓은 라벨이 정하므로, 그 라벨([MATE_SHORT])만 짧게 하면 **하나짜리
 * 크기가 통째로 올라간다** — 7.36 → **9.09sp(+23%)**. 예외 칸은 여전히 0이다.
 *
 * → **v1.6.60**: *"칸을 벗어나지않게 … 몇단계 더 키울수 있어?"*. 레버는 넷인데 셋은 막혀 있었다 —
 * 칸 폭(실질 여유 0.11dp, 더 키우면 날짜가 준다), 이름 열(이미 최장 이름에 모자라다),
 * 폭 모델의 0.95 여유(글꼴이 바뀌면 잘리는 자리라 그대로). 넷째인 **칸 안의 여백**만 실제 여유였다:
 * [MatrixMetrics.CHIP_PAD_H] 1 → 0dp(+6.5%) · [MatrixMetrics.CELL_GAP] 1.5 → 1dp(+3.0%)로
 * 가용폭이 29.45 → 32.30이 되어 **9.09 → 9.97sp(+9.7%)**. 크기는 여전히 딱 하나다.
 *
 * → **v1.6.61**: 막혀 있던 셋 중 **이름 열**이 열렸다. 사용자가 v1.6.60의 판단을 뒤집었다 —
 * *"남궁용권 은 특이한 1명 이라 그것만 줄여주면 되지.."*. 262명 중 네 글자 이름이 그 한 명뿐이라
 * 폭 기준을 `세 글자 + 배지`로 다시 잡아 [MatrixMetrics.nameW] 68 → 56dp, 회수한 12dp를
 * 그대로 칸 폭에 넘겨 [MatrixMetrics.cellW] 36 → 37dp → 가용폭 32.30 → **33.25**,
 * **9.97 → 10.26sp(+3.0%)**. 이름 열은 약한 레버라 12dp가 3%밖에 안 된다 —
 * 남은 두 레버(0.95 여유·[UNIFORM_UNITS])는 그대로다. 근거는 [MatrixMetrics.Roomy] 주석에 전부 있다.
 *
 * 접는 라벨은 이제 없다. 줄바꿈이 들어오는 건 [DutyCode.mateLabel]이 이미 접어 보내는
 * 충당 계열(`대기`⏎`지2`, v1.6.46)뿐이고, **그 두 줄도 같은 10.26sp**다 —
 * 두 줄 어느 쪽도 [UNIFORM_UNITS]를 안 넘어 한 줄 라벨과 폭 상한이 같아 예외가 필요 없다.
 * 높이도 여유다: 2줄 × 1.05 × 10.26 = 21.5dp 상당 ≤ 칸 [MatrixMetrics.cellH] 30dp.
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
    // 줄바꿈이 들어오는 건 충당 계열(`대기`⏎`지2`, DutyCode.mateLabel)뿐이다.
    // **여기서 접는 라벨은 없다** — 접기는 곧 크기 두 종류를 낳았고 v1.6.50에서 거부됐다.
    val lines = if ('\n' in text) 2 else 1
    // **표 전체가 딱 한 크기다.** 칸의 텍스트가 아니라 [UNIFORM_UNITS] 상수 하나로만 정해지므로
    // 이 식에는 칸마다 달라지는 값이 **아예 들어오지 않는다** — 그게 "모든 칸이 같은 크기"의 증명이다.
    // 글자배율(fontScale)은 basePx·availPx 양쪽에 같이 곱해지지 않는다: availPx는 dp에서 온
    // 고정 폭이고 basePx만 배율을 탄다. 37dp 칸에서는 안드로이드 최소 배율 0.85에서도
    // basePx(11.05dp 상당)가 오른쪽 항(10.26)보다 커서 **항상 오른쪽이 이긴다**
    // → 배율 1.0이든 1.5든 전 칸이 같은 10.26sp다(뒤집히는 지점은 배율 0.789 = 10.26 ÷ 13)
    // (v1.6.49의 "야간만 작다"는 배율이 오를 때 긴 라벨만 오른쪽 항으로 넘어가서 생긴 일이다).
    // basePx는 칸을 넓혔을 때의 상한으로만 남는다.
    val sizePx = minOf(basePx, availPx / UNIFORM_UNITS)
    // px → sp도 **선형 역산**이다. `Float.toSp()`를 쓰면 다시 비선형 표를 타서 어긋난다.
    val size = (sizePx / spToPx).sp
    // 마지막 넘침 방지 — 예상 못 한 긴 문자열(손으로 넣은 근무변경 등)이 옆 칸을 뚫지 않게.
    // **전수 조사한 575종에는 절대 안 걸린다**: 줄별 최대 폭이 [UNIFORM_UNITS] = 3.24이고
    // sizePx가 이미 `availPx / 3.24` 이하라 곱이 availPx를 넘을 수 없다(비 = 정확히 1.0).
    // 0.99 문턱은 그 등호 지점에서 float 반올림으로 1.0을 아슬하게 밑돌아 **멀쩡한 라벨이
    // 눌리는** 것을 막는다 — 눌린 글자는 크기가 달라 보인다(v1.6.49가 그래서 거부됐다).
    // 폭 계산은 여기까지 전부 px 한 단위다 — 비율이라 sp↔dp 환산이 끼어들 자리가 없다.
    val maxLineUnits = text.split('\n').maxOf(::widthUnits)
    val scaleX = (availPx / (maxLineUnits * sizePx)).coerceAtMost(1f)
    // ⚠ **글자는 알약 `Surface` 안이 아니라 위에 겹쳐 그린다**(v1.6.42 ⑥).
    // `Surface`는 shape로 내용을 잘라내는데 알약은 위아래 가장자리에서 폭이 급격히 좁아진다 —
    // 두 줄짜리 코드(`대기충당`⏎`지2`)는 바깥 줄이 그 곡선에 걸려 잘렸다(fs 1.3 실측: 가장자리에서
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
            text,
            modifier = Modifier.padding(horizontal = MatrixMetrics.CHIP_PAD_H),
            // 가로 압축만 스타일로 준다(위 scaleX). 나머지 값은 아래 인자들이 그대로 이긴다.
            style = if (scaleX < 0.99f)
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
            // 4조2교대면 부서·조까지 풀어 적는다 — 격자 배지 `관D`가 무슨 뜻인지 여기서 확인한다.
            // 공식 문서 표기를 따라 `반`이 아니라 `조`(`ShiftTeam.label`), `기지`가 아니라 `관제`.
            Text(
                person.group.label +
                    (ShiftTeam.ofOffset(person.offset)?.label
                        ?.takeIf { person.group == CrewGroup.SHIFT_4_2 }
                        ?.let { " ${if (BundledRoster.isControl(cleanName)) "관제" else "운용"} $it" }
                        ?: ""),
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
