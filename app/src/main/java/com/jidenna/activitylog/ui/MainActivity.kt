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
                    .setPositiveButton("Disable") { _, _ ->
                        if (com.jidenna.activitylog.util.PasswordUtil.checkPassword(this, input.text.toString())) {
                            dpm.removeActiveAdmin(compName)
                            Toast.makeText(this, "Uninstall protection disabled", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Incorrect password", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else {
                val intent = Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, compName)
                    putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "This stops the app from being uninstalled without first turning this off in Settings.")
                }
                startActivity(intent)
            }
        }

        searchBox.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = loadEntries(s?.toString().orEmpty())
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
    }

    override fun onResume() {
        super.onResume()
        if (hasUsageAccess()) {
            UsagePollService.pollOnce(this)
        }
        if (hasAllFilesAccess()) {
            MediaWatchService.start(this)
        }
        loadEntries("")
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    private fun loadEntries(query: String) {
        lifecycleScope.launch {
            val dao = AppDatabase.get(this@MainActivity).activityDao()
            val entries = if (query.isBlank()) dao.recent() else dao.search(query)
            val lines = entries.map { formatEntry(it) }
            listView.adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_list_item_1,
                lines
            )
        }
    }

    private fun formatEntry(e: ActivityEntry): String {
        val time = fmt.format(Date(e.timestamp))
        val tag = when (e.type) {
            "typed" -> "⌨ typed in ${e.source}"
            "opened" -> "▶ opened ${e.source}"
            "notification" -> "🔔 ${e.source}"
            else -> e.type
        }
        return "$time — $tag\n${e.content}"
    }
}
