package com.raaghav99.appdock.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.raaghav99.appdock.model.AppEntry

@Database(entities = [AppEntry::class], version = 1, exportSchema = false)
abstract class AppDockDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao

    companion object {
        @Volatile private var INSTANCE: AppDockDatabase? = null

        fun get(context: Context): AppDockDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context, AppDockDatabase::class.java, "appdock.db")
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
