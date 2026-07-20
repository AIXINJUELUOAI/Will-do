package com.antgskds.calendarassistant.platform.capsule.provider

import android.app.Notification
import android.content.Context
import com.antgskds.calendarassistant.feature.capsule.domain.model.CapsuleUiState

interface ICapsuleProvider {
    fun buildNotification(
        context: Context,
        item: CapsuleUiState.Active.CapsuleItem,
        iconResId: Int
    ): Notification
}
