package com.antgskds.calendarassistant.feature.api.schedule

import com.antgskds.calendarassistant.feature.api.schedule.model.ScheduleCreateCommand
import com.antgskds.calendarassistant.feature.api.schedule.model.ScheduleDeleteCommand
import com.antgskds.calendarassistant.feature.api.schedule.model.ScheduleQuery
import com.antgskds.calendarassistant.feature.api.schedule.model.ScheduleResult
import com.antgskds.calendarassistant.feature.api.schedule.model.ScheduleSnapshot
import com.antgskds.calendarassistant.feature.api.schedule.model.ScheduleUpdateCommand

interface ScheduleApi {
    suspend fun create(command: ScheduleCreateCommand): ScheduleResult

    suspend fun update(command: ScheduleUpdateCommand): ScheduleResult

    suspend fun delete(command: ScheduleDeleteCommand): ScheduleResult

    suspend fun get(query: ScheduleQuery): ScheduleSnapshot?

    suspend fun list(query: ScheduleQuery = ScheduleQuery()): List<ScheduleSnapshot>
}
