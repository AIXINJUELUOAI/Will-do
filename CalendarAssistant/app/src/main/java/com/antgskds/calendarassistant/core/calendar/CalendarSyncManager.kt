package com.antgskds.calendarassistant.core.calendar

import android.content.Context
import android.util.Log
import com.antgskds.calendarassistant.data.model.Course
import com.antgskds.calendarassistant.data.model.EventType
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.data.model.SyncData
import com.antgskds.calendarassistant.data.model.TimeNode
import com.antgskds.calendarassistant.data.source.SyncJsonDataSource
import com.antgskds.calendarassistant.core.util.EventDeduplicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 日历同步管理器
 * 负责协调应用与系统日历之间的双向同步流程
 */
class CalendarSyncManager(private val context: Context) {

    companion object {
        private const val TAG = "CalendarSyncManager"

        /**
         * 课程同步的未来周数
         * 只同步未来 N 周的课程，避免生成过多历史事件
         */
        private const val COURSE_SYNC_WEEKS_AHEAD = 16
    }

    private val calendarManager = CalendarManager(context)
    private val syncDataSource = SyncJsonDataSource.getInstance(context)

    // 防止并发同步的标志
    private val _isSyncing = AtomicBoolean(false)

    // ==================== App -> 系统日历同步 ====================

