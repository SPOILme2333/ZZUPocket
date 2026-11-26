package com.zzupocket.app.data.eas

import com.zzupocket.app.domain.ExamRecord
import com.zzupocket.app.domain.GpaRankSummary
import com.zzupocket.app.domain.GradeRecord
import com.zzupocket.app.domain.RegisterRecord
import com.zzupocket.app.domain.StudentProfile
import com.zzupocket.app.domain.SubGradeItem
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/**
 * 郑大教务（成绩 / GPA 排名 / 考场安排 / 学籍档案）纯函数响应映射器。
 */
object AcademicParser {

    fun parseGrades(
        items: List<EasClient.GradeItemDto>,
        semesterNameMap: Map<String, String> = emptyMap(),
    ): List<GradeRecord> {
        val seenIds = HashMap<String, Int>()
        return items.map { dto ->
            val unevaluated = dto.gradeDisplayShowStatus == "surveyLesson"
            val waitDelay = dto.state == "WAIT_FOR_DELAY"
            val rawFinal = formatGradeText(dto.finalGrade.asCleanString())
            val finalDisplay = when {
                unevaluated -> "未评教"
                waitDelay -> "待缓考"
                rawFinal.isNotBlank() -> rawFinal
                else -> "--"
            }
            val displayTag = when (dto.gradeDisplayShowStatus) {
                "makeupGrade" -> "补考"
                "delayedExamination" -> "缓考"
                else -> null
            }
            val subItems = buildList {
                for (sg in dto.subGrades) {
                    val pub = sg.published.asInt() == 1 || sg.published.asBoolean() == true
                    if (pub) {
                        val typeName = sg.subGradeType?.nameZh?.takeIf { it.isNotBlank() } ?: "分项"
                        val scoreVal = if (typeName.contains("期末") && waitDelay) {
                            "缓考"
                        } else {
                            formatGradeText(sg.result.asCleanString()).ifBlank { "--" }
                        }
                        add(SubGradeItem(name = typeName, score = scoreVal))
                    }
                    for (proc in sg.processGradeSearchVms) {
                        val pName = proc.name?.takeIf { it.isNotBlank() } ?: continue
                        val pScore = formatGradeText(proc.result.asCleanString()).ifBlank { "--" }
                        add(SubGradeItem(name = pName, score = pScore))
                    }
                }
                dto.makeupGrade?.result.asCleanString().takeIf { it.isNotBlank() }?.let {
                    add(SubGradeItem(name = "补考", score = it))
                }
                dto.delayGrade?.result.asCleanString().takeIf { it.isNotBlank() }?.let {
                    add(SubGradeItem(name = "缓考", score = it))
                }
            }

            val baseId = "${dto.semesterCode}|${dto.courseCode.ifBlank { dto.courseNameZh }}"
            val seq = (seenIds[baseId] ?: 0) + 1
            seenIds[baseId] = seq
            val uniqueId = if (seq == 1) baseId else "$baseId#$seq"

            val semName = semesterNameMap[dto.semesterCode] ?: formatSemesterCode(dto.semesterCode)
            GradeRecord(
                id = uniqueId,
                semesterCode = dto.semesterCode,
                semesterName = semName,
                courseCode = dto.courseCode,
                courseName = dto.courseNameZh.ifBlank { dto.courseNameEn.orEmpty() },
                credits = dto.credits,
                gp = if (unevaluated || waitDelay) null else dto.gp.asDouble(),
                finalGrade = finalDisplay,
                passed = if (unevaluated) true else dto.passed,
                retake = dto.retake,
                isMinor = dto.cultivateType?.nameZh == "辅修",
                isUnevaluated = unevaluated,
                courseType = dto.lesson?.courseType?.nameZh?.takeIf { it.isNotBlank() },
                courseProperty = dto.lesson?.courseProperty?.nameZh?.takeIf { it.isNotBlank() },
                displayTag = displayTag,
                subGrades = subItems,
            )
        }
    }

    fun parseGpaRank(dto: EasClient.GpaRankDto, fetchedAt: Long = System.currentTimeMillis()): GpaRankSummary =
        GpaRankSummary(
            gpa = dto.gpa.asDouble(),
            rank = dto.rank.asInt()?.takeIf { it > 0 },
            total = dto.total.asInt()?.takeIf { it > 0 },
            fetchedAt = fetchedAt,
        )

