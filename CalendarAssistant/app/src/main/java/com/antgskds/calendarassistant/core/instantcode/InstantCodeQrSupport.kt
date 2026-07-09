package com.antgskds.calendarassistant.core.instantcode

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.EnumMap
import kotlin.coroutines.resume

object InstantCodeQrSupport {
    private const val TAG = "InstantCodeQrSupport"

    private val scanner by lazy {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        BarcodeScanning.getClient(options)
    }

    suspend fun scanQrPayloads(bitmap: Bitmap): List<String> = suspendCancellableCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val payloads = barcodes
                    .mapNotNull { it.rawValue?.trim()?.takeIf(String::isNotBlank) }
                    .distinct()
                if (continuation.isActive) continuation.resume(payloads)
            }
            .addOnFailureListener { error ->
                Log.w(TAG, "二维码识别失败", error)
                if (continuation.isActive) continuation.resume(emptyList())
            }
            .addOnCanceledListener {
                if (continuation.isActive) continuation.resume(emptyList())
            }
    }

    fun createQrBitmap(payload: String, sizePx: Int = 768): Bitmap? {
        val clean = payload.trim()
        if (clean.isBlank() || sizePx <= 0) return null
        return runCatching {
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
                put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
                put(EncodeHintType.MARGIN, 2)
            }
            val matrix = MultiFormatWriter().encode(clean, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val pixels = IntArray(sizePx * sizePx)
            for (y in 0 until sizePx) {
                for (x in 0 until sizePx) {
                    pixels[y * sizePx + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
                }
            }
            Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
            }
        }.getOrNull()
    }
}
