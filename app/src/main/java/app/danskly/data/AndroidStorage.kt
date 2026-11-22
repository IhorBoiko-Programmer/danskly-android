package app.danskly.data

import android.content.Context
import app.danskly.core.content.ContentParser
import app.danskly.core.model.Content
import app.danskly.core.progress.ProgressStore
import java.util.UUID

/** Reads the bundled learning content from `assets/content/`. */
object AssetContent {
    fun load(context: Context): Content =
        ContentParser.parse { name -> context.assets.open("content/$name").bufferedReader().use { it.readText() } }
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
