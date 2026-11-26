package com.zzupocket.app.ui.settings

import android.app.Application
import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zzupocket.app.core.AppGraph
import com.zzupocket.app.data.prefs.DarkContrast
import com.zzupocket.app.data.prefs.DarkMode
import com.zzupocket.app.data.prefs.GlassStyle
import com.zzupocket.app.data.prefs.ThemePreset
import com.zzupocket.app.data.prefs.UiPrefs
import com.zzupocket.app.ui.components.GlassCard
import com.zzupocket.app.ui.components.GlassInsetDivider
import com.zzupocket.app.ui.components.GlassListCard
import com.zzupocket.app.ui.components.GlassSectionHeader
import com.zzupocket.app.ui.components.LiquidGlassBackButton
import com.zzupocket.app.ui.components.LiquidGlassChip
import com.zzupocket.app.ui.components.LiquidGlassOutlinedButton
import com.zzupocket.app.ui.components.glassClickable
import com.zzupocket.app.ui.theme.SchemeVariant
import com.zzupocket.app.ui.theme.rememberAppColorScheme
import com.zzupocket.app.ui.theme.rememberEffectiveDark
import com.zzupocket.app.ui.theme.tabularNumbers
import com.zzupocket.app.ui.widget.Widgets
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 预设主题显示名（贴合原仓库风格并突出郑大默认校色） */
internal fun ThemePreset.label(): String = when (this) {
    ThemePreset.ZZU -> "郑大校色（默认）"
    ThemePreset.DYNAMIC -> "跟随壁纸（动态取色）"
    ThemePreset.PURPLE -> "紫"
    ThemePreset.BLUE -> "蓝"
    ThemePreset.TEAL -> "青"
    ThemePreset.GREEN -> "绿"
    ThemePreset.AMBER -> "琥珀"
    ThemePreset.PINK -> "玫红"
    ThemePreset.GRAPHITE -> "石墨"
    ThemePreset.CUSTOM -> "自定义色"
}

internal fun GlassStyle.label(): String = when (this) {
    GlassStyle.CRYSTAL -> "通透"
    GlassStyle.BALANCED -> "适中"
    GlassStyle.FROSTED -> "磨砂"
}

private fun DarkMode.label(): String = when (this) {
    DarkMode.SYSTEM -> "跟随系统"
    DarkMode.LIGHT -> "浅色"
    DarkMode.DARK -> "深色"
}

private fun DarkContrast.label(): String = when (this) {
    DarkContrast.STANDARD -> "标准"
    DarkContrast.MEDIUM -> "中"
    DarkContrast.HIGH -> "高"
}

private val ORDERED_THEME_PRESETS = listOf(
    ThemePreset.ZZU,
    ThemePreset.DYNAMIC,
    ThemePreset.PURPLE,
    ThemePreset.BLUE,
    ThemePreset.TEAL,
    ThemePreset.GREEN,
    ThemePreset.AMBER,
    ThemePreset.PINK,
    ThemePreset.GRAPHITE,
    ThemePreset.CUSTOM,
)

private val QUICK_CUSTOM_SWATCHES = listOf(
    0xFF223C3B.toInt(), // 墨青
    0xFF730917.toInt(), // 枣红
    0xFF3F5F8A.toInt(), // 黛蓝
    0xFF4F7A5A.toInt(), // 松青
    0xFF8A6A9E.toInt(), // 藕紫
    0xFFB07A3A.toInt(), // 麦褐
    0xFF5F6B73.toInt(), // 岩灰
    0xFF8C4A3C.toInt(), // 赭红
)

class AppearanceViewModel(application: Application) : AndroidViewModel(application) {

    private val store = AppGraph.uiPrefsStore

    private val _state = MutableStateFlow(store.latest)
    val state: StateFlow<UiPrefs> = _state.asStateFlow()

    init {
        viewModelScope.launch { store.flow.collect { _state.value = it } }
    }

    private fun update(block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
            runCatching { Widgets.refreshAll(getApplication()) }
        }
    }

    fun setPreset(preset: ThemePreset) = update { store.setThemePreset(preset) }

    fun setCustomSeed(argb: Int) = update { store.setCustomThemeSeed(argb) }

    fun setSchemeVariant(variant: SchemeVariant) = update { store.setSchemeVariant(variant.name) }

    fun setGlassStyle(style: GlassStyle) = update { store.setGlassStyle(style) }

    fun setDarkMode(mode: DarkMode) = update { store.setDarkMode(mode) }

    fun setDarkContrast(v: DarkContrast) = update { store.setDarkContrast(v) }

    fun setDarkTinted(v: Boolean) = update { store.setDarkTinted(v) }
}

