package com.antgskds.calendarassistant.core.center

import android.content.Context
import com.antgskds.calendarassistant.core.rule.RuleRegistry
import com.antgskds.calendarassistant.data.db.AppDatabase
import com.antgskds.calendarassistant.data.db.entity.EventRuleEntity
import com.antgskds.calendarassistant.data.db.entity.EventStateEntity

class RuleCenter(appContext: Context) {
    private val applicationContext = appContext.applicationContext
    private val database by lazy { AppDatabase.getInstance(applicationContext) }
    private val ruleDao by lazy { database.eventRuleDao() }
    private val stateDao by lazy { database.eventStateDao() }

    suspend fun getAllRules(): List<EventRuleEntity> {
        return ruleDao.getAll()
    }

    suspend fun upsertRule(rule: EventRuleEntity) {
        ruleDao.insert(rule)
    }

    suspend fun updateRuleEnabled(rule: EventRuleEntity, enabled: Boolean) {
        ruleDao.update(rule.copy(isEnabled = enabled))
    }

    suspend fun deleteRule(ruleId: String) {
        ruleDao.delete(ruleId)
        stateDao.deleteByRuleId(ruleId)
    }

    suspend fun getStatesByRuleId(ruleId: String): List<EventStateEntity> {
        return stateDao.getByRuleId(ruleId)
    }

    suspend fun refreshRegistry() {
        RuleRegistry.refresh(applicationContext)
    }
}
