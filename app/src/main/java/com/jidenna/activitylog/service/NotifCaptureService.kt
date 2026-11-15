package com.jidenna.activitylog.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.jidenna.activitylog.data.ActivityEntry
import com.jidenna.activitylog.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotifCaptureService : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = extras.getCharSequence("android.text")?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return

        val content = listOf(title, text).filter { it.isNotBlank() }.joinToString(": ")

        scope.launch {
            AppDatabase.get(applicationContext).activityDao().insert(
                ActivityEntry(
                    type = "notification",
                    source = sbn.packageName,
                    content = content,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }
}
