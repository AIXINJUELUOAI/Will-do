package com.antgskds.calendarassistant.feature.linkanalysis.domain

data class LinkSummaryPresentation(val markdown: String, val warnings: List<String>)

/** 同一规则用于详情与导出；只移动明确标为限制的段落，正文中的条件不受影响。 */
object LinkSummaryPresentationPolicy {
    private val label = Regex("^\\s*(?:#{1,6}\\s*)?(?:\\*\\*)?(?:已知限制|限制说明|素材限制)(?:\\*\\*)?\\s*(?:[：:](?:\\*\\*)?\\s*(.*)|$)")
    private fun normalized(value: String) = value.replace(Regex("[\\s\\p{P}\\p{S}]+"), "")

    fun present(data: LinkSummaryData): LinkSummaryPresentation {
        val warnings = data.warnings.toMutableList()
        val content = mutableListOf<String>()
        var collecting = false
        var waitingForParagraph = false
        for (line in data.summary.lines()) {
            val match = label.matchEntire(line)
            if (match != null) {
                collecting = true
                waitingForParagraph = match.groupValues[1].isBlank()
                warnings += match.groupValues[1].split('；', ';').map(String::trim).filter(String::isNotBlank)
            } else if (collecting && waitingForParagraph && line.isBlank()) {
                continue
            } else if (collecting && line.isNotBlank() && !line.trimStart().startsWith('#')) {
                waitingForParagraph = false
                warnings += line.split('；', ';').map { it.trim().removePrefix("- ").removePrefix("* ") }.filter(String::isNotBlank)
            } else {
                collecting = false
                content += line
            }
        }
        val unique = warnings.map(String::trim).filter(String::isNotBlank).distinctBy(::normalized)
        return LinkSummaryPresentation(content.joinToString("\n").trim(), unique)
    }
}
