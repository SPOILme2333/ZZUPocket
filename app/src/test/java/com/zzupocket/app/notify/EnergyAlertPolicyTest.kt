package com.zzupocket.app.notify

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 宿舍低电量预警防骚扰策略单测 */
class EnergyAlertPolicyTest {

    @Test
    fun `低于阈值首次提醒且冷却期内不重复打扰`() {
        val t0 = 1_000_000L
        val first = EnergyAlertPolicy.evaluate(
            enabled = true,
            thresholdKwh = 10.0,
            lightKwh = 8.5,
            acKwh = 25.0,
            lastAlertAtMs = 0L,
            nowMs = t0,
        )
        assertTrue(first.shouldNotify)
        assertTrue(first.lightLow)
        assertFalse(first.acLow)
        assertFalse(first.resetCooldown)

        // 1 小时后仍在低电量 → 不重复推送
        val withinCooldown = EnergyAlertPolicy.evaluate(
            enabled = true,
            thresholdKwh = 10.0,
            lightKwh = 7.8,
            acKwh = 24.0,
            lastAlertAtMs = t0,
            nowMs = t0 + 3_600_000L,
        )
        assertFalse(withinCooldown.shouldNotify)

        // 超过 24 小时仍在低电量 → 再次提醒
        val afterCooldown = EnergyAlertPolicy.evaluate(
            enabled = true,
            thresholdKwh = 10.0,
            lightKwh = 6.2,
            acKwh = 24.0,
            lastAlertAtMs = t0,
            nowMs = t0 + EnergyAlertPolicy.REPEAT_COOLDOWN_MS,
        )
        assertTrue(afterCooldown.shouldNotify)
    }

    @Test
    fun `充值恢复正常水量后自动清零冷却记录`() {
        val recharged = EnergyAlertPolicy.evaluate(
            enabled = true,
            thresholdKwh = 10.0,
            lightKwh = 65.0,
            acKwh = 50.0,
            lastAlertAtMs = 1_000_000L,
            nowMs = 2_000_000L,
        )
        assertFalse(recharged.shouldNotify)
        assertTrue(recharged.resetCooldown)
    }

    @Test
    fun `功能关闭时不发送通知`() {
        val disabled = EnergyAlertPolicy.evaluate(
            enabled = false,
            thresholdKwh = 15.0,
            lightKwh = 3.0,
            acKwh = null,
            lastAlertAtMs = 0L,
            nowMs = 1000L,
        )
        assertFalse(disabled.shouldNotify)
        assertTrue(disabled.lightLow)
    }
}
