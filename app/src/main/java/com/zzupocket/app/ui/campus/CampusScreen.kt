package com.zzupocket.app.ui.campus

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.EventSeat
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zzupocket.app.core.AppGraph
import com.zzupocket.app.data.prefs.UiPrefs
import com.zzupocket.app.data.repo.ScheduleIcsExportHelper
import com.zzupocket.app.data.update.AppUpdate
import com.zzupocket.app.data.update.UpdateCenter
import com.zzupocket.app.domain.AcademicStats
import com.zzupocket.app.domain.EnergyStats
import com.zzupocket.app.domain.FocusStats
import com.zzupocket.app.domain.GpaRankSummary
import com.zzupocket.app.domain.PomodoroClock
import com.zzupocket.app.domain.StudentProfile
import com.zzupocket.app.ui.components.GlassCard
import com.zzupocket.app.ui.components.GlassInsetDivider
import com.zzupocket.app.ui.components.GlassListCard
import com.zzupocket.app.ui.components.GlassListRow
import com.zzupocket.app.ui.components.GlassMetricCell
import com.zzupocket.app.ui.components.GlassSectionHeader
import com.zzupocket.app.ui.components.LiquidGlassButton
import com.zzupocket.app.ui.components.LiquidGlassIconButton
import com.zzupocket.app.ui.components.MiniSparkline
import com.zzupocket.app.ui.nav.DOCK_CLEARANCE
import com.zzupocket.app.ui.theme.tabularNumbers
import com.zzupocket.app.ui.today.CountdownManagerSheet
import com.zzupocket.app.ui.today.TodayCardViewModel
import com.zzupocket.app.ui.today.TodayTimelineViewModel
import com.zzupocket.app.ui.today.TodayViewModel
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * 「校园」克制液态玻璃分组看板：零嵌套卡片、零多余胶囊、状态直接落进内容数值。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampusScreen(
    onOpenLogin: () -> Unit,
    onOpenElectricity: () -> Unit = {},
    onOpenCard: () -> Unit = {},
    onOpenRooms: () -> Unit = {},
    onOpenLibrary: () -> Unit = {},
    onOpenLibRecords: () -> Unit = {},
    onOpenFocus: () -> Unit = {},
    onOpenGrades: () -> Unit = {},
    onOpenExams: () -> Unit = {},
    onOpenStudentStatus: () -> Unit = {},
    onOpenTrainingPlan: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenAppearance: () -> Unit = {},
    todayVm: TodayViewModel = viewModel(),
    cardVm: TodayCardViewModel = viewModel(),
    timelineVm: TodayTimelineViewModel = viewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs by AppGraph.uiPrefsStore.flow.collectAsStateWithLifecycle(initialValue = AppGraph.uiPrefsStore.latest)

    val todayState by todayVm.state.collectAsStateWithLifecycle()
    val cardState by cardVm.state.collectAsStateWithLifecycle()
    val timelineState by timelineVm.state.collectAsStateWithLifecycle()
    val timerUi by AppGraph.pomodoroEngine.state.collectAsStateWithLifecycle()
    val countdowns by AppGraph.countdownRepository.observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val grades by AppGraph.academicRepository.observeGrades()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val exams by AppGraph.academicRepository.observeExams()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var loggedIn by remember { mutableStateOf(AppGraph.authRepository.isLoggedIn()) }
    var cachedRoomCount by remember { mutableStateOf<Int?>(null) }
    var cachedGpaRank by remember { mutableStateOf<GpaRankSummary?>(null) }
    var cachedProfile by remember { mutableStateOf<StudentProfile?>(null) }
    var showCountdownSheet by remember { mutableStateOf(false) }

    val currentVersion = remember { AppUpdate.localVersion(context) }
    val remoteRelease by UpdateCenter.release.collectAsState(initial = null)
    val mutedVersion by UpdateCenter.mutedVersion.collectAsState(initial = null)
    val showDot = AppUpdate.shouldShowDot(currentVersion, remoteRelease?.version, mutedVersion)

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
                Toast.makeText(context, "课表日历 (.ics) 已导出", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "导出失败：${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        loggedIn = AppGraph.authRepository.isLoggedIn()
        todayVm.onResume()
        cardVm.reload()
        timelineVm.reload()
        scope.launch {
            val todayIso = LocalDate.now().toString()
            val summary = runCatching { AppGraph.scheduleRepository.roomSnapshotSummary() }
                .getOrDefault(emptyList())
            cachedRoomCount = summary.filter { it.date == todayIso }.sumOf { it.rooms }
                .takeIf { it > 0 }
            cachedGpaRank = runCatching { AppGraph.academicRepository.cachedGpaRank() }.getOrNull()
            cachedProfile = runCatching { AppGraph.academicRepository.cachedProfiles().firstOrNull() }.getOrNull()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("校园", style = MaterialTheme.typography.headlineMedium) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                actions = {
                    LiquidGlassIconButton(
                        icon = Icons.Outlined.Palette,
                        contentDescription = "外观与配色",
                        onClick = onOpenAppearance,
                    )
                    Spacer(Modifier.width(8.dp))
                    LiquidGlassIconButton(
                        icon = Icons.Outlined.Settings,
                        contentDescription = "设置",
                        badgeDot = showDot,
                        onClick = onOpenSettings,
                    )
                    Spacer(Modifier.width(12.dp))
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = DOCK_CLEARANCE),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!loggedIn && todayState.light.kwh == null && cardState.balance == null) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("绑定郑大账号", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "登录后自动同步电费、校园卡流水与教室占用",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        LiquidGlassButton(onClick = onOpenLogin) {
                            Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("登录")
                        }
                    }
                }
            }

            // 1. 生活：宿舍电量 + 校园卡
            GlassSectionHeader("生活")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val threshold = if (prefs.lowEnergyAlertEnabled) {
                    prefs.lowEnergyThresholdKwh.toDouble()
                } else {
                    EnergyStats.LOW_KWH
                }
                val primaryMeter = todayState.light.takeIf { it.kwh != null || it.roomId.isNotBlank() }
                    ?: todayState.ac
                val lowPower = listOfNotNull(todayState.light.kwh, todayState.ac.kwh)
                    .any { it < threshold }

                GlassCard(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = onOpenElectricity,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Bolt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "宿舍电量",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        if (primaryMeter.kwh != null) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = formatCampusNum(primaryMeter.kwh!!),
                                    style = MaterialTheme.typography.headlineSmall.tabularNumbers(),
                                    color = if (lowPower) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                )
                                Text(
                                    text = " 度",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 2.dp),
                                )
                            }
                        } else {
                            Text(
                                text = if (loggedIn) "暂未查询" else "未登录",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }

                        val baseSecondary = buildString {
                            if (todayState.light.kwh != null && todayState.ac.kwh != null) {
                                append("空调 ${formatCampusNum(todayState.ac.kwh!!)} 度")
                            } else if (todayState.trendTotalKwh > 0) {
                                append("近 7 天 ${formatCampusNum(todayState.trendTotalKwh)} 度")
                            } else {
                                append(todayState.roomHint.ifBlank { "照明 + 空调用量" })
                            }
                        }
                        val secondaryText = if (lowPower) "偏低 · $baseSecondary" else baseSecondary
                        Text(
                            text = secondaryText,
                            style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        if (todayState.trend.isNotEmpty()) {
                            Spacer(Modifier.weight(1f, fill = false))
                            MiniSparkline(
                                values = todayState.trend.map { (it.light + it.ac).toFloat() },
                                lineColor = if (lowPower) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.60f)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(24.dp),
                            )
                        }
                    }
                }

                GlassCard(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = onOpenCard,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "校园卡",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        if (cardState.balance != null) {
                            Text(
                                text = "¥${String.format(Locale.US, "%.2f", cardState.balance)}",
                                style = MaterialTheme.typography.headlineSmall.tabularNumbers(),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val spendLabel = if (cardState.todayCount > 0) {
                                "今日 ${cardState.todayCount} 笔 · ¥${String.format(Locale.US, "%.2f", cardState.todaySpend)}"
                            } else {
                                cardState.lastTrade ?: "点击查看收支明细"
                            }
                            Text(
                                text = spendLabel,
                                style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        } else {
                            Text(
                                text = if (loggedIn) "暂未查询" else "未登录",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            // 2. 教务：1 卡 3 格 + 竖向细线
            GlassSectionHeader("教务")
            val campusToday = remember { LocalDate.now() }
            val displayGpa = remember(cachedGpaRank, grades) {
                cachedGpaRank?.gpa ?: AcademicStats.weightedGpa(grades)
            }
            val upcomingExams = remember(exams, campusToday) {
                exams.filter { !it.isFinished(campusToday) }
            }
            val nextExam = upcomingExams.firstOrNull()
            val examUrgent = nextExam?.daysUntil(campusToday)?.let { it in 0..3 } == true

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                    ) {
                        GlassMetricCell(
                            label = "成绩",
                            value = displayGpa?.let { String.format(Locale.US, "%.2f", it) }
                                ?: if (grades.isNotEmpty()) "${grades.size} 门" else "—",
                            caption = "GPA",
                            onClick = onOpenGrades,
                            modifier = Modifier.weight(1f),
                        )
                        VerticalDivider(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(vertical = 12.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.70f),
                        )
                        val examValue = when {
                            nextExam == null -> "—"
                            nextExam.daysUntil(campusToday) == 0 -> "今天"
                            nextExam.daysUntil(campusToday) != null -> "${nextExam.daysUntil(campusToday)} 天后"
                            else -> "${upcomingExams.size} 门"
                        }
                        GlassMetricCell(
                            label = "考场",
                            value = examValue,
                            caption = if (upcomingExams.isNotEmpty()) "${upcomingExams.size} 门待考" else "暂无安排",
                            valueColor = if (examUrgent) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                            onClick = onOpenExams,
                            modifier = Modifier.weight(1f),
                        )
                        VerticalDivider(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(vertical = 12.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.70f),
                        )
                        GlassMetricCell(
                            label = "学籍",
                            value = cachedProfile?.statusText ?: "查看",
                            caption = "学籍档案",
                            onClick = onOpenStudentStatus,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    GlassInsetDivider(startIndent = 16.dp)
                    GlassListRow(
                        icon = Icons.Outlined.School,
                        title = "培养方案与完成情况",
                        trailing = "学分进度",
                        onClick = onOpenTrainingPlan,
                    )
                }
            }

            // 3. 学习空间：单张分组列表卡
            GlassSectionHeader("学习空间")
            GlassListCard {
                GlassListRow(
                    icon = Icons.Outlined.MeetingRoom,
                    title = "空教室查询",
                    trailing = cachedRoomCount?.let { "$it 间" },
                    onClick = onOpenRooms,
                )
                GlassInsetDivider()
                GlassListRow(
                    icon = Icons.Outlined.EventSeat,
                    title = "图书馆预约",
                    trailing = "选座系统",
                    onClick = onOpenLibrary,
                )
                GlassInsetDivider()
                GlassListRow(
                    icon = Icons.Outlined.History,
                    title = "我的座位预约记录",
                    onClick = onOpenLibRecords,
                )
            }

            // 4. 专注与倒数：双磁贴
            GlassSectionHeader("专注与倒数")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val focusActive = !timerUi.idle
                GlassCard(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = onOpenFocus,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "番茄专注",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = if (focusActive) {
                                PomodoroClock.format(timerUi.remainingSeconds)
                            } else if (timelineState.focusSeconds > 0) {
                                FocusStats.humanDuration(timelineState.focusSeconds)
                            } else {
                                "待开启"
                            },
                            style = MaterialTheme.typography.headlineSmall.tabularNumbers(),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                        )
                        Text(
                            text = if (focusActive) {
                                "${timerUi.phase.label}进行中"
                            } else {
                                "今日已完成 ${timelineState.focusCount} 轮"
                            },
                            style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                val todayDate = remember { LocalDate.now() }
                val nearest = remember(countdowns, todayDate) {
                    countdowns.firstOrNull { !it.isExpired(todayDate) }
                }
                val nearestDays = nearest?.daysUntil(todayDate)
                val urgent = nearestDays != null && nearestDays <= 3
                GlassCard(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = { showCountdownSheet = true },
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "考试倒数",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        when {
                            nearest == null || nearestDays == null -> Text(
                                text = "点击添加",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                            nearestDays == 0 -> Text(
                                text = "今天",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                maxLines = 1,
                            )
                            else -> Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = "$nearestDays",
                                    style = MaterialTheme.typography.headlineSmall.tabularNumbers(),
                                    color = if (urgent) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                )
                                Text(
                                    text = " 天",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 2.dp),
                                )
                            }
                        }
                        Text(
                            text = nearest?.title ?: "四六级 / 期末考日程",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // 5. 工具
            GlassSectionHeader("工具")
            GlassListCard {
                GlassListRow(
                    icon = Icons.Outlined.CalendarToday,
                    title = "导出课表为日历 (.ics)",
                    subtitle = "导入系统日历或手表提醒",
                    onClick = {
                        scope.launch {
                            val lead = if (prefs.classReminderEnabled) prefs.classReminderMinutes else 15
                            val result = ScheduleIcsExportHelper.exportCurrentSchedule(
                                scheduleRepo = AppGraph.scheduleRepository,
                                uiPrefs = AppGraph.uiPrefsStore,
                                reminderMinutes = lead,
                            )
                            if (result == null) {
                                Toast.makeText(context, "暂无可导出的本地课表，请先同步课表", Toast.LENGTH_SHORT).show()
                            } else {
                                val (fileName, ics) = result
                                pendingIcs = ics
                                createIcsLauncher.launch(fileName)
                            }
                        }
                    },
                )
            }
        }
    }

    if (showCountdownSheet) {
        CountdownManagerSheet(
            items = countdowns,
            onSave = { item -> scope.launch { AppGraph.countdownRepository.save(item) } },
            onDelete = { id -> scope.launch { AppGraph.countdownRepository.delete(id) } },
            onTogglePin = { item -> scope.launch { AppGraph.countdownRepository.togglePinned(item) } },
            onDismiss = { showCountdownSheet = false },
        )
    }
}

private fun formatCampusNum(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else String.format(Locale.US, "%.2f", v)
