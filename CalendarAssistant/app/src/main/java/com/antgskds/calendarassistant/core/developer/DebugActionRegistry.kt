package com.antgskds.calendarassistant.core.developer

import android.util.Log
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.data.model.EventPatch
import com.antgskds.calendarassistant.data.model.WeatherAlertData
import com.antgskds.calendarassistant.data.model.WeatherRiskAlert
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationBehavior
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationAction
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationDisplaySnapshot
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationKey
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationKind
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationQuery
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationRequest
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationRoute
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationTapTarget
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationTapTargetType
import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.core.query.DailySummaryPayload
import com.antgskds.calendarassistant.feature.weather.domain.WeatherAlertIconMapper
import com.antgskds.calendarassistant.platform.receiver.EventActionReceiver
import com.antgskds.calendarassistant.service.capsule.CapsuleActionSpec
import com.antgskds.calendarassistant.shared.management.resource.notification.display.normal.ScheduleNormalDisplay
import java.time.LocalDate
import com.antgskds.calendarassistant.core.ai.RecognitionFailureDisplay
import com.antgskds.calendarassistant.service.capsule.NetworkSpeedMonitor
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog
import com.antgskds.calendarassistant.shared.management.catalog.NotificationKindCatalog
import com.antgskds.calendarassistant.shared.management.catalog.PageCatalog
import com.antgskds.calendarassistant.shared.management.catalog.PipelineCatalog
import com.antgskds.calendarassistant.shared.management.catalog.HelperCatalog
import com.antgskds.calendarassistant.shared.management.catalog.PolicyCatalog
import com.antgskds.calendarassistant.shared.management.catalog.WorkerCatalog
import com.antgskds.calendarassistant.core.rule.RecognitionRuleCatalog
import com.antgskds.calendarassistant.core.content.ContentRegistry
import com.antgskds.calendarassistant.shared.management.catalog.FeatureCatalog
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 一个调试动作的声明。
 *
 * - [id]：稳定标识。adb 通过 `debug:<id>` 触发；未来开发者页也用它作为 key。
 * - [label]：给（未来）开发者页 UI 显示的人类可读名称。
 * - [category]：分组（通知 / 胶囊 / 事件 / 元…），供 UI 按组渲染。
 * - [dangerous]：高风险动作（如写入持久化数据），UI 可据此加二次确认或显著标记。
 * - [execute]：动作本体。**只接收 [App]、只调用其暴露的公开 api**
 *   （notificationCenter / scheduleCenter / reminderCenter / settings），
 *   不反射、不直接钻内部实现——这是调试动作的硬纪律。
 */
class DebugAction(
    val id: String,
    val label: String,
    val category: String,
    val dangerous: Boolean = false,
    val execute: suspend (App) -> Unit,
)

/**
 * 调试动作注册中心：ADB 后门与（未来）开发者页**共用的唯一清单**。
 *
 * 设计要点（对应架构纪律「注册要承重」）：
 * - 加一个调试动作 = 在 [actions] 里登记一条，adb 与 UI 都照这张表走，
 *   不再在各处各写各的分发逻辑（散落清单一定会烂、维护者也看不出）。
 * - 接收器只负责「按 id 查表 → 执行」，不再持有任何具体动作实现。
 * - 所有动作只调 [App] 的公开 api，保证「不在注册表里就接不进去」且不触碰内部实现。
 */
object DebugActionRegistry {

