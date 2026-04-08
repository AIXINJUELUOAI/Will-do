package com.antgskds.calendarassistant.core.rule

import android.content.Context
import android.util.Log
import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.core.ai.RulePatchProvider
import com.antgskds.calendarassistant.data.db.AppDatabase
import com.antgskds.calendarassistant.data.db.entity.EventRuleEntity
import com.antgskds.calendarassistant.data.db.entity.EventStateEntity
import com.antgskds.calendarassistant.data.db.entity.EventTransitionEntity
import com.antgskds.calendarassistant.core.rule.RuleIconSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 统一规则查询中心。
 * 一次性从 DB 加载全部规则/状态/转换/图标到内存缓存，
 * 所有查询均为同步方法（读缓存），可安全在 Compose remember 和通知构建器中调用。
 */
object RuleRegistry {
    private const val TAG = "RuleRegistry"

    // --- 缓存 ---
    @Volatile private var rules: Map<String, EventRuleEntity> = emptyMap()
    @Volatile private var statesByRule: Map<String, List<EventStateEntity>> = emptyMap()
    @Volatile private var allStates: Map<String, EventStateEntity> = emptyMap()
    @Volatile private var transitionsByRule: Map<String, List<EventTransitionEntity>> = emptyMap()
    @Volatile private var transitionsByFromState: Map<String, List<EventTransitionEntity>> = emptyMap()
    @Volatile private var iconCache: Map<String, Int> = emptyMap()
    // 用户通过 IconPicker 选择的图标（优先于 iconCache 中的默认值）
    @Volatile private var customCapsuleIconCache: Map<String, Int> = emptyMap()
    // 用户选择的图标资源名（用于 UI 预览）
    @Volatile private var customCapsuleIconNameCache: Map<String, String> = emptyMap()

    // --- 生命周期 ---

    /**
     * 从 DB 刷新全部缓存。App 启动时调用一次，规则编辑后再调用。
     */
    suspend fun refresh(context: Context) {
        withContext(Dispatchers.IO) {
            val appCtx = context.applicationContext
            val db = AppDatabase.getInstance(appCtx)

            // 1. 确保内置规则和默认状态/动作存在
            RulePatchProvider.ensureBuiltins(appCtx)
            RuleActionSeeder.ensureDefaults(appCtx)

            // 2. 加载规则
            val loadedRules = db.eventRuleDao().getAll()
            val newRules = loadedRules.associateBy { it.ruleId }
            rules = newRules

            // 3. 加载状态
            val loadedStates = db.eventStateDao().getAll()
            val newStatesByRule = loadedStates.groupBy { it.ruleId }
            val newAllStates = loadedStates.associateBy { it.stateId }
            statesByRule = newStatesByRule
            allStates = newAllStates

            // 4. 加载转换（一次查询替代 N+1）
            val newTransitionsByRule = mutableMapOf<String, List<EventTransitionEntity>>()
            val newTransitionsByFrom = mutableMapOf<String, MutableList<EventTransitionEntity>>()
            val allTransitions = db.eventTransitionDao().getAll()
            for (t in allTransitions) {
                newTransitionsByRule.getOrPut(t.ruleId) { mutableListOf() }.let { list ->
                    (list as MutableList).add(t)
                }
                newTransitionsByFrom.getOrPut(t.fromStateId) { mutableListOf() }.add(t)
            }
            transitionsByRule = newTransitionsByRule
            transitionsByFromState = newTransitionsByFrom.mapValues { it.value.toList() }

            // 5. 加载图标
            val newIcons = mutableMapOf<String, Int>()
            val newCustomIcons = mutableMapOf<String, Int>()
            val newCustomNames = mutableMapOf<String, String>()
            loadedRules.forEach { rule ->
                val resName = RuleIconResolver.buildFallbackResName(rule.ruleId)
                val resId = resolveResId(appCtx, resName)
                if (resId != null && resId != 0) {
                    newIcons[rule.ruleId] = resId
                }
                // 解析用户自定义图标
                val iconSource = RuleIconSource.parse(rule.iconSourceJson)
                val customResName = iconSource.capsuleIcon.trim()
                if (customResName.isNotBlank()) {
                    val customResId = resolveResId(appCtx, customResName)
                    if (customResId != null && customResId != 0) {
                        newCustomIcons[rule.ruleId] = customResId
                        newCustomNames[rule.ruleId] = customResName
                    }
                }
            }
            iconCache = newIcons
            customCapsuleIconCache = newCustomIcons
            customCapsuleIconNameCache = newCustomNames

            // 6. 刷新模板缓存 (供 RuleDisplayTemplateResolver.renderTitle 使用)
            RuleDisplayTemplateResolver.refresh(appCtx)

            Log.d(TAG, "Refreshed: ${rules.size} rules, ${allStates.size} states, " +
                    "${transitionsByRule.values.sumOf { it.size }} transitions, ${iconCache.size} icons")
        }
    }

