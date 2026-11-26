package com.zzupocket.app.domain

import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 课表 RFC 5545 .ics 导出器单测 */
class IcsExporterTest {

    private val periods = listOf(
        Period(indexNo = 1, nameZh = "第1节", startTime = 800, endTime = 845, dayPart = "MORNING", segmentIndex = 1),
        Period(indexNo = 2, nameZh = "第2节", startTime = 855, endTime = 940, dayPart = "MORNING", segmentIndex = 1),
    )

    private fun sampleSession(
        id: String,
        code: String,
        name: String,
        room: String? = "南1 101",
    ) = Session(
        id = id,
        semesterId = 1,
        source = "eas",
        courseCode = code,
        courseName = name,
        weekIndex = 3,
        weekday = 1,
        startUnit = 1,
        endUnit = 2,
        date = "2026-09-14",
        startTime = "08:00",
        endTime = "09:40",
        teacherName = "张老师",
        roomText = room,
        campusText = "主校区",
        color = null,
    )

    @Test
    fun `生成标准RFC5545日历与CRLF及VALARM`() {
        val ics = IcsExporter.export(
            calendarName = "郑大课表",
            sessions = listOf(sampleSession("s1", "CS101", "数据结构, 算法; 导论")),
            periods = periods,
            semesterStart = LocalDate.of(2026, 8, 31),
            reminderMinutes = 15,
            nowStampUtc = "20260901T000000Z",
        )
        assertTrue(ics.contains("\r\n"))
        assertTrue(ics.startsWith("BEGIN:VCALENDAR\r\n"))
        assertTrue(ics.contains("X-WR-TIMEZONE:Asia/Shanghai\r\n"))
        assertTrue(ics.contains("DTSTART;TZID=Asia/Shanghai:20260914T080000\r\n"))
        assertTrue(ics.contains("DTEND;TZID=Asia/Shanghai:20260914T094000\r\n"))
        assertTrue(ics.contains("SUMMARY:数据结构\\, 算法\\; 导论\r\n"))
        assertTrue(ics.contains("LOCATION:南1 101\r\n"))
        assertTrue(ics.contains("TRIGGER:-PT15M\r\n"))
        assertTrue(ics.trimEnd().endsWith("END:VCALENDAR"))
    }

    @Test
    fun `已解决冲突仅导出选定课程未解决时保留全部课程`() {
        val a = sampleSession("s1", "A01", "离散数学")
        val b = sampleSession("s2", "B02", "体育舞蹈")
        val block = scheduleBlocks(listOf(a, b)).first()

        // 未解决冲突：全部导出
        val unresolved = IcsExporter.export(
            calendarName = "课表",
            sessions = listOf(a, b),
            periods = periods,
        )
        assertTrue(unresolved.contains("SUMMARY:离散数学"))
        assertTrue(unresolved.contains("SUMMARY:体育舞蹈"))

        // 用户选择了 B02：只导 B02
        val choice = ConflictDisplayChoice(
            semesterId = 1,
            conflictKey = block.conflictKey,
            weekIndex = 3,
            sessionKey = b.conflictDisplayKey(),
        )
        val resolved = IcsExporter.export(
            calendarName = "课表",
            sessions = listOf(a, b),
            periods = periods,
            conflictChoices = listOf(choice),
        )
        assertFalse(resolved.contains("SUMMARY:离散数学"))
        assertTrue(resolved.contains("SUMMARY:体育舞蹈"))
    }

    @Test
    fun `导出官方考场安排含考场地点与考前VALARM提醒`() {
        val exam = ExamRecord(
            id = "e1",
            courseName = "编译原理",
            examDate = "2026-06-25",
            weekDay = 4,
            startTime = 1430,
            endTime = 1630,
            place = "核心教学区南3 301 座位22",
            examType = "期末考试",
            isMinor = false,
        )
        val ics = IcsExporter.exportExams(exams = listOf(exam), reminderMinutes = 60)
        assertTrue(ics.contains("DTSTART;TZID=Asia/Shanghai:20260625T143000\r\n"))
        assertTrue(ics.contains("DTEND;TZID=Asia/Shanghai:20260625T163000\r\n"))
        assertTrue(ics.contains("SUMMARY:[期末考试] 编译原理 考试\r\n"))
        assertTrue(ics.contains("LOCATION:核心教学区南3 301 座位22\r\n"))
        assertTrue(ics.contains("TRIGGER:-PT60M\r\n"))
    }
}
