package com.zzupocket.app.ui.components

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zzupocket.app.data.prefs.GlassStyle
import com.zzupocket.app.ui.theme.tabularNumbers

/**
 * 克制液态玻璃容器卡片：背景实时柔和模糊 + 平涂半透明釉面 + 0.6dp 中性细线。
 *
 * 彻底摒弃底部灰色假阴影唇、霓虹着色边缘、三段斜渐变与顶部高光丝；
 * `clickable` 位于 `clip(shape)` 之内，保证点击水波纹沿圆角准确裁切。
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    containerColor: Color? = null,
    translucent: Boolean = false,
    @Suppress("UNUSED_PARAMETER")
    rimTint: Color? = null,
    @Suppress("UNUSED_PARAMETER")
    rimBias: RimBias = RimBias.NONE,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val ambient = LocalAmbientBackdrop.current
    val glassStyle = LocalGlassStyle.current
    var cardPosInRoot by remember { mutableStateOf(Offset.Zero) }

    val effectiveShape = if (translucent) RoundedCornerShape(12.dp) else shape
    val useRealtimeRefract = !translucent && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ambient != null
    val bodyLayer = if (useRealtimeRefract) androidx.compose.ui.graphics.rememberGraphicsLayer() else null

    val bodyBlurDp = when (glassStyle) {
        GlassStyle.CRYSTAL -> 24.dp
        GlassStyle.BALANCED -> 28.dp
        GlassStyle.FROSTED -> 32.dp
    }
    val bodyEffect = rememberGlassRenderEffect(blurDp = bodyBlurDp, saturation = 1.0f)

    val glazeColor = when {
        containerColor != null -> containerColor
        translucent -> if (isDark) Color.White.copy(alpha = 0.045f) else Color(0xFF10201E).copy(alpha = 0.035f)
        isDark -> Color(0xFF18201F).copy(
            alpha = when (glassStyle) {
                GlassStyle.CRYSTAL -> 0.52f
                GlassStyle.BALANCED -> 0.64f
                GlassStyle.FROSTED -> 0.76f
            },
        )
        else -> Color.White.copy(
            alpha = when (glassStyle) {
                GlassStyle.CRYSTAL -> 0.46f
                GlassStyle.BALANCED -> 0.60f
                GlassStyle.FROSTED -> 0.74f
            },
        )
    }

    val hairlineColor = if (isDark) {
        Color.White.copy(alpha = 0.09f)
    } else {
        Color(0xFF10201E).copy(alpha = 0.07f)
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { cardPosInRoot = it.positionInRoot() }
            .clip(effectiveShape)
            .drawBehind {
                if (!translucent && ambient != null && bodyLayer != null && bodyEffect != null) {
                    val deltaX = ambient.rootOffset.x - cardPosInRoot.x
                    val deltaY = ambient.rootOffset.y - cardPosInRoot.y
                    bodyLayer.renderEffect = bodyEffect
                    bodyLayer.record {
                        translate(deltaX, deltaY) {
                            drawLayer(ambient.layer)
                        }
                    }
                    drawLayer(bodyLayer)
                }
                drawRect(glazeColor)
            }
            .let { mod ->
                if (!translucent) mod.border(BorderStroke(0.6.dp, hairlineColor), effectiveShape) else mod
            }
            .let { mod ->
                if (onClick != null) mod.glassClickable(onClick = onClick, isDark = isDark) else mod
            },
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            content()
        }
    }
}

/** 均匀贴合圆角容器的按压微釉感，彻底替代 Material 默认大圆形水波纹。 */
@Composable
internal fun Modifier.glassClickable(
    onClick: () -> Unit,
    isDark: Boolean? = null,
): Modifier {
    val dark = isDark ?: (MaterialTheme.colorScheme.background.luminance() < 0.5f)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val overlayAlpha by animateFloatAsState(
        targetValue = if (pressed) (if (dark) 0.08f else 0.055f) else 0f,
        animationSpec = tween(durationMillis = if (pressed) 70 else 150),
        label = "glassPressAlpha",
    )
    val overlayColor = if (dark) Color.White else Color(0xFF10201E)
    return this
        .drawWithContent {
            drawContent()
            if (overlayAlpha > 0.001f) {
                drawRect(overlayColor.copy(alpha = overlayAlpha))
            }
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        )
}

/** 分组区外置小标题（置于卡片外部，消除卡内嵌套卡与标签噪音）。 */
@Composable
fun GlassSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 4.dp, top = 8.dp, bottom = 0.dp),
    )
}

/** 标准玻璃分组列表卡片，内部搭配 [GlassListRow] 与 [GlassInsetDivider]。 */
@Composable
fun GlassListCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

/** 列表行内缩细分隔线（默认左缩进 52dp 与正文文字齐平）。 */
@Composable
fun GlassInsetDivider(
    modifier: Modifier = Modifier,
    startIndent: Dp = 52.dp,
) {
    HorizontalDivider(
        modifier = modifier.padding(start = startIndent, end = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.70f),
    )
}

/** 通用克制列表行（≥56dp 触控热区，支持左图标、标题、副标题、右侧附注与箭头）。 */
@Composable
fun GlassListRow(
    icon: ImageVector?,
    title: String,
    subtitle: String? = null,
    trailing: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .glassClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(16.dp))
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!trailing.isNullOrBlank()) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = trailing,
                style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** 单卡分格关键指标单元格（搭配竖向细线使用，彻底替代套娃小卡片）。 */
@Composable
fun GlassMetricCell(
    label: String,
    value: String,
    caption: String? = null,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .glassClickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall.tabularNumbers(),
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!caption.isNullOrBlank()) {
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
