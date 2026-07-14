package com.example.mimochat.ui.screens

/**
 * Pure Kotlin policy for chat list auto-scroll behavior with reverseLayout.
 *
 * Responsibilities:
 * - Determine if user is near the bottom of the message list
 * - Determine when to show "scroll to bottom" button
 * - Track whether auto-follow is enabled (user hasn't scrolled away)
 * - Handle streaming updates vs user-initiated scrolling
 *
 * With reverseLayout=true, index 0 = newest message (bottom of list).
 */
class ChatScrollPolicy {

    companion object {
        /** Maximum item index (reverseLayout) to be considered "near bottom" for auto-follow */
        const val AUTO_SCROLL_INDEX_THRESHOLD = 1

        /** Maximum scroll offset (px) within the threshold item to be considered at bottom */
        const val AUTO_SCROLL_OFFSET_THRESHOLD = 50

        /** Minimum item index to show the "scroll to bottom" button */
        const val SCROLL_BUTTON_INDEX_THRESHOLD = 3
    }

    /**
     * Whether auto-scroll is currently enabled.
     * Set to false when user scrolls away from bottom.
     * Reset to true when user explicitly requests scroll-to-bottom or sends a new message.
     */
    var isAutoScrollEnabled: Boolean = true
        private set

    /**
     * Whether the user is currently near the bottom of the list.
     * Combines item index and scroll offset for accurate detection.
     */
    fun isNearBottom(firstVisibleItemIndex: Int, firstVisibleItemScrollOffset: Int = 0): Boolean {
        return firstVisibleItemIndex < AUTO_SCROLL_INDEX_THRESHOLD &&
            (firstVisibleItemIndex != 0 || firstVisibleItemScrollOffset <= AUTO_SCROLL_OFFSET_THRESHOLD) ||
            (firstVisibleItemIndex == AUTO_SCROLL_INDEX_THRESHOLD &&
                firstVisibleItemScrollOffset <= AUTO_SCROLL_OFFSET_THRESHOLD)
    }

    /**
     * Whether the "scroll to bottom" button should be shown.
     * Only shown when user has scrolled significantly away from bottom.
     */
    fun shouldShowScrollButton(firstVisibleItemIndex: Int): Boolean {
        return firstVisibleItemIndex > SCROLL_BUTTON_INDEX_THRESHOLD
    }

    /**
     * Update auto-scroll state based on current scroll position.
     * Call this whenever the scroll position changes.
     *
     * @return true if auto-scroll should trigger (user is near bottom AND auto-scroll is enabled)
     */
    fun onScrollPositionChanged(
        firstVisibleItemIndex: Int,
        firstVisibleItemScrollOffset: Int = 0
    ): Boolean {
        val nearBottom = isNearBottom(firstVisibleItemIndex, firstVisibleItemScrollOffset)
        if (nearBottom) {
            // User scrolled back to bottom → restore auto-follow
            isAutoScrollEnabled = true
        } else {
            // User scrolled away from bottom → disable auto-follow
            isAutoScrollEnabled = false
        }
        return isAutoScrollEnabled
    }

    /**
     * Called when the "scroll to bottom" button is clicked.
     * Resets auto-scroll and returns true to indicate scroll animation should trigger.
     */
    fun onScrollToBottomClicked(): Boolean {
        isAutoScrollEnabled = true
        return true
    }

    /**
     * Called when a new user message is sent.
     * Always re-enables auto-scroll so the new message is visible.
     */
    fun onNewUserMessage() {
        isAutoScrollEnabled = true
    }

    /**
     * Check if auto-scroll should happen for a content update (streaming or new message).
     * Does NOT modify state - use [onScrollPositionChanged] for state updates.
     */
    fun shouldAutoScroll(): Boolean {
        return isAutoScrollEnabled
    }
}
