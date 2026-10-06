/*
 * Adapted from airline233/WakeUpDecoder, wakeup_share_sim.py.
 * Upstream revision: 577161c9ea8c34dddbcecd6ea9a702687c4ac8c4
 * https://github.com/airline233/WakeUpDecoder
 * Licensed under the Apache License, Version 2.0.
 * The full license and attribution are bundled in assets/licenses/WakeUpDecoder-*.
 *
 * Modified for Will do on 2026-10-06: Python -> Kotlin; APK metadata is a fixed
 * 6.1.70 compatibility profile; networking and user-facing errors are handled
 * by WakeUpShareClient. No APK, native library, private signing key, account
 * credentials, or real device identifiers are distributed with this port.
 */
package com.antgskds.calendarassistant.feature.backup.courseimport.external.wakeup

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** APK protocol values are compatibility constants, not user settings or user credentials. */
internal object WakeUpShareProtocol {
    private const val MAGIC = "8&%d*"
    private const val SIGN_A_KEY = "@fG2SuLA"
    private const val KEY_SALT = "@#AIjd83#@6B"
    private const val VERSION_CODE = 450
    private const val VERSION_NAME = "6.1.70"
    private const val PACKAGE_NAME = "com.suda.yzune.wakeupschedule"
    private const val CHANNEL = "100271a"
    private const val PUBLIC_CLIENT_TOKEN = "1_XPXQH3c5HRPtFHkSwi3sCCURmT25QfxM"
    private const val CERTIFICATE_HEX_MD5 = "318c6d4f74655d4f032fb0466bcfdfbc"

    // ponytail: upstream's documented anonymous profile; if the server rejects it,
    // fail with file-import guidance. Never collect or guess a logged-in device ID.
    private const val COMPAT_ANDROID_ID = "0000000000000000"
    val cuid: String = md5("com.baidu" + COMPAT_ANDROID_ID).uppercase() + "|0"
    val adid: String = md5("alpha.beta" + COMPAT_ANDROID_ID).let { hash ->
        val checksum = hash.chunked(8).fold(0L) { acc, part -> acc xor part.toLong(16) }
        hash + checksum.toString(16).padStart(8, '0')
    }

    data class ShareRequest(val body: String, val rc4Key: String)

