package com.zzupocket.app.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.zzupocket.app.MainActivity
import com.zzupocket.app.R
import com.zzupocket.app.core.AppGraph
import com.zzupocket.app.data.gms.GmsScheduleMapper
import com.zzupocket.app.data.prefs.UiPrefsStore
import com.zzupocket.app.data.repo.ScheduleRepository
import com.zzupocket.app.data.repo.TodayTimetable
import com.zzupocket.app.domain.ConflictDisplayChoice
import com.zzupocket.app.ui.nav.AppRoutes
import java.time.LocalDate
import kotlinx.coroutines.flow.first

/**
 * 上课前通知提醒调度器（Issue #23）。
 *
 * 使用 `AlarmManager.setAndAllowWhileIdle` 预约单次闹钟（无需额外申请 `SCHEDULE_EXACT_ALARM` 权限）；
 * 每次响铃后在广播接收器里链式计算并预约下一节课。
 */
class ClassReminderScheduler(
    private val context: Context,
    private val scheduleRepo: ScheduleRepository,
    private val uiPrefs: UiPrefsStore,
) {

    fun ensureChannel() {
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        mgr.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "上课前提醒",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "开课前按设定时间推送课程与教室提醒"
            },
        )
    }

    /** 根据本地课表重新规划下一次课前提醒闹钟。 */
    suspend fun reschedule(nowMillis: Long = System.currentTimeMillis()) {
        val prefs = runCatching { uiPrefs.flow.first() }.getOrNull() ?: return
        if (!prefs.classReminderEnabled) {
            cancel()
            return
        }
        val target = computeNextTarget(
            leadMinutes = prefs.classReminderMinutes,
            lastFiredKey = prefs.classReminderLastFiredKey,
            shortenNames = prefs.smartShortCourseName,
            nowMillis = nowMillis,
        )
        if (target == null) {
            cancel()
            return
        }
        scheduleAlarm(target)
    }

    /** 闹钟触发时弹出通知，记录防重键，并顺手预约下一节课。 */
    suspend fun onAlarmFired(intent: Intent) {
        val prefs = runCatching { uiPrefs.flow.first() }.getOrNull() ?: return
        if (!prefs.classReminderEnabled) return

        val sessionKey = intent.getStringExtra(EXTRA_SESSION_KEY).orEmpty()
        val courseName = intent.getStringExtra(EXTRA_COURSE_NAME).orEmpty()
        val roomText = intent.getStringExtra(EXTRA_ROOM_TEXT).orEmpty()
        val startClock = intent.getStringExtra(EXTRA_START_CLOCK).orEmpty()
        val unitText = intent.getStringExtra(EXTRA_UNIT_TEXT).orEmpty()
        val leadMinutes = intent.getIntExtra(EXTRA_LEAD_MINUTES, prefs.classReminderMinutes)

        if (sessionKey.isNotBlank() && sessionKey != prefs.classReminderLastFiredKey && courseName.isNotBlank()) {
            ensureChannel()
            showNotification(
                courseName = courseName,
                roomText = roomText,
                startClock = startClock,
                unitText = unitText,
                leadMinutes = leadMinutes,
                notifId = (sessionKey.hashCode() and 0x7FFFFFFF).coerceAtLeast(2000),
            )
            uiPrefs.setClassReminderLastFiredKey(sessionKey)
        }
        reschedule()
    }

    fun cancel() {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching { am.cancel(alarmIntent(null)) }
    }

    private suspend fun computeNextTarget(
        leadMinutes: Int,
        lastFiredKey: String?,
        shortenNames: Boolean,
        nowMillis: Long,
    ): ClassReminderTarget? {
        val periods = runCatching { scheduleRepo.periods() }.getOrDefault(emptyList())
        val today = LocalDate.now()
        val days = (0L..6L).map { offset ->
            val date = today.plusDays(offset)
            val sessions = runCatching {
                TodayTimetable.sessionsOn(scheduleRepo, uiPrefs, date)
            }.getOrDefault(emptyList())
            date to sessions
        }
        if (days.all { it.second.isEmpty() }) return null

        val choicesBySemester = HashMap<Int, List<ConflictDisplayChoice>>()
        runCatching { scheduleRepo.currentSemester()?.id }
            .getOrNull()?.let { id ->
                choicesBySemester[id] = runCatching { scheduleRepo.conflictDisplayChoices(id) }.getOrDefault(emptyList())
            }
        choicesBySemester[GmsScheduleMapper.GRAD_SEMESTER_ID] = runCatching {
            scheduleRepo.conflictDisplayChoices(GmsScheduleMapper.GRAD_SEMESTER_ID)
        }.getOrDefault(emptyList())

        return ClassReminderPlanner.nextTarget(
            daySessions = days,
            periods = periods,
            choicesBySemester = choicesBySemester,
            leadMinutes = leadMinutes,
            nowMillis = nowMillis,
            lastFiredKey = lastFiredKey,
            shortenNames = shortenNames,
        )
    }

    private fun scheduleAlarm(target: ClassReminderTarget) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = alarmIntent(target)
        runCatching { am.cancel(pi) }
        runCatching {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.triggerAtMillis, pi)
        }.onFailure {
            Log.w(TAG, "设置课前提醒闹钟失败", it)
        }
    }

    private fun alarmIntent(target: ClassReminderTarget?): PendingIntent {
        val intent = Intent(context, ClassReminderReceiver::class.java).apply {
            action = ACTION_CLASS_REMINDER
            if (target != null) {
                putExtra(EXTRA_SESSION_KEY, target.sessionKey)
                putExtra(EXTRA_COURSE_NAME, target.courseName)
                putExtra(EXTRA_ROOM_TEXT, target.roomText)
                putExtra(EXTRA_START_CLOCK, target.startClock)
                putExtra(EXTRA_UNIT_TEXT, target.unitRangeText)
                putExtra(EXTRA_LEAD_MINUTES, target.leadMinutes)
            }
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun showNotification(
        courseName: String,
        roomText: String,
        startClock: String,
        unitText: String,
        leadMinutes: Int,
        notifId: Int,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        val title = if (roomText.isNotBlank()) {
            "$courseName · $roomText"
        } else {
            courseName
        }
        val body = buildString {
            append("${startClock.ifBlank { unitText }} 上课")
            if (unitText.isNotBlank() && startClock.isNotBlank()) append("（$unitText）")
            append(" · 约 $leadMinutes 分钟后开始")
        }
        val openIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE + 1,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(AppRoutes.EXTRA_ROUTE, AppRoutes.SCHEDULE)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(notifId, notification)
        }
    }

    companion object {
        private const val TAG = "ClassReminder"
        const val CHANNEL_ID = "class_reminder"
        const val ACTION_CLASS_REMINDER = "com.zzupocket.app.action.CLASS_REMINDER"
        private const val REQUEST_CODE = 5301

        private const val EXTRA_SESSION_KEY = "session_key"
        private const val EXTRA_COURSE_NAME = "course_name"
        private const val EXTRA_ROOM_TEXT = "room_text"
        private const val EXTRA_START_CLOCK = "start_clock"
        private const val EXTRA_UNIT_TEXT = "unit_text"
        private const val EXTRA_LEAD_MINUTES = "lead_minutes"
    }
}
