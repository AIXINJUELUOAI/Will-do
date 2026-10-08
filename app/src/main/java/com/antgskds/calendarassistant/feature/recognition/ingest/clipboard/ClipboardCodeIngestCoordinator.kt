package com.antgskds.calendarassistant.feature.recognition.ingest.clipboard

import com.antgskds.calendarassistant.feature.quickmemo.application.QuickMemoFacade
import com.antgskds.calendarassistant.feature.quickmemo.domain.QuickMemoLinkParser
import android.app.ActivityManager
import android.content.ClipboardManager
import android.content.Context
import android.os.SystemClock
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.platform.clipboard.PrivilegedClipboardReader
import com.antgskds.calendarassistant.platform.clipboard.ClipboardProcessEvent
import com.antgskds.calendarassistant.platform.clipboard.ClipboardProcessException
import com.antgskds.calendarassistant.feature.notification.api.NotificationApi
import com.antgskds.calendarassistant.feature.notification.model.*
import com.antgskds.calendarassistant.feature.notification.policy.ClipboardCodePromptDeliveryPolicy
import com.antgskds.calendarassistant.shared.management.resource.notification.display.live.template.ClipboardCodePromptDisplay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import com.antgskds.calendarassistant.shared.util.AppLogger as Log
import com.antgskds.calendarassistant.feature.recognition.ingest.instantcode.InstantCodeCandidate
import com.antgskds.calendarassistant.feature.recognition.ingest.instantcode.InstantCodeParser
import com.antgskds.calendarassistant.shared.operation.IngestCommandApi
import com.antgskds.calendarassistant.shared.query.SettingsQueryApi
import com.antgskds.calendarassistant.feature.recognition.ingest.pickup.SmsPickupFingerprint
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog
import com.antgskds.calendarassistant.shared.util.PrivilegeManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

data class ClipboardCodePrompt(
    val candidate: InstantCodeCandidate,
    val fingerprint: String,
    val instanceKey: String,
    val traceId: Long
)

private data class ClipboardSnapshot(
    val text: String,
    val textHash: String,
    val instanceKey: String
)

private data class ClipboardInstance(
    val firstSeenElapsed: Long,
    var lastSeenElapsed: Long
)

