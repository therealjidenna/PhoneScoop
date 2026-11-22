package com.therealjidenna.phonescoop.keyboard

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View

class KeyboardKeyView(context: Context) : View(context) {

    var onKeyPress: (() -> Unit)? = null
    private var isPressed = false

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isPressed = true
                invalidate()
                onKeyPress?.invoke()
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isPressed = false
                invalidate()
            }
        }
        return true
    }
}
