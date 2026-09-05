package com.sinjeong.crewcalendar.presentation.live

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.sinjeong.crewcalendar.presentation.theme.MapStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * 실시간 지도(본선 순환선 · 지선 카드)의 **색 한 벌** — v1.7.0.
 *
 * 사용자 원문: *"설정에 이런 클레이 디자인도 선택할수있게 가능?"* (크림 바탕·민트 튜브 선로·
 * 흰 클레이 배지에 빨간 열번·신도림 초록·성수 빨강)
 *
 * ## 규칙 — 되돌리지 말 것
 *
 * 1. **배치 규칙은 한 줄도 안 바뀐다.** v1.6.98 의 보조설비식 배치(가로 변 열차 선로 위·역명
 *    아래 / 세로 변 열차 루프 밖·역명 안 · 전체 보기 같은 차선 + 계단 · 바퀴는 선로에 ·
 *    열번 방향 · 역명 2단 축소)와 v1.6.99 의 라벨 회피는 **그대로다.** 클레이가 바꾸는 것은
 *    **색·두께·그림자** 셋뿐이다.
 * 2. **[MapStyle.CAB] 값은 v1.6.99 의 상수와 글자 하나까지 같다** — 기본값이라 회귀가 나면
 *    안 쓰던 사람까지 화면이 바뀐다. [MapArgb] 의 `Cab*` 값을 `MapStyleTest` 가 잠근다.
 * 3. **클레이는 밝은 스타일 고정** — 앱이 다크 모드여도 지도 다이얼로그·지선 카드는 크림
 *    바탕이다(운전실 남색이 테마와 무관한 것과 같은 이유: 이 화면은 앱이 아니라 **계기판**이다).
 *
 * ## ⚠ 색 값이 [MapArgb] 에 **숫자로** 사는 이유
 *
 * 유닛테스트 하네스(`tools/runtests.ps1`)에는 **Compose 가 없다.** `Color(...)` 를 최상위에
 * 두면 클래스 초기화가 터진다(`MainLineMap.kt` 가 `mainTrainSide` 를 `Loco.kt` 로 옮긴 것과
 * 같은 사정). 그래서 값은 `Long` 으로 [MapArgb] 에 두고, 팔레트는 그 숫자를 [Color] 로 싼다.
 * `const` 가 아니라 `val` 인 것도 일부러다 — `const` 는 테스트 바이트코드에 **인라인**되어
 * 나중에 값을 고쳐도 테스트가 안 깨진다(잠그는 값이 아니게 된다).
 */
internal object MapArgb {

    /* ── 운전실 남색(CAB) — v1.6.99 의 상수 그대로 ─────────────────────────── */
    /** 운전실 화면 바탕 */
    val CabBg = 0xFF0E2A47L
    val CabRail = 0xFF2FC24AL
    val CabStation = 0xFFFFFFFFL
    /** 열차가 서 있는 역 */
    val CabStationRed = 0xFFF0392BL
    /** 일반 영업 열차 몸통 */
    val CabOtherBody = 0xFFA9DCF5L
    val CabOtherInk = 0xFF0A2036L
    /** 지선 위 차선(까치산행) — 하늘색을 남색 쪽으로 섞어 물러나게 */
    val CabSoftBody = 0xFF7BA7C1L
    /** 기지 입고 회송 */
    val CabDepot = 0xFFC3CAD1L
    val CabMineBody = 0xFFFFE14DL
    val CabMineInk = 0xFFB3261EL
    /** 신도림·성수(지선은 신도림·양천구청) */
    val CabKey = 0xFFFFB74DL
    /** 운전취급역 6곳 */
    val CabOp = 0xFF8FD0FFL
    val CabDim = 0xFF8FA9C4L
    /** 정보 칩 글자 · 툴팁 둘째 줄 */
    val CabInfo = 0xFFCFE3F5L
    /** 조회 실패 칩 */
    val CabFail = 0xFFE9A23BL
    /** 툴팁 바탕 */
    val CabTip = 0xFF0A1E33L

