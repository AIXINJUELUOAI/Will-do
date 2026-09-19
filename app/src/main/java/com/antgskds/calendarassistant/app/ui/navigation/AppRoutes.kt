package com.antgskds.calendarassistant.app.ui.navigation

object AppRoutes {
    const val Home = "home"
    const val AccountingPreview = "accounting_preview/{date}/{period}"
    fun accountingPreview(date: java.time.LocalDate, monthly: Boolean): String =
        "accounting_preview/$date/${if (monthly) "MONTH" else "DAY"}"

    const val OnboardingGuide = "onboarding_guide"
    const val WeatherDetail = "weather_detail"
    // 普通便签已下线：仅供恢复旧导航栈后返回首页，不提供新的导航入口。
    private const val NoteEditorBase = "note_editor"
    const val NoteEditorArg = "noteId"
    const val NoteEditorPattern = "$NoteEditorBase/{$NoteEditorArg}"
    const val QuickMemoDetailBase = "quick_memo_detail"
    const val QuickMemoDetailArg = "memoId"
    const val QuickMemoDetailPattern = "$QuickMemoDetailBase/{$QuickMemoDetailArg}"

    const val SettingsBase = "settings"
    const val SettingsTypeArg = "type"
    const val SettingsPattern = "$SettingsBase/{$SettingsTypeArg}"

    fun settings(destinationName: String): String = "$SettingsBase/$destinationName"

    fun quickMemoDetail(memoId: Long): String = "$QuickMemoDetailBase/$memoId"
}