    val actions: List<DebugAction> = listOf(
        // —— 通知 ——
        DebugAction("dump", "导出通知快照", CATEGORY_NOTIFICATION) { app -> dump(app) },
        DebugAction("resync", "重排所有系统闹钟", CATEGORY_NOTIFICATION) { app ->
            Log.d(DEBUG_TAG, "resync requested")
            app.notificationCenter.rescheduleAllAlarms()
        },
        DebugAction("reconcile", "强制全量重排（含重复窗口）", CATEGORY_NOTIFICATION) { app ->
            Log.d(DEBUG_TAG, "reconcile requested")
            app.reminderCenter.reconcileAllNow()
        },
        DebugAction("weather-alert", "触发天气预警（普通+灵动两形态）", CATEGORY_NOTIFICATION) { app ->
            fireWeatherAlert(app)
        },
        DebugAction("weather-risk", "触发天气风险（普通+灵动两形态）", CATEGORY_NOTIFICATION) { app ->
            fireWeatherRisk(app)
        },
    ) + weatherSampleDebugActions() + listOf(
        // —— 通知测试（迁自实验室：普通通知 / 每日提醒 / 新链路预览；与旧 chip 同实现）——
        DebugAction("test-plain-schedule", "测试普通通知·日程", CATEGORY_NOTIFICATION) { app ->
            firePlainSample(app, "schedule", "调试·日程", "15 分钟后开始：检查普通日程通知样式。", R.drawable.ic_stat_event)
        },
        DebugAction("test-plain-pickup", "测试普通通知·取件", CATEGORY_NOTIFICATION) { app ->
            firePlainSample(app, "pickup", "调试·取件", "菜鸟驿站 3-2-101，取件码 8-2333。", R.drawable.ic_stat_package)
        },
        DebugAction("test-plain-course", "测试普通通知·课程", CATEGORY_NOTIFICATION) { app ->
            firePlainSample(app, "course", "调试·课程", "高等数学即将开始，地点：教学楼 A203。", R.drawable.ic_stat_course)
        },
        DebugAction("test-normal-double-action", "测试普通通知·双按钮", CATEGORY_NOTIFICATION) { app ->
            fireNormalDoubleAction(app)
        },
        DebugAction("test-daily-today", "测试每日提醒·今日", CATEGORY_NOTIFICATION) { app ->
            fireDailySummary(app, isMorning = true)
        },
        DebugAction("test-daily-tomorrow", "测试每日提醒·明日", CATEGORY_NOTIFICATION) { app ->
            fireDailySummary(app, isMorning = false)
        },
        DebugAction("test-new-chain-preview", "新链路预览·普通提醒", CATEGORY_NOTIFICATION) { app ->
            previewNewChain(app)
        },
        // —— 胶囊 ——
        DebugAction("capsule:on", "实况胶囊：开", CATEGORY_CAPSULE) { app -> setCapsule(app, true) },
        DebugAction("capsule:off", "实况胶囊：关", CATEGORY_CAPSULE) { app -> setCapsule(app, false) },
        // —— 胶囊测试（迁自实验室「实况胶囊」段；与旧 chip 同实现，gate 于实况胶囊开关）——
        DebugAction("test-capsule-ocr", "测试胶囊·OCR 进度", CATEGORY_CAPSULE) { app -> fireOcrProgress(app) },
        DebugAction("test-capsule-recognition-1", "测试胶囊·识别成功", CATEGORY_CAPSULE) { app -> fireRecognitionSuccess(app, 1) },
        DebugAction("test-capsule-recognition-2", "测试胶囊·识别成功 x2", CATEGORY_CAPSULE) { app -> fireRecognitionSuccess(app, 2) },
        DebugAction("test-capsule-recognition-fail", "测试胶囊·识别失败", CATEGORY_CAPSULE) { app -> fireRecognitionFailure(app) },
        DebugAction("test-capsule-model-loading", "测试胶囊·模型加载", CATEGORY_CAPSULE) { app -> fireModelLoading(app) },
        DebugAction("test-capsule-network-speed", "测试胶囊·网速", CATEGORY_CAPSULE) { app -> fireNetworkSpeed(app) },
        DebugAction("test-live-double-action", "测试实况通知·双按钮", CATEGORY_CAPSULE) { app -> fireLiveDoubleAction(app) },
        DebugAction("test-live-daily-today", "测试实况通知·每日提醒今日", CATEGORY_CAPSULE) { app ->
            fireDailySummary(app, isMorning = true, requireLive = true)
        },
        DebugAction("test-live-daily-tomorrow", "测试实况通知·每日提醒明日", CATEGORY_CAPSULE) { app ->
            fireDailySummary(app, isMorning = false, requireLive = true)
        },
        DebugAction("test-capsule-clear", "测试胶囊·清除", CATEGORY_CAPSULE) { app -> clearTestCapsules(app) },
        // —— 事件（写入持久化数据，标 dangerous）——
        DebugAction("create-recurring", "建测试事件：每日重复", CATEGORY_EVENT, dangerous = true) { app ->
            createEvent(app, recurring = true)
        },
        DebugAction("create-single", "建测试事件：单次", CATEGORY_EVENT, dangerous = true) { app ->
            createEvent(app, recurring = false)
        },
        DebugAction("create-soon", "建测试事件：约70秒后提醒（验双弹）", CATEGORY_EVENT, dangerous = true) { app ->
            createSoonEvent(app)
        },
        DebugAction("create-missed", "建测试事件：开始已过30秒（验错过补发）", CATEGORY_EVENT, dangerous = true) { app ->
            createMissedEvent(app)
        },
        DebugAction("test-imported-restore", "测试导入归档日程恢复", CATEGORY_EVENT, dangerous = true) { app ->
            testImportedRestore(app)
        },
    ) + developerEventDebugActions() + listOf(
        DebugAction("delete-test-events", "删除所有 DEBUG 测试事件", CATEGORY_EVENT, dangerous = true) { app ->
            deleteTestEvents(app)
        },
        // —— 元 ——
        DebugAction("actions", "列出已注册调试动作", CATEGORY_META) { _ -> listActions() },
        DebugAction("verify-sort", "验证列表排序方向（正序/倒序对比）", CATEGORY_META) { app -> verifySort(app) },
        DebugAction("registry-check", "自检所有注册台账（页面/通知类型/事件类型/配置/调试动作/流程/工具/策略/后台任务）", CATEGORY_META) { _ -> registryCheck() },
    )

    /** 按 id 查动作；接收器据此分发，未找到返回 null。 */
    fun find(id: String): DebugAction? = actions.firstOrNull { it.id == id }

    // ============================ 动作实现（只调公开 api） ============================

    private suspend fun dump(app: App) {
        val all = app.notificationCenter.list(NotificationQuery(includeCancelled = true))
        Log.d(DEBUG_TAG, "dump: ${all.size} snapshots")
        all.forEach { s ->
            Log.d(
                DEBUG_TAG,
                "  key=${s.key.value} state=${s.state} triggerAt=${s.behavior.triggerAtEpochMillis} " +
                    "kind=${s.kind} offset=${s.offsetMinutes}"
            )
        }
    }

    private suspend fun createEvent(app: App, recurring: Boolean) {
        val nowSec = System.currentTimeMillis() / 1000L
        val event = Event(
            id = null,
            startTS = nowSec + 600L,   // 10 分钟后开始
            endTS = nowSec + 3600L,    // 1 小时
            title = if (recurring) "DEBUG 每日重复" else "DEBUG 单次",
            reminder1Minutes = 5,      // 提前 5 分钟
            rrule = if (recurring) "FREQ=DAILY" else ""
        )
        val id = app.scheduleCenter.addEvent(event)
        Log.d(
            DEBUG_TAG,
            "created ${if (recurring) "recurring" else "single"} test event " +
                "id=$id startTS=${event.startTS} reminder=5m"
        )
    }

