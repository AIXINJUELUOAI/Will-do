package com.antgskds.calendarassistant.shared.query

import com.antgskds.calendarassistant.feature.capsule.domain.model.CapsuleUiState
import kotlinx.coroutines.flow.StateFlow

interface CapsuleQueryApi {
    val uiState: StateFlow<CapsuleUiState>
}
