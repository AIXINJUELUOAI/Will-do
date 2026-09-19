package com.antgskds.calendarassistant.platform.receiver

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.antgskds.calendarassistant.feature.settings.data.SettingsDataSource
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings

object AccountingMessageAccessPolicy {
    fun hasSmsPermission(context: Context): Boolean =
        listOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS).all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    fun permissionsGranted(context: Context): Boolean =
        SmsNotificationListenerService.isEnabled(context) && hasSmsPermission(context)

    fun enabled(context: Context, settings: MySettings = SettingsDataSource(context).loadSettings()): Boolean =
        allows(settings.automaticAccountingEnabled, settings.accountingMessagesEnabled,
            SmsNotificationListenerService.isEnabled(context), hasSmsPermission(context))

    fun allows(automatic: Boolean, messages: Boolean, notification: Boolean, sms: Boolean): Boolean =
        automatic && messages && notification && sms
}
