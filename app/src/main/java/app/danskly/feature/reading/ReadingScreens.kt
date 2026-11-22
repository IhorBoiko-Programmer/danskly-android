package app.danskly.feature.reading

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.danskly.AppGraph
import app.danskly.core.navigation.Route
import app.danskly.core.text.TextTools
import app.danskly.feature.exercises.OptionButton
import app.danskly.feature.exercises.OptionState
import app.danskly.ui.ScreenColumn
import app.danskly.ui.components.Glass
import app.danskly.ui.components.GroupedList
import app.danskly.ui.components.LargeTitle
import app.danskly.ui.components.ListRow
import app.danskly.ui.components.LocalSpeaker
import app.danskly.ui.components.PrimaryButton
import app.danskly.ui.components.SecondaryButton
import app.danskly.ui.components.SectionTitle
import app.danskly.ui.components.Segmented
import app.danskly.ui.components.SpeakPair
import app.danskly.ui.components.Subtitle
import app.danskly.ui.theme.LocalPalette
import app.danskly.ui.theme.TileColors

@Composable
fun ReadingListScreen(graph: AppGraph) {
    val progress by graph.repository.progress.collectAsState()
    val texts = graph.repository.content.reading
    ScreenColumn {
        LargeTitle("Читання й слухання")
        Subtitle("Тексти як на PD3: читай або слухай і відповідай на питання")
        GroupedList(texts.withIndex().toList()) { (i, t) ->
            val score = progress.readingScores[i]?.let { " · ✓ $it/${t.questions.size}" }.orEmpty()
            ListRow(t.icon, t.title, t.kind + score, TileColors.list[i % TileColors.list.size]) { graph.navigator.open(Route.Reading(i)) }
        }
    }
}

private enum class ReadingMode { READ, LISTEN }

/** A text with comprehension questions. In listening mode the text appears only after checking. */
@Composable
fun ReadingScreen(graph: AppGraph, index: Int) {
    val repo = graph.repository
    val text = repo.content.reading[index]
    val p = LocalPalette.current
    val speaker = LocalSpeaker.current
    val spoken = remember(index) { TextTools.withoutSpeakerNames(text.danish) }
    var mode by remember { mutableStateOf(ReadingMode.READ) }
    var translated by remember { mutableStateOf(false) }
    var checked by remember { mutableStateOf(false) }
    val answers = remember { mutableStateMapOf<Int, Int>() }
    // Options are shuffled once per visit; option 0 in the data is always the right one.
    val order = remember(index) { text.questions.map { q -> q.options.indices.shuffled(graph.random) } }
    val score = text.questions.indices.count { answers[it] == 0 }
    val showText = mode == ReadingMode.READ || checked

    ScreenColumn {
        LargeTitle(text.title)
        Subtitle(text.kind)
        Segmented(listOf(ReadingMode.READ to "📖 Читати", ReadingMode.LISTEN to "🎧 Слухати"), mode) {
            mode = it
            translated = false
            if (it == ReadingMode.LISTEN && !checked) speaker.speak(spoken)
        }
        if (mode == ReadingMode.LISTEN) {
            Glass(Modifier.padding(vertical = 8.dp), padding = 16.dp) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { SpeakPair(spoken, big = true) }
                Text(
                    if (checked) "Текст відкрито нижче." else "Послухай текст (можна кілька разів) і дай відповіді. Текст з’явиться після перевірки.",
                    color = p.muted,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
        if (showText) {
            Glass(Modifier.padding(vertical = 8.dp), padding = 16.dp) {
                Text(if (translated) text.ukrainian else text.danish, color = p.ink, lineHeight = 24.sp, modifier = Modifier.testTag("reading-text"))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                    if (mode == ReadingMode.READ) SpeakPair(spoken)
                    Text(
                        if (translated) "Показати данською" else "Показати переклад",
                        color = p.accent,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .clickable(role = Role.Button) { translated = !translated }
                            .padding(6.dp)
                            .testTag("translate-toggle"),
                    )
                }
            }
        }
        SectionTitle("Питання")
        text.questions.forEachIndexed { qi, q ->
            Column(Modifier.padding(bottom = 14.dp)) {
                Text("${qi + 1}. ${q.question}", fontWeight = FontWeight.SemiBold, color = p.ink)
                Text(q.hint, color = p.muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
                order[qi].forEach { oi ->
                    val state = when {
                        !checked -> if (answers[qi] == oi) OptionState.SELECTED else OptionState.OPEN
                        oi == 0 -> OptionState.RIGHT
                        answers[qi] == oi -> OptionState.WRONG
                        else -> OptionState.DIMMED
                    }
                    OptionButton(q.options[oi], state) { answers[qi] = oi }
                }
            }
        }
        if (checked) {
            Glass(padding = 16.dp) {
                Text("Результат: $score з ${text.questions.size}", fontWeight = FontWeight.Bold, color = if (score == text.questions.size) p.ok else p.bad)
                Text(if (score == text.questions.size) "Усе правильно!" else "Перечитай або переслухай текст і подивись, де відповідь.", color = p.ink)
            }
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Ще раз", Modifier.weight(1f)) {
                    answers.clear()
                    checked = false
                }
                PrimaryButton("Наступний текст", Modifier.weight(1f)) { graph.navigator.replace(Route.Reading((index + 1) % repo.content.reading.size)) }
            }
        } else {
            PrimaryButton("Перевірити відповіді", Modifier.fillMaxWidth(), enabled = answers.size == text.questions.size) {
                checked = true
                repo.saveReadingScore(index, text.questions.indices.count { answers[it] == 0 })
            }
        }
    }
}