    /* ── 클레이(CLAY) — 사용자가 확인한 시안 값 ─────────────────────────────── */
    val ClayBg = 0xFFF6F1E7L
    val ClayRail = 0xFF9FDCB5L
    val ClayRailTop = 0xFFC9EED6L
    val ClayRailBottom = 0xFF7FC79BL
    val ClayRailShadow = 0xFF6FAE86L
    val ClayRailHighlight = 0xFFDFF7E6L
    val ClayStation = 0xFFFFFFFFL
    val ClayStationEdge = 0xFFD9D1C2L
    val ClayStationRed = 0xFFE4573FL
    val ClayLabel = 0xFF4E463BL
    /** **신도림 초록** — 클레이에서만 신도림·성수가 다른 색이다(CAB 는 둘 다 주황) */
    val ClayKeyA = 0xFF2E9A5EL
    /** **성수 빨강** */
    val ClayKeyB = 0xFFE4573FL
    val ClayOp = 0xFF3F87C9L
    val ClayOtherBody = 0xFFFFFFFFL
    val ClayOtherBottom = 0xFFEFEAE0L
    val ClayOtherEdge = 0xFFDDD5C6L
    val ClayOtherInk = 0xFFD6432EL
    val ClaySoftBody = 0xFFEDE6D9L
    val ClayDepot = 0xFFCFC8BBL
    val ClayWheel = 0xFF6D6355L
    val ClayMineBody = 0xFFF5CF3DL
    val ClayMineTop = 0xFFFFF3A6L
    val ClayMineBottom = 0xFFE9C22FL
    /** 내 열차 테 — **흰 테는 크림 바탕에서 안 보인다**(사용자 확정) */
    val ClayMineRing = 0xFFD9B32AL
    val ClayMineInk = 0xFFC43A25L
    val ClaySmoke = 0xFF8A7A5AL
    val ClayTitle = 0xFF6F6455L
    val ClayDim = 0xFF9A8F7EL
    val ClayClock = 0xFFD98A2BL
    val ClayChip = 0xFFDDF5E4L
    val ClayChipInk = 0xFF2E9A5EL
    val ClayInfo = 0xFF5C7A66L
    val ClayFail = 0xFFD9722BL
}

/**
 * 지도 한 장이 쓰는 **색 전부**. 두 지도([MainLineMapDialog] · [BranchLiveMap])가 같은 한 벌을
 * 본다 — 한쪽만 클레이가 되는 일이 없다.
 *
 * ⚠ 그림 파일이 아니라 **값**이라서 반응형이 안 깨진다: 두께·그림자는 여전히 dp 이고
 * 폴드 펼침·글자배율·세로 회전은 종전 코드가 그대로 정한다.
 */
