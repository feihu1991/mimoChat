# MIMO CI Monitor

## 1. 文件定位

本文件定义 `feihu1991/mimoChat` 的 GitHub Actions 验收标准和失败处理规范。

名称保留为 `MIMO_CI_MONITOR.md`，但当前主流程中：

- ChatGPT 通过 GitHub 插件负责读取和控制 GitHub Actions；
- MiMo Claw 负责修改代码并推送；
- MiMo Claw 不需要使用 `gh`；
- 用户在 MiMo Claw 推送后唤醒 ChatGPT 检查 CI。

本文件不是要求 MiMo Claw 在单次任务中无限等待 CI。

---

## 2. 触发点

MiMo Claw 推送代码后必须输出：

```text
PR：
分支：
最新 Head SHA：
提交信息：
是否成功推送：
需要 ChatGPT 执行：CHECK_CI
```

然后结束本次代码任务。

用户向 ChatGPT发送：

```text
MiMo Claw 已推送，检查当前 PR 和 CI。
```

ChatGPT 开始 CI 验收。

---

## 3. ChatGPT CI 检查流程

ChatGPT 必须：

1. 找到对应开放 PR；
2. 确认 Base 是 `master`；
3. 确认 Head 是 `claw/*`；
4. 读取 PR 当前最新 Head SHA；
5. 读取该 SHA 对应的 Workflow Run；
6. 读取所有 Job 和 Step；
7. 检查最终状态和 Conclusion；
8. 检查 APK Artifact；
9. 如果失败，读取失败 Job 日志；
10. 按本文件分类处理。

不得把其他分支、其他 PR 或旧提交的 CI 结果当作当前结果。

---

## 4. CI 等待策略

### 4.1 Workflow 尚未触发

推送后短时间内可能没有 Run。

ChatGPT 应：

1. 确认 Workflow 触发条件；
2. 确认 PR Head SHA；
3. 确认工作流文件存在；
4. 在用户再次要求检查时重新读取。

ChatGPT 不能在后台持续运行，也不能承诺稍后自动返回。

如果 10 分钟以上仍没有 Run，分类为：

```text
WORKFLOW_NOT_TRIGGERED
```

可能原因：

- Workflow 的 branch 过滤不匹配；
- path 过滤不匹配；
- Workflow YAML 无效；
- Actions 被禁用；
- 提交未真正推送；
- PR Head SHA 与预期不一致。

不得通过推送空提交反复触发。

### 4.2 Workflow 运行中

如果状态仍为：

```text
queued
in_progress
waiting
pending
```

ChatGPT 应明确报告当前状态。

用户稍后再次发送：

```text
继续检查当前 PR 的 CI。
```

ChatGPT 再次读取。

---

## 5. 成功标准

只有以下条件全部满足，CI 才算通过：

1. 当前 PR Head SHA 与 Workflow Run Head SHA 一致；
2. Workflow 最终状态是 `completed`；
3. Workflow 结论是 `success`；
4. `assembleDebug` 或等效 Android Debug 编译任务成功；
5. `testDebugUnitTest` 或等效 JVM 单元测试任务成功；
6. `lintDebug` 或等效 Android Lint 任务成功；
7. APK Artifact 上传成功；
8. Artifact 未过期；
9. Artifact 对应当前 Run；
10. 没有其他阻断 Job 失败、取消或超时。

不能因为某一个 Job 成功就宣称整个 CI 成功。

---

## 6. APK Artifact 验收

ChatGPT 必须检查 Artifact：

- 数量大于 0；
- 未过期；
- 名称与 APK 或 Android Debug 构建产物一致；
- 对应当前 Workflow Run；
- 当前 Run 对应 PR 最新 Head SHA。

常见有效名称可能包含：

```text
apk
debug-apk
app-debug
android-debug
mimoChat-debug
```

如果只存在：

- 测试报告；
- Lint 报告；
- 日志；
- Coverage；

不能视为 APK 已生成。

如果编译成功但 Artifact 上传失败，整个打包流程仍视为失败。

---

## 7. CI 成功后的操作

