package com.antgskds.calendarassistant.feature.recognition.ingest.clipboard

/** 提示按一次复制去重，入库去重由原入库链路独立处理；调用方串行访问。 */
class ClipboardCopyPromptPolicy {
    private var lastPromptedInstanceKey: String? = null

    fun instanceKey(textHash: String, copiedAt: Long): String =
        "$textHash:copy:${copiedAt.coerceAtLeast(0L)}"

    fun shouldSkip(instanceKey: String): Boolean = instanceKey == lastPromptedInstanceKey

    /** 仅在提示已显示后消费该实例；发送失败仍允许重试。 */
    fun markPrompted(instanceKey: String) {
        lastPromptedInstanceKey = instanceKey
    }
}
