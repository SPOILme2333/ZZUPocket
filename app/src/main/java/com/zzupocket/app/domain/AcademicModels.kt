package com.zzupocket.app.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.serialization.Serializable

/** 分项成绩（如平时、期中、期末、实验） */
@Serializable
data class SubGradeItem(
    val name: String,
    val score: String,
)

/** 成绩单条目领域模型 */
data class GradeRecord(
    val id: String,
    val semesterCode: String,
    val semesterName: String,
    val courseCode: String,
    val courseName: String,
    val credits: Double,
    val gp: Double?,
    val finalGrade: String,
    val passed: Boolean,
    val retake: Boolean,
    val isMinor: Boolean,
    val isUnevaluated: Boolean,
    val courseType: String?,
    val courseProperty: String?,
    val displayTag: String?,
    val subGrades: List<SubGradeItem>,
    val isNew: Boolean = false,
)

/** 官方绩点排名汇总 */
@Serializable
data class GpaRankSummary(
    val gpa: Double? = null,
    val rank: Int? = null,
    val total: Int? = null,
    val fetchedAt: Long = 0L,
)

/** 单个学期的绩点聚合点 */
data class SemesterGpaPoint(
    val semesterCode: String,
    val semesterName: String,
    val gpa: Double,
    val credits: Double,
    val courseCount: Int,
)

/** 模拟算分器中的临时新增课程 */
data class SimulatedCourseInput(
    val id: String,
    val name: String,
    val credits: Double,
    val gp: Double,
)

/** 官方考场安排领域模型 */
data class ExamRecord(
    val id: String,
    val courseName: String,
    val examDate: String?,
    val weekDay: Int,
    val startTime: Int,
    val endTime: Int,
    val place: String,
    val examType: String?,
    val isMinor: Boolean,
) {
    val startTimeText: String get() = formatHhmm(startTime)
    val endTimeText: String get() = formatHhmm(endTime)

    fun daysUntil(today: LocalDate): Int? {
        val d = parsedDate() ?: return null
        return ChronoUnit.DAYS.between(today, d).toInt()
    }

    fun isFinished(today: LocalDate): Boolean {
        val days = daysUntil(today) ?: return false
        return days < 0
    }

    fun parsedDate(): LocalDate? = examDate?.take(10)?.let { raw ->
        runCatching { LocalDate.parse(raw) }.getOrNull()
    }

    companion object {
        fun formatHhmm(hhmm: Int): String {
            if (hhmm <= 0) return "--:--"
            val h = hhmm / 100
            val m = hhmm % 100
            if (h !in 0..23 || m !in 0..59) return "--:--"
            return "%02d:%02d".format(h, m)
        }
    }
}

/** 学期注册与缴费记录 */
@Serializable
data class RegisterRecord(
    val semesterName: String,
    val registered: Boolean,
    val payed: Boolean? = null,
)

/** 学籍基础档案领域模型 */
@Serializable
data class StudentProfile(
    val studentCode: String,
    val name: String,
    val gender: String? = null,
    val nation: String? = null,
    val grade: String? = null,
    val department: String? = null,
    val major: String? = null,
    val majorDirection: String? = null,
    val adminClass: String? = null,
    val campus: String? = null,
    val education: String? = null,
    val cultivateType: String? = null,
    val statusText: String? = null,
    val inSchool: Boolean? = null,
    val enterDate: String? = null,
    val expectedGraduateDate: String? = null,
    val studyForm: String? = null,
    val tutor: String? = null,
    val registers: List<RegisterRecord> = emptyList(),
)

/**
 * 教务纯函数统计与算分引擎（零 Android 依赖）。
 */
object AcademicStats {

    /**
     * 计算加权平均绩点（GPA）。
     * 过滤未评教条目、无绩点条目与 0 学分条目。
     */
    fun weightedGpa(grades: List<GradeRecord>): Double? {
        var totalWeighted = 0.0
        var totalCredits = 0.0
        for (g in grades) {
            val gp = g.gp ?: continue
            if (g.isUnevaluated || g.credits <= 0.0) continue
            totalWeighted += gp * g.credits
            totalCredits += g.credits
        }
        if (totalCredits <= 0.0) return null
        return totalWeighted / totalCredits
    }

    /** 统计已通过课程的累计获学分 */
    fun earnedCredits(grades: List<GradeRecord>): Double =
        grades.filter { it.passed && !it.isUnevaluated && it.credits > 0.0 }
            .sumOf { it.credits }

