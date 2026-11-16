package com.jidenna.activitylog.service

import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.textservice.SentenceSuggestionsInfo
import android.view.textservice.SpellCheckerSession
import android.view.textservice.SpellCheckerSession.SpellCheckerSessionListener
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import android.view.textservice.TextServicesManager
import com.jidenna.activitylog.R
import com.jidenna.activitylog.data.ActivityEntry
import com.jidenna.activitylog.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

class LoggingKeyboardService : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private lateinit var keyboardView: KeyboardView
    private lateinit var lettersKeyboard: Keyboard
    private lateinit var symbolsKeyboard: Keyboard
    private var usingSymbols = false
    private var isShifted = false

    private val buffer = StringBuilder()
    private var currentField: String = "unknown"

    private var spellSession: SpellCheckerSession? = null
    private val currentWord = StringBuilder()
    private var pendingFix: String? = null
    private var pendingFixForWord: String? = null

    override fun onCreate() {
        super.onCreate()
        val tsm = getSystemService(TEXT_SERVICES_MANAGER_SERVICE) as? TextServicesManager
        spellSession = tsm?.newSpellCheckerSession(null, Locale.getDefault(), spellListener, true)
    }

    override fun onDestroy() {
        spellSession?.close()
        spellSession = null
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        keyboardView = layoutInflater.inflate(R.layout.keyboard_view, null) as KeyboardView
        lettersKeyboard = Keyboard(this, R.xml.qwerty)
        symbolsKeyboard = Keyboard(this, R.xml.qwerty_symbols)
        usingSymbols = false
        isShifted = false
        keyboardView.keyboard = lettersKeyboard
        keyboardView.setOnKeyboardActionListener(this)
        return keyboardView
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        currentField = info.packageName ?: "unknown"
        usingSymbols = false
        isShifted = false
        if (::keyboardView.isInitialized) {
            keyboardView.keyboard = lettersKeyboard
            updateShiftKeys()
        }
        currentWord.clear()
        pendingFix = null
        pendingFixForWord = null
        flushBuffer()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        flushBuffer()
        super.onFinishInputView(finishingInput)
    }

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        val ic = currentInputConnection ?: return
        when (primaryCode) {
            Keyboard.KEYCODE_SHIFT -> {
                isShifted = !isShifted
                updateShiftKeys()
            }
            -2 -> {
                usingSymbols = !usingSymbols
                keyboardView.keyboard = if (usingSymbols) symbolsKeyboard else lettersKeyboard
            }
            Keyboard.KEYCODE_DELETE -> {
                ic.deleteSurroundingText(1, 0)
                if (buffer.isNotEmpty()) buffer.deleteCharAt(buffer.length - 1)
                if (currentWord.isNotEmpty()) currentWord.deleteCharAt(currentWord.length - 1)
            }
            10 -> {
                applyAutocorrectIfAny(ic)
                ic.commitText("\n", 1)
                buffer.append('\n')
                currentWord.clear()
                flushBuffer()
            }
            32 -> {
                applyAutocorrectIfAny(ic)
                ic.commitText(" ", 1)
                buffer.append(' ')
                currentWord.clear()
                pendingFix = null
                pendingFixForWord = null
            }
            else -> {
                val c = primaryCode.toChar()
                ic.commitText(c.toString(), 1)
                buffer.append(c)
                if (c.isLetter()) {
                    currentWord.append(c)
                    requestSuggestions()
                } else {
                    currentWord.clear()
                }
            }
        }
    }

    /** Swap letter keys between upper/lower case based on isShifted. */
    private fun updateShiftKeys() {
        if (!::lettersKeyboard.isInitialized) return
        for (key in lettersKeyboard.keys) {
            val code = key.codes?.getOrNull(0) ?: continue
            if (code in 97..122 || code in 65..90) {
                val lower = code.toChar().lowercaseChar()
                val newChar = if (isShifted) lower.uppercaseChar() else lower
                key.codes[0] = newChar.code
                key.label = newChar.toString()
            }
        }
        if (::keyboardView.isInitialized) keyboardView.invalidateAllKeys()
    }

    private fun applyAutocorrectIfAny(ic: android.view.inputmethod.InputConnection) {
        val word = currentWord.toString()
        val fix = pendingFix
        if (word.isNotEmpty() && fix != null && fix != word && pendingFixForWord == word) {
            ic.deleteSurroundingText(word.length, 0)
            ic.commitText(fix, 1)
            if (buffer.length >= word.length) {
                buffer.setLength(buffer.length - word.length)
                buffer.append(fix)
            }
        }
    }

    private fun requestSuggestions() {
        val word = currentWord.toString()
        if (word.length < 2) return
        spellSession?.getSentenceSuggestions(arrayOf(TextInfo(word)), 3)
    }

    private val spellListener = object : SpellCheckerSessionListener {
        override fun onGetSuggestions(results: Array<out SuggestionsInfo>?) {}

        override fun onGetSentenceSuggestions(results: Array<out SentenceSuggestionsInfo>?) {
            val sentenceInfo = results?.firstOrNull() ?: return
            if (sentenceInfo.suggestionsCount == 0) return
            val info = sentenceInfo.getSuggestionsInfoAt(0) ?: return
            val isTypo = (info.suggestionsAttributes and SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO) != 0
            val word = currentWord.toString()
            if (isTypo && info.suggestionsCount > 0) {
                pendingFix = info.getSuggestionAt(0)
                pendingFixForWord = word
            } else {
                pendingFix = null
                pendingFixForWord = null
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
