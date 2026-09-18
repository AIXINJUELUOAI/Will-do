package com.antgskds.calendarassistant.feature.accounting.ui

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.antgskds.calendarassistant.feature.accounting.data.AccountingEntry
import com.antgskds.calendarassistant.feature.accounting.data.AccountingDraft
import com.antgskds.calendarassistant.feature.accounting.domain.*
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog
import com.antgskds.calendarassistant.shared.operation.IngestCommandApi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.IOException
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId

data class AccountingEntriesState(
    val entries: List<AccountingEntry> = emptyList(), val loading: Boolean = true, val error: String? = null,
)

data class AccountingImportState(
    val open: Boolean = false,
    val source: BillFileSource = BillFileSource.WECHAT,
    val reading: Boolean = false,
    val saving: Boolean = false,
    val preview: AccountingImportPreview? = null,
    val result: AccountingImportResult? = null,
    val error: String? = null,
) { val busy get() = reading || saving }

data class AccountingEditorState(
    val open: Boolean = false,
    val entry: AccountingEntry? = null,
    val initialDate: LocalDate = LocalDate.now(),
    val saving: Boolean = false,
    val error: String? = null,
    val draft: AccountingDraft? = null,
)

data class AccountingRecognitionState(
    val drafts: List<AccountingDraft> = emptyList(), val open: Boolean = false,
    val busy: Boolean = false, val message: String? = null,
)

data class AccountingDeleteState(
    val entry: AccountingEntry? = null, val saving: Boolean = false, val error: String? = null,
)

