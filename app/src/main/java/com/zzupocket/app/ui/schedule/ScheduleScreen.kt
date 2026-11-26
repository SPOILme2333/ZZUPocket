package com.zzupocket.app.ui.schedule

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import com.zzupocket.app.ui.components.GlassCard
import com.zzupocket.app.ui.theme.rememberEffectiveDark
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zzupocket.app.core.AppGraph
import com.zzupocket.app.core.CourseShortener
import com.zzupocket.app.core.ScheduleText
import com.zzupocket.app.core.WeekMath
import com.zzupocket.app.data.gms.GmsScheduleSource
import com.zzupocket.app.data.prefs.BlockBorder
import com.zzupocket.app.data.prefs.CourseColorMode
import com.zzupocket.app.data.prefs.CourseTextMode
import com.zzupocket.app.data.prefs.GridDensity
import com.zzupocket.app.data.prefs.UiPrefs
import com.zzupocket.app.data.repo.ScheduleRepository
import com.zzupocket.app.domain.Course
import com.zzupocket.app.domain.ConflictDisplayChoice
import com.zzupocket.app.domain.ScheduleBlock
import com.zzupocket.app.domain.scheduleBlocks
import com.zzupocket.app.domain.Period
import com.zzupocket.app.domain.Phase
import com.zzupocket.app.domain.Semester
import com.zzupocket.app.domain.Session
import com.zzupocket.app.ui.components.LiquidGlassIconButton
import com.zzupocket.app.ui.components.glassClickable
import com.zzupocket.app.ui.nav.DOCK_CLEARANCE
import com.zzupocket.app.ui.theme.tabularNumbers
import java.time.LocalDate
import java.time.LocalTime

