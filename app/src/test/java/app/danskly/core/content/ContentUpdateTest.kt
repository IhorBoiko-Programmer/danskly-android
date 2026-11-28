package app.danskly.core.content

import app.danskly.core.TestContent
import app.danskly.core.json.JsonException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ContentUpdateTest {

    private val realFiles = ContentFiles.ALL.associateWith { TestContent.read(it) }

    private fun manifest(version: String = "v2", schema: Int = 1, app: String = "") =
        """{"version":"$version","schema":$schema$app}"""

    /** A fake server: `files` by name; anything missing fails like a 404. */
    private fun server(manifest: String, files: Map<String, String> = realFiles): suspend (String) -> String = { name ->
        if (name == ContentUpdater.MANIFEST) manifest else files[name] ?: throw IOException("HTTP 404")
    }

    @Test
    fun manifestParsing() {
        val m = ContentManifest.parse(manifest(app = ""","app":{"versionCode":7,"versionName":"1.2.0","url":"https://x/a.apk"}"""))
        assertEquals(ContentManifest("v2", 1, AppRelease(7, "1.2.0", "https://x/a.apk")), m)
        assertNull(ContentManifest.parse(manifest(app = ""","app":{"versionCode":7,"versionName":"1","url":"http://x"}""")).app)
        assertNull(ContentManifest.parse(manifest()).app)
    }

    @Test
    fun downloadsValidatesAndCachesNewContent() = runBlocking {
        val cache = MemoryContentCache()
        val result = ContentUpdater(server(manifest()), cache).update()
        assertTrue(result is ContentUpdate.Updated)
        result as ContentUpdate.Updated
        assertEquals("v2", result.version)
        assertEquals(TestContent.content.words.size, result.content.words.size)
        assertEquals("v2", cache.version())
        assertEquals(realFiles[ContentFiles.WORDS], cache.read(ContentFiles.WORDS))
    }

    @Test
    fun sameVersionIsNotDownloadedAgain() = runBlocking {
        val cache = MemoryContentCache().apply { save("v2", realFiles) }
        var fileRequests = 0
        val fetch: suspend (String) -> String = { name -> if (name != ContentUpdater.MANIFEST) fileRequests++; server(manifest())(name) }
        assertEquals(ContentUpdate.UpToDate, ContentUpdater(fetch, cache).update())
        assertEquals(0, fileRequests)
    }

    @Test
    fun newerFormatWaitsForANewerApp() = runBlocking {
        val cache = MemoryContentCache()
        assertEquals(ContentUpdate.NeedsNewerApp, ContentUpdater(server(manifest(schema = 2)), cache).update())
        assertNull(cache.version())
    }

    @Test
    fun brokenDownloadsNeverReplaceContent() = runBlocking {
        val cache = MemoryContentCache()
        val offline: suspend (String) -> String = { throw IOException("offline") }
        val cases = listOf(
            server("{not json"),
            offline,
            server(manifest(), realFiles - ContentFiles.GRAMMAR),
            server(manifest(), realFiles + (ContentFiles.WORDS to """{"topics":[],"words":[{"da":"x"}]}""")),
            server(manifest(), realFiles + (ContentFiles.WORDS to """{"topics":[],"words":[]}""")),
        )
        cases.forEach { fetch ->
            val result = ContentUpdater(fetch, cache).update()
            assertTrue(result.toString(), result is ContentUpdate.Failed)
        }
        assertNull(cache.version())
    }

    @Test
    fun startsWithCachedContentOnlyWhenItIsValid() {
        val bundled = TestContent.content
        val empty = MemoryContentCache()
        assertEquals(bundled to null, ContentUpdater.load(empty) { bundled })

        val good = MemoryContentCache().apply { save("v3", realFiles) }
        val (content, version) = ContentUpdater.load(good) { throw AssertionError("bundled not needed") }
        assertEquals("v3", version)
        assertEquals(bundled.words.size, content.words.size)

        val broken = MemoryContentCache().apply { save("v4", realFiles - ContentFiles.READING) }
        val (fallback, none) = ContentUpdater.load(broken) { bundled }
        assertSame(bundled, fallback)
        assertNull(none)
    }

    @Test
    fun newAppIsOfferedOnlyWhenNewerAndAllowed() = runBlocking {
        val withApp = manifest(app = ""","app":{"versionCode":12,"versionName":"1.2.0","url":"https://x/a.apk"}""")
        fun checker(installed: Int, offer: Boolean) =
            UpdateChecker(ContentUpdater(server(withApp), MemoryContentCache()), AppVersion(installed, "1.1.0"), null, offer)

        val older = checker(installed = 10, offer = true)
        assertNull(older.newApp.value)
        assertTrue(older.check() is ContentUpdate.Updated)
        assertEquals("1.2.0", older.newApp.value!!.versionName)
        assertTrue(older.lastResult is ContentUpdate.Updated)
        older.dismissNewApp()
        assertNull(older.newApp.value)

        val same = checker(installed = 12, offer = true).apply { check() }
        assertNull(same.newApp.value)
        val play = checker(installed = 1, offer = false).apply { check() }
        assertNull(play.newApp.value)
        assertEquals("1.1.0", play.appVersion)
    }

    @Test
    fun offlineCheckerFindsNothing() = runBlocking {
        val offline = UpdateChecker.offline()
        assertNull(offline.contentVersion)
        assertTrue(offline.check() is ContentUpdate.Failed)
        assertNull(offline.newApp.value)
        assertEquals("1.0.0", offline.appVersion)
        assertFalse(AppRelease(1, "1", "https://x").isNewerThan(1))
    }

    @Test(expected = JsonException::class)
    fun manifestWithoutVersionIsRejected() {
        ContentManifest.parse("""{"schema":1}""")
    }
}
