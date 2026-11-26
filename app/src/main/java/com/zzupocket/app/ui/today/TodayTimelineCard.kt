package com.zzupocket.app.ui.today

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zzupocket.app.core.AppGraph
import com.zzupocket.app.core.ScheduleText
import com.zzupocket.app.data.repo.ScheduleRepository
import com.zzupocket.app.data.repo.TodayTimetable
import com.zzupocket.app.domain.FocusStats
import com.zzupocket.app.domain.Phase
import com.zzupocket.app.domain.PlanColor
import com.zzupocket.app.domain.Session
import com.zzupocket.app.ui.components.GlassCard
import com.zzupocket.app.ui.components.glassClickable
import com.zzupocket.app.ui.theme.tabularNumbers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.Clock
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 「今天」时间轴的界面状态 */
data class TodayTimelineUi(
    val loaded: Boolean = false,
    /** 当前是学期第几周（拿不到就是 null） */
    val week: Int? = null,
    val dateText: String = "",
    val entries: List<TimelineEntry> = emptyList(),
    /** 现在时刻（当天第几分钟），用来画"现在"那条线、并高亮进行中的一段 */
    val nowMin: Int = 0,
    val courseCount: Int = 0,
    /** 今天累计专注秒数（只算 FOCUS 段） */
    val focusSeconds: Int = 0,
    /** 今天完成的番茄数 */
    val focusCount: Int = 0,
    /** "下一节 14:00《…》· 40 分钟后" 这类提示 */
    val nextHint: String? = null,
    /** 今天一节课也没有时："明天 08:00《…》" —— 空态也能回答"那我下节课是啥" */
    val tomorrowHint: String? = null,
    /** 今天课全结束后，横线下方用同款行样式展示的明天第一节课 */
    val tomorrowFirst: TimelineEntry? = null,
    /** 今天**自己加的**日程（课表里的自定义日程），单独列一张卡，不和教务课程混在一起 */
    val customEntries: List<TimelineEntry> = emptyList(),
)

