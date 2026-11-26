package com.zzupocket.app.ui.academic

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.zzupocket.app.domain.ExamRecord
import com.zzupocket.app.domain.IcsExporter
import com.zzupocket.app.ui.components.GlassCard
import com.zzupocket.app.ui.components.LiquidGlassBackButton
import com.zzupocket.app.ui.components.LiquidGlassButton
import com.zzupocket.app.ui.components.LiquidGlassIconButton
import com.zzupocket.app.ui.components.LiquidGlassOutlinedButton
import com.zzupocket.app.ui.components.RelativeTime
import com.zzupocket.app.ui.components.StatusPill
import com.zzupocket.app.ui.theme.tabularNumbers
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ExamsUiState(
    val loggedIn: Boolean = false,
    val exams: List<ExamRecord> = emptyList(),
    val fetchedAt: Long? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

class ExamsViewModel : ViewModel() {

    private val repo = AppGraph.academicRepository
    private val auth = AppGraph.authRepository

    private val fetchedAt = MutableStateFlow<Long?>(null)
    private val loading = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private val loggedIn = MutableStateFlow(auth.isLoggedIn())

    val state: StateFlow<ExamsUiState> = combine(
        repo.observeExams(),
        fetchedAt,
        loading,
        error,
        loggedIn,
    ) { exams, ts, busy, err, logged ->
        ExamsUiState(
            loggedIn = logged,
            exams = exams,
            fetchedAt = ts,
            loading = busy,
            error = err,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ExamsUiState(loggedIn = auth.isLoggedIn()))

    init {
        viewModelScope.launch {
            val ts = repo.examsFetchedAt()
            fetchedAt.value = ts
            if (auth.isLoggedIn() && (ts == null || System.currentTimeMillis() - ts > AUTO_TTL_MS)) {
                refresh()
            }
        }
    }

    fun onResume() {
        loggedIn.value = auth.isLoggedIn()
        viewModelScope.launch {
            fetchedAt.value = repo.examsFetchedAt()
        }
    }

    fun refresh() {
        if (loading.value) return
        loggedIn.value = auth.isLoggedIn()
        if (!loggedIn.value) {
            error.value = "请先登录后同步官方考场安排"
            return
        }
        viewModelScope.launch {
            loading.value = true
            error.value = null
            runCatching {
                repo.refreshExams()
            }.onSuccess {
                fetchedAt.value = System.currentTimeMillis()
            }.onFailure { err ->
                error.value = err.toUserMessage()
            }
            loading.value = false
        }
    }

    suspend fun syncAllToCountdowns(): Int =
        repo.syncExamsToCountdowns(state.value.exams)

    suspend fun syncSingleToCountdown(exam: ExamRecord): Int =
        repo.syncExamsToCountdowns(listOf(exam))

    private companion object {
        const val AUTO_TTL_MS = 30 * 60 * 1000L
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamsScreen(
    onBack: () -> Unit,
    onOpenLogin: () -> Unit = {},
    vm: ExamsViewModel = viewModel(),
) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        vm.onResume()
    }

    var pendingIcs by remember { mutableStateOf<String?>(null) }
    val createIcsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/calendar"),
    ) { uri ->
        val content = pendingIcs
        pendingIcs = null
        if (uri != null && content != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(content.toByteArray(Charsets.UTF_8))
                }
            }.onSuccess {
                Toast.makeText(context, "考试日历 (.ics) 已导出", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "导出失败：${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun exportExamsIcs() {
        if (ui.exams.isEmpty()) {
            Toast.makeText(context, "暂无可导出的考试安排", Toast.LENGTH_SHORT).show()
            return
        }
        pendingIcs = IcsExporter.exportExams(exams = ui.exams, reminderMinutes = 60)
        createIcsLauncher.launch("zzupocket-exams.ics")
    }

    fun importCountdowns() {
        scope.launch {
            val added = vm.syncAllToCountdowns()
            val msg = if (added > 0) {
                "已同步 $added 门待考科目到「今天」倒数日"
            } else {
                "所有待考科目已在倒数日清单中"
            }
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    val upcoming = remember(ui.exams, today) { ui.exams.filter { !it.isFinished(today) } }
    val finished = remember(ui.exams, today) { ui.exams.filter { it.isFinished(today) } }
    val nearest = upcoming.firstOrNull()
    val nearestUrgent = nearest?.daysUntil(today)?.let { it <= 3 } == true

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("考场安排") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    LiquidGlassBackButton(onClick = onBack)
                },
                actions = {
                    LiquidGlassIconButton(
                        icon = Icons.Filled.CalendarToday,
                        contentDescription = "导出考试日历 (.ics)",
                        onClick = ::exportExamsIcs,
                    )
                    Spacer(Modifier.width(6.dp))
                    LiquidGlassIconButton(
                        icon = Icons.Filled.EventAvailable,
                        contentDescription = "同步至倒数日",
                        onClick = ::importCountdowns,
                    )
                    Spacer(Modifier.width(6.dp))
                    LiquidGlassIconButton(
                        icon = Icons.Filled.Refresh,
                        contentDescription = "刷新考场",
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
                if (!ui.loggedIn && ui.exams.isEmpty()) {
                    item(key = "login_prompt") {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("未登录教务账号", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "登录后可同步期末/补缓考时间、考场与座位号",
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

                // 汇总与联动快捷卡
                item(key = "summary_hero") {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusPill(
                                    text = if (upcoming.isEmpty()) "近期无待考安排" else "待考 ${upcoming.size} 门",
                                    containerColor = (if (nearestUrgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary).copy(alpha = 0.14f),
                                    contentColor = if (nearestUrgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.weight(1f))
                                ui.fetchedAt?.let { ts ->
                                    Text(
                                        text = "更新于 ${RelativeTime.ago(ts)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                }
                            }

                            if (nearest != null) {
                                val d = nearest.daysUntil(today)
                                val dayLabel = when {
                                    d == null -> "待定"
                                    d == 0 -> "今天开考"
                                    else -> "还有 $d 天"
                                }
                                Text(
                                    text = "${nearest.courseName} · $dayLabel",
                                    style = MaterialTheme.typography.headlineSmall.tabularNumbers(),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = listOfNotNull(
                                        nearest.examDate,
                                        "${nearest.startTimeText}-${nearest.endTimeText}".takeIf { nearest.startTime > 0 },
                                        nearest.place.takeIf { it.isNotBlank() },
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                Text("暂无待考记录", style = MaterialTheme.typography.titleMedium)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                LiquidGlassOutlinedButton(
                                    onClick = ::importCountdowns,
                                    enabled = upcoming.isNotEmpty(),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("导入倒数日")
                                }
                                LiquidGlassOutlinedButton(
                                    onClick = ::exportExamsIcs,
                                    enabled = ui.exams.isNotEmpty(),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("导出考试 .ics")
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

                if (upcoming.isNotEmpty()) {
                    item(key = "header_upcoming") {
                        Text(
                            text = "即将考试 (${upcoming.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    items(upcoming, key = { "up-${it.id}" }) { exam ->
                        ExamCard(
                            exam = exam,
                            today = today,
                            onAddCountdown = {
                                scope.launch {
                                    val n = vm.syncSingleToCountdown(exam)
                                    val msg = if (n > 0) "已将「${exam.courseName}」加入倒数日" else "该场考试已在倒数日中"
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                    }
                }

                if (finished.isNotEmpty()) {
                    item(key = "header_finished") {
                        Text(
                            text = "已结束 (${finished.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    items(finished, key = { "done-${it.id}" }) { exam ->
                        ExamCard(exam = exam, today = today, onAddCountdown = null)
                    }
                }

                if (ui.exams.isEmpty() && !ui.loading) {
                    item(key = "empty") {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text("教务暂未公布考试安排", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "考前两周左右教务发布考场与座位后，点右上角刷新即可同步",
                                    style = MaterialTheme.typography.bodySmall,
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

@Composable
private fun ExamCard(
    exam: ExamRecord,
    today: LocalDate,
    onAddCountdown: (() -> Unit)?,
) {
    val days = exam.daysUntil(today)
    val urgent = days != null && days in 0..3
    val isMakeup = exam.examType?.contains("补") == true || exam.examType?.contains("缓") == true

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    exam.examType?.let { type ->
                        StatusPill(
                            text = type,
                            containerColor = (if (isMakeup) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary).copy(alpha = 0.12f),
                            contentColor = if (isMakeup) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (exam.isMinor) {
                        StatusPill(
                            text = "辅修",
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    Text(
                        text = exam.courseName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (days != null && days >= 0) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (days == 0) "今天" else "${days}天后",
                        style = MaterialTheme.typography.titleSmall.tabularNumbers(),
                        color = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(5.dp))
                val weekLabel = weekDayLabel(exam.weekDay)
                val timePart = if (exam.startTime > 0) "${exam.startTimeText} – ${exam.endTimeText}" else ""
                Text(
                    text = listOfNotNull(exam.examDate, weekLabel, timePart.takeIf { it.isNotBlank() }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (exam.place.isNotBlank()) {
                    StatusPill(
                        text = exam.place,
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.11f),
                        contentColor = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("考场地点待定", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
                Spacer(Modifier.weight(1f))
                if (onAddCountdown != null && exam.parsedDate() != null) {
                    TextButton(onClick = onAddCountdown) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("倒数")
                    }
                }
            }
        }
    }
}

private fun weekDayLabel(weekDay: Int): String? = when (weekDay) {
    1 -> "周一"
    2 -> "周二"
    3 -> "周三"
    4 -> "周四"
    5 -> "周五"
    6 -> "周六"
    7 -> "周日"
    else -> null
}
