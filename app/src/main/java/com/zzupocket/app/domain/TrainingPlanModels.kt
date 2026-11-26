package com.zzupocket.app.domain

import kotlinx.serialization.Serializable

/** 个人培养计划学分目标与官方网页地址配置（持久化于本机）。 */
@Serializable
data class TrainingPlanConfig(
    val totalTargetCredits: Double = 160.0,
    val categoryTargets: Map<String, Double> = emptyMap(),
    val officialPortalUrl: String? = null,
)

/** 培养计划中的单个课程性质/模块进度（本地按成绩推算用）。 */
data class TrainingPlanModule(
    val category: String,
    val earnedCredits: Double,
    val targetCredits: Double?,
    val courses: List<GradeRecord>,
)

/** 培养计划本地推算聚合快照。 */
data class TrainingPlanSnapshot(
    val totalEarned: Double,
    val totalTarget: Double,
    val modules: List<TrainingPlanModule>,
)

// ── 官方「培养方案完成情况」领域模型 ──────────────────────────────────────

/** 课程修读完成状态 */
@Serializable
enum class PlanCourseStatus(val label: String) {
    PASSED("已通过"),
    TAKING("在修"),
    UNTAKEN("未修"),
    FAILED("未通过"),
    OTHER("其他"),
}

/** 官方「培养方案完成情况」中的单门课程条目 */
@Serializable
data class OfficialPlanCourse(
    val code: String,
    val name: String,
    val credits: Double,
    val compulsory: Boolean? = null,
    val status: PlanCourseStatus,
    val resultText: String,
    val scoreText: String? = null,
    val gp: Double? = null,
    val termsText: String = "",
    val marks: List<String> = emptyList(),
    val remark: String? = null,
    val substitutes: List<String> = emptyList(),
)

/** 官方「培养方案完成情况」递归模块节点 */
@Serializable
data class OfficialCompletionModule(
    val id: String,
    val name: String,
    val passed: Boolean,
    val resultText: String,
    val requiredCredits: Double? = null,
    val passedCredits: Double = 0.0,
    val skipCredits: Double = 0.0,
    val takingCredits: Double = 0.0,
    val requiredSubModuleNum: Int? = null,
    val passedSubModuleNum: Int = 0,
    val courses: List<OfficialPlanCourse> = emptyList(),
    val children: List<OfficialCompletionModule> = emptyList(),
) {
    /** 统计当前模块及其子模块中指定状态（null 表示全部）的课程门数 */
    fun countCourses(status: PlanCourseStatus? = null): Int {
        val selfCount = if (status == null) courses.size else courses.count { it.status == status }
        return selfCount + children.sumOf { it.countCourses(status) }
    }

    /**
     * 按修读状态递归筛选模块树，保留含匹配课程或含匹配子模块的分支。
     * `filter == null` 时原样返回。
     */
    fun filterByStatus(filter: PlanCourseStatus?): OfficialCompletionModule? {
        if (filter == null) return this
        val filteredCourses = courses.filter { it.status == filter }
        val filteredChildren = children.mapNotNull { it.filterByStatus(filter) }
        if (filteredCourses.isEmpty() && filteredChildren.isEmpty()) return null
        return copy(
            courses = filteredCourses,
            children = filteredChildren,
        )
    }
}

/** 官方「培养方案完成情况」总览 */
@Serializable
data class OfficialPlanCompletion(
    val cultivateTypeCode: String,
    val cultivateTypeName: String,
    val totalRequiredCredits: Double,
    val totalPassedCredits: Double,
    val totalTakingCredits: Double,
    val modules: List<OfficialCompletionModule>,
    val outplanCourses: List<OfficialPlanCourse> = emptyList(),
    val fetchedAt: Long = 0L,
) {
    /** 统计整个培养方案内指定状态（null 表示全部）的课程门数 */
    fun countCourses(status: PlanCourseStatus? = null): Int =
        modules.sumOf { it.countCourses(status) }
}

