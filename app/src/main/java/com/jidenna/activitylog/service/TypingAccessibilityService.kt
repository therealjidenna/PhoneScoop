package com.jidenna.activitylog.service

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.jidenna.activitylog.data.ActivityEntry
import com.jidenna.activitylog.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Captures typed text system-wide, from whichever keyboard is active
 * (Gboard, in-app keyboards, etc).
 * Skips logging while our own LoggingKeyboardService is the active IME,
 * since that service already logs its own typing directly.
 *
 * Fixes vs the old version:
 *  - A field being cleared after "send" used to be ignored (or worse, reported
 *    as the hint text, e.g. "Message"), which overwrote the real message and
 *    lost it. Now a cleared field saves what was in it immediately.
 *  - Typing in a second field / a brand-new message within 1.5s no longer
 *    overwrites the previous one.
 *  - Hint text is never logged as if it was typed.
 *  - Extra fallbacks (selection-changed + window-content-changed) for apps
 *    whose fields don't send normal text-changed events.
 */
class TypingAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO)

    // What we are currently collecting (not saved yet)
    private var pendingPackage: String? = null
    private var pendingFieldKey: String? = null
    private var pendingText: String = ""

    // Last text we saw per field, used so that taps/selection moves in a field
    // that already has text (e.g. a URL bar) are not logged as "typed".
    private var baselineKey: String? = null
    private var baselineText: String = ""

    private val flushRunnable = Runnable { flush() }
    private val contentCheckRunnable = Runnable { checkFocusedField() }
    private val flushDelayMs = 1500L
    private val contentCheckDelayMs = 400L

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                val pkg = event.packageName?.toString() ?: return
                handleField(pkg, event.source, event, certainChange = true)
            }
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> {
                // Some custom fields/keyboards only send this on every keystroke.
                val pkg = event.packageName?.toString() ?: return
                handleField(pkg, event.source, event, certainChange = false)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // Last-resort fallback: look at the focused input a moment after
                // the screen content settles (debounced so it stays cheap).
                handler.removeCallbacks(contentCheckRunnable)
                handler.postDelayed(contentCheckRunnable, contentCheckDelayMs)
            }
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // Only flush when we really moved to another app. Moving between
                // fields in the same app is handled by the field key, and the
                // keyboard's own window opening must not cut typing in half.
                val pkg = event.packageName?.toString()
                if (pkg != pendingPackage) flush()
            }
        }
    }

    private fun checkFocusedField() {
        try {
            if (ownKeyboardActive()) return
            val node = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return
            if (!node.isEditable || node.isPassword) return
            val pkg = node.packageName?.toString() ?: return
            handleField(pkg, node, null, certainChange = false)
        } catch (e: Exception) {
            // Window can vanish at any time; ignore.
        }
    }

    private fun handleField(
        pkg: String,
        source: AccessibilityNodeInfo?,
        event: AccessibilityEvent?,
        certainChange: Boolean
    ) {
        if (pkg == packageName) return // ignore our own app's UI
        if (ownKeyboardActive()) return
        if (source?.isPassword == true) return

        val text = readText(source, event)
        val key = fieldKey(pkg, source, event)

        if (certainChange) {
            baselineKey = key
            baselineText = text
        } else {
            // Selection / content events: only count it if the text really changed
            if (key != baselineKey) {
                baselineKey = key
                baselineText = text
                return
            }
            if (text == baselineText) return
            baselineText = text
        }

        if (text.isBlank()) {
            // Field was emptied (message sent, or text deleted): save what it held.
            if (key == pendingFieldKey) flush()
            return
        }

        // Moved to a different field: save the old one first.
        if (pendingFieldKey != null && key != pendingFieldKey) flush()

        // Same field but totally different text (e.g. a new message started
        // before the clear event arrived): save the old one first.
        if (pendingText.isNotEmpty() && !isContinuation(pendingText, text)) flush()

        pendingPackage = pkg
        pendingFieldKey = key
        pendingText = text

        handler.removeCallbacks(flushRunnable)
        handler.postDelayed(flushRunnable, flushDelayMs)
    }

    /** Current text of the field, never the hint ("Type a message"). */
    private fun readText(source: AccessibilityNodeInfo?, event: AccessibilityEvent?): String {
        if (source?.isShowingHintText == true) return ""
        val hint = source?.hintText?.toString()

        var text = source?.text?.toString().orEmpty()
        if (text.isEmpty() && event != null) {
            // Some custom views only put the text on the event. Trust it only if
            // something was actually added (a clear event has addedCount == 0).
            if (source == null || event.addedCount > 0) {
                text = event.text?.joinToString("").orEmpty()
            }
        }
        if (!hint.isNullOrEmpty() && text == hint) return ""
        return text
    }

    private fun fieldKey(pkg: String, source: AccessibilityNodeInfo?, event: AccessibilityEvent?): String {
        val id = source?.viewIdResourceName.orEmpty()
        val cls = (source?.className ?: event?.className)?.toString().orEmpty()
        return "$pkg|$id|$cls"
    }

    /**
     * True when [new] looks like the same text being typed / backspaced / lightly
     * edited from [old] (autocorrect, edit in the middle), false when it is
     * clearly something else.
     */
    private fun isContinuation(old: String, new: String): Boolean {
        val max = minOf(old.length, new.length)
        var prefix = 0
        while (prefix < max && old[prefix] == new[prefix]) prefix++
        var suffix = 0
        while (suffix < max - prefix &&
            old[old.length - 1 - suffix] == new[new.length - 1 - suffix]
        ) suffix++
        val allowedDifference = maxOf(3, max / 4)
        return prefix + suffix >= max - allowedDifference
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
        pendingFieldKey = null
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

    override fun onInterrupt() {
        flush()
    }

    override fun onDestroy() {
        flush()
        super.onDestroy()
    }
}
