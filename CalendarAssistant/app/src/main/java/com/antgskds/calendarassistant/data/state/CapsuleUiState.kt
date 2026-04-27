package com.antgskds.calendarassistant.data.state

import com.antgskds.calendarassistant.service.capsule.CapsuleDisplayModel

sealed class CapsuleUiState {

    object None : CapsuleUiState()

    data class Active(
        val capsules: List<CapsuleItem>
    ) : CapsuleUiState() {

        data class CapsuleItem(
            val id: String,
            val notifId: Int,
            val type: Int,
            val eventType: String,
            val title: String,
            val content: String,
            val description: String,
            val color: Int,
            val state: Int = 0,
            val startMillis: Long,
            val endMillis: Long,
            val display: CapsuleDisplayModel
        )
    }
}
