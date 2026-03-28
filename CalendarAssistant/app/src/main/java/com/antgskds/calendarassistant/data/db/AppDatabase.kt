package com.antgskds.calendarassistant.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.antgskds.calendarassistant.data.db.dao.CalendarSyncMapDao
import com.antgskds.calendarassistant.data.db.dao.EventExcludedDateDao
import com.antgskds.calendarassistant.data.db.dao.EventInstanceDao
import com.antgskds.calendarassistant.data.db.dao.EventMasterDao
import com.antgskds.calendarassistant.data.db.dao.EventRuleDao
import com.antgskds.calendarassistant.data.db.dao.EventStateDao
import com.antgskds.calendarassistant.data.db.dao.EventTransitionDao
import com.antgskds.calendarassistant.data.db.entity.CalendarSyncMapEntity
import com.antgskds.calendarassistant.data.db.entity.EventExcludedDateEntity
import com.antgskds.calendarassistant.data.db.entity.EventInstanceEntity
import com.antgskds.calendarassistant.data.db.entity.EventMasterEntity
import com.antgskds.calendarassistant.data.db.entity.EventRuleEntity
import com.antgskds.calendarassistant.data.db.entity.EventStateEntity
import com.antgskds.calendarassistant.data.db.entity.EventTransitionEntity

/**
 * Room 数据库主类
 * 数据库名：calendar_assistant.db
 * 版本：2
 */
@Database(
    entities = [
        EventMasterEntity::class,
        EventInstanceEntity::class,
        EventRuleEntity::class,
        EventStateEntity::class,
        EventTransitionEntity::class,
        EventExcludedDateEntity::class,
        CalendarSyncMapEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    // DAO
    abstract fun eventMasterDao(): EventMasterDao
    abstract fun eventInstanceDao(): EventInstanceDao
    abstract fun eventRuleDao(): EventRuleDao
    abstract fun eventStateDao(): EventStateDao
    abstract fun eventTransitionDao(): EventTransitionDao
    abstract fun eventExcludedDateDao(): EventExcludedDateDao
    abstract fun calendarSyncMapDao(): CalendarSyncMapDao

    companion object {
        private const val DATABASE_NAME = "calendar_assistant.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE event_rules ADD COLUMN aiTitlePrompt TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * 获取数据库单例
         * 使用双重检查锁定确保线程安全
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    // .fallbackToDestructiveMigration() // 开发阶段可使用
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * 关闭数据库连接（主要用于测试）
         */
        fun closeDatabase() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}
