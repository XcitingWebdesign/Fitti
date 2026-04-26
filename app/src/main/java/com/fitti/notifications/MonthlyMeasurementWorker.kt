package com.fitti.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fitti.data.FittiDatabase
import com.fitti.data.SettingsRepository
import com.fitti.ui.common.parseDateTime
import java.util.concurrent.TimeUnit

class MonthlyMeasurementWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!hasPostNotificationsPermission(applicationContext)) return Result.success()
        if (!SettingsRepository(applicationContext).monthlyMeasurementReminderEnabled) return Result.success()

        val db = FittiDatabase.create(applicationContext)
        val latest = db.bodyMeasurementDao().getLatest()
        val now = System.currentTimeMillis()
        val daysSinceLast = if (latest == null) {
            Long.MAX_VALUE
        } else {
            val date = parseDateTime(latest.measuredAt) ?: return Result.success()
            TimeUnit.MILLISECONDS.toDays(now - date.time)
        }

        if (daysSinceLast >= 30) {
            val text = if (latest == null) {
                "Tape-Maße noch nicht erfasst — Brust, Bauch, Bizeps."
            } else {
                "Tape-Maße seit ${daysSinceLast} Tagen — Zeit für eine neue Messung."
            }
            showNotification(applicationContext, NOTIF_ID_MEAS, "Fitti Coach", text)
        }
        return Result.success()
    }

    companion object {
        private const val NOTIF_ID_MEAS = 1002
    }
}
