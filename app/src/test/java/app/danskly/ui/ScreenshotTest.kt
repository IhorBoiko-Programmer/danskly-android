package app.danskly.ui

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.danskly.core.exercise.ExerciseType
import app.danskly.core.navigation.Route
import app.danskly.support.UiTest
import app.danskly.support.testGraph
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.robolectric.annotation.GraphicsMode

/**
 * Renders every screen to a PNG (light and dark) so the whole app can be reviewed visually.
 * Run `./gradlew recordRoborazziDebug`; images land in app/screenshots/.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest : UiTest() {

    private fun scrollTo(text: String) {
        compose.onNodeWithText(text).performScrollTo()
    }

    private fun shoot(name: String, route: Route?, dark: Boolean = false, prepare: () -> Unit = {}) {
        launch(route, testGraph(), dark)
        prepare()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/${if (dark) "dark" else "light"}/$name.png")
    }

    @Test fun home() = shoot("01_home", null)
    @Test fun homeDark() = shoot("01_home", null, dark = true)
    @Test fun today() = shoot("02_today", Route.Today)
    @Test fun review() = shoot("03_review", Route.Review)
    @Test fun reviewBack() = shoot("03b_review_back", Route.Review) { clickTag("flip-card") }
    @Test fun topics() = shoot("04_topics", Route.Topics)
    @Test fun topicsDark() = shoot("04_topics", Route.Topics, dark = true)
    @Test fun topicsVerbs() = shoot("04b_topics_verbs", Route.Topics) { scrollTo("Прикметники") }
    @Test fun quizResult() = shoot("09c_quiz_result", Route.Quiz("Подорожі")) {
        clickTag("length:10")
        repeat(10) { i ->
            compose.onAllNodes(tagStartsWith("option:"))[0].performClick()
            click(if (i == 9) "Результат" else "Далі")
        }
    }
    @Test fun topic() = shoot("05_topic", Route.Topic("Подорожі"))
    @Test fun cards() = shoot("06_cards", Route.Cards("Подорожі")) { clickTag("flip-card") }
    @Test fun wordVerb() = shoot("07_word_verb", Route.WordDetails("at gå"))
    @Test fun wordNoun() = shoot("07b_word_noun", Route.WordDetails("kuffert"))
    @Test fun wordDark() = shoot("07_word_verb", Route.WordDetails("at gå"), dark = true)
    @Test fun addWord() = shoot("08_add_word", Route.AddWord(null))
    @Test fun quiz() = shoot("09_quiz", Route.Quiz(null))
    @Test fun quizQuestion() = shoot("09b_quiz_question", Route.Quiz("Подорожі")) { clickTag("length:20") }
    @Test fun exercises() = shoot("10_exercises", Route.Exercises)
    @Test fun gap() = shoot("11_ex_gap", Route.Exercise(ExerciseType.GAP))
    @Test fun build() = shoot("12_ex_build", Route.Exercise(ExerciseType.BUILD)) { clickTag("bank:0") }
    @Test fun write() = shoot("13_ex_write", Route.Exercise(ExerciseType.WRITE))
    @Test fun dictation() = shoot("14_ex_dictation", Route.Exercise(ExerciseType.DICTATION))
    @Test fun readingList() = shoot("15_reading_list", Route.ReadingList)
    @Test fun texts() = shoot("15b_texts_listening", Route.Texts(listening = true))
    @Test fun reading() = shoot("16_reading", Route.Reading(3))
    @Test fun listening() = shoot("16b_listening", Route.Reading(3, listening = true))
    @Test fun grammar() = shoot("17_grammar", Route.Grammar)
    @Test fun grammarSection() = shoot("18_grammar_verbs", Route.GrammarSection(2))
    @Test fun grammarExam() = shoot("18b_grammar_exam", Route.GrammarSection(9))
    @Test fun pronunciation() = shoot("19_pronunciation", Route.Pronunciation)
    @Test fun writingList() = shoot("20_writing_list", Route.WritingList)
    @Test fun writing() = shoot("21_writing", Route.Writing(3)) { click("Приклад відповіді") }
    @Test fun tutor() = shoot("22_tutor", Route.Tutor) { click("Розмова: подорожі") }
    @Test fun settings() = shoot("23_settings", Route.Settings)
    @Test fun settingsVoices() = shoot("23b_settings_voices", Route.Settings) { click("Magnus (Enhanced)") }
    @Test fun settingsDark() = shoot("23_settings", Route.Settings, dark = true)
}
