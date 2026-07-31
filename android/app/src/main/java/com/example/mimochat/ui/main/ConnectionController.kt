package com.example.mimochat.ui.main

import com.example.mimochat.data.ConnectionPhase
import com.example.mimochat.data.MimoConnection
import com.example.mimochat.data.ProbeResult
import com.example.mimochat.data.ProbeStatus
import com.example.mimochat.data.local.SettingsStorage
import com.example.mimochat.data.remote.MimoClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 连接与模型探测控制器：连接状态、探测进度与结果。
 *
 * 模型列表加载完成后通过 [onModelsLoaded] 回调同步给宿主 ViewModel
 * （用于模型切换时的可用性校验）。
 */
class ConnectionController(
    private val settingsStorage: SettingsStorage,
    private val scope: CoroutineScope,
    private val onModelsLoaded: (Set<String>) -> Unit
) {
    private val _connectionPhase = MutableStateFlow(ConnectionPhase.IDLE)
    val connectionPhase: StateFlow<ConnectionPhase> = _connectionPhase.asStateFlow()

    private val _connectionError = MutableStateFlow("")
    val connectionError: StateFlow<String> = _connectionError.asStateFlow()

    private val _probeResults = MutableStateFlow<List<ProbeResult>>(emptyList())
    val probeResults: StateFlow<List<ProbeResult>> = _probeResults.asStateFlow()

    fun loadConnection(): MimoConnection = settingsStorage.loadConnection()

    fun saveConnection(connection: MimoConnection) {
        settingsStorage.saveConnection(connection)
        onModelsLoaded(emptySet())
    }

    fun clearApiKey() {
        settingsStorage.clearApiKey()
    }

    fun connect() {
        val config = settingsStorage.loadConnection()
        if (config.baseUrl.isBlank() || config.apiKey.isBlank()) {
            _connectionError.value = "请填写模型地址和 API Key"
            return
        }
        _connectionError.value = ""
        _probeResults.value = emptyList()
        _connectionPhase.value = ConnectionPhase.LOADING

        scope.launch {
            try {
                val models = MimoClient.loadModels(config)
                if (models.isEmpty()) throw Exception("没有加载到模型")
                onModelsLoaded(models.toSet())
                _connectionPhase.value = ConnectionPhase.TESTING
                _probeResults.value = models.map { m ->
                    ProbeResult(model = m, capability = "识别中", status = ProbeStatus.TESTING, detail = "正在验证能力")
                }
                val results = mutableListOf<ProbeResult>()
                for (m in models) {
                    results.add(MimoClient.probeModel(config, m))
                    _probeResults.value = results.toList()
                }
                onModelsLoaded(results
                    .filter { it.status == ProbeStatus.PASSED || it.status == ProbeStatus.REACHABLE }
                    .map { it.model }
                    .toSet())
                _connectionPhase.value = ConnectionPhase.DONE
            } catch (e: Exception) {
                _connectionError.value = MimoClient.translateError(e)
                _connectionPhase.value = ConnectionPhase.IDLE
            }
        }
    }
}