class TodayTimelineViewModel(
    private val scheduleRepo: ScheduleRepository = AppGraph.scheduleRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    private val _state = MutableStateFlow(TodayTimelineUi())
    val state: StateFlow<TodayTimelineUi> = _state.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    /** 从课表/专注页切回来时调用 —— 时间轴是会变的 */
    fun reload() {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val now = clock.millis()
        val nowMin = LocalTime.now(clock).let { it.hour * 60 + it.minute }
        val today = LocalDate.now(clock)

        // 本科 + 研究生统一口径：研究生账号没有学期行，也要能看到今天的课
        val sessions = runCatching {
            com.zzupocket.app.data.repo.TodayTimetable
                .sessionsOn(scheduleRepo, AppGraph.uiPrefsStore, today)
        }.getOrDefault(emptyList())
        val periods = scheduleRepo.periods()

        // 今天（按 FocusStats 的口径：凌晨 4 点起算）的专注记录
        val dayStart = FocusStats.dayStart(FocusStats.dayKey(now))
        val records = runCatching { AppGraph.focusRepository.recordsSince(dayStart) }
            .getOrDefault(emptyList())
            .filter { it.phase == Phase.FOCUS }

        // 每门课今天已经专注了多少（番茄钟计时时会把课程写进 contextRef）
        val focusByRef = records.filter { it.contextRef.isNotBlank() }
            .groupBy { it.contextRef }
            .mapValues { (_, list) -> list.sumOf { it.actualSeconds } }

        val uiPrefs = runCatching { AppGraph.uiPrefsStore.flow.first() }.getOrNull()
        val shortenNames = uiPrefs?.smartShortCourseName ?: false
        val courseEntries = TodayTimeline.courseEntries(
            blocks = TodayTimetable.displayBlocks(scheduleRepo, sessions),
            periods = periods,
            shortenNames = shortenNames,
        )
        val customEntries = sessions
            .filter { it.source == ScheduleRepository.SOURCE_CUSTOM }
            .mapNotNull { session -> courseEntry(session, periods, null, focusByRef) }
            .sortedBy { it.startMin }
        val focusEntries = records
            .map { record -> focusEntry(record) }
            .filter { it.endMin > it.startMin }
            .sortedBy { it.startMin }

        // 明天的课概览：没课的空态给"明天第一节"，有课的日子给"明天 N 节"脚注
        val tomorrowEntries = runCatching {
            val tomorrow = TodayTimetable.sessionsOn(scheduleRepo, AppGraph.uiPrefsStore, today.plusDays(1))
            TodayTimeline.courseEntries(
                blocks = TodayTimetable.displayBlocks(scheduleRepo, tomorrow),
                periods = periods,
                shortenNames = shortenNames,
            )
        }.getOrDefault(emptyList())
        val tomorrowHint = if (courseEntries.isEmpty()) {
            tomorrowEntries.firstOrNull()
                ?.let { "明天 ${TodayTimeline.clock(it.startMin)}《${it.title}》" }
        } else {
            null
        }
        // 课上完之后才预告明天第一节；"今天没课"归空态提示管，别两处都说一遍
        val tomorrowFirst = if (TodayTimeline.allCoursesFinished(courseEntries, nowMin)) {
            tomorrowEntries.firstOrNull()
        } else {
            null
        }

        _state.value = TodayTimelineUi(
            loaded = true,
            week = com.zzupocket.app.data.repo.TodayTimetable.currentWeekAt(scheduleRepo, AppGraph.uiPrefsStore, today),
            dateText = DATE_FMT.format(today),
            // 只画**真实发生的事**：课 / 自己加的日程 / 已完成的专注。
            //
            // 课与课之间的"空档"不再画（用户明确说「空档 30 分钟 / 可以塞一个专注」这类
            // 提示是噪音，今天页只该显示课和日程）。空档本身仍在 TodayTimeline.build 里算、
            // 也仍有单测 —— 以后要做"课间可用时间"直接取用即可，这里只是不渲染。
            entries = TodayTimeline.build(courseEntries, focusEntries)
                .filter { it.kind != TimelineKind.GAP },
            nowMin = nowMin,
            courseCount = sessions.size,
            focusSeconds = records.sumOf { it.actualSeconds },
            focusCount = records.count { it.completed },
            nextHint = TodayTimeline.nextCourseHint(courseEntries, nowMin),
            tomorrowHint = tomorrowHint,
            tomorrowFirst = tomorrowFirst,
            customEntries = customEntries,
        )
    }

    private fun courseEntry(
        session: Session,
        periods: List<com.zzupocket.app.domain.Period>,
        semesterId: Int?,
        focusByRef: Map<String, Int>,
    ): TimelineEntry? {
        val startPeriod = periods.firstOrNull { it.indexNo == session.startUnit }
        val endPeriod = periods.firstOrNull { it.indexNo == session.endUnit }
        val start = TodayTimeline.minuteOfDay(startPeriod?.startText ?: session.startTime) ?: return null
        val end = TodayTimeline.minuteOfDay(endPeriod?.endText ?: session.endTime) ?: (start + 45)
        val ref = semesterId?.let {
            "course:${ScheduleRepository.courseKeyOf(it, session.courseCode)}"
        }
        return TimelineEntry(
            kind = TimelineKind.COURSE,
            startMin = start,
            endMin = end,
            title = session.courseName,
            // 老师不进时间轴（点开课程详情能看到）；地点统一规整成"南2 206"
            subtitle = ScheduleText.tidyPlace(session.roomText ?: session.campusText).orEmpty(),
            colorArgb = PlanColor.of(session.color)?.argb,
            focusSeconds = ref?.let { focusByRef[it] } ?: 0,
        )
    }

    private fun focusEntry(record: com.zzupocket.app.domain.FocusRecord): TimelineEntry {
        val start = TodayTimeline.minuteOfEpoch(record.startedAt)
        val rawEnd = TodayTimeline.minuteOfEpoch(maxOf(record.endedAt, record.startedAt))
        // 跨零点（凌晨那一段）时把终点夹到当天末尾，避免出现负数长度
        val end = if (rawEnd <= start) minOf(1440, start + (record.actualSeconds / 60).coerceAtLeast(1)) else rawEnd
        return TimelineEntry(
            kind = TimelineKind.FOCUS,
            startMin = start,
            endMin = end,
            title = record.tagName.ifBlank { "专注" },
            subtitle = FocusStats.humanDuration(record.actualSeconds) +
                if (record.completed) "" else " · 中断",
        )
    }

    private companion object {
        val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("M 月 d 日 EEEE", Locale.CHINA)
    }
}

/**
 * 「今天」页的时间轴卡片：把**上课 / 已完成的专注**画在同一根轴上。点一下跳到课表。
 *
 * 刻意不画"课间空档"那一行（见 `load()` 里的说明）：今天页只该回答
 * "我今天有什么课、已经专注了什么"，不替用户安排空档怎么用。
 */
@Composable
fun TodayTimelineCard(
    state: TodayTimelineUi,
    onOpenSchedule: () -> Unit,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER")
    showStatusHint: Boolean = true,
) {
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onOpenSchedule,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("课程", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Text(
                    text = "课表",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(18.dp),
                )
            }

            if (!state.loaded) {
                Text(
                    text = "读取中…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (state.entries.isEmpty()) {
                Text(
                    text = "今天没有课，也没有专注记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.tomorrowHint?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                TimelineList(state)
                state.tomorrowFirst?.let { first ->
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 6.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                    )
                    Text(
                        text = "明天第一节",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    TimelineRow(entry = first, nowMin = Int.MAX_VALUE)
                }
            }
        }
    }
}