    private suspend fun createSoonEvent(app: App) {
        val nowSec = System.currentTimeMillis() / 1000L
        val event = Event(
            id = null,
            startTS = nowSec + 70L,    // 约 70 秒后开始
            endTS = nowSec + 3670L,    // 1 小时
            title = "DEBUG 即将开始",
            reminder1Minutes = 0,      // 0 偏移：开始时提醒
            rrule = ""
        )
        val id = app.scheduleCenter.addEvent(event)
        Log.d(DEBUG_TAG, "created soon test event id=$id startTS=${event.startTS} reminder=0m (~70s)")
    }

    private suspend fun createMissedEvent(app: App) {
        val nowSec = System.currentTimeMillis() / 1000L
        val event = Event(
            id = null,
            startTS = nowSec - 30L,    // 开始时间已过去 30 秒
            endTS = nowSec + 3600L,    // 仍在进行中（1 小时后结束）
            title = "DEBUG 错过补发",
            reminder1Minutes = 0,      // 0 偏移：开始时提醒，已错过 → 应即时补发
            rrule = ""
        )
        val id = app.scheduleCenter.addEvent(event)
        Log.d(DEBUG_TAG, "created missed test event id=$id startTS=${event.startTS}(已过30s) reminder=0m，预期 MISSED_IMMEDIATE 补发")
    }

    private suspend fun deleteTestEvents(app: App) {
        // 匹配调试入口造的事件，旧版工厂事件以 "[DEV] " 开头，新链路专项事件以 "DEBUG " 开头。
        val targets = (app.calendarCenter.getEvents() + app.calendarCenter.getArchivedEvents())
            .filter { it.title.startsWith("DEBUG ") || it.title.startsWith("[DEV] ") }
            .distinctBy { it.id }
        Log.d(DEBUG_TAG, "delete-test-events: found ${targets.size} DEBUG events")
        targets.forEach { e ->
            val id = e.id ?: return@forEach
            app.scheduleCenter.deleteEvent(id)
            Log.d(DEBUG_TAG, "  deleted id=$id title=${e.title}")
        }
        // 删后强制全量重排，清掉重复事件窗口外残留的 rec: 提醒键。
        app.reminderCenter.reconcileAllNow()
        Log.d(DEBUG_TAG, "delete-test-events: reconciled after deletion")
    }

    private suspend fun testImportedRestore(app: App) {
        val nowSec = System.currentTimeMillis() / 1000L
        val original = Event(
            id = null,
            startTS = nowSec - 21 * 24 * 3600L,
            endTS = nowSec - 20 * 24 * 3600L,
            title = "DEBUG 导入归档恢复",
            location = "debug-imported-restore",
            description = "模拟备份导入后的归档/异常状态日程",
            reminder1Minutes = 0,
            state = 99,
            archivedAt = nowSec - 3600L
        )
        val id = app.scheduleCenter.addEvent(original)
        Log.d(
            DEBUG_TAG,
            "imported-restore created id=$id start=${original.startTS} end=${original.endTS} " +
                "state=${original.state} archived=${original.archivedAt}"
        )
        app.scheduleCenter.updateSingleFromPatch(
            id,
            EventPatch(
                title = original.title,
                startTS = nowSec - 30L,
                endTS = nowSec + 3600L,
                location = original.location,
                description = original.description,
                tag = original.tag,
                color = original.color,
                rrule = original.rrule,
                reminder1Minutes = 0,
                reminder2Minutes = original.reminder2Minutes,
                reminder3Minutes = original.reminder3Minutes
            )
        )
        val restored = app.calendarCenter.getEvent(id)
        Log.d(
            DEBUG_TAG,
            "imported-restore after-edit id=$id start=${restored?.startTS} end=${restored?.endTS} " +
                "state=${restored?.state} archived=${restored?.archivedAt}"
        )
    }

    private fun setCapsule(app: App, enabled: Boolean) {
        val current = app.settingsQueryApi.settings.value
        app.settingsOperationApi.updateSettings(current.copy(isLiveCapsuleEnabled = enabled))
        // 回读同步内存 flow（saveSettings 同步更新它），给一个不依赖 XML 落盘的确认信号。
        Log.d(
            DEBUG_TAG,
            "capsule set to $enabled; readback=${app.settingsQueryApi.settings.value.isLiveCapsuleEnabled}"
        )
    }

    /**
     * 触发一次天气「预警」通知，同时打出普通 + 灵动两种形态，二者共用 weatherNotificationTimeoutMs。
     * 只调 App 公开 api（capsuleCenter / notificationCenter / settingsQueryApi），不碰 WeatherNotifier 内部。
     */
    private fun fireWeatherAlert(app: App) {
        val location = "调试·天气"
        val alert = WeatherAlertData(
            id = "debug-alert",
            eventName = "暴雨预警",
            severity = "Orange",
            headline = "调试用暴雨橙色预警",
            description = "用于验证天气通知停留时长的调试预警，应在配置时长后自动消失。",
            instruction = "无需采取实际行动，仅供测试。"
        )
        val timeoutMs = app.settingsQueryApi.settings.value.weatherNotificationTimeoutMs.toLong()
        Log.d(
            DEBUG_TAG,
            "weather-alert fired; timeoutMs=$timeoutMs isLive=${app.settingsQueryApi.settings.value.isLiveCapsuleEnabled}"
        )
        // 灵动形态：胶囊自身读取 weatherNotificationTimeoutMs 排自动清除（需实况胶囊开启才可见）
        app.capsuleCenter.showWeatherAlert(location, alert)
        // 普通形态：传入同一时长，由 setTimeoutAfter 自动消失
        val result = app.notificationCenter.publishPlainNotification(
            weatherPlainRequest(
                key = NotificationKey.weatherAlert("debug-alert"),
                notificationId = DEBUG_WEATHER_ALERT_NOTIF_ID,
                title = "调试·天气预警",
                content = alert.description,
                smallIcon = WeatherAlertIconMapper.officialIconRes(alert),
                timeoutMs = timeoutMs,
                source = "debug_weather_alert"
            )
        )
        Log.d(DEBUG_TAG, "weather-alert new-chain result=$result")
    }

