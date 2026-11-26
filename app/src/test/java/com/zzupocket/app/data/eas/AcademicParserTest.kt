package com.zzupocket.app.data.eas

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 郑大教务（成绩 / GPA排名 / 考场 / 学籍）H5 契约解析单测 */
class AcademicParserTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Test
    fun `成绩与GPA排名JSON映射正确处理正常与未评教与补考状态`() {
        val rawGradesJson = """
            [
              {
                "semesterCode": "2025-2026-1",
                "courseCode": "CS201",
                "courseNameZh": "操作系统",
                "credits": 4.0,
                "gp": 3.7,
                "finalGrade": "88",
                "passed": true,
                "retake": false,
                "cultivateType": {"nameZh": "主修"},
                "lesson": {
                  "courseType": {"nameZh": "必修"},
                  "courseProperty": {"nameZh": "专业核心"}
                },
                "subGrades": [
                  {"published": 1, "result": "85", "subGradeType": {"nameZh": "平时成绩"}},
                  {"published": 1, "result": "90", "subGradeType": {"nameZh": "期末成绩"}},
                  {"published": 0, "result": "99", "subGradeType": {"nameZh": "隐藏项"}}
                ]
              },
              {
                "semesterCode": "2025-2026-2",
                "courseCode": "PE102",
                "courseNameZh": "大学体育Ⅱ",
                "credits": 1.0,
                "gp": 0.0,
                "finalGrade": "MAKEUP",
                "passed": false,
                "gradeDisplayShowStatus": "surveyLesson"
              }
            ]
        """.trimIndent()

        val dtos = json.decodeFromString(ListSerializer(EasClient.GradeItemDto.serializer()), rawGradesJson)
        val parsed = AcademicParser.parseGrades(dtos, mapOf("2025-2026-1" to "2025-2026 第一学期"))
        assertEquals(2, parsed.size)

        val g1 = parsed[0]
        assertEquals("2025-2026 第一学期", g1.semesterName)
        assertEquals(3.7, g1.gp!!, 1e-6)
        assertEquals("88", g1.finalGrade)
        assertEquals(2, g1.subGrades.size)
        assertEquals("平时成绩", g1.subGrades[0].name)

        val g2 = parsed[1]
        assertTrue(g2.isUnevaluated)
        assertNull(g2.gp)
        assertEquals("未评教", g2.finalGrade)
        assertEquals("2025-2026 第二学期", g2.semesterName)

        val rankDto = json.decodeFromString(
            EasClient.GpaRankDto.serializer(),
            """{"gpa": "3.682", "rank": 12, "total": 185}""",
        )
        val rank = AcademicParser.parseGpaRank(rankDto, fetchedAt = 1000L)
        assertEquals(3.682, rank.gpa!!, 1e-6)
        assertEquals(12, rank.rank)
        assertEquals(185, rank.total)
    }

    @Test
    fun `考场与学籍档案JSON映射兼容整数时刻与嵌套对象`() {
        val examsJson = """
            [
              {
                "id": 9001,
                "courseNameZh": "计算机网络",
                "examDate": "2026-06-22T00:00:00+08:00",
                "weekDay": 1,
                "startTime": 900,
                "endTime": 1100,
                "place": "北2 205 · 座位 18",
                "examType": {"nameZh": "期末考试"},
                "cultivateType": {"nameZh": "主修"}
              }
            ]
        """.trimIndent()
        val exams = AcademicParser.parseExams(
            json.decodeFromString(ListSerializer(EasClient.ExamItemDto.serializer()), examsJson),
        )
        assertEquals(1, exams.size)
        assertEquals("2026-06-22", exams[0].examDate)
        assertEquals("09:00", exams[0].startTimeText)
        assertEquals("11:00", exams[0].endTimeText)
        assertFalse(exams[0].isMinor)

        val baseJson = """
            [
              {
                "code": "202400000001",
                "grade": "2024",
                "department": {"nameZh": "计算机与人工智能学院"},
                "major": {"nameZh": "计算机科学与技术"},
                "campus": {"nameZh": "主校区"},
                "stdStatus": {"nameZh": "在籍"},
                "getInSchool": true,
                "personInfo": {"nameZh": "张同学", "gender": {"nameZh": "男"}}
              }
            ]
        """.trimIndent()
        val regJson = """
            [
              {"studentCode": "202400000001", "semester": {"nameZh": "2025-2026 第一学期"}, "registered": 1, "payed": true}
            ]
        """.trimIndent()
        val profiles = AcademicParser.parseStudentProfiles(
            baseInfos = json.decodeFromString(ListSerializer(EasClient.StudentBaseInfoDto.serializer()), baseJson),
            registers = json.decodeFromString(ListSerializer(EasClient.StudentRegisterDto.serializer()), regJson),
        )
        assertEquals(1, profiles.size)
        val p = profiles.first()
        assertEquals("张同学", p.name)
        assertEquals("计算机科学与技术", p.major)
        assertEquals(true, p.inSchool)
        assertEquals(1, p.registers.size)
        assertTrue(p.registers.first().registered)
        assertEquals(true, p.registers.first().payed)
    }
}
