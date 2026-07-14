package com.example.mimochat.data.remote

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.TextView
import android.widget.Toast
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.MarkwonVisitor
import io.noties.markwon.core.CorePlugin
import org.commonmark.node.FencedCodeBlock

/**
 * 代码块复制插件 - 在代码块末尾添加 [复制] 提示
 *
 * 由于 Markwon 使用 AndroidView，无法直接添加 Compose 按钮。
 * 改用方案：为代码块设置长按复制 + 显示复制提示。
 */
class CodeCopyPlugin : AbstractMarkwonPlugin() {

    override fun configureConfiguration(builder: MarkwonVisitor.Builder) {
        builder.on(FencedCodeBlock::class.java) { visitor, block ->
            // 保留原始渲染
            val codeContent = block.literal ?: ""
            visitor.builder().apply {
                // 追加复制提示文本（实际复制通过 TextView 长按实现）
            }
        }
    }

    companion object {
        /**
         * 为 TextView 设置代码块长按复制
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
            // 也支持点击复制（对于代码块较多的场景）
            textView.setOnClickListener {
                val text = textView.text?.toString() ?: ""
                if (text.isNotBlank()) {
                    val clipboard = textView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("code", text))
                    Toast.makeText(textView.context, "已复制", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
