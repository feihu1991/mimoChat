package com.example.mimochat.core.workspace

import android.content.Context
import android.util.Base64
import com.example.mimochat.data.local.SettingsStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Serializable
data class GitHubWorkspaceConfig(
    val repository: String = "",
    val baseBranch: String = "master",
    val token: String = "",
    val workingBranch: String = ""
) {
    val isConfigured: Boolean
        get() = repository.count { it == '/' } == 1 && token.isNotBlank() && baseBranch.isNotBlank()
}

sealed class WorkspaceSyncState {
    data object Idle : WorkspaceSyncState()
    data object Syncing : WorkspaceSyncState()
    data class Ready(val files: Int, val baseCommit: String) : WorkspaceSyncState()
    data class Error(val message: String) : WorkspaceSyncState()
}

data class WorkspaceChange(
    val path: String,
    val type: ChangeType,
    val oldContent: String,
    val newContent: String
) {
    enum class ChangeType { ADDED, MODIFIED, DELETED }

    val additions: Int get() = newContent.lineSequence().count()
    val deletions: Int get() = oldContent.lineSequence().count()
}

data class GitCommitResult(val branch: String, val commitSha: String, val changedFiles: Int)
data class PullRequestResult(val number: Int, val url: String)

@Serializable
private data class WorkspaceManifest(
    val repository: String,
    val baseBranch: String,
    val baseCommit: String,
    val files: Map<String, String>
)

private data class GitTreeEntry(
    val path: String,
    val type: String,
    val sha: String,
    val size: Long
)

/**
 * Android-native project workspace backed by GitHub's Git Data API.
 *
 * Project files live in app-private storage. The model never receives filesystem
 * access; it can only request structured tools that call this class. Git commits
 * are created as one blob/tree/commit transaction and the branch ref is moved
 * with force=false semantics.
 */
