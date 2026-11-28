package app.danskly.core.content

import app.danskly.core.json.Json
import app.danskly.core.json.JsonValue
import app.danskly.core.json.arr
import app.danskly.core.json.list
import app.danskly.core.json.obj
import app.danskly.core.json.str
import app.danskly.core.json.strOrNull
import app.danskly.core.json.strings
import app.danskly.core.model.AdjectiveEntry
import app.danskly.core.model.Article
import app.danskly.core.model.Content
import app.danskly.core.model.Example
import app.danskly.core.model.GrammarRule
import app.danskly.core.model.GrammarSection
import app.danskly.core.model.Letter
import app.danskly.core.model.NounEntry
import app.danskly.core.model.ReadingQuestion
import app.danskly.core.model.ReadingText
import app.danskly.core.model.SoundTopic
import app.danskly.core.model.Topic
import app.danskly.core.model.VerbEntry
import app.danskly.core.model.Word
import app.danskly.core.model.WritingTask

/** Names of the bundled content files (in `assets/content/`). */
object ContentFiles {
    const val WORDS = "words.json"
    const val GRAMMAR = "grammar.json"
    const val WRITING = "writing.json"
    const val READING = "reading.json"
    const val FORMS = "forms.json"
    const val PRONUNCIATION = "pronunciation.json"
    val ALL = listOf(WORDS, GRAMMAR, WRITING, READING, FORMS, PRONUNCIATION)
}

/** Turns the bundled JSON files into a [Content] model. `read` returns a file's text by name. */
object ContentParser {

    fun parse(read: (String) -> String): Content {
        val words = Json.parse(read(ContentFiles.WORDS)).obj()
        val writing = Json.parse(read(ContentFiles.WRITING)).obj()
        val forms = Json.parse(read(ContentFiles.FORMS)).obj()
        val pronunciation = Json.parse(read(ContentFiles.PRONUNCIATION)).obj()
        return Content(
            topics = words.list("topics").map { it.obj().let { o -> Topic(o.str("name"), o.str("icon"), o.strOrNull("group").orEmpty()) } },
            words = words.list("words").map { it.obj().toWord() },
            grammar = Json.parse(read(ContentFiles.GRAMMAR)).arr().map { it.obj().toSection() },
            writingTasks = writing.list("tasks").map {
                it.obj().let { o -> WritingTask(o.str("title"), o.str("task"), o.str("sample"), o.str("tips")) }
            },
            writingPhrases = writing.strings("phrases"),
            reading = Json.parse(read(ContentFiles.READING)).arr().map { it.obj().toReading() },
            verbs = forms.list("verbs").map { it.obj().toVerb() }.associateBy { it.infinitive },
            nouns = forms.list("nouns").map { it.obj().toNoun() }.associateBy { it.singular },
            adjectives = forms.list("adjectives").map { it.obj().toAdjective() }.associateBy { it.base },
            alphabet = pronunciation.list("alphabet").map {
                it.obj().let { o -> Letter(o.str("letter"), o.str("word"), o.str("uk")) }
            },
            sounds = pronunciation.list("sounds").map {
                it.obj().let { o -> SoundTopic(o.str("title"), o.str("body"), o.examples()) }
            },
        )
    }

    private fun JsonValue.Obj.toWord() =
        Word(danish = str("da"), ukrainian = str("uk"), topic = str("topic"), article = Article.fromText(strOrNull("article")))

    private fun JsonValue.Obj.examples() = list("examples").map { it.obj().let { o -> Example(o.str("da"), o.str("uk")) } }

    private fun JsonValue.Obj.toSection() = GrammarSection(
        title = str("title"),
        icon = str("icon"),
        rules = list("rules").map { it.obj().let { r -> GrammarRule(r.str("title"), r.str("body"), r.examples()) } },
    )

    private fun JsonValue.Obj.toReading() = ReadingText(
        title = str("title"),
        kind = str("kind"),
        icon = str("icon"),
        danish = str("da"),
        ukrainian = str("uk"),
        questions = list("questions").map {
            it.obj().let { q -> ReadingQuestion(q.str("question"), q.str("hint"), q.strings("options")) }
        },
    )

    private fun JsonValue.Obj.toVerb() =
        VerbEntry(str("infinitive"), str("present"), str("past"), str("participle"), str("auxiliary"))

    private fun JsonValue.Obj.toNoun() = NounEntry(str("singular"), str("definite"), str("plural"), str("pluralDefinite"))

    private fun JsonValue.Obj.toAdjective() =
        AdjectiveEntry(str("base"), str("neuter"), str("plural"), str("comparative"), str("superlative"))
}
