package app.danskly.core.progress

import app.danskly.core.json.Json
import app.danskly.core.json.JsonException
import app.danskly.core.json.JsonValue
import app.danskly.core.json.bool
import app.danskly.core.json.child
import app.danskly.core.json.double
import app.danskly.core.json.int
import app.danskly.core.json.json
import app.danskly.core.json.list
import app.danskly.core.json.long
import app.danskly.core.json.obj
import app.danskly.core.json.str
import app.danskly.core.json.strOrNull
import app.danskly.core.json.strings
import app.danskly.core.model.Article
import app.danskly.core.model.CardState
import app.danskly.core.model.DailyPlan
import app.danskly.core.model.DailyTask
import app.danskly.core.model.DayLog
import app.danskly.core.model.Example
import app.danskly.core.model.LabeledExample
import app.danskly.core.model.Progress
import app.danskly.core.model.Settings
import app.danskly.core.model.Word
import app.danskly.core.model.WordDetails

/** Converts [Progress] to and from JSON. Unknown or missing fields fall back to defaults. */
object ProgressCodec {

    private const val VERSION = 1

    fun encode(p: Progress): String = Json.write(
        json(
            "version" to VERSION,
            "cards" to p.cards.mapValues { (_, c) -> json("box" to c.box, "due" to c.dueAt) },
            "streak" to p.streak,
            "lastActive" to p.lastActiveDate,
            "day" to p.day?.let { json("date" to it.date, "actions" to it.actions, "done" to it.done.map { t -> t.name }) },
            "plan" to p.plan?.let { json("date" to it.date, "words" to it.newWords) },
            "quizCorrect" to p.quizCorrect,
            "quizTotal" to p.quizTotal,
            "reading" to p.readingScores.mapKeys { it.key.toString() },
            "listening" to p.listeningScores.mapKeys { it.key.toString() },
            "custom" to p.customWords.map { json("da" to it.danish, "uk" to it.ukrainian, "topic" to it.topic, "article" to it.article?.text) },
            "details" to p.wordDetails.mapValues { (_, d) -> encodeDetails(d) },
            "examples" to p.exampleSentences.mapValues { (_, e) -> json("da" to e.danish, "uk" to e.ukrainian) },
            "settings" to p.settings.let {
                json(
                    "voice" to it.voiceName,
                    "rate" to it.speechRate,
                    "volume" to it.volume,
                    "auto" to it.autoSpeak,
                    "code" to it.accessCode,
                    "server" to it.serverUrl,
                )
            },
        ),
    )

    /** Reads saved progress. Returns fresh progress if the text is missing or damaged. */
    fun decode(text: String?): Progress {
        if (text.isNullOrBlank()) return Progress()
        return try {
            decodeObject(Json.parse(text).obj())
        } catch (e: JsonException) {
            Progress()
        }
    }

    private fun decodeObject(o: JsonValue.Obj): Progress {
        return Progress(
            cards = o.child("cards")?.fields.orEmpty().mapValues { (_, v) -> v.obj().let { CardState(it.int("box"), it.long("due")) } },
            streak = o.int("streak"),
            lastActiveDate = o.strOrNull("lastActive"),
            day = o.child("day")?.let { d ->
                DayLog(d.str("date"), d.int("actions"), d.strings("done").mapNotNull { name -> DailyTask.entries.firstOrNull { it.name == name } }.toSet())
            },
            plan = o.child("plan")?.let { DailyPlan(it.str("date"), it.strings("words")) },
            quizCorrect = o.int("quizCorrect"),
            quizTotal = o.int("quizTotal"),
            readingScores = scores(o.child("reading")),
            listeningScores = scores(o.child("listening")),
            customWords = o.list("custom").map { it.obj() }.map {
                Word(it.str("da"), it.str("uk"), it.str("topic"), Article.fromText(it.strOrNull("article")), isCustom = true)
            },
            wordDetails = o.child("details")?.fields.orEmpty().mapValues { (_, v) -> decodeDetails(v.obj()) },
            exampleSentences = o.child("examples")?.fields.orEmpty().mapValues { (_, v) -> v.obj().let { Example(it.str("da"), it.str("uk")) } },
            settings = o.child("settings")?.let { s ->
                Settings(
                    voiceName = s.strOrNull("voice"),
                    speechRate = s.double("rate", Settings().speechRate.toDouble()).toFloat(),
                    volume = s.double("volume", 1.0).toFloat(),
                    autoSpeak = s.bool("auto"),
                    accessCode = s.strOrNull("code").orEmpty(),
                    serverUrl = s.strOrNull("server").orEmpty(),
                )
            } ?: Settings(),
        )
    }

    private fun scores(o: JsonValue.Obj?): Map<Int, Int> = o?.fields.orEmpty().mapNotNull { (k, v) ->
        val index = k.toIntOrNull() ?: return@mapNotNull null
        index to ((v as? JsonValue.Num)?.value?.toInt() ?: 0)
    }.toMap()

    fun encodeDetails(d: WordDetails): JsonValue.Obj = json(
        "pos" to d.partOfSpeech,
        "usage" to d.usage,
        "forms" to d.forms.map { listOf(it.first, it.second) },
        "examples" to d.examples.map { json("label" to it.label, "da" to it.danish, "uk" to it.ukrainian) },
        "phrases" to d.phrases.map { json("da" to it.danish, "uk" to it.ukrainian) },
    )

    fun decodeDetails(o: JsonValue.Obj): WordDetails = WordDetails(
        partOfSpeech = o.strOrNull("pos").orEmpty(),
        usage = o.strOrNull("usage").orEmpty(),
        forms = o.list("forms").mapNotNull { f ->
            val pair = (f as? JsonValue.Arr)?.items?.mapNotNull { (it as? JsonValue.Str)?.value }
            if (pair != null && pair.size == 2 && pair[1].isNotBlank()) pair[0] to pair[1] else null
        },
        examples = o.list("examples").mapNotNull { e ->
            val x = e as? JsonValue.Obj ?: return@mapNotNull null
            val da = x.strOrNull("da")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val uk = x.strOrNull("uk")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            LabeledExample(x.strOrNull("label").orEmpty(), da, uk)
        },
        phrases = o.list("phrases").mapNotNull { e ->
            val x = e as? JsonValue.Obj ?: return@mapNotNull null
            val da = x.strOrNull("da")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val uk = x.strOrNull("uk")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Example(da, uk)
        },
    )
}

/** Where progress is kept between app launches. */
interface ProgressStore {
    fun load(): String?
    fun save(text: String)
}

/** Keeps progress in memory only. Used by tests and previews. */
class InMemoryProgressStore(private var text: String? = null) : ProgressStore {
    override fun load(): String? = text
    override fun save(text: String) {
        this.text = text
    }
}
