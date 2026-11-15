package com.jidenna.activitylog.service

import android.app.usage.UsageStatsManager
import android.content.Context
import com.jidenna.activitylog.data.ActivityEntry
import com.jidenna.activitylog.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object UsagePollService {

    private const val PREFS = "usage_poll_prefs"
    private const val KEY_LAST_POLL = "last_poll_ts"

    fun pollOnce(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val since = prefs.getLong(KEY_LAST_POLL, now - 60 * 60 * 1000)

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = usm.queryEvents(since, now)
        val pm = context.packageManager

        val seen = LinkedHashMap<String, Long>()
        val event = android.app.usage.UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) {
                seen.putIfAbsent(event.packageName, event.timeStamp)
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            val dao = AppDatabase.get(context).activityDao()
            for ((pkg, ts) in seen) {
                val label = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                } catch (e: Exception) {
                    pkg
                }
                dao.insert(
                    ActivityEntry(
                        type = "opened",
                        source = pkg,
                        content = label,
                        timestamp = ts
                    )
                )
            }
        }

        prefs.edit().putLong(KEY_LAST_POLL, now).apply()
    }
}
