package com.antgskds.calendarassistant.core.event

import java.security.MessageDigest
import java.util.UUID

object EventIdentity {
    fun newTraceId(prefix: String? = null): String {
        val raw = UUID.randomUUID().toString()
        return if (prefix.isNullOrBlank()) raw else "${prefix}_${raw}"
    }

    fun entityKey(sourceType: String, sourceId: String, contentHashSource: String): String {
        val hash = sha256Hex(contentHashSource)
        return "${sourceType}_${sourceId}_${hash}"
    }

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        val builder = StringBuilder(digest.size * 2)
        for (byte in digest) {
            builder.append(String.format("%02x", byte))
        }
        return builder.toString()
    }
}
