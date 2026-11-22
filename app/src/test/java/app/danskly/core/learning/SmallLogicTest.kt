package app.danskly.core.learning

import app.danskly.core.model.Article
import app.danskly.core.model.CardState
import app.danskly.core.model.Word
import app.danskly.core.time.FixedClock
import app.danskly.core.time.SystemClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class SpacedRepetitionTest {
    private val srs = SpacedRepetition()
    private val day = FixedClock.DAY_MILLIS

    @Test
    fun climbsBoxesAndCapsAtTheTop() {
        var s: CardState? = null
        val expectedDays = listOf(1, 3, 7, 21, 21)
        for (d in expectedDays) {
            s = srs.grade(s, true, 0)
            assertEquals(d * day, s.dueAt)
        }
        assertEquals(srs.maxBox, s!!.box)
    }

    @Test
    fun wrongAnswerResetsAndIsDueNow() {
        val s = srs.grade(CardState(3, 999), false, 100)
        assertEquals(CardState(0, 100), s)
        assertTrue(srs.isDue(s, 100))
        assertFalse(srs.isDue(CardState(1, 101), 100))
    }

    @Test(expected = IllegalArgumentException::class)
    fun needsIntervals() {
        SpacedRepetition(emptyList())
    }
}

class CustomWordFactoryTest {

    @Test
    fun detectsKindFromTypedText() {
        assertEquals(WordKind.NOUN_EN, CustomWordFactory.detectKind(" En bog"))
        assertEquals(WordKind.NOUN_ET, CustomWordFactory.detectKind("et hus"))
        assertEquals(WordKind.VERB, CustomWordFactory.detectKind("at gå"))
        assertNull(CustomWordFactory.detectKind("hurtig"))
    }

    @Test
    fun cleansNounsVerbsAndOthers() {
        assertEquals(NewWordResult.Ok(Word("skraldespand", "смітник", "T", Article.EN, true)), CustomWordFactory.create(" en  skraldespand ", "смітник ", WordKind.NOUN_EN, "T"))
        assertEquals(NewWordResult.Ok(Word("vindue", "вікно", "T", Article.ET, true)), CustomWordFactory.create("vindue", "вікно", WordKind.NOUN_ET, "T"))
        assertEquals(NewWordResult.Ok(Word("at støvsuge", "пилососити", "T", null, true)), CustomWordFactory.create("støvsuge", "пилососити", WordKind.VERB, "T"))
        assertEquals(NewWordResult.Ok(Word("at gå", "йти", "T", null, true)), CustomWordFactory.create("at gå", "йти", WordKind.VERB, "T"))
        assertEquals(NewWordResult.Ok(Word("bil", "авто", "T", Article.EN, true)), CustomWordFactory.create("at bil", "авто", WordKind.NOUN_EN, "T"))
        assertEquals(NewWordResult.Ok(Word("hurtig", "швидкий", "T", null, true)), CustomWordFactory.create("hurtig", "швидкий", WordKind.OTHER, "T"))
    }

    @Test
    fun requiresBothFields() {
        assertEquals(NewWordResult.MissingFields, CustomWordFactory.create(" ", "x", WordKind.OTHER, "T"))
        assertEquals(NewWordResult.MissingFields, CustomWordFactory.create("bog", " ", WordKind.OTHER, "T"))
        assertEquals(NewWordResult.MissingFields, CustomWordFactory.create("", "x", WordKind.VERB, "T"))
    }
}

class ClockTest {
    @Test
    fun fixedClockAdvancesByDays() {
        val c = FixedClock(0)
        assertEquals(LocalDate.of(1970, 1, 1), c.today())
        c.advanceDays(2)
        assertEquals(LocalDate.of(1970, 1, 3), c.today())
        assertEquals(2 * FixedClock.DAY_MILLIS, c.nowMillis())
    }

    @Test
    fun systemClockReturnsNow() {
        val c = SystemClock(ZoneId.of("UTC"))
        assertTrue(Math.abs(c.nowMillis() - System.currentTimeMillis()) < 5_000)
        assertEquals(LocalDate.now(ZoneId.of("UTC")), c.today())
        assertTrue(SystemClock().today().year >= 2024)
    }
}

class WordModelTest {
    @Test
    fun displayAndVerbHelpers() {
        val verb = Word("at glæde sig", "радіти", "T")
        assertTrue(verb.isVerb)
        assertEquals("glæde sig", verb.verbBase)
        assertEquals("at glæde sig", verb.display)
        assertEquals("et hus", Word("hus", "дім", "T", Article.ET).display)
        assertNull(Article.fromText("xx"))
        assertNull(Article.fromText(null))
    }
}