    fun parseExams(items: List<EasClient.ExamItemDto>): List<ExamRecord> =
        items.mapIndexed { idx, dto ->
            val stableId = if (dto.id != 0L) dto.id.toString() else "exam-$idx-${dto.courseNameZh}"
            ExamRecord(
                id = stableId,
                courseName = dto.courseNameZh.ifBlank { dto.courseNameEn.orEmpty() },
                examDate = dto.examDate?.take(10),
                weekDay = dto.weekDay,
                startTime = dto.startTime,
                endTime = dto.endTime,
                place = dto.place?.trim().orEmpty().ifBlank { dto.placeEn?.trim().orEmpty() },
                examType = dto.examType?.nameZh?.takeIf { it.isNotBlank() },
                isMinor = dto.cultivateType?.nameZh != null && dto.cultivateType.nameZh != "主修",
            )
        }.sortedWith(compareBy({ it.examDate ?: "9999-99-99" }, { it.startTime }, { it.courseName }))

    fun parseStudentProfiles(
        baseInfos: List<EasClient.StudentBaseInfoDto>,
        registers: List<EasClient.StudentRegisterDto> = emptyList(),
        graduates: List<EasClient.StudentGraduateDto> = emptyList(),
    ): List<StudentProfile> {
        val regByCode = registers.groupBy { it.studentCode.orEmpty() }
        val gradByCode = graduates.associateBy { it.studentCode.orEmpty() }
        return baseInfos.map { base ->
            val regList = (regByCode[base.code] ?: registers.filter { it.studentCode.isNullOrBlank() })
                .map { r ->
                    val regFlag = r.registered.asInt() == 1 || r.registered.asBoolean() == true
                    val payFlag = r.payed?.let { it.asBoolean() ?: (it.asInt() == 1) }
                    RegisterRecord(
                        semesterName = r.semester?.nameZh?.takeIf { it.isNotBlank() } ?: "未知学期",
                        registered = regFlag,
                        payed = payFlag,
                    )
                }
            val expectedGrad = gradByCode[base.code]?.expectedGraduateDate?.take(10)
            StudentProfile(
                studentCode = base.code,
                name = base.personInfo?.nameZh?.takeIf { it.isNotBlank() }
                    ?: base.personInfo?.nameEn.orEmpty(),
                gender = base.personInfo?.gender?.nameZh,
                nation = base.personInfo?.nation?.nameZh,
                grade = base.grade ?: base.enterSchoolGrade,
                department = base.department?.nameZh,
                major = base.major?.nameZh,
                majorDirection = base.majorDirection?.nameZh,
                adminClass = base.adminclass?.nameZh,
                campus = base.campus?.nameZh,
                education = base.education?.nameZh,
                cultivateType = base.cultivateType?.nameZh,
                statusText = base.stdStatus?.nameZh,
                inSchool = base.getInSchool,
                enterDate = base.enterSchoolDate?.take(10),
                expectedGraduateDate = expectedGrad,
                studyForm = base.studyForm?.nameZh,
                tutor = base.firstTutor?.nameZh,
                registers = regList,
            )
        }
    }

    private fun formatGradeText(raw: String): String = when {
        raw.contains("MAKEUP") -> "补考"
        raw.contains("DELAYED") -> "缓考"
        else -> raw
    }

    private fun formatSemesterCode(code: String): String {
        val m = Regex("^(\\d{4}-\\d{4})-(\\d+)$").find(code.trim())
        if (m != null) {
            val term = when (m.groupValues[2]) {
                "1" -> "第一学期"
                "2" -> "第二学期"
                "3" -> "第三学期"
                else -> "第${m.groupValues[2]}学期"
            }
            return "${m.groupValues[1]} $term"
        }
        return code.ifBlank { "未知学期" }
    }

    private fun JsonPrimitive?.asCleanString(): String =
        this?.contentOrNull?.takeIf { it != "null" }?.trim().orEmpty()

    private fun JsonPrimitive?.asDouble(): Double? =
        this?.doubleOrNull ?: this?.contentOrNull?.toDoubleOrNull()

    private fun JsonPrimitive?.asInt(): Int? =
        this?.intOrNull ?: this?.contentOrNull?.toIntOrNull()

    private fun JsonPrimitive?.asBoolean(): Boolean? =
        this?.booleanOrNull
}
