package com.antgskds.calendarassistant

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.antgskds.calendarassistant.core.util.CrashHandler
import com.antgskds.calendarassistant.core.util.AnrMonitor
import com.antgskds.calendarassistant.core.util.AppLogger
import com.antgskds.calendarassistant.core.capsule.CapsuleStateManager
import com.antgskds.calendarassistant.core.center.CapsuleCenter
import com.antgskds.calendarassistant.core.center.ContentIngestCenter
import com.antgskds.calendarassistant.core.center.DiagnosticLogCenter
import com.antgskds.calendarassistant.core.center.FloatingCenter
import com.antgskds.calendarassistant.core.center.ImportCenter
import com.antgskds.calendarassistant.core.center.NotificationCenter
import com.antgskds.calendarassistant.core.center.PermissionCenter
import com.antgskds.calendarassistant.core.center.RecognitionCenter
import com.antgskds.calendarassistant.core.center.ReminderCenter
import com.antgskds.calendarassistant.core.center.RuleCenter
import com.antgskds.calendarassistant.core.center.RuntimeCenter
import com.antgskds.calendarassistant.core.center.ScheduleCenter
import com.antgskds.calendarassistant.core.center.SyncCenter
import com.antgskds.calendarassistant.core.center.WidgetCenter
import com.antgskds.calendarassistant.core.event.DomainEventBus
import com.antgskds.calendarassistant.core.attachment.EventAttachmentManager
import com.antgskds.calendarassistant.core.content.ContentDefinition
import com.antgskds.calendarassistant.core.content.ContentRegistry
import com.antgskds.calendarassistant.core.content.ContentSourceType
import com.antgskds.calendarassistant.core.query.CapsuleRoutingQueryApi
import com.antgskds.calendarassistant.core.query.AlarmRoutingQueryApi
import com.antgskds.calendarassistant.core.operation.CapsuleCommandApi
import com.antgskds.calendarassistant.core.operation.IngestCommandApi
import com.antgskds.calendarassistant.core.center.BackupCenter
import com.antgskds.calendarassistant.core.query.ScheduleQueryApi
import com.antgskds.calendarassistant.core.operation.SettingsOperationApi
import com.antgskds.calendarassistant.core.operation.WeatherOperationApi
import com.antgskds.calendarassistant.core.query.CapsuleQueryApi
import com.antgskds.calendarassistant.core.query.EventActionQueryApi
import com.antgskds.calendarassistant.core.query.DailySummaryQueryApi
import com.antgskds.calendarassistant.core.query.HomeQueryApi
import com.antgskds.calendarassistant.core.query.NotificationPresentationQueryApi
import com.antgskds.calendarassistant.core.query.NetworkSpeedProbeQueryApi
import com.antgskds.calendarassistant.core.query.ScheduleInsightsQueryApi
import com.antgskds.calendarassistant.core.query.SettingsQueryApi
import com.antgskds.calendarassistant.core.query.SettingsTransformApi
import com.antgskds.calendarassistant.core.query.WeatherQueryApi
import com.antgskds.calendarassistant.data.operation.CapsuleStateManagerCommandApi
import com.antgskds.calendarassistant.data.operation.WeatherRepositoryOperationApi
import com.antgskds.calendarassistant.data.query.CapsuleStateManagerQueryApi
import com.antgskds.calendarassistant.data.query.LocalCapsuleRoutingQueryApi
import com.antgskds.calendarassistant.data.query.LocalAlarmRoutingQueryApi
import com.antgskds.calendarassistant.data.query.LocalDailySummaryQueryApi
import com.antgskds.calendarassistant.data.query.LocalEventActionQueryApi
import com.antgskds.calendarassistant.data.query.LocalHomeQueryApi
import com.antgskds.calendarassistant.data.query.LocalNotificationPresentationQueryApi
import com.antgskds.calendarassistant.data.query.LocalNetworkSpeedProbeQueryApi
import com.antgskds.calendarassistant.data.query.LocalScheduleInsightsQueryApi
import com.antgskds.calendarassistant.data.query.LocalSettingsTransformApi
import com.antgskds.calendarassistant.data.query.LocalWidgetScheduleQueryApi
import com.antgskds.calendarassistant.data.query.WeatherRepositoryQueryApi
import com.antgskds.calendarassistant.data.repository.SettingsRepository
import com.antgskds.calendarassistant.core.center.CalendarCenter
import com.antgskds.calendarassistant.core.center.ClipboardCodeCenter
import com.antgskds.calendarassistant.core.sms.SmsContentObserver
import com.antgskds.calendarassistant.core.sms.SmsPickupIngestCoordinator
import com.antgskds.calendarassistant.core.migration.LegacyDataMigrationCoordinator
import com.antgskds.calendarassistant.core.localmodel.LocalModelManager
import com.antgskds.calendarassistant.core.localmodel.LocalModelLogScheduler
import com.antgskds.calendarassistant.core.localmodel.LiteRtAiEngineClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class App : Application() {

    companion object {
        const val CHANNEL_ID_POPUP = "calendar_assistant_popup_channel_v2"
        const val CHANNEL_ID_LIVE = "calendar_assistant_live_channel_v3"
        const val CHANNEL_ID_WEATHER = "calendar_assistant_weather_channel_v1"
        private const val TAG = "App"
        lateinit var instance: App
            private set
    }

    private val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ══════════════════════════════════════════════════════════════════════
    // 新底层核心
    // ══════════════════════════════════════════════════════════════════════

    val calendarCenter: CalendarCenter by lazy {
        CalendarCenter.getInstance(this)
    }

    val scheduleCenter: ScheduleCenter by lazy {
        ScheduleCenter(calendarCenter, appScope)
    }

    val syncCenter: SyncCenter by lazy {
        SyncCenter(calendarCenter, this)
    }

    val eventAttachmentManager: EventAttachmentManager by lazy {
        EventAttachmentManager(applicationContext)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 设置（独立于日程底层）
    // ══════════════════════════════════════════════════════════════════════

    private val settingsRepository by lazy { SettingsRepository(this) }

    val settingsQueryApi: SettingsQueryApi by lazy {
        object : SettingsQueryApi {
            override val settings = settingsRepository.settingsFlow
        }
    }

    val settingsOperationApi: SettingsOperationApi by lazy {
        object : SettingsOperationApi {
            override fun updateSettings(newSettings: com.antgskds.calendarassistant.data.model.MySettings) {
                settingsRepository.saveSettings(newSettings)
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 天气
    // ══════════════════════════════════════════════════════════════════════

    private val weatherRepository by lazy {
        com.antgskds.calendarassistant.core.weather.WeatherRepository.getInstance(applicationContext)
    }

    val weatherQueryApi: WeatherQueryApi by lazy {
        WeatherRepositoryQueryApi(weatherRepository)
    }

    val weatherOperationApi: WeatherOperationApi by lazy {
        WeatherRepositoryOperationApi(weatherRepository)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 查询 API (独立于日程底层)
    // ══════════════════════════════════════════════════════════════════════

    val domainEventBus: DomainEventBus by lazy { DomainEventBus() }

    val homeQueryApi: HomeQueryApi by lazy { LocalHomeQueryApi() }
    val scheduleInsightsQueryApi: ScheduleInsightsQueryApi by lazy { LocalScheduleInsightsQueryApi() }
    val dailySummaryQueryApi: DailySummaryQueryApi by lazy { LocalDailySummaryQueryApi() }
    val settingsTransformApi: SettingsTransformApi by lazy { LocalSettingsTransformApi() }
    val eventActionQueryApi: EventActionQueryApi by lazy { LocalEventActionQueryApi() }
    val notificationPresentationQueryApi: NotificationPresentationQueryApi by lazy { LocalNotificationPresentationQueryApi() }
    val alarmRoutingQueryApi: AlarmRoutingQueryApi by lazy { LocalAlarmRoutingQueryApi() }
    val capsuleRoutingQueryApi: CapsuleRoutingQueryApi by lazy { LocalCapsuleRoutingQueryApi() }
    val networkSpeedProbeQueryApi: NetworkSpeedProbeQueryApi by lazy { LocalNetworkSpeedProbeQueryApi() }
    val widgetScheduleQueryApi by lazy { LocalWidgetScheduleQueryApi() }

    // ══════════════════════════════════════════════════════════════════════
    // 识别 / 入库
    // ══════════════════════════════════════════════════════════════════════

    val recognitionCenter: RecognitionCenter by lazy {
        RecognitionCenter(domainEventBus = domainEventBus)
    }

    private val importCenter: ImportCenter by lazy {
        ImportCenter(
            scheduleCenter = scheduleCenter,
            settingsQueryApi = settingsQueryApi,
            attachmentManager = eventAttachmentManager
        )
    }

    val contentIngestCenter: ContentIngestCenter by lazy {
        ContentIngestCenter(
            importCenter = importCenter,
            domainEventBus = domainEventBus,
            appScope = appScope
        )
    }

    val ingestCommandApi: IngestCommandApi by lazy { contentIngestCenter }

    val clipboardCodeCenter: ClipboardCodeCenter by lazy {
        ClipboardCodeCenter(
            appContext = applicationContext,
            settingsQueryApi = settingsQueryApi,
            ingestCommandApi = ingestCommandApi,
            appScope = appScope
        )
    }

    val smsPickupIngestCoordinator: SmsPickupIngestCoordinator by lazy {
        SmsPickupIngestCoordinator(
            appScope = appScope,
            getIngestCommandApi = { try { ingestCommandApi } catch (_: Exception) { null } }
        )
    }

    val localModelManager: LocalModelManager by lazy {
        LocalModelManager(applicationContext)
    }

    val liteRtAiEngineClient: LiteRtAiEngineClient by lazy {
        LiteRtAiEngineClient(applicationContext)
    }

    val diagnosticLogCenter: DiagnosticLogCenter by lazy {
        DiagnosticLogCenter(applicationContext)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 规则 / 胶囊 / 权限 / 通知
    // ══════════════════════════════════════════════════════════════════════

    val ruleCenter: RuleCenter by lazy { RuleCenter(applicationContext) }

    val permissionCenter: PermissionCenter by lazy { PermissionCenter() }

    val capsuleStateManager: CapsuleStateManager by lazy {
        CapsuleStateManager(
            scheduleCenter = scheduleCenter,
            settingsQueryApi = settingsQueryApi,
            appScope = appScope,
            context = applicationContext
        )
    }

    val capsuleCommandApi: CapsuleCommandApi by lazy { CapsuleStateManagerCommandApi(capsuleStateManager) }
    val capsuleQueryApi: CapsuleQueryApi by lazy { CapsuleStateManagerQueryApi(capsuleStateManager) }

    val capsuleCenter: CapsuleCenter by lazy {
        CapsuleCenter(capsuleCommandApi = capsuleCommandApi, capsuleQueryApi = capsuleQueryApi)
    }

    val floatingCenter: FloatingCenter by lazy {
        FloatingCenter(appContext = applicationContext, permissionCenter = permissionCenter)
    }

    val notificationCenter: NotificationCenter by lazy { NotificationCenter(applicationContext) }

    val reminderCenter: ReminderCenter by lazy {
        ReminderCenter(
            appContext = applicationContext,
            capsuleCenter = capsuleCenter,
            settingsQueryApi = settingsQueryApi,
            scheduleCenter = scheduleCenter,
            domainEventBus = domainEventBus,
            appScope = appScope
        )
    }

    val runtimeCenter: RuntimeCenter by lazy {
        RuntimeCenter(
            appContext = applicationContext,
            settingsQueryApi = settingsQueryApi,
            permissionCenter = permissionCenter,
            floatingCenter = floatingCenter,
            networkSpeedProbeQueryApi = networkSpeedProbeQueryApi,
            capsuleCenter = capsuleCenter,
            appScope = appScope
        )
    }

    // 短信内容观察者
    private var smsObserver: SmsContentObserver? = null

    val backupCenter: BackupCenter by lazy {
        BackupCenter(
            context = applicationContext,
            scheduleCenter = scheduleCenter,
            settingsQueryApi = settingsQueryApi,
            settingsOperationApi = settingsOperationApi,
            attachmentManager = eventAttachmentManager,
            legacyDataMigrationCoordinator = legacyDataMigrationCoordinator
        )
    }

    val widgetCenter: WidgetCenter by lazy {
        WidgetCenter(
            appContext = applicationContext,
            calendarQueryApi = calendarCenter,
            settingsQueryApi = settingsQueryApi,
            widgetScheduleQueryApi = widgetScheduleQueryApi,
            appScope = appScope
        )
    }

    private val legacyDataMigrationCoordinator: LegacyDataMigrationCoordinator by lazy {
        LegacyDataMigrationCoordinator(
            context = applicationContext,
            calendarCenter = calendarCenter,
            settingsRepository = settingsRepository
        )
    }

    val scheduleQueryApi: ScheduleQueryApi by lazy {
        object : ScheduleQueryApi {
            override val events: kotlinx.coroutines.flow.StateFlow<List<com.antgskds.calendarassistant.calendar.models.Event>>
                get() = scheduleCenter.events
        }
    }

    fun initCalendarObserver() {
        // 第一阶段：日历同步由 StoreRootNode 内部处理，此处为空实现
    }

    // ══════════════════════════════════════════════════════════════════════

    override fun onCreate() {
        super.onCreate()
        instance = this

        if (currentProcessName().endsWith(":ai_engine")) {
            Log.d(TAG, "AI engine process started; skipping main app initialization")
            return
        }

        AppLogger.init(this)
        AppLogger.i(TAG, "main app process started")
        CrashHandler.init(this)
        AnrMonitor.start(this)
        createNotificationChannels()

        // 首启自动迁移旧底层数据（Room/JSON）到新 events.db
        runBlocking(Dispatchers.IO) {
            AppLogger.i(TAG, "legacy data migration check started")
            legacyDataMigrationCoordinator.runAutoMigrationIfNeeded()
            AppLogger.i(TAG, "legacy data migration check finished")
        }

        // 初始化日程数据
        scheduleCenter.refreshEvents()
        AppLogger.i(TAG, "schedule events refreshed count=${scheduleCenter.events.value.size}")
        scheduleCenter.onScheduleChanged = { widgetCenter.requestRefresh() }

        appScope.launch(Dispatchers.IO) {
            runCatching { eventAttachmentManager.migrateLegacyDescriptionMarkers() }
                .onFailure { Log.w(TAG, "Failed to migrate legacy source image markers", it) }
            scheduleCenter.refreshEvents()
        }

        // 预热入库中心
        contentIngestCenter

        // 注册内容源
        ContentRegistry.register(ContentDefinition(ContentSourceType.SCHEDULE, "日程", true, true))
        ContentRegistry.register(ContentDefinition(ContentSourceType.NOTE, "便签", true, false))
        ContentRegistry.register(ContentDefinition(ContentSourceType.WEATHER, "天气", true, true))
        ContentRegistry.register(ContentDefinition(ContentSourceType.VOICE_CAPTURE, "语音输入", true, false))

        // CalDAVUpdateListener 通过 JobScheduler 自动监听系统日历变化
        // 需要在 sync 开启时主动注册一次 content observer job
        if (syncCenter.getSyncStatus().isEnabled) {
            try {
                com.antgskds.calendarassistant.calendar.jobs.CalDAVUpdateListener()
                    .scheduleJob(this)
            } catch (_: Exception) { }
        }

        initSmsObserver()
        if (settingsQueryApi.settings.value.isLocalSemanticEnabled) {
            LocalModelLogScheduler.scheduleNext(this)
        }
        runtimeCenter.startAppRoutines()
        reminderCenter.startEventSubscriptions()
        widgetCenter.startSubscriptions()
        AppLogger.i(TAG, "main app routines started")
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            val popupChannel = NotificationChannel(CHANNEL_ID_POPUP, "日程提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "普通日程的弹窗提醒"; enableLights(true); enableVibration(true)
            }
            val liveChannel = NotificationChannel(CHANNEL_ID_LIVE, "实况胶囊", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "进行中日程的实况胶囊"; setSound(null, null); setShowBadge(false)
            }
            val weatherChannel = NotificationChannel(CHANNEL_ID_WEATHER, "天气预警", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "天气预警和风险提醒"; enableLights(true); enableVibration(true)
            }
            notificationManager.createNotificationChannels(listOf(popupChannel, liveChannel, weatherChannel))
        }
    }

    private fun initSmsObserver() {
        smsObserver = SmsContentObserver(
            context = this,
            getSmsPickupIngestCoordinator = { try { smsPickupIngestCoordinator } catch (_: Exception) { null } }
        )
        smsObserver?.register()
    }

    private fun currentProcessName(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return getProcessName().orEmpty()
        }
        return runCatching {
            val pid = android.os.Process.myPid()
            val manager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
            manager.runningAppProcesses
                ?.firstOrNull { it.pid == pid }
                ?.processName
                .orEmpty()
        }.getOrDefault("")
    }
}
