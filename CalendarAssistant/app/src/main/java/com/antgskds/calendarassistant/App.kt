package com.antgskds.calendarassistant

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.antgskds.calendarassistant.core.util.CrashHandler
import com.antgskds.calendarassistant.core.util.AnrMonitor
import com.antgskds.calendarassistant.core.calendar.CalendarContentObserver
import com.antgskds.calendarassistant.core.calendar.CalendarReverseSyncWorker
import com.antgskds.calendarassistant.core.capsule.CapsuleStateManager
import com.antgskds.calendarassistant.core.center.CapsuleCenter
import com.antgskds.calendarassistant.core.center.BackupCenter
import com.antgskds.calendarassistant.core.center.ContentIngestCenter
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
import com.antgskds.calendarassistant.core.event.DomainEventBus
import com.antgskds.calendarassistant.core.content.ContentDefinition
import com.antgskds.calendarassistant.core.content.ContentRegistry
import com.antgskds.calendarassistant.core.content.ContentSourceType
import com.antgskds.calendarassistant.core.query.CapsuleRoutingQueryApi
import com.antgskds.calendarassistant.core.query.AlarmRoutingQueryApi
import com.antgskds.calendarassistant.core.operation.BackupOperationApi
import com.antgskds.calendarassistant.core.operation.CapsuleCommandApi
import com.antgskds.calendarassistant.core.operation.IngestCommandApi
import com.antgskds.calendarassistant.core.operation.ScheduleOperationApi
import com.antgskds.calendarassistant.core.operation.SettingsOperationApi
import com.antgskds.calendarassistant.core.operation.WeatherOperationApi
import com.antgskds.calendarassistant.core.query.CapsuleQueryApi
import com.antgskds.calendarassistant.core.query.EventActionQueryApi
import com.antgskds.calendarassistant.core.query.DailySummaryQueryApi
import com.antgskds.calendarassistant.core.query.HomeQueryApi
import com.antgskds.calendarassistant.core.query.NotificationPresentationQueryApi
import com.antgskds.calendarassistant.core.query.NetworkSpeedProbeQueryApi
import com.antgskds.calendarassistant.core.query.ScheduleInsightsQueryApi
import com.antgskds.calendarassistant.core.query.ScheduleQueryApi
import com.antgskds.calendarassistant.core.query.SettingsQueryApi
import com.antgskds.calendarassistant.core.query.SettingsTransformApi
import com.antgskds.calendarassistant.core.query.WeatherQueryApi
import com.antgskds.calendarassistant.data.operation.CapsuleStateManagerCommandApi
import com.antgskds.calendarassistant.data.operation.WeatherRepositoryOperationApi
import com.antgskds.calendarassistant.data.port.StoreDispatcher
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
import com.antgskds.calendarassistant.data.query.WeatherRepositoryQueryApi
import com.antgskds.calendarassistant.core.sms.SmsContentObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class App : Application() {

    companion object {
        // 全局通知渠道常量
        const val CHANNEL_ID_POPUP = "calendar_assistant_popup_channel_v2"
        const val CHANNEL_ID_LIVE = "calendar_assistant_live_channel_v3"

        private const val TAG = "App"
        
        lateinit var instance: App
            private set
    }

    private val storeDispatcher by lazy {
        StoreDispatcher.getInstance(this)
    }

    val scheduleOperationApi: ScheduleOperationApi by lazy {
        storeDispatcher
    }

    val settingsOperationApi: SettingsOperationApi by lazy {
        storeDispatcher
    }

    val backupOperationApi: BackupOperationApi by lazy {
        storeDispatcher
    }

    val scheduleQueryApi: ScheduleQueryApi by lazy {
        storeDispatcher
    }

    val settingsQueryApi: SettingsQueryApi by lazy {
        storeDispatcher
    }

    val homeQueryApi: HomeQueryApi by lazy {
        LocalHomeQueryApi()
    }

    val scheduleInsightsQueryApi: ScheduleInsightsQueryApi by lazy {
        LocalScheduleInsightsQueryApi()
    }

    val dailySummaryQueryApi: DailySummaryQueryApi by lazy {
        LocalDailySummaryQueryApi()
    }

    val settingsTransformApi: SettingsTransformApi by lazy {
        LocalSettingsTransformApi()
    }

    private val weatherRepository by lazy {
        com.antgskds.calendarassistant.core.weather.WeatherRepository.getInstance(applicationContext)
    }

    val weatherQueryApi: WeatherQueryApi by lazy {
        WeatherRepositoryQueryApi(weatherRepository)
    }

    val weatherOperationApi: WeatherOperationApi by lazy {
        WeatherRepositoryOperationApi(weatherRepository)
    }

    val domainEventBus: DomainEventBus by lazy {
        DomainEventBus()
    }

    val eventActionQueryApi: EventActionQueryApi by lazy {
        LocalEventActionQueryApi()
    }

    val notificationPresentationQueryApi: NotificationPresentationQueryApi by lazy {
        LocalNotificationPresentationQueryApi()
    }

    val alarmRoutingQueryApi: AlarmRoutingQueryApi by lazy {
        LocalAlarmRoutingQueryApi()
    }

    val capsuleRoutingQueryApi: CapsuleRoutingQueryApi by lazy {
        LocalCapsuleRoutingQueryApi()
    }

    val scheduleCenter: ScheduleCenter by lazy {
        ScheduleCenter(
            scheduleOperationApi = scheduleOperationApi,
            scheduleQueryApi = scheduleQueryApi,
            settingsQueryApi = settingsQueryApi
        )
    }

    private val importCenter: ImportCenter by lazy {
        ImportCenter(
            scheduleCenter = scheduleCenter
        )
    }

    val contentIngestCenter: ContentIngestCenter by lazy {
        ContentIngestCenter(
            importCenter = importCenter,
            domainEventBus = domainEventBus,
            appScope = appScope
        )
    }

    val recognitionCenter: RecognitionCenter by lazy {
        RecognitionCenter(domainEventBus = domainEventBus)
    }

    val ingestCommandApi: IngestCommandApi by lazy {
        contentIngestCenter
    }

    val ruleCenter: RuleCenter by lazy {
        RuleCenter(applicationContext)
    }

    val syncCenter: SyncCenter by lazy {
        SyncCenter(
            settingsOperationApi = settingsOperationApi,
            settingsQueryApi = settingsQueryApi
        )
    }

    val backupCenter: BackupCenter by lazy {
        BackupCenter(backupOperationApi = backupOperationApi)
    }

    // 日历内容观察者（可选，仅在有权限时初始化）
    private var calendarObserver: CalendarContentObserver? = null

    // 短信内容观察者
    private var smsObserver: SmsContentObserver? = null

    private val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val capsuleStateManager: CapsuleStateManager by lazy {
        CapsuleStateManager(
            scheduleQueryApi = scheduleQueryApi,
            settingsQueryApi = settingsQueryApi,
            appScope = appScope,
            context = applicationContext
        )
    }

    val capsuleCommandApi: CapsuleCommandApi by lazy {
        CapsuleStateManagerCommandApi(capsuleStateManager)
    }

    val capsuleQueryApi: CapsuleQueryApi by lazy {
        CapsuleStateManagerQueryApi(capsuleStateManager)
    }

    val capsuleCenter: CapsuleCenter by lazy {
        CapsuleCenter(
            capsuleCommandApi = capsuleCommandApi,
            capsuleQueryApi = capsuleQueryApi
        )
    }

    val permissionCenter: PermissionCenter by lazy {
        PermissionCenter()
    }

    val floatingCenter: FloatingCenter by lazy {
        FloatingCenter(
            appContext = applicationContext,
            permissionCenter = permissionCenter
        )
    }

    val notificationCenter: NotificationCenter by lazy {
        NotificationCenter(applicationContext)
    }

    val networkSpeedProbeQueryApi: NetworkSpeedProbeQueryApi by lazy {
        LocalNetworkSpeedProbeQueryApi()
    }

    val reminderCenter: ReminderCenter by lazy {
        ReminderCenter(
            appContext = applicationContext,
            capsuleCenter = capsuleCenter,
            settingsQueryApi = settingsQueryApi,
            scheduleQueryApi = scheduleQueryApi,
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

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // 初始化全局崩溃捕获
        CrashHandler.init(this)

        // 启动轻量 ANR 监测
        AnrMonitor.start(this)
        
        // 初始化通知渠道
        createNotificationChannels()

        storeDispatcher.bindCapsuleRefreshHandler {
            capsuleCommandApi.forceRefresh()
        }

        // 预热入库中心，避免识别事件在订阅建立前发出导致“正在创建”无后续。
        contentIngestCenter

        // 注册内容源定义，后续便签/天气/语音可平滑接入统一时间轴和胶囊框架
        ContentRegistry.register(
            ContentDefinition(
                sourceType = ContentSourceType.SCHEDULE,
                displayName = "日程",
                supportsTimeline = true,
                supportsCapsule = true
            )
        )
        ContentRegistry.register(
            ContentDefinition(
                sourceType = ContentSourceType.NOTE,
                displayName = "便签",
                supportsTimeline = true,
                supportsCapsule = false
            )
        )
        ContentRegistry.register(
            ContentDefinition(
                sourceType = ContentSourceType.WEATHER,
                displayName = "天气",
                supportsTimeline = true,
                supportsCapsule = true
            )
        )
        ContentRegistry.register(
            ContentDefinition(
                sourceType = ContentSourceType.VOICE_CAPTURE,
                displayName = "语音输入",
                supportsTimeline = true,
                supportsCapsule = false
            )
        )

        // 初始化日历内容观察者（仅在已有权限时）
        initCalendarObserverIfPermissionGranted()

        // 初始化短信 ContentObserver（监听短信数据库变化，自动提取取件码）
        initSmsObserver()

        runtimeCenter.startAppRoutines()

        reminderCenter.startEventSubscriptions()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // A. 普通提醒渠道 (High Priority, 有声音/震动)
            val popupChannel = NotificationChannel(
                CHANNEL_ID_POPUP,
                "日程提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "普通日程的弹窗提醒"
                enableLights(true)
                enableVibration(true)
            }

            // B. 实况胶囊渠道 (High Priority, 但静音)
            // 胶囊通常伴随系统闹钟，或者是静默显示的 Live Activity，所以不该自己乱叫
            val liveChannel = NotificationChannel(
                CHANNEL_ID_LIVE,
                "实况胶囊",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "进行中日程的实况胶囊"
                setSound(null, null) // 静音
                setShowBadge(false)  // 不显示角标
            }

            notificationManager.createNotificationChannels(listOf(popupChannel, liveChannel))
        }
    }

    /**
     * 初始化日历内容观察者（仅在已有权限时）
     * 避免新安装未授权时崩溃或报错
     */
    private fun initCalendarObserverIfPermissionGranted() {
        if (permissionCenter.hasCalendarPermissions(this)) {
            initCalendarObserver()
        } else {
            Log.d(TAG, "日历权限未授予，跳过 Observer 初始化")
        }
    }

    /**
     * 初始化短信 ContentObserver
     * 监听 content://sms 数据库变化，新短信到来时自动提取取件码。
     * 依赖 READ_SMS 权限，内部已做权限检查。
     */
    private fun initSmsObserver() {
        smsObserver = SmsContentObserver(
            context = this,
            getIngestCommandApi = { try { ingestCommandApi } catch (_: Exception) { null } }
        )
        smsObserver?.register()
    }

    /**
     * 初始化日历内容观察者
     * 监听系统日历的变化，用于反向同步
     * 此方法为 public，可供外部在权限授予后调用
     */
    fun initCalendarObserver() {
        if (calendarObserver != null) {
            Log.d(TAG, "日历 Observer 已初始化，跳过")
            return
        }

        calendarObserver = CalendarContentObserver(applicationContext) {
            Log.d(TAG, "检测到系统日历变化，使用 WorkManager 触发反向同步")
            CalendarReverseSyncWorker.enqueue(applicationContext)
        }
        calendarObserver?.register()
        Log.d(TAG, "日历内容观察者已初始化并注册")
    }

}
