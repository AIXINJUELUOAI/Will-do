package com.antgskds.calendarassistant.ui.page_display.settings

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun DonatePage(
    uiSize: Int = 2,
    settingsViewModel: SettingsViewModel? = null
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    var confettiTrigger by remember { mutableIntStateOf(0) }
    var showConfetti by remember { mutableStateOf(false) }

    // 用于保存按钮在屏幕上的中心坐标
    var buttonCenter by remember { mutableStateOf<Offset?>(null) }

    // 用于保存当前被放大的二维码图片资源 ID
    var enlargedImageRes by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(confettiTrigger) {
        if (confettiTrigger <= 0) return@LaunchedEffect
        showConfetti = true
        delay(1100)
        showConfetti = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "如果觉得软件不错，请开发者喝瓶可乐吧",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DonateQrCard(
                    title = "支付宝",
                    subtitle = "Alipay",
                    imageRes = R.drawable.qr_alipay,
                    modifier = Modifier.weight(1f)
                ) {
                    enlargedImageRes = R.drawable.qr_alipay
                }

                DonateQrCard(
                    title = "微信",
                    subtitle = "WeChat Pay",
                    imageRes = R.drawable.qr_wechat_pay,
                    modifier = Modifier.weight(1f)
                ) {
                    enlargedImageRes = R.drawable.qr_wechat_pay
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    confettiTrigger += 1
                    // 保存捐赠状态
                    settingsViewModel?.updateHasDonated(true)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .sizeIn(maxWidth = 420.dp)
                    // 核心：动态获取按钮在屏幕中的绝对坐标
                    .onGloballyPositioned { coordinates ->
                        buttonCenter = coordinates.boundsInRoot().center
                    },
                contentPadding = PaddingValues(vertical = 14.dp)
            ) {
                Text(
                    text = "\uD83C\uDF89感谢捐赠！",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (showConfetti) {
            ConfettiBurst(
                trigger = confettiTrigger,
                originOffset = buttonCenter, // 将按钮坐标传给彩带组件
                modifier = Modifier
                    .fillMaxSize()
                    // 提升层级，防止被滚动视图截断
                    .zIndex(2f)
            )
        }
    }

    // ========== 放大二维码的 Dialog 保持不变 ==========
    if (enlargedImageRes != null) {
        Dialog(
            onDismissRequest = { enlargedImageRes = null },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { enlargedImageRes = null },
                            onLongPress = {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                saveImageToGallery(context, enlargedImageRes!!)
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = painterResource(id = enlargedImageRes!!),
                        contentDescription = "Enlarged QR Code",
                        modifier = Modifier
                            .fillMaxWidth(0.75f)
                            .aspectRatio(1f),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        text = "长按二维码保存到相册",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

// ========== 捐赠卡片和保存图片的方法保持不变 ==========

@Composable
private fun DonateQrCard(
    title: String,
    subtitle: String,
    imageRes: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))
            Image(
                painter = painterResource(id = imageRes),
                contentDescription = title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
            )
        }
    }
}

private fun saveImageToGallery(context: Context, imageResId: Int) {
    try {
        val bitmap = BitmapFactory.decodeResource(context.resources, imageResId)
        val filename = "donate_qr_${System.currentTimeMillis()}.png"
        var fos: OutputStream? = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver?.also { resolver ->
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                }
                val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (imageUri != null) {
                    fos = resolver.openOutputStream(imageUri)
                }
            }
        } else {
            val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            if (!imagesDir.exists()) imagesDir.mkdirs()
            val image = File(imagesDir, filename)
            fos = FileOutputStream(image)
        }

        fos?.use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            Toast.makeText(context, "已成功保存到相册", Toast.LENGTH_SHORT).show()
        } ?: run {
            Toast.makeText(context, "保存失败：无法访问存储", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

// ======================== 全新升级的 360 度派对礼炮特效 ========================

private data class ConfettiParticle(
    val vx: Float,
    val vy: Float,
    val size: Dp,
    val color: Color,
    val rotationSpeed: Float,
    val isCircle: Boolean
)

@Composable
private fun ConfettiBurst(
    trigger: Int,
    originOffset: Offset?, // 接收来自按钮的坐标
    modifier: Modifier = Modifier
) {
    val palette = listOf(
        Color(0xFFFFC107), Color(0xFF4CAF50), Color(0xFF03A9F4),
        Color(0xFFE91E63), Color(0xFFFF5722), Color(0xFF9C27B0)
    )

    // 生成爆炸粒子
    val particles = remember(trigger) {
        List(110) { // 增加粒子数量让爆炸更华丽
            val angle = Random.nextDouble(0.0, 2 * PI).toFloat()
            // 速度随机分布，打造层次感
            val speed = Random.nextFloat() * 1.5f + 0.6f
            ConfettiParticle(
                vx = cos(angle) * speed,
                // sin(angle) 原本是各向同性，减去 0.8f 赋予其向上的爆发趋势，更像拉响的礼炮
                vy = sin(angle) * speed - 0.8f,
                size = Random.nextInt(6, 14).dp,
                color = palette[Random.nextInt(palette.size)],
                rotationSpeed = (Random.nextFloat() - 0.5f) * 6f,
                isCircle = Random.nextBoolean() // 一半圆形一半矩形，视觉更丰富
            )
        }
    }

    val progress = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 1100))
    }

    Canvas(modifier = modifier) {
        val t = progress.value
        if (t <= 0f) return@Canvas

        // 使用传递进来的按钮坐标；如果没有获取到则退化为屏幕中心点
        val origin = originOffset ?: Offset(size.width / 2f, size.height / 2f)

        // travel 为抛射距离基准，调整这个参数控制散开的大小范围
        val travel = minOf(size.width, size.height) * 0.45f
        // 重力系数：拉大重力让彩片最后具有非常自然的下坠感
        val gravity = travel * 2.5f

        // 后期渐隐特效
        val alpha = ((1f - t) * 1.5f).coerceIn(0f, 1f)

        particles.forEach { p ->
            // 计算 X 和 Y 坐标 (受初始速度、扩散范围和重力影响)
            val x = origin.x + (p.vx * t * travel)
            val y = origin.y + (p.vy * t * travel) + (0.5f * gravity * t * t)

            val w = p.size.toPx()
            val h = (p.size * 0.6f).toPx()

            if (p.isCircle) {
                drawCircle(
                    color = p.color,
                    radius = w / 2f,
                    center = Offset(x, y),
                    alpha = alpha
                )
            } else {
                rotate(degrees = p.rotationSpeed * t * 360f, pivot = Offset(x, y)) {
                    drawRect(
                        color = p.color,
                        topLeft = Offset(x - w / 2f, y - h / 2f),
                        size = Size(w, h),
                        alpha = alpha
                    )
                }
            }
        }
    }
}