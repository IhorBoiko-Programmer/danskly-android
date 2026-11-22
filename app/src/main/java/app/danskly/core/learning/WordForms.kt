package app.danskly.core.learning

import app.danskly.core.model.Content
import app.danskly.core.model.Example
import app.danskly.core.model.VerbEntry
import app.danskly.core.model.Word

/** One line of a word's form table, e.g. "Минулий" → "gik". */
data class FormRow(val label: String, val form: String, val hint: String? = null)

enum class FormsKind { VERB, NOUN, ADJECTIVE }

/** Verified forms of a word plus the spellings used to find it in example sentences. */
data class WordForms(val kind: FormsKind, val rows: List<FormRow>, val spellings: List<String>)

/** A grammar example that contains a given word. */
data class FoundExample(val example: Example, val section: String)

/** Builds form tables (all tenses, plural, comparison) from the verified tables in [Content]. */
class WordFormsBuilder(private val content: Content) {

    fun formsOf(word: Word): WordForms? {
        if (word.isVerb) {
            val parts = word.verbBase.split(" ")
            val rest = parts.drop(1).joinToString(" ")
            val entry = content.verbs[parts.first()]
            if (entry != null && (rest.isEmpty() || rest == "sig")) {
                return WordForms(
                    kind = FormsKind.VERB,
                    rows = conjugate(entry, rest),
                    spellings = listOf(entry.infinitive, entry.present, entry.past, entry.participle, imperative(entry.infinitive)),
                )
            }
            return null
        }
        content.nouns[word.danish]?.takeIf { word.article != null }?.let { n ->
            return WordForms(
                kind = FormsKind.NOUN,
                rows = listOf(
                    FormRow(SINGULAR, word.display),
                    FormRow(SINGULAR_DEFINITE, n.definite),
                    FormRow(PLURAL, n.plural),
                    FormRow(PLURAL_DEFINITE, n.pluralDefinite),
                ),
                spellings = listOf(n.singular, n.definite, n.plural, n.pluralDefinite),
            )
        }
        content.adjectives[word.danish]?.let { a ->
            return WordForms(
                kind = FormsKind.ADJECTIVE,
                rows = listOf(
                    FormRow("З en-словом", "en ${a.base} …"),
                    FormRow("З et-словом", "et ${a.neuter} …"),
                    FormRow("Множина й означена форма", a.plural),
                    FormRow("Вищий ступінь", a.comparative),
                    FormRow("Найвищий ступінь", a.superlative),
                ),
                spellings = listOf(a.base, a.neuter, a.plural, a.comparative, a.superlative),
            )
        }
        return null
    }

    /** All tenses of a verb. `rest` is "" or "sig" for reflexive verbs such as "glæde sig". */
    fun conjugate(v: VerbEntry, rest: String = ""): List<FormRow> {
        val tail = if (rest.isEmpty()) "" else " $rest"
        val noImperative = v.infinitive in MODALS || (v.infinitive.endsWith("s") && v.present == v.infinitive)
        val perfectAux = auxiliary(v.auxiliary, "har", "er")
        val pluperfectAux = auxiliary(v.auxiliary, "havde", "var")
        val rows = mutableListOf(
            row(INFINITIVE, "at ${v.infinitive}$tail"),
            row(PRESENT, "${v.present}$tail"),
            row(PAST, "${v.past}$tail"),
            row(PERFECT, "$perfectAux ${v.participle}$tail"),
        )
        if (noImperative) {
            rows += row(PLUPERFECT, "havde ${v.participle}$tail")
        } else {
            val imperativeTail = if (rest == "sig") " dig" else tail
            rows += row(PLUPERFECT, "$pluperfectAux ${v.participle}$tail")
            rows += row(FUTURE, "vil/skal ${v.infinitive}$tail")
            rows += row(CONDITIONAL, "ville ${v.infinitive}$tail")
            rows += row(IMPERATIVE, "${imperative(v.infinitive)}$imperativeTail!")
        }
        return rows
    }

    /** "spise" → "spis", "komme" → "kom", "gå" → "gå". */
    fun imperative(infinitive: String): String {
        val vowels = infinitive.count { it in VOWELS }
        if (vowels < 2 || !infinitive.endsWith("e")) return infinitive
        val stem = infinitive.dropLast(1)
        val doubled = stem.length >= 2 && stem.last() == stem[stem.length - 2] && stem.last() !in VOWELS
        return if (doubled) stem.dropLast(1) else stem
    }

    /** Grammar examples (full sentences) that contain any of the given spellings. */
    fun examplesWith(spellings: List<String>, limit: Int = 5): List<FoundExample> {
        val forms = spellings.filter { it.length > 1 }.map { it.lowercase() }.distinct()
        if (forms.isEmpty()) return emptyList()
        val pattern = Regex("(^|[^\\p{L}])(" + forms.joinToString("|") { Regex.escape(it) } + ")(?![\\p{L}])", RegexOption.IGNORE_CASE)
        return content.grammar.asSequence()
            .flatMap { s -> s.rules.asSequence().flatMap { r -> r.examples.asSequence().map { FoundExample(it.copy(danish = it.danish.replace("*", "")), s.title) } } }
            .filter { it.example.danish.trim().contains(' ') && pattern.containsMatchIn(it.example.danish) }
            .take(limit)
            .toList()
    }

    private fun row(label: String, form: String) = FormRow(label, form, TENSE_HINTS[label])

    private fun auxiliary(aux: String, har: String, er: String) = when (aux) {
        "har" -> har
        "er" -> er
        else -> "$har/$er"
    }

    companion object {
        const val INFINITIVE = "Інфінітив"
        const val PRESENT = "Теперішній"
        const val PAST = "Минулий"
        const val PERFECT = "Перфект"
        const val PLUPERFECT = "Плюсквамперфект"
        const val FUTURE = "Майбутній"
        const val CONDITIONAL = "Умовний"
        const val IMPERATIVE = "Наказовий"
        const val SINGULAR = "Однина"
        const val SINGULAR_DEFINITE = "Однина, означена"
        const val PLURAL = "Множина"
        const val PLURAL_DEFINITE = "Множина, означена"

        private const val VOWELS = "aeiouyæøå"
        val MODALS = setOf("kunne", "skulle", "ville", "måtte", "burde")

        val TENSE_HINTS = mapOf(
            INFINITIVE to "Словникова форма. Після модальних дієслів без at.",
            PRESENT to "Зараз, регулярно або найближчим часом.",
            PAST to "Завершена дія з точним часом (i går, sidste år).",
            PERFECT to "Минуле, важливе зараз, без точного часу.",
            PLUPERFECT to "Дія, що сталася до іншої дії в минулому.",
            FUTURE to "Плани й наміри (vil, skal).",
            CONDITIONAL to "Ввічливо або нереально: «я б…».",
            IMPERATIVE to "Прохання чи команда.",
        )
    }
}
