你负责维护 GitHub 仓库：

```text
feihu1991/mimoChat
```

本项目的 Android APK 不要求在当前 MiMo Claw 环境中完成本地打包。代码修改完成后，必须推送到 GitHub，由 GitHub Actions 执行编译、测试、Lint 和 APK 打包。

你必须持续监控本次提交触发的 GitHub Actions，直到：

* CI 全部成功；
* 或确认存在需要修改代码的问题；
* 或达到自动修复上限并通知用户。

不得只在推送后立即宣称“修改完成”。

# 一、开始工作前

每次任务开始时，必须重新读取：

```text
AGENTS.md
.github/AI_REVIEW_WORKFLOW.md
```

并遵守以下永久规则：

1. 只在 `claw/*` 分支工作。
2. 不直接修改、推送或合并 `master`。
3. 同一个任务持续更新同一个 Pull Request。
4. 不创建重复 PR。
5. 不使用 force push。
6. 不自动合并 PR。
7. 不提交 Token、API Key 或其他凭证。
8. 不得通过删除测试、跳过任务或弱化断言让 CI 通过。

# 二、修改并推送代码

完成代码修改后：

1. 检查当前分支必须以 `claw/` 开头。
2. 检查工作区修改内容。
3. 确认没有凭证、临时文件、大型构建目录或无关内容。
4. 提交代码。
5. 推送到当前 PR 的同一个远程分支。

推送前执行：

```bash
git status
git diff --check
git branch --show-current
```

如果当前分支是 `master` 或不符合 `claw/*`：

* 立即停止；
* 不提交；
* 不推送；
* 通知用户处理。

提交信息应准确说明修改内容，例如：

```text
fix: address Codex review round 2 for PR #12
```

# 三、确定本次提交 SHA

推送后记录：

```bash
git rev-parse HEAD
```

保存为：

```text
EXPECTED_HEAD_SHA
```

后续只监控由这个 SHA 或当前 PR 最新 SHA 触发的 CI。

不得把其他分支、其他 PR 或旧提交的 CI 结果当作本次结果。

同时读取当前 PR 信息：

```bash
gh pr view \
  --repo feihu1991/mimoChat \
  --json number,url,headRefName,headRefOid,state,isDraft
```

确认：

* PR 仍然开放；
* PR 分支与当前分支一致；
* `headRefOid` 与本次推送 SHA 一致。

如果不一致，重新拉取状态并确认是否有其他提交者更新了该分支。

# 四、等待 GitHub Actions 被触发

推送后，通过 GitHub CLI 查询与当前提交相关的 Workflow Run。

优先使用当前提交 SHA 过滤：

```bash
gh run list \
  --repo feihu1991/mimoChat \
  --commit "$EXPECTED_HEAD_SHA" \
  --limit 20 \
  --json databaseId,name,workflowName,status,conclusion,headSha,event,url,createdAt
```

如果推送后暂时没有发现 Run：

1. 等待一段合理时间；
2. 再次查询；
3. 不要立即判断为失败；
4. 最多等待约 10 分钟确认 Workflow 是否触发。

如果 10 分钟后仍没有任何 Run：

* 检查 Workflow 触发条件；
* 检查分支名和文件路径过滤；
* 检查 GitHub Actions 是否被禁用；
* 检查工作流 YAML 是否有效；
* 将问题分类为 `WORKFLOW_NOT_TRIGGERED`；
* 不要重复无意义地推送空提交；
* 通知用户并说明排查结果。

# 五、监控 CI 直到结束

找到本次提交对应的 Run 后，持续监控：

```bash
gh run watch RUN_ID \
  --repo feihu1991/mimoChat \
  --exit-status
```

或者定期读取：

```bash
gh run view RUN_ID \
  --repo feihu1991/mimoChat \
  --json status,conclusion,jobs,url,headSha
```

监控期间必须确认：

```text
headSha == EXPECTED_HEAD_SHA
```

如果 PR 又出现了更新的 SHA：

* 停止监控旧 Run；
* 切换到最新 PR Head SHA；
* 重新定位最新 Run；
* 不要继续根据旧代码的结果修改当前分支。

# 六、成功标准

只有以下检查全部成功，才能判断本轮 CI 通过：

```text
assembleDebug
testDebugUnitTest
lintDebug
APK artifact 上传
```

如果项目 Workflow 的任务名称不同，应根据实际工作流识别对应任务，但至少必须覆盖：

1. Android Debug 编译；
2. 单元测试；
3. Android Lint；
4. APK 产物生成和上传。

必须确认整个 Workflow 的最终结论为：

```text
success
```

不能因为某一个 Job 成功，就忽略其他失败或取消的 Job。

CI 成功后：

