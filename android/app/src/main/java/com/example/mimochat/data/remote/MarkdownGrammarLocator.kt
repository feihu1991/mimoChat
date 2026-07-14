package com.example.mimochat.data.remote

import io.noties.prism4j.GrammarLocator
import io.noties.prism4j.Prism4j
import org.commonmark.node.Node

/**
 * Prism4j 语法定位器 - 支持常见编程语言的代码高亮
 */
class MarkdownGrammarLocator : GrammarLocator {
    override fun grammar(prism4j: Prism4j, language: String): Prism4j.Grammar? {
        return when (language.lowercase()) {
            "java", "kotlin", "python", "javascript", "js", "typescript", "ts",
            "json", "xml", "html", "css", "sql", "bash", "shell", "sh",
            "go", "rust", "c", "cpp", "csharp", "ruby", "php", "swift",
            "dart", "yaml", "yml", "toml", "markdown", "md" -> {
                // 返回基础语法（Prism4j 会自动处理）
                prism4j.grammar(language)
            }
            else -> null
        }
    }

    override fun languages(): Set<String> = setOf(
        "java", "kotlin", "python", "javascript", "typescript",
        "json", "xml", "html", "css", "sql", "bash", "shell",
        "go", "rust", "c", "cpp", "csharp", "ruby", "php",
        "swift", "dart", "yaml", "toml", "markdown"
    )
}
