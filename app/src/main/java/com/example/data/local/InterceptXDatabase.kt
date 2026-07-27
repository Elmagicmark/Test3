package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        HttpTransactionEntity::class,
        RepeaterTabEntity::class,
        InterceptedRequestEntity::class,
        TargetScopeEntity::class,
        SecurityProjectEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class InterceptXDatabase : RoomDatabase() {
    abstract fun httpTransactionDao(): HttpTransactionDao
    abstract fun repeaterDao(): RepeaterDao
    abstract fun interceptedRequestDao(): InterceptedRequestDao
    abstract fun targetScopeDao(): TargetScopeDao
    abstract fun securityProjectDao(): SecurityProjectDao

    companion object {
        @Volatile
        private var INSTANCE: InterceptXDatabase? = null

        fun getDatabase(context: Context): InterceptXDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    InterceptXDatabase::class.java,
                    "interceptx_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
