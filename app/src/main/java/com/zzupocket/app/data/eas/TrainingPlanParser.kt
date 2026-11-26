package com.zzupocket.app.data.eas

import com.zzupocket.app.domain.OfficialCompletionModule
import com.zzupocket.app.domain.OfficialGraduationRequirement
import com.zzupocket.app.domain.OfficialPlanCompletion
import com.zzupocket.app.domain.OfficialPlanCourse
import com.zzupocket.app.domain.OfficialProgramCourse
import com.zzupocket.app.domain.OfficialProgramModule
import com.zzupocket.app.domain.OfficialTrainObjective
import com.zzupocket.app.domain.OfficialTrainingProgram
import com.zzupocket.app.domain.PlanCourseStatus
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * 郑大教务「培养方案」与「培养方案完成情况」纯函数响应映射器（零 Android 依赖）。
 */
object TrainingPlanParser {

    /** 解析官方「培养方案完成情况」接口响应 */
    fun parseCompletion(
        cultivateTypeCode: String,
        cultivateTypeName: String,
        dto: EasClient.PlanCompletionInfoDto,
        fetchedAt: Long = System.currentTimeMillis(),
    ): OfficialPlanCompletion {
        val parsedModules = dto.modules.mapIndexed { idx, m ->
            parseCompletionModule(pathPrefix = idx.toString(), dto = m)
        }
        val totalPassed = parsedModules.sumOf { it.passedCredits }
        val totalTaking = parsedModules.sumOf { it.takingCredits }
        val moduleReqSum = parsedModules.sumOf { it.requiredCredits ?: 0.0 }
        val totalRequired = dto.requireInfo?.credits?.asDoubleOrNull()
            ?: dto.requireInfo?.requiredCredits?.asDoubleOrNull()
            ?: moduleReqSum

        val outplan = dto.outplanCourses.map { parseCompletionCourse(it) }

        return OfficialPlanCompletion(
            cultivateTypeCode = cultivateTypeCode,
            cultivateTypeName = cultivateTypeName.ifBlank { "主修" },
            totalRequiredCredits = totalRequired,
            totalPassedCredits = totalPassed,
            totalTakingCredits = totalTaking,
            modules = parsedModules,
            outplanCourses = outplan,
            fetchedAt = fetchedAt,
        )
    }

    private fun parseCompletionModule(
        pathPrefix: String,
        dto: EasClient.PlanCompletionModuleDto,
    ): OfficialCompletionModule {
        val children = dto.children.mapIndexed { idx, child ->
            parseCompletionModule(pathPrefix = "$pathPrefix-$idx", dto = child)
        }
        val passedSubCount = children.count { it.passed }
        val rawResult = dto.result?.trim().orEmpty()
        val isPassed = rawResult == "通过" || (rawResult.contains("通过") && !rawResult.contains("不") && !rawResult.contains("未"))
        val name = dto.type?.nameZh?.trim()?.takeIf { it.isNotEmpty() }
            ?: dto.type?.nameEn?.trim()?.takeIf { it.isNotEmpty() }
            ?: "未命名模块"
        val id = dto.typeCode?.takeIf { it.isNotBlank() }?.let { "$it#$pathPrefix" }
            ?: dto.type?.code?.takeIf { it.isNotBlank() }?.let { "$it#$pathPrefix" }
            ?: "$name#$pathPrefix"

        return OfficialCompletionModule(
            id = id,
            name = name,
            passed = isPassed,
            resultText = rawResult.ifBlank { if (isPassed) "通过" else "未通过" },
            requiredCredits = dto.requireInfo?.requiredCredits?.asDoubleOrNull()
                ?: dto.requireInfo?.credits?.asDoubleOrNull(),
            passedCredits = dto.passedCredits?.asDoubleOrNull() ?: 0.0,
            skipCredits = dto.skip?.asDoubleOrNull() ?: 0.0,
            takingCredits = dto.taking?.asDoubleOrNull() ?: 0.0,
            requiredSubModuleNum = dto.requireInfo?.requiredSubModuleNum?.takeIf { it > 0 },
            passedSubModuleNum = passedSubCount,
            courses = dto.planCourses.map { parseCompletionCourse(it) },
            children = children,
        )
    }

