package com.screensaver.data.repository

import com.screensaver.data.local.dao.RuleDao
import com.screensaver.data.local.entities.ScheduleRuleEntity
import kotlinx.coroutines.flow.Flow

/**
 * 规则数据访问（RuleDao 的薄封装，保留类以隔离 UI/调度层与 Room DAO 细节）。
 * 单一实现、无多态需求，故使用具体类而非接口。
 */
class ScreensaverRepository(private val ruleDao: RuleDao) {

    fun observeRules(): Flow<List<ScheduleRuleEntity>> = ruleDao.observeAll()

    /** 供调度器和冲突检查读取全部当前规则。 */
    suspend fun getAllRules(): List<ScheduleRuleEntity> = ruleDao.getAll()

    suspend fun insertRule(entity: ScheduleRuleEntity): Long = ruleDao.insert(entity)
    suspend fun updateRule(entity: ScheduleRuleEntity) = ruleDao.update(entity)
    suspend fun deleteRule(entity: ScheduleRuleEntity) = ruleDao.delete(entity)
    suspend fun getRule(id: Long): ScheduleRuleEntity? = ruleDao.getById(id)
}
