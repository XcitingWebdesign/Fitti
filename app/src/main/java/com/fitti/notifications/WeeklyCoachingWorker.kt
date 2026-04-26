package com.fitti.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fitti.data.FittiDatabase
import com.fitti.data.SettingsRepository
import com.fitti.ui.common.parseDateTime
import java.util.concurrent.TimeUnit

class WeeklyCoachingWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!hasPostNotificationsPermission(applicationContext)) return Result.success()
        if (!SettingsRepository(applicationContext).weeklyCoachingReminderEnabled) return Result.success()

        val db = FittiDatabase.create(applicationContext)
        val latest = db.aiAnalysisDao().getLatest()
        val now = System.currentTimeMillis()
        val daysSinceLast = if (latest == null) {
            Long.MAX_VALUE
        } else {
            val date = parseDateTime(latest.createdAt) ?: return Result.success()
            TimeUnit.MILLISECONDS.toDays(now - date.time)
        }

        if (daysSinceLast >= 7) {
            val text = if (latest == null) {
                "Noch kein wöchentliches Coaching — jetzt anstoßen."
            } else {
                "Letztes Coaching vor ${daysSinceLast} Tagen — Zeit für ein neues."
            }
            showNotification(applicationContext, NOTIF_ID_COACH, "Fitti Coach", text)
        }
        return Result.success()
    }

    companion object {
        private const val NOTIF_ID_COACH = 1003
    }
}
