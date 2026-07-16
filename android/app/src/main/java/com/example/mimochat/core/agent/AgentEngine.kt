package com.example.mimochat.core.agent

import com.example.mimochat.core.workspace.GitHubWorkspace
import com.example.mimochat.core.workspace.WorkspaceChange
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

enum class ApprovalKind { FILE_WRITE, FILE_DELETE, GIT_BRANCH, GIT_PUSH, PULL_REQUEST }

data class AgentApproval(
    val id: String = UUID.randomUUID().toString(),
    val kind: ApprovalKind,
    val title: String,
    val description: String,
    val diff: String = ""
)

class ApprovalManager {
    private val mutex = Mutex()
    private val _pending = MutableStateFlow<AgentApproval?>(null)
    val pending: StateFlow<AgentApproval?> = _pending.asStateFlow()
    private var deferred: CompletableDeferred<Boolean>? = null

    suspend fun request(approval: AgentApproval): Boolean {
        val waiter = mutex.withLock {
            check(deferred == null) { "已有操作等待用户确认" }
            CompletableDeferred<Boolean>().also {
                deferred = it
                _pending.value = approval
            }
        }
        return waiter.await()
    }

    fun approve() = resolve(true)
    fun deny() = resolve(false)
    fun cancelPending() = resolve(false)

    private fun resolve(approved: Boolean) {
        val current = deferred ?: return
        deferred = null
        _pending.value = null
        current.complete(approved)
    }
}

data class AgentToolCall(
    val index: Int,
    val id: String,
    val name: String,
    val argumentsJson: String
)

data class AgentToolResult(
    val callId: String,
    val name: String,
    val content: String,
    val display: String,
    val failed: Boolean = false
)

