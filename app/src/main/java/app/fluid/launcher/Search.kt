package app.fluid.launcher

import java.text.Normalizer

// Words split on punctuation/spaces and camelCase, so "YouTube Music" -> you, tube, music.
private val wordBreak = Regex("[^\\p{L}\\p{N}]+|(?<=\\p{Ll})(?=\\p{Lu})")
private val marks = Regex("\\p{Mn}+")

private fun fold(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(marks, "").lowercase()

/** Lower is better: 0 prefix, 1 word prefix, 2 initials, 3 substring; null = no match. */
fun matchScore(label: String, query: String): Int? {
    val q = fold(query).filter(Char::isLetterOrDigit)
    if (q.isEmpty()) return null
    val words = label.split(wordBreak).filter(String::isNotEmpty).map(::fold)
    val joined = words.joinToString("")
    return when {
        joined.startsWith(q) -> 0
        words.any { it.startsWith(q) } -> 1
        q.length > 1 && words.joinToString("") { it.take(1) }.startsWith(q) -> 2
        joined.contains(q) -> 3
        else -> null
    }
}

/** Matches ranked by score, then most launched; input order (alphabetical) breaks remaining ties. */
fun <T> rank(items: List<T>, query: String, label: (T) -> String, launches: (T) -> Int): List<T> =
    items.mapNotNull { item -> matchScore(label(item), query)?.let { item to it } }
        .sortedWith(compareBy<Pair<T, Int>> { it.second }.thenByDescending { launches(it.first) })
        .map { it.first }
