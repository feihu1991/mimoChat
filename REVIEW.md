# mimoChat Android 端审查报告

## 项目架构
- **语言**: Kotlin + Jetpack Compose
- **网络**: Ktor (OkHttp)
- **存储**: SharedPreferences (JSON 字符串)
- **状态**: AndroidViewModel + MutableStateFlow
- **依赖**: Compose BOM, Coil, Navigation3

## 核心问题

### P0 - 致命缺陷
1. **多轮上下文缺失**: `sendPrompt()` 只发送 `role.prompt + 当前输入`，不包含历史消息
2. **无流式输出**: `chatCompletion()` 设置 `stream: false`，长回复卡死等待
3. **假回复**: 未配置 API Key 时 `demoReply()` 返回预设文本，冒充模型回答
4. **无停止生成**: 没有取消请求的机制
5. **无重试/重新生成**: 消息失败后无法操作
6. **无消息状态**: 没有 PENDING/STREAMING/SUCCESS/FAILED/STOPPED 状态模型
7. **SharedPreferences 存所有数据**: 会话、消息、配置全用 SP JSON 字符串，无 Room

### P1 - 重要缺陷
8. **TTS 未实现**: `playRoleVoice()` 是空函数
9. **ASR 未实现**: `toggleRecording()` 是空函数
10. **API Key 明文存储**: 普通 SharedPreferences，无加密
11. **无权限申请**: Manifest 缺少 INTERNET/RECORD_AUDIO/CAMERA
12. **无 Markdown 渲染**: 消息纯文本显示
13. **Memory 页面假数据**: 硬编码 3 条记忆，不参与请求
14. **复制按钮未实现**: 仅 UI 无逻辑

### P2 - 待完善
15. **图片/附件**: UI 存在但不参与真实请求
16. **声线克隆/设计**: 入口存在但未实现
17. **连续语音聊天**: 未实现

## 计划修改文件清单

### 新建
- `data/local/AppDatabase.kt` - Room 数据库
- `data/local/ConversationDao.kt` - 会话 DAO
- `data/local/MessageDao.kt` - 消息 DAO
- `data/local/SettingsStorage.kt` - 安全配置存储
- `data/remote/ChatStreamParser.kt` - SSE 流式解析
- `data/repository/ChatRepository.kt` - 聊天仓库
- `data/repository/ConversationRepository.kt` - 会话仓库
- `ui/chat/MessageBubble.kt` - 消息气泡组件
- `ui/chat/MarkdownText.kt` - Markdown 渲染
- `ui/common/ErrorTranslator.kt` - 错误信息中文翻译

### 重写
- `data/MimoClient.kt` - 添加流式支持、取消、错误处理
- `data/Models.kt` - 添加消息状态、会话结构
- `ui/main/MainViewModel.kt` - 完全重写，使用 Room + Repository
- `ui/screens/ChatScreen.kt` - 添加流式显示、停止、重试
- `ui/screens/SettingsScreen.kt` - API Key 加密存储
- `AndroidManifest.xml` - 补齐权限

### 删除/隐藏
- `demoReply()` - 移除假回复
- `MemoryScreen.kt` - 暂时隐藏入口
- 声线克隆/设计入口 - 暂时隐藏
- 文件上传入口 - 暂时隐藏
