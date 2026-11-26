package com.zzupocket.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.zzupocket.app.MainActivity
import com.zzupocket.app.R
import com.zzupocket.app.data.prefs.UiPrefsStore
import com.zzupocket.app.data.repo.ElectricityRepository
import com.zzupocket.app.ui.nav.AppRoutes
import java.util.Locale
import kotlinx.coroutines.flow.first

/**
 * 宿舍低电量预警通知器。
 *
 * 在后台采样完成后按 [EnergyAlertPolicy] 判断是否发送预警通知并持久化冷却状态。
 */
object EnergyAlertNotifier {

    private const val CHANNEL_ID = "low_energy_alert"
    private const val NOTIF_ID = 5401

    fun ensureChannel(context: Context) {
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        mgr.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "宿舍低电量预警",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "宿舍照明或空调剩余电量低于设定阈值时提醒"
            },
        )
    }

    suspend fun evaluateAndNotify(
        context: Context,
        uiPrefs: UiPrefsStore,
        snapshot: ElectricityRepository.Snapshot,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        val prefs = runCatching { uiPrefs.flow.first() }.getOrNull() ?: return
        val decision = EnergyAlertPolicy.evaluate(
            enabled = prefs.lowEnergyAlertEnabled,
            thresholdKwh = prefs.lowEnergyThresholdKwh.toDouble(),
            lightKwh = snapshot.light.kwh,
            acKwh = snapshot.ac.kwh,
            lastAlertAtMs = prefs.lowEnergyLastAlertAt,
            nowMs = nowMillis,
        )
        if (decision.resetCooldown) {
            uiPrefs.setLowEnergyLastAlertAt(0L)
            return
        }
        if (!decision.shouldNotify) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        ensureChannel(context)
        val parts = buildList {
            if (decision.lightLow) {
                add("照明剩余 ${formatKwh(snapshot.light.kwh ?: 0.0)} 度")
            }
            if (decision.acLow) {
                add("空调剩余 ${formatKwh(snapshot.ac.kwh ?: 0.0)} 度")
            }
        }
        val body = parts.joinToString("，") + "（已低于 ${prefs.lowEnergyThresholdKwh} 度预警线），请及时充值。"
        val openIntent = PendingIntent.getActivity(
            context,
            NOTIF_ID,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(AppRoutes.EXTRA_ROUTE, AppRoutes.ELECTRICITY)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle("宿舍电量偏低")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIF_ID, notification)
            uiPrefs.setLowEnergyLastAlertAt(nowMillis)
        }
    }

    private fun formatKwh(v: Double): String = String.format(Locale.US, "%.2f", v)
}
