package com.ele.steamprice.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [MonitoredGameEntity::class, PriceHistoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class SteamPriceDatabase : RoomDatabase() {

    abstract fun steamPriceDao(): SteamPriceDao

    companion object {
        @Volatile
        private var INSTANCE: SteamPriceDatabase? = null

        fun getDatabase(context: Context): SteamPriceDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SteamPriceDatabase::class.java,
                    "steam_price_database"
                )
                    // 💡 在开发阶段如果改了表结构，这行代码能防止 App 崩溃，它会自动清空表重新建
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}