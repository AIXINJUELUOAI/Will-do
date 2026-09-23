// Ported from AutoAccounting 4e707980; see docs/third-party/autoaccounting/README.md.
package com.antgskds.calendarassistant.platform.xposed.upstream.dex.model

data class ClazzMethod(
    val name: String = "",
    val returnType: String = "",
    val modifiers: String = "",
    val parameters: List<ClazzField> = listOf(),
    val regex: String = "",
    val strings: List<String> = listOf(),
    val findName: String = "",
)