private val CELL_HEIGHT = 54.dp
private val UNIT_COL_WIDTH = 56.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    onOpenLogin: () -> Unit,
    onOpenOverview: (Int) -> Unit,
    onOpenDisplaySettings: () -> Unit,
    vm: ScheduleViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val prefs by AppGraph.uiPrefsStore.flow.collectAsStateWithLifecycle(initialValue = AppGraph.uiPrefsStore.latest)
    var showSemesters by remember { mutableStateOf(false) }
    var showWeekPicker by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.onResume() }

    Scaffold(
        // 壁纸画在外壳层，这里必须透明，否则页面自己的底色会把壁纸盖掉
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                // 左上角不显示"课表"二字，直接放学期切换；研究生视图显示研究生学期名
                title = {
                    if (state.gradMode) {
                        val (year, term) = splitSemesterHeader(state.gmsSemester?.displayName ?: "研究生课表")
                        Column(verticalArrangement = Arrangement.spacedBy((-1).dp)) {
                            if (year != null) {
                                Text(
                                    text = year,
                                    style = MaterialTheme.typography.labelSmall.tabularNumbers(),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                            Text(
                                text = term,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    } else {
                        SemesterButton(state.selectedSemester) { showSemesters = true }
                    }
                },
                actions = {
                    Row(
                        modifier = Modifier.padding(end = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!state.gradMode) {
                            LiquidGlassIconButton(
                                icon = Icons.Filled.Add,
                                contentDescription = "添加日程",
                                onClick = { vm.openPlanEditor() },
                            )
                        }
                        LiquidGlassIconButton(
                            icon = Icons.Filled.Refresh,
                            contentDescription = "刷新课表",
                            onClick = { if (state.gradMode) vm.loadGmsWeek(force = true) else vm.refresh() },
                        )
                        // 顶栏只留一个设置入口：教学周、显示、背景、入学年、课程总览都在里面
                        LiquidGlassIconButton(
                            icon = Icons.Filled.Tune,
                            contentDescription = "课程表设置",
                            onClick = onOpenDisplaySettings,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // 研究生账号在本科教务里本来就没有角色：那条 401/403 说明对他是噪音，
            // 而且与下面的「研究生课表」区块自相矛盾（一边说没权限、一边请他登录研究生系统）。
            // 切到研究生视图后不再显示这条错误；研究生链路的错误由 grad 区块自己的 gmsError 负责。
            state.error?.takeIf { !state.gradMode }?.let { err ->
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            // 缓存提示：一行小灰字，点了重试。刻意不用红色 —— 红色错误会让人以为账号出问题
            // （历史 issue #2 反复重登就是这么来的）。刷新成功且内容没变时这里是 null，什么都不显示。
            // 后台同步进行中优先给"正在同步…"：缓存先行后没有整页转圈，
            // 不给轻反馈的话内容没变化时用户会以为"点刷新没反应"（2026-11-16 实测）。
            if (state.syncing) {
                Text(
                    text = "正在同步课表…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                )
            }
            state.cacheNotice?.let { msg ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (state.cacheNoticeNeedsLogin) {
                                if (state.gradMode) vm.openGmsLogin() else onOpenLogin()
                            } else if (state.gradMode) {
                                vm.loadGmsWeek(force = true)
                            } else {
                                vm.refresh()
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiary),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            state.notice?.let { msg ->
                Text(
                    text = "$msg（点一下收起）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vm.dismissNotice() }
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            // 补录保存/删除成功的一次性确认：与错误不同色（primary），桌面小组件在中途已无感刷新。
            state.planNotice?.let { msg ->
                Text(
                    text = "$msg（点一下收起）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vm.clearPlanNotice() }
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            when {
                // 研究生：整学期数据已经映射成本科的 Session/Course，直接复用同一套周网格。
                // 没有数据时才退回下面的登录/空态区块。
                state.gradMode && state.gmsSessions.isNotEmpty() -> {
                    // pager 状态提升：表头在滑动过程中实时跟随 currentPage，
                    // 落定后才写回 ViewModel（ WeekPager 内部仍只听 settledPage，防打架逻辑不变）。
                    // 修复「滑动时网格已到下一周、表头还停着旧周次」的慢一拍（2026-11-11 用户反馈）。
                    val gmsPager = rememberPagerState(
                        initialPage = state.gmsWeeks.indexOf(state.gmsWeekIndex).coerceAtLeast(0),
                        pageCount = { state.gmsWeeks.size },
                    )
                    WeekHeader(
                        week = if (gmsPager.isScrollInProgress) {
                            state.gmsWeeks.getOrNull(gmsPager.currentPage) ?: state.gmsWeekIndex
                        } else {
                            state.gmsWeekIndex
                        },
                        total = state.gmsWeekCount,
                        onPick = { showWeekPicker = true },
                        onWallpaper = prefs.backgroundPath != null,
                        confirmed = state.weekConfirmed,
                        onOpenSettings = onOpenDisplaySettings,
                    )
                    WeekPager(
                        pagerState = gmsPager,
                        weeks = state.gmsWeeks,
                        weekIndex = state.gmsWeekIndex,
                        sessionsOf = state::gmsSessionsOf,
                        periods = state.gmsPeriods,
                        weekMondayOf = { week ->
                            state.weekStart
                                ?.let { WeekMath.dateOf(it, week, 1, state.weekStartDay) }
                                ?: ScheduleText.mondayOf(state.gmsSemester?.startDate, week)
                        },
                        onWeekSelected = vm::goToGmsWeek,
                        prefs = prefs,
                        conflictChoices = state.conflictChoices,
                        scopeKey = state.gmsTermValue,
                        onBlockClick = vm::onBlockClick,
                    )
                }

                // gradMode 优先：从 0.31.0 覆盖升级的账号可能已有学期缓存，weeks 非空；
                // 只看 weeks 会让研究生区块和底部兜底入口都被本科周表格挡住。
                state.gradMode || state.weeks.isEmpty() -> NoSemesterBlock(
                    loggedIn = state.loggedIn,
                    loading = state.loading,
                    gradMode = state.gradMode,
                    gmsLessons = state.gmsLessons,
                    gmsBusy = state.gmsBusy,
                    gmsError = state.gmsError,
                    gmsDialogOpen = state.gmsDialogOpen,
                    onLogin = onOpenLogin,
                    onRefresh = vm::refresh,
                    onGmsLogin = vm::openGmsLogin,
                    onGmsRefresh = { vm.loadGmsWeek(force = true) },
                    onGmsDialogDismiss = vm::closeGmsLogin,
                    onGmsAdopt = vm::adoptGmsSession,
                    onGradFallback = vm::useGradSchedule,
                )

                else -> {
                    // 周次还没解析出来之前**不要挂翻页器**：它的初始页取自 state.weekIndex，
                    // 而 bootstrap 会先给 weeks（19 周）、后给真实周次，中间那一小段
                    // weekIndex 还是默认的 1 —— 于是翻页器从第 1 周挂载、再一路动画滚到本周，
                    // 动画每经过一页都会被写回 ViewModel（详见 WeekPager 的注释）。
                    // 等解析完再挂，初始页直接就是本周，没有"从第 1 周滚过来"这一出。
                    val weekReady = state.weekResolvedOn != null || !state.loading
                    // pager 状态提升：表头在滑动过程中实时跟随 currentPage，落定后才写回
                    // ViewModel（WeekPager 内部仍只听 settledPage，防打架逻辑不变）。
                    // 修复「滑动时网格已到下一周、表头还停着旧周次」的慢一拍（2026-11-11 用户反馈）。
                    val pagerState = if (weekReady) rememberPagerState(
                        initialPage = state.weeks.indexOf(state.weekIndex).coerceAtLeast(0),
                        pageCount = { state.weeks.size },
                    ) else null
                    WeekHeader(
                        week = if (pagerState != null && pagerState.isScrollInProgress) {
                            state.weeks.getOrNull(pagerState.currentPage) ?: state.weekIndex
                        } else {
                            state.weekIndex
                        },
                        total = state.weekCount,
                        onPick = { showWeekPicker = true },
                        onWallpaper = prefs.backgroundPath != null,
                        confirmed = state.weekConfirmed,
                        onOpenSettings = onOpenDisplaySettings,
                    )
                    if (pagerState != null) {
                        WeekPager(
                            pagerState = pagerState,
                            weeks = state.weeks,
                            weekIndex = state.weekIndex,
                            sessionsOf = state::sessionsOf,
                            periods = state.periods,
                            weekMondayOf = { week ->
                                state.weekStart
                                    ?.let { WeekMath.dateOf(it, week, 1, state.weekStartDay) }
                                    ?: ScheduleText.mondayOf(state.selectedSemester?.startDate, week)
                            },
                            onWeekSelected = vm::goToWeek,
                            prefs = prefs,
                            // 点课程块 → 教务课开详情，自定义日程开编辑框
                            conflictChoices = state.conflictChoices,
                            onBlockClick = vm::onBlockClick,
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }

    if (showSemesters) {
        SemesterSheet(
            semesters = state.semesters,
            selectedId = state.selectedSemester?.id,
            onPick = { vm.selectSemester(it); showSemesters = false },
            onDismiss = { showSemesters = false },
        )
    }

    if (showWeekPicker) {
        WeekPickerDialog(
            weeks = if (state.gradMode) state.gmsWeeks else state.weeks,
            current = if (state.gradMode) state.gmsWeekIndex else state.weekIndex,
            onPick = {
                if (state.gradMode) vm.goToGmsWeek(it) else vm.goToWeek(it)
                showWeekPicker = false
            },
            onDismiss = { showWeekPicker = false },
        )
    }

    state.detailCourse?.let { course ->
        CourseDetailSheet(
            course = course,
            onDismiss = vm::closeDetail,
            overrides = state.detailOverrides,
            onApplyEdits = vm::applyDisplayEdits,
            onResetOverrides = vm::resetDisplayOverrides,
            beforeDetails = {
                state.detailConflict?.let { conflict ->
                    ConflictDisplaySection(
                        detail = conflict,
                        saving = state.conflictSaving,
                        error = state.conflictError,
                        notice = state.conflictNotice,
                        onCourseClick = vm::selectConflictCourse,
                        onSave = vm::saveConflictDisplay,
                    )
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                }
            },
        )
    }

    if (state.planEditorOpen) {
        PlanEditDialog(
            initial = state.editingPlan,
            semesterId = state.selectedSemester?.id ?: 0,
            availableWeeks = state.weeks.ifEmpty { (1..18).toList() },
            maxUnit = state.periods.size.coerceAtLeast(10),
            canStartFocus = true,
            currentWeek = state.weekIndex,
            onDismiss = vm::closePlanEditor,
            onSave = vm::savePlan,
            onDelete = { plan -> vm.deletePlan(plan.code) },
            onStartFocus = { plan ->
                vm.closePlanEditor()
                startFocusFor(name = plan.name, contextRef = "plan:${plan.code}")
            },
        )
    }

    if (state.pendingPlan != null) {
        PlanConflictDialog(
            conflicts = state.pendingConflicts,
            onConfirm = vm::confirmPendingPlan,
            onDismiss = vm::dismissPendingPlan,
        )
    }
}

/**
 * 从课表跳到番茄钟：标签自动用课名/日程名，并记下关联对象。
 * 这样统计里能算出"这门课实际专注了多久"。
 */
private fun startFocusFor(name: String, contextRef: String) {
    AppGraph.pomodoroEngine.start(
        phase = Phase.FOCUS,
        tagId = null,
        tagName = name,
        contextRef = contextRef,
    )
}

/** 将「2026-2027 第一学期」拆分为上行小字学年与下行大字学期名，避免窄屏被省略号截断。 */
internal fun splitSemesterHeader(displayName: String): Pair<String?, String> {
    val trimmed = displayName.trim()
    val spaceMatch = Regex("^(\\d{4}-\\d{4})\\s+(.+)$").find(trimmed)
    if (spaceMatch != null) {
        return spaceMatch.groupValues[1] to spaceMatch.groupValues[2]
    }
    val dashMatch = Regex("^(\\d{4}-\\d{4})-(\\d+)$").find(trimmed)
    if (dashMatch != null) {
        val term = when (dashMatch.groupValues[2]) {
            "1" -> "第一学期"
            "2" -> "第二学期"
            "3" -> "第三学期"
            else -> "第${dashMatch.groupValues[2]}学期"
        }
        return dashMatch.groupValues[1] to term
    }
    return null to trimmed
}

@Composable
private fun SemesterButton(semester: Semester?, onClick: () -> Unit) {
    val (year, term) = splitSemesterHeader(semester?.displayName ?: "选择学期")
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .glassClickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy((-1).dp),
    ) {
        if (year != null) {
            Text(
                text = year,
                style = MaterialTheme.typography.labelSmall.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = term,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
            )
            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = "切换学期",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun WeekHeader(
    week: Int,
    total: Int,
    onPick: () -> Unit,
    onWallpaper: Boolean = false,
    /** 周次参数是否已确认；false 时表头下面给一行小灰字提醒（不抢眼，但看得见） */
    confirmed: Boolean = true,
    onOpenSettings: (() -> Unit)? = null,
) {
    val shadow = wallpaperShadow(onWallpaper, MaterialTheme.colorScheme.surface)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onPick),
        ) {
            Text(
                text = "第 $week 周",
                style = MaterialTheme.typography.titleMedium.copy(shadow = shadow),
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = " / $total",
                style = MaterialTheme.typography.bodyMedium.copy(shadow = shadow),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!confirmed && onOpenSettings != null) {
            // 只在"周次是自动猜的、还没确认"时出现，确认后永久消失。
            // 用一行小灰字而不是按钮：它是个提醒，不该抢周次的视觉重量。
            Text(
                text = "周次为自动推断，可在课程表设置里确认",
                style = MaterialTheme.typography.labelSmall.copy(shadow = shadow),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable(onClick = onOpenSettings)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

/** 壁纸开着时给直接压在照片上的字加一层极轻投影 —— 比给文字垫实色块自然得多 */
private fun wallpaperShadow(enabled: Boolean, surface: Color): Shadow? = if (enabled) {
    Shadow(
        color = surface.copy(alpha = 0.7f),
        offset = Offset(0f, 1.5f),
        blurRadius = 4f,
    )
} else {
    null
}

/** 星期表头行的高度：周几（约 16dp）+ 日期小字（约 12dp）+ 一点余量 */
private val WEEKDAY_HEADER_HEIGHT = 38.dp

@Composable
private fun WeekPager(
    pagerState: PagerState,
    weeks: List<Int>,
    weekIndex: Int,
    sessionsOf: (Int) -> List<Session>,
    periods: List<Period>,
    weekMondayOf: (Int) -> LocalDate?,
    onWeekSelected: (Int) -> Unit,
    prefs: UiPrefs,
    conflictChoices: List<ConflictDisplayChoice>,
    scopeKey: String? = null,
    onBlockClick: (ScheduleBlock) -> Unit,
) {

    // ① 用户滑动 → 通知 ViewModel。
    //
    // ⚠️ 两个要点，都是真机上踩出来的（课表页停在上一周）：
    //  · 只听 **settledPage**（松手落定后的页），不听 currentPage：后者在动画过程中
    //    会逐页变化，等于把动画的中间态写回 ViewModel；
    //  · **程序化滚动期间（isScrollInProgress）一律不写**：否则"滚到本周"的动画
    //    每经过一页就改一次 weekIndex，而 weekIndex 一变又会取消正在跑的动画
    //    （下面 ② 重新执行）—— 两个 effect 互相驱动，最后稳定停在中途某一周。
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage to pagerState.isScrollInProgress }
            .collect { (page, scrolling) ->
                if (scrolling) return@collect
                weeks.getOrNull(page)?.let { if (it != weekIndex) onWeekSelected(it) }
            }
    }
    // ② ViewModel 变化（点周次选择、隔天自动回到本周）→ 滚过去。
    //    这是单向的：①不会回写中间态，所以不会再打架。
    LaunchedEffect(weekIndex, weeks) {
        val idx = weeks.indexOf(weekIndex)
        if (idx >= 0 && idx != pagerState.currentPage) pagerState.animateScrollToPage(idx)
    }

    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
        val week = weeks.getOrNull(page)
        if (week != null) {
            ScheduleGrid(
                sessions = sessionsOf(week),
                periods = periods,
                prefs = prefs,
                // 星期表头下面那行日期要用它：算不出来（没学期/没起始日）就不显示日期
                weekMonday = weekMondayOf(week),
                conflictChoices = conflictChoices,
                scopeKey = scopeKey,
                onBlockClick = onBlockClick,
            )
        }
    }
}

@Composable
internal fun ScheduleGrid(
    sessions: List<Session>,
    periods: List<Period>,
    prefs: UiPrefs,
    /** 这一周周一的日期；null = 算不出来（没选学期/没同步），表头就只显示周几 */
    weekMonday: java.time.LocalDate?,
    conflictChoices: List<ConflictDisplayChoice>,
    scopeKey: String? = null,
    onBlockClick: (ScheduleBlock) -> Unit,
) {
    val days = 1..prefs.dayCount
    val cellH = prefs.cellHeightDp.dp
    val gap = if (prefs.blockBorder == BlockBorder.NONE) 0.75.dp else 1.dp
    // 课程块明度必须跟「生效深浅」同口径（外观页可以强制深/浅，与系统分叉）；
    // 用系统档会把块底和块上文字带成两套配色，互相不可读。
    val dark = rememberEffectiveDark(prefs.darkMode)
    val todayIso = LocalDate.now().toString()
    val today = LocalDate.now()
    val now = LocalTime.now()
    // 有壁纸时，星期表头和节次时间这些直接压在照片上的小字加一层轻投影
    val labelShadow = wallpaperShadow(
        prefs.backgroundPath != null,
        MaterialTheme.colorScheme.surface,
    )
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val compactWidth = LocalConfiguration.current.screenWidthDp < 360
    val unitColWidth = if (compactWidth) 42.dp else 52.dp

    // ⚠️ 表头**不放在可滚动区里**：往下翻课表时"周几 + 日期"要一直看得见
    // （用户要求固定住；它只是一行普通 Row，不吃横向拖动，所以左右翻周的手感不受影响）。
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(WEEKDAY_HEADER_HEIGHT)) {
            Spacer(Modifier.width(unitColWidth))
            for (d in days) {
                val date = weekMonday?.plusDays((d - 1).toLong())
                val isToday = date != null && date == today
                val todayPillMod = if (isToday) {
                    Modifier
                        .padding(horizontal = 4.dp, vertical = 3.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary)
                } else {
                    Modifier
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .then(todayPillMod),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = ScheduleText.weekdayName(d).removePrefix("周"),
                        style = MaterialTheme.typography.labelMedium.copy(shadow = labelShadow),
                        color = if (isToday) MaterialTheme.colorScheme.onPrimary else labelColor,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    )
                    // 日期：10-01 这种写法（带月份、补零）。今天那一列用主色块 + 白字标出来
                    if (date != null) {
                        Text(
                            text = ScheduleText.dayLabel(date),
                            fontSize = 9.sp,
                            lineHeight = 11.sp,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isToday) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                labelColor
                            },
                            style = LocalTextStyle.current.copy(shadow = labelShadow),
                        )
                    }
                }
            }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            val gridLine = if (dark) {
                Color.White.copy(alpha = 0.055f)
            } else {
                Color(0xFF0F172A).copy(alpha = 0.045f)
            }
            Row(
                modifier = Modifier.fillMaxWidth().drawBehind {
                    val unitW = unitColWidth.toPx()
                    val rowH = cellH.toPx()
                    val gapPx = gap.toPx()
                    val stroke = 0.55.dp.toPx()
                    // 极轻柔的玻璃刻痕线：横竖分隔格线
                    for (i in 0 until 10) {
                        val y = (rowH + gapPx) * i
                        drawLine(gridLine, Offset(0f, y), Offset(size.width, y), stroke)
                    }
                    drawLine(
                        gridLine,
                        Offset(0f, size.height),
                        Offset(size.width, size.height),
                        stroke,
                    )
                    val dayCount = days.count()
                    val dayW = (size.width - unitW) / dayCount
                    for (i in 0..dayCount) {
                        val x = unitW + dayW * i
                        drawLine(gridLine, Offset(x, 0f), Offset(x, size.height), stroke)
                    }
                },
            ) {
                // 左侧节次列：固定 10 格，与右侧各天严格对齐
                Column(
                    modifier = Modifier.width(unitColWidth),
                    verticalArrangement = Arrangement.spacedBy(gap),
                ) {
                    for (unit in 1..10) {
                        Column(
                            modifier = Modifier.fillMaxWidth().height(cellH),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = "$unit",
                                style = MaterialTheme.typography.labelMedium.copy(shadow = labelShadow),
                            )
                            periods.firstOrNull { it.indexNo == unit }?.let {
                                if (compactWidth) {
                                    Text(
                                        text = it.startText,
                                        fontSize = 7.5.sp,
                                        lineHeight = 8.5.sp,
                                        maxLines = 1,
                                        softWrap = false,
                                        color = labelColor,
                                        style = LocalTextStyle.current.copy(shadow = labelShadow),
                                    )
                                    Text(
                                        text = it.endText,
                                        fontSize = 7.5.sp,
                                        lineHeight = 8.5.sp,
                                        maxLines = 1,
                                        softWrap = false,
                                        color = labelColor,
                                        style = LocalTextStyle.current.copy(shadow = labelShadow),
                                    )
                                } else {
                                    // 起止放在同一行，避免行高不够时和时间数字叠在一起
                                    Text(
                                        text = "${it.startText}-${it.endText}",
                                        fontSize = 7.sp,
                                        lineHeight = 9.sp,
                                        maxLines = 1,
                                        softWrap = false,
                                        color = labelColor,
                                        style = LocalTextStyle.current.copy(shadow = labelShadow),
                                    )
                                }
                            }
                        }
                    }
                }

                // 每天一列 —— 只有按列排布才可能把跨节次的课画成一个连续的整块
                for (day in days) {
                    DayColumn(
                        modifier = Modifier.weight(1f),
                        day = day,
                        sessions = sessions,
                        periods = periods,
                        prefs = prefs,
                        cellH = cellH,
                        gap = gap,
                        dark = dark,
                        todayIso = todayIso,
                        now = now,
                        conflictChoices = conflictChoices,
                        scopeKey = scopeKey,
                        compactWidth = compactWidth,
                        onBlockClick = onBlockClick,
                    )
                }
            }
        Spacer(Modifier.height(DOCK_CLEARANCE))
        } // 关闭可滚动区（表头在上面，不跟着滚）
    } // 关闭外层 Column
} // 关闭 ScheduleGrid

/**
 * 一天的列。
 *
 * 按实际占用集合切段；普通课程仍合并成整块，重叠部分显示冲突占位或本地选择。
 * 块高仍为节数 × 行高 + 内部间隙，各天与左侧节次列严格对齐。
 */
@Composable
private fun DayColumn(
    modifier: Modifier,
    day: Int,
    sessions: List<Session>,
    periods: List<Period>,
    prefs: UiPrefs,
    cellH: Dp,
    gap: Dp,
    dark: Boolean,
    todayIso: String,
    now: LocalTime,
    conflictChoices: List<ConflictDisplayChoice>,
    scopeKey: String? = null,
    compactWidth: Boolean = false,
    onBlockClick: (ScheduleBlock) -> Unit,
) {
    val corner = RoundedCornerShape(8.dp)
    val blocks = remember(sessions, day, conflictChoices, scopeKey) {
        scheduleBlocks(sessions.filter { it.weekday == day }, conflictChoices, scopeKey = scopeKey).associateBy { it.startUnit }
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        var unit = 1
        while (unit <= 10) {
            val block = blocks[unit]
            if (block == null) {
                Box(Modifier.fillMaxWidth().height(cellH))
                unit += 1
                continue
            }
            val session = block.displaySession
            val span = block.endUnit - block.startUnit + 1
            CourseBlock(
                session = session,
                span = span,
                height = cellH * span + gap * (span - 1),
                prefs = prefs,
                corner = corner,
                periods = periods,
                dark = dark,
                live = prefs.liveOutlineEnabled &&
                    (if (block.showPlaceholder) block.sessions else listOf(session)).any {
                        isLiveNow(it.copy(startUnit = block.startUnit, endUnit = block.endUnit), periods, todayIso, now)
                    },
                conflict = block.takeIf { it.isConflict },
                compactWidth = compactWidth,
                onClick = { onBlockClick(block) },
            )
            unit += span
        }
    }
}

@Composable
private fun CourseBlock(
    session: Session,
    span: Int,
    height: Dp,
    prefs: UiPrefs,
    corner: Shape,
    periods: List<Period>,
    dark: Boolean,
    live: Boolean,
    conflict: ScheduleBlock? = null,
    compactWidth: Boolean = false,
    onClick: (Session) -> Unit,
) {
    val placeholder = conflict?.showPlaceholder == true
    val accent = when {
        placeholder -> MaterialTheme.colorScheme.error
        session.source == ScheduleRepository.SOURCE_CUSTOM ->
            planBlockColor(session.color, dark = false)
        prefs.colorMode == CourseColorMode.SINGLE -> MaterialTheme.colorScheme.primary
        else -> courseColor(session.courseCode, dark)
    }

    // 克制低彩纸釉课程块材质：去除刺眼渐变高光与白色边缘，确保深浅模式下均沉稳易读
    val baseSurface = remember(accent, dark, placeholder) {
        if (dark) {
            lerp(Color(0xFF151C1B), accent, if (placeholder) 0.34f else 0.30f).copy(alpha = 0.86f)
        } else {
            lerp(Color(0xFFF7F8F6), accent, if (placeholder) 0.22f else 0.17f).copy(alpha = 0.90f)
        }
    }
    val titleColor = remember(accent, dark) {
        if (dark) lerp(accent, Color.White, 0.86f) else lerp(accent, Color(0xFF121716), 0.58f)
    }
    val roomColor = remember(titleColor) {
        titleColor.copy(alpha = 0.70f)
    }

    val tertiary = MaterialTheme.colorScheme.tertiary
    val errorColor = MaterialTheme.colorScheme.error
    val decoration = when {
        placeholder -> Modifier.border(
            BorderStroke(
                1.4.dp,
                errorColor.copy(alpha = 0.75f),
            ),
            corner,
        )
        live -> Modifier.border(
            BorderStroke(
                1.5.dp,
                tertiary.copy(alpha = 0.90f),
            ),
            corner,
        )
        prefs.blockBorder == BlockBorder.SOLID -> Modifier.border(
            BorderStroke(
                0.6.dp,
                accent.copy(alpha = if (dark) 0.30f else 0.22f),
            ),
            corner,
        )
        prefs.blockBorder == BlockBorder.DASHED ->
            Modifier.dashedBorder(accent.copy(alpha = 0.35f))
        else -> Modifier
    }
    val hasCornerBadge = !placeholder && (session.experiment || conflict != null)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = if (compactWidth) 1.5.dp else 2.dp, vertical = 1.5.dp)
            .clip(corner)
            .background(baseSurface)
            .then(decoration)
            .then(if (conflict != null) Modifier.semantics(mergeDescendants = true) {
                contentDescription = "课程冲突：" + conflict.sessions.joinToString("、") { it.courseName } +
                    if (placeholder) "，点击查看冲突详情" else "，当前显示${session.courseName}，点击更改显示"
            } else Modifier)
            .clickable { onClick(session) },
    ) {
        // 实验/见习标记：右上角纯文字微标，去掉实心或胶囊底色
        if (hasCornerBadge) {
            Text(
                text = listOfNotNull(if (session.experiment) "实" else null, if (conflict != null) "冲" else null).joinToString("·"),
                fontSize = 8.sp,
                lineHeight = 9.sp,
                fontWeight = FontWeight.Bold,
                color = roomColor,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 4.dp),
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(
                    start = 2.dp,
                    end = 2.dp,
                    top = if (hasCornerBadge) 12.dp else 2.dp,
                    bottom = 2.dp,
                )
                .align(Alignment.Center),
        ) {
            val shownName = CourseShortener.shorten(session.courseName, prefs.smartShortCourseName)
            val compactFourCharAdjust = if (prefs.dayCount >= 7 && shownName.length == 4 && shownName.none { it.isWhitespace() }) {
                -0.8f
            } else {
                0f
            }
            val nameFont = (prefs.courseFontSp + ScheduleText.fontAdjust(shownName) + compactFourCharAdjust)
                .coerceAtLeast(prefs.courseFontSp - 2.2f)
            if (placeholder) {
                ConflictPlaceholderTitle(prefs)
                ConflictCourseNames(conflict!!.sessions, prefs, span)
            } else Text(
                text = shownName,
                fontSize = nameFont.sp,
                lineHeight = (nameFont + 2).sp,
                maxLines = if (span >= 2) 4 else 3,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold,
                color = titleColor,
            )
            val secondary = buildList {
                if (session.source == GmsScheduleSource.SOURCE && span >= 2) {
                    session.teacherName?.takeIf { it.isNotBlank() }
                        ?.let { add(SlotText.Kind.TEACHER to it) }
                }
                if (prefs.courseTextMode != CourseTextMode.NAME) {
                    add(SlotText.Kind.ROOM to ScheduleText.tidyPlace(session.roomText))
                }
            }
            if (!placeholder) secondary.forEachIndexed { index, (kind, line) ->
                if (line == null) return@forEachIndexed
                val text = if (kind == SlotText.Kind.ROOM) {
                    ScheduleText.abbreviatePlace(
                        line,
                        maxWeighted = if (span >= 2) 26 else 12,
                    ) ?: line
                } else {
                    line
                }
                val roomFont = if (kind == SlotText.Kind.ROOM) {
                    (prefs.roomFontSp + ScheduleText.fontAdjust(text))
                        .coerceAtLeast(prefs.roomFontSp - 1)
                } else {
                    prefs.roomFontSp
                }
                val maxLines = when {
                    kind != SlotText.Kind.ROOM -> 1
                    span >= 2 -> 3
                    else -> 1
                }
                Text(
                    text = text,
                    fontSize = roomFont.sp,
                    lineHeight = (roomFont + 1.5f).sp,
                    maxLines = maxLines,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    color = roomColor,
                )
            }
        }
    }
}

/** 副行的种类：教师 / 地点（地点才享受换行与缩写待遇） */
private object SlotText {
    enum class Kind { TEACHER, ROOM }
}

/** 这节课此刻正在上 —— 供「正在上的课」描边使用。按真实日期判断，调休也正确。 */
private fun isLiveNow(
    s: Session,
    periods: List<Period>,
    todayIso: String,
    now: LocalTime,
): Boolean {
    if (s.date != todayIso) return false
    val startText = periods.firstOrNull { it.indexNo == s.startUnit }?.startText ?: s.startTime ?: return false
    val endText = periods.firstOrNull { it.indexNo == s.endUnit }?.endText ?: s.endTime ?: return false
    val start = runCatching { LocalTime.parse(startText) }.getOrNull() ?: return false
    val end = runCatching { LocalTime.parse(endText) }.getOrNull() ?: return false
    return !now.isBefore(start) && !now.isAfter(end)
}

private val CURATED_COURSE_ACCENTS = listOf(
    Color(0xFF4A6FA5), // 黛蓝
    Color(0xFF3E8C84), // 松石
    Color(0xFF6B8F4E), // 苔绿
    Color(0xFFB88A2E), // 芥黄
    Color(0xFFC0703F), // 赭橙
    Color(0xFF9A3B47), // 枣绯
    Color(0xFF8A6A9E), // 藕紫
    Color(0xFF5B88A8), // 雾蓝
    Color(0xFF5F7C7A), // 岩青
    Color(0xFF8C6F56), // 茶褐
)

/** 按课程代码从精选琉璃色板稳定取色 */
private fun courseColor(code: String, dark: Boolean): Color {
    val idx = ((code.hashCode() % CURATED_COURSE_ACCENTS.size) + CURATED_COURSE_ACCENTS.size) %
        CURATED_COURSE_ACCENTS.size
    return CURATED_COURSE_ACCENTS[idx]
}

/**
 * 圆角虚线描边。
 *
 * Compose 没有内置虚线组件，用 drawBehind + dashPathEffect 自己画。
 * 虚线用来把相邻、颜色接近的课程块分开，避免连成一片看不出边界。
 */
private fun Modifier.dashedBorder(
    color: Color,
    dash: Dp = 4.dp,
    gap: Dp = 3.dp,
    strokeWidth: Dp = 1.dp,
    radius: Dp = 8.dp,
): Modifier = drawBehind {
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(radius.toPx(), radius.toPx()),
        style = Stroke(
            width = strokeWidth.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.toPx(), gap.toPx())),
        ),
    )
}

@Composable
private fun NoSemesterBlock(
    loggedIn: Boolean,
    loading: Boolean,
    gradMode: Boolean,
    gmsLessons: List<com.zzupocket.app.data.gms.GradLesson>,
    gmsBusy: Boolean,
    gmsError: String?,
    gmsDialogOpen: Boolean,
    onLogin: () -> Unit,
    onRefresh: () -> Unit,
    onGmsLogin: () -> Unit,
    onGmsRefresh: () -> Unit,
    onGmsDialogDismiss: () -> Unit,
    onGmsAdopt: () -> Unit,
    onGradFallback: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when {
            // 研究生：本科教务对这个账号没有角色，课表在研究生院那套系统里。
            // 这里显示的是「本周课表」—— 那个接口由服务端按当前周返回，所以不需要日期。
            gradMode -> {
                Text("研究生课表", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "你的账号在本科教务里没有课表权限 —— 研究生课表在研究生院系统里。" +
                        "登录用的是学校统一身份认证，密码输在学校自己的页面上。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                when {
                    gmsBusy -> CircularProgressIndicator(modifier = Modifier.size(26.dp))
                    gmsLessons.isEmpty() -> Button(onClick = onGmsLogin) { Text("登录研究生系统") }
                    else -> {
                        gmsLessons
                            .sortedWith(compareBy({ it.weekday }, { it.period }))
                            .forEach { l ->
                                Text(
                                    "周${weekdayZh(l.weekday)} · 第${l.period}节 · ${l.courseName}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onClick = onGmsRefresh) { Text("刷新本周课表") }
                    }
                }
                if (gmsError != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        gmsError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (gmsDialogOpen) {
                    com.zzupocket.app.ui.diag.GmsProbeDialog(
                        onDismiss = onGmsDialogDismiss,
                        onReport = { onGmsAdopt() },
                        loginMode = true,
                    )
                }
            }

            loading -> {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                Spacer(Modifier.height(12.dp))
                Text("正在获取课表…", style = MaterialTheme.typography.bodyMedium)
            }

            !loggedIn -> {
                Text("登录后查看课表", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onLogin) {
                    Icon(Icons.Filled.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("登录")
                }
            }

            else -> {
                Text("暂时没有课表数据", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onRefresh) { Text("重新获取") }
            }
        }

        // 兜底入口：学籍探测判不出来（校外网络、接口异常等）时，这里是他唯一的出路。
        // 做成次要的文字按钮放在最下面：本科生课表正常时压根到不了这个页面，不会被打扰。
        if (!gradMode) {
            Spacer(Modifier.height(22.dp))
            TextButton(onClick = onGradFallback) {
                Text("我是研究生，课表在研究生院系统")
            }
        }
    }
}

/** 1..7 → 一..日 */
private fun weekdayZh(d: Int): String = when (d) {
    1 -> "一"
    2 -> "二"
    3 -> "三"
    4 -> "四"
    5 -> "五"
    6 -> "六"
    else -> "日"
}
