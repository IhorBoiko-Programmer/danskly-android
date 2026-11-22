package app.danskly.core.exercise

import app.danskly.core.learning.WordFormsBuilder
import app.danskly.core.model.Content
import app.danskly.core.model.Word
import app.danskly.core.text.TextTools
import kotlin.random.Random

/**
 * Creates exercise questions from the grammar examples, the verified form tables and the words.
 * Every generated question is guaranteed to be solvable: the right answer is among the options.
 */
class ExerciseGenerator(
    private val content: Content,
    private val words: () -> List<Word>,
    private val isStudied: (Word) -> Boolean,
    private val random: Random = Random.Default,
    private val speechAvailable: Boolean = true,
) {
    private data class GapItem(val sentence: String, val answer: String, val translation: String, val section: String, val full: String)
    private data class Sentence(val danish: String, val ukrainian: String)

    private val forms = WordFormsBuilder(content)
    private val structural = Regex("[→–=/()]")
    private val marked = Regex("(^|[^\\p{L}])\\*([^*]+)\\*(?!\\p{L})")
    private val sentenceEnd = Regex("[.!?]$")

    private val gaps: List<GapItem>
    private val sentences: List<Sentence>

    init {
        val g = mutableListOf<GapItem>()
        val s = mutableListOf<Sentence>()
        for (section in content.grammar) for (rule in section.rules) for (ex in rule.examples) {
            if (structural.containsMatchIn(ex.danish)) continue
            val plain = ex.danish.replace("*", "").trim()
            val wordCount = plain.split(Regex("\\s+")).size
            if (wordCount in 3..10 && sentenceEnd.containsMatchIn(plain)) s += Sentence(plain, ex.ukrainian)
            val m = marked.find(ex.danish) ?: continue
            val answer = m.groupValues[2]
            if (wordCount < 3 || answer.split(" ").size > 3) continue
            val start = m.range.first + m.groupValues[1].length
            val withGap = ex.danish.substring(0, start) + GAP + ex.danish.substring(start + answer.length + 2)
            g += GapItem(withGap.replace("*", ""), answer, ex.ukrainian, section.title, plain)
        }
        gaps = g
        sentences = s
    }

    val gapCount: Int get() = gaps.size
    val sentenceCount: Int get() = sentences.size

    fun availableTypes(): List<ExerciseType> = ExerciseType.entries.filter { !it.needsSpeech || speechAvailable }

    fun next(type: ExerciseType): Question = when (type) {
        ExerciseType.MIX -> next(availableTypes().filter { it != ExerciseType.MIX }.random(random))
        ExerciseType.GAP -> gap()
        ExerciseType.BUILD -> build()
        ExerciseType.WRITE -> write()
        ExerciseType.VERB -> verb()
        ExerciseType.NOUN -> noun()
        ExerciseType.DICTATION -> dictation()
    }

    private fun gap(): ChoiceQuestion {
        val item = gaps.random(random)
        val lower = item.answer.lowercase()
        val sameSection = gaps.filter { it.section == item.section }.shuffled(random).map { it.answer }
        val others = gaps.shuffled(random).map { it.answer }
        val distractors = (sameSection + others).distinctBy { it.lowercase() }.filter { it.lowercase() != lower }.take(3)
        return ChoiceQuestion(
            tag = ExerciseType.GAP.title,
            prompt = item.sentence,
            hint = item.translation,
            options = (distractors + item.answer).shuffled(random),
            answer = item.answer,
            speak = item.full,
            explanation = item.translation,
        )
    }

    private fun build(): TilesQuestion {
        val item = sentences.random(random)
        val ending = item.danish.last().toString()
        val tokens = item.danish.dropLast(1).split(Regex("\\s+"))
        val used = tokens.map(TextTools::normalize).toSet()
        val extra = sentences.filter { it != item }.shuffled(random)
            .flatMap { TextTools.normalize(it.danish).split(" ") }
            .distinct().filter { it.length > 1 && it !in used }.take(2)
        return TilesQuestion(
            tag = ExerciseType.BUILD.title,
            prompt = item.ukrainian,
            tiles = (tokens + extra).shuffled(random),
            answer = tokens,
            ending = ending,
            speak = item.danish,
        )
    }

    private fun write(): TypingQuestion {
        val simple = words().filter { it.danish.split(" ").size <= 3 && !it.danish.contains('?') && !it.danish.contains('!') }
        val studied = simple.filter(isStudied)
        val word = (if (studied.size >= MIN_STUDIED) studied else simple).random(random)
        val hint = when {
            word.article != null -> "Іменник: напиши разом з en або et"
            word.isVerb -> "Дієслово: напиши з at"
            else -> null
        }
        return TypingQuestion(
            tag = ExerciseType.WRITE.title,
            prompt = word.ukrainian,
            hint = hint,
            accepted = listOf(word.display),
            alternatives = listOf(word.verbBase),
            speak = word.display,
            word = word,
        )
    }

    private fun verb(): ChoiceQuestion {
        val candidates = content.verbs.values.filter { v ->
            v.infinitive !in WordFormsBuilder.MODALS &&
                !(v.infinitive.endsWith("s") && v.present == v.infinitive) &&
                words().any { it.danish == "at ${v.infinitive}" }
        }
        val v = candidates.random(random)
        val inf = v.infinitive
        val endsE = inf.endsWith("e")
        val stem = if (endsE) inf.dropLast(1) else inf
        val fakePast1 = (if (endsE) inf else inf + "e") + "de"
        val fakePast2 = stem + "te"
        val asks = mutableListOf(
            Triple("теперішній час", v.present, listOf(inf, v.past, stem + "er", inf + "r")),
            Triple("минулий час", v.past, listOf(fakePast1, fakePast2, v.present, v.participle)),
        )
        if (v.auxiliary != "har/er") {
            val other = if (v.auxiliary == "har") "er" else "har"
            asks += Triple("перфект", "${v.auxiliary} ${v.participle}", listOf("$other ${v.participle}", "${v.auxiliary} ${v.past}", "${v.auxiliary} ${stem}et"))
        }
        val (name, answer, wrong) = asks.random(random)
        val options = (wrong.distinct().filter { it != answer }.take(3) + answer).shuffled(random)
        return ChoiceQuestion(
            tag = ExerciseType.VERB.title,
            prompt = "at $inf → $name",
            hint = words().firstOrNull { it.danish == "at $inf" }?.ukrainian,
            options = options,
            answer = answer,
            speak = answer,
            explanation = "at $inf – ${v.present} – ${v.past} – ${v.auxiliary} ${v.participle}",
        )
    }

    private fun noun(): ChoiceQuestion {
        val nouns = words().filter { it.article != null }
        val withPlural = nouns.filter { content.nouns.containsKey(it.danish) }
        if (withPlural.isEmpty() || random.nextBoolean()) {
            val w = nouns.random(random)
            return ChoiceQuestion(
                tag = "Рід іменника",
                prompt = w.danish,
                hint = w.ukrainian,
                options = listOf("en", "et"),
                answer = w.article!!.text,
                speak = w.display,
                explanation = w.display,
            )
        }
        val w = withPlural.random(random)
        val n = content.nouns.getValue(w.danish)
        val fake = listOf(w.danish + "er", if (w.danish.endsWith("e")) w.danish + "r" else w.danish + "e", n.definite, n.pluralDefinite)
            .distinct().filter { it != n.plural }.shuffled(random).take(3)
        return ChoiceQuestion(
            tag = "Множина",
            prompt = "${w.display} → множина",
            hint = w.ukrainian,
            options = (fake + n.plural).shuffled(random),
            answer = n.plural,
            speak = n.plural,
            explanation = "${w.display} – ${n.definite} – ${n.plural} – ${n.pluralDefinite}",
        )
    }

    private fun dictation(): TypingQuestion {
        val short = sentences.filter { it.danish.split(" ").size <= 6 }
        if (short.isNotEmpty() && random.nextInt(10) < 4) {
            val s = short.random(random)
            return TypingQuestion(
                tag = ExerciseType.DICTATION.title,
                prompt = "Послухай і запиши речення",
                hint = null,
                accepted = listOf(s.danish),
                speak = s.danish,
                audio = s.danish,
                explanation = s.ukrainian,
            )
        }
        val w = words().filter { it.danish.split(" ").size <= 2 && !it.danish.contains('?') && !it.danish.contains('!') }.random(random)
        return TypingQuestion(
            tag = ExerciseType.DICTATION.title,
            prompt = "Послухай і запиши слово",
            hint = null,
            accepted = listOf(w.display),
            alternatives = listOf(w.verbBase),
            speak = w.display,
            audio = w.display,
            word = w,
            explanation = w.ukrainian,
        )
    }

    companion object {
        const val GAP = "_____"
        private const val MIN_STUDIED = 8
    }
}
