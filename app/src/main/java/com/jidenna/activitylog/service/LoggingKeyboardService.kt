package com.jidenna.activitylog.service

import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.view.View
import android.view.inputmethod.EditorInfo
import com.jidenna.activitylog.R
import com.jidenna.activitylog.data.ActivityEntry
import com.jidenna.activitylog.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LoggingKeyboardService : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private lateinit var keyboardView: KeyboardView
    private lateinit var keyboard: Keyboard
    private val buffer = StringBuilder()
    private var currentField: String = "unknown"

    override fun onCreateInputView(): View {
        keyboardView = layoutInflater.inflate(R.layout.keyboard_view, null) as KeyboardView
        keyboard = Keyboard(this, R.xml.qwerty)
        keyboardView.keyboard = keyboard
        keyboardView.setOnKeyboardActionListener(this)
        return keyboardView
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        currentField = info.packageName ?: "unknown"
        flushBuffer()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        flushBuffer()
        super.onFinishInputView(finishingInput)
    }

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        val ic = currentInputConnection ?: return
        when (primaryCode) {
            Keyboard.KEYCODE_DELETE -> {
                ic.deleteSurroundingText(1, 0)
                if (buffer.isNotEmpty()) buffer.deleteCharAt(buffer.length - 1)
            }
            10 -> {
                ic.commitText("\n", 1)
                flushBuffer()
            }
            32 -> {
                ic.commitText(" ", 1)
                buffer.append(' ')
            }
            else -> {
                val c = primaryCode.toChar()
                ic.commitText(c.toString(), 1)
                buffer.append(c)
            }
        }
    }

    private fun flushBuffer() {
        val text = buffer.toString().trim()
        buffer.clear()
        if (text.isEmpty()) return
        val field = currentField
        CoroutineScope(Dispatchers.IO).launch {
            AppDatabase.get(applicationContext).activityDao().insert(
                ActivityEntry(
                    type = "typed",
                    source = field,
                    content = text,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    override fun onPress(primaryCode: Int) {}
    override fun onRelease(primaryCode: Int) {}
    override fun onText(text: CharSequence?) {}
    override fun swipeLeft() {}
    override fun swipeRight() {}
    override fun swipeDown() {}
    override fun swipeUp() {}
}
