package com.sinjeong.crewcalendar.data.calendar

import android.content.Context
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import com.sinjeong.crewcalendar.domain.model.DaySchedule
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 구글 캘린더 양방향 동기화.
 *
 * 내보내기(앱 → 구글): 이달 근무를 종일 이벤트로 업서트.
 *   - extendedProperties.private["sinjeongDuty"] = "1" 마킹으로 앱 생성 이벤트 식별
 *   - 같은 날짜의 기존 앱 이벤트는 갱신, 근무 없어지면 삭제
 * 가져오기(구글 → 앱): 앱 마킹이 없는 이벤트 제목을 달력 메모 점으로 표시.
 */
@Singleton
class GoogleCalendarSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val MARK_KEY = "sinjeongDuty"
        val SCOPES = listOf(CalendarScopes.CALENDAR_EVENTS)
    }

    private fun service(accountName: String): Calendar {
        val credential = GoogleAccountCredential.usingOAuth2(context, SCOPES)
            .setSelectedAccountName(accountName)
        return Calendar.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName("SinjeongCrewCalendar")
            .build()
    }

    /** 앱 → 구글: 한 달치 근무를 업서트 */
    suspend fun exportMonth(
        accountName: String,
        calendarId: String,
        month: YearMonth,
        days: List<DaySchedule>,
    ): Unit = withContext(Dispatchers.IO) {
        val cal = service(accountName)
        val existing = cal.events().list(calendarId)
            .setTimeMin(com.google.api.client.util.DateTime("${month.atDay(1)}T00:00:00+09:00"))
            .setTimeMax(com.google.api.client.util.DateTime("${month.atEndOfMonth().plusDays(1)}T00:00:00+09:00"))
            .setPrivateExtendedProperty(listOf("$MARK_KEY=1"))
            .setSingleEvents(true)
            .execute().items.orEmpty()
            .associateBy { it.start?.date?.toString() ?: "" }

        days.forEach { day ->
            val dateStr = day.date.toString()
            val title = dutyTitle(day) ?: run {
                existing[dateStr]?.let { cal.events().delete(calendarId, it.id).execute() }
                return@forEach
            }
            val old = existing[dateStr]
            if (old != null) {
                if (old.summary != title) {
                    old.summary = title
                    cal.events().update(calendarId, old.id, old).execute()
                }
            } else {
                cal.events().insert(calendarId, buildAllDayEvent(day.date, title)).execute()
            }
        }
    }

    /** 구글 → 앱: 앱이 만들지 않은 일정 제목 가져오기 (달력 점 표시/메모 병합용) */
    suspend fun importMonth(
        accountName: String,
        calendarId: String,
        month: YearMonth,
    ): Map<LocalDate, List<String>> = withContext(Dispatchers.IO) {
        val cal = service(accountName)
        cal.events().list(calendarId)
            .setTimeMin(com.google.api.client.util.DateTime("${month.atDay(1)}T00:00:00+09:00"))
            .setTimeMax(com.google.api.client.util.DateTime("${month.atEndOfMonth().plusDays(1)}T00:00:00+09:00"))
            .setSingleEvents(true)
            .setOrderBy("startTime")
            .execute().items.orEmpty()
            .filter { it.extendedProperties?.private?.get(MARK_KEY) != "1" }
            .groupBy(
                keySelector = { ev ->
                    val d = ev.start?.date?.toString() ?: ev.start?.dateTime?.toString()?.take(10)
                    d?.let(LocalDate::parse) ?: LocalDate.MIN
                },
                valueTransform = { it.summary.orEmpty() },
            )
            .filterKeys { it != LocalDate.MIN }
    }

    private fun dutyTitle(day: DaySchedule): String? {
        if (day.duty.raw.isBlank()) return null
        return "[신정] ${day.duty.display}" + if (day.memo.isNotBlank()) " · ${day.memo}" else ""
    }

    private fun buildAllDayEvent(date: LocalDate, title: String): Event = Event().apply {
        summary = title
        start = EventDateTime().setDate(com.google.api.client.util.DateTime(date.toString()))
        end = EventDateTime().setDate(com.google.api.client.util.DateTime(date.plusDays(1).toString()))
        extendedProperties = Event.ExtendedProperties().setPrivate(mapOf(MARK_KEY to "1"))
    }
}
