package com.sinjeong.crewcalendar.presentation.live

import android.content.Context
import android.util.Log
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
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
import com.sinjeong.crewcalendar.domain.model.dutyTrainNumbers
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
 *  3. **신도림**은 이름 주황 +2sp 굵게 · 점 1.5배 + 흰 테두리 — 본선의 신도림·성수와 같은 규칙.
 *  4. 열차 = 하늘색 열번 배지. **신도림행은 선 위 차선 · 까치산행은 선 아래 차선**(본선이
 *     내선을 선 안쪽, 외선을 바깥쪽에 두는 것과 같은 발상 — 방향을 자리로 말한다).
 *  5. **내 열차**는 노란 배지 + 빨간 글씨 + 노란 행선 깃발, 그리고 **맨 나중에** 그린다.
 *
 * ## v1.6.43~87 에서 넘어온, 손대지 않은 것
 *
 * 이 파일은 편승지키미(kass79/SinjeongShuttle2) `ui/LineMap.kt` 이식본이었다. **그림만** 갈아
 * 끼웠고 로직은 그대로다 — 폴링 눈금(2초·절대시각) · 회차 기억 · 위치 보간(등속) · 후진 금지 ·
 * 입고 열차/기지 회송 · 정차 판정([holding]). 아래 KDoc 에 남은 실측 근거들이 그 증거다.
 *
 * 종전 그림에서 **빠진 것**(사용자가 시안에서 확인한 대로):
 *  · 증기기관차 아이콘·동륜·연기 — 배지 하나로 바뀌었다(본선과 같은 표기).
 *  · 상·하 두 선로와 건넘선(까치산 단선·도림천 X) — 선 하나 + 위/아래 차선으로 접었다.
 *  · 진행 셰브런·셔머·`↻ 회차` 배지 — 회차 상태는 내 열차 한 줄(헤더 밑)이 말한다.
 */

/* ── 색: [MainLineMap] 과 **같은 값** — 같이 고칠 것 ────────────────────────── */
/** 운전실 화면 바탕 — 테마와 무관하게 늘 이 남색이다. */
private val CabNavy = Color(0xFF0E2A47)
private val LoopGreen = Color(0xFF2FC24A)
private val StationWhite = Color(0xFFFFFFFF)
/** 열차가 서 있는 역 */
private val StationRed = Color(0xFFF0392B)
/** 열번 배지 — 옅은 하늘색 바탕 + 진한 남색 글씨 */
private val BadgeSky = Color(0xFFA9DCF5)
private val BadgeInk = Color(0xFF0A2036)
private val MineYellow = Color(0xFFFFE14D)
private val MineInk = Color(0xFFB3261E)
/** 신도림 강조 — 본선에서 신도림·성수에 쓰는 그 주황 */
private val KeyOrange = Color(0xFFFFB74D)
private val Dim = Color(0xFF8FA9C4)
/* ─────────────────────────────────────────────────────────────────────────── */

/** 지선에서 강조하는 역 — 본선이 갈라지는 자리. 본선 쪽 `KEY_STATIONS` 와 같은 규칙이다. */
private const val KEY_STATION = "신도림"

/** 지선 열차를 **실제로 잡는** 근무 — 운휴(`지휴`)·대기(`지대`)는 여기 없다. */
private val DRIVING_BRANCH = setOf(DutyType.BRANCH, DutyType.BRANCH_NIGHT)

/** 초록 가로선 두께 — 역 흰 점(지름 7dp)이 선 위에 살짝 얹혀 보이는 굵기다. */
private val LINE_H = 6.dp

private const val DEPOT_RUN_END = 2.5f     // 도림천 지나 중간쯤에서 기지 진입(배지 소멸)
private const val DEPOT_RUN_SEC = 150f     // 신도림→기지 진입 소요 가정
private const val TAG = "BranchLive"
/** 회차 기억 저장 키 — 값 형식은 [BranchLive.turnMemory] */
private const val TURN_KEY = "turn_memory"

