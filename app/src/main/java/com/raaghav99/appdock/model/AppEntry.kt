package com.raaghav99.appdock.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "apps")
data class AppEntry(
    @PrimaryKey val packageName: String,
    val appName: String,
    val apkCachePath: String,      // local path to backed-up APK
    val apkSizeBytes: Long,
    val isActive: Boolean = false, // currently installed & running
    val lastUsed: Long = 0L,
    val iconPath: String = ""
)
