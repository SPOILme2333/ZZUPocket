package com.zzupocket.app.notify

import com.zzupocket.app.domain.ConflictDisplayChoice
import com.zzupocket.app.domain.Period
import com.zzupocket.app.domain.Session
import com.zzupocket.app.domain.conflictDisplayKey
import com.zzupocket.app.domain.scheduleBlocks
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** 课前提醒纯规划器单测 */
class ClassReminderPlannerTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val periods = listOf(
        Period(1, "第1节", 800, 845, "MORNING", 1),
        Period(2, "第2节", 855, 940, "MORNING", 1),
        Period(3, "第3节", 1010, 1055, "MORNING", 2),
        Period(4, "第4节", 1105, 1150, "MORNING", 2),
    )

    private fun makeSession(
        id: String,
        code: String,
        name: String,
        date: LocalDate,
        startUnit: Int,
        endUnit: Int,
        room: String? = "北3 202",
    ) = Session(
        id = id,
        semesterId = 10,
        source = "eas",
        courseCode = code,
        courseName = name,
        weekIndex = 5,
        weekday = date.dayOfWeek.value,
        startUnit = startUnit,
        endUnit = endUnit,
        date = date.toString(),
        startTime = null,
        endTime = null,
        teacherName = "王教授",
        roomText = room,
        campusText = null,
        color = null,
    )

    @Test
    fun `精确算出下一节课提前触发时刻与课程简称`() {
        val today = LocalDate.of(2026, 10, 12)
        val nowMs = ZonedDateTime.of(today, LocalTime.of(9, 0), zone).toInstant().toEpochMilli()
        val earlyPassed = makeSession("s1", "C1", "线性代数", today, 1, 2)
        val next = makeSession("s2", "C2", "习近平新时代中国特色社会主义思想概论", today, 3, 4)

        val target = ClassReminderPlanner.nextTarget(
            daySessions = listOf(today to listOf(earlyPassed, next)),
            periods = periods,
            leadMinutes = 15,
            nowMillis = nowMs,
            shortenNames = true,
            zone = zone,
        )

        assertNotNull(target)
        assertEquals("习概", target!!.courseName)
        assertEquals("北3 202", target.roomText)
        assertEquals("10:10", target.startClock)
        val expectedTrigger = ZonedDateTime.of(today, LocalTime.of(9, 55), zone).toInstant().toEpochMilli()
        assertEquals(expectedTrigger, target.triggerAtMillis)
    }

    @Test
    fun `已响铃防重跳过并自动衔接下一天早八`() {
        val today = LocalDate.of(2026, 10, 12)
        val tomorrow = today.plusDays(1)
        val nowMs = ZonedDateTime.of(today, LocalTime.of(9, 58), zone).toInstant().toEpochMilli()
        val todayClass = makeSession("s1", "C2", "数据结构", today, 3, 4)
        val tomorrowClass = makeSession("s2", "C3", "编译原理", tomorrow, 1, 2)

        val firstTarget = ClassReminderPlanner.nextTarget(
            daySessions = listOf(today to listOf(todayClass), tomorrow to listOf(tomorrowClass)),
            periods = periods,
            leadMinutes = 15,
            nowMillis = nowMs,
            lastFiredKey = null,
            zone = zone,
        )
        assertNotNull(firstTarget)
        assertEquals("数据结构", firstTarget!!.courseName)
        // 已经落在开课前 12 分钟窗口内 → 立即补偿（now + 2s）
        assertEquals(nowMs + 2_000L, firstTarget.triggerAtMillis)

        // 记录防重键后，下一次规划直接指向明天早八
        val secondTarget = ClassReminderPlanner.nextTarget(
            daySessions = listOf(today to listOf(todayClass), tomorrow to listOf(tomorrowClass)),
            periods = periods,
            leadMinutes = 15,
            nowMillis = nowMs,
            lastFiredKey = firstTarget.sessionKey,
            zone = zone,
        )
        assertNotNull(secondTarget)
        assertEquals("编译原理", secondTarget!!.courseName)
    }

    @Test
    fun `冲突选课后仅提醒选中的那门课且leadMinutes为0返回null`() {
        val today = LocalDate.of(2026, 10, 12)
        val nowMs = ZonedDateTime.of(today, LocalTime.of(7, 0), zone).toInstant().toEpochMilli()
        val s1 = makeSession("s1", "A", "高等数学（一）", today, 1, 2)
        val s2 = makeSession("s2", "B", "软件工程导论", today, 1, 2)
        val block = scheduleBlocks(listOf(s1, s2)).first()
        val choice = ConflictDisplayChoice(10, block.conflictKey, 5, s2.conflictDisplayKey())

        val picked = ClassReminderPlanner.nextTarget(
            daySessions = listOf(today to listOf(s1, s2)),
            periods = periods,
            choicesBySemester = mapOf(10 to listOf(choice)),
            leadMinutes = 10,
            nowMillis = nowMs,
            shortenNames = true,
            zone = zone,
        )
        assertEquals("软工导论", picked?.courseName)

        assertNull(
            ClassReminderPlanner.nextTarget(
                daySessions = listOf(today to listOf(s1)),
                periods = periods,
                leadMinutes = 0,
                nowMillis = nowMs,
                zone = zone,
            ),
        )
    }
}