/**
 * 「外观」设置中心：原仓库三色圆点分组列表形态，默认郑州大学校徽墨青与枣红双色。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
    vm: AppearanceViewModel = viewModel(),
) {
    val prefs by vm.state.collectAsStateWithLifecycle()
    val effectiveDark = rememberEffectiveDark(prefs.darkMode)
    val currentVariant = remember(prefs.schemeVariantName) {
        runCatching { SchemeVariant.valueOf(prefs.schemeVariantName) }.getOrDefault(SchemeVariant.TONAL_SPOT)
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("外观", style = MaterialTheme.typography.headlineMedium) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    LiquidGlassBackButton(onClick = onBack)
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassSectionHeader("显示模式")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DarkMode.entries.forEach { mode ->
                    LiquidGlassChip(
                        label = mode.label(),
                        selected = prefs.darkMode == mode,
                        onClick = { vm.setDarkMode(mode) },
                    )
                }
            }

            GlassSectionHeader("玻璃质感")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GlassStyle.entries.forEach { style ->
                    LiquidGlassChip(
                        label = style.label(),
                        selected = prefs.glassStyle == style,
                        onClick = { vm.setGlassStyle(style) },
                    )
                }
            }

            GlassSectionHeader("主题色")
            GlassListCard {
                ORDERED_THEME_PRESETS.forEachIndexed { index, preset ->
                    PresetRow(
                        preset = preset,
                        customSeedArgb = prefs.customThemeSeedArgb,
                        selected = prefs.themePreset == preset,
                        dark = effectiveDark,
                        variant = currentVariant,
                        darkContrast = prefs.darkContrast,
                        darkTinted = prefs.darkTinted,
                        onClick = { vm.setPreset(preset) },
                    )
                    if (index < ORDERED_THEME_PRESETS.lastIndex) {
                        GlassInsetDivider(startIndent = 76.dp)
                    }
                }
            }

            Text(
                text = "主题色会同步到桌面小组件",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )

            AnimatedVisibility(visible = prefs.themePreset == ThemePreset.CUSTOM) {
                CustomThemeColorCard(
                    currentSeedArgb = prefs.customThemeSeedArgb,
                    onPickArgb = vm::setCustomSeed,
                )
            }

            GlassSectionHeader("色彩风格")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SchemeVariant.entries.forEach { v ->
                    LiquidGlassChip(
                        label = v.label,
                        selected = currentVariant == v,
                        onClick = { vm.setSchemeVariant(v) },
                    )
                }
            }

            GlassSectionHeader("深色对比度")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DarkContrast.entries.forEach { level ->
                    LiquidGlassChip(
                        label = level.label(),
                        selected = prefs.darkContrast == level,
                        onClick = { vm.setDarkContrast(level) },
                    )
                }
            }

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("深色背景带主题色调", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "深色模式下背景微带主题色，减少纯黑的压抑感",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = prefs.darkTinted,
                        onCheckedChange = { vm.setDarkTinted(it) },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 一行配色：左侧模切叠放三色圆点一组，中间名称，右侧选中对勾 */
