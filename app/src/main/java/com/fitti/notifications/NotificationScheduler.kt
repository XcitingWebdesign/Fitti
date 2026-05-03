package com.fitti.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.fitti.data.SettingsRepository
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Schedules and cancels Fitti's three periodic notifications:
 *  - Daily protein/weight reminder
 *  - Monthly tape-measurement reminder
 *  - Weekly coaching reminder
 *
 * Triggered from [com.fitti.MainActivity.onCreate] and from the
 * Settings screen toggles.
 */
object NotificationScheduler {

    const val CHANNEL_ID = "fitti_coach"
    const val CHANNEL_ID_TIMER = "fitti_rest_timer"
    const val WORK_DAILY = "fitti_daily_reminder"
    const val WORK_MONTHLY_MEAS = "fitti_monthly_measurement"
    const val WORK_WEEKLY_COACH = "fitti_weekly_coaching"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Fitti Coach",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Tägliche Erinnerungen vom Fitti Coach"
                }
                mgr.createNotificationChannel(channel)
            }
            if (mgr.getNotificationChannel(CHANNEL_ID_TIMER) == null) {
                val timerChannel = NotificationChannel(
                    CHANNEL_ID_TIMER,
                    "Pause-Timer",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Zeigt den laufenden Pause-Timer und meldet das Ende mit Ton."
                    enableVibration(true)
                }
                mgr.createNotificationChannel(timerChannel)
            }
        }
    }

    fun applyAll(context: Context, settings: SettingsRepository) {
        ensureChannel(context)
        val wm = WorkManager.getInstance(context)

        if (settings.dailyReminderEnabled) {
            wm.enqueueUniquePeriodicWork(
                WORK_DAILY,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<DailyReminderWorker>(1, TimeUnit.DAYS)
                    .setInitialDelay(initialDelayUntil(settings.dailyReminderHour, 0), TimeUnit.MILLISECONDS)
                    .build()
            )
        } else {
            wm.cancelUniqueWork(WORK_DAILY)
        }

        if (settings.monthlyMeasurementReminderEnabled) {
            wm.enqueueUniquePeriodicWork(
                WORK_MONTHLY_MEAS,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<MonthlyMeasurementWorker>(7, TimeUnit.DAYS)
                    .setInitialDelay(initialDelayUntil(9, 0), TimeUnit.MILLISECONDS)
                    .build()
            )
        } else {
            wm.cancelUniqueWork(WORK_MONTHLY_MEAS)
        }

        if (settings.weeklyCoachingReminderEnabled) {
            wm.enqueueUniquePeriodicWork(
                WORK_WEEKLY_COACH,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<WeeklyCoachingWorker>(1, TimeUnit.DAYS)
                    .setInitialDelay(initialDelayUntil(18, 0), TimeUnit.MILLISECONDS)
                    .build()
            )
        } else {
            wm.cancelUniqueWork(WORK_WEEKLY_COACH)
        }
    }

    private fun initialDelayUntil(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (!target.after(now)) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis - now.timeInMillis
    }
}