    /**
     * 触发一次天气「风险」通知（软件自算 WeatherRiskAlert），同时打出普通 + 灵动两种形态。
     */
    private fun fireWeatherRisk(app: App) {
        val location = "调试·天气"
        val risk = WeatherRiskAlert(
            id = "debug-risk",
            title = "调试·天气风险提醒",
            level = "medium",
            weatherText = "雷阵雨转中雨",
            message = "用于验证天气通知停留时长的调试风险提醒，应在配置时长后自动消失。"
        )
        val timeoutMs = app.settingsQueryApi.settings.value.weatherNotificationTimeoutMs.toLong()
        Log.d(
            DEBUG_TAG,
            "weather-risk fired; timeoutMs=$timeoutMs isLive=${app.settingsQueryApi.settings.value.isLiveCapsuleEnabled}"
        )
        app.capsuleCenter.showWeatherRisk(location, risk)
        val result = app.notificationCenter.publishPlainNotification(
            weatherPlainRequest(
                key = NotificationKey.weatherAlert("debug-risk"),
                notificationId = DEBUG_WEATHER_RISK_NOTIF_ID,
                title = "调试·天气风险",
                content = risk.message,
                smallIcon = WeatherAlertIconMapper.riskIconRes(risk),
                timeoutMs = timeoutMs,
                source = "debug_weather_risk"
            )
        )
        Log.d(DEBUG_TAG, "weather-risk new-chain result=$result")
    }

    private fun weatherSampleDebugActions(): List<DebugAction> {
        val alertActions = weatherAlertSamples().flatMap { sample ->
            listOf(
                DebugAction(
                    "weather-alert-normal-${sample.id}",
                    "天气普通通知·${sample.label}",
                    CATEGORY_NOTIFICATION
                ) { app -> fireWeatherAlertSample(app, sample, WeatherDebugRoute.NORMAL) },
                DebugAction(
                    "weather-alert-live-${sample.id}",
                    "天气实况通知·${sample.label}",
                    CATEGORY_CAPSULE
                ) { app -> fireWeatherAlertSample(app, sample, WeatherDebugRoute.LIVE) }
            )
        }
        val riskActions = weatherRiskSamples().flatMap { sample ->
            listOf(
                DebugAction(
                    "weather-risk-normal-${sample.id}",
                    "天气普通通知·${sample.label}",
                    CATEGORY_NOTIFICATION
                ) { app -> fireWeatherRiskSample(app, sample, WeatherDebugRoute.NORMAL) },
                DebugAction(
                    "weather-risk-live-${sample.id}",
                    "天气实况通知·${sample.label}",
                    CATEGORY_CAPSULE
                ) { app -> fireWeatherRiskSample(app, sample, WeatherDebugRoute.LIVE) }
            )
        }
        return alertActions + riskActions
    }

    private suspend fun createDeveloperTestEvents(app: App, types: List<DeveloperTestDataFactory.TestEventType>) {
        val settings = app.settingsQueryApi.settings.value
        val sequenceBase = ((System.currentTimeMillis() / 1000L) % 100_000L).toInt()
        var created = 0
        types.forEachIndexed { index, type ->
            val bundle = DeveloperTestDataFactory.build(type, sequenceBase + index, settings)
            bundle.patches.forEach { patch ->
                val id = app.scheduleCenter.addEventFromPatch(patch)
                created++
                Log.d(DEBUG_TAG, "developer-test-event created id=$id type=${type.name} title=${patch.title}")
            }
            bundle.events.forEach { event ->
                val id = app.scheduleCenter.addEvent(event)
                created++
                Log.d(DEBUG_TAG, "developer-test-event created id=$id type=${type.name} title=${event.title}")
            }
        }
        Log.d(DEBUG_TAG, "developer-test-event batch created count=$created types=${types.joinToString { it.name }}")
    }

    private fun developerEventDebugActions(): List<DebugAction> {
        val typeActions = DeveloperTestDataFactory.allTypes.map { type ->
            DebugAction(
                id = "create-dev-${type.name.lowercase()}",
                label = "建测试事件：${type.label}",
                category = CATEGORY_EVENT,
                dangerous = true
            ) { app -> createDeveloperTestEvents(app, listOf(type)) }
        }
        return listOf(
            DebugAction(
                id = "create-dev-all",
                label = "建测试事件：全部类型",
                category = CATEGORY_EVENT,
                dangerous = true
            ) { app -> createDeveloperTestEvents(app, DeveloperTestDataFactory.allTypes) }
        ) + typeActions
    }

    private fun fireWeatherAlertSample(app: App, sample: WeatherAlertDebugSample, route: WeatherDebugRoute) {
        val location = "调试·天气"
        val alert = WeatherAlertData(
            id = "debug-alert-${sample.id}",
            eventName = sample.eventName,
            severity = sample.severity,
            headline = sample.headline,
            description = sample.description,
            instruction = sample.instruction,
            messageTypeCode = sample.messageTypeCode
        )
        val timeoutMs = app.settingsQueryApi.settings.value.weatherNotificationTimeoutMs.toLong()
        if (route.includesLive) {
            app.capsuleCenter.showWeatherAlert(location, alert)
        }
        if (route.includesNormal) {
            val result = app.notificationCenter.publishPlainNotification(
                weatherPlainRequest(
                    key = NotificationKey.weatherAlert("debug-alert-${route.id}-${sample.id}"),
                    notificationId = testNotificationId("weather_alert_${route.id}_${sample.id}"),
                    title = "调试·${sample.label}",
                    content = alert.description,
                    smallIcon = WeatherAlertIconMapper.officialIconRes(alert),
                    timeoutMs = timeoutMs,
                    source = "debug_weather_alert_${sample.id}"
                )
            )
            Log.d(DEBUG_TAG, "weather-alert sample result=$result route=${route.id} sample=${sample.id}")
        }
        Log.d(DEBUG_TAG, "weather-alert sample fired route=${route.id} sample=${sample.id} timeoutMs=$timeoutMs")
    }

