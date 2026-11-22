package com.therealjidenna.phonescoop.keyboard

import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView

class MyKeyboardService : InputMethodService() {

    private lateinit var mainKeyboardView: View
    private lateinit var emojiView: EmojiKeyboardView
    private lateinit var suggestionEngine: SuggestionEngine
    private lateinit var suggestionViews: List<TextView>

    private var showingEmoji = false
    private val currentWord = StringBuilder()

    private val handler = Handler(Looper.getMainLooper())
    private var pendingSuggestionUpdate: Runnable? = null

    override fun onCreateInputView(): View {
        val container = FrameLayout(this)

        mainKeyboardView = layoutInflater.inflate(R.layout.keyboard_view, null)

        suggestionViews = listOf(
            mainKeyboardView.findViewById(R.id.suggestion_1),
            mainKeyboardView.findViewById(R.id.suggestion_2),
            mainKeyboardView.findViewById(R.id.suggestion_3)
        )
        suggestionViews.forEach { tv ->
            tv.setOnClickListener { commitSuggestion(tv.text.toString()) }
        }

        val dictionary = loadDictionary()
        suggestionEngine = SuggestionEngine(dictionary)

        emojiView = EmojiKeyboardView(this).apply {
            setEmojiClickListener { emoji -> commitEmoji(emoji) }
            visibility = View.GONE
        }

        container.addView(mainKeyboardView)
        container.addView(emojiView)

        mainKeyboardView.findViewById<View>(R.id.emoji_toggle_button)
            ?.setOnClickListener { toggleEmojiPanel() }

        return container
    }

    private fun toggleEmojiPanel() {
        showingEmoji = !showingEmoji
        mainKeyboardView.visibility = if (showingEmoji) View.GONE else View.VISIBLE
        emojiView.visibility = if (showingEmoji) View.VISIBLE else View.GONE
    }

    private fun commitEmoji(emoji: String) {
        currentInputConnection?.commitText(emoji, 1)
    }

    fun onLetterKey(letter: String) {
        currentInputConnection?.commitText(letter, 1)
        currentWord.append(letter)
        updateSuggestions()
    }

    fun onWordBoundary() {
        val finishedWord = currentWord.toString()
        currentWord.clear()
        if (finishedWord.isNotBlank()) {
            showPredictions(suggestionEngine.getNextWordPredictions(finishedWord))
        } else {
            clearSuggestions()
        }
    }

    private fun updateSuggestions() {
        pendingSuggestionUpdate?.let { handler.removeCallbacks(it) }
        val word = currentWord.toString()
        pendingSuggestionUpdate = Runnable {
            val completions = suggestionEngine.getCompletions(word)
            showPredictions(completions)
        }
        handler.postDelayed(pendingSuggestionUpdate!!, 30)
    }

    private fun showPredictions(words: List<String>) {
        suggestionViews.forEachIndexed { i, tv ->
            tv.text = words.getOrNull(i) ?: ""
        }
    }

    private fun clearSuggestions() {
        suggestionViews.forEach { it.text = "" }
    }

    private fun commitSuggestion(word: String) {
        if (word.isBlank()) return
        val ic = currentInputConnection ?: return
        ic.deleteSurroundingText(currentWord.length, 0)
        ic.commitText("$word ", 1)
        currentWord.clear()
        clearSuggestions()
    }

    private fun loadDictionary(): Set<String> {
        return assets.open("words.txt").bufferedReader().readLines().toSet()
    }
}
