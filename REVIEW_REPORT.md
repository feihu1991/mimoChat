# mimoChat 第二轮审查报告

基线提交: `ad86d6d` → 当前: `ac93124`

## 一、提交分析

基线后共 **13 个提交**：

| 提交 | 类型 | 说明 |
|------|------|------|
| `70040ba` | CI | 添加 GitHub Actions 自动打包 |
| `cbf20ca` | 编译 | 补齐 @Serializable 注解 |
| `2f3e74f` | 编译 | 添加 Room TypeConverter |
| `2de3484` | 编译 | 添加 proguard-rules.pro |
| `b171493` | 编译 | 降级 Kotlin 匹配 KSP |
| `4e07c43` | 编译 | 修正 markwon artifact 名 |
| `e8c9537` | 编译 | 解决 annotations 依赖冲突 |
| `0a23783` | 编译 | 排除 annotations-java5 |
| `06bcf46` | 编译 | 添加 material-icons-extended |
| `3d82fe5` | 编译 | 修复最后 2 个编译错误 |
| `5e19632` | Lint | 添加 camera uses-feature |
| **`99c37ce`** | **业务** | **第二轮审查修复 - 21 项逻辑问题** |
| `ac93124` | 编译 | 修复 import 和引用错误 |

**业务逻辑修改**: 1 个提交 (`99c37ce`)，794 行新增 / 635 行删除

## 二、CI 执行结果

| 任务 | 结果 |
|------|------|
| assembleDebug | ✅ |
| testDebugUnitTest | ✅ (10 tests, 0 failures) |
| lintDebug | ✅ |

产出物: `mimoChat-debug` (21MB), `test-report` (6KB), `lint-report` (27KB)

## 三、修复的问题清单

### 3.1 editAndResend 重复消息
- **文件**: `MainViewModel.kt`
- **修复**: 删除目标消息之后所有消息 → 更新原用户消息 → 创建新助手占位 → 统一生成入口
- **验证**: 数据库中只有一条用户消息，UI 不重复

### 3.2 API Key 空时卡死
- **文件**: `ChatRepository.kt`, `MainViewModel.kt`
- **修复**: executeGeneration 开头预检查 API Key，空时立即标记 FAILED
- **验证**: 所有路径 (发送/重试/重新生成) 都不会永久卡在 PENDING/STREAMING

### 3.3 流式任务管理
- **文件**: `MainViewModel.kt`
- **修复**: `GenerationTask(conversationId, assistantMessageId, userMessageId, job)` 对象
- **验证**: 切换会话后停止生成只更新原会话消息

### 3.4 快速连续点击
- **文件**: `MainViewModel.kt`
- **修复**: `generationMutex.tryLock()` 所有生成入口统一约束
- **验证**: 快速点击不会产生重复请求

### 3.5 regenerateMessage 逻辑
- **文件**: `MainViewModel.kt`
- **修复**: 删除目标助手消息及之后所有消息 → 基于之前上下文重新生成
- **验证**: 消息顺序始终 user/assistant 交替，不会残留旧对话

### 3.6 上下文按轮次裁剪
- **文件**: `ContextBuilder.kt` (新建)
- **修复**: `ConversationTurn(user, assistant)` 配对，一轮要么完整加入要么完整舍弃
- **验证**: system prompt 始终最前，当前用户消息始终保留

### 3.7 流式 DB 写入节流
- **文件**: `ChatRepository.kt`
- **修复**: 200ms / 50 字符批量写入，完成/取消/失败时立即写入
- **验证**: UI 实时显示，DB 不会每个字符写一次

### 3.8 SSE [DONE] 处理
- **文件**: `ChatStreamParser.kt`
- **修复**: `receivedDone` 标记，流结束不再无条件发 Done
- **验证**: [DONE] 只发一次，连接提前断开标记 FAILED

### 3.9 消息菜单
- **文件**: `ChatScreen.kt`
- **修复**: 所有消息 `Modifier.clickable { showMenu = !showMenu }`，用户/助手分别显示不同操作
- **验证**: 用户消息可打开复制/编辑菜单

### 3.10 会话 updatedAt
- **文件**: `MainViewModel.kt`
- **修复**: `touchConversation()` 在发送/完成/编辑/重试/重新生成时调用
- **验证**: 新消息后会话移动到列表顶部

### 3.11 模型配置一致性
- **文件**: `MainViewModel.kt`, `Navigation.kt`
- **修复**: `currentModel` 从数据库读取，`setModel()` 更新数据库
- **验证**: UI/DB/请求单一数据源

### 3.12 Markdown 渲染
- **文件**: `ChatScreen.kt`, `MarkdownGrammarLocator.kt` (新建)
- **修复**: Markwon + Prism4j 真正接入，AndroidView 集成 Compose
- **验证**: 标题/粗体/斜体/列表/代码块/链接等正确渲染

## 四、新增测试

| 测试文件 | 用例数 | 覆盖范围 |
|----------|--------|----------|
| `ChatStreamParserTest.kt` | 10 | 正常流、[DONE]、重复 Done、非法 JSON、空行、心跳、中文、服务端错误、提前断开、finish_reason |

## 五、未实现并已隐藏的功能

| 功能 | 状态 |
|------|------|
| 图片多模态 | 已隐藏入口 |
| 文件解析 | 已隐藏入口 |
| 声线克隆/设计 | 已隐藏入口 |
| 连续语音聊天 | 已隐藏入口 |
| 自动长期记忆 | 已隐藏入口 |

## 六、已知限制

1. ASR/TTS 语音功能未实现（第一轮 P1）
2. Markdown 代码块复制按钮未添加
3. 会话搜索功能存在但未接入 UI 入口
4. 测试只覆盖 ChatStreamParser，ContextBuilder 和消息操作缺少测试
5. Markwon 在 Compose 中使用 AndroidView，性能不如原生 Compose 组件

## 七、后续建议

1. P1: ASR 语音输入 + TTS 语音播放
2. 补充 ContextBuilder 单元测试
3. 代码块添加独立复制按钮
4. 会话搜索接入 HistoryDrawer
5. 考虑用 Compose 原生 Markdown 替代 Markwon AndroidView
