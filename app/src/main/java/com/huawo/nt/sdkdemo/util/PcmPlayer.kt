package com.huawo.nt.sdkdemo.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.io.FileInputStream

/**
 * Minimal streaming PCM player for AI watchface debug audio.
 *
 * ## Purpose
 * Plays raw PCM files produced by AiCenter / AFlash voice recording
 * (typically `{AiCenter.workspace}/record.pcm`).
 *
 * ## Expected format (must match the recorder)
 * - Sample rate: **16000 Hz** (default)
 * - Channels: **mono** ([AudioFormat.CHANNEL_OUT_MONO])
 * - Encoding: **PCM 16-bit** ([AudioFormat.ENCODING_PCM_16BIT])
 *
 * Playing with mismatched parameters will sound garbled or silent — this is not an encoded
 * WAV/MP3 file; there is no header, only interleaved PCM samples.
 *
 * ## Threading / lifecycle
 * - Playback runs on a background thread; [stop] is safe to call from the main thread.
 * - Always call [stop] when leaving the screen to release [AudioTrack].
 * - Calling [playPcm] again stops any previous playback first.
 *
 * Used by [com.huawo.nt.sdkdemo.ui.watchface.AiWatchfaceFragment].
 */
class PcmPlayer {
    @Volatile
    private var playing = false
    private var audioTrack: AudioTrack? = null
    private var playThread: Thread? = null

    /**
     * Stream [filePath] to the speaker.
     *
     * @param sampleRate Must match the file (AI recorder uses 16000).
     * @param channelConfig Output channel mask (mono for AI PCM).
     * @param audioFormat Encoding (PCM16 for AI PCM).
     */
    fun playPcm(
        filePath: String,
        sampleRate: Int = 16_000,
        channelConfig: Int = AudioFormat.CHANNEL_OUT_MONO,
        audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT,
    ) {
        stop()
        playing = true
        val bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val track =
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(audioFormat)
                        .setChannelMask(channelConfig)
                        .build(),
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        audioTrack = track

        playThread =
            Thread {
                try {
                    FileInputStream(filePath).use { fis ->
                        val buffer = ByteArray(bufferSize)
                        track.play()
                        while (playing) {
                            val read = fis.read(buffer)
                            if (read <= 0) break
                            track.write(buffer, 0, read)
                        }
                    }
                } catch (_: Exception) {
                    // Demo: swallow IO / AudioTrack errors; check logcat if playback fails silently.
                } finally {
                    stop()
                }
            }.also { it.start() }
    }

    /** Stop playback and release the AudioTrack (idempotent). */
    fun stop() {
        playing = false
        try {
            audioTrack?.stop()
        } catch (_: Exception) {
        }
        try {
            audioTrack?.release()
        } catch (_: Exception) {
        }
        audioTrack = null
        playThread = null
    }
}
