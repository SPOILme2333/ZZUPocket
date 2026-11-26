package com.zzupocket.app.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zzupocket.app.core.AppGraph
import com.zzupocket.app.core.ScheduleText
import com.zzupocket.app.domain.Course
import com.zzupocket.app.domain.FocusRecord
import com.zzupocket.app.domain.FocusStats
import com.zzupocket.app.domain.Phase
import com.zzupocket.app.domain.Semester
import com.zzupocket.app.domain.Session
import com.zzupocket.app.domain.toSlotLines
import com.zzupocket.app.ui.components.GlassCard
import com.zzupocket.app.ui.components.StatusPill

/**
 * 编辑框里的一个教室字段：同一时段里**教室相同**的教学周合成一组；
 * 单双周换教室（单周 302、双周 405）会拆成多组、各自带周次，
 * 覆盖键随之带上周次，互不串扰。[wholeSlot] = 该时段只有一种教室 → 一条覆盖管全部周。
 */
internal data class RoomEditSlot(
    val weekday: Int,
    val startUnit: Int,
    val endUnit: Int,
    val room: String?,
    val weeks: List<Int>,
    val wholeSlot: Boolean,
) {
    /** 与 [DisplayOverridesView.rooms] / [ScheduleViewModel.applyDisplayEdits] 约定的键 */
    val editKey: String
        get() = "$weekday|$startUnit" +
            (if (wholeSlot) "" else "|" + weeks.joinToString(","))

    /** 初始值：精确周的覆盖 → 全部周的覆盖 → 教务原值 */
    fun initialValue(rooms: Map<String, String>): String =
        rooms[editKey] ?: rooms["$weekday|$startUnit"] ?: room.orEmpty()
}

/** 按「时段 → 教室」两级归并出可编辑字段（与详情页分时段明细同一套思路） */
internal fun roomEditSlots(sessions: List<Session>): List<RoomEditSlot> =
    sessions.groupBy { it.weekday to it.startUnit }
        .flatMap { (slot, sameSlot) ->
            val byRoom = sameSlot.groupBy { it.roomText ?: "" }
            val groups = (if (byRoom.size == 1) listOf(sameSlot) else byRoom.values.toList())
                .map { g ->
                    val head = g.first()
                    RoomEditSlot(
                        weekday = slot.first,
                        startUnit = slot.second,
                        endUnit = head.endUnit,
                        room = head.roomText,
                        weeks = g.map { it.weekIndex }.distinct().sorted(),
                        wholeSlot = byRoom.size == 1,
                    )
                }
            groups
        }
        .sortedWith(compareBy({ it.weekday }, { it.startUnit }, { it.weeks.firstOrNull() ?: 0 }))

