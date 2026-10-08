package com.antgskds.calendarassistant.feature.linkanalysis.domain

import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import java.net.URI

object LinkAnalysisPolicy {
    fun coversVideo(audioMs: Long?, videoMs: Long?): Boolean {
        if(audioMs==null || videoMs==null || videoMs<=0) return true
        return audioMs + com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog.LINK_AUDIO_DURATION_TOLERANCE_MS >=
            videoMs / 100 * com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog.LINK_AUDIO_MIN_COVERAGE_PERCENT
    }
    fun preferredAudio(media: List<LinkResultMedia>): LinkResultMedia? {
        val speech=media.firstOrNull { it.type=="audio" && it.role=="speech_audio" }
        val video=media.firstOrNull { it.type=="video" && it.role=="video" }
        return if(speech!=null && coversVideo(speech.durationMs,video?.durationMs)) speech else video
    }

    fun browserUserAgent(value: String?): String? = value?.also {
        require(it.isNotBlank() && it.length <= com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog.LINK_BROWSER_UA_MAX_CHARS &&
            !it.contains('\r') && !it.contains('\n'))
    }

    fun enabled(settings: MySettings) = settings.linkAnalysisEnabled
    fun matches(manifest: LinkSourceManifest, url: String): Boolean = runCatching {
        val host = LinkSourceProtocol.host(url)
        val path = URI(url).path.orEmpty().ifBlank { "/" }
        manifest.matches.any { LinkSourceProtocol.matchesHost(it.host, host) && path.startsWith(it.pathPrefix) }
    }.getOrDefault(false)
    fun allows(manifest: LinkSourceManifest, url: String) = runCatching {
        val host = LinkSourceProtocol.host(url)
        manifest.permissions.networkHosts.any { LinkSourceProtocol.matchesHost(it, host) }
    }.getOrDefault(false)
}
