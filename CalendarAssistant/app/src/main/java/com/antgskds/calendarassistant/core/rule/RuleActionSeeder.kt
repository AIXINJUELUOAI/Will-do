package com.antgskds.calendarassistant.core.rule

import android.content.Context
import com.antgskds.calendarassistant.core.rule.RuleMatchingEngine
import com.antgskds.calendarassistant.data.db.AppDatabase
import com.antgskds.calendarassistant.data.db.dao.EventRuleDao
import com.antgskds.calendarassistant.data.db.dao.EventStateDao
import com.antgskds.calendarassistant.data.db.dao.EventTransitionDao
import com.antgskds.calendarassistant.data.db.entity.EventRuleEntity
import com.antgskds.calendarassistant.data.db.entity.EventStateEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 内置规则 ID — 由开发者维护的模板，启动时始终与 RuleActionDefaults 保持同步。
 * 自定义规则不在此列表中，仅补缺失不覆盖。
 */
private val BUILT_IN_RULE_IDS = setOf(
    RuleMatchingEngine.RULE_GENERAL,
    RuleMatchingEngine.RULE_TRAIN,
    RuleMatchingEngine.RULE_TAXI,
    RuleMatchingEngine.RULE_FLIGHT,
    RuleMatchingEngine.RULE_PICKUP,
    RuleMatchingEngine.RULE_FOOD,
    RuleMatchingEngine.RULE_TICKET,
    RuleMatchingEngine.RULE_SENDER
)

object RuleActionSeeder {
    suspend fun ensureDefaults(context: Context) {
        withContext(Dispatchers.IO) {
            val database = AppDatabase.getInstance(context.applicationContext)
            val ruleDao = database.eventRuleDao()
            val stateDao = database.eventStateDao()
            val transitionDao = database.eventTransitionDao()
            val rules = ruleDao.getAll()
            rules.forEach { rule ->
                ensureRuleDefaults(rule, ruleDao, stateDao, transitionDao)
            }
        }
    }

    suspend fun ensureRuleDefaults(context: Context, ruleId: String) {
        withContext(Dispatchers.IO) {
            val database = AppDatabase.getInstance(context.applicationContext)
            val ruleDao = database.eventRuleDao()
            val stateDao = database.eventStateDao()
            val transitionDao = database.eventTransitionDao()
            val rule = ruleDao.getById(ruleId) ?: return@withContext
            ensureRuleDefaults(rule, ruleDao, stateDao, transitionDao)
        }
    }

    private suspend fun ensureRuleDefaults(
        rule: EventRuleEntity,
        ruleDao: EventRuleDao,
        stateDao: EventStateDao,
        transitionDao: EventTransitionDao
    ) {
        val defaults = RuleActionDefaults.defaultsFor(rule.ruleId)
        val isBuiltIn = rule.ruleId in BUILT_IN_RULE_IDS

        // --- 状态 ---
        val desiredStates = RuleActionDefaults.buildStates(rule.ruleId, defaults)
        if (isBuiltIn) {
            // 内置规则：同步结构性字段（name, isTerminal），但保留用户自定义的 displayTemplate
            val existingStates = stateDao.getByRuleId(rule.ruleId).associateBy { it.stateId }
            val merged = desiredStates.map { desired ->
                val existing = existingStates[desired.stateId]
                if (existing != null) {
                    // 已存在：保留用户的 displayTemplate，更新其他字段
                    desired.copy(displayTemplate = existing.displayTemplate)
                } else {
                    desired
                }
            }
            stateDao.insertAll(merged)
        } else {
            // 自定义规则：仅补缺失，保留用户修改
            val existingStateIds = stateDao.getByRuleId(rule.ruleId).map { it.stateId }.toSet()
            val missing = desiredStates.filter { it.stateId !in existingStateIds }
            if (missing.isNotEmpty()) stateDao.insertAll(missing)
        }

        // --- 转换 ---
        val desiredTransitions = RuleActionDefaults.buildTransitions(rule.ruleId, defaults)
        if (isBuiltIn) {
            transitionDao.insertAll(desiredTransitions)
        } else {
            val existingTransitionIds = transitionDao.getByRuleId(rule.ruleId)
                .map { it.transitionId }.toSet()
            val missing = desiredTransitions.filter { it.transitionId !in existingTransitionIds }
            if (missing.isNotEmpty()) transitionDao.insertAll(missing)
        }

        // --- 初始状态指针 ---
        val pendingStateId = RuleActionDefaults.stateId(rule.ruleId, defaults.pending.suffix)
        val allStateIds = desiredStates.map { it.stateId }.toSet()
        if (rule.initialStateId.isBlank() || rule.initialStateId !in allStateIds) {
            ruleDao.update(rule.copy(initialStateId = pendingStateId))
        }
    }

    suspend fun updateDisplayTemplates(
        context: Context,
        ruleId: String,
        pendingTemplate: String,
        terminalTemplate: String
    ) {
        withContext(Dispatchers.IO) {
            val database = AppDatabase.getInstance(context.applicationContext)
            val stateDao = database.eventStateDao()
            val defaults = RuleActionDefaults.defaultsFor(ruleId)
            val pendingStateId = RuleActionDefaults.stateId(ruleId, defaults.pending.suffix)
            val terminalStateId = RuleActionDefaults.stateId(ruleId, defaults.terminal.suffix)

            val pendingValue = pendingTemplate.trim().ifBlank { defaults.pending.displayTemplate }
            val terminalValue = terminalTemplate.trim().ifBlank { defaults.terminal.displayTemplate }

            val pendingState = stateDao.getById(pendingStateId) ?: EventStateEntity(
                stateId = pendingStateId,
                ruleId = ruleId,
                name = defaults.pending.name,
                isTerminal = defaults.pending.isTerminal,
                displayTemplate = pendingValue
            )
            val terminalState = stateDao.getById(terminalStateId) ?: EventStateEntity(
                stateId = terminalStateId,
                ruleId = ruleId,
                name = defaults.terminal.name,
                isTerminal = defaults.terminal.isTerminal,
                displayTemplate = terminalValue
            )

            stateDao.insertAll(
                listOf(
                    pendingState.copy(displayTemplate = pendingValue),
                    terminalState.copy(displayTemplate = terminalValue)
                )
            )
        }
    }
}