    private fun parseCompletionCourse(dto: EasClient.PlanCompletionCourseDto): OfficialPlanCourse {
        val rawResult = dto.result?.trim().orEmpty()
        val status = mapCourseStatus(rawResult)
        val substitutes = (dto.substituteCourses + dto.alternativeCourses)
            .mapNotNull { extractSubstituteName(it) }
            .distinct()

        return OfficialPlanCourse(
            code = dto.course?.code?.trim().orEmpty(),
            name = dto.course?.nameZh?.trim().orEmpty()
                .ifBlank { dto.course?.nameEn?.trim().orEmpty() }
                .ifBlank { "未命名课程" },
            credits = dto.course?.credits?.asDoubleOrNull() ?: 0.0,
            compulsory = dto.compulsory,
            status = status,
            resultText = rawResult.ifBlank { status.label },
            scoreText = dto.resultScore?.asCleanText(),
            gp = dto.gp?.asDoubleOrNull(),
            termsText = formatTerms(dto.terms),
            marks = dto.marks.map { it.trim() }.filter { it.isNotEmpty() },
            remark = dto.remark?.trim()?.takeIf { it.isNotEmpty() },
            substitutes = substitutes,
        )
    }

    /** 解析官方「指导性培养方案」全套数据（基本信息 + 课程体系 + 培养目标 + 毕业要求） */
    fun parseProgram(
        info: EasClient.StudentProgramInfoDto,
        coursesTree: EasClient.PlanCoursesTreeDto?,
        objectives: List<EasClient.TrainObjectiveItemDto> = emptyList(),
        gradReqs: List<EasClient.GraduationRequirementItemDto> = emptyList(),
        fetchedAt: Long = System.currentTimeMillis(),
    ): OfficialTrainingProgram {
        val parsedObjectives = objectives.mapIndexed { idx, obj ->
            val rawHtml = obj.contentZh ?: obj.content ?: ""
            OfficialTrainObjective(
                indexNo = obj.indexNo?.asCleanText() ?: "${idx + 1}",
                title = obj.type?.nameZh?.trim().orEmpty().ifBlank { "培养目标 ${idx + 1}" },
                content = stripHtml(rawHtml),
            )
        }.filter { it.title.isNotBlank() || it.content.isNotBlank() }

        val parsedGradReqs = buildGraduationReqTree(gradReqs)
        val parsedModules = coursesTree?.children.orEmpty().mapIndexed { idx, m ->
            parseGuideModule(pathPrefix = idx.toString(), dto = m)
        }

        return OfficialTrainingProgram(
            programId = info.id,
            cultivateTypeCode = info.cultivateType?.code?.trim().orEmpty(),
            cultivateTypeName = info.cultivateType?.nameZh?.trim().orEmpty().ifBlank { "主修" },
            grade = info.grade?.trim()?.takeIf { it.isNotEmpty() },
            department = info.department?.nameZh?.trim()?.takeIf { it.isNotEmpty() },
            major = info.major?.nameZh?.trim()?.takeIf { it.isNotEmpty() },
            majorDirection = info.majorDirection?.nameZh?.trim()?.takeIf { it.isNotEmpty() },
            education = info.education?.nameZh?.trim()?.takeIf { it.isNotEmpty() },
            requireCredits = info.requireCredits?.asDoubleOrNull(),
            objectives = parsedObjectives,
            graduationRequirements = parsedGradReqs,
            courseModules = parsedModules,
            fetchedAt = fetchedAt,
        )
    }

    private fun buildGraduationReqTree(
        items: List<EasClient.GraduationRequirementItemDto>,
    ): List<OfficialGraduationRequirement> {
        if (items.isEmpty()) return emptyList()
        val roots = items.filter { it.parentId?.asCleanText().isNullOrBlank() }
        if (roots.isEmpty()) {
            return items.mapIndexed { idx, item ->
                OfficialGraduationRequirement(
                    indexNo = item.indexNo?.asCleanText() ?: "${idx + 1}",
                    title = item.nameZh?.trim().orEmpty(),
                    content = stripHtml(item.contentZh ?: item.content ?: ""),
                )
            }
        }
        val childrenByParent = items
            .filter { !it.parentId?.asCleanText().isNullOrBlank() }
            .groupBy { it.parentId?.asCleanText().orEmpty() }

        return roots.mapIndexed { idx, root ->
            val rootId = root.id?.asCleanText()
            val subNodes = if (rootId != null) {
                childrenByParent[rootId].orEmpty().mapIndexed { cIdx, sub ->
                    OfficialGraduationRequirement(
                        indexNo = sub.indexNo?.asCleanText() ?: "${idx + 1}.${cIdx + 1}",
                        title = sub.nameZh?.trim().orEmpty(),
                        content = stripHtml(sub.contentZh ?: sub.content ?: ""),
                    )
                }
            } else {
                emptyList()
            }
            OfficialGraduationRequirement(
                indexNo = root.indexNo?.asCleanText() ?: "${idx + 1}",
                title = root.nameZh?.trim().orEmpty().ifBlank { "毕业要求 ${idx + 1}" },
                content = stripHtml(root.contentZh ?: root.content ?: ""),
                subItems = subNodes,
            )
        }
    }

