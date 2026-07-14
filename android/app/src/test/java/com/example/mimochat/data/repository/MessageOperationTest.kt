package com.example.mimochat.data.repository

import com.example.mimochat.data.*
import com.example.mimochat.data.local.ConversationDao
import com.example.mimochat.data.local.ConversationEntity
import com.example.mimochat.data.local.MessageDao
import com.example.mimochat.data.local.MessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * 消息操作单元测试
 * 验证发送、编辑重发、重试、重新生成、停止的正确性
 */
class MessageOperationTest {

    private lateinit var fakeMessageDao: FakeMsgDao
    private lateinit var fakeConversationDao: FakeConvDao
    private lateinit var repo: ConversationRepository

    @Before
    fun setup() {
        fakeMessageDao = FakeMsgDao()
        fakeConversationDao = FakeConvDao()
        repo = ConversationRepository(fakeConversationDao, fakeMessageDao)
    }

    @Test
    fun `send inserts only one user message`() = runTest {
        val msg = makeMsg("u1", "user", "你好")
        repo.insertMessage(msg)

        assertEquals(1, fakeMessageDao.inserted.size)
        assertEquals("user", fakeMessageDao.inserted[0].role)
        assertEquals("你好", fakeMessageDao.inserted[0].content)
    }

    @Test
    fun `edit and resend updates original message`() = runTest {
        fakeMessageDao.messages = listOf(
            makeMsg("u1", "user", "原始消息"),
            makeMsg("a1", "assistant", "回答", MessageStatus.SUCCESS)
        )

        repo.updateMessageContent("u1", "修改后的消息", MessageStatus.SUCCESS)

        assertEquals(1, fakeMessageDao.updatedContent.size)
        assertEquals("修改后的消息", fakeMessageDao.updatedContent["u1"]?.first)
    }

    @Test
    fun `retry does not insert new user message`() = runTest {
        fakeMessageDao.messages = listOf(
            makeMsg("u1", "user", "问题"),
            makeMsg("a1", "assistant", "", MessageStatus.FAILED)
        )

        // 重试只更新助手消息状态，不插入新用户消息
        repo.updateMessageContent("a1", "", MessageStatus.STREAMING)

        assertEquals(0, fakeMessageDao.inserted.size)
        assertEquals(MessageStatus.STREAMING, fakeMessageDao.updatedContent["a1"]?.second)
    }

    @Test
    fun `regenerate deletes target and messages after it`() = runTest {
        fakeMessageDao.messages = listOf(
            makeMsg("u1", "user", "Q1"),
            makeMsg("a1", "assistant", "A1"),
            makeMsg("u2", "user", "Q2"),
            makeMsg("a2", "assistant", "A2")
        )

        // 删除 a1 及之后的所有消息
        repo.deleteById("a1")
        repo.deleteById("u2")
        repo.deleteById("a2")

        assertEquals(3, fakeMessageDao.deleted.size)
        assertTrue(fakeMessageDao.deleted.contains("a1"))
        assertTrue(fakeMessageDao.deleted.contains("u2"))
        assertTrue(fakeMessageDao.deleted.contains("a2"))
    }

    @Test
    fun `stop updates message to STOPPED status`() = runTest {
        fakeMessageDao.messages = listOf(
            makeMsg("u1", "user", "Q"),
            makeMsg("a1", "assistant", "部分内容", MessageStatus.STREAMING)
        )

        repo.updateMessageContent("a1", "部分内容", MessageStatus.STOPPED)

        assertEquals(MessageStatus.STOPPED, fakeMessageDao.updatedContent["a1"]?.second)
        assertEquals("部分内容", fakeMessageDao.updatedContent["a1"]?.first)
    }

    @Test
    fun `failed message with error text`() = runTest {
        repo.updateMessageStatus("a1", MessageStatus.FAILED, "请先在设置中配置 API Key")

        assertEquals(MessageStatus.FAILED, fakeMessageDao.updatedStatus["a1"]?.first)
        assertEquals("请先在设置中配置 API Key", fakeMessageDao.updatedStatus["a1"]?.second)
    }

    @Test
    fun `delete conversation cascades messages`() = runTest {
        fakeConversationDao.conversations = listOf(
            ConversationEntity(id = "conv1", title = "测试")
        )

        repo.deleteConversation("conv1")

        assertTrue(fakeConversationDao.deleted.contains("conv1"))
    }

    @Test
    fun `update title persists`() = runTest {
        fakeConversationDao.conversations = listOf(
            ConversationEntity(id = "conv1", title = "新对话")
        )

        repo.updateTitle("conv1", "新标题")

        assertEquals("新标题", fakeConversationDao.updatedTitle["conv1"])
    }

    private fun makeMsg(id: String, role: String, content: String, status: MessageStatus = MessageStatus.SUCCESS) =
        MessageEntity(id = id, conversationId = "conv1", role = role, content = content, status = status)
}

// ── Fakes ──

class FakeMsgDao : MessageDao {
    var messages: List<MessageEntity> = emptyList()
    val inserted = mutableListOf<MessageEntity>()
    val updatedContent = mutableMapOf<String, Pair<String, MessageStatus>>()
    val updatedStatus = mutableMapOf<String, Pair<MessageStatus, String?>>()
    val deleted = mutableListOf<String>()

    override suspend fun getByConversation(convId: String) = messages
    override fun getByConversationFlow(convId: String) = flowOf(messages)
    override suspend fun getById(id: String) = messages.find { it.id == id }
    override suspend fun upsert(message: MessageEntity) { inserted.add(message) }
    override suspend fun upsertAll(msgs: List<MessageEntity>) { inserted.addAll(msgs) }
    override suspend fun updateContent(id: String, content: String, status: MessageStatus, updatedAt: Long) {
        updatedContent[id] = content to status
    }
    override suspend fun updateStatus(id: String, status: MessageStatus, error: String?, updatedAt: Long) {
        updatedStatus[id] = status to error
    }
    override suspend fun deleteByConversation(convId: String) {}
    override suspend fun deleteById(id: String) { deleted.add(id) }
    override suspend fun getLastUserMessage(convId: String) = messages.lastOrNull { it.role == "user" }
}

class FakeConvDao : ConversationDao {
    var conversations: List<ConversationEntity> = emptyList()
    val deleted = mutableListOf<String>()
    val updatedTitle = mutableMapOf<String, String>()
    val updated = mutableListOf<ConversationEntity>()

    override fun getAllFlow(): Flow<List<ConversationEntity>> = flowOf(conversations)
    override suspend fun getAll() = conversations
    override suspend fun getById(id: String) = conversations.find { it.id == id }
    override suspend fun upsert(conversation: ConversationEntity) {}
    override suspend fun update(conversation: ConversationEntity) { updated.add(conversation) }
    override suspend fun deleteById(id: String) { deleted.add(id) }
    override suspend fun deleteAll() {}
    override suspend fun search(query: String) = conversations.filter { it.title.contains(query) }
}
