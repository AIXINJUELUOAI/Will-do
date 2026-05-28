package com.antgskds.calendarassistant.core.instantcode

object InstantCodeRoute {
    fun shouldUseInstantCodePrompt(text: String): Boolean {
        if (text.isBlank()) return false
        return InstantCodeParser.parseClipboard(text, InstantCodeParseMode.CLIPBOARD_AUTO) != null
    }
}
