package app.danskly.core.learning

import app.danskly.core.TestContent
import app.danskly.core.model.Article
import app.danskly.core.model.VerbEntry
import app.danskly.core.model.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WordFormsBuilderTest {

    private val builder = WordFormsBuilder(TestContent.content)
    private fun forms(word: Word) = builder.formsOf(word)!!.rows.associate { it.label to it.form }

    @Test
    fun conjugatesIrregularMotionVerb() {
        val f = forms(Word("at gå", "йти", "T"))
        assertEquals("at gå", f["Інфінітив"])
        assertEquals("går", f["Теперішній"])
        assertEquals("gik", f["Минулий"])
        assertEquals("er gået", f["Перфект"])
        assertEquals("var gået", f["Плюсквамперфект"])
        assertEquals("vil/skal gå", f["Майбутній"])
        assertEquals("ville gå", f["Умовний"])
        assertEquals("gå!", f["Наказовий"])
        assertEquals(FormsKind.VERB, builder.formsOf(Word("at gå", "йти", "T"))!!.kind)
    }

    @Test
    fun handlesMixedAuxiliaryReflexiveDeponentAndModalVerbs() {
        assertEquals("har/er rejst", forms(Word("at rejse", "", "T"))["Перфект"])
        assertEquals("havde/var rejst", forms(Word("at rejse", "", "T"))["Плюсквамперфект"])
        val reflexive = forms(Word("at glæde sig", "", "T"))
        assertEquals("glæder sig", reflexive["Теперішній"])
        assertEquals("har glædet sig", reflexive["Перфект"])
        assertEquals("glæd dig!", reflexive["Наказовий"])
        val deponent = forms(Word("at synes", "", "T"))
        assertEquals("syntes", deponent["Минулий"])
        assertNull(deponent["Наказовий"])
        assertEquals("havde syntes", deponent["Плюсквамперфект"])
        assertNull(forms(Word("at kunne", "", "T"))["Наказовий"])
    }

    @Test
    fun everyTenseHasAHint() {
        builder.conjugate(TestContent.content.verbs.getValue("spise")).forEach { assertTrue(it.label, it.hint != null) }
    }

    @Test
    fun imperativeRules() {
        assertEquals("spis", builder.imperative("spise"))
        assertEquals("kom", builder.imperative("komme"))
        assertEquals("gå", builder.imperative("gå"))
        assertEquals("se", builder.imperative("se"))
        assertEquals("bo", builder.imperative("bo"))
        assertEquals("sov", builder.imperative("sove"))
        assertEquals("tag", builder.imperative("tage"))
        assertEquals("ee", builder.imperative("eee"))
    }

    @Test
    fun nounAndAdjectiveTables() {
        val noun = builder.formsOf(Word("barn", "дитина", "T", Article.ET))!!
        assertEquals(FormsKind.NOUN, noun.kind)
        assertEquals(listOf("et barn", "barnet", "børn", "børnene"), noun.rows.map { it.form })
        val adj = builder.formsOf(Word("god", "добрий", "T"))!!
        assertEquals(FormsKind.ADJECTIVE, adj.kind)
        assertEquals("et godt …", adj.rows[1].form)
        assertEquals("bedst", adj.rows[4].form)
    }

    @Test
    fun returnsNullWhenNothingIsKnown() {
        assertNull(builder.formsOf(Word("at støvsuge", "", "T")))
        assertNull(builder.formsOf(Word("at stå op", "", "T")))
        assertNull(builder.formsOf(Word("barn", "", "T")))
        assertNull(builder.formsOf(Word("måske", "", "T")))
    }

    @Test
    fun findsGrammarExamplesWithTheWord() {
        val gaa = builder.formsOf(Word("at gå", "", "T"))!!
        val found = builder.examplesWith(gaa.spellings)
        assertTrue(found.isNotEmpty())
        assertTrue(found.all { !it.example.danish.contains('*') && it.example.danish.contains(' ') })
        assertTrue(builder.examplesWith(listOf("x")).isEmpty())
        assertEquals(2, builder.examplesWith(listOf("jeg"), limit = 2).size)
    }

    @Test
    fun customVerbEntryWithErAuxiliary() {
        val rows = builder.conjugate(VerbEntry("vågne", "vågner", "vågnede", "vågnet", "er")).associate { it.label to it.form }
        assertEquals("er vågnet", rows["Перфект"])
        assertEquals("var vågnet", rows["Плюсквамперфект"])
        assertEquals("vågn!", rows["Наказовий"])
    }
}
