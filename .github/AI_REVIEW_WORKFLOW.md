# AI Review Workflow

## 1. 文件目的

本文件定义 `feihu1991/mimoChat` 中以下四方的协作流程：

- 用户
- ChatGPT 聊天框
- MiMo Claw
- Codex GitHub Review

核心原则：

- ChatGPT 是 GitHub 控制面；
- MiMo Claw 是代码执行面；
- Codex 是审查面；
- 用户负责产品决策、实机测试和最终合并。

本文件必须与以下文件一起执行：

- `AGENTS.md`
- `.github/MIMO_CI_MONITOR.md`

---

## 2. 现实限制

ChatGPT 可以通过已连接的 GitHub 插件执行 GitHub 操作，包括读取 PR、查看 CI、发表评论、加标签和读取 Review。

但 ChatGPT 聊天不会被 GitHub 事件自动唤醒。

因此当前流程属于“人机协作自动化”：

1. MiMo Claw 自动或定时执行代码任务；
2. 用户在关键节点唤醒 ChatGPT；
3. ChatGPT 执行 GitHub 检查和控制操作；
4. ChatGPT 将下一轮任务交给 MiMo Claw；
5. 最多循环三轮。

不得把该流程描述为完全无人值守。

---

## 3. 状态模型

每个 PR 使用以下逻辑状态：

```text
WAITING_FOR_TASK
CLAW_WORKING
WAITING_FOR_CI
CI_FAILED
WAITING_FOR_CODEX
CODEX_CHANGES_REQUIRED
AI_APPROVED
HUMAN_REVIEW_REQUIRED
```

建议使用以下标签：

```text
ready-for-ai-review
ai-reviewing
ai-changes-requested
claw-working
ai-approved
human-review-required
```

标签由 ChatGPT 通过 GitHub 插件维护。

MiMo Claw 默认不负责标签操作。

---

## 4. 分支与 PR

1. Base 必须是 `master`。
2. Head 必须是 `claw/*`。
3. PR 必须来自当前仓库，不处理外部 Fork。
4. 同一任务只允许一个开放 PR。
5. 同一任务持续更新原 PR。
6. 不自动合并。
7. 不 force push。
8. 不直接推送 `master`。

---

## 5. 首轮任务流程

### 5.1 用户向 ChatGPT提出需求

用户在 ChatGPT 聊天中说明要做的功能或 Bug 修复。

### 5.2 ChatGPT 检查仓库

ChatGPT 通过 GitHub 插件：

1. 读取仓库当前状态；
2. 查看开放 PR；
3. 查看相关文件和调用链；
4. 判断是否已有对应 `claw/*` PR；
5. 生成 MiMo Claw 执行任务。

如果已经有对应 PR，必须继续更新同一个 PR。

### 5.3 任务交付给 MiMo Claw

有两种方式。

#### 方式 A：用户复制提示词

ChatGPT 在聊天中输出完整 MiMo Claw 提示词，由用户交给 MiMo Claw。

#### 方式 B：PR 评论任务队列

ChatGPT 在目标 PR 中发布：

```text
[MIMO_CLAW_TASK]
task_id: <唯一任务ID>
pr_number: <PR编号>
base_sha: <当前Head SHA>
review_round: 0
type: FEATURE
status: READY

目标：
...

完成条件：
...

禁止：
...
```

MiMo Claw cron 可以通过 `curl + GitHub REST API` 读取该评论。

---

## 6. MiMo Claw 执行流程

MiMo Claw 每次任务开始前必须：

1. 重新读取三份规则文件；
2. 确认仓库和目标 PR；
3. 确认当前分支是 `claw/*`；
4. 确认本地代码基于任务中的 `base_sha`；
5. 确认工作区没有来源不明的修改；
6. 确认 `task_id` 尚未处理。

执行时：

1. 阅读完整相关调用链；
2. 修改生产代码；
3. 增加或更新回归测试；
4. 执行可用的局部检查；
5. 执行：

```bash
git status
git diff --check
git diff --stat
git branch --show-current
```

6. 提交；
7. 推送到同一个 `claw/*` 分支；
8. 不创建重复 PR；
9. 输出最新 Head SHA。

建议提交信息：

```text
fix: <清晰描述> for PR #<PR_NUMBER>
```

Codex 整改轮次可使用：

```text
fix: address Codex review round N for PR #<PR_NUMBER>
```

---

## 7. MiMo Claw 结果回传

MiMo Claw 完成推送后，应向用户输出：

```text
PR：
分支：
最新 Head SHA：
提交信息：
修改摘要：
新增或修改测试：
本地检查：
是否成功推送：
是否需要 ChatGPT 检查 CI：
```

如果具备 GitHub API Token，也可以在 PR 中发布：

