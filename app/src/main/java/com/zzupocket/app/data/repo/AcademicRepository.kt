package com.zzupocket.app.data.repo

import com.zzupocket.app.data.auth.AuthRepository
import com.zzupocket.app.data.db.AppDatabase
import com.zzupocket.app.data.db.ExamEntity
import com.zzupocket.app.data.db.GradeEntity
import com.zzupocket.app.data.db.KvCacheEntity
import com.zzupocket.app.data.eas.AcademicParser
import com.zzupocket.app.data.eas.EasClient
import com.zzupocket.app.data.eas.TrainingPlanParser
import com.zzupocket.app.domain.AcademicStats
import com.zzupocket.app.domain.CountdownCategory
import com.zzupocket.app.domain.CountdownItem
import com.zzupocket.app.domain.ExamRecord
import com.zzupocket.app.domain.GpaRankSummary
import com.zzupocket.app.domain.GradeRecord
import com.zzupocket.app.domain.OfficialPlanBundle
import com.zzupocket.app.domain.OfficialPlanCompletion
import com.zzupocket.app.domain.OfficialTrainingProgram
import com.zzupocket.app.domain.StudentProfile
import com.zzupocket.app.domain.SubGradeItem
import com.zzupocket.app.domain.TrainingPlanConfig
import java.time.LocalDate
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * 教务信息（成绩与 GPA、官方考场安排、学籍档案）本地离线优先仓库。
 *
 * 所有请求均复用现有 `EasClient` 裸 JWT 与 Nox 网关自动恢复链，不重走账密登录。
 */
