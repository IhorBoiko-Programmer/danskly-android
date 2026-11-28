package app.danskly.core.model

/** Grammatical gender of a Danish noun: common ("en") or neuter ("et"). */
enum class Article(val text: String) {
    EN("en"),
    ET("et");

    companion object {
        fun fromText(text: String?): Article? = entries.firstOrNull { it.text == text }
    }
}

/** A vocabulary item. `danish` is stored without the article; verbs keep their "at". */
data class Word(
    val danish: String,
    val ukrainian: String,
    val topic: String,
    val article: Article? = null,
    val isCustom: Boolean = false,
) {
    /** How the word is shown and spoken, e.g. "en bog" or "at gå". */
    val display: String get() = article?.let { "${it.text} $danish" } ?: danish

    val isVerb: Boolean get() = danish.startsWith("at ")

    /** The part after "at" for verbs, e.g. "glæde sig" for "at glæde sig". */
    val verbBase: String get() = danish.removePrefix("at ")
}

/** A word topic. Topics with the same [group] are shown together; an empty group means "show on top". */
data class Topic(val name: String, val icon: String, val group: String = "")

/** A heading on the words screen with its topics, e.g. "Дієслова" → Щоденні дії, Мова й думки… */
data class TopicGroup(val name: String, val topics: List<Topic>)

/** A Danish sentence with its translation. `danish` may mark highlighted parts with *asterisks*. */
data class Example(val danish: String, val ukrainian: String)

data class GrammarRule(val title: String, val body: String, val examples: List<Example>)

data class GrammarSection(val title: String, val icon: String, val rules: List<GrammarRule>)

data class WritingTask(val title: String, val task: String, val sample: String, val tips: String)

/** A comprehension question; the first option is always the correct one. */
data class ReadingQuestion(val question: String, val hint: String, val options: List<String>)

data class ReadingText(
    val title: String,
    val kind: String,
    val icon: String,
    val danish: String,
    val ukrainian: String,
    val questions: List<ReadingQuestion>,
)

/** Verified forms of a verb. `auxiliary` is "har", "er" or "har/er". */
data class VerbEntry(
    val infinitive: String,
    val present: String,
    val past: String,
    val participle: String,
    val auxiliary: String,
)

data class NounEntry(val singular: String, val definite: String, val plural: String, val pluralDefinite: String)

data class AdjectiveEntry(
    val base: String,
    val neuter: String,
    val plural: String,
    val comparative: String,
    val superlative: String,
)

data class Letter(val letter: String, val word: String, val ukrainian: String)

data class SoundTopic(val title: String, val body: String, val examples: List<Example>)

/** Everything the app teaches. Loaded once from the bundled JSON files. */
data class Content(
    val topics: List<Topic>,
    val words: List<Word>,
    val grammar: List<GrammarSection>,
    val writingTasks: List<WritingTask>,
    val writingPhrases: List<String>,
    val reading: List<ReadingText>,
    val verbs: Map<String, VerbEntry>,
    val nouns: Map<String, NounEntry>,
    val adjectives: Map<String, AdjectiveEntry>,
    val alphabet: List<Letter>,
    val sounds: List<SoundTopic>,
) {
    val ruleCount: Int get() = grammar.sumOf { it.rules.size }
}