internal data class MapPalette(
    val style: MapStyle,
    /** 바탕 */
    val bg: Color,
    /** 선로 본체(CAB 단색 · 클레이는 [railTop]~[railBottom] 그라데이션의 가운데) */
    val rail: Color,
    val railTop: Color,
    val railBottom: Color,
    /** 선로 그림자 — 클레이만. CAB 은 `null` 이라 한 줄도 안 그린다. */
    val railShadow: Color?,
    /** 선로 하이라이트(가는 밝은 줄) — 클레이만 */
    val railHighlight: Color?,
    /** 지선 **위 차선(까치산행)** 선로 */
    val railSoft: Color,
    /** 역 점 */
    val station: Color,
    /** 역 점 테 */
    val stationEdge: Color,
    /** 열차가 서 있는 역 */
    val stationRed: Color,
    /** 역 이름 기본 */
    val label: Color,
    /** 핵심역 — 신도림(지선은 양천구청도). 클레이 초록 */
    val keyA: Color,
    /** 핵심역 — **성수**. 클레이 빨강. CAB 은 [keyA] 와 같은 주황이다. */
    val keyB: Color,
    /** 운전취급역 6곳 */
    val op: Color,
    val otherBody: Color,
    val otherTop: Color,
    val otherBottom: Color,
    /** 몸통 바깥 1dp 테 — `null` = 종전대로 몸통색에서 만들어 쓴다(CAB) */
    val otherEdge: Color?,
    val otherInk: Color,
    /** 지선 위 차선(까치산행) 몸통 */
    val softBody: Color,
    /** 기지 입고 회송 몸통 */
    val depot: Color,
    /** 바퀴·창·대차 */
    val wheel: Color,
    val mineBody: Color,
    val mineTop: Color,
    val mineBottom: Color,
    /** 내 열차 안쪽 테(CAB 흰색 / 클레이 [MapArgb.ClayMineRing]) */
    val mineRing: Color,
    val mineInk: Color,
    /** 굴뚝 연기 */
    val smoke: Color,
    /** 바닥 그림자 · 클레이 오프셋 복제 그림자 */
    val shadow: Color,
    /** 헤더 제목·닫기 X */
    val title: Color,
    /** 흐린 글자(차선 안내·부제) */
    val dim: Color,
    /** 헤더 시계 */
    val clock: Color,
    /** 칩 바탕(클레이 민트) */
    val chip: Color,
    /** 칩 글자(클레이) */
    val chipInk: Color,
    /** 고른 칩 바탕(클레이 흰 알약) */
    val chipSel: Color,
    /** 정보 칩·툴팁 둘째 줄 */
    val info: Color,
    /** 조회 실패 칩 */
    val fail: Color,
    /** 툴팁 바탕 */
    val tipBg: Color,
) {
    /** 클레이인가 — 그림자·하이라이트 같은 **클레이 전용 획**을 켜는 스위치다. */
    val clay: Boolean get() = style == MapStyle.CLAY

    /** 툴팁·헤더에서 내 열차를 가리키는 글자색. 크림 바탕에서는 노랑이 안 보인다. */
    val mineText: Color get() = if (clay) mineInk else mineBody

    /** 역 이름 색 — 신도림/양천구청은 [keyA], **성수만** [keyB](CAB 은 둘이 같다). */
    fun keyInk(name: String): Color = if (name == "성수") keyB else keyA
}

/** 운전실 남색 — v1.6.99 까지의 그 화면. 기본값이다. */
internal val CAB_PALETTE = MapPalette(
    style = MapStyle.CAB,
    bg = Color(MapArgb.CabBg),
    rail = Color(MapArgb.CabRail),
    railTop = Color(MapArgb.CabRail),
    railBottom = Color(MapArgb.CabRail),
    railShadow = null,
    railHighlight = null,
    // ⚠ `copy(alpha)` 로 만든다 — 8비트로 적으면 0.549 가 되어 옛 화면과 1/255 어긋난다.
    railSoft = Color(MapArgb.CabRail).copy(alpha = 0.55f),
    station = Color(MapArgb.CabStation),
    stationEdge = Color(MapArgb.CabStation),
    stationRed = Color(MapArgb.CabStationRed),
    label = Color(MapArgb.CabStation),
    keyA = Color(MapArgb.CabKey),
    keyB = Color(MapArgb.CabKey),
    op = Color(MapArgb.CabOp),
    otherBody = Color(MapArgb.CabOtherBody),
    otherTop = Color(MapArgb.CabOtherBody),
    otherBottom = Color(MapArgb.CabOtherBody),
    otherEdge = null,
    otherInk = Color(MapArgb.CabOtherInk),
    softBody = Color(MapArgb.CabSoftBody),
    depot = Color(MapArgb.CabDepot),
    wheel = Color(MapArgb.CabBg),
    mineBody = Color(MapArgb.CabMineBody),
    mineTop = Color(MapArgb.CabMineBody),
    mineBottom = Color(MapArgb.CabMineBody),
    mineRing = Color(MapArgb.CabStation),
    mineInk = Color(MapArgb.CabMineInk),
    smoke = Color(MapArgb.CabStation),
    shadow = Color.Black.copy(alpha = 0.35f),
    title = Color(MapArgb.CabStation),
    dim = Color(MapArgb.CabDim),
    clock = Color(MapArgb.CabMineBody),
    chip = Color(MapArgb.CabOtherBody),
    chipInk = Color(MapArgb.CabMineInk),
    chipSel = Color(MapArgb.CabStation),
    info = Color(MapArgb.CabInfo),
    fail = Color(MapArgb.CabFail),
    tipBg = Color(MapArgb.CabTip),
)