class ClipboardCodeIngestCoordinator(
    private val appContext: Context,
    private val settingsQueryApi: SettingsQueryApi,
    private val ingestCommandApi: IngestCommandApi,
    private val appScope: CoroutineScope,
    private val notificationApi: NotificationApi,
    private val quickMemoFacade: QuickMemoFacade,
) {
    private val _pendingPrompt = MutableStateFlow<ClipboardCodePrompt?>(null)
    val pendingPrompt: StateFlow<ClipboardCodePrompt?> = _pendingPrompt.asStateFlow()

    private val stateMutex = Mutex()
    private val checkMutex = Mutex()
    private val privilegedReader = PrivilegedClipboardReader(appContext)
    private val started = AtomicBoolean()
    @Volatile private var backgroundListener = "none"

    fun start() {
        if (!started.compareAndSet(false, true)) return
        appScope.launch {
            combine(settingsQueryApi.settings.map { it.clipboardCodeRecognitionEnabled to it.clipboardLinkCollectionEnabled }.distinctUntilChanged(),
                PrivilegeManager.privilegeTypeFlow) { flags, privilege -> flags to privilege }
                .collectLatest { (flags, privilege) ->
                    val enabled = flags.first || flags.second
                    cancelDisabledNotifications()
                    if (!enabled || privilege == PrivilegeManager.PrivilegeType.NONE) {
                        backgroundListener = "none"
                        record("listener_stopped", details = "reason=${if (enabled) "no_privilege" else "setting_disabled"}")
                        _pendingPrompt.value?.let { cancelPromptNotification(it) }
                        if (!enabled) _pendingPrompt.value = null
                        return@collectLatest
                    }
                    try {
                        while (currentCoroutineContext().isActive && PrivilegeManager.refreshPrivilege() == privilege) {
                            record("listener_starting", details = "requested_mode=${privilege.name}")
                            try {
                                privilegedReader.watch { event ->
                                    if (!clipboardEnabled() ||
                                        PrivilegeManager.refreshPrivilege() != privilege) return@watch
                                    if (event.type == "ready") {
                                        backgroundListener = event.reader
                                        record("listener_ready", details = "reader=${event.reader} actual_uid=${event.uid}")
                                    } else {
                                        val traceId = traceSequence.incrementAndGet()
                                        recordProcessRead(event, "clipboard_changed", traceId)
                                        val text = event.text?.trim()?.takeIf(String::isNotBlank)
                                        if (text != null) checkMutex.withLock {
                                            checkClipboardForPromptInternal("clipboard_changed", traceId, text)
                                        }
                                    }
                                }
                                record("listener_ended")
                            } catch (error: CancellationException) { throw error }
                            catch (error: Exception) { record("listener_failed", details = "error_type=${error.javaClass.simpleName} reason=${(error as? ClipboardProcessException)?.reason ?: "unknown"}") }
                            backgroundListener = "none"
                            delay(ConfigCatalog.CLIPBOARD_PROCESS_RETRY_MS.toLong())
                        }
                    } finally {
                        backgroundListener = "none"
                        record("listener_stopped")
                    }
                }
        }
    }

    private fun recordProcessRead(event: ClipboardProcessEvent, source: String, traceId: Long) {
        record("read_complete", traceId, source,
            "reader=${event.reader} actual_uid=${event.uid} result=${event.result} item_count=${event.itemCount} " +
                "clipboard_time_ms=${event.timestamp} text_length=${event.text?.length ?: 0} error_type=${event.errorType ?: "none"}")
    }

    private fun promptNotificationKey(prompt: ClipboardCodePrompt) = ClipboardCodePromptDeliveryPolicy.key(prompt.traceId, sessionToken)

    private suspend fun cancelPromptNotification(prompt: ClipboardCodePrompt) {
        try { notificationApi.cancel(promptNotificationKey(prompt)) }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) { record("prompt_notification_cancel_failed", prompt.traceId, details = "error_type=${error.javaClass.simpleName}") }
    }

    private suspend fun publishPromptNotification(prompt: ClipboardCodePrompt) {
        try { publishPromptNotificationInternal(prompt) }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) { record("prompt_notification_failed", prompt.traceId, details = "error_type=${error.javaClass.simpleName}") }
    }

    private suspend fun publishPromptNotificationInternal(prompt: ClipboardCodePrompt) {
        val key = promptNotificationKey(prompt)
        val created = notificationApi.create(NotificationRequest(
            key = key, kind = NotificationKind.CLIPBOARD_CODE_PROMPT, route = NotificationRoute.AUTO,
            notificationId = key.value.hashCode(),
            channelKey = App.CHANNEL_ID_POPUP,
            display = ClipboardCodePromptDisplay.snapshot(prompt.candidate.type.displayLabel, prompt.candidate.code),
            actions = listOf(ClipboardPromptAction.create(key, "添加${prompt.candidate.type.header}")),
            metadata = ClipboardPromptAction.encode(prompt.candidate) + mapOf("instanceKey" to prompt.instanceKey, "fingerprint" to prompt.fingerprint),
            tapTarget = NotificationTapTarget(NotificationTapTargetType.APP_HOME),
            behavior = NotificationBehavior(priority = NotificationPriority.HIGH), source = "clipboard_changed",
        ))
        val result = if (created is NotificationResult.Failure) created else notificationApi.trigger(NotificationTrigger.ByKey(key))
        val success = result as? NotificationResult.Success
        val status = when {
            ClipboardCodePromptDeliveryPolicy.isDelivered(result) -> "posted"
            result is NotificationResult.Failure -> "failed"
            else -> "not_posted"
        }
        val route = notificationApi.get(key)?.route?.name ?: "unknown"
        record("prompt_notification", prompt.traceId, "clipboard_changed",
            "result=$status state=${success?.state?.name ?: "none"} route=$route reason=${(result as? NotificationResult.Failure)?.reason?.name ?: "none"}")
    }
    private val handledKeys = LinkedHashMap<String, Long>()
    private val ignoredKeys = LinkedHashMap<String, Long>()
    private val failedKeys = LinkedHashMap<String, Long>()
    private val contentInstances = LinkedHashMap<String, ClipboardInstance>()
    private val processingKeys = mutableSetOf<String>()
    private var lastSeenPromptTextHash: String? = null
    private var lastPromptedTextHash: String? = null

    private val sessionToken = java.util.UUID.randomUUID().toString()
    private val traceSequence = AtomicLong(System.currentTimeMillis())
    private var pendingLinkKey: NotificationKey? = null
    private val diagnosticRunning = AtomicBoolean()
    @Volatile private var activityVisible: Boolean? = null
    @Volatile private var windowFocused: Boolean? = null

    fun recordActivityVisibility(visible: Boolean) {
        activityVisible = visible
        record("activity_visibility", details = "visible=$visible")
    }

    fun recordWindowFocus(focused: Boolean) {
        windowFocused = focused
        record("window_focus", details = "focused=$focused")
    }

    fun recordPromptPresentation(traceId: Long, requested: Boolean, placement: String) {
        record("prompt_presentation", traceId, "ui", "requested=$requested placement=$placement")
    }

    /** A manual, single read to reproduce background restrictions; never publishes or ingests. */
    fun scheduleReadDiagnostic() {
        if (!diagnosticRunning.compareAndSet(false, true)) {
            record("diagnostic_skipped", details = "reason=already_running")
            return
        }
        val traceId = traceSequence.incrementAndGet()
        record("diagnostic_scheduled", traceId, "diagnostic_background", "delay_ms=${ConfigCatalog.CLIPBOARD_DIAGNOSTIC_DELAY_MS}")
        appScope.launch {
            try {
                delay(ConfigCatalog.CLIPBOARD_DIAGNOSTIC_DELAY_MS.toLong())
                record("diagnostic_begin", traceId, "diagnostic_background")
                val text = readClipboardText("diagnostic_background", traceId)
                val candidate = text?.let(InstantCodeParser::parseClipboard)
                record("diagnostic_complete", traceId, "diagnostic_background",
                    "readable=${text != null} matched=${candidate != null} type=${candidate?.type?.name ?: "none"} ingest_attempted=false")
            } catch (error: CancellationException) {
                record("diagnostic_cancelled", traceId, "diagnostic_background")
                throw error
            } catch (error: Exception) {
                record("diagnostic_failed", traceId, "diagnostic_background", "error_type=${error.javaClass.simpleName}")
            } finally {
                diagnosticRunning.set(false)
            }
        }
    }

    fun checkClipboardForPrompt(source: String) {
        val traceId = traceSequence.incrementAndGet()
        record("check_requested", traceId, source)
        appScope.launch {
            try {
                checkMutex.withLock { checkClipboardForPromptInternal(source, traceId) }
            } catch (error: Exception) {
                record("check_failed", traceId, source, "error_type=${error.javaClass.simpleName}")
                throw error
            }
        }
    }

    private fun record(stage: String, traceId: Long = 0L, source: String = "lifecycle", details: String = "") {
        val importance = runCatching {
            ActivityManager.RunningAppProcessInfo().also(ActivityManager::getMyMemoryState).importance
        }.getOrNull()
        val settings = settingsQueryApi.settings.value
        Log.i(TAG, "stage=$stage trace=$traceId source=$source " +
            "activity_visible=$activityVisible window_focus=$windowFocused process_importance=$importance " +
            "enabled=${settings.clipboardCodeRecognitionEnabled} link_enabled=${settings.clipboardLinkCollectionEnabled} auto_record=${settings.autoRecordLogs} " +
            "privilege_cache=${PrivilegeManager.privilegeType.name} background_listener=$backgroundListener $details")
    }

    private fun clipboardEnabled() = settingsQueryApi.settings.value.let {
        it.clipboardCodeRecognitionEnabled || it.clipboardLinkCollectionEnabled
    }

    private suspend fun cancelDisabledNotifications() {
        val settings = settingsQueryApi.settings.value
        notificationApi.list().filter {
            ClipboardCodePromptDeliveryPolicy.owns(it.key) &&
                (it.kind == NotificationKind.CLIPBOARD_CODE_PROMPT && !settings.clipboardCodeRecognitionEnabled ||
                    it.kind == NotificationKind.CLIPBOARD_LINK_PROMPT && !settings.clipboardLinkCollectionEnabled)
        }.forEach { notificationApi.cancel(it.key) }
        if (!settings.clipboardCodeRecognitionEnabled) _pendingPrompt.value = null
        if (!settings.clipboardLinkCollectionEnabled) pendingLinkKey = null
    }

    /** Serialized with clipboard checks; duplicate/stale taps cannot save a different candidate. */
    suspend fun acceptPrompt(key: NotificationKey): String? = checkMutex.withLock { acceptPromptInternal(key) }

    private suspend fun acceptPromptInternal(key: NotificationKey): String? {
        if (!ClipboardCodePromptDeliveryPolicy.owns(key)) return "提示已失效"
        val snapshot = notificationApi.get(key) ?: return "提示已处理"
        if (snapshot.state in setOf(NotificationState.CANCELLED, NotificationState.EXPIRED))
            return "提示已处理"
        val settings = settingsQueryApi.settings.value
        val message = when (snapshot.kind) {
            NotificationKind.CLIPBOARD_CODE_PROMPT -> {
                if (!settings.clipboardCodeRecognitionEnabled) return "取件类识别已关闭"
                val candidate = ClipboardPromptAction.code(snapshot.metadata) ?: return "提示已失效"
                val added = ingestCommandApi.ingestInstantCode(InstantCodeParser.toDraft(candidate), "clipboard_confirm")
                markHandled(listOfNotNull(snapshot.metadata["instanceKey"], snapshot.metadata["fingerprint"]?.let(::fingerprintKey)))
                if (added == null) "该${candidate.type.header}已添加" else "已添加${candidate.type.header}"
            }
            NotificationKind.CLIPBOARD_LINK_PROMPT -> {
                if (!settings.clipboardLinkCollectionEnabled) return "链接收藏已关闭"
                snapshot.metadata["savedMemoId"]?.toLongOrNull()?.let {
                    val result = notificationApi.trigger(NotificationTrigger.ByKey(key))
                    return if (ClipboardCodePromptDeliveryPolicy.isDelivered(result)) null else "已收藏，结果通知暂未显示"
                }
                val link = ClipboardPromptAction.link(snapshot.metadata) ?: return "提示已失效"
                val memoId = quickMemoFacade.createLinkMemo(link)
                val title = quickMemoFacade.getQuickMemo(memoId)?.title?.ifBlank { link.title } ?: link.title
                val posted = publishSavedLinkNotification(snapshot, memoId, title)
                if (pendingLinkKey == key) pendingLinkKey = null
                record("ingest_complete", details = "result=saved kind=${snapshot.kind.name}")
                return if (posted) null else "已收藏，结果通知暂未显示"
            }
            else -> return "提示已失效"
        }
        notificationApi.cancel(key)
        _pendingPrompt.value?.takeIf { promptNotificationKey(it) == key }?.let { _pendingPrompt.value = null }
        if (pendingLinkKey == key) pendingLinkKey = null
        record("ingest_complete", details = "result=saved kind=${snapshot.kind.name}")
        return message
    }

    fun confirmPendingPrompt() {
        val prompt = _pendingPrompt.value ?: return
        appScope.launch {
            try {
                val message = checkMutex.withLock {
                    if (_pendingPrompt.value?.traceId != prompt.traceId) return@withLock "提示已处理"
                    val key = promptNotificationKey(prompt)
                    if (notificationApi.get(key)?.state.let { it == null || it == NotificationState.CANCELLED }) {
                        notificationApi.create(NotificationRequest(
                            key = key, kind = NotificationKind.CLIPBOARD_CODE_PROMPT,
                            display = ClipboardCodePromptDisplay.snapshot(prompt.candidate.type.displayLabel, prompt.candidate.code),
                            metadata = ClipboardPromptAction.encode(prompt.candidate) +
                                mapOf("instanceKey" to prompt.instanceKey, "fingerprint" to prompt.fingerprint),
                        ))
                    }
                    acceptPromptInternal(key)
                }
                withContext(Dispatchers.Main) {
                    message?.let { com.antgskds.calendarassistant.shared.ui.material.component.UniversalToastUtil.showInfo(appContext, it) }
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                record("ingest_exception", prompt.traceId, "clipboard_confirm", "error_type=${error.javaClass.simpleName}")
            }
        }
    }

    private suspend fun publishSavedLinkNotification(snapshot: NotificationSnapshot, memoId: Long, title: String): Boolean {
        return try {
            val created = notificationApi.update(NotificationRequest(
                key = snapshot.key, kind = snapshot.kind, notificationId = snapshot.notificationId,
                route = NotificationRoute.AUTO, channelKey = snapshot.channelKey,
                display = ClipboardCodePromptDisplay.savedLinkSnapshot(title),
                tapTarget = NotificationTapTarget(NotificationTapTargetType.QUICK_MEMO_DETAIL,
                    mapOf("quickMemoId" to memoId.toString())),
                actions = listOf(NotificationAction.viewQuickMemo(memoId)),
                metadata = mapOf("savedMemoId" to memoId.toString()),
                behavior = snapshot.behavior.copy(onlyAlertOnce = true), source = "clipboard_link_saved",
            ))
            val result = if (created is NotificationResult.Failure) created else notificationApi.trigger(NotificationTrigger.ByKey(snapshot.key))
            recordLinkNotification(result, snapshot.key, "saved_link_notification")
            ClipboardCodePromptDeliveryPolicy.isDelivered(result)
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            record("saved_link_notification_failed", details = "error_type=${error.javaClass.simpleName}")
            false
        }
    }

    suspend fun dismissLinkResults(memoId: Long) = checkMutex.withLock {
        if (memoId <= 0L) return@withLock
        val keys = notificationApi.list().filter {
            it.kind == NotificationKind.CLIPBOARD_LINK_PROMPT && ClipboardCodePromptDeliveryPolicy.owns(it.key) &&
                it.metadata["savedMemoId"] == memoId.toString()
        }.map { it.key }
        notificationApi.cancelAll(keys)
    }

    private suspend fun recordLinkNotification(result: NotificationResult, key: NotificationKey, stage: String, traceId: Long = 0L) {
        val success = result as? NotificationResult.Success
        val failure = result as? NotificationResult.Failure
        val route = notificationApi.get(key)?.route?.name ?: "unknown"
        record(stage, traceId, details = "posted=${ClipboardCodePromptDeliveryPolicy.isDelivered(result)} " +
            "state=${success?.state?.name ?: "none"} route=$route reason=${failure?.reason?.name ?: "none"}")
    }

    private suspend fun publishLinkNotification(text: String, traceId: Long): Boolean {
        if (!settingsQueryApi.settings.value.clipboardLinkCollectionEnabled) return false
        val link = QuickMemoLinkParser.parse(text) ?: return false
        if (quickMemoFacade.isSavedLinkCurrent(link)) {
            record("check_skipped", traceId, details = "reason=link_already_saved")
            return true
        }
        val key = ClipboardCodePromptDeliveryPolicy.key(traceId, sessionToken)
        pendingLinkKey?.let { notificationApi.cancel(it) }
        val created = notificationApi.create(NotificationRequest(
            key = key, kind = NotificationKind.CLIPBOARD_LINK_PROMPT, route = NotificationRoute.AUTO,
            notificationId = key.value.hashCode(), channelKey = App.CHANNEL_ID_POPUP,
            display = ClipboardCodePromptDisplay.linkSnapshot(link.source.takeUnless { it == "链接" }.orEmpty()),
            tapTarget = NotificationTapTarget(NotificationTapTargetType.APP_HOME),
            actions = listOf(ClipboardPromptAction.create(key, "收藏")),
            metadata = ClipboardPromptAction.encode(link),
            behavior = NotificationBehavior(priority = NotificationPriority.HIGH), source = "clipboard_link",
        ))
        val result = if (created is NotificationResult.Failure) created else notificationApi.trigger(NotificationTrigger.ByKey(key))
        val posted = ClipboardCodePromptDeliveryPolicy.isDelivered(result)
        if (posted) pendingLinkKey = key
        recordLinkNotification(result, key, "link_notification", traceId)
        return posted
    }

    fun dismissPendingPrompt() {
        val prompt = _pendingPrompt.value ?: return
        _pendingPrompt.value = null
        record("prompt_dismissed", prompt.traceId)
        appScope.launch {
            cancelPromptNotification(prompt)
            stateMutex.withLock {
                val now = SystemClock.elapsedRealtime()
                ignoredKeys[prompt.instanceKey] = now
                cleanupLocked(now)
            }
        }
    }

    private suspend fun checkClipboardForPromptInternal(source: String, traceId: Long, providedText: String? = null) {
        record("check_begin", traceId, source)
        if (!clipboardEnabled()) {
            record("check_skipped", traceId, source, "reason=setting_disabled")
            return
        }
        val snapshot = (providedText?.let { createSnapshot(it, source, traceId) }
            ?: readClipboardSnapshot(source, traceId)) ?: return
        if (!clipboardEnabled()) {
            record("check_skipped", traceId, source, "reason=setting_disabled_after_read")
            return
        }
        if (shouldSkipRepeatedPrompt(snapshot, source, traceId)) return
        val candidate = if (settingsQueryApi.settings.value.clipboardCodeRecognitionEnabled)
            InstantCodeParser.parseClipboard(snapshot.text) else null
        if (candidate == null) {
            val linkHandled = publishLinkNotification(snapshot.text, traceId)
            if (linkHandled) markPrompted(snapshot.textHash)
            record("match_complete", traceId, source, "code_matched=false link_handled=$linkHandled")
            return
        }
        record("match_complete", traceId, source, "matched=true type=${candidate.type.name}")
        val draft = InstantCodeParser.toDraft(candidate)
        val fingerprint = SmsPickupFingerprint.fromDraft(draft) ?: fingerprintOf(candidate.code)
        val processingKeys = listOf(snapshot.instanceKey, fingerprintKey(fingerprint))
        val shouldProcess = beginProcessing(processingKeys, source, traceId) ?: return
        if (!shouldProcess) return
        try {
            val previous = _pendingPrompt.value
            previous?.let { cancelPromptNotification(it) }
            markPrompted(snapshot.textHash)
            val prompt = ClipboardCodePrompt(candidate, fingerprint, snapshot.instanceKey, traceId)
            _pendingPrompt.value = prompt
            record("prompt_pending", traceId, source, "delivery=in_app awaits_confirmation=true replacing_pending=${previous != null}")
            if (source == "clipboard_changed" && windowFocused != true) publishPromptNotification(prompt)
        } finally {
            endProcessing(processingKeys)
        }
    }

    private suspend fun readClipboardSnapshot(source: String, traceId: Long): ClipboardSnapshot? {
        val text = readClipboardText(source, traceId) ?: return null
        return createSnapshot(text, source, traceId)
    }

    private suspend fun createSnapshot(text: String, source: String, traceId: Long): ClipboardSnapshot? {
        val hash = hashText(text)
        val now = SystemClock.elapsedRealtime()
        val instanceKey = stateMutex.withLock {
            cleanupLocked(now)
            val existing = contentInstances[hash]
            val instance = if (existing != null && now - existing.lastSeenElapsed <= INSTANCE_REUSE_MS) {
                existing.lastSeenElapsed = now
                existing
            } else {
                ClipboardInstance(firstSeenElapsed = now, lastSeenElapsed = now).also {
                    contentInstances[hash] = it
                }
            }
            "$hash:${instance.firstSeenElapsed}"
        }
        record("snapshot_created", traceId, source)
        return ClipboardSnapshot(text = text, textHash = hash, instanceKey = instanceKey)
    }

    private suspend fun shouldSkipRepeatedPrompt(snapshot: ClipboardSnapshot, source: String, traceId: Long): Boolean =
        stateMutex.withLock {
            if (snapshot.textHash != lastSeenPromptTextHash) {
                lastSeenPromptTextHash = snapshot.textHash
                lastPromptedTextHash = null
                return@withLock false
            }
            if (snapshot.textHash == lastPromptedTextHash) {
                record("check_skipped", traceId, source, "reason=unchanged_already_prompted")
                true
            } else {
                false
            }
        }

    private suspend fun markPrompted(textHash: String) {
        stateMutex.withLock {
            lastPromptedTextHash = textHash
        }
    }

    private suspend fun readClipboardText(source: String, traceId: Long): String? {
        val privilege = PrivilegeManager.refreshPrivilege()
        if (privilege != PrivilegeManager.PrivilegeType.NONE) {
            record("read_begin", traceId, source, "requested_mode=${privilege.name}")
            return try {
                val event = privilegedReader.read()
                recordProcessRead(event, source, traceId)
                event.text?.trim()?.takeIf(String::isNotBlank)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                record("read_exception", traceId, source, "requested_mode=${privilege.name} error_type=${error.javaClass.simpleName} reason=${(error as? ClipboardProcessException)?.reason ?: "unknown"}")
                null
            }
        }
        return readAppClipboardText(source, traceId)
    }

    private suspend fun readAppClipboardText(source: String, traceId: Long): String? = withContext(Dispatchers.Main) {
        val started = SystemClock.elapsedRealtime()
        record("read_begin", traceId, source, "reader=app_clipboard")
        runCatching {
            val clipboard = appContext.getSystemService(ClipboardManager::class.java)
            if (clipboard == null) {
                record("read_complete", traceId, source, "result=service_unavailable")
                return@runCatching null
            }
            val clip = clipboard.primaryClip
            if (clip == null) {
                // Android can return null for both an empty clipboard and a denied read.
                record("read_complete", traceId, source, "result=null_clip cause=empty_or_restricted")
                return@runCatching null
            }
            if (clip.itemCount <= 0) {
                record("read_complete", traceId, source, "result=no_items")
                return@runCatching null
            }
            val text = clip.getItemAt(0).coerceToText(appContext)?.toString()?.trim()?.takeIf { it.isNotBlank() }
            record("read_complete", traceId, source,
                "result=${if (text == null) "blank_text" else "text"} item_count=${clip.itemCount} clipboard_time_ms=${clip.description.timestamp} text_length=${text?.length ?: 0} elapsed_ms=${SystemClock.elapsedRealtime() - started}")
            text
        }.onFailure {
            record("read_exception", traceId, source, "error_type=${it.javaClass.simpleName} elapsed_ms=${SystemClock.elapsedRealtime() - started}")
        }.getOrNull()
    }

    private suspend fun beginProcessing(keys: List<String>, source: String, traceId: Long): Boolean? = stateMutex.withLock {
        beginProcessingLocked(keys, source, traceId)
    }

    private fun beginProcessingLocked(keys: List<String>, source: String, traceId: Long): Boolean? {
        val now = SystemClock.elapsedRealtime()
        cleanupLocked(now)
        return when {
            keys.any { processingKeys.contains(it) } -> {
                record("check_skipped", traceId, source, "reason=already_processing")
                false
            }
            keys.any { handledKeys.containsKey(it) } -> {
                record("check_skipped", traceId, source, "reason=already_handled")
                null
            }
            keys.any { ignoredKeys.containsKey(it) } -> {
                record("check_skipped", traceId, source, "reason=ignored")
                null
            }
            keys.any { failedKeys.containsKey(it) } -> {
                record("check_skipped", traceId, source, "reason=failed_cooldown")
                null
            }
            else -> {
                processingKeys.addAll(keys)
                true
            }
        }
    }

    private suspend fun endProcessing(keys: List<String>) {
        stateMutex.withLock { processingKeys.removeAll(keys.toSet()) }
    }

    private suspend fun markHandled(keys: List<String>) {
        stateMutex.withLock {
            val now = SystemClock.elapsedRealtime()
            keys.forEach { key ->
                handledKeys[key] = now
                failedKeys.remove(key)
            }
            cleanupLocked(now)
        }
    }

    private suspend fun markFailed(key: String) {
        stateMutex.withLock {
            val now = SystemClock.elapsedRealtime()
            failedKeys[key] = now
            cleanupLocked(now)
        }
    }

    private fun cleanupLocked(now: Long) {
        cleanupMap(handledKeys, now, ENTRY_TTL_MS)
        cleanupMap(ignoredKeys, now, ENTRY_TTL_MS)
        cleanupMap(failedKeys, now, FAILED_TTL_MS)
        val instanceIterator = contentInstances.entries.iterator()
        while (instanceIterator.hasNext()) {
            if (now - instanceIterator.next().value.lastSeenElapsed > INSTANCE_TTL_MS) instanceIterator.remove()
        }
        while (contentInstances.size > MAX_ENTRIES) {
            val eldest = contentInstances.entries.iterator()
            if (!eldest.hasNext()) return
            eldest.next()
            eldest.remove()
        }
    }

    private fun cleanupMap(map: LinkedHashMap<String, Long>, now: Long, ttl: Long) {
        val iterator = map.entries.iterator()
        while (iterator.hasNext()) {
            if (now - iterator.next().value > ttl) iterator.remove()
        }
        while (map.size > MAX_ENTRIES) {
            val eldest = map.entries.iterator()
            if (!eldest.hasNext()) return
            eldest.next()
            eldest.remove()
        }
    }

    private fun hashText(text: String): String = fingerprintOf(text.trim())

    private fun fingerprintKey(value: String): String = "fingerprint:$value"

    private fun fingerprintOf(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        private const val TAG = "ClipboardIngest"
        private const val ENTRY_TTL_MS = 10 * 60 * 1000L
        private const val FAILED_TTL_MS = 15 * 1000L
        private const val INSTANCE_REUSE_MS = 5 * 1000L
        private const val INSTANCE_TTL_MS = 10 * 60 * 1000L
        private const val MAX_ENTRIES = 128
    }
}
