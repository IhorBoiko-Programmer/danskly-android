package app.danskly.feature.words

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.danskly.AppGraph
import app.danskly.core.ai.AiException
import app.danskly.core.ai.AiTasks
import app.danskly.core.ai.ChatMessage
import app.danskly.core.ai.Role
import app.danskly.core.learning.FormRow
import app.danskly.core.learning.FormsKind
import app.danskly.core.learning.WordFormsBuilder
import app.danskly.core.model.WordDetails
import app.danskly.ui.ScreenColumn
import app.danskly.ui.components.ExampleRow
import app.danskly.ui.components.Glass
import app.danskly.ui.components.LargeTitle
import app.danskly.ui.components.Note
import app.danskly.ui.components.RowDivider
import app.danskly.ui.components.SecondaryButton
import app.danskly.ui.components.SectionTitle
import app.danskly.ui.components.SpeakButton
import app.danskly.ui.components.SpeakPair
import app.danskly.ui.components.Subtitle
import app.danskly.ui.theme.LocalPalette
import kotlinx.coroutines.launch

/**
 * Everything about one word: all tenses or forms (from verified tables), when it is used,
 * example sentences with translations (from AI, cached) and examples from the grammar section.
 */
@Composable
fun WordDetailsScreen(graph: AppGraph, danish: String) {
    val repo = graph.repository
    val word = repo.findWord(danish) ?: return
    val progress by repo.progress.collectAsState()
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val details = progress.wordDetails[word.danish]
    val forms = graph.forms.formsOf(word)

    fun load() {
        loading = true
        error = null
        scope.launch {
            try {
                val reply = graph.ai.complete(listOf(ChatMessage(Role.USER, AiTasks.wordDetailsPrompt(word))), json = true)
                repo.cacheDetails(word, AiTasks.parseWordDetails(reply.text))
            } catch (e: AiException) {
                error = e.message
            }
            loading = false
        }
    }
    if (details == null && graph.isAiConfigured) LaunchedEffect(word.danish) { load() }

    ScreenColumn {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                LargeTitle(word.display)
                Subtitle("${word.ukrainian} · ${word.topic}")
            }
            SpeakPair(word.display, big = true)
        }
        when {
            forms != null -> {
                SectionTitle(if (forms.kind == FormsKind.VERB) "Усі часи й форми" else "Форми")
                FormTable(forms.rows)
            }
            details != null && details.forms.isNotEmpty() -> {
                SectionTitle("Форми")
                FormTable(details.forms.map { (label, form) -> FormRow(label, form, WordFormsBuilder.TENSE_HINTS[label]) })
                Subtitle("Форми згенерував ШІ, вони можуть містити помилки.", Modifier.padding(top = 6.dp))
            }
        }
        when {
            details != null -> AiDetails(details)
            loading -> Note("Готую пояснення й приклади…")
            error != null -> Note(error.orEmpty())
            !graph.isAiConfigured -> Note("Приклади від ШІ з’являться, коли в Налаштуваннях буде вказано сервер ШІ.")
        }
        if (graph.isAiConfigured && !loading) {
            SecondaryButton(if (details != null) "Інші приклади" else "Спробувати ще раз", Modifier.fillMaxWidth().padding(top = 10.dp)) { load() }
        }
        val local = graph.forms.examplesWith(forms?.spellings ?: listOf(word.verbBase.substringBefore(" ")))
        if (local.isNotEmpty()) {
            SectionTitle("Приклади з граматики")
            Glass(padding = 12.dp) { local.forEach { ExampleRow(it.example.danish, it.example.ukrainian, tag = it.section) } }
        }
        if (word.isCustom) {
            SecondaryButton("Видалити це слово", Modifier.fillMaxWidth().padding(top = 16.dp), color = LocalPalette.current.bad) {
                repo.deleteCustomWord(word)
                graph.navigator.back()
            }
        }
    }
}

@Composable
private fun FormTable(rows: List<FormRow>) {
    val p = LocalPalette.current
    Glass {
        rows.forEachIndexed { i, row ->
            if (i > 0) RowDivider()
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(row.label, color = p.muted, fontSize = 12.sp)
                    Text(row.form, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = p.ink)
                    row.hint?.let { Text(it, color = p.muted, fontSize = 13.sp) }
                }
                SpeakButton(row.form.removeSuffix(" …"))
            }
        }
    }
}

@Composable
private fun AiDetails(d: WordDetails) {
    val p = LocalPalette.current
    if (d.usage.isNotBlank()) {
        SectionTitle("Коли вживається")
        Glass(padding = 16.dp) { Text(d.usage, color = p.ink) }
    }
    SectionTitle("Приклади речень")
    Glass(padding = 12.dp) { d.examples.forEach { ExampleRow(it.danish, it.ukrainian, tag = it.label.ifBlank { null }) } }
    if (d.phrases.isNotEmpty()) {
        SectionTitle("Сталі вирази")
        Glass(padding = 12.dp) { d.phrases.forEach { ExampleRow(it.danish, it.ukrainian) } }
    }
    Subtitle("Пояснення й приклади згенерував ШІ.", Modifier.padding(top = 8.dp))
}
