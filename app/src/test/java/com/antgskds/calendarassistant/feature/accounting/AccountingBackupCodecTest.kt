package com.antgskds.calendarassistant.feature.accounting

import com.antgskds.calendarassistant.feature.accounting.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class AccountingBackupCodecTest {
    private fun entry() = AccountingEntryEditor.build(AccountingEntryInput(
        amount = "35.12", direction = "EXPENSE", merchant = "餐饮", category = "餐饮",
        note = "换行\n引号\"、逗号,和中文", date = LocalDate.of(2026, 9, 18), time = LocalTime.NOON,
    ), null, 123L)

    @Test fun roundTripPreservesIdentityMoneyStatusesAndUnicode() {
        val manual = entry()
        val entries = listOf(manual,
            manual.copy(id = "file", source = "FILE", status = "PENDING", currency = "USD", dedupKey = "key",
                transactionId = "order", transactionIdType = "MERCHANT_ORDER"),
            manual.copy(id = "recognition", source = "RECOGNITION", direction = "INCOME"))
        assertEquals(entries, AccountingBackupCodec.decode(AccountingBackupCodec.encode(entries).toByteArray()))
    }

    @Test fun screenshotIsEmbeddedOnceAndLocalPathsAreNotExported() {
        val original = entry().copy(sourceImagePath = "/private/accounting_images/picture.jpg")
        val image = requireNotNull(javaClass.getResourceAsStream("/accounting/one-pixel.jpg")).use { it.readBytes() }
        val json = AccountingBackupCodec.encode(listOf(original, original.copy(id = "second")),
            mapOf(requireNotNull(original.sourceImagePath) to image))
        assertFalse(json.contains("/private/"))
        val preview = AccountingBackupCodec.preview(json.toByteArray())
        assertEquals(1, preview.images.size)
        assertEquals(preview.entries[0].sourceImagePath, preview.entries[1].sourceImagePath)
        assertArrayEquals(image, preview.images.getValue(requireNotNull(preview.entries[0].sourceImagePath)))
    }

    @Test fun rejectsUnsupportedOrBrokenBackupsBeforeImport() {
        val json = AccountingBackupCodec.encode(listOf(entry()))
        for (invalid in listOf("{}", json.replace("willdo-accounting", "another-format"),
            json.replace("\"version\": 1", "\"version\": 2"), json.dropLast(5),
            json.replace("\"sourceImagePath\": null", "\"sourceImagePath\": \"../../private-file\""))) {
            assertThrows(IllegalArgumentException::class.java) { AccountingBackupCodec.decode(invalid.toByteArray()) }
        }
    }

    @Test fun rejectsInvalidMoneyDirectionAndRepeatedIdentity() {
        val a = entry()
        for (entries in listOf(listOf(a.copy(amountMinor = 0)), listOf(a.copy(direction = "WRONG")),
            listOf(a.copy(zoneId = "invalid-zone")), listOf(a.copy(deletedAt = 1)), listOf(a, a))) {
            assertThrows(IllegalArgumentException::class.java) { AccountingBackupCodec.encode(entries) }
        }
    }

    @Test fun parserReadsNativeBackupWithoutTreatingItAsOfficialCsv() {
        val a = entry()
        val result = AccountingFileParser(10000, 10000, 100, 10).read(
            AccountingBackupCodec.encode(listOf(a)).byteInputStream(), BillFileSource.WILLDO)
        assertEquals(listOf(a), result.entries)
        assertTrue(result.issues.isEmpty())
        assertEquals(BillFileSource.WILLDO, result.source)
    }

    @Test fun emptyBackupCanRoundTrip() {
        assertTrue(AccountingBackupCodec.decode(AccountingBackupCodec.encode(emptyList()).toByteArray()).isEmpty())
    }
}
