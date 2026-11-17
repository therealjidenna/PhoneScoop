package com.jidenna.activitylog.service

import android.content.Context
import android.os.Build
import android.os.Environment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * Copies WhatsApp media into the app's own private storage so it's kept
 * even after the original is deleted from WhatsApp (e.g. disappearing
 * messages, manual deletion, WhatsApp's own auto-cleanup).
 */
object MediaBackupService {

    private val audioExtensions = listOf("opus", "m4a")
    private val videoExtensions = listOf("mp4", "3gp")
    private val imageExtensions = listOf("jpg", "jpeg", "png", "webp")
    private val allExtensions = audioExtensions + videoExtensions + imageExtensions

    fun backupFolder(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "WhatsAppBackup")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** Scans WhatsApp's media folders and copies anything new into our backup folder. Safe to call often. */
    fun backupOnce(context: Context) {
        if (!hasAllFilesAccess()) return

        CoroutineScope(Dispatchers.IO).launch {
            val roots = listOf(
                File(Environment.getExternalStorageDirectory(), "WhatsApp/Media/WhatsApp Voice Notes"),
                File(Environment.getExternalStorageDirectory(), "WhatsApp/Media/WhatsApp Video"),
                File(Environment.getExternalStorageDirectory(), "WhatsApp/Media/WhatsApp Images"),
                File(Environment.getExternalStorageDirectory(), "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes"),
                File(Environment.getExternalStorageDirectory(), "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video"),
                File(Environment.getExternalStorageDirectory(), "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images")
            )

            val destDir = backupFolder(context)

            for (root in roots) {
                if (!root.exists() || !root.isDirectory) continue
                root.walkTopDown().forEach { f ->
                    if (f.isFile && f.extension.lowercase() in allExtensions) {
                        val dest = File(destDir, f.name)
                        if (!dest.exists() || dest.length() != f.length()) {
                            try {
                                f.copyTo(dest, overwrite = true)
                            } catch (e: Exception) {
                                // skip files that are locked/unreadable, keep going
                            }
                        }
                    }
                }
            }
        }
    }

    private fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }
}
