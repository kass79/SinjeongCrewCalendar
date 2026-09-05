package com.sinjeong.crewcalendar.presentation.live

import android.content.Context
import android.util.Log
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sinjeong.crewcalendar.domain.model.DutyCode
import com.sinjeong.crewcalendar.domain.model.DutyType
import com.sinjeong.crewcalendar.domain.model.Line2Timetable
import com.sinjeong.crewcalendar.domain.model.LiveRef
import com.sinjeong.crewcalendar.domain.model.dutyTrainNumbers
import com.sinjeong.crewcalendar.domain.model.pickRun
import com.sinjeong.crewcalendar.domain.model.sameRun
import com.sinjeong.crewcalendar.presentation.theme.MapStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/*
 * 신정지선 실시간 카드 — v1.6.88 에서 **본선 지도와 같은 "운전실 보조설비" 스타일**로 다시 그렸다.
 * 사용자(기관사) 확정: *"신정지선도 에뮬레이터 디자인도 본선스타일로 해버릴까? 비슷해야
 * 하잖아!"* → 시안 확인 후 *"그렇게 해!"*. 사양은 [MainLineMap] 과 한 벌이다:
 *
 *  1. 남색 바탕(#0E2A47) 고정 — 앱 테마(라이트/다크)와 **무관**하다.
 *  2. 굵은 초록 가로선 하나에 5역을 붙인다. 역 = 흰 점, **열차가 서 있는 역 = 빨간 점**.
 *  3. **신도림·양천구청**은 이름 주황 +2sp 굵게 · 점 1.5배 + 흰 테두리 — 본선의 신도림·성수와
 *     같은 규칙이다([KEY_STATIONS]. 양천구청은 v1.6.97 — 지선 편승역이라 사용자가 지목했다).
 *  4. **아래가 신도림행(주 선로) · 위가 까치산행**(v1.6.95 복선. 아래 절을 보라) — 본선이
 *     내선을 선 안쪽, 외선을 바깥쪽에 두는 것과 같은 발상으로 **방향을 자리로 말한다**.
 *  5. **내 열차**는 흰 테두리 + 빨간 열번 + 지붕 위 행선판, 그리고 **맨 나중에** 그린다
 *     (몸통 노랑은 v1.6.96 부터 신도림행 **전부**가 쓰므로 구분 표시가 아니다).
 *  6. v1.6.91 — **영업 열차는 전부 증기기관차**([drawLoco]). 모양 = 열차 / 머리 = 진행 방향 /
 *     색 = 신분(내 열차 노랑 · 일반 하늘 · 기지 회송 회색). **열번은 늘 몸통 안**이고,
 *     행선은 **지붕 위 행선판**이다(깃발처럼 따로 떠다니지 않는다).
 *
 * ## v1.6.43~87 에서 넘어온, 손대지 않은 것
 *
 * 이 파일은 편승지키미(kass79/SinjeongShuttle2) `ui/LineMap.kt` 이식본이었다. **그림만** 갈아
 * 끼웠고 로직은 그대로다 — 폴링 눈금(2초·절대시각) · 회차 기억 · 위치 보간(등속) · 후진 금지 ·
 * 입고 열차/기지 회송 · 정차 판정([holding]). 아래 KDoc 에 남은 실측 근거들이 그 증거다.
 *
 * 종전 그림에서 **빠진 것**(사용자가 시안에서 확인한 대로):
 *  · 건넘선(까치산 단선·도림천 X) — 안 그린다.
 *  · 진행 셰브런·셔머·`↻ 회차` 배지 — 회차 상태는 내 열차 한 줄(헤더 밑)이 말한다.
 *
 * ## v1.6.95 — **복선**(사용자 확정)
 *
 * v1.6.94 까지는 선이 **한 줄**이고 차선만 위·아래로 갈렸다. 그래서 아래 차선(까치산행)
 * 기관차는 밑에 아무 선도 없이 **허공에 떠** 보였다 — 사용자: *"까치산행 선로가 없으니 뭔가
 * 좀 이상하다"*. 이제 두 선로를 긋고 **기관차는 늘 제 선로 위에 바퀴를 붙인다**:
 *
 * | | 선로 | 굵기·색 | 기관차 |
 * |---|---|---|---|
 * | 위 | 까치산행 | [UP_LINE_H] · `railSoft` (연하고 가늘게) | [UP_LOCO_K] 배 · `softBody` |
 * | 아래 | **신도림행(주)** | [LINE_H] · `rail` (종전 그대로) | 1배 · **`mineBody`**(v1.6.96) |
 *
 * 역 점은 두 선로에 각각 찍힌다(위 선로 점은 작게). 정차(빨간 점)는 그 열차가 달리는
 * **제 선로의 점**에만 든다. 두 차선 모두 기관차가 선로 위로 쌓이므로 계단식 회피(`r = 1`)는
 * **양쪽 다 위로** 물러난다.
 *
 * ## v1.6.96 — 신도림행은 **노랑**, 역 이름은 **아래 선로 밑**(사용자 확정)
 *
 * > *"신정지선도 신도림행이 중요하니까 역이름을 신도림행 쪽으로 넣고,, 신도림행으로 가는
 * > 열차를 본선과 마찬가지로 트렌디한 노란색으로 해줘!"*
 *
 *  · 세로 배치가 `까치산행 차선 → 위 선로 → 신도림행 차선 → **아래 선로 → 역 이름**` 이 됐다
 *    (v1.6.95 는 이름이 두 선로 **사이**였다). 카드 높이는 그대로 — 순서만 바뀌었다.
 *  · **신도림행 열차는 전부 `mineBody` 몸통 + `otherInk` 남색 열번.** 색이 곧 방향이다.
 *  · **내 열차 구분은 몸통색이 아니다** — 흰 테두리(`highlight`) + 지붕 위 행선판 +
 *    **`mineInk` 빨간 열번**, 그리고 맨 나중에 그린다. 몸통색으로 갈랐다가는 사용자가 원한
 *    *"신도림행은 노랑"* 이 깨진다.
 *  · 까치산행(`softBody`)·기지 회송(`depot`)은 종전 그대로 — 위계가 살아 있다.
 */

/*
 * ── 색: [MainLineMap] 과 **한 벌**(v1.7.0 [MapPalette]) ────────────────────────
 *
 * 색 상수는 `MapStyle.kt` 한 곳으로 옮겼다 — 설정에서 고른 스타일(운전실 남색 / 클레이)에
 * 따라 통째로 갈아 끼운다. 본선 지도와 **같은 팔레트**를 받으므로 한쪽만 클레이가 되는 일이
 * 없다. `CAB_PALETTE` 값은 v1.6.99 의 상수와 같다(`MapStyleTest` 가 잠근다).
 *
 * 종전 이름 → 팔레트 자리:
 *   `CabNavy`→`bg`(바퀴색도 이것) · `LoopGreen`→`rail` · `LoopGreenSoft`→`railSoft` ·
 *   `StationWhite`→`station` · `StationRed`→`stationRed` · `BadgeSky`→`otherBody` ·
 *   `BadgeSkySoft`→`softBody` · `BadgeInk`→`otherInk` · `MineYellow`→`mineBody` ·
 *   `MineInk`→`mineInk` · `DepotGray`→`depot` · `KeyOrange`→`keyA` · `Dim`→`dim`.
 *
 * ⚠ **남색 바탕에서 "연하게"는 배경 쪽으로 섞는 것**이다(v1.6.95 `BadgeSkySoft` 의 교훈) —
 * 흰 쪽으로 가면 도리어 튀어 주 차선을 이긴다. 클레이는 반대다(바탕이 크림이라 흰 쪽이 물러난다).
 */

/**
 * 지선에서 강조하는 역 — 본선 쪽 `KEY_STATIONS` 와 같은 규칙(주황 이름 +2sp · 큰 흰 테 점).
 *
 * **신도림**은 본선이 갈라지는 자리고, **양천구청**은 v1.6.97 에서 더했다 — 사용자 확정
 * *"신정지선에 양천구청역이 중요하니까 트렌디한 색으로"*. 지선 근무의 **편승역**이라
 * 승무원이 이 카드에서 가장 먼저 찾는 역이다. 나머지 세 역은 흰색 그대로다.
 */
private val KEY_STATIONS = setOf("신도림", "양천구청")

/** 지선 열차를 **실제로 잡는** 근무 — 운휴(`지휴`)·대기(`지대`)는 여기 없다. */
private val DRIVING_BRANCH = setOf(DutyType.BRANCH, DutyType.BRANCH_NIGHT)

