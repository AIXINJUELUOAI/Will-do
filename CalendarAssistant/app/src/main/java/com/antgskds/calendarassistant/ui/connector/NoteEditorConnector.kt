package com.antgskds.calendarassistant.ui.connector

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.core.ai.AnalysisResult
import com.antgskds.calendarassistant.core.ai.isTextRecognitionConfigReady
import com.antgskds.calendarassistant.core.ai.textRecognitionConfigMissingMessage
import com.antgskds.calendarassistant.core.note.NoteEntity
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.ui.contract.NoteEditorAnalysisOutcome
import com.antgskds.calendarassistant.ui.contract.NoteEditorExportFormat
import com.antgskds.calendarassistant.ui.contract.NoteEditorMessageKind
import com.antgskds.calendarassistant.ui.contract.NoteEditorUiAction
import com.antgskds.calendarassistant.ui.contract.NoteEditorUiState
import com.antgskds.calendarassistant.ui.flavor.NoteEditorScreen
import com.antgskds.calendarassistant.ui.page_display.RecognitionFeedbackSource
import com.antgskds.calendarassistant.ui.viewmodel.MainViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun NoteEditorRoute(
    noteId: Long,
    newNoteId: Long,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onOpenImportedNote: (Long) -> Unit,
    onShowMessage: (String, NoteEditorMessageKind) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val mainState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var initialNote by remember(noteId) { mutableStateOf<NoteEntity?>(null) }
    var noteLoaded by remember(noteId) { mutableStateOf(noteId == newNoteId) }

    LaunchedEffect(noteId) {
        initialNote = if (noteId == newNoteId) null else viewModel.getNoteById(noteId)
        noteLoaded = true
    }

    if (!noteLoaded) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val settings = mainState.settings
    val state = remember(initialNote, noteId, settings) {
        buildNoteEditorUiState(
            initialNote = initialNote,
            editorSessionKey = noteId.hashCode(),
            settings = settings
        )
    }

    NoteEditorScreen(
        state = state,
        modifier = modifier,
        onAction = { action ->
            when (action) {
                NoteEditorUiAction.Dismiss -> onDismiss()
                is NoteEditorUiAction.Save -> {
                    viewModel.saveNote(
                        action.noteId,
                        action.title,
                        action.document,
                        action.createdAt,
                        action.onSaved
                    )
                }

                is NoteEditorUiAction.Delete -> {
                    viewModel.deleteNote(action.noteId, action.onDeleted)
                }

                is NoteEditorUiAction.SetPinned -> {
                    viewModel.setNotePinned(action.noteId, action.pinned)
                }

                is NoteEditorUiAction.Export -> {
                    val uri = Uri.parse(action.uri)
                    when (action.format) {
                        NoteEditorExportFormat.DEFAULT -> {
                            viewModel.exportNote(action.noteId, uri, action.onResult)
                        }

                        NoteEditorExportFormat.MARKDOWN -> {
                            viewModel.exportMarkdownNote(action.noteId, uri, action.onResult)
                        }
                    }
                }

                is NoteEditorUiAction.Import -> {
                    viewModel.importNote(Uri.parse(action.uri), action.onResult)
                }

                is NoteEditorUiAction.OpenImportedNote -> onOpenImportedNote(action.noteId)
                is NoteEditorUiAction.ToggleAudioAttachment -> {
                    viewModel.toggleAudioPlayback(action.path)
                }

                is NoteEditorUiAction.ShowMessage -> onShowMessage(action.message, action.kind)
                is NoteEditorUiAction.AnalyzeText -> {
                    scope.launch {
                        val outcome = try {
                            when (withContext(Dispatchers.IO) {
                                (context.applicationContext as App)
                                    .recognitionCenter
                                    .parseUserText(
                                        text = action.text,
                                        settings = settings,
                                        context = context.applicationContext,
                                        sourceType = RecognitionFeedbackSource.NOTE_SOURCE_TYPE,
                                        sourceId = RecognitionFeedbackSource.NOTE_SOURCE_ID,
                                        ingestRequested = true
                                    )
                            }) {
                                is AnalysisResult.Success -> NoteEditorAnalysisOutcome.SUCCESS
                                is AnalysisResult.Empty -> NoteEditorAnalysisOutcome.EMPTY
                                is AnalysisResult.Failure -> NoteEditorAnalysisOutcome.FAILURE
                            }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            NoteEditorAnalysisOutcome.FAILURE
                        }
                        action.onResult(outcome)
                    }
                }
            }
        }
    )
}

internal fun buildNoteEditorUiState(
    initialNote: NoteEntity?,
    editorSessionKey: Int,
    settings: MySettings
): NoteEditorUiState {
    val recognitionReady = settings.isTextRecognitionConfigReady()
    return NoteEditorUiState(
        editorSessionKey = editorSessionKey,
        initialNoteId = initialNote?.id,
        initialTitle = initialNote?.title.orEmpty(),
        initialDocument = initialNote?.document() ?: com.antgskds.calendarassistant.core.note.NoteDocument(),
        initialCreatedAt = initialNote?.createdAt,
        initiallyPinned = initialNote?.pinnedAt != null,
        hapticEnabled = settings.hapticFeedbackEnabled,
        backgroundEnabled = settings.appBackgroundImagePath.isNotBlank(),
        backgroundBlurEnabled = settings.appBackgroundMiuiBlurTestEnabled,
        backgroundCardAlphaPercent = settings.appBackgroundCardAlphaPercent,
        predictiveBackEnabled = settings.predictiveBackEnabled,
        recognitionReady = recognitionReady,
        recognitionMissingMessage = if (recognitionReady) "" else settings.textRecognitionConfigMissingMessage()
    )
}
