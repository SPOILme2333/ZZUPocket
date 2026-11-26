package com.zzupocket.app.ui.academic

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zzupocket.app.core.AppGraph
import com.zzupocket.app.core.CourseShortener
import com.zzupocket.app.core.toUserMessage
import com.zzupocket.app.data.prefs.UiPrefs
import com.zzupocket.app.domain.AcademicStats
import com.zzupocket.app.domain.GpaRankSummary
import com.zzupocket.app.domain.GradeRecord
import com.zzupocket.app.domain.SimulatedCourseInput
import com.zzupocket.app.ui.components.GlassCard
import com.zzupocket.app.ui.components.LiquidGlassBackButton
import com.zzupocket.app.ui.components.LiquidGlassButton
import com.zzupocket.app.ui.components.LiquidGlassChip
import com.zzupocket.app.ui.components.LiquidGlassIconButton
import com.zzupocket.app.ui.components.MiniSparkline
import com.zzupocket.app.ui.components.RelativeTime
import com.zzupocket.app.ui.components.StatusPill
import com.zzupocket.app.ui.components.liquidGlassSheetContainerColor
import com.zzupocket.app.ui.theme.tabularNumbers
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class GradeFilter(val label: String) {
    ALL("全部"),
    FULL_GP("满绩 4.0"),
    FAILED("未通过"),
    MINOR("辅修"),
}

data class GradesUiState(
    val loggedIn: Boolean = false,
    val grades: List<GradeRecord> = emptyList(),
    val gpaRank: GpaRankSummary? = null,
    val fetchedAt: Long? = null,
    val newlyPublishedIds: Set<String> = emptySet(),
    val loading: Boolean = false,
    val error: String? = null,
)

class GradesViewModel : ViewModel() {

    private val repo = AppGraph.academicRepository
    private val auth = AppGraph.authRepository

    private val gpaRank = MutableStateFlow<GpaRankSummary?>(null)
    private val fetchedAt = MutableStateFlow<Long?>(null)
    private val newIds = MutableStateFlow<Set<String>>(emptySet())
    private val loading = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private val loggedIn = MutableStateFlow(auth.isLoggedIn())

    private val metaFlow = combine(newIds, loading, error, loggedIn) { freshIds, busy, err, logged ->
        Triple(freshIds, busy, err to logged)
    }

