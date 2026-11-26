package com.zzupocket.app.data.eas

import com.zzupocket.app.domain.PlanCourseStatus
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 教务「培养方案」与「培养方案完成情况」解析器单测 */
class TrainingPlanParserTest {

    @Test
    fun `完成情况多级模块解析、状态映射、替代课程与未修筛选`() {
        val dto = EasClient.PlanCompletionInfoDto(
            requireInfo = EasClient.PlanRequireInfoDto(
                credits = JsonPrimitive(160.0),
                requiredSubModuleNum = 2,
            ),
            modules = listOf(
                EasClient.PlanCompletionModuleDto(
                    type = EasClient.NamedDto(nameZh = "公共基础课", code = "GGJC"),
                    typeCode = "GGJC",
                    result = "未通过",
                    requireInfo = EasClient.PlanRequireInfoDto(
                        requiredCredits = JsonPrimitive(40.0),
                        requiredSubModuleNum = 2,
                    ),
                    passedCredits = JsonPrimitive(12.0),
                    skip = JsonPrimitive(28.0),
                    taking = JsonPrimitive(4.0),
                    children = listOf(
                        EasClient.PlanCompletionModuleDto(
                            type = EasClient.NamedDto(nameZh = "思想政治类", code = "SZ"),
                            result = "通过",
                            requireInfo = EasClient.PlanRequireInfoDto(requiredCredits = JsonPrimitive(12.0)),
                            passedCredits = JsonPrimitive(12.0),
                            planCourses = listOf(
                                EasClient.PlanCompletionCourseDto(
                                    course = EasClient.PlanCourseBriefDto(
                                        code = "POL101",
                                        nameZh = "马克思主义基本原理",
                                        credits = JsonPrimitive(3.0),
                                    ),
                                    compulsory = true,
                                    result = "通过",
                                    resultScore = JsonPrimitive("91"),
                                    gp = JsonPrimitive(4.0),
                                    terms = listOf("TERM_1", "AUTUMN"),
                                    marks = listOf("首修"),
                                ),
                            ),
                        ),
                        EasClient.PlanCompletionModuleDto(
                            type = EasClient.NamedDto(nameZh = "高等数学类", code = "MATH"),
                            result = "未通过",
                            requireInfo = EasClient.PlanRequireInfoDto(requiredCredits = JsonPrimitive(10.0)),
                            passedCredits = JsonPrimitive(0.0),
                            taking = JsonPrimitive(4.0),
                            planCourses = listOf(
                                EasClient.PlanCompletionCourseDto(
                                    course = EasClient.PlanCourseBriefDto(
                                        code = "MAT101",
                                        nameZh = "高等数学 A(上)",
                                        credits = JsonPrimitive(4.0),
                                    ),
                                    compulsory = true,
                                    result = "在读",
                                    terms = listOf("TERM_2", "TERM_TERM_NULL"),
                                ),
                                EasClient.PlanCompletionCourseDto(
                                    course = EasClient.PlanCourseBriefDto(
                                        code = "MAT102",
                                        nameZh = "高等数学 A(下)",
                                        credits = JsonPrimitive(5.0),
                                    ),
                                    compulsory = true,
                                    result = "未修",
                                    substituteCourses = listOf(
                                        JsonObject(mapOf("nameZh" to JsonPrimitive("微积分(上)"))),
                                    ),
                                    alternativeCourses = listOf(
                                        JsonObject(
                                            mapOf(
                                                "course" to JsonObject(
                                                    mapOf("nameZh" to JsonPrimitive("工科数学分析")),
                                                ),
                                            ),
                                        ),
                                    ),
                                ),
                                EasClient.PlanCompletionCourseDto(
                                    course = EasClient.PlanCourseBriefDto(
                                        code = "MAT103",
                                        nameZh = "线性代数",
                                        credits = JsonPrimitive(3.0),
                                    ),
                                    result = "不通过",
                                    resultScore = JsonPrimitive("52"),
                                    gp = JsonPrimitive(0.0),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            outplanCourses = listOf(
                EasClient.PlanCompletionCourseDto(
                    course = EasClient.PlanCourseBriefDto(
                        code = "ART001",
                        nameZh = "合唱与指挥鉴赏",
                        credits = JsonPrimitive(1.5),
                    ),
                    result = "通过",
                    resultScore = JsonPrimitive("优秀"),
                    terms = listOf("SPRING"),
                ),
            ),
        )

        val parsed = TrainingPlanParser.parseCompletion(
            cultivateTypeCode = "MAJOR",
            cultivateTypeName = "主修",
            dto = dto,
            fetchedAt = 123456L,
        )

        assertEquals(160.0, parsed.totalRequiredCredits, 1e-6)
        assertEquals(12.0, parsed.totalPassedCredits, 1e-6)
        assertEquals(4.0, parsed.totalTakingCredits, 1e-6)
        assertEquals(1, parsed.modules.size)
        assertEquals(1, parsed.outplanCourses.size)
        assertEquals("春", parsed.outplanCourses.first().termsText)

        val rootMod = parsed.modules.first()
        assertFalse(rootMod.passed)
        assertEquals(1, rootMod.passedSubModuleNum)
        assertEquals(2, rootMod.requiredSubModuleNum)

        val szCourse = rootMod.children[0].courses[0]
        assertEquals(PlanCourseStatus.PASSED, szCourse.status)
        assertEquals("第1学期、秋", szCourse.termsText)
        assertEquals("91", szCourse.scoreText)

        val mathCourses = rootMod.children[1].courses
        assertEquals(PlanCourseStatus.TAKING, mathCourses[0].status)
        assertEquals("第2学期", mathCourses[0].termsText)
        assertEquals(PlanCourseStatus.UNTAKEN, mathCourses[1].status)
        assertEquals(listOf("微积分(上)", "工科数学分析"), mathCourses[1].substitutes)
        assertEquals(PlanCourseStatus.FAILED, mathCourses[2].status)

        // 按「未修」筛选：应裁掉全已通过的“思想政治类”，只保留含未修的“高等数学类”且只留 1 门未修课
        val untakenFiltered = rootMod.filterByStatus(PlanCourseStatus.UNTAKEN)
        assertNotNull(untakenFiltered)
        assertEquals(1, untakenFiltered!!.children.size)
        assertEquals("高等数学类", untakenFiltered.children.first().name)
        assertEquals(1, untakenFiltered.children.first().courses.size)
        assertEquals("MAT102", untakenFiltered.children.first().courses.first().code)

        // 思想政治类模块按「未修」过滤应返回 null
        assertNull(rootMod.children[0].filterByStatus(PlanCourseStatus.UNTAKEN))
    }

    @Test
    fun `指导性培养方案解析培养目标HTML清洗、毕业要求父子树与课程体系`() {
        val info = EasClient.StudentProgramInfoDto(
            id = 8899L,
            grade = "2024",
            requireCredits = JsonPrimitive(165.0),
            department = EasClient.NamedDto(nameZh = "计算机与人工智能学院"),
            major = EasClient.NamedDto(nameZh = "计算机科学与技术"),
            education = EasClient.NamedDto(nameZh = "本科"),
            cultivateType = EasClient.NamedDto(nameZh = "主修", code = "MAJOR"),
        )
        val tree = EasClient.PlanCoursesTreeDto(
            culTypeIndex = 0,
            children = listOf(
                EasClient.PlanGuideModuleDto(
                    type = EasClient.NamedDto(nameZh = "专业核心课", code = "ZYHX"),
                    requireInfo = EasClient.PlanRequireInfoDto(requiredCredits = JsonPrimitive(32.0)),
                    planCourses = listOf(
                        EasClient.PlanGuideCourseDto(
                            course = EasClient.PlanCourseBriefDto(
                                code = "CS201",
                                nameZh = "数据结构",
                                credits = JsonPrimitive(4.0),
                            ),
                            compulsory = true,
                            terms = listOf("TERM_3"),
                            remark = "学位核心",
                        ),
                    ),
                ),
            ),
        )
        val objectives = listOf(
            EasClient.TrainObjectiveItemDto(
                indexNo = JsonPrimitive(1),
                type = EasClient.NamedDto(nameZh = "总体目标"),
                content = "<p>培养具有&nbsp;工程实践能力的高素质人才。</p><p>具备扎实数理基础。</p>",
            ),
        )
        val gradReqs = listOf(
            EasClient.GraduationRequirementItemDto(
                id = JsonPrimitive(10),
                parentId = null,
                indexNo = JsonPrimitive(1),
                nameZh = "工程知识",
                contentZh = "<p>掌握数学、自然科学与计算机专业知识。</p>",
            ),
            EasClient.GraduationRequirementItemDto(
                id = JsonPrimitive(11),
                parentId = JsonPrimitive(10),
                indexNo = JsonPrimitive("1.1"),
                nameZh = "数理基础",
                contentZh = "能将数学工具用于复杂工程建模。",
            ),
        )

        val program = TrainingPlanParser.parseProgram(
            info = info,
            coursesTree = tree,
            objectives = objectives,
            gradReqs = gradReqs,
            fetchedAt = 999L,
        )

        assertEquals("计算机科学与技术", program.major)
        assertEquals(165.0, program.requireCredits ?: 0.0, 1e-6)
        assertEquals(1, program.objectives.size)
        assertEquals("培养具有 工程实践能力的高素质人才。\n具备扎实数理基础。", program.objectives[0].content)
        assertEquals(1, program.graduationRequirements.size)
        assertEquals(1, program.graduationRequirements[0].subItems.size)
        assertEquals("1.1", program.graduationRequirements[0].subItems[0].indexNo)
        assertEquals(1, program.courseModules.size)
        assertEquals(1, program.courseModules[0].totalCourseCount())
        assertEquals("第3学期", program.courseModules[0].courses[0].termsText)
    }
}
