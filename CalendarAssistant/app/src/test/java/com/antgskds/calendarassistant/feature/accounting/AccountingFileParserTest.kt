package com.antgskds.calendarassistant.feature.accounting

import com.antgskds.calendarassistant.feature.accounting.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AccountingFileParserTest {
    private val parser = AccountingFileParser(1024 * 1024, 2 * 1024 * 1024, 1000, 100)
    private val header = "交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,交易单号,商户单号,备注"
    private val alipayHeader = "交易时间,交易分类,交易对方,对方账号,商品说明,收/支,金额,收/付款方式,交易状态,交易订单号,商家订单号,备注"
    private fun row(id: String = "100000000000000001", date: String = "2026-09-16 12:30:00",
        amount: String = "35.20", direction: String = "支出", state: String = "支付成功") =
        "$date,商户消费,午餐店,午餐,$direction,$amount,零钱,$state,$id,/,/"
    private fun read(text: String, source: BillFileSource = BillFileSource.WECHAT, charset: Charset = Charsets.UTF_8) =
        parser.read(text.toByteArray(charset).inputStream(), source)

    @Test fun `explicit payment headings carry type but generic order heading stays unknown`() {
        val payment = read("$header\n${row()}").entries.single()
        assertEquals("PAYMENT", payment.transactionIdType)
        val unknown = read("${header.replace("交易单号", "订单号")}\n${row()}").entries.single()
        assertEquals("UNKNOWN", unknown.transactionIdType)
        assertEquals(payment.transactionId, unknown.transactionId)
        assertEquals(payment.dedupKey, unknown.dedupKey) // 原文件身份保持兼容。
    }

    @Test fun `wechat BOM and variable preamble preserve quoted content and transaction id`() {
        val result = read("\uFEFF微信支付账单\r\n导出说明\r\n\r\n$header\r\n" +
            "2026-09-16 12:30:00,商户消费,\"午餐,店\",\"套餐\n加\"\"蛋\"\"\",支出,￥35.20,零钱,支付成功,'100000000000000001\t,/,/\r\n")
        assertTrue(result.issues.isEmpty())
        val entry = result.entries.single()
        assertEquals("午餐,店", entry.merchant)
        assertTrue(entry.note.contains("套餐\n加\"蛋\""))
        assertEquals("100000000000000001", entry.transactionId)
        assertEquals(3520L, entry.amountMinor)
        assertEquals("Asia/Shanghai", entry.zoneId)
        assertEquals("2026-09-16T04:30:00Z", java.time.Instant.ofEpochMilli(entry.occurredAt).toString())
    }

    @Test fun `CSV issue row uses physical line after a multiline field`() {
        val text = "$header\n" + row().replace("午餐店", "\"午餐\n店\"") + "\n" + row("bad", amount = "bad")
        assertEquals(4, read(text).issues.single().row)
    }

    @Test fun `alipay GBK old time heading and neutral rows`() {
        val text = "支付宝交易记录\n" + alipayHeader.replace("交易时间", "交易创建时间") + "\n" +
            "2026/9/16 12:30:00,餐饮美食,餐厅,/,午饭,支出,12.34,余额,交易成功,a1,/,/\n" +
            "2026/9/16 13:30:00,转账,余额宝,/,转入,不计收支,200.00,余额,交易成功,a2,/,/\n" +
            "2026/9/16 14:30:00,餐饮美食,餐厅,/,晚饭,支出,20.00,余额,交易关闭,a3,/,/\n"
        val result = read(text, BillFileSource.ALIPAY, Charset.forName("GB18030"))
        assertTrue(result.issues.isEmpty())
        assertEquals(2, result.entries.size)
        assertEquals(1, result.ignored)
        assertEquals(1, result.neutral)
        assertEquals("餐饮美食", result.entries.first().category)
        assertEquals(1234L, result.entries.first().amountMinor)
    }

    @Test fun `invalid rows report errors without losing valid rows`() {
        val result = read(listOf(header, row("good"), row("bad-date", date = "2026-02-30 12:30:00"),
            row("bad-money", amount = "12.345"), row("overflow", amount = "92233720368547758.08"),
            row("failed", state = "支付失败"), "---汇总---").joinToString("\n"))
        assertEquals("good", result.entries.single().transactionId)
        assertEquals(listOf(3, 4, 5), result.issues.map { it.row })
        assertEquals(1, result.ignored)
    }

    @Test fun `refund expense and foreign currency stay pending without invented refund amounts`() {
        val result = read("$header,币种\n" + row("original", state = "已部分退款") + ",CNY\n" +
            row("refund", direction = "收入", state = "退款成功") + ",CNY\n" + row("foreign") + ",USD\n")
        assertEquals(listOf("PENDING", "CONFIRMED", "PENDING"), result.entries.map { it.status })
        assertTrue(result.entries.all { it.amountMinor == 3520L && it.refundOf == null })
        assertEquals(2, result.pending)
    }

    @Test fun `same platform transaction dedup survives file format and merchant changes`() {
        val csv = read("$header\n${row()}").entries.single()
        val days = ChronoUnit.DAYS.between(LocalDate.of(1899, 12, 30), LocalDate.of(2026, 9, 16))
        val numericDate = "$days.5208333333333333"
        val xlsx = parser.read(workbook(numericDate).inputStream(), BillFileSource.WECHAT).entries.single()
        assertEquals(csv.occurredAt, xlsx.occurredAt)
        assertEquals(csv.amountMinor, xlsx.amountMinor)
        assertEquals(csv.dedupKey, xlsx.dedupKey)
        assertEquals(csv.id, xlsx.id)
        assertEquals(csv.id, read("$header\n${row().replace("午餐店", "午饭店")}").entries.single().id)
        val alipay = read("$alipayHeader\n2026-09-16 12:30:00,餐饮,午餐店,/,午餐,支出,35.20,余额,交易成功,100000000000000001,/,/", BillFileSource.ALIPAY)
        assertNotEquals(csv.dedupKey, alipay.entries.single().dedupKey)
    }

    @Test fun `xlsx 1904 date system and sparse physical row numbers`() {
        val days = ChronoUnit.DAYS.between(LocalDate.of(1904, 1, 1), LocalDate.of(2026, 9, 16))
        val result = parser.read(workbook("$days.5", true, invalidRow = true).inputStream(), BillFileSource.WECHAT)
        assertEquals(LocalDate.of(2026, 9, 16), java.time.Instant.ofEpochMilli(result.entries.single().occurredAt)
            .atZone(ZoneId.of("Asia/Shanghai")).toLocalDate())
        assertEquals(20, result.issues.single().row)
    }

    @Test fun `xlsx accepts string dates too`() {
        val result = parser.read(workbook("2026-09-16 12:30:00", textDate = true).inputStream(), BillFileSource.WECHAT)
        assertTrue(result.issues.isEmpty())
        assertEquals(read("$header\n${row()}").entries.single().occurredAt, result.entries.single().occurredAt)
    }

    @Test fun `wrong source and outer zip have actionable errors`() {
        assertTrue(assertThrows(IllegalArgumentException::class.java) {
            read("$header\n${row()}", BillFileSource.ALIPAY)
        }.message!!.contains("切换"))
        assertTrue(assertThrows(IllegalArgumentException::class.java) {
            parser.read(zip(mapOf("bill.csv" to "$header\n${row()}")).inputStream(), BillFileSource.WECHAT)
        }.message!!.contains("解压"))
    }

    @Test fun `size limits and external entities reject entire file`() {
        assertThrows(IllegalArgumentException::class.java) {
            AccountingFileParser(10, 100, 10, 10).read("$header\n${row()}".byteInputStream(), BillFileSource.WECHAT)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AccountingFileParser(1024 * 1024, 10, 10, 10).read(workbook("46000").inputStream(), BillFileSource.WECHAT)
        }
        assertThrows(IllegalArgumentException::class.java) {
            parser.read(zip(mapOf("xl/workbook.xml" to "<!DOCTYPE a [<!ENTITY x SYSTEM 'file:///not-readable'>]><workbook/>"))
                .inputStream(), BillFileSource.WECHAT)
        }
    }

    @Test fun `UTF16 TSV and full width amount heading`() {
        val text = "\uFEFF" + header.replace("金额(元)", "金额（元）").replace(',', '\t') + "\n" + row().replace(',', '\t')
        assertEquals(3520L, read(text, charset = Charsets.UTF_16LE).entries.single().amountMinor)
    }

    @Test fun `fallback identity is stable and direction remains distinct`() {
        val rows = "$header\n${row(id = "/")}\n${row(id = "/", direction = "收入")}"
        val first = read(rows).entries
        assertEquals(first.map { it.id }, read(rows).entries.map { it.id })
        assertNotEquals(first[0].id, first[1].id)
    }

    private fun workbook(date: String, system1904: Boolean = false, invalidRow: Boolean = false, textDate: Boolean = false): ByteArray {
        fun cell(ref: String, value: String) = "<c r=\"$ref\" t=\"inlineStr\"><is><t>$value</t></is></c>"
        val fields = header.split(',')
        val headings = fields.mapIndexed { i, field -> cell("${'A' + i}5", field) }.joinToString("")
        val dateCell = if (textDate) cell("A9", date) else "<c r=\"A9\"><v>$date</v></c>"
        // B、G 等为空而被省略；商户来自 sharedStrings，商品来自 inlineStr。
        val transaction = dateCell + "<c r=\"C9\" t=\"s\"><v>0</v></c>" + cell("D9", "午餐") + cell("E9", "支出") +
            "<c r=\"F9\"><v>35.2</v></c>" + cell("H9", "支付成功") + cell("I9", "100000000000000001")
        val bad = if (invalidRow) "<row r=\"20\">${cell("A20", "bad-date")}${cell("E20", "支出")}${cell("F20", "1.00")}</row>" else ""
        return zip(mapOf(
            "xl/workbook.xml" to "<workbook xmlns=\"urn:test\"><workbookPr date1904=\"${if (system1904) 1 else 0}\"/></workbook>",
            "xl/sharedStrings.xml" to "<sst xmlns=\"urn:test\"><si><r><t>午餐</t></r><r><t>店</t></r></si></sst>",
            "xl/worksheets/sheet1.xml" to "<worksheet xmlns=\"urn:test\"><sheetData><row r=\"5\">$headings</row><row r=\"9\">$transaction</row>$bad</sheetData></worksheet>",
            "xl/worksheets/sheet2.xml" to "<worksheet xmlns=\"urn:test\"><sheetData><row r=\"1\">${cell("A1", "说明")}</row></sheetData></worksheet>",
        ))
    }

    private fun zip(files: Map<String, String>): ByteArray = ByteArrayOutputStream().apply {
        ZipOutputStream(this).use { out -> files.forEach { (name, content) ->
            out.putNextEntry(ZipEntry(name)); out.write(content.toByteArray()); out.closeEntry()
        } }
    }.toByteArray()
}
