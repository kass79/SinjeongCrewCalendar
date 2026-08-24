package com.sinjeong.crewcalendar.domain.usecase

import com.sinjeong.crewcalendar.domain.model.Bundled
import com.sinjeong.crewcalendar.domain.model.CrewGroup
import com.sinjeong.crewcalendar.domain.model.scheduleSegment
import com.sinjeong.crewcalendar.domain.repository.UserRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject

/**
 * 근무선택 — 이 앱의 핵심 기능. 2단계:
 *  ① 소속(신정지선 / 본선 기관사 / 본선 차장) 선택
 *  ② "date의 내 근무는 패턴의 patternIndex 칸" 지정 → offset 재계산 저장
 *
 * [scheduled]로 **적용 시작일**이 갈린다 (v1.6.63):
 *  - `false` = `바로 적용` — 종전 그대로. 구간을 비워 저장 모양까지 v1.6.62와 같아진다.
 *    앞뒤 모든 날짜가 새 교번으로 다시 계산된다(근무가 밀렸을 때 과거까지 바로잡는 길).
 *  - `true`  = `다음 달 1일부터` — [date] 이전은 지금 교번 그대로, [date]부터 새 교번.
 *    기관사가 지선 2개월 / 본선 4~6개월 주기로 **달 경계에서** 소속이 바뀌기 때문이다.
 *    이때 [date]는 곧 적용 시작일이고 [patternIndex]는 **그 날의 근무**다 —
 *    사용자가 새 교번표를 보고 "9/1은 몇 다이아"를 읽어 고른다.
 */
class SelectDutyPositionUseCase @Inject constructor(
    private val userRepo: UserRepository,
) {
    suspend operator fun invoke(date: LocalDate, group: CrewGroup, patternIndex: Int, scheduled: Boolean = false) {
        val user = userRepo.observeMe().first() ?: error("로그인이 필요합니다")
        val pattern = Bundled.patternFor(group)
        require(patternIndex in pattern.sequence.indices) { "잘못된 근무 위치" }

        val offset = pattern.offsetFor(date, patternIndex)
        userRepo.upsert(
            if (scheduled) user.scheduleSegment(date, pattern.id, offset, group.role)
            else user.copy(
                patternId = pattern.id, patternOffset = offset, role = group.role,
                patternSegments = emptyList(),
            )
        )
    }
}
