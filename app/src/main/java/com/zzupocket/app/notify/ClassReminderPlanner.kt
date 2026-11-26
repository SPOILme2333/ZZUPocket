package com.zzupocket.app.notify

import com.zzupocket.app.core.CourseShortener
import com.zzupocket.app.core.ScheduleText
import com.zzupocket.app.domain.ConflictDisplayChoice
import com.zzupocket.app.domain.Period
import com.zzupocket.app.domain.Session
import com.zzupocket.app.domain.scheduleBlocks
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** 即将触发的课前提醒目标 */
data class ClassReminderTarget(
    /** 防重唯一键（含日期、课程编码、起止节次与计划触发时刻） */
    val sessionKey: String,
    val triggerAtMillis: Long,
    val classStartMillis: Long,
    val courseName: String,
    val roomText: String,
    val startClock: String,
    val endClock: String,
    val unitRangeText: String,
    val leadMinutes: Int,
)

/**
 * 课前通知提醒纯逻辑规划器（Issue #23）。
 *
 * 给定某天之后连续几天的排课列表与当前时刻，精确找出下一个尚未触发且落在未来窗口内的课前提醒。
 */
object ClassReminderPlanner {

    /** 当某节课即将在 `leadMinutes` 内开课，但距离开课仍有 > 1 分钟时仍立即补偿触发 */
    private const val MIN_BEFORE_CLASS_MS = 60_000L

    /**
     * 从按天分好组的课程（最多看未来 7 天）中计算下一次课前提醒。
     *
     * @param daySessions `(日期, 当天排课)` 列表
     * @param periods 节次时刻表
     * @param choicesBySemester 按学期分组的冲突选择
     * @param leadMinutes 提前提醒分钟数（默认 15）
     * @param nowMillis 当前毫秒时间戳
     * @param lastFiredKey 上一次已响铃的 [ClassReminderTarget.sessionKey]，防止同一节课重复弹窗
     * @param shortenNames 是否应用课程智能简称
     * @param zone 时区
     */
    fun nextTarget(
        daySessions: List<Pair<LocalDate, List<Session>>>,
        periods: List<Period>,
        choicesBySemester: Map<Int, List<ConflictDisplayChoice>> = emptyMap(),
        leadMinutes: Int,
        nowMillis: Long,
        lastFiredKey: String? = null,
        shortenNames: Boolean = false,
        zone: ZoneId = ZoneId.systemDefault(),
    ): ClassReminderTarget? {
        if (leadMinutes <= 0) return null
        val periodMap = periods.associateBy { it.indexNo }
        val leadMs = leadMinutes * 60_000L

        val candidates = ArrayList<ClassReminderTarget>()
        for ((date, rawSessions) in daySessions) {
            if (rawSessions.isEmpty()) continue
            val visibleSessions = resolveVisibleSessions(rawSessions, choicesBySemester)
            for (s in visibleSessions) {
                val startClock = periodMap[s.startUnit]?.startText ?: s.startTime ?: continue
                val endClock = periodMap[s.endUnit]?.endText ?: s.endTime ?: startClock
                val startLocal = parseLocalTime(startClock) ?: continue
                val classStartMs = ZonedDateTime.of(date, startLocal, zone).toInstant().toEpochMilli()

                // 已经开课（或距离开课不足 1 分钟）不再提醒
                if (classStartMs - MIN_BEFORE_CLASS_MS <= nowMillis) continue

                val idealTriggerMs = classStartMs - leadMs
                // 若当前已进入“课前 N 分钟”窗口，直接安排在当前后 2 秒尽快触发；否则按理想时刻触发
                val triggerAtMs = if (idealTriggerMs <= nowMillis) nowMillis + 2_000L else idealTriggerMs

                val unitText = if (s.startUnit == s.endUnit) {
                    "第 ${s.startUnit} 节"
                } else {
                    "第 ${s.startUnit}-${s.endUnit} 节"
                }
                val displayName = CourseShortener.shorten(s.courseName, shortenNames)
                val room = ScheduleText.tidyPlace(s.roomText ?: s.campusText).orEmpty()
                val key = "$date|${s.courseCode}|${s.startUnit}-${s.endUnit}|$classStartMs"
                if (key == lastFiredKey) continue

                candidates += ClassReminderTarget(
                    sessionKey = key,
                    triggerAtMillis = triggerAtMs,
                    classStartMillis = classStartMs,
                    courseName = displayName,
                    roomText = room,
                    startClock = startClock,
                    endClock = endClock,
                    unitRangeText = unitText,
                    leadMinutes = leadMinutes,
                )
            }
        }
        return candidates.minByOrNull { it.triggerAtMillis }
    }

    private fun resolveVisibleSessions(
        sessions: List<Session>,
        choicesBySemester: Map<Int, List<ConflictDisplayChoice>>,
    ): List<Session> {
        val (indexed, unindexed) = sessions.partition {
            it.weekIndex > 0 && it.weekday in 1..7 && it.startUnit in 1..10 && it.endUnit >= it.startUnit
        }
        val fromBlocks = indexed.groupBy { it.semesterId }.flatMap { (semesterId, group) ->
            val choices = choicesBySemester[semesterId].orEmpty()
            scheduleBlocks(group, choices).map { block -> block.displaySession }
        }
        return (fromBlocks + unindexed).distinctBy { it.id }
    }

    private fun parseLocalTime(raw: String): LocalTime? {
        val parts = raw.trim().split(':')
        if (parts.size < 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].take(2).toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return LocalTime.of(h, m)
    }
}
