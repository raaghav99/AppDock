package com.raaghav99.appdock.service

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import com.raaghav99.appdock.data.AppDockDatabase
import com.raaghav99.appdock.model.AppEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ApkBackupHelper {

    /**
     * Backs up the APK of an installed app to AppDock's cache dir.
     * Preserves /data/data/ (login, settings) since we only uninstall APK,
     * not app data (pm uninstall -k equivalent via Device Owner).
     */
    suspend fun backupApp(context: Context, packageName: String): AppEntry? =
        withContext(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val appInfo: ApplicationInfo = pm.getApplicationInfo(packageName, 0)
                val appName = pm.getApplicationLabel(appInfo).toString()
                val apkFile = File(appInfo.sourceDir)

                // Copy APK to our cache
                val cacheDir = File(context.filesDir, "apk_cache").also { it.mkdirs() }
                val dest = File(cacheDir, "$packageName.apk")
                apkFile.copyTo(dest, overwrite = true)

                // Save icon
                val iconPath = saveIcon(context, packageName)

                AppEntry(
                    packageName = packageName,
                    appName = appName,
                    apkCachePath = dest.absolutePath,
                    apkSizeBytes = dest.length(),
                    iconPath = iconPath
                )
            } catch (e: Exception) {
                null
            }
        }

    private fun saveIcon(context: Context, packageName: String): String {
        return try {
            val pm = context.packageManager
            val drawable = pm.getApplicationIcon(packageName)
            val bitmap = when (drawable) {
                is BitmapDrawable -> drawable.bitmap
                is AdaptiveIconDrawable -> {
                    val bmp = Bitmap.createBitmap(108, 108, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    bmp
                }
                else -> {
                    val bmp = Bitmap.createBitmap(108, 108, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    bmp
                }
            }
            val iconFile = File(context.filesDir, "icons/$packageName.png").also {
                it.parentFile?.mkdirs()
            }
            iconFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            iconFile.absolutePath
        } catch (e: Exception) {
            ""
        }
    }

    suspend fun addToVault(context: Context, packageName: String) {
        val entry = backupApp(context, packageName) ?: return
        AppDockDatabase.get(context).appDao().upsert(entry)
    }
}