1. 记录 Workflow Run ID 和 URL；
2. 记录本次 Head SHA；
3. 确认 APK Artifact 已生成；
4. 在当前 PR 发布评论；
5. 再触发 Codex Review。

PR 评论格式：

```text
本轮代码已推送，GitHub CI 验证通过。

提交 SHA：<HEAD_SHA>

验证结果：
- assembleDebug：通过
- testDebugUnitTest：通过
- lintDebug：通过
- APK artifact：已生成
- GitHub Actions：通过

@codex review

请复核上一轮 P0/P1 问题是否真正修复，并检查本次提交是否引入新的阻断问题。
```

不得在 CI 尚未结束时发布“全部通过”。

# 七、CI 失败时读取日志

如果 Workflow 失败，必须读取失败 Job 和 Step 的日志。

优先执行：

```bash
gh run view RUN_ID \
  --repo feihu1991/mimoChat \
  --log-failed
```

同时获取 Job 信息：

```bash
gh run view RUN_ID \
  --repo feihu1991/mimoChat \
  --json jobs,status,conclusion,url,headSha
```

分析时必须提取：

1. 失败的 Job；
2. 失败的 Step；
3. 第一处有意义的编译、测试或 Lint 错误；
4. 涉及的文件和行号；
5. 错误根因；
6. 是否与本次提交有关；
7. 是否属于 GitHub 基础设施故障。

不要只复制最后几十行堆栈，也不要只说“CI 失败”。

# 八、失败分类

将失败分为以下类型：

```text
CODE_FAILURE
TEST_FAILURE
LINT_FAILURE
WORKFLOW_FAILURE
INFRASTRUCTURE_FAILURE
CANCELLED_OR_SUPERSEDED
UNCERTAIN
```

## CODE_FAILURE

包括：

* Kotlin 或 Java 编译错误；
* 缺少 import；
* 类型不匹配；
* API 使用错误；
* Room、KSP、Gradle 配置错误；
* Android 资源编译错误。

处理方式：

* 修改生产代码或构建配置；
* 增加必要测试；
* 提交并推送；
* 重新监控新 SHA 的 CI。

## TEST_FAILURE

包括：

* 单元测试失败；
* 回归测试暴露真实逻辑问题；
* 测试超时且由代码死锁或协程未释放造成。

处理方式：

* 优先修复生产代码；
* 只有测试本身确实错误时才修改测试；
* 不得删除测试；
* 不得弱化断言；
* 不得将测试标记为 ignored；
* 不得跳过 `testDebugUnitTest`。

## LINT_FAILURE

包括：

* Android Lint 阻断；
* Manifest 或权限问题；
* 资源、生命周期或兼容性问题。

处理方式：

* 修复实际问题；
* 不得通过全局关闭 Lint 或大范围 `suppress` 掩盖问题；
* 只有明确确认是误报时，才能做范围最小且有注释的 suppression。

## WORKFLOW_FAILURE

包括：

* Workflow YAML 语法错误；
* working-directory 错误；
* Java 或 Android 环境配置错误；
* Artifact 路径错误；
* 命令路径错误。

处理方式：

* 修复 `.github/workflows/` 中的工作流；
* 保证 Workflow 仍真实执行编译、测试和 Lint；
* 不得删除失败任务来让工作流变绿。

## INFRASTRUCTURE_FAILURE

包括：

* GitHub Runner 临时故障；
* GitHub 服务异常；
* Artifact 服务异常；
* 网络下载临时失败；
* 依赖仓库短时不可用；
* Runner 无磁盘空间；
* 与代码无关的 Action 服务故障。

处理方式：

1. 不立即修改代码；
2. 对同一 SHA 重新运行失败 Job 或 Workflow；
3. 基础设施重试最多两次；
4. 两次仍失败则通知用户；
5. 不通过推送空提交触发重复构建。

可以使用：

```bash
gh run rerun RUN_ID \
  --repo feihu1991/mimoChat \
  --failed
```

重新运行前必须确认它确实属于基础设施问题。

## CANCELLED_OR_SUPERSEDED

如果 Run 被取消是因为分支已经推送了更新代码：

* 不处理旧 Run；
* 查找最新 Head SHA 对应的 Run；
* 监控最新 Run。

## UNCERTAIN

如果无法可靠判断失败原因：

* 不盲目修改；
* 不重复推送；
* 整理日志摘要；
* 标记需要人工处理；
* 通知用户。

# 九、自动修复循环

如果确认属于代码、测试、Lint 或 Workflow 问题：

1. 拉取当前 PR 最新代码；
2. 确认 Head SHA 未被其他提交更新；
3. 阅读完整失败日志；
4. 定位根因；
5. 修改代码；
6. 增加或调整必要的回归测试；
7. 提交；
8. 推送到同一个 `claw/*` 分支；
9. 记录新的 Head SHA；
10. 监控新 SHA 对应的 GitHub Actions；
11. 重复直到成功或达到上限。

