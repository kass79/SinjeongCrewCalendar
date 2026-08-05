package com.sinjeong.crewcalendar.domain.usecase

import com.sinjeong.crewcalendar.domain.model.Bundled
import com.sinjeong.crewcalendar.domain.model.CrewGroup
import com.sinjeong.crewcalendar.domain.repository.UserRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject

/**
 * 근무선택 — 이 앱의 핵심 기능. 2단계:
 *  ① 소속(신정지선 / 본선 기관사 / 본선 차장) 선택
 *  ② "date의 내 근무는 패턴의 patternIndex 칸" 지정
 * → offset 재계산 저장. 이후 모든 날짜(과거·미래)가 교번 순서대로 자동 계산된다.
 * 언제든 어느 날짜 기준으로든 다시 지정 가능 (근무 밀림 보정).
 */
class SelectDutyPositionUseCase @Inject constructor(
    private val userRepo: UserRepository,
) {
    suspend operator fun invoke(date: LocalDate, group: CrewGroup, patternIndex: Int) {
        val user = userRepo.observeMe().first() ?: error("로그인이 필요합니다")
        val pattern = Bundled.patternFor(group)
        require(patternIndex in pattern.sequence.indices) { "잘못된 근무 위치" }

        val offset = pattern.offsetFor(date, patternIndex)
        userRepo.upsert(user.copy(patternId = pattern.id, patternOffset = offset, role = group.role))
    }
}