    private fun fireWeatherRiskSample(app: App, sample: WeatherRiskDebugSample, route: WeatherDebugRoute) {
        val location = "调试·天气"
        val risk = WeatherRiskAlert(
            id = "debug-risk-${sample.id}",
            title = sample.title,
            level = sample.level,
            weatherText = sample.weatherText,
            message = sample.message
        )
        val timeoutMs = app.settingsQueryApi.settings.value.weatherNotificationTimeoutMs.toLong()
        if (route.includesLive) {
            app.capsuleCenter.showWeatherRisk(location, risk)
        }
        if (route.includesNormal) {
            val result = app.notificationCenter.publishPlainNotification(
                weatherPlainRequest(
                    key = NotificationKey.weatherAlert("debug-risk-${route.id}-${sample.id}"),
                    notificationId = testNotificationId("weather_risk_${route.id}_${sample.id}"),
                    title = "调试·${sample.label}",
                    content = risk.message,
                    smallIcon = WeatherAlertIconMapper.riskIconRes(risk),
                    timeoutMs = timeoutMs,
                    source = "debug_weather_risk_${sample.id}"
                )
            )
            Log.d(DEBUG_TAG, "weather-risk sample result=$result route=${route.id} sample=${sample.id}")
        }
        Log.d(DEBUG_TAG, "weather-risk sample fired route=${route.id} sample=${sample.id} timeoutMs=$timeoutMs")
    }

    private fun weatherAlertSamples(): List<WeatherAlertDebugSample> = listOf(
        WeatherAlertDebugSample(
            id = "heat",
            label = "高温",
            eventName = "高温预警",
            severity = "Orange",
            headline = "调试用高温橙色预警",
            description = "预计白天最高气温将超过 37℃，请减少户外活动。",
            instruction = "注意防暑降温，及时补水。"
        ),
        WeatherAlertDebugSample(
            id = "thunder",
            label = "雷暴",
            eventName = "雷电预警",
            severity = "Yellow",
            headline = "调试用雷电黄色预警",
            description = "未来 2 小时可能出现雷电活动，并伴有短时强降水。",
            instruction = "请远离高处和空旷区域。"
        ),
        WeatherAlertDebugSample(
            id = "rainstorm",
            label = "暴雨",
            eventName = "暴雨预警",
            severity = "Orange",
            headline = "调试用暴雨橙色预警",
            description = "局地 3 小时降雨量可能超过 50 毫米。",
            instruction = "注意防范积水和交通延误。"
        ),
        WeatherAlertDebugSample(
            id = "wind",
            label = "大风",
            eventName = "大风预警",
            severity = "Blue",
            headline = "调试用大风蓝色预警",
            description = "阵风可达 7 到 8 级，请注意高空坠物。",
            instruction = "收好阳台物品，外出注意安全。"
        ),
        WeatherAlertDebugSample(
            id = "haze",
            label = "雾霾",
            eventName = "霾预警",
            severity = "Yellow",
            headline = "调试用霾黄色预警",
            description = "能见度和空气质量下降，敏感人群请减少外出。",
            instruction = "外出建议佩戴口罩。"
        ),
        WeatherAlertDebugSample(
            id = "snow",
            label = "降雪",
            eventName = "道路结冰预警",
            severity = "Yellow",
            headline = "调试用降雪道路结冰预警",
            description = "夜间有降雪，部分路段可能结冰。",
            instruction = "出行注意防滑，谨慎驾驶。"
        ),
        WeatherAlertDebugSample(
            id = "cold",
            label = "低温",
            eventName = "低温预警",
            severity = "Blue",
            headline = "调试用低温蓝色预警",
            description = "最低气温将降至 0℃ 附近。",
            instruction = "注意添衣保暖。"
        ),
        WeatherAlertDebugSample(
            id = "cold-wave-update",
            label = "寒潮更新",
            eventName = "寒潮预警",
            severity = "Orange",
            headline = "调试用寒潮橙色预警更新",
            description = "寒潮影响范围扩大，48 小时内气温明显下降。",
            instruction = "请关注后续预报并做好防寒准备。",
            messageTypeCode = "Update"
        ),
        WeatherAlertDebugSample(
            id = "thunder-cancel",
            label = "雷电解除",
            eventName = "雷电预警解除",
            severity = "Cancel",
            headline = "调试用雷电预警解除",
            description = "本轮雷电天气过程已减弱，预警解除。",
            instruction = "仍请留意短时天气变化。",
            messageTypeCode = "Cancel"
        )
    )

    private fun weatherRiskSamples(): List<WeatherRiskDebugSample> = listOf(
        WeatherRiskDebugSample(
            id = "heat",
            label = "高温风险",
            title = "高温风险提醒",
            level = "high",
            weatherText = "晴热高温",
            message = "未来几小时体感温度较高，户外活动请注意防暑。"
        ),
        WeatherRiskDebugSample(
            id = "rain",
            label = "降雨风险",
            title = "降雨风险提醒",
            level = "medium",
            weatherText = "雷阵雨转中雨",
            message = "通勤时段可能出现明显降雨，请携带雨具并预留时间。"
        ),
        WeatherRiskDebugSample(
            id = "haze",
            label = "雾霾风险",
            title = "雾霾风险提醒",
            level = "medium",
            weatherText = "轻度霾",
            message = "空气扩散条件较差，敏感人群请减少户外停留。"
        )
    )

