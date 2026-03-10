package com.antgskds.calendarassistant.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

object ImageImportUtils {
    private const val TAG = "ImageImportUtils"

    fun createImportedImageFile(
        context: Context,
        prefix: String = "UPLOAD_",
        extension: String = "jpg"
    ): File {
        val imagesDir = File(context.filesDir, "event_screenshots")
        if (!imagesDir.exists()) imagesDir.mkdirs()
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return File(imagesDir, "$prefix$timestamp.$extension")
    }

    fun copyUriToFile(context: Context, uri: Uri, dest: File): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            } ?: return false
            true
        } catch (e: Exception) {
            Log.e(TAG, "copyUriToFile failed", e)
            false
        }
    }

    fun decodeSampledBitmapFromFile(file: File, maxSide: Int = 1600): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)

            val outW = bounds.outWidth
            val outH = bounds.outHeight
            if (outW <= 0 || outH <= 0) return null

            val options = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(outW, outH, maxSide)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
        } catch (e: Exception) {
            Log.e(TAG, "decodeSampledBitmapFromFile failed", e)
            null
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxSide: Int): Int {
        var inSampleSize = 1
        val maxDim = max(width, height)
        while (maxDim / inSampleSize > maxSide) {
            inSampleSize *= 2
        }
        return inSampleSize.coerceAtLeast(1)
    }
}