/** 영업시간: 05:30~24:00 + 익일 0~1시(전날 영업 연장). 빈 상태 문구를 고르는 데만 쓴다. */
private fun inService(t: LocalTime = LocalTime.now()): Boolean {
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
    if (showFull) MainLineMapDialog(duty, date) { showFull = false }

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
     */
    val candidates = remember(duty, date) {
        if (duty != null && duty.type in DRIVING_BRANCH) dutyTrainNumbers(duty, date) else emptyList()
    }

    LineMapCard(
        onFullMap = { showFull = true },
        trains = snap.trains,
        inbound = snap.inbound,
        candidates = candidates,
        fetchedAtMillis = snap.fetchedAtMillis,
        nowMillis = now,
        error = snap.error,
        onRefresh = { scope.launch { snap = BranchLive.loadSnapshot(force = true) } },
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
    val mine = candidates.firstNotNullOfOrNull { no -> trains.firstOrNull { it.trainNo == no } }

    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CabNavy),
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
                val nameSp = if (big) 15f else 13f
                val badgeSp = if (big) 11.5f else 9f
                // 배지 한 칸 높이 = 차선 한 단. 계단식 회피가 **한 단**이므로 차선은 두 칸이다.
                val badgeH = if (big) 21.dp else 17.dp
                val laneH = badgeH * 2f + 2.dp
                val nameH = if (big) 21.dp else 18.dp
                // 위 차선(신도림행) → 선 → 역명 → 아래 차선(까치산행).
                val canvasH = laneH + 2.dp + LINE_H + 2.dp + nameH + 2.dp + laneH

                Column(Modifier.padding(vertical = 4.dp)) {
                    BranchHeader(nowMillis, big, onRefresh)
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
                        color = Dim, maxLines = 1, overflow = TextOverflow.Ellipsis,
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

                    Canvas(Modifier.fillMaxWidth().height(canvasH)) {
                        val laneP = laneH.toPx()
                        val badgeP = badgeH.toPx()
                        val lineP = LINE_H.toPx()
                        val stepP = badgeP + 2.dp.toPx()
                        val lineY = laneP + 2.dp.toPx() + lineP / 2f
                        val nameTop = lineY + lineP / 2f + 2.dp.toPx()
                        val dnTop = nameTop + nameH.toPx() + 2.dp.toPx()
                        /** 차선 행 중심 y. `r=0`은 선에 붙은 행, `r=1`은 계단식으로 한 단 물러난 행. */
                        fun rowY(up: Boolean, r: Int) =
                            if (up) laneP - badgeP / 2f - r * stepP else dnTop + badgeP / 2f + r * stepP

                        val pad = 16.dp.toPx()
                        // 역 화면 위치 = 구간 실측시간 비율(상·하행 평균). 표시만 변환(위치 계산은 0~4 유지)
                        val stFrac = floatArrayOf(0f, 0.211f, 0.485f, 0.745f, 1f)
                        fun xOf(pos: Float): Float {
                            val i = pos.toInt().coerceIn(0, 3)
                            val f = (pos - i).coerceIn(0f, 1f)
                            return pad + (stFrac[i] + (stFrac[i + 1] - stFrac[i]) * f) * (size.width - pad * 2)
                        }

                        // ── 선 ────────────────────────────────────────────────
                        drawRect(LoopGreen, topLeft = Offset(0f, lineY - lineP / 2f),
                            size = Size(size.width, lineP))

                        /*
                         * 정차 중인 열차가 서 있는 역(v1.6.49 사용자: *"빨간점은 역에 하얀점에
                         * 넣어줘야지"*). 판정은 v1.6.47의 [holding]을 **그대로** 쓴다 — 새 상태도,
                         * 새 조회도 없다. 0.25칸(≈한 구간의 1/4) 안에 있을 때만 그 역으로 친다:
                         * 종착 회차(pos 3.8)도 신도림으로 잡히고, 구간 한복판의 신호대기는
                         * 어느 역도 물들이지 않는다.
                         * (종전엔 상·하행 선로까지 갈랐지만 선이 하나가 돼 역 번호만 남았다.)
                         */
                        val holdingStops = animated.filter { it.third }.mapNotNull { (_, pos, _) ->
                            val i = Math.round(pos)
                            if (i in BranchLine.stations.indices && abs(pos - i) <= 0.25f) i else null
                        }.toSet()

                        // ── 역 5개 + 이름 ─────────────────────────────────────
                        // 양 끝 역(까치산·신도림)은 이름 반폭이 캔버스 여백(16dp)보다 넓어
                        // 삐져나간다 — 카드 벽에 **4dp 는 남기고** 물린다(실측: 안 남기면
                        // 신도림 글자가 둥근 모서리에 닿아 잘려 보였다).
                        val inset = 4.dp.toPx()
                        fun nameX(l: TextLayoutResult, i: Int) = (xOf(i.toFloat()) - l.size.width / 2f)
                            .coerceIn(inset, (size.width - inset - l.size.width).coerceAtLeast(inset))
                        fun nameLabels(sp: Float) = BranchLine.stations.map { name ->
                            val key = name == KEY_STATION
                            tm.measure(name, TextStyle(
                                fontSize = (sp + if (key) 2f else 0f).sp,
                                fontWeight = if (key) FontWeight.ExtraBold else FontWeight.Bold,
                                color = if (key) KeyOrange else StationWhite))
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
                        BranchLine.stations.forEachIndexed { i, name ->
                            val x = xOf(i.toFloat())
                            val key = name == KEY_STATION
                            val red = i in holdingStops
                            val rad = 3.5.dp.toPx() * (if (key) 1.5f else 1f)
                            drawCircle(
                                if (red) StationRed else if (key) KeyOrange else StationWhite,
                                rad, Offset(x, lineY))
                            if (key || red) drawCircle(
                                if (key) Color.White else Color.White.copy(alpha = 0.55f), rad,
                                Offset(x, lineY), style = Stroke(width = 1.5.dp.toPx()))
                            drawText(labs[i], topLeft = Offset(nameX(labs[i], i), nameTop))
                        }

                        // ── 차선 안내(흐린 글씨) ───────────────────────────────
                        // 배지보다 **먼저** 그린다 — 자리를 다투면 열차가 이긴다(장식이 정보를
                        // 밀어내면 안 된다). 그래서 겹침 판정([boxes])에도 넣지 않는다.
                        fun hint(text: String, y: Float) {
                            val l = tm.measure(text, TextStyle(
                                fontSize = (if (big) 10f else 8.5f).sp,
                                fontWeight = FontWeight.Bold, color = Dim))
                            drawText(l, topLeft = Offset(2.dp.toPx(), y - l.size.height / 2f))
                        }
                        hint("신도림행 ▲", rowY(true, 1))
                        hint("▼ 까치산행", rowY(false, 1))

                        // ── 배지 자리잡기 ──────────────────────────────────────
                        // ⚠ 배지 상자 크기는 **가장 넓은 열번("0000")으로** 잰다(본선과 같은 규칙) —
                        // 실제 열번마다 재면 상자가 들쭉날쭉해져 겹침 판정과 그린 결과가 어긋난다.
                        val probe = tm.measure("0000", TextStyle(
                            fontSize = badgeSp.sp, fontWeight = FontWeight.ExtraBold))
                        val bw = probe.size.width + (if (big) 14 else 11).dp.toPx()
                        val boxes = ArrayList<Rect>()
                        /**
                         * 빈 자리를 찾아 배지 중심을 돌려준다. 두 단 다 막혔으면 `null`(= 점만).
                         * ⚠ 가장자리 물림에 **남색 테두리 2dp 를 더해** 잰다 — 종착역(까치산·신도림)
                         * 배지가 딱 반폭까지만 물러나면 테두리가 카드 밖으로 잘린다.
                         */
                        fun place(x: Float, up: Boolean): Offset? {
                            val edge = bw / 2f + 2.dp.toPx()
                            val cx = x.coerceIn(edge, (size.width - edge).coerceAtLeast(edge))
                            for (r in 0..1) {
                                val cy = rowY(up, r)
                                val rect = Rect(cx - bw / 2f, cy - badgeP / 2f,
                                    cx + bw / 2f, cy + badgeP / 2f)
                                if (boxes.none { it.overlaps(rect) }) { boxes += rect; return Offset(cx, cy) }
                            }
                            return null
                        }
                        // **내 열차부터** 자리를 잡는다(선에 붙은 행을 먼저 가져간다).
                        val mineNo = mine?.trainNo
                        val spots = animated.sortedByDescending { it.first.trainNo == mineNo }
                            .map { (t, pos, _) -> Triple(t, pos, place(xOf(pos), t.toSindorim)) }
                        // 입고 회송(신도림 → 기지)은 왼쪽으로 달리니 **까치산행 차선**이다.
                        val runnerSpots = runnerAnimated.map { (no, pos) ->
                            Triple(no, pos, place(xOf(pos), false))
                        }

                        /** 배지를 접은 열차 — 그래도 **어디 있는지는** 선 위 점으로 남긴다. */
                        fun dotOnly(x: Float) = drawCircle(BadgeSky, 2.5.dp.toPx(), Offset(x, lineY))

                        runnerSpots.forEach { (no, pos, c) ->
                            if (c == null) dotOnly(xOf(pos))
                            else drawNoBadge(tm, c, no, false, bw, badgeP, badgeSp)
                        }
                        spots.filter { it.first.trainNo != mineNo }.forEach { (t, pos, c) ->
                            if (c == null) dotOnly(xOf(pos))
                            else drawNoBadge(tm, c, t.trainNo, false, bw, badgeP, badgeSp)
                        }
                        // ⚠ **내 열차는 맨 나중에** 그린다 — 다른 배지에 가리면 "표시가 안 된다"는 말이 된다.
                        spots.firstOrNull { it.first.trainNo == mineNo }?.let { (t, pos, c) ->
                            if (c == null) dotOnly(xOf(pos)) else {
                                drawNoBadge(tm, c, t.trainNo, true, bw, badgeP, badgeSp)
                                drawDestFlag(tm, c, bw, destOf(t.toSindorim), big)
                            }
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
                                color = if (failed) Color.White else Dim))
                            val hintL = if (!failed) null else tm.measure("오른쪽 위 ↻ 를 눌러 다시 시도",
                                TextStyle(fontSize = (if (big) 11f else 10f).sp, color = Dim))
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
                        BranchChip("지선", big, LoopGreen)
                        // 입고 접근(10분 전~도착 전) — 판정·값은 v1.6.43 그대로, 자리만 칩으로 옮겼다.
                        inbound.map { it to adjEta(it) }
                            .filter { it.second in 1..600 }
                            .minByOrNull { it.second }
                            ?.let { (t, eta) ->
                                BranchChip(
                                    "입고 ${t.trainNo} · " +
                                        if (eta >= 60) "${eta / 60}분 후" else "${eta}초 후",
                                    big, BadgeSky, modifier = Modifier.weight(1f, fill = false),
                                )
                            }
                        Spacer(Modifier.weight(1f))
                        BranchChip("본선 전체 보기", big, LoopGreen, fill = true, onClick = onFullMap)
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
private fun BranchHeader(nowMillis: Long, big: Boolean, onRefresh: () -> Unit) {
    val t = remember(nowMillis / 1_000) {
        LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), ZoneId.systemDefault())
    }
    val sp = (if (big) 17f else 15f).sp
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("신정지선 실시간", fontSize = sp, fontWeight = FontWeight.Bold,
            color = Color.White, maxLines = 1)
        Spacer(Modifier.weight(1f))
        // 시계는 노란색(사용자 확정 — 본선 헤더와 같다).
        Text("%02d:%02d:%02d".format(t.hour, t.minute, t.second),
            fontSize = sp, fontWeight = FontWeight.Bold, color = MineYellow, maxLines = 1)
        RefreshButton(onRefresh)
    }
}

