package com.antgskds.calendarassistant.ui.navigation

object AppRoutes {
    const val Home = "home"
    const val WeatherDetail = "weather_detail"

    const val NoteEditorBase = "note_editor"
    const val NoteEditorArg = "noteId"
    const val NoteEditorPattern = "$NoteEditorBase/{$NoteEditorArg}"
    const val NoteEditorNewArg = -1L

    const val SettingsBase = "settings"
    const val SettingsTypeArg = "type"
    const val SettingsPattern = "$SettingsBase/{$SettingsTypeArg}"

    fun noteEditor(noteId: Long?): String = "$NoteEditorBase/${noteId ?: NoteEditorNewArg}"

    fun settings(destinationName: String): String = "$SettingsBase/$destinationName"
}
