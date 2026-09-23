package com.antgskds.calendarassistant

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.feature.schedule.application.model.EditDraft
import com.antgskds.calendarassistant.feature.schedule.ui.render.material.dialog.MaterialAddEventDialog
import com.antgskds.calendarassistant.shared.ui.material.component.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DetailWorkspaceRegressionTest {
    @get:Rule val compose = createComposeRule()

    private fun workspace(width: Int, attachment: Boolean) {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalDensity provides Density(0.5f)) {
                    Box(Modifier.requiredSize(width.dp, 600.dp)) {
                        AppDetailWorkspace("详情", {}, attachment = if (attachment) ({ Text("附件预览") }) else null) {
                            Text("正文")
                        }
                    }
                }
            }
        }
    }

    @Test fun noAttachmentKeepsFormCenteredAndBounded() {
        workspace(1000, false)
        val form = compose.onNodeWithTag("detail_form").fetchSemanticsNode().boundsInRoot
        assertEquals(280f, form.width, 0.1f)
        compose.onNodeWithTag("detail_attachment").assertDoesNotExist()
    }

    @Test fun wideWorkspacePlacesAttachmentBesideForm() {
        workspace(1000, true)
        val form = compose.onNodeWithTag("detail_form").fetchSemanticsNode().boundsInRoot
        val attachment = compose.onNodeWithTag("detail_attachment").fetchSemanticsNode().boundsInRoot
        assertTrue(attachment.left >= form.right)
        assertEquals(form.top, attachment.top, 0.1f)
        assertEquals(form.bottom, attachment.bottom, 0.1f)
    }

    @Test fun narrowWorkspacePlacesAttachmentBelowForm() {
        workspace(600, true)
        compose.onNodeWithTag("detail_attachment").assertDoesNotExist()
        val body = compose.onNodeWithText("正文").fetchSemanticsNode().boundsInRoot
        val image = compose.onNodeWithText("附件预览").fetchSemanticsNode().boundsInRoot
        assertTrue(image.top >= body.bottom)
    }

    @Test fun eventOpensAsDetailsAndBackProtectsAnUnsavedEdit() {
        var closed = false
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalDetailWorkspace provides true) {
                    MaterialAddEventDialog(editDraft = EditDraft(title = "原日程"),
                        onDismiss = { closed = true }, onConfirm = {})
                }
            }
        }
        compose.onNodeWithText("日程详情").assertIsDisplayed()
        compose.onNodeWithText("编辑").performClick()
        compose.onNodeWithText("标题").performTextReplacement("修改后的日程")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("放弃修改？").assertIsDisplayed()
        assertTrue(!closed)
        compose.onNodeWithText("继续编辑").performClick()
        compose.onNodeWithText("修改后的日程").assertIsDisplayed()
    }

    @Test fun demoDetailsDoNotOfferSaveOrEdit() {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalDetailWorkspace provides true, LocalDetailReadOnly provides true) {
                    MaterialAddEventDialog(editDraft = EditDraft(title = "演示日程"), onDismiss = {}, onConfirm = {})
                }
            }
        }
        compose.onNodeWithText("日程详情").assertIsDisplayed()
        compose.onNodeWithText("编辑").assertDoesNotExist()
        compose.onNodeWithText("保存").assertDoesNotExist()
    }
}
