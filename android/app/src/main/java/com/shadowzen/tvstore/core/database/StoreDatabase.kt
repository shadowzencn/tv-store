package com.shadowzen.tvstore.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [DownloadTask::class], version = 1, exportSchema = true)
abstract class StoreDatabase : RoomDatabase() {
    abstract fun downloadTaskDao(): DownloadTaskDao

    companion object {
        fun create(context: Context): StoreDatabase =
            Room.databaseBuilder(context.applicationContext, StoreDatabase::class.java, "tvstore.db").build()
    }
}