单次任务最多允许三轮“修改代码并重新推送”。

轮次定义：

* 一次代码修改、提交并推送，算一轮；
* 仅重新运行基础设施失败的 Workflow 不算代码整改轮次；
* 仅轮询状态不算轮次；
* 旧 Run 被新 Run 替代不算轮次。

达到三轮后仍然失败：

1. 停止继续修改；
2. 不推送更多试探性代码；
3. 给 PR 添加 `human-review-required`；
4. 发布失败摘要；
5. 通知用户。

评论格式：

```text
MiMo Claw 已达到三轮自动 CI 修复上限，当前 GitHub Actions 仍未通过。

最新提交：<HEAD_SHA>
Workflow Run：<RUN_URL>

失败分类：<FAILURE_TYPE>

失败任务：
- <JOB>
- <STEP>

主要错误：
<ERROR_SUMMARY>

已尝试：
1. ...
2. ...
3. ...

自动修改已停止，需要人工处理。
```

# 十、并发和代码更新保护

在分析失败或修改代码前，必须再次确认：

```bash
gh pr view PR_NUMBER \
  --repo feihu1991/mimoChat \
  --json headRefOid,headRefName,state
```

如果远程 Head SHA 与当前本地基线不一致：

* 说明有人或其他 Agent 推送了新代码；
* 放弃基于旧日志的修改；
* 拉取最新代码；
* 重新查找新 SHA 的 CI；
* 不要把旧错误修复到新代码上。

同一 PR 同时只能有一个 MiMo Claw CI 修复任务。

必须使用持久化锁，锁标识：

```text
feihu1991-mimoChat-pr-<PR_NUMBER>-ci
```

任务结束、成功、失败或异常退出后都必须释放锁。

# 十一、Artifact 检查

CI 成功后，确认 APK Artifact 存在。

查询：

```bash
gh run view RUN_ID \
  --repo feihu1991/mimoChat \
  --json artifacts
```

如果当前 GitHub CLI 版本不支持直接读取 artifacts，可使用 GitHub API：

```bash
gh api \
  repos/feihu1991/mimoChat/actions/runs/RUN_ID/artifacts
```

至少确认：

* Artifact 数量大于 0；
* Artifact 未过期；
* 名称符合 APK 构建产物；
* Artifact 对应当前 Run；
* Run 的 Head SHA 对应当前 PR 最新提交。

如果编译任务成功但 Artifact 上传失败：

* 整个打包流程不能视为完成；
* 应读取 Artifact 上传失败日志；
* 根据失败类型修复或重试。

# 十二、不要做的事情

禁止：

1. 直接修改、推送或合并 `master`。
2. 创建重复 PR。
3. force push。
4. 推送空提交只为反复触发 CI。
5. 删除失败测试。
6. 跳过 `testDebugUnitTest`。
7. 跳过 `lintDebug`。
8. 通过 `continue-on-error` 掩盖失败。
9. 将关键 Job 改成非阻断。
10. 全局关闭 Lint。
11. 把单个 Job 成功误报成整个 CI 成功。
12. 监控不属于当前 Head SHA 的 Workflow。
13. 在 CI 仍运行时宣称完成。
14. 在日志或评论中输出 Token、认证 Header或环境变量。
15. 因 GitHub 临时故障盲目修改生产代码。
16. 自动下载并执行不可信 Artifact。
17. 自动合并 PR。

# 十三、完成条件

只有同时满足以下条件，本轮代码修改才算完成：

1. 修改已经推送到同一个 `claw/*` 分支；
2. 当前 PR Head SHA 与监控的 SHA 一致；
3. GitHub Actions 最终状态为 `success`；
4. `assembleDebug` 成功；
5. `testDebugUnitTest` 成功；
6. `lintDebug` 成功；
7. APK Artifact 已成功生成；
8. PR 已发布 CI 通过说明；
9. 已评论 `@codex review`；
10. 没有自动合并 `master`。

满足后停止本轮 CI 监控，等待 Codex Review 或下一次 cron。

# 十四、每次任务的最终报告

每次任务结束时，向用户报告：

```text
PR：
分支：
最新提交 SHA：
Workflow Run：
CI 最终状态：
assembleDebug：
testDebugUnitTest：
lintDebug：
APK Artifact：
代码修复轮次：
基础设施重试次数：
是否已触发 @codex review：
是否需要人工处理：
```

如果成功，明确说明：

```text
本次提交已由 GitHub Actions 完成编译、测试、Lint 和 APK 打包，当前未自动合并 master。
```

如果失败，必须说明：

* 失败分类；
* 第一处真实错误；
* 已尝试的修复；
* 为什么停止；
* 用户下一步需要查看什么。