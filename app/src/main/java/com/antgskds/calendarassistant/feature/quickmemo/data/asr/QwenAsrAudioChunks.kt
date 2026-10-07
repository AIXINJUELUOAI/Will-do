package com.antgskds.calendarassistant.feature.quickmemo.data.asr

import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog

internal object QwenAsrAudioChunks {
    /** Ranges are contiguous and end-exclusive. Prefer a quiet boundary near the length limit. */
    fun ranges(samples: FloatArray, sampleRate: Int): List<IntRange> {
        require(sampleRate > 0)
        val maximum = sampleRate * ConfigCatalog.QWEN_ASR_CHUNK_SECONDS
        val search = sampleRate * ConfigCatalog.QWEN_ASR_BOUNDARY_SEARCH_SECONDS
        val window = (sampleRate.toLong() * ConfigCatalog.QWEN_ASR_BOUNDARY_WINDOW_MS / 1000).toInt().coerceAtLeast(1)
        val result = mutableListOf<IntRange>()
        var start = 0
        while (start < samples.size) {
            var end = minOf(start + maximum, samples.size)
            if (end < samples.size) {
                var lowestEnergy = Double.POSITIVE_INFINITY
                for (candidate in (end - search)..end step window) {
                    var energy = 0.0
                    for (index in (candidate - window) until candidate) energy += samples[index].toDouble() * samples[index]
                    if (energy <= lowestEnergy) {
                        lowestEnergy = energy
                        end = candidate
                    }
                }
            }
            result += start until end
            start = end
        }
        return result
    }
}
