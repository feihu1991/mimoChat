package com.example.mimochat.data.remote

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ChatStreamParserTest {

    @Test
    fun `normal delta stream`() = runTest {
        val lines = flowOf(
            """data: {"choices":[{"delta":{"content":"Hello"}}]}""",
            """data: {"choices":[{"delta":{"content":" World"}}]}""",
            """data: [DONE]"""
        )
        val chunks = ChatStreamParser.parseStream(lines).toList()
        val deltas = chunks.filterIsInstance<StreamChunk.Delta>()
        assertEquals(2, deltas.size)
        assertEquals("Hello", deltas[0].text)
        assertEquals(" World", deltas[1].text)
        assertTrue(chunks.any { it is StreamChunk.Done })
    }

    @Test
    fun `sse data without a space is parsed`() = runTest {
        val chunks = ChatStreamParser.parseStream(
            flowOf(
                "event: message",
                "data:{\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}",
                "data:[DONE]"
            )
        ).toList()

        assertEquals("你好", chunks.filterIsInstance<StreamChunk.Delta>().single().text)
        assertTrue(chunks.any { it is StreamChunk.Done })
    }

    @Test
    fun `content parts are flattened`() = runTest {
        val chunks = ChatStreamParser.parseStream(
            flowOf(
                "data: {\"choices\":[{\"delta\":{\"content\":[{\"type\":\"text\",\"text\":\"A\"},{\"type\":\"text\",\"text\":\"B\"}]}}]}"
            )
        ).toList()

        assertEquals("AB", chunks.filterIsInstance<StreamChunk.Delta>().single().text)
    }

    @Test
    fun `done only emitted once`() = runTest {
        val lines = flowOf(
            """data: {"choices":[{"delta":{"content":"test"}}]}""",
            """data: [DONE]""",
            """data: [DONE]"""
        )
        val chunks = ChatStreamParser.parseStream(lines).toList()
        val dones = chunks.filterIsInstance<StreamChunk.Done>()
        assertEquals(1, dones.size)
    }

    @Test
    fun `empty lines are skipped`() = runTest {
        val lines = flowOf(
            "",
            """data: {"choices":[{"delta":{"content":"A"}}]}""",
            "",
            """data: [DONE]"""
        )
        val chunks = ChatStreamParser.parseStream(lines).toList()
        val deltas = chunks.filterIsInstance<StreamChunk.Delta>()
        assertEquals(1, deltas.size)
    }

    @Test
    fun `heartbeat lines are skipped`() = runTest {
        val lines = flowOf(
            ":heartbeat",
            """data: {"choices":[{"delta":{"content":"B"}}]}""",
            """data: [DONE]"""
        )
        val chunks = ChatStreamParser.parseStream(lines).toList()
        val deltas = chunks.filterIsInstance<StreamChunk.Delta>()
        assertEquals(1, deltas.size)
        assertEquals("B", deltas[0].text)
    }

    @Test
    fun `invalid json is handled`() = runTest {
        val lines = flowOf(
            """data: {invalid json""",
            """data: {"choices":[{"delta":{"content":"ok"}}]}""",
            """data: [DONE]"""
        )
        val chunks = ChatStreamParser.parseStream(lines).toList()
        val deltas = chunks.filterIsInstance<StreamChunk.Delta>()
        assertEquals(1, deltas.size)
    }

    @Test
    fun `server error event`() = runTest {
        val lines = flowOf(
            """data: {"error":{"message":"Rate limit exceeded"}}"""
        )
        val chunks = ChatStreamParser.parseStream(lines).toList()
        val errors = chunks.filterIsInstance<StreamChunk.Error>()
        assertEquals(1, errors.size)
        assertEquals("Rate limit exceeded", errors[0].message)
    }

    @Test
    fun `chinese characters preserved`() = runTest {
        val lines = flowOf(
            """data: {"choices":[{"delta":{"content":"你好世界"}}]}""",
            """data: [DONE]"""
        )
        val chunks = ChatStreamParser.parseStream(lines).toList()
        val deltas = chunks.filterIsInstance<StreamChunk.Delta>()
        assertEquals("你好世界", deltas[0].text)
    }

    @Test
    fun `non-streaming complete response`() = runTest {
        val lines = flowOf(
            """data: {"choices":[{"message":{"content":"Full response"}}]}"""
        )
        val chunks = ChatStreamParser.parseStream(lines).toList()
        val completes = chunks.filterIsInstance<StreamChunk.Complete>()
        assertEquals(1, completes.size)
        assertEquals("Full response", completes[0].text)
    }

    @Test
    fun `stream ends without done - no done emitted`() = runTest {
        val lines = flowOf(
            """data: {"choices":[{"delta":{"content":"partial"}}]}"""
        )
        val chunks = ChatStreamParser.parseStream(lines).toList()
        val dones = chunks.filterIsInstance<StreamChunk.Done>()
        assertTrue(dones.isEmpty())
    }

    @Test
    fun `finish reason event`() = runTest {
        val lines = flowOf(
            """data: {"choices":[{"finish_reason":"stop"}]}""",
            """data: [DONE]"""
        )
        val chunks = ChatStreamParser.parseStream(lines).toList()
        val finished = chunks.filterIsInstance<StreamChunk.Finished>()
        assertEquals(1, finished.size)
        assertEquals("stop", finished[0].reason)
    }
}
