package com.antgskds.calendarassistant.feature.imagepin

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog
import com.antgskds.calendarassistant.shared.operation.CapsuleCommandApi
import com.antgskds.calendarassistant.shared.query.SettingsQueryApi
import com.antgskds.calendarassistant.shared.util.ImageImportUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.InputStream

/** 图片独立于日程/随口记存储；整批复制并验证成功后追加，失败不影响原图。 */
class ImagePinController(private val context: Context, private val settings: SettingsQueryApi,
    private val capsules: CapsuleCommandApi, private val scope: CoroutineScope) {
    private val mutex = Mutex()
    private val prefs = context.getSharedPreferences("image_pin", Context.MODE_PRIVATE)
    private val directory get() = File(context.filesDir, "image_pin").apply { mkdirs() }
    private fun file(id: Long) = File(directory, "pin_" + id + ".image")
    private fun imageIds() = restoreImagePinIds(prefs.getString("ids", null), prefs.getLong("id", 0))
        .filter { file(it).isFile }

    fun start() {
        scope.launch {
            settings.settings.map { ImagePinPolicy.enabled(it) to ImagePinPolicy.canPublish(it) }
                .distinctUntilChanged().collect { (enabled, publish) ->
                    runCatching {
                        val component = ComponentName(context, "com.antgskds.calendarassistant.platform.entry.image.ImagePinShareActivity")
                        val state = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                        if (context.packageManager.getComponentEnabledSetting(component) != state)
                            context.packageManager.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
                        if (!enabled) {
                            androidx.core.content.pm.ShortcutManagerCompat.removeDynamicShortcuts(context, listOf("image_pin"))
                            androidx.core.content.pm.ShortcutManagerCompat.disableShortcuts(context, listOf("image_pin"), "请先在实验室开启图片挂起")
                            clear()
                        } else {
                            context.getSystemService(android.content.pm.ShortcutManager::class.java)
                                .enableShortcuts(listOf("image_pin"))
                            mutex.withLock {
                                val id = prefs.getLong("id", 0)
                                val ids = imageIds()
                                val names = ids.map { file(it).name }.toSet()
                                directory.listFiles().orEmpty().filter { it.name !in names }.forEach { it.delete() }
                                if (publish && id > 0 && ids.isNotEmpty()) capsules.showImagePin(id, ids.size) else capsules.clearImagePin()
                            }
                        }
                    }.onFailure { com.antgskds.calendarassistant.shared.util.AppLogger.w("ImagePin", "state update failed: " + it.javaClass.simpleName) }
                }
        }
    }

    suspend fun pin(uris: List<Uri>): Long {
        val incoming = uris.distinct()
        require(incoming.isNotEmpty()) { "未找到图片" }
        require(incoming.all { it.scheme == "content" }) { "不支持此图片来源" }
        return append(incoming.size) { index ->
            checkNotNull(context.contentResolver.openInputStream(incoming[index])) { "图片无法读取" }
        }
    }

    /** 接管已打开的分享流，任务不依赖中转 Activity 的生命周期。 */
    fun pinOpenedImages(streams: List<InputStream>, onResult: (Result<Long>) -> Unit): Job =
        launchImagePinImport(scope, streams) {
            val result = try {
                require(streams.isNotEmpty()) { "未找到图片" }
                Result.success(append(streams.size) { streams[it] })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
            withContext(Dispatchers.Main) { onResult(result) }
        }

    /** 截图与外部分享共用追加、容量限制和回滚；临时 PNG 无论成功与否都清理。 */
    suspend fun pinScreenshot(bitmap: Bitmap): Long = withContext(Dispatchers.IO) {
        val temporary = File.createTempFile("image_pin_capture_", ".png", context.cacheDir)
        try {
            temporary.outputStream().use { output ->
                // PNG 为无损格式，quality 参数按 Android API 要求传入，但不参与压缩质量控制。
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "截图保存失败" }
            }
            ensureActive()
            ImagePinPolicy.captureBlockReason(context, settings.settings.value)?.let { error(it) }
            append(1) { temporary.inputStream() }
        } finally { temporary.delete() }
    }

    private suspend fun append(count: Int, open: (Int) -> InputStream): Long = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(ImagePinPolicy.canPublish(settings.settings.value)) { "请先在实验室开启图片挂起，并开启实况通知" }
            val old = prefs.getLong("id", 0)
            val previousIds = imageIds()
            check(previousIds.size + count <= ConfigCatalog.IMAGE_PIN_MAX_COUNT) {
                "最多挂起 ${ConfigCatalog.IMAGE_PIN_MAX_COUNT} 张图片，请先结束当前挂起"
            }
            val firstId = maxOf(System.currentTimeMillis(), old + 1)
            val addedIds = (0 until count).map { firstId + it }
            val id = addedIds.last()
            var totalBytes = previousIds.sumOf { file(it).length() }
            var committed = false
            try {
                repeat(count) { index ->
                    val target = file(addedIds[index])
                    open(index).use { input ->
                        target.outputStream().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var size = 0L
                            while (true) {
                                ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                size += count
                                totalBytes += count
                                check(size <= ConfigCatalog.IMAGE_PIN_MAX_BYTES) { "图片过大，请先裁剪后分享" }
                                check(totalBytes <= ConfigCatalog.IMAGE_PIN_MAX_TOTAL_BYTES) { "挂起图片总大小超限，请先结束当前挂起" }
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    val bitmap = ImageImportUtils.decodeSampledBitmapFromFile(target)
                    checkNotNull(bitmap) { "第 ${index + 1} 张图片无法打开，本次未追加" }
                    bitmap.recycle()
                }
                ensureActive()
                check(ImagePinPolicy.canPublish(settings.settings.value)) { "图片挂起已关闭" }
                val ids = previousIds + addedIds
                val previousEncoded = prefs.getString("ids", null)
                if (!prefs.edit().putLong("id", id).putString("ids", ids.joinToString(",")).commit()) {
                    // commit 失败也可能已更新内存，恢复旧索引后再清理本批文件。
                    prefs.edit().putLong("id", old).putString("ids", previousEncoded).commit()
                    error("图片保存失败")
                }
                committed = true
                capsules.showImagePin(id, ids.size)
                id
            } finally { if (!committed) addedIds.forEach { file(it).delete() } }
        }
    }

    suspend fun currentImages(id: Long): List<File> = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (id > 0 && ImagePinPolicy.enabled(settings.settings.value) && prefs.getLong("id", 0) == id)
                imageIds().map { file(it) } else emptyList()
        }
    }

    /** 只移除当前批次中的指定副本；保留批次 id，旧弹窗不能修改后来追加的图片。 */
    suspend fun removeImage(expectedId: Long, imagePath: String): List<File>? = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!ImagePinPolicy.enabled(settings.settings.value)) return@withLock null
            val id = prefs.getLong("id", 0)
            val previousEncoded = prefs.getString("ids", null)
            val remaining = removeImagePinCopy(id, expectedId, imageIds().associateWith(::file), imagePath) { ids ->
                val editor = prefs.edit()
                if (ids.isEmpty()) editor.remove("id").remove("ids")
                else editor.putString("ids", ids.joinToString(","))
                val saved = editor.commit()
                if (!saved) prefs.edit().putLong("id", id).putString("ids", previousEncoded).commit()
                saved
            } ?: return@withLock null
            if (remaining.isEmpty() || !ImagePinPolicy.canPublish(settings.settings.value)) capsules.clearImagePin()
            else capsules.showImagePin(id, remaining.size)
            remaining.map(::file)
        }
    }

    suspend fun clear(expectedId: Long? = null) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val id = prefs.getLong("id", 0)
            if (expectedId != null && expectedId != id) return@withLock
            check(prefs.edit().remove("id").remove("ids").commit()) { "清理图片失败" }
            capsules.clearImagePin()
            directory.listFiles().orEmpty().forEach { it.delete() }
        }
    }
}

