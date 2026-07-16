# MiMo Code Android Agent 能力边界

MiMo Code 手机端采用“模型负责决策、Android App 负责执行”的结构。MiMo 只能提出结构化工具调用，不能直接获得手机文件权限、GitHub Token 或任意系统命令执行权限。

## 已实现能力

### 本地项目工作区

- 从指定 GitHub 仓库和基础分支同步文本文件到 App 私有目录
- 自动排除 `.git`、`build`、`.gradle`、`node_modules`、二进制文件等内容
- 限制同步文件数、总体积和单文件大小，避免超大仓库耗尽手机资源
- 通过 canonical path 校验阻止 `../` 路径越界和工作区元数据访问

### Agent 文件工具

- `list_files`：按 glob 模式列出文件
- `grep_files`：搜索项目文本并返回路径和行号
- `read_file`：按行读取文本文件
- `write_file`：创建或完整覆盖文件
- `edit_file`：通过唯一精确匹配修改文件
- `delete_file`：删除文件
- 文件写入和删除执行前展示 Diff，并要求用户明确确认

### Git 与 GitHub

- `git_status`：查看工作区变更
- `git_diff`：查看未提交 Diff
- `git_create_branch`：从基础分支创建远程工作分支
- `git_commit_push`：通过 GitHub Git Data API 创建 blob、tree、commit 并更新分支引用
- `github_create_pull_request`：创建 Draft Pull Request
- 分支更新固定使用非强制方式，不提供 force push
- 建分支、Commit/Push 和创建 PR 都需要用户确认

### Agent 循环

1. App 把消息和可用工具定义发送给 MiMo。
2. MiMo 返回文本或结构化工具调用。
3. App 验证参数和权限，在本地执行工具。
4. App 把工具结果作为 `tool` 消息回传 MiMo。
5. MiMo 继续分析，直到完成任务或达到安全步数上限。

## 凭据安全

- MiMo API Key 和 GitHub Token 使用 `EncryptedSharedPreferences` 保存
- GitHub Token 只进入本地 GitHub HTTP 客户端，不进入模型消息、工具结果或聊天记录
- 设置页明确展示工作仓库、基础分支和当前工作分支

## 当前限制

- 第一版只支持 GitHub HTTPS API，不支持 GitLab、Gitee、SSH Key 或本地裸 Git 协议
- 不实现 merge、rebase、冲突解决、Git LFS、submodule、signed commit 或 hooks
- 不运行 Gradle、Maven、Node、Python、Go、Rust 等构建工具链
- 不提供 APK/AAB 打包、服务器部署、应用商店发布或任意 Bash/PowerShell
- 不下载并执行未知二进制文件
- CI 可以验证 App 编译、单元测试和 Lint，但真实 MiMo 与 GitHub 凭据仍需在设备上进行端到端验证

## 设计原则

1. 会话和任务是界面主线，参考 Happy 的 session-first 信息架构。
2. 只暴露明确、可审计的结构化工具，不给模型任意系统权限。
3. 文件写入和远程 Git 操作始终经过用户确认。
4. 凭据永不进入模型上下文。
5. 手机端聚焦代码阅读、修改和 GitHub 协作，不复制完整桌面开发环境。
