package com.zzupocket.app.ui.components

import android.graphics.Color as AndroidColor
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zzupocket.app.data.prefs.GlassStyle

/**
 * 液态玻璃边缘微映色方位。
 */
enum class RimBias {
    NONE,
    LEFT,
    RIGHT,
    TOP_LEFT,
    TOP_RIGHT,
    ALL,
}

/** 录制后的图层及根节点偏移，供液态玻璃进行实时光学采样与折射。 */
data class LiquidBackdropState(
    val layer: GraphicsLayer,
    val rootOffset: Offset = Offset.Zero,
)

/** 主内容层采样源（用于浮动在列表之上的底部 Dock 实时穿透采样） */
val LocalLiquidBackdrop = compositionLocalOf<LiquidBackdropState?> { null }

/** 纯环境光底图采样源（仅包含极光画布与壁纸，用于全部页面内部卡片/按钮硬件折射，杜绝自递归） */
val LocalAmbientBackdrop = compositionLocalOf<LiquidBackdropState?> { null }

/** 全应用统一的液态玻璃通透度档位 */
val LocalGlassStyle = compositionLocalOf { GlassStyle.CRYSTAL }

/** 辅助生成并缓存 Android 12+ GPU 模糊 RenderEffect（saturation == 1f 时不叠滤镜矩阵）。 */
@Composable
internal fun rememberGlassRenderEffect(
    blurDp: Dp,
    saturation: Float,
): RenderEffect? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val density = LocalDensity.current
    val blurPx = with(density) { blurDp.toPx() }.coerceAtLeast(1f)
    return remember(blurPx, saturation) {
        val blur = AndroidRenderEffect.createBlurEffect(
            blurPx,
            blurPx,
            Shader.TileMode.CLAMP,
        )
        if (kotlin.math.abs(saturation - 1f) < 1e-3f) {
            blur.asComposeRenderEffect()
        } else {
            val satMatrix = ColorMatrix().apply { setSaturation(saturation) }
            AndroidRenderEffect.createColorFilterEffect(
                ColorMatrixColorFilter(satMatrix),
                blur,
            ).asComposeRenderEffect()
        }
    }
}

/**
 * 全屏克制纸感渐变底图画布（墨 · 印 · 纸）。
 *
 * 严格遵循去 AI 味原则：仅从中性纸底向 primary / secondary 做轻度线性混合（lerp），
 * 绝不做色相偏移，绝不引入 tertiary 铺底或霓虹极光斑。
 */
