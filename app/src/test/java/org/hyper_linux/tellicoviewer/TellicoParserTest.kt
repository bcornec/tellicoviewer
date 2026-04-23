package org.hyper_linux.tellicoviewer

import kotlinx.coroutines.test.runTest
import org.hyper_linux.tellicoviewer.data.model.*
import org.hyper_linux.tellicoviewer.data.parser.TellicoParser
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Tests unitaires du parseur Tellico.
 *
 * Ces tests s'exécutent sur la JVM locale (pas besoin d'émulateur Android).
 * Commande : ./gradlew test
 *
 * STRATÉGIE DE TEST :
 * On crée de "faux" fichiers .tc en mémoire (ZIP + XML) pour tester
 * le parseur sans dépendre de fichiers externes.
 */
class TellicoParserTest {

    private val parser = TellicoParser()

    // ---------------------------------------------------------------------------
    // Helpers pour créer des fichiers .tc de test
    // ---------------------------------------------------------------------------

    private fun createTcFile(xml: String, images: Map<String, ByteArray> = emptyMap()): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            // tellico.xml
            zip.putNextEntry(ZipEntry("tellico.xml"))
            zip.write(xml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            // Images optionnelles
            images.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry("images/$name"))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    private val minimalTellicoXml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <tellico version="2.0">
          <collection id="1" title="Test Collection" type="2">
            <fields>
              <field name="title"  title="Title"  type="1" flags="8" category="General"/>
              <field name="author" title="Author" type="1" flags="7" category="General"/>
              <field name="year"   title="Year"   type="6" flags="0" category="Publishing"/>
              <field name="rating" title="Rating" type="14" flags="0" category="Personal"/>
            </fields>
            <entry id="1">
              <title>Dune</title>
              <author>Frank Herbert</author>
              <year>1965</year>
              <rating>5</rating>
            </entry>
            <entry id="2">
              <title>Foundation</title>
              <author>Isaac Asimov</author>
              <year>1951</year>
            </entry>
          </collection>
        </tellico>
    """.trimIndent()

    // ---------------------------------------------------------------------------
    // Tests de parsing
    // ---------------------------------------------------------------------------

    @Test
    fun `parse minimal tellico file returns correct collection`() = runTest {
        val tcFile = createTcFile(minimalTellicoXml)
        val result = parser.parse(tcFile.inputStream())

        assertEquals("Test Collection", result.collection.title)
        assertEquals(CollectionType.BOOKS, result.collection.type)
        assertEquals(1, result.collection.id)
    }

    @Test
    fun `parse extracts all fields with correct types`() = runTest {
        val tcFile = createTcFile(minimalTellicoXml)
        val result = parser.parse(tcFile.inputStream())
        val fields = result.collection.fields

        assertEquals(4, fields.size)
        assertEquals("title", fields[0].name)
        assertEquals(FieldType.LINE, fields[0].type)
        assertEquals("year", fields[2].name)
        assertEquals(FieldType.NUMBER, fields[2].type)
        assertEquals("rating", fields[3].name)
        assertEquals(FieldType.RATING, fields[3].type)
    }

    @Test
    fun `parse extracts correct number of entries`() = runTest {
        val tcFile = createTcFile(minimalTellicoXml)
        val result = parser.parse(tcFile.inputStream())

        assertEquals(2, result.collection.entries.size)
    }

    @Test
    fun `parse extracts entry field values correctly`() = runTest {
        val tcFile = createTcFile(minimalTellicoXml)
        val result = parser.parse(tcFile.inputStream())
        val dune = result.collection.entries.first { it.id == 1 }

        assertEquals("Dune", dune.getValue("title"))
        assertEquals("Frank Herbert", dune.getValue("author"))
        assertEquals("1965", dune.getValue("year"))
        assertEquals("5", dune.getValue("rating"))
    }

    @Test
    fun `parse returns empty string for missing field`() = runTest {
        val tcFile = createTcFile(minimalTellicoXml)
        val result = parser.parse(tcFile.inputStream())
        val asimov = result.collection.entries.first { it.id == 2 }

        // Foundation n'a pas de rating
        assertEquals("", asimov.getValue("rating"))
    }

    @Test
    fun `parse extracts images from zip`() = runTest {
        val fakeImage = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())  // JPEG magic
        val tcFile = createTcFile(
            minimalTellicoXml,
            images = mapOf("cover.jpg" to fakeImage)
        )
        val result = parser.parse(tcFile.inputStream())

        assertTrue(result.images.containsKey("cover.jpg"))
        assertArrayEquals(fakeImage, result.images["cover.jpg"])
    }

    @Test
    fun `parse choice field with allowed values`() = runTest {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tellico version="2.0">
              <collection id="1" title="DVDs" type="3">
                <fields>
                  <field name="medium" title="Medium" type="3" allowed="DVD;Blu-ray;Digital" category="General"/>
                </fields>
              </collection>
            </tellico>
        """.trimIndent()

        val result = parser.parse(createTcFile(xml).inputStream())
        val field = result.collection.fields.first { it.name == "medium" }

        assertEquals(FieldType.CHOICE, field.type)
        assertEquals(listOf("DVD", "Blu-ray", "Digital"), field.allowed)
    }

    @Test
    fun `parse table field accumulates multiple values`() = runTest {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tellico version="2.0">
              <collection id="1" title="Movies" type="3">
                <fields>
                  <field name="genre" title="Genre" type="8" flags="6" category="General"/>
                </fields>
                <entry id="1">
                  <genre>Science Fiction</genre>
                  <genre>Adventure</genre>
                </entry>
              </collection>
            </tellico>
        """.trimIndent()

        val result = parser.parse(createTcFile(xml).inputStream())
        val entry = result.collection.entries.first()
        val genres = entry.getList("genre")

        assertTrue(genres.contains("Science Fiction"))
        assertTrue(genres.contains("Adventure"))
    }

    @Test(expected = org.hyper_linux.tellicoviewer.data.parser.TellicoParseException::class)
    fun `parse invalid zip throws TellicoParseException`() = runTest {
        val invalidData = "not a zip file".toByteArray()
        parser.parse(invalidData.inputStream())
    }

    @Test(expected = org.hyper_linux.tellicoviewer.data.parser.TellicoParseException::class)
    fun `parse zip without tellico xml throws TellicoParseException`() = runTest {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            zip.putNextEntry(ZipEntry("other.txt"))
            zip.write("hello".toByteArray())
            zip.closeEntry()
        }
        parser.parse(baos.toByteArray().inputStream())
    }
}
