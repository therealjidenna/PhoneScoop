package com.jidenna.activitylog.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.jidenna.activitylog.R
import java.io.File

/**
 * Watches WhatsApp's media folders continuously and backs up any new file
 * the moment it finishes writing, so media survives even if it's deleted
 * from WhatsApp seconds later.
 */
class MediaWatchService : Service() {

    private val observers = mutableListOf<FileObserver>()
    private val watchMask = FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())
        startWatching()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        observers.forEach { it.stopWatching() }
        observers.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startWatching() {
        val roots = listOf(
            File(Environment.getExternalStorageDirectory(), "WhatsApp/Media/WhatsApp Voice Notes"),
            File(Environment.getExternalStorageDirectory(), "WhatsApp/Media/WhatsApp Video"),
            File(Environment.getExternalStorageDirectory(), "WhatsApp/Media/WhatsApp Images"),
            File(Environment.getExternalStorageDirectory(), "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes"),
            File(Environment.getExternalStorageDirectory(), "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video"),
            File(Environment.getExternalStorageDirectory(), "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images")
        )

        for (root in roots) {
            if (!root.exists() || !root.isDirectory) continue
            val observer = makeObserver(root)
            observer.startWatching()
            observers.add(observer)
        }

        // Catch anything already sitting there, or anything missed while the service was off.
        MediaBackupService.backupOnce(applicationContext)
    }

    @Suppress("DEPRECATION")
    private fun makeObserver(root: File): FileObserver {
        return object : FileObserver(root.absolutePath, watchMask) {
            override fun onEvent(event: Int, path: String?) {
                if (path == null) return
                MediaBackupService.backupOnce(applicationContext)
            }
        }
    }

    private fun buildNotification(): Notification {
        val channelId = "media_watch_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                channelId,
                "Media backup",
                NotificationManager.IMPORTANCE_MIN
            )
            manager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Activity Log")
            .setContentText("Watching for new WhatsApp media")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val NOTIF_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, MediaWatchService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
