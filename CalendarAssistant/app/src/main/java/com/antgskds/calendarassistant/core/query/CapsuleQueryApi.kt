package com.antgskds.calendarassistant.core.query

import com.antgskds.calendarassistant.data.state.CapsuleUiState
import kotlinx.coroutines.flow.StateFlow

interface CapsuleQueryApi {
    val uiState: StateFlow<CapsuleUiState>
}
