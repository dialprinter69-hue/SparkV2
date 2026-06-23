package com.example.sparkv2.automation

import java.text.Normalizer

/**
 * Fuzzy, intent-aware text matching for Spark Driver UI labels and screen copy.
 * Handles plurals (offer/offers), accents, punctuation, and timer suffixes (Accept 0:45).
 */
data class TextIntent(
    val roots: List<String> = emptyList(),
    val phrases: List<String> = emptyList(),
    val excludes: List<String> = emptyList(),
    val minRootLength: Int = 4,
)

object TextMatcher {
    private val accentStrip = Regex("\\p{Mn}+")
    // Commas are treated as separators (not kept), so "Sí, rechazar" tokenizes to ["si","rechazar"]
    // instead of ["si,", ...] — otherwise a leading word followed by a comma never matched.
    private val noiseChars = Regex("""[^\w\s$.':+\-]""")
    private val whitespace = Regex("""\s+""")

    private val UNIT_TOKENS = setOf("mi", "km", "ft", "lb", "lbs", "hr", "min")

    fun normalize(raw: String): String {
        val noAccent = Normalizer.normalize(raw, Normalizer.Form.NFD).replace(accentStrip, "")
        return whitespace.replace(
            noiseChars.replace(noAccent.lowercase(), " "),
            " ",
        ).trim()
    }

    fun tokenize(raw: String): List<String> {
        return normalize(raw).split(' ').filter { it.isNotEmpty() }
    }

    fun singularize(token: String): String {
        if (token.length <= 2 || token in UNIT_TOKENS) return token
        return when {
            token.endsWith("ies") && token.length > 4 -> token.dropLast(3) + "y"
            token.endsWith("ches") || token.endsWith("shes") || token.endsWith("sses") -> token.dropLast(2)
            token.endsWith("es") && token.length > 4 -> token.dropLast(2)
            token.endsWith("s") && token.length > 3 -> token.dropLast(1)
            else -> token
        }
    }

    fun matchesIntent(text: String, intent: TextIntent): Boolean = scoreIntent(text, intent) > 0

    fun scoreIntent(text: String, intent: TextIntent): Int {
        if (text.isBlank()) return -1

        for (exclude in intent.excludes) {
            if (containsFuzzy(text, exclude)) return -1
        }

        var best = -1
        val normalized = normalize(text)

        intent.phrases.forEachIndexed { index, phrase ->
            if (containsFuzzy(normalized, phrase)) {
                best = maxOf(best, 100 - index * 4)
            }
        }

        val tokens = tokenize(text).map(::singularize)
        intent.roots.forEachIndexed { index, root ->
            val r = singularize(normalize(root))
            for (token in tokens) {
                if (tokenMatchesRoot(token, r, intent.minRootLength)) {
                    best = maxOf(best, 88 - index * 3)
                }
            }
        }

        return best
    }

    fun scoreBest(text: String, intents: List<TextIntent>): Int {
        return intents.maxOfOrNull { scoreIntent(text, it) } ?: -1
    }

    fun matchesAny(text: String, intents: List<TextIntent>): Boolean {
        return intents.any { matchesIntent(text, it) }
    }

    fun containsFuzzy(haystack: String, needle: String): Boolean {
        val h = normalize(haystack)
        val n = normalize(needle)
        if (n.isEmpty()) return false
        if (h.contains(n)) return true

        val needleTokens = n.split(' ').filter { it.isNotEmpty() }
        if (needleTokens.size == 1) {
            val singularNeedle = singularize(needleTokens.single())
            return tokenize(h).map(::singularize).any {
                tokenMatchesRoot(it, singularNeedle, minRootLen = 3)
            }
        }
        return containsTokensInOrder(h, needleTokens)
    }

    fun containsAnyKeyword(text: String, keywords: List<TextIntent>): Boolean {
        return keywords.any { matchesIntent(text, it) }
    }

    private fun containsTokensInOrder(haystack: String, needles: List<String>): Boolean {
        val htokens = haystack.split(' ').map(::singularize)
        val ntokens = needles.map { singularize(it) }
        var idx = 0
        for (ht in htokens) {
            if (tokenMatchesRoot(ht, ntokens[idx], minRootLen = 3)) {
                idx++
                if (idx == ntokens.size) return true
            }
        }
        return false
    }

    private fun tokenMatchesRoot(token: String, root: String, minRootLen: Int): Boolean {
        if (token == root) return true
        if (root.length < minRootLen) return token == root
        if (!token.startsWith(root)) return false
        val suffix = token.removePrefix(root)
        if (suffix.isEmpty()) return true
        return suffix.all { !it.isLetter() }
    }
}
