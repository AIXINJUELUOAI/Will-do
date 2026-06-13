package com.antgskds.calendarassistant.data.model

import kotlinx.serialization.Serializable

@Serializable
data class RemoteAppUpdateInfo(
    val versioncode: Int = 0,
    val versions: List<RemoteAppVersion> = emptyList()
) {
    fun isValid(): Boolean {
        return versioncode > 0 && versions.isNotEmpty()
    }
}

@Serializable
data class RemoteAppVersion(
    val versionname: String = "",
    val downloadUrl: String = "",
    val downloadPassword: String = "",
    val sections: List<RemoteAppUpdateSection> = emptyList()
)

@Serializable
data class RemoteAppUpdateSection(
    val title: String = "",
    val items: List<String> = emptyList()
)
