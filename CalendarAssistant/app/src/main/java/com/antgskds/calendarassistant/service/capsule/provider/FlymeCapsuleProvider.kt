package com.antgskds.calendarassistant.service.capsule.provider

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.MainActivity
import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.service.capsule.CapsuleService
import com.antgskds.calendarassistant.service.capsule.CapsuleUiUtils

/**
 * Flyme 实况胶囊提供者
 *
 * 第二阶段修复：使用 RemoteViews 加载自定义布局，解决通知中心展开空白问题
 */
class FlymeCapsuleProvider : ICapsuleProvider {

    companion object {
        private const val TAG = "FlymeCapsuleProvider"
    }

    override fun buildNotification(
        context: Context,
        eventId: String,
        title: String,
        content: String,
        color: Int,
        capsuleType: Int,  // 1=日程, 2=取件码
        eventType: String  // 事件类型：event=日程, temp=取件码, course=课程
    ): Notification {

        // 1. 点击跳转
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            eventId.hashCode(),
            tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 2. 准备图标 (Flyme 要求白色 Bitmap)
        var iconDrawable: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_notification_small)
        if (iconDrawable == null) iconDrawable = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)

        val rawBitmap = iconDrawable?.let { CapsuleUiUtils.drawableToBitmap(it) }
        val whiteIconBitmap = if (rawBitmap != null) CapsuleUiUtils.tintBitmap(rawBitmap, Color.WHITE) else null
        val iconObj = whiteIconBitmap?.let { Icon.createWithBitmap(it) }

        // 3. 创建 RemoteViews（关键修复）
        val remoteViews = createRemoteViews(context, capsuleType, eventType, title, content)

        // 4. 构建 Notification Builder
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, App.CHANNEL_ID_LIVE)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }

        val collapsedTitle = if (title.length > 10) title.take(10) else title
        val icon = Icon.createWithResource(context, R.drawable.ic_notification_small)

        // 5. 设置基础属性
        builder.setSmallIcon(icon)
            .setContentTitle(collapsedTitle)
            .setContentText("进行中")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setColor(color)
            .setCategory(Notification.CATEGORY_EVENT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)

        // 6. ✅ 关键：设置 RemoteViews（双重设置）
        builder.setCustomContentView(remoteViews)
            .setCustomBigContentView(remoteViews)

        // 7. ✅ 保留 BigTextStyle 作为兜底
        builder.setStyle(Notification.BigTextStyle()
            .setBigContentTitle(title)
            .bigText(content)
        )

        // 8. Android 版本适配
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }

        builder.setGroup("LIVE_CAPSULE_GROUP")
            .setGroupSummary(false)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)

        // Android 16 (Baklava) 适配
        try {
            val methodSetText = Notification.Builder::class.java.getMethod("setShortCriticalText", String::class.java)
            methodSetText.invoke(builder, collapsedTitle)
        } catch (e: Exception) {
            // ignore
        }

        try {
            val methodSetPromoted = Notification.Builder::class.java.getMethod("setRequestPromotedOngoing", Boolean::class.java)
            methodSetPromoted.invoke(builder, true)
        } catch (e: Exception) {
            // ignore
        }

        // 9. 构建 Flyme 专属 Bundle（胶囊信息）
        val capsuleBundle = Bundle().apply {
            putInt("notification.live.capsuleStatus", 1)
            putInt("notification.live.capsuleType", 1)
            putString("notification.live.capsuleContent", collapsedTitle)
            putInt("notification.live.capsuleBgColor", color)
            putInt("notification.live.capsuleContentColor", Color.WHITE)
            if (iconObj != null) {
                putParcelable("notification.live.capsuleIcon", iconObj)
            }
        }

        // 10. ✅ 添加 Extras（扁平化结构 + String 类型修正）
        val finalExtras = Bundle().apply {
            putBoolean("is_live", true)
            putInt("notification.live.operation", 0)
            putInt("notification.live.type", 10)
            putBundle("notification.live.capsule", capsuleBundle)
            // ❌ 移除 notification.live.contentColor（让 XML 控制颜色）
            // ✅ String 类型（防止崩溃）
            putString("android.substName", context.getString(R.string.app_name))
            putString("android.title", title)
        }
        builder.addExtras(finalExtras)

        builder.setOnlyAlertOnce(true)

        return builder.build()
    }

    /**
     * 创建 RemoteViews
     * 封装 Flyme 实况通知的自定义布局逻辑
     */
    private fun createRemoteViews(
        context: Context,
        capsuleType: Int,
        eventType: String,  // 事件类型：event=日程, temp=取件码, course=课程
        title: String,
        content: String
    ): RemoteViews {
        return RemoteViews(context.packageName, R.layout.notification_live_flyme).apply {

            // 主标题：绑定 title
            setTextViewText(R.id.tv_main_content, title)

            // 副标题：地点 + 时间
            val subInfo = if (content.isNotEmpty()) {
                "$content • 进行中"
            } else {
                "进行中"
            }
            setTextViewText(R.id.tv_sub_info, subInfo)

            // 图标：根据 eventType 区分
            val iconRes = when (eventType) {
                "temp" -> R.drawable.ic_capsule_pickup      // 取件/取餐
                "course" -> R.drawable.ic_capsule_course    // 课程
                "event" -> R.drawable.ic_capsule_event      // 普通日程
                else -> R.drawable.ic_capsule_event         // 默认
            }
            setImageViewResource(R.id.iv_icon, iconRes)
        }
    }
}
