package app.danskly.core.learning

import app.danskly.core.model.Article
import app.danskly.core.model.Word

/** What kind of word the learner is adding. */
enum class WordKind { NOUN_EN, NOUN_ET, VERB, OTHER }

sealed interface NewWordResult {
    data class Ok(val word: Word) : NewWordResult
    data object MissingFields : NewWordResult
}

/** Builds a clean [Word] from what the learner typed in the "Add word" form. */
object CustomWordFactory {

    private val articlePrefix = Regex("^(en|et) ", RegexOption.IGNORE_CASE)
    private val verbPrefix = Regex("^at ", RegexOption.IGNORE_CASE)
    private val spaces = Regex("\\s+")

    /** Guesses the kind from the typed text: "en bog" → noun (en), "at gå" → verb. */
    fun detectKind(typed: String): WordKind? {
        val t = typed.trim().lowercase()
        return when {
            t.startsWith("en ") -> WordKind.NOUN_EN
            t.startsWith("et ") -> WordKind.NOUN_ET
            t.startsWith("at ") -> WordKind.VERB
            else -> null
        }
    }

    fun create(danish: String, ukrainian: String, kind: WordKind, topic: String): NewWordResult {
        var da = danish.trim().replace(spaces, " ")
        val uk = ukrainian.trim()
        da = da.replace(articlePrefix, "")
        when (kind) {
            WordKind.VERB -> if (!verbPrefix.containsMatchIn(da)) da = "at $da"
            WordKind.NOUN_EN, WordKind.NOUN_ET -> da = da.replace(verbPrefix, "")
            WordKind.OTHER -> Unit
        }
        if (da.replace(verbPrefix, "").isBlank() || uk.isBlank()) return NewWordResult.MissingFields
        val article = when (kind) {
            WordKind.NOUN_EN -> Article.EN
            WordKind.NOUN_ET -> Article.ET
            else -> null
        }
        return NewWordResult.Ok(Word(da, uk, topic, article, isCustom = true))
    }
}
