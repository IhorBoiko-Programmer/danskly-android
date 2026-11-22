package app.danskly.core.json

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonTest {

    @Test
    fun parsesAllValueTypes() {
        val v = Json.parse(""" { "s":"hej", "n": -1.5e2, "i": 42, "t": true, "f": false, "z": null, "a": [1, "x", []], "o": {} } """).obj()
        assertEquals("hej", v.str("s"))
        assertEquals(-150.0, v.double("n", 0.0), 0.0)
        assertEquals(42, v.int("i"))
        assertTrue(v.bool("t"))
        assertFalse(v.bool("f", default = true))
        assertEquals(JsonValue.Null, v.fields["z"])
        assertEquals(3, v.list("a").size)
        assertEquals(emptyMap<String, JsonValue>(), v.child("o")!!.fields)
    }

    @Test
    fun parsesEscapesAndUnicode() {
        val s = Json.parse("\"a\\\"b\\\\c\\/d\\ne\\rf\\tg\\bh\\fi\\u00e6\"")
        assertEquals(JsonValue.Str("a\"b\\c/d\ne\rf\tg\bh\u000Ciæ"), s)
        assertEquals(JsonValue.Str("æøå"), Json.parse("\"æøå\""))
    }

    @Test
    fun parsesTopLevelArraysAndEmptyContainers() {
        assertEquals(JsonValue.Arr(emptyList()), Json.parse(" [ ] "))
        assertEquals(listOf(JsonValue.Num(1.0), JsonValue.Num(2.0)), Json.parse("[1,2]").arr())
    }

    @Test
    fun rejectsMalformedJson() {
        val bad = listOf(
            "", "{", "[1,", "[1 2]", "{\"a\" 1}", "{\"a\":1 \"b\":2}", "{1:2}", "tru", "nul", "fals", "x",
            "\"abc", "\"\\x\"", "\"\\u12\"", "\"\\uzzzz\"", "-", "1.2.3", "[1]]", "{\"a\":}",
        )
        for (text in bad) {
            assertThrows(text) { Json.parse(text) }
        }
    }

    @Test
    fun readingHelpersFailOrDefaultSensibly() {
        val o = Json.parse("""{"s":"x","n":3}""").obj()
        assertThrows("str") { o.str("missing") }
        assertNull(o.strOrNull("n"))
        assertEquals(7L, o.long("missing", 7))
        assertEquals(2.5, o.double("missing", 2.5), 0.0)
        assertEquals(emptyList<JsonValue>(), o.list("s"))
        assertNull(o.child("s"))
        assertEquals(emptyList<String>(), o.strings("missing"))
        assertThrows("obj") { JsonValue.Num(1.0).obj() }
        assertThrows("arr") { JsonValue.Num(1.0).arr() }
    }

    @Test
    fun writesAndRoundTrips() {
        val value = json(
            "text" to "quote\" back\\ nl\n cr\r tab\t ctl\u0001 æ",
            "int" to 3,
            "neg" to -2L,
            "frac" to 0.25,
            "big" to 1e20,
            "inf" to Double.POSITIVE_INFINITY,
            "yes" to true,
            "none" to null,
            "list" to listOf(1, "a", null),
            "map" to mapOf("k" to false),
            "raw" to JsonValue.Num(5.0),
        )
        val text = Json.write(value)
        assertTrue(text.contains("\"int\":3"))
        assertTrue(text.contains("\"frac\":0.25"))
        assertTrue(text.contains("\\u0001"))
        val back = Json.parse(text.replace("Infinity", "0")).obj()
        assertEquals("quote\" back\\ nl\n cr\r tab\t ctl\u0001 æ", back.str("text"))
        assertEquals(listOf(JsonValue.Num(1.0), JsonValue.Str("a"), JsonValue.Null), back.list("list"))
        assertEquals("[]", Json.write(JsonValue.Arr(emptyList())))
        assertEquals("{}", Json.write(JsonValue.Obj(emptyMap())))
    }

    @Test
    fun refusesUnsupportedTypes() {
        assertThrows("type") { Any().toJson() }
    }

    private fun assertThrows(label: String, block: () -> Unit) {
        try {
            block()
        } catch (e: JsonException) {
            return
        }
        throw AssertionError("Expected JsonException for: $label")
    }
}
