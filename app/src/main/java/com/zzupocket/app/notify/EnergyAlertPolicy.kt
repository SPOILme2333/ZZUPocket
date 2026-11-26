package com.zzupocket.app.notify

/**
 * 宿舍低电量预警防骚扰策略（纯函数，易于单测）。
 *
 * 规则：
 * - 任一已配置表计度数低于 [thresholdKwh] 时判定为低电量；
 * - 首次跌破阈值立即提醒；
 * - 持续低电量时至少间隔 [REPEAT_COOLDOWN_MS]（24 小时）才再次提醒；
 * - 充值后所有表计回升到阈值及以上时，自动重置提醒记录，下次跌破可立即再次提醒。
 */
object EnergyAlertPolicy {

    const val REPEAT_COOLDOWN_MS = 24 * 60 * 60 * 1000L

    data class Decision(
        val shouldNotify: Boolean,
        val resetCooldown: Boolean,
        val lightLow: Boolean,
        val acLow: Boolean,
    )

    fun evaluate(
        enabled: Boolean,
        thresholdKwh: Double,
        lightKwh: Double?,
        acKwh: Double?,
        lastAlertAtMs: Long,
        nowMs: Long,
    ): Decision {
        val lightLow = lightKwh != null && lightKwh < thresholdKwh
        val acLow = acKwh != null && acKwh < thresholdKwh
        val anyLow = lightLow || acLow

        if (!anyLow) {
            return Decision(
                shouldNotify = false,
                resetCooldown = lastAlertAtMs > 0L,
                lightLow = false,
                acLow = false,
            )
        }
        if (!enabled) {
            return Decision(
                shouldNotify = false,
                resetCooldown = false,
                lightLow = lightLow,
                acLow = acLow,
            )
        }
        val cooldownPassed = lastAlertAtMs <= 0L || (nowMs - lastAlertAtMs) >= REPEAT_COOLDOWN_MS
        return Decision(
            shouldNotify = cooldownPassed,
            resetCooldown = false,
            lightLow = lightLow,
            acLow = acLow,
        )
    }
}
