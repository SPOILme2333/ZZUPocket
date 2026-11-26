package com.zzupocket.app.domain

import com.zzupocket.app.core.ScheduleText
import com.zzupocket.app.core.WeekMath
import com.zzupocket.app.core.WeekStartDay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 课表 `.ics`（iCalendar / RFC 5545）导出器。
 *
 * 纯函数实现，不依赖 Android 环境，便于同步至手机系统日历、Google Calendar 或智能手表。
 */
object IcsExporter {

    private val DATE_BASIC = DateTimeFormatter.BASIC_ISO_DATE

    /**
     * 生成完整 `.ics` 日历文本。
     *
     * @param calendarName 日历名称
     * @param sessions 学期全部排课
     * @param periods 节次时刻表
     * @param semesterStart 开学基准日（仅在单条 session 缺少真实 date 时作为周次推算兜底）
     * @param weekStartOnSunday 周起始日是否为周日
     * @param conflictChoices 用户已保存的冲突显示选择；存在未解决冲突时保留全部课以免漏导
     * @param scopeKey 作用域标识（研究生课表使用）
     * @param reminderMinutes 是否为每节课附带课前 `VALARM` 提醒；null 表示不生成提醒
     */
    fun export(
        calendarName: String,
        sessions: List<Session>,
        periods: List<Period>,
        semesterStart: LocalDate? = null,
        weekStartOnSunday: Boolean = false,
        conflictChoices: List<ConflictDisplayChoice> = emptyList(),
        scopeKey: String? = null,
        reminderMinutes: Int? = 15,
        nowStampUtc: String = "20260101T000000Z",
    ): String {
        val startDay = if (weekStartOnSunday) WeekStartDay.SUNDAY else WeekStartDay.MONDAY
        val resolved = resolveExportSessions(sessions, conflictChoices, scopeKey)
        val periodMap = periods.associateBy { it.indexNo }

        val sb = StringBuilder()
        sb.appendLine("BEGIN:VCALENDAR")
        sb.appendLine("VERSION:2.0")
        sb.appendLine("PRODID:-//ZZUPocket//Schedule Exporter//ZH")
        sb.appendLine("CALSCALE:GREGORIAN")
        sb.appendLine("METHOD:PUBLISH")
        sb.appendLine("X-WR-CALNAME:${escapeText(calendarName)}")
        sb.appendLine("X-WR-TIMEZONE:Asia/Shanghai")

        for (s in resolved) {
            val date = resolveDate(s, semesterStart, startDay) ?: continue
            val startClock = periodMap[s.startUnit]?.startText ?: s.startTime ?: continue
            val endClock = periodMap[s.endUnit]?.endText ?: s.endTime ?: continue
            val startTime = parseLocalTime(startClock) ?: continue
            val endTime = parseLocalTime(endClock) ?: continue
            if (!endTime.isAfter(startTime)) continue

            val dateStr = date.format(DATE_BASIC)
            val dtStart = "${dateStr}T${formatTimeBasic(startTime)}"
            val dtEnd = "${dateStr}T${formatTimeBasic(endTime)}"
            val location = ScheduleText.tidyPlace(s.roomText ?: s.campusText).orEmpty()
            val descParts = buildList {
                s.teacherName?.takeIf { it.isNotBlank() }?.let { add("教师：$it") }
                add("第 ${s.weekIndex} 周 · 第 ${s.startUnit}-${s.endUnit} 节")
                if (s.experiment) add("类型：实验/见习")
            }

            sb.appendLine("BEGIN:VEVENT")
            sb.appendLine("UID:${escapeText(s.id)}@zzupocket.app")
            sb.appendLine("DTSTAMP:$nowStampUtc")
            sb.appendLine("DTSTART;TZID=Asia/Shanghai:$dtStart")
            sb.appendLine("DTEND;TZID=Asia/Shanghai:$dtEnd")
            sb.appendLine("SUMMARY:${escapeText(s.courseName)}")
            if (location.isNotBlank()) {
                sb.appendLine("LOCATION:${escapeText(location)}")
            }
            if (descParts.isNotEmpty()) {
                sb.appendLine("DESCRIPTION:${escapeText(descParts.joinToString("\n"))}")
            }
            if (reminderMinutes != null && reminderMinutes > 0) {
                sb.appendLine("BEGIN:VALARM")
                sb.appendLine("ACTION:DISPLAY")
                sb.appendLine("DESCRIPTION:${escapeText("即将上课：${s.courseName}")}")
                sb.appendLine("TRIGGER:-PT${reminderMinutes}M")
                sb.appendLine("END:VALARM")
            }
            sb.appendLine("END:VEVENT")
        }

        sb.appendLine("END:VCALENDAR")
        return sb.toString().replace("\n", "\r\n")
    }

