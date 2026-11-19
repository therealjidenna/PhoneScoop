package com.jidenna.activitylog.email

import android.content.Context
import android.util.Log
import com.jidenna.activitylog.BuildConfig
import com.jidenna.activitylog.data.EmailPrefs
// TODO: replace this import + call below with your actual Room database singleton
import com.jidenna.activitylog.data.ActivityDatabase
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

object DigestSender {
    private const val TAG = "DigestSender"
    // Your own address (same Gmail account used to send) also receives every digest
    private val PARENT_EMAIL = BuildConfig.GMAIL_ADDRESS

    suspend fun sendDailyDigest(context: Context) {
        val dao = ActivityDatabase.getInstance(context).activityDao() // adjust to your real class
        val since = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(24)
        val entries = dao.since(since)

        val deviceLabel = android.os.Build.MODEL ?: "Device"
        val dateStr = SimpleDateFormat("EEEE, MMM d yyyy", Locale.getDefault()).format(Date())

        val body = buildString {
            appendLine("Activity digest for $deviceLabel — $dateStr")
            appendLine("Total entries: ${entries.size}")
            appendLine()
            entries.take(200).forEach { appendLine("[${it.source}] ${it.content}") }
        }

        val recipients = buildRecipientList(context)
        if (recipients.isEmpty()) return

        sendEmail("Activity Digest — $deviceLabel — $dateStr", body, recipients)
    }

    private fun buildRecipientList(context: Context): List<String> {
        val list = mutableListOf<String>()
        EmailPrefs.getUserEmail(context)?.let { list.add(it) }
        if (PARENT_EMAIL.isNotBlank()) list.add(PARENT_EMAIL)
        return list.distinct()
    }

    private fun sendEmail(subject: String, body: String, recipients: List<String>) {
        val props = Properties().apply {
            put("mail.smtp.host", "smtp.gmail.com")
            put("mail.smtp.port", "587")
            put("mail.smtp.auth", "true")
            put("mail.smtp.starttls.enable", "true")
        }
        val session = Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication() =
                PasswordAuthentication(BuildConfig.GMAIL_ADDRESS, BuildConfig.GMAIL_APP_PASSWORD)
        })
        try {
            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(BuildConfig.GMAIL_ADDRESS))
                setRecipients(Message.RecipientType.TO, recipients.map { InternetAddress(it) }.toTypedArray())
                setSubject(subject)
                setText(body)
            }
            Transport.send(message)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send digest", e)
        }
    }
}
