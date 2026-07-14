package com.example.mimochat.data

import org.junit.Assert.*
import org.junit.Test

/**
 * 消息状态机测试 - 验证合法状态流转
 */
class MessageStatusTest {

    @Test
    fun `valid transitions from PENDING`() {
        assertTrue(MessageStatus.PENDING.canTransitionTo(MessageStatus.STREAMING))
        assertTrue(MessageStatus.PENDING.canTransitionTo(MessageStatus.FAILED))
        assertFalse(MessageStatus.PENDING.canTransitionTo(MessageStatus.SUCCESS))
        assertFalse(MessageStatus.PENDING.canTransitionTo(MessageStatus.STOPPED))
    }

    @Test
    fun `valid transitions from STREAMING`() {
        assertTrue(MessageStatus.STREAMING.canTransitionTo(MessageStatus.SUCCESS))
        assertTrue(MessageStatus.STREAMING.canTransitionTo(MessageStatus.FAILED))
        assertTrue(MessageStatus.STREAMING.canTransitionTo(MessageStatus.STOPPED))
        assertFalse(MessageStatus.STREAMING.canTransitionTo(MessageStatus.PENDING))
    }

    @Test
    fun `FAILED is terminal`() {
        assertFalse(MessageStatus.FAILED.canTransitionTo(MessageStatus.SUCCESS))
        assertFalse(MessageStatus.FAILED.canTransitionTo(MessageStatus.STREAMING))
        assertFalse(MessageStatus.FAILED.canTransitionTo(MessageStatus.PENDING))
        assertFalse(MessageStatus.FAILED.canTransitionTo(MessageStatus.STOPPED))
    }

    @Test
    fun `STOPPED is terminal`() {
        assertFalse(MessageStatus.STOPPED.canTransitionTo(MessageStatus.SUCCESS))
        assertFalse(MessageStatus.STOPPED.canTransitionTo(MessageStatus.STREAMING))
        assertFalse(MessageStatus.STOPPED.canTransitionTo(MessageStatus.PENDING))
        assertFalse(MessageStatus.STOPPED.canTransitionTo(MessageStatus.FAILED))
    }

    @Test
    fun `SUCCESS is terminal`() {
        assertFalse(MessageStatus.SUCCESS.canTransitionTo(MessageStatus.STREAMING))
        assertFalse(MessageStatus.SUCCESS.canTransitionTo(MessageStatus.PENDING))
        assertFalse(MessageStatus.SUCCESS.canTransitionTo(MessageStatus.FAILED))
        assertFalse(MessageStatus.SUCCESS.canTransitionTo(MessageStatus.STOPPED))
    }

    @Test
    fun `terminal status check`() {
        assertTrue(MessageStatus.SUCCESS.isTerminal())
        assertTrue(MessageStatus.FAILED.isTerminal())
        assertTrue(MessageStatus.STOPPED.isTerminal())
        assertFalse(MessageStatus.PENDING.isTerminal())
        assertFalse(MessageStatus.STREAMING.isTerminal())
    }
}
