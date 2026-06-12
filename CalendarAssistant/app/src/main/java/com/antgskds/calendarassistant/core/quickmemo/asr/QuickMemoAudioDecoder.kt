package com.antgskds.calendarassistant.core.quickmemo.asr

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.nio.ByteBuffer
import kotlin.math.max

data class DecodedAudio(
    val samples: FloatArray,
    val sampleRate: Int
)

class QuickMemoAudioDecoder {
    fun decodeToFloatSamples(audioPath: String): DecodedAudio {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(audioPath)
            val trackIndex = findAudioTrack(extractor)
            require(trackIndex >= 0) { "未找到音频轨道" }

            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME).orEmpty()
            require(mime.startsWith("audio/")) { "不支持的音频格式" }
            extractor.selectTrack(trackIndex)

            var sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
            val builder = FloatArrayBuilder()
            val bufferInfo = MediaCodec.BufferInfo()
            val decoder = MediaCodec.createDecoderByType(mime).also { codec = it }

            decoder.configure(inputFormat, null, null, 0)
            decoder.start()

            var sawInputEnd = false
            var sawOutputEnd = false
            while (!sawOutputEnd) {
                if (!sawInputEnd) {
                    val inputBufferIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputBufferIndex)
                        inputBuffer?.clear()
                        val sampleSize = if (inputBuffer != null) extractor.readSampleData(inputBuffer, 0) else -1
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(
                                inputBufferIndex,
                                0,
                                0,
                                0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            sawInputEnd = true
                        } else {
                            decoder.queueInputBuffer(
                                inputBufferIndex,
                                0,
                                sampleSize,
                                extractor.sampleTime.coerceAtLeast(0L),
                                0
                            )
                            extractor.advance()
                        }
                    }
                }

                when (val outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outputFormat = decoder.outputFormat
                        if (outputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (outputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            channelCount = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                        }
                        if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            pcmEncoding = outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        }
                    }
                    else -> if (outputBufferIndex >= 0) {
                        if (bufferInfo.size > 0) {
                            decoder.getOutputBuffer(outputBufferIndex)?.let { outputBuffer ->
                                appendPcmBuffer(
                                    buffer = outputBuffer,
                                    offset = bufferInfo.offset,
                                    size = bufferInfo.size,
                                    channelCount = channelCount,
                                    pcmEncoding = pcmEncoding,
                                    builder = builder
                                )
                            }
                        }
                        sawOutputEnd = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        decoder.releaseOutputBuffer(outputBufferIndex, false)
                    }
                }
            }

            val samples = builder.toFloatArray()
            require(samples.isNotEmpty()) { "音频内容为空" }
            return DecodedAudio(samples = samples, sampleRate = sampleRate)
        } finally {
            codec?.runCatchingStopAndRelease()
            extractor.release()
        }
    }

    private fun findAudioTrack(extractor: MediaExtractor): Int {
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith("audio/")) return index
        }
        return -1
    }

    private fun appendPcmBuffer(
        buffer: ByteBuffer,
        offset: Int,
        size: Int,
        channelCount: Int,
        pcmEncoding: Int,
        builder: FloatArrayBuilder
    ) {
        buffer.position(offset)
        buffer.limit(offset + size)
        val data = ByteArray(size)
        buffer.get(data)
        when (pcmEncoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> appendPcmFloat(data, channelCount, builder)
            else -> appendPcm16(data, channelCount, builder)
        }
    }

    private fun appendPcm16(data: ByteArray, channelCount: Int, builder: FloatArrayBuilder) {
        val frameSize = max(channelCount, 1) * 2
        var index = 0
        while (index + frameSize <= data.size) {
            var sum = 0f
            for (channel in 0 until channelCount) {
                val byteIndex = index + channel * 2
                val low = data[byteIndex].toInt() and 0xFF
                val high = data[byteIndex + 1].toInt()
                val sample = (high shl 8) or low
                sum += sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()) / 32768f
            }
            builder.add((sum / channelCount).coerceIn(-1f, 1f))
            index += frameSize
        }
    }

    private fun appendPcmFloat(data: ByteArray, channelCount: Int, builder: FloatArrayBuilder) {
        val frameSize = max(channelCount, 1) * 4
        var index = 0
        while (index + frameSize <= data.size) {
            var sum = 0f
            for (channel in 0 until channelCount) {
                val byteIndex = index + channel * 4
                val bits = (data[byteIndex].toInt() and 0xFF) or
                    ((data[byteIndex + 1].toInt() and 0xFF) shl 8) or
                    ((data[byteIndex + 2].toInt() and 0xFF) shl 16) or
                    ((data[byteIndex + 3].toInt() and 0xFF) shl 24)
                sum += Float.fromBits(bits)
            }
            builder.add((sum / channelCount).coerceIn(-1f, 1f))
            index += frameSize
        }
    }

    private fun MediaCodec.runCatchingStopAndRelease() {
        runCatching { stop() }
        runCatching { release() }
    }

    private class FloatArrayBuilder(initialCapacity: Int = 16_000) {
        private var data = FloatArray(initialCapacity)
        private var size = 0

        fun add(value: Float) {
            if (size == data.size) data = data.copyOf(data.size * 2)
            data[size++] = value
        }

        fun toFloatArray(): FloatArray = data.copyOf(size)
    }

    companion object {
        private const val TIMEOUT_US = 10_000L
    }
}
