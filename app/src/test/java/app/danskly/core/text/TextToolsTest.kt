package app.danskly.core.text

import org.junit.Assert.assertEquals
import org.junit.Test

class TextToolsTest {

    @Test
    fun normalizeIgnoresCasePunctuationAndSpaces() {
        assertEquals("en bok", TextTools.normalize("  En   Bok. "))
        assertEquals("hvad hedder du", TextTools.normalize("«Hvad hedder du?»"))
    }

    @Test
    fun levenshteinCountsEdits() {
        assertEquals(0, TextTools.levenshtein("bog", "bog"))
        assertEquals(1, TextTools.levenshtein("bog", "bok"))
        assertEquals(1, TextTools.levenshtein("bog", "bogs"))
        assertEquals(3, TextTools.levenshtein("", "abc"))
        assertEquals(2, TextTools.levenshtein("bgo", "bog"))
    }

    @Test
    fun forSpeechCleansMarkup() {
        assertEquals("at bo, jeg bor", TextTools.forSpeech("at bo → jeg bo*r*"))
        assertEquals("at være, var, været", TextTools.forSpeech("at være – var – været"))
        assertEquals("Jeg tror at, Jeg tror", TextTools.forSpeech("Jeg tror at / Jeg tror"))
    }

    @Test
    fun removesSpeakerNames() {
        assertEquals("Hej.\nGoddag.", TextTools.withoutSpeakerNames("Sekretær: Hej.\nAhmad: Goddag."))
    }

    @Test
    fun splitsSentencesButKeepsAbbreviations() {
        assertEquals(listOf("Hej!", "Kan I komme i dag kl. 14.30?", "Ja."), TextTools.sentences("Hej! Kan I komme i dag kl. 14.30? Ja."))
        assertEquals(listOf("Ø. øl"), TextTools.sentences("Ø. øl"))
        assertEquals(listOf("a", "b"), TextTools.sentences("a\n\n b"))
    }

    @Test
    fun highlightsSplitMarkedParts() {
        assertEquals(
            listOf(TextSegment("Jeg ", false), TextSegment("bor", true), TextSegment(" her.", false)),
            TextTools.highlights("Jeg *bor* her."),
        )
        assertEquals(listOf(TextSegment("bog", false), TextSegment("en", true)), TextTools.highlights("bog*en*"))
    }
}
