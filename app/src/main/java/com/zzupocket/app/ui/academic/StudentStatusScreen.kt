package com.zzupocket.app.ui.academic

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zzupocket.app.core.AppGraph
import com.zzupocket.app.core.toUserMessage
import com.zzupocket.app.domain.AcademicStats
import com.zzupocket.app.domain.StudentProfile
import com.zzupocket.app.ui.components.GlassCard
import com.zzupocket.app.ui.components.LiquidGlassBackButton
import com.zzupocket.app.ui.components.LiquidGlassButton
import com.zzupocket.app.ui.components.LiquidGlassChip
import com.zzupocket.app.ui.components.LiquidGlassIconButton
import com.zzupocket.app.ui.components.RelativeTime
import com.zzupocket.app.ui.components.StatusPill
import com.zzupocket.app.ui.components.glassClickable
import com.zzupocket.app.ui.theme.tabularNumbers
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StudentStatusUiState(
    val loggedIn: Boolean = false,
    val profiles: List<StudentProfile> = emptyList(),
    val creditsBreakdown: List<Pair<String, Double>> = emptyList(),
    val totalCredits: Double = 0.0,
    val fetchedAt: Long? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

class StudentStatusViewModel : ViewModel() {

    private val repo = AppGraph.academicRepository
    private val auth = AppGraph.authRepository

    private val profiles = MutableStateFlow<List<StudentProfile>>(emptyList())
    private val fetchedAt = MutableStateFlow<Long?>(null)
    private val loading = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private val loggedIn = MutableStateFlow(auth.isLoggedIn())

    private val metaFlow = combine(loading, error, loggedIn) { busy, err, logged ->
        Triple(busy, err, logged)
    }

    val state: StateFlow<StudentStatusUiState> = combine(
        profiles,
        repo.observeGrades(),
        fetchedAt,
        metaFlow,
    ) { profs, grades, ts, meta ->
        val (busy, err, logged) = meta
        StudentStatusUiState(
            loggedIn = logged,
            profiles = profs,
            creditsBreakdown = AcademicStats.creditsByProperty(grades),
            totalCredits = AcademicStats.earnedCredits(grades),
            fetchedAt = ts,
            loading = busy,
            error = err,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, StudentStatusUiState(loggedIn = auth.isLoggedIn()))

    init {
        viewModelScope.launch {
            profiles.value = repo.cachedProfiles()
            val ts = repo.profileFetchedAt()
            fetchedAt.value = ts
            if (auth.isLoggedIn() && (ts == null || profiles.value.isEmpty())) {
                refresh()
            }
        }
    }

    fun onResume() {
        loggedIn.value = auth.isLoggedIn()
        viewModelScope.launch {
            profiles.value = repo.cachedProfiles()
            fetchedAt.value = repo.profileFetchedAt()
        }
    }

    fun refresh() {
        if (loading.value) return
        loggedIn.value = auth.isLoggedIn()
        if (!loggedIn.value) {
            error.value = "请先登录后查看学籍档案"
            return
        }
        viewModelScope.launch {
            loading.value = true
            error.value = null
            runCatching {
                repo.refreshProfiles()
            }.onSuccess { list ->
                profiles.value = list
                fetchedAt.value = System.currentTimeMillis()
            }.onFailure { err ->
                error.value = err.toUserMessage()
            }
            loading.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentStatusScreen(
    onBack: () -> Unit,
    onOpenLogin: () -> Unit = {},
    onOpenTrainingPlan: () -> Unit = {},
    vm: StudentStatusViewModel = viewModel(),
) {
    val ui by vm.state.collectAsStateWithLifecycle()
    // 默认开启隐私打码，保护截屏或旁边人看到姓名与学号
    var masked by remember { mutableStateOf(true) }
    var selectedIdx by remember { mutableIntStateOf(0) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        vm.onResume()
    }

    val currentProfile = ui.profiles.getOrNull(selectedIdx.coerceIn(0, (ui.profiles.size - 1).coerceAtLeast(0)))

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("学籍档案") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    LiquidGlassBackButton(onClick = onBack)
                },
                actions = {
                    LiquidGlassIconButton(
                        icon = if (masked) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (masked) "显示姓名与学号" else "隐藏姓名与学号",
                        onClick = { masked = !masked },
                    )
                    Spacer(Modifier.width(6.dp))
                    LiquidGlassIconButton(
                        icon = Icons.Filled.Refresh,
                        contentDescription = "刷新学籍信息",
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
                if (!ui.loggedIn && ui.profiles.isEmpty()) {
                    item(key = "login_prompt") {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("未登录教务账号", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "登录后可查看学籍状态、院系专业与注册缴费记录",
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

                if (ui.profiles.size > 1) {
                    item(key = "cultivate_tabs") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ui.profiles.forEachIndexed { idx, prof ->
                                LiquidGlassChip(
                                    label = prof.cultivateType ?: "学籍 ${idx + 1}",
                                    selected = idx == selectedIdx,
                                    onClick = { selectedIdx = idx },
                                )
                            }
                        }
                    }
                }

                if (currentProfile != null) {
                    item(key = "profile_card") {
                        var expanded by remember { mutableStateOf(false) }
                        val arrowRotation by animateFloatAsState(
                            targetValue = if (expanded) 180f else 0f,
                            animationSpec = tween(220, easing = FastOutSlowInEasing),
                            label = "statusArrowRotation",
                        )
                        val displayName = if (masked) {
                            AcademicStats.maskName(currentProfile.name).ifBlank { "郑大同学" }
                        } else {
                            currentProfile.name.ifBlank { "郑大同学" }
                        }
                        val displayCode = if (masked) {
                            AcademicStats.maskStudentCode(currentProfile.studentCode)
                        } else {
                            currentProfile.studentCode
                        }
                        val detailRows = remember(currentProfile) {
                            listOfNotNull(
                                currentProfile.department?.let { "学院" to it },
                                currentProfile.major?.let { "专业" to it },
                                currentProfile.majorDirection?.let { "专业方向" to it },
                                currentProfile.adminClass?.let { "行政班" to it },
                                currentProfile.grade?.let { "年级" to it },
                                currentProfile.campus?.let { "校区" to it },
                                currentProfile.education?.let { "培养层次" to it },
                                currentProfile.studyForm?.let { "学习形式" to it },
                                currentProfile.enterDate?.let { "入学日期" to it },
                                currentProfile.expectedGraduateDate?.let { "预计毕业" to it },
                                currentProfile.tutor?.let { "导师" to it },
                            )
                        }

                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { if (detailRows.isNotEmpty()) expanded = !expanded },
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = currentProfile.name.firstOrNull()?.toString() ?: "郑",
                                            style = MaterialTheme.typography.titleLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(displayName, style = MaterialTheme.typography.titleLarge)
                                        Text(
                                            text = "学号 $displayCode",
                                            style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    val statusText = currentProfile.statusText?.trim()?.takeIf { it.isNotEmpty() }
                                    val inSchoolLabel = currentProfile.inSchool?.let { if (it) "在校" else "非在校" }
                                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        statusText?.let { status ->
                                            StatusPill(
                                                text = status,
                                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                                contentColor = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                        if (inSchoolLabel != null && inSchoolLabel != statusText) {
                                            StatusPill(
                                                text = inSchoolLabel,
                                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = ui.fetchedAt?.let { ts ->
                                            "更新于 ${RelativeTime.ago(ts)}"
                                        } ?: "仅保存于本机",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (detailRows.isNotEmpty()) {
                                        Spacer(Modifier.width(8.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = if (expanded) "收起详情" else "展开详情",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                            Spacer(Modifier.width(2.dp))
                                            Icon(
                                                imageVector = Icons.Filled.KeyboardArrowDown,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .graphicsLayer { rotationZ = arrowRotation },
                                            )
                                        }
                                    }
                                }

                                AnimatedVisibility(
                                    visible = expanded && detailRows.isNotEmpty(),
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
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                        detailRows.forEachIndexed { index, (label, value) ->
                                            if (index > 0) {
                                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.width(84.dp),
                                                )
                                                Text(
                                                    text = value,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium,
                                                    modifier = Modifier.weight(1f),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 学分分布卡片（联动本地成绩缓存 + 培养计划查看入口）
                if (ui.creditsBreakdown.isNotEmpty() || ui.loggedIn) {
                    item(key = "credits_breakdown") {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("已获学分分布", style = MaterialTheme.typography.titleMedium)
                                    Spacer(Modifier.weight(1f))
                                    StatusPill(
                                        text = "合计 ${formatStatusCredits(ui.totalCredits)} 学分",
                                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        contentColor = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                if (ui.creditsBreakdown.isEmpty()) {
                                    Text(
                                        text = "刷新成绩单后自动汇总各类别已修学分",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    val maxCredit = ui.creditsBreakdown.maxOfOrNull { it.second }?.coerceAtLeast(1.0) ?: 1.0
                                    ui.creditsBreakdown.forEach { (category, credits) ->
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(Modifier.fillMaxWidth()) {
                                                Text(category, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                                Text(
                                                    text = "${formatStatusCredits(credits)} 学分",
                                                    style = MaterialTheme.typography.labelMedium.tabularNumbers(),
                                                    color = MaterialTheme.colorScheme.primary,
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
                                                        .fillMaxWidth((credits / maxCredit).toFloat().coerceIn(0f, 1f))
                                                        .height(4.dp)
                                                        .background(MaterialTheme.colorScheme.primary),
                                                )
                                            }
                                        }
                                    }
                                }

                                HorizontalDivider(
                                    modifier = Modifier.padding(top = 2.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .glassClickable(onClick = onOpenTrainingPlan)
                                        .padding(horizontal = 4.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.School,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        Text(
                                            text = "查看培养计划",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = "核对各类学分进度与官方方案",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                // 学期注册记录卡
                if (currentProfile != null && currentProfile.registers.isNotEmpty()) {
                    item(key = "register_records") {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text("学期注册与缴费记录", style = MaterialTheme.typography.titleMedium)
                                currentProfile.registers.forEachIndexed { idx, reg ->
                                    if (idx > 0) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = reg.semesterName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f),
                                        )
                                        val statusText = buildString {
                                            append(if (reg.registered) "✓ 已注册" else "未注册")
                                            reg.payed?.let { paid ->
                                                append(if (paid) " · 已缴费" else " · 未缴费")
                                            }
                                        }
                                        val hasIssue = !reg.registered || reg.payed == false
                                        Text(
                                            text = statusText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (hasIssue) {
                                                MaterialTheme.colorScheme.tertiary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
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

private fun formatStatusCredits(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else String.format(Locale.US, "%.1f", v)
