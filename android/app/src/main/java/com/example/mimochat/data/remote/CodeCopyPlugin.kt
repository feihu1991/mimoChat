package com.example.mimochat.data.remote

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.TextView
import android.widget.Toast
import io.noties.markwon.AbstractMarkwonPlugin

/**
 * 代码块复制插件 - 为 TextView 设置点击复制功能
 *
 * 在 Markwon 渲染后的 TextView 上启用点击/长按复制
 */
class CodeCopyPlugin : AbstractMarkwonPlugin() {

    companion object {
        /**
         * 为 TextView 设置点击复制
         */
        fun setupCopyOnLongPress(textView: TextView) {
            textView.setOnLongClickListener {
                val text = textView.text?.toString() ?: ""
                if (text.isNotBlank()) {
                    val clipboard = textView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("code", text))
                    Toast.makeText(textView.context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                }
                true
            }
        }
    }
}
