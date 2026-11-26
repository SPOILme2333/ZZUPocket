package com.zzupocket.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TrainingPlanAnalyzerTest {

    private fun grade(
        id: String,
        sem: String,
        name: String,
        credits: Double,
        property: String?,
        type: String? = null,
        passed: Boolean = true,
        isMinor: Boolean = false,
        isUnevaluated: Boolean = false,
    ) = GradeRecord(
        id = id,
        semesterCode = sem,
        semesterName = sem,
        courseCode = id,
        courseName = name,
        credits = credits,
        gp = 3.5,
        finalGrade = "85",
        passed = passed,
        retake = false,
        isMinor = isMinor,
        isUnevaluated = isUnevaluated,
        courseType = type,
        courseProperty = property,
        displayTag = null,
        subGrades = emptyList(),
    )

    @Test
    fun `aggregates passed major courses filters invalid rows and merges unstarted target categories`() {
        val grades = listOf(
            grade("1", "2025-2026-1", "高等数学", 5.0, "通修通识课"),
            grade("2", "2025-2026-2", "数据结构", 4.0, "专业必修课"),
            grade("3", "2025-2026-2", "离散数学", 4.0, "专业必修课"),
            grade("4", "2025-2026-2", "体育挂科", 1.0, "必修课", passed = false),
            grade("5", "2025-2026-2", "辅修课程", 3.0, "专业必修课", isMinor = true),
            grade("6", "2025-2026-2", "未评教课程", 2.0, "通修通识课", isUnevaluated = true),
            grade("7", "2025-2026-2", "形式与政策", 1.0, null, type = "思想政治课"),
        )
        val config = TrainingPlanConfig(
            totalTargetCredits = 160.0,
            categoryTargets = mapOf(
                "专业必修课" to 40.0,
                "专业选修课" to 20.0,
            ),
        )

        val snapshot = TrainingPlanAnalyzer.build(grades, config)
        assertEquals(14.0, snapshot.totalEarned, 1e-6)
        assertEquals(160.0, snapshot.totalTarget, 1e-6)
        assertEquals(4, snapshot.modules.size)

        val majorReq = snapshot.modules[0]
        assertEquals("专业必修课", majorReq.category)
        assertEquals(8.0, majorReq.earnedCredits, 1e-6)
        assertEquals(40.0, majorReq.targetCredits!!, 1e-6)
        assertEquals(listOf("数据结构", "离散数学"), majorReq.courses.map { it.courseName })

        val unstarted = snapshot.modules.last()
        assertEquals("专业选修课", unstarted.category)
        assertEquals(0.0, unstarted.earnedCredits, 1e-6)
        assertEquals(20.0, unstarted.targetCredits!!, 1e-6)
        assertEquals(0, unstarted.courses.size)
    }
}
