package com.antgskds.calendarassistant.data.db.model

import androidx.room.Embedded
import androidx.room.Relation
import com.antgskds.calendarassistant.data.db.entity.EventInstanceEntity
import com.antgskds.calendarassistant.data.db.entity.EventMasterEntity
import com.antgskds.calendarassistant.data.db.entity.EventRuleEntity

data class MasterWithRule(
    @Embedded
    val master: EventMasterEntity,
    @Relation(
        parentColumn = "ruleId",
        entityColumn = "ruleId"
    )
    val rule: EventRuleEntity?
)

data class FullInstance(
    @Embedded
    val instance: EventInstanceEntity,
    @Relation(
        entity = EventMasterEntity::class,
        parentColumn = "masterId",
        entityColumn = "masterId"
    )
    val masterWithRule: MasterWithRule
)
