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
import com.jidenna.activitylog.service.MediaBackupService
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

    private val audioExtensions = listOf("opus", "m4a")
    private val videoExtensions = listOf("mp4", "3gp")
    private val imageExtensions = listOf("jpg", "jpeg", "png", "webp")

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
            MediaBackupService.backupOnce(this)
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
            true
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
        // Read from our own backup folder, not the live WhatsApp folder,
        // so files still show even after being deleted from WhatsApp.
        val backupDir = MediaBackupService.backupFolder(this)
        val allExtensions = audioExtensions + videoExtensions + imageExtensions

        if (backupDir.exists() && backupDir.isDirectory) {
            backupDir.listFiles()?.forEach { f ->
                if (f.isFile && f.extension.lowercase() in allExtensions) {
                    mediaFiles.add(f)
                }
            }
        }

        mediaFiles.sortByDescending { it.lastModified() }

        if (mediaFiles.isEmpty()) {
            statusText.text = "No WhatsApp voice notes, videos, or images found yet. Open this screen again after new media arrives."
        } else {
            statusText.text = "${mediaFiles.size} file(s) saved"
        }

        val labels = mediaFiles.map { f ->
            val time = fmt.format(Date(f.lastModified()))
            val ext = f.extension.lowercase()
            val kind = when {
                ext in audioExtensions -> "🎤 Voice"
                ext in videoExtensions -> "🎬 Video"
                ext in imageExtensions -> "🖼 Image"
                else -> "📄 File"
            }
            "$kind — $time\n${f.name}"
        }

        listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)

        listView.setOnItemClickListener { _, _, position, _ ->
            val file = mediaFiles[position]
            val ext = file.extension.lowercase()
            when {
                ext in audioExtensions -> playAudio(file)
                ext in videoExtensions -> playVideo(file)
                ext in imageExtensions -> viewImage(file)
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

    private fun viewImage(file: File) {
        val uri: Uri = FileProvider.getUriForFile(this, "com.jidenna.activitylog.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "image/*")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No image viewer app found", Toast.LENGTH_SHORT).show()
        }
    }
}
