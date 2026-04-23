package org.hyper_linux.tellicoviewer.util

import org.hyper_linux.tellicoviewer.data.model.ScoredEntry
import org.hyper_linux.tellicoviewer.data.model.TellicoEntry
import org.hyper_linux.tellicoviewer.data.model.TellicoField
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Search engine with scoring and fuzzy matching.
 *
 * ALGORITHME :
 * Search combines multiple strategies in order of precision:
 *
 * 1. EXACT (score 1.0)       : the full chain corresponds
 * 2. PREFIX (score 0.9)      : value starts with the term
 * 3. CONTAINS (score 0.7)    : value contains the term
 * 4. FUZZY (score variable)  : distance de Levenshtein normalisée
 *
 * The final score factors in the field weight:
 * - Title: weight ×2.0
 * - Searchable fields: coefficient ×1.5
 * - Other fields: coefficient ×1.0
 *
 * OPTIMISATION :
 * For 10 000 entries, use the SQLite FTS index (via EntryDao.searchFtsPaged)
 * for the first pass. Kotlin fuzzy scoring is only applied in
 * "advanced search" mode or to re-rank FTS results.
 *
 * Analogy: like grep -i (containment), agrep (fuzzy), and a simplified TF-IDF score.
 */
@Singleton
class SearchEngine @Inject constructor() {

    companion object {
        const val MIN_SCORE_THRESHOLD = 0.3f  // score minimum pour inclure un résultat
        const val MAX_LEVENSHTEIN_DISTANCE = 3 // distance max pour le fuzzy matching
    }

    /**
     * Searches and ranks a list of entries.
     *
     * @param entries List of entries to filter
     * @param query   Terme de recherche
     * @param fields  Schema to know field types and priorities
     * @param fieldFilter Restrict search to a specific field
     * @return List sorted by descending score
     */
    fun search(
        entries: List<TellicoEntry>,
        query: String,
        fields: List<TellicoField>,
        fieldFilter: String? = null
    ): List<ScoredEntry> {
        if (query.isBlank()) return entries.map { ScoredEntry(it, 1.0f) }

        val normalizedQuery = query.lowercase().trim()
        val searchableFields = if (fieldFilter != null)
            fields.filter { it.name == fieldFilter }
        else
            fields.filter { it.isSearchable || it.name == "title" }

        return entries
            .mapNotNull { entry ->
                scoreEntry(entry, normalizedQuery, searchableFields, fields)
            }
            .filter { it.score >= MIN_SCORE_THRESHOLD }
            .sortedByDescending { it.score }
    }

    private fun scoreEntry(
        entry: TellicoEntry,
        query: String,
        searchableFields: List<TellicoField>,
        allFields: List<TellicoField>
    ): ScoredEntry? {
        var bestScore = 0f
        val matches = mutableMapOf<String, String>()

        for (field in searchableFields) {
            val rawValue = entry.getValue(field.name)
            if (rawValue.isBlank()) continue

            val value = rawValue.lowercase()
            val fieldScore = scoreValue(value, query) * fieldWeight(field)

            if (fieldScore > 0f) {
                bestScore = max(bestScore, fieldScore)
                matches[field.name] = rawValue
            }
        }

        return if (bestScore > 0f)
            ScoredEntry(entry = entry, score = bestScore.coerceAtMost(1.0f), matches = matches)
        else null
    }

    /**
     * Match score between a value and the query.
     * Returns a float between 0.0 (no match) and 1.0 (exact match).
     */
    private fun scoreValue(value: String, query: String): Float {
        // 1. Correspondance exacte
        if (value == query) return 1.0f

        // 2. Préfixe
        if (value.startsWith(query)) return 0.9f

        // 3. Contains (substring).
        if (value.contains(query)) return 0.7f

        // 4. Word-by-word search (multi-term).
        val queryWords = query.split("\\s+".toRegex())
        if (queryWords.size > 1) {
            val wordScore = queryWords.count { word -> value.contains(word) }.toFloat() /
                    queryWords.size.toFloat()
            if (wordScore > 0.5f) return wordScore * 0.65f
        }

        // 5. Fuzzy matching (Levenshtein) on value words.
        val valueWords = value.split("\\W+".toRegex())
        val queryWords2 = query.split("\\W+".toRegex())
        var fuzzyScore = 0f
        for (qWord in queryWords2) {
            if (qWord.length < 3) continue  // too short for fuzzy
            for (vWord in valueWords) {
                if (vWord.length < 3) continue
                val dist = levenshtein(qWord, vWord)
                val maxLen = max(qWord.length, vWord.length)
                if (dist <= MAX_LEVENSHTEIN_DISTANCE) {
                    val wordFuzzy = 1f - dist.toFloat() / maxLen.toFloat()
                    fuzzyScore = max(fuzzyScore, wordFuzzy * 0.5f)
                }
            }
        }

        return fuzzyScore
    }

    /** Field weighting coefficient (title is more important). */
    private fun fieldWeight(field: TellicoField): Float = when {
        field.name == "title"       -> 2.0f
        field.name == "author"      -> 1.8f
        field.isSearchable          -> 1.5f
        else                        -> 1.0f
    }

    /**
     * Distance de Levenshtein (edit distance).
     *
     * Measures the minimum number of operations (insert, delete, substitute)
     * to transform one string into another.
     *
     * Classic implementation with memory optimisation (single row).
     * Complexité : O(m*n) en temps, O(min(m,n)) en espace.
     *
     * Ex: levenshtein("livre", "livres") = 1
     *     levenshtein("python", "pithon") = 1
     */
    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        val m = a.length
        val n = b.length

        // Optimisation : garder la chaîne courte en ligne
        val (s, t, rows, cols) = if (m < n)
            quadruple(a, b, m + 1, n + 1)
        else
            quadruple(b, a, n + 1, m + 1)

        var prev = IntArray(cols) { it }
        var curr = IntArray(cols)

        for (i in 1 until rows) {
            curr[0] = i
            for (j in 1 until cols) {
                val cost = if (s[i - 1] == t[j - 1]) 0 else 1
                curr[j] = minOf(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val tmp = prev; prev = curr; curr = tmp
        }
        return prev[cols - 1]
    }

    private data class Quadruple<A,B,C,D>(val a:A, val b:B, val c:C, val d:D)
    private fun quadruple(a:String,b:String,c:Int,d:Int) = Quadruple(a,b,c,d)
}
