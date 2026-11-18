package com.jidenna.activitylog.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.jidenna.activitylog.R
import com.jidenna.activitylog.util.PasswordUtil

class LockActivity : AppCompatActivity() {

    private lateinit var passwordField: EditText
    private lateinit var confirmField: EditText
    private lateinit var errorText: TextView
    private lateinit var actionButton: Button
    private var settingUp = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock)

        passwordField = findViewById(R.id.password_field)
        confirmField = findViewById(R.id.confirm_field)
        errorText = findViewById(R.id.error_text)
        actionButton = findViewById(R.id.action_button)

        settingUp = !PasswordUtil.hasPassword(this)
        if (settingUp) {
            confirmField.visibility = View.VISIBLE
            actionButton.text = "Set Password"
        } else {
            confirmField.visibility = View.GONE
            actionButton.text = "Unlock"
        }

        actionButton.setOnClickListener { onSubmit() }
    }

    private fun onSubmit() {
        val password = passwordField.text.toString()
        if (password.isBlank()) {
            showError("Enter a password")
            return
        }

        if (settingUp) {
            val confirm = confirmField.text.toString()
            if (password != confirm) {
                showError("Passwords don't match")
                confirmField.text.clear()
                return
            }
            PasswordUtil.setPassword(this, password)
            proceed()
        } else {
            if (PasswordUtil.checkPassword(this, password)) {
                proceed()
            } else {
                showError("Incorrect password")
                passwordField.text.clear()
            }
        }
    }

    private fun showError(message: String) {
        errorText.text = message
        errorText.visibility = View.VISIBLE
    }

    private fun proceed() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
