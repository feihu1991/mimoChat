package com.example.mimochat.ui.util

import org.junit.Assert.*
import org.junit.Test

class ScrollStateUtilTest {

    @Test
    fun `at bottom - firstVisibleItemIndex 0 is near bottom`() {
        assertTrue(isNearBottomForAutoScroll(0))
    }

    @Test
    fun `near bottom - firstVisibleItemIndex 1 is near bottom`() {
        assertTrue(isNearBottomForAutoScroll(1))
    }

    @Test
    fun `scrolled up - firstVisibleItemIndex 2 is not near bottom`() {
        assertFalse(isNearBottomForAutoScroll(2))
    }

    @Test
    fun `scrolled far up - firstVisibleItemIndex 10 is not near bottom`() {
        assertFalse(isNearBottomForAutoScroll(10))
    }

    @Test
    fun `scroll button - not shown when near bottom`() {
        assertFalse(shouldShowScrollToBottomButton(0))
        assertFalse(shouldShowScrollToBottomButton(1))
        assertFalse(shouldShowScrollToBottomButton(2))
        assertFalse(shouldShowScrollToBottomButton(3))
    }

    @Test
    fun `scroll button - shown when scrolled past threshold`() {
        assertTrue(shouldShowScrollToBottomButton(4))
        assertTrue(shouldShowScrollToBottomButton(10))
    }
}
