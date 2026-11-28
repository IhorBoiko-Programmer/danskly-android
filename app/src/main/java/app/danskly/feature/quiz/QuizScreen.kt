package app.danskly.feature.quiz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.danskly.AppGraph
import app.danskly.core.exercise.QuizGenerator
import app.danskly.core.exercise.QuizMode
import app.danskly.core.exercise.QuizRound
import app.danskly.feature.exercises.OptionButton
import app.danskly.feature.exercises.OptionState
import app.danskly.ui.ScreenColumn
import app.danskly.ui.components.Chip
import app.danskly.ui.components.Glass
import app.danskly.ui.components.LargeTitle
import app.danskly.ui.components.LocalSpeaker
import app.danskly.ui.components.Note
import app.danskly.ui.components.PrimaryButton
import app.danskly.ui.components.ProgressLine
import app.danskly.ui.components.RowDivider
import app.danskly.ui.components.SecondaryButton
import app.danskly.ui.components.SectionTitle
import app.danskly.ui.components.SpeakButton
import app.danskly.ui.components.SpeakPair
import app.danskly.ui.components.Subtitle
import app.danskly.ui.theme.LocalPalette
import app.danskly.ui.theme.TileColors

/** Vocabulary test: choose the mode and the length (10, 20 or 50 questions), answer, see the result. */
@Composable
fun QuizScreen(graph: AppGraph, topic: String?) {
    val repo = graph.repository
    val words = remember { repo.wordsIn(topic) }
    if (words.size < 2) {
        ScreenColumn { Note("Для тесту потрібно щонайменше два слова в темі.") }
        return
    }
    val generator = remember { QuizGenerator(words, graph.random) }
    var mode by remember { mutableStateOf(QuizMode.TRANSLATE) }
    var round by remember { mutableStateOf<QuizRound?>(null) }
    val start = { length: Int -> round = generator.round(mode, length) }

    val current = round
    when {
        current == null -> QuizSetup(topic ?: "Усі слова", words.size, generator.modes(), mode, onMode = { mode = it }, onStart = start)
        current.isFinished -> QuizResult(current, onAgain = { start(current.size) }, onNew = { round = null })
        else -> QuizQuestionView(
            current,
            onAnswer = { option ->
                val next = current.answer(option)
                val q = current.current
                repo.grade(q.word, option == q.answer)
                repo.recordQuizAnswer(option == q.answer)
                round = next
            },
            onNext = { round = current.next() },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuizSetup(title: String, wordCount: Int, modes: List<QuizMode>, mode: QuizMode, onMode: (QuizMode) -> Unit, onStart: (Int) -> Unit) {
    ScreenColumn {
        LargeTitle("Тест")
        Subtitle("$title · $wordCount слів")
        SectionTitle("Що перевіряємо")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            modes.forEach { m -> Chip(m.label, selected = m == mode) { onMode(m) } }
        }
        SectionTitle("Скільки питань")
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            QuizGenerator.LENGTHS.forEach { n ->
                PrimaryButton("Тест: $n питань", Modifier.fillMaxWidth().testTag("length:$n")) { onStart(n) }
            }
        }
    }
}

@Composable
private fun QuizQuestionView(round: QuizRound, onAnswer: (String) -> Unit, onNext: () -> Unit) {
    val p = LocalPalette.current
    val speaker = LocalSpeaker.current
    val question = round.current
    val chosen = round.chosen

    if (question.mode == QuizMode.LISTEN && chosen == null) LaunchedEffect(round.index) { speaker.speak(question.word.display) }

    ScreenColumn {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Питання ${round.index + 1} з ${round.size}", color = p.muted, modifier = Modifier.weight(1f).testTag("quiz-counter"))
            Text("✓ ${round.correct}", color = p.ok, fontWeight = FontWeight.SemiBold)
        }
        ProgressLine((round.index + 1f) / round.size, TileColors.quiz.first, TileColors.quiz.second, Modifier.padding(top = 6.dp, bottom = 16.dp))
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
                onAnswer(option)
                speaker.speak(question.word.display)
            }
        }
        PrimaryButton(if (round.isLast) "Результат" else "Далі", Modifier.fillMaxWidth().padding(top = 8.dp), enabled = chosen != null, onClick = onNext)
    }
}

@Composable
private fun QuizResult(round: QuizRound, onAgain: () -> Unit, onNew: () -> Unit) {
    val p = LocalPalette.current
    val emoji = when {
        round.percent >= 90 -> "🏆"
        round.percent >= 70 -> "🎉"
        round.percent >= 50 -> "👍"
        else -> "💪"
    }
    ScreenColumn {
        Text(emoji, fontSize = 64.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
        Text(
            "${round.correct} з ${round.size}",
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = p.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().testTag("quiz-score"),
        )
        Subtitle("Правильно ${round.percent}%", center = true)
        if (round.mistakes.isNotEmpty()) {
            SectionTitle("Повтори ці слова")
            Glass {
                round.mistakes.distinct().forEachIndexed { i, q ->
                    if (i > 0) RowDivider()
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(q.word.display, fontWeight = FontWeight.SemiBold, color = p.ink)
                            Text(q.word.ukrainian, color = p.muted, fontSize = 14.sp)
                        }
                        SpeakButton(q.word.display)
                    }
                }
            }
        }
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Інший тест", Modifier.weight(1f), onClick = onNew)
            PrimaryButton("Ще раз", Modifier.weight(1f), onClick = onAgain)
        }
    }
}