/**
 * 카드 한 단계 **축소**(v1.6.98) — 사용자: *"신정지선 에큘레이터 조금 더 작게 해줘서
 * 최적화 되게해!"* 카드 높이가 약 **15% 준다**(폰 166.5 → 141dp · 펼침 191 → 162dp).
 *
 * 한 손잡이가 **선로 두께·기관차 배율·역명 글자·차선 여백**에 다 같이 먹어야 비율이 안
 * 깨진다 — 하나만 줄이면 기관차가 선로를 밟거나 역 이름이 카드 밖으로 나간다.
 *
 * ⚠ **하한은 열번 판독**이다. 열번은 `11sp × 배수`([drawLoco])이고 위 차선은 [UP_LOCO_K]
 * 가 한 번 더 곱해져 폰 기준 `11 × 0.85 × 0.85 = 7.9sp` — 본선에서 확인한 판독 하한
 * 7.7sp 를 겨우 넘는다. **여기서 더 줄이지 말 것**(줄이려면 [drawLoco] 의 11 을 올려야 한다).
 */
private const val CARD_K = 0.85f

/** **아래 선로(신도림행 = 주 선로)** 두께 — 역 흰 점이 선 위에 살짝 얹혀 보이는 굵기다. */
private val LINE_H = 5.dp

/** **위 선로(까치산행)** 두께 — 주 선로보다 한 단계 가늘다(v1.6.95 사용자 확정). */
private val UP_LINE_H = 3.dp

/**
 * 위 차선(까치산행) 기관차 크기 배수 — 아래(신도림행)가 주 차선이라 **0.85배**로 위계를 준다
 * (v1.6.95 사용자 확정). 차선 높이는 큰 쪽(아래) 기준 하나로 잡고 그림만 줄인다.
 */
/**
 * ↻ **연타 방지 시간**(v1.7.7 A9). 강제 조회 한 번 = API 2회라 손가락 속도로 한도를
 * 태울 수 있었다. 폴링 눈금(4초)보다 짧으면 막는 의미가 없고, 길면 진짜 다시 받고
 * 싶을 때 답답하다 — **3초**가 그 사이다.
 */
private const val REFRESH_COOLDOWN_MS = 3_000L

private const val UP_LOCO_K = 0.85f

private const val DEPOT_RUN_END = 2.5f     // 도림천 지나 중간쯤에서 기지 진입(배지 소멸)
private const val DEPOT_RUN_SEC = 150f     // 신도림→기지 진입 소요 가정
private const val TAG = "BranchLive"
/** 회차 기억 저장 키 — 값 형식은 [BranchLive.turnMemory] */
private const val TURN_KEY = "turn_memory"

/**
 * 영업시간: 05:30~24:00 + 익일 0~1시(전날 영업 연장). 빈 상태 문구를 고르는 데만 쓴다.
 * 본선 지도([MainLineMap])도 같은 판정을 쓴다 — 두 지도가 다른 시각에 "운행 종료"라고 말하면 안 된다.
 */
internal fun inService(t: LocalTime = LocalTime.now()): Boolean {
    val mins = t.hour * 60 + t.minute
    return mins >= 330 || mins < 60
}

/**
 * 오늘 상세시트 안의 실시간 지선 열차 지도.
 *
 * **생명주기가 이 컴포넌트의 핵심이다.** 여기 있는 코루틴은 전부 컴포지션에 묶여 있어
 * 시트가 닫혀 이 컴포저블이 컴포지션에서 빠지는 순간 함께 취소된다:
 *  · 폴링 [LaunchedEffect] (2초 tick) — 취소되면 [BranchLive.loadSnapshot] 호출이 멎는다
 *  · 시계 [LaunchedEffect] (1초) — 보간 이동·입고 카운트다운의 시간축
 *  · 새로고침 버튼의 [rememberCoroutineScope] 일회성 launch
 * 즉 배터리·API 한도 소모 범위가 **"시트가 열려 있는 동안"** 으로 확실히 갇힌다.
 * (열림/닫힘은 [DisposableEffect]가 logcat `BranchLive` 태그로 남긴다 — 실기기 확인용)
 */
/**
 * @param bleed 상세시트 좌우 여백을 **밖으로 되찾는 폭**(한쪽). 지도만 시트 폭에 가깝게 넓힌다
 *   (v1.6.50 사용자: *"애뮬레이트 가로 길이는 조금 더 늘려줘"*). 역 간격이 그만큼 벌어져
 *   열차·라벨이 겹칠 일이 준다. 여백을 얼마나 내줄 수 있는지는 **부르는 쪽만 안다** —
 *   접힘 시트는 20dp, 펼침 패널은 10dp라 같은 값을 쓰면 패널에서 카드가 벽에 붙는다.
 */