/** 先进入 finally 再调度后台任务；成功、异常或取消均关闭整批流，包括尚未读取的图片。 */
internal fun launchImagePinImport(
    scope: CoroutineScope, streams: List<InputStream>, importImages: suspend () -> Unit,
): Job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
    try {
        ensureActive()
        // UNDISPATCHED 先接管资源；yield 再把复制切到应用 scope 的 IO 调度线程。
        yield()
        importImages()
    } finally {
        streams.forEach { runCatching { it.close() } }
    }
}

/** 先持久化索引，再删除副本；拒绝旧批次和列表外路径，失败时保留全部文件。 */
internal fun removeImagePinCopy(
    currentId: Long, expectedId: Long, images: Map<Long, File>, imagePath: String,
    persist: (List<Long>) -> Boolean,
): List<Long>? {
    if (expectedId <= 0 || currentId != expectedId) return null
    val target = images.entries.firstOrNull { it.value.absolutePath == imagePath } ?: return null
    val remaining = images.keys.filter { it != target.key }
    check(persist(remaining)) { "取消图片挂起失败" }
    // 索引已提交；删除失败的孤立副本由已有启动清理回收。
    target.value.delete()
    return remaining
}

/** 无列表索引的旧版本以 id 作为唯一图片；新索引保留追加顺序。 */
internal fun restoreImagePinIds(encoded: String?, legacyId: Long): List<Long> =
    if (encoded == null) listOf(legacyId).filter { it > 0 }
    else encoded.split(',').mapNotNull { it.toLongOrNull()?.takeIf { id -> id > 0 } }.distinct()
