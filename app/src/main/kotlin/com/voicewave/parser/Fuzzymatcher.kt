package com.voicewave.parser

/**
 * Fuzzy string matching using Levenshtein distance.
 *
 * HOW LEVENSHTEIN DISTANCE WORKS (simple version):
 * It counts the minimum number of single-character edits (insertions,
 * deletions, substitutions) needed to turn one string into another.
 *
 * Examples:
 *   "spotify" vs "spotfy"   → distance 1 (one missing 'i')
 *   "wikipdia" vs "wikipedia" → distance 2 (two insertions)
 *   "firefox" vs "chrome"   → distance 6 (very different)
 *
 * We use this to find the closest match in a list of candidates.
 * A match is accepted if it's "close enough" — within our threshold.
 */
object FuzzyMatcher {

    /**
     * Find the best match for [input] from a list of [candidates].
     * Returns the best match or null if nothing is close enough.
     *
     * @param threshold max edit distance to accept. Lower = stricter.
     *        1 = only typos. 3 = pretty loose. 5 = very loose.
     */
    fun bestMatch(input: String, candidates: List<String>, threshold: Int = 3): String? {
        if (input.isBlank()) return null

        val normalizedInput = input.lowercase().trim()

        return candidates
            .map { candidate -> candidate to levenshtein(normalizedInput, candidate.lowercase().trim()) }
            .filter { (_, distance) -> distance <= threshold }
            .minByOrNull { (_, distance) -> distance }
            ?.first
    }

    /**
     * Check if [input] is a fuzzy match for [target].
     * Useful for keyword detection: "wikipdia" matches "wikipedia".
     */
    fun matches(input: String, target: String, threshold: Int = 2): Boolean {
        return levenshtein(input.lowercase().trim(), target.lowercase().trim()) <= threshold
    }

    /**
     * Check if any word in [input] fuzzy-matches [keyword].
     * e.g. "search wikipdia for cats" → fuzzy matches "wikipedia"
     */
    fun containsFuzzy(input: String, keyword: String, threshold: Int = 2): Boolean {
        val words = input.lowercase().trim().split(" ")
        return words.any { word -> levenshtein(word, keyword.lowercase()) <= threshold }
    }

    /**
     * The actual Levenshtein distance algorithm.
     * Uses dynamic programming — builds a grid of edit costs.
     *
     * Don't panic at the code — it's a classic CS algorithm,
     * just trust that it works and returns the edit distance between a and b.
     */
    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        val dp = Array(a.length + 1) { IntArray(b.length + 1) }

        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j

        for (i in 1..a.length) {
            for (j in 1..b.length) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) {
                    dp[i - 1][j - 1]
                } else {
                    1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
                }
            }
        }

        return dp[a.length][b.length]
    }
}
