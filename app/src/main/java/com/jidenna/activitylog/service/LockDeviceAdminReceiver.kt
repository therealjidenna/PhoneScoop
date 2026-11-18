package com.jidenna.activitylog.service

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class LockDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Turning this off removes uninstall protection for Activity Log."
    }
}
