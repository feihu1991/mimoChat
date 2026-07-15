package com.example.mimochat.ui.screens

/**
 * reverseLayout 聊天列表的自动跟随策略。
 * index 0 代表最新消息，但长消息内部滚动时还必须结合 offset 判断。
 */
class ChatScrollPolicy {
    companion object {
        const val AUTO_SCROLL_INDEX_THRESHOLD = 1
        const val AUTO_SCROLL_OFFSET_THRESHOLD = 50
        const val SCROLL_BUTTON_INDEX_THRESHOLD = 3
    }

    var isAutoScrollEnabled: Boolean = true
        private set

    fun isNearBottom(firstVisibleItemIndex: Int, firstVisibleItemScrollOffset: Int = 0): Boolean {
        if (firstVisibleItemIndex > AUTO_SCROLL_INDEX_THRESHOLD) return false
        return firstVisibleItemScrollOffset <= AUTO_SCROLL_OFFSET_THRESHOLD
    }

    fun onUserScrollPositionChanged(
        firstVisibleItemIndex: Int,
        firstVisibleItemScrollOffset: Int = 0
    ) {
        isAutoScrollEnabled = isNearBottom(firstVisibleItemIndex, firstVisibleItemScrollOffset)
    }

    fun onNewUserMessage() {
        isAutoScrollEnabled = true
    }

    fun onScrollToBottomClicked() {
        isAutoScrollEnabled = true
    }

    fun shouldAutoScroll(): Boolean = isAutoScrollEnabled

    fun shouldShowScrollButton(
        firstVisibleItemIndex: Int,
        firstVisibleItemScrollOffset: Int = 0
    ): Boolean {
        return !isNearBottom(firstVisibleItemIndex, firstVisibleItemScrollOffset) &&
            (firstVisibleItemIndex > SCROLL_BUTTON_INDEX_THRESHOLD ||
                firstVisibleItemScrollOffset > AUTO_SCROLL_OFFSET_THRESHOLD)
    }
}
