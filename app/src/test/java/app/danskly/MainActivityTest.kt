package app.danskly

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import app.danskly.core.navigation.Route
import app.danskly.support.testGraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainActivityTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun launchesTheAppAndHandlesSystemBack() {
        val app = ApplicationProvider.getApplicationContext<DansklyApplication>()
        val graph = testGraph()
        app.graph = graph
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.onNodeWithTag("tile:Граматика").performClick()
            compose.waitForIdle()
            assertEquals(Route.Grammar, graph.navigator.current)
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.waitForIdle()
            assertEquals(Route.Home, graph.navigator.current)
        }
    }

    @Test
    fun applicationCreatesItsGraphOnFirstUse() {
        val app = ApplicationProvider.getApplicationContext<DansklyApplication>()
        app.graph = null
        val first = app.graph()
        assertNotNull(first)
        assertEquals(first, app.graph())
    }
}