    val state: StateFlow<GradesUiState> = combine(
        repo.observeGrades(),
        gpaRank,
        fetchedAt,
        metaFlow,
    ) { grades, rank, ts, meta ->
        val (freshIds, busy, errAndLogged) = meta
        val (err, logged) = errAndLogged
        val tagged = if (freshIds.isEmpty()) grades else grades.map {
            if (it.id in freshIds) it.copy(isNew = true) else it
        }
        GradesUiState(
            loggedIn = logged,
            grades = tagged,
            gpaRank = rank,
            fetchedAt = ts,
            newlyPublishedIds = freshIds,
            loading = busy,
            error = err,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, GradesUiState(loggedIn = auth.isLoggedIn()))

    init {
        viewModelScope.launch {
            gpaRank.value = repo.cachedGpaRank()
            val ts = repo.gradesFetchedAt()
            fetchedAt.value = ts
            if (auth.isLoggedIn() && (ts == null || System.currentTimeMillis() - ts > AUTO_TTL_MS)) {
                refresh()
            }
        }
    }

    fun onResume() {
        loggedIn.value = auth.isLoggedIn()
        viewModelScope.launch {
            gpaRank.value = repo.cachedGpaRank()
            fetchedAt.value = repo.gradesFetchedAt()
        }
    }

    fun refresh() {
        if (loading.value) return
        loggedIn.value = auth.isLoggedIn()
        if (!loggedIn.value) {
            error.value = "请先登录后同步成绩单"
            return
        }
        viewModelScope.launch {
            loading.value = true
            error.value = null
            runCatching {
                repo.refreshGrades()
            }.onSuccess { res ->
                gpaRank.value = res.gpaRank
                fetchedAt.value = System.currentTimeMillis()
                if (res.newlyPublishedIds.isNotEmpty()) {
                    newIds.value = res.newlyPublishedIds
                }
            }.onFailure { err ->
                error.value = err.toUserMessage()
            }
            loading.value = false
        }
    }

    private companion object {
        const val AUTO_TTL_MS = 30 * 60 * 1000L
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradesScreen(
    onBack: () -> Unit,
    onOpenLogin: () -> Unit = {},
    vm: GradesViewModel = viewModel(),
) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val prefs by AppGraph.uiPrefsStore.flow.collectAsStateWithLifecycle(initialValue = AppGraph.uiPrefsStore.latest)

    var selectedSemester by remember { mutableStateOf<String?>(null) }
    var selectedFilter by remember { mutableStateOf(GradeFilter.ALL) }
    var showSimulator by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        vm.onResume()
    }

    val semesters = remember(ui.grades) {
        ui.grades
            .distinctBy { it.semesterCode }
            .sortedByDescending { it.semesterCode }
            .map { it.semesterCode to it.semesterName }
    }

    val filteredGrades = remember(ui.grades, selectedSemester, selectedFilter) {
        ui.grades.filter { g ->
            val matchSem = selectedSemester == null || g.semesterCode == selectedSemester
            val matchFilter = when (selectedFilter) {
                GradeFilter.ALL -> true
                GradeFilter.FULL_GP -> !g.isUnevaluated && (g.gp ?: 0.0) >= 3.99
                GradeFilter.FAILED -> !g.passed && !g.isUnevaluated
                GradeFilter.MINOR -> g.isMinor
            }
            matchSem && matchFilter
        }
    }

    val trend = remember(ui.grades) { AcademicStats.semesterTrend(ui.grades) }
    val computedGpa = remember(ui.grades) { AcademicStats.weightedGpa(ui.grades) }
    val earnedCredits = remember(ui.grades) { AcademicStats.earnedCredits(ui.grades) }
    val displayGpa = ui.gpaRank?.gpa ?: computedGpa

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("成绩与绩点") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    LiquidGlassBackButton(onClick = onBack)
                },
                actions = {
                    LiquidGlassIconButton(
                        icon = Icons.Filled.Calculate,
                        contentDescription = "GPA 模拟算分器",
                        onClick = { showSimulator = true },
                    )
                    Spacer(Modifier.width(6.dp))
                    LiquidGlassIconButton(
                        icon = Icons.Filled.Refresh,
                        contentDescription = "刷新成绩",
                        onClick = vm::refresh,
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
            if (ui.loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!ui.loggedIn && ui.grades.isEmpty()) {
                    item(key = "login_prompt") {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("未登录教务账号", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "登录后可同步本科全部学期成绩单与 GPA 排名",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                LiquidGlassButton(onClick = onOpenLogin) {
                                    Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("登录")
                                }
                            }
                        }
                    }
                }

                // GPA Hero 卡
                item(key = "gpa_hero") {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.School,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("平均学分绩点", style = MaterialTheme.typography.titleSmall)
                                }
                                Spacer(Modifier.weight(1f))
                                ui.gpaRank?.let { r ->
                                    if (r.rank != null && r.total != null) {
                                        Text(
                                            text = "专业 ${r.rank} / ${r.total}",
                                            style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Bottom,
                            ) {
                                Text(
                                    text = displayGpa?.let { String.format(Locale.US, "%.3f", it) } ?: "--",
                                    style = MaterialTheme.typography.displaySmall.tabularNumbers(),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.padding(bottom = 4.dp)) {
                                    Text(
                                        text = "已修 ${formatCredits(earnedCredits)} 学分 · ${ui.grades.count { !it.isUnevaluated }} 门课",
                                        style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    ui.fetchedAt?.let { ts ->
                                        Text(
                                            text = "更新于 ${RelativeTime.ago(ts)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline,
                                        )
                                    }
                                }
                            }

                            if (trend.size >= 2) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = "GPA 走势 · ${compactSemesterLabel(trend.first().semesterName)} → ${compactSemesterLabel(trend.last().semesterName)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            text = "最新 ${String.format(Locale.US, "%.2f", trend.last().gpa)}",
                                            style = MaterialTheme.typography.labelMedium.tabularNumbers(),
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    MiniSparkline(
                                        values = trend.map { it.gpa.toFloat() },
                                        lineColor = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(34.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                if (ui.newlyPublishedIds.isNotEmpty()) {
                    item(key = "new_grades_banner") {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                StatusPill(
                                    text = "新出分",
                                    containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f),
                                    contentColor = MaterialTheme.colorScheme.tertiary,
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "本轮刷新发现 ${ui.newlyPublishedIds.size} 门新增成绩，已在下方标记高亮。",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }

                ui.error?.let { err ->
                    item(key = "error_banner") {
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
                                TextButton(onClick = vm::refresh) { Text("重试") }
                            }
                        }
                    }
                }

                // 学期 & 快捷筛选芯片栏
                if (semesters.isNotEmpty()) {
                    item(key = "filters") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                LiquidGlassChip(
                                    label = "全部学期",
                                    selected = selectedSemester == null,
                                    onClick = { selectedSemester = null },
                                )
                                semesters.forEach { (code, name) ->
                                    LiquidGlassChip(
                                        label = name,
                                        selected = selectedSemester == code,
                                        onClick = { selectedSemester = code },
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                GradeFilter.entries.forEach { f ->
                                    LiquidGlassChip(
                                        label = f.label,
                                        selected = selectedFilter == f,
                                        accentColor = if (f == GradeFilter.FAILED) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        },
                                        onClick = { selectedFilter = f },
                                    )
                                }
                            }
                        }
                    }
                }

                if (filteredGrades.isEmpty() && !ui.loading) {
                    item(key = "empty") {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text("暂无符合条件的成绩", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "如果已登录，可点击右上角刷新按钮从教务系统同步",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                items(filteredGrades, key = { it.id }) { grade ->
                    GradeCard(
                        grade = grade,
                        smartShorten = prefs.smartShortCourseName,
                    )
                }
            }
        }
    }

    if (showSimulator) {
        GpaSimulatorSheet(
            grades = ui.grades,
            currentGpa = displayGpa,
            onDismiss = { showSimulator = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GradeCard(
    grade: GradeRecord,
    smartShorten: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    val hasSubs = grade.subGrades.isNotEmpty()
    val failed = !grade.passed && !grade.isUnevaluated

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = if (hasSubs) { { expanded = !expanded } } else null,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (grade.isNew) {
                            StatusPill(
                                text = "新",
                                containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f),
                                contentColor = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                        if (grade.isMinor) {
                            StatusPill(
                                text = "辅修",
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                        Text(
                            text = CourseShortener.shorten(grade.courseName, smartShorten),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    val meta = listOfNotNull(
                        grade.semesterName,
                        grade.courseProperty ?: grade.courseType,
                        if (grade.retake) "重修" else null,
                        grade.displayTag,
                    ).joinToString(" · ")
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = grade.finalGrade,
                        style = MaterialTheme.typography.headlineSmall.tabularNumbers(),
                        color = when {
                            grade.isUnevaluated -> MaterialTheme.colorScheme.outline
                            failed -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Text(
                        text = buildString {
                            append("${formatCredits(grade.credits)} 学分")
                            grade.gp?.let { append(" · GP ${String.format(Locale.US, "%.1f", it)}") }
                        },
                        style = MaterialTheme.typography.labelSmall.tabularNumbers(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (hasSubs) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (expanded) "收起成绩构成" else "展开分项成绩（${grade.subGrades.size} 项）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
                AnimatedVisibility(visible = expanded) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        grade.subGrades.forEach { sub ->
                            StatusPill(
                                text = "${sub.name}: ${sub.score}",
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.09f),
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GpaSimulatorSheet(
    grades: List<GradeRecord>,
    currentGpa: Double?,
    onDismiss: () -> Unit,
) {
    val excludedIds = remember { mutableStateMapOf<String, Boolean>() }
    val extras = remember { mutableStateListOf<SimulatedCourseInput>() }

    var newCourseName by remember { mutableStateOf("") }
    var newCreditText by remember { mutableStateOf("2.0") }
    var sliderScore by remember { mutableDoubleStateOf(90.0) }

    val activeExcluded = excludedIds.filterValues { it }.keys
    val (simGpa, totalCredits) = remember(grades, activeExcluded.size, extras.size) {
        AcademicStats.simulateGpa(
            baseGrades = grades,
            excludedIds = activeExcluded,
            extras = extras.toList(),
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = liquidGlassSheetContainerColor(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("GPA 模拟算分器", style = MaterialTheme.typography.titleLarge)

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("模拟后加权 GPA", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = simGpa?.let { String.format(Locale.US, "%.3f", it) } ?: "--",
                            style = MaterialTheme.typography.headlineMedium.tabularNumbers(),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "计入 ${formatCredits(totalCredits)} 学分",
                            style = MaterialTheme.typography.labelSmall.tabularNumbers(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (currentGpa != null && simGpa != null) {
                        val delta = simGpa - currentGpa
                        val sign = if (delta >= 0) "+" else ""
                        StatusPill(
                            text = "较当前 $sign${String.format(Locale.US, "%.3f", delta)}",
                            containerColor = (if (delta >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error).copy(alpha = 0.14f),
                            contentColor = if (delta >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            // 添加假想新课程
            Text("添加预估课程（按郑大 4.0 分段换算）", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = newCourseName,
                    onValueChange = { newCourseName = it.take(24) },
                    label = { Text("课程名") },
                    singleLine = true,
                    modifier = Modifier.weight(1.4f),
                )
                OutlinedTextField(
                    value = newCreditText,
                    onValueChange = { newCreditText = it.take(5) },
                    label = { Text("学分") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(0.8f),
                )
            }
            val estGp = AcademicStats.scoreToZzuGp(sliderScore)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "预估分数 ${sliderScore.toInt()}（GP ${String.format(Locale.US, "%.1f", estGp)}）",
                    style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                    modifier = Modifier.weight(1f),
                )
                LiquidGlassButton(
                    onClick = {
                        val cr = newCreditText.toDoubleOrNull() ?: return@LiquidGlassButton
                        if (cr <= 0.0) return@LiquidGlassButton
                        extras += SimulatedCourseInput(
                            id = "sim-${System.nanoTime()}",
                            name = newCourseName.trim().ifBlank { "预估课程 ${extras.size + 1}" },
                            credits = cr,
                            gp = estGp,
                        )
                        newCourseName = ""
                    },
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("加入计算")
                }
            }
            Slider(
                value = sliderScore.toFloat(),
                onValueChange = { sliderScore = it.toDouble() },
                valueRange = 50f..100f,
            )

            if (extras.isNotEmpty()) {
                extras.forEach { item ->
                    GlassCard(modifier = Modifier.fillMaxWidth(), translucent = true) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${item.name} · ${formatCredits(item.credits)}学分 · GP ${String.format(Locale.US, "%.1f", item.gp)}",
                                style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = { extras.remove(item) },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "移除", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            if (grades.isNotEmpty()) {
                HorizontalDivider()
                Text("或勾选取消计入已修课程", style = MaterialTheme.typography.titleSmall)
                grades.filter { !it.isUnevaluated && it.gp != null && it.credits > 0.0 }
                    .take(30)
                    .forEach { g ->
                        val checked = excludedIds[g.id] != true
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { excludedIds[g.id] = checked },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { include -> excludedIds[g.id] = !include },
                            )
                            Text(
                                text = g.courseName,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${formatCredits(g.credits)}学分 / ${String.format(Locale.US, "%.1f", g.gp)}",
                                style = MaterialTheme.typography.labelSmall.tabularNumbers(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
            }
        }
    }
}

private fun formatCredits(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else String.format(Locale.US, "%.1f", v)

private val COMPACT_SEMESTER_REGEX = Regex("""(\d{2})(\d{2})-(\d{2})(\d{2})\s*第([一二12])学期""")

private fun compactSemesterLabel(name: String): String {
    val match = COMPACT_SEMESTER_REGEX.find(name) ?: return name
    val (_, y1, _, y2, term) = match.destructured
    val termLabel = if (term == "一" || term == "1") "秋" else "春"
    return "$y1-$y2$termLabel"
}