@Composable
fun LiquidGlassCanvasBackground(
    dark: Boolean,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    secondaryColor: Color = MaterialTheme.colorScheme.secondary,
    @Suppress("UNUSED_PARAMETER")
    tertiaryColor: Color = MaterialTheme.colorScheme.tertiary,
    modifier: Modifier = Modifier,
) {
    val baseBrush = remember(dark) {
        if (dark) {
            Brush.verticalGradient(
                listOf(Color(0xFF0E1413), Color(0xFF0B1110), Color(0xFF090D0C)),
            )
        } else {
            Brush.verticalGradient(
                listOf(Color(0xFFF5F6F3), Color(0xFFF1F3F0), Color(0xFFEDEFEC)),
            )
        }
    }

    val fields = remember(dark, accentColor, secondaryColor) {
        if (dark) {
            listOf(
                ColorField(
                    xRatio = 0.10f,
                    yRatio = 0.0f,
                    radiusFactor = 0.90f,
                    color = lerp(Color(0xFF0E1413), accentColor, 0.18f),
                ),
                ColorField(
                    xRatio = 0.95f,
                    yRatio = 0.70f,
                    radiusFactor = 0.65f,
                    color = lerp(Color(0xFF0B1110), accentColor, 0.08f),
                ),
            )
        } else {
            listOf(
                ColorField(
                    xRatio = 0.0f,
                    yRatio = 0.0f,
                    radiusFactor = 0.85f,
                    color = lerp(Color(0xFFF5F6F3), accentColor, 0.16f),
                ),
                ColorField(
                    xRatio = 1.0f,
                    yRatio = 0.62f,
                    radiusFactor = 0.70f,
                    color = lerp(Color(0xFFF1F3F0), secondaryColor, 0.10f),
                ),
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseBrush)
            .drawBehind {
                val maxDim = size.maxDimension.coerceAtLeast(1f)
                fields.forEach { field ->
                    val center = Offset(size.width * field.xRatio, size.height * field.yRatio)
                    val radius = maxDim * field.radiusFactor
                    drawCircle(
                        brush = Brush.radialGradient(
                            0.0f to field.color,
                            1.0f to Color.Transparent,
                            center = center,
                            radius = radius,
                        ),
                        radius = radius,
                        center = center,
                    )
                }
            },
    )
}

private data class ColorField(
    val xRatio: Float,
    val yRatio: Float,
    val radiusFactor: Float,
    val color: Color,
)

/**
 * 录制内容或底图图层到离屏 [GraphicsLayer]，供液态玻璃实时采样。
 */
fun Modifier.captureToLiquidBackdrop(
    state: LiquidBackdropState?,
    onRootPositioned: (Offset) -> Unit,
): Modifier {
    if (state == null) return this
    return this
        .onGloballyPositioned { coords ->
            onRootPositioned(coords.positionInRoot())
        }
        .drawWithContent {
            state.layer.record {
                this@drawWithContent.drawContent()
            }
            drawLayer(state.layer)
        }
}

/**
 * 悬浮液态玻璃底部 Dock（唯一保留边缘折射环的浮层组件）。
 *
 * 摒弃底部阴影唇与左右染色边：高模糊隔离底层干扰文字 + 平涂可读底色 + 1.5dp 克制折射环 + 0.6dp 单色细线。
 */
@Composable
fun LiquidGlassRealTimeBar(
    dark: Boolean,
    transparentWallpaper: Boolean,
    @Suppress("UNUSED_PARAMETER")
    rightEdgeTint: Color? = null,
    modifier: Modifier = Modifier,
    height: Dp = 64.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val backdrop = LocalLiquidBackdrop.current
    val glassStyle = LocalGlassStyle.current
    var barPosInRoot by remember { mutableStateOf(Offset.Zero) }

    val glassLayer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && backdrop != null) {
        androidx.compose.ui.graphics.rememberGraphicsLayer()
    } else {
        null
    }
    val rimLayer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && backdrop != null) {
        androidx.compose.ui.graphics.rememberGraphicsLayer()
    } else {
        null
    }

    val blurDp = when (glassStyle) {
        GlassStyle.CRYSTAL -> 14.dp
        GlassStyle.BALANCED -> 18.dp
        GlassStyle.FROSTED -> 24.dp
    }
    val bodyEffect = rememberGlassRenderEffect(blurDp = blurDp, saturation = 1.15f)
    val rimEffect = rememberGlassRenderEffect(blurDp = 1.5.dp, saturation = 1.0f)

    val glazeAlpha = when {
        transparentWallpaper -> if (dark) 0.48f else 0.40f
        glassStyle == GlassStyle.CRYSTAL -> if (dark) 0.62f else 0.58f
        glassStyle == GlassStyle.BALANCED -> if (dark) 0.72f else 0.68f
        else -> if (dark) 0.80f else 0.78f
    }
    val glazeColor = if (dark) {
        Color(0xFF141B1A).copy(alpha = glazeAlpha)
    } else {
        Color.White.copy(alpha = glazeAlpha)
    }
    val borderColor = if (dark) {
        Color.White.copy(alpha = 0.12f)
    } else {
        Color(0xFF10201E).copy(alpha = 0.08f)
    }

    Box(
        modifier = modifier
            .height(height)
            .onGloballyPositioned { barPosInRoot = it.positionInRoot() }
            .clip(RoundedCornerShape(999.dp))
            .drawBehind {
                val corner = size.height / 2f
                val deltaX = backdrop?.rootOffset?.x?.minus(barPosInRoot.x) ?: 0f
                val deltaY = backdrop?.rootOffset?.y?.minus(barPosInRoot.y) ?: 0f

                // ① 中心硬件光学折射模糊层
                if (backdrop != null && glassLayer != null && bodyEffect != null) {
                    glassLayer.renderEffect = bodyEffect
                    glassLayer.record {
                        translate(deltaX, deltaY) {
                            drawLayer(backdrop.layer)
                        }
                    }
                    drawLayer(glassLayer)
                }

                // ② 平涂可读半透明底色（隔离下方滚过文字）
                drawRect(glazeColor)

                // ③ 外圈 1.5dp 克制边缘微折射环
                val rimThickness = 1.5.dp.toPx()
                if (backdrop != null && rimLayer != null && rimEffect != null) {
                    rimLayer.renderEffect = rimEffect
                    rimLayer.record {
                        scale(
                            scaleX = 1.02f,
                            scaleY = 1.02f,
                            pivot = Offset(size.width / 2f, size.height / 2f),
                        ) {
                            translate(deltaX, deltaY) {
                                drawLayer(backdrop.layer)
                            }
                        }
                    }
                    val ringPath = Path().apply {
                        fillType = PathFillType.EvenOdd
                        addRoundRect(
                            RoundRect(
                                rect = Rect(0f, 0f, size.width, size.height),
                                cornerRadius = CornerRadius(corner, corner),
                            ),
                        )
                        addRoundRect(
                            RoundRect(
                                rect = Rect(
                                    rimThickness,
                                    rimThickness,
                                    (size.width - rimThickness).coerceAtLeast(rimThickness),
                                    (size.height - rimThickness).coerceAtLeast(rimThickness),
                                ),
                                cornerRadius = CornerRadius(
                                    (corner - rimThickness).coerceAtLeast(0f),
                                    (corner - rimThickness).coerceAtLeast(0f),
                                ),
                            ),
                        )
                    }
                    clipPath(ringPath) {
                        drawLayer(rimLayer)
                    }
                }

                // ④ 0.6dp 单色中性细边框
                val strokeW = 0.6.dp.toPx()
                val inset = strokeW / 2f
                drawRoundRect(
                    color = borderColor,
                    topLeft = Offset(inset, inset),
                    size = Size(
                        (size.width - strokeW).coerceAtLeast(0f),
                        (size.height - strokeW).coerceAtLeast(0f),
                    ),
                    cornerRadius = CornerRadius(
                        (corner - inset).coerceAtLeast(0f),
                        (corner - inset).coerceAtLeast(0f),
                    ),
                    style = Stroke(width = strokeW),
                )
            },
        content = content,
    )
}

