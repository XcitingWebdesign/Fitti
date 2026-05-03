package com.fitti.notifications

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.fitti.MainActivity
import kotlin.math.sin

/**
 * Foreground service that runs the rest-timer countdown so it survives the
 * app being backgrounded or the screen turning off, and so the chime fires
 * reliably at the end via a HIGH-importance notification.
 */
class RestTimerService : Service() {

    private var timer: CountDownTimer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        NotificationScheduler.ensureChannel(applicationContext)

        val seconds = intent?.getIntExtra(EXTRA_SECONDS, 60)?.coerceAtLeast(1) ?: 60
        val kind = intent?.getStringExtra(EXTRA_KIND) ?: KIND_SET

        val notif = buildRunningNotification(seconds, seconds)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIF_ID_RUNNING,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
            )
        } else {
            startForeground(NOTIF_ID_RUNNING, notif)
        }

        timer?.cancel()
        timer = object : CountDownTimer(seconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val remaining = (millisUntilFinished / 1000).toInt() + 1
                notify(NOTIF_ID_RUNNING, buildRunningNotification(remaining, seconds))
            }

            override fun onFinish() {
                notify(NOTIF_ID_DONE, buildDoneNotification(kind))
                playChime(kind)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }.start()

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        timer?.cancel()
        timer = null
        super.onDestroy()
    }

    private fun notify(id: Int, notif: Notification) {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(id, notif)
    }

    private fun buildRunningNotification(remaining: Int, total: Int): Notification {
        val pending = openAppIntent()
        val mins = remaining / 60
        val secs = remaining % 60
        val timeLabel = "%d:%02d".format(mins, secs)
        return NotificationCompat.Builder(this, NotificationScheduler.CHANNEL_ID_TIMER)
            .setContentTitle("Pause läuft")
            .setContentText("Noch $timeLabel")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(total, total - remaining, false)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun buildDoneNotification(kind: String): Notification {
        val pending = openAppIntent()
        val text = if (kind == KIND_EXERCISE) "Weiter zur nächsten Übung." else "Weiter zum nächsten Satz."
        return NotificationCompat.Builder(this, NotificationScheduler.CHANNEL_ID_TIMER)
            .setContentTitle("Pause vorbei")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun playChime(kind: String) {
        try {
            val sampleRate = 44100
            val notes = if (kind == KIND_EXERCISE) {
                listOf(523.25, 659.25, 783.99, 1046.50)
            } else {
                listOf(523.25, 659.25, 783.99)
            }
            val noteMs = if (kind == KIND_EXERCISE) 180 else 110
            val samplesPerNote = sampleRate * noteMs / 1000
            val totalSamples = samplesPerNote * notes.size
            val buffer = ShortArray(totalSamples)
            val attack = samplesPerNote / 20
            val releaseStart = samplesPerNote * 9 / 10
            val releaseLen = samplesPerNote - releaseStart
            notes.forEachIndexed { idx, freq ->
                val offset = idx * samplesPerNote
                for (i in 0 until samplesPerNote) {
                    val t = i.toDouble() / sampleRate
                    val envelope = when {
                        i < attack -> i.toDouble() / attack
                        i > releaseStart -> (samplesPerNote - i).toDouble() / releaseLen
                        else -> 1.0
                    }
                    val sample = (sin(2 * Math.PI * freq * t) * envelope * 0.6 * Short.MAX_VALUE).toInt().toShort()
                    buffer[offset + i] = sample
                }
            }
            val track = AudioTrack(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
                buffer.size * 2,
                AudioTrack.MODE_STATIC,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )
            track.write(buffer, 0, buffer.size)
            track.play()
            // Release after the chime finishes; keep the service alive long enough.
            Thread.sleep((noteMs.toLong() * notes.size) + 100L)
            track.release()
        } catch (_: Exception) {
        }
    }

    companion object {
        const val EXTRA_SECONDS = "seconds"
        const val EXTRA_KIND = "kind"
        const val KIND_SET = "set"
        const val KIND_EXERCISE = "exercise"

        private const val NOTIF_ID_RUNNING = 2001
        private const val NOTIF_ID_DONE = 2002

        fun start(context: Context, seconds: Int, kind: String) {
            val intent = Intent(context, RestTimerService::class.java).apply {
                putExtra(EXTRA_SECONDS, seconds)
                putExtra(EXTRA_KIND, kind)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, RestTimerService::class.java)
            context.stopService(intent)
        }
    }
}