CI 成功后，ChatGPT：

1. 记录 PR 编号；
2. 记录 Head SHA；
3. 记录 Workflow Run；
4. 记录 Artifact；
5. 更新 PR 标签；
6. 在 PR 中发布：

```text
本轮代码已由 GitHub Actions 验证通过。

提交 SHA：<HEAD_SHA>

验证结果：
- assembleDebug：通过
- testDebugUnitTest：通过
- lintDebug：通过
- APK Artifact：已生成
- GitHub Actions：通过

@codex review

请审查当前 PR 最新提交，重点检查上一轮问题是否真正修复，以及是否引入新的 P0/P1、并发、状态机、Room、生命周期或回归问题。
```

7. 通知用户等待 Codex Review。

不得在 CI 未完成时发布“全部通过”。

---

## 8. CI 失败信息提取

CI 失败时，ChatGPT 必须读取：

1. 失败的 Workflow；
2. 失败的 Job；
3. 失败的 Step；
4. 第一处有意义的错误；
5. 涉及文件；
6. 行号；
7. 错误类型；
8. 是否由当前提交引起；
9. 是否属于基础设施故障。

不要只复制日志最后几十行。

不要把整个日志原样贴进 PR 评论。

输出应类似：

```text
失败分类：
失败 Job：
失败 Step：
第一处真实错误：
涉及文件：
涉及行号：
根因判断：
是否需要 MiMo Claw 修改：
```

---

## 9. 失败分类

```text
CODE_FAILURE
TEST_FAILURE
LINT_FAILURE
WORKFLOW_FAILURE
WORKFLOW_NOT_TRIGGERED
INFRASTRUCTURE_FAILURE
CANCELLED_OR_SUPERSEDED
UNCERTAIN
```

### 9.1 CODE_FAILURE

包括：

- Kotlin 或 Java 编译错误；
- 缺少 import；
- 类型不匹配；
- API 使用错误；
- Room、KSP、Gradle 配置错误；
- Android 资源编译错误。

处理：

- ChatGPT 读取日志；
- 生成 MiMo Claw 修复任务；
- 在同一 `claw/*` 分支修复；
- 推送后重新检查新 SHA。

### 9.2 TEST_FAILURE

包括：

- 单元测试失败；
- 回归测试暴露逻辑问题；
- 代码导致测试超时；
- 协程、Flow 或数据库测试未正确结束。

处理：

- 优先修复生产代码；
- 只有测试本身确实错误时才修改测试；
- 禁止删除测试；
- 禁止弱化断言；
- 禁止 ignored；
- 禁止跳过测试。

### 9.3 LINT_FAILURE

包括：

- Android Lint 阻断；
- Manifest 问题；
- 权限问题；
- 生命周期问题；
- 资源兼容性问题。

处理：

- 修复真实问题；
- 禁止全局关闭 Lint；
- 禁止大范围 suppression；
- 只有确认误报时，才做最小范围 suppression 并说明原因。

### 9.4 WORKFLOW_FAILURE

包括：

- Workflow YAML 错误；
- working-directory 错误；
- JDK 或 Android 环境配置错误；
- Gradle 命令路径错误；
- Artifact 路径错误；
- Action 参数错误。

处理：

- 修改 `.github/workflows/`；
- 保留真实编译、测试和 Lint；
- 禁止删除失败 Job 让 CI 变绿。

### 9.5 WORKFLOW_NOT_TRIGGERED

包括：

- push/PR 触发条件不匹配；
- 分支过滤不匹配；
- path 过滤不匹配；
- Workflow 无效；
- Actions 未启用。

处理：

- ChatGPT 检查 Workflow 配置；
- 必要时给 MiMo Claw 生成 Workflow 修复任务；
- 不推送空提交。

### 9.6 INFRASTRUCTURE_FAILURE

包括：

- GitHub Runner 临时故障；
- GitHub 服务异常；
- Artifact 服务异常；
- 依赖仓库短时不可用；
- 网络下载临时失败；
- Runner 磁盘不足；
- Action 服务故障。

处理：