/**
 * 极简圆形图标按钮（40.dp，无折射层、无假阴影唇、水波纹精准裁切在圆内）。
 */
@Composable
fun LiquidGlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeDot: Boolean = false,
    @Suppress("UNUSED_PARAMETER")
    rimTint: Color? = null,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val fillColor = if (isDark) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.62f)
    val borderColor = if (isDark) Color.White.copy(alpha = 0.10f) else Color(0xFF10201E).copy(alpha = 0.07f)

    Box(modifier = modifier.size(40.dp), contentAlignment = Alignment.Center) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(fillColor)
                .border(BorderStroke(0.6.dp, borderColor), CircleShape)
                .clickable(onClick = onClick),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
        }
        if (badgeDot) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-3).dp, y = 3.dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary)
                    .border(1.5.dp, MaterialTheme.colorScheme.background, CircleShape),
            )
        }
    }
}

/**
 * 二级页面左上角返回按钮。
 */
@Composable
fun LiquidGlassBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LiquidGlassIconButton(
        icon = Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = "返回",
        onClick = onClick,
        modifier = modifier.padding(start = 8.dp),
    )
}

/**
 * 克制胶囊筛选片：选中为实心 primary + onPrimary；未选中为平涂半透明 + 0.6dp 中性细线。
 */
@Composable
fun LiquidGlassChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val fillColor = when {
        selected -> accentColor
        isDark -> Color.White.copy(alpha = 0.06f)
        else -> Color.White.copy(alpha = 0.55f)
    }
    val borderColor = if (isDark) {
        Color.White.copy(alpha = 0.10f)
    } else {
        Color(0xFF10201E).copy(alpha = 0.08f)
    }
    val textColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 34.dp)
            .clip(shape)
            .background(fillColor)
            .let { mod -> if (selected) mod else mod.border(BorderStroke(0.6.dp, borderColor), shape) }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(5.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color = textColor,
            )
        }
    }
}

/**
 * 克制主操作胶囊按钮：实心 primary 平涂、无渐变发光阴影、水波纹裁切。
 */
@Composable
fun LiquidGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val contentColor = if (accentColor == MaterialTheme.colorScheme.primary) {
        MaterialTheme.colorScheme.onPrimary
    } else if (accentColor.luminance() < 0.5f) {
        Color.White
    } else {
        Color(0xFF141A19)
    }

    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.45f)
            .clip(shape)
            .background(accentColor)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            ProvideTextStyle(
                value = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
            ) {
                Row(
                    modifier = Modifier
                        .defaultMinSize(minHeight = 40.dp)
                        .padding(contentPadding),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    content = content,
                )
            }
        }
    }
}

/**
 * 次级描边胶囊按钮：透明底 + 1dp 轻色线。
 */
@Composable
fun LiquidGlassOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.primary,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.45f)
            .clip(shape)
            .border(BorderStroke(1.dp, tint.copy(alpha = 0.35f)), shape)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides tint) {
            ProvideTextStyle(
                value = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
            ) {
                Row(
                    modifier = Modifier
                        .defaultMinSize(minHeight = 40.dp)
                        .padding(contentPadding),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    content = content,
                )
            }
        }
    }
}

/** 底部抽屉 `ModalBottomSheet` 统一底色。 */
@Composable
fun liquidGlassSheetContainerColor(): Color {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (isDark) Color(0xFF141B1A).copy(alpha = 0.96f) else Color(0xFFF4F5F2).copy(alpha = 0.96f)
}

/** 对话框 `AlertDialog` 统一液态冰透底色。 */
@Composable
fun liquidGlassDialogContainerColor(): Color {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (isDark) Color(0xFF0E1525).copy(alpha = 0.96f) else Color(0xFFF7FAFF).copy(alpha = 0.96f)
}
