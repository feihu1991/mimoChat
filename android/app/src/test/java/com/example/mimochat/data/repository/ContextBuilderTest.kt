package com.example.mimochat.data.repository

import com.example.mimochat.data.*
import com.example.mimochat.data.local.MemoryDao
import com.example.mimochat.data.local.MemoryEntity
import com.example.mimochat.data.local.MessageDao
import com.example.mimochat.data.local.MessageEntity
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * ContextBuilder 单元测试
 * 使用假 DAO 测试上下文构建逻辑
 */
class ContextBuilderTest {

    private lateinit var fakeMessageDao: FakeMessageDao
    private lateinit var fakeMemoryDao: FakeMemoryDao
    private lateinit var contextBuilder: ContextBuilder

    @Before
    fun setup() {
        fakeMessageDao = FakeMessageDao()
        fakeMemoryDao = FakeMemoryDao()
        contextBuilder = ContextBuilder(fakeMessageDao, fakeMemoryDao)
    }

    @Test
    fun `system prompt is always first`() = runTest {
        fakeMessageDao.messages = listOf(
            makeMessage("u1", "user", "你好", MessageStatus.SUCCESS),
            makeMessage("a1", "assistant", "你好！有什么可以帮你的？", MessageStatus.SUCCESS)
        )

        val context = contextBuilder.build("conv1", "你是 MiMo 助手")
        assertEquals("system", context[0]["role"])
        assertEquals("你是 MiMo 助手", context[0]["content"])
    }

    @Test
    fun `system prompt with memory`() = runTest {
        fakeMessageDao.messages = listOf(
            makeMessage("u1", "user", "你好", MessageStatus.SUCCESS),
            makeMessage("a1", "assistant", "你好！", MessageStatus.SUCCESS)
        )
        fakeMemoryDao.enabledMemories = listOf(
            MemoryEntity(id = "m1", content = "我喜欢简洁回答", enabled = true)
        )

        val context = contextBuilder.build("conv1", "你是 MiMo 助手")
        val systemContent = context[0]["content"] as String
        assertTrue(systemContent.contains("你是 MiMo 劅手"))
        assertTrue(systemContent.contains("我喜欢简洁回答"))
    }

    @Test
    fun `messages organized as complete turns`() = runTest {
        fakeMessageDao.messages = listOf(
            makeMessage("u1", "user", "第一轮问题", MessageStatus.SUCCESS),
            makeMessage("a1", "assistant", "第一轮回答", MessageStatus.SUCCESS),
            makeMessage("u2", "user", "第二轮问题", MessageStatus.SUCCESS),
            makeMessage("a2", "assistant", "第二轮回答", MessageStatus.SUCCESS)
        )

        val context = contextBuilder.build("conv1", "")
        // 跳过 system prompt (空)，验证轮次
        val roles = context.map { it["role"] }
        assertEquals(listOf("user", "assistant", "user", "assistant"), roles)
    }

    @Test
    fun `current user message always preserved`() = runTest {
        fakeMessageDao.messages = listOf(
            makeMessage("u1", "user", "旧问题", MessageStatus.SUCCESS),
            makeMessage("a1", "assistant", "旧回答", MessageStatus.SUCCESS),
            makeMessage("u2", "user", "当前问题", MessageStatus.SUCCESS)
        )

        val context = contextBuilder.build("conv1", "", "u2")
        val lastMsg = context.last()
        assertEquals("user", lastMsg["role"])
        assertEquals("当前问题", lastMsg["content"])
    }

    @Test
    fun `failed messages excluded`() = runTest {
        fakeMessageDao.messages = listOf(
            makeMessage("u1", "user", "问题1", MessageStatus.SUCCESS),
            makeMessage("a1", "assistant", "回答1", MessageStatus.SUCCESS),
            makeMessage("u2", "user", "问题2", MessageStatus.SUCCESS),
            makeMessage("a2", "assistant", "", MessageStatus.FAILED, "网络错误")
        )

        val context = contextBuilder.build("conv1", "")
        // a2 是 FAILED，不应包含
        val contents = context.filter { it["role"] == "assistant" }.map { it["content"] }
        assertFalse(contents.any { it.toString().contains("网络错误") })
    }

    @Test
    fun `streaming messages excluded`() = runTest {
        fakeMessageDao.messages = listOf(
            makeMessage("u1", "user", "问题", MessageStatus.SUCCESS),
            makeMessage("a1", "assistant", "正在生成...", MessageStatus.STREAMING)
        )

        val context = contextBuilder.build("conv1", "")
        val assistantMsgs = context.filter { it["role"] == "assistant" }
        assertTrue(assistantMsgs.isEmpty())
    }

    @Test
    fun `incomplete turn preserved as user only`() = runTest {
        fakeMessageDao.messages = listOf(
            makeMessage("u1", "user", "问题", MessageStatus.SUCCESS)
            // 没有对应的 assistant
        )

        val context = contextBuilder.build("conv1", "")
        val userMsgs = context.filter { it["role"] == "user" }
        assertEquals(1, userMsgs.size)
        assertEquals("问题", userMsgs[0]["content"])
    }

    @Test
    fun `new conversation isolation`() = runTest {
        fakeMessageDao.messages = emptyList()

        val context = contextBuilder.build("new-conv", "系统提示")
        assertEquals(1, context.size)
        assertEquals("system", context[0]["role"])
    }

    // ── Fakes ──

    private fun makeMessage(
        id: String,
        role: String,
        content: String,
        status: MessageStatus,
        error: String? = null
    ) = MessageEntity(
        id = id,
        conversationId = "conv1",
        role = role,
        content = content,
        status = status,
        errorMessage = error
    )
}

/**
 * 假 MessageDao 用于测试
 */
class FakeMessageDao : MessageDao {
    var messages: List<MessageEntity> = emptyList()

    override suspend fun getByConversation(convId: String) = messages
    override fun getByConversationFlow(convId: String) = flowOf(messages)
    override suspend fun getById(id: String) = messages.find { it.id == id }
    override suspend fun upsert(message: MessageEntity) {}
    override suspend fun upsertAll(messages: List<MessageEntity>) {}
    override suspend fun updateContent(id: String, content: String, status: MessageStatus, updatedAt: Long) {}
    override suspend fun updateStatus(id: String, status: MessageStatus, error: String?, updatedAt: Long) {}
    override suspend fun deleteByConversation(convId: String) {}
    override suspend fun deleteById(id: String) {}
    override suspend fun getLastUserMessage(convId: String) = messages.lastOrNull { it.role == "user" }
}

/**
 * 假 MemoryDao 用于测试
 */
class FakeMemoryDao : MemoryDao {
    var enabledMemories: List<MemoryEntity> = emptyList()

    override fun getAllFlow() = flowOf(enabledMemories)
    override suspend fun getEnabled() = enabledMemories
    override suspend fun upsert(memory: MemoryEntity) {}
    override suspend fun deleteById(id: String) {}
    override suspend fun setEnabled(id: String, enabled: Boolean) {}
}
