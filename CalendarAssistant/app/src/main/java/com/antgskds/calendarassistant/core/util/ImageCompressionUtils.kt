package com.antgskds.calendarassistant.core.util

import android.graphics.Bitmap
import android.util.Log
import java.io.ByteArrayOutputStream

object ImageCompressionUtils {
    private const val TAG = "ImageCompression"

    /**
     * 为 AI 多模态识别优化图片
     *
     * 策略：
     * - 最大边长限制为 1600px（保持宽度 ~720px，避免小字模糊）
     * - JPEG 质量 75%（文字识别最佳平衡点）
     *
     * @return 压缩后的 JPEG 字节数组
     */
    fun compressForAiRecognition(bitmap: Bitmap): ByteArray {
        val startTime = System.currentTimeMillis()

        // 1. 尺寸压缩
        val resizedBitmap = resizeBitmap(bitmap, maxSize = 1600)

        // 2. 质量压缩
        val outputStream = ByteArrayOutputStream()
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
        val result = outputStream.toByteArray()

        // 3. 如果调整后不是原图，回收临时 bitmap
        if (resizedBitmap != bitmap) {
            resizedBitmap.recycle()
        }

        val elapsed = System.currentTimeMillis() - startTime
        Log.d(TAG, "图片压缩完成: ${bitmap.width}x${bitmap.height} -> ${result.size} bytes (${elapsed}ms)")

        return result
    }

    /**
     * 按比例缩放 bitmap，使最长边不超过 maxSize
     */
    private fun resizeBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        // 如果图片已经足够小，直接返回
        if (width <= maxSize && height <= maxSize) {
            return bitmap
        }

        // 计算缩放比例
        val scale = if (width > height) {
            maxSize.toFloat() / width
        } else {
            maxSize.toFloat() / height
        }

        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}
