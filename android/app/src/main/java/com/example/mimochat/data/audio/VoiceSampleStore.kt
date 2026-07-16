package com.example.mimochat.data.audio

import android.content.Context
import android.util.Base64
import java.io.File

/** Stores generated voice samples outside the serialized role preferences. */
class VoiceSampleStore(context: Context) {
    companion object {
        private const val REFERENCE_PREFIX = "file:"
    }

    private val root = File(context.filesDir, "mimo-voice-samples")

    fun save(roleId: String, dataUrl: String): String {
        val encoded = dataUrl.substringAfter(',', "")
        require(encoded.isNotBlank()) { "语音样本格式无效" }
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        val name = roleId.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".wav"
        root.mkdirs()
        val target = File(root, name)
        val temp = File(root, ".${name}.tmp-${System.nanoTime()}")
        temp.writeBytes(bytes)
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
        return REFERENCE_PREFIX + name
    }

    /** Returns a data URL for the TTS API; legacy data URLs remain supported. */
    fun resolve(reference: String?): String? {
        if (reference.isNullOrBlank()) return null
        if (!reference.startsWith(REFERENCE_PREFIX)) return reference

        val name = reference.removePrefix(REFERENCE_PREFIX)
        if (name.isBlank() || name != File(name).name || name.contains("..")) return null
        val file = File(root, name)
        if (!file.isFile) return null
        return "data:audio/wav;base64," + Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
    }
}
