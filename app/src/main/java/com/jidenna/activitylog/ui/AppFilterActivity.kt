package com.jidenna.activitylog.ui

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import com.jidenna.activitylog.R

class AppFilterActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private val packageNames = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_filter)

        listView = findViewById(R.id.app_filter_list)
        listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE

        val pm = packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }

        val labels = mutableListOf<String>()
        for (app: ApplicationInfo in apps) {
            packageNames.add(app.packageName)
            labels.add(app.loadLabel(pm).toString())
        }

        listView.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_multiple_choice,
            labels
        )

        val prefs = getSharedPreferences("notif_filter_prefs", MODE_PRIVATE)
        val configured = prefs.getBoolean("configured", false)
        val enabled = prefs.getStringSet("enabled_packages", emptySet()) ?: emptySet()

        for (i in packageNames.indices) {
            // Before the user has ever saved a choice, default everything to ON.
            val shouldCheck = if (!configured) true else packageNames[i] in enabled
            listView.setItemChecked(i, shouldCheck)
        }

        findViewById<Button>(R.id.btn_save_filter).setOnClickListener {
            val checked = listView.checkedItemPositions
            val selected = mutableSetOf<String>()
            for (i in packageNames.indices) {
                if (checked.get(i)) selected.add(packageNames[i])
            }
            prefs.edit()
                .putStringSet("enabled_packages", selected)
                .putBoolean("configured", true)
                .apply()
            finish()
        }
    }
}
