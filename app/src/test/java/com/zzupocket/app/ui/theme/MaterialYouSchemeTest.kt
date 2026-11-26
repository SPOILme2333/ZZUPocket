package com.zzupocket.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 配色生成器的回归测试。
 *
 * 这层是纯计算（不碰 Android 框架），所以能在普通单元测试里跑：
 * 换依赖版本、或以后手改映射表时，这几条能立刻发现"配色算错了"。
 */
class MaterialYouSchemeTest {

    private val blue = 0xFF1A73E8.toInt()
    private val green = 0xFF1E8E3E.toInt()

    @Test
    fun `同一个种子色能算出浅色与深色两套配色`() {
        val light = MaterialYouScheme.fromSeed(blue, dark = false)
        val dark = MaterialYouScheme.fromSeed(blue, dark = true)

        // 深色的底色必须比浅色暗
        assertTrue(
            "深色 surface 亮度应低于浅色",
            dark.surface.luminance() < light.surface.luminance(),
        )
        // 两套配色的文字色也必须相反
        assertTrue(
            "浅色 onSurface 应比深色 onSurface 暗",
            light.onSurface.luminance() < dark.onSurface.luminance(),
        )
    }

    @Test
    fun `不同种子色给出不同的主色`() {
        val a = MaterialYouScheme.fromSeed(blue, dark = false)
        val b = MaterialYouScheme.fromSeed(green, dark = false)
        assertNotEquals(a.primary, b.primary)
        assertNotEquals(a.primaryContainer, b.primaryContainer)
    }

    @Test
    fun `深色配色的底色是暗的、浅色配色的底色是亮的`() {
        val light = MaterialYouScheme.fromSeed(blue, dark = false)
        val dark = MaterialYouScheme.fromSeed(blue, dark = true)
        assertTrue("浅色 surface 应偏亮：${light.surface.luminance()}", light.surface.luminance() > 0.5f)
        assertTrue("深色 surface 应偏暗：${dark.surface.luminance()}", dark.surface.luminance() < 0.3f)
    }

    @Test
    fun `几种配色变体都能算出来且互不相同`() {
        val schemes = SchemeVariant.entries.map { variant ->
            MaterialYouScheme.fromSeed(blue, dark = false, variant = variant)
        }
        schemes.forEach { assertTrue("配色不能是透明的", it.primary.alpha > 0f) }
        // TONAL_SPOT 与 EXPRESSIVE 属于不同算法，主色不应一样
        assertNotEquals(schemes[0].primary, schemes[2].primary)
    }

    @Test
    fun `关键角色成对存在_文字色与底色不会撞在一起`() {
        val scheme = MaterialYouScheme.fromSeed(blue, dark = true)
        assertNotEquals(scheme.primary, scheme.onPrimary)
        assertNotEquals(scheme.surface, scheme.onSurface)
        assertNotEquals(scheme.error, scheme.onError)
    }

    @Test
    fun `郑大校徽专属双色方案精准还原墨青与枣红且浅深对比正常`() {
        val light = MaterialYouScheme.zzuEmblemScheme(dark = false)
        assertEquals(Color(0xFF223C3B), light.primary)
        assertEquals(Color(0xFF730917), light.tertiary)
        assertEquals(Color(0xFF730917), light.error)
        assertEquals(Color(0xFFF4F5F2), light.background)
        assertTrue("浅色 primary 应偏深以保证白字清晰", light.primary.luminance() < 0.2f)

        val dark = MaterialYouScheme.zzuEmblemScheme(dark = true)
        assertEquals(Color(0xFFA9C9C5), dark.primary)
        assertEquals(Color(0xFF2A4644), dark.primaryContainer)
        assertEquals(Color(0xFFD4656F), dark.tertiary)
        assertEquals(Color(0xFFD4656F), dark.error)
        assertTrue("深色 primary 应偏亮以保证可读性", dark.primary.luminance() > 0.35f)
    }
}
