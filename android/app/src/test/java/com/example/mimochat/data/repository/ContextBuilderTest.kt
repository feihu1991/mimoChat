package com.example.mimochat.data.repository

import com.example.mimochat.data.*
import com.example.mimochat.data.local.MemoryDao
import com.example.mimochat.data.local.MessageDao
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

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
            makeMsg("u1", "user", "你好"),
            makeMsg("a1", "assistant", "你好！")
        )
        val ctx = contextBuilder.build("conv1", "你是 MiMo")
        assertEquals("system", ctx[0]["role"])
    }

    @Test
    fun `messages organized as complete turns`() = runTest {
        fakeMessageDao.messages = listOf(
            makeMsg("u1", "user", "Q1"),
            makeMsg("a1", "assistant", "A1"),
            makeMsg("u2", "user", "Q2"),
            makeMsg("a2", "assistant", "A2")
        )
        val ctx = contextBuilder.build("conv1", "")
        val roles = ctx.map { it["role"] }
        assertEquals(listOf("user", "assistant", "user", "assistant"), roles)
    }

    @Test
    fun `current user message always preserved`() = runTest {
        fakeMessageDao.messages = listOf(
            makeMsg("u1", "user", "旧问题"),
            makeMsg("a1", "assistant", "旧回答"),
            makeMsg("u2", "user", "当前问题")
        )
        val ctx = contextBuilder.build("conv1", "", "u2")
        val last = ctx.last()
        assertEquals("user", last["role"])
        assertEquals("当前问题", last["content"])
    }

    @Test
    fun `failed messages excluded`() = runTest {
        fakeMessageDao.messages = listOf(
            makeMsg("u1", "user", "Q"),
            makeMsg("a1", "assistant", "A", MessageStatus.FAILED)
        )
        val ctx = contextBuilder.build("conv1", "")
        val assistants = ctx.filter { it["role"] == "assistant" }
        assertTrue(assistants.isEmpty())
    }

    @Test
    fun `streaming messages excluded`() = runTest {
        fakeMessageDao.messages = listOf(
            makeMsg("u1", "user", "Q"),
            makeMsg("a1", "assistant", "生成中...", MessageStatus.STREAMING)
        )
        val ctx = contextBuilder.build("conv1", "")
        val assistants = ctx.filter { it["role"] == "assistant" }
        assertTrue(assistants.isEmpty())
    }

    @Test
    fun `incomplete turn preserved as user only`() = runTest {
        fakeMessageDao.messages = listOf(makeMsg("u1", "user", "Q"))
        val ctx = contextBuilder.build("conv1", "")
        val users = ctx.filter { it["role"] == "user" }
        assertEquals(1, users.size)
    }

    @Test
    fun `new conversation has only system`() = runTest {
        fakeMessageDao.messages = emptyList()
        val ctx = contextBuilder.build("conv1", "系统提示")
        assertEquals(1, ctx.size)
        assertEquals("system", ctx[0]["role"])
    }

    private fun makeMsg(id: String, role: String, content: String, status: MessageStatus = MessageStatus.SUCCESS) =
        MessageEntity(id = id, conversationId = "conv1", role = role, content = content, status = status)
}

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

class FakeMemoryDao : MemoryDao {
    var enabledMemories: List<MemoryEntity> = emptyList()
    override fun getAllFlow() = flowOf(enabledMemories)
    override suspend fun getEnabled() = enabledMemories
    override suspend fun upsert(memory: MemoryEntity) {}
    override suspend fun deleteById(id: String) {}
    override suspend fun setEnabled(id: String, enabled: Boolean) {}
}
