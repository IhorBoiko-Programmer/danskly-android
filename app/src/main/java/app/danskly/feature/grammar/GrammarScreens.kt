package app.danskly.feature.grammar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.danskly.AppGraph
import app.danskly.core.navigation.Route
import app.danskly.core.speech.VoiceSelector
import app.danskly.ui.ScreenColumn
import app.danskly.ui.components.ExampleRow
import app.danskly.ui.components.Glass
import app.danskly.ui.components.GroupedList
import app.danskly.ui.components.HighlightedText
import app.danskly.ui.components.LargeTitle
import app.danskly.ui.components.ListRow
import app.danskly.ui.components.LocalSpeaker
import app.danskly.ui.components.SectionTitle
import app.danskly.ui.components.Subtitle
import app.danskly.ui.theme.LocalPalette
import app.danskly.ui.theme.TileColors

@Composable
fun GrammarScreen(graph: AppGraph) {
    val sections = graph.repository.content.grammar
    ScreenColumn {
        LargeTitle("Граматика")
        Subtitle("Частини мови")
        GroupedList(sections.withIndex().toList()) { (i, s) ->
            ListRow(s.icon, s.title, "${s.rules.size} правил з прикладами", TileColors.list[i % TileColors.list.size]) { graph.navigator.open(Route.GrammarSection(i)) }
        }
    }
}

@Composable
fun GrammarSectionScreen(graph: AppGraph, index: Int) {
    val section = graph.repository.content.grammar[index]
    val p = LocalPalette.current
    ScreenColumn {
        LargeTitle(section.title)
        section.rules.forEach { rule ->
            Glass(Modifier.padding(bottom = 12.dp), padding = 16.dp) {
                Text(rule.title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = p.ink)
                HighlightedText(rule.body, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
                rule.examples.forEach { ExampleRow(it.danish, it.ukrainian) }
            }
        }
    }
}

/** The Danish alphabet and the tricky sounds, every item can be played. */
@Composable
fun PronunciationScreen(graph: AppGraph) {
    val content = graph.repository.content
    val speaker = LocalSpeaker.current
    val voices by graph.speaker.voices.collectAsState()
    val available by graph.speaker.isAvailable.collectAsState()
    val progress by graph.repository.progress.collectAsState()
    val p = LocalPalette.current
    val voice = VoiceSelector.choose(voices, progress.settings)
    ScreenColumn {
        LargeTitle("Вимова")
        Subtitle(
            when {
                !available -> "На цьому телефоні не працює синтез мовлення. Перевір налаштування системи."
                voice == null -> "Данського голосу не знайдено. Як його додати — у Налаштуваннях."
                else -> "Голос: ${voice.name}. Кнопка 🐢 вимовляє повільніше."
            },
        )
        SectionTitle("Алфавіт")
        content.alphabet.chunked(LETTERS_PER_ROW).forEach { row ->
            Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { l ->
                    val shape = RoundedCornerShape(16.dp)
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(shape)
                            .background(p.glass)
                            .border(1.dp, p.line, shape)
                            .clickable(onClickLabel = "${l.letter}: ${l.word}, ${l.ukrainian}", role = Role.Button) { speaker.speak("${l.letter}. ${l.word}") }
                            .padding(vertical = 10.dp)
                            .testTag("letter:${l.letter}"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(l.letter + l.letter.lowercase(), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = p.ink)
                        Text(l.word, fontSize = 12.sp, color = p.muted)
                    }
                }
                repeat(LETTERS_PER_ROW - row.size) { Column(Modifier.weight(1f)) {} }
            }
        }
        SectionTitle("Звуки, які варто знати")
        content.sounds.forEach { sound ->
            Glass(Modifier.padding(bottom = 12.dp), padding = 16.dp) {
                Text(sound.title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = p.ink)
                Text(sound.body, color = p.muted, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
                sound.examples.forEach { ExampleRow(it.danish, it.ukrainian, slowButton = true) }
            }
        }
    }
}

private const val LETTERS_PER_ROW = 4
