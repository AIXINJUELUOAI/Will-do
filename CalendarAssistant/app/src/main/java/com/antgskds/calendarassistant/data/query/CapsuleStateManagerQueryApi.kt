package com.antgskds.calendarassistant.data.query

import com.antgskds.calendarassistant.core.capsule.CapsuleStateManager
import com.antgskds.calendarassistant.core.query.CapsuleQueryApi
import com.antgskds.calendarassistant.data.state.CapsuleUiState
import kotlinx.coroutines.flow.StateFlow

class CapsuleStateManagerQueryApi(
    private val capsuleStateManager: CapsuleStateManager
) : CapsuleQueryApi {
    override val uiState: StateFlow<CapsuleUiState>
        get() = capsuleStateManager.uiState
}
