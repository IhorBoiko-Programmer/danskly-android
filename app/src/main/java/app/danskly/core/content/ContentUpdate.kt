package app.danskly.core.content

import app.danskly.core.json.Json
import app.danskly.core.json.JsonException
import app.danskly.core.json.child
import app.danskly.core.json.int
import app.danskly.core.json.obj
import app.danskly.core.json.str
import app.danskly.core.model.Content
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The newest app build published on GitHub. Offered only when it is newer than the installed one. */
data class AppRelease(val versionCode: Int, val versionName: String, val url: String)

/**
 * `content/manifest.json` on the Danskly server: which content version is live, in which file format
 * ([schema]), and the newest app release. The server publishes it on every deploy.
 */
data class ContentManifest(val version: String, val schema: Int, val app: AppRelease?) {
    companion object {
        /** The content file format this app understands. Raise it when the JSON shape changes. */
        const val SUPPORTED_SCHEMA = 1

        fun parse(text: String): ContentManifest {
            val o = Json.parse(text).obj()
            val app = o.child("app")?.let { a ->
                val url = a.str("url")
                if (url.startsWith("https://")) AppRelease(a.int("versionCode"), a.str("versionName"), url) else null
            }
            return ContentManifest(o.str("version"), o.int("schema"), app)
        }
    }
}

/** Downloaded content files kept on the device. */
interface ContentCache {
    /** Version of the cached content, or null when nothing was downloaded yet. */
    fun version(): String?

    /** A cached file's text, or null if it is missing. */
    fun read(name: String): String?

    /** Replaces the cache with a complete, already validated set of files. */
    fun save(version: String, files: Map<String, String>)
}

/** Result of one update check. */
sealed interface ContentUpdate {
    /** New content was downloaded and validated; it is used from the next start. */
    data class Updated(val version: String, val content: Content) : ContentUpdate

    data object UpToDate : ContentUpdate

    /** The server has content for a newer app; the learner keeps the current content. */
    data object NeedsNewerApp : ContentUpdate

    data class Failed(val reason: String) : ContentUpdate
}

/**
 * Downloads learning content from the server when a new version is published.
 * Files are validated with [ContentParser] before anything is saved, so a broken
 * download can never replace working content.
 */
class ContentUpdater(
    /** Returns the text of a file under the server's `content/` folder; throws on network errors. */
    private val fetch: suspend (String) -> String,
    private val cache: ContentCache,
) {
    /** The manifest of the last check, also used for the "new app version" notice. */
    var manifest: ContentManifest? = null
        private set

    suspend fun update(): ContentUpdate {
        val m = try {
            ContentManifest.parse(fetch(MANIFEST))
        } catch (e: JsonException) {
            return ContentUpdate.Failed("manifest: ${e.message}")
        } catch (e: java.io.IOException) {
            return ContentUpdate.Failed("network: ${e.message}")
        }
        manifest = m
        if (m.schema > ContentManifest.SUPPORTED_SCHEMA) return ContentUpdate.NeedsNewerApp
        if (m.version == cache.version()) return ContentUpdate.UpToDate
        return try {
            val files = ContentFiles.ALL.associateWith { fetch(it) }
            val content = ContentParser.parse { files.getValue(it) }
            if (content.words.isEmpty() || content.grammar.isEmpty() || content.reading.isEmpty()) {
                return ContentUpdate.Failed("content: empty")
            }
            cache.save(m.version, files)
            ContentUpdate.Updated(m.version, content)
        } catch (e: JsonException) {
            ContentUpdate.Failed("content: ${e.message}")
        } catch (e: java.io.IOException) {
            ContentUpdate.Failed("network: ${e.message}")
        }
    }

    companion object {
        const val MANIFEST = "manifest.json"

        /**
         * The content to start with: the cached download if it is complete and valid, else [bundled].
         * Returns the content and the version it came from (null = bundled with the app).
         */
        fun load(cache: ContentCache, bundled: () -> Content): Pair<Content, String?> {
            val version = cache.version() ?: return bundled() to null
            return try {
                ContentParser.parse { name -> cache.read(name) ?: throw JsonException("missing $name") } to version
            } catch (e: JsonException) {
                bundled() to null
            }
        }
    }
}

/** Whether [release] should be offered to a learner who has build [installedVersionCode]. */
fun AppRelease.isNewerThan(installedVersionCode: Int): Boolean = versionCode > installedVersionCode

/** Keeps downloaded content in memory only. Used by tests and previews. */
class MemoryContentCache : ContentCache {
    private var version: String? = null
    private var files: Map<String, String> = emptyMap()

    override fun version(): String? = version

    override fun read(name: String): String? = files[name]

    override fun save(version: String, files: Map<String, String>) {
        this.version = version
        this.files = files
    }
}

/**
 * Runs the update check once per app start and remembers what it found: new content
 * (used from the next start) and, for builds installed outside Google Play, a newer app.
 */
class UpdateChecker(
    private val updater: ContentUpdater,
    private val installed: AppVersion,
    /** Version of the content in use now; null means the content bundled with the app. */
    val contentVersion: String?,
    /** False for Google Play builds: Play updates the app by itself. */
    private val offerAppUpdates: Boolean,
) {
    private val newAppState = MutableStateFlow<AppRelease?>(null)
    val newApp: StateFlow<AppRelease?> = newAppState.asStateFlow()

    val appVersion: String get() = installed.name

    var lastResult: ContentUpdate? = null
        private set

    suspend fun check(): ContentUpdate = updater.update().also { result ->
        lastResult = result
        newAppState.value = updater.manifest?.app?.takeIf { offerAppUpdates && it.isNewerThan(installed.code) }
    }

    fun dismissNewApp() {
        newAppState.value = null
    }

    companion object {
        /** A checker that never finds anything: for tests and previews without a server. */
        fun offline(installed: AppVersion = AppVersion(1, "1.0.0")) = UpdateChecker(
            ContentUpdater({ throw java.io.IOException("offline") }, MemoryContentCache()),
            installed,
            contentVersion = null,
            offerAppUpdates = false,
        )
    }
}

/** The installed build: `versionCode` and `versionName` from the build file. */
data class AppVersion(val code: Int, val name: String)
