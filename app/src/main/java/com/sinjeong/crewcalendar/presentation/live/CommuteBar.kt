package com.sinjeong.crewcalendar.presentation.live

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.sinjeong.crewcalendar.domain.model.TrainMotion
import com.sinjeong.crewcalendar.domain.model.pruneMotions
import com.sinjeong.crewcalendar.presentation.theme.MapStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/*
 * 출퇴근 역 실시간 — 화면 (v1.7.9 ⑦). 순수 로직은 전부 [CommuteLive.kt] 에 있다.
 *
 * ## ⑥ 색은 **지도 스타일**을 따른다 (v1.7.14)
 *
 * 카스: *"**지도스타일도 같이 클레이로** 바뀌면 바뀔수있게!"* — 설정 > 화면 > 지도 스타일을
 * 클레이로 바꾸면 이 줄도 크림·민트로 같이 바뀐다. 색 한 벌은 [paletteOf] 로 뽑는다
 * (본선 지도·지선 카드와 **같은 팔레트**).
 *
 * ⚠ **v1.7.9~v1.7.13 은 `MaterialTheme` 에서 뽑았다** — v1.7.6 확정(*"바꾸는 것은 달력 탭뿐 …
 * 상세시트는 손대지 않는다"*)을 근거로 든 판단이었는데, 그 확정은 **달력 스타일**(`CalendarStyle`)
 * 이야기이지 **지도 스타일**이 아니다. 이 줄은 바로 아래 지선 실시간 카드와 나란히 서는
 * **계기판**이라 지도 쪽 팔레트를 따르는 것이 맞다(카스 확정).
 * 그래서 이 줄은 이제 **라이트/다크 테마를 안 따라간다** — 지도·지선 카드와 같은 이유다.
 */

/**
 * 상세시트 **행로표 위**의 출퇴근 역 줄.
 *
 * · 등록한 역이 없으면 **아무것도 안 그린다**(높이 0).
 * · 칩 한 줄 — 누르면 그 아래가 펼쳐지고 **다른 칩은 접힌다**(한 번에 하나).
 * · 펼친 동안만 **15초에 1회** 조회. 접거나 시트를 닫으면 [LaunchedEffect] 가 취소돼 멎는다.
 *
 * 호출 예산: 칩 하나를 1분 펼쳐 두면 4회다. 지선 카드(10초/4초)와 달리 사용자가 직접 펼쳐야
 * 도는 경로라 평소엔 0회다.
 *
 * ## v1.7.12 ① — 펼친 칸은 **가로 미니 노선 한 줄**(칩 줄은 그대로)
 *
 * 카스: *"아이콘을 좀 더 귀엽게 각호선 색상에 맞게 … 세로칸은 최소화 해서 일자로"*.
 * 종전엔 다가오는 열차 최대 세 대를 **글자 목록**으로 세로로 쌓았다(약 131dp). 이제
 * [CommuteMiniLine] 이 한 줄로 접는다.
 *
 * ## v1.7.13 — ① 기관차가 **1초마다 앞으로** · ③ 더 낮게 · ⑦ 닫는 손잡이 · ⑧ 칩에 방향
 *
 * ① 카스: *"역으로 다가오는 **열차아이콘이 안움직이는데?**"* — v1.7.12 는 15초 폴링 때마다
 * 칸을 뛰고 그 사이엔 멈춰 있었다. 이제 **1초 눈금**([tickMs])이 돌고 자리는 순수 함수
 * [commuteAdvance] 가 남은 초로 칸 사이를 메운다. **API 는 더 안 부른다**(15초 그대로).
 *
 * ⑦ 카스: *"출퇴근역은 **바로 꺼지는 버튼**도 만들어주면 좋지!"* — **고른 칩에 `×` 를 단다.**
 * 닫는 동작은 v1.7.9 부터 있었다(같은 칩을 다시 누르면 접힌다) — 없던 것은 그게 **보이는
 * 손잡이**였다. 칩 안이라 ⓐ 세로가 한 픽셀도 안 늘고 ⓑ 터치 영역이 칩의 48dp 그대로이며
 * ⓒ 누르면 [open] 이 null 이 돼 15초 폴링·1초 눈금이 둘 다 그 자리에서 멎는다.
 *
 * ## v1.7.14 — ② 칩에서 방향 빼기 · ③ 한 단 더 낮게 · ④ 칩을 호선 색으로 · ⑤ 앞뒤 2역 이름 ·
 *              ⑥ 지도 스타일 따라가기 · ⑦ 기관차 모양 통일
 *
 * ② 카스: *"출퇴근역 아이콘에 하행,내선 이런 정보는 안해도 될꺼같애..그래야 가로 크기가
 *   줄어들듯"* — **v1.7.13 ⑧ 을 되무르는 것이고 카스의 결정이다**([commuteChipLabel] KDoc 에
 *   이력을 남겼다). 설정 목록은 방향을 **그대로 둔다**(거기서는 같은 역 두 줄을 갈라 지운다).
 * ④ 카스: *"마곡을 선택했다면 **5호선 마곡 아이콘을 5호선 색으로** 해줘야지"* — 그 "아이콘"이
 *   **칩**이다. 고른 칩은 호선 색으로 꽉 채우고 안 고른 칩은 옅은 테두리만 남긴다. 글자색은
 *   [chipInkArgb] 가 흰·검 중 대비가 큰 쪽을 고른다(5호선 보라 위 흰 글자는 4.12:1 로 모자란다).
 */
