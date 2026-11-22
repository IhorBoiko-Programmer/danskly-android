package app.danskly.support

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.danskly.AppGraph
import app.danskly.core.navigation.Route
import app.danskly.ui.DansklyApp
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Base for screen tests: runs the real app UI on the JVM with Robolectric. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h820dp-xxhdpi")
abstract class UiTest {

    @get:Rule
    val compose = createComposeRule()

    lateinit var graph: AppGraph
    val speaker: FakeSpeaker get() = graph.speaker as FakeSpeaker
    val ai: FakeAiClient get() = graph.ai as FakeAiClient

    /** Shows the app, optionally opening [route] on top of the home screen. */
    fun launch(route: Route? = null, g: AppGraph = testGraph(), dark: Boolean? = false) {
        graph = g
        route?.let { graph.navigator.open(it) }
        compose.setContent { DansklyApp(graph, dark) }
        compose.waitForIdle()
    }

    fun text(value: String, substring: Boolean = false): SemanticsNodeInteraction =
        compose.onNodeWithText(value, substring = substring, useUnmergedTree = false)

    fun tag(value: String): SemanticsNodeInteraction = compose.onNodeWithTag(value)

    fun click(value: String, substring: Boolean = false) {
        text(value, substring).scrollIfPossible().performClick()
        compose.waitForIdle()
    }

    fun clickTag(value: String) {
        tag(value).scrollIfPossible().performClick()
        compose.waitForIdle()
    }

    /** Scrolls the node into view when it lives in a scrolling screen; the top bar does not scroll. */
    fun SemanticsNodeInteraction.scrollIfPossible(): SemanticsNodeInteraction = try {
        performScrollTo()
    } catch (e: AssertionError) {
        this
    }

    fun exists(value: String, substring: Boolean = false): Boolean =
        compose.onAllNodes(androidx.compose.ui.test.hasText(value, substring = substring)).fetchSemanticsNodes().isNotEmpty()
}
