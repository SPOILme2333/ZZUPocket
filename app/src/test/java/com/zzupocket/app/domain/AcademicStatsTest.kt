package com.zzupocket.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 教务统计与模拟 GPA 计算单测 */
class AcademicStatsTest {

    private fun grade(
        id: String,
        sem: String = "2025-2026-1",
        credits: Double = 3.0,
        gp: Double? = 4.0,
        passed: Boolean = true,
        uneval: Boolean = false,
        prop: String? = "学科基础课",
    ) = GradeRecord(
        id = id,
        semesterCode = sem,
        semesterName = sem,
        courseCode = id,
        courseName = "课程 $id",
        credits = credits,
        gp = gp,
        finalGrade = if (passed) "92" else "45",
        passed = passed,
        retake = false,
        isMinor = false,
        isUnevaluated = uneval,
        courseType = null,
        courseProperty = prop,
        displayTag = null,
        subGrades = emptyList(),
    )

    @Test
    fun `加权GPA忽略未评教与零学分条目`() {
        val list = listOf(
            grade("A", credits = 4.0, gp = 4.0),
            grade("B", credits = 2.0, gp = 3.0),
            grade("C", credits = 2.0, gp = 4.0, uneval = true),
            grade("D", credits = 0.0, gp = 4.0),
        )
        val gpa = AcademicStats.weightedGpa(list)
        assertNotNull(gpa)
        // (4*4 + 2*3) / 6 = 22 / 6 = 3.6666...
        assertEquals(22.0 / 6.0, gpa!!, 1e-6)
        // 已获学分含 C 吗？C 是未评教，过滤掉 → 只算 A + B = 6.0
        assertEquals(6.0, AcademicStats.earnedCredits(list), 1e-6)
    }

    @Test
    fun `学期趋势与课程性质学分统计`() {
        val list = listOf(
            grade("1", sem = "2024-2025-1", credits = 2.0, gp = 3.0, prop = "通识课"),
            grade("2", sem = "2024-2025-2", credits = 4.0, gp = 4.0, prop = "专业必修"),
            grade("3", sem = "2024-2025-2", credits = 2.0, gp = 1.0, passed = false, prop = "通识课"),
        )
        val trend = AcademicStats.semesterTrend(list)
        assertEquals(listOf("2024-2025-1", "2024-2025-2"), trend.map { it.semesterCode })
        assertEquals(3.0, trend[0].gpa, 1e-6)
        assertEquals((4.0 * 4.0 + 2.0 * 1.0) / 6.0, trend[1].gpa, 1e-6)

        val byProp = AcademicStats.creditsByProperty(list)
        // 未通过不计入已获学分
        assertEquals(listOf("专业必修" to 4.0, "通识课" to 2.0), byProp)
    }

    @Test
    fun `模拟算分支持排除课程与添加预估课程`() {
        val list = listOf(
            grade("1", credits = 4.0, gp = 3.0),
            grade("2", credits = 2.0, gp = 2.0),
        )
        val (sim, total) = AcademicStats.simulateGpa(
            baseGrades = list,
            excludedIds = setOf("2"),
            extras = listOf(SimulatedCourseInput("sim", "新增满绩课", 4.0, 4.0)),
        )
        assertEquals(8.0, total, 1e-6)
        assertEquals(3.5, sim!!, 1e-6)

        assertNull(AcademicStats.simulateGpa(baseGrades = list, excludedIds = setOf("1", "2")).first)
    }

    @Test
    fun `郑大四分制换算与隐私脱敏与新出分识别`() {
        assertEquals(4.0, AcademicStats.scoreToZzuGp(95.0), 1e-6)
        assertEquals(3.7, AcademicStats.scoreToZzuGp(86.0), 1e-6)
        assertEquals(3.3, AcademicStats.scoreToZzuGp(83.0), 1e-6)
        assertEquals(3.0, AcademicStats.scoreToZzuGp(79.0), 1e-6)
        assertEquals(1.0, AcademicStats.scoreToZzuGp(61.0), 1e-6)
        assertEquals(0.0, AcademicStats.scoreToZzuGp(59.0), 1e-6)

        assertEquals("张*", AcademicStats.maskName("张三"))
        assertEquals("李**", AcademicStats.maskName("李四五"))
        assertEquals("2024******01", AcademicStats.maskStudentCode("202412345601"))

        val fresh = listOf(grade("old"), grade("new1"), grade("unev", uneval = true))
        assertTrue(AcademicStats.detectNewGradeIds(emptySet(), fresh).isEmpty())
        assertEquals(setOf("new1"), AcademicStats.detectNewGradeIds(setOf("old"), fresh))
    }
}
