package com.therealjidenna.phonescoop.keyboard

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class EmojiKeyboardView(context: Context) : RecyclerView(context) {

    companion object {
        val EMOJI_CATEGORIES = mapOf(
            "Smileys" to listOf(
                "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇",
                "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙", "😚"
            ),
            "Gestures" to listOf(
                "👍", "👎", "👌", "✌️", "🤞", "🤟", "🤘", "🤙", "👈", "👉",
                "👆", "👇", "☝️", "✋", "🤚", "🖐️", "🖖", "👋", "🤝", "🙏"
            ),
            "Hearts" to listOf(
                "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔"
            ),
            "Objects" to listOf(
                "🔥", "✨", "🎉", "🎊", "💯", "⭐", "🌟", "💫", "⚡", "☀️"
            )
        )
    }

    init {
        layoutManager = GridLayoutManager(context, 8)
    }

    fun setEmojiClickListener(listener: (String) -> Unit) {
        val allEmojis = EMOJI_CATEGORIES.values.flatten()
        adapter = EmojiAdapter(allEmojis, listener)
    }
}

class EmojiAdapter(
    private val emojis: List<String>,
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<EmojiAdapter.EmojiViewHolder>() {

    class EmojiViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmojiViewHolder {
        val textView = TextView(parent.context).apply {
            textSize = 24f
            gravity = Gravity.CENTER
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(48, context)
            )
            setPadding(8, 8, 8, 8)
        }
        return EmojiViewHolder(textView)
    }

    override fun onBindViewHolder(holder: EmojiViewHolder, position: Int) {
        val emoji = emojis[position]
        holder.textView.text = emoji
        holder.textView.setOnClickListener { onClick(emoji) }
    }

    override fun getItemCount() = emojis.size

    private fun dpToPx(dp: Int, context: Context): Int =
        (dp * context.resources.displayMetrics.density).toInt()
}
