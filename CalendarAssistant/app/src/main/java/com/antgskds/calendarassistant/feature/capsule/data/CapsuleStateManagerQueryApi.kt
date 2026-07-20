package com.antgskds.calendarassistant.feature.capsule.data

import com.antgskds.calendarassistant.feature.capsule.application.CapsuleStateManager
import com.antgskds.calendarassistant.shared.query.CapsuleQueryApi
import com.antgskds.calendarassistant.feature.capsule.domain.model.CapsuleUiState
import kotlinx.coroutines.flow.StateFlow

class CapsuleStateManagerQueryApi(
    private val capsuleStateManager: CapsuleStateManager
) : CapsuleQueryApi {
    override val uiState: StateFlow<CapsuleUiState>
        get() = capsuleStateManager.uiState
}
