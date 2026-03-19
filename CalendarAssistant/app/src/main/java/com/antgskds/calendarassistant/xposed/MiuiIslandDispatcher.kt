package com.antgskds.calendarassistant.xposed

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Log
import io.github.d4viddf.hyperisland_kit.HyperAction
import io.github.d4viddf.hyperisland_kit.HyperIslandNotification
import io.github.d4viddf.hyperisland_kit.HyperPicture
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoLeft
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoRight
import io.github.d4viddf.hyperisland_kit.models.PicInfo
import io.github.d4viddf.hyperisland_kit.models.TextInfo

/**
 * SystemUI 进程内超级岛发送调度器。
 */
object MiuiIslandDispatcher {

    const val ACTION = "com.antgskds.calendarassistant.ACTION_SHOW_MIUI_ISLAND"
    const val PERM = "com.antgskds.calendarassistant.SEND_ISLAND"
    const val NOTIF_ID = 0x57494C4C // "WILL"

    private const val CHANNEL_ID = "calendar_assistant_island_silent_v2"
    private const val CHANNEL_NAME = "超级岛"
    private const val TAG = "CalendarAssistant[MiuiDispatcher]"
    private const val PIC_KEY_EVENT = "miui.focus.pic_event"
    private const val PIC_KEY_APP = "miui.focus.pic_app"
    private const val ACTION_KEY_PREFIX = "miui.focus.action_"

