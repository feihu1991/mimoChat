package com.example.mimochat.data.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var output: File? = null

    fun start() {
        check(recorder == null) { "录音已经开始" }
        val target = File(context.cacheDir, "voice-${System.currentTimeMillis()}.m4a")
        val current = createRecorder()
        try {
            current.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16_000)
                setAudioEncodingBitRate(64_000)
                setOutputFile(target.absolutePath)
                prepare()
                start()
            }
            output = target
            recorder = current
        } catch (e: Exception) {
            current.release()
            target.delete()
            throw e
        }
    }

    fun stop(): File? {
        val current = recorder ?: return null
        return try {
            current.stop()
            output
        } catch (_: RuntimeException) {
            output?.delete()
            null
        } finally {
            current.release()
            recorder = null
            output = null
        }
    }

    fun cancel() {
        recorder?.release()
        recorder = null
        output?.delete()
        output = null
    }

    @Suppress("DEPRECATION")
    private fun createRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
}
