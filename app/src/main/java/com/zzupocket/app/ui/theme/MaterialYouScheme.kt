package com.zzupocket.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.zzupocket.app.data.prefs.DarkContrast
import com.materialkolor.dynamiccolor.DynamicColor
import com.materialkolor.dynamiccolor.MaterialDynamicColors
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.scheme.SchemeContent
import com.materialkolor.scheme.SchemeExpressive
import com.materialkolor.scheme.SchemeTonalSpot
import com.materialkolor.scheme.SchemeVibrant

/**
 * 配色风格变体（对标 Android 14+ 的"主题色"风格）。
 * 默认的 TONAL_SPOT 就是系统动态取色用的那套算法，所以它跟原生观感一致。
 */
enum class SchemeVariant(val label: String) {
    TONAL_SPOT("标准"),
    VIBRANT("鲜艳"),
    EXPRESSIVE("表现力"),
    CONTENT("内容色"),
}

/**
 * 由**一个种子色**算出整套 Material 3 配色（浅色 / 深色两套）。
 *
 * 用的是 Google `material-color-utilities`（MIT，Kotlin 移植）：先用 HCT 把种子色摊成
 * 若干色调调色板，再按 M3 的规范取各角色色值 —— 和 Android 系统的动态取色同一套算法，
 * 所以自己给种子色也能得到"原生感"的配色，而不是随手挑的色。
 */
object MaterialYouScheme {

    /** 默认对比度（系统默认档） */
    const val DEFAULT_CONTRAST = 0.0

    /** 郑州大学校徽标准墨青（外环与 Z 字主色） */
    const val ZZU_EMBLEM_TEAL_ARGB: Int = 0xFF223C3B.toInt()

    /** 郑州大学校徽标准深红（中央 A 字枣红主色） */
    const val ZZU_EMBLEM_RED_ARGB: Int = 0xFF730917.toInt()