class AcademicRepository(
    private val db: AppDatabase,
    private val eas: EasClient,
    private val auth: AuthRepository,
    private val json: Json,
    private val countdownRepo: CountdownRepository,
) {

    data class RefreshGradesResult(
        val grades: List<GradeRecord>,
        val gpaRank: GpaRankSummary?,
        val newlyPublishedIds: Set<String>,
    )

    private val gradeDao = db.gradeDao()
    private val examDao = db.examDao()
    private val kvDao = db.kvCacheDao()

    // ── 成绩与 GPA ──────────────────────────────────────────────────────────

    fun observeGrades(): Flow<List<GradeRecord>> =
        gradeDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun gradesFetchedAt(): Long? = gradeDao.latestUpdatedAt()

    suspend fun cachedGpaRank(): GpaRankSummary? {
        val raw = kvDao.get(KEY_GPA_RANK)?.payload ?: return null
        return runCatching { json.decodeFromString(GpaRankSummary.serializer(), raw) }.getOrNull()
    }

    suspend fun refreshGrades(): RefreshGradesResult {
        val token = auth.requireCasToken()
        val now = System.currentTimeMillis()
        val semesterMap = runCatching {
            db.semesterDao().all().associate { it.code to it.displayLabel() }
        }.getOrDefault(emptyMap())

        val (rawGrades, rankSummary) = coroutineScope {
            val gradesJob = async { eas.fetchGrades(token) }
            val rankJob = async { runCatching { eas.fetchGpaRank(token) }.getOrNull() }
            val list = gradesJob.await()
            val rank = rankJob.await()?.let { AcademicParser.parseGpaRank(it, now) }
            list to rank
        }

        val fresh = AcademicParser.parseGrades(rawGrades, semesterMap)
        val oldIds = gradeDao.all().map { it.id }.toHashSet()
        val newIds = AcademicStats.detectNewGradeIds(oldIds, fresh)

        gradeDao.replaceAll(
            fresh.map { g ->
                GradeEntity(
                    id = g.id,
                    semesterCode = g.semesterCode,
                    semesterName = g.semesterName,
                    courseCode = g.courseCode,
                    courseName = g.courseName,
                    credits = g.credits,
                    gp = g.gp,
                    finalGrade = g.finalGrade,
                    passed = g.passed,
                    retake = g.retake,
                    isMinor = g.isMinor,
                    isUnevaluated = g.isUnevaluated,
                    courseType = g.courseType,
                    courseProperty = g.courseProperty,
                    displayTag = g.displayTag,
                    subGradesJson = json.encodeToString(ListSerializer(SubGradeItem.serializer()), g.subGrades),
                    updatedAt = now,
                )
            },
        )

        if (rankSummary != null) {
            kvDao.put(
                KvCacheEntity(
                    key = KEY_GPA_RANK,
                    payload = json.encodeToString(GpaRankSummary.serializer(), rankSummary),
                    fetchedAt = now,
                    ttlSeconds = CACHE_TTL_SEC,
                ),
            )
        }

        val tagged = fresh.map { if (it.id in newIds) it.copy(isNew = true) else it }
        return RefreshGradesResult(
            grades = tagged,
            gpaRank = rankSummary ?: cachedGpaRank(),
            newlyPublishedIds = newIds,
        )
    }

    // ── 官方考场安排 × 倒数日联动 ─────────────────────────────────────────────

    fun observeExams(): Flow<List<ExamRecord>> =
        examDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun examsFetchedAt(): Long? = examDao.latestUpdatedAt()

    suspend fun refreshExams(): List<ExamRecord> {
        val token = auth.requireCasToken()
        val now = System.currentTimeMillis()
        val raw = eas.fetchExams(token)
        val parsed = AcademicParser.parseExams(raw)
        examDao.replaceAll(
            parsed.map { e ->
                ExamEntity(
                    id = e.id,
                    courseName = e.courseName,
                    examDate = e.examDate,
                    weekDay = e.weekDay,
                    startTime = e.startTime,
                    endTime = e.endTime,
                    place = e.place,
                    examType = e.examType,
                    isMinor = e.isMinor,
                    updatedAt = now,
                )
            },
        )
        return parsed
    }

    /**
     * 将尚未结束的官方考试批量同步至本地「倒数日」（按名称 + 日期自动去重，返回实际新增条数）。
     */
    suspend fun syncExamsToCountdowns(
        exams: List<ExamRecord>,
        today: LocalDate = LocalDate.now(),
    ): Int {
        val existing = countdownRepo.all(today)
        val existingKeys = existing.map { "${it.title.trim()}|${it.targetDate}" }.toHashSet()
        var added = 0
        for (exam in exams) {
            val date = exam.parsedDate() ?: continue
            if (exam.isFinished(today)) continue
            val title = exam.courseName.trim()
            if (title.isBlank()) continue
            val key = "$title|$date"
            if (key in existingKeys) continue

            val locParts = listOfNotNull(
                exam.place.takeIf { it.isNotBlank() },
                "${exam.startTimeText}-${exam.endTimeText}".takeIf { exam.startTime > 0 },
            ).joinToString(" · ")

            countdownRepo.save(
                CountdownItem(
                    title = title,
                    targetDate = date,
                    category = CountdownCategory.EXAM,
                    location = locParts.takeIf { it.isNotBlank() },
                ),
            )
            existingKeys += key
            added++
        }
        return added
    }

    // ── 学籍档案 ─────────────────────────────────────────────────────────────

    suspend fun cachedProfiles(): List<StudentProfile> {
        val raw = kvDao.get(KEY_STUDENT_PROFILES)?.payload ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(StudentProfile.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    suspend fun profileFetchedAt(): Long? = kvDao.get(KEY_STUDENT_PROFILES)?.fetchedAt

    suspend fun refreshProfiles(): List<StudentProfile> {
        val token = auth.requireCasToken()
        val now = System.currentTimeMillis()
        val (base, regs, grads) = coroutineScope {
            val baseJob = async { eas.fetchStudentBaseInfos(token) }
            val regJob = async { runCatching { eas.fetchStudentRegisters(token) }.getOrDefault(emptyList()) }
            val gradJob = async { runCatching { eas.fetchStudentGraduates(token) }.getOrDefault(emptyList()) }
            Triple(baseJob.await(), regJob.await(), gradJob.await())
        }
        val profiles = AcademicParser.parseStudentProfiles(base, regs, grads)
        kvDao.put(
            KvCacheEntity(
                key = KEY_STUDENT_PROFILES,
                payload = json.encodeToString(ListSerializer(StudentProfile.serializer()), profiles),
                fetchedAt = now,
                ttlSeconds = CACHE_TTL_SEC,
            ),
        )
        return profiles
    }

    // ── 培养计划目标配置与官方入口探测 ───────────────────────────────────────

    suspend fun loadTrainingPlanConfig(): TrainingPlanConfig {
        val raw = kvDao.get(KEY_TRAINING_PLAN_CONFIG)?.payload ?: return TrainingPlanConfig()
        return runCatching {
            json.decodeFromString(TrainingPlanConfig.serializer(), raw)
        }.getOrDefault(TrainingPlanConfig())
    }

    suspend fun saveTrainingPlanConfig(config: TrainingPlanConfig) {
        kvDao.put(
            KvCacheEntity(
                key = KEY_TRAINING_PLAN_CONFIG,
                payload = json.encodeToString(TrainingPlanConfig.serializer(), config),
                fetchedAt = System.currentTimeMillis(),
                ttlSeconds = PLAN_CONFIG_TTL_SEC,
            ),
        )
    }

    /** 尝试从教务服务目录发现「培养方案/计划/学业」页面入口（复用 EasClient 网关恢复链，失败静默返回缓存或 null）。 */
    suspend fun discoverOfficialPlanUrl(): String? {
        val cached = loadTrainingPlanConfig().officialPortalUrl
        val discovered = runCatching {
            val token = auth.requireCasToken()
            val body = eas.fetchDoorServiceListRaw(token) ?: return@runCatching null
            val root = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return@runCatching null
            extractPlanServiceUrl(root)
        }.getOrNull()
        if (!discovered.isNullOrBlank()) {
            val cur = loadTrainingPlanConfig()
            if (cur.officialPortalUrl != discovered) {
                saveTrainingPlanConfig(cur.copy(officialPortalUrl = discovered))
            }
            return discovered
        }
        return cached
    }

    // ── 官方「培养方案」与「培养方案完成情况」 ──────────────────────────────

    suspend fun cachedOfficialPlanBundle(): OfficialPlanBundle? {
        val raw = kvDao.get(KEY_OFFICIAL_PLAN_BUNDLE)?.payload ?: return null
        return runCatching {
            json.decodeFromString(OfficialPlanBundle.serializer(), raw)
        }.getOrNull()
    }

    /**
     * 并行拉取教务官方「培养方案完成情况」与「指导性培养方案」全量数据并缓存到本地。
     * 两路互相容错：任一路有结果即落库返回；若两路同时失败则抛出首条异常供界面提示。
     */
    suspend fun refreshOfficialPlanBundle(): OfficialPlanBundle {
        val token = auth.requireCasToken()
        val now = System.currentTimeMillis()

        val (completionRes, programRes) = coroutineScope {
            val compJob = async {
                runCatching { fetchAllCompletions(token, now) }
            }
            val progJob = async {
                runCatching { fetchAllPrograms(token, now) }
            }
            compJob.await() to progJob.await()
        }

        val completions = completionRes.getOrNull().orEmpty()
        val programs = programRes.getOrNull().orEmpty()
        if (completions.isEmpty() && programs.isEmpty()) {
            completionRes.exceptionOrNull()?.let { throw it }
            programRes.exceptionOrNull()?.let { throw it }
        }

        val bundle = OfficialPlanBundle(
            completions = completions,
            programs = programs,
            fetchedAt = now,
        )
        if (completions.isNotEmpty() || programs.isNotEmpty()) {
            kvDao.put(
                KvCacheEntity(
                    key = KEY_OFFICIAL_PLAN_BUNDLE,
                    payload = json.encodeToString(OfficialPlanBundle.serializer(), bundle),
                    fetchedAt = now,
                    ttlSeconds = CACHE_TTL_SEC,
                ),
            )
        }
        return bundle
    }

    private suspend fun fetchAllCompletions(token: String, now: Long): List<OfficialPlanCompletion> = coroutineScope {
        val types = eas.fetchPlanCompletionCultivateTypes(token)
        val targets = types.mapNotNull { t ->
            val code = t.code?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            code to (t.nameZh?.trim().orEmpty().ifBlank { code })
        }
        if (targets.isEmpty()) return@coroutineScope emptyList()
        targets.map { (code, name) ->
            async {
                val info = eas.fetchPlanCompletionInfo(token, code)
                TrainingPlanParser.parseCompletion(
                    cultivateTypeCode = code,
                    cultivateTypeName = name,
                    dto = info,
                    fetchedAt = now,
                )
            }
        }.awaitAll()
    }

    private suspend fun fetchAllPrograms(token: String, now: Long): List<OfficialTrainingProgram> = coroutineScope {
        val rawPrograms = eas.fetchMyPrograms(token)
        if (rawPrograms.isEmpty()) return@coroutineScope emptyList()
        rawPrograms.mapIndexed { idx, prog ->
            async {
                val coursesJob = async {
                    runCatching { eas.fetchProgramPlanCourses(token, prog.id, idx) }.getOrNull()
                }
                val objJob = async {
                    runCatching { eas.fetchProgramTrainObjectives(token, prog.id) }.getOrDefault(emptyList())
                }
                val gradJob = async {
                    runCatching { eas.fetchProgramGraduationRequirements(token, prog.id) }.getOrDefault(emptyList())
                }
                TrainingPlanParser.parseProgram(
                    info = prog,
                    coursesTree = coursesJob.await(),
                    objectives = objJob.await(),
                    gradReqs = gradJob.await(),
                    fetchedAt = now,
                )
            }
        }.awaitAll()
    }

    internal fun extractPlanServiceUrl(el: JsonElement): String? =
        Companion.extractPlanServiceUrl(el)

    // ── Mappers ──────────────────────────────────────────────────────────────

    private fun GradeEntity.toDomain(): GradeRecord {
        val subs = runCatching {
            json.decodeFromString(ListSerializer(SubGradeItem.serializer()), subGradesJson)
        }.getOrDefault(emptyList())
        return GradeRecord(
            id = id,
            semesterCode = semesterCode,
            semesterName = semesterName,
            courseCode = courseCode,
            courseName = courseName,
            credits = credits,
            gp = gp,
            finalGrade = finalGrade,
            passed = passed,
            retake = retake,
            isMinor = isMinor,
            isUnevaluated = isUnevaluated,
            courseType = courseType,
            courseProperty = courseProperty,
            displayTag = displayTag,
            subGrades = subs,
        )
    }

    private fun ExamEntity.toDomain() = ExamRecord(
        id = id,
        courseName = courseName,
        examDate = examDate,
        weekDay = weekDay,
        startTime = startTime,
        endTime = endTime,
        place = place,
        examType = examType,
        isMinor = isMinor,
    )

    private fun com.zzupocket.app.data.db.SemesterEntity.displayLabel(): String {
        val m = Regex("^(\\d{4}-\\d{4})-(\\d+)$").find(nameZh.trim())
        if (m != null) {
            val term = when (m.groupValues[2]) {
                "1" -> "第一学期"
                "2" -> "第二学期"
                "3" -> "第三学期"
                else -> "第${m.groupValues[2]}学期"
            }
            return "${m.groupValues[1]} $term"
        }
        return nameZh.ifBlank { schoolYear }
    }

    companion object {
        private const val KEY_GPA_RANK = "academic:gpa_rank_v1"
        private const val KEY_STUDENT_PROFILES = "academic:student_profiles_v1"
        private const val KEY_TRAINING_PLAN_CONFIG = "academic:training_plan_config_v1"
        private const val KEY_OFFICIAL_PLAN_BUNDLE = "academic:official_plan_bundle_v1"
        private const val CACHE_TTL_SEC = 7L * 24 * 3600
        private const val PLAN_CONFIG_TTL_SEC = 365L * 24 * 3600

        /** 从教务服务目录 JSON 中提取培养方案/学业审核相关链接（支持相对路径自动补全域名） */
        internal fun extractPlanServiceUrl(el: JsonElement): String? {
            val keywords = listOf("培养方案", "培养计划", "学业", "毕业", "program", "plan")
            when (el) {
                is JsonArray -> el.forEach { child ->
                    extractPlanServiceUrl(child)?.let { return it }
                }
                is JsonObject -> {
                    val name = listOf("nameZh", "name", "serviceName", "title")
                        .firstNotNullOfOrNull { k -> (el[k] as? JsonPrimitive)?.contentOrNull?.trim() }
                        .orEmpty()
                    val rawUrl = listOf("serviceUrl", "url", "appUrl")
                        .firstNotNullOfOrNull { k -> (el[k] as? JsonPrimitive)?.contentOrNull?.trim() }
                        .orEmpty()
                    if (rawUrl.isNotEmpty() && keywords.any { kw ->
                            name.contains(kw, ignoreCase = true) || rawUrl.contains(kw, ignoreCase = true)
                        }
                    ) {
                        return if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
                            rawUrl
                        } else if (rawUrl.startsWith("/")) {
                            "https://jwxt.zzu.edu.cn$rawUrl"
                        } else {
                            null
                        }
                    }
                    el.values.forEach { child ->
                        extractPlanServiceUrl(child)?.let { return it }
                    }
                }
                else -> Unit
            }
            return null
        }
    }
}
