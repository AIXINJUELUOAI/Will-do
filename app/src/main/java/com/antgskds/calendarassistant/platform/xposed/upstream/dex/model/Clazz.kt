// Ported from AutoAccounting 4e707980; see docs/third-party/autoaccounting/README.md.
package com.antgskds.calendarassistant.platform.xposed.upstream.dex.model

data class Clazz(
    val name: String = "",
    val fields: List<ClazzField> = listOf(),
    val methods: List<ClazzMethod> = listOf(),
    val nameRule: String = "",
    val type: String = "",
    val strings: List<String> = listOf(),
)
