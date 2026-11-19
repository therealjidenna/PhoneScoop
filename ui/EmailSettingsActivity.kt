package com.jidenna.activitylog.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.jidenna.activitylog.R
import com.jidenna.activitylog.data.EmailPrefs

class EmailSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_email_settings)

        val emailInput = findViewById<EditText>(R.id.emailInput)
        val saveButton = findViewById<Button>(R.id.saveButton)

        EmailPrefs.getUserEmail(this)?.let { emailInput.setText(it) }

        saveButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Enter a valid email address", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            EmailPrefs.setUserEmail(this, email)
            Toast.makeText(this, "Email saved", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
