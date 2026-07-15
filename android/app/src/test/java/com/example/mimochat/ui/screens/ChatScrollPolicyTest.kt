package com.example.mimochat.ui.screens

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ChatScrollPolicyTest {
    private lateinit var policy: ChatScrollPolicy

    @Before
    fun setUp() {
        policy = ChatScrollPolicy()
    }

    @Test
    fun `index zero still respects large offset`() {
        assertTrue(policy.isNearBottom(0, 0))
        assertTrue(policy.isNearBottom(0, 50))
        assertFalse(policy.isNearBottom(0, 51))
        assertFalse(policy.isNearBottom(0, 200))
    }

    @Test
    fun `index one is near bottom only within offset threshold`() {
        assertTrue(policy.isNearBottom(1, 0))
        assertTrue(policy.isNearBottom(1, 49))
        assertFalse(policy.isNearBottom(1, 100))
    }

    @Test
    fun `indices beyond threshold are not near bottom`() {
        assertFalse(policy.isNearBottom(2, 0))
        assertFalse(policy.isNearBottom(5, 0))
    }

    @Test
    fun `user scrolling away disables auto follow`() {
        policy.onUserScrollPositionChanged(5, 0)
        assertFalse(policy.shouldAutoScroll())
    }

    @Test
    fun `user returning to bottom restores auto follow`() {
        policy.onUserScrollPositionChanged(5, 0)
        policy.onUserScrollPositionChanged(0, 0)
        assertTrue(policy.shouldAutoScroll())
    }

    @Test
    fun `new user message restores auto follow`() {
        policy.onUserScrollPositionChanged(5, 0)
        policy.onNewUserMessage()
        assertTrue(policy.shouldAutoScroll())
    }

    @Test
    fun `scroll button click restores auto follow`() {
        policy.onUserScrollPositionChanged(5, 0)
        policy.onScrollToBottomClicked()
        assertTrue(policy.shouldAutoScroll())
    }

    @Test
    fun `scroll button appears for long offset inside newest item`() {
        assertFalse(policy.shouldShowScrollButton(0, 20))
        assertTrue(policy.shouldShowScrollButton(0, 200))
    }

    @Test
    fun `scroll button appears when browsing older items`() {
        assertFalse(policy.shouldShowScrollButton(3, 0))
        assertTrue(policy.shouldShowScrollButton(4, 0))
    }
}