@Composable
internal fun BranchLiveMap(
    bleed: Dp = 0.dp,
    /**
     * 오늘 근무 — 전체 보기(본선 순환선 지도)의 **내 열번** 판정(v1.6.84)과,
     * v1.6.88부터는 이 카드의 **내 열차 노란 배지**에도 쓴다.
     */
    duty: DutyCode? = null,
    date: LocalDate = LocalDate.now(),
    /** 설정 > 화면 > **지도 스타일**(v1.7.0). 색만 정한다 — 배치는 스타일과 무관하다. */
    style: MapStyle = MapStyle.CAB,
    modifier: Modifier = Modifier,
) {
    var snap by remember { mutableStateOf(Snapshot()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val scope = rememberCoroutineScope()

    val ctx = LocalContext.current

    DisposableEffect(Unit) {
        Log.i(TAG, "지도 열림 — 폴링 시작")
        onDispose { Log.i(TAG, "지도 닫힘 — 폴링·애니메이션 취소") }
    }
    LaunchedEffect(Unit) {
        // 회차 기억 복원(v1.6.70 ⑤) — 프로세스가 죽어도 **직전에 본** 회차 열차를 되살린다.
        // 근거와 한계는 [BranchLive.restoreTurnMemory] KDoc. 저장소를 여기서 다루는 이유는
        // [BranchLive]를 안드로이드 클래스에서 자유롭게 둬야 유닛테스트가 그대로 돌기 때문이다.
        val sp = withContext(Dispatchers.IO) {
            ctx.getSharedPreferences("branch_live", Context.MODE_PRIVATE)
        }
        BranchLive.restoreTurnMemory(sp.getString(TURN_KEY, "").orEmpty(), System.currentTimeMillis())
        var saved = ""
        var nextTick = System.currentTimeMillis()
        while (isActive) {
            snap = BranchLive.loadSnapshot()
            // 바뀔 때만 쓴다 — 시트가 열려 있는 동안 4~10초마다 값이 바뀌는 자리다.
            BranchLive.turnMemory().let { if (it != saved) { saved = it; sp.edit().putString(TURN_KEY, it).apply() } }
            /*
             * ⚠ 실제 갱신 주기를 정하는 건 [BranchLive.pollIntervalMs](10초/4초)다. 여기 눈금은
             * 그 주기를 **정확히 집어낼 수 있는 자**여야 하고, 조건이 둘이다. 둘 다 v1.6.72
             * 실측에서 하나씩 걸린 것이라 어느 쪽도 장식이 아니다.
             *
             * 1. 눈금이 두 주기의 **최대공약수** — `gcd(10, 4) = 2`초. 5초 눈금에 4초 주기를 넣으면
             *    첫 눈금이 5초라 실제 간격이 5초로 반올림된다.
             * 2. **눈금은 절대 시각으로 놓는다.** `delay(2_000)`처럼 "지금부터 2초"로 재우면
             *    네트워크에 쓴 시간(실측 0.9~1.2초)과 `delay` 오버슈트(에뮬 실측 눈금당 ~0.17초)가
             *    **누적**된다 — 실측으로 10초 주기가 11.5초(10.4~12.2초)까지 밀렸고,
             *    "지금부터 2초"를 "이번 눈금부터 2초"로만 고쳤을 때도 4초가 4.34초로 남았다.
             *
             * 눈금 사이의 호출은 캐시가 같은 인스턴스를 돌려주므로 리컴포지션도 안 난다(공짜다).
             * 뒤처지면(백그라운드 등) 밀린 눈금을 몰아치지 않고 **자를 다시 놓는다.**
             */
            nextTick += 2_000
            val nowMs = System.currentTimeMillis()
            if (nextTick < nowMs) nextTick = nowMs + 2_000
            delay(nextTick - nowMs)
        }
    }
    LaunchedEffect(Unit) {
        while (isActive) { now = System.currentTimeMillis(); delay(1_000) }
    }

    // v1.6.84 — 지선 지도 카드의 `본선 전체 보기` 칩으로 여는 본선 순환선 지도.
    // 닫으면 그쪽 LaunchedEffect 가 취소돼 폴링이 멎는다(스냅샷 캐시는 공유).
    var showFull by remember { mutableStateOf(false) }
    if (showFull) MainLineMapDialog(duty, date, style) { showFull = false }

    /*
     * 오늘 근무가 잡는 열번 후보 — **지선 열차를 실제로 잡는 근무일 때만** 이 카드가 말한다
     * (사용자 확정: 지선 근무가 아니면 내 열차 줄을 아예 생략).
     *
     * ⚠ [DRIVING_BRANCH] 는 **중복 필터가 아니다.** 운휴(`지휴5`)·대기(`지대2`)를 걸러 내는
     * 일은 v1.6.88에서 [dutyTrainNumbers] 안으로 옮겼지만(같은 함수를 부르는 본선 지도·달력
     * 헤더가 함께 틀렸던 자리), 이 카드는 오늘 근무가 **본선이어도** 그려진다 —
     * 여기를 지우면 본선 주간 근무의 열번이 지선 지도 위에서 내 열차로 잡힌다.
     * ⚠ **시각을 추정하지 않는다** — 후보를 주고, 그중 실제로 API 에 살아 있는 것을 고른다
     * (본선 지도와 같은 규칙 — `MyTrain` KDoc).
     * ⚠ **비번(`지11~`)은 전날 야간의 이어짐**이라 오늘 아침 후반을 지선에서 잡는다(v1.6.95) —
     *   종별 판정은 그 야간 근무로 한다([DutyCode.effectiveNight]). 본선 비번(`38~`)은 그
     *   야간이 본선이라 여기서 그대로 걸러진다.
     */
    val candidates = remember(duty, date) {
        duty?.takeIf { d -> (DutyCode.effectiveNight(d, date)?.first ?: d).type in DRIVING_BRANCH }
            ?.let { dutyTrainNumbers(it, date) }.orEmpty()
    }

    /*
     * 입고 ETA 를 **시간표로 다듬는다**(v1.7.2) — 근사(역 수 × 110초)는 지연을 모른다.
     * 시간표는 자산이라 **API 호출이 안 늘고**, 못 찾으면 근사가 그대로 남는다.
     */
    val tt by produceState<Line2Timetable?>(null) {
        value = withContext(Dispatchers.IO) { Line2TimetableLoader.get(ctx) }
    }
    val inbound = remember(snap.inbound, tt, now / 30_000) {
        val (d, sec) = Line2Timetable.serviceClock(LocalDateTime.now())
        BranchLive.refineInbound(snap.inbound, tt, Line2Timetable.weekTagOf(d), sec)
    }

    LineMapCard(
        onFullMap = { showFull = true },
        trains = snap.trains,
        inbound = inbound,
        candidates = candidates,
        fetchedAtMillis = snap.fetchedAtMillis,
        nowMillis = now,
        error = snap.error,
        onRefresh = { scope.launch { snap = BranchLive.loadSnapshot(force = true) } },
        pal = paletteOf(style),
        modifier = modifier.bleedH(bleed),
    )
}

/**
 * 부모의 좌우 여백을 [amount]만큼 **밖으로 넘어가서** 그린다(음수 padding).
 * 차지하는 자리(부모에게 보고하는 폭)는 그대로라 위아래 형제가 밀리지 않는다.
 */
private fun Modifier.bleedH(amount: Dp) = if (amount <= 0.dp) this else this.layout { m, c ->
    val extra = amount.roundToPx() * 2
    val p = m.measure(c.copy(minWidth = c.maxWidth + extra, maxWidth = c.maxWidth + extra))
    layout(c.maxWidth, p.height) { p.place(-extra / 2, 0) }
}

@Composable
private fun LineMapCard(
    onFullMap: () -> Unit,
    trains: List<TrainMark>,
    inbound: List<InboundTrain>,
    candidates: List<String>,
    fetchedAtMillis: Long,
    nowMillis: Long,
    error: String?,
    pal: MapPalette,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    fun adjEta(t: InboundTrain) =
        (t.etaSec - ((nowMillis - fetchedAtMillis) / 1000)).toInt().coerceAtLeast(0)

    // 입고열차 신도림 도착 시각 추적 → 기지 회송 애니메이션
    val depotRuns = remember { mutableStateMapOf<String, Long>() }
    SideEffect {
        inbound.forEach { t ->
            if (adjEta(t) <= 0 && !depotRuns.containsKey(t.trainNo)) depotRuns[t.trainNo] = nowMillis
        }
        depotRuns.keys.toList().forEach { no ->
            if ((nowMillis - (depotRuns[no] ?: 0L)) / 1000f > DEPOT_RUN_SEC + 20f) depotRuns.remove(no)
        }
    }
    val runners = depotRuns.mapNotNull { (no, ts) ->
        val e = (nowMillis - ts) / 1000f
        if (e > DEPOT_RUN_SEC) null            // 도림천 도달 → 화면에서 퇴장
        else no to (4f - minOf(4f - DEPOT_RUN_END, e * ((4f - DEPOT_RUN_END) / DEPOT_RUN_SEC)))
    }
    // 후보 중 **실제로 API 에 살아 있는** 첫 번째가 내 열차다. 없으면 없는 것이다.
    // ⚠ 잣대는 [pickRun] — 같은 운행이 다른 접두로 뜨고(v1.7.2), 같은 몸통이 둘 뜨면
    // 하나만 고른다(v1.7.3, 본선 지도와 같은 함수). 지선 후보(`5xxx`)는 [sameRun] 이
    // **정확히 같은 번호만** 받으므로 종전 동작 그대로다.
    val mine = run {
        val lives = trains.map { LiveRef(it.trainNo) }
        candidates.firstNotNullOfOrNull { no ->
            pickRun(no, lives)?.let { l -> trains.first { it.trainNo == l.trainNo } }
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = pal.bg),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        run {
            /*
             * 폴드 펼침이면 글자를 키운다(역명 13 → 15sp 등).
             *
             * ⚠ 잣대는 **창 폭**이지 카드 폭이 아니다. 펼침은 달력 + 상세 **2단**이라 카드가
             * 도리어 좁아진다(에뮬 실측: 접힘 시트 389dp > 펼침 패널 334dp) — 카드 폭으로
             * 재면 펼침에서 글자가 영영 안 커진다. 이 저장소가 쓰는 잣대
             * (`MainCalendarScreen` 의 `wide`)와 **같은 값**으로 맞춘다.
             */
            val big = LocalConfiguration.current.screenWidthDp >= 600
            // 노선도는 **글자가 아니라 그림**이다 — 선·차선·역 간격이 전부 dp로 고정돼 있는데
            // 글자만 sp라, 시스템 폰트를 키우면 양 끝 역명(까치산·신도림)이 카드 밖으로 잘려
            // 나갔다(fontScale 1.5 실측). 이 저장소가 여러 번 물린 자리다.
            //
            // 그래서 **카드 안에서만** fontScale 상한을 1.2로 잡는다. dp 배율(density)은 그대로라
            // 그림 크기는 안 변하고, 폰트를 키운 사용자도 1.2배까지는 커진 글자를 본다.
            //
            // ⚠ [rememberTextMeasurer]는 **만들어질 때의 density를 물고 간다** — 반드시
            //   이 Provider 안에서 만들어야 상한이 실제 측정에 먹는다(밖에 두면 조용히 무시된다).
            val d = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(d.density, d.fontScale.coerceAtMost(1.2f))
            ) {
                val tm = rememberTextMeasurer()
                val nameSp = (if (big) 15f else 13f) * CARD_K
                /*
                 * **영업 열차는 전부 증기기관차**(v1.6.91 사용자 확정 — *"신도림행 네모 아이콘은
                 * 왜 따로 다녀?"*). 한 카드 안에서 열차가 기관차와 네모 배지 두 모양으로 그려지던
                 * 것을 하나로 접었다. 모양 = 열차 / 머리 = 진행 방향 / 색 = 신분(내 열차 노랑 ·
                 * 일반 하늘 · 기지 회송 회색). 열번은 늘 몸통 안이다([Loco] 규칙 1).
                 *
                 * 기관차 한 칸 높이 = 차선 한 단. 계단식 회피가 **한 단**이므로 차선은 두 칸이다.
                 */
                val locoScale = (if (big) 54f / LOCO_LEN else 1f) * CARD_K
                val locoH = (LOCO_BOX_H * locoScale).dp
                /**
                 * 차선 하나 = 기관차 두 칸 + 단 사이 2dp + **제 선로 위 2dp**.
                 * 두 차선 다 선로 위에 기관차가 쌓이므로 높이가 같다(v1.6.95 복선).
                 * 위 차선 기관차는 [UP_LOCO_K] 배로 작아 그만큼 여유가 더 남는다 —
                 * 굴뚝 연기와 행선판이 카드 밖으로 안 나가는 자리가 그 여유다.
                 *
                 * ⚠ v1.6.98 — 이 아래 값들은 전부 [CARD_K] 가 한 번씩 곱해진다(카드 15% 축소).
                 */
                val laneH = locoH * 2f + 4.dp * CARD_K
                val nameH = (if (big) 21f else 18f).dp * CARD_K
                /*
                 * 위→아래: 까치산행 차선 → 위 선로 → 신도림행 차선 → **아래 선로 → 역 이름**.
                 *
                 * ⚠ v1.6.96 — 역 이름이 **두 선로 사이에서 아래 선로 밑으로** 내려왔다.
                 * 사용자: *"신정지선도 신도림행이 중요하니까 역이름을 신도림행 쪽으로 넣고"*.
                 * 이름이 주 선로(신도림행) 바로 밑에 붙어 **어느 선로의 역인지**가 눈에 먼저
                 * 든다. 높이 총합은 v1.6.95 와 **똑같다** — 순서만 바뀌었다.
                 *
                 * ⚠ **행선판 자리(`boardRoom`)를 따로 안 비운다**(v1.6.94까지는 비웠다).
                 * 두 차선 모두 기관차가 선로 **위**로 쌓이고 내 열차가 늘 `r = 0`(선로에 붙은
                 * 칸)을 먼저 가져가므로, 지붕 위 행선판([LOCO_BOARD_H] = 17)은 비어 있는
                 * `r = 1` 칸(31 + 2dp) 안에 그대로 들어간다. 굴뚝 연기도 같은 자리다.
                 */
                val canvasH = laneH + UP_LINE_H + laneH + LINE_H + nameH + 7.dp * CARD_K

                Column(Modifier.padding(vertical = 4.dp)) {
                    BranchHeader(nowMillis, big, pal, onRefresh)
                    /*
                     * 내 열차 한 줄 — `내 열차 5581 · 신도림행 · 양천구청 진입`.
                     * ⚠ **도착 예정 시각은 만들지 않는다**(본선 헤더와 같은 규칙) — 그 데이터가
                     * 앱에 없다. API 가 준 상태 문구([TrainMark.statusText])만 그대로 옮긴다.
                     */
                    if (candidates.isNotEmpty()) Text(
                        mine?.let {
                            "내 열차 ${it.trainNo} · ${destOf(it.toSindorim)} · ${it.statusText}"
                        } ?: ("내 열차 미검출 · 오늘 열번 " + shortNos(candidates)),
                        fontSize = (if (big) 12.5f else 11f).sp,
                        color = pal.dim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 1.dp),
                    )

                    // 위치 보간 드리프트 + 뒤로가기 방지(단조 전진)
                    val elapsedSec = ((nowMillis - fetchedAtMillis) / 1000f).coerceAtLeast(0f)
                    val maxIdx = (BranchLine.stations.size - 1).toFloat()
                    val lastShown = remember { HashMap<String, Float>() }
                    // 화면에 **실제로 그려진** 위치. 등속 지속시간을 남은 거리로 잡는 데 쓴다(v1.6.70).
                    val lastDrawn = remember { HashMap<String, Float>() }
                    run { // 사라진 열차의 기억 정리
                        val alive = trains.map { it.trainNo + if (it.toSindorim) "U" else "D" }.toSet()
                        lastShown.keys.retainAll(alive)
                        lastDrawn.keys.retainAll(alive)
                    }
                    val animated = trains
                        .sortedBy { it.trainNo + if (it.toSindorim) "U" else "D" }
                        .map { t ->
                            key(t.trainNo + if (t.toSindorim) "U" else "D") {
                                val atTerminus = t.position <= 0.2f || t.position >= 3.8f
                                val holding = t.statusText.endsWith("진입") || t.statusText.endsWith("도착") ||
                                    (t.statusText.contains("회차") && atTerminus)
                                // 현재 구간의 실측 주행시간 = **화면 속도의 유일한 기준**(실차와 같은 리듬).
                                // 역 화면 간격(`stFrac`)도 같은 실측시간 비율이라 px/초가 구간마다 같다.
                                val segIdx = (if (t.toSindorim) floor(t.position.toDouble())
                                              else ceil(t.position.toDouble()) - 1).toInt().coerceIn(0, 3)
                                val segSec = (if (t.toSindorim) BranchLine.SEG_UP[segIdx]
                                              else BranchLine.SEG_DN[segIdx]).toFloat()
                                val target = if (holding) t.position else {
                                    val dir = if (t.toSindorim) 1f else -1f
                                    val advance = elapsedSec / segSec
                                    // 다음 역까지 '완주'하는 연속 보간 (도착 후 그 자리에서 대기)
                                    val cap = if (t.toSindorim)
                                        (floor(t.position.toDouble()).toFloat() + 1f - t.position)
                                    else
                                        (t.position - (ceil(t.position.toDouble()).toFloat() - 1f))
                                    (t.position + dir * minOf(advance, cap.coerceAtLeast(0f)))
                                        .coerceIn(0f, maxIdx)
                                }
                                // 후진 완전 금지: 어떤 크기의 후퇴 신호도 무시하고 전진 상태 유지
                                val k = t.trainNo + if (t.toSindorim) "U" else "D"
                                val prev = lastShown[k]
                                val forwardSafe = if (prev == null) target else {
                                    val backstep = if (t.toSindorim) prev - target else target - prev
                                    if (backstep > 0f) prev else target
                                }
                                lastShown[k] = forwardSafe
                                /*
                                 * **등속**(v1.6.70). 지속시간을 `남은 거리 ÷ 실제 주행속도`로 잡는다.
                                 *
                                 * 종전 `tween(5000)`은 목표가 새로 올 때마다 "5초 안에 거기까지"로
                                 * 다시 잡혔다 — 남은 거리가 크면 빨라지고 작으면 느려져서, 갱신
                                 * 경계마다 **속도가 확 달라졌다**(사용자가 본 가속·감속의 정체).
                                 * 이제 거리가 얼마든 속도는 `1구간 ÷ segSec` 하나뿐이라
                                 * ③의 갱신 주기가 10↔4초로 바뀌어도(v1.6.72) 화면 속도는 안 변한다.
                                 *
                                 * ponytail: 상한 = 한 구간 주행시간. 그보다 먼 목표(열차가 오래
                                 * 사라졌다 나타난 경우)는 그만큼 빨리 따라붙는다 — 데이터 도약이라
                                 * 어차피 등속으로 메울 거리가 아니다. 필요하면 여기 clamp만 손본다.
                                 */
                                val durMs = (abs(forwardSafe - (lastDrawn[k] ?: forwardSafe))
                                    * segSec * 1000f).toInt().coerceIn(1, segSec.toInt() * 1000)
                                val p by animateFloatAsState(
                                    forwardSafe, tween(durMs, easing = LinearEasing), label = "t${t.trainNo}")
                                lastDrawn[k] = p
                                Triple(t, p, holding)
                            }
                        }
                    val runnerAnimated = runners.map { (no, pos) ->
                        val p by animateFloatAsState(pos, tween(1000, easing = LinearEasing), label = "r$no")
                        no to p
                    }
                    /*
                     * 연기·물결 위상 — **카드 한 장에 하나뿐**이다(v1.6.91, 본선 지도와 같은 처방).
                     * 열차마다 [rememberInfiniteTransition] 을 만들면 트랜지션이 줄줄이 돈다.
                     */
                    val phase by rememberInfiniteTransition(label = "loco").animateFloat(
                        0f, 1f,
                        infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "phase",
                    )

                    Canvas(Modifier.fillMaxWidth().height(canvasH)) {
                        val gapP = 2.dp.toPx() * CARD_K
                        val laneP = laneH.toPx()
                        /** 기관차 높이·폭·행선판 — **차선마다** 다르다(위 = [UP_LOCO_K] 배). */
                        val locoP = locoH.toPx()
                        val locoUpP = locoP * UP_LOCO_K
                        val boardP = (LOCO_BOARD_H * locoScale).dp.toPx()
                        val boardUpP = boardP * UP_LOCO_K
                        val lineP = LINE_H.toPx()
                        val upLineP = UP_LINE_H.toPx()
                        // 위→아래: 까치산행 차선 → 위 선로 → 신도림행 차선 → 아래 선로 → 역 이름.
                        val upLineTop = laneP
                        val upLineY = upLineTop + upLineP / 2f
                        val lineTop = upLineTop + upLineP + gapP + laneP
                        val lineY = lineTop + lineP / 2f
                        // 역 이름은 **아래 선로(신도림행) 바로 밑**이다(v1.6.96 사용자 확정).
                        val nameTop = lineTop + lineP + gapP
                        /**
                         * 차선 행 중심 y. `r=0`은 **제 선로에 바퀴를 붙인** 행, `r=1`은 계단식으로
                         * 한 단 **위로** 물러난 행 — 두 차선 다 선로가 밑에 있으니 물러나는 쪽도 같다.
                         */
                        fun rowY(up: Boolean, r: Int): Float {
                            val h = if (up) locoUpP else locoP
                            val railTop = if (up) upLineTop else lineTop
                            return railTop - gapP - h / 2f - r * (h + gapP)
                        }

                        // 상자는 열번 길이와 무관하게 **기관차 한 대 크기**로 늘 같다(v1.6.91) —
                        // 종전처럼 열번마다 재면 상자가 들쭉날쭉해져 겹침 판정과 그린 결과가 어긋난다.
                        val locoW = LOCO_BOX_W * locoScale * 1.dp.toPx()
                        val locoUpW = locoW * UP_LOCO_K
                        /**
                         * 양 끝 안쪽 여백 — **기관차 반 폭 + 6dp 이상**을 늘 남긴다(v1.6.97).
                         *
                         * 종전엔 16dp 고정이라 종착역(까치산·신도림)에 선 기관차가 [place] 의
                         * 가장자리 물림(반 폭 + 2dp)까지 끌려와 **카드 둥근 테두리(16dp)에 닿았다** —
                         * 사용자: *"까치산에 선 5517 의 운전실이 카드 왼쪽 테두리에 닿아 잘려 보인다"*.
                         * 이제 `xOf(0)`·`xOf(4)` 자체가 반 폭 밖이라 물림이 아예 안 걸리고 양 끝
                         * 기관차가 온전히 선다. 선로는 종전대로 카드 폭 전체를 가로지르고(밖으로
                         * 이어지는 그림이다) **역 간격만** 그만큼 촘촘해진다 — 역명이 붙으면
                         * 아래 `fits()` 가 0.5sp 씩 알아서 줄인다.
                         */
                        val pad = maxOf(16.dp.toPx(), locoW / 2f + 6.dp.toPx())
                        // 역 화면 위치 = 구간 실측시간 비율(상·하행 평균). 표시만 변환(위치 계산은 0~4 유지)
                        val stFrac = floatArrayOf(0f, 0.211f, 0.485f, 0.745f, 1f)
                        fun xOf(pos: Float): Float {
                            val i = pos.toInt().coerceIn(0, 3)
                            val f = (pos - i).coerceIn(0f, 1f)
                            return pad + (stFrac[i] + (stFrac[i + 1] - stFrac[i]) * f) * (size.width - pad * 2)
                        }

                        // ── 선로 둘 ───────────────────────────────────────────
                        // 아래 = 신도림행(주 선로, 종전 굵기·색 그대로) / 위 = 까치산행(연하고 가늘게)
                        // ⚠ 클레이는 두 선로 밑에 **그림자 띠**를 한 겹 깐다(본선 튜브와 같은
                        //   처방 — 크림 바탕에서 연한 민트가 묻히지 않게 윤곽을 잡는다).
                        //   지선 카드는 늘 가로라 지도 회전이 없다 = 그림자는 그냥 아래로.
                        pal.railShadow?.let {
                            val d = 3.dp.toPx()
                            drawRect(it, topLeft = Offset(0f, lineTop + d),
                                size = Size(size.width, lineP))
                            drawRect(it, topLeft = Offset(0f, upLineTop + d),
                                size = Size(size.width, upLineP))
                        }
                        drawRect(pal.rail, topLeft = Offset(0f, lineTop),
                            size = Size(size.width, lineP))
                        drawRect(pal.railSoft, topLeft = Offset(0f, upLineTop),
                            size = Size(size.width, upLineP))
                        // 아래(주) 선로 윗면 하이라이트 — 튜브처럼 보이게 하는 가는 밝은 줄.
                        pal.railHighlight?.let {
                            drawRect(it, topLeft = Offset(0f, lineTop),
                                size = Size(size.width, lineP * 0.34f))
                        }

                        /*
                         * 정차 중인 열차가 서 있는 역(v1.6.49 사용자: *"빨간점은 역에 하얀점에
                         * 넣어줘야지"*). 판정은 v1.6.47의 [holding]을 **그대로** 쓴다 — 새 상태도,
                         * 새 조회도 없다. 0.25칸(≈한 구간의 1/4) 안에 있을 때만 그 역으로 친다:
                         * 종착 회차(pos 3.8)도 신도림으로 잡히고, 구간 한복판의 신호대기는
                         * 어느 역도 물들이지 않는다.
                         *
                         * ⚠ 복선이 되면서 **선로까지 다시 가른다**(v1.6.95) — 빨간 점은 그 열차가
                         * 실제로 달리는 선로의 점에만 든다. 선이 하나였을 때는 역 번호만 남겼었다.
                         */
                        val holdUp = HashSet<Int>()      // 까치산행(위 선로)
                        val holdDn = HashSet<Int>()      // 신도림행(아래 선로)
                        animated.filter { it.third }.forEach { (t, pos, _) ->
                            val i = Math.round(pos)
                            if (i in BranchLine.stations.indices && abs(pos - i) <= 0.25f)
                                (if (t.toSindorim) holdDn else holdUp) += i
                        }

                        // ── 역 5개 + 이름 ─────────────────────────────────────
                        // 양 끝 역(까치산·신도림)은 이름 반폭이 캔버스 여백(16dp)보다 넓어
                        // 삐져나간다 — 카드 벽에 **4dp 는 남기고** 물린다(실측: 안 남기면
                        // 신도림 글자가 둥근 모서리에 닿아 잘려 보였다).
                        val inset = 4.dp.toPx()
                        fun nameX(l: TextLayoutResult, i: Int) = (xOf(i.toFloat()) - l.size.width / 2f)
                            .coerceIn(inset, (size.width - inset - l.size.width).coerceAtLeast(inset))
                        fun nameLabels(sp: Float) = BranchLine.stations.map { name ->
                            val key = name in KEY_STATIONS
                            tm.measure(name, TextStyle(
                                fontSize = (sp + if (key) 2f else 0f).sp,
                                fontWeight = if (key) FontWeight.ExtraBold else FontWeight.Bold,
                                // ⚠ **역 이름은 `label`, 역 점은 `station`** — 남색에서는 둘 다
                                // 흰색이라 한 값이어도 됐지만, 크림 바탕에서 이름까지 흰색이면
                                // 까치산·신정네거리·도림천이 통째로 안 보인다(실측).
                                color = if (key) pal.keyA else pal.label))
                        }
                        /*
                         * 역명 크기는 **재서 정한다.**
                         *
                         * ⚠ 폴드 펼침은 달력+상세 2단이라 카드가 도리어 좁다(실측 334dp < 접힘
                         * 389dp). 그 폭에 `big` 15sp 를 그대로 넣었더니 왼쪽 두 역이 통째로 붙어
                         * `까치산신정네거리` 로 읽혔다. 그래서 [nameSp] 는 **바라는 크기**일 뿐이고,
                         * 이웃과 3dp 도 못 띄우면 0.5sp 씩 내린다(하한 9sp — 그 아래는 못 읽는다).
                         * 넉넉한 화면에서는 첫 판에 통과해 바라는 크기 그대로 그려진다.
                         */
                        var labs = nameLabels(nameSp)
                        run {
                            var sp = nameSp
                            fun fits(ls: List<TextLayoutResult>): Boolean {
                                var prevRight = -Float.MAX_VALUE
                                ls.forEachIndexed { i, l ->
                                    val x = nameX(l, i)
                                    if (x < prevRight + 3.dp.toPx()) return false
                                    prevRight = x + l.size.width
                                }
                                return true
                            }
                            while (sp > 9f && !fits(labs)) { sp -= 0.5f; labs = nameLabels(sp) }
                        }
                        // 역 점은 **두 선로에 각각**. 위 선로 점은 한 단계 작다(위계 — v1.6.95).
                        BranchLine.stations.forEachIndexed { i, name ->
                            val x = xOf(i.toFloat())
                            val key = name in KEY_STATIONS
                            val rad = 3.5.dp.toPx() * (if (key) 1.5f else 1f)
                            fun dot(y: Float, r: Float, red: Boolean, ring: Float) {
                                // 클레이 점토 단추 — 밑에 그림자 한 겹(크림 위에서 흰 점이 뜬다)
                                if (pal.clay) drawCircle(
                                    pal.shadow, r, Offset(x, y + 1.6.dp.toPx()))
                                drawCircle(
                                    if (red) pal.stationRed else if (key) pal.keyA else pal.station,
                                    r, Offset(x, y))
                                if (key || red || pal.clay) drawCircle(
                                    when {
                                        key -> Color.White
                                        red -> Color.White.copy(alpha = 0.55f)
                                        else -> pal.stationEdge
                                    },
                                    r, Offset(x, y), style = Stroke(width = ring))
                            }
                            dot(lineY, rad, i in holdDn, 1.5.dp.toPx())
                            dot(upLineY, rad * 0.7f, i in holdUp, 1.1.dp.toPx())
                        }
                        // 역 **이름**은 이 블록 **맨 끝**에서 그린다 — 아래 "역 이름은 맨 나중에" 절을 보라.

                        // ── 차선 안내(흐린 글씨) ───────────────────────────────
                        // 배지보다 **먼저** 그린다 — 자리를 다투면 열차가 이긴다(장식이 정보를
                        // 밀어내면 안 된다). 그래서 겹침 판정([boxes])에도 넣지 않는다.
                        fun hint(text: String, y: Float) {
                            val l = tm.measure(text, TextStyle(
                                fontSize = (if (big) 10f else 8.5f).sp,
                                fontWeight = FontWeight.Bold, color = pal.dim))
                            drawText(l, topLeft = Offset(2.dp.toPx(), y - l.size.height / 2f))
                        }
                        // 위가 까치산행, 아래가 신도림행(주 선로) — 화살표도 새 배치를 따른다(v1.6.95).
                        hint("까치산행 ▲", rowY(true, 1))
                        hint("▼ 신도림행", rowY(false, 1))

                        // ── 기관차 자리잡기 ────────────────────────────────────
                        // 상자 폭(`locoW`·`locoUpW`)은 위 `pad` 옆에서 잡았다 — 여백과 그림이
                        // **같은 숫자**를 봐야 양 끝 기관차가 안 잘린다(v1.6.97).
                        val boxes = ArrayList<Rect>()
                        /*
                         * `기지` 꼬리표 — 몸통 **오른쪽**(진행 반대쪽)이 원칙이다. 신도림 끝에서는
                         * 오른쪽에 자리가 없어 종전엔 `coerceAtMost` 로 끌려와 **제 회색 몸통 위에
                         * 겹쳐 찍혔다**(글자색도 몸통색과 같은 `depot` 라 통째로 못 읽었다).
                         * 자리가 없으면 반대쪽(왼쪽)에 붙인다 — 그리는 곳과 자리를 잡는 곳이
                         * **같은 함수**를 봐야 어긋나지 않는다(v1.6.93).
                         */
                        val tagGap = 2.dp.toPx()
                        val depotTag = tm.measure("기지", TextStyle(
                            fontSize = (if (big) 9f else 8f).sp,
                            fontWeight = FontWeight.Bold, color = pal.depot))
                        // ⚠ 기지 회송은 **위 차선(까치산행)** 이라 몸통 폭도 [locoUpW] 다(v1.6.95).
                        fun tagLeft(cx: Float): Float {
                            val right = cx + locoUpW / 2f + tagGap
                            return if (right + depotTag.size.width <= size.width) right
                            else (cx - locoUpW / 2f - tagGap - depotTag.size.width).coerceAtLeast(0f)
                        }
                        /**
                         * 빈 자리를 찾아 중심을 돌려준다. 두 단 다 막혔으면 `null`(= 점만).
                         * ⚠ 가장자리 물림에 **2dp 를 더해** 잰다 — 종착역(까치산·신도림)에서 딱
                         * 반폭까지만 물러나면 앞코가 카드 밖으로 잘린다.
                         *
                         * @param board 내 열차 = 지붕 위 행선판까지 **한 상자**다(v1.6.91).
                         *   판만큼 위로 큰 상자를 잡아야 남의 열차가 판 위에 올라앉지 않는다.
                         * @param tag 기지 회송 = `기지` 꼬리표까지 **한 상자**다(v1.6.93).
                         *   꼬리표를 [boxes] 밖에 두면 나중에 놓이는 열차가 그 위에 올라앉는다.
                         * @param up **위 차선(까치산행)** 이면 true. 기관차가 [UP_LOCO_K] 배라
                         *   상자도 그만큼 작다(v1.6.95).
                         */
                        fun place(x: Float, up: Boolean, board: Boolean = false,
                                  tag: Boolean = false): Offset? {
                            val w = if (up) locoUpW else locoW
                            val h = if (up) locoUpP else locoP
                            val bp = if (up) boardUpP else boardP
                            val edge = w / 2f + 2.dp.toPx()
                            val cx = x.coerceIn(edge, (size.width - edge).coerceAtLeast(edge))
                            val roof = h / 2f + if (board) bp else 0f
                            val l0 = cx - w / 2f
                            val r0 = cx + w / 2f
                            val tagL = if (tag) minOf(l0, tagLeft(cx)) else l0
                            val tagR = if (tag) maxOf(r0, tagLeft(cx) + depotTag.size.width) else r0
                            for (r in 0..1) {
                                val cy = rowY(up, r)
                                val rect = Rect(tagL, cy - roof, tagR, cy + h / 2f)
                                if (boxes.none { it.overlaps(rect) }) {
                                    boxes += rect
                                    /*
                                     * 계단(`r = 1`)으로 올라간 열차는 발밑에 **받침선**을 깐다
                                     * (v1.6.98 사용자 확정: *"떠 있는 열차 금지"*). 제 선로와
                                     * 같은 색·같은 간격(`gapP`)이라 **한 칸 위에 놓인 선로**로
                                     * 읽힌다. `r = 0` 은 진짜 선로가 이미 그 자리에 있다.
                                     */
                                    if (r > 0) {
                                        val fy = cy + h / 2f + gapP
                                        drawLine(if (up) pal.railSoft else pal.rail,
                                            Offset(cx - w / 2f, fy), Offset(cx + w / 2f, fy),
                                            strokeWidth = if (up) upLineP else lineP,
                                            cap = StrokeCap.Round)
                                    }
                                    return Offset(cx, cy)
                                }
                            }
                            return null
                        }
                        // **내 열차부터** 자리를 잡는다(제 선로에 붙은 행을 먼저 가져간다).
                        // ⚠ 위 차선 = **까치산행**이므로 `up = !toSindorim` 이다(v1.6.95 복선).
                        val mineNo = mine?.trainNo
                        val spots = animated.sortedByDescending { it.first.trainNo == mineNo }
                            .map { (t, pos, _) ->
                                Triple(t, pos, place(
                                    xOf(pos), !t.toSindorim, board = t.trainNo == mineNo))
                            }
                        // 입고 회송(신도림 → 기지)은 왼쪽으로 달리니 **까치산행 = 위 차선**이다.
                        val runnerSpots = runnerAnimated.map { (no, pos) ->
                            Triple(no, pos, place(xOf(pos), up = true, tag = true))
                        }

                        /**
                         * 자리를 못 잡은 열차 — 그래도 **어디 있는지는** 제 선로 위 점으로 남긴다.
                         * 색도 제 차선 몸통색이다(v1.6.96 — 신도림행은 노랑).
                         */
                        // ⚠ 클레이는 **열번색**으로 찍는다 — 흰 몸통 점은 크림 바탕에서 안 보인다
                        // (v1.7.0. 본선 지도의 접힌 열차 점과 같은 처방).
                        fun dotOnly(x: Float, up: Boolean) = drawCircle(
                            if (pal.clay) pal.wheel else if (up) pal.softBody else pal.mineBody,
                            2.5.dp.toPx(), Offset(x, if (up) upLineY else lineY))
                        /**
                         * 기관차 한 대. **머리 = 진행 방향** — 신도림이 오른쪽 끝이라 신도림행은
                         * 오른쪽, 까치산행은 왼쪽이다([headingFor] 한 곳이 정하고 [LocoTest] 가
                         * 잠근다). 열번은 늘 몸통 안.
                         *
                         * 지선은 열차가 적으니 **모두 연기·물결**을 낸다(사용자: 은하철도999면
                         * 연기가 나야지). 20대가 넘게 뜨는 본선 전체 필터만 내 열차로 제한한다.
                         *
                         * ⚠ **크기·연기가 차선마다 다르다**(v1.6.95 복선). 까치산행(위 차선)은
                         * 덜 중요한 차선이라 [UP_LOCO_K] 배로 작고 연기도 0.8배다 — 그 위가 바로
                         * 카드 머리라 제 길이로 오르면 헤더를 침범한다. 신도림행(아래, 주 차선)은
                         * 제 크기·제 연기 그대로다(굴뚝 위가 빈 `r = 1` 칸이라 자리가 있다).
                         *
                         * @param dest 비어 있지 않으면 지붕 위 행선판까지 함께 그린다(내 열차).
                         */
                        fun loco(c: Offset, no: String, toSindorim: Boolean, body: Color, ink: Color,
                                 mine: Boolean = false, dest: String = "") = drawLoco(
                            c, headingFor(1f, 0f, toSindorim),
                            if (toSindorim) locoScale else locoScale * UP_LOCO_K,
                            body, pal.wheel,
                            no, ink, tm, smoke = true, phase = phase, highlight = mine,
                            dest = dest, smokeK = if (toSindorim) 1f else 0.8f,
                            // 클레이 색 손잡이 — 남색 스타일은 전부 기본값이라 그림이 안 바뀐다.
                            // 몸통 그라데이션은 **몸통색이 노랑일 때만** 노랑 램프를 쓴다(신도림행).
                            bodyRamp = if (!pal.clay) null
                                else if (body == pal.mineBody) pal.mineTop to pal.mineBottom
                                else pal.otherTop to pal.otherBottom,
                            edge = if (!pal.clay) null
                                else if (body == pal.mineBody) pal.mineRing else pal.otherEdge,
                            ring = pal.mineRing, smokeColor = pal.smoke, shadowColor = pal.shadow,
                            // 지선은 늘 대여섯 대뿐이라 밀도 가드가 걸릴 일이 없다(두 겹 고정).
                            clayShadow = if (pal.clay) 2 else 0,
                        )

                        /*
                         * 기지 입고 회송 — **회색 몸통 + `기지` 꼬리표**(v1.6.91 색 = 신분 규칙).
                         * 신도림에서 도림천 기지로 왼쪽으로 달리니 머리도 왼쪽이고, 꼬리표는
                         * 진행 **반대쪽**(오른쪽) 몸통 밖에 붙는다. 왼쪽으로 달리니 **위 차선
                         * (까치산행 선로)** 이다(v1.6.95) — 역 이름 줄보다 위라 글자를 안 가린다.
                         * 자리는 [tagLeft] 가 정한다(오른쪽이 막히면 왼쪽 — v1.6.93).
                         */
                        runnerSpots.forEach { (no, pos, c) ->
                            if (c == null) dotOnly(xOf(pos), up = true) else {
                                loco(c, no, toSindorim = false, body = pal.depot, ink = pal.otherInk)
                                drawText(depotTag, topLeft = Offset(
                                    tagLeft(c.x), c.y - depotTag.size.height / 2f))
                            }
                        }
                        /*
                         * 남의 열차 — **신도림행은 전부 노란 몸통 + 남색 열번**(v1.6.96 사용자
                         * 확정: *"신도림행으로 가는 열차를 본선과 마찬가지로 트렌디한 노란색으로
                         * 해줘!"*). 색이 곧 **어느 방향인가**를 말한다: 노랑 = 신도림행(주),
                         * 물러난 하늘 = 까치산행, 회색 = 기지 회송.
                         *
                         * ⚠ 내 열차도 노란 몸통이라 **구분은 몸통색이 아니다**(아래) — 흰 테두리 +
                         * 지붕 위 행선판 + **빨간** 열번 셋이 맡는다. 남의 신도림행 열번은
                         * `otherInk` 남색이라 한눈에 갈린다(노랑 위 남색 대비 12:1).
                         */
                        spots.filter { it.first.trainNo != mineNo }.forEach { (t, pos, c) ->
                            if (c == null) dotOnly(xOf(pos), !t.toSindorim)
                            else loco(c, t.trainNo, t.toSindorim,
                                if (t.toSindorim) pal.mineBody else pal.softBody, pal.otherInk)
                        }
                        // ⚠ **내 열차는 맨 나중에** 그린다 — 다른 표시에 가리면 "표시가 안 된다"는 말이 된다.
                        spots.firstOrNull { it.first.trainNo == mineNo }?.let { (t, pos, c) ->
                            if (c == null) dotOnly(xOf(pos), !t.toSindorim)
                            // 행선은 **지붕 위 행선판**이다(v1.6.91 사용자 확정). 종전엔 기관차
                            // 옆에 노란 조각을 따로 붙였는데, 열차와 따로 놀아 *"왜 따로 노냐?"*
                            // 는 말을 들었다. 이제 [drawLoco] 가 한 몸으로 그린다.
                            else loco(c, t.trainNo, t.toSindorim, pal.mineBody, pal.mineInk,
                                mine = true, dest = destOf(t.toSindorim))
                        }

                        /*
                         * ── 역 이름은 **맨 나중에** (v1.6.91) ───────────────────
                         * 사용자 확정 규칙: *"텍스트가 겹쳐서 안 보이게 하는 일은 없도록"*.
                         *
                         * 까치산행까지 기관차가 되면서 **굴뚝 연기가 이름 줄까지 떠올랐다**
                         * (실측: 신도림 글자에 흰 연기가 얹혔다). 연기를 줄이는 대신 **순서로**
                         * 푼다 — 이름이 늘 맨 위면 무엇을 더 그려도 이 규칙이 안 깨진다.
                         * (v1.6.95 복선에서는 이름 줄 아래·위가 다 빈 `r = 1` 칸이라 연기가
                         * 애초에 안 닿지만, 순서 규칙은 그대로 둔다 — 값이 바뀌어도 안 깨지는
                         * 것이 이 처방의 값어치다.)
                         */
                        BranchLine.stations.indices.forEach { i ->
                            drawText(labs[i], topLeft = Offset(nameX(labs[i], i), nameTop))
                        }

                        // 표시할 열차가 없을 때: 이유를 알려주는 빈 상태 안내.
                        // ⚠ **실패를 반드시 말로 한다**(v1.6.46). 종전엔 [error]를 화면이 안 읽어서
                        // 비행기 모드·서버 오류·API 한도 소진 어느 쪽이든 `"실시간 조회 중…"` 에 영원히
                        // 머물렀다 — 사용자는 앱이 고장인지 알 수 없었다. 새로고침(↻)은 헤더 오른쪽 끝.
                        if (animated.isEmpty() && runnerAnimated.isEmpty()) {
                            val failed = error != null
                            val msg = error
                                ?: if (inService()) "실시간 조회 중…" else "금일 운행 종료 (영업 05:30~)"
                            val empty = tm.measure(msg, TextStyle(
                                fontSize = (if (big) 12f else 11f).sp,
                                fontWeight = if (failed) FontWeight.Bold else FontWeight.Normal,
                                color = if (failed) pal.title else pal.dim))
                            val hintL = if (!failed) null else tm.measure("오른쪽 위 ↻ 를 눌러 다시 시도",
                                TextStyle(fontSize = (if (big) 11f else 10f).sp, color = pal.dim))
                            // 두 줄일 때도 **묶음 전체**를 위 차선 가운데에 둔다(선·역명과 안 겹친다).
                            val gap = 3.dp.toPx()
                            val blockH = empty.size.height + (hintL?.let { gap + it.size.height } ?: 0f)
                            var ty = laneP / 2f - blockH / 2f
                            drawText(empty, topLeft = Offset(size.width / 2 - empty.size.width / 2, ty))
                            hintL?.let {
                                ty += empty.size.height + gap
                                drawText(it, topLeft = Offset(size.width / 2 - it.size.width / 2, ty))
                            }
                        }
                    }

                    // ── 하단 칩 한 줄 ─────────────────────────────────────────
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        /*
                         * ⚠ 왼쪽 칩 둘을 **한 겹 더 싼다**(v1.6.91). 종전엔 `입고` 칩과 빈
                         * [Spacer] 가 둘 다 `weight(1f)` 이라 남는 폭을 **반씩** 나눠 가졌다 —
                         * 글자배율 1.5 에서 칩 몫(≈116dp)이 글자보다 좁아 `입고 7516 · …` 로
                         * **잘렸는데 옆은 텅 비어 있었다**(실측). 이제 안쪽 Row 가 남는 폭을
                         * 통째로 받아 칩이 제 폭을 먼저 가져가고, 빈자리는 그 안에 남는다
                         * (덤으로 `본선 전체 보기` 가 오른쪽 끝에 붙는다).
                         */
                        Row(
                            Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            BranchChip("지선", big, pal.rail, pal)
                            // 입고 접근(**15분** 전~도착 전) — 판정·값은 v1.6.43 그대로, 자리만 칩으로 옮겼다.
                            // ⚠ v1.6.91 에서 10분(600초) → 15분(900초). 사용자 요청 *"15분 이내로 .."* —
                            // 10분이면 칩이 떠 있는 시간이 짧아 준비할 틈이 없다. 창을 여기서 넓혀도
                            // 조회는 안 늘어난다(같은 응답을 거르는 값일 뿐).
                            inbound.map { it to adjEta(it) }
                                .filter { it.second in 1..900 }
                                .minByOrNull { it.second }
                                ?.let { (t, eta) ->
                                    BranchChip(
                                        "입고 ${t.trainNo} · " +
                                            if (eta >= 60) "${eta / 60}분 후" else "${eta}초 후",
                                        big, pal.otherBody, pal, modifier = Modifier.weight(1f, fill = false),
                                    )
                                }
                        }
                        BranchChip("본선 전체 보기", big, pal.rail, pal, fill = true, onClick = onFullMap)
                    }
                }
            }
        }
    }
}

