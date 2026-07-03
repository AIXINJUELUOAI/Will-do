package com.antgskds.calendarassistant.core.operation

import com.antgskds.calendarassistant.calendar.models.stubs.CalendarManager
import com.antgskds.calendarassistant.calendar.models.stubs.CalendarSyncManager

/**
 * 同步链路的统一入口契约。
 *
 * 「同类能力统一边界和统一入口」——所有同步操作（启停、立即同步、改间隔、选日历）和同步状态查询
 * 都应只依赖本接口，而不是直接拿 [com.antgskds.calendarassistant.core.center.SyncCenter] 实现类。
 * 同步是外部副作用：允许失败/重试，失败不回滚本地入库。
 *
 * 由 SyncCenter 实现。签名与其现有实现一致（纯增量契约，不改行为）。
 */
interface SyncApi {

    suspend fun enableCalendarSync(): Result<Unit>

    suspend fun disableCalendarSync(): Result<Unit>

    suspend fun manualSync(): Result<Unit>

    suspend fun setSyncedCalendarIds(ids: String): Result<Unit>

    suspend fun enableCalendarSyncAndSyncNow(): Result<Unit>

    suspend fun updateSyncIntervalSeconds(seconds: Int): Result<Unit>

    suspend fun updateSourceCalendars(calendarIds: List<Long>): Result<Unit>

    /** 查询当前同步状态（是否启用、目标日历、权限等）。 */
    fun getSyncStatus(): CalendarSyncManager.SyncStatus

    /** 列出可选作为同步源的系统日历。 */
    fun getSelectableSyncCalendars(): List<CalendarManager.CalendarInfo>
}
