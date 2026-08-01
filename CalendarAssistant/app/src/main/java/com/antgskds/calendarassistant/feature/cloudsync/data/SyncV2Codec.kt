package com.antgskds.calendarassistant.feature.cloudsync.data

import com.antgskds.calendarassistant.feature.cloudsync.domain.SYNC_V2_PROTOCOL_VERSION
import com.antgskds.calendarassistant.feature.cloudsync.domain.SyncV2AttachmentRef
import com.antgskds.calendarassistant.feature.cloudsync.domain.SyncV2DeviceState
import com.antgskds.calendarassistant.feature.cloudsync.domain.SyncV2Record
import com.antgskds.calendarassistant.feature.cloudsync.domain.SyncV2VaultConfig
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class SyncV2Codec(
    private val secureRandom: SecureRandom = SecureRandom(),
) {
    private val gson = Gson()

    fun encodeVault(config: SyncV2VaultConfig): ByteArray = gson.toJson(config).toByteArray(Charsets.UTF_8)

    fun decodeVault(bytes: ByteArray): SyncV2VaultConfig {
        val json = gson.fromJson(bytes.toString(Charsets.UTF_8), JsonObject::class.java)
        val config = if (json.has("vaultId")) {
            gson.fromJson(json, SyncV2VaultConfig::class.java)
        } else {
            SyncV2VaultConfig(
                vaultId = json.get("a")?.asString.orEmpty(),
                saltBase64 = json.get("b")?.asString.orEmpty(),
                iterations = DEFAULT_ITERATIONS,
                keyVerifierBase64 = json.get("c")?.asString.orEmpty(),
                createdAt = json.get("d")?.asLong ?: 0L,
            )
        }
        return config.also {
            require(it.protocolVersion == SYNC_V2_PROTOCOL_VERSION) { "远端同步协议版本不受支持" }
            require(it.vaultId.isNotBlank() && it.saltBase64.isNotBlank() && it.keyVerifierBase64.isNotBlank()) {
                "远端加密配置无效"
            }
            require(it.iterations in MIN_ITERATIONS..MAX_ITERATIONS) { "远端加密迭代次数不受支持" }
            val salt = runCatching { Base64.getDecoder().decode(it.saltBase64) }.getOrNull()
            require(salt != null && salt.size >= 16) { "远端加密盐值无效" }
        }
    }

    fun encodeState(state: SyncV2DeviceState, key: ByteArray): ByteArray {
        require(state.protocolVersion == SYNC_V2_PROTOCOL_VERSION)
        return encrypt(gzip(gson.toJson(state).toByteArray(Charsets.UTF_8)), key, STATE_MAGIC)
    }

    fun decodeState(bytes: ByteArray, key: ByteArray): SyncV2DeviceState {
        val state = gson.fromJson(
            ungzip(decrypt(bytes, key, STATE_MAGIC)).toString(Charsets.UTF_8),
            SyncV2DeviceState::class.java,
        )
        require(state.protocolVersion == SYNC_V2_PROTOCOL_VERSION) { "设备状态版本不受支持" }
        require(state.deviceId.isNotBlank() && state.generation >= 0L) { "设备状态元数据无效" }
        require(state.records.all { record ->
            record.syncUuid.isNotBlank() && record.revisionId.isNotBlank() && record.versionVector.isNotEmpty()
        }) { "设备状态包含无效记录" }
        return state
    }

    fun encodeAsset(plain: ByteArray, key: ByteArray): ByteArray = encrypt(plain, key, ASSET_MAGIC)

    fun decodeAsset(encrypted: ByteArray, key: ByteArray): ByteArray = decrypt(encrypted, key, ASSET_MAGIC)

    fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int = DEFAULT_ITERATIONS): ByteArray {
        require(passphrase.isNotEmpty()) { "同步密码不能为空" }
        require(salt.size >= 16) { "同步密钥盐值无效" }
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun keyVerifier(key: ByteArray): String = Base64.getEncoder().encodeToString(
        hmac(key, "WillDo WebDAV Sync V2 key verifier".toByteArray(Charsets.UTF_8))
    )

    fun assetKey(key: ByteArray, plain: ByteArray): String = hmac(key, plain).toHex()

    fun sha256(plain: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(plain).toHex()

    fun payloadHash(payloadJson: String, attachments: List<SyncV2AttachmentRef>): String {
        val stableAttachments = attachments.sortedWith(compareBy<SyncV2AttachmentRef> { it.role }.thenBy { it.attachmentUuid })
        return sha256((payloadJson + "\n" + gson.toJson(stableAttachments)).toByteArray(Charsets.UTF_8))
    }

    fun stateHash(records: List<SyncV2Record>): String {
        val stable = records.map { record ->
            record.copy(
                parentRevisionIds = record.parentRevisionIds.sorted(),
                versionVector = record.versionVector.toSortedMap(),
                attachments = record.attachments.sortedWith(
                    compareBy<SyncV2AttachmentRef> { it.role }
                        .thenBy { it.attachmentUuid }
                        .thenBy { it.assetKey }
                ),
            )
        }.sortedWith(compareBy<SyncV2Record> { it.entityType }.thenBy { it.syncUuid }.thenBy { it.revisionId })
        return sha256(gson.toJson(stable).toByteArray(Charsets.UTF_8))
    }

    fun encodeStringList(values: List<String>): String = gson.toJson(values)

    fun decodeStringList(json: String): List<String> =
        runCatching { gson.fromJson<List<String>>(json, object : TypeToken<List<String>>() {}.type) }.getOrDefault(emptyList())

    fun encodeVector(vector: Map<String, Long>): String = gson.toJson(vector.toSortedMap())

    fun decodeVector(json: String): Map<String, Long> =
        runCatching { gson.fromJson<Map<String, Long>>(json, object : TypeToken<Map<String, Long>>() {}.type) }
            .getOrDefault(emptyMap())

    fun encodeAttachments(attachments: List<SyncV2AttachmentRef>): String = gson.toJson(attachments)

    fun decodeAttachments(json: String): List<SyncV2AttachmentRef> =
        runCatching {
            gson.fromJson<List<SyncV2AttachmentRef>>(json, object : TypeToken<List<SyncV2AttachmentRef>>() {}.type)
        }.getOrDefault(emptyList())

    fun <T> encodePayload(payload: T): String = gson.toJson(payload)

    fun <T> decodePayload(json: String, type: Class<T>): T = gson.fromJson(json, type)

    private fun encrypt(plain: ByteArray, key: ByteArray, magic: ByteArray): ByteArray {
        require(key.size == KEY_SIZE_BYTES) { "同步密钥必须为 256 位" }
        val nonce = ByteArray(NONCE_SIZE).also(secureRandom::nextBytes)
        val encrypted = Cipher.getInstance(TRANSFORMATION).run {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            updateAAD(magic)
            doFinal(plain)
        }
        return magic + nonce + encrypted
    }

    private fun decrypt(encoded: ByteArray, key: ByteArray, magic: ByteArray): ByteArray {
        require(encoded.size > magic.size + NONCE_SIZE) { "同步文件格式无效" }
        require(encoded.copyOfRange(0, magic.size).contentEquals(magic)) { "同步文件版本不受支持" }
        val nonce = encoded.copyOfRange(magic.size, magic.size + NONCE_SIZE)
        val ciphertext = encoded.copyOfRange(magic.size + NONCE_SIZE, encoded.size)
        return Cipher.getInstance(TRANSFORMATION).run {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            updateAAD(magic)
            doFinal(ciphertext)
        }
    }

    private fun hmac(key: ByteArray, bytes: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256"))
        doFinal(bytes)
    }

    private fun gzip(bytes: ByteArray): ByteArray = ByteArrayOutputStream().use { output ->
        GZIPOutputStream(output).use { it.write(bytes) }
        output.toByteArray()
    }

    private fun ungzip(bytes: ByteArray): ByteArray =
        GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }

    private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte) }

    companion object {
        const val DEFAULT_ITERATIONS = 210_000
        private const val MIN_ITERATIONS = 100_000
        private const val MAX_ITERATIONS = 1_000_000
        private const val KEY_BITS = 256
        private const val KEY_SIZE_BYTES = 32
        private const val NONCE_SIZE = 12
        private const val TAG_BITS = 128
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private val STATE_MAGIC = byteArrayOf('W'.code.toByte(), 'D'.code.toByte(), 'S'.code.toByte(), 2)
        private val ASSET_MAGIC = byteArrayOf('W'.code.toByte(), 'D'.code.toByte(), 'A'.code.toByte(), 2)
    }
}
