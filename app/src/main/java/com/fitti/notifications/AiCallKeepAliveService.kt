package com.fitti.notifications

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.fitti.data.ClaudeApiService
import java.util.concurrent.atomic.AtomicInteger

/**
 * Kurzer Vordergrund-Dienst, solange ein Claude-Call laeuft. Ohne ihn kappt
 * Android die Verbindung, sobald die App in den Hintergrund geht
 * ("Software caused connection abort"), weil Hintergrund-Apps vom Netz
 * genommen werden. Referenzgezaehlt: mehrere parallele Calls teilen sich
 * einen Dienst.
 */
class AiCallKeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        NotificationScheduler.ensureChannel(applicationContext)
        val notif = NotificationCompat.Builder(this, NotificationScheduler.CHANNEL_ID)
            .setContentTitle("KI-Antwort wird erstellt …")
            .setContentText("Du kannst die App solange verlassen.")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setOngoing(true)
            .setSilent(true)
            .setProgress(0, 0, true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
        return START_NOT_STICKY
    }

    // shortService-Limit (ca. 3 Min.) erreicht: sauber beenden statt ANR.
    override fun onTimeout(startId: Int) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        private const val NOTIF_ID = 2101
        private val activeCalls = AtomicInteger(0)

        /** Verbindet ClaudeApiService mit diesem Dienst (einmal in MainActivity). */
        fun install(context: Context) {
            val appContext = context.applicationContext
            ClaudeApiService.callLifecycle = object : ClaudeApiService.CallLifecycle {
                override fun started() {
                    if (activeCalls.incrementAndGet() == 1) start(appContext)
                }

                override fun finished() {
                    if (activeCalls.decrementAndGet() <= 0) {
                        activeCalls.set(0)
                        appContext.stopService(Intent(appContext, AiCallKeepAliveService::class.java))
                    }
                }
            }
        }

        private fun start(context: Context) {
            // Start aus dem Hintergrund (z.B. Worker) ist ab Android 12 verboten –
            // dann laeuft der Call eben ohne Schutz weiter.
            try {
                val intent = Intent(context, AiCallKeepAliveService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
            }
        }
    }
}