class GitHubWorkspace(
    context: Context,
    private val settingsStorage: SettingsStorage
) {
    companion object {
        private const val MAX_SYNC_FILES = 600
        private const val MAX_SYNC_BYTES = 24L * 1024L * 1024L
        private const val MAX_FILE_BYTES = 768L * 1024L
        private const val MAX_READ_CHARS = 60_000

        private val IGNORED_DIRECTORIES = setOf(
            ".git", ".gradle", ".idea", "build", "node_modules", "dist", "target", "vendor"
        )
        private val BINARY_EXTENSIONS = setOf(
            "png", "jpg", "jpeg", "gif", "webp", "ico", "pdf", "zip", "gz", "jar", "aar",
            "apk", "aab", "so", "dll", "exe", "class", "woff", "woff2", "ttf", "mp3", "wav", "mp4"
        )
    }

    private val root = File(context.filesDir, "mimo-code-workspace")
    private val metaRoot get() = File(root, ".mimo")
    private val baseRoot get() = File(metaRoot, "base")
    private val manifestFile get() = File(metaRoot, "manifest.json")
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; prettyPrint = true }
    private val api = GitHubApiClient()

    fun config(): GitHubWorkspaceConfig = settingsStorage.loadWorkspaceConfig()

    suspend fun sync(config: GitHubWorkspaceConfig): WorkspaceSyncState.Ready = withContext(Dispatchers.IO) {
        require(config.isConfigured) { "请先配置 GitHub 仓库、分支和 Token" }
        validateRepository(config.repository)

        val baseCommit = api.getRefSha(config.repository, config.baseBranch, config.token)
        val treeSha = api.getCommitTreeSha(config.repository, baseCommit, config.token)
        val entries = api.listTree(config.repository, treeSha, config.token)
            .asSequence()
            .filter { it.type == "blob" }
            .filter { it.size in 0..MAX_FILE_BYTES }
            .filterNot { isIgnored(it.path) }
            .take(MAX_SYNC_FILES)
            .toList()

        root.deleteRecursively()
        root.mkdirs()
        baseRoot.mkdirs()

        var totalBytes = 0L
        val hashes = linkedMapOf<String, String>()
        for (entry in entries) {
            if (totalBytes + entry.size > MAX_SYNC_BYTES) break
            val content = api.getBlobText(config.repository, entry.sha, config.token) ?: continue
            val bytes = content.toByteArray(Charsets.UTF_8).size.toLong()
            if (totalBytes + bytes > MAX_SYNC_BYTES) break
            writeAtomic(resolveProjectPath(entry.path), content)
            writeAtomic(resolveBasePath(entry.path), content)
            hashes[entry.path] = sha256(content)
            totalBytes += bytes
        }

        val manifest = WorkspaceManifest(
            repository = config.repository,
            baseBranch = config.baseBranch,
            baseCommit = baseCommit,
            files = hashes
        )
        writeAtomic(manifestFile, json.encodeToString(manifest))
        settingsStorage.saveWorkspaceConfig(config.copy(workingBranch = ""))
        WorkspaceSyncState.Ready(hashes.size, baseCommit.take(12))
    }

    fun isReady(): Boolean = manifestFile.isFile

    fun listFiles(pattern: String? = null, limit: Int = 300): List<String> {
        ensureReady()
        val regex = pattern?.takeIf { it.isNotBlank() }?.let(::globToRegex)
        return root.walkTopDown()
            .filter { it.isFile }
            .filterNot { it.toPath().startsWith(metaRoot.toPath()) }
            .map { it.relativeTo(root).invariantSeparatorsPath }
            .filter { regex == null || regex.matches(it) }
            .sorted()
            .take(limit.coerceIn(1, 1000))
            .toList()
    }

    fun readFile(path: String, startLine: Int = 1, endLine: Int? = null): String {
        ensureReady()
        val file = resolveProjectPath(path)
        require(file.isFile) { "文件不存在：$path" }
        require(file.length() <= MAX_FILE_BYTES) { "文件过大，无法直接读取：$path" }
        val lines = file.readLines()
        val from = (startLine.coerceAtLeast(1) - 1).coerceAtMost(lines.size)
        val to = (endLine ?: lines.size).coerceAtLeast(startLine).coerceAtMost(lines.size)
        val selected = lines.subList(from, to)
        return selected.mapIndexed { index, line -> "${from + index + 1}: $line" }
            .joinToString("\n")
            .take(MAX_READ_CHARS)
    }

    fun grep(query: String, glob: String? = null, limit: Int = 100): String {
        require(query.isNotBlank()) { "query 不能为空" }
        val regex = runCatching { Regex(query, setOf(RegexOption.IGNORE_CASE)) }
            .getOrElse { Regex(Regex.escape(query), RegexOption.IGNORE_CASE) }
        val matches = mutableListOf<String>()
        for (path in listFiles(glob, 1000)) {
            val file = resolveProjectPath(path)
            if (file.length() > MAX_FILE_BYTES) continue
            file.useLines { lines ->
                lines.forEachIndexed { index, line ->
                    if (matches.size < limit && regex.containsMatchIn(line)) {
                        matches += "$path:${index + 1}: ${line.take(500)}"
                    }
                }
            }
            if (matches.size >= limit) break
        }
        return if (matches.isEmpty()) "未找到匹配内容" else matches.joinToString("\n")
    }

    fun previewWrite(path: String, content: String): WorkspaceChange {
        ensureReady()
        require(content.length <= MAX_READ_CHARS * 4) { "写入内容过大" }
        val file = resolveProjectPath(path)
        val old = if (file.isFile) file.readText() else ""
        return WorkspaceChange(
            path = normalizeRelativePath(path),
            type = if (file.exists()) WorkspaceChange.ChangeType.MODIFIED else WorkspaceChange.ChangeType.ADDED,
            oldContent = old,
            newContent = content
        )
    }

    fun previewEdit(path: String, oldText: String, newText: String): WorkspaceChange {
        require(oldText.isNotEmpty()) { "old_text 不能为空" }
        val file = resolveProjectPath(path)
        require(file.isFile) { "文件不存在：$path" }
        val content = file.readText()
        val first = content.indexOf(oldText)
        require(first >= 0) { "未找到要替换的原文" }
        require(content.indexOf(oldText, first + oldText.length) < 0) { "原文匹配多次，请提供更精确的上下文" }
        return WorkspaceChange(
            path = normalizeRelativePath(path),
            type = WorkspaceChange.ChangeType.MODIFIED,
            oldContent = content,
            newContent = content.replaceFirst(oldText, newText)
        )
    }

    fun previewDelete(path: String): WorkspaceChange {
        val file = resolveProjectPath(path)
        require(file.isFile) { "文件不存在：$path" }
        return WorkspaceChange(
            path = normalizeRelativePath(path),
            type = WorkspaceChange.ChangeType.DELETED,
            oldContent = file.readText(),
            newContent = ""
        )
    }

    fun apply(change: WorkspaceChange) {
        when (change.type) {
            WorkspaceChange.ChangeType.DELETED -> resolveProjectPath(change.path).delete()
            else -> writeAtomic(resolveProjectPath(change.path), change.newContent)
        }
    }

    fun status(): List<WorkspaceChange> {
        ensureReady()
        val manifest = loadManifest()
        val currentPaths = listFiles(limit = 5000).toSet()
        val allPaths = (manifest.files.keys + currentPaths).toSortedSet()
        return buildList {
            for (path in allPaths) {
                val current = resolveProjectPath(path)
                val base = resolveBasePath(path)
                when {
                    path !in manifest.files && current.isFile -> add(
                        WorkspaceChange(path, WorkspaceChange.ChangeType.ADDED, "", current.readText())
                    )
                    path in manifest.files && !current.exists() -> add(
                        WorkspaceChange(path, WorkspaceChange.ChangeType.DELETED, base.takeIf { it.isFile }?.readText().orEmpty(), "")
                    )
                    current.isFile && sha256(current.readText()) != manifest.files[path] -> add(
                        WorkspaceChange(path, WorkspaceChange.ChangeType.MODIFIED, base.takeIf { it.isFile }?.readText().orEmpty(), current.readText())
                    )
                }
            }
        }
    }

    fun diff(changes: List<WorkspaceChange> = status()): String {
        if (changes.isEmpty()) return "工作区没有未提交修改"
        return changes.joinToString("\n\n") { change ->
            val oldLines = change.oldContent.lines()
            val newLines = change.newContent.lines()
            val body = buildList {
                add("--- a/${change.path}")
                add("+++ b/${change.path}")
                add("@@ ${change.type.name.lowercase()} @@")
                oldLines.take(160).forEach { add("-$it") }
                newLines.take(160).forEach { add("+$it") }
                if (oldLines.size > 160 || newLines.size > 160) add("... diff 已截断 ...")
            }
            body.joinToString("\n")
        }.take(40_000)
    }

    suspend fun ensureBranch(requestedBranch: String = ""): String = withContext(Dispatchers.IO) {
        val config = config()
        require(config.isConfigured) { "GitHub 工作区尚未配置" }
        val branch = normalizeBranch(
            requestedBranch.ifBlank {
                config.workingBranch.ifBlank { "agent/android-${System.currentTimeMillis()}" }
            }
        )
        val existing = api.getRefShaOrNull(config.repository, branch, config.token)
        if (existing == null) {
            val baseSha = api.getRefSha(config.repository, config.baseBranch, config.token)
            api.createRef(config.repository, branch, baseSha, config.token)
        }
        settingsStorage.saveWorkspaceConfig(config.copy(workingBranch = branch))
        branch
    }

    suspend fun commitAndPush(message: String): GitCommitResult = withContext(Dispatchers.IO) {
        require(message.isNotBlank()) { "Commit message 不能为空" }
        val config = config()
        val changes = status()
        require(changes.isNotEmpty()) { "工作区没有可提交的修改" }
        val branch = ensureBranch(config.workingBranch)
        val parentSha = api.getRefSha(config.repository, branch, config.token)
        val baseTreeSha = api.getCommitTreeSha(config.repository, parentSha, config.token)

        val treeEntries = mutableListOf<JsonObject>()
        for (change in changes) {
            treeEntries += if (change.type == WorkspaceChange.ChangeType.DELETED) {
                buildJsonObject {
                    put("path", change.path)
                    put("mode", "100644")
                    put("type", "blob")
                    put("sha", JsonNull)
                }
            } else {
                val blobSha = api.createBlob(config.repository, change.newContent, config.token)
                buildJsonObject {
                    put("path", change.path)
                    put("mode", "100644")
                    put("type", "blob")
                    put("sha", blobSha)
                }
            }
        }

        val treeSha = api.createTree(config.repository, baseTreeSha, treeEntries, config.token)
        val commitSha = api.createCommit(config.repository, message.trim(), treeSha, parentSha, config.token)
        api.updateRef(config.repository, branch, commitSha, config.token)
        refreshBaseline(commitSha)
        GitCommitResult(branch, commitSha, changes.size)
    }

    suspend fun createPullRequest(title: String, body: String): PullRequestResult = withContext(Dispatchers.IO) {
        val config = config()
        val branch = config.workingBranch
        require(branch.isNotBlank()) { "请先创建并推送工作分支" }
        api.createPullRequest(
            repository = config.repository,
            title = title.ifBlank { "MiMo Code changes" },
            body = body,
            head = branch,
            base = config.baseBranch,
            token = config.token
        )
    }

    private fun refreshBaseline(commitSha: String) {
        val config = config()
        val files = listFiles(limit = 5000)
        baseRoot.deleteRecursively()
        baseRoot.mkdirs()
        val hashes = linkedMapOf<String, String>()
        for (path in files) {
            val content = resolveProjectPath(path).readText()
            writeAtomic(resolveBasePath(path), content)
            hashes[path] = sha256(content)
        }
        writeAtomic(
            manifestFile,
            json.encodeToString(
                WorkspaceManifest(config.repository, config.baseBranch, commitSha, hashes)
            )
        )
    }

    private fun ensureReady() {
        require(isReady()) { "工作区尚未同步，请先在设置中配置并同步 GitHub 仓库" }
    }

    private fun loadManifest(): WorkspaceManifest =
        json.decodeFromString(manifestFile.readText())

    private fun resolveProjectPath(path: String): File = resolveInside(root, path)
    private fun resolveBasePath(path: String): File = resolveInside(baseRoot, path)

    private fun resolveInside(base: File, path: String): File {
        val relative = normalizeRelativePath(path)
        val canonicalBase = base.canonicalFile
        val target = File(canonicalBase, relative).canonicalFile
        require(target == canonicalBase || target.path.startsWith(canonicalBase.path + File.separator)) {
            "路径越过工作区：$path"
        }
        require(!target.toPath().startsWith(metaRoot.canonicalFile.toPath()) || base == baseRoot) {
            "禁止访问工作区元数据"
        }
        return target
    }

    private fun normalizeRelativePath(path: String): String {
        val normalized = path.trim().replace('\\', '/').removePrefix("./")
        require(normalized.isNotBlank() && !normalized.startsWith('/') && !normalized.contains("../")) {
            "非法项目路径：$path"
        }
        return normalized
    }

    private fun normalizeBranch(branch: String): String {
        val normalized = branch.trim().removePrefix("refs/heads/")
        require(normalized.isNotBlank() && !normalized.startsWith('/') && !normalized.contains("..")) {
            "非法分支名称"
        }
        return normalized
    }

    private fun validateRepository(repository: String) {
        val parts = repository.split('/')
        require(parts.size == 2 && parts.all { it.matches(Regex("[A-Za-z0-9_.-]+")) }) {
            "仓库格式应为 owner/repository"
        }
    }

    private fun isIgnored(path: String): Boolean {
        val segments = path.split('/')
        val ext = path.substringAfterLast('.', "").lowercase()
        return segments.any { it in IGNORED_DIRECTORIES } || ext in BINARY_EXTENSIONS || path.startsWith(".mimo/")
    }

    private fun globToRegex(glob: String): Regex {
        val pattern = buildString {
            append('^')
            var index = 0
            while (index < glob.length) {
                val ch = glob[index]
                when {
                    ch == '*' && index + 1 < glob.length && glob[index + 1] == '*' -> {
                        append(".*")
                        index++
                    }
                    ch == '*' -> append("[^/]*")
                    ch == '?' -> append('.')
                    ch in ".+()^$|{}[]\\" -> append('\\').append(ch)
                    else -> append(ch)
                }
                index++
            }
            append('$')
        }
        return Regex(pattern, RegexOption.IGNORE_CASE)
    }

    private fun writeAtomic(file: File, content: String) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, ".${file.name}.tmp-${System.nanoTime()}")
        temp.writeText(content)
        if (file.exists() && !file.delete()) {
            temp.delete()
            error("无法覆盖文件：${file.path}")
        }
        if (!temp.renameTo(file)) {
            temp.copyTo(file, overwrite = true)
            temp.delete()
        }
    }

    private fun sha256(content: String): String = MessageDigest.getInstance("SHA-256")
        .digest(content.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

private class GitHubApiClient {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(this@GitHubApiClient.json) }
        install(HttpTimeout) {
            connectTimeoutMillis = 30_000
            requestTimeoutMillis = 120_000
            socketTimeoutMillis = 120_000
        }
    }

    suspend fun getRefSha(repository: String, branch: String, token: String): String =
        getRefShaOrNull(repository, branch, token) ?: error("远程分支不存在：$branch")

    suspend fun getRefShaOrNull(repository: String, branch: String, token: String): String? {
        val encoded = branch.encodeURLPathPart()
        val response = raw(HttpMethod.Get, "/repos/$repository/git/ref/heads/$encoded", token, null, allowNotFound = true)
            ?: return null
        return response["object"]?.jsonObject?.get("sha")?.jsonPrimitive?.contentOrNull
    }

    suspend fun getCommitTreeSha(repository: String, commitSha: String, token: String): String {
        val response = raw(HttpMethod.Get, "/repos/$repository/git/commits/$commitSha", token)
            ?: error("无法读取 Git commit")
        return response["tree"]?.jsonObject?.get("sha")?.jsonPrimitive?.contentOrNull
            ?: error("Commit 未返回 tree SHA")
    }

    suspend fun listTree(repository: String, treeSha: String, token: String): List<GitTreeEntry> {
        val response = raw(HttpMethod.Get, "/repos/$repository/git/trees/$treeSha?recursive=1", token)
            ?: error("无法读取仓库目录")
        return response["tree"]?.jsonArray.orEmpty().mapNotNull { item ->
            val obj = item.jsonObject
            val path = obj["path"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val type = obj["type"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val sha = obj["sha"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val size = obj["size"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
            GitTreeEntry(path, type, sha, size)
        }
    }

    suspend fun getBlobText(repository: String, sha: String, token: String): String? {
        val response = raw(HttpMethod.Get, "/repos/$repository/git/blobs/$sha", token) ?: return null
        if (response["encoding"]?.jsonPrimitive?.contentOrNull != "base64") return null
        val encoded = response["content"]?.jsonPrimitive?.contentOrNull?.replace("\n", "") ?: return null
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        if (bytes.any { it == 0.toByte() }) return null
        return runCatching { bytes.toString(Charsets.UTF_8) }.getOrNull()
    }

    suspend fun createBlob(repository: String, content: String, token: String): String {
        val response = raw(
            HttpMethod.Post,
            "/repos/$repository/git/blobs",
            token,
            buildJsonObject {
                put("content", content)
                put("encoding", "utf-8")
            }
        ) ?: error("创建 Git blob 失败")
        return response["sha"]?.jsonPrimitive?.contentOrNull ?: error("Git blob 未返回 SHA")
    }

    suspend fun createTree(
        repository: String,
        baseTreeSha: String,
        entries: List<JsonObject>,
        token: String
    ): String {
        val response = raw(
            HttpMethod.Post,
            "/repos/$repository/git/trees",
            token,
            buildJsonObject {
                put("base_tree", baseTreeSha)
                put("tree", JsonArray(entries))
            }
        ) ?: error("创建 Git tree 失败")
        return response["sha"]?.jsonPrimitive?.contentOrNull ?: error("Git tree 未返回 SHA")
    }

    suspend fun createCommit(
        repository: String,
        message: String,
        treeSha: String,
        parentSha: String,
        token: String
    ): String {
        val response = raw(
            HttpMethod.Post,
            "/repos/$repository/git/commits",
            token,
            buildJsonObject {
                put("message", message)
                put("tree", treeSha)
                put("parents", buildJsonArray { add(JsonPrimitive(parentSha)) })
            }
        ) ?: error("创建 Git commit 失败")
        return response["sha"]?.jsonPrimitive?.contentOrNull ?: error("Git commit 未返回 SHA")
    }

    suspend fun createRef(repository: String, branch: String, sha: String, token: String) {
        raw(
            HttpMethod.Post,
            "/repos/$repository/git/refs",
            token,
            buildJsonObject {
                put("ref", "refs/heads/$branch")
                put("sha", sha)
            }
        )
    }

    suspend fun updateRef(repository: String, branch: String, sha: String, token: String) {
        raw(
            HttpMethod.Patch,
            "/repos/$repository/git/refs/heads/${branch.encodeURLPathPart()}",
            token,
            buildJsonObject {
                put("sha", sha)
                put("force", false)
            }
        )
    }

    suspend fun createPullRequest(
        repository: String,
        title: String,
        body: String,
        head: String,
        base: String,
        token: String
    ): PullRequestResult {
        val response = raw(
            HttpMethod.Post,
            "/repos/$repository/pulls",
            token,
            buildJsonObject {
                put("title", title)
                put("body", body)
                put("head", head)
                put("base", base)
                put("draft", true)
            }
        ) ?: error("创建 Pull Request 失败")
        val number = response["number"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
        val url = response["html_url"]?.jsonPrimitive?.contentOrNull.orEmpty()
        return PullRequestResult(number, url)
    }

    private suspend fun raw(
        method: HttpMethod,
        path: String,
        token: String,
        body: JsonElement? = null,
        allowNotFound: Boolean = false
    ): JsonObject? {
        val response = client.request("https://api.github.com$path") {
            this.method = method
            header(HttpHeaders.Accept, "application/vnd.github+json")
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            header("X-GitHub-Api-Version", "2022-11-28")
            header(HttpHeaders.Authorization, "Bearer $token")
            if (body != null) setBody(body)
        }
        val text = response.bodyAsText()
        if (allowNotFound && response.status.value == 404) return null
        if (!response.status.isSuccess()) {
            val detail = runCatching {
                json.parseToJsonElement(text).jsonObject["message"]?.jsonPrimitive?.contentOrNull
            }.getOrNull()
            error("GitHub ${response.status.value}: ${detail ?: text.take(300)}")
        }
        if (text.isBlank()) return buildJsonObject { }
        return json.parseToJsonElement(text).jsonObject
    }
}
