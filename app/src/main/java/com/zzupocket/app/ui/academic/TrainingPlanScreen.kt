package com.zzupocket.app.ui.academic

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.net.http.SslError
import android.view.ViewGroup
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zzupocket.app.core.AppGraph
import com.zzupocket.app.core.toUserMessage
import com.zzupocket.app.domain.OfficialCompletionModule
import com.zzupocket.app.domain.OfficialGraduationRequirement
import com.zzupocket.app.domain.OfficialPlanBundle
import com.zzupocket.app.domain.OfficialPlanCompletion
import com.zzupocket.app.domain.OfficialPlanCourse
import com.zzupocket.app.domain.OfficialProgramCourse
import com.zzupocket.app.domain.OfficialProgramModule
import com.zzupocket.app.domain.OfficialTrainObjective
import com.zzupocket.app.domain.OfficialTrainingProgram
import com.zzupocket.app.domain.PlanCourseStatus
import com.zzupocket.app.domain.StudentProfile
import com.zzupocket.app.domain.TrainingPlanAnalyzer
import com.zzupocket.app.domain.TrainingPlanConfig
import com.zzupocket.app.domain.TrainingPlanModule
import com.zzupocket.app.domain.TrainingPlanSnapshot
import com.zzupocket.app.ui.components.GlassCard
import com.zzupocket.app.ui.components.GlassInsetDivider
import com.zzupocket.app.ui.components.GlassListCard
import com.zzupocket.app.ui.components.GlassListRow
import com.zzupocket.app.ui.components.GlassSectionHeader
import com.zzupocket.app.ui.components.LiquidGlassBackButton
import com.zzupocket.app.ui.components.LiquidGlassButton
import com.zzupocket.app.ui.components.LiquidGlassChip
import com.zzupocket.app.ui.components.LiquidGlassIconButton
import com.zzupocket.app.ui.components.LiquidGlassOutlinedButton
import com.zzupocket.app.ui.components.RelativeTime
import com.zzupocket.app.ui.components.StatusPill
import com.zzupocket.app.ui.components.glassClickable
import com.zzupocket.app.ui.components.liquidGlassSheetContainerColor
import com.zzupocket.app.ui.theme.tabularNumbers
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val DEFAULT_JWXT_PORTAL_URL = "https://jwxt.zzu.edu.cn/manager/secondary-portal"
private const val OFFICIAL_AUTO_SYNC_TTL_MS = 12L * 3600 * 1000

/** 培养计划顶层模式 */
enum class TrainingPlanTab(val label: String) {
    COMPLETION("完成情况"),
    PROGRAM("培养方案"),
}

data class TrainingPlanUiState(
    val loggedIn: Boolean = false,
    val profile: StudentProfile? = null,
    val config: TrainingPlanConfig = TrainingPlanConfig(),
    val snapshot: TrainingPlanSnapshot = TrainingPlanSnapshot(0.0, 160.0, emptyList()),
    val officialBundle: OfficialPlanBundle? = null,
    val loadingOfficial: Boolean = false,
    val officialError: String? = null,
    val probingOfficial: Boolean = false,
)

class TrainingPlanViewModel : ViewModel() {

    private val repo = AppGraph.academicRepository
    private val auth = AppGraph.authRepository

    private val profileFlow = MutableStateFlow<StudentProfile?>(null)
    private val configFlow = MutableStateFlow(TrainingPlanConfig())
    private val bundleFlow = MutableStateFlow<OfficialPlanBundle?>(null)
    private val loadingOfficialFlow = MutableStateFlow(false)
    private val officialErrorFlow = MutableStateFlow<String?>(null)
    private val probingFlow = MutableStateFlow(false)
    private val loggedInFlow = MutableStateFlow(auth.isLoggedIn())

    private val statusSlice = combine(
        bundleFlow,
        loadingOfficialFlow,
        officialErrorFlow,
        probingFlow,
        loggedInFlow,
    ) { bundle, loading, error, probing, logged ->
        OfficialSlice(bundle, loading, error, probing, logged)
    }

