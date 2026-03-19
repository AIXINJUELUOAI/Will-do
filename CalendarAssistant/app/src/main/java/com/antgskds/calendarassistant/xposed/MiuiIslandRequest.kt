package com.antgskds.calendarassistant.xposed

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle

data class MiuiIslandAction(
    val title: String,
    val pendingIntent: PendingIntent
) {
    fun toBundle(): Bundle = Bundle().apply {
        putString(KEY_TITLE, title)
        putParcelable(KEY_INTENT, pendingIntent)
    }

    companion object {
        private const val KEY_TITLE = "action_title"
        private const val KEY_INTENT = "action_intent"

        fun fromBundle(bundle: Bundle): MiuiIslandAction? {
            val title = bundle.getString(KEY_TITLE) ?: return null
            val intent = if (Build.VERSION.SDK_INT >= 33) {
                bundle.getParcelable(KEY_INTENT, PendingIntent::class.java)
            } else {
                @Suppress("DEPRECATION") bundle.getParcelable(KEY_INTENT)
            }
            return if (intent == null) null else MiuiIslandAction(title, intent)
        }
    }
}

/**
 * 超级岛展示请求，跨进程通过 Intent extras 传递。
 */
data class MiuiIslandRequest(
    val title: String,
    val content: String,
    val icon: Icon? = null,
    val iconDark: Icon? = null,
    val appIcon: Icon? = null,
    val summaryStatus: String? = null,
    val summaryTitle: String? = null,
    val notifId: Int = MiuiIslandDispatcher.NOTIF_ID,
    val timeoutSecs: Int = 5,
    val firstFloat: Boolean = true,
    val enableFloat: Boolean = true,
    val showNotification: Boolean = true,
    val highlightColor: String? = null,
    val dismissIsland: Boolean = false,
    val contentIntent: PendingIntent? = null,
    val actions: List<MiuiIslandAction> = emptyList(),
    val templateType: Int = TEMPLATE_TEXT_ICON,
    val tagText: String? = null,
    val hintTitle: String? = null,
    val actionTitle: String? = null,
    val actionIntentUri: String? = null,
) {
    fun toBundle(): Bundle = Bundle().apply {
        putString(KEY_TITLE, title)
        putString(KEY_CONTENT, content)
        putParcelable(KEY_ICON, icon)
        putParcelable(KEY_ICON_DARK, iconDark)
        putParcelable(KEY_APP_ICON, appIcon)
        putString(KEY_SUMMARY_STATUS, summaryStatus)
        putString(KEY_SUMMARY_TITLE, summaryTitle)
        putInt(KEY_NOTIF_ID, notifId)
        putInt(KEY_TIMEOUT, timeoutSecs)
        putBoolean(KEY_FIRST_FLOAT, firstFloat)
        putBoolean(KEY_ENABLE_FLOAT, enableFloat)
        putBoolean(KEY_SHOW_NOTIF, showNotification)
        putString(KEY_HIGHLIGHT, highlightColor)
        putBoolean(KEY_DISMISS, dismissIsland)
        putParcelable(KEY_CONTENT_INTENT, contentIntent)
        val actionBundles = ArrayList<Bundle>(actions.size)
        actions.forEach { actionBundles.add(it.toBundle()) }
        putParcelableArrayList(KEY_ACTIONS, actionBundles)
        putInt(KEY_TEMPLATE, templateType)
        putString(KEY_TAG_TEXT, tagText)
        putString(KEY_HINT_TITLE, hintTitle)
        putString(KEY_ACTION_TITLE, actionTitle)
        putString(KEY_ACTION_INTENT, actionIntentUri)
    }

    companion object {
        const val TEMPLATE_TEXT_ICON = 2
        const val TEMPLATE_TEXT_ICON_ACTION = 10

        private const val KEY_TITLE = "title"
        private const val KEY_CONTENT = "content"
        private const val KEY_ICON = "icon"
        private const val KEY_ICON_DARK = "iconDark"
        private const val KEY_APP_ICON = "appIcon"
        private const val KEY_SUMMARY_STATUS = "summaryStatus"
        private const val KEY_SUMMARY_TITLE = "summaryTitle"
        private const val KEY_NOTIF_ID = "notifId"
        private const val KEY_TIMEOUT = "timeoutSecs"
        private const val KEY_FIRST_FLOAT = "firstFloat"
        private const val KEY_ENABLE_FLOAT = "enableFloat"
        private const val KEY_SHOW_NOTIF = "showNotification"
        private const val KEY_HIGHLIGHT = "highlightColor"
        private const val KEY_DISMISS = "dismissIsland"
        private const val KEY_CONTENT_INTENT = "contentIntent"
        private const val KEY_ACTIONS = "actions"
        private const val KEY_TEMPLATE = "templateType"
        private const val KEY_TAG_TEXT = "tagText"
        private const val KEY_HINT_TITLE = "hintTitle"
        private const val KEY_ACTION_TITLE = "actionTitle"
        private const val KEY_ACTION_INTENT = "actionIntentUri"

        fun fromBundle(bundle: Bundle): MiuiIslandRequest = MiuiIslandRequest(
            title = bundle.getString(KEY_TITLE, ""),
            content = bundle.getString(KEY_CONTENT, ""),
            icon = iconFromBundle(bundle),
            iconDark = iconDarkFromBundle(bundle),
            appIcon = appIconFromBundle(bundle),
            summaryStatus = bundle.getString(KEY_SUMMARY_STATUS),
            summaryTitle = bundle.getString(KEY_SUMMARY_TITLE),
            notifId = bundle.getInt(KEY_NOTIF_ID, MiuiIslandDispatcher.NOTIF_ID),
            timeoutSecs = bundle.getInt(KEY_TIMEOUT, 5),
            firstFloat = bundle.getBoolean(KEY_FIRST_FLOAT, true),
            enableFloat = bundle.getBoolean(KEY_ENABLE_FLOAT, true),
            showNotification = bundle.getBoolean(KEY_SHOW_NOTIF, true),
            highlightColor = bundle.getString(KEY_HIGHLIGHT),
            dismissIsland = bundle.getBoolean(KEY_DISMISS, false),
            contentIntent = pendingIntentFromBundle(bundle),
            actions = actionsFromBundle(bundle),
            templateType = bundle.getInt(KEY_TEMPLATE, TEMPLATE_TEXT_ICON),
            tagText = bundle.getString(KEY_TAG_TEXT),
            hintTitle = bundle.getString(KEY_HINT_TITLE),
            actionTitle = bundle.getString(KEY_ACTION_TITLE),
            actionIntentUri = bundle.getString(KEY_ACTION_INTENT),
        )

        private fun iconFromBundle(bundle: Bundle): Icon? =
            if (Build.VERSION.SDK_INT >= 33) {
                bundle.getParcelable(KEY_ICON, Icon::class.java)
            } else {
                @Suppress("DEPRECATION") bundle.getParcelable(KEY_ICON)
            }

        private fun iconDarkFromBundle(bundle: Bundle): Icon? =
            if (Build.VERSION.SDK_INT >= 33) {
                bundle.getParcelable(KEY_ICON_DARK, Icon::class.java)
            } else {
                @Suppress("DEPRECATION") bundle.getParcelable(KEY_ICON_DARK)
            }

        private fun appIconFromBundle(bundle: Bundle): Icon? =
            if (Build.VERSION.SDK_INT >= 33) {
                bundle.getParcelable(KEY_APP_ICON, Icon::class.java)
            } else {
                @Suppress("DEPRECATION") bundle.getParcelable(KEY_APP_ICON)
            }

        private fun pendingIntentFromBundle(bundle: Bundle): PendingIntent? =
            if (Build.VERSION.SDK_INT >= 33) {
                bundle.getParcelable(KEY_CONTENT_INTENT, PendingIntent::class.java)
            } else {
                @Suppress("DEPRECATION") bundle.getParcelable(KEY_CONTENT_INTENT)
            }

        private fun actionsFromBundle(bundle: Bundle): List<MiuiIslandAction> {
            val bundles = if (Build.VERSION.SDK_INT >= 33) {
                bundle.getParcelableArrayList(KEY_ACTIONS, Bundle::class.java)
            } else {
                @Suppress("DEPRECATION") bundle.getParcelableArrayList(KEY_ACTIONS)
            } ?: return emptyList()
            return bundles.mapNotNull { MiuiIslandAction.fromBundle(it) }
        }

        fun fromIntent(intent: Intent): MiuiIslandRequest = fromBundle(intent.extras ?: Bundle())
    }
}
