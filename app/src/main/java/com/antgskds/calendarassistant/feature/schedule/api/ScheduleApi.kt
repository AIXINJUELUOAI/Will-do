package com.antgskds.calendarassistant.feature.schedule.api

import com.antgskds.calendarassistant.feature.schedule.api.model.ScheduleCreateCommand
import com.antgskds.calendarassistant.feature.schedule.api.model.ScheduleDeleteCommand
import com.antgskds.calendarassistant.feature.schedule.api.model.ScheduleQuery
import com.antgskds.calendarassistant.feature.schedule.api.model.ScheduleResult
import com.antgskds.calendarassistant.feature.schedule.api.model.ScheduleSnapshot
import com.antgskds.calendarassistant.feature.schedule.api.model.ScheduleUpdateCommand

interface ScheduleApi {
    suspend fun create(command: ScheduleCreateCommand): ScheduleResult

    suspend fun update(command: ScheduleUpdateCommand): ScheduleResult

    suspend fun delete(command: ScheduleDeleteCommand): ScheduleResult

    suspend fun get(query: ScheduleQuery): ScheduleSnapshot?

    suspend fun list(query: ScheduleQuery = ScheduleQuery()): List<ScheduleSnapshot>
}
