package app.danskly.core.learning

import app.danskly.core.model.Content
import app.danskly.core.model.DailyPlan
import app.danskly.core.model.DailyTask
import app.danskly.core.model.DayLog
import app.danskly.core.model.Example
import app.danskly.core.model.Progress
import app.danskly.core.model.Settings
import app.danskly.core.model.Topic
import app.danskly.core.model.Word
import app.danskly.core.model.WordDetails
import app.danskly.core.progress.ProgressCodec
import app.danskly.core.progress.ProgressStore
import app.danskly.core.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface AddWordResult {
    data class Added(val word: Word) : AddWordResult
    data class Duplicate(val existing: Word) : AddWordResult
}

/**
 * The single source of truth for the learner's progress. Every change goes through here,
 * is published to [progress] and saved to the [store] straight away.
 */
class LearningRepository(
    val content: Content,
    private val store: ProgressStore,
    private val clock: Clock,
    private val srs: SpacedRepetition = SpacedRepetition(),
) {
    private val state = MutableStateFlow(ProgressCodec.decode(store.load()))
    val progress: StateFlow<Progress> = state.asStateFlow()

    private val current: Progress get() = state.value

    // ---------- words & topics ----------

    val allWords: List<Word> get() = content.words + current.customWords

    /** Content topics, with "My words" first if the learner added any. */
    val topics: List<Topic>
        get() {
            val extra = current.customWords.map { it.topic }.distinct()
                .filter { name -> content.topics.none { it.name == name } }
                .map { Topic(it, if (it == MY_WORDS) MY_WORDS_ICON else "•") }
            return extra.sortedByDescending { it.name == MY_WORDS } + content.topics
        }

    fun wordsIn(topic: String?): List<Word> = if (topic == null) allWords else allWords.filter { it.topic == topic }

    fun findWord(danish: String): Word? = allWords.firstOrNull { it.danish == danish }

    fun addCustomWord(word: Word): AddWordResult {
        allWords.firstOrNull { it.danish.equals(word.danish, ignoreCase = true) }?.let { return AddWordResult.Duplicate(it) }
        val added = word.copy(isCustom = true)
        update { it.copy(customWords = it.customWords + added) }
        return AddWordResult.Added(added)
    }

    fun deleteCustomWord(word: Word) = update {
        it.copy(
            customWords = it.customWords.filterNot { w -> w.danish == word.danish },
            cards = it.cards - word.danish,
            wordDetails = it.wordDetails - word.danish,
            exampleSentences = it.exampleSentences - word.danish,
        )
    }

    // ---------- learning actions ----------

    /** Records an answer about a word: moves it through the boxes and counts a daily action. */
    fun grade(word: Word, correct: Boolean) {
        update { it.copy(cards = it.cards + (word.danish to srs.grade(it.cards[word.danish], correct, clock.nowMillis()))) }
        recordAction()
    }

    /** Counts one learning action for today's goal and the streak; optionally ticks a daily task. */
    fun recordAction(task: DailyTask? = null) = update { p ->
        val today = clock.today().toString()
        val log = p.todayLog(today)
        p.withStreak(today).copy(day = log.copy(actions = log.actions + 1, done = log.done + listOfNotNull(task)))
    }

    /** Ticks a daily task without counting an extra action. */
    fun markDone(task: DailyTask) = update { p ->
        val today = clock.today().toString()
        val log = p.todayLog(today)
        p.copy(day = log.copy(done = log.done + task))
    }

    fun recordQuizAnswer(correct: Boolean) {
        update { it.copy(quizTotal = it.quizTotal + 1, quizCorrect = it.quizCorrect + if (correct) 1 else 0) }
    }

    fun saveReadingScore(textIndex: Int, score: Int) {
        update { it.copy(readingScores = it.readingScores + (textIndex to score)) }
        recordAction(DailyTask.READING)
    }

    // ---------- today ----------

    fun todayLog(): DayLog = current.todayLog(clock.today().toString())

    fun dueWords(): List<Word> {
        val now = clock.nowMillis()
        return allWords.filter { w -> current.cards[w.danish]?.let { srs.isDue(it, now) } ?: false }
    }

    /** Up to [limit] unseen words for today, chosen in topic order and fixed for the whole day. */
    fun newWordsToday(limit: Int = NEW_WORDS_PER_DAY): List<Word> {
        val today = clock.today().toString()
        val plan = current.plan?.takeIf { it.date == today } ?: run {
            val order = topics.map { it.name }
            val unseen = order.flatMap { t -> allWords.filter { it.topic == t } }.filter { it.danish !in current.cards }
            DailyPlan(today, unseen.take(limit).map { it.danish }).also { p -> update { it.copy(plan = p) } }
        }
        return plan.newWords.mapNotNull { findWord(it) }.filter { it.danish !in current.cards }
    }

    /** Index (section, rule) of today's grammar rule; changes every day. */
    fun ruleOfDay(): Pair<Int, Int> {
        val all = content.grammar.flatMapIndexed { s, section -> section.rules.indices.map { s to it } }
        return all[Math.floorMod(clock.today().toEpochDay(), all.size.toLong()).toInt()]
    }

    fun textOfDay(): Int = Math.floorMod(clock.today().toEpochDay(), content.reading.size.toLong()).toInt()

    // ---------- settings & caches ----------

    fun updateSettings(change: (Settings) -> Settings) = update { it.copy(settings = change(it.settings)) }

    fun cacheDetails(word: Word, details: WordDetails) = update { it.copy(wordDetails = it.wordDetails + (word.danish to details)) }

    fun cacheExample(word: Word, example: Example) = update { it.copy(exampleSentences = it.exampleSentences + (word.danish to example)) }

    fun clearAiCache() = update { it.copy(wordDetails = emptyMap(), exampleSentences = emptyMap()) }

    /** Forgets learning progress but keeps settings, the learner's own words and AI caches. */
    fun resetProgress() = update {
        Progress(customWords = it.customWords, wordDetails = it.wordDetails, exampleSentences = it.exampleSentences, settings = it.settings)
    }

    private fun update(change: (Progress) -> Progress) {
        val next = change(state.value)
        state.value = next
        store.save(ProgressCodec.encode(next))
    }

    private fun Progress.todayLog(today: String): DayLog = day?.takeIf { it.date == today } ?: DayLog(today)

    private fun Progress.withStreak(today: String): Progress {
        if (lastActiveDate == today) return this
        val yesterday = clock.today().minusDays(1).toString()
        return copy(streak = if (lastActiveDate == yesterday) streak + 1 else 1, lastActiveDate = today)
    }

    companion object {
        const val MY_WORDS = "Мої слова"
        const val MY_WORDS_ICON = "⭐"
        const val NEW_WORDS_PER_DAY = 10
        const val DAILY_GOAL = 20
    }
}
