package com.sinjeong.crewcalendar.widget

import com.sinjeong.crewcalendar.domain.model.Line2Stations
import com.sinjeong.crewcalendar.presentation.live.PositionRow

/** 알람 알림 둘째 줄. 순수 함수 — Line2TimetableTest 가 잠근다. */
internal fun liveLine(row: PositionRow, delayMin: Int?): String {
    val st = when (row.trainSttus) { "0" -> "진입"; "1" -> "도착"; "2" -> "출발"; "3" -> "전역 출발"; else -> "운행 중" }
    val d = when { delayMin == null -> ""; delayMin > 0 -> " · +${delayMin}분 지연"; delayMin < 0 -> " · ${-delayMin}분 빠름"; else -> " · 정시" }
    return "${row.trainNo}열차 지금 ${Line2Stations.norm(row.statnNm)} $st$d"
}
