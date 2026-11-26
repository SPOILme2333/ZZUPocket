package com.zzupocket.app.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zzupocket.app.core.CourseShortener
import com.zzupocket.app.ui.components.GlassCard
import com.zzupocket.app.ui.theme.tabularNumbers

/**
 * 首页顶部「实时课况」液态玻璃 Hero 卡片（克制极简排版：单粗体课程名 + 3dp 实心细进度条）。
 */
@Composable
fun TodayHeroBanner(
    timeline: TodayTimelineUi,
    smartShorten: Boolean,
    onOpenSchedule: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val courses = timeline.entries.filter { it.kind == TimelineKind.COURSE }
    val ongoing = courses.firstOrNull { it.covers(timeline.nowMin) }
    val next = courses.filter { it.startMin > timeline.nowMin }.minByOrNull { it.startMin }

    val (overlineText, headline, metaText, progress, overlineColor) = when {
        ongoing != null && ongoing.hasUnresolvedConflict -> HeroSnapshot(
            overline = "时段冲突",
            headline = "当前时段有课程冲突",
            meta = ongoing.subtitle.ifBlank { "点击查看课表" },
            progress = null,
            overlineColor = MaterialTheme.colorScheme.tertiary,
        )
        ongoing != null -> {
            val total = (ongoing.endMin - ongoing.startMin).coerceAtLeast(1)
            val elapsed = (timeline.nowMin - ongoing.startMin).coerceIn(0, total)
            val leftMin = (ongoing.endMin - timeline.nowMin).coerceAtLeast(0)
            val timeRange = "${TodayTimeline.clock(ongoing.startMin)} – ${TodayTimeline.clock(ongoing.endMin)}"
            val room = ongoing.subtitle.takeIf { it.isNotBlank() }
            HeroSnapshot(
                overline = "正在上课 · 还剩 ${TodayTimeline.duration(leftMin)}",
                headline = CourseShortener.shorten(ongoing.title, smartShorten),
                meta = listOfNotNull(timeRange, room).joinToString(" · "),
                progress = elapsed.toFloat() / total.toFloat(),
                overlineColor = MaterialTheme.colorScheme.tertiary,
            )
        }
        next != null -> {
            val waitMin = (next.startMin - timeline.nowMin).coerceAtLeast(1)
            val title = if (next.hasUnresolvedConflict) {
                "下一时段有冲突课程"
            } else {
                CourseShortener.shorten(next.title, smartShorten)
            }
            val timeRange = "${TodayTimeline.clock(next.startMin)} – ${TodayTimeline.clock(next.endMin)}"
            val room = next.subtitle.takeIf { it.isNotBlank() && !next.hasUnresolvedConflict }
            HeroSnapshot(
                overline = "下一节 · ${TodayTimeline.duration(waitMin)}后",
                headline = title,
                meta = listOfNotNull(timeRange, room).joinToString(" · "),
                progress = null,
                overlineColor = MaterialTheme.colorScheme.primary,
            )
        }
        TodayTimeline.allCoursesFinished(courses, timeline.nowMin) -> {
            val sub = timeline.tomorrowFirst?.let {
                val room = it.subtitle.takeIf { s -> s.isNotBlank() }
                val base = "明日 ${TodayTimeline.clock(it.startMin)} · ${CourseShortener.shorten(it.title, smartShorten)}"
                if (room != null) "$base · $room" else base
            } ?: "好好休息，明天见"
            HeroSnapshot(
                overline = "今日已结课",
                headline = "今天 ${courses.size} 节课已上完",
                meta = sub,
                progress = null,
                overlineColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        else -> HeroSnapshot(
            overline = "今日概览",
            headline = if (timeline.loaded) "今天没有排课" else "正在载入今日课况…",
            meta = timeline.tomorrowHint ?: "点按查看完整课表",
            progress = null,
            overlineColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onOpenSchedule,
    ) {
        Column(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = overlineText,
                style = MaterialTheme.typography.labelLarge.tabularNumbers(),
                color = overlineColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = headline,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = metaText,
                style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (progress != null) {
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
    }
}

private data class HeroSnapshot(
    val overline: String,
    val headline: String,
    val meta: String,
    val progress: Float?,
    val overlineColor: Color,
)
