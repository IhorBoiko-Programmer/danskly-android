package app.danskly.core.content

import app.danskly.core.TestContent
import app.danskly.core.model.Article
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checks the real bundled content, so broken data never ships. */
class ContentParserTest {

    private val content = TestContent.content

    @Test
    fun loadsEverything() {
        assertEquals(42, content.topics.size)
        assertTrue(content.words.size >= 1100)
        assertEquals(13, content.grammar.size)
        assertTrue(content.ruleCount >= 115)
        assertEquals(6, content.writingTasks.size)
        assertEquals(6, content.writingPhrases.size)
        assertEquals(6, content.reading.size)
        assertTrue(content.verbs.size >= 130)
        assertTrue(content.nouns.isNotEmpty())
        assertTrue(content.adjectives.isNotEmpty())
        assertEquals(29, content.alphabet.size)
        assertEquals(9, content.sounds.size)
        assertEquals(ContentFiles.ALL.size, 6)
    }

    @Test
    fun topicsAreSmallAndGrouped() {
        assertEquals(listOf("Корисні фрази"), content.topics.filter { it.group.isEmpty() }.map { it.name })
        assertEquals(9, content.topics.map { it.group }.filter { it.isNotEmpty() }.distinct().size)
        val sizes = content.topics.associate { t -> t.name to content.words.count { it.topic == t.name } }
        assertTrue("every topic has words: $sizes", sizes.values.all { it > 0 })
        assertTrue("subtopics stay small: $sizes", sizes.filterKeys { it != "Корисні фрази" }.values.all { it <= 45 })
    }

    @Test
    fun wordsAreClean() {
        val names = content.topics.map { it.name }.toSet()
        assertEquals("duplicate words", content.words.size, content.words.map { it.danish }.toSet().size)
        assertTrue(content.words.all { it.topic in names && it.danish.isNotBlank() && it.ukrainian.isNotBlank() })
        val bog = content.words.first { it.danish == "bog" }
        assertEquals(Article.EN, bog.article)
        assertEquals("en bog", bog.display)
    }

    @Test
    fun everyExampleHasATranslation() {
        content.grammar.flatMap { it.rules }.flatMap { it.examples }.forEach { assertTrue(it.danish, it.ukrainian.isNotBlank()) }
        content.sounds.flatMap { it.examples }.forEach { assertTrue(it.ukrainian.isNotBlank()) }
    }

    @Test
    fun readingQuestionsHaveThreeOptions() {
        content.reading.forEach { text -> text.questions.forEach { assertEquals(3, it.options.size) } }
    }

    @Test
    fun formTablesAreConsistent() {
        assertTrue(content.verbs.values.all { it.auxiliary in setOf("har", "er", "har/er") })
        assertEquals("gik", content.verbs.getValue("gå").past)
        assertEquals("børn", content.nouns.getValue("barn").plural)
        assertEquals("bedre", content.adjectives.getValue("god").comparative)
    }
}
