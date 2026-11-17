package com.jidenna.activitylog.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.jidenna.activitylog.BuildConfig
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

    private val gmailAddress = BuildConfig.GMAIL_ADDRESS
    private val gmailAppPassword = BuildConfig.GMAIL_APP_PASSWORD

    override fun onCreate() {
        super.onCreate()
        // One-time startup check so we can see in the log whether the credentials even made it into this build.
        scope.launch {
            val addressStatus = if (gmailAddress.isBlank()) "EMPTY" else "set (${gmailAddress.take(3)}***)"
            val passwordStatus = if (gmailAppPassword.isBlank()) "EMPTY" else "set (${gmailAppPassword.length} chars)"
            logToActivity("debug", "startup", "GMAIL_ADDRESS: $addressStatus, GMAIL_APP_PASSWORD: $passwordStatus")
        }
    }

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

    private suspend fun sendEmail(subject: String, body: String) {
        if (gmailAddress.isBlank() || gmailAppPassword.isBlank()) {
            logToActivity("email_error", "config", "Gmail address or app password is empty — check GitHub secrets and build.gradle.kts")
            return
        }
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
            logToActivity("email_sent", "success", subject)
        } catch (e: Exception) {
            logToActivity("email_error", e.javaClass.simpleName, e.message ?: "no message")
        }
    }

    private suspend fun logToActivity(type: String, source: String, content: String) {
        AppDatabase.get(applicationContext).activityDao().insert(
            ActivityEntry(
                type = type,
                source = source,
                content = content,
                timestamp = System.currentTimeMillis()
            )
        )
    }
}
