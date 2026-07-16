package com.example.mimochat.data.remote

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentToolCallParserTest {

    @Test
    fun `fragmented tool call arguments preserve index id and name`() = runTest {
        val chunks = ChatStreamParser.parseStream(
            flowOf(
                """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","type":"function","function":{"name":"read_file","arguments":"{\"path\":"}}]}}]}""",
                """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\"src/Main.kt\"}"}}]}}]}""",
                """data: {"choices":[{"finish_reason":"tool_calls"}]}""",
                "data: [DONE]"
            )
        ).toList()

        val calls = chunks.filterIsInstance<StreamChunk.ToolCallDelta>()
        assertEquals(2, calls.size)
        assertEquals(0, calls[0].index)
        assertEquals("call_1", calls[0].id)
        assertEquals("read_file", calls[0].name)
        assertEquals("{\"path\":", calls[0].arguments)
        assertEquals("\"src/Main.kt\"}", calls[1].arguments)
        assertTrue(chunks.any { it is StreamChunk.Finished && it.reason == "tool_calls" })
        assertTrue(chunks.any { it is StreamChunk.Done })
    }

    @Test
    fun `reasoning and visible text are emitted separately`() = runTest {
        val chunks = ChatStreamParser.parseStream(
            flowOf(
                """data: {"choices":[{"delta":{"reasoning_content":"先定位文件","content":"我来处理。"}}]}""",
                "data: [DONE]"
            )
        ).toList()

        assertEquals("先定位文件", chunks.filterIsInstance<StreamChunk.ReasoningDelta>().single().text)
        assertEquals("我来处理。", chunks.filterIsInstance<StreamChunk.Delta>().single().text)
    }
}
