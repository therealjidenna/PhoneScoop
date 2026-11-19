package com.jidenna.activitylog.ui

import android.app.AppOpsManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Process
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.jidenna.activitylog.R
import com.jidenna.activitylog.data.ActivityEntry
import com.jidenna.activitylog.data.AppDatabase
import com.jidenna.activitylog.email.DigestScheduler
import com.jidenna.activitylog.service.MediaWatchService
import com.jidenna.activitylog.service.UsagePollService
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var searchBox: EditText
    private val fmt = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        DigestScheduler.scheduleNext(this)

        listView = findViewById(R.id.entry_list)
        searchBox = findViewById(R.id.search_box)

        findViewById<android.widget.Button>(R.id.btn_usage_access).setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        findViewById<android.widget.Button>(R.id.btn_notif_access).setOnClickListener {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        }
        findViewById<android.widget.Button>(R.id.btn_keyboard).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        findViewById<android.widget.Button>(R.id.btn_notif_filter).setOnClickListener {
            startActivity(Intent(this, AppFilterActivity::class.java))
        }
        findViewById<android.widget.Button>(R.id.btn_email_settings).setOnClickListener {
            startActivity(Intent(this, EmailSettingsActivity::class.java))
        }

        findViewById<android.widget.Button>(R.id.btn_device_admin).setOnClickListener {
            val compName = android.content.ComponentName(this, com.jidenna.activitylog.service.LockDeviceAdminReceiver::class.java)
            val dpm = getSystemService(android.app.admin.DevicePolicyManager::class.java)
            if (dpm.isAdminActive(compName)) {
                val input = EditText(this).apply { inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD }
                android.app.AlertDialog.Builder(this)
                    .setTitle("Enter password to disable protection")
                    .setView(input)
