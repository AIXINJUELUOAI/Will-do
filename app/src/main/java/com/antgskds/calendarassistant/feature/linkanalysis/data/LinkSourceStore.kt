package com.antgskds.calendarassistant.feature.linkanalysis.data

import com.antgskds.calendarassistant.feature.linkanalysis.domain.*
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog as Limits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

@Serializable data class InstalledLinkSource(val manifest: LinkSourceManifest, val digest: String, val enabled: Boolean = true)

class LinkSourceStore(private val directory: File) {
    private val mutex = Mutex()
    private val _sources = MutableStateFlow(readIndex())
    val sources = _sources.asStateFlow()
    private val json get() = LinkSourceProtocol.json

    private fun readIndex(): List<InstalledLinkSource> = runCatching {
        val index = File(directory, "index.json")
        if (!index.exists()) emptyList() else json.decodeFromString<List<InstalledLinkSource>>(index.readText())
            .filter { File(directory, it.digest + ".json").isFile }
    }.getOrDefault(emptyList())

    suspend fun import(input: InputStream): InstalledLinkSource = withContext(Dispatchers.IO) {
        val pack = readPackage(input)
        mutex.withLock {
            directory.mkdirs()
            val installed = InstalledLinkSource(pack.manifest, pack.digest,
                enabled = _sources.value.firstOrNull { it.manifest.id==pack.manifest.id }?.enabled ?: true)
            atomicWrite(File(directory, pack.digest + ".json"), json.encodeToString(pack))
            val next = _sources.value.filterNot { it.manifest.id == installed.manifest.id } + installed
            saveIndex(next)
            installed
        }
    }
    suspend fun setEnabled(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        mutex.withLock { saveIndex(_sources.value.map { if (it.manifest.id == id) it.copy(enabled = enabled) else it }) }
    }
    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock { saveIndex(_sources.value.filterNot { it.manifest.id == id }) }
    }
    suspend fun load(id: String, digest: String): LinkSourcePackage? = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (_sources.value.none { it.manifest.id == id && it.digest == digest && it.enabled }) null
            else runCatching { json.decodeFromString<LinkSourcePackage>(File(directory, digest + ".json").readText()) }.getOrNull()
        }
    }
    fun find(url: String) = _sources.value.filter { it.enabled && LinkAnalysisPolicy.matches(it.manifest, url) }
        .sortedBy { it.manifest.id }.firstOrNull()

    private fun saveIndex(next: List<InstalledLinkSource>) {
        atomicWrite(File(directory, "index.json"), json.encodeToString(next))
        _sources.value = next
        // Immutable snapshots are only retained while installed. Removed/replaced jobs fail their identity check.
        val kept = next.map { it.digest + ".json" }.toSet() + "index.json"
        directory.listFiles()?.filter { it.name.endsWith(".json") && it.name !in kept }?.forEach { it.delete() }
    }
    private fun atomicWrite(file: File, content: String) {
        val temporary = File(file.parentFile, file.name + ".tmp")
        java.io.FileOutputStream(temporary).use { out -> out.write(content.toByteArray(Charsets.UTF_8)); out.fd.sync() }
        java.nio.file.Files.move(temporary.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            java.nio.file.StandardCopyOption.ATOMIC_MOVE)
    }
    companion object {
        fun readPackage(input: InputStream): LinkSourcePackage {
            val files = linkedMapOf<String,String>()
            var total = 0
            var entries = 0
            val bounded = object : java.io.FilterInputStream(input) {
                var bytes = 0
                override fun read(): Int {
                    val result=super.read()
                    if(result>=0) { bytes++; require(bytes<=Limits.LINK_SOURCE_MAX_BYTES) { "源包超过大小上限" } }
                    return result
                }
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    val result=super.read(buffer,offset,length)
                    if(result>0) { bytes+=result; require(bytes<=Limits.LINK_SOURCE_MAX_BYTES) { "源包超过大小上限" } }
                    return result
                }
            }
            ZipInputStream(bounded).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(++entries <= Limits.LINK_SOURCE_MAX_FILES) { "源包文件过多" }
                    require(LinkSourceProtocol.portablePath(entry.name.trimEnd('/'))) { "源包含不安全路径" }
                    if (entry.isDirectory) continue
                    require(files.size < Limits.LINK_SOURCE_MAX_FILES && entry.name !in files) { "源包文件过多或重复" }
                    require(entry.name == "manifest.json" || entry.name.endsWith(".js")) { "源包只允许 manifest.json 和 JS" }
                    val bytes = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val size = zip.read(buffer)
                        if (size < 0) break
                        total += size
                        require(total <= Limits.LINK_SOURCE_MAX_BYTES) { "源包超过大小上限" }
                        bytes.write(buffer, 0, size)
                    }
                    val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    files[entry.name] = decoder.decode(java.nio.ByteBuffer.wrap(bytes.toByteArray())).toString()
                }
            }
            val manifest = LinkSourceProtocol.json.decodeFromString<LinkSourceManifest>(requireNotNull(files.remove("manifest.json")) { "缺少 manifest.json" })
            LinkSourceProtocol.validate(manifest)
            require(files[manifest.entry]?.isNotBlank() == true) { "缺少 JS 入口" }
            val hash = MessageDigest.getInstance("SHA-256")
            hash.update(LinkSourceProtocol.json.encodeToString(manifest).toByteArray())
            files.toSortedMap().forEach { (name, body) ->
                hash.update(name.toByteArray()); hash.update(0.toByte()); hash.update(body.toByteArray()); hash.update(0.toByte())
            }
            return LinkSourcePackage(manifest, files, hash.digest().joinToString("") { "%02x".format(it) })
        }
    }
}
