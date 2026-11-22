package app.danskly.core.ai

import app.danskly.core.json.Json
import app.danskly.core.json.JsonException
import app.danskly.core.json.JsonValue
import app.danskly.core.json.arr
import app.danskly.core.json.child
import app.danskly.core.json.obj
import app.danskly.core.json.strOrNull

sealed interface SseEvent {
    data class Delta(val text: String) : SseEvent
    data object Timeout : SseEvent
}

/**
 * Parses an OpenAI-style "text/event-stream" (lines like `data: {...}`) arriving in arbitrary chunks.
 */
class SseParser {
    private val buffer = StringBuilder()

    fun feed(chunk: String): List<SseEvent> {
        buffer.append(chunk)
        val events = mutableListOf<SseEvent>()
        while (true) {
            val nl = buffer.indexOf("\n")
            if (nl < 0) break
            val line = buffer.substring(0, nl).trim()
            buffer.delete(0, nl + 1)
            parseLine(line)?.let(events::add)
        }
        return events
    }

    private fun parseLine(line: String): SseEvent? {
        if (!line.startsWith("data:")) return null
        val data = line.removePrefix("data:").trim()
        if (data.isEmpty() || data == "[DONE]") return null
        val obj = try {
            Json.parse(data).obj()
        } catch (e: JsonException) {
            return null
        }
        if (obj.fields.containsKey("error")) return SseEvent.Timeout
        val delta = try {
            obj.fields["choices"]?.arr()?.firstOrNull()?.obj()?.child("delta")?.strOrNull("content")
        } catch (e: JsonException) {
            null
        }
        return delta?.takeIf { it.isNotEmpty() }?.let { SseEvent.Delta(it) }
    }
}

/** Helpers for cleaning up model output. */
object AiText {
    private val think = Regex("<think>[\\s\\S]*?(</think>|$)")
    private val fences = Regex("```(?:json)?", RegexOption.IGNORE_CASE)

    /** Removes hidden reasoning blocks some models emit. */
    fun stripThinking(text: String): String = text.replace(think, "").trimStart()

    /** Finds and parses the JSON in a reply, tolerating code fences and surrounding prose. */
    fun parseJsonLoose(text: String): JsonValue {
        val cleaned = stripThinking(text).replace(fences, "").trim()
        try {
            return Json.parse(cleaned)
        } catch (e: JsonException) {
            // fall through to extraction
        }
        val start = cleaned.indexOfFirst { it == '{' || it == '[' }
        if (start < 0) throw AiException(AiErrorKind.BAD_RESPONSE)
        val end = cleaned.lastIndexOf(if (cleaned[start] == '{') '}' else ']')
        if (end <= start) throw AiException(AiErrorKind.BAD_RESPONSE)
        return try {
            Json.parse(cleaned.substring(start, end + 1))
        } catch (e: JsonException) {
            throw AiException(AiErrorKind.BAD_RESPONSE, e)
        }
    }
}
