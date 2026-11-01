package com.screensaver.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.screensaver.data.local.entities.ScheduleRuleEntity
import kotlinx.coroutines.flow.Flow

/** rules 单表的数据访问；图片由规则的 imageUri 直接引用，所有记录默认生效。 */
@Dao
interface RuleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ScheduleRuleEntity): Long

    @Update
    suspend fun update(entity: ScheduleRuleEntity)

    @Delete
    suspend fun delete(entity: ScheduleRuleEntity)

    @Query("SELECT * FROM rules ORDER BY start_minute ASC")
    fun observeAll(): Flow<List<ScheduleRuleEntity>>

    @Query("SELECT * FROM rules WHERE id = :id")
    suspend fun getById(id: Long): ScheduleRuleEntity?

    /** 调度器和冲突检查使用的无序快照。 */
    @Query("SELECT * FROM rules")
    suspend fun getAll(): List<ScheduleRuleEntity>
}
