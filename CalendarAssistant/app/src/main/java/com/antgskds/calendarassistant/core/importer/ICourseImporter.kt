package com.antgskds.calendarassistant.core.importer

interface ICourseImporter {
    /**
     * 判断当前导入器是否支持解析该内容
     */
    fun supports(content: String): Boolean

    /**
     * 执行解析
     */
    suspend fun parse(content: String): Result<ImportResult>
}
