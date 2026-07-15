package com.example.mimochat.data.update

/**
 * Compares release tags such as v1.2.3 and 1.2.4.
 * Stable releases sort after pre-releases with the same numeric version.
 */
object VersionComparator {
    fun isNewer(latest: String, current: String): Boolean = compare(latest, current) > 0

    fun compare(left: String, right: String): Int {
        val a = parse(left)
        val b = parse(right)
        val maxSize = maxOf(a.numbers.size, b.numbers.size)

        repeat(maxSize) { index ->
            val leftPart = a.numbers.getOrElse(index) { 0 }
            val rightPart = b.numbers.getOrElse(index) { 0 }
            if (leftPart != rightPart) return leftPart.compareTo(rightPart)
        }

        return when {
            a.preRelease == null && b.preRelease != null -> 1
            a.preRelease != null && b.preRelease == null -> -1
            else -> (a.preRelease ?: "").compareTo(b.preRelease ?: "")
        }
    }

    private fun parse(raw: String): ParsedVersion {
        val normalized = raw.trim().removePrefix("v").removePrefix("V")
        val mainAndPreRelease = normalized.split('-', limit = 2)
        val numbers = mainAndPreRelease.first()
            .split('.')
            .map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
        val preRelease = mainAndPreRelease.getOrNull(1)?.takeIf { it.isNotBlank() }
        return ParsedVersion(numbers = numbers, preRelease = preRelease)
    }

    private data class ParsedVersion(
        val numbers: List<Int>,
        val preRelease: String?
    )
}