private fun destOf(toSindorim: Boolean) = if (toSindorim) "신도림행" else "까치산행"

/** 후보 열번이 많을 때 줄여 적는다 — 본선 헤더와 같은 규칙. */
private fun shortNos(nos: List<String>): String =
    nos.take(2).joinToString("·") + if (nos.size > 2) " 외 " + (nos.size - 2) + "개" else ""

/** 헤더 한 줄 — 왼쪽 `신정지선 실시간`, 오른쪽 노란 시계와 새로고침. */
@Composable
private fun BranchHeader(nowMillis: Long, big: Boolean, pal: MapPalette, onRefresh: () -> Unit) {
    val t = remember(nowMillis / 1_000) {
        LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), ZoneId.systemDefault())
    }
    val sp = (if (big) 17f else 15f).sp
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("신정지선 실시간", fontSize = sp, fontWeight = FontWeight.Bold,
            color = pal.title, maxLines = 1)
        Spacer(Modifier.weight(1f))
        // 시계는 노란색(사용자 확정 — 본선 헤더와 같다).
        Text("%02d:%02d:%02d".format(t.hour, t.minute, t.second),
            fontSize = sp, fontWeight = FontWeight.Bold, color = pal.clock, maxLines = 1)
        RefreshButton(pal, onRefresh)
    }
}