    fun generateRand10(): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val random = SecureRandom()
        return buildString { repeat(10) { append(alphabet[random.nextInt(alphabet.length)]) } }
    }

    fun makeSignA(rand10: String): String {
        require(rand10.matches(Regex("[A-Za-z0-9]{10}"))) { "Invalid WakeUp challenge" }
        val plain = "$MAGIC##$rand10##$CERTIFICATE_HEX_MD5##$cuid"
        return nativeHexEncode(nativeDesEncrypt(plain.toByteArray(StandardCharsets.UTF_8), SIGN_A_KEY))
    }

    fun buildAntispamBody(rand10: String): String =
        formEncode(listOf("data" to makeSignA(rand10)) + commonParams()) + "&"

    fun tokenFromSignB(signB: String, rand10: String): String {
        val plain = nativeDesDecrypt(nativeHexDecode(signB), rand10.take(5) + "#G4")
        require(plain.size >= 22 &&
            String(plain, 0, 10, StandardCharsets.ISO_8859_1) == rand10
        ) { "Invalid WakeUp authentication response" }
        return String(plain, 12, 10, StandardCharsets.ISO_8859_1)
    }

    fun buildShareRequest(
        code: String,
        token: String,
        serverTime: Long = System.currentTimeMillis() / 1_000,
        monotonicMs: Long = System.nanoTime() / 1_000_000
    ): ShareRequest {
        val key = nativeGetKey(VERSION_CODE.toString(), token)
        val encrypted = Base64.getEncoder().encodeToString(
            rc4(("key=" + quote(code)).toByteArray(StandardCharsets.UTF_8), key)
        )
        val params = listOf("data" to encrypted) + commonParams() + listOf(
            "nt" to "wifi", "_t_" to serverTime.toString(),
            "kakorrhaphiophobia" to monotonicMs.toString()
        )
        val sorted = params.map { (name, value) -> "$name=$value" }.sorted().joinToString("")
        val packed = Base64.getEncoder().encodeToString(sorted.toByteArray(StandardCharsets.UTF_8))
        val sign = md5("$MAGIC[" + md5(token) + "]@" + packed)
        val body = "&" + formEncode(params.dropLast(2)) +
            "&sign=$sign&_t_=$serverTime&kakorrhaphiophobia=$monotonicMs"
        return ShareRequest(body, key)
    }

    fun decryptShareData(encrypted: String, key: String): String =
        String(rc4(Base64.getDecoder().decode(encrypted), key), StandardCharsets.UTF_8)

    private fun commonParams(): List<Pair<String, String>> = listOf(
        "area" to "", "screensize" to "1080x2400", "cuid" to cuid, "os" to "android",
        "city" to "", "abis" to "arm64-v8a", "channel" to CHANNEL, "appBit" to "64",
        "vc" to VERSION_CODE.toString(), "deviceId" to "", "token" to PUBLIC_CLIENT_TOKEN,
        "adid" to adid, "province" to "", "pkgName" to PACKAGE_NAME, "appId" to "wakeup",
        "download_type" to "1", "vcname" to VERSION_NAME, "sdk" to "35",
        "device" to "Pixel 7", "brand" to "google", "operatorid" to ""
    )

    private fun quote(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
    private fun formEncode(params: List<Pair<String, String>>): String =
        params.joinToString("&") { (name, value) -> "$name=" + quote(value) }

    private fun md5(value: String): String =
        MessageDigest.getInstance("MD5").digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    private fun nativeGetKey(type: String, token: String): String {
        val tokenHash = md5("[$token]@")
        val rearranged = tokenHash.substring(17).reversed() + tokenHash.substring(15, 17) +
            tokenHash.substring(0, 15).reversed()
        val chars = (md5(KEY_SALT) + md5(type) + rearranged).toCharArray()
        repeat(3) { swap(chars, it, chars.lastIndex - it) }
        val combined = String(chars)
        val result = (combined + md5(combined)).toCharArray()
        repeat(60) { swap(result, it, result.lastIndex - it) }
        return String(result)
    }

    private fun swap(chars: CharArray, left: Int, right: Int) {
        val value = chars[left]
        chars[left] = chars[right]
        chars[right] = value
    }

    private fun rc4(data: ByteArray, key: String): ByteArray {
        val keyBytes = key.toByteArray(StandardCharsets.UTF_8)
        require(keyBytes.isNotEmpty())
        val state = IntArray(256) { it }
        var j = 0
        for (i in state.indices) {
            j = (j + state[i] + (keyBytes[i % keyBytes.size].toInt() and 0xff)) and 0xff
            val temp = state[i]; state[i] = state[j]; state[j] = temp
        }
        var i = 0
        j = 0
        return ByteArray(data.size) { index ->
            i = (i + 1) and 0xff
            j = (j + state[i]) and 0xff
            val temp = state[i]; state[i] = state[j]; state[j] = temp
            (data[index].toInt() xor state[(state[i] + state[j]) and 0xff]).toByte()
        }
    }

    // Preserve upstream's LSB-first bit order, custom padding, reversed-nibble hex,
    // and the duplicated 46 in PC2. Standard DES libraries produce different data.
    private fun bits(data: ByteArray): IntArray = IntArray(data.size * 8) { index ->
        (data[index / 8].toInt() ushr (index % 8)) and 1
    }
    private fun permute(bits: IntArray, table: IntArray): IntArray =
        IntArray(table.size) { bits[table[it]] }
    private fun rotate(bits: IntArray, amount: Int): IntArray =
        IntArray(bits.size) { bits[(it + amount) % bits.size] }

    private fun subkeys(key: String): List<IntArray> {
        val bytes = key.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size == 8)
        val permuted = permute(bits(bytes), PC1)
        var left = permuted.copyOfRange(0, 28)
        var right = permuted.copyOfRange(28, 56)
        return SHIFTS.map { shift ->
            left = rotate(left, shift)
            right = rotate(right, shift)
            permute(left + right, PC2)
        }
    }

    private fun desFunction(right: IntArray, subkey: IntArray): IntArray {
        val mixed = permute(right, E).let { expanded ->
            IntArray(expanded.size) { expanded[it] xor subkey[it] }
        }
        val sBits = IntArray(32)
        repeat(8) { box ->
            val offset = box * 6
            val row = mixed[offset] * 2 + mixed[offset + 5]
            val column = mixed[offset + 1] * 8 + mixed[offset + 2] * 4 +
                mixed[offset + 3] * 2 + mixed[offset + 4]
            val value = SBOX[box][row][column]
            repeat(4) { bit -> sBits[box * 4 + bit] = (value ushr (3 - bit)) and 1 }
        }
        return permute(sBits, P)
    }

    private fun desBlock(block: ByteArray, keys: List<IntArray>): ByteArray {
        val permuted = permute(bits(block), IP)
        var left = permuted.copyOfRange(0, 32)
        var right = permuted.copyOfRange(32, 64)
        for (key in keys) {
            val f = desFunction(right, key)
            val next = IntArray(32) { left[it] xor f[it] }
            left = right
            right = next
        }
        val output = permute(right + left, FP)
        return ByteArray(8) { index ->
            (0 until 8).fold(0) { value, bit -> value or (output[index * 8 + bit] shl bit) }.toByte()
        }
    }

    private fun nativeDesEncrypt(plain: ByteArray, key: String): ByteArray {
        val paddedSize = (plain.size and 7.inv()) + 8
        val padded = plain.copyOf(paddedSize)
        padded[padded.lastIndex] = (paddedSize - plain.size).toByte()
        val keys = subkeys(key)
        val result = ByteArray(paddedSize)
        for (offset in result.indices step 8) {
            desBlock(padded.copyOfRange(offset, offset + 8), keys).copyInto(result, offset)
        }
        return result
    }

    private fun nativeDesDecrypt(cipher: ByteArray, key: String): ByteArray {
        require(cipher.isNotEmpty() && cipher.size % 8 == 0) { "Invalid WakeUp ciphertext" }
        val keys = subkeys(key).reversed()
        val result = ByteArray(cipher.size)
        for (offset in result.indices step 8) {
            desBlock(cipher.copyOfRange(offset, offset + 8), keys).copyInto(result, offset)
        }
        val padding = result.last().toInt() and 0xff
        require(padding in 1..8 && padding <= result.size) { "Invalid WakeUp padding" }
        require((result.size - padding until result.lastIndex).all { result[it] == 0.toByte() }) {
            "Invalid WakeUp padding"
        }
        return result.copyOf(result.size - padding)
    }

    private fun reverseNibble(value: Int): Int =
        ((value and 1) shl 3) or ((value and 2) shl 1) or
            ((value and 4) ushr 1) or ((value and 8) ushr 3)

    private fun nativeHexEncode(data: ByteArray): String = buildString {
        for (byte in data) {
            val value = byte.toInt() and 0xff
            append('0').append(reverseNibble(value and 15).toString(16))
            append('0').append(reverseNibble(value ushr 4).toString(16))
        }
    }

    private fun nativeHexDecode(text: String): ByteArray {
        val clean = text.trim()
        require(clean.isNotEmpty() && clean.length % 4 == 0) { "Invalid WakeUp hex data" }
        return ByteArray(clean.length / 4) { index ->
            val offset = index * 4
            require(clean[offset] == '0' && clean[offset + 2] == '0') { "Invalid WakeUp hex data" }
            val low = clean[offset + 1].digitToIntOrNull(16) ?: error("Invalid WakeUp hex data")
            val high = clean[offset + 3].digitToIntOrNull(16) ?: error("Invalid WakeUp hex data")
            (reverseNibble(low) or (reverseNibble(high) shl 4)).toByte()
        }
    }

    private val IP = intArrayOf(57, 49, 41, 33, 25, 17, 9, 1, 59, 51, 43, 35, 27, 19, 11, 3, 61, 53, 45, 37, 29, 21, 13, 5, 63, 55, 47, 39, 31, 23, 15, 7, 56, 48, 40, 32, 24, 16, 8, 0, 58, 50, 42, 34, 26, 18, 10, 2, 60, 52, 44, 36, 28, 20, 12, 4, 62, 54, 46, 38, 30, 22, 14, 6)
    private val FP = intArrayOf(39, 7, 47, 15, 55, 23, 63, 31, 38, 6, 46, 14, 54, 22, 62, 30, 37, 5, 45, 13, 53, 21, 61, 29, 36, 4, 44, 12, 52, 20, 60, 28, 35, 3, 43, 11, 51, 19, 59, 27, 34, 2, 42, 10, 50, 18, 58, 26, 33, 1, 41, 9, 49, 17, 57, 25, 32, 0, 40, 8, 48, 16, 56, 24)
    private val E = intArrayOf(31, 0, 1, 2, 3, 4, 3, 4, 5, 6, 7, 8, 7, 8, 9, 10, 11, 12, 11, 12, 13, 14, 15, 16, 15, 16, 17, 18, 19, 20, 19, 20, 21, 22, 23, 24, 23, 24, 25, 26, 27, 28, 27, 28, 29, 30, 31, 0)
    private val P = intArrayOf(15, 6, 19, 20, 28, 11, 27, 16, 0, 14, 22, 25, 4, 17, 30, 9, 1, 7, 23, 13, 31, 26, 2, 8, 18, 12, 29, 5, 21, 10, 3, 24)
    private val PC1 = intArrayOf(56, 48, 40, 32, 24, 16, 8, 0, 57, 49, 41, 33, 25, 17, 9, 1, 58, 50, 42, 34, 26, 18, 10, 2, 59, 51, 43, 35, 62, 54, 46, 38, 30, 22, 14, 6, 61, 53, 45, 37, 29, 21, 13, 5, 60, 52, 44, 36, 28, 20, 12, 4, 27, 19, 11, 3)
    private val PC2 = intArrayOf(13, 16, 10, 23, 0, 4, 2, 27, 14, 5, 20, 9, 22, 18, 11, 3, 25, 7, 15, 6, 26, 19, 12, 1, 40, 51, 30, 36, 46, 54, 29, 39, 50, 44, 32, 46, 43, 48, 38, 55, 33, 52, 45, 41, 49, 35, 28, 31)
    private val SHIFTS = intArrayOf(1, 1, 2, 2, 2, 2, 2, 2, 1, 2, 2, 2, 2, 2, 2, 1)
    private val SBOX = arrayOf(
        arrayOf(
            intArrayOf(14, 4, 13, 1, 2, 15, 11, 8, 3, 10, 6, 12, 5, 9, 0, 7),
            intArrayOf(0, 15, 7, 4, 14, 2, 13, 1, 10, 6, 12, 11, 9, 5, 3, 8),
            intArrayOf(4, 1, 14, 8, 13, 6, 2, 11, 15, 12, 9, 7, 3, 10, 5, 0),
            intArrayOf(15, 12, 8, 2, 4, 9, 1, 7, 5, 11, 3, 14, 10, 0, 6, 13)
        ),
        arrayOf(
            intArrayOf(15, 1, 8, 14, 6, 11, 3, 4, 9, 7, 2, 13, 12, 0, 5, 10),
            intArrayOf(3, 13, 4, 7, 15, 2, 8, 14, 12, 0, 1, 10, 6, 9, 11, 5),
            intArrayOf(0, 14, 7, 11, 10, 4, 13, 1, 5, 8, 12, 6, 9, 3, 2, 15),
            intArrayOf(13, 8, 10, 1, 3, 15, 4, 2, 11, 6, 7, 12, 0, 5, 14, 9)
        ),
        arrayOf(
            intArrayOf(10, 0, 9, 14, 6, 3, 15, 5, 1, 13, 12, 7, 11, 4, 2, 8),
            intArrayOf(13, 7, 0, 9, 3, 4, 6, 10, 2, 8, 5, 14, 12, 11, 15, 1),
            intArrayOf(13, 6, 4, 9, 8, 15, 3, 0, 11, 1, 2, 12, 5, 10, 14, 7),
            intArrayOf(1, 10, 13, 0, 6, 9, 8, 7, 4, 15, 14, 3, 11, 5, 2, 12)
        ),
        arrayOf(
            intArrayOf(7, 13, 14, 3, 0, 6, 9, 10, 1, 2, 8, 5, 11, 12, 4, 15),
            intArrayOf(13, 8, 11, 5, 6, 15, 0, 3, 4, 7, 2, 12, 1, 10, 14, 9),
            intArrayOf(10, 6, 9, 0, 12, 11, 7, 13, 15, 1, 3, 14, 5, 2, 8, 4),
            intArrayOf(3, 15, 0, 6, 10, 1, 13, 8, 9, 4, 5, 11, 12, 7, 2, 14)
        ),
        arrayOf(
            intArrayOf(2, 12, 4, 1, 7, 10, 11, 6, 8, 5, 3, 15, 13, 0, 14, 9),
            intArrayOf(14, 11, 2, 12, 4, 7, 13, 1, 5, 0, 15, 10, 3, 9, 8, 6),
            intArrayOf(4, 2, 1, 11, 10, 13, 7, 8, 15, 9, 12, 5, 6, 3, 0, 14),
            intArrayOf(11, 8, 12, 7, 1, 14, 2, 13, 6, 15, 0, 9, 10, 4, 5, 3)
        ),
        arrayOf(
            intArrayOf(12, 1, 10, 15, 9, 2, 6, 8, 0, 13, 3, 4, 14, 7, 5, 11),
            intArrayOf(10, 15, 4, 2, 7, 12, 9, 5, 6, 1, 13, 14, 0, 11, 3, 8),
            intArrayOf(9, 14, 15, 5, 2, 8, 12, 3, 7, 0, 4, 10, 1, 13, 11, 6),
            intArrayOf(4, 3, 2, 12, 9, 5, 15, 10, 11, 14, 1, 7, 6, 0, 8, 13)
        ),
        arrayOf(
            intArrayOf(4, 11, 2, 14, 15, 0, 8, 13, 3, 12, 9, 7, 5, 10, 6, 1),
            intArrayOf(13, 0, 11, 7, 4, 9, 1, 10, 14, 3, 5, 12, 2, 15, 8, 6),
            intArrayOf(1, 4, 11, 13, 12, 3, 7, 14, 10, 15, 6, 8, 0, 5, 9, 2),
            intArrayOf(6, 11, 13, 8, 1, 4, 10, 7, 9, 5, 0, 15, 14, 2, 3, 12)
        ),
        arrayOf(
            intArrayOf(13, 2, 8, 4, 6, 15, 11, 1, 10, 9, 3, 14, 5, 0, 12, 7),
            intArrayOf(1, 15, 13, 8, 10, 3, 7, 4, 12, 5, 6, 11, 0, 14, 9, 2),
            intArrayOf(7, 11, 4, 1, 9, 12, 14, 2, 0, 6, 10, 13, 15, 3, 5, 8),
            intArrayOf(2, 1, 14, 7, 4, 10, 8, 13, 15, 12, 9, 0, 3, 5, 6, 11)
        )
    )
}