    /**
     * 导出官方考场安排为 RFC 5545 `.ics` 日历文本（含考场位置与考前提醒）。
     */
    fun exportExams(
        calendarName: String = "郑大考试安排",
        exams: List<ExamRecord>,
        reminderMinutes: Int? = 60,
        nowStampUtc: String = "20260101T000000Z",
    ): String {
        val sb = StringBuilder()
        sb.appendLine("BEGIN:VCALENDAR")
        sb.appendLine("VERSION:2.0")
        sb.appendLine("PRODID:-//ZZUPocket//Exam Exporter//ZH")
        sb.appendLine("CALSCALE:GREGORIAN")
        sb.appendLine("METHOD:PUBLISH")
        sb.appendLine("X-WR-CALNAME:${escapeText(calendarName)}")
        sb.appendLine("X-WR-TIMEZONE:Asia/Shanghai")

        for (exam in exams) {
            val date = exam.parsedDate() ?: continue
            val startTime = parseLocalTime(exam.startTimeText) ?: continue
            val rawEnd = parseLocalTime(exam.endTimeText)
            val endTime = if (rawEnd != null && rawEnd.isAfter(startTime)) rawEnd else startTime.plusHours(2)

            val dateStr = date.format(DATE_BASIC)
            val dtStart = "${dateStr}T${formatTimeBasic(startTime)}"
            val dtEnd = "${dateStr}T${formatTimeBasic(endTime)}"
            val title = buildString {
                if (!exam.examType.isNullOrBlank()) append("[${exam.examType}] ")
                append("${exam.courseName} 考试")
            }

            sb.appendLine("BEGIN:VEVENT")
            sb.appendLine("UID:exam-${escapeText(exam.id)}@zzupocket.app")
            sb.appendLine("DTSTAMP:$nowStampUtc")
            sb.appendLine("DTSTART;TZID=Asia/Shanghai:$dtStart")
            sb.appendLine("DTEND;TZID=Asia/Shanghai:$dtEnd")
            sb.appendLine("SUMMARY:${escapeText(title)}")
            if (exam.place.isNotBlank()) {
                sb.appendLine("LOCATION:${escapeText(exam.place)}")
            }
            sb.appendLine("DESCRIPTION:${escapeText("ZZUPocket 教务考场同步 · ${exam.place.ifBlank { "地点待定" }}")}")
            if (reminderMinutes != null && reminderMinutes > 0) {
                sb.appendLine("BEGIN:VALARM")
                sb.appendLine("ACTION:DISPLAY")
                sb.appendLine("DESCRIPTION:${escapeText("即将开考：${exam.courseName}（${exam.place}）")}")
                sb.appendLine("TRIGGER:-PT${reminderMinutes}M")
                sb.appendLine("END:VALARM")
            }
            sb.appendLine("END:VEVENT")
        }

        sb.appendLine("END:VCALENDAR")
        return sb.toString().replace("\n", "\r\n")
    }

    private fun resolveExportSessions(
        sessions: List<Session>,
        choices: List<ConflictDisplayChoice>,
        scopeKey: String?,
    ): List<Session> {
        val blocks = scheduleBlocks(sessions, choices, scopeKey = scopeKey)
        val picked = blocks.flatMap { block ->
            if (!block.isConflict || block.selectedSession == null) {
                block.sessions
            } else {
                listOf(block.displaySession)
            }
        }
        val pickedIds = picked.map { it.id }.toHashSet()
        // 补充无周网格信息的临时补课记录
        val unindexed = sessions.filter {
            it.id !in pickedIds && (it.weekIndex <= 0 || it.weekday !in 1..7 || it.startUnit <= 0)
        }
        return (picked + unindexed).distinctBy { it.id }
            .sortedWith(compareBy({ it.date ?: "" }, { it.weekIndex }, { it.weekday }, { it.startUnit }))
    }

    private fun resolveDate(
        s: Session,
        semesterStart: LocalDate?,
        startDay: WeekStartDay,
    ): LocalDate? {
        s.date?.take(10)?.let { raw ->
            runCatching { LocalDate.parse(raw) }.getOrNull()?.let { return it }
        }
        if (semesterStart != null && s.weekIndex > 0 && s.weekday in 1..7) {
            return WeekMath.dateOf(semesterStart, s.weekIndex, s.weekday, startDay)
        }
        return null
    }

    private fun parseLocalTime(raw: String): LocalTime? {
        val parts = raw.trim().split(':')
        if (parts.size < 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].take(2).toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return LocalTime.of(h, m)
    }

    private fun formatTimeBasic(t: LocalTime): String =
        "%02d%02d00".format(t.hour, t.minute)

    /** RFC 5545 文本字段转义 */
    internal fun escapeText(text: String): String = text
        .replace("\\", "\\\\")
        .replace("\r\n", "\\n")
        .replace("\n", "\\n")
        .replace(";", "\\;")
        .replace(",", "\\,")
}
