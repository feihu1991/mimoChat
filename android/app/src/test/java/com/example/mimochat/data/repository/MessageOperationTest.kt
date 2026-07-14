package com.example.mimochat.data.repository

import com.example.mimochat.data.*
import com.example.mimochat.data.local.ConversationDao
import com.example.mimochat.data.local.MessageDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

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
        repo.insertMessage(makeMsg("u1", "user", "你好"))
        assertEquals(1, fakeMessageDao.inserted.size)
        assertEquals("user", fakeMessageDao.inserted[0].role)
    }

    @Test
    fun `edit updates original message`() = runTest {
        fakeMessageDao.messages = listOf(
            makeMsg("u1", "user", "原始"),
            makeMsg("a1", "assistant", "回答")
        )
        repo.updateMessageContent("u1", "修改后", MessageStatus.SUCCESS)
        assertEquals("修改后", fakeMessageDao.updatedContent["u1"])
    }

    @Test
    fun `stop updates to STOPPED`() = runTest {
        repo.updateMessageStatus("a1", MessageStatus.STOPPED)
        assertEquals(MessageStatus.STOPPED, fakeMessageDao.updatedStatus["a1"])
    }

    @Test
    fun `failed with error text`() = runTest {
        repo.updateMessageStatus("a1", MessageStatus.FAILED, "API Key 未配置")
        assertEquals(MessageStatus.FAILED, fakeMessageDao.updatedStatus["a1"])
    }

    @Test
    fun `delete message calls dao`() = runTest {
        repo.deleteMessage("msg1")
        assertTrue(fakeMessageDao.deleted.contains("msg1"))
    }

    private fun makeMsg(id: String, role: String, content: String, status: MessageStatus = MessageStatus.SUCCESS) =
        MessageEntity(id = id, conversationId = "conv1", role = role, content = content, status = status)
}

class FakeMsgDao : MessageDao {
    var messages: List<MessageEntity> = emptyList()
    val inserted = mutableListOf<MessageEntity>()
    val updatedContent = mutableMapOf<String, String>()
    val updatedStatus = mutableMapOf<String, MessageStatus>()
    val deleted = mutableListOf<String>()

    override suspend fun getByConversation(convId: String) = messages
    override fun getByConversationFlow(convId: String): Flow<List<MessageEntity>> = flowOf(messages)
    override suspend fun getById(id: String) = messages.find { it.id == id }
    override suspend fun upsert(message: MessageEntity) { inserted.add(message) }
    override suspend fun upsertAll(messages: List<MessageEntity>) { inserted.addAll(messages) }
    override suspend fun updateContent(id: String, content: String, status: MessageStatus, updatedAt: Long) {
        updatedContent[id] = content
    }
    override suspend fun updateStatus(id: String, status: MessageStatus, error: String?, updatedAt: Long) {
        updatedStatus[id] = status
    }
    override suspend fun deleteByConversation(convId: String) {}
    override suspend fun deleteById(id: String) { deleted.add(id) }
    override suspend fun getLastUserMessage(convId: String) = messages.lastOrNull { it.role == "user" }
}

class FakeConvDao : ConversationDao {
    var conversations: List<ConversationEntity> = emptyList()
    val deleted = mutableListOf<String>()
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