/**
 * 즉시 갱신 버튼 (탭하면 한 바퀴 회전 피드백).
 * 직접 그린 새로고침 글리프 (원호 + 화살촉) — 아이콘 라이브러리 의존성 없음.
 *
 * ⚠ **본선 지도 헤더도 이 함수를 그대로 쓴다**(v1.7.5 — 사용자: *"지금 신정지선에 에뮬레이터
 * 오른쪽 위에있는 리프레시 버튼 본선 전체보기에도 하나 만들어줘"*). 두 지도의 ↻ 는 같은
 * 모양·같은 터치 영역·같은 회전 피드백이라야 한다 — **복사해서 두 벌로 만들지 말 것.**
 */
@Composable
internal fun RefreshButton(pal: MapPalette, onRefresh: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    val spin by animateFloatAsState(tick * 360f, tween(700), label = "spin")
    /*
     * ⚠ **연타 방지 3초**(v1.7.7 A9). `onRefresh` 는 캐시를 건너뛰는 강제 조회라 **한 번에
     * API 2회**를 쓴다(위치 + 양천구청 도착). 종전엔 막는 것이 없어 세 번 누르면 6회가
     * 나갔다 — 키 6개 10000회/일을 282명이 나눠 쓰는 자원이다(`BranchLive.API_KEYS` 주석).
     * 누르면 3초 동안 **버튼만 꺼지고** 회전 애니메이션은 그대로 돈다 — 눌린 것은 보이고
     * 호출만 안 는다. 폴링(10초/4초)은 이 값과 무관하게 제 눈금대로 돈다.
     */
    var cooling by remember { mutableStateOf(false) }
    LaunchedEffect(tick) {
        if (tick > 0) { cooling = true; delay(REFRESH_COOLDOWN_MS); cooling = false }
    }
    // ⚠ 누르는 것은 **[Surface] 자체**다(v1.6.93). 종전엔 안쪽 [Canvas] 만 13dp + 여백 10dp =
    // 23dp 라 손끝의 절반도 안 걸렸는데, 빈 상태 문구가 바로 이 ↻ 를 누르라고 안내한다.
    // `Surface(onClick = …)` 이 `minimumInteractiveComponentSize()` 로 터치만 48dp 로 넓힌다.
    Surface(
        onClick = { tick++; onRefresh() },
        enabled = !cooling,
        color = if (pal.clay) pal.chip else Color.White.copy(alpha = 0.10f),
        shape = CircleShape,
        border = BorderStroke(1.2.dp, pal.rail.copy(alpha = if (cooling) 0.35f else 0.75f)),
        modifier = Modifier.padding(start = 8.dp),
    ) {
        Canvas(Modifier.padding(5.dp).size(13.dp).rotate(spin)) {
            val c = (if (pal.clay) pal.chipInk else Color(0xFFB9F5C0))
                .copy(alpha = if (cooling) 0.45f else 1f)
            drawArc(c, startAngle = -50f, sweepAngle = 290f, useCenter = false,
                style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round))
            val r = size.minDimension / 2f
            val ang = Math.toRadians(-50.0)
            val ax = size.width / 2f + (r * kotlin.math.cos(ang)).toFloat()
            val ay = size.height / 2f + (r * kotlin.math.sin(ang)).toFloat()
            val ah = 4.5.dp.toPx()
            drawPath(Path().apply {
                moveTo(ax + ah * 0.9f, ay - ah * 0.5f)
                lineTo(ax + ah * 0.2f, ay + ah * 0.9f)
                lineTo(ax - ah * 0.9f, ay - ah * 0.3f)
                close()
            }, c)
        }
    }
}

