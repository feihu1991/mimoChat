package com.example.mimochat.ui.voice

import android.app.Application
import android.media.MediaPlayer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mimochat.core.voice.VoiceAudioStore
import com.example.mimochat.data.Message
import com.example.mimochat.data.Role
import com.example.mimochat.data.VoiceModel
import com.example.mimochat.data.local.SettingsStorage
import com.example.mimochat.data.remote.MimoClient
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class VoiceDesignCandidate(
    val id: String,
    val label: String,
    val filePath: String
)

data class VoiceDesignState(
    val roleId: String? = null,
    val description: String = "",
    val isGenerating: Boolean = false,
    val candidates: List<VoiceDesignCandidate> = emptyList(),
    val previewingCandidateId: String? = null,
    val selectedCandidateId: String? = null,
    val error: String? = null
)

class VoiceViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val CANDIDATE_COUNT = 3
        private const val DESIGN_SAMPLE_TEXT =
            "你好，很高兴认识你。以后我会用这个声音陪你聊天，也会在你需要时帮你整理想法、阅读内容和处理文件。希望这个声音听起来自然、清晰，也足够有辨识度。"
    }

    private val settingsStorage = SettingsStorage(application)
    private val audioStore = VoiceAudioStore(application)

    private val _playingMessageId = MutableStateFlow<String?>(null)
    val playingMessageId: StateFlow<String?> = _playingMessageId.asStateFlow()

    private val _loadingMessageId = MutableStateFlow<String?>(null)
    val loadingMessageId: StateFlow<String?> = _loadingMessageId.asStateFlow()

    private val _designState = MutableStateFlow(VoiceDesignState())
    val designState: StateFlow<VoiceDesignState> = _designState.asStateFlow()

    private val _toast = MutableStateFlow("")
    val toast: StateFlow<String> = _toast.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var voiceJob: Job? = null
    private var designJob: Job? = null
    private var playbackToken = 0

    fun toggleMessage(message: Message, role: Role) {
        if (_playingMessageId.value == message.id || _loadingMessageId.value == message.id) {
            stopPlayback()
            return
        }
        playMessage(message, role, forceRegenerate = false)
    }

    fun regenerateMessage(message: Message, role: Role) {
        audioStore.deleteMessageAudio(message.id)
        playMessage(message, role, forceRegenerate = true)
    }

    private fun playMessage(message: Message, role: Role, forceRegenerate: Boolean) {
        if (message.text.isBlank()) return
        val config = settingsStorage.loadConnection()
        if (config.apiKey.isBlank()) {
            showToast("请先在设置中配置 API Key")
            return
        }

        stopPlayback()
        val token = playbackToken
        _loadingMessageId.value = message.id

        voiceJob = viewModelScope.launch {
            try {
                val fingerprint = withContext(Dispatchers.IO) {
                    audioStore.messageFingerprint(message.text, role)
                }
                val target = audioStore.messageAudioFile(message.id, fingerprint)
                val audioFile = if (!forceRegenerate && target.isFile) {
                    target
                } else {
                    val voiceSample = if (role.voiceModel == VoiceModel.MIMO_V2_5_TTS_VOICECLONE) {
                        withContext(Dispatchers.IO) {
                            audioStore.voiceSampleDataUrl(role.voiceSample)
                        } ?: throw IllegalStateException("当前角色还没有固定音色样本")
                    } else {
                        null
                    }
                    val bytes = MimoClient.synthesizeSpeechBytes(
                        config = config,
                        model = role.voiceModel.apiName,
                        text = message.text,
                        voiceName = role.voiceName,
                        voiceSample = voiceSample,
                        voicePrompt = role.voicePrompt.orEmpty().ifBlank {
                            "自然、清晰、像面对面聊天一样回应。"
                        }
                    )
                    withContext(Dispatchers.IO) {
                        audioStore.saveMessageAudio(target, bytes)
                    }
                }

                if (token != playbackToken) return@launch
                startPlayback(audioFile, messageId = message.id, candidateId = null, token = token)
            } catch (_: CancellationException) {
                // Replaced by a newer playback request.
            } catch (e: Exception) {
                if (token == playbackToken) {
                    _loadingMessageId.value = null
                    showToast(e.message ?: "语音生成失败")
                }
            }
        }
    }

    fun generateVoiceCandidates(roleId: String, description: String) {
        val cleanDescription = description.trim()
        if (cleanDescription.isBlank()) {
            showToast("请先填写音色提示词")
            return
        }
        val config = settingsStorage.loadConnection()
        if (config.apiKey.isBlank()) {
            showToast("请先在设置中配置 API Key")
            return
        }

        designJob?.cancel()
        stopPlayback()
        audioStore.clearDesignCandidates(roleId)
        _designState.value = VoiceDesignState(
            roleId = roleId,
            description = cleanDescription,
            isGenerating = true
        )

        designJob = viewModelScope.launch {
            val candidates = mutableListOf<VoiceDesignCandidate>()
            try {
                repeat(CANDIDATE_COUNT) { index ->
                    val bytes = MimoClient.designVoiceSample(
                        config = config,
                        description = cleanDescription,
                        text = DESIGN_SAMPLE_TEXT
                    )
                    val file = withContext(Dispatchers.IO) {
                        audioStore.saveDesignCandidate(roleId, index, bytes)
                    }
                    candidates += VoiceDesignCandidate(
                        id = "$roleId-${index + 1}",
                        label = "方案 ${index + 1}",
                        filePath = file.absolutePath
                    )
                    _designState.value = _designState.value.copy(
                        candidates = candidates.toList(),
                        isGenerating = candidates.size < CANDIDATE_COUNT
                    )
                }
                _designState.value = _designState.value.copy(isGenerating = false, error = null)
            } catch (_: CancellationException) {
                // A new design request replaced this one.
            } catch (e: Exception) {
                _designState.value = _designState.value.copy(
                    isGenerating = false,
                    error = e.message ?: "音色设计失败"
                )
                showToast(e.message ?: "音色设计失败")
            }
        }
    }

    fun previewCandidate(candidateId: String) {
        val candidate = _designState.value.candidates.find { it.id == candidateId } ?: return
        val file = File(candidate.filePath)
        if (!file.isFile) {
            showToast("试听音频已失效，请重新生成")
            return
        }

        if (_designState.value.previewingCandidateId == candidateId) {
            stopPlayback()
            return
        }

        stopPlayback()
        val token = playbackToken
        _designState.value = _designState.value.copy(previewingCandidateId = candidateId)
        startPlayback(file, messageId = null, candidateId = candidateId, token = token)
    }

    fun selectCandidate(roleId: String, candidateId: String, onSelected: (String) -> Unit) {
        val state = _designState.value
        val candidate = state.candidates.find { it.id == candidateId && state.roleId == roleId } ?: return

        viewModelScope.launch {
            try {
                val selectedFile = withContext(Dispatchers.IO) {
                    audioStore.commitDesignCandidate(roleId, candidate.filePath)
                }
                onSelected(selectedFile.absolutePath)
                _designState.value = _designState.value.copy(selectedCandidateId = candidateId)
                showToast("已固定 ${candidate.label}，后续将使用声音克隆")
            } catch (e: Exception) {
                showToast(e.message ?: "保存音色失败")
            }
        }
    }

    fun stopPlayback() {
        playbackToken += 1
        voiceJob?.cancel()
        voiceJob = null
        releasePlayer()
        _playingMessageId.value = null
        _loadingMessageId.value = null
        _designState.value = _designState.value.copy(previewingCandidateId = null)
    }

    private fun startPlayback(
        file: File,
        messageId: String?,
        candidateId: String?,
        token: Int
    ) {
        releasePlayer()
        val player = MediaPlayer()
        mediaPlayer = player
        player.setOnPreparedListener {
            if (token != playbackToken) {
                it.release()
                return@setOnPreparedListener
            }
            _loadingMessageId.value = null
            _playingMessageId.value = messageId
            _designState.value = _designState.value.copy(previewingCandidateId = candidateId)
            it.start()
        }
        player.setOnCompletionListener {
            if (token == playbackToken) {
                releasePlayer()
                _playingMessageId.value = null
                _loadingMessageId.value = null
                _designState.value = _designState.value.copy(previewingCandidateId = null)
            }
        }
        player.setOnErrorListener { _, _, _ ->
            if (token == playbackToken) {
                releasePlayer()
                _playingMessageId.value = null
                _loadingMessageId.value = null
                _designState.value = _designState.value.copy(previewingCandidateId = null)
                showToast("语音播放失败")
            }
            true
        }
        try {
            player.setDataSource(file.absolutePath)
            player.prepareAsync()
        } catch (e: Exception) {
            releasePlayer()
            _playingMessageId.value = null
            _loadingMessageId.value = null
            _designState.value = _designState.value.copy(previewingCandidateId = null)
            showToast(e.message ?: "语音播放失败")
        }
    }

    private fun releasePlayer() {
        mediaPlayer?.let { player ->
            runCatching { player.stop() }
            runCatching { player.reset() }
            runCatching { player.release() }
        }
        mediaPlayer = null
    }

    private fun showToast(message: String) {
        val text = message.take(100)
        _toast.value = text
        viewModelScope.launch {
            delay(2500)
            if (_toast.value == text) _toast.value = ""
        }
    }

    override fun onCleared() {
        designJob?.cancel()
        stopPlayback()
        super.onCleared()
    }
}