    /**
     * 全量同步：将应用数据同步到系统日历
     * 由 AppRepository 在数据变更时触发
     *
     * @param events 应用内所有事件
     * @param courses 应用内所有课程
     * @param semesterStart 学期开始日期
     * @param totalWeeks 总周数
     * @param timeNodes 作息时间表
     * @return 同步结果
     */
    suspend fun syncAllToCalendar(
        events: List<MyEvent>,
        courses: List<Course>,
        semesterStart: String?,
        totalWeeks: Int,
        timeNodes: List<TimeNode>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 1. 检查权限
            if (!CalendarPermissionHelper.hasAllPermissions(context)) {
                Log.w(TAG, "缺少日历权限，跳过同步")
                return@withContext Result.failure(SecurityException("缺少日历权限"))
            }

            // 2. 读取同步配置
            var syncData = syncDataSource.loadSyncData()

            // 3. 如果未启用同步，直接返回
            if (!syncData.isSyncEnabled) {
                Log.d(TAG, "日历同步未启用，跳过")
                return@withContext Result.success(Unit)
            }

            // 4. 获取或创建目标日历
            val calendarId = if (syncData.targetCalendarId == -1L) {
                val id = calendarManager.getOrCreateAppCalendar()
                if (id == -1L) {
                    return@withContext Result.failure(Exception("无法获取日历 ID"))
                }
                // 更新配置
                syncData = syncData.copy(targetCalendarId = id)
                id
            } else {
                syncData.targetCalendarId
            }

            Log.d(TAG, "开始同步到日历 (ID: $calendarId)")

            // 5. 同步课程（单向强制同步：先删除再重建）
            syncData = syncCourses(courses, semesterStart, totalWeeks, timeNodes, calendarId, syncData)

            // 6. 同步普通事件（双向同步）
            syncData = syncEvents(events, calendarId, syncData)

            // 7. 更新同步时间
            syncData = syncData.copy(lastSyncTime = System.currentTimeMillis())
            syncDataSource.saveSyncData(syncData)

            Log.d(TAG, "同步完成")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "同步失败", e)
            Result.failure(e)
        }
    }

    /**
     * 同步课程（单向强制同步）
     * 策略：先批量删除所有托管课程事件，再重新生成写入
     *
     * @return 更新后的 SyncData（包含新的学期哈希）
     */
    private suspend fun syncCourses(
        courses: List<Course>,
        semesterStart: String?,
        totalWeeks: Int,
        timeNodes: List<TimeNode>,
        calendarId: Long,
        syncData: SyncData
    ): SyncData {
        // 解析学期开始日期
        val parsedSemesterStart = try {
            LocalDate.parse(semesterStart)
        } catch (e: Exception) {
            Log.w(TAG, "解析学期开始日期失败，使用今天: $semesterStart")
            LocalDate.now()
        }

        // 计算当前学期哈希
        val currentSemesterHash = CalendarEventMapper.generateSemesterHash(
            parsedSemesterStart,
            totalWeeks
        )

        // 检查学期配置是否变化
        val needRebuild = syncData.lastSemesterHash != currentSemesterHash

        if (needRebuild) {
            Log.d(TAG, "学期配置变化，需要重建课程事件")

            // 1. 批量删除旧的托管课程事件
            val deletedCount = calendarManager.batchDeleteManagedCourseEvents(calendarId)
            Log.d(TAG, "删除了 $deletedCount 个旧的课程事件")

            // 2. 展开所有课程
            val instances = CalendarEventMapper.expandAllCourses(
                courses = courses,
                semesterStart = parsedSemesterStart,
                totalWeeks = totalWeeks
            )

            // 3. 只同步未来 N 周的课程（避免生成过多历史事件）
            val today = LocalDate.now()
            val weeksAheadDate = today.plusWeeks(COURSE_SYNC_WEEKS_AHEAD.toLong())
            val futureInstances = instances.filter { it.date <= weeksAheadDate }

            // 4. 批量创建课程事件
            if (futureInstances.isNotEmpty()) {
                val mapping = calendarManager.batchCreateCourseEvents(
                    courseEvents = futureInstances,
                    calendarId = calendarId,
                    timeNodes = timeNodes
                )
                Log.d(TAG, "创建了 ${mapping.size} 个新的课程事件")
            }

        } else {
            Log.d(TAG, "学期配置未变化，跳过课程同步")
        }

        // 返回更新后的 SyncData（包含新的学期哈希）
        return syncData.copy(lastSemesterHash = currentSemesterHash)
    }

    /**
     * 同步普通事件（双向同步）
     * 策略：遍历本地事件，有映射则更新，无映射则创建
     * 严格过滤：不同步 eventType == EventType.PICKUP 的临时事件（取件码等）
     */
    private suspend fun syncEvents(
        events: List<MyEvent>,
        calendarId: Long,
        syncData: SyncData
    ): SyncData {
        var updatedSyncData = syncData
        val currentMapping = updatedSyncData.mapping.toMutableMap()

        // 过滤：只同步 eventType == EventType.EVENT 的普通事件
        val eventsToSync = events.filter { it.eventType == EventType.EVENT }

        Log.d(TAG, "普通事件: ${events.size} 个，过滤后: ${eventsToSync.size} 个")

        eventsToSync.forEach { event ->
            try {
                val appId = event.id
                val existingCalendarEventId = currentMapping[appId]?.toLongOrNull()

                if (existingCalendarEventId != null) {
                    // 已有映射：更新事件
                    val success = calendarManager.updateEvent(
                        eventId = existingCalendarEventId,
                        event = event,
                        calendarId = calendarId
                    )
                    if (!success) {
                        Log.w(TAG, "更新事件失败，保留映射留待重试: $appId")
                    }
                } else {
                    // 无映射：创建新事件
                    val newEventId = calendarManager.createEvent(event, calendarId)
                    if (newEventId != -1L) {
                        currentMapping[appId] = newEventId.toString()
                        Log.d(TAG, "创建新事件: $appId -> $newEventId")
                    } else {
                        Log.e(TAG, "创建事件失败: ${event.title}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "同步事件异常: ${event.title}", e)
            }
        }

        // 处理已删除的事件（映射中有但本地已不存在）
        val validAppIds = eventsToSync.map { it.id }.toSet()
        val entriesToDelete = currentMapping.filter { !validAppIds.contains(it.key) }

        if (entriesToDelete.isNotEmpty()) {
            Log.d(TAG, "发现 ${entriesToDelete.size} 个已删除的事件")
            entriesToDelete.forEach { (appId, calendarEventIdStr) ->
                val calendarEventId = calendarEventIdStr.toLongOrNull()
                if (calendarEventId != null) {
                    calendarManager.deleteEvent(calendarEventId)
                }
                currentMapping.remove(appId)
            }
        }

        return updatedSyncData.copy(mapping = currentMapping)
    }

    // ==================== 系统日历 -> App 同步 ====================

    /**
     * 从系统日历同步变更到应用
     *
     * 修复版：
     * 1. 使用 queryEventsByIds 准确追踪已映射事件的更新和删除
     * 2. 扩大 queryEventsInRange 的时间窗口，防止漏掉正在进行或近期的事件
     * 3. 增加 onEventDeleted 回调处理
     * 4. 检查归档事件，防止"僵尸事件"复活
     *
     * @param onEventAdded 新增事件回调
     * @param onEventUpdated 更新事件回调
     * @param onEventDeleted 删除事件回调
     * @param activeEvents 当前活跃事件列表（用于去重检查）
     * @param archivedEvents 当前归档事件列表（用于去重检查）
     */
    suspend fun syncFromCalendar(
        onEventAdded: suspend (MyEvent) -> Unit,
        onEventUpdated: suspend (MyEvent) -> Unit,
        onEventDeleted: suspend (String) -> Unit, // 新增删除回调
        activeEvents: List<MyEvent> = emptyList(), // 新增：活跃事件列表
        archivedEvents: List<MyEvent> = emptyList() // 新增：归档事件列表
    ): Result<Int> = withContext(Dispatchers.IO) {
        // 防止并发同步
        if (_isSyncing.get()) return@withContext Result.success(0)

        try {
            // 1. 检查权限
            if (!CalendarPermissionHelper.hasAllPermissions(context)) {
                return@withContext Result.failure(SecurityException("缺少日历权限"))
            }

            // 2. 读取同步配置
            val syncData = syncDataSource.loadSyncData()
            if (!syncData.isSyncEnabled) return@withContext Result.success(0)

            val calendarId = syncData.targetCalendarId
            if (calendarId == -1L) return@withContext Result.failure(Exception("未配置目标日历"))

            // 3. 准备映射数据
            val mapping = syncData.mapping.toMutableMap()
            // 反向索引: System ID -> App ID
            val systemToAppMap = mapping.entries.associate { (k, v) -> v to k }
            val mappedSystemIds = mapping.values.mapNotNull { it.toLongOrNull() }.toSet()

            Log.d(TAG, "反向同步开始: 映射数量=${mapping.size}, 系统事件ID数量=${mappedSystemIds.size}")

            var addedCount = 0
            var updatedCount = 0
            var deletedCount = 0
            var hasChanges = false

            // ==================== 阶段一：处理已映射的事件 (更新 & 删除) ====================
            // 直接查询这些 ID，无视时间范围，确保能捕捉到修改和删除
            val existingSystemEvents = calendarManager.queryEventsByIds(mappedSystemIds)
            val foundSystemIds = existingSystemEvents.map { it.eventId.toString() }.toSet()

            // 1.1 检测删除：在映射中但系统日历查不到的 ID
            val deletedSystemIds = mapping.values.toSet() - foundSystemIds
            deletedSystemIds.forEach { sysIdStr ->
                val appId = systemToAppMap[sysIdStr]
                if (appId != null) {
                    Log.d(TAG, "检测到事件删除: System ID $sysIdStr -> App ID $appId")
                    onEventDeleted(appId)
                    mapping.remove(appId)
                    hasChanges = true
                    deletedCount++
                }
            }

            // 1.2 检测更新：查到了，同步最新状态
            existingSystemEvents.forEach { systemEvent ->
                val appId = systemToAppMap[systemEvent.eventId.toString()]
                if (appId != null) {
                    // 这里无论 isManaged 是什么都更新，允许用户修改由 App 创建的日程
                    val myEvent = CalendarEventMapper.mapSystemEventToMyEvent(systemEvent, fixedId = appId)
                    if (myEvent != null) {
                        onEventUpdated(myEvent)
                        updatedCount++
                    }
                }
            }

            // ==================== 阶段二：扫描新事件 (新增) ====================
            // 扩大时间窗口：从过去 1 年到未来 1 年，避免遗漏用户修改的旧事件
            val now = System.currentTimeMillis()
            val startMillis = now - 365L * 24 * 60 * 60 * 1000 // 过去 1 年
            val endMillis = now + 365L * 24 * 60 * 60 * 1000  // 未来 1 年

            val rangeEvents = calendarManager.queryEventsInRange(
                calendarId = calendarId,
                startMillis = startMillis,
                endMillis = endMillis
            )

            rangeEvents.forEach { systemEvent ->
                val sysIdStr = systemEvent.eventId.toString()

                // 如果这个 ID 不在映射表中，且不是 App 自己托管的(防止映射丢失后重复导入)
                if (!systemToAppMap.containsKey(sysIdStr) && !systemEvent.isManaged) {
                    // 检查内容是否与活跃或归档事件重复
                    // 防止已归档事件在反向同步时被重新添加
                    val allExistingEvents = activeEvents + archivedEvents
                    val isDuplicate = EventDeduplicator.isContentDuplicate(systemEvent, allExistingEvents)

                    if (!isDuplicate) {
                        // 真正的新事件，添加到 APP
                        val fingerprint = EventDeduplicator.generateFingerprintFromSystemEvent(systemEvent)
                        Log.d(TAG, "检测到新事件: ID=$sysIdStr, title=${systemEvent.title}, fingerprint=$fingerprint")
                        val myEvent = CalendarEventMapper.mapSystemEventToMyEvent(systemEvent)
                        if (myEvent != null) {
                            onEventAdded(myEvent)
                            mapping[myEvent.id] = sysIdStr
                            hasChanges = true
                            addedCount++
                        }
                    } else {
                        // 内容重复，跳过（防止归档事件复活）
                        Log.d(TAG, "跳过重复事件: ${systemEvent.title}")
                    }
                }
            }

            // 4. 保存映射变更
            if (hasChanges) {
                val updatedSyncData = syncData.copy(
                    mapping = mapping,
                    lastSyncTime = System.currentTimeMillis()
                )
                syncDataSource.saveSyncData(updatedSyncData)
            }

            Log.d(TAG, "反向同步完成: +$addedCount, ~$updatedCount, -$deletedCount")
            Result.success(addedCount + updatedCount + deletedCount)

        } catch (e: Exception) {
            Log.e(TAG, "从系统日历同步失败", e)
            Result.failure(e)
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 启用日历同步
     */
    suspend fun enableSync(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!CalendarPermissionHelper.hasAllPermissions(context)) {
                return@withContext Result.failure(SecurityException("缺少日历权限"))
            }

            val calendarId = calendarManager.getOrCreateAppCalendar()
            if (calendarId == -1L) {
                return@withContext Result.failure(Exception("无法获取日历 ID"))
            }

            // 加载原有的 syncData，保留 mapping 避免重新开启同步后事件被复制
            val existingSyncData = syncDataSource.loadSyncData()
            val syncData = SyncData(
                isSyncEnabled = true,
                targetCalendarId = calendarId,
                mapping = existingSyncData.mapping,
                lastSyncTime = System.currentTimeMillis()
            )

            syncDataSource.saveSyncData(syncData)
            Log.d(TAG, "日历同步已启用")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "启用日历同步失败", e)
            Result.failure(e)
        }
    }

    /**
     * 禁用日历同步
     */
    suspend fun disableSync(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val syncData = syncDataSource.loadSyncData()
            val updated = syncData.copy(isSyncEnabled = false)
            syncDataSource.saveSyncData(updated)
            Log.d(TAG, "日历同步已禁用")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "禁用日历同步失败", e)
            Result.failure(e)
        }
    }

    /**
     * 获取当前同步状态
     */
    suspend fun getSyncStatus(): SyncStatus = withContext(Dispatchers.IO) {
        val syncData = syncDataSource.loadSyncData()
        val hasPermission = CalendarPermissionHelper.hasAllPermissions(context)

        SyncStatus(
            isEnabled = syncData.isSyncEnabled,
            hasPermission = hasPermission,
            targetCalendarId = syncData.targetCalendarId,
            lastSyncTime = syncData.lastSyncTime,
            mappedEventCount = syncData.mapping.size
        )
    }

    /**
     * 同步状态
     */
    data class SyncStatus(
        val isEnabled: Boolean,
        val hasPermission: Boolean,
        val targetCalendarId: Long,
        val lastSyncTime: Long,
        val mappedEventCount: Int
    )
}