/** 文件解析在后台进行；确认后仅通过统一入库契约提交。 */
class AccountingViewModel(
    private val api: AccountingApi,
    private val ingest: IngestCommandApi,
    private val resolver: ContentResolver,
    private val filesDir: java.io.File,
) : ViewModel() {
    private val _entries = MutableStateFlow(AccountingEntriesState())
    val entries = _entries.asStateFlow()
    private val _import = MutableStateFlow(AccountingImportState())
    val importState = _import.asStateFlow()
    private val _editor = MutableStateFlow(AccountingEditorState())
    val editorState = _editor.asStateFlow()
    private val _recognition = MutableStateFlow(AccountingRecognitionState())
    val recognitionState = _recognition.asStateFlow()
    private val _delete = MutableStateFlow(AccountingDeleteState())
    val deleteState = _delete.asStateFlow()
    private val _savedDate = MutableStateFlow<LocalDate?>(null)
    val savedDate = _savedDate.asStateFlow()
    private var readJob: Job? = null
    private var observeJob: Job? = null
    private val _exporting = MutableStateFlow(false)
    val exporting = _exporting.asStateFlow()
    private val _exportMessage = MutableStateFlow<String?>(null)
    val exportMessage = _exportMessage.asStateFlow()

    fun consumeExportMessage() { _exportMessage.value = null }

    fun exportFile(uri: Uri) {
        if (_exporting.value) return
        _exporting.value = true
        viewModelScope.launch {
            try {
                val count = withContext(Dispatchers.IO) {
                    val snapshot = api.entries.first()
                    val imageDirectory = java.io.File(filesDir, "accounting_images").canonicalFile
                    var remaining = ConfigCatalog.ACCOUNTING_IMPORT_MAX_BYTES
                    val images = snapshot.mapNotNull { it.sourceImagePath }.distinct().associateWith { path ->
                        val file = java.io.File(path).canonicalFile
                        require(file.parentFile == imageDirectory && file.isFile) { "账单截图缺失，暂未导出，请检查原图" }
                        require(file.length() <= remaining) { "含截图的账单备份超过文件大小上限" }
                        file.inputStream().use { it.readNBytes(remaining + 1) }.also {
                            require(it.size <= remaining) { "账单截图过大" }
                            remaining -= it.size
                        }
                    }
                    val bytes = AccountingBackupCodec.encode(snapshot, images).toByteArray(Charsets.UTF_8)
                    resolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                        ?: throw IOException("无法创建文件")
                    snapshot.size
                }
                _exportMessage.value = "已导出 $count 条账单"
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                _exportMessage.value = if (e is IllegalArgumentException) e.message else "账单导出失败，请重试"
            } finally { _exporting.value = false }
        }
    }

    init {
        reload()
        viewModelScope.launch {
            api.drafts.catch { _recognition.update { s -> s.copy(message = "待确认账单读取失败，请重新打开应用") } }
                .collect { drafts ->
                    _recognition.update { it.copy(drafts = drafts) }
                }
        }
    }

    fun openRecognition() { _recognition.update { it.copy(open = true) } }
    fun closeRecognition() { if (!_recognition.value.busy) _recognition.update { it.copy(open = false) } }
    fun editDraft(draft: AccountingDraft) {
        if (_editor.value.saving || _recognition.value.busy) return
        _editor.value = AccountingEditorState(open = true, draft = draft)
    }
    fun dismissDraft(id: String) {
        if (_recognition.value.busy || _editor.value.saving) return
        _recognition.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { ingest.dismissAccountingDraft(id) } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { _recognition.update { it.copy(message = "移除失败，请重试") } }
            finally { _recognition.update { it.copy(busy = false) } }
        }
    }

    fun countAnyway(draft: AccountingDraft) {
        if (_recognition.value.busy || _editor.value.saving) return
        val current = _recognition.value.drafts.firstOrNull { it.id == draft.id } ?: return
        val input = AccountingRecognitionMapper.possibleDuplicateInput(current)
        if (input == null) {
            _recognition.update { it.copy(message = "账单信息不完整，请先核对并入账") }
            return
        }
        _recognition.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { ingest.confirmAccountingDraft(current.id, input) }
                _recognition.update { it.copy(message = if (result.duplicate) "该交易已有记录，未重复入账" else "账单已计入") }
                result.entry?.let { _savedDate.value = Instant.ofEpochMilli(it.occurredAt).atZone(ZoneId.of(it.zoneId)).toLocalDate() }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                _recognition.update { it.copy(message = if (e is IllegalArgumentException) e.message else "保存失败，请重试") }
            } finally { _recognition.update { it.copy(busy = false) } }
        }
    }

    fun reload() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            _entries.update { it.copy(loading = true, error = null) }
            api.entries.catch {
                _entries.update { state -> state.copy(loading = false, error = "账单读取失败，请重试") }
            }.collect { _entries.value = AccountingEntriesState(entries = it, loading = false) }
        }
    }

    fun openEditor(date: LocalDate, id: String? = null) {
        if (_editor.value.saving) return
        val entry = id?.let { target -> _entries.value.entries.firstOrNull { it.id == target } }
        if (id != null && entry == null) return
        _editor.value = AccountingEditorState(open = true, entry = entry, initialDate = date)
    }

    fun closeEditor() { if (!_editor.value.saving) _editor.value = AccountingEditorState() }
    fun consumeSavedDate() { _savedDate.value = null }

    fun saveEntry(input: AccountingEntryInput) {
        val state = _editor.value
        if (!state.open || state.saving || input.id != state.entry?.id) return
        _editor.value = state.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                val draft = state.draft
                val saved = if (draft != null) {
                    val result = withContext(Dispatchers.IO) { ingest.confirmAccountingDraft(draft.id, input) }
                    _recognition.update { it.copy(message = if (result.duplicate) "该交易已有记录，未重复入账" else "账单已保存") }
                    result.entry
                } else withContext(Dispatchers.IO) { ingest.saveAccountingEntry(input) }
                saved?.let { _savedDate.value = Instant.ofEpochMilli(it.occurredAt).atZone(ZoneId.of(it.zoneId)).toLocalDate() }
                _editor.value = AccountingEditorState()
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                _editor.value = state.copy(error = if (e is IllegalArgumentException) e.message else "保存失败，请重试")
            }
        }
    }

    fun requestDelete(id: String) {
        if (_delete.value.saving) return
        _delete.value = AccountingDeleteState(entry = _entries.value.entries.firstOrNull { it.id == id })
    }
    fun closeDelete() { if (!_delete.value.saving) _delete.value = AccountingDeleteState() }
    fun confirmDelete() {
        val state = _delete.value
        val entry = state.entry ?: return
        if (state.saving) return
        _delete.value = state.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { ingest.deleteAccountingEntry(entry.id) }
                _delete.value = AccountingDeleteState()
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) {
                _delete.value = state.copy(error = "删除失败，请重试")
            }
        }
    }

    fun openImport() { if (!_import.value.open) _import.value = AccountingImportState(open = true) }
    fun closeImport() {
        if (_import.value.saving) return
        readJob?.cancel()
        _import.value = AccountingImportState()
    }
    fun selectSource(source: BillFileSource) {
        if (_import.value.busy) return
        _import.value = AccountingImportState(open = true, source = source)
    }

    fun readFile(uri: Uri, source: BillFileSource) {
        if (_import.value.saving) return
        readJob?.cancel()
        _import.value = AccountingImportState(open = true, source = source, reading = true)
        readJob = viewModelScope.launch {
            try {
                val preview = withContext(Dispatchers.IO) {
                    val parser = AccountingFileParser(ConfigCatalog.ACCOUNTING_IMPORT_MAX_BYTES,
                        ConfigCatalog.ACCOUNTING_IMPORT_EXPANDED_BYTES, ConfigCatalog.ACCOUNTING_IMPORT_MAX_ROWS,
                        ConfigCatalog.ACCOUNTING_IMPORT_MAX_ZIP_ENTRIES)
                    resolver.openInputStream(uri)?.use { parser.read(it, source) }
                        ?: throw IOException("无法打开文件")
                }
                _import.value = AccountingImportState(open = true, source = source, preview = preview)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                val message = when (e) {
                    is IllegalArgumentException -> e.message ?: "账单格式无法识别"
                    is SecurityException -> "无法读取此文件，请重新选择"
                    is IOException -> "文件读取失败，请下载到本机后重新选择"
                    else -> "账单解析失败，请检查文件是否完整或重新导出"
                }
                _import.value = AccountingImportState(open = true, source = source, error = message)
            }
        }
    }

    fun confirmImport() {
        val state = _import.value
        val preview = state.preview ?: return
        if (state.busy || state.result != null || preview.entries.isEmpty()) return
        _import.value = state.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    if (preview.source == BillFileSource.WILLDO) {
                        val directory = java.io.File(filesDir, "accounting_images").apply { mkdirs() }
                        val created = mutableListOf<java.io.File>()
                        try {
                            val restoredPaths = preview.images.mapValues { (_, bytes) ->
                                java.io.File(directory, "${java.util.UUID.randomUUID()}.jpg").also {
                                    created += it
                                    it.writeBytes(bytes)
                                }.absolutePath
                            }
                            ingest.restoreAccountingEntries(preview.entries.map {
                                it.copy(sourceImagePath = restoredPaths[it.sourceImagePath])
                            })
                        } finally {
                            // 重复导入不遗留第二份截图；取消发生在事务提交之后也保留已被引用的文件。
                            withContext(NonCancellable) {
                                runCatching {
                                    val used = api.entries.first().mapNotNull { it.sourceImagePath }.toSet()
                                    created.filter { it.absolutePath !in used }.forEach { it.delete() }
                                }
                            }
                        }
                    }
                    else ingest.ingestAccountingEntries(preview.entries)
                }
                _import.value = state.copy(result = result)
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) {
                _import.value = state.copy(error = "导入未完成，本次写入已回滚，请重试")
            }
        }
    }

    class Factory(private val api: AccountingApi, private val ingest: IngestCommandApi,
        private val resolver: ContentResolver, private val filesDir: java.io.File) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AccountingViewModel::class.java))
            return AccountingViewModel(api, ingest, resolver, filesDir) as T
        }
    }
}
