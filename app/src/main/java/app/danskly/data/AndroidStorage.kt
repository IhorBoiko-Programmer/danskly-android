package app.danskly.data

import android.content.Context
import app.danskly.core.content.ContentCache
import app.danskly.core.content.ContentParser
import app.danskly.core.model.Content
import app.danskly.core.progress.ProgressStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.UUID

/** Reads the bundled learning content from `assets/content/`. */
object AssetContent {
    fun load(context: Context): Content =
        ContentParser.parse { name -> context.assets.open("content/$name").bufferedReader().use { it.readText() } }
}

/**
 * Downloads files from the server's `content/` folder (published with every server deploy).
 * Throws [IOException] when the server cannot be reached or answers with an error.
 */
class HttpContentFetcher(private val http: OkHttpClient, private val serverUrl: () -> String) {
    suspend fun fetch(name: String): String = withContext(Dispatchers.IO) {
        val base = serverUrl().trimEnd('/').ifBlank { throw IOException("no server") }
        val request = try {
            Request.Builder().url("$base/content/$name").header("cache-control", "no-cache").build()
        } catch (e: IllegalArgumentException) {
            throw IOException("bad server address", e)
        }
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body.string()
        }
    }
}

/** Keeps progress as JSON in the app's private SharedPreferences (included in Android backup). */
class SharedPrefsProgressStore(context: Context) : ProgressStore {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun load(): String? = prefs.getString(KEY_PROGRESS, null)

    override fun save(text: String) {
        prefs.edit().putString(KEY_PROGRESS, text).apply()
    }

    /** A random id for this installation, sent to the AI server so it can cache per conversation. */
    fun sessionId(): String = prefs.getString(KEY_SESSION, null) ?: UUID.randomUUID().toString().also {
        prefs.edit().putString(KEY_SESSION, it).apply()
    }

    private companion object {
        const val PREFS = "danskly"
        const val KEY_PROGRESS = "progress"
        const val KEY_SESSION = "session"
    }
}

/** Downloaded content files in the app's private storage (`files/content/`). */
class FileContentCache(private val dir: File) : ContentCache {

    override fun version(): String? = File(dir, VERSION).takeIf { it.isFile }?.readText()?.trim()?.ifEmpty { null }

    override fun read(name: String): String? = File(dir, name).takeIf { it.isFile }?.readText()

    override fun save(version: String, files: Map<String, String>) {
        // Write into a fresh folder first and swap it in, so a crash never leaves half-updated content.
        val next = File(dir.parentFile, dir.name + ".new").apply { deleteRecursively(); mkdirs() }
        files.forEach { (name, text) -> File(next, name).writeText(text) }
        File(next, VERSION).writeText(version)
        dir.deleteRecursively()
        check(next.renameTo(dir)) { "Could not replace $dir" }
    }

    private companion object {
        const val VERSION = "version.txt"
    }
}
