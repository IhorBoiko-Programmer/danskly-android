package app.danskly.ui

import app.danskly.AppGraph
import app.danskly.core.TestContent
import app.danskly.core.content.AppVersion
import app.danskly.core.content.ContentUpdate
import app.danskly.core.content.ContentUpdater
import app.danskly.core.content.MemoryContentCache
import app.danskly.core.content.UpdateChecker
import app.danskly.core.navigation.Route
import app.danskly.support.UiTest
import app.danskly.support.testGraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The app checks the server once per start: new content for the next start, a newer app if there is one. */
class UpdatesTest : UiTest() {

    private val apk = "https://github.com/IhorBoiko-Programmer/danskly-android/releases/latest/download/Danskly.apk"

    private fun graphWithServer(newestApp: Int, offerAppUpdates: Boolean = true, contentVersion: String? = null): AppGraph {
        val manifest = """{"version":"2026-11-28","schema":1,"app":{"versionCode":$newestApp,"versionName":"1.2.0","url":"$apk"}}"""
        val fetch: suspend (String) -> String = { name -> if (name == ContentUpdater.MANIFEST) manifest else TestContent.read(name) }
        val checker = UpdateChecker(ContentUpdater(fetch, MemoryContentCache()), AppVersion(5, "1.1.0"), contentVersion, offerAppUpdates)
        val g = testGraph()
        return AppGraph(g.repository, g.speaker, g.ai, g.builtInServerUrl, g.navigator, g.random, checker)
    }

    @Test
    fun newerAppIsOfferedOnHome() {
        launch(g = graphWithServer(newestApp = 6))
        assertTrue(exists("🎉 Доступна нова версія 1.2.0"))
        click("Оновити")
        assertEquals(listOf(apk), openedLinks)
        click("Пізніше")
        assertFalse(exists("🎉 Доступна нова версія 1.2.0"))
    }

    @Test
    fun noBannerWhenUpToDateOrInstalledFromPlay() {
        launch(g = graphWithServer(newestApp = 5))
        assertFalse(exists("Оновити"))
    }

    @Test
    fun playBuildsLeaveUpdatesToPlay() {
        launch(g = graphWithServer(newestApp = 9, offerAppUpdates = false))
        assertFalse(exists("Оновити"))
    }

    @Test
    fun contentIsDownloadedOnStart() {
        val g = graphWithServer(newestApp = 5)
        launch(g = g)
        assertTrue(g.updates.lastResult is ContentUpdate.Updated)
    }

    @Test
    fun settingsShowVersions() {
        launch(Route.Settings, graphWithServer(newestApp = 5, contentVersion = "2026-11-27"))
        assertTrue(exists("1.1.0"))
        assertTrue(exists("оновлено · 2026-11-27"))
    }

    @Test
    fun settingsShowBundledContent() {
        launch(Route.Settings)
        assertTrue(exists("вбудовані"))
        assertTrue(exists("Нові слова, правила й тексти завантажуються автоматично", substring = true))
    }
}
