package com.zzupocket.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 紧凑型微透状态胶囊（仅用于真实运行状态：在籍、计时中、已暂停等；无白边、无塑料光泽）。
 */
@Composable
fun StatusPill(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = containerColor ?: contentColor.copy(alpha = 0.10f),
        contentColor = contentColor,
        shadowElevation = 0.dp,
        border = null,
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/**
 * 克制趋势曲线（Sparkline）：无面积填充，仅单色柔和曲线 + 末端采样点。
 */
@Composable
fun MiniSparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
) {
    if (values.isEmpty()) return
    val normalized = if (values.size == 1) listOf(values.first(), values.first()) else values

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val padX = 2.5.dp.toPx()
        val padY = 3.dp.toPx()
        val usableW = (w - padX * 2).coerceAtLeast(1f)
        val usableH = (h - padY * 2).coerceAtLeast(1f)
        val minVal = normalized.minOrNull() ?: 0f
        val maxVal = normalized.maxOrNull() ?: 0f
        val span = (maxVal - minVal).takeIf { it > 1e-4f } ?: 1f

        val points = normalized.mapIndexed { idx, v ->
            val x = padX + usableW * idx / (normalized.size - 1).coerceAtLeast(1)
            val ratio = if (maxVal == minVal) 0.5f else (v - minVal) / span
            val y = padY + usableH * (1f - ratio)
            Offset(x, y)
        }

        val strokePath = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 1 until points.size) {
                val prev = points[i - 1]
                val curr = points[i]
                val midX = (prev.x + curr.x) / 2f
                cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
            }
        }

        drawPath(
            path = strokePath,
            color = lineColor,
            style = Stroke(
                width = 1.5.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
        drawCircle(
            color = lineColor,
            radius = 2.5.dp.toPx(),
            center = points.last(),
        )
    }
}
