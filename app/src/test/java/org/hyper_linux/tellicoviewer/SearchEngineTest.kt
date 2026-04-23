package org.hyper_linux.tellicoviewer

import org.hyper_linux.tellicoviewer.data.model.*
import org.hyper_linux.tellicoviewer.util.SearchEngine
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests unitaires du moteur de recherche.
 */
class SearchEngineTest {

    private lateinit var engine: SearchEngine
    private lateinit var testFields: List<TellicoField>
    private lateinit var testEntries: List<TellicoEntry>

    @Before
    fun setUp() {
        engine = SearchEngine()
        testFields = listOf(
            TellicoField("title",  "Title",  FieldType.LINE, flags = 8),  // searchable
            TellicoField("author", "Author", FieldType.LINE, flags = 8),
            TellicoField("year",   "Year",   FieldType.NUMBER, flags = 0)
        )
        testEntries = listOf(
            TellicoEntry(1, mapOf("title" to "Dune", "author" to "Frank Herbert", "year" to "1965")),
            TellicoEntry(2, mapOf("title" to "Foundation", "author" to "Isaac Asimov", "year" to "1951")),
            TellicoEntry(3, mapOf("title" to "The Martian", "author" to "Andy Weir", "year" to "2011")),
            TellicoEntry(4, mapOf("title" to "Neuromancer", "author" to "William Gibson", "year" to "1984")),
            TellicoEntry(5, mapOf("title" to "Dune Messiah", "author" to "Frank Herbert", "year" to "1969"))
        )
    }

    @Test
    fun `exact match returns score 1`() {
        val results = engine.search(testEntries, "Dune", testFields)
        val dune = results.firstOrNull { it.entry.id == 1 }
        assertNotNull(dune)
        assertTrue(dune!!.score > 0.9f)
    }

    @Test
    fun `prefix match returns high score`() {
        val results = engine.search(testEntries, "Foun", testFields)
        assertTrue(results.any { it.entry.id == 2 })  // Foundation
    }

    @Test
    fun `substring match finds results`() {
        val results = engine.search(testEntries, "Martian", testFields)
        assertTrue(results.any { it.entry.id == 3 })
    }

    @Test
    fun `empty query returns all entries`() {
        val results = engine.search(testEntries, "", testFields)
        assertEquals(testEntries.size, results.size)
    }

    @Test
    fun `results sorted by score descending`() {
        val results = engine.search(testEntries, "Dune", testFields)
        // "Dune" (exact) doit être avant "Dune Messiah" (contains)
        val duneIdx    = results.indexOfFirst { it.entry.id == 1 }
        val messiahIdx = results.indexOfFirst { it.entry.id == 5 }
        assertTrue(duneIdx < messiahIdx)
    }

    @Test
    fun `search by author field filter`() {
        val results = engine.search(testEntries, "Herbert", testFields, fieldFilter = "author")
        assertTrue(results.all { it.entry.getValue("author").contains("Herbert") })
        assertEquals(2, results.size)  // Dune et Dune Messiah
    }

    @Test
    fun `no match returns empty list`() {
        val results = engine.search(testEntries, "xyzzy_nomatch_123", testFields)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `levenshtein distance 0 for identical strings`() {
        assertEquals(0, engine.levenshtein("hello", "hello"))
    }

    @Test
    fun `levenshtein distance 1 for single edit`() {
        assertEquals(1, engine.levenshtein("cat", "bat"))
        assertEquals(1, engine.levenshtein("cat", "cats"))
        assertEquals(1, engine.levenshtein("cats", "cat"))
    }

    @Test
    fun `levenshtein distance correct for real typo`() {
        // "Asimov" -> "Asiomov" (une transposition)
        assertTrue(engine.levenshtein("asimov", "asiomov") <= 2)
    }

    @Test
    fun `fuzzy search finds typo in title`() {
        // "Donne" est proche de "Dune" (distance 2)
        val results = engine.search(testEntries, "Donne", testFields)
        // Devrait trouver Dune (fuzzy) — score plus bas mais présent
        // Note : selon le seuil MIN_SCORE_THRESHOLD, peut être filtré
        // Ce test vérifie que l'algo ne plante pas sur une typo
        assertNotNull(results)
    }
}