    // --- 规则查询 ---

    fun getRule(ruleId: String): EventRuleEntity? = rules[ruleId]

    fun getAllRules(): Collection<EventRuleEntity> = rules.values

    // --- 状态机查询 ---

    fun getStates(ruleId: String): List<EventStateEntity> =
        statesByRule[ruleId] ?: emptyList()

    fun getState(stateId: String): EventStateEntity? = allStates[stateId]

    fun getInitialState(ruleId: String): EventStateEntity? {
        val rule = rules[ruleId] ?: return null
        if (rule.initialStateId.isNotBlank()) {
            val state = allStates[rule.initialStateId]
            if (state != null) return state
        }
        val pendingId = RuleActionDefaults.stateId(ruleId, RuleActionDefaults.STATE_PENDING)
        return allStates[pendingId]
    }

    /**
     * 根据事件的 completed/checkedIn 标志解析当前状态 ID。
     */
    fun resolveCurrentStateId(ruleId: String, isCompleted: Boolean, isCheckedIn: Boolean): String {
        val suffix = RuleActionDefaults.resolveStateSuffix(ruleId, isCompleted, isCheckedIn)
        return RuleActionDefaults.stateId(ruleId, suffix)
    }

    fun getTransitions(ruleId: String): List<EventTransitionEntity> =
        transitionsByRule[ruleId] ?: emptyList()

    fun getTransitionsFrom(fromStateId: String): List<EventTransitionEntity> =
        transitionsByFromState[fromStateId] ?: emptyList()

    fun getPrimaryTransition(ruleId: String, currentStateId: String): EventTransitionEntity? {
        return getTransitionsFrom(currentStateId).firstOrNull()
    }

    // --- 展示查询 ---

    fun getDisplayTemplate(stateId: String): String? = allStates[stateId]?.displayTemplate

    // --- 图标查询 ---

    fun getIconResId(ruleId: String): Int? = iconCache[ruleId]

    /**
     * 获取用户自定义的胶囊图标 resId。
     * @return 用户选择的图标资源 ID，未自定义时返回 null。
     */
    fun getCustomCapsuleIconResId(ruleId: String): Int? = customCapsuleIconCache[ruleId]

    /**
     * 获取用户自定义的胶囊图标资源名（用于 UI 预览）。
     */
    fun getCustomCapsuleIconName(ruleId: String): String? = customCapsuleIconNameCache[ruleId]

    /**
     * 获取规则的默认胶囊图标资源名（iic_stat_{ruleId}）。
     */
    fun getDefaultCapsuleIconName(ruleId: String): String = RuleIconResolver.buildFallbackResName(ruleId)

    fun getIconResIdWithFallback(ruleId: String, context: Context): Int {
        return customCapsuleIconCache[ruleId]
            ?: iconCache[ruleId]
            ?: resolveResId(context, RuleIconResolver.buildFallbackResName(ruleId))
            ?: R.drawable.ic_notification_small
    }

    // --- 内部 ---

    private fun resolveResId(context: Context, resName: String): Int? {
        if (resName.isBlank()) return null
        val resId = context.resources.getIdentifier(resName, "drawable", context.packageName)
        return if (resId == 0) {
            Log.w(TAG, "Icon resource not found: $resName")
            null
        } else {
            resId
        }
    }
}
