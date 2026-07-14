package com.antgskds.calendarassistant.ui.flavor

import android.net.Uri
import androidx.compose.runtime.Composable
import com.antgskds.calendarassistant.calendar.models.EventAttachment
import com.antgskds.calendarassistant.data.model.EditDraft
import com.antgskds.calendarassistant.data.model.EventPatch
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.ui.dialogs.MaterialAddEventDialog

@Composable
fun AddEventDialog(
    editDraft: EditDraft? = null,
    currentEventsCount: Int = 0,
    settings: MySettings = MySettings(),
    visible: Boolean = true,
    attachments: List<EventAttachment> = emptyList(),
    onAddAttachment: (Uri) -> Unit = {},
    onAddPendingAttachment: (Uri, String) -> Unit = { _, _ -> },
    onOpenAttachment: (EventAttachment) -> Unit = {},
    onDeleteAttachment: (EventAttachment) -> Unit = {},
    onShowMessage: (String) -> Unit = {},
    onDismiss: () -> Unit,
    onConfirm: (EventPatch) -> Unit
) {
    MaterialAddEventDialog(
        editDraft = editDraft,
        currentEventsCount = currentEventsCount,
        settings = settings,
        visible = visible,
        attachments = attachments,
        onAddAttachment = onAddAttachment,
        onAddPendingAttachment = onAddPendingAttachment,
        onOpenAttachment = onOpenAttachment,
        onDeleteAttachment = onDeleteAttachment,
        onShowMessage = onShowMessage,
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}
