package com.antgskds.calendarassistant.service.capsule.provider

import android.app.Notification
import android.content.Context
import com.antgskds.calendarassistant.data.state.CapsuleUiState

interface ICapsuleProvider {
    fun buildNotification(
        context: Context,
        item: CapsuleUiState.Active.CapsuleItem,
        iconResId: Int
    ): Notification
}