    @Volatile private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION) return
            try {
                val request = MiuiIslandRequest.fromIntent(intent)
                post(context.applicationContext ?: context, request)
            } catch (e: Exception) {
                Log.w(TAG, "onReceive error: ${e.message}")
            }
        }
    }

    fun register(context: Context) {
        if (registered) return
        val appCtx = context.applicationContext ?: context
        createChannel(appCtx)
        val filter = IntentFilter(ACTION)
        if (Build.VERSION.SDK_INT >= 33) {
            appCtx.registerReceiver(receiver, filter, PERM, null, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            appCtx.registerReceiver(receiver, filter, PERM, null)
        }
        registered = true
        Log.d(TAG, "registered in pid=${android.os.Process.myPid()}")
    }

    fun post(context: Context, request: MiuiIslandRequest) {
        try {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            createChannel(context)

            val content = request.content.ifBlank { request.title }
            val eventIcon = request.icon ?: Icon.createWithResource(context, android.R.drawable.sym_def_app_icon)
            val appIcon = request.appIcon ?: eventIcon

            val builder = HyperIslandNotification.Builder(context, "calendar_assistant_island", request.title)
            builder.addPicture(HyperPicture(PIC_KEY_EVENT, eventIcon))
            builder.addPicture(HyperPicture(PIC_KEY_APP, appIcon))

            builder.setIconTextInfo(
                picKey = PIC_KEY_EVENT,
                title = request.title,
                content = content,
            )
            builder.setIslandFirstFloat(request.firstFloat)
            builder.setEnableFloat(request.enableFloat)
            builder.setShowNotification(request.showNotification)
            builder.setIslandConfig(timeout = request.timeoutSecs)

            builder.setSmallIsland(PIC_KEY_EVENT)
            builder.setBigIslandInfo(
                left = ImageTextInfoLeft(
                    type = 1,
                    picInfo = PicInfo(type = 1, pic = PIC_KEY_EVENT),
                    textInfo = TextInfo(title = request.summaryStatus?.ifBlank { null } ?: request.title),
                ),
                right = ImageTextInfoRight(
                    type = 2,
                    textInfo = TextInfo(
                        title = request.summaryTitle?.ifBlank { null } ?: content,
                        narrowFont = true
                    ),
                ),
            )

            val actions = request.actions.take(2)
            val actionKeys = actions.mapIndexed { index, _ -> "$ACTION_KEY_PREFIX${index + 1}" }
            if (actions.isNotEmpty() && request.showNotification) {
                val hyperActions = actions.mapIndexed { index, action ->
                    HyperAction(
                        key = actionKeys[index],
                        title = action.title,
                        pendingIntent = action.pendingIntent,
                        actionIntentType = 2,
                    )
                }
                hyperActions.forEach { builder.addHiddenAction(it) }
                builder.setTextButtons(*hyperActions.toTypedArray())
            }

            val resourceBundle = builder.buildResourceBundle()

            val notif = Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(request.title)
                .setContentText(content)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setCategory(Notification.CATEGORY_EVENT)
                .setShowWhen(true)
                .apply { request.contentIntent?.let { setContentIntent(it) } }
                .build()

            notif.extras.putAll(resourceBundle)
            ensureFocusPics(notif.extras, eventIcon, appIcon)
            flattenActionsToExtras(resourceBundle, notif.extras)

            val jsonParam = builder.buildJsonParam()
                .let { buildOfficialTemplateParam(it, request, PIC_KEY_EVENT, PIC_KEY_APP, actionKeys) }
                .let { injectIslandAppearance(it, request.highlightColor, request.dismissIsland) }
            notif.extras.putString("miui.focus.param", jsonParam)

            nm.cancel(request.notifId)
            nm.notify(request.notifId, notif)

            if (request.dismissIsland && !request.showNotification) {
                nm.cancel(request.notifId)
            }

            Log.d(
                TAG,
                "posted: ${request.title} | ${request.content} | actions=${actions.size}" +
                    " | highlight=${request.highlightColor} | dismiss=${request.dismissIsland}"
            )
        } catch (e: Exception) {
            Log.w(TAG, "post error: ${e.message}")
        }
    }

    fun sendBroadcast(context: Context, request: MiuiIslandRequest) {
        val intent = Intent(ACTION).apply {
            putExtras(request.toBundle())
        }
        context.sendBroadcast(intent)
    }

    private fun injectIslandAppearance(
        jsonParam: String,
        highlightColor: String?,
        dismissIsland: Boolean,
    ): String {
        if (highlightColor == null && !dismissIsland) return jsonParam
        return try {
            val json = org.json.JSONObject(jsonParam)
            val pv2 = json.optJSONObject("param_v2") ?: return jsonParam
            val paramIsland = pv2.optJSONObject("param_island") ?: org.json.JSONObject()
            highlightColor?.let { paramIsland.put("highlightColor", it) }
            if (dismissIsland) paramIsland.put("dismissIsland", true)
            pv2.put("param_island", paramIsland)
            json.toString()
        } catch (_: Exception) {
            jsonParam
        }
    }

    private fun fixTextButtonJson(jsonParam: String): String {
        return try {
            val json = org.json.JSONObject(jsonParam)
            val pv2 = json.optJSONObject("param_v2") ?: return jsonParam
            val btns = pv2.optJSONArray("textButton") ?: return jsonParam
            for (i in 0 until btns.length()) {
                val btn = btns.getJSONObject(i)
                val key = btn.optString("actionIntent").takeIf { it.isNotEmpty() } ?: continue
                btn.put("action", key)
                btn.remove("actionIntent")
                btn.remove("actionIntentType")
            }
            json.toString()
        } catch (_: Exception) {
            jsonParam
        }
    }

    private fun buildOfficialTemplateParam(
        jsonParam: String,
        request: MiuiIslandRequest,
        eventPicKey: String,
        appPicKey: String,
        actionKeys: List<String>
    ): String {
        return try {
            val json = org.json.JSONObject(jsonParam)
            val pv2 = json.optJSONObject("param_v2") ?: org.json.JSONObject().also {
                json.put("param_v2", it)
            }

            val baseInfo = buildBaseInfo(request, eventPicKey)
            pv2.put("baseInfo", baseInfo)
            pv2.put("picInfo", buildPicInfo(appPicKey, appPicKey))
            pv2.remove("textButton")

            if (request.templateType == MiuiIslandRequest.TEMPLATE_TEXT_ICON_ACTION && actionKeys.isNotEmpty()) {
                pv2.put("hintInfo", buildHintInfo(request, actionKeys.first()))
            } else {
                pv2.remove("hintInfo")
            }

            json.toString()
        } catch (_: Exception) {
            jsonParam
        }
    }

    private fun buildBaseInfo(request: MiuiIslandRequest, functionPicKey: String?): org.json.JSONObject {
        val safeTitle = request.title.ifBlank { request.content.ifBlank { "提醒" } }
        val safeContent = request.content.ifBlank { safeTitle }
        return org.json.JSONObject().apply {
            put("type", 2)
            put("title", safeTitle)
            put("content", safeContent)
            if (request.templateType == MiuiIslandRequest.TEMPLATE_TEXT_ICON_ACTION && functionPicKey != null) {
                put("picFunction", functionPicKey)
            }
        }
    }

    private fun buildPicInfo(picKey: String, picDarkKey: String): org.json.JSONObject {
        return org.json.JSONObject().apply {
            put("type", 1)
            put("pic", picKey)
            put("picDark", picDarkKey)
        }
    }

    private fun buildHintInfo(
        request: MiuiIslandRequest,
        actionKey: String
    ): org.json.JSONObject {
        val hintTitle = request.hintTitle?.ifBlank { null }
            ?: request.content.ifBlank { request.title }
        val actionIntentUri = request.actionIntentUri?.takeIf { it.isNotBlank() }
        val actionTitle = request.actionTitle?.takeIf { it.isNotBlank() } ?: "操作"

        return org.json.JSONObject().apply {
            put("type", 1)
            put("title", hintTitle)
            request.tagText?.takeIf { it.isNotBlank() }?.let { tagText ->
                put("content", tagText)
                normalizeArgb(request.highlightColor)?.let { put("colorContentBg", it) }
            }
            put("actionInfo", org.json.JSONObject().apply {
                if (actionIntentUri != null) {
                    put("actionTitle", actionTitle)
                    put("actionIntentType", 2)
                    put("actionIntent", actionIntentUri)
                } else {
                    put("action", actionKey)
                }
            })
        }
    }

    private fun normalizeArgb(color: String?): String? {
        val value = color?.trim()?.takeIf { it.startsWith("#") } ?: return null
        return when (value.length) {
            7 -> "#FF" + value.substring(1)
            9 -> value
            else -> null
        }
    }

    private fun ensureFocusPics(extras: Bundle, eventIcon: Icon, appIcon: Icon) {
        val pics = extras.getBundle("miui.focus.pics") ?: Bundle().also {
            extras.putBundle("miui.focus.pics", it)
        }
        pics.putParcelable(PIC_KEY_EVENT, eventIcon)
        pics.putParcelable(PIC_KEY_APP, appIcon)
    }


    private fun flattenActionsToExtras(resourceBundle: Bundle, extras: Bundle) {
        val nested = resourceBundle.getBundle("miui.focus.actions") ?: return
        for (key in nested.keySet()) {
            val action: Notification.Action? = if (Build.VERSION.SDK_INT >= 33) {
                nested.getParcelable(key, Notification.Action::class.java)
            } else {
                @Suppress("DEPRECATION") nested.getParcelable(key)
            }
            if (action != null) extras.putParcelable(key, action)
        }
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
        )
    }
}
