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
import com.antgskds.calendarassistant.core.util.ExceptionLogStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean

class RecurringSyncLimitException(message: String) : Exception(message)
class LowMemoryAbortException(message: String) : Exception(message)

/**
 * 日历同步管理器
 * 负责协调应用与系统日历之间的双向同步流程
 */
class CalendarSyncManager(private val context: Context) {

    companion object {
        private const val TAG = "CalendarSyncManager"
        private const val SYNC_LOOK_BACK_DAYS = 30L
        private const val SYNC_LOOK_AHEAD_DAYS = 30L

        /**
         * 课程同步的未来周数
         * 只同步未来 N 周的课程，避免生成过多历史事件
         */
        private const val COURSE_SYNC_WEEKS_AHEAD = 16

        private const val RECURRING_INSTANCES_SYNC_LIMIT = 2000
        private const val MIN_FREE_MEMORY_BYTES = 64L * 1024L * 1024L
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

            val memoryError = checkMemoryOrAbort(
                action = "正向同步",
                extra = "events=${events.size}, courses=${courses.size}, timeNodes=${timeNodes.size}"
            )
            if (memoryError != null) {
                return@withContext Result.failure(memoryError)
            }

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
            ExceptionLogStore.append(
                context,
                TAG,
                "syncAllToCalendar failed: events=${events.size}, courses=${courses.size}, timeNodes=${timeNodes.size}",
                e
            )
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
     * 说明：eventType == EVENT 的事件都允许同步，tag 通过日历扩展属性保留
     */
    private suspend fun syncEvents(
        events: List<MyEvent>,
        calendarId: Long,
        syncData: SyncData
    ): SyncData {
        var updatedSyncData = syncData
        val currentMapping = updatedSyncData.mapping.toMutableMap()

        // 过滤：只同步 eventType == EventType.EVENT 的普通事件
        val eventsToSync = events.filter {
            it.eventType == EventType.EVENT && !it.skipCalendarSync && !it.isRecurring
        }

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
        allowRecurringSync: Boolean = false,
        activeEvents: List<MyEvent> = emptyList(), // 新增：活跃事件列表
        archivedEvents: List<MyEvent> = emptyList() // 新增：归档事件列表
    ): Result<Int> = withContext(Dispatchers.IO) {
        // 防止并发同步
        if (_isSyncing.get()) {
            Log.d(TAG, "正在同步中，跳过")
            return@withContext Result.success(0)
        }
            _isSyncing.set(true)
            Log.d(TAG, "开始执行反向同步")

            var mappingSize = 0
            var mappedSystemCount = 0
            val activeCount = activeEvents.size
            val archivedCount = archivedEvents.size
            var syncWindowStart = 0L
            var syncWindowEnd = 0L

            try {

                // 1. 检查权限
                if (!CalendarPermissionHelper.hasAllPermissions(context)) {
                    return@withContext Result.failure(SecurityException("缺少日历权限"))
                }

            // 2. 读取同步配置
            val syncData = syncDataSource.loadSyncData()
            Log.d(TAG, "syncData.isSyncEnabled=${syncData.isSyncEnabled}, targetCalendarId=${syncData.targetCalendarId}")
            if (!syncData.isSyncEnabled) {
                Log.d(TAG, "同步未启用，跳过")
                return@withContext Result.success(0)
            }

            val calendarId = syncData.targetCalendarId
            if (calendarId == -1L) {
                Log.d(TAG, "未配置目标日历")
                return@withContext Result.failure(Exception("未配置目标日历"))
            }

            // 3. 准备映射数据
            val mapping = syncData.mapping.toMutableMap()
            // 反向索引: System ID -> App ID
            val systemToAppMap = mapping.entries.associate { (k, v) -> v to k }
            val mappedSystemIds = mapping.values.mapNotNull { it.toLongOrNull() }.toSet()
            mappingSize = mapping.size
            mappedSystemCount = mappedSystemIds.size

            Log.d(TAG, "反向同步开始: 映射数量=${mapping.size}, 系统事件ID数量=${mappedSystemIds.size}, calendarId=$calendarId")

            var addedCount = 0
            var updatedCount = 0
            var deletedCount = 0
            var hasChanges = false
            val now = System.currentTimeMillis()
            syncWindowStart = now - SYNC_LOOK_BACK_DAYS * 24 * 60 * 60 * 1000
            syncWindowEnd = now + SYNC_LOOK_AHEAD_DAYS * 24 * 60 * 60 * 1000

            val memoryError = checkMemoryOrAbort(
                action = "反向同步",
                extra = "mapping=$mappingSize, mappedSystem=$mappedSystemCount, active=$activeCount, archived=$archivedCount"
            )
            if (memoryError != null) {
                return@withContext Result.failure(memoryError)
            }

            // ==================== 阶段一：处理已映射的事件 (更新 & 删除) ====================
            // 直接查询这些 ID，无视时间范围，确保能捕捉到修改和删除
            val existingSystemEvents = calendarManager.queryEventsByIds(mappedSystemIds, calendarId)
            val foundSystemIds = existingSystemEvents.map { it.eventId.toString() }.toSet()

            // 1.1 检测删除：在映射中但系统日历查不到的 ID
            // 修复：先检查系统日历中是否有相同内容的事件（标题+时间+地点+备注）
            // 如果有，说明是同一个事件（只是 ID 变了），不应该删除
            
            // 关键修复：如果所有映射的事件都查不到，先检查是否需要正向同步
            // 这可能发生在第一次同步时（系统日历中还没有事件）
            if (foundSystemIds.isEmpty() && mappedSystemIds.isNotEmpty()) {
                Log.d(TAG, "所有映射事件都查不到，检查是否需要正向同步...")
                // 先查询系统日历中有没有任何事件
                val rangeEventsForCheck = calendarManager.queryEventsInRange(
                    calendarId = calendarId,
                    startMillis = syncWindowStart,
                    endMillis = syncWindowEnd
                )
                val recurringEventsForCheck = if (allowRecurringSync) {
                    calendarManager.queryRecurringInstancesInRangeLimited(
                        calendarId = calendarId,
                        startMillis = syncWindowStart,
                        endMillis = syncWindowEnd,
                        limit = 1
                    ).events
                } else {
                    emptyList()
                }
                if (rangeEventsForCheck.isEmpty() && recurringEventsForCheck.isEmpty()) {
                    // 系统日历中完全没有事件，这是第一次同步，跳过反向同步
                    Log.d(TAG, "系统日历为空，跳过反向同步，等待正向同步")
                    return@withContext Result.success(0)
                }
            }
            
            val deletedSystemIds = mapping.values.toSet() - foundSystemIds
            val rangeEventsForDedup = calendarManager.queryEventsInRange(
                calendarId = calendarId,
                startMillis = syncWindowStart,
                endMillis = syncWindowEnd
            )
            
            // 构建系统事件的指纹集合（用于判断是否是同一个事件）
            val systemEventFingerprints = rangeEventsForDedup.map { event ->
                "${event.title}|${event.description}|${event.location}|${event.startMillis}|${event.endMillis}"
            }.toSet()
            
            deletedSystemIds.forEach { sysIdStr ->
                val appId = systemToAppMap[sysIdStr]
                if (appId != null) {
                    // 查找本地事件
                    val localEvent = activeEvents.find { it.id == appId } ?: archivedEvents.find { it.id == appId }
                    if (localEvent != null) {
                        // 构建本地事件的指纹
                        val localFingerprint = "${localEvent.title}|${localEvent.description}|${localEvent.location}"
                        // 检查系统日历中是否有相同内容的事件
                        if (systemEventFingerprints.any { it.startsWith(localFingerprint) }) {
                            Log.d(TAG, "检测到事件内容相同但ID变化: System ID $sysIdStr -> App ID $appId，不删除，更新映射")
                            // 更新映射：将旧的系统ID替换为新的系统ID
                            // 需要在 rangeEvents 中找到对应的系统事件
                            val newSystemEvent = rangeEventsForDedup.find { 
                                "${it.title}|${it.description}|${it.location}".startsWith(localFingerprint)
                            }
                            if (newSystemEvent != null) {
                                mapping[appId] = newSystemEvent.eventId.toString()
                                hasChanges = true
                                // 同时更新本地事件的 lastModified
                                val myEvent = CalendarEventMapper.mapSystemEventToMyEvent(newSystemEvent, fixedId = appId)
                                if (myEvent != null) {
                                    onEventUpdated(myEvent)
                                    updatedCount++
                                }
                            }
                        } else {
                            Log.d(TAG, "检测到事件删除: System ID $sysIdStr -> App ID $appId")
                            onEventDeleted(appId)
                            mapping.remove(appId)
                            hasChanges = true
                            deletedCount++
                        }
                    } else {
                        // 本地找不到对应事件，直接删除
                        Log.d(TAG, "检测到事件删除: System ID $sysIdStr -> App ID $appId (本地未找到)")
                        onEventDeleted(appId)
                        mapping.remove(appId)
                        hasChanges = true
                        deletedCount++
                    }
                }
            }

            // 1.2 检测更新：查到了，同步最新状态
            existingSystemEvents.forEach { systemEvent ->
                val appId = systemToAppMap[systemEvent.eventId.toString()]
                if (appId != null) {
                    if (systemEvent.isRecurring) {
                        Log.d(TAG, "映射事件已变为重复系列，移除旧映射并交给 Instances 同步: appId=$appId")
                        onEventDeleted(appId)
                        mapping.remove(appId)
                        hasChanges = true
                        deletedCount++
                        return@forEach
                    }

                    Log.d(TAG, "检测到系统日历事件更新: appId=$appId, title=${systemEvent.title}, startMillis=${systemEvent.startMillis}, lastModified=${systemEvent.lastModified}")
                    // 这里无论 isManaged 是什么都更新，允许用户修改由 App 创建的日程
                    val myEvent = CalendarEventMapper.mapSystemEventToMyEvent(systemEvent, fixedId = appId)
                    Log.d(TAG, "mapSystemEventToMyEvent result: $myEvent")
                    if (myEvent != null) {
                        Log.d(TAG, "准备调用 onEventUpdated: id=${myEvent.id}, title=${myEvent.title}")
                        onEventUpdated(myEvent)
                        updatedCount++
                    }
                }
            }

            // ==================== 阶段二：扫描新事件 (新增) ====================
            val rangeEvents = calendarManager.queryEventsInRange(
                calendarId = calendarId,
                startMillis = syncWindowStart,
                endMillis = syncWindowEnd
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

            // ==================== 阶段三：重复日程实例同步 ====================
            val activeRecurringEvents = activeEvents.filter { it.isRecurring }

            if (allowRecurringSync) {
                val recurringSeries = calendarManager.queryRecurringSeries(calendarId)
                val recurringInstancesResult = calendarManager.queryRecurringInstancesInRangeLimited(
                    calendarId = calendarId,
                    startMillis = syncWindowStart,
                    endMillis = syncWindowEnd,
                    limit = RECURRING_INSTANCES_SYNC_LIMIT
                )
                if (recurringInstancesResult.isTruncated) {
                    val message = "重复日程实例数量超过上限($RECURRING_INSTANCES_SYNC_LIMIT)，已停止同步"
                    Log.w(TAG, message)
                    ExceptionLogStore.append(context, TAG, message)
                    return@withContext Result.failure(RecurringSyncLimitException(message))
                }
                val recurringInstances = recurringInstancesResult.events

                val recurringParents = (activeEvents + archivedEvents)
                    .filter { it.isRecurring && it.isRecurringParent && !it.recurringSeriesKey.isNullOrBlank() }
                    .associateBy { it.id }

                val desiredRecurringEvents = buildRecurringEvents(
                    calendarId = calendarId,
                    recurringSeries = recurringSeries,
                    recurringInstances = recurringInstances,
                    existingRecurringParents = recurringParents,
                    now = now
                )

                val activeRecurringById = activeRecurringEvents.associateBy { it.id }
                val desiredRecurringById = desiredRecurringEvents.associateBy { it.id }

                desiredRecurringEvents.forEach { incomingEvent ->
                    val existingEvent = activeRecurringById[incomingEvent.id]
                    if (existingEvent == null) {
                        onEventAdded(incomingEvent)
                        addedCount++
                    } else {
                        val mergedEvent = mergeRecurringEvent(existingEvent, incomingEvent)
                        if (mergedEvent != existingEvent) {
                            onEventUpdated(mergedEvent.copy(lastModified = System.currentTimeMillis()))
                            updatedCount++
                        }
                    }
                }

                activeRecurringEvents.forEach { existingEvent ->
                    val shouldDelete = if (existingEvent.isRecurringParent) {
                        existingEvent.id !in desiredRecurringById
                    } else {
                        existingEvent.id !in desiredRecurringById && isWithinSyncWindow(existingEvent, syncWindowStart, syncWindowEnd)
                    }

                    if (shouldDelete) {
                        onEventDeleted(existingEvent.id)
                        deletedCount++
                    }
                }
            } else {
                activeRecurringEvents.forEach { existingEvent ->
                    onEventDeleted(existingEvent.id)
                    deletedCount++
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
            ExceptionLogStore.append(
                context,
                TAG,
                "syncFromCalendar failed: allowRecurringSync=$allowRecurringSync, mapping=$mappingSize, mappedSystem=$mappedSystemCount, active=$activeCount, archived=$archivedCount, window=[$syncWindowStart,$syncWindowEnd]",
                e
            )
            Result.failure(e)
        } finally {
            _isSyncing.set(false)
        }
    }

    // ==================== 辅助方法 ====================

    private fun checkMemoryOrAbort(action: String, extra: String): LowMemoryAbortException? {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val usedMemory = totalMemory - freeMemory
        val availableMemory = maxMemory - usedMemory

        if (availableMemory < MIN_FREE_MEMORY_BYTES) {
            val message = "内存不足，已取消$action"
            val detail = "available=${formatBytes(availableMemory)}, used=${formatBytes(usedMemory)}, max=${formatBytes(maxMemory)}, $extra"
            Log.w(TAG, "$message ($detail)")
            ExceptionLogStore.append(context, TAG, "$message ($detail)")
            return LowMemoryAbortException(message)
        }
        return null
    }

    private fun formatBytes(bytes: Long): String {
        val mb = bytes / (1024 * 1024)
        return "${mb}MB"
    }

    private suspend fun buildRecurringEvents(
        calendarId: Long,
        recurringSeries: List<CalendarManager.SystemEventInfo>,
        recurringInstances: List<CalendarManager.SystemEventInfo>,
        existingRecurringParents: Map<String, MyEvent>,
        now: Long
    ): List<MyEvent> {
        val desiredEvents = mutableListOf<MyEvent>()
        val recurringInstancesBySeries = recurringInstances
            .filter { it.isRecurring && !it.seriesKey.isNullOrBlank() && !it.isManaged }
            .groupBy { it.seriesKey!! }

        recurringSeries
            .filter { it.isRecurring && !it.seriesKey.isNullOrBlank() && !it.isManaged }
            .forEach { seriesEvent ->
                val seriesKey = seriesEvent.seriesKey ?: return@forEach
                val instances = recurringInstancesBySeries[seriesKey].orEmpty()
                val parentId = RecurringEventUtils.buildParentId(seriesKey)
                val existingParent = existingRecurringParents[parentId]
                val excludedKeys = existingParent?.excludedRecurringInstances.orEmpty().toSet()

                val childEvents = instances
                    .sortedBy { it.startMillis }
                    .distinctBy { it.instanceKey }
                    .filter { it.instanceKey !in excludedKeys }
                    .mapNotNull { CalendarEventMapper.mapSystemEventToMyEvent(it) }

                val nextSystemInstance = calendarManager.queryNextRecurringInstance(
                    calendarId = calendarId,
                    eventId = seriesEvent.eventId,
                    seriesKey = seriesKey,
                    fromMillis = now,
                    recurringRule = seriesEvent.recurringRule,
                    excludedInstanceKeys = excludedKeys
                )

                val currentFallbackEvent = childEvents
                    .mapNotNull { child ->
                        val startMillis = RecurringEventUtils.eventStartMillis(child) ?: return@mapNotNull null
                        val endMillis = RecurringEventUtils.eventEndMillis(child) ?: return@mapNotNull null
                        if (endMillis > now) child to startMillis else null
                    }
                    .minByOrNull { (_, startMillis) -> startMillis }
                    ?.first

                val parentSourceEvent = nextSystemInstance?.let { CalendarEventMapper.mapSystemEventToMyEvent(it) }
                    ?: currentFallbackEvent
                    ?: return@forEach

                val parentEvent = parentSourceEvent.copy(
                    id = parentId,
                    reminders = emptyList(),
                    isRecurring = true,
                    isRecurringParent = true,
                    recurringSeriesKey = seriesKey,
                    recurringInstanceKey = nextSystemInstance?.instanceKey ?: parentSourceEvent.recurringInstanceKey,
                    parentRecurringId = null,
                    excludedRecurringInstances = existingParent?.excludedRecurringInstances ?: emptyList(),
                    nextOccurrenceStartMillis = nextSystemInstance?.startMillis ?: RecurringEventUtils.eventStartMillis(parentSourceEvent),
                    skipCalendarSync = true
                )

                desiredEvents.add(parentEvent)
                desiredEvents.addAll(childEvents)
            }

        return desiredEvents
    }

    private fun mergeRecurringEvent(existingEvent: MyEvent, incomingEvent: MyEvent): MyEvent {
        return existingEvent.copy(
            title = incomingEvent.title,
            startDate = incomingEvent.startDate,
            endDate = incomingEvent.endDate,
            startTime = incomingEvent.startTime,
            endTime = incomingEvent.endTime,
            location = incomingEvent.location,
            description = incomingEvent.description,
            eventType = incomingEvent.eventType,
            tag = incomingEvent.tag,
            isRecurring = incomingEvent.isRecurring,
            isRecurringParent = incomingEvent.isRecurringParent,
            recurringSeriesKey = incomingEvent.recurringSeriesKey,
            recurringInstanceKey = incomingEvent.recurringInstanceKey,
            parentRecurringId = incomingEvent.parentRecurringId,
            nextOccurrenceStartMillis = incomingEvent.nextOccurrenceStartMillis,
            excludedRecurringInstances = if (existingEvent.isRecurringParent) {
                existingEvent.excludedRecurringInstances
            } else {
                incomingEvent.excludedRecurringInstances
            },
            skipCalendarSync = true
        )
    }

    private fun isWithinSyncWindow(event: MyEvent, syncWindowStart: Long, syncWindowEnd: Long): Boolean {
        val effectiveStart = if (event.isRecurringParent) {
            event.nextOccurrenceStartMillis ?: RecurringEventUtils.eventStartMillis(event)
        } else {
            RecurringEventUtils.eventStartMillis(event)
        }
        val effectiveEnd = if (event.isRecurringParent) {
            effectiveStart ?: RecurringEventUtils.eventEndMillis(event)
        } else {
            RecurringEventUtils.eventEndMillis(event)
        }

        if (effectiveStart == null || effectiveEnd == null) return false
        return effectiveEnd > syncWindowStart && effectiveStart < syncWindowEnd
    }

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

    /**
     * 单事件同步到系统日历
     * 只同步指定的单个事件，避免全量同步
     * 用于在 APP 外修改状态后立即同步到系统日历
     */
    suspend fun syncEventToCalendar(event: MyEvent): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (event.skipCalendarSync || event.isRecurring) {
                Log.d(TAG, "单事件同步：跳过本地只读/重复事件 event.id=${event.id}")
                return@withContext Result.success(Unit)
            }

            if (!CalendarPermissionHelper.hasAllPermissions(context)) {
                Log.w(TAG, "单事件同步：缺少日历权限")
                return@withContext Result.failure(SecurityException("缺少日历权限"))
            }

            val syncData = syncDataSource.loadSyncData()
            if (!syncData.isSyncEnabled) {
                Log.d(TAG, "单事件同步：同步未启用，跳过")
                return@withContext Result.success(Unit)
            }

            val calendarId = syncData.targetCalendarId
            if (calendarId == -1L) {
                Log.w(TAG, "单事件同步：未配置目标日历")
                return@withContext Result.success(Unit)
            }

            val calendarEventId = syncData.mapping[event.id]?.toLongOrNull()
            if (calendarEventId == null) {
                Log.w(TAG, "单事件同步：未找到映射，跳过 event.id=${event.id}")
                return@withContext Result.success(Unit)
            }

            val success = calendarManager.updateEvent(
                eventId = calendarEventId,
                event = event,
                calendarId = calendarId
            )

            if (success) {
                Log.d(TAG, "单事件同步成功: event.id=${event.id}, title=${event.title}")
                Result.success(Unit)
            } else {
                Log.e(TAG, "单事件同步失败: event.id=${event.id}")
                Result.failure(Exception("同步失败"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "单事件同步异常: event.id=${event.id}", e)
            Result.failure(e)
        }
    }
}
