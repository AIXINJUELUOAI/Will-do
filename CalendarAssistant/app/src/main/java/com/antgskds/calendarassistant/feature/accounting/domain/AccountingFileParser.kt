package com.antgskds.calendarassistant.feature.accounting.domain

import com.antgskds.calendarassistant.feature.accounting.data.AccountingEntry
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.UUID
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.SAXException

/** 官方账单文件解析，不调用 AI；文件内容仅在本机处理，错误行带行号供预览。 */
class AccountingFileParser(
    private val maxBytes: Int,
    private val maxExpandedBytes: Int,
    private val maxRows: Int,
    private val maxZipEntries: Int,
) {
    fun read(input: InputStream, source: BillFileSource): AccountingImportPreview {
        val bytes = input.readLimited(maxBytes)
        require(bytes.isNotEmpty()) { "账单文件为空" }
        if (source == BillFileSource.WILLDO) return AccountingBackupCodec.preview(bytes)
        if (bytes.take(2) == listOf(0x50.toByte(), 0x4b.toByte())) return readXlsx(bytes, source)
        require(!(bytes.size >= 2 && bytes[0] == 0xd0.toByte() && bytes[1] == 0xcf.toByte())) {
            "暂不支持旧版 XLS 或加密表格，请选择 CSV 或未加密 XLSX"
        }
        val text = decode(bytes)
        val headerLine = text.lineSequence().firstOrNull { it.contains("交易") && it.contains("金额") }.orEmpty()
        val delimiter = if (headerLine.count { it == '\t' } > headerLine.count { it == ',' }) '\t' else ','
        val table = csvRows(text, delimiter)
        return parseRows(table.rows, source, "CSV", false, table.rowNumbers)
            ?: throw IllegalArgumentException("未找到${source.label}账单表头，请选择官方导出的完整 CSV 或 XLSX 文件")
    }

    private fun decode(bytes: ByteArray): String {
        if (bytes.size >= 2 && bytes[0] == 0xff.toByte() && bytes[1] == 0xfe.toByte()) return bytes.toString(Charsets.UTF_16LE).removePrefix("\uFEFF")
        if (bytes.size >= 2 && bytes[0] == 0xfe.toByte() && bytes[1] == 0xff.toByte()) return bytes.toString(Charsets.UTF_16BE).removePrefix("\uFEFF")
        fun strict(charset: Charset) = charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
        return runCatching { strict(Charsets.UTF_8) }.getOrElse {
            runCatching { strict(Charset.forName("GB18030")) }.getOrElse { throw IllegalArgumentException("账单编码无法识别，请重新导出 UTF-8 或 GBK 文件") }
        }
    }

    private fun InputStream.readLimited(limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            require(count <= limit - output.size()) { "账单文件或解压内容过大" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private data class CsvTable(val rows: List<List<String>>, val rowNumbers: List<Int>)

    private fun csvRows(text: String, delimiter: Char): CsvTable {
        val rows = mutableListOf<List<String>>()
        val rowNumbers = mutableListOf<Int>()
        var physicalLine = 1
        var rowStart = 1
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var index = 0
        fun endRow() {
            row.add(field.toString()); field.clear(); rows.add(row); rowNumbers.add(rowStart); row = mutableListOf()
            require(rows.size <= maxRows) { "账单行数超过导入上限" }
        }
        while (index < text.length) {
            val c = text[index]
            when {
                c == '"' && quoted && text.getOrNull(index + 1) == '"' -> { field.append('"'); index++ }
                c == '"' && quoted -> quoted = false
                c == '"' && field.isBlank() -> { field.clear(); quoted = true }
                c == delimiter && !quoted -> { row.add(field.toString()); field.clear() }
                (c == '\n' || c == '\r') && !quoted -> {
                    endRow()
                    if (c == '\r' && text.getOrNull(index + 1) == '\n') index++
                    physicalLine++
                    rowStart = physicalLine
                }
                else -> {
                    field.append(c)
                    if (c == '\n' || (c == '\r' && text.getOrNull(index + 1) != '\n')) physicalLine++
                }
            }
            index++
        }
        require(!quoted) { "CSV 引号未闭合，文件可能不完整" }
        if (field.isNotEmpty() || row.isNotEmpty()) endRow()
        return CsvTable(rows, rowNumbers)
    }

    private fun readXlsx(bytes: ByteArray, source: BillFileSource): AccountingImportPreview {
        val documents = mutableMapOf<String, ByteArray>()
        var remaining = maxExpandedBytes
        var count = 0
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(++count <= maxZipEntries) { "压缩文件条目过多" }
                val data = zip.readLimited(remaining)
                remaining -= data.size
                if (entry.name == "xl/sharedStrings.xml" || entry.name == "xl/workbook.xml" ||
                    entry.name.matches(Regex("xl/worksheets/[^/]+\\.xml"))) documents[entry.name] = data
            }
        }
        require("xl/workbook.xml" in documents) { "请选择未加密 XLSX；若下载的是 ZIP 压缩包，请先解压后选择其中的账单文件" }
        val workbook = xml(documents.getValue("xl/workbook.xml"))
        val props = workbook.getElementsByTagNameNS("*", "workbookPr").item(0) as? Element
        val date1904 = props?.getAttribute("date1904") in setOf("1", "true")
        val shared = documents["xl/sharedStrings.xml"]?.let {
            val nodes = xml(it).getElementsByTagNameNS("*", "si")
            (0 until nodes.length).map { index -> richText(nodes.item(index) as Element) }
        }.orEmpty()
        val results = mutableListOf<AccountingImportPreview>()
        var totalRows = 0
        documents.filterKeys { it.startsWith("xl/worksheets/") }.toSortedMap().forEach { (name, data) ->
            val nodes = xml(data).getElementsByTagNameNS("*", "row")
            totalRows += nodes.length
            require(totalRows <= maxRows) { "账单行数超过导入上限" }
            val rows = (0 until nodes.length).map { index ->
                val row = mutableListOf<String>()
                val cells = (nodes.item(index) as Element).getElementsByTagNameNS("*", "c")
                for (i in 0 until cells.length) {
                    val cell = cells.item(i) as Element
                    val letters = cell.getAttribute("r").takeWhile { it in 'A'..'Z' }
                    val column = if (letters.isEmpty()) row.size else letters.fold(0) { acc, char -> acc * 26 + char.code - 'A'.code + 1 } - 1
                    require(column in 0..16383) { "XLSX 单元格列号无效" }
                    while (row.size <= column) row.add("")
                    val raw = cell.getElementsByTagNameNS("*", "v").item(0)?.textContent.orEmpty()
                    row[column] = when (cell.getAttribute("t")) {
                        "s" -> shared.getOrNull(raw.toIntOrNull() ?: -1) ?: throw IllegalArgumentException("XLSX 字符串索引无效")
                        "inlineStr" -> richText(cell)
                        else -> raw
                    }
                }
                row
            }
            parseRows(rows, source, name.substringAfterLast('/'), date1904,
                (0 until nodes.length).map { index -> (nodes.item(index) as Element).getAttribute("r").toIntOrNull() ?: (index + 1) })?.let(results::add)
        }
        require(results.isNotEmpty()) { "未找到${source.label}账单表头，请检查导出来源和文件内容" }
        return AccountingImportPreview(source, results.flatMap { it.entries }, results.flatMap { it.issues }, results.sumOf { it.ignored })
    }

    private fun xml(bytes: ByteArray): org.w3c.dom.Document {
        val text = bytes.toString(Charsets.UTF_8)
        require(!text.contains('\u0000') && !text.contains("<!DOCTYPE", true) && !text.contains("<!ENTITY", true)) { "不支持含外部实体或特殊编码的表格 XML" }
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true; isExpandEntityReferences = false }
        val builder = factory.newDocumentBuilder()
        builder.setEntityResolver { _, _ -> throw SAXException("禁止读取外部实体") }
        return builder.parse(bytes.inputStream())
    }

    private fun richText(element: Element): String {
        val texts = element.getElementsByTagNameNS("*", "t")
        return (0 until texts.length).joinToString("") { texts.item(it).textContent }
    }

    private fun normal(value: String) = value.trim().removePrefix("\uFEFF").replace("（", "(").replace("）", ")").replace(Regex("\\s"), "")
    private fun clean(value: String) = value.trim().removePrefix("'").trim()

    private fun parseRows(rows: List<List<String>>, source: BillFileSource, sheet: String, date1904: Boolean, rowNumbers: List<Int>? = null): AccountingImportPreview? {
        val amountNames = setOf("金额", "金额(元)", "交易金额", "交易金额(元)")
        val timeNames = setOf("交易时间", "交易创建时间", "创建时间", "付款时间")
        val directionNames = setOf("收/支", "收支", "资金流向")
        val headerIndex = rows.indexOfFirst { row ->
            val h = row.map(::normal)
            h.any { it in amountNames } && h.any { it in timeNames } && h.any { it in directionNames }
        }
        if (headerIndex < 0) return null
        val header = rows[headerIndex].map(::normal)
        if (source == BillFileSource.WECHAT && ("交易分类" in header || "收/付款方式" in header)) {
            throw IllegalArgumentException("文件看起来是支付宝账单，请切换导入来源")
        }
        if (source == BillFileSource.ALIPAY && "当前状态" in header && "交易单号" in header) {
            throw IllegalArgumentException("文件看起来是微信账单，请切换导入来源")
        }
        val entries = mutableListOf<AccountingEntry>()
        val issues = mutableListOf<AccountingImportIssue>()
        var ignored = 0
        val now = System.currentTimeMillis()
        rows.drop(headerIndex + 1).forEachIndexed { offset, row ->
            if (row.all(String::isBlank) || row.map(::normal) == header) return@forEachIndexed
            fun value(vararg names: String): String = names.firstNotNullOfOrNull { name ->
                header.indexOf(name).takeIf { it >= 0 }?.let { row.getOrNull(it)?.let(::clean)?.takeIf(String::isNotBlank) }
            }.orEmpty()
            val rawTime = value("交易时间", "交易创建时间", "创建时间", "付款时间")
            val rawAmount = value("金额", "金额(元)", "交易金额", "交易金额(元)")
            val directionText = value("收/支", "收支", "资金流向")
            // 尾部汇总/说明不属于交易行，不计为格式错误。
            if (rawTime.isBlank() && rawAmount.isBlank() && directionText.isBlank()) return@forEachIndexed
            if (row.firstOrNull()?.trim()?.let { it.startsWith("---") || it.startsWith("共") || it.startsWith("总计") || it.startsWith("说明") } == true) return@forEachIndexed
            val rowNumber = rowNumbers?.getOrNull(headerIndex + offset + 1) ?: (headerIndex + offset + 2)
            try {
                val state = value("当前状态", "交易状态", "状态")
                if (listOf("关闭", "失败", "待付款", "等待付款", "未支付", "已撤销").any(state::contains)) { ignored++; return@forEachIndexed }
                val direction = when (directionText) {
                    "支出", "付款" -> "EXPENSE"
                    "收入", "收款" -> "INCOME"
                    "/", "不计收支", "中性交易", "不计收入", "不计支出" -> "TRANSFER"
                    else -> throw IllegalArgumentException("收支方向无法识别")
                }
                val amount = parseAmount(rawAmount)
                val occurred = parseDate(rawTime, date1904).atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli()
                val paymentTransaction = value("交易单号", "交易订单号", "交易号")
                val transaction = paymentTransaction.ifBlank { value("订单号") }
                // 无类型的“订单号”保留原值与旧导入身份，但不能据此排除疑似重复。
                val transactionType = if (paymentTransaction.isNotBlank() && paymentTransaction != "/") "PAYMENT" else "UNKNOWN"
                val merchant = value("交易对方", "商户名称", "商家名称")
                val product = value("商品", "商品说明", "商品名称")
                val category = value("交易分类", "分类").ifBlank { if (direction == "TRANSFER") "不计收支" else "未分类" }
                val currency = value("币种").ifBlank { "CNY" }.let { if (it in setOf("人民币", "元", "RMB")) "CNY" else it }
                val note = listOf(product, value("备注"), state.takeIf(String::isNotBlank)?.let { "原始状态：$it" }.orEmpty()).filter(String::isNotBlank).distinct().joinToString(" · ")
                val identity = if (transaction.isNotBlank() && transaction != "/") "${source.label}|$transaction|$direction"
                    else "file|${source.label}|$direction|$amount|$occurred|$merchant|$product"
                val key = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
                // 退款状态可能描述原付款而非独立退款，不猜退款金额/时间；外币也不混入人民币汇总。
                val pending = (state.contains("退款") && direction == "EXPENSE") || currency != "CNY"
                entries.add(AccountingEntry(
                    id = UUID.nameUUIDFromBytes(key.toByteArray(Charsets.UTF_8)).toString(),
                    amountMinor = amount, direction = direction, currency = currency, merchant = merchant.ifBlank { product.ifBlank { "未提供交易对方" } },
                    category = category, note = note, occurredAt = occurred, zoneId = "Asia/Shanghai", source = "FILE", channel = source.label,
                    transactionId = transaction, status = if (pending) "PENDING" else "CONFIRMED", ruleId = "bill-file-v2",
                    transactionIdType = transactionType,
                    dedupKey = key, refundOf = null, createdAt = now, updatedAt = now, deletedAt = null,
                ))
            } catch (e: IllegalArgumentException) {
                issues.add(AccountingImportIssue(sheet, rowNumber, e.message ?: "字段格式错误"))
            } catch (_: java.time.DateTimeException) {
                issues.add(AccountingImportIssue(sheet, rowNumber, "交易时间无法识别"))
            } catch (_: ArithmeticException) {
                issues.add(AccountingImportIssue(sheet, rowNumber, "金额超出范围或精度不正确"))
            }
        }
        return AccountingImportPreview(source, entries, issues, ignored)
    }

    private fun parseAmount(raw: String): Long {
        val value = raw.replace("¥", "").replace("￥", "").replace(",", "").trim()
        require(value.matches(Regex("[+]?[0-9]+(?:\\.[0-9]{1,2})?"))) { "金额格式无效" }
        return BigDecimal(value).movePointRight(2).longValueExact().also { require(it > 0) { "金额必须大于零" } }
    }

    private fun parseDate(raw: String, date1904: Boolean): LocalDateTime {
        if (raw.matches(Regex("[0-9]+(?:\\.[0-9]+)?"))) {
            val value = BigDecimal(raw)
            val days = value.setScale(0, RoundingMode.FLOOR).longValueExact()
            val seconds = value.subtract(BigDecimal(days)).multiply(BigDecimal(86400)).setScale(0, RoundingMode.HALF_UP).longValueExact()
            val base = if (date1904) LocalDate.of(1904, 1, 1) else LocalDate.of(1899, 12, 30)
            return base.plusDays(days).atStartOfDay().plusSeconds(seconds)
        }
        val text = raw.replace('/', '-').replace('T', ' ')
        val formatter = DateTimeFormatter.ofPattern("uuuu-M-d H:mm[:ss][.SSS]").withResolverStyle(ResolverStyle.STRICT)
        return LocalDateTime.parse(text, formatter)
    }
}
