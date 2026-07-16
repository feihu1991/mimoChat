package com.example.mimochat.core.voice

import android.content.Context
import android.util.Base64
import com.example.mimochat.data.Role
import java.io.File
import java.security.MessageDigest

/**
 * Stores generated message audio, temporary voice-design candidates and the
 * selected persistent voice-clone sample inside app-private storage.
 */
class VoiceAudioStore(context: Context) {
    private val messageRoot = File(context.filesDir, "voice/messages").apply { mkdirs() }
    private val roleRoot = File(context.filesDir, "voice/roles").apply { mkdirs() }
    private val designRoot = File(context.cacheDir, "voice-design").apply { mkdirs() }

    fun messageFingerprint(text: String, role: Role): String {
        val sampleFingerprint = role.voiceSample.orEmpty().let { sample ->
            when {
                sample.isBlank() -> ""
                sample.startsWith("data:") -> sha256(sample.toByteArray())
                else -> {
                    val file = File(sample)
                    if (file.isFile) sha256(file.readBytes()) else sha256(sample.toByteArray())
                }
            }
        }
        return sha256(
            listOf(
                text,
                role.voiceModel.apiName,
                role.voiceName,
                role.voicePrompt.orEmpty(),
                sampleFingerprint
            ).joinToString("\u0000").toByteArray()
        )
    }

    fun messageAudioFile(messageId: String, fingerprint: String): File =
        File(messageRoot, "${safeName(messageId)}-${fingerprint.take(24)}.wav")

    fun deleteMessageAudio(messageId: String) {
        val prefix = "${safeName(messageId)}-"
        messageRoot.listFiles()?.filter { it.name.startsWith(prefix) }?.forEach(File::delete)
    }

    fun saveMessageAudio(target: File, bytes: ByteArray): File {
        writeAtomic(target, bytes)
        return target
    }

    fun clearDesignCandidates(roleId: String) {
        File(designRoot, safeName(roleId)).deleteRecursively()
    }

    fun saveDesignCandidate(roleId: String, index: Int, bytes: ByteArray): File {
        val directory = File(designRoot, safeName(roleId)).apply { mkdirs() }
        val target = File(directory, "candidate-${index + 1}.wav")
        writeAtomic(target, bytes)
        return target
    }

    fun commitDesignCandidate(roleId: String, candidatePath: String): File {
        val source = File(candidatePath)
        require(source.isFile) { "试听音色文件不存在" }
        val target = File(roleRoot, "${safeName(roleId)}.wav")
        writeAtomic(target, source.readBytes())
        return target
    }

    fun voiceSampleDataUrl(sample: String?): String? {
        if (sample.isNullOrBlank()) return null
        if (sample.startsWith("data:")) return sample

        val file = File(sample)
        require(file.isFile) { "固定音色样本不存在，请重新设计或录入音色" }
        val mimeType = when (file.extension.lowercase()) {
            "mp3" -> "audio/mpeg"
            else -> "audio/wav"
        }
        val encoded = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        return "data:$mimeType;base64,$encoded"
    }

    private fun writeAtomic(target: File, bytes: ByteArray) {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, ".${target.name}.tmp")
        temp.writeBytes(bytes)
        if (!temp.renameTo(target)) {
            target.writeBytes(bytes)
            temp.delete()
        }
    }

    private fun safeName(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120)

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