/**
 * 하단 알약 칩 — 본선 상태바 칩과 같은 모양·같은 크기.
 *
 * ⚠ 누를 수 있는 칩은 **[Surface] 가 통째로 단추**다(v1.6.93 — [MainLineMap] 칩과 같은 처방).
 * 안쪽 [Text] 에 `clickable` 을 걸면 터치 영역이 글자 높이(≈19dp)뿐이라 M3 최소 48dp 에 한참
 * 못 미친다. `Surface(onClick = …)` 은 리플·`Role.Button` 과 함께
 * `minimumInteractiveComponentSize()` 를 붙여 보이는 크기는 그대로 두고 터치만 넓힌다.
 */
@Composable
private fun BranchChip(
    text: String, big: Boolean, tint: Color, pal: MapPalette, fill: Boolean = false,
    modifier: Modifier = Modifier, onClick: (() -> Unit)? = null,
) {
    // 클레이는 칩 색이 한 가지(민트)이고 **누른 칩만 흰 알약**이다 — 본선 상태바 칩과 같다.
    val label: @Composable () -> Unit = {
        Text(
            text,
            fontSize = if (big) 12.5.sp else 10.sp, fontWeight = FontWeight.Bold,
            // ⚠ v1.7.7 D2 — **채운 칩의 글자는 남색 바탕색**이다. 종전 `pal.mineInk`(#B3261E)
            // 는 `본선 전체 보기` 의 초록 바탕(#2FC24A) 위에서 **2.78:1** 로 안 읽혔다(실측
            // 스샷 `F05b`). 흰 글자도 답이 아니다 — 초록이 밝아 **2.35:1** 로 더 나쁘다.
            // 남색(#0E2A47)이면 **6.23:1** 이고 알약은 종전 초록 그대로다.
            color = if (pal.clay) pal.chipInk else if (fill) pal.bg else tint,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
    val bg = when {
        pal.clay -> if (fill) pal.chipSel else pal.chip
        fill -> tint
        else -> tint.copy(alpha = 0.12f)
    }
    val shape = RoundedCornerShape(50)
    val line = BorderStroke(1.dp, if (pal.clay) pal.chipInk.copy(alpha = 0.30f)
                                  else tint.copy(alpha = 0.85f))
    if (onClick == null) Surface(modifier, shape, bg, border = line) { label() }
    else Surface(onClick, modifier, shape = shape, color = bg, border = line) { label() }
}
