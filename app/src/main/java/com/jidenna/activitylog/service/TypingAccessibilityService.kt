package com.jidenna.activitylog.service

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import com.jidenna.activitylog.data.ActivityEntry
import com.jidenna.activitylog.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Captures typed text system-wide, from whichever keyboard is active.
 * Skips logging while our own LoggingKeyboardService is the active IME,
 * since that service already logs its own typing directly.
 */
class TypingAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO)

    private var pendingPackage: String? = null
    private var pendingText: String = ""

    private val flushRunnable = Runnable { flush() }
    private val flushDelayMs = 1500L

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                if (ownKeyboardActive()) return
                val source = event.source
                if (source?.isPassword == true) return

                val pkg = event.packageName?.toString() ?: return
                if (pkg == packageName) return // ignore our own app's UI

                val text = event.text?.joinToString("") ?: return
                if (text.isBlank()) return

                if (pkg != pendingPackage) {
                    flush()
                    pendingPackage = pkg
                }
                pendingText = text

                handler.removeCallbacks(flushRunnable)
                handler.postDelayed(flushRunnable, flushDelayMs)
            }
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                flush()
            }
        }
    }

    private fun ownKeyboardActive(): Boolean {
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?: return false
        return current.startsWith(packageName)
    }

    private fun flush() {
        handler.removeCallbacks(flushRunnable)
        val text = pendingText.trim()
        val pkg = pendingPackage
        pendingText = ""
        pendingPackage = null
        if (text.isBlank() || pkg == null) return

        scope.launch {
            AppDatabase.get(applicationContext).activityDao().insert(
                ActivityEntry(
                    type = "typed",
                    source = pkg,
                    content = text,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    override fun onInterrupt() {}
}
