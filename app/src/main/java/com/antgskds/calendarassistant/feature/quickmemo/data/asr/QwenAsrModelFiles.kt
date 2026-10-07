package com.antgskds.calendarassistant.feature.quickmemo.data.asr

import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog
import com.google.gson.JsonParser
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/** File-only validation can also be exercised without Android or native inference. */
internal object QwenAsrModelFiles {
    const val DIRECTORY = "quick_memos/asr/qwen3-asr-0.6B-int8"
    val required = listOf(
        "conv_frontend.onnx", "encoder.int8.onnx", "decoder.int8.onnx",
        "tokenizer/merges.txt", "tokenizer/vocab.json", "tokenizer/tokenizer_config.json",
    )

    fun targetName(name: String): String? {
        val normalized = name.replace('\\', '/')
        val parts = normalized.split('/')
        require(!normalized.startsWith('/') && ':' !in normalized && parts.none { it == ".." }) {
            "模型文件路径不合法"
        }
        return required.firstOrNull { it.substringAfterLast('/') == parts.last() }
    }

    fun missing(directory: File): List<String> = required.filter { name ->
        val file = File(directory, name)
        !file.isFile || file.length() < if (name.endsWith(".onnx")) ConfigCatalog.ASR_MODEL_MIN_BYTES else 128L
    }

    /** Validate the whole replacement before touching the dedicated legacy model directory. */
    fun cleanupLegacyModel(filesDirectory: File): Boolean {
        val current = File(filesDirectory, DIRECTORY)
        if (missing(current).isNotEmpty()) return false
        required.forEach { validate(it, File(current, it)) }
        val legacy = File(filesDirectory, QuickMemoAsrModelStore.MODEL_DIR)
        val root = filesDirectory.canonicalFile.toPath()
        require(legacy.canonicalFile.toPath().startsWith(root) && legacy.canonicalFile != filesDirectory.canonicalFile) {
            "旧模型目录不在应用存储内"
        }
        return !legacy.exists() || legacy.deleteRecursively()
    }

    fun version(directory: File): String = required.joinToString("|") { name ->
        val file = File(directory, name)
        "$name:${file.length()}:${file.lastModified()}"
    }

    fun validate(name: String, file: File) {
        if (name.endsWith(".onnx")) {
            require(file.length() >= ConfigCatalog.ASR_MODEL_MIN_BYTES) { "模型文件不完整：$name" }
        } else {
            require(file.length() in 128..ConfigCatalog.ASR_TOKENIZER_MAX_BYTES) { "分词器文件异常：$name" }
            when (name) {
                "tokenizer/merges.txt" -> require(file.bufferedReader().use { it.readLine() }.orEmpty().startsWith("#version:")) {
                    "请导入 Qwen 配套的 merges.txt"
                }
                "tokenizer/vocab.json" -> {
                    val json = file.reader().use { JsonParser.parseReader(it) }.asJsonObject
                    require(json.size() > 1000 && json.entrySet().all { it.value.isJsonPrimitive && it.value.asJsonPrimitive.isNumber }) {
                        "vocab.json 不是有效的 Qwen 词表"
                    }
                }
                "tokenizer/tokenizer_config.json" -> {
                    val json = file.reader().use { JsonParser.parseReader(it) }.asJsonObject
                    require(json.has("added_tokens_decoder")) { "tokenizer_config.json 缺少特殊 token 配置" }
                }
            }
        }
    }

    fun copyLimited(input: InputStream, target: File, limit: Long = ConfigCatalog.QWEN_ASR_IMPORT_MAX_BYTES): Long {
        target.parentFile?.mkdirs()
        var total = 0L
        target.outputStream().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= limit) { "模型导入文件过大" }
                output.write(buffer, 0, count)
            }
        }
        return total
    }

    /** Only our six-file bundle is accepted; duplicate/path-traversal entries cannot overwrite files. */
    fun extractBundle(input: InputStream, directory: File) {
        val imported = mutableSetOf<String>()
        var total = 0L
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = targetName(entry.name) // Validate directory names too.
                if (!entry.isDirectory) {
                    require(name != null) { "模型包包含不支持的文件：${entry.name}" }
                    require(imported.add(name)) { "模型包包含重复文件：$name" }
                    val target = File(directory, name)
                    total += copyLimited(zip, target, ConfigCatalog.QWEN_ASR_IMPORT_MAX_BYTES - total)
                    validate(name, target)
                }
                zip.closeEntry()
            }
        }
        require(imported.containsAll(required)) { "模型包不完整，缺少：${(required - imported).joinToString()}" }
    }
}
