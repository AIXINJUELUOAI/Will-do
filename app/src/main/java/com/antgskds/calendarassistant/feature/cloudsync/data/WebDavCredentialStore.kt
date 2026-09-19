package com.antgskds.calendarassistant.feature.cloudsync.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class WebDavCredentialStore(context: Context) {
    private val credentialFile = AtomicFile(File(context.noBackupFilesDir, FILE_NAME))
    private val json = Json { ignoreUnknownKeys = true }

    fun hasPassword(): Boolean = readPassword() != null

    fun hasSyncPassphrase(): Boolean = readSecrets()?.syncPassphrase?.isNotEmpty() == true

    @OptIn(ExperimentalEncodingApi::class)
    fun savePassword(password: String) {
        require(password.isNotEmpty()) { "WebDAV 密码不能为空" }
        saveSecrets((readSecrets() ?: StoredSecrets()).copy(webDavPassword = password))
    }

    fun saveSyncPassphrase(passphrase: String) {
        require(passphrase.isNotEmpty()) { "同步密码不能为空" }
        saveSecrets((readSecrets() ?: StoredSecrets()).copy(syncPassphrase = passphrase))
    }

    fun readPassword(): String? = readSecrets()?.webDavPassword?.takeIf { it.isNotEmpty() }

    fun readSyncPassphrase(): String? = readSecrets()?.syncPassphrase?.takeIf { it.isNotEmpty() }

    @OptIn(ExperimentalEncodingApi::class)
    private fun saveSecrets(secrets: StoredSecrets) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        }
        val payload = EncryptedCredential(
            iv = Base64.encode(cipher.iv),
            ciphertext = Base64.encode(cipher.doFinal(json.encodeToString(secrets).toByteArray(Charsets.UTF_8))),
        )
        val output = credentialFile.startWrite()
        try {
            output.write(json.encodeToString(payload).toByteArray(Charsets.UTF_8))
            credentialFile.finishWrite(output)
        } catch (error: Throwable) {
            credentialFile.failWrite(output)
            throw error
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun readSecrets(): StoredSecrets? {
        if (!credentialFile.baseFile.exists()) return null
        return runCatching {
            val payload = credentialFile.openRead().use { input ->
                json.decodeFromString<EncryptedCredential>(input.readBytes().toString(Charsets.UTF_8))
            }
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(
                    Cipher.DECRYPT_MODE,
                    getOrCreateKey(),
                    GCMParameterSpec(GCM_TAG_LENGTH_BITS, Base64.decode(payload.iv)),
                )
            }
            val plain = cipher.doFinal(Base64.decode(payload.ciphertext)).toString(Charsets.UTF_8)
            runCatching { json.decodeFromString<StoredSecrets>(plain) }
                .getOrElse { StoredSecrets(webDavPassword = plain) }
        }.getOrElse {
            clear()
            null
        }
    }

    fun clear() {
        credentialFile.delete()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    @Serializable
    private data class EncryptedCredential(
        val iv: String,
        val ciphertext: String,
    )

    @Serializable
    private data class StoredSecrets(
        val webDavPassword: String = "",
        val syncPassphrase: String = "",
    )

    companion object {
        private const val FILE_NAME = "webdav_credentials.json"
        private const val KEY_ALIAS = "willdo_webdav_credentials_v1"
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
    }
}
