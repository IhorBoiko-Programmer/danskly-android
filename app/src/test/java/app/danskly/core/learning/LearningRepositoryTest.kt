package app.danskly.core.learning

import app.danskly.core.TestContent
import app.danskly.core.model.Article
import app.danskly.core.model.CardState
import app.danskly.core.model.DailyTask
import app.danskly.core.model.Example
import app.danskly.core.model.LabeledExample
import app.danskly.core.model.Word
import app.danskly.core.model.WordDetails
import app.danskly.core.progress.InMemoryProgressStore
import app.danskly.core.progress.ProgressCodec
import app.danskly.core.time.FixedClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningRepositoryTest {

    private val day = FixedClock.DAY_MILLIS
    private val clock = FixedClock(20_000 * day)
    private val store = InMemoryProgressStore()
    private val repo = LearningRepository(TestContent.content, store, clock)
    private val bog get() = repo.findWord("bog")!!

    @Test
    fun gradingMovesWordsThroughBoxesAndSaves() {
        repo.grade(bog, correct = true)
        assertEquals(CardState(1, clock.millis + day), repo.progress.value.cards["bog"])
        repo.grade(bog, correct = true)
        repo.grade(bog, correct = true)
        assertTrue(repo.progress.value.isKnown(bog))
        repo.grade(bog, correct = false)
        assertEquals(0, repo.progress.value.boxOf(bog))
        assertEquals(repo.progress.value, ProgressCodec.decode(store.load()))
    }

    @Test
    fun dueWordsAreThoseWhoseTimeHasCome() {
        repo.grade(bog, true)
        assertTrue(repo.dueWords().isEmpty())
        clock.advanceDays(1)
        assertEquals(listOf(bog), repo.dueWords())
    }

    @Test
    fun streakGrowsOnConsecutiveDaysAndResetsAfterAGap() {
        repo.recordAction()
        repo.recordAction()
        assertEquals(1, repo.progress.value.streak)
        assertEquals(2, repo.todayLog().actions)
        clock.advanceDays(1)
        repo.recordAction()
        assertEquals(2, repo.progress.value.streak)
        assertEquals(1, repo.todayLog().actions)
        clock.advanceDays(3)
        repo.recordAction(DailyTask.RULE)
        assertEquals(1, repo.progress.value.streak)
        assertEquals(setOf(DailyTask.RULE), repo.todayLog().done)
    }

    @Test
    fun markDoneTicksWithoutCounting() {
        repo.markDone(DailyTask.EXERCISE)
        assertEquals(0, repo.todayLog().actions)
        assertEquals(setOf(DailyTask.EXERCISE), repo.todayLog().done)
    }

    @Test
    fun newWordsAreStableForTheDayAndSkipSeenWords() {
        val first = repo.newWordsToday()
        assertEquals(10, first.size)
        assertEquals(TestContent.content.topics.first().name, first.first().topic)
        repo.grade(first.first(), true)
        assertEquals(first.drop(1), repo.newWordsToday())
        clock.advanceDays(1)
        assertNotEquals(first.drop(1), repo.newWordsToday())
    }

    @Test
    fun ruleAndTextOfDayChangeDaily() {
        val rule = repo.ruleOfDay()
        val text = repo.textOfDay()
        clock.advanceDays(1)
        assertNotEquals(rule, repo.ruleOfDay())
        assertNotEquals(text, repo.textOfDay())
        assertTrue(repo.textOfDay() in TestContent.content.reading.indices)
    }

    @Test
    fun customWordsAppearInMyWordsTopicFirst() {
        val word = Word("skraldespand", "смітник", LearningRepository.MY_WORDS, Article.EN)
        val result = repo.addCustomWord(word)
        assertTrue(result is AddWordResult.Added)
        assertEquals(LearningRepository.MY_WORDS, repo.topics.first().name)
        assertEquals("⭐", repo.topics.first().icon)
        assertEquals(listOf(word.copy(isCustom = true)), repo.wordsIn(LearningRepository.MY_WORDS))
        assertEquals(repo.allWords.size, repo.wordsIn(null).size)
        repo.addCustomWord(Word("x-ord", "x", "Власна тема"))
        assertEquals("•", repo.topics.first { it.name == "Власна тема" }.icon)
        repo.addCustomWord(Word("y-ord", "y", "Подорожі"))
        assertEquals(1, repo.topics.count { it.name == "Подорожі" })
    }

    @Test
    fun topicsSplitIntoTopListAndGroups() {
        assertEquals(listOf("Корисні фрази"), repo.topTopics.map { it.name })
        assertEquals("Люди й спілкування", repo.topicGroups.first().name)
        assertEquals(listOf("Щоденні дії", "Мова й думки", "Рух і дозвілля", "Справи й рішення", "Життя й почуття"),
            repo.topicGroups.first { it.name == "Дієслова" }.topics.map { it.name })
        repo.addCustomWord(Word("skraldespand", "смітник", LearningRepository.MY_WORDS, Article.EN))
        assertEquals(listOf(LearningRepository.MY_WORDS, "Корисні фрази"), repo.topTopics.map { it.name })
        assertEquals(repo.topics.size, repo.topTopics.size + repo.topicGroups.sumOf { it.topics.size })
    }

    @Test
    fun duplicateWordsAreRejected() {
        val result = repo.addCustomWord(Word("Bog", "книга", LearningRepository.MY_WORDS))
        assertEquals(AddWordResult.Duplicate(bog), result)
    }

    @Test
    fun deletingACustomWordForgetsEverythingAboutIt() {
        val w = (repo.addCustomWord(Word("ord1", "слово", LearningRepository.MY_WORDS)) as AddWordResult.Added).word
        repo.grade(w, true)
        repo.cacheExample(w, Example("a", "b"))
        repo.cacheDetails(w, details())
        repo.deleteCustomWord(w)
        val p = repo.progress.value
        assertNull(repo.findWord("ord1"))
        assertFalse("ord1" in p.cards || "ord1" in p.wordDetails || "ord1" in p.exampleSentences)
    }

    @Test
    fun quizAndReadingScoresAreKept() {
        repo.recordQuizAnswer(true)
        repo.recordQuizAnswer(false)
        repo.saveReadingScore(2, 3)
        repo.saveReadingScore(1, 2, listening = true)
        val p = repo.progress.value
        assertEquals(mapOf(1 to 2), p.listeningScores)
        assertEquals(1, p.quizCorrect)
        assertEquals(2, p.quizTotal)
        assertEquals(mapOf(2 to 3), p.readingScores)
        assertTrue(DailyTask.READING in repo.todayLog().done)
    }

    @Test
    fun settingsCachesAndReset() {
        repo.updateSettings { it.copy(voiceName = "Magnus", speechRate = 1.2f) }
        repo.cacheDetails(bog, details())
        repo.cacheExample(bog, Example("Jeg læser en bog.", "Я читаю книгу."))
        repo.grade(bog, true)
        repo.resetProgress()
        val p = repo.progress.value
        assertTrue(p.cards.isEmpty())
        assertEquals("Magnus", p.settings.voiceName)
        assertEquals(1, p.wordDetails.size)
        repo.clearAiCache()
        assertTrue(repo.progress.value.wordDetails.isEmpty() && repo.progress.value.exampleSentences.isEmpty())
    }

    @Test
    fun loadsPreviouslySavedProgress() {
        repo.grade(bog, true)
        val again = LearningRepository(TestContent.content, store, clock)
        assertEquals(1, again.progress.value.boxOf(bog))
    }

    private fun details() = WordDetails("іменник", "часто", listOf("a" to "b"), listOf(LabeledExample("l", "d", "u")), listOf(Example("p", "q")))
}