    /**
     * 生成郑州大学校徽专属双色主题（墨青 `#223C3B` × 枣红 `#730917`）。
     *
     * 默认标准对比度下直接锚定校徽真实色值并确保高对比度；高对比度档由 MCU 算法自动拉高对比。
     */
    fun zzuEmblemScheme(
        dark: Boolean,
        variant: SchemeVariant = SchemeVariant.TONAL_SPOT,
        contrastLevel: Double = DEFAULT_CONTRAST,
    ): ColorScheme {
        val base = fromSeed(
            seedArgb = ZZU_EMBLEM_TEAL_ARGB,
            dark = dark,
            variant = variant,
            contrastLevel = contrastLevel,
        )
        val redAccent = fromSeed(
            seedArgb = ZZU_EMBLEM_RED_ARGB,
            dark = dark,
            variant = variant,
            contrastLevel = contrastLevel,
        )
        val merged = base.copy(
            tertiary = redAccent.primary,
            onTertiary = redAccent.onPrimary,
            tertiaryContainer = redAccent.primaryContainer,
            onTertiaryContainer = redAccent.onPrimaryContainer,
        )
        if (contrastLevel != DEFAULT_CONTRAST) {
            return merged.copy(
                error = redAccent.primary,
                onError = redAccent.onPrimary,
                errorContainer = redAccent.primaryContainer,
                onErrorContainer = redAccent.onPrimaryContainer,
            )
        }
        return if (dark) {
            merged.copy(
                primary = Color(0xFFA9C9C5),
                onPrimary = Color(0xFF0F2422),
                primaryContainer = Color(0xFF2A4644),
                onPrimaryContainer = Color(0xFFD2E6E3),
                secondary = Color(0xFFB3C6C3),
                secondaryContainer = Color(0xFF33403E),
                tertiary = Color(0xFFD4656F),
                onTertiary = Color(0xFF3B0309),
                tertiaryContainer = Color(0xFF5A0F19),
                onTertiaryContainer = Color(0xFFF6D9DC),
                error = Color(0xFFD4656F),
                onError = Color(0xFF3B0309),
                errorContainer = Color(0xFF5A0F19),
                onErrorContainer = Color(0xFFF6D9DC),
                background = Color(0xFF0D1312),
                onBackground = Color(0xFFE4EAE9),
                surface = Color(0xFF0D1312),
                onSurface = Color(0xFFE4EAE9),
                surfaceVariant = Color(0xFF222B2A),
                onSurfaceVariant = Color(0xFFA1ACAA),
                outline = Color(0xFF6E7977),
                outlineVariant = Color(0xFF2C3534),
                surfaceContainerLow = Color(0xFF121918),
                surfaceContainer = Color(0xFF161D1C),
                surfaceContainerHigh = Color(0xFF1C2423),
                surfaceContainerHighest = Color(0xFF222B2A),
            )
        } else {
            merged.copy(
                primary = Color(0xFF223C3B),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFDDE6E4),
                onPrimaryContainer = Color(0xFF142826),
                secondary = Color(0xFF4E6361),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFE3EAE8),
                onSecondaryContainer = Color(0xFF1A2725),
                tertiary = Color(0xFF730917),
                onTertiary = Color.White,
                tertiaryContainer = Color(0xFFF3E3E4),
                onTertiaryContainer = Color(0xFF4A0610),
                error = Color(0xFF730917),
                onError = Color.White,
                errorContainer = Color(0xFFF3E3E4),
                onErrorContainer = Color(0xFF4A0610),
                background = Color(0xFFF4F5F2),
                onBackground = Color(0xFF141A19),
                surface = Color(0xFFF4F5F2),
                onSurface = Color(0xFF141A19),
                surfaceVariant = Color(0xFFE6EAE8),
                onSurfaceVariant = Color(0xFF5A6462),
                outline = Color(0xFF8C9593),
                outlineVariant = Color(0xFFD5DAD8),
                surfaceContainerLowest = Color.White,
                surfaceContainerLow = Color(0xFFEEF0ED),
                surfaceContainer = Color(0xFFE9ECE9),
                surfaceContainerHigh = Color(0xFFE3E7E4),
                surfaceContainerHighest = Color(0xFFDDE2DF),
            )
        }
    }

    /** 外观页「深色对比度」档位 → MCU contrastLevel（对标系统对比度三档的映射） */
    fun darkContrastLevel(darkContrast: DarkContrast): Double = when (darkContrast) {
        DarkContrast.STANDARD -> 0.0
        DarkContrast.MEDIUM -> 0.5
        DarkContrast.HIGH -> 1.0
    }

    /**
     * 「深色保留颜色」的表达：深色列表的表面角色统一染色（往主色靠 20%，
     * 保住壁纸/种子色的色相）并轻微上抬（加白 7%），底不再是深灰近黑。
     *
     * 只动背景/表面系角色，文字角色一概不碰——
     * 文字与表面的明度差还被加白拉大了一档，对比反而更宽松。
     */
    internal fun tintedSurfaces(scheme: ColorScheme): ColorScheme {
        val accent = scheme.primary
        fun lift(c: Color): Color = lerp(lerp(c, accent, 0.20f), Color.White, 0.07f)
        return scheme.copy(
            background = lift(scheme.background),
            surface = lift(scheme.surface),
            surfaceDim = lift(scheme.surfaceDim),
            surfaceBright = lift(scheme.surfaceBright),
            surfaceContainerLowest = lift(scheme.surfaceContainerLowest),
            surfaceContainerLow = lift(scheme.surfaceContainerLow),
            surfaceContainer = lift(scheme.surfaceContainer),
            surfaceContainerHigh = lift(scheme.surfaceContainerHigh),
            surfaceContainerHighest = lift(scheme.surfaceContainerHighest),
            surfaceVariant = lift(scheme.surfaceVariant),
            surfaceTint = scheme.primary,
        )
    }

    fun fromSeed(
        seedArgb: Int,
        dark: Boolean,
        variant: SchemeVariant = SchemeVariant.TONAL_SPOT,
        contrastLevel: Double = DEFAULT_CONTRAST,
    ): ColorScheme {
        val scheme = dynamicScheme(seedArgb, dark, variant, contrastLevel)
        val colors = MaterialDynamicColors()
        fun role(color: DynamicColor): Color = Color(color.getArgb(scheme))
        return ColorScheme(
            primary = role(colors.primary()),
            onPrimary = role(colors.onPrimary()),
            primaryContainer = role(colors.primaryContainer()),
            onPrimaryContainer = role(colors.onPrimaryContainer()),
            inversePrimary = role(colors.inversePrimary()),

            secondary = role(colors.secondary()),
            onSecondary = role(colors.onSecondary()),
            secondaryContainer = role(colors.secondaryContainer()),
            onSecondaryContainer = role(colors.onSecondaryContainer()),

            tertiary = role(colors.tertiary()),
            onTertiary = role(colors.onTertiary()),
            tertiaryContainer = role(colors.tertiaryContainer()),
            onTertiaryContainer = role(colors.onTertiaryContainer()),

            background = role(colors.background()),
            onBackground = role(colors.onBackground()),

            surface = role(colors.surface()),
            onSurface = role(colors.onSurface()),
            surfaceVariant = role(colors.surfaceVariant()),
            onSurfaceVariant = role(colors.onSurfaceVariant()),
            surfaceTint = role(colors.surfaceTint()),
            inverseSurface = role(colors.inverseSurface()),
            inverseOnSurface = role(colors.inverseOnSurface()),
            surfaceBright = role(colors.surfaceBright()),
            surfaceDim = role(colors.surfaceDim()),
            surfaceContainer = role(colors.surfaceContainer()),
            surfaceContainerHigh = role(colors.surfaceContainerHigh()),
            surfaceContainerHighest = role(colors.surfaceContainerHighest()),
            surfaceContainerLow = role(colors.surfaceContainerLow()),
            surfaceContainerLowest = role(colors.surfaceContainerLowest()),

            error = role(colors.error()),
            onError = role(colors.onError()),
            errorContainer = role(colors.errorContainer()),
            onErrorContainer = role(colors.onErrorContainer()),

            outline = role(colors.outline()),
            outlineVariant = role(colors.outlineVariant()),
            scrim = role(colors.scrim()),
            // 注：primaryFixed / secondaryFixed / tertiaryFixed 那一组角色
            // 在当前的 Compose Material3 版本里还不是构造参数，本 App 也没用到，故不映射。
        )
    }

    internal fun dynamicScheme(
        seedArgb: Int,
        dark: Boolean,
        variant: SchemeVariant,
        contrastLevel: Double,
    ): DynamicScheme {
        val hct = Hct.fromInt(seedArgb)
        return when (variant) {
            SchemeVariant.TONAL_SPOT -> SchemeTonalSpot(hct, dark, contrastLevel)
            SchemeVariant.VIBRANT -> SchemeVibrant(hct, dark, contrastLevel)
            SchemeVariant.EXPRESSIVE -> SchemeExpressive(hct, dark, contrastLevel)
            SchemeVariant.CONTENT -> SchemeContent(hct, dark, contrastLevel)
        }
    }
}
