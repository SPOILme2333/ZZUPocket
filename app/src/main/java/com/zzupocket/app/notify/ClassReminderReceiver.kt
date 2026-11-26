package com.zzupocket.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.zzupocket.app.core.AppGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 接收课前提醒闹钟与系统开机/升级/改时广播，重新安排下一次课前通知。
 */
class ClassReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppGraph.init(context.applicationContext)
                when (action) {
                    ClassReminderScheduler.ACTION_CLASS_REMINDER -> {
                        AppGraph.classReminderScheduler.onAlarmFired(intent)
                    }
                    Intent.ACTION_BOOT_COMPLETED,
                    Intent.ACTION_MY_PACKAGE_REPLACED,
                    Intent.ACTION_TIME_CHANGED,
                    Intent.ACTION_TIMEZONE_CHANGED,
                    -> {
                        AppGraph.classReminderScheduler.reschedule()
                    }
                }
            } catch (t: Throwable) {
                Log.w("ClassReminderReceiver", "处理课前提醒广播失败: $action", t)
            } finally {
                pending.finish()
            }
        }
    }
}
