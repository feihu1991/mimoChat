package com.example.mimochat.data.update

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.mimochat.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val LATEST_RELEASE_URL =
    "https://api.github.com/repos/feihu1991/mimoChat/releases/latest"
private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

data class AppRelease(
    val versionName: String,
    val tagName: String,
    val releaseNotes: String,
    val apkName: String,
    val apkDownloadUrl: String,
    val releasePageUrl: String
)

sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data object Checking : AppUpdateState
    data class UpToDate(val currentVersion: String) : AppUpdateState
    data class Available(val release: AppRelease) : AppUpdateState
    data class Downloading(val release: AppRelease) : AppUpdateState
    data class Installing(val release: AppRelease) : AppUpdateState
    data class Error(val message: String) : AppUpdateState
}

class AppUpdateManager private constructor(context: Context) {
    companion object {
        @Volatile
        private var instance: AppUpdateManager? = null

        fun getInstance(context: Context): AppUpdateManager =
            instance ?: synchronized(this) {
                instance ?: AppUpdateManager(context.applicationContext).also { instance = it }
            }
    }

    private val appContext = context.applicationContext
    private val downloadManager = appContext.getSystemService(DownloadManager::class.java)
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 20_000
            requestTimeoutMillis = 30_000
            socketTimeoutMillis = 30_000
        }
    }

    private val _state = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val state: StateFlow<AppUpdateState> = _state.asStateFlow()

    private var downloadReceiver: BroadcastReceiver? = null
    private var activeDownloadId: Long? = null

    suspend fun checkForUpdate(currentVersion: String) {
        if (_state.value is AppUpdateState.Checking ||
            _state.value is AppUpdateState.Downloading
        ) return

        _state.value = AppUpdateState.Checking
        _state.value = fetchUpdateState(currentVersion)
    }

    private suspend fun fetchUpdateState(currentVersion: String): AppUpdateState {
        return try {
            val response = client.get(LATEST_RELEASE_URL) {
                header(HttpHeaders.Accept, "application/vnd.github+json")
                header(HttpHeaders.UserAgent, "mimoChat/${BuildConfig.VERSION_NAME}")
                header("X-GitHub-Api-Version", "2022-11-28")
            }

            if (response.status == HttpStatusCode.NotFound) {
                return AppUpdateState.Error("仓库还没有发布可安装版本")
            }
            if (!response.status.isSuccess()) {
                val message = response.bodyAsText().take(240)
                return AppUpdateState.Error("检查更新失败：HTTP ${response.status.value} $message")
            }

            val githubRelease = response.body<GitHubRelease>()
            val apkAsset = githubRelease.assets.firstOrNull { asset ->
                asset.name.endsWith(".apk", ignoreCase = true)
            } ?: return AppUpdateState.Error("最新 Release 中没有 APK 文件")

            if (!isTrustedApkUrl(apkAsset.downloadUrl)) {
                return AppUpdateState.Error("Release APK 下载地址不可信")
            }

            val latestVersion = githubRelease.tagName.removePrefix("v").removePrefix("V")
            if (!VersionComparator.isNewer(latestVersion, currentVersion)) {
                AppUpdateState.UpToDate(currentVersion)
            } else {
                AppUpdateState.Available(
                    AppRelease(
                        versionName = latestVersion,
                        tagName = githubRelease.tagName,
                        releaseNotes = githubRelease.body.orEmpty(),
                        apkName = apkAsset.name,
                        apkDownloadUrl = apkAsset.downloadUrl,
                        releasePageUrl = githubRelease.htmlUrl
                    )
                )
            }
        } catch (e: Exception) {
            AppUpdateState.Error(e.message ?: "检查更新失败")
        }
    }

    fun downloadAndInstall() {
        val release = (_state.value as? AppUpdateState.Available)?.release ?: return

        if (!isTrustedApkUrl(release.apkDownloadUrl)) {
            _state.value = AppUpdateState.Error("Release APK 下载地址不可信")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !appContext.packageManager.canRequestPackageInstalls()
        ) {
            try {
                val settingsIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${appContext.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                appContext.startActivity(settingsIntent)
                _state.value = AppUpdateState.Error("请允许 MiMo Chat 安装未知应用，然后返回重试")
            } catch (_: ActivityNotFoundException) {
                _state.value = AppUpdateState.Error("无法打开安装权限设置")
            }
            return
        }

        clearDownloadReceiver()

        val request = DownloadManager.Request(Uri.parse(release.apkDownloadUrl))
            .setTitle("MiMo Chat ${release.versionName}")
            .setDescription("正在下载应用更新")
            .setMimeType(APK_MIME_TYPE)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(
                appContext,
                Environment.DIRECTORY_DOWNLOADS,
                release.apkName
            )

        val downloadId = try {
            downloadManager.enqueue(request)
        } catch (e: Exception) {
            _state.value = AppUpdateState.Error(e.message ?: "无法开始下载")
            return
        }

        activeDownloadId = downloadId
        _state.value = AppUpdateState.Downloading(release)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
                val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (completedId != activeDownloadId) return
                handleDownloadComplete(completedId, release)
            }
        }
        downloadReceiver = receiver

        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    private fun handleDownloadComplete(downloadId: Long, release: AppRelease) {
        val cursor = downloadManager.query(
            DownloadManager.Query().setFilterById(downloadId)
        )

        cursor.use {
            if (!it.moveToFirst()) {
                clearDownloadReceiver()
                _state.value = AppUpdateState.Error("无法读取下载结果")
                return
            }

            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            if (status != DownloadManager.STATUS_SUCCESSFUL) {
                val reason = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                clearDownloadReceiver()
                _state.value = AppUpdateState.Error("APK 下载失败，错误码 $reason")
                return
            }
        }

        val apkUri = downloadManager.getUriForDownloadedFile(downloadId)
        if (apkUri == null) {
            clearDownloadReceiver()
            _state.value = AppUpdateState.Error("找不到已下载的 APK")
            return
        }

        val installIntent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(apkUri, APK_MIME_TYPE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        try {
            clearDownloadReceiver()
            _state.value = AppUpdateState.Installing(release)
            appContext.startActivity(installIntent)
        } catch (_: ActivityNotFoundException) {
            _state.value = AppUpdateState.Error("系统中没有可用的 APK 安装器")
        }
    }

    private fun isTrustedApkUrl(url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        return uri.scheme == "https" &&
            uri.host?.equals("github.com", ignoreCase = true) == true
    }

    private fun clearDownloadReceiver() {
        downloadReceiver?.let { receiver ->
            runCatching { appContext.unregisterReceiver(receiver) }
        }
        downloadReceiver = null
        activeDownloadId = null
    }
}

@Serializable
private data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String,
    val body: String? = null,
    val assets: List<GitHubAsset> = emptyList()
)

@Serializable
private data class GitHubAsset(
    val name: String,
    @SerialName("browser_download_url") val downloadUrl: String
)