    val state: StateFlow<TrainingPlanUiState> = combine(
        repo.observeGrades(),
        profileFlow,
        configFlow,
        statusSlice,
    ) { grades, profile, config, off ->
        TrainingPlanUiState(
            loggedIn = off.loggedIn,
            profile = profile,
            config = config,
            snapshot = TrainingPlanAnalyzer.build(grades, config),
            officialBundle = off.bundle,
            loadingOfficial = off.loading,
            officialError = off.error,
            probingOfficial = off.probing,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TrainingPlanUiState(loggedIn = auth.isLoggedIn()))

    init {
        viewModelScope.launch {
            profileFlow.value = repo.cachedProfiles().firstOrNull()
            configFlow.value = repo.loadTrainingPlanConfig()
            val cached = repo.cachedOfficialPlanBundle()
            bundleFlow.value = cached
            val isStale = cached == null ||
                (cached.completions.isEmpty() && cached.programs.isEmpty()) ||
                (System.currentTimeMillis() - cached.fetchedAt > OFFICIAL_AUTO_SYNC_TTL_MS)
            if (auth.isLoggedIn() && isStale) {
                refreshOfficial()
            }
        }
    }

    /** 刷新官方「培养方案完成情况」与「指导性培养方案」 */
    fun refreshOfficial() {
        if (loadingOfficialFlow.value) return
        loggedInFlow.value = auth.isLoggedIn()
        if (!loggedInFlow.value) {
            officialErrorFlow.value = "请先登录后再同步教务官方培养方案"
            return
        }
        viewModelScope.launch {
            loadingOfficialFlow.value = true
            officialErrorFlow.value = null
            runCatching {
                repo.refreshOfficialPlanBundle()
            }.onSuccess { bundle ->
                bundleFlow.value = bundle
            }.onFailure { err ->
                officialErrorFlow.value = err.toUserMessage()
            }
            loadingOfficialFlow.value = false
        }
    }

    fun saveConfig(totalTargetCredits: Double, categoryTargets: Map<String, Double>) {
        val next = configFlow.value.copy(
            totalTargetCredits = totalTargetCredits.coerceIn(1.0, 500.0),
            categoryTargets = categoryTargets.filter { it.key.isNotBlank() && it.value > 0.0 },
        )
        configFlow.value = next
        viewModelScope.launch { repo.saveTrainingPlanConfig(next) }
    }

    fun probeOfficialUrl(onReady: (String) -> Unit) {
        if (probingFlow.value) return
        viewModelScope.launch {
            probingFlow.value = true
            val found = repo.discoverOfficialPlanUrl()
            configFlow.value = repo.loadTrainingPlanConfig()
            probingFlow.value = false
            onReady(found ?: configFlow.value.officialPortalUrl ?: DEFAULT_JWXT_PORTAL_URL)
        }
    }

    private data class OfficialSlice(
        val bundle: OfficialPlanBundle?,
        val loading: Boolean,
        val error: String?,
        val probing: Boolean,
        val loggedIn: Boolean,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingPlanScreen(
    onBack: () -> Unit,
    vm: TrainingPlanViewModel = viewModel(),
) {
    val ui by vm.state.collectAsStateWithLifecycle()
    var activeTab by remember { mutableStateOf(TrainingPlanTab.COMPLETION) }
    var selectedTypeIdx by remember { mutableIntStateOf(0) }
    var statusFilter by remember { mutableStateOf<PlanCourseStatus?>(null) }
    var selectedCourse by remember { mutableStateOf<OfficialPlanCourse?>(null) }
    var showTargetSheet by remember { mutableStateOf(false) }
    var webUrl by remember { mutableStateOf<String?>(null) }

    if (webUrl != null) {
        OfficialPlanWebViewScreen(
            url = webUrl!!,
            onBackToNative = { webUrl = null },
        )
        return
    }

    val completions = ui.officialBundle?.completions.orEmpty()
    val programs = ui.officialBundle?.programs.orEmpty()
    val currentCompletion = completions.getOrNull(selectedTypeIdx.coerceIn(0, (completions.size - 1).coerceAtLeast(0)))
    val currentProgram = programs.getOrNull(selectedTypeIdx.coerceIn(0, (programs.size - 1).coerceAtLeast(0)))

    val subtitleText = remember(ui.profile, currentProgram) {
        listOfNotNull(
            currentProgram?.major ?: ui.profile?.major?.takeIf { it.isNotBlank() },
            (currentProgram?.grade ?: ui.profile?.grade)?.takeIf { it.isNotBlank() }?.let { "${it}级" },
            currentProgram?.education ?: ui.profile?.education?.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("培养计划") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    LiquidGlassBackButton(onClick = onBack)
                },
                actions = {
                    LiquidGlassIconButton(
                        icon = Icons.Filled.Refresh,
                        contentDescription = "同步官方培养方案",
                        onClick = vm::refreshOfficial,
                    )
                    Spacer(Modifier.width(6.dp))
                    LiquidGlassIconButton(
                        icon = Icons.Filled.Edit,
                        contentDescription = "设置本地目标学分",
                        onClick = { showTargetSheet = true },
                    )
                    Spacer(Modifier.width(6.dp))
                    LiquidGlassIconButton(
                        icon = Icons.Filled.Public,
                        contentDescription = "教务官方培养方案网页",
                        onClick = { vm.probeOfficialUrl { url -> webUrl = url } },
                    )
                    Spacer(Modifier.width(12.dp))
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (ui.loadingOfficial || ui.probingOfficial) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // 顶部模式切换：「完成情况」 / 「培养方案」
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TrainingPlanTab.entries.forEach { tab ->
                    LiquidGlassChip(
                        label = tab.label,
                        selected = activeTab == tab,
                        onClick = { activeTab = tab },
                    )
                }
                val typeLabels = when (activeTab) {
                    TrainingPlanTab.COMPLETION -> completions.map { it.cultivateTypeName }
                    TrainingPlanTab.PROGRAM -> programs.map { it.cultivateTypeName }
                }
                if (typeLabels.size > 1) {
                    Spacer(Modifier.width(4.dp))
                    typeLabels.forEachIndexed { idx, label ->
                        LiquidGlassChip(
                            label = label,
                            selected = idx == selectedTypeIdx,
                            onClick = { selectedTypeIdx = idx },
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ui.officialError?.let { err ->
                    item(key = "official_error") {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = err,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = vm::refreshOfficial) {
                                    Text("重试")
                                }
                            }
                        }
                    }
                }

                when (activeTab) {
                    TrainingPlanTab.COMPLETION -> {
                        if (currentCompletion != null) {
                            item(key = "official_completion_overview") {
                                OfficialCompletionOverviewCard(
                                    subtitle = subtitleText,
                                    completion = currentCompletion,
                                )
                            }

                            item(key = "completion_status_filter") {
                                CompletionStatusFilterBar(
                                    completion = currentCompletion,
                                    selected = statusFilter,
                                    onSelect = { statusFilter = it },
                                )
                            }

                            val visibleModules = currentCompletion.modules.mapNotNull { it.filterByStatus(statusFilter) }
                            if (visibleModules.isEmpty()) {
                                item(key = "filter_empty") {
                                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            text = "当前筛选状态下暂无课程",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(18.dp),
                                        )
                                    }
                                }
                            } else {
                                item(key = "official_modules_header") {
                                    GlassSectionHeader("培养模块完成情况")
                                }
                                items(visibleModules, key = { it.id }) { module ->
                                    OfficialCompletionModuleCard(
                                        module = module,
                                        autoExpand = statusFilter == PlanCourseStatus.UNTAKEN || statusFilter == PlanCourseStatus.TAKING,
                                        onCourseClick = { selectedCourse = it },
                                    )
                                }
                            }

                            if (currentCompletion.outplanCourses.isNotEmpty() && statusFilter == null) {
                                item(key = "outplan_header") {
                                    GlassSectionHeader("计划外课程 (${currentCompletion.outplanCourses.size})")
                                }
                                item(key = "outplan_card") {
                                    OutplanCoursesCard(
                                        courses = currentCompletion.outplanCourses,
                                        onCourseClick = { selectedCourse = it },
                                    )
                                }
                            }
                        } else {
                            // 本地成绩归集兜底视图（未同步官方完成情况时展示）
                            item(key = "local_overview") {
                                TrainingPlanOverviewCard(
                                    subtitle = subtitleText,
                                    snapshot = ui.snapshot,
                                    onEditTarget = { showTargetSheet = true },
                                    onSyncOfficial = if (ui.loggedIn) vm::refreshOfficial else null,
                                )
                            }
                            if (ui.snapshot.modules.isEmpty()) {
                                item(key = "empty_hint") {
                                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                                        Column(
                                            modifier = Modifier.padding(18.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Text("暂未同步培养方案完成情况", style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                text = "点击下方按钮从教务系统拉取官方毕业进度与未修课程清单；也可先刷新成绩单按本地成绩汇总。",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            LiquidGlassButton(
                                                onClick = vm::refreshOfficial,
                                                enabled = !ui.loadingOfficial,
                                            ) {
                                                Text("同步官方完成情况")
                                            }
                                        }
                                    }
                                }
                            } else {
                                item(key = "local_modules_header") {
                                    GlassSectionHeader("本地已修课程归集")
                                }
                                items(ui.snapshot.modules, key = { it.category }) { module ->
                                    TrainingPlanModuleCard(
                                        module = module,
                                        totalEarned = ui.snapshot.totalEarned,
                                    )
                                }
                            }
                        }
                    }

                    TrainingPlanTab.PROGRAM -> {
                        if (currentProgram != null) {
                            item(key = "program_overview") {
                                OfficialProgramOverviewCard(program = currentProgram)
                            }
                            if (currentProgram.objectives.isNotEmpty()) {
                                item(key = "program_objectives") {
                                    TrainObjectivesCard(objectives = currentProgram.objectives)
                                }
                            }
                            if (currentProgram.graduationRequirements.isNotEmpty()) {
                                item(key = "program_grad_reqs") {
                                    GraduationRequirementsCard(requirements = currentProgram.graduationRequirements)
                                }
                            }
                            if (currentProgram.courseModules.isNotEmpty()) {
                                item(key = "program_courses_header") {
                                    GlassSectionHeader("指导性课程体系")
                                }
                                items(currentProgram.courseModules, key = { it.id }) { mod ->
                                    OfficialProgramModuleCard(module = mod)
                                }
                            }
                        } else {
                            item(key = "program_empty") {
                                GlassCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(
                                        modifier = Modifier.padding(18.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text("暂未拉取指导性培养方案", style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            text = "可同步教务系统的培养目标、毕业要求与各学期指导性修读课程表。",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        LiquidGlassButton(
                                            onClick = vm::refreshOfficial,
                                            enabled = !ui.loadingOfficial,
                                        ) {
                                            Text("立即同步官方培养方案")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 底部官方网页兜底入口
                item(key = "official_section") {
                    GlassSectionHeader("官方教务网页")
                }
                item(key = "official_entry") {
                    GlassListCard {
                        GlassListRow(
                            icon = Icons.Filled.Public,
                            title = "在教务系统网页查看",
                            subtitle = "需要核对原版表格或打印指导性计划时可用",
                            onClick = { vm.probeOfficialUrl { url -> webUrl = url } },
                        )
                    }
                }
            }
        }
    }

    selectedCourse?.let { course ->
        OfficialCourseDetailSheet(
            course = course,
            onDismiss = { selectedCourse = null },
        )
    }

    if (showTargetSheet) {
        TrainingPlanTargetSheet(
            snapshot = ui.snapshot,
            config = ui.config,
            onDismiss = { showTargetSheet = false },
            onSave = { totalTarget, catTargets ->
                vm.saveConfig(totalTarget, catTargets)
                showTargetSheet = false
            },
        )
    }
}

// ── 官方「培养方案完成情况」组件 ─────────────────────────────────────────

@Composable
private fun OfficialCompletionOverviewCard(
    subtitle: String,
    completion: OfficialPlanCompletion,
) {
    val target = completion.totalRequiredCredits.coerceAtLeast(1.0)
    val ratio = (completion.totalPassedCredits / target).toFloat().coerceIn(0f, 1f)
    val percent = ((completion.totalPassedCredits / target) * 100.0).coerceIn(0.0, 100.0)
    val remaining = (completion.totalRequiredCredits - completion.totalPassedCredits).coerceAtLeast(0.0)

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${completion.cultivateTypeName}完成进度", style = MaterialTheme.typography.titleMedium)
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                StatusPill(
                    text = String.format(Locale.US, "%.1f%%", percent),
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    contentColor = MaterialTheme.colorScheme.primary,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = formatPlanCredits(completion.totalPassedCredits),
                    style = MaterialTheme.typography.displaySmall.tabularNumbers(),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "/ ${formatPlanCredits(completion.totalRequiredCredits)} 学分",
                    style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 5.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (remaining > 0.0) "还差 ${formatPlanCredits(remaining)}" else "已达毕业要求",
                    style = MaterialTheme.typography.labelMedium.tabularNumbers(),
                    color = if (remaining > 0.0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 5.dp),
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(ratio)
                        .height(6.dp)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }

            val metaLine = buildList {
                if (completion.totalTakingCredits > 0.0) add("在修 ${formatPlanCredits(completion.totalTakingCredits)} 学分")
                add("未修 ${completion.countCourses(PlanCourseStatus.UNTAKEN)} 门")
                if (completion.fetchedAt > 0L) add("更新于 ${RelativeTime.ago(completion.fetchedAt)}")
            }.joinToString(" · ")
            Text(
                text = metaLine,
                style = MaterialTheme.typography.labelSmall.tabularNumbers(),
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CompletionStatusFilterBar(
    completion: OfficialPlanCompletion,
    selected: PlanCourseStatus?,
    onSelect: (PlanCourseStatus?) -> Unit,
) {
    val totalCount = remember(completion) { completion.countCourses(null) }
    val untakenCount = remember(completion) { completion.countCourses(PlanCourseStatus.UNTAKEN) }
    val takingCount = remember(completion) { completion.countCourses(PlanCourseStatus.TAKING) }
    val passedCount = remember(completion) { completion.countCourses(PlanCourseStatus.PASSED) }
    val failedCount = remember(completion) { completion.countCourses(PlanCourseStatus.FAILED) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LiquidGlassChip(
            label = "全部 ($totalCount)",
            selected = selected == null,
            onClick = { onSelect(null) },
        )
        LiquidGlassChip(
            label = "未修 ($untakenCount)",
            selected = selected == PlanCourseStatus.UNTAKEN,
            onClick = { onSelect(if (selected == PlanCourseStatus.UNTAKEN) null else PlanCourseStatus.UNTAKEN) },
        )
        if (takingCount > 0) {
            LiquidGlassChip(
                label = "在修 ($takingCount)",
                selected = selected == PlanCourseStatus.TAKING,
                onClick = { onSelect(if (selected == PlanCourseStatus.TAKING) null else PlanCourseStatus.TAKING) },
            )
        }
        if (passedCount > 0) {
            LiquidGlassChip(
                label = "已通过 ($passedCount)",
                selected = selected == PlanCourseStatus.PASSED,
                onClick = { onSelect(if (selected == PlanCourseStatus.PASSED) null else PlanCourseStatus.PASSED) },
            )
        }
        if (failedCount > 0) {
            LiquidGlassChip(
                label = "未通过 ($failedCount)",
                selected = selected == PlanCourseStatus.FAILED,
                onClick = { onSelect(if (selected == PlanCourseStatus.FAILED) null else PlanCourseStatus.FAILED) },
            )
        }
    }
}

@Composable
private fun OfficialCompletionModuleCard(
    module: OfficialCompletionModule,
    autoExpand: Boolean,
    onCourseClick: (OfficialPlanCourse) -> Unit,
) {
    var expanded by remember(module.id, autoExpand) { mutableStateOf(autoExpand) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "officialCompletionArrow",
    )
    val req = module.requiredCredits
    val ratio = if (req != null && req > 0.0) {
        (module.passedCredits / req).toFloat().coerceIn(0f, 1f)
    } else if (module.passed) {
        1f
    } else {
        0f
    }
    val creditSummary = if (req != null && req > 0.0) {
        "${formatPlanCredits(module.passedCredits)} / ${formatPlanCredits(req)}"
    } else {
        "${formatPlanCredits(module.passedCredits)} 学分"
    }
    val subLine = buildList {
        if (module.takingCredits > 0.0) add("在修 ${formatPlanCredits(module.takingCredits)}")
        if (module.skipCredits > 0.0) add("尚缺 ${formatPlanCredits(module.skipCredits)}")
        module.requiredSubModuleNum?.let { reqSub ->
            add("子模块 ${module.passedSubModuleNum}/$reqSub")
        }
    }.joinToString(" · ")

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = module.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (subLine.isNotEmpty()) "${module.resultText} · $subLine" else module.resultText,
                        style = MaterialTheme.typography.labelSmall.tabularNumbers(),
                        color = if (module.passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = creditSummary,
                    style = MaterialTheme.typography.titleSmall.tabularNumbers(),
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer { rotationZ = arrowRotation },
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(ratio)
                        .height(4.dp)
                        .background(if (module.passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary),
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = tween(220, easing = FastOutSlowInEasing),
                    expandFrom = Alignment.Top,
                ) + fadeIn(animationSpec = tween(160, delayMillis = 40)),
                exit = shrinkVertically(
                    animationSpec = tween(180, easing = FastOutSlowInEasing),
                    shrinkTowards = Alignment.Top,
                ) + fadeOut(animationSpec = tween(90)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    OfficialCompletionModuleBody(
                        module = module,
                        depth = 0,
                        onCourseClick = onCourseClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun OfficialCompletionModuleBody(
    module: OfficialCompletionModule,
    depth: Int,
    onCourseClick: (OfficialPlanCourse) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        module.courses.forEachIndexed { idx, course ->
            if (idx > 0) {
                GlassInsetDivider(startIndent = 0.dp)
            }
            OfficialCompletionCourseRow(
                course = course,
                onClick = { onCourseClick(course) },
            )
        }
        module.children.forEach { child ->
            val req = child.requiredCredits
            val childCredit = if (req != null && req > 0.0) {
                "${formatPlanCredits(child.passedCredits)}/${formatPlanCredits(req)} 分"
            } else {
                "${formatPlanCredits(child.passedCredits)} 分"
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = if (depth == 0) 6.dp else 10.dp, top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = child.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${child.resultText} · $childCredit",
                        style = MaterialTheme.typography.labelSmall.tabularNumbers(),
                        color = if (child.passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                OfficialCompletionModuleBody(
                    module = child,
                    depth = depth + 1,
                    onCourseClick = onCourseClick,
                )
            }
        }
    }
}

@Composable
private fun OfficialCompletionCourseRow(
    course: OfficialPlanCourse,
    onClick: () -> Unit,
) {
    val statusColor = when (course.status) {
        PlanCourseStatus.PASSED -> MaterialTheme.colorScheme.primary
        PlanCourseStatus.TAKING -> MaterialTheme.colorScheme.secondary
        PlanCourseStatus.FAILED -> MaterialTheme.colorScheme.tertiary
        PlanCourseStatus.UNTAKEN, PlanCourseStatus.OTHER -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val subParts = buildList {
        add("${formatPlanCredits(course.credits)} 学分")
        course.compulsory?.let { add(if (it) "必修" else "选修") }
        if (course.termsText.isNotBlank()) add(course.termsText)
        if (course.marks.isNotEmpty()) add(course.marks.joinToString(" "))
    }.joinToString(" · ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .glassClickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = course.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subParts,
                style = MaterialTheme.typography.labelSmall.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = course.scoreText ?: course.resultText,
                style = MaterialTheme.typography.titleSmall.tabularNumbers(),
                fontWeight = FontWeight.Medium,
                color = statusColor,
            )
            course.gp?.let { gp ->
                Text(
                    text = "GP ${String.format(Locale.US, "%.1f", gp)}",
                    style = MaterialTheme.typography.labelSmall.tabularNumbers(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun OutplanCoursesCard(
    courses: List<OfficialPlanCourse>,
    onCourseClick: (OfficialPlanCourse) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "outplanArrow",
    )
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("计划外已修读课程", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "共 ${courses.size} 门 · 不计入本专业培养方案模块",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer { rotationZ = arrowRotation },
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(220, easing = FastOutSlowInEasing), expandFrom = Alignment.Top) + fadeIn(tween(160, 40)),
                exit = shrinkVertically(tween(180, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top) + fadeOut(tween(90)),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    courses.forEachIndexed { idx, c ->
                        if (idx > 0) GlassInsetDivider(startIndent = 0.dp)
                        OfficialCompletionCourseRow(course = c, onClick = { onCourseClick(c) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OfficialCourseDetailSheet(
    course: OfficialPlanCourse,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = liquidGlassSheetContainerColor(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(course.name, style = MaterialTheme.typography.headlineSmall)
            if (course.code.isNotBlank()) CourseMetaRow("课程代码", course.code)
            CourseMetaRow("课程学分", "${formatPlanCredits(course.credits)} 学分")
            course.compulsory?.let { CourseMetaRow("修读性质", if (it) "必修" else "选修") }
            CourseMetaRow("完成结果", course.resultText)
            course.scoreText?.let { CourseMetaRow("修读成绩", it) }
            course.gp?.let { CourseMetaRow("课程绩点", String.format(Locale.US, "%.2f", it)) }
            if (course.termsText.isNotBlank()) CourseMetaRow("修读学期", course.termsText)
            if (course.marks.isNotEmpty()) CourseMetaRow("修读标记", course.marks.joinToString("、"))
            course.remark?.let { CourseMetaRow("课程备注", it) }
            if (course.substitutes.isNotEmpty()) {
                CourseMetaRow("替代课程", course.substitutes.joinToString("、"))
            }
        }
    }
}

@Composable
private fun CourseMetaRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(88.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
    }
}

// ── 官方「指导性培养方案」组件 ───────────────────────────────────────────

@Composable
private fun OfficialProgramOverviewCard(program: OfficialTrainingProgram) {
    val lines = listOfNotNull(
        program.department?.let { "开课学院" to it },
        program.major?.let { "专业名称" to it },
        program.majorDirection?.let { "专业方向" to it },
        program.grade?.let { "适用年级" to "${it}级" },
        program.education?.let { "培养层次" to it },
        program.requireCredits?.let { "毕业学分要求" to "${formatPlanCredits(it)} 学分" },
    )
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${program.cultivateTypeName}指导性培养方案",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (program.fetchedAt > 0L) {
                    Text(
                        text = RelativeTime.ago(program.fetchedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            lines.forEach { (k, v) ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = k,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(88.dp),
                    )
                    Text(
                        text = v,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun TrainObjectivesCard(objectives: List<OfficialTrainObjective>) {
    var expanded by remember { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "trainObjArrow",
    )
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("培养目标 (${objectives.size})", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer { rotationZ = arrowRotation },
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(220, easing = FastOutSlowInEasing), expandFrom = Alignment.Top) + fadeIn(tween(160, 40)),
                exit = shrinkVertically(tween(180, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top) + fadeOut(tween(90)),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    objectives.forEach { obj ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "${obj.indexNo}、${obj.title}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (obj.content.isNotBlank()) {
                                Text(
                                    text = obj.content,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GraduationRequirementsCard(requirements: List<OfficialGraduationRequirement>) {
    var expanded by remember { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "gradReqArrow",
    )
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("毕业要求 (${requirements.size})", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer { rotationZ = arrowRotation },
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(220, easing = FastOutSlowInEasing), expandFrom = Alignment.Top) + fadeIn(tween(160, 40)),
                exit = shrinkVertically(tween(180, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top) + fadeOut(tween(90)),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    requirements.forEach { req ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val titleLine = if (req.title.isNotBlank()) "${req.indexNo}、${req.title}" else req.indexNo
                            Text(
                                text = titleLine,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (req.content.isNotBlank()) {
                                Text(text = req.content, style = MaterialTheme.typography.bodySmall)
                            }
                            req.subItems.forEach { sub ->
                                Text(
                                    text = buildString {
                                        if (sub.indexNo.isNotBlank()) append("${sub.indexNo} ")
                                        if (sub.title.isNotBlank()) append("${sub.title}：")
                                        append(sub.content)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 10.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OfficialProgramModuleCard(module: OfficialProgramModule) {
    var expanded by remember(module.id) { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "programModuleArrow",
    )
    val totalCount = remember(module) { module.totalCourseCount() }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = module.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "共 $totalCount 门课程",
                        style = MaterialTheme.typography.labelSmall.tabularNumbers(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                module.requiredCredits?.let { req ->
                    Text(
                        text = "${formatPlanCredits(req)} 学分",
                        style = MaterialTheme.typography.titleSmall.tabularNumbers(),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer { rotationZ = arrowRotation },
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(220, easing = FastOutSlowInEasing), expandFrom = Alignment.Top) + fadeIn(tween(160, 40)),
                exit = shrinkVertically(tween(180, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top) + fadeOut(tween(90)),
            ) {
                Column(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    OfficialProgramModuleBody(module = module, depth = 0)
                }
            }
        }
    }
}

@Composable
private fun OfficialProgramModuleBody(module: OfficialProgramModule, depth: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        module.courses.forEachIndexed { idx, c ->
            if (idx > 0) GlassInsetDivider(startIndent = 0.dp)
            OfficialProgramCourseRow(c)
        }
        module.children.forEach { child ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = if (depth == 0) 6.dp else 10.dp, top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = child.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    child.requiredCredits?.let { req ->
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "要求 ${formatPlanCredits(req)} 分",
                            style = MaterialTheme.typography.labelSmall.tabularNumbers(),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                OfficialProgramModuleBody(module = child, depth = depth + 1)
            }
        }
    }
}

@Composable
private fun OfficialProgramCourseRow(course: OfficialProgramCourse) {
    val sub = buildList {
        if (course.code.isNotBlank()) add(course.code)
        course.compulsory?.let { add(if (it) "必修" else "选修") }
        if (course.termsText.isNotBlank()) add(course.termsText)
        course.remark?.let { add(it) }
    }.joinToString(" · ")
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = course.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (sub.isNotBlank()) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.labelSmall.tabularNumbers(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "${formatPlanCredits(course.credits)} 分",
            style = MaterialTheme.typography.labelMedium.tabularNumbers(),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

// ── 本地成绩推算兜底组件（兼容未同步官方数据时的展示）────────────────────

@Composable
private fun TrainingPlanOverviewCard(
    subtitle: String,
    snapshot: TrainingPlanSnapshot,
    onEditTarget: () -> Unit,
    onSyncOfficial: (() -> Unit)? = null,
) {
    val ratio = (snapshot.totalEarned / snapshot.totalTarget).toFloat().coerceIn(0f, 1f)
    val percent = ((snapshot.totalEarned / snapshot.totalTarget) * 100.0).coerceIn(0.0, 100.0)
    val remaining = (snapshot.totalTarget - snapshot.totalEarned).coerceAtLeast(0.0)

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onEditTarget,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("学分达成进度（本地估算）", style = MaterialTheme.typography.titleMedium)
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                StatusPill(
                    text = String.format(Locale.US, "%.1f%%", percent),
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    contentColor = MaterialTheme.colorScheme.primary,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = formatPlanCredits(snapshot.totalEarned),
                    style = MaterialTheme.typography.displaySmall.tabularNumbers(),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "/ ${formatPlanCredits(snapshot.totalTarget)} 学分",
                    style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 5.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (remaining > 0.0) "还差 ${formatPlanCredits(remaining)}" else "已达目标",
                    style = MaterialTheme.typography.labelMedium.tabularNumbers(),
                    color = if (remaining > 0.0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 5.dp),
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(ratio)
                        .height(6.dp)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "点击可自定义目标学分",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.weight(1f),
                )
                if (onSyncOfficial != null) {
                    TextButton(onClick = onSyncOfficial) {
                        Text("同步官方进度")
                    }
                }
            }
        }
    }
}

@Composable
private fun TrainingPlanModuleCard(
    module: TrainingPlanModule,
    totalEarned: Double,
) {
    var expanded by remember(module.category) { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "planModuleArrow",
    )

    val ratio = when {
        module.targetCredits != null && module.targetCredits > 0.0 ->
            (module.earnedCredits / module.targetCredits).toFloat().coerceIn(0f, 1f)
        totalEarned > 0.0 ->
            (module.earnedCredits / totalEarned).toFloat().coerceIn(0f, 1f)
        else -> 0f
    }

    val creditText = if (module.targetCredits != null) {
        "${formatPlanCredits(module.earnedCredits)} / ${formatPlanCredits(module.targetCredits)}"
    } else {
        "${formatPlanCredits(module.earnedCredits)} 学分"
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = module.category,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "已修 ${module.courses.size} 门",
                        style = MaterialTheme.typography.labelSmall.tabularNumbers(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = creditText,
                    style = MaterialTheme.typography.titleSmall.tabularNumbers(),
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer { rotationZ = arrowRotation },
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(ratio)
                        .height(4.dp)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = tween(220, easing = FastOutSlowInEasing),
                    expandFrom = Alignment.Top,
                ) + fadeIn(animationSpec = tween(160, delayMillis = 40)),
                exit = shrinkVertically(
                    animationSpec = tween(180, easing = FastOutSlowInEasing),
                    shrinkTowards = Alignment.Top,
                ) + fadeOut(animationSpec = tween(90)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    if (module.courses.isEmpty()) {
                        Text(
                            text = "尚未修读该类课程",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        module.courses.forEachIndexed { idx, course ->
                            if (idx > 0) {
                                GlassInsetDivider(startIndent = 0.dp)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = course.courseName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "${course.semesterName} · ${formatPlanCredits(course.credits)} 学分",
                                        style = MaterialTheme.typography.labelSmall.tabularNumbers(),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = course.finalGrade,
                                        style = MaterialTheme.typography.titleSmall.tabularNumbers(),
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    course.gp?.let { gp ->
                                        Text(
                                            text = "GP ${String.format(Locale.US, "%.1f", gp)}",
                                            style = MaterialTheme.typography.labelSmall.tabularNumbers(),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrainingPlanTargetSheet(
    snapshot: TrainingPlanSnapshot,
    config: TrainingPlanConfig,
    onDismiss: () -> Unit,
    onSave: (Double, Map<String, Double>) -> Unit,
) {
    var totalText by remember(config.totalTargetCredits) {
        mutableStateOf(formatPlanCredits(config.totalTargetCredits))
    }
    val categoryOrder = remember(snapshot, config) {
        (snapshot.modules.map { it.category } + config.categoryTargets.keys).distinct()
    }
    val categoryInputs = remember(categoryOrder, config) {
        mutableStateMapOf<String, String>().apply {
            categoryOrder.forEach { cat ->
                val target = config.categoryTargets[cat]
                this[cat] = if (target != null && target > 0.0) formatPlanCredits(target) else ""
            }
        }
    }
    var customCategoryName by remember { mutableStateOf("") }
    var customCategoryTarget by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = liquidGlassSheetContainerColor(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("设置培养计划学分目标", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "按你所在专业的培养方案填写总学分与各类要求（单类留空表示暂不限制）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = totalText,
                onValueChange = { totalText = it },
                label = { Text("毕业总目标学分") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            if (categoryInputs.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                Text("各模块目标学分", style = MaterialTheme.typography.titleSmall)
                categoryInputs.keys.toList().forEach { cat ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = cat,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = categoryInputs[cat].orEmpty(),
                            onValueChange = { categoryInputs[cat] = it },
                            placeholder = { Text("未设") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.width(104.dp),
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            Text("添加未修课程类别", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = customCategoryName,
                    onValueChange = { customCategoryName = it },
                    label = { Text("类别名称") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = customCategoryTarget,
                    onValueChange = { customCategoryTarget = it },
                    label = { Text("目标") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.width(88.dp),
                )
                IconButton(
                    onClick = {
                        val name = customCategoryName.trim()
                        val target = customCategoryTarget.trim().toDoubleOrNull()
                        if (name.isNotEmpty()) {
                            categoryInputs[name] = if (target != null && target > 0.0) formatPlanCredits(target) else ""
                            customCategoryName = ""
                            customCategoryTarget = ""
                        }
                    },
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "添加类别")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LiquidGlassOutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("取消")
                }
                LiquidGlassButton(
                    onClick = {
                        val total = totalText.trim().toDoubleOrNull()?.coerceIn(1.0, 500.0) ?: config.totalTargetCredits
                        val catMap = buildMap {
                            categoryInputs.forEach { (k, raw) ->
                                val v = raw.trim().toDoubleOrNull()
                                if (v != null && v > 0.0) put(k, v)
                            }
                            val extraName = customCategoryName.trim()
                            val extraVal = customCategoryTarget.trim().toDoubleOrNull()
                            if (extraName.isNotEmpty() && extraVal != null && extraVal > 0.0) {
                                put(extraName, extraVal)
                            }
                        }
                        onSave(total, catMap)
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("保存目标")
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OfficialPlanWebViewScreen(
    url: String,
    onBackToNative: () -> Unit,
) {
    val context = LocalContext.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableStateOf(true) }
    var webError by remember { mutableStateOf<String?>(null) }

    fun openExternalBrowser() {
        val target = webViewRef?.url?.takeIf { it.startsWith("http") } ?: url
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
        }.onFailure {
            Toast.makeText(context, "未找到可用浏览器", Toast.LENGTH_SHORT).show()
        }
    }

    BackHandler {
        val wv = webViewRef
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
        } else {
            onBackToNative()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("教务官方培养方案") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    LiquidGlassBackButton(onClick = onBackToNative)
                },
                actions = {
                    LiquidGlassIconButton(
                        icon = Icons.Filled.Refresh,
                        contentDescription = "刷新网页",
                        onClick = {
                            webError = null
                            webViewRef?.reload()
                        },
                    )
                    Spacer(Modifier.width(6.dp))
                    LiquidGlassIconButton(
                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "在浏览器打开",
                        onClick = { openExternalBrowser() },
                    )
                    Spacer(Modifier.width(12.dp))
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                loading = true
                                webError = null
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                loading = false
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?,
                            ) {
                                if (request?.isForMainFrame == true) {
                                    loading = false
                                    webError = "无法加载教务页面（${error?.description ?: "网络异常"}），请检查校园网或认证状态后重试。"
                                }
                            }

                            override fun onReceivedHttpError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                errorResponse: WebResourceResponse?,
                            ) {
                                if (request?.isForMainFrame == true && (errorResponse?.statusCode ?: 0) >= 400) {
                                    loading = false
                                    webError = "教务页面返回 HTTP ${errorResponse?.statusCode}，可重试或改用系统浏览器打开。"
                                }
                            }

                            override fun onReceivedSslError(
                                view: WebView?,
                                handler: SslErrorHandler?,
                                error: SslError?,
                            ) {
                                handler?.cancel()
                                loading = false
                                webError = "教务网站 TLS 证书校验未通过，已中止连接；可尝试刷新或使用系统浏览器核验。"
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                return false
                            }
                        }
                        loadUrl(url)
                        webViewRef = this
                    }
                },
                onRelease = { wv ->
                    wv.stopLoading()
                    wv.destroy()
                },
            )
            if (loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            webError?.let { msg ->
                GlassCard(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("教务网页暂时无法访问", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            LiquidGlassOutlinedButton(
                                onClick = {
                                    webError = null
                                    webViewRef?.loadUrl(url)
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("重试")
                            }
                            LiquidGlassButton(
                                onClick = { openExternalBrowser() },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("浏览器打开")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatPlanCredits(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else String.format(Locale.US, "%.1f", v)
