package com.antgskds.calendarassistant.feature.schedule.ui.render

import androidx.compose.runtime.Composable
import com.antgskds.calendarassistant.feature.schedule.domain.model.RepeatSpec
import com.antgskds.calendarassistant.feature.schedule.ui.render.material.dialog.MaterialRepeatRulePickerDialog
import java.time.LocalDate

@Composable
fun RepeatRulePickerDialog(
    currentSpec: RepeatSpec?,
    startDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (RepeatSpec?) -> Unit
) {
    MaterialRepeatRulePickerDialog(
        currentSpec = currentSpec,
        startDate = startDate,
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}