1. 不修改业务代码；
2. ChatGPT 可以重跑失败 Job；
3. 最多重试两次；
4. 两次仍失败则通知用户；
5. 标记 `human-review-required`。

### 9.7 CANCELLED_OR_SUPERSEDED

包括：

- 当前 Run 因新提交被取消；
- 监控的是旧 Head SHA；
- PR 已有更新提交。

处理：

- 不分析旧日志；
- 读取当前最新 Head SHA；
- 查找新 SHA 对应的 Run。

### 9.8 UNCERTAIN

包括：

- 日志不完整；
- 无法判断是代码还是环境；
- 错误与当前提交关系不明；
- 需要产品或架构决策。

处理：

- 不盲目修改；
- 整理证据；
- 通知用户；
- 标记 `human-review-required`。

---

## 10. CI 自动修复轮次

CI 修复最多三轮。

一轮定义为：

```text
ChatGPT 读取当前 SHA 的失败日志
→ 生成 MiMo Claw CI 修复任务
→ MiMo Claw 修改并推送
→ ChatGPT 检查新 SHA 的 CI
```

以下不增加 CI 修复轮次：

- 等待 CI；
- 重跑基础设施失败 Job；
- 旧 Run 被新提交取代；
- 只读取日志；
- Workflow 尚未触发。

达到三轮后仍失败：

1. 添加 `human-review-required`；
2. 停止生成新的自动 CI 修复任务；
3. 评论失败摘要；
4. 通知用户。

---

## 11. ChatGPT 生成 CI 修复任务

任务必须包含：

```text
[MIMO_CLAW_TASK]
task_id: <唯一ID>
pr_number: <PR编号>
base_sha: <当前Head SHA>
type: CI_FIX
ci_fix_round: <1-3>
status: READY

失败分类：
失败 Workflow：
失败 Job：
失败 Step：
第一处真实错误：
涉及文件和行号：
根因：
必须修改：
必须增加或更新的测试：
禁止：
完成后输出：
```

不得只给 MiMo Claw 一段日志，让它自行猜测。

---

## 12. MiMo Claw 本地检查

MiMo Claw 云端环境可能无法可靠完成 Android 全量构建。

因此推送前最低要求是：

```bash
git status
git diff --check
git diff --stat
git branch --show-current
```

如果环境允许，可执行：

```bash
cd android
chmod +x gradlew
./gradlew testDebugUnitTest
```

但最终结果必须以 GitHub Actions 为准。

MiMo Claw 不得因为本地不能打包就宣称任务失败，也不得因为本地快速检查通过就宣称 CI 通过。

---

## 13. 禁止事项

禁止：

1. MiMo Claw 使用 `gh`。
2. 直接修改或推送 `master`。
3. 自动合并。
4. force push。
5. 创建重复 PR。
6. 推送空提交只为触发 CI。
7. 删除失败测试。
8. 跳过 `testDebugUnitTest`。
9. 跳过 `lintDebug`。
10. 使用 `continue-on-error` 掩盖失败。
11. 将关键 Job 设为非阻断。
12. 全局关闭 Lint。
13. 把单个 Job 成功误报为整个 CI 成功。
14. 使用旧 SHA 的 CI 结果。
15. 在 CI 运行中宣称完成。
16. 在日志或评论中输出 Token。
17. 因基础设施故障盲目修改业务代码。
18. 下载并执行不可信 Artifact。
19. 自动关闭 PR。
20. 自动删除分支。

---

## 14. 每次检查报告

ChatGPT 每次完成 CI 检查后，应向用户报告：

```text
PR：
分支：
当前 Head SHA：
Workflow Run：
CI 状态：
assembleDebug：
testDebugUnitTest：
lintDebug：
APK Artifact：
失败分类：
失败 Job：
失败 Step：
是否需要 MiMo Claw 修改：
CI 修复轮次：
是否已触发 @codex review：
下一步：
```

如果 CI 成功，应明确说明：

```text
当前提交已由 GitHub Actions 完成编译、测试、Lint 和 APK 打包，尚未自动合并 master。
```