/**
 * 즉시 갱신 버튼 (탭하면 한 바퀴 회전 피드백).
 * 직접 그린 새로고침 글리프 (원호 + 화살촉) — 아이콘 라이브러리 의존성 없음.
 */
@Composable
private fun RefreshButton(onRefresh: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    val spin by animateFloatAsState(tick * 360f, tween(700), label = "spin")
    Surface(
        color = Color.White.copy(alpha = 0.10f),
        shape = CircleShape,
        border = BorderStroke(1.2.dp, LoopGreen.copy(alpha = 0.75f)),
        modifier = Modifier.padding(start = 8.dp).clickable { tick++; onRefresh() },
    ) {
        Canvas(Modifier.padding(5.dp).size(13.dp).rotate(spin)) {
            val c = Color(0xFFB9F5C0)
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

/** 하단 알약 칩 — 본선 상태바 칩과 같은 모양·같은 크기. */
@Composable
private fun BranchChip(
    text: String, big: Boolean, tint: Color, fill: Boolean = false,
    modifier: Modifier = Modifier, onClick: (() -> Unit)? = null,
) = Surface(
    modifier = modifier,
    color = if (fill) tint else tint.copy(alpha = 0.12f),
    shape = RoundedCornerShape(50),
    border = BorderStroke(1.dp, tint.copy(alpha = 0.85f)),
) {
    Text(
        text,
        fontSize = if (big) 12.5.sp else 10.sp, fontWeight = FontWeight.Bold,
        color = if (fill) MineInk else tint,
        maxLines = 1, overflow = TextOverflow.Ellipsis,
        // 누를 수 있는 칩만 클릭 영역을 만든다 — 나머지는 그냥 글씨다.
        modifier = Modifier
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/** 열번 배지 — 옅은 하늘색(내 열차는 노랑) 둥근 사각형 + 진한 글씨. 본선 `drawBadge` 와 같다. */
private fun DrawScope.drawNoBadge(
    tm: TextMeasurer, c: Offset, no: String, mine: Boolean, w: Float, h: Float, sp: Float,
) {
    val lab = tm.measure(no, TextStyle(
        fontSize = sp.sp, fontWeight = FontWeight.ExtraBold,
        color = if (mine) MineInk else BadgeInk))
    val tl = Offset(c.x - w / 2f, c.y - h / 2f)
    // 남색 테두리 — 초록 선·역 점 위에 얹혀도 배지 모양이 살아 있게 한다.
    drawRoundRect(CabNavy, topLeft = Offset(tl.x - 2.dp.toPx(), tl.y - 2.dp.toPx()),
        size = Size(w + 4.dp.toPx(), h + 4.dp.toPx()), cornerRadius = CornerRadius(7.dp.toPx()))
    drawRoundRect(if (mine) MineYellow else BadgeSky, topLeft = tl, size = Size(w, h),
        cornerRadius = CornerRadius(5.dp.toPx()))
    if (mine) drawRoundRect(Color.White, topLeft = tl, size = Size(w, h),
        cornerRadius = CornerRadius(5.dp.toPx()), style = Stroke(width = 1.5.dp.toPx()))
    drawText(lab, topLeft = Offset(c.x - lab.size.width / 2f, c.y - lab.size.height / 2f))
}

/**
 * 내 열차 배지 **옆**의 작은 노란 행선 깃발.
 * 본선은 깃대를 세워 선 바깥으로 뽑지만, 카드는 차선이 얇아 옆에 바로 붙인다 —
 * 오른쪽이 모자라면 왼쪽으로 뒤집는다(종착역에서 카드 밖으로 새지 않게).
 */
private fun DrawScope.drawDestFlag(
    tm: TextMeasurer, c: Offset, bw: Float, dest: String, big: Boolean,
) {
    val lab = tm.measure(dest, TextStyle(
        fontSize = (if (big) 10f else 8.5f).sp, fontWeight = FontWeight.Bold, color = MineInk))
    val w = lab.size.width + 8.dp.toPx()
    val h = lab.size.height + 4.dp.toPx()
    val gap = 3.dp.toPx()
    val x = (c.x + bw / 2f + gap).let {
        if (it + w <= size.width) it else (c.x - bw / 2f - gap - w).coerceAtLeast(0f)
    }
    drawRoundRect(MineYellow, topLeft = Offset(x, c.y - h / 2f), size = Size(w, h),
        cornerRadius = CornerRadius(3.dp.toPx()))
    drawText(lab, topLeft = Offset(x + (w - lab.size.width) / 2f, c.y - lab.size.height / 2f))
}
