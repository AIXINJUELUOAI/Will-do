package com.antgskds.calendarassistant.platform.linkanalysis

import android.media.*
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog as Limits
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteOrder

/** Streaming decode to bounded mono PCM WAVs; never holds a complete long track in memory. */
object LinkAudioChunks {
    suspend fun decode(file: File, directory: File): List<File> {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var writer: RandomAccessFile? = null
        val files = mutableListOf<File>()
        var rate = 16000; var channels = 1; var encoding = AudioFormat.ENCODING_PCM_16BIT
        var frames = 0; var allFrames = 0L
        val targetRate = 16000
        var sourceFrame = 0L; var nextOutput = 0.0; var previous = 0.0
        fun finish() {
            writer?.let { out ->
                val size = (out.length() - 44).toInt()
                out.seek(0)
                out.writeBytes("RIFF"); writeInt(out,size+36); out.writeBytes("WAVEfmt "); writeInt(out,16)
                writeShort(out,1); writeShort(out,1); writeInt(out,targetRate); writeInt(out,targetRate*2)
                writeShort(out,2); writeShort(out,16); out.writeBytes("data"); writeInt(out,size)
                out.close()
            }
            writer = null; frames = 0
        }
        fun append(sample: Int) {
            if (writer == null) {
                val next = File(directory, "audio-" + files.size + ".wav")
                files += next
                writer = RandomAccessFile(next,"rw").also { it.setLength(0); it.write(ByteArray(44)) }
            }
            writeShort(writer!!,sample.coerceIn(-32768,32767))
            frames++; allFrames++
            require(allFrames * 1000 / targetRate <= Limits.LINK_AUDIO_MAX_MS) { "音频超过处理时长上限" }
            if (frames.toLong()*1000/targetRate >= Limits.LINK_AUDIO_CHUNK_MS) finish()
        }
        try {
            extractor.setDataSource(file.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ?: error("素材没有可解码的音轨")
            val format = extractor.getTrackFormat(track)
            if (format.containsKey(MediaFormat.KEY_DURATION)) require(format.getLong(MediaFormat.KEY_DURATION)/1000 <= Limits.LINK_AUDIO_MAX_MS) { "音频超过处理时长上限" }
            extractor.selectTrack(track)
            val decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec = decoder; decoder.configure(format,null,null,0); decoder.start()
            var inputEnd = false; var outputEnd = false
            val info = MediaCodec.BufferInfo()
            while (!outputEnd) {
                currentCoroutineContext().ensureActive()
                if (!inputEnd) {
                    val index = decoder.dequeueInputBuffer(10_000)
                    if (index >= 0) {
                        val buffer = decoder.getInputBuffer(index)!!
                        val size = extractor.readSampleData(buffer,0)
                        if (size < 0) { decoder.queueInputBuffer(index,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputEnd=true }
                        else { decoder.queueInputBuffer(index,0,size,extractor.sampleTime.coerceAtLeast(0),0); extractor.advance() }
                    }
                }
                val index = decoder.dequeueOutputBuffer(info,10_000)
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    finish()
                    val output = decoder.outputFormat
                    rate = output.getInteger(MediaFormat.KEY_SAMPLE_RATE); channels = output.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    encoding = if (output.containsKey(MediaFormat.KEY_PCM_ENCODING)) output.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                    require(rate in 8000..192000 && channels in 1..8 && encoding in setOf(AudioFormat.ENCODING_PCM_16BIT,AudioFormat.ENCODING_PCM_FLOAT)) { "音轨格式不支持" }
                } else if (index >= 0) {
                    try {
                        val output = decoder.getOutputBuffer(index)!!.order(ByteOrder.LITTLE_ENDIAN)
                        output.position(info.offset); output.limit(info.offset+info.size)
                        val bytes = if (encoding == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
                        while (output.remaining() >= bytes*channels) {
                            var sample = 0.0
                            repeat(channels) { sample += if (encoding == AudioFormat.ENCODING_PCM_FLOAT) output.float.coerceIn(-1f,1f)*32767.0 else output.short.toDouble() }
                            val mono = sample/channels
                            while (nextOutput <= sourceFrame) {
                                val fraction = (nextOutput - (sourceFrame - 1)).coerceIn(0.0,1.0)
                                append((previous + (mono-previous)*fraction).toInt())
                                nextOutput += rate.toDouble()/targetRate
                            }
                            previous=mono; sourceFrame++
                        }
                        outputEnd = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    } finally { decoder.releaseOutputBuffer(index,false) }
                }
            }
            finish()
            require(files.isNotEmpty()) { "音轨内容为空" }
            return files
        } finally {
            writer?.close()
            codec?.let { runCatching { it.stop() }; it.release() }
            extractor.release()
        }
    }
    private fun writeInt(out: RandomAccessFile,value: Int) { repeat(4) { out.write((value ushr (it*8)) and 255) } }
    private fun writeShort(out: RandomAccessFile,value: Int) { out.write(value and 255); out.write((value ushr 8) and 255) }
}
