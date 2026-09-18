package com.antgskds.calendarassistant.feature.accounting.domain

import com.antgskds.calendarassistant.feature.accounting.data.AccountingEntry
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.util.Base64

/** 本应用账单备份保留记录身份；恢复采用追加，不覆盖修改或复活已删除记录。 */
object AccountingBackupCodec {
    @Serializable
    private data class Backup(val format: String, val version: Int, val entries: List<AccountingEntry>,
        val images: Map<String, String> = emptyMap())

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    fun encode(entries: List<AccountingEntry>, images: Map<String, ByteArray> = emptyMap()): String {
        validate(entries)
        val paths = entries.mapNotNull { it.sourceImagePath }.distinct()
        require(paths.all { it in images }) { "账单截图缺失，未导出" }
        val imageKeys = paths.mapIndexed { index, path -> path to "image_$index" }.toMap()
        val encodedImages = paths.associate { path -> imageKeys.getValue(path) to Base64.getEncoder().encodeToString(images.getValue(path)) }
        val portableEntries = entries.map { it.copy(sourceImagePath = imageKeys[it.sourceImagePath]) }
        return json.encodeToString(Backup("willdo-accounting", 1, portableEntries, encodedImages)).also {
            require(it.toByteArray(Charsets.UTF_8).size <= ConfigCatalog.ACCOUNTING_IMPORT_MAX_BYTES) {
                "账单备份超过文件大小上限，未导出"
            }
        }
    }

    fun decode(bytes: ByteArray): List<AccountingEntry> = preview(bytes).entries

    fun preview(bytes: ByteArray): AccountingImportPreview {
        require(bytes.size <= ConfigCatalog.ACCOUNTING_IMPORT_MAX_BYTES) { "账单备份文件过大" }
        val backup = try {
            json.decodeFromString<Backup>(bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF"))
        } catch (e: Exception) {
            throw IllegalArgumentException("账单备份格式无效，请选择本应用导出的 JSON 文件", e)
        }
        require(backup.format == "willdo-accounting" && backup.version == 1) { "不支持此账单备份版本" }
        validate(backup.entries)
        require(backup.images.keys.all { it.matches(Regex("image_[0-9]+")) }) { "账单图片标识无效" }
        require(backup.entries.all { it.sourceImagePath == null || it.sourceImagePath in backup.images }) { "账单截图缺失" }
        val images = backup.images.mapValues { (_, encoded) ->
            val image = try { Base64.getDecoder().decode(encoded) }
                catch (e: IllegalArgumentException) { throw IllegalArgumentException("账单截图格式无效", e) }
            require(image.size >= 3 && image[0] == 0xff.toByte() && image[1] == 0xd8.toByte() && image[2] == 0xff.toByte()) { "账单截图不是 JPEG 图片" }
            image
        }
        return AccountingImportPreview(BillFileSource.WILLDO, backup.entries, emptyList(), 0, images)
    }

    fun validate(entries: List<AccountingEntry>) {
        require(entries.size <= ConfigCatalog.ACCOUNTING_IMPORT_MAX_ROWS) { "账单条数超过导入上限" }
        val ids = mutableSetOf<String>()
        val keys = mutableSetOf<String>()
        entries.forEach { entry ->
            require(entry.id.isNotBlank() && ids.add(entry.id)) { "账单身份为空或重复" }
            require(entry.amountMinor > 0 && entry.deletedAt == null) { "账单金额或删除状态无效" }
            require(entry.direction in setOf("EXPENSE", "INCOME", "TRANSFER")) { "账单方向无效" }
            require(entry.status in setOf("CONFIRMED", "PENDING")) { "账单状态无效" }
            require(entry.source in setOf("FILE", "MANUAL", "RECOGNITION")) { "账单来源无效" }
            require(entry.currency.matches(Regex("[A-Z]{3}"))) { "账单币种无效" }
            require(entry.currency == "CNY" || entry.status == "PENDING") { "外币账单必须保留待核对状态" }
            require(entry.transactionIdType in setOf("UNKNOWN", "PAYMENT", "MERCHANT_ORDER")) { "交易号类型无效" }
            require(entry.dedupKey == null || (entry.dedupKey.isNotBlank() && keys.add(entry.dedupKey))) { "账单去重标识无效或重复" }
            require(runCatching {
                Instant.ofEpochMilli(entry.occurredAt).atZone(ZoneId.of(entry.zoneId)).toLocalDate()
            }.isSuccess) { "账单日期或时区无效" }
        }
    }
}
