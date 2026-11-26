package com.zzupocket.app.data.repo

import com.zzupocket.app.core.WeekStartDay
import com.zzupocket.app.data.gms.GmsScheduleMapper
import com.zzupocket.app.data.prefs.UiPrefsStore
import com.zzupocket.app.domain.IcsExporter

/**
 * 公共课表 `.ics` 日历导出助手。
 *
 * 供「课程表设置」与「校园」Bento 工具条复用同一套导出逻辑（优先本科学期，若无课则回退研究生课表）。
 */
object ScheduleIcsExportHelper {

    suspend fun exportCurrentSchedule(
        scheduleRepo: ScheduleRepository,
        uiPrefs: UiPrefsStore,
        reminderMinutes: Int = 15,
    ): Pair<String, String>? {
        val sem = scheduleRepo.currentSemester()
        val ugSessions = sem?.let { scheduleRepo.sessionsOfSemester(it.id) }.orEmpty()
        val periods = scheduleRepo.periods()

        if (ugSessions.isNotEmpty() && sem != null) {
            val ws = uiPrefs.weekSettings(sem.id)
            val start = ws.weekOneStart ?: sem.startDate
            val choices = scheduleRepo.conflictDisplayChoices(sem.id)
            val ics = IcsExporter.export(
                calendarName = "郑大课表 ${sem.nameZh}",
                sessions = ugSessions,
                periods = periods,
                semesterStart = start,
                weekStartOnSunday = ws.startDay == WeekStartDay.SUNDAY,
                conflictChoices = choices,
                reminderMinutes = reminderMinutes,
            )
            val safeName = sem.nameZh.replace(Regex("[^A-Za-z0-9_-]"), "").ifBlank { sem.id.toString() }
            return "zzupocket-$safeName.ics" to ics
        }

        val gradId = GmsScheduleMapper.GRAD_SEMESTER_ID
        val gradSessions = scheduleRepo.sessionsOfSemester(gradId)
        if (gradSessions.isNotEmpty()) {
            val ws = uiPrefs.weekSettings(gradId)
            val choices = scheduleRepo.conflictDisplayChoices(gradId)
            val scopeKey = scheduleRepo.timetableScope("gms", gradId)
            val ics = IcsExporter.export(
                calendarName = "郑大研究生课表",
                sessions = gradSessions,
                periods = periods,
                semesterStart = ws.weekOneStart,
                weekStartOnSunday = ws.startDay == WeekStartDay.SUNDAY,
                conflictChoices = choices,
                scopeKey = scopeKey,
                reminderMinutes = reminderMinutes,
            )
            return "zzupocket-grad.ics" to ics
        }

        return null
    }
}