    private fun weatherPlainRequest(
        key: NotificationKey,
        notificationId: Int,
        title: String,
        content: String,
        smallIcon: Int,
        timeoutMs: Long,
        source: String
    ): NotificationRequest {
        return NotificationRequest(
            key = key,
            kind = NotificationKind.WEATHER_ALERT,
            display = NotificationDisplaySnapshot(
                shortText = "天气提醒",
                primaryText = title,
                secondaryText = content,
                expandedText = content
            ),
            route = NotificationRoute.NORMAL,
            notificationId = notificationId,
            smallIconResId = smallIcon,
            channelKey = App.CHANNEL_ID_WEATHER,
            category = "weather",
            behavior = NotificationBehavior(timeoutAfterMillis = timeoutMs),
            tapTarget = NotificationTapTarget(NotificationTapTargetType.APP_HOME),
            source = source
        )
    }

    private fun firePlainSample(app: App, key: String, title: String, content: String, smallIcon: Int) {
        app.notificationCenter.showPlainNotification(
            notificationId = testNotificationId("plain_$key"),
            title = title,
            content = content,
            channelId = App.CHANNEL_ID_POPUP,
            smallIcon = smallIcon
        )
        Log.d(DEBUG_TAG, "plain test fired: $title")
    }

    private fun fireNormalDoubleAction(app: App) {
        val result = app.notificationCenter.publishPlainNotification(
            NotificationRequest(
                key = NotificationKey.debug("normal-double-action"),
                kind = NotificationKind.DEBUG,
                display = NotificationDisplaySnapshot(
                    shortText = "双按钮测试",
                    primaryText = "调试·普通通知双按钮",
                    secondaryText = "用于验证普通通知是否同时展示两个操作按钮。",
                    expandedText = "这条通知通过新通知链路发布，包含两个调试动作按钮。点击按钮只记录日志，不修改数据。"
                ),
                route = NotificationRoute.NORMAL,
                notificationId = testNotificationId("normal_double_action"),
                smallIconResId = R.drawable.ic_notification_small,
                channelKey = App.CHANNEL_ID_POPUP,
                category = "debug",
                behavior = NotificationBehavior(timeoutAfterMillis = 60_000L),
                tapTarget = NotificationTapTarget(NotificationTapTargetType.APP_HOME),
                actions = debugNotificationActions("normal"),
                source = "debug_normal_double_action"
            )
        )
        Log.d(DEBUG_TAG, "normal double-action result=$result")
    }

    private fun fireLiveDoubleAction(app: App) {
        if (!liveCapsuleReady(app)) return
        app.capsuleCenter.showOcrResult(
            title = "调试·实况双按钮",
            content = "用于验证实况通知是否同时展示两个操作按钮。",
            durationMs = 60_000L,
            actions = debugCapsuleActions()
        )
        Log.d(DEBUG_TAG, "live double-action fired")
    }

    private fun debugNotificationActions(source: String): List<NotificationAction> {
        return listOf(
            NotificationAction(
                key = EventActionReceiver.ACTION_DEBUG_PRIMARY,
                label = "按钮一",
                payload = mapOf("debug_source" to source, "debug_button" to "primary")
            ),
            NotificationAction(
                key = EventActionReceiver.ACTION_DEBUG_SECONDARY,
                label = "按钮二",
                payload = mapOf("debug_source" to source, "debug_button" to "secondary")
            )
        )
    }

    private fun debugCapsuleActions(): List<CapsuleActionSpec> {
        return listOf(
            CapsuleActionSpec(
                label = "按钮一",
                receiverAction = EventActionReceiver.ACTION_DEBUG_PRIMARY
            ),
            CapsuleActionSpec(
                label = "按钮二",
                receiverAction = EventActionReceiver.ACTION_DEBUG_SECONDARY
            )
        )
    }

    private fun fireDailySummary(app: App, isMorning: Boolean, requireLive: Boolean = false) {
        if (requireLive && !liveCapsuleReady(app)) return
        val settings = app.settingsQueryApi.settings.value
        val payload = app.dailySummaryQueryApi.buildPayload(
            isMorning = isMorning,
            settings = settings.copy(isDailySummaryEnabled = true),
            events = app.scheduleCenter.events.value,
            weatherData = app.weatherQueryApi.weatherData.value
        ) ?: fallbackDailySummaryPayload(isMorning)
        app.notificationCenter.showDailySummaryNotification(payload, isMorning)
        Log.d(DEBUG_TAG, "daily-summary fired isMorning=$isMorning shortTitle=${payload.shortTitle}")
    }

    private fun fallbackDailySummaryPayload(isMorning: Boolean): DailySummaryPayload {
        val shortTitle = ScheduleNormalDisplay.dailySummaryShortTitle(isMorning)
        val titles = if (isMorning) {
            listOf("调试·晨会", "调试·取件", "调试·复盘")
        } else {
            listOf("调试·早课", "调试·航班", "调试·晚间复盘")
        }
        return DailySummaryPayload(
            targetDate = if (isMorning) LocalDate.now() else LocalDate.now().plusDays(1),
            title = ScheduleNormalDisplay.dailySummaryTitle(shortTitle, "24°C 阴"),
            shortTitle = shortTitle,
            content = ScheduleNormalDisplay.dailySummaryContent(titles.size, titles),
            eventCount = titles.size,
            fullLines = titles,
            compactLines = listOf(titles.first(), ScheduleNormalDisplay.dailySummaryMoreLine(titles.size - 1))
        )
    }

    private suspend fun previewNewChain(app: App) {
        val result = NotificationDebugActions.previewScheduleReminder(app.notificationCenter)
        Log.d(DEBUG_TAG, "new-chain preview result=$result")
    }

