package com.fitti.notifications

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fitti.data.FittiDatabase
import com.fitti.data.SettingsRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DailyReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!hasPostNotificationsPermission(applicationContext)) return Result.success()
        if (!SettingsRepository(applicationContext).dailyReminderEnabled) return Result.success()

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(Date())
        val db = FittiDatabase.create(applicationContext)
        val log = db.nutritionLogDao().getForDate(today)
        val plan = db.coachingPlanDao().getLatestPlan()

        val proteinTarget = plan?.weeklyProteinG ?: 0
        val proteinDone = log?.proteinHit == true
        val title = "Fitti Coach"
        val text = buildString {
            if (!proteinDone && proteinTarget > 0) {
                append("Protein-Ziel ${proteinTarget} g heute erreicht? ")
            } else if (proteinDone) {
                append("Protein heute ✓ ")
            }
            if (log?.weightKg == null) {
                append("Gewicht noch eintragen.")
            } else {
                append("Gewicht ${log.weightKg} kg ✓")
            }
        }.trim()

        showNotification(applicationContext, NOTIF_ID_DAILY, title, text)
        return Result.success()
    }

    companion object {
        private const val NOTIF_ID_DAILY = 1001
    }
}

internal fun hasPostNotificationsPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ActivityCompat.checkSelfPermission(
        context, Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
}

internal fun showNotification(context: Context, notifId: Int, title: String, text: String) {
    val notif = NotificationCompat.Builder(context, NotificationScheduler.CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(title)
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .build()
    val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    mgr.notify(notifId, notif)
}
