package app.danskly

import android.content.Context
import app.danskly.core.ai.AiClient
import app.danskly.core.exercise.ExerciseGenerator
import app.danskly.core.learning.LearningRepository
import app.danskly.core.learning.WordFormsBuilder
import app.danskly.core.navigation.Navigator
import app.danskly.core.speech.Speaker
import app.danskly.core.time.SystemClock
import app.danskly.data.AndroidSpeaker
import app.danskly.data.AssetContent
import app.danskly.data.HttpAiClient
import app.danskly.data.SharedPrefsProgressStore
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * The app's dependencies in one place (manual dependency injection).
 * Screens receive the graph; tests build one with fakes.
 */
class AppGraph(
    val repository: LearningRepository,
    val speaker: Speaker,
    val ai: AiClient,
    val builtInServerUrl: String = "",
    val navigator: Navigator = Navigator(),
    val random: Random = Random.Default,
) {
    val forms = WordFormsBuilder(repository.content)

    /** The AI server address: the one set in Settings, else the one built into the app. */
    val serverUrl: String get() = repository.progress.value.settings.serverUrl.ifBlank { builtInServerUrl }

    val isAiConfigured: Boolean get() = serverUrl.isNotBlank()

    fun exerciseGenerator() = ExerciseGenerator(
        content = repository.content,
        words = { repository.allWords },
        isStudied = { repository.progress.value.boxOf(it) >= 1 },
        random = random,
        speechAvailable = speaker.isAvailable.value,
    )

    companion object {
        fun create(context: Context): AppGraph {
            val store = SharedPrefsProgressStore(context)
            val repository = LearningRepository(AssetContent.load(context), store, SystemClock())
            val http = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .callTimeout(120, TimeUnit.SECONDS)
                .build()
            lateinit var graph: AppGraph
            val ai = HttpAiClient(
                http = http,
                serverUrl = { graph.serverUrl },
                accessCode = { repository.progress.value.settings.accessCode },
                sessionId = store.sessionId(),
            )
            val speaker = AndroidSpeaker(context, settings = { repository.progress.value.settings })
            graph = AppGraph(repository, speaker, ai, BuildConfig.AI_SERVER_URL)
            return graph
        }
    }
}