    private fun testNotificationId(key: String): Int =
        ("debug_notification_test:$key".hashCode() and Int.MAX_VALUE)

    private fun liveCapsuleReady(app: App): Boolean {
        val ready = app.settingsQueryApi.settings.value.isLiveCapsuleEnabled
        if (!ready) Log.w(DEBUG_TAG, "实况胶囊开关未开启，胶囊测试不会显示")
        return ready
    }

    private fun fireOcrProgress(app: App) {
        if (!liveCapsuleReady(app)) return
        app.capsuleCenter.showOcrProgress(title = "正在分析截图", content = "调试：OCR 识别中，预计数秒后完成。")
        Log.d(DEBUG_TAG, "capsule ocr-progress fired")
    }

    private fun fireRecognitionSuccess(app: App, count: Int) {
        if (!liveCapsuleReady(app)) return
        app.notificationCenter.showCreatedEventResultNotifications(sourceType = "developer", events = recognitionSampleEvents(count))
        Log.d(DEBUG_TAG, "capsule recognition-success fired count=$count")
    }

    private fun fireRecognitionFailure(app: App) {
        if (!liveCapsuleReady(app)) return
        app.notificationCenter.showRecognitionFailureResultNotification(
            RecognitionFailureDisplay(reason = "模型返回格式异常", suggestion = "请重试，或切换到更稳定的模型")
        )
        Log.d(DEBUG_TAG, "capsule recognition-failure fired")
    }

    private fun fireModelLoading(app: App) {
        if (!liveCapsuleReady(app)) return
        app.capsuleCenter.showModelLoading(title = "本地模型加载中", content = "调试：正在准备本地语义模型。")
        Log.d(DEBUG_TAG, "capsule model-loading fired")
    }

    private fun fireNetworkSpeed(app: App) {
        if (!liveCapsuleReady(app)) return
        app.capsuleCenter.updateNetworkSpeed(NetworkSpeedMonitor.NetworkSpeed(downloadSpeed = 2_621_440L, formattedSpeed = "2.5MB/s"))
        Log.d(DEBUG_TAG, "capsule network-speed fired (需网速胶囊开关才显示)")
    }

    private fun clearTestCapsules(app: App) {
        app.capsuleCenter.clearOcrCapsule()
        app.capsuleCenter.clearModelLoading()
        app.capsuleCenter.clearWeatherCapsules()
        app.capsuleCenter.updateNetworkSpeed(null)
        Log.d(DEBUG_TAG, "test capsules cleared")
    }

    /**
     * 自检所有注册台账：统计项数 + 检查每条登记是否健康（关键字段非空）。
     * 给（不读代码的）维护者和 agent 一条命令验证「注册的东西都没坏」。结果打到 WillDoNotify。
     */
    private fun registryCheck() {
        var problems = 0
        fun warn(msg: String) { problems++; Log.w(DEBUG_TAG, "registry-check 问题: $msg") }

        Log.d(DEBUG_TAG, "===== registry-check 开始 =====")

        // 功能模块
        Log.d(DEBUG_TAG, "功能模块台账(FeatureCatalog): ${FeatureCatalog.features.size} 项")
        FeatureCatalog.features.forEach { fea ->
            if (fea.name.isBlank() || fea.entry.isBlank()) warn("功能模块 ${fea.name} 含空字段")
        }

        // 页面
        Log.d(DEBUG_TAG, "页面台账(PageCatalog): ${PageCatalog.pages.size} 项")
        PageCatalog.pages.forEach { p ->
            if (p.title.isBlank()) warn("页面 ${p.destination} 标题为空")
            if (p.visibility != PageCatalog.PageVisibility.ACTION && p.route.isNullOrBlank()) {
                warn("页面 ${p.destination} 非操作类却没有路由")
            }
        }

        // 通知类型
        Log.d(DEBUG_TAG, "通知类型台账(NotificationKindCatalog): ${NotificationKindCatalog.kinds.size} 项")
        NotificationKindCatalog.kinds.forEach { k ->
            if (k.label.isBlank()) warn("通知类型 ${k.kind} 标签为空")
        }

        // 事件类型
        val eventTypes = RecognitionRuleCatalog.registeredTypes()
        Log.d(DEBUG_TAG, "事件类型台账(RecognitionRuleCatalog): ${eventTypes.size} 项")
        eventTypes.forEach { (tag, name) ->
            if (tag.isBlank() || name.isBlank()) warn("事件类型 tag=$tag name=$name 含空字段")
        }

        // 配置
        Log.d(DEBUG_TAG, "配置台账(ConfigCatalog): ${ConfigCatalog.items.size} 项")
        ConfigCatalog.items.forEach { c ->
            if (c.label.isBlank()) warn("配置项 ${c.key} 标签为空")
        }

        // 内容源（运行时由 App 注册）
        val contentSources = ContentRegistry.getDefinitions()
        Log.d(DEBUG_TAG, "内容源台账(ContentRegistry): ${contentSources.size} 项")
        contentSources.forEach { d ->
            if (d.displayName.isBlank()) warn("内容源 ${d.sourceType} 显示名为空")
        }

        // 调试动作
        Log.d(DEBUG_TAG, "调试动作台账(DebugActionRegistry): ${actions.size} 项")
        val dupIds = actions.groupBy { it.id }.filter { it.value.size > 1 }.keys
        if (dupIds.isNotEmpty()) warn("调试动作存在重复 id: $dupIds")
        actions.forEach { a ->
            if (a.label.isBlank()) warn("调试动作 ${a.id} 标签为空")
        }

        // 流程主线
        Log.d(DEBUG_TAG, "流程主线台账(PipelineCatalog): ${PipelineCatalog.pipelines.size} 项")
        PipelineCatalog.pipelines.forEach { p ->
            if (p.name.isBlank() || p.entry.isBlank()) warn("流程主线 ${p.name} 名称或入口为空")
        }

        // 辅助工具
        Log.d(DEBUG_TAG, "辅助工具台账(HelperCatalog): ${HelperCatalog.helpers.size} 项")
        HelperCatalog.helpers.forEach { h ->
            if (h.name.isBlank() || h.entry.isBlank()) warn("辅助工具 ${h.name} 名称或入口为空")
        }

        // 策略
        Log.d(DEBUG_TAG, "策略台账(PolicyCatalog): ${PolicyCatalog.policies.size} 项")
        PolicyCatalog.policies.forEach { p ->
            if (p.name.isBlank() || p.entry.isBlank()) warn("策略 ${p.name} 名称或入口为空")
        }

        // 后台任务
        Log.d(DEBUG_TAG, "后台任务台账(WorkerCatalog): ${WorkerCatalog.workers.size} 项")
        WorkerCatalog.workers.forEach { w ->
            if (w.name.isBlank() || w.entry.isBlank()) warn("后台任务 ${w.name} 名称或入口为空")
        }

        if (problems == 0) {
            Log.d(DEBUG_TAG, "===== registry-check 通过：所有注册台账健康 =====")
        } else {
            Log.w(DEBUG_TAG, "===== registry-check 完成：发现 $problems 个问题（见上方 W 日志）=====")
        }
    }

