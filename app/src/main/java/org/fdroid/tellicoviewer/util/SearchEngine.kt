package org.fdroid.tellicoviewer.util

import org.fdroid.tellicoviewer.data.model.ScoredEntry
import org.fdroid.tellicoviewer.data.model.TellicoEntry
import org.fdroid.tellicoviewer.data.model.TellicoField
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Moteur de recherche avec scoring et fuzzy matching.
 *
 * ALGORITHME :
 * La recherche combine plusieurs stratégies par ordre de précision :
 *
 * 1. EXACTE (score 1.0)      : la chaîne entière correspond
 * 2. PRÉFIXE (score 0.9)     : la valeur commence par le terme
 * 3. CONTIENT (score 0.7)    : la valeur contient le terme
 * 4. FUZZY (score variable)  : distance de Levenshtein normalisée
 *
 * Le score final prend en compte le champ concerné :
 * - Titre : coefficient ×2.0
 * - Champs searchable : coefficient ×1.5
 * - Autres champs : coefficient ×1.0
 *
 * OPTIMISATION :
 * Pour 10 000 articles, on utilise l'index FTS SQLite (via EntryDao.searchFtsPaged)
 * pour la première passe. Le fuzzy scoring en Kotlin n'est appliqué qu'en mode
 * "recherche avancée" ou sur les résultats FTS pour re-trier.
 *
 * Analogie : comme grep -i (containment), agrep (fuzzy), et un score TF-IDF simplifié.
 */
@Singleton
class SearchEngine @Inject constructor() {

    companion object {
        const val MIN_SCORE_THRESHOLD = 0.3f  // score minimum pour inclure un résultat
        const val MAX_LEVENSHTEIN_DISTANCE = 3 // distance max pour le fuzzy matching
    }

    /**
     * Recherche et classement d'une liste d'entrées.
     *
     * @param entries Liste des articles à filtrer
     * @param query   Terme de recherche
     * @param fields  Schéma pour connaître les types et priorités des champs
     * @param fieldFilter Restreindre la recherche à un champ spécifique
     * @return Liste triée par score décroissant
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
     * Score de correspondance entre une valeur et la requête.
     * Retourne un float entre 0.0 (aucune correspondance) et 1.0 (exact).
     */
    private fun scoreValue(value: String, query: String): Float {
        // 1. Correspondance exacte
        if (value == query) return 1.0f

        // 2. Préfixe
        if (value.startsWith(query)) return 0.9f

        // 3. Contient (substring)
        if (value.contains(query)) return 0.7f

        // 4. Recherche mot par mot (multi-termes)
        val queryWords = query.split("\\s+".toRegex())
        if (queryWords.size > 1) {
            val wordScore = queryWords.count { word -> value.contains(word) }.toFloat() /
                    queryWords.size.toFloat()
            if (wordScore > 0.5f) return wordScore * 0.65f
        }

        // 5. Fuzzy matching (Levenshtein) sur les mots de la valeur
        val valueWords = value.split("\\W+".toRegex())
        val queryWords2 = query.split("\\W+".toRegex())
        var fuzzyScore = 0f
        for (qWord in queryWords2) {
            if (qWord.length < 3) continue  // trop court pour fuzzy
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

    /** Coefficient de pondération selon le champ (titre plus important) */
    private fun fieldWeight(field: TellicoField): Float = when {
        field.name == "title"       -> 2.0f
        field.name == "author"      -> 1.8f
        field.isSearchable          -> 1.5f
        else                        -> 1.0f
    }

    /**
     * Distance de Levenshtein (edit distance).
     *
     * Mesure le nombre minimum d'opérations (insertion, suppression, substitution)
     * pour transformer une chaîne en une autre.
     *
     * Implémentation classique avec optimisation mémoire (une seule ligne).
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
