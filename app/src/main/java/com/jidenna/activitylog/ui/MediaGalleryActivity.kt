package com.jidenna.activitylog.ui

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.jidenna.activitylog.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MediaGalleryActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var statusText: TextView
    private val mediaFiles = mutableListOf<File>()
    private var player: MediaPlayer? = null
    private val fmt = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media_gallery)

        listView = findViewById(R.id.media_list)
        statusText = findViewById(R.id.media_status)
        findViewById<Button>(R.id.btn_grant_all_files).setOnClickListener {
            openAllFilesAccessSettings()
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasAllFilesAccess()) {
            loadMedia()
        } else {
            statusText.text = "Needs \"All files access\" permission — tap the button below."
        }
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }

    private fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true // older versions don't need this special permission
        }
    }

    private fun openAllFilesAccessSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }
    }

    private fun loadMedia() {
        mediaFiles.clear()
        val roots = listOf(
            File(Environment.getExternalStorageDirectory(), "WhatsApp/Media/WhatsApp Voice Notes"),
            File(Environment.getExternalStorageDirectory(), "WhatsApp/Media/WhatsApp Video"),
            File(Environment.getExternalStorageDirectory(), "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes"),
            File(Environment.getExternalStorageDirectory(), "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video")
        )

        for (root in roots) {
            if (root.exists() && root.isDirectory) {
                root.walkTopDown().forEach { f ->
                    if (f.isFile && (f.extension.lowercase() in listOf("opus", "mp4", "m4a", "3gp"))) {
                        mediaFiles.add(f)
                    }
                }
            }
        }

        mediaFiles.sortByDescending { it.lastModified() }

        if (mediaFiles.isEmpty()) {
            statusText.text = "No WhatsApp voice notes or videos found."
        } else {
            statusText.text = "${mediaFiles.size} file(s) found"
        }

        val labels = mediaFiles.map { f ->
            val time = fmt.format(Date(f.lastModified()))
            val kind = if (f.extension.lowercase() == "opus") "🎤 Voice" else "🎬 Video"
            "$kind — $time\n${f.name}"
        }

        listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)

        listView.setOnItemClickListener { _, _, position, _ ->
            val file = mediaFiles[position]
            if (file.extension.lowercase() == "opus" || file.extension.lowercase() == "m4a") {
                playAudio(file)
            } else {
                playVideo(file)
            }
        }
    }

    private fun playAudio(file: File) {
        player?.release()
        player = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            setOnPreparedListener { start() }
            setOnCompletionListener { Toast.makeText(this@MediaGalleryActivity, "Finished playing", Toast.LENGTH_SHORT).show() }
            prepareAsync()
        }
        Toast.makeText(this, "Playing ${file.name}", Toast.LENGTH_SHORT).show()
    }

    private fun playVideo(file: File) {
        val uri: Uri = FileProvider.getUriForFile(this, "com.jidenna.activitylog.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/*")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No video player app found", Toast.LENGTH_SHORT).show()
        }
    }
}