/**
 * 编辑一门课的**本地显示**（issue #6 保底）：课程名 + 各时段教室。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EditDisplayDialog(
    course: Course,
    overrides: DisplayOverridesView?,
    onDismiss: () -> Unit,
    onSave: (String, Map<String, String>) -> Unit,
) {
    var name by remember(course) { mutableStateOf(overrides?.name ?: course.nameZh) }
    val slots = remember(course) { roomEditSlots(course.sessions) }
    val rooms = remember(course) {
        val initial = slots.associate { it.editKey to it.initialValue(overrides?.rooms ?: emptyMap()) }
        mutableStateMapOf(*initial.toList().toTypedArray())
    }
    val multiSlots = remember(course) {
        slots.groupBy { it.weekday to it.startUnit }
            .filterValues { it.size > 1 }
            .map { it.key }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑显示（仅本机）") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "改的是显示，不改教务数据；刷新课表不会被覆盖。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(24) },
                    label = { Text("课程名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                slots.forEachIndexed { i, s ->
                    val key = s.editKey
                    if ((s.weekday to s.startUnit) in multiSlots &&
                        slots.getOrNull(i - 1)?.let { it.weekday to it.startUnit } != (s.weekday to s.startUnit)
                    ) {
                        Text(
                            text = "该时段不同周教室不同，已按周拆分：",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Text(
                        text = if (s.wholeSlot) {
                            "${ScheduleText.weekdayName(s.weekday)} ${s.startUnit}-${s.endUnit} 节"
                        } else {
                            "${ScheduleText.weekdayName(s.weekday)} ${s.startUnit}-${s.endUnit} 节 · " +
                                ScheduleText.compressWeeks(s.weeks)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    OutlinedTextField(
                        value = rooms[key].orEmpty(),
                        onValueChange = { rooms[key] = it.take(20) },
                        label = { Text("教室（清空恢复默认）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim(), rooms.toMap()) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CourseDetailSheet(
    course: Course,
    onDismiss: () -> Unit,
    overrides: DisplayOverridesView? = null,
    onApplyEdits: ((Course, String, Map<String, String>) -> Unit)? = null,
    onResetOverrides: ((Course) -> Unit)? = null,
    beforeDetails: @Composable () -> Unit = {},
) {
    var editOpen by remember(course) { mutableStateOf(false) }
    if (editOpen && onApplyEdits != null) {
        EditDisplayDialog(
            course = course,
            overrides = overrides,
            onDismiss = { editOpen = false },
            onSave = { name, rooms ->
                onApplyEdits(course, name, rooms)
                editOpen = false
            },
        )
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = com.zzupocket.app.ui.components.liquidGlassSheetContainerColor(),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            beforeDetails()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    course.nameZh,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (overrides?.any == true) {
                    Spacer(Modifier.width(8.dp))
                    StatusPill(text = "已自定义")
                }
            }
            if (onApplyEdits != null) {
                Row {
                    TextButton(onClick = { editOpen = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (overrides?.any == true) "改显示" else "编辑显示")
                    }
                    if (overrides?.any == true && onResetOverrides != null) {
                        TextButton(onClick = { onResetOverrides(course) }) { Text("恢复默认") }
                    }
                }
            }
            if (course.teachers.isNotEmpty()) {
                DetailRow("教师", course.teachers.joinToString("、"))
            }
            val rooms = course.roomTexts.mapNotNull { ScheduleText.tidyPlace(it) }.distinct()
            if (rooms.isNotEmpty()) DetailRow("地点", rooms.joinToString("、"))
            val clock = course.sessions.firstOrNull()?.let { s ->
                listOfNotNull(s.startTime, s.endTime).takeIf { it.size == 2 }?.joinToString("–")
            }
            DetailRow("时间", listOfNotNull(ScheduleText.timeSlots(course.slots), clock).joinToString(" · "))
            DetailRow("周次", ScheduleText.compressWeeks(course.weeks))
            if (course.code.isNotBlank() && course.code != course.nameZh) {
                DetailRow("课程代码", course.code)
            }
            course.openDepartment?.takeIf { it.isNotBlank() }?.let { DetailRow("开课院系", it) }

            CourseFocusSection(course = course, onStartFocus = onDismiss)

            val slotLines = course.sessions.toSlotLines()
            if (slotLines.size > 1) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text("分时段明细", style = MaterialTheme.typography.titleSmall)
                slotLines.forEach { line ->
                    val unitText =
                        if (line.startUnit == line.endUnit) "${line.startUnit}节"
                        else "${line.startUnit}-${line.endUnit}节"
                    val detail = listOfNotNull(
                        line.room,
                        line.teacher,
                        ScheduleText.compressWeeks(line.weeks),
                    ).joinToString(" · ")
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Text(
                            text = "${ScheduleText.weekdayName(line.weekday)} $unitText",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.width(104.dp),
                        )
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseFocusSection(course: Course, onStartFocus: () -> Unit) {
    val records by AppGraph.focusRepository
        .observeRecords(0L)
        .collectAsStateWithLifecycle(initialValue = emptyList<FocusRecord>())

    val ref = "course:${course.key}"
    val focusedSeconds = records
        .filter { it.contextRef == ref && it.phase == Phase.FOCUS }
        .sumOf { it.actualSeconds }
    val plannedMinutes = course.sessions
        .distinctBy { Triple(it.weekday, it.startUnit, it.endUnit) }
        .sumOf { minutesBetween(it.startTime, it.endTime) }

    HorizontalDivider(Modifier.padding(vertical = 4.dp))
    Text("计划 vs 实际", style = MaterialTheme.typography.titleSmall)
    if (plannedMinutes > 0) {
        DetailRow("每周课时", FocusStats.humanDuration(plannedMinutes * 60))
    }
    DetailRow(
        "番茄钟累计",
        if (focusedSeconds > 0) FocusStats.humanDuration(focusedSeconds) else "还没有记录",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = {
            AppGraph.pomodoroEngine.start(
                phase = Phase.FOCUS,
                tagId = null,
                tagName = course.nameZh,
                contextRef = ref,
            )
            onStartFocus()
        }) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("开始专注（标签：${course.nameZh}）")
        }
    }
}

private fun minutesBetween(start: String?, end: String?): Int {
    val a = parseHhmm(start) ?: return 0
    val b = parseHhmm(end) ?: return 0
    return (b - a).coerceAtLeast(0)
}

private fun parseHhmm(text: String?): Int? {
    val raw = text?.trim().orEmpty()
    val parts = raw.split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    return h * 60 + m
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SemesterSheet(
    semesters: List<Semester>,
    selectedId: Int?,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = com.zzupocket.app.ui.components.liquidGlassSheetContainerColor(),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
            Text(
                "选择学期",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            semesters.groupBy { it.schoolYear }.forEach { (year, list) ->
                Text(
                    text = year.ifBlank { "其他" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
                list.forEach { s ->
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        onClick = { onPick(s.id) },
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = s.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            if (s.isCurrent) {
                                Spacer(Modifier.width(8.dp))
                                StatusPill(text = "本学期")
                            }
                            Spacer(Modifier.weight(1f))
                            if (s.id == selectedId) {
                                Text(
                                    "当前",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
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
internal fun WeekPickerDialog(
    weeks: List<Int>,
    current: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("跳转到教学周") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                weeks.chunked(6).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { w ->
                            val selected = w == current
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                    )
                                    .clickable { onPick(w) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("$w", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