    /** 按学期升序生成 GPA 趋势点，用于趋势曲线图 */
    fun semesterTrend(grades: List<GradeRecord>): List<SemesterGpaPoint> =
        grades
            .filter { !it.isUnevaluated && it.gp != null && it.credits > 0.0 && it.semesterCode.isNotBlank() }
            .groupBy { it.semesterCode }
            .entries
            .sortedBy { it.key }
            .mapNotNull { (code, list) ->
                val gpa = weightedGpa(list) ?: return@mapNotNull null
                val name = list.firstOrNull()?.semesterName?.takeIf { it.isNotBlank() } ?: code
                SemesterGpaPoint(
                    semesterCode = code,
                    semesterName = name,
                    gpa = gpa,
                    credits = list.sumOf { it.credits },
                    courseCount = list.size,
                )
            }

    /** 按课程性质（必修/选修/通识等）统计已获学分，降序排列 */
    fun creditsByProperty(grades: List<GradeRecord>): List<Pair<String, Double>> =
        grades
            .filter { it.passed && !it.isUnevaluated && it.credits > 0.0 }
            .groupBy {
                it.courseProperty?.takeIf { s -> s.isNotBlank() }
                    ?: it.courseType?.takeIf { s -> s.isNotBlank() }
                    ?: "其他"
            }
            .mapValues { (_, list) -> list.sumOf { it.credits } }
            .entries
            .sortedByDescending { it.value }
            .map { it.key to it.value }

    /**
     * 模拟 GPA 计算器：支持排除已修课程、覆写目标绩点以及追加未来虚拟课程。
     * 返回 `(模拟后加权 GPA, 参与计算总学分)`。
     */
    fun simulateGpa(
        baseGrades: List<GradeRecord>,
        excludedIds: Set<String> = emptySet(),
        overrideGp: Map<String, Double> = emptyMap(),
        extras: List<SimulatedCourseInput> = emptyList(),
    ): Pair<Double?, Double> {
        var totalWeighted = 0.0
        var totalCredits = 0.0

        for (g in baseGrades) {
            if (g.id in excludedIds) continue
            val effectiveGp = overrideGp[g.id] ?: g.gp
            if (effectiveGp == null || g.credits <= 0.0) continue
            if (g.isUnevaluated && g.id !in overrideGp) continue
            totalWeighted += effectiveGp * g.credits
            totalCredits += g.credits
        }

        for (extra in extras) {
            if (extra.credits <= 0.0) continue
            totalWeighted += extra.gp.coerceIn(0.0, 4.0) * extra.credits
            totalCredits += extra.credits
        }

        if (totalCredits <= 0.0) return null to 0.0
        return (totalWeighted / totalCredits) to totalCredits
    }

    /**
     * 郑州大学标准 4.0 绩点换算表（供模拟算分器按百分制直观试算）。
     */
    fun scoreToZzuGp(score: Double): Double = when {
        score >= 90.0 -> 4.0
        score >= 85.0 -> 3.7
        score >= 82.0 -> 3.3
        score >= 78.0 -> 3.0
        score >= 75.0 -> 2.7
        score >= 72.0 -> 2.3
        score >= 68.0 -> 2.0
        score >= 64.0 -> 1.5
        score >= 60.0 -> 1.0
        else -> 0.0
    }

    /** 姓名隐私脱敏 */
    fun maskName(name: String): String {
        val clean = name.trim()
        if (clean.isEmpty()) return ""
        return when (clean.length) {
            1 -> "*"
            2 -> "${clean.first()}*"
            else -> clean.first() + "*".repeat(clean.length - 1)
        }
    }

    /** 学号隐私脱敏（保留前 4 位与后 2 位） */
    fun maskStudentCode(code: String): String {
        val clean = code.trim()
        if (clean.length <= 6) return "*".repeat(clean.length)
        return clean.take(4) + "*".repeat(clean.length - 6) + clean.takeLast(2)
    }

    /**
     * 检测本轮刷新后新出分的成绩 ID 集合。
     * 仅当本地已有历史成绩记录（[oldIds] 非空）时才标新，首次全量同步不全部高亮。
     */
    fun detectNewGradeIds(oldIds: Set<String>, fresh: List<GradeRecord>): Set<String> {
        if (oldIds.isEmpty()) return emptySet()
        return fresh.asSequence()
            .filter { !it.isUnevaluated && it.id !in oldIds }
            .map { it.id }
            .toSet()
    }
}
