package com.antgskds.calendarassistant.platform.linkanalysis
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.antgskds.calendarassistant.feature.linkanalysis.domain.*
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog as Limits
import com.antgskds.calendarassistant.shared.util.AppLogger as Log
import kotlinx.coroutines.*
import java.io.File
import kotlin.math.max

data class LinkPreparedMaterial(val file: File, val mimeType: String, val order: Int, val label: String)
data class LinkPreparedContent(val text: String, val images: List<LinkPreparedMaterial>, val audio: List<LinkPreparedMaterial>, val warnings: List<String>, val transcript: String = "")
class LinkMaterialProcessor(private val context: Context) {
    suspend fun prepare(pack: LinkSourcePackage, result: LinkExtractionResult, directory: File, localAudio: Boolean, traceId: String = ""): LinkPreparedContent = withContext(Dispatchers.IO) {
        val client = LinkHttpClient(pack.manifest,traceId)
        val warnings = result.warnings.toMutableList()
        if (result.status == "partial" || result.body.kind in setOf("description","excerpt"))
            warnings += "源提供的正文不完整，摘要仅依据已取得素材"
        val images = mutableListOf<LinkPreparedMaterial>(); val audio = mutableListOf<LinkPreparedMaterial>()
        val transcripts = mutableListOf<String>()
        val text = StringBuilder(result.body.text); var downloaded = 0L
        suspend fun download(media: LinkResultMedia): File {
            val file = File(directory, "media-" + media.order); var done = false
            var lastFailure: Exception? = null
            for (url in media.urls) {
                try { client.download(url,media.headers,file); done=true; break }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) {
                    Log.w("LinkAnalysis","trace=$traceId stage=MEDIA_ATTEMPT_FAIL detail=" + LinkAnalysisFailure.safeDetail(failure))
                    lastFailure = failure
                }
            }
            if (!done) throw LinkAnalysisFailure.describe(lastFailure ?: LinkAnalysisFailure(LinkFailureCode.MEDIA_DOWNLOAD), "PREPARING")
            downloaded += file.length(); require(downloaded <= Limits.LINK_MEDIA_TOTAL_BYTES) { "素材总大小超过上限" }
            val prefix = file.inputStream().use { it.readNBytes(64) }.toString(Charsets.ISO_8859_1).trimStart().lowercase()
            require(!prefix.startsWith("<!doctype") && !prefix.startsWith("<html") && !prefix.startsWith("{")) { "素材地址返回了网页或错误信息" }
            return file
        }
        val ordered = result.media.sortedBy { it.order }
        suspend fun tryImage(media: LinkResultMedia, label: String) {
            try { images += image(download(media), media.order, label) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                Log.w("LinkAnalysis","trace=$traceId stage=MEDIA_SKIPPED detail=" + LinkAnalysisFailure.safeDetail(failure))
                warnings += "部分图片素材不可用，已跳过"
            }
        }
        for (media in ordered.filter { it.type == "image" && it.role == "content_image" }) {
            tryImage(media, if (media.group.isBlank()) "图片" else "素材组 " + media.group)
        }
        val groups = ordered.filter { it.type in setOf("audio","video") && it.role != "background_music" }.groupBy { it.group }
        if (result.contentType == "video") require(groups.isNotEmpty()) { "源未提供视频音轨，请更新源后重试" }
        for ((group, media) in groups) {
            var selected = LinkAnalysisPolicy.preferredAudio(media) ?: error("源没有声明完整语音音轨")
            var file = download(selected)
            var audioDirectory = File(directory,"audio-"+selected.order).also { it.mkdirs() }
            var chunks = LinkAudioChunks.decode(file,audioDirectory)
            val video=media.firstOrNull { it.type=="video" && it.role=="video" }
            val actualMs=chunks.sumOf { (it.length()-44).coerceAtLeast(0) } / 32
            if(selected.type=="audio" && !LinkAnalysisPolicy.coversVideo(actualMs,video?.durationMs)) {
                val fallback=video ?: error("音轨显著短于视频，没有完整音轨可用")
                chunks.forEach { it.delete() }
                selected=fallback; file=download(fallback)
                audioDirectory=File(directory,"audio-"+fallback.order).also { it.mkdirs() }
                chunks=LinkAudioChunks.decode(file,audioDirectory)
            }
            for ((index, chunk) in chunks.withIndex()) {
                currentCoroutineContext().ensureActive()
                val label = "素材组 $group，音频片段 " + (index+1)
                if (localAudio) {
                    val transcript = LinkAudioTranscriptionClient(context).transcribe(chunk)
                    require(transcript.isNotBlank()) { "未识别到语音内容，可修改音频处理设置后重试" }
                    transcripts += transcript.trim()
                    text.append("\n\n").append(label).append("：\n").append(transcript)
                    require(text.length <= Limits.LINK_TEXT_MAX_CHARS) { "转写内容超过处理上限" }
                    chunk.delete()
                } else {
                    require(chunk.length() <= Limits.LINK_AI_INPUT_BYTES) { "音频分段超过 AI 输入上限" }
                    audio += LinkPreparedMaterial(chunk,"audio/wav",selected.order,label)
                }
            }
            if (result.contentType in setOf("video","mixed")) warnings += "当前摘要基于视频音轨和文案，未包含画面中的全部信息"
        }
        if (images.isEmpty()) ordered.firstOrNull { it.type == "image" && it.role == "cover" }?.let { cover ->
            tryImage(cover,"封面（不能代表完整视频画面）")
        }
        require(text.isNotBlank() || images.isNotEmpty() || audio.isNotEmpty()) { "源没有返回可用于摘要的内容" }
        LinkPreparedContent(text.toString(),images,audio,warnings.distinct(), transcripts.joinToString("\n\n"))
    }
    private fun image(file: File, order: Int, label: String): LinkPreparedMaterial {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(file.absolutePath,bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "素材不是可解码图片" }
        var sample = 1
        while (max(bounds.outWidth,bounds.outHeight)/sample > Limits.LINK_AI_IMAGE_SIDE*2) sample *= 2
        val bitmap = BitmapFactory.decodeFile(file.absolutePath,BitmapFactory.Options().apply { inSampleSize=sample }) ?: error("图片解码失败")
        val side = max(bitmap.width,bitmap.height)
        val scaled = if (side > Limits.LINK_AI_IMAGE_SIDE) Bitmap.createScaledBitmap(bitmap,
            (bitmap.width.toLong()*Limits.LINK_AI_IMAGE_SIDE/side).toInt().coerceAtLeast(1),
            (bitmap.height.toLong()*Limits.LINK_AI_IMAGE_SIDE/side).toInt().coerceAtLeast(1),true) else bitmap
        val target = File(file.parentFile,file.name+".jpg")
        try { target.outputStream().use { require(scaled.compress(Bitmap.CompressFormat.JPEG,Limits.LINK_AI_JPEG_QUALITY,it)) } }
        finally { if (scaled !== bitmap) scaled.recycle(); bitmap.recycle() }
        require(target.length() <= Limits.LINK_AI_INPUT_BYTES)
        return LinkPreparedMaterial(target,"image/jpeg",order,label)
    }
}
