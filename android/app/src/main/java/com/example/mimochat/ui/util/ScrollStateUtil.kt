package com.example.mimochat.ui.util

/**
 * 判断用户是否在消息列表底部附近，应当恢复自动跟随。
 *
 * @param firstVisibleItemIndex 当前第一个可见 item 的索引（reverseLayout）
 * @return true 表示在底部附近，流式内容应自动跟随
 */
fun isNearBottomForAutoScroll(firstVisibleItemIndex: Int): Boolean {
    return firstVisibleItemIndex <= 1
}

/**
 * 判断是否应显示"回到底部"悬浮按钮。
 *
 * @param firstVisibleItemIndex 当前第一个可见 item 的索引（reverseLayout）
 * @return true 表示用户已向上滚动较多，应显示按钮
 */
fun shouldShowScrollToBottomButton(firstVisibleItemIndex: Int): Boolean {
    return firstVisibleItemIndex > 3
}
