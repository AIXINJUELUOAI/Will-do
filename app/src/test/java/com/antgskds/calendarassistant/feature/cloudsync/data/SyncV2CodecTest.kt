package com.antgskds.calendarassistant.feature.cloudsync.data

import com.antgskds.calendarassistant.feature.cloudsync.domain.SyncV2AttachmentRef
import com.antgskds.calendarassistant.feature.cloudsync.domain.SyncV2DeviceState
import com.antgskds.calendarassistant.feature.cloudsync.domain.SyncV2EntityType
import com.antgskds.calendarassistant.feature.cloudsync.domain.SyncV2Record
import com.antgskds.calendarassistant.feature.cloudsync.domain.SyncV2RecordStatus
import com.antgskds.calendarassistant.feature.cloudsync.domain.SyncV2VaultConfig
import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SyncV2CodecTest {
    private val codec = SyncV2Codec()
    private val salt = ByteArray(16) { it.toByte() }
    private val key = codec.deriveKey("correct horse battery staple".toCharArray(), salt)

    @Test
    fun vaultAndEncryptedStateRoundTrip() {
        val vault = SyncV2VaultConfig(
            vaultId = "vault-1",
            saltBase64 = Base64.getEncoder().encodeToString(salt),
            iterations = SyncV2Codec.DEFAULT_ITERATIONS,
            keyVerifierBase64 = codec.keyVerifier(key),
            createdAt = 123L,
        )
        assertEquals(vault, codec.decodeVault(codec.encodeVault(vault)))

        val state = SyncV2DeviceState(
            deviceId = "device-A",
            generation = 3L,
            generatedAt = 456L,
            records = listOf(record()),
        )
        assertEquals(state, codec.decodeState(codec.encodeState(state, key), key))
    }

    @Test
    fun legacyObfuscatedVaultIsDecodedWithoutChangingItsKeyMaterial() {
        val verifier = codec.keyVerifier(key)
        val legacy = """{"a":"vault-1","b":"${Base64.getEncoder().encodeToString(salt)}","c":"$verifier","d":123}"""

        assertEquals(
            SyncV2VaultConfig(
                vaultId = "vault-1",
                saltBase64 = Base64.getEncoder().encodeToString(salt),
                iterations = SyncV2Codec.DEFAULT_ITERATIONS,
                keyVerifierBase64 = verifier,
                createdAt = 123L,
            ),
            codec.decodeVault(legacy.toByteArray()),
        )
    }

    @Test
    fun wrongKeyAndTamperingAreRejected() {
        val encoded = codec.encodeState(
            SyncV2DeviceState(
                deviceId = "device-A",
                generation = 1L,
                generatedAt = 1L,
                records = listOf(record()),
            ),
            key,
        )
        val wrongKey = codec.deriveKey("wrong password".toCharArray(), salt)
        assertThrows(Exception::class.java) { codec.decodeState(encoded, wrongKey) }

        val tampered = encoded.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        assertThrows(Exception::class.java) { codec.decodeState(tampered, key) }
    }

    @Test
    fun assetEncryptionRoundTripsAndContentAddressIsStable() {
        val plain = "attachment payload".toByteArray()
        val first = codec.encodeAsset(plain, key)
        val second = codec.encodeAsset(plain, key)

        assertNotEquals(first.toList(), second.toList())
        assertArrayEquals(plain, codec.decodeAsset(first, key))
        assertEquals(codec.assetKey(key, plain), codec.assetKey(key, plain.copyOf()))
    }

    @Test
    fun stateHashIgnoresCollectionOrdering() {
        val attachmentA = attachment("a", "key-a")
        val attachmentB = attachment("b", "key-b")
        val first = record().copy(
            parentRevisionIds = listOf("parent-b", "parent-a"),
            versionVector = linkedMapOf("B" to 2L, "A" to 1L),
            attachments = listOf(attachmentB, attachmentA),
        )
        val second = first.copy(
            parentRevisionIds = first.parentRevisionIds.reversed(),
            versionVector = linkedMapOf("A" to 1L, "B" to 2L),
            attachments = first.attachments.reversed(),
        )

        assertEquals(codec.stateHash(listOf(first)), codec.stateHash(listOf(second)))
    }

    @Test
    fun unsupportedVaultCostIsRejectedBeforeKeyDerivation() {
        val unsafe = SyncV2VaultConfig(
            vaultId = "vault-1",
            saltBase64 = Base64.getEncoder().encodeToString(salt),
            iterations = Int.MAX_VALUE,
            keyVerifierBase64 = "verifier",
            createdAt = 0L,
        )
        assertThrows(IllegalArgumentException::class.java) { codec.decodeVault(codec.encodeVault(unsafe)) }
    }

    private fun record() = SyncV2Record(
        entityType = SyncV2EntityType.EVENT.name,
        syncUuid = "event-1",
        revisionId = "revision-1",
        parentRevisionIds = emptyList(),
        versionVector = mapOf("device-A" to 1L),
        modifiedAt = 100L,
        modifiedByDeviceId = "device-A",
        status = SyncV2RecordStatus.ACTIVE.name,
        payloadJson = "{\"title\":\"test\"}",
    )

    private fun attachment(id: String, key: String) = SyncV2AttachmentRef(
        attachmentUuid = id,
        assetKey = key,
        role = "EVENT_ATTACHMENT",
        displayName = "$id.txt",
        mimeType = "text/plain",
        plainSize = 1L,
        plainSha256 = "sha-$id",
    )
}
