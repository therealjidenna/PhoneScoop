package com.jidenna.activitylog.ui

import android.app.AppOpsManager
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import com.jidenna.activitylog.R
import com.jidenna.activitylog.data.ActivityEntry
import com.jidenna.activitylog.data.AppDatabase
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
        loadEntries("")
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
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