@Composable
internal fun CommuteBar(
    stations: List<CommuteStation>,
    /** ⑥ 설정 > 화면 > **지도 스타일**. 색만 정한다(v1.7.14). */
    style: MapStyle = MapStyle.CAB,
    modifier: Modifier = Modifier,
) {
    if (stations.isEmpty()) return
    val pal = paletteOf(style)
    var open by remember { mutableStateOf<CommuteStation?>(null) }
    var rows by remember { mutableStateOf(emptyList<PositionRow>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    /** ③ 응답의 **기준 시각**과 그것을 **처음 본 우리 시각** — 나이를 재는 두 값이다. */
    var recptnDt by remember { mutableStateOf("") }
    var recptnAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    /** ② 1초 눈금 — **보간만** 돌린다(API 는 위 폴링만 부른다). */
    var uiNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
    /** ② 열차별 화면 위치 장부([stepCommute]). 지도의 `motions` 와 같은 물건이다. */
    val motions = remember { mutableMapOf<String, TrainMotion>() }

    /*
     * ① **실시간 위치 폴링**(v1.7.15) — `realtimePosition/{호선명}` 을 15초에 한 번.
     *
     * ⚠ **키가 호선(`subwayId`)이지 역이 아니다.** 위치 응답은 그 호선 전체를 주므로 같은
     * 호선의 다른 역 칩으로 옮겨도 **다시 부르지 않는다**(카스 지시: *"호출은 호선마다 한 번"*).
     * 칩은 한 번에 하나만 펼쳐지므로 **한 순간에 도는 폴링은 늘 하나**다.
     * 접으면(null) 조기 반환 후 루프가 아예 안 돌고, 시트를 닫으면 [LaunchedEffect] 가 취소된다.
     *
     * 눈금은 **절대 시각**으로 놓는다 — `delay(3_000)` 로 재우면 네트워크에 쓴 시간과 delay
     * 오버슈트가 누적돼 주기가 밀린다(근거는 [BranchLiveMap] 폴링 KDoc, v1.6.72 실측).
     *
     * ⚠ **주기는 15초 → 3초**(v1.7.16 ①, 카스 지시). 서버는 18~22초마다 값을 바꾸므로
     * 자료가 더 새로워지지는 않는다 — 줄어드는 것은 **새 값을 받기까지의 기다림**(평균 =
     * 주기의 절반)이라 7.5초 → 1.5초다. 근거와 한계는 [BranchLive.POLL_INTERVAL_MS] KDoc.
     * ⚠ **1초 눈금을 되살렸다**(v1.7.16 ②) — 칸 사이는 [stepCommute] 가 등속으로 메운다.
     */
    LaunchedEffect(open?.subwayId) {
        val s = open ?: return@LaunchedEffect
        rows = emptyList(); error = null; loading = true
        var next = System.currentTimeMillis()
        while (isActive) {
            BranchLive.positionsOfLine(lineName(s.subwayId))
                .onSuccess {
                    rows = it; error = null
                    // ③ 값이 그대로면 처음 본 시각을 지킨다 — 그래야 "몇 초째 안 바뀌나"가 산다.
                    val dt = latestRecptn(it)
                    recptnAt = recptnFirstSeen(recptnDt, recptnAt, dt, System.currentTimeMillis())
                    recptnDt = dt
                }
                .onFailure { error = BranchLive.humanError(it) }
            loading = false
            next += BranchLive.POLL_INTERVAL_MS
            val now = System.currentTimeMillis()
            if (next < now) next = now + BranchLive.POLL_INTERVAL_MS
            delay(next - now)
        }
    }
    /*
     * ② **1초 눈금** — 보간만 돌린다(v1.7.16). 이 카드는 연기·물결이 없어 캔버스를 다시
     * 그릴 이유가 저절로 생기지 않는다([CommuteMiniLine] 은 `smoke = false`) — 그래서
     * [uiNow] 한 값이 컴포지션을 깨워 [stepCommute] 를 한 걸음 돌린다.
     * 접으면(`open == null`) 키가 바뀌어 이 루프도 그 자리에서 멎는다.
     */
    LaunchedEffect(open?.subwayId) {
        if (open == null) return@LaunchedEffect
        while (isActive) {
            uiNow = System.currentTimeMillis()
            delay(1_000)
        }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(CHIP_GAP)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).shrinkHeight(CHIP_ROW_H),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            stations.forEach { s ->
                // ④ **칩이 호선 색**이다(v1.7.14). 고른 칩은 꽉 찬 호선 색 + 대비로 고른 글자색,
                //   안 고른 칩은 **같은 색을 옅게**(바탕 0.14 · 테두리 0.55) — 한눈에 몇 호선인지
                //   보이면서 무엇을 고른 상태인지도 갈린다.
                val line = Color(lineArgb(s.subwayId))
                val ink = Color(chipInkArgb(lineArgb(s.subwayId)))
                FilterChip(
                    selected = open == s,
                    onClick = { open = if (open == s) null else s },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = line.copy(alpha = 0.14f),
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        iconColor = MaterialTheme.colorScheme.onSurface,
                        selectedContainerColor = line,
                        selectedLabelColor = ink,
                        selectedLeadingIconColor = ink,
                        selectedTrailingIconColor = ink,
                    ),
                    border = BorderStroke(1.dp, line.copy(alpha = if (open == s) 1f else 0.55f)),
                    label = {
                        // ② **방향을 뺀다**(v1.7.14) — `5호선 마곡`. 카스가 v1.7.13 ⑧ 을 되물렀다.
                        Text(
                            commuteChipLabel(s),
                            fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    },
                    // ⑦(v1.7.13) **닫는 손잡이** — 고른 칩에만 붙는다.
                    trailingIcon = if (open != s) null else {
                        { Icon(Icons.Filled.Close, "닫기", Modifier.size(14.dp)) }
                    },
                )
            }
        }
        open?.let { s ->
            /*
             * ① **응답이 말한 자리에만 세운다**(v1.7.15). [commuteTrains] 가 ⓐ 같은 호선
             * ⓑ 같은 방향 ⓒ `statnNm` 이 **다섯 칸 이름 중 하나**인 열차만 남긴다.
             * 다섯 칸 밖 열차는 목록에 아예 없다 — **맨 왼쪽에 밀어 넣지 않는다.**
             */
            val trains = commuteTrains(rows, s)
            val lead = commuteLead(trains, s.fromHigher)
            /** ③ 기준 시각이 [RECPTN_STALE_SEC] 넘게 안 바뀌었나 — 그러면 붉게 말한다. */
            val stale = recptnStale(recptnDt, recptnAt, uiNow)
            Surface(
                // ⑥ 지도 스타일을 따라간다 — 남색이면 운전실, 클레이면 크림(v1.7.14).
                color = pal.bg,
                contentColor = pal.label,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = CARD_PAD),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (lead == null) {
                        // 빈 상태·오류는 **한 줄로만** 말한다(확정 표 — 문구는 v1.7.9 그대로).
                        // ⚠ `역 정보 없음` 은 v1.7.15 ② 에서 늘었다 — 옛 저장값(칸 셋·넷)은
                        //   다섯 칸 이름이 없어 위치 응답과 견줄 상대가 없다. 조용히 비우지 않고
                        //   **다시 등록하라고 말한다**([decodeCommute] KDoc).
                        Text(
                            when {
                                s.stops.size != COMMUTE_SLOTS ->
                                    "역을 다시 등록해 주세요 (앞뒤 역 정보 없음)"
                                error != null -> error!!
                                loading -> "실시간 조회 중…"
                                !inService() -> "운행 시간이 아닙니다"
                                else -> "다가오는 열차가 없습니다"
                            },
                            fontSize = 12.sp, color = pal.label,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        /*
                         * ⚠ **칸 키는 배율을 따라간다.** 오른쪽 두 줄은 sp 라 배율 1.5 에서
                         * 두 배 가까이 되는데 미니 노선을 dp 로 못 박아 두면 그 차이만큼 캔버스가
                         * 비고, 밑 라벨(sp)이 두꺼워진 만큼 **기관차만 작아진다**
                         * (v1.7.13 ③ 실측: 배율 1.5 에서 `k` 가 1.0 → 0.67 로 떨어졌다).
                         * 두 줄과 같은 키를 주면 칸 높이는 그대로면서 기관차가 제 크기를 지킨다.
                         */
                        val miniH = with(LocalDensity.current) {
                            /*
                             * ⚠ **긴 역 이름은 두 줄로 접힌다** — 확정 표: *"자리가 모자라면
                             * 글자를 줄이지 이름을 줄이지 않는다. 두 줄로 접는 것은 허용"*.
                             * 그때 칸을 안 키우면 라벨이 먹은 만큼 **기관차만 작아진다**
                             * (v1.7.13 ③ 과 똑같은 함정 · 실측으로 `동대문역사문화공원` 에서 났다).
                             * 글자 수는 **어림**이고 실제 접기는 캔버스가 폭을 재서 정한다 —
                             * 어림이 빗나가면 칸이 조금 클 뿐이다.
                             *
                             * ⚠ **라벨 자리는 sp, 기관차 자리는 dp** 다(v1.7.15 ③). 종전처럼
                             * 통째로 dp 상수([MINI_MIN_H])로 잡으면 배율 1.5 에서 라벨만
                             * 두꺼워져 기관차가 `k = 0.446` 까지 눌리고(실측), 몸통이 20.5dp 로
                             * 줄어 **열번 네 자리가 몸통 밖으로 넘친다**(확정 표 *"열번은 아이콘
                             * 안에"* 위반). 그래서 **라벨(sp) + 기관차(dp)** 로 나눠 더한다 —
                             * 어느 배율에서도 기관차는 [LOCO_MAX_K] 를 지킨다.
                             */
                            val long = (s.stops + s.name).any { it.length >= LONG_NAME_LEN }
                            val label = NAME_SP.sp.toDp() * (if (long) 2.7f else 1.35f)
                            maxOf(label + 2.dp + LOCO_MIN_H, ETA_LINE.toDp() + DEST_LINE.toDp())
                        }
                        CommuteMiniLine(s, trains, pal, motions, uiNow,
                            Modifier.weight(1f).height(miniH))
                        Spacer(Modifier.width(6.dp))
                        /*
                         * ⚠ **칸 높이를 정하는 것은 미니 노선이 아니라 이 두 줄이다**(v1.7.13 ③ 실측).
                         * 글꼴 기본 줄높이는 글자 크기의 1.4배쯤이라 두 줄이 미니 노선을 이긴다 —
                         * 그래서 줄높이를 **박아 준다**(글자의 1.15~1.2배). v1.7.14 ③ 에서 한 단 더
                         * 내렸다(15/10sp · 18/12sp → **14/9sp · 16/11sp**). **되돌리면 ③ 이
                         * 통째로 되돌아간다.**
                         */
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                /*
                                 * ① **`몇 분 후` 가 사라진 자리**(v1.7.15). 위치 API 에는 남은
                                 * 초가 없다 — 카스가 알고 고른 맞바꿈이다. 대신 응답이 실제로
                                 * 주는 `trainSttus` 를 낱말 하나로 적는다([sttusText] — 본선
                                 * 지도가 쓰는 것과 **같은 표**). 앞에 역 이름을 붙이지 않는
                                 * 이유는 **미니 노선이 이미 그 역 위에 기관차를 세워** 같은 말을
                                 * 두 번 하게 되기 때문이다(확정 표 *"빈 상태·오류 자리"* 의 취지).
                                 */
                                // ⑥ 막차면 `도착 · 막차` — 자리와 이유는 [commuteStatusLine] KDoc.
                                commuteStatusLine(lead),
                                fontSize = 14.sp, lineHeight = ETA_LINE,
                                fontWeight = FontWeight.ExtraBold,
                                // ⚠ 글자색은 **팔레트의 기본 잉크**다 — `pal.clock`(클레이 주황
                                //   `#D98A2B`)은 크림 위에서 **2.45:1** 이라 못 읽는다(실측).
                                //   막차만 예외로 [MapPalette.fail] 을 쓴다(두 팔레트 다 대비 확인).
                                color = if (lead.lastCar) pal.fail else pal.label,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                // ③ `방화행 · 20:54:27` — 뒤가 응답의 기준 시각이다.
                                //   낡으면(60초째 그대로) 붉게 바뀌어 통신이 끊긴 것을 말한다.
                                commuteDestLine(lead.dest, recptnDt),
                                fontSize = 9.sp, lineHeight = DEST_LINE,
                                color = if (stale) pal.fail else pal.label,
                                fontWeight = if (stale) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}


/* ── 미니 노선 한 줄 (v1.7.12 ① · v1.7.14 ⑤ 에서 앞뒤 2역 + 역명) ── */

/**
 * 기관차 한 대가 **선 위로 꼭 가져야 하는 높이**(dp) — 굴뚝 갓 꼭대기에서 바퀴 아랫날까지를
 * [LOCO_MAX_K] 배율로 잰 값이다. `28.7 × 0.62 = 17.8dp`.
 *
 * 카스: *"세로칸은 최소화 해서 일자로 보여주면 좋지"* → 펼친 칸은 세로로 쌓던 글자 목록
 * (약 131dp)을 **한 줄**로 접은 자리다. 40dp(v1.7.12) → 32dp(v1.7.13 ③) → 28dp(v1.7.14 ③)
 * → **v1.7.15 ③ 부터는 상수가 아니라 `라벨(sp) + 2dp + 이 값(dp)`** 이다.
 *
 * ⚠ **여기가 하한이다.** 더 내리면 몸통이 줄어 **열번 네 자리가 안 든다**(확정 표
 * *"열차 아이콘 = 열번 상자"*): 열번 띠 높이 `16 × k` 가 글자 높이(7.7dp × 1.25 ≈ 9.6dp)를
 * 담으려면 `k ≥ 0.60` 이 필요하다. 카스가 또 줄이라 하면 이 맞바꿈을 먼저 물어라
 * (지선 카드 `CARD_K` 와 같은 성격의 손잡이다).
 * ⚠ **실측으로 카드는 한 픽셀도 안 컸다** — 칸 키를 정하는 것은 늘 오른쪽 두 줄
 * ([ETA_LINE] + [DEST_LINE])이고 그쪽이 배율 1.0 에서 33.2dp · 1.5 에서 40.5dp 라
 * 이 합(1.0 = 32.0dp · 1.5 = 38.0dp)을 늘 이긴다.
 */
private val LOCO_MIN_H = (LOCO_TOTAL_H * LOCO_MAX_K).dp

/**
 * 오른쪽 두 줄의 **줄높이**(v1.7.13 ③ · v1.7.14 ③ 에서 한 단 더). 글꼴 기본 줄높이는 글자의
 * 1.4배쯤이라 그냥 두면 이 두 줄이 미니 노선을 이겨 칸 높이를 혼자 정한다.
 * 두 값의 합은 27sp 이고 [MINI_MIN_H](31dp)가 그보다 크므로 **칸의 키는 미니 노선이 정한다**
 * (v1.7.14 까지는 반대였다 — 한쪽만 고치면 세로가 도로 는다).
 */
private val ETA_LINE = 16.sp
private val DEST_LINE = 11.sp

/**
 * 칩 줄이 **부모에게 말하는 높이**(v1.7.13 ③ · v1.7.14 ③ 에서 34 → 32dp).
 * `FilterChip` 은 머티리얼의 `minimumInteractiveComponentSize` 때문에 **48dp** 로 재는데
 * 보이는 알약은 **정확히 32dp** 뿐이라 위아래로 8dp 씩 빈 띠가 남는다 — [shrinkHeight] 가
 * 신고 높이만 줄인다. **32dp 가 하한이다**(더 줄이면 알약이 잘린다).
 * **터치 영역은 48dp 그대로다**(노드가 제 크기를 그대로 보고한다).
 */
private val CHIP_ROW_H = 32.dp

/** 칩 줄 ↔ 카드 틈. 6dp(v1.7.12) → 4dp(v1.7.13 ③) → **3dp**(v1.7.14 ③). */
private val CHIP_GAP = 3.dp

/** 카드 안쪽 위아래 여백. 5dp(v1.7.12) → 4dp(v1.7.13 ③) → **3dp**(v1.7.14 ③). */
private val CARD_PAD = 3.dp

/**
 * 기관차 배율 상한. 0.5(v1.7.14 ⑦ · 몸통 23dp) → **0.62**(v1.7.15 ③ · 몸통 **28.5dp**).
 *
 * 카스: *"지금 열차 아이콘이 얼마나 이쁜데..그리고 **열번도 작게 넣어도 될텐데?**"* —
 * 모양은 그대로 두고 **몸통 안에 열번**을 넣는다(확정 표 *"열차 아이콘 = 열번 상자"*).
 * 0.5 에서는 열번 띠가 `39 × 8dp` 라 네 자리가 물리적으로 안 들어간다:
 * 띠 높이 `16 × k` 가 글자 높이(7.7sp × 1.25 ≈ 9.6dp)를 담으려면 **`k ≥ 0.60`**,
 * 띠 폭 `39 × k` 가 네 자리(≈19dp)를 담으려면 `k ≥ 0.49` — 세로가 하한을 정한다.
 * ⚠ 본선 지도 0.55 · 지선 카드 0.70 사이 값이다. 가로는 칸 다섯이 이름을 이고 서 있어
 *   빠듯하지만 몸통 28.5dp 는 접힘 실측 칸 폭(≈50dp)의 절반 남짓이라 든다.
 */
private const val LOCO_MAX_K = 0.62f

/**
 * 열번 **판독 하한** — 확정 표가 정한 값 그대로다: 본선 타 열차가 `11 × 0.7 = 7.7sp` 이고
 * 지선 카드 `NUMBER_MIN_SP` 도 같은 7.7 이다. 여기 자연 크기는 `11 × 0.62 = 6.8sp` 라
 * **하한이 먹는다** — [drawLoco] 의 `numberMinSp` 가 그 손잡이다(v1.7.10 에서 지선 카드를
 * 위해 낸 인자를 그대로 쓴다. 본선 지도는 여전히 안 넘긴다).
 *
 * ⚠ **글자배율로 나눠서 넘긴다** — 이 숫자가 사는 곳은 **dp 로 크기가 정해진 몸통 안**이다
 * (확정 표 *"열번은 아이콘 안에 있다"*). sp 그대로 넘기면 배율 1.5 에서 글자만 1.5배가 돼
 * 네 자리가 몸통 밖으로 삐져나온다(실측). 나눠 넘기면 어느 배율에서도 **물리 크기가 7.7dp** —
 * 판독 하한 그대로이고 몸통 안에 든다. 몸통은 dp 라 배율과 무관하게 28.5dp 를 지킨다.
 */
private const val NUMBER_MIN_SP = 7.7f

/**
 * 기관차 한 대가 선 위로 차지하는 높이(그림 좌표) — 굴뚝 갓 꼭대기(−15.2)에서 바퀴
 * 아랫날([LOCO_WHEEL_BOTTOM] = 13.5)까지. 배율 1 일 때 dp 값이고, 선 위에 남은 자리를
 * 이 값으로 나눈 것이 배율이다.
 */
private const val LOCO_TOTAL_H = 15.2f + LOCO_WHEEL_BOTTOM

/**
 * 기관차가 **역 점을 덮는** 거리(칸 단위)를 실제 몸통 반폭에서 낸다 — 이보다 가까우면 그 점을
 * 안 찍는다. 점의 흰 속이 바퀴 사이로 삐져나와 **턱수염처럼** 보였다(v1.7.12 실측 크롭 둘).
 * v1.7.13 까지는 `0.35` 상수였는데 이제 배율·칸 간격이 둘 다 움직여 **계산값**이다.
 */
private fun coversSlots(bodyHalfPx: Float, dotPx: Float, stepPx: Float) =
    if (stepPx <= 0f) 0f else (bodyHalfPx + dotPx) / stepPx

/**
 * **가로 미니 노선 한 줄** — 가운데가 **등록한 그 역**([COMMUTE_HERE])이고 왼쪽 둘이 앞 2역,
 * 오른쪽 둘이 뒤 2역이다(v1.7.14 ⑤). 카스: *"마곡역이면 **김포공항-송정-마곡-발산-우장산**
 * 이렇게 표시해주고 **역명까지 표시**해주자!"*
 *
 * ## ② 차례는 고정 · 방향은 **기관차가 말한다** (v1.7.15)
 *
 * 카스: *"상행을 고르면 **열차가 반대방향으로 가면 되지**"* · *"마곡역 **송정방면**으로
 * 지정했을때 **열차가 반대방향으로 움직여야지**?"*
 *
 * 칸 차례는 [CommuteStation.stops] 가 준 **지리 순서 고정**(`FR_CODE` 오름차순)이고,
 * [CommuteStation.fromHigher] 가 true 면 기관차 머리가 [Heading.LEFT] 로 돌아
 * **오른쪽에서 왼쪽으로** 달린다. 뒤집는 곳은 지도와 같은 순수 함수 두 개
 * ([headingFor] · [locoFlip])뿐이고, `trainSttus` 미세 오프셋([commuteOffset])도 같은
 * 방향 부호를 탄다 — 진입은 늘 **진행 방향 앞쪽**이다.
 *
 * ⚠ **바퀴는 늘 선로 쪽**(확정 표) — `railTowards = (0, +1)` 이라 [locoFlip] 이 좌우 어느
 *   머리에서도 false 다(배 벡터가 둘 다 `(0, +1)`). **열번은 가로 좌→우**([locoTextDeg] 가
 *   `mapDeg = 0` 에서 RIGHT·LEFT 둘 다 0° 를 준다).
 *
 * 색은 **선·점·기관차 몸통이 호선 색**([lineArgb])이다 — 카스: *"아이콘을 좀 더 귀엽게 각호선
 * 색상에 맞게"*. **글자는 팔레트 잉크**([MapPalette.label])다 — 1호선 남색(`#0052A4`)·7호선
 * 올리브(`#747F00`) 같은 어두운 호선색은 어느 바탕에서도 작은 글자로 못 읽는다.
 */
@Composable
private fun CommuteMiniLine(
    station: CommuteStation,
    trains: List<CommuteTrain>,
    pal: MapPalette,
    /** ② 열차별 화면 위치 장부 — 프레임마다 [stepCommute] 가 한 걸음씩 옮긴다(v1.7.16). */
    motions: MutableMap<String, TrainMotion>,
    /** ② 1초 눈금. 이 값이 바뀌어야 캔버스가 다시 그려진다(연기가 없어 저절로는 안 돈다). */
    nowMs: Long,
    modifier: Modifier,
) {
    val line = Color(lineArgb(station.subwayId))
    val ink = pal.label
    val tm = rememberTextMeasurer()
    /*
     * 다섯 칸의 이름 — [CommuteStation.stops] 그대로다(v1.7.15 ② — 지리 순서 고정이라
     * 화면에서 다시 뒤집을 것이 없다). 못 얻은 등록값이면 카드가 이 줄까지 안 오고
     * *"역을 다시 등록해 주세요"* 로 떨어진다([CommuteBar]).
     */
    val names = remember(station) {
        List(COMMUTE_SLOTS) { i -> station.stops.getOrNull(i).orEmpty() }
    }
    /** ② **화면에서의 진행 방향** — `false` 면 오른쪽에서 왼쪽으로 달린다. */
    val forward = !station.fromHigher
    /*
     * ② **한 걸음** — 규칙(앞으로만 · 다음 칸 안 넘음 · 도착·진입은 정지)은 지도가 쓰는
     * [stepCommute] → `stepMotion` 한 곳이다. 여기서 새로 세지 않는다.
     * ⚠ 목록에서 사라진 열차의 기억은 2분 뒤 지운다([pruneMotions]) — 그 안에 다시 나타나면
     *   **이어서** 달리고, 넘으면 새 열차로 쳐서 목표 자리에서 시작한다.
     */
    val placed = run {
        pruneMotions(motions, trains.mapTo(HashSet()) { it.trainNo }, nowMs)
        trains.map { t ->
            val m = stepCommute(motions[t.trainNo], t, forward, nowMs)
            motions[t.trainNo] = m
            t to m.pos
        }
    }
    Canvas(modifier) {
        val step0 = size.width / COMMUTE_SLOTS          // 이름 한 칸이 쓸 수 있는 폭(어림)
        /*
         * 이름이 다섯이라 **글자를 줄여** 맞춘다(확정 표: *"자리가 모자라면 글자를 줄이지
         * 이름을 줄이지 않는다. 두 줄로 접는 것은 허용"*). 9sp 로 재 보고 넘치면 비례로 줄이되
         * **7sp 가 하한**(본선 지도 역명 판독 하한과 같은 값)이고, 그래도 넘치면 **두 줄**로 접는다.
         */
        fun width(sp: Float, s: String) =
            if (s.isBlank()) 0 else tm.measure(
                s, TextStyle(fontSize = sp.sp, fontWeight = FontWeight.Bold), maxLines = 1,
            ).size.width
        val wide = names.maxOf { width(NAME_SP, it) }.toFloat()
        val room = (step0 - 3.dp.toPx()).coerceAtLeast(1f)
        // ⚠ `× 0.97` 은 반올림 여유다 — 딱 맞게 줄이면 다시 잰 폭이 1~2px 넘쳐 **두 줄로
        //   접혀 버린다**(실측: `구로디지털단지` 가 7.14sp 에서 그랬다). 여유를 주면 7자까지는
        //   한 줄로 산다.
        val nameSp = (if (wide <= room) NAME_SP else NAME_SP * room / wide * 0.97f)
            .coerceAtLeast(NAME_MIN_SP)
        val twoLines = names.any { width(nameSp, it) > room }
        val labels = names.mapIndexed { i, s ->
            if (s.isBlank()) null else tm.measure(
                s,
                TextStyle(
                    fontSize = nameSp.sp, color = ink,
                    fontWeight = if (i == COMMUTE_HERE) FontWeight.ExtraBold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                ),
                maxLines = if (twoLines) 2 else 1,
                overflow = TextOverflow.Visible,
                constraints = androidx.compose.ui.unit.Constraints(maxWidth = room.toInt()),
            )
        }
        // 라벨은 선 밑에 눕는다 — 캔버스 아래쪽을 라벨 높이만큼 비우고 나머지가 그림 자리다.
        val labelH = labels.filterNotNull().maxOfOrNull { it.size.height }?.toFloat() ?: 0f
        val y = size.height - labelH - 2.dp.toPx()
        // 양 끝 여백 = 한 칸의 절반 — 다섯 이름이 저마다 제 칸 가운데에 눕는다.
        val step = size.width / COMMUTE_SLOTS
        val x0 = step / 2f
        fun xOf(slot: Float) = x0 + step * slot.coerceIn(0f, (COMMUTE_SLOTS - 1).toFloat())

        // ⑦ **지도와 같은 기관차**([drawLoco]) — 배율은 선 위에 남은 자리에 맞춘다.
        // 글자배율을 키우면 라벨이 두꺼워져 선이 올라오는데, 그때 안 줄이면 굴뚝이 카드 위로
        // 삐져나간다(v1.7.13 ③ 배율 1.5 실측 자리).
        val k = (y / (LOCO_TOTAL_H * 1.dp.toPx())).coerceIn(0.28f, LOCO_MAX_K)
        val covers = coversSlots(LOCO_BOX_W / 2f * k * 1.dp.toPx(), 3.2.dp.toPx(), step)
        fun covered(i: Int) = placed.any { kotlin.math.abs(it.second - i) < covers }

        // ⚠ 크림 바탕(클레이)에서는 옅은 호선색이 묻힌다 — 2호선 초록 `#00A84D` 이 크림 위에서
        //   2.78:1 뿐이라 0.32 알파로는 선이 안 보인다(실측). 남색은 4.66:1 이라 종전 값 그대로.
        drawLine(
            line.copy(alpha = if (pal.clay) 0.55f else 0.32f),
            Offset(x0, y), Offset(xOf((COMMUTE_SLOTS - 1).toFloat()), y),
            strokeWidth = 3.5.dp.toPx(), cap = StrokeCap.Round,
        )
        // 지나온 역·앞으로 갈 역은 작은 흰 점 + 호선색 테, **등록한 역**만 꽉 찬 큰 점이다.
        // ⚠ 기관차가 선 칸엔 점을 안 찍는다 — 점이 바퀴 사이로 삐져나와 **턱수염처럼** 보였다.
        for (i in 0 until COMMUTE_SLOTS) {
            if (covered(i)) continue
            val c = Offset(xOf(i.toFloat()), y)
            if (i == COMMUTE_HERE) drawCircle(line, 5.4.dp.toPx(), c)
            else {
                drawCircle(Color.White, 3.2.dp.toPx(), c)
                drawCircle(line, 3.2.dp.toPx(), c, style = Stroke(1.8.dp.toPx()))
            }
        }
        labels.forEachIndexed { i, l ->
            if (l != null) drawText(
                l,
                topLeft = Offset(xOf(i.toFloat()) - l.size.width / 2f, y + 2.dp.toPx()),
            )
        }

        /*
         * 기관차 — **본선 지도·지선 카드와 같은 [drawLoco]** 다(v1.7.14 ⑦).
         * 카스: *"**신정지선과 본선 열차 아이콘과 같은 열차 아이콘 모양**으로 해줘."*
         * v1.7.15 에서도 **모양은 한 줄도 안 건드렸다** — 카스: *"지금 열차 아이콘이 얼마나
         * 이쁜데.."*. 바뀐 것은 몸통 배율([LOCO_MAX_K])과 **몸통 안 열번**뿐이다.
         *
         * ③ ⚠ **열번은 위치 응답의 `trainNo`** 다(v1.7.15). 확정 표 *"열차 아이콘 = 열번
         *   상자 · 열번을 아이콘 밖 배지로 빼지 말 것"* 그대로 몸통 가운데에 든다. 크기는
         *   `11 × k = 6.8sp` 지만 [NUMBER_MIN_SP] 7.7sp 가 받친다 — 지선 카드가 v1.7.10 에
         *   낸 `numberMinSp` 손잡이를 그대로 쓴다(**본선 지도는 여전히 안 넘긴다**).
         * ② ⚠ **머리가 진행 방향**이다 — [forward] 가 false 면 [Heading.LEFT] 라 오른쪽에서
         *   왼쪽으로 달린다. [locoFlip] 은 두 머리 모두 false(배 벡터가 `(0, +1)` 로 같다)라
         *   **바퀴는 어느 쪽이든 선로 쪽**이고, [locoTextDeg] 도 둘 다 0° 라 **열번은 가로
         *   좌→우**다(확정 표 두 줄을 다 지킨다).
         * ⚠ **연기·물결은 끈다** — 지도에서도 내 열차만 내는 장식이고, 여기서는 굴뚝 위로
         *   12dp 를 더 먹어 카드 밖으로 나간다.
         * ⚠ **떠 있는 열차 금지** — 중심을 `y − LOCO_WHEEL_BOTTOM × k` 에 놓아 바퀴 아랫날이
         *   선 위에 앉는다.
         */
        placed.forEach { (t, pos) ->
            drawLoco(
                center = Offset(xOf(pos), y - LOCO_WHEEL_BOTTOM * k * 1.dp.toPx()),
                heading = headingFor(1f, 0f, forward),
                scale = k,
                body = line,
                wheel = pal.wheel,
                number = t.trainNo,
                numberColor = pal.otherInk,
                textMeasurer = tm,
                smoke = false,
                wake = false,
                railTowards = Offset(0f, 1f),         // 선로는 늘 밑에 있다
                // ⚠ 배율로 나눈다 — 위 [NUMBER_MIN_SP] KDoc. 몸통이 dp 라 글자도 dp 로 붙든다.
                numberMinSp = NUMBER_MIN_SP / fontScale.coerceAtLeast(1f),
                shadowColor = pal.shadow,
                clayShadow = if (pal.clay) 2 else 0,
            )
        }
    }
}

/** 역명 글자 크기(sp)와 **판독 하한** — 하한은 본선 지도 역명과 같은 값이다(확정 표). */
private const val NAME_SP = 9f
private const val NAME_MIN_SP = 7f

/**
 * 이 글자 수부터는 **두 줄로 접힐 수 있다고 본다**(칸 높이를 미리 키우는 어림).
 * 접힐 7자 `구로디지털단지` 까지는 글자를 줄여 한 줄로 산다(실측) — 거기서
 * 끊었다. 본선 지도의 `LONG_NAME_LEN`(5)과 다른 값인 것은 이 칸이 더 좀기 때문이다.
 */
private const val LONG_NAME_LEN = 8

/* ── 설정 > 출퇴근 역 등록 ──────────────────────────────────── */

/**
 * 등록 화면. **역 목록 자산을 만들지 않는다** — 수도권 700여 역을 번들하면 낡는다.
 * 대신 입력한 이름으로 도착 API 를 **최대 2회** 불러(①) 응답에 실제로 있던 (호선 × 방향)을
 * 칩으로 준다.
 *
 * ## ① `역` 을 붙여 쳐도 찾아진다 (v1.7.14)
 *
 * 카스: *"출퇴근역 **검색에 마곡, 이면 마곡역으로까지 검색**되게 해줘!"* — 도착 API 는
 * `마곡` 으로만 답한다(`마곡역` 은 `INFO-200` = 0건, 2026-09-07 실호출). [stationQueries] 가
 * 두 꼴을 내고 **첫째가 비면 둘째를 한 번 더** 부른다 — **호출은 최대 2회**다.
 * 저장은 **실제로 답이 온 이름**이다. 그 이름이 곧 다음 조회의 열쇠라 답 없는 이름을
 * 저장하면 화면이 영영 빈다. ⚠ **이름을 줄이는 것이 아니다**(확정 표) — `구로디지털단지` 는
 * 통째로 남고, `서울역` 은 원문이 먼저라 첫 번에 걸린다.
 *
 * ## ⑤ 다섯 칸 이름은 **고를 때 딱 한 번** 부른다 (v1.7.14 · v1.7.15 ②)
 *
 * 방향 칩을 누르는 순간 역 목록 API 를 **1회**([BranchLive.stationsOfLine]) 불러 앞뒤 2역과
 * 등록역까지 **다섯 이름**을 저장값에 담는다. 보는 화면은 그 값을 읽기만 하므로 **볼 때마다
 * 부르지 않는다.**
 *
 * ⚠ **여기는 도착 API 를 그대로 쓴다** — v1.7.15 ① 이 실시간 위치로 바꾼 것은 **보는 화면**
 * 이다. (호선 × 방향) 조합을 주는 것은 도착 응답뿐이고, 열차가 **어느 쪽에서 오나**
 * ([approachFromHigher])도 여기서만 알 수 있다(위치 응답에는 그 역 기준의 이전역이 없다).
 * 못 얻으면 이름 없이 저장하고, 그때 보는 화면은 *"역을 다시 등록해 주세요"* 라고 말한다.
 *
 * ⚠ 운행 종료 시간엔 `INFO-200`(빈 목록)이라 조합을 못 뽑는다 — **오류가 아니라 안내**다.
 */
@Composable
internal fun CommuteSettingDialog(
    stations: List<CommuteStation>,
    onSave: (List<CommuteStation>) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var asked by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var options by remember { mutableStateOf(emptyList<CommuteOption>()) }
    var msg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
            Column(
                Modifier.padding(18.dp).heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("출퇴근 역", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                Text(
                    "등록한 역은 달력 상세시트 행로표 위에 뜹니다. 칩을 누르면 그 역 앞뒤 " +
                        "${COMMUTE_NEAR}역과 그 사이 열차의 실시간 위치가 보입니다 (최대 ${COMMUTE_MAX}개).",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                stations.forEach { s ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        // ⚠ **여기는 방향을 남긴다**(v1.7.14 ②) — 칩에서는 뺐지만 이 목록은
                        // 같은 역 두 줄을 갈라 **지우는** 자리라 글자가 같으면 무엇을 지우는지
                        // 알 수 없다([commuteChipLabel] KDoc).
                        Text(
                            commuteLabel(s), Modifier.weight(1f),
                            fontWeight = FontWeight.Bold,
                        )
                        TextButton(onClick = { onSave(stations - s) }) { Text("삭제") }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                if (stations.size >= COMMUTE_MAX) {
                    Text(
                        "${COMMUTE_MAX}개까지 등록할 수 있습니다 · 하나를 지우면 더 넣을 수 있어요",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it; options = emptyList(); msg = null },
                        // ① `마곡` 이든 `마곡역` 이든 찾아진다 — 줄여 쓰는 것만 안 된다.
                        label = { Text("역 이름 — 마곱 · 마곱역 둘 다 됩니다") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        if (busy) CircularProgressIndicator(Modifier.height(20.dp))
                        TextButton(
                            enabled = !busy && query.isNotBlank(),
                            onClick = {
                                busy = true; msg = null; options = emptyList(); asked = ""
                                scope.launch {
                                    var err: String? = null
                                    // ① **최대 2회.** 첫 꼴이 비면 `역` 을 붙이거나 떼고 한 번 더.
                                    for (q in stationQueries(query)) {
                                        val r = BranchLive.arrivalsAt(q)
                                        r.onSuccess { rows ->
                                            if (rows.isNotEmpty()) {
                                                options = commuteOptions(rows); asked = q; err = null
                                            }
                                        }.onFailure { err = BranchLive.humanError(it) }
                                        if (options.isNotEmpty()) break
                                    }
                                    // 빈 목록 = INFO-200(운행 종료·결과 0건). 오류가 아니다.
                                    msg = err ?: if (options.isEmpty())
                                        "지금은 오는 열차가 없어 방향을 고를 수 없습니다. " +
                                            "역 이름이 맞는지 확인하고, 운행 시간에 등록해 주세요."
                                    else null
                                    busy = false
                                }
                            },
                        ) { Text("확인") }
                    }
                    if (options.isNotEmpty()) {
                        Text(
                            "$asked · 방향을 고르세요 (한 칩 = 한 방향)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        options.forEach { o ->
                            Surface(
                                // 고른 뒤에도 칩을 남긴다 — 같은 역의 **다른 호선·다른 방향**을
                                // 이어서 담을 수 있다(까치산 2호선 + 5호선). 도착 조회를 또 하지
                                // 않으니 그쪽 호출은 안 는다. 같은 조합을 두 번 눌러도 distinct 가 막는다.
                                onClick = {
                                    if (busy) return@Surface
                                    busy = true
                                    scope.launch {
                                        // ⑤ **다섯 칸 이름 — 여기서 딱 한 번.** v1.7.15 ② 부터
                                        //   등록역까지 다섯을 다 담는다(차례는 지리 오름차순
                                        //   고정이고 방향은 기관차가 말한다).
                                        val stops = lineNumOf(o.subwayId)?.let { ln ->
                                            BranchLive.stationsOfLine(ln).getOrNull()
                                                ?.let { commuteStops(it, asked) }
                                        }.orEmpty()
                                        busy = false
                                        onSave((stations + o.toStation(asked, stops)).distinct())
                                    }
                                },
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                shape = RoundedCornerShape(999.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    o.label, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                )
                            }
                        }
                    }
                    msg?.let {
                        Text(
                            it, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("닫기") }
                }
            }
        }
    }
}
