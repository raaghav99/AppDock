package com.raaghav99.appdock.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class AppDockAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        // Device admin enabled
    }

    override fun onDisabled(context: Context, intent: Intent) {
        // Device admin disabled
    }
}
