package com.example.mimochat.ui.main

import android.content.Context
import android.media.MediaPlayer
import android.util.Base64
import com.example.mimochat.data.Role
import com.example.mimochat.data.VoiceChatState
import com.example.mimochat.data.VoiceModel
import com.example.mimochat.data.audio.VoiceRecorder
import com.example.mimochat.data.audio.VoiceSampleStore
import com.example.mimochat.data.local.SettingsStorage
import com.example.mimochat.data.remote.MimoClient
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 语音能力控制器：TTS 合成播放（角色试听、消息朗读）与录音转写。
 *
 * 负责 [VoiceChatState] / [speakingId] / [isGeneratingVoice] 等语音相关状态，
 * 通过回调与宿主 ViewModel 解耦（toast、角色读写）。
 */
class VoiceController(
    private val context: Context,
    private val settingsStorage: SettingsStorage,
    private val voiceRecorder: VoiceRecorder,
    private val voiceSampleStore: VoiceSampleStore,
    private val scope: CoroutineScope,
    private val showToast: (String) -> Unit,
    private val rolesProvider: () -> List<Role>,
    private val saveRoles: (List<Role>) -> Unit,
    private val roleProvider: () -> Role
) {
    private val _voiceState = MutableStateFlow(VoiceChatState.IDLE)
    val voiceState: StateFlow<VoiceChatState> = _voiceState.asStateFlow()

    private val _isGeneratingVoice = MutableStateFlow(false)
    val isGeneratingVoice: StateFlow<Boolean> = _isGeneratingVoice.asStateFlow()

    private val _speakingId = MutableStateFlow<String?>(null)
    val speakingId: StateFlow<String?> = _speakingId.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var mediaFile: File? = null

    fun generateRoleVoice(role: Role) {
        if (settingsStorage.loadConnection().apiKey.isBlank()) {
            showToast("请先配置 API Key")
            return
        }
        scope.launch {
            try {
                _isGeneratingVoice.value = true
                _voiceState.value = VoiceChatState.THINKING
                val dataUrl = withContext(Dispatchers.IO) {
                    MimoClient.synthesizeSpeech(
                        settingsStorage.loadConnection(), "mimo-v2.5-tts-voicedesign",
                        "你好，我是${role.name}。${role.description}", role.voiceName, null,
                        role.voicePrompt ?: "自然、清晰、像面对面聊天一样回应。"
                    )
                }
                // MiMo 声音设计没有可复用的 voiceId；保存生成音频作为克隆样本，
                // 后续试听和聊天都使用同一份样本，避免每次重新设计出不同声音。
                val sampleReference = withContext(Dispatchers.IO) {
                    voiceSampleStore.save(role.id, dataUrl)
                }
                val saved = role.copy(
                    voiceModel = VoiceModel.MIMO_V2_5_TTS_VOICEDESIGN,
                    voiceSample = sampleReference,
                    voiceGenerated = true
                )
                saveRoles(rolesProvider().map { if (it.id == role.id) saved else it })
                showToast("音色已生成并保存，聊天将使用该音色")
                playDataUrl("role-preview", dataUrl)
            } catch (e: Exception) {
                _voiceState.value = VoiceChatState.ERROR
                showToast(MimoClient.translateError(e))
                delay(1200)
                _voiceState.value = VoiceChatState.IDLE
            } finally {
                _isGeneratingVoice.value = false
            }
        }
    }

    fun previewRoleVoice(role: Role) {
        if (role.voiceModel == VoiceModel.MIMO_V2_5_TTS_VOICEDESIGN && role.voiceSample.isNullOrBlank()) {
            showToast("请先生成并保存音色")
            return
        }
        speakText("role-preview", "你好，我是${role.name}。${role.description}", role)
    }

    fun speakMessage(messageId: String, text: String) {
        if (text.isBlank()) return
        if (_speakingId.value == messageId && mediaPlayer != null) {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.pause()
                    _voiceState.value = VoiceChatState.IDLE
                } else {
                    player.start()
                    _voiceState.value = VoiceChatState.SPEAKING
                }
            }
            return
        }
        speakText(messageId, text, roleProvider())
    }

    private fun speakText(id: String, text: String, role: Role) {
        if (settingsStorage.loadConnection().apiKey.isBlank()) {
            showToast("请先配置 API Key")
            return
        }
        scope.launch {
            try {
                _voiceState.value = VoiceChatState.THINKING
                val voiceSample = withContext(Dispatchers.IO) {
                    voiceSampleStore.resolve(role.voiceSample)
                }
                val voiceApiModel = if (role.voiceModel == VoiceModel.MIMO_V2_5_TTS_VOICEDESIGN && !voiceSample.isNullOrBlank()) {
                    VoiceModel.MIMO_V2_5_TTS_VOICECLONE.apiName
                } else role.voiceModel.apiName
                val dataUrl = withContext(Dispatchers.IO) {
                    MimoClient.synthesizeSpeech(
                        settingsStorage.loadConnection(), voiceApiModel, text,
                        role.voiceName, voiceSample, role.voicePrompt ?: "自然、清晰、像面对面聊天一样回应。"
                    )
                }
                playDataUrl(id, dataUrl)
            } catch (e: Exception) {
                _speakingId.value = null
                _voiceState.value = VoiceChatState.ERROR
                showToast(MimoClient.translateError(e))
                delay(1200)
                _voiceState.value = VoiceChatState.IDLE
            }
        }
    }

    private suspend fun playDataUrl(id: String, dataUrl: String) {
        val encoded = dataUrl.substringAfter(',', "")
        val file = File(context.cacheDir, "speech-${System.currentTimeMillis()}.wav")
        withContext(Dispatchers.IO) {
            file.writeBytes(Base64.decode(encoded, Base64.DEFAULT))
        }
        withContext(Dispatchers.Main) {
            mediaPlayer?.release()
            mediaFile?.delete()
            mediaFile = file
            mediaPlayer = MediaPlayer().apply {
                val player = this
                setDataSource(file.absolutePath)
                setOnPreparedListener {
                    _speakingId.value = id
                    _voiceState.value = VoiceChatState.SPEAKING
                    start()
                }
                setOnCompletionListener {
                    _speakingId.value = null
                    _voiceState.value = VoiceChatState.IDLE
                    release()
                    file.delete()
                    if (mediaPlayer === player) {
                        mediaFile = null
                        mediaPlayer = null
                    }
                }
                setOnErrorListener { _, _, _ ->
                    _speakingId.value = null
                    _voiceState.value = VoiceChatState.ERROR
                    file.delete()
                    if (mediaPlayer === player) {
                        mediaFile = null
                        mediaPlayer = null
                    }
                    release()
                    true
                }
                prepareAsync()
            }
        }
    }

    fun startVoiceRecording() {
        if (voiceState.value != VoiceChatState.IDLE) return
        try {
            voiceRecorder.start()
            _voiceState.value = VoiceChatState.LISTENING
        } catch (e: Exception) {
            voiceRecorder.cancel()
            showToast("无法开始录音：${e.message ?: "请检查麦克风权限"}")
        }
    }

    fun stopVoiceRecording(onTranscript: (String) -> Unit) {
        if (voiceState.value != VoiceChatState.LISTENING) return
        val file = voiceRecorder.stop() ?: run { _voiceState.value = VoiceChatState.IDLE; return }
        scope.launch {
            try {
                _voiceState.value = VoiceChatState.TRANSCRIBING
                val audio = withContext(Dispatchers.IO) {
                    "data:audio/mp4;base64," + Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                }
                val transcript = withContext(Dispatchers.IO) { MimoClient.speechRecognition(settingsStorage.loadConnection(), audio) }
                _voiceState.value = VoiceChatState.THINKING
                onTranscript(transcript)
            } catch (e: Exception) {
                _voiceState.value = VoiceChatState.ERROR
                showToast(MimoClient.translateError(e))
                delay(1200)
            } finally {
                file.delete()
                _voiceState.value = VoiceChatState.IDLE
            }
        }
    }

    fun cancelVoiceRecording() {
        voiceRecorder.cancel()
        _voiceState.value = VoiceChatState.IDLE
    }

    /** 释放播放器与临时文件，ViewModel onCleared 时调用。 */
    fun release() {
        mediaPlayer?.release()
        mediaFile?.delete()
        mediaPlayer = null
    }
}
