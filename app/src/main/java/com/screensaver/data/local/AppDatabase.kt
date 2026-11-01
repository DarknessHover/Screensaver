package com.screensaver.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.screensaver.data.local.dao.RuleDao
import com.screensaver.data.local.entities.ScheduleRuleEntity

/**
 * 本地数据库：单表 `rules`。
 *
 * 当前策略允许 schema 变化时由 `fallbackToDestructiveMigration()` 重建空库，
 * 因为规则数据可由用户重新创建。若未来需要保证升级后保留数据，应改用正式 Migration。
 */
@Database(
    entities = [
        ScheduleRuleEntity::class,
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun ruleDao(): RuleDao

    companion object {
        const val DATABASE_NAME = "screensaver.db"
    }
}
