package com.antgskds.calendarassistant

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.antgskds.calendarassistant.shared.util.CrashHandler
import com.antgskds.calendarassistant.shared.util.AnrMonitor
import com.antgskds.calendarassistant.shared.util.AppLogger
import com.antgskds.calendarassistant.feature.capsule.application.CapsuleStateManager
import com.antgskds.calendarassistant.feature.capsule.application.CapsuleController
import com.antgskds.calendarassistant.feature.recognition.application.ingest.IngestPipeline
import com.antgskds.calendarassistant.feature.settings.diagnostics.application.DiagnosticLogExporter
import com.antgskds.calendarassistant.feature.schedule.data.maintenance.DuplicateEventCleaner
import com.antgskds.calendarassistant.platform.floating.FloatingServiceController
import com.antgskds.calendarassistant.feature.recognition.application.ingest.ScheduleIngestWriter
import com.antgskds.calendarassistant.feature.recognition.application.localmodel.LocalModelResidueController
import com.antgskds.calendarassistant.feature.note.application.NoteService
import com.antgskds.calendarassistant.feature.notification.application.NotificationOrchestrator
import com.antgskds.calendarassistant.feature.notification.bracelet.BraceletNotificationPublisher
import com.antgskds.calendarassistant.platform.permission.AndroidPermissionChecker
import com.antgskds.calendarassistant.feature.quickmemo.application.QuickMemoFacade
import com.antgskds.calendarassistant.feature.recognition.application.RecognitionOrchestrator
import com.antgskds.calendarassistant.feature.schedule.notification.ScheduleReminderCoordinator
import com.antgskds.calendarassistant.app.runtime.AppRuntimeCoordinator
import com.antgskds.calendarassistant.feature.schedule.application.ScheduleFacade
import com.antgskds.calendarassistant.feature.schedule.application.sync.CalendarSyncService
import com.antgskds.calendarassistant.platform.widget.WidgetController
import com.antgskds.calendarassistant.shared.event.DomainEventBus
import com.antgskds.calendarassistant.feature.schedule.data.attachment.EventAttachmentManager
import com.antgskds.calendarassistant.shared.content.ContentDefinition
import com.antgskds.calendarassistant.shared.content.ContentRegistry
import com.antgskds.calendarassistant.shared.content.ContentSourceType
import com.antgskds.calendarassistant.feature.note.data.NoteRepository
import com.antgskds.calendarassistant.feature.note.data.migration.LegacyNoteMigrator
import com.antgskds.calendarassistant.feature.quickmemo.data.QuickMemoRepository
import com.antgskds.calendarassistant.feature.quickmemo.data.asr.SherpaParaformerTranscriber
import com.antgskds.calendarassistant.feature.quickmemo.application.audio.QuickMemoAudioPlayer
import com.antgskds.calendarassistant.shared.query.CapsuleRoutingQueryApi
import com.antgskds.calendarassistant.shared.query.AlarmRoutingQueryApi
import com.antgskds.calendarassistant.shared.operation.CapsuleCommandApi
import com.antgskds.calendarassistant.shared.operation.IngestCommandApi
import com.antgskds.calendarassistant.feature.backup.application.BackupCoordinator
import com.antgskds.calendarassistant.shared.query.ScheduleQueryApi
import com.antgskds.calendarassistant.shared.operation.SettingsOperationApi
import com.antgskds.calendarassistant.feature.weather.api.WeatherOperationApi
import com.antgskds.calendarassistant.shared.query.CapsuleQueryApi
import com.antgskds.calendarassistant.shared.query.EventActionQueryApi
import com.antgskds.calendarassistant.shared.query.DailySummaryQueryApi
import com.antgskds.calendarassistant.shared.query.HomeQueryApi
import com.antgskds.calendarassistant.shared.query.NotificationPresentationQueryApi
import com.antgskds.calendarassistant.shared.query.NetworkSpeedProbeQueryApi
import com.antgskds.calendarassistant.shared.query.ScheduleInsightsQueryApi
import com.antgskds.calendarassistant.shared.query.SettingsQueryApi
import com.antgskds.calendarassistant.shared.query.SettingsTransformApi
import com.antgskds.calendarassistant.feature.weather.api.WeatherQueryApi
import com.antgskds.calendarassistant.feature.capsule.data.CapsuleStateManagerCommandApi
import com.antgskds.calendarassistant.feature.weather.data.WeatherRepositoryOperationApi
import com.antgskds.calendarassistant.feature.capsule.data.CapsuleStateManagerQueryApi
import com.antgskds.calendarassistant.feature.capsule.data.LocalCapsuleRoutingQueryApi
import com.antgskds.calendarassistant.feature.schedule.data.query.LocalAlarmRoutingQueryApi
import com.antgskds.calendarassistant.feature.schedule.data.query.LocalDailySummaryQueryApi
import com.antgskds.calendarassistant.feature.schedule.data.query.LocalEventActionQueryApi
import com.antgskds.calendarassistant.feature.home.data.LocalHomeQueryApi
import com.antgskds.calendarassistant.feature.notification.data.LocalNotificationPresentationQueryApi
import com.antgskds.calendarassistant.feature.capsule.data.LocalNetworkSpeedProbeQueryApi
import com.antgskds.calendarassistant.feature.schedule.data.query.LocalScheduleInsightsQueryApi
import com.antgskds.calendarassistant.feature.settings.data.LocalSettingsTransformApi
import com.antgskds.calendarassistant.platform.widget.data.LocalWidgetScheduleQueryApi
import com.antgskds.calendarassistant.feature.weather.data.WeatherRepositoryQueryApi
import com.antgskds.calendarassistant.feature.settings.data.SettingsRepository
import com.antgskds.calendarassistant.feature.notification.data.local.SharedPreferencesNotificationRegistryStore
import com.antgskds.calendarassistant.platform.notification.alarm.AndroidSystemAlarmGateway
import com.antgskds.calendarassistant.platform.notification.normal.AndroidNormalNotificationPublisher
import com.antgskds.calendarassistant.feature.schedule.data.ScheduleStoreGateway
import com.antgskds.calendarassistant.feature.recognition.ingest.clipboard.ClipboardCodeIngestCoordinator
import com.antgskds.calendarassistant.feature.recognition.ingest.sms.SmsPickupIngestCoordinator
import com.antgskds.calendarassistant.platform.receiver.sms.SmsContentObserver
import com.antgskds.calendarassistant.app.runtime.migration.LegacyDataMigrationCoordinator
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
        const val CHANNEL_ID_BRACELET = "calendar_assistant_bracelet_channel_v1"
        const val CHANNEL_ID_VOICE_CAPTURE = "calendar_assistant_voice_capture_v1"
        private const val TAG = "App"
        lateinit var instance: App
            private set
    }

    private val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ══════════════════════════════════════════════════════════════════════
    // 新底层核心
    // ══════════════════════════════════════════════════════════════════════

    val calendarCenter: ScheduleStoreGateway by lazy {
        ScheduleStoreGateway.getInstance(this)
    }

    val scheduleCenter: ScheduleFacade by lazy {
        ScheduleFacade(
            calendarCenter = calendarCenter,
            appScope = appScope,
            notificationApi = notificationCenter,
            eventActionQueryApi = eventActionQueryApi,
            settingsProvider = { settingsQueryApi.settings.value },
            braceletScheduleUpdateNotifier = { event ->
                braceletNotificationPublisher.notifyScheduleEventUpdate(event)
            }
        )
    }

    val syncCenter: CalendarSyncService by lazy {
        CalendarSyncService(calendarCenter, this)
    }

    val eventAttachmentManager: EventAttachmentManager by lazy {
        EventAttachmentManager(applicationContext)
    }

    private val noteRepository: NoteRepository by lazy {
        NoteRepository(com.antgskds.calendarassistant.feature.schedule.data.db.EventsDatabase.getInstance(applicationContext).notesDao())
    }

    val noteCenter: NoteService by lazy {
        NoteService(noteRepository, appScope)
    }

    private val quickMemoRepository: QuickMemoRepository by lazy {
        QuickMemoRepository(com.antgskds.calendarassistant.feature.schedule.data.db.EventsDatabase.getInstance(applicationContext).quickMemoDao())
    }

    val audioPlaybackCenter: QuickMemoAudioPlayer by lazy { QuickMemoAudioPlayer() }

    val quickMemoCenter: QuickMemoFacade by lazy {
        QuickMemoFacade(
            repository = quickMemoRepository,
            appScope = appScope,
            speechTranscriber = SherpaParaformerTranscriber(applicationContext),
            recognitionCenter = recognitionCenter,
            settingsQueryApi = settingsQueryApi,
            appContext = applicationContext,
            notificationCenter = notificationCenter,
            capsuleCommandApi = capsuleCommandApi,
            capsuleQueryApi = capsuleQueryApi
        )
    }

    val legacyNoteMigrationCenter: LegacyNoteMigrator by lazy {
        LegacyNoteMigrator(applicationContext, noteRepository)
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
            override fun updateSettings(newSettings: com.antgskds.calendarassistant.feature.settings.data.model.MySettings) {
                settingsRepository.saveSettings(newSettings)
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 天气
    // ══════════════════════════════════════════════════════════════════════

    private val weatherRepository by lazy {
        com.antgskds.calendarassistant.feature.weather.domain.WeatherRepository.getInstance(applicationContext)
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

    val recognitionCenter: RecognitionOrchestrator by lazy {
        RecognitionOrchestrator(domainEventBus = domainEventBus)
    }

    private val regexAiReviewCoordinator: com.antgskds.calendarassistant.feature.recognition.application.rule.RegexAiReviewCoordinator by lazy {
        com.antgskds.calendarassistant.feature.recognition.application.rule.RegexAiReviewCoordinator(
            appContext = applicationContext,
            scheduleCenter = scheduleCenter,
            appScope = appScope
        )
    }

    private val importCenter: ScheduleIngestWriter by lazy {
        ScheduleIngestWriter(
            scheduleCenter = scheduleCenter,
            settingsQueryApi = settingsQueryApi,
            attachmentManager = eventAttachmentManager
        )
    }

    val contentIngestCenter: IngestPipeline by lazy {
        IngestPipeline(
            importCenter = importCenter,
            domainEventBus = domainEventBus,
            appScope = appScope,
            notificationCenter = notificationCenter,
            settingsProvider = { settingsQueryApi.settings.value },
            regexAiReviewCoordinator = regexAiReviewCoordinator
        )
    }

    val ingestCommandApi: IngestCommandApi by lazy { contentIngestCenter }

    val clipboardCodeCenter: ClipboardCodeIngestCoordinator by lazy {
        ClipboardCodeIngestCoordinator(
            appContext = applicationContext,
            settingsQueryApi = settingsQueryApi,
            ingestCommandApi = ingestCommandApi,
            appScope = appScope
        )
    }

    val localModelResidueCenter: LocalModelResidueController by lazy {
        LocalModelResidueController(
            appContext = applicationContext,
            settingsQueryApi = settingsQueryApi,
            settingsOperationApi = settingsOperationApi,
            appScope = appScope
        )
    }

    val smsPickupIngestCoordinator: SmsPickupIngestCoordinator by lazy {
        SmsPickupIngestCoordinator(
            appScope = appScope,
            settingsQueryApi = settingsQueryApi,
            getIngestCommandApi = { try { ingestCommandApi } catch (_: Exception) { null } }
        )
    }

    val diagnosticLogCenter: DiagnosticLogExporter by lazy {
        DiagnosticLogExporter(applicationContext)
    }

    val duplicateEventCleanupCenter: DuplicateEventCleaner by lazy {
        DuplicateEventCleaner(applicationContext)
    }

    // ══════════════════════════════════════════════════════════════════════
    // 规则 / 胶囊 / 权限 / 通知
    // ══════════════════════════════════════════════════════════════════════

    val permissionCenter: AndroidPermissionChecker by lazy { AndroidPermissionChecker() }

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

    val capsuleCenter: CapsuleController by lazy {
        CapsuleController(capsuleCommandApi = capsuleCommandApi, capsuleQueryApi = capsuleQueryApi)
    }

    val floatingCenter: FloatingServiceController by lazy {
        FloatingServiceController(
            appContext = applicationContext,
            permissionCenter = permissionCenter,
            settingsQueryApi = settingsQueryApi
        )
    }

    val notificationRegistryStore: SharedPreferencesNotificationRegistryStore by lazy {
        SharedPreferencesNotificationRegistryStore(applicationContext)
    }

    val systemAlarmGateway: AndroidSystemAlarmGateway by lazy {
        AndroidSystemAlarmGateway(applicationContext)
    }

    val notificationPublisher: AndroidNormalNotificationPublisher by lazy {
        AndroidNormalNotificationPublisher(applicationContext)
    }

    val braceletNotificationPublisher: BraceletNotificationPublisher by lazy {
        BraceletNotificationPublisher(applicationContext)
    }

    val notificationCenter: NotificationOrchestrator by lazy {
        NotificationOrchestrator(
            appContext = applicationContext,
            registryStore = notificationRegistryStore,
            systemAlarmGateway = systemAlarmGateway,
            platformPublisher = notificationPublisher,
            liveCapsuleEnabledProvider = { settingsQueryApi.settings.value.isLiveCapsuleEnabled }
        )
    }

    val reminderCenter: ScheduleReminderCoordinator by lazy {
        ScheduleReminderCoordinator(
            appContext = applicationContext,
            capsuleCenter = capsuleCenter,
            settingsQueryApi = settingsQueryApi,
            scheduleCenter = scheduleCenter,
            domainEventBus = domainEventBus,
            appScope = appScope
        )
    }

    val runtimeCenter: AppRuntimeCoordinator by lazy {
        AppRuntimeCoordinator(
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

    val backupCenter: BackupCoordinator by lazy {
        BackupCoordinator(
            context = applicationContext,
            scheduleCenter = scheduleCenter,
            settingsQueryApi = settingsQueryApi,
            settingsOperationApi = settingsOperationApi,
            attachmentManager = eventAttachmentManager,
            legacyDataMigrationCoordinator = legacyDataMigrationCoordinator
        )
    }

    val widgetCenter: WidgetController by lazy {
        WidgetController(
            appContext = applicationContext,
            calendarQueryApi = calendarCenter,
            settingsQueryApi = settingsQueryApi,
            widgetScheduleQueryApi = widgetScheduleQueryApi,
            weatherQueryApi = weatherQueryApi,
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
            override val events: kotlinx.coroutines.flow.StateFlow<List<com.antgskds.calendarassistant.feature.schedule.domain.model.Event>>
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

        val processName = currentProcessName().orEmpty()
        if (processName != packageName) {
            Log.i(TAG, "secondary app process started early: $processName, skipping main init")
            return
        }

        AppLogger.init(this)
        AppLogger.i(TAG, "main app process started")
        CrashHandler.init(this)
        AnrMonitor.start(this)
        createNotificationChannels()
        calendarCenter.attachDomainEventBus(domainEventBus)

        // 首启自动迁移旧底层数据（Room/JSON）到新 events.db
        runBlocking(Dispatchers.IO) {
            AppLogger.i(TAG, "legacy data migration check started")
            legacyDataMigrationCoordinator.runAutoMigrationIfNeeded()
            legacyNoteMigrationCenter.runAutoMigrationIfNeeded()
            val duplicateCleanup = duplicateEventCleanupCenter.runAutoCleanupIfNeeded()
            if (duplicateCleanup.deleted > 0 || duplicateCleanup.mergedBindings > 0) {
                AppLogger.i(TAG, "duplicate event cleanup result=$duplicateCleanup")
            }
            AppLogger.i(TAG, "legacy data migration check finished")
        }

        // 初始化日程数据
        scheduleCenter.refreshEvents()
        noteCenter.start()
        quickMemoCenter.start()
        AppLogger.i(TAG, "schedule events refreshed count=${scheduleCenter.events.value.size}")
        scheduleCenter.onScheduleChanged = {
            widgetCenter.requestRefresh(com.antgskds.calendarassistant.platform.widget.WidgetType.SCHEDULE)
            widgetCenter.requestRefresh(com.antgskds.calendarassistant.platform.widget.WidgetType.COURSE)
        }

        appScope.launch(Dispatchers.IO) {
            runCatching { eventAttachmentManager.migrateLegacyDescriptionMarkers() }
                .onFailure { Log.w(TAG, "Failed to migrate legacy source image markers", it) }
            scheduleCenter.refreshEvents()
        }

        // 预热入库中心
        contentIngestCenter

        // 注册内容源
        ContentRegistry.register(ContentDefinition(ContentSourceType.SCHEDULE, "日程", true, true))
        ContentRegistry.register(ContentDefinition(ContentSourceType.WEATHER, "天气", true, true))
        ContentRegistry.register(ContentDefinition(ContentSourceType.VOICE_CAPTURE, "随口记", true, false))
        ContentRegistry.register(ContentDefinition(ContentSourceType.IMAGE_SHARE, "图片分享", false, false))

        // CalDAVUpdateListener 通过 JobScheduler 自动监听系统日历变化
        // 需要在 sync 开启时主动注册一次 content observer job
        if (syncCenter.getSyncStatus().isEnabled) {
            try {
                com.antgskds.calendarassistant.calendar.jobs.CalDAVUpdateListener()
                    .scheduleJob(this)
            } catch (_: Exception) { }
        }

        refreshSmsObserver()
        runtimeCenter.startAppRoutines()
        reminderCenter.startEventSubscriptions()
        reminderCenter.reconcileAll()
        widgetCenter.startSubscriptions()
        AppLogger.i(TAG, "main app routines started")
    }

    private fun currentProcessName(): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Application.getProcessName()
        }
        val pid = android.os.Process.myPid()
        val manager = getSystemService(android.app.ActivityManager::class.java) ?: return null
        return manager.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName
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
            val braceletChannel = NotificationChannel(CHANNEL_ID_BRACELET, "手环通知", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "同步到手环的短通知"; enableLights(true); enableVibration(true); setShowBadge(false)
            }
            val voiceCaptureChannel = NotificationChannel(
                CHANNEL_ID_VOICE_CAPTURE,
                "随口记录音服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "后台录音时 Android 要求保留的运行状态"
                setSound(null, null)
                enableLights(false)
                enableVibration(false)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannels(
                listOf(popupChannel, liveChannel, weatherChannel, braceletChannel, voiceCaptureChannel)
            )
        }
    }

    fun refreshSmsObserver(enabled: Boolean = settingsQueryApi.settings.value.isSmsMonitoringEnabled) {
        if (smsObserver == null) {
            smsObserver = SmsContentObserver(
                context = this,
                getSmsPickupIngestCoordinator = { try { smsPickupIngestCoordinator } catch (_: Exception) { null } }
            )
        }
        val hasReadSmsPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
        if (enabled && hasReadSmsPermission) {
            smsObserver?.register()
        } else {
            smsObserver?.unregister()
        }
    }
}
