package app.danskly.data

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class HttpContentFetcherTest {

    private val server = MockWebServer()
    private var base = ""

    @Before
    fun setUp() {
        server.start()
        base = server.url("/").toString()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private val fetcher = HttpContentFetcher(OkHttpClient()) { base }

    private fun failsWith(message: String) = runBlocking {
        try {
            fetcher.fetch("words.json")
            fail("expected an IOException")
        } catch (e: IOException) {
            assertTrue(e.message, e.message!!.contains(message))
        }
    }

    @Test
    fun downloadsAFileFromTheContentFolder() = runBlocking {
        server.enqueue(MockResponse.Builder().body("""{"ok":1}""").build())
        assertEquals("""{"ok":1}""", fetcher.fetch("manifest.json"))
        val request = server.takeRequest()
        assertEquals("/content/manifest.json", request.url.encodedPath)
        assertEquals("no-cache", request.headers["cache-control"])
    }

    @Test
    fun errorsBecomeIOExceptions() {
        server.enqueue(MockResponse.Builder().code(404).build())
        failsWith("HTTP 404")
        base = ""
        failsWith("no server")
        base = "not a url"
        failsWith("bad server address")
    }
}

class FileContentCacheTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun savesAndReadsAWholeSet() {
        val dir = File(tmp.root, "content")
        val cache = FileContentCache(dir)
        assertNull(cache.version())
        assertNull(cache.read("words.json"))
        cache.save("v1", mapOf("words.json" to "A", "grammar.json" to "B"))
        assertEquals("v1", cache.version())
        assertEquals("A", cache.read("words.json"))
        cache.save("v2", mapOf("words.json" to "C"))
        assertEquals("v2", FileContentCache(dir).version())
        assertEquals("C", cache.read("words.json"))
        assertNull("old files are gone", cache.read("grammar.json"))
        assertTrue(File(tmp.root, "content.new").let { !it.exists() })
    }

    @Test
    fun emptyVersionFileMeansNothingCached() {
        val dir = File(tmp.root, "content").apply { mkdirs() }
        File(dir, "version.txt").writeText("  ")
        assertNull(FileContentCache(dir).version())
    }
}