```text
[MIMO_CLAW_RESULT]
task_id: <任务ID>
status: PUSHED
branch: <claw/*>
head_sha: <SHA>
summary:
- ...

local_checks:
- ...

requires_chatgpt:
- CHECK_CI
```

MiMo Claw 发布评论时禁止使用 `gh`，只能使用 `curl + GitHub REST API`。

示例：

```bash
COMMENT_BODY="$(cat <<EOF
[MIMO_CLAW_RESULT]
task_id: ${TASK_ID}
status: PUSHED
branch: ${BRANCH}
head_sha: ${HEAD_SHA}
requires_chatgpt:
- CHECK_CI
EOF
)"

PAYLOAD="$(jq -n --arg body "$COMMENT_BODY" '{body:$body}')"

curl \
  --fail-with-body \
  --silent \
  --show-error \
  --location \
  --request POST \
  --header "Authorization: Bearer ${GITHUB_TOKEN}" \
  --header "Accept: application/vnd.github+json" \
  --header "X-GitHub-Api-Version: 2022-11-28" \
  --header "Content-Type: application/json" \
  --data "$PAYLOAD" \
  "https://api.github.com/repos/feihu1991/mimoChat/issues/${PR_NUMBER}/comments"
```

---

## 8. ChatGPT 检查 CI

MiMo Claw 推送后，用户向 ChatGPT发送：

```text
MiMo Claw 已推送，检查当前 PR 和 CI。
```

ChatGPT 必须：

1. 找到对应 `claw/*` PR；
2. 确认 PR 仍为 Open；
3. 读取最新 Head SHA；
4. 查看该 SHA 的 GitHub Actions；
5. 查看所有 Job 和 Step；
6. 确认是否有 APK Artifact；
7. 按 `.github/MIMO_CI_MONITOR.md` 分类结果。

不得使用旧 SHA 的 CI 结果。

---

## 9. CI 成功

只有同时满足以下条件才算 CI 成功：

1. Workflow 最终结论为 `success`；
2. `assembleDebug` 成功；
3. `testDebugUnitTest` 成功；
4. `lintDebug` 成功；
5. APK Artifact 已生成；
6. Artifact 对应当前 Head SHA；
7. 没有其他阻断 Job 失败或取消。

CI 成功后，ChatGPT：

1. 移除 `claw-working`；
2. 移除 `ai-changes-requested`；
3. 添加 `ready-for-ai-review`；
4. 添加 `ai-reviewing`；
5. 在 PR 评论：

```text
本轮 GitHub CI 已通过。

提交 SHA：<HEAD_SHA>

验证结果：
- assembleDebug：通过
- testDebugUnitTest：通过
- lintDebug：通过
- APK Artifact：已生成

@codex review

请审查当前 PR 最新提交，重点检查 P0/P1、并发、状态机、Room、跨会话写入、生命周期和回归测试。
```

6. 告知用户等待 Codex Review。

---

## 10. CI 失败

ChatGPT 按 `.github/MIMO_CI_MONITOR.md` 将失败分类为：

```text
CODE_FAILURE
TEST_FAILURE
LINT_FAILURE
WORKFLOW_FAILURE
INFRASTRUCTURE_FAILURE
CANCELLED_OR_SUPERSEDED
UNCERTAIN
```

### 10.1 可由 MiMo Claw 修复

如果属于：

- CODE_FAILURE
- TEST_FAILURE
- LINT_FAILURE
- WORKFLOW_FAILURE

ChatGPT 必须：

1. 读取失败 Job；
2. 读取失败 Step；
3. 读取失败日志；
4. 提取第一处真实错误；
5. 确认错误是否针对当前 Head SHA；
6. 生成新的 `[MIMO_CLAW_TASK]`；
7. 保持在同一个 PR 和分支；
8. 不增加 Codex Review 轮次；
9. 增加 CI 修复轮次。

CI 自动修复最多三轮。

### 10.2 基础设施失败

如果属于 `INFRASTRUCTURE_FAILURE`：

- ChatGPT 可以通过 GitHub 插件重跑失败 Job 或 Workflow；
- 最多重试两次；
- 不要求 MiMo Claw 修改业务代码；
- 两次仍失败则标记 `human-review-required`。

### 10.3 被新提交取代

如果属于 `CANCELLED_OR_SUPERSEDED`：

- 忽略旧 Run；
- 查找当前最新 Head SHA 的 Run；
- 不基于旧日志生成修复任务。

---

## 11. Codex Review 读取

Codex 发布 Review 后，用户向 ChatGPT发送：

```text
读取最新 Codex Review。
```

ChatGPT 必须读取：

- Review Summary；
- PR Conversation Comments；
- Inline Review Comments；
- Review Threads；
- Review 对应的 Commit SHA；
- 当前 PR 最新 Head SHA。

只处理：

