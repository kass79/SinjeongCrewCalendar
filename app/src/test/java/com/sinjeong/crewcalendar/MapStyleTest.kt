package com.sinjeong.crewcalendar

import com.sinjeong.crewcalendar.presentation.live.MapArgb
import com.sinjeong.crewcalendar.presentation.theme.MapStyle
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 지도 스타일(v1.7.0) — **기본값이 안 바뀌는 것**과 **고른 값이 살아 돌아오는 것** 둘을 잠근다.
 *
 * ⚠ 이 하네스에는 **Compose 가 없다**(`tools/runtests.ps1`) — 그래서 색을 `Color` 가 아니라
 * `MapArgb` 의 `Long` 으로 읽는다. `MapPalette` 나 `MainLineMap` 을 건드리면 클래스 초기화가
 * 터진다(`LocoTest` 가 `mainTrainSide` 를 `Loco.kt` 에서 가져오는 것과 같은 사정).
 */
class MapStyleTest {

    /**
     * **운전실 남색은 v1.6.99 그대로다.**
     *
     * 클레이를 더하면서 색 상수를 팔레트로 옮겼는데, 남색은 **기본값**이라 한 칸이라도 어긋나면
     * 클레이를 고르지도 않은 사람의 화면이 바뀐다. 여기 숫자는 v1.6.98~99 의 `MainLineMap.kt` ·
     * `LineMap.kt` 최상위 상수를 그대로 옮겨 적은 것이다 — **고치려면 사용자에게 물어야 한다.**
     */
    @Test
    fun `남색 팔레트는 옛 상수와 같다`() {
        assertEquals(0xFF0E2A47L, MapArgb.CabBg)          // CabNavy
        assertEquals(0xFF2FC24AL, MapArgb.CabRail)        // LoopGreen
        assertEquals(0xFFFFFFFFL, MapArgb.CabStation)     // StationWhite
        assertEquals(0xFFF0392BL, MapArgb.CabStationRed)  // StationRed
        assertEquals(0xFFA9DCF5L, MapArgb.CabOtherBody)   // BadgeSky
        assertEquals(0xFF0A2036L, MapArgb.CabOtherInk)    // BadgeInk
        assertEquals(0xFF7BA7C1L, MapArgb.CabSoftBody)    // BadgeSkySoft
        assertEquals(0xFFC3CAD1L, MapArgb.CabDepot)       // DepotGray
        assertEquals(0xFFFFE14DL, MapArgb.CabMineBody)    // MineYellow
        assertEquals(0xFFB3261EL, MapArgb.CabMineInk)     // MineInk
        assertEquals(0xFFFFB74DL, MapArgb.CabKey)         // KeyOrange
        assertEquals(0xFF8FD0FFL, MapArgb.CabOp)          // OpStationBlue
        assertEquals(0xFF8FA9C4L, MapArgb.CabDim)         // Dim
        assertEquals(0xFFCFE3F5L, MapArgb.CabInfo)        // 정보 칩
        assertEquals(0xFFE9A23BL, MapArgb.CabFail)        // 조회 실패 칩
        assertEquals(0xFF0A1E33L, MapArgb.CabTip)         // 툴팁 바탕
    }

    /**
     * 저장·복원 — 설정에서 고른 값이 `theme` 저장소에 `.name` 으로 앉았다가 그대로 돌아온다.
     * **모르는 값·빈 값은 기본(운전실 남색)** 이다: 옛 판에서 올라온 기기, 저장이 날아간 기기,
     * enum 이름을 나중에 잘못 바꾼 경우가 다 여기로 떨어진다 — 지도가 안 뜨는 것보다 낫다.
     */
    @Test
    fun `지도 스타일은 저장값에서 그대로 복원된다`() {
        MapStyle.entries.forEach { assertEquals(it, MapStyle.of(it.name)) }
        assertEquals(MapStyle.CAB, MapStyle.of(null))     // 처음 켠 기기
        assertEquals(MapStyle.CAB, MapStyle.of(""))       // 저장이 비었다
        assertEquals(MapStyle.CAB, MapStyle.of("clay"))   // 대소문자가 다르다
        assertEquals(MapStyle.CAB, MapStyle.of("NEON"))   // 없어진 스타일
    }
}
