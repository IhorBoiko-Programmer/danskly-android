package app.danskly.core.json

/**
 * A tiny, dependency-free JSON model with a parser and a writer.
 *
 * Used for the bundled content, saved progress and AI responses, so the whole
 * data layer stays plain Kotlin and can be unit-tested on the JVM.
 */
sealed interface JsonValue {
    data class Str(val value: String) : JsonValue
    data class Num(val value: Double) : JsonValue
    data class Bool(val value: Boolean) : JsonValue
    data class Arr(val items: List<JsonValue>) : JsonValue
    data class Obj(val fields: Map<String, JsonValue>) : JsonValue
    data object Null : JsonValue
}

class JsonException(message: String) : RuntimeException(message)

// ---------- reading helpers ----------

fun JsonValue.obj(): JsonValue.Obj = this as? JsonValue.Obj ?: throw JsonException("Expected object")

fun JsonValue.arr(): List<JsonValue> = (this as? JsonValue.Arr)?.items ?: throw JsonException("Expected array")

fun JsonValue.Obj.str(key: String): String =
    (fields[key] as? JsonValue.Str)?.value ?: throw JsonException("Missing string '$key'")

fun JsonValue.Obj.strOrNull(key: String): String? = (fields[key] as? JsonValue.Str)?.value

fun JsonValue.Obj.long(key: String, default: Long = 0): Long = (fields[key] as? JsonValue.Num)?.value?.toLong() ?: default

fun JsonValue.Obj.int(key: String, default: Int = 0): Int = long(key, default.toLong()).toInt()

fun JsonValue.Obj.double(key: String, default: Double): Double = (fields[key] as? JsonValue.Num)?.value ?: default

fun JsonValue.Obj.bool(key: String, default: Boolean = false): Boolean = (fields[key] as? JsonValue.Bool)?.value ?: default

fun JsonValue.Obj.list(key: String): List<JsonValue> = (fields[key] as? JsonValue.Arr)?.items.orEmpty()

fun JsonValue.Obj.child(key: String): JsonValue.Obj? = fields[key] as? JsonValue.Obj

fun JsonValue.Obj.strings(key: String): List<String> = list(key).mapNotNull { (it as? JsonValue.Str)?.value }

// ---------- building helpers ----------

fun json(vararg pairs: Pair<String, Any?>): JsonValue.Obj = JsonValue.Obj(pairs.associate { (k, v) -> k to v.toJson() })

fun Any?.toJson(): JsonValue = when (this) {
    null -> JsonValue.Null
    is JsonValue -> this
    is String -> JsonValue.Str(this)
    is Boolean -> JsonValue.Bool(this)
    is Number -> JsonValue.Num(this.toDouble())
    is Iterable<*> -> JsonValue.Arr(this.map { it.toJson() })
    is Map<*, *> -> JsonValue.Obj(this.entries.associate { (k, v) -> k.toString() to v.toJson() })
    else -> throw JsonException("Cannot convert ${this::class.simpleName} to JSON")
}

object Json {

    fun parse(text: String): JsonValue = Parser(text).parseDocument()

    fun write(value: JsonValue): String = StringBuilder().also { writeTo(it, value) }.toString()

    private fun writeTo(out: StringBuilder, value: JsonValue) {
        when (value) {
            is JsonValue.Str -> writeString(out, value.value)
            is JsonValue.Num -> out.append(formatNumber(value.value))
            is JsonValue.Bool -> out.append(value.value)
            is JsonValue.Null -> out.append("null")
            is JsonValue.Arr -> {
                out.append('[')
                value.items.forEachIndexed { i, item ->
                    if (i > 0) out.append(',')
                    writeTo(out, item)
                }
                out.append(']')
            }
            is JsonValue.Obj -> {
                out.append('{')
                value.fields.entries.forEachIndexed { i, (k, v) ->
                    if (i > 0) out.append(',')
                    writeString(out, k)
                    out.append(':')
                    writeTo(out, v)
                }
                out.append('}')
            }
        }
    }

    private fun formatNumber(d: Double): String =
        if (d == Math.floor(d) && !d.isInfinite() && Math.abs(d) < 1e15) d.toLong().toString() else d.toString()

    private fun writeString(out: StringBuilder, s: String) {
        out.append('"')
        for (c in s) {
            when (c) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> if (c < ' ') out.append("\\u%04x".format(c.code)) else out.append(c)
            }
        }
        out.append('"')
    }

    private class Parser(private val s: String) {
        private var i = 0

        fun parseDocument(): JsonValue {
            val v = value()
            skipWs()
            if (i != s.length) fail("Unexpected trailing characters")
            return v
        }

        private fun value(): JsonValue {
            skipWs()
            if (i >= s.length) fail("Unexpected end")
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> JsonValue.Str(string())
                't' -> literal("true", JsonValue.Bool(true))
                'f' -> literal("false", JsonValue.Bool(false))
                'n' -> literal("null", JsonValue.Null)
                else -> if (c == '-' || c.isDigit()) number() else fail("Unexpected '$c'")
            }
        }

        private fun obj(): JsonValue.Obj {
            i++
            val fields = LinkedHashMap<String, JsonValue>()
            skipWs()
            if (peek() == '}') {
                i++
                return JsonValue.Obj(fields)
            }
            while (true) {
                skipWs()
                if (peek() != '"') fail("Expected key")
                val key = string()
                skipWs()
                expect(':')
                fields[key] = value()
                skipWs()
                when (next()) {
                    ',' -> continue
                    '}' -> return JsonValue.Obj(fields)
                    else -> fail("Expected , or }")
                }
            }
        }

        private fun arr(): JsonValue.Arr {
            i++
            val items = ArrayList<JsonValue>()
            skipWs()
            if (peek() == ']') {
                i++
                return JsonValue.Arr(items)
            }
            while (true) {
                items += value()
                skipWs()
                when (next()) {
                    ',' -> continue
                    ']' -> return JsonValue.Arr(items)
                    else -> fail("Expected , or ]")
                }
            }
        }

        private fun string(): String {
            i++
            val sb = StringBuilder()
            while (true) {
                when (val c = next()) {
                    '"' -> return sb.toString()
                    '\\' -> when (val e = next()) {
                        '"', '\\', '/' -> sb.append(e)
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'u' -> {
                            if (i + 4 > s.length) fail("Bad unicode escape")
                            sb.append(s.substring(i, i + 4).toIntOrNull(16)?.toChar() ?: fail("Bad unicode escape"))
                            i += 4
                        }
                        else -> fail("Bad escape '\\$e'")
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun number(): JsonValue.Num {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            return JsonValue.Num(s.substring(start, i).toDoubleOrNull() ?: fail("Bad number"))
        }

        private fun literal(word: String, v: JsonValue): JsonValue {
            if (!s.startsWith(word, i)) fail("Expected $word")
            i += word.length
            return v
        }

        private fun skipWs() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        private fun peek(): Char? = s.getOrNull(i)

        private fun next(): Char = if (i < s.length) s[i++] else fail("Unexpected end")

        private fun expect(c: Char) {
            if (next() != c) fail("Expected '$c'")
        }

        private fun fail(msg: String): Nothing = throw JsonException("$msg at $i")
    }
}