    private fun recognitionSampleEvents(count: Int): List<Event> {
        val now = LocalDateTime.now().withSecond(0).withNano(0)
        return (0 until count).map { index ->
            val start = now.plusMinutes(30L + index * 90L)
            val end = start.plusMinutes(45L)
            val startSeconds = start.atZone(ZoneId.systemDefault()).toEpochSecond()
            val endSeconds = end.atZone(ZoneId.systemDefault()).toEpochSecond()
            Event(
                id = -(System.currentTimeMillis() % 100_000L) - index,
                startTS = startSeconds,
                endTS = endSeconds,
                title = if (index == 0) "调试·测试会议" else "调试·测试复盘",
                location = if (index == 0) "会议室 A203" else "线上会议",
                description = if (index == 0) "确认识别结果胶囊的两行内容" else "验证多个日程各发一条结果通知"
            )
        }
    }

    private fun listActions() {
        Log.d(DEBUG_TAG, "registered debug actions: ${actions.size}")
        actions.forEach { a ->
            Log.d(
                DEBUG_TAG,
                "  debug:${a.id} [${a.category}]${if (a.dangerous) " (dangerous)" else ""} - ${a.label}"
            )
        }
    }

    /**
     * 验证「列表排序方向」：自构造今天三条不同时间的样本事件（纯内存，不写库），
     * 分别以正序 / 倒序 settings 跑首页查询，打印两种结果的标题+开始时间顺序，
     * 直接对比即可确认翻转生效且分组结构不乱。
     */
    private fun verifySort(app: App) {
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        fun ts(hour: Int): Long = today.atTime(hour, 0).atZone(zone).toEpochSecond()
        val sample = listOf(
            Event(id = -90101, startTS = ts(9), endTS = ts(10), title = "样本·09点"),
            Event(id = -90102, startTS = ts(12), endTS = ts(13), title = "样本·12点"),
            Event(id = -90103, startTS = ts(15), endTS = ts(16), title = "样本·15点"),
        )
        val base = app.settingsQueryApi.settings.value

        val asc = app.homeQueryApi.buildSnapshot(
            today, sample, base.copy(homeListReverseOrder = false, showTomorrowEvents = false)
        ).currentDateEvents
        val desc = app.homeQueryApi.buildSnapshot(
            today, sample, base.copy(homeListReverseOrder = true, showTomorrowEvents = false)
        ).currentDateEvents

        Log.d(DEBUG_TAG, "verify-sort: today=$today sampleCount=${sample.size} homeToday=${asc.size}")
        Log.d(DEBUG_TAG, "verify-sort [正序] ↓")
        asc.forEachIndexed { i, it -> Log.d(DEBUG_TAG, "  $i. ts=${it.startTS} ${it.title}") }
        Log.d(DEBUG_TAG, "verify-sort [倒序] ↓")
        desc.forEachIndexed { i, it -> Log.d(DEBUG_TAG, "  $i. ts=${it.startTS} ${it.title}") }
        val reversedMatches = asc.map { it.stableKey } == desc.map { it.stableKey }.asReversed()
        Log.d(DEBUG_TAG, "verify-sort: 倒序 == 正序完全翻转 ? $reversedMatches")
    }

    private enum class WeatherDebugRoute(
        val id: String,
        val includesNormal: Boolean,
        val includesLive: Boolean
    ) {
        NORMAL("normal", includesNormal = true, includesLive = false),
        LIVE("live", includesNormal = false, includesLive = true)
    }

    private data class WeatherAlertDebugSample(
        val id: String,
        val label: String,
        val eventName: String,
        val severity: String,
        val headline: String,
        val description: String,
        val instruction: String,
        val messageTypeCode: String = "Alert"
    )

    private data class WeatherRiskDebugSample(
        val id: String,
        val label: String,
        val title: String,
        val level: String,
        val weatherText: String,
        val message: String
    )

    const val DEBUG_TAG = "WillDoNotify"
    private const val CATEGORY_NOTIFICATION = "通知"
    private const val CATEGORY_CAPSULE = "胶囊"
    private const val CATEGORY_EVENT = "事件"
    private const val CATEGORY_META = "元"
    private const val DEBUG_WEATHER_ALERT_NOTIF_ID = 990101
    private const val DEBUG_WEATHER_RISK_NOTIF_ID = 990102
}
