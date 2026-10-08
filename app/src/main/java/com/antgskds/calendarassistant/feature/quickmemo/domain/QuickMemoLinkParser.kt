package com.antgskds.calendarassistant.feature.quickmemo.domain

import java.net.URI

data class QuickMemoLink(
    val url: String,
    val source: String,
    val caption: String,
    val dedupKey: String,
) {
    val title: String get() = "${source}收藏"
    val body: String get() = if (caption.isBlank()) url else "$caption\n$url"
}

/** 只处理用户提供的文字；不联网，不从网址推测标题，不按域名子串误认来源。 */
object QuickMemoLinkParser {
    private val urls = Regex("""https?://[^\s<>"“”]+""", RegexOption.IGNORE_CASE)

    fun parse(text: String): QuickMemoLink? {
        for (match in urls.findAll(text)) {
            val url = match.value.trimEnd('。', '，', '！', '？', '）', '】', '》', '.', ',', '!', ';')
            val uri = runCatching { URI(url) }.getOrNull() ?: continue
            val host = uri.host?.lowercase() ?: continue
            if (uri.userInfo != null || uri.scheme.lowercase() !in setOf("http", "https")) continue
            val rule = QuickMemoLinkRules.platforms.firstOrNull { host in it.hosts }
            val source = rule?.name ?: "链接"
            val rawCaption = text.substring(0, match.range.first).trim().replace(Regex("\\s+"), " ")
            val caption = rule?.captionCleanup.orEmpty().fold(rawCaption) { current, pattern ->
                current.replace(pattern, "")
            }
            val contentId = rule?.dedupPath?.matchEntire(uri.path.orEmpty())?.groupValues?.getOrNull(1)
            val authority = host + if (uri.port == -1 ||
                uri.scheme.equals("https", true) && uri.port == 443 ||
                uri.scheme.equals("http", true) && uri.port == 80) "" else ":${uri.port}"
            val key = contentId?.let { "${rule.dedupNamespace}:$it" } ?: buildString {
                append(uri.scheme.lowercase()); append("://"); append(authority)
                append(uri.rawPath.orEmpty().ifEmpty { "/" })
                uri.rawQuery?.let { append('?'); append(it) }
                uri.rawFragment?.let { append('#'); append(it) }
            }
            return QuickMemoLink(url, source, caption.trim(), key)
        }
        return null
    }
}
