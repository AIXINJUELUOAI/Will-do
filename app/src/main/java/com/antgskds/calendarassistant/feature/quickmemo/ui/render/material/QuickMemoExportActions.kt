package com.antgskds.calendarassistant.feature.quickmemo.ui.render.material

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import java.io.File
import kotlinx.coroutines.NonCancellable
import androidx.compose.ui.platform.LocalContext
import com.antgskds.calendarassistant.feature.quickmemo.application.*
import com.antgskds.calendarassistant.platform.quickmemo.QuickMemoExportFiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class QuickMemoExportActions(val busy: Boolean, val share: () -> Unit, val save: () -> Unit)

@Composable
internal fun rememberQuickMemoExportActions(snapshot: QuickMemoExportSnapshot): QuickMemoExportActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val files = remember(context) { QuickMemoExportFiles(context) }
    val latest by rememberUpdatedState(snapshot)
    var busy by remember { mutableStateOf(false) }
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }
    fun finishSave(uri: android.net.Uri?) {
        val path = pendingPath ?: return
        val export = QuickMemoExportFile(File(path), if (path.endsWith(".zip")) "application/zip" else "text/markdown")
        busy = true
        scope.launch {
            try {
                if (uri != null) {
                    withContext(Dispatchers.IO) { files.save(export, uri) }
                    Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { Toast.makeText(context, error.message ?: "保存失败", Toast.LENGTH_SHORT).show() }
            finally { withContext(NonCancellable + Dispatchers.IO) { files.discard(export) }; pendingPath = null; busy = false }
        }
    }
    val saveMarkdown = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown"), ::finishSave)
    val saveZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip"), ::finishSave)
    fun export(save: Boolean) {
        if (busy || pendingPath != null) return
        busy = true
        val current = latest
        scope.launch {
            var prepared: QuickMemoExportFile? = null
            try {
                val result = withContext(Dispatchers.IO) { files.prepare(current) }
                prepared = result
                if (save) {
                    pendingPath = result.file.absolutePath
                    if (result.mimeType == "application/zip") saveZip.launch(result.file.name) else saveMarkdown.launch(result.file.name)
                } else {
                    files.share(result)
                    busy = false
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                prepared?.let { withContext(Dispatchers.IO) { files.discard(it) } }
                pendingPath = null
                busy = false
                Toast.makeText(context, error.message ?: "导出失败", Toast.LENGTH_SHORT).show()
            }
        }
    }
    return QuickMemoExportActions(busy || pendingPath != null, { export(false) }, { export(true) })
}