    private fun parseGuideModule(
        pathPrefix: String,
        dto: EasClient.PlanGuideModuleDto,
    ): OfficialProgramModule {
        val name = dto.type?.nameZh?.trim()?.takeIf { it.isNotEmpty() }
            ?: dto.type?.nameEn?.trim()?.takeIf { it.isNotEmpty() }
            ?: "课程模块"
        val id = dto.type?.code?.takeIf { it.isNotBlank() }?.let { "$it#$pathPrefix" } ?: "$name#$pathPrefix"
        val courses = dto.planCourses.map { c ->
            OfficialProgramCourse(
                code = c.course?.code?.trim().orEmpty(),
                name = c.course?.nameZh?.trim().orEmpty()
                    .ifBlank { c.course?.nameEn?.trim().orEmpty() }
                    .ifBlank { "未命名课程" },
                credits = c.course?.credits?.asDoubleOrNull() ?: 0.0,
                compulsory = c.compulsory,
                termsText = formatTerms(c.terms),
                remark = c.remark?.trim()?.takeIf { it.isNotEmpty() },
            )
        }
        val children = dto.children.mapIndexed { idx, child ->
            parseGuideModule(pathPrefix = "$pathPrefix-$idx", dto = child)
        }
        return OfficialProgramModule(
            id = id,
            name = name,
            requiredCredits = dto.requireInfo?.requiredCredits?.asDoubleOrNull()
                ?: dto.requireInfo?.credits?.asDoubleOrNull(),
            requiredSubModuleNum = dto.requireInfo?.requiredSubModuleNum?.takeIf { it > 0 },
            courses = courses,
            children = children,
        )
    }

    internal fun mapCourseStatus(rawResult: String): PlanCourseStatus {
        val r = rawResult.trim()
        return when {
            r.isEmpty() || r.contains("未修") || r.contains("未选") -> PlanCourseStatus.UNTAKEN
            r.contains("在修") || r.contains("在读") || r.contains("修读") -> PlanCourseStatus.TAKING
            r.contains("不通") || r.contains("未通") || r.contains("挂") -> PlanCourseStatus.FAILED
            r.contains("通过") || r == "免修" || r == "合格" -> PlanCourseStatus.PASSED
            else -> PlanCourseStatus.OTHER
        }
    }

    internal fun formatTerms(terms: List<String>): String {
        if (terms.isEmpty()) return ""
        return terms
            .flatMap { it.split(',') }
            .mapNotNull { raw ->
                val t = raw.trim()
                when {
                    t.isEmpty() || t == "TERM_TERM_NULL" || t == "null" -> null
                    t.startsWith("TERM_") -> {
                        val num = t.removePrefix("TERM_")
                        if (num.all { it.isDigit() }) "第${num}学期" else num
                    }
                    t == "SPRING" -> "春"
                    t == "SUMMER" -> "夏"
                    t == "AUTUMN" -> "秋"
                    t == "WINTER" -> "冬"
                    else -> t
                }
            }
            .distinct()
            .joinToString("、")
    }

    private fun extractSubstituteName(el: JsonElement): String? = when (el) {
        is JsonPrimitive -> el.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        is JsonObject -> {
            val direct = (el["nameZh"] as? JsonPrimitive)?.contentOrNull?.trim()
                ?: (el["courseName"] as? JsonPrimitive)?.contentOrNull?.trim()
            if (!direct.isNullOrEmpty()) {
                direct
            } else {
                val nested = el["course"] as? JsonObject
                (nested?.get("nameZh") as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
            }
        }
        is JsonArray -> el.firstNotNullOfOrNull { extractSubstituteName(it) }
        else -> null
    }

    private fun stripHtml(raw: String): String {
        if (raw.isBlank()) return ""
        return raw
            .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("""</(?:p|div|li|tr)>""", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("""<[^>]+>"""), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .lines()
            .map { it.trim().replace(Regex("""\s+"""), " ") }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    private fun JsonPrimitive?.asCleanText(): String? =
        this?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }

    private fun JsonPrimitive?.asDoubleOrNull(): Double? {
        if (this == null) return null
        return doubleOrNull ?: contentOrNull?.trim()?.toDoubleOrNull()
    }
}