@Composable
private fun PresetRow(
    preset: ThemePreset,
    customSeedArgb: Int,
    selected: Boolean,
    dark: Boolean,
    variant: SchemeVariant,
    darkContrast: DarkContrast,
    darkTinted: Boolean,
    onClick: () -> Unit,
) {
    val swatches = rememberPresetSwatches(
        preset = preset,
        customSeedArgb = customSeedArgb,
        variant = variant,
        darkContrast = darkContrast,
        darkTinted = darkTinted,
    )
    val cutoutBorderColor = if (dark) Color(0xFF1B2322) else Color(0xFFF9FAF8)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .glassClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(width = 48.dp, height = 22.dp)) {
            swatches.forEachIndexed { index, color ->
                Swatch(
                    color = color,
                    offsetIndex = index,
                    cutoutBorderColor = cutoutBorderColor,
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = preset.label(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (preset == ThemePreset.ZZU) {
                Text(
                    text = "取自郑州大学校徽 · 墨青与枣红",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "已选中",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** 三个模切叠放圆点（22dp 直径 + 2dp 卡片底色描边） */
@Composable
private fun Swatch(color: Color, offsetIndex: Int, cutoutBorderColor: Color) {
    Box(
        Modifier
            .padding(start = (offsetIndex * 13).dp)
            .size(22.dp)
            .clip(CircleShape)
            .background(color)
            .border(2.dp, cutoutBorderColor, CircleShape),
    )
}

/** 计算预设代表三色：ZZU 在深浅模式下固定校徽原色，其余统一以浅色基调展示稳定身份 */
@Composable
private fun rememberPresetSwatches(
    preset: ThemePreset,
    customSeedArgb: Int,
    variant: SchemeVariant,
    darkContrast: DarkContrast,
    darkTinted: Boolean,
): List<Color> {
    if (preset == ThemePreset.ZZU) {
        return listOf(Color(0xFF223C3B), Color(0xFFEDE9DF), Color(0xFF730917))
    }
    val scheme = rememberAppColorScheme(
        preset = preset,
        dark = false,
        customSeedArgb = customSeedArgb,
        variant = variant,
        darkContrast = darkContrast,
        darkTinted = darkTinted,
    )
    return listOf(scheme.primary, scheme.primaryContainer, scheme.tertiary)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomThemeColorCard(
    currentSeedArgb: Int,
    onPickArgb: (Int) -> Unit,
) {
    val initHsv = remember(currentSeedArgb) {
        FloatArray(3).also { AndroidColor.colorToHSV(currentSeedArgb, it) }
    }
    var hue by remember(currentSeedArgb) { mutableFloatStateOf(initHsv[0]) }
    var sat by remember(currentSeedArgb) { mutableFloatStateOf(initHsv[1].coerceIn(0.35f, 1f)) }
    var hexInput by remember(currentSeedArgb) {
        mutableStateOf(String.format(Locale.US, "%06X", currentSeedArgb and 0x00FFFFFF))
    }

    fun commitHsv(newH: Float, newS: Float) {
        hue = newH
        sat = newS
        val argb = AndroidColor.HSVToColor(floatArrayOf(newH, newS, 0.92f))
        hexInput = String.format(Locale.US, "%06X", argb and 0x00FFFFFF)
        onPickArgb(argb)
    }

    val previewColor = remember(hue, sat) {
        Color(AndroidColor.HSVToColor(floatArrayOf(hue, sat, 0.92f)))
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Palette,
                    contentDescription = null,
                    tint = previewColor,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("自定义主题取色器", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(previewColor)
                        .border(1.dp, Color.White.copy(alpha = 0.85f), CircleShape),
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QUICK_CUSTOM_SWATCHES.forEach { argb ->
                    val isCurrent = (argb and 0x00FFFFFF) == (currentSeedArgb and 0x00FFFFFF)
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(argb))
                            .border(
                                BorderStroke(
                                    if (isCurrent) 2.dp else 0.8.dp,
                                    Color.White.copy(alpha = if (isCurrent) 0.96f else 0.65f),
                                ),
                                CircleShape,
                            )
                            .clickable { onPickArgb(argb) },
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFFFF3B30),
                                Color(0xFFFFCC00),
                                Color(0xFF34C759),
                                Color(0xFF00C7BE),
                                Color(0xFF007AFF),
                                Color(0xFFAF52DE),
                                Color(0xFFFF2D55),
                                Color(0xFFFF3B30),
                            ),
                        ),
                    ),
            )
            Slider(
                value = hue,
                onValueChange = { h -> commitHsv(h, sat) },
                valueRange = 0f..359f,
                colors = SliderDefaults.colors(
                    thumbColor = previewColor,
                    activeTrackColor = previewColor,
                ),
            )

            Text(
                text = "色彩鲜艳度 ${(sat * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = sat,
                onValueChange = { s -> commitHsv(hue, s) },
                valueRange = 0.35f..1.0f,
                colors = SliderDefaults.colors(
                    thumbColor = previewColor,
                    activeTrackColor = previewColor,
                ),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { raw ->
                        val cleaned = raw.uppercase(Locale.US).filter { it in '0'..'9' || it in 'A'..'F' }.take(6)
                        hexInput = cleaned
                        if (cleaned.length == 6) {
                            runCatching {
                                val parsed = ("FF$cleaned").toLong(16).toInt()
                                onPickArgb(parsed)
                            }
                        }
                    },
                    prefix = { Text("#") },
                    label = { Text("HEX 色码") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                LiquidGlassOutlinedButton(
                    onClick = {
                        if (hexInput.length == 6) {
                            runCatching {
                                val parsed = ("FF$hexInput").toLong(16).toInt()
                                onPickArgb(parsed)
                            }
                        }
                    },
                    enabled = hexInput.length == 6,
                ) {
                    Text("应用")
                }
            }
        }
    }
}
