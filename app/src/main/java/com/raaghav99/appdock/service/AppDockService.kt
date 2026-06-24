package com.raaghav99.appdock.service

import android.app.*
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.raaghav99.appdock.admin.AppDockAdminReceiver
import com.raaghav99.appdock.data.AppDockDatabase
import kotlinx.coroutines.*
import java.io.File

class AppDockService : Service() {

    private val TAG = "AppDockService"
    private val CHANNEL_ID = "appdock_session"
    private val NOTIF_ID = 1

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val sessionApps = mutableSetOf<String>() // packages launched this session

    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminComponent: ComponentName
    private lateinit var db: AppDockDatabase

    override fun onCreate() {
        super.onCreate()
        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, AppDockAdminReceiver::class.java)
        db = AppDockDatabase.get(this)
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("AppDock session active"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_LAUNCH -> {
                val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: return START_STICKY
                scope.launch { launchApp(pkg) }
            }
            ACTION_STOP -> {
                scope.launch { cleanupSession() }
            }
        }
        return START_STICKY
    }

    /**
     * Called when user swipes AppDock away from recents.
     * Auto-uninstalls all apps launched this session.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "Task removed — cleaning up ${sessionApps.size} session apps")
        runBlocking { cleanupSession() }
        stopSelf()
    }

    private suspend fun launchApp(packageName: String) {
        val app = db.appDao().getApp(packageName) ?: return
        val apkFile = File(app.apkCachePath)
        if (!apkFile.exists()) {
            Log.e(TAG, "APK cache missing for $packageName")
            return
        }

        if (isDeviceOwner()) {
            silentInstall(packageName, apkFile)
        } else {
            // Fallback: prompt user install
            promptInstall(apkFile)
        }

        sessionApps.add(packageName)
        db.appDao().setActive(packageName, true)

        // Launch the app
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        launchIntent?.let { startActivity(it) }

        updateNotification("Running: ${sessionApps.size} app(s)")
        Log.d(TAG, "Launched $packageName")
    }

    private suspend fun cleanupSession() {
        Log.d(TAG, "Cleaning up session: $sessionApps")
        sessionApps.forEach { pkg ->
            try {
                if (isDeviceOwner()) {
                    val intent = Intent(this@AppDockService, AppDockService::class.java)
                    val pi = PendingIntent.getService(this@AppDockService, pkg.hashCode(), intent, PendingIntent.FLAG_MUTABLE)
                    packageManager.packageInstaller.uninstall(pkg, pi.intentSender)
                }
                db.appDao().setActive(pkg, false)
                Log.d(TAG, "Uninstalled $pkg")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to uninstall $pkg: ${e.message}")
            }
        }
        sessionApps.clear()
    }

    private fun silentInstall(packageName: String, apkFile: java.io.File) {
        val installer = packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(packageName)

        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)

        apkFile.inputStream().use { input ->
            session.openWrite(packageName, 0, apkFile.length()).use { output ->
                input.copyTo(output)
                session.fsync(output)
            }
        }

        val intent = Intent(this, AppDockService::class.java)
        val pendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_MUTABLE)
        session.commit(pendingIntent.intentSender)
        session.close()
    }

    private fun promptInstall(apkFile: java.io.File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            this,
            "${packageName}.provider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(intent)
    }

    private fun isDeviceOwner(): Boolean =
        dpm.isDeviceOwnerApp(packageName)

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "AppDock Session",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "AppDock active session" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AppDock")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .build()

    private fun updateNotification(text: String) {
        val notif = buildNotification(text)
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notif)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_LAUNCH = "com.raaghav99.appdock.LAUNCH"
        const val ACTION_STOP = "com.raaghav99.appdock.STOP"
        const val EXTRA_PACKAGE = "package_name"

        fun launch(context: Context, packageName: String) {
            context.startService(Intent(context, AppDockService::class.java).apply {
                action = ACTION_LAUNCH
                putExtra(EXTRA_PACKAGE, packageName)
            })
        }

        fun stop(context: Context) {
            context.startService(Intent(context, AppDockService::class.java).apply {
                action = ACTION_STOP
            })
        }
    }
}