// ── 官方「指导性培养方案」领域模型 ────────────────────────────────────────

/** 培养目标条文 */
@Serializable
data class OfficialTrainObjective(
    val indexNo: String,
    val title: String,
    val content: String,
)

/** 毕业要求条文（支持一级指标与二级子指标） */
@Serializable
data class OfficialGraduationRequirement(
    val indexNo: String,
    val title: String,
    val content: String,
    val subItems: List<OfficialGraduationRequirement> = emptyList(),
)

/** 指导性培养方案中的课程条目 */
@Serializable
data class OfficialProgramCourse(
    val code: String,
    val name: String,
    val credits: Double,
    val compulsory: Boolean? = null,
    val termsText: String = "",
    val remark: String? = null,
)

/** 指导性培养方案课程体系模块节点 */
@Serializable
data class OfficialProgramModule(
    val id: String,
    val name: String,
    val requiredCredits: Double? = null,
    val requiredSubModuleNum: Int? = null,
    val courses: List<OfficialProgramCourse> = emptyList(),
    val children: List<OfficialProgramModule> = emptyList(),
) {
    /** 统计当前模块及子模块下的全部课程门数 */
    fun totalCourseCount(): Int = courses.size + children.sumOf { it.totalCourseCount() }
}

/** 官方「指导性培养方案」总览 */
@Serializable
data class OfficialTrainingProgram(
    val programId: Long,
    val cultivateTypeCode: String,
    val cultivateTypeName: String,
    val grade: String? = null,
    val department: String? = null,
    val major: String? = null,
    val majorDirection: String? = null,
    val education: String? = null,
    val requireCredits: Double? = null,
    val objectives: List<OfficialTrainObjective> = emptyList(),
    val graduationRequirements: List<OfficialGraduationRequirement> = emptyList(),
    val courseModules: List<OfficialProgramModule> = emptyList(),
    val fetchedAt: Long = 0L,
)

/** 培养方案与完成情况本地离线聚合缓存包 */
@Serializable
data class OfficialPlanBundle(
    val completions: List<OfficialPlanCompletion> = emptyList(),
    val programs: List<OfficialTrainingProgram> = emptyList(),
    val fetchedAt: Long = 0L,
)

/**
 * 培养计划本地归集引擎（纯函数，零 Android 依赖）。
 * 用于教务官方数据尚未同步时的本地成绩兜底估算。
 */
object TrainingPlanAnalyzer {

    /**
     * 按课程性质/类别归集已通过主修课程，并与自定义模块目标合并排序。
     */
    fun build(grades: List<GradeRecord>, config: TrainingPlanConfig): TrainingPlanSnapshot {
        val valid = grades.filter { !it.isMinor && !it.isUnevaluated && it.passed && it.credits > 0.0 }
        val grouped = valid.groupBy { g ->
            g.courseProperty?.takeIf { it.isNotBlank() }
                ?: g.courseType?.takeIf { it.isNotBlank() }
                ?: "其他"
        }

        val allCategories = (grouped.keys + config.categoryTargets.keys).distinct()
        val modules = allCategories.map { category ->
            val courses = grouped[category].orEmpty()
                .sortedWith(compareByDescending<GradeRecord> { it.semesterCode }.thenBy { it.courseName })
            val earned = courses.sumOf { it.credits }
            TrainingPlanModule(
                category = category,
                earnedCredits = earned,
                targetCredits = config.categoryTargets[category]?.takeIf { it > 0.0 },
                courses = courses,
            )
        }.sortedWith(compareByDescending<TrainingPlanModule> { it.earnedCredits }.thenBy { it.category })

        return TrainingPlanSnapshot(
            totalEarned = valid.sumOf { it.credits },
            totalTarget = config.totalTargetCredits.coerceAtLeast(1.0),
            modules = modules,
        )
    }
}