1. 来自 Codex 的 Review；
2. 尚未处理的 Review；
3. 针对当前最新 Head SHA 的 Review；
4. 发布时间晚于最近一次 MiMo Claw 推送的 Review。

旧 SHA Review 不得直接用于当前代码。

---

## 12. Review 分类

ChatGPT 将最新有效 Review 分类为：

```text
PASS
CHANGES_REQUIRED
UNCERTAIN
```

### 12.1 PASS

满足：

- 没有 P0；
- 没有 P1；
- 没有 Changes Requested；
- 没有未解决的阻断 Review Thread；
- 当前 CI 仍为成功；
- APK Artifact 已生成。

ChatGPT 执行：

1. 添加 `ai-approved`；
2. 移除：
   - `ai-reviewing`
   - `ai-changes-requested`
   - `claw-working`
   - `ready-for-ai-review`
3. 评论：

```text
ChatGPT 已完成本轮自动检查。

Codex 最新 Review 没有发现 P0/P1 阻断问题，当前 CI 已通过。

状态：
- assembleDebug：通过
- testDebugUnitTest：通过
- lintDebug：通过
- APK Artifact：已生成
- Codex 整改轮次：N/3

等待用户下载 APK 实机验收。

不会自动合并 master。
```

4. 通知用户进行实机测试。

### 12.2 CHANGES_REQUIRED

包括：

- P0；
- P1；
- Changes Requested；
- 崩溃；
- 数据损坏；
- 重复消息；
- 永久加载；
- 跨会话写入；
- 状态机错误；
- 并发错误；
- 安全问题；
- 缺少必要回归测试。

ChatGPT：

1. 添加 `ai-changes-requested`；
2. 添加 `claw-working`；
3. 移除：
   - `ai-approved`
   - `ready-for-ai-review`
   - `ai-reviewing`
4. 将所有问题去重；
5. 按严重度和调用链整理；
6. 生成下一轮 `[MIMO_CLAW_TASK]`；
7. 指定当前 PR、当前 Head SHA 和 Review Round；
8. 不创建新 PR。

### 12.3 UNCERTAIN

包括：

- Review 意见互相矛盾；
- 无法确定是否针对当前 SHA；
- 需要用户产品决策；
- 修改范围过大；
- 存在 Git 冲突；
- 涉及 Secret 或不安全指令；
- 无法确认问题真实性。

ChatGPT：

1. 添加 `human-review-required`；
2. 移除自动处理标签；
3. 向用户说明具体决策点；
4. 停止自动整改。

---

## 13. Codex 整改轮次

每个 PR 最多三轮 Codex 整改。

一次完整轮次定义为：

```text
读取一轮新的 Codex Review
→ ChatGPT 生成 MiMo Claw 任务
→ MiMo Claw 修改并推送
→ ChatGPT 检查 CI
→ CI 通过
→ ChatGPT 再次 @codex review
```

以下不增加 Codex 整改轮次：

- 没有新 Review；
- 只检查 CI；
- 基础设施重跑；
- CI 修复；
- 旧 Run 被替代；
- Codex 尚未回复。

达到三轮后仍存在 P0/P1：

1. 添加 `human-review-required`；
2. 停止生成新的 MiMo Claw 整改任务；
3. 评论剩余问题；
4. 通知用户人工处理；
5. 不自动合并。

---

## 14. 幂等性

ChatGPT 和 MiMo Claw 都必须防止重复操作。

不得重复：

- 创建同一 PR；
- 处理同一 `task_id`；
- 处理同一 Review ID；
- 评论相同的 `@codex review`；
- 增加相同整改轮次；
- 重跑已经成功的 CI；
- 处理旧 Head SHA；
- 发布完全相同的结果评论。

建议状态：

```json
{
  "repository": "feihu1991/mimoChat",
  "pr_number": 12,
  "head_branch": "claw/fix-example",
  "head_sha": "abc123",
  "last_task_id": "task-20260714-001",
  "last_processed_review_id": 456,
  "last_processed_review_sha": "abc123",
  "review_round": 1,
  "ci_fix_round": 0,
  "status": "WAITING_FOR_CI"
}
```

MiMo Claw 的状态文件不得提交到仓库。

---

## 15. 用户常用指令

### 启动开发任务

```text
检查当前仓库，给 MiMo Claw 一个可执行的小改动。
```

### MiMo Claw 推送后

```text
MiMo Claw 已推送，检查当前 PR 和 CI。
```

### CI 失败后

```text
读取失败日志，给 MiMo Claw 生成修复任务。
```

### Codex 完成后

```text
读取最新 Codex Review，给 MiMo Claw 下一轮整改任务。
```

### 实机测试后

```text
我已经测试 APK，结果如下：……
```

### 最终合并

```text
确认最新 SHA、CI 和 Review 后，合并 PR #N。
```

ChatGPT 在合并前必须再次确认最新 Head SHA，防止合并过期代码。