class AgentToolExecutor(
    private val workspace: GitHubWorkspace,
    private val approvals: ApprovalManager
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun definitions(): List<JsonObject> = listOf(
        tool(
            name = "list_files",
            description = "列出当前本地项目工作区中的文件。支持 glob 模式，例如 **/*.kt。",
            properties = buildJsonObject {
                put("pattern", stringSchema("可选 glob 文件模式"))
                put("limit", integerSchema("最大返回数量，默认 300"))
            }
        ),
        tool(
            name = "grep_files",
            description = "在当前项目文件中搜索文本或正则表达式，并返回路径、行号和匹配行。",
            properties = buildJsonObject {
                put("query", stringSchema("搜索文本或正则表达式"))
                put("glob", stringSchema("可选文件模式，例如 **/*.kt"))
                put("limit", integerSchema("最大匹配数量，默认 100"))
            },
            required = listOf("query")
        ),
        tool(
            name = "read_file",
            description = "读取当前项目中的文本文件。路径必须相对于项目根目录。",
            properties = buildJsonObject {
                put("path", stringSchema("相对于项目根目录的文件路径"))
                put("start_line", integerSchema("起始行，默认 1"))
                put("end_line", integerSchema("可选结束行"))
            },
            required = listOf("path")
        ),
        tool(
            name = "write_file",
            description = "创建新文件或完整覆盖现有文件。执行前 App 会展示 Diff 并请求用户确认。",
            properties = buildJsonObject {
                put("path", stringSchema("相对于项目根目录的文件路径"))
                put("content", stringSchema("要写入的完整 UTF-8 文本"))
            },
            required = listOf("path", "content")
        ),
        tool(
            name = "edit_file",
            description = "通过唯一的精确文本匹配修改现有文件。执行前 App 会展示 Diff 并请求用户确认。",
            properties = buildJsonObject {
                put("path", stringSchema("相对于项目根目录的文件路径"))
                put("old_text", stringSchema("必须在文件中唯一出现的原文"))
                put("new_text", stringSchema("替换后的文本"))
            },
            required = listOf("path", "old_text", "new_text")
        ),
        tool(
            name = "delete_file",
            description = "删除项目中的文本文件。每次删除都必须获得用户明确确认。",
            properties = buildJsonObject {
                put("path", stringSchema("相对于项目根目录的文件路径"))
            },
            required = listOf("path")
        ),
        tool(
            name = "git_status",
            description = "查看本地工作区相对于上次同步或提交基线的新增、修改和删除文件。",
            properties = buildJsonObject { }
        ),
        tool(
            name = "git_diff",
            description = "查看本地工作区所有未提交修改的文本 Diff。",
            properties = buildJsonObject { }
        ),
        tool(
            name = "git_create_branch",
            description = "从配置的基础分支创建远程工作分支。禁止覆盖已有历史。",
            properties = buildJsonObject {
                put("branch", stringSchema("分支名，建议 agent/ 开头；为空时自动生成"))
            }
        ),
        tool(
            name = "git_commit_push",
            description = "把当前工作区的全部变更创建为一个 Git commit 并推送到工作分支。不会 force push。",
            properties = buildJsonObject {
                put("message", stringSchema("简洁明确的 commit message"))
            },
            required = listOf("message")
        ),
        tool(
            name = "github_create_pull_request",
            description = "从当前工作分支向配置的基础分支创建 Draft Pull Request。",
            properties = buildJsonObject {
                put("title", stringSchema("Pull Request 标题"))
                put("body", stringSchema("Pull Request Markdown 说明"))
            },
            required = listOf("title")
        )
    )

    suspend fun execute(call: AgentToolCall): AgentToolResult {
        return try {
            val args = parseArguments(call.argumentsJson)
            when (call.name) {
                "list_files" -> {
                    val paths = workspace.listFiles(
                        pattern = args.string("pattern").takeIf { it.isNotBlank() },
                        limit = args.int("limit", 300)
                    )
                    success(call, if (paths.isEmpty()) "工作区没有匹配文件" else paths.joinToString("\n"), "列出 ${paths.size} 个文件")
                }
                "grep_files" -> {
                    val output = workspace.grep(
                        query = args.requiredString("query"),
                        glob = args.string("glob").takeIf { it.isNotBlank() },
                        limit = args.int("limit", 100)
                    )
                    success(call, output, "搜索项目内容")
                }
                "read_file" -> {
                    val path = args.requiredString("path")
                    val output = workspace.readFile(
                        path = path,
                        startLine = args.int("start_line", 1),
                        endLine = args.optionalInt("end_line")
                    )
                    success(call, output, "读取 $path")
                }
                "write_file" -> {
                    val change = workspace.previewWrite(args.requiredString("path"), args.requiredString("content"))
                    approveAndApply(call, change, ApprovalKind.FILE_WRITE, "写入 ${change.path}")
                }
                "edit_file" -> {
                    val change = workspace.previewEdit(
                        path = args.requiredString("path"),
                        oldText = args.requiredString("old_text"),
                        newText = args.requiredString("new_text")
                    )
                    approveAndApply(call, change, ApprovalKind.FILE_WRITE, "修改 ${change.path}")
                }
                "delete_file" -> {
                    val change = workspace.previewDelete(args.requiredString("path"))
                    approveAndApply(call, change, ApprovalKind.FILE_DELETE, "删除 ${change.path}")
                }
                "git_status" -> {
                    val changes = workspace.status()
                    val output = if (changes.isEmpty()) {
                        "工作区干净"
                    } else {
                        changes.joinToString("\n") {
                            "${it.type.name.first()} ${it.path} (+${it.additions} -${it.deletions})"
                        }
                    }
                    success(call, output, "检查 Git 状态")
                }
                "git_diff" -> success(call, workspace.diff(), "查看工作区 Diff")
                "git_create_branch" -> {
                    val requested = args.string("branch")
                    val approved = approvals.request(
                        AgentApproval(
                            kind = ApprovalKind.GIT_BRANCH,
                            title = "创建 Git 工作分支",
                            description = requested.ifBlank { "将从基础分支创建一个 agent/* 工作分支" }
                        )
                    )
                    if (!approved) denied(call)
                    else {
                        val branch = workspace.ensureBranch(requested)
                        success(call, "已准备分支：$branch", "创建分支 $branch")
                    }
                }
                "git_commit_push" -> {
                    val message = args.requiredString("message")
                    val changes = workspace.status()
                    require(changes.isNotEmpty()) { "工作区没有可提交修改" }
                    val approved = approvals.request(
                        AgentApproval(
                            kind = ApprovalKind.GIT_PUSH,
                            title = "Commit 并推送 ${changes.size} 个文件",
                            description = message,
                            diff = workspace.diff(changes)
                        )
                    )
                    if (!approved) denied(call)
                    else {
                        val result = workspace.commitAndPush(message)
                        success(
                            call,
                            "分支：${result.branch}\nCommit：${result.commitSha}\n文件：${result.changedFiles}",
                            "推送 ${result.changedFiles} 个文件"
                        )
                    }
                }
                "github_create_pull_request" -> {
                    val title = args.requiredString("title")
                    val body = args.string("body")
                    val config = workspace.config()
                    val approved = approvals.request(
                        AgentApproval(
                            kind = ApprovalKind.PULL_REQUEST,
                            title = "创建 Draft Pull Request",
                            description = "${config.workingBranch} → ${config.baseBranch}\n$title"
                        )
                    )
                    if (!approved) denied(call)
                    else {
                        val result = workspace.createPullRequest(title, body)
                        success(call, "PR #${result.number}: ${result.url}", "创建 PR #${result.number}")
                    }
                }
                else -> failure(call, "未知工具：${call.name}")
            }
        } catch (e: Exception) {
            failure(call, e.message ?: "工具执行失败")
        }
    }

    private suspend fun approveAndApply(
        call: AgentToolCall,
        change: WorkspaceChange,
        kind: ApprovalKind,
        title: String
    ): AgentToolResult {
        val approved = approvals.request(
            AgentApproval(
                kind = kind,
                title = title,
                description = "${change.type.name.lowercase()} · +${change.additions} -${change.deletions}",
                diff = workspace.diff(listOf(change))
            )
        )
        if (!approved) return denied(call)
        workspace.apply(change)
        return success(call, "已更新 ${change.path}", title)
    }

    private fun parseArguments(raw: String): JsonObject {
        if (raw.isBlank()) return buildJsonObject { }
        return json.parseToJsonElement(raw).jsonObject
    }

    private fun success(call: AgentToolCall, content: String, display: String) =
        AgentToolResult(call.id, call.name, content, display)

    private fun failure(call: AgentToolCall, message: String) =
        AgentToolResult(call.id, call.name, "ERROR: $message", "${call.name} 失败：$message", failed = true)

    private fun denied(call: AgentToolCall) =
        AgentToolResult(call.id, call.name, "DENIED: 用户拒绝了该操作", "已拒绝 ${call.name}", failed = true)

    private fun tool(
        name: String,
        description: String,
        properties: JsonObject,
        required: List<String> = emptyList()
    ): JsonObject = buildJsonObject {
        put("type", "function")
        put("function", buildJsonObject {
            put("name", name)
            put("description", description)
            put("parameters", buildJsonObject {
                put("type", "object")
                put("properties", properties)
                put("additionalProperties", false)
                if (required.isNotEmpty()) {
                    put("required", buildJsonArray { required.forEach { add(JsonPrimitive(it)) } })
                }
            })
        })
    }

    private fun stringSchema(description: String) = buildJsonObject {
        put("type", "string")
        put("description", description)
    }

    private fun integerSchema(description: String) = buildJsonObject {
        put("type", "integer")
        put("description", description)
    }

    private fun JsonObject.requiredString(key: String): String =
        string(key).ifBlank { error("缺少参数：$key") }

    private fun JsonObject.string(key: String): String =
        this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

    private fun JsonObject.int(key: String, default: Int): Int =
        this[key]?.jsonPrimitive?.intOrNull ?: default

    private fun JsonObject.optionalInt(key: String): Int? =
        this[key]?.jsonPrimitive?.intOrNull
}
