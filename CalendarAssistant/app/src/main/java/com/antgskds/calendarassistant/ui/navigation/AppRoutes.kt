package com.antgskds.calendarassistant.ui.navigation

object AppRoutes {
    const val Home = "home"
    const val WeatherDetail = "weather_detail"
    const val NoteEditorBase = "note_editor"
    const val NoteEditorArg = "noteId"
    const val NoteEditorNewArg = -1L
    const val NoteEditorPattern = "$NoteEditorBase/{$NoteEditorArg}"

    const val SettingsBase = "settings"
    const val SettingsTypeArg = "type"
    const val SettingsPattern = "$SettingsBase/{$SettingsTypeArg}"

    fun settings(destinationName: String): String = "$SettingsBase/$destinationName"

    fun noteEditor(noteId: Long = NoteEditorNewArg): String = "$NoteEditorBase/$noteId"
}
