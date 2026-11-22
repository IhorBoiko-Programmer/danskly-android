package app.danskly.core.text

/** A piece of text that is either plain or highlighted (marked with *asterisks* in the content). */
data class TextSegment(val text: String, val highlighted: Boolean)

object TextTools {

    private val punctuation = Regex("[.,!?;:«»\"“”]")
    private val spaces = Regex("\\s+")
    private val speakerPrefix = Regex("^\\p{Lu}[\\p{L}]+:\\s*", RegexOption.MULTILINE)
    private val sentenceBreak = Regex("([.!?])\\s+(?=[A-ZÆØÅ«\"„])")
    private val arrows = Regex("\\s*[→–]\\s*")
    private val slashes = Regex("\\s*/\\s*")

    /** Lower-cases and removes punctuation and extra spaces, for comparing typed answers. */
    fun normalize(text: String): String =
        text.lowercase().replace(punctuation, "").replace(spaces, " ").trim()

    /** Edit distance between two strings (insertions, deletions and substitutions all cost 1). */
    fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            prev = cur
        }
        return prev[b.length]
    }

    /** Prepares content text for speech: drops highlight marks and reads "→", "–" and "/" as pauses. */
    fun forSpeech(text: String): String =
        text.replace("*", "").replace(arrows, ", ").replace(slashes, ", ").trim()

    /** Removes "Name:" at the start of dialogue lines so they are not read aloud. */
    fun withoutSpeakerNames(text: String): String = text.replace(speakerPrefix, "")

    /**
     * Splits text into sentences for speech. Splits only when the next sentence starts with a
     * capital letter, so abbreviations such as "kl. 14.30" stay in one piece.
     */
    fun sentences(text: String): List<String> =
        text.replace(sentenceBreak, "$1\n").split('\n').map { it.trim() }.filter { it.isNotEmpty() }

    /** Splits "Jeg *bor* her" into plain and highlighted segments. */
    fun highlights(text: String): List<TextSegment> {
        val parts = text.split('*')
        return parts.mapIndexedNotNull { i, part ->
            if (part.isEmpty()) null else TextSegment(part, highlighted = i % 2 == 1)
        }
    }
}
