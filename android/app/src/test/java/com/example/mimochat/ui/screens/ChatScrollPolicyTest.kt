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

    // --- Test 1: Index 0 with small offset → at bottom ---
    @Test
    fun `at bottom - index 0 small offset is near bottom`() {
        assertTrue(policy.isNearBottom(0, 0))
        assertTrue(policy.isNearBottom(0, 30))
        assertTrue(policy.isNearBottom(0, 49))
    }

    // --- Test 2: Index > 0 → not at bottom ---
    @Test
    fun `scrolled up - index greater than 0 is not near bottom`() {
        assertFalse(policy.isNearBottom(2, 0))
        assertFalse(policy.isNearBottom(5, 0))
        assertFalse(policy.isNearBottom(10, 0))
    }

    // --- Test 3: Index 0 but large offset → not at bottom ---
    @Test
    fun `at bottom edge - index 0 with large offset is not near bottom`() {
        assertFalse(policy.isNearBottom(0, 200))
        assertFalse(policy.isNearBottom(0, 100))
    }

    // --- Test 4: User browsing history → streaming does not auto-follow ---
    @Test
    fun `user scrolled away - auto scroll disabled after scrolling up`() {
        // User scrolls away from bottom
        val shouldScroll = policy.onScrollPositionChanged(5, 0)
        assertFalse(shouldScroll)
        assertFalse(policy.isAutoScrollEnabled)
        // Streaming update should not trigger auto-scroll
        assertFalse(policy.shouldAutoScroll())
    }

    // --- Test 5: Return to bottom → auto-follow restored ---
    @Test
    fun `return to bottom - auto scroll re-enabled when user scrolls back`() {
        // User scrolls away
        policy.onScrollPositionChanged(5, 0)
        assertFalse(policy.isAutoScrollEnabled)

        // User scrolls back to bottom
        val shouldScroll = policy.onScrollPositionChanged(0, 0)
        assertTrue(shouldScroll)
        assertTrue(policy.isAutoScrollEnabled)
    }

    // --- Test 6: New user message → auto-scroll enabled ---
    @Test
    fun `new user message - auto scroll enabled even if user was scrolled up`() {
        // User scrolled away
        policy.onScrollPositionChanged(5, 0)
        assertFalse(policy.isAutoScrollEnabled)

        // New user message sent
        policy.onNewUserMessage()
        assertTrue(policy.isAutoScrollEnabled)
        assertTrue(policy.shouldAutoScroll())
    }

    // --- Test 7: Empty list → scroll button not shown ---
    @Test
    fun `empty list - scroll button not shown`() {
        // With no items, firstVisibleItemIndex is 0
        assertFalse(policy.shouldShowScrollButton(0))
        assertFalse(policy.shouldShowScrollButton(1))
        assertFalse(policy.shouldShowScrollButton(2))
        assertFalse(policy.shouldShowScrollButton(3))
    }

    // --- Additional: scroll button shown when scrolled past threshold ---
    @Test
    fun `scroll button - shown when scrolled past threshold`() {
        assertTrue(policy.shouldShowScrollButton(4))
        assertTrue(policy.shouldShowScrollButton(10))
    }

    // --- Additional: scroll to bottom click restores auto-scroll ---
    @Test
    fun `scroll to bottom click - restores auto scroll`() {
        policy.onScrollPositionChanged(5, 0)
        assertFalse(policy.isAutoScrollEnabled)

        val result = policy.onScrollToBottomClicked()
        assertTrue(result)
        assertTrue(policy.isAutoScrollEnabled)
    }

    // --- Additional: index 1 with small offset is near bottom ---
    @Test
    fun `near bottom - index 1 with small offset is near bottom`() {
        assertTrue(policy.isNearBottom(1, 0))
        assertTrue(policy.isNearBottom(1, 49))
    }

    // --- Additional: index 1 with large offset is not near bottom ---
    @Test
    fun `near bottom - index 1 with large offset is not near bottom`() {
        assertFalse(policy.isNearBottom(1, 100))
        assertFalse(policy.isNearBottom(1, 200))
    }

    // --- Additional: index 0 with offset exactly at threshold is near bottom ---
    @Test
    fun `at bottom edge - index 0 offset at threshold is near bottom`() {
        assertTrue(policy.isNearBottom(0, 50))
    }

    // --- Additional: index 0 with offset just above threshold is not near bottom ---
    @Test
    fun `at bottom edge - index 0 offset above threshold is not near bottom`() {
        assertFalse(policy.isNearBottom(0, 51))
    }
}
