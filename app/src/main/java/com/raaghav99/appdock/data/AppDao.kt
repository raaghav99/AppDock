package com.raaghav99.appdock.data

import androidx.room.*
import com.raaghav99.appdock.model.AppEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Query("SELECT * FROM apps ORDER BY appName ASC")
    fun getAllApps(): Flow<List<AppEntry>>

    @Query("SELECT * FROM apps WHERE isActive = 1")
    fun getActiveApps(): Flow<List<AppEntry>>

    @Query("SELECT * FROM apps WHERE packageName = :pkg LIMIT 1")
    suspend fun getApp(pkg: String): AppEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(app: AppEntry)

    @Query("UPDATE apps SET isActive = :active, lastUsed = :time WHERE packageName = :pkg")
    suspend fun setActive(pkg: String, active: Boolean, time: Long = System.currentTimeMillis())

    @Delete
    suspend fun delete(app: AppEntry)
}
