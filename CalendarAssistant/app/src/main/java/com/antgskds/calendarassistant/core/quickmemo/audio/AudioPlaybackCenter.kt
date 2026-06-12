package com.antgskds.calendarassistant.core.quickmemo.audio

import android.media.MediaPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AudioPlaybackState(
    val audioPath: String? = null,
    val isPlaying: Boolean = false
)

class AudioPlaybackCenter {
    private val _playbackState = MutableStateFlow(AudioPlaybackState())
    val playbackState: StateFlow<AudioPlaybackState> = _playbackState.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null

    fun toggle(audioPath: String?) {
        val path = audioPath?.takeIf { it.isNotBlank() } ?: return
        val current = _playbackState.value
        if (current.audioPath == path && current.isPlaying) {
            pause()
        } else {
            play(path)
        }
    }

    fun play(audioPath: String) {
        stop()
        val player = MediaPlayer().apply {
            setDataSource(audioPath)
            setOnCompletionListener { this@AudioPlaybackCenter.stop() }
            setOnErrorListener { _, _, _ ->
                this@AudioPlaybackCenter.stop()
                true
            }
            prepare()
            start()
        }
        mediaPlayer = player
        _playbackState.value = AudioPlaybackState(audioPath = audioPath, isPlaying = true)
    }

    fun pause() {
        val player = mediaPlayer ?: return
        runCatching { player.pause() }
        _playbackState.value = _playbackState.value.copy(isPlaying = false)
    }

    fun stop() {
        val player = mediaPlayer
        mediaPlayer = null
        if (player != null) {
            runCatching { if (player.isPlaying) player.stop() }
            runCatching { player.reset() }
            runCatching { player.release() }
        }
        _playbackState.value = AudioPlaybackState()
    }
}
