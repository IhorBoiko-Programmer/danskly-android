package app.danskly.feature.writing

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.danskly.AppGraph
import app.danskly.core.ai.AiException
import app.danskly.core.ai.AiTasks
import app.danskly.core.ai.ChatMessage
import app.danskly.core.ai.Role
import app.danskly.core.navigation.Route
import app.danskly.ui.ScreenColumn
import app.danskly.ui.components.Glass
import app.danskly.ui.components.GroupedList
import app.danskly.ui.components.LargeTitle
import app.danskly.ui.components.ListRow
import app.danskly.ui.components.Note
import app.danskly.ui.components.PrimaryButton
import app.danskly.ui.components.SecondaryButton
import app.danskly.ui.components.ReportAi
import app.danskly.ui.components.SectionTitle
import app.danskly.ui.components.SpeakPair
import app.danskly.ui.components.Subtitle
import app.danskly.ui.theme.LocalPalette
import app.danskly.ui.theme.TileColors
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

private val TASK_ICONS = listOf("📧", "💌", "💭", "📣", "💼", "💡")

@Composable
fun WritingListScreen(graph: AppGraph) {
    val tasks = graph.repository.content.writingTasks
    ScreenColumn {
        LargeTitle("Письмо")
        Subtitle("Обери тип тексту")
        GroupedList(tasks.withIndex().toList()) { (i, t) ->
            ListRow(TASK_ICONS.getOrElse(i) { "📝" }, t.title, t.task.substringBefore(". ").take(SUBTITLE_LENGTH) + "…", TileColors.list[i % TileColors.list.size]) {
                graph.navigator.open(Route.Writing(i))
            }
        }
    }
}

/** A writing task with a model answer, useful phrases and AI feedback on the learner's text. */
@Composable
fun WritingScreen(graph: AppGraph, index: Int) {
    val content = graph.repository.content
    val task = content.writingTasks[index]
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    var draft by rememberSaveable(index) { mutableStateOf("") }
    var showSample by remember { mutableStateOf(false) }
    var translation by remember { mutableStateOf<String?>(null) }
    var review by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var reviewIsAi by remember { mutableStateOf(false) }

    ScreenColumn {
        LargeTitle(task.title)
        Note("Завдання. ${task.task}")
        SecondaryButton(if (showSample) "Сховати приклад відповіді" else "Приклад відповіді", Modifier.fillMaxWidth().padding(vertical = 6.dp)) { showSample = !showSample }
        if (showSample) {
            Glass(padding = 16.dp) {
                Text(task.sample, color = p.ink, modifier = Modifier.testTag("sample"))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                    SpeakPair(task.sample)
                    if (graph.isAiConfigured) {
                        SecondaryButton("Переклад (ШІ)", Modifier.padding(start = 12.dp)) {
                            translation = "Перекладаю…"
                            scope.launch {
                                translation = try {
                                    graph.ai.complete(listOf(ChatMessage(Role.USER, AiTasks.translatePrompt(task.sample)))).text
                                } catch (e: AiException) {
                                    e.message
                                }
                            }
                        }
                    }
                }
                translation?.let { Text(it, color = p.muted, modifier = Modifier.padding(top = 10.dp).testTag("translation")) }
                Text(task.tips, color = p.ink, modifier = Modifier.padding(top = 10.dp))
            }
        }
        SectionTitle("Корисні фрази")
        Glass(padding = 16.dp) { content.writingPhrases.forEach { Text(it, color = p.ink, modifier = Modifier.padding(vertical = 3.dp)) } }
        SectionTitle("Твій текст")
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            placeholder = { Text("Напиши свій текст данською…") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp).testTag("draft"),
        )
        when {
            !graph.isAiConfigured -> Note("Перевірка тексту запрацює, коли в Налаштуваннях буде вказано сервер ШІ.")
            else -> PrimaryButton(if (busy) "Перевіряю…" else "Перевірити з ШІ", Modifier.fillMaxWidth().padding(top = 10.dp), enabled = !busy) {
                if (draft.trim().length < MIN_TEXT) {
                    review = "Спочатку напиши текст."
                    reviewIsAi = false
                    return@PrimaryButton
                }
                busy = true
                review = "Перевіряю…"
                reviewIsAi = false
                scope.launch {
                    graph.ai.stream(listOf(ChatMessage(Role.USER, AiTasks.checkWritingPrompt(task, draft))))
                        .catch { e -> review = (e as? AiException)?.message ?: e.message }
                        .collect {
                            review = it
                            reviewIsAi = true
                        }
                    busy = false
                }
            }
        }
        review?.let {
            Glass(Modifier.padding(top = 12.dp), padding = 16.dp) {
                Text(it, color = p.ink, fontWeight = FontWeight.Normal, modifier = Modifier.testTag("review"))
                if (reviewIsAi && !busy) ReportAi(it, graph.ai)
            }
        }
    }
}

private const val MIN_TEXT = 20
private const val SUBTITLE_LENGTH = 60
