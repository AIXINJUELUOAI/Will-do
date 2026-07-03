package com.antgskds.calendarassistant.feature.appearance.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

data class AppBackgroundImportResult(
    val path: String
)

class AppBackgroundImageStore(context: Context) {
    private val appContext = context.applicationContext
    private val backgroundDir: File = File(appContext.filesDir, "theme/background")

    fun importBackground(uri: Uri, oldPath: String?): AppBackgroundImportResult {
        val bitmap = decodeScaledBitmap(uri)
        backgroundDir.mkdirs()

        val target = File(backgroundDir, "app_background_${System.currentTimeMillis()}.jpg")
        FileOutputStream(target).use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, OUTPUT_QUALITY, output)) {
                error("无法保存图片")
            }
        }
        bitmap.recycle()

        deleteOwnedBackground(oldPath, exceptPath = target.absolutePath)
        cleanupOldBackgrounds(keepPath = target.absolutePath)

        return AppBackgroundImportResult(
            path = target.absolutePath
        )
    }

    fun extractSeedColorHex(path: String): String {
        val file = ownedBackgroundFile(path)?.takeIf { it.exists() } ?: error("背景图片不存在")
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: error("无法读取背景图片")
        return try {
            extractSeedColorHex(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    fun clearBackground(path: String?) {
        deleteOwnedBackground(path, exceptPath = null)
    }

    private fun decodeScaledBitmap(uri: Uri): Bitmap {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }

        val sampleSize = calculateSampleSize(options.outWidth, options.outHeight)
        val decodeOptions = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = sampleSize
        }
        val decoded = appContext.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, decodeOptions)
        } ?: error("无法读取图片")

        val maxSide = max(decoded.width, decoded.height)
        if (maxSide <= MAX_IMAGE_SIDE) return decoded

        val scale = MAX_IMAGE_SIDE.toFloat() / maxSide.toFloat()
        val scaledWidth = (decoded.width * scale).toInt().coerceAtLeast(1)
        val scaledHeight = (decoded.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(decoded, scaledWidth, scaledHeight, true)
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    private fun calculateSampleSize(width: Int, height: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var sampleSize = 1
        var currentWidth = width
        var currentHeight = height
        while (max(currentWidth, currentHeight) / 2 >= MAX_IMAGE_SIDE) {
            sampleSize *= 2
            currentWidth /= 2
            currentHeight /= 2
        }
        return sampleSize
    }

    private fun extractSeedColorHex(bitmap: Bitmap): String {
        val stepX = (bitmap.width / COLOR_SAMPLE_GRID).coerceAtLeast(1)
        val stepY = (bitmap.height / COLOR_SAMPLE_GRID).coerceAtLeast(1)
        var redTotal = 0.0
        var greenTotal = 0.0
        var blueTotal = 0.0
        var weightTotal = 0.0

        var y = stepY / 2
        while (y < bitmap.height) {
            var x = stepX / 2
            while (x < bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val alpha = Color.alpha(pixel)
                if (alpha >= MIN_ALPHA) {
                    val hsv = FloatArray(3)
                    Color.colorToHSV(pixel, hsv)
                    val saturation = hsv[1]
                    val value = hsv[2]
                    if (saturation >= MIN_SATURATION && value in MIN_VALUE..MAX_VALUE) {
                        val weight = (saturation * alpha / 255f).toDouble()
                        redTotal += Color.red(pixel) * weight
                        greenTotal += Color.green(pixel) * weight
                        blueTotal += Color.blue(pixel) * weight
                        weightTotal += weight
                    }
                }
                x += stepX
            }
            y += stepY
        }

        if (weightTotal <= 0.0) {
            return DEFAULT_SEED_COLOR
        }

        val red = (redTotal / weightTotal).toInt().coerceIn(0, 255)
        val green = (greenTotal / weightTotal).toInt().coerceIn(0, 255)
        val blue = (blueTotal / weightTotal).toInt().coerceIn(0, 255)
        return "#%02X%02X%02X".format(red, green, blue)
    }

    private fun deleteOwnedBackground(path: String?, exceptPath: String?) {
        val file = ownedBackgroundFile(path) ?: return
        if (exceptPath != null && file.absolutePath == exceptPath) return
        file.delete()
    }

    private fun cleanupOldBackgrounds(keepPath: String) {
        backgroundDir.listFiles()?.forEach { file ->
            if (file.isFile && file.absolutePath != keepPath) {
                file.delete()
            }
        }
    }

    private fun ownedBackgroundFile(path: String?): File? {
        if (path.isNullOrBlank()) return null
        val canonicalDir = backgroundDir.canonicalFile
        val file = File(path).canonicalFile
        return file.takeIf {
            it.path == canonicalDir.path || it.path.startsWith(canonicalDir.path + File.separator)
        }
    }

    private companion object {
        private const val MAX_IMAGE_SIDE = 1920
        private const val OUTPUT_QUALITY = 90
        private const val COLOR_SAMPLE_GRID = 48
        private const val MIN_ALPHA = 180
        private const val MIN_SATURATION = 0.12f
        private const val MIN_VALUE = 0.18f
        private const val MAX_VALUE = 0.95f
        private const val DEFAULT_SEED_COLOR = "#6750A4"
    }
}