/** 클레이 — 크림 바탕·민트 튜브·흰 클레이 열차. **배치는 [CAB_PALETTE] 와 똑같다.** */
internal val CLAY_PALETTE = MapPalette(
    style = MapStyle.CLAY,
    bg = Color(MapArgb.ClayBg),
    rail = Color(MapArgb.ClayRail),
    railTop = Color(MapArgb.ClayRailTop),
    railBottom = Color(MapArgb.ClayRailBottom),
    railShadow = Color(MapArgb.ClayRailShadow).copy(alpha = 0.35f),
    railHighlight = Color(MapArgb.ClayRailHighlight),
    railSoft = Color(MapArgb.ClayRailTop),
    station = Color(MapArgb.ClayStation),
    stationEdge = Color(MapArgb.ClayStationEdge),
    stationRed = Color(MapArgb.ClayStationRed),
    label = Color(MapArgb.ClayLabel),
    keyA = Color(MapArgb.ClayKeyA),
    keyB = Color(MapArgb.ClayKeyB),
    op = Color(MapArgb.ClayOp),
    otherBody = Color(MapArgb.ClayOtherBody),
    otherTop = Color(MapArgb.ClayOtherBody),
    otherBottom = Color(MapArgb.ClayOtherBottom),
    otherEdge = Color(MapArgb.ClayOtherEdge),
    otherInk = Color(MapArgb.ClayOtherInk),
    softBody = Color(MapArgb.ClaySoftBody),
    depot = Color(MapArgb.ClayDepot),
    wheel = Color(MapArgb.ClayWheel),
    mineBody = Color(MapArgb.ClayMineBody),
    mineTop = Color(MapArgb.ClayMineTop),
    mineBottom = Color(MapArgb.ClayMineBottom),
    mineRing = Color(MapArgb.ClayMineRing),
    mineInk = Color(MapArgb.ClayMineInk),
    smoke = Color(MapArgb.ClaySmoke),
    shadow = Color(MapArgb.ClaySmoke).copy(alpha = 0.30f),
    title = Color(MapArgb.ClayTitle),
    dim = Color(MapArgb.ClayDim),
    clock = Color(MapArgb.ClayClock),
    chip = Color(MapArgb.ClayChip),
    chipInk = Color(MapArgb.ClayChipInk),
    chipSel = Color(MapArgb.ClayStation),
    info = Color(MapArgb.ClayInfo),
    fail = Color(MapArgb.ClayFail),
    tipBg = Color(MapArgb.ClayStation),
)

internal fun paletteOf(style: MapStyle) = if (style == MapStyle.CLAY) CLAY_PALETTE else CAB_PALETTE

/**
 * **화면에서 아래쪽**을 가리키는 캔버스 벡터. 클레이 그림자·하이라이트는 늘 화면 기준으로
 * 떨어져야 하는데, 세로 화면에서는 지도 전체가 `rotationZ = 90f` 로 돌아간다
 * ([MainLineMapDialog]) — 캔버스는 자기가 돌아간 줄 모르므로 여기서 되돌려 준다.
 * [locoTextDeg] 가 글자에 대해 하는 일과 같다.
 *
 * `mapDeg = 0` → `(0, d)` · `mapDeg = 90` → `(d, 0)`.
 */
internal fun screenDown(mapDeg: Float, d: Float): Offset {
    val r = mapDeg * PI.toFloat() / 180f
    return Offset(d * sin(r), d * cos(r))
}
