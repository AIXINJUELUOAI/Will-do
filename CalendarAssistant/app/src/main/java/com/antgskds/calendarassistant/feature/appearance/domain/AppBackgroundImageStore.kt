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
    val path: String,
    val averageLuminance: Float
)

class AppBackgroundImageStore(context: Context) {
    private val appContext = context.applicationContext
    private val backgroundDir: File = File(appContext.filesDir, "theme/background")

    fun importBackground(uri: Uri, oldPath: String?): AppBackgroundImportResult {
        val bitmap = decodeScaledBitmap(uri)
        val averageLuminance = extractAverageLuminance(bitmap)
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
            path = target.absolutePath,
            averageLuminance = averageLuminance
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

    fun extractAverageLuminance(path: String): Float {
        val file = ownedBackgroundFile(path)?.takeIf { it.exists() } ?: error("背景图片不存在")
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: error("无法读取背景图片")
        return try {
            extractAverageLuminance(bitmap)
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

    private fun extractAverageLuminance(bitmap: Bitmap): Float {
        val stepX = (bitmap.width / LUMINANCE_SAMPLE_GRID).coerceAtLeast(1)
        val stepY = (bitmap.height / LUMINANCE_SAMPLE_GRID).coerceAtLeast(1)
        val samples = ArrayList<Double>(LUMINANCE_SAMPLE_GRID * LUMINANCE_SAMPLE_GRID)
        val regionTotals = DoubleArray(LUMINANCE_REGION_GRID * LUMINANCE_REGION_GRID)
        val regionCounts = IntArray(LUMINANCE_REGION_GRID * LUMINANCE_REGION_GRID)
        var total = 0.0
        var count = 0
        var y = stepY / 2
        while (y < bitmap.height) {
            var x = stepX / 2
            while (x < bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                if (Color.alpha(pixel) >= MIN_ALPHA) {
                    val luminance = pixelLuminance(pixel)
                    val regionIndex = luminanceRegionIndex(bitmap, x, y)
                    samples += luminance
                    regionTotals[regionIndex] += luminance
                    regionCounts[regionIndex]++
                    total += luminance
                    count++
                }
                x += stepX
            }
            y += stepY
        }
        if (count == 0) return -1f

        val average = total / count
        val sortedSamples = samples.sorted()
        val sampleMedian = percentile(sortedSamples, 0.50)
        val regionLuminance = regionTotals.indices.mapNotNull { index ->
            val regionCount = regionCounts[index]
            if (regionCount == 0) null else regionTotals[index] / regionCount
        }.sorted()
        val regionMedian = percentile(regionLuminance, 0.50)
        val darkSampleRatio = sortedSamples.count { it <= DARK_LUMINANCE_THRESHOLD }.toDouble() / sortedSamples.size
        val darkRegionRatio = regionLuminance.count { it <= DARK_REGION_LUMINANCE_THRESHOLD }.toDouble() / regionLuminance.size

        val robustLuminance = when {
            darkRegionRatio >= DARK_REGION_DOMINANT_RATIO || darkSampleRatio >= DARK_SAMPLE_DOMINANT_RATIO -> {
                minOf(average, sampleMedian, regionMedian)
            }
            darkRegionRatio >= DARK_REGION_MIXED_RATIO && regionMedian < 0.52 -> {
                minOf(sampleMedian, regionMedian)
            }
            else -> average * 0.35 + sampleMedian * 0.35 + regionMedian * 0.30
        }
        return robustLuminance.toFloat().coerceIn(0f, 1f)
    }

    private fun pixelLuminance(pixel: Int): Double {
        return (0.299 * Color.red(pixel) + 0.587 * Color.green(pixel) + 0.114 * Color.blue(pixel)) / 255.0
    }

    private fun luminanceRegionIndex(bitmap: Bitmap, x: Int, y: Int): Int {
        val regionX = ((x.toFloat() / bitmap.width) * LUMINANCE_REGION_GRID).toInt()
            .coerceIn(0, LUMINANCE_REGION_GRID - 1)
        val regionY = ((y.toFloat() / bitmap.height) * LUMINANCE_REGION_GRID).toInt()
            .coerceIn(0, LUMINANCE_REGION_GRID - 1)
        return regionY * LUMINANCE_REGION_GRID + regionX
    }

    private fun percentile(sortedValues: List<Double>, ratio: Double): Double {
        if (sortedValues.isEmpty()) return -1.0
        val index = ((sortedValues.size - 1) * ratio).toInt().coerceIn(0, sortedValues.lastIndex)
        return sortedValues[index]
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
        private const val LUMINANCE_SAMPLE_GRID = 64
        private const val LUMINANCE_REGION_GRID = 3
        private const val DARK_LUMINANCE_THRESHOLD = 0.42
        private const val DARK_REGION_LUMINANCE_THRESHOLD = 0.48
        private const val DARK_SAMPLE_DOMINANT_RATIO = 0.45
        private const val DARK_REGION_DOMINANT_RATIO = 0.50
        private const val DARK_REGION_MIXED_RATIO = 0.34
        private const val MIN_ALPHA = 180
        private const val MIN_SATURATION = 0.12f
        private const val MIN_VALUE = 0.18f
        private const val MAX_VALUE = 0.95f
        private const val DEFAULT_SEED_COLOR = "#6750A4"
    }
}
