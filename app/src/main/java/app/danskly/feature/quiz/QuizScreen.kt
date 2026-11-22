package app.danskly.feature.quiz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.danskly.AppGraph
import app.danskly.core.exercise.QuizGenerator
import app.danskly.core.exercise.QuizMode
import app.danskly.ui.ScreenColumn
import app.danskly.ui.components.Chip
import app.danskly.ui.components.LocalSpeaker
import app.danskly.ui.components.Note
import app.danskly.ui.components.PrimaryButton
import app.danskly.ui.components.SpeakPair
import app.danskly.ui.components.Subtitle
import app.danskly.ui.theme.LocalPalette
import app.danskly.feature.exercises.OptionButton
import app.danskly.feature.exercises.OptionState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuizScreen(graph: AppGraph, topic: String?) {
    val repo = graph.repository
    val words = remember { repo.wordsIn(topic) }
    if (words.size < 2) {
        ScreenColumn { Note("Для тесту потрібно щонайменше два слова в темі.") }
        return
    }
    val p = LocalPalette.current
    val speaker = LocalSpeaker.current
    val progress by repo.progress.collectAsState()
    val generator = remember { QuizGenerator(words, graph.random) }
    var mode by remember { mutableStateOf(QuizMode.TRANSLATE) }
    var question by remember { mutableStateOf(generator.next(mode)) }
    var chosen by remember { mutableStateOf<String?>(null) }

    if (mode == QuizMode.LISTEN && chosen == null) LaunchedEffect(question) { speaker.speak(question.word.display) }

    ScreenColumn {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
            generator.modes().forEach { m ->
                Chip(m.label, selected = m == mode) {
                    mode = m
                    question = generator.next(m)
                    chosen = null
                }
            }
        }
        if (question.mode == QuizMode.LISTEN) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 14.dp)) {
                SpeakPair(question.word.display, big = true)
                Text(
                    if (chosen == null) "Послухай і обери переклад" else question.word.display,
                    Modifier.padding(start = 14.dp),
                    fontSize = if (chosen == null) 16.sp else 22.sp,
                    fontWeight = if (chosen == null) FontWeight.Normal else FontWeight.Bold,
                    color = if (chosen == null) p.muted else p.ink,
                )
            }
        } else {
            Text(question.prompt, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = p.ink, modifier = Modifier.padding(bottom = 14.dp))
        }
        question.options.forEach { option ->
            val state = when {
                chosen == null -> OptionState.OPEN
                option == question.answer -> OptionState.RIGHT
                option == chosen -> OptionState.WRONG
                else -> OptionState.DIMMED
            }
            OptionButton(option, state) {
                chosen = option
                val correct = option == question.answer
                repo.grade(question.word, correct)
                repo.recordQuizAnswer(correct)
                speaker.speak(question.word.display)
            }
        }
        PrimaryButton("Далі", Modifier.fillMaxWidth().padding(top = 8.dp), enabled = chosen != null) {
            question = generator.next(mode)
            chosen = null
        }
        Subtitle("${topic ?: "Усі слова"} · правильно ${progress.quizCorrect} з ${progress.quizTotal}", Modifier.padding(top = 12.dp), center = true)
    }
}