@Composable
private fun TimelineList(state: TodayTimelineUi) {
    val entries = state.entries
    val pastEntries = remember(entries, state.nowMin) {
        entries.filter { it.endMin <= state.nowMin }
    }
    val activeAndUpcoming = remember(entries, state.nowMin) {
        entries.filter { it.endMin > state.nowMin }
    }
    // 只有当已结束条数 + 总数超过 2 条时才默认收拢，保持首页如原版般短小紧凑
    val shouldCollapsePast = pastEntries.isNotEmpty() && entries.size > 2
    var showPast by remember { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (showPast) 180f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "todayTimelineArrowRotation",
    )

    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        if (shouldCollapsePast) {
            val pastCourses = pastEntries.count { it.kind == TimelineKind.COURSE }
            val pastFocus = pastEntries.count { it.kind == TimelineKind.FOCUS }
            val summaryText = buildList {
                if (pastCourses > 0) add("${pastCourses} 节课")
                if (pastFocus > 0) add("专注 ${pastFocus} 轮")
            }.joinToString(" · ")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .glassClickable(onClick = { showPast = !showPast })
                    .padding(vertical = 5.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "已结束 $summaryText",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (showPast) "收起" else "展开",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer { rotationZ = arrowRotation },
                )
            }
            AnimatedVisibility(
                visible = showPast,
                enter = expandVertically(
                    animationSpec = tween(220, easing = FastOutSlowInEasing),
                    expandFrom = Alignment.Top,
                ) + fadeIn(animationSpec = tween(160, delayMillis = 40)),
                exit = shrinkVertically(
                    animationSpec = tween(180, easing = FastOutSlowInEasing),
                    shrinkTowards = Alignment.Top,
                ) + fadeOut(animationSpec = tween(90)),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    pastEntries.forEach { entry ->
                        TimelineRow(entry = entry, nowMin = state.nowMin)
                    }
                }
            }
        }

        val visibleList = if (shouldCollapsePast) activeAndUpcoming else entries
        var nowPlaced = false
        visibleList.forEach { entry ->
            if (!nowPlaced && entry.startMin > state.nowMin) {
                NowRow(state.nowMin)
                nowPlaced = true
            }
            TimelineRow(entry = entry, nowMin = state.nowMin)
        }
        if (!nowPlaced && (activeAndUpcoming.isNotEmpty() || !shouldCollapsePast)) {
            NowRow(state.nowMin)
        }
    }
}

/** 当前时刻横线指示行（纯细线 + 枣红时刻点，去除冗余文字） */
@Composable
private fun NowRow(nowMin: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = TodayTimeline.clock(nowMin),
            style = MaterialTheme.typography.labelSmall.tabularNumbers(),
            color = MaterialTheme.colorScheme.tertiary,
            textAlign = TextAlign.End,
            modifier = Modifier.width(48.dp),
        )
        Spacer(Modifier.width(8.dp))
        Box(modifier = Modifier.width(16.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary),
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(0.75.dp)
                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)),
        )
    }
}

@Composable
private fun TimelineRow(entry: TimelineEntry, nowMin: Int) {
    val ongoing = entry.covers(nowMin)
    val railColor = MaterialTheme.colorScheme.outlineVariant
    val dotColor = when {
        entry.hasUnresolvedConflict -> MaterialTheme.colorScheme.error
        ongoing -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    }
    val titleColor = when {
        entry.kind == TimelineKind.GAP -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }

    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        // 左：时间栏
        Column(
            modifier = Modifier.width(48.dp).padding(top = 2.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = TodayTimeline.clock(entry.startMin),
                style = MaterialTheme.typography.labelMedium.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = TodayTimeline.clock(entry.endMin),
                style = MaterialTheme.typography.labelSmall.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        // 中：竖轴 + 圆点
        Box(Modifier.width(16.dp).fillMaxHeight()) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(railColor),
            )
            if (ongoing) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = 4.dp)
                        .size(15.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(dotColor),
                    )
                }
            } else {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = 7.dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        // 右：内容
        Column(
            modifier = Modifier.weight(1f).padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            if (entry.hasUnresolvedConflict) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.WarningAmber, contentDescription = null,
                                tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("课程冲突", style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        }
                        Text(entry.subtitle, style = MaterialTheme.typography.bodySmall,
                            maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text("点按查看课表", style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.error)
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entryTitle(entry),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (ongoing) FontWeight.Bold else FontWeight.Normal,
                        color = titleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (ongoing) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "进行中",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
                entrySubtitle(entry)?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (entry.kind == TimelineKind.COURSE && entry.focusSeconds > 0) {
                    Text(
                        text = "已专注 ${FocusStats.humanDuration(entry.focusSeconds)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
    }
}

private fun entryTitle(entry: TimelineEntry): String = entry.title

private fun entrySubtitle(entry: TimelineEntry): String? =
    entry.subtitle.takeIf { it.isNotBlank() }
