package app.danskly.feature.exercises

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.danskly.AppGraph
import app.danskly.core.exercise.ChoiceQuestion
import app.danskly.core.exercise.ExerciseSession
import app.danskly.core.exercise.ExerciseType
import app.danskly.core.exercise.Question
import app.danskly.core.exercise.SessionState
import app.danskly.core.exercise.TilesQuestion
import app.danskly.core.exercise.TypingQuestion
import app.danskly.core.exercise.Verdict
import app.danskly.core.model.DailyTask
import app.danskly.core.navigation.Route
import app.danskly.ui.ScreenColumn
import app.danskly.ui.components.DanishLetters
import app.danskly.ui.components.Glass
import app.danskly.ui.components.GroupedList
import app.danskly.ui.components.LargeTitle
import app.danskly.ui.components.ListRow
import app.danskly.ui.components.LocalSpeaker
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

@Composable
fun ExercisesScreen(graph: AppGraph) {
    val types = remember { graph.exerciseGenerator().availableTypes() }
    ScreenColumn {
        LargeTitle("Завдання")
        Subtitle("Кожна сесія — ${ExerciseSession.DEFAULT_LENGTH} вправ")
        GroupedList(types.withIndex().toList()) { (i, t) ->
            ListRow(t.icon, t.title, t.subtitle, TileColors.list[i % TileColors.list.size]) { graph.navigator.open(Route.Exercise(t)) }
        }
    }
}

/** How an answer option looks: open to tap, picked (can still change), or graded. */
enum class OptionState { OPEN, SELECTED, RIGHT, WRONG, DIMMED }

/** An answer option. After answering it turns green (right), red (chosen wrong) or fades. */
@Composable
fun OptionButton(text: String, state: OptionState, onClick: () -> Unit) {
    val p = LocalPalette.current
    val color = when (state) {
        OptionState.RIGHT -> p.ok
        OptionState.WRONG -> p.bad
        OptionState.SELECTED -> p.ink
        else -> p.line
    }
    val shape = RoundedCornerShape(14.dp)
    Text(
        text,
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .alpha(if (state == OptionState.DIMMED) 0.5f else 1f)
            .clip(shape)
            .background(p.glass)
            .border(1.5.dp, color, shape)
            .clickable(enabled = state == OptionState.OPEN || state == OptionState.SELECTED, role = Role.Button, onClick = onClick)
            .semantics { selected = state == OptionState.SELECTED }
            .padding(14.dp)
            .testTag("option:$text"),
        color = if (state == OptionState.RIGHT || state == OptionState.WRONG) color else p.ink,
        fontWeight = FontWeight.SemiBold,
    )
}

/** A session of 10 exercises of one type, then a results screen. */
@Composable
fun ExerciseScreen(graph: AppGraph, type: ExerciseType) {
    val repo = graph.repository
    var session by remember { mutableStateOf(ExerciseSession(type, graph.exerciseGenerator())) }
    // ExerciseSession is plain Kotlin; the UI draws an immutable snapshot that is refreshed after every change.
    var state by remember { mutableStateOf(session.state()) }
    val p = LocalPalette.current

    ScreenColumn {
        if (state.isFinished) {
            LaunchedEffect(session) { repo.markDone(DailyTask.EXERCISE) }
            Results(state, onAgain = {
                session = ExerciseSession(type, graph.exerciseGenerator())
                state = session.state()
            }, onBack = { graph.navigator.back() })
            return@ScreenColumn
        }
        ProgressLine(state.index / state.length.toFloat(), p.accent, p.accent.copy(alpha = 0.6f))
        Subtitle("${state.index + 1} з ${state.length} · ${state.question.tag}", Modifier.padding(top = 6.dp), center = true)
        val record: (Verdict) -> Unit = { v ->
            val q = state.question
            if (q is TypingQuestion && q.word != null) repo.grade(q.word, v.correct) else repo.recordAction()
            state = session.state()
        }
        // Chosen with a plain `when` statement: an exhaustive statement needs no fallback branch.
        val exercise: @Composable () -> Unit
        when (val q = state.question) {
            is ChoiceQuestion -> exercise = { ChoiceExercise(q, state.verdict) { record(session.answerChoice(it)) } }
            is TilesQuestion -> exercise = { TilesExercise(q, state.verdict != null) { record(session.answerTiles(it)) } }
            is TypingQuestion -> exercise = { TypingExercise(q, state.verdict != null) { record(session.answerTyped(it)) } }
        }
        key(session, state.index) { exercise() }
        state.verdict?.let { v ->
            Feedback(v, state.question)
            PrimaryButton(if (state.isLast) "Результат" else "Далі", Modifier.fillMaxWidth().padding(top = 4.dp)) {
                session.next()
                state = session.state()
            }
        }
    }
}

@Composable
private fun ChoiceExercise(q: ChoiceQuestion, verdict: Verdict?, onAnswer: (String) -> Unit) {
    val p = LocalPalette.current
    var chosen by remember { mutableStateOf<String?>(null) }
    Text(q.prompt, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = p.ink, modifier = Modifier.padding(vertical = 8.dp))
    q.hint?.let { Text(it, color = p.muted, modifier = Modifier.padding(bottom = 10.dp)) }
    q.options.forEach { option ->
        val state = when {
            verdict == null -> OptionState.OPEN
            option == q.answer -> OptionState.RIGHT
            option == chosen -> OptionState.WRONG
            else -> OptionState.DIMMED
        }
        OptionButton(option, state) {
            chosen = option
            onAnswer(option)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TilesExercise(q: TilesQuestion, answered: Boolean, onAnswer: (List<String>) -> Unit) {
    val p = LocalPalette.current
    val picked = remember { mutableStateListOf<Int>() }
    Text("Склади речення данською", color = p.muted)
    Text(q.prompt, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = p.ink, modifier = Modifier.padding(vertical = 8.dp))
    FlowRow(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 8.dp).testTag("tile-slot"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        picked.toList().forEachIndexed { pos, index -> WordTile(q.tiles[index], enabled = !answered, tag = "picked:$pos") { picked.removeAt(pos) } }
    }
    RowDivider()
    FlowRow(Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag("tile-bank"), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        q.tiles.forEachIndexed { index, tile ->
            val used = index in picked
            WordTile(tile, enabled = !answered && !used, faded = used, tag = "bank:$index") { picked.add(index) }
        }
    }
    PrimaryButton("Перевірити", Modifier.fillMaxWidth(), enabled = picked.isNotEmpty() && !answered) { onAnswer(picked.map { q.tiles[it] }) }
}

@Composable
private fun WordTile(text: String, enabled: Boolean, faded: Boolean = false, tag: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(12.dp)
    Text(
        text,
        Modifier
            .alpha(if (faded) 0.25f else 1f)
            .clip(shape)
            .background(p.glass)
            .border(1.dp, p.line, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag(tag),
        fontWeight = FontWeight.SemiBold,
        color = p.ink,
    )
}

@Composable
private fun TypingExercise(q: TypingQuestion, answered: Boolean, onAnswer: (String) -> Unit) {
    val p = LocalPalette.current
    val speaker = LocalSpeaker.current
    var value by remember { mutableStateOf(TextFieldValue("")) }
    if (q.audio != null) {
        LaunchedEffect(q) { speaker.speak(q.audio) }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 10.dp)) {
            SpeakPair(q.audio, big = true)
            Text(q.prompt, color = p.muted, modifier = Modifier.padding(start = 14.dp))
        }
    } else {
        Text(q.prompt, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = p.ink, modifier = Modifier.padding(vertical = 8.dp))
        q.hint?.let { Text(it, color = p.muted) }
    }
    val submit = { if (value.text.isNotBlank() && !answered) onAnswer(value.text) }
    OutlinedTextField(
        value = value,
        onValueChange = { value = it },
        enabled = !answered,
        singleLine = true,
        placeholder = { Text("Напиши данською…") },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp).testTag("answer-field"),
    )
    DanishLetters { letter ->
        val t = value.text
        value = TextFieldValue(t.substring(0, value.selection.min) + letter + t.substring(value.selection.max), TextRange(value.selection.min + 1))
    }
    PrimaryButton("Перевірити", Modifier.fillMaxWidth(), enabled = !answered) { submit() }
}

@Composable
private fun Feedback(v: Verdict, q: Question) {
    val p = LocalPalette.current
    val speaker = LocalSpeaker.current
    LaunchedEffect(v) { if (!(q is TypingQuestion && q.audio != null)) speaker.speak(q.speak) }
    val color = if (v.correct) p.ok else p.bad
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .clip(shape)
            .background(p.glass)
            .border(1.5.dp, color, shape)
            .padding(14.dp)
            .testTag("feedback"),
    ) {
        Text(if (v.correct) "Правильно!" else "Не зовсім.", color = color, fontWeight = FontWeight.Bold)
        v.note?.let { Text(it, color = p.ink, modifier = Modifier.padding(top = 6.dp)) }
        if (!v.correct) Text("Правильна відповідь: ${v.correctAnswer}", color = p.ink, modifier = Modifier.padding(top = 6.dp))
        q.explanation?.takeIf { it != v.correctAnswer }?.let { Text(it, color = p.muted, modifier = Modifier.padding(top = 6.dp)) }
        Row(Modifier.padding(top = 10.dp)) { SpeakPair(q.speak) }
    }
}

@Composable
private fun Results(session: SessionState, onAgain: () -> Unit, onBack: () -> Unit) {
    val p = LocalPalette.current
    val percent = session.score * 100 / session.length
    val (emoji, text) = when {
        percent >= 80 -> "🏆" to "Чудовий результат!"
        percent >= 50 -> "💪" to "Непогано, ще трохи практики."
        else -> "📚" to "Повтори правила й спробуй ще раз."
    }
    Text(emoji, Modifier.fillMaxWidth().padding(top = 24.dp), fontSize = 64.sp, textAlign = TextAlign.Center)
    Text("${session.score}/${session.length}", Modifier.fillMaxWidth().testTag("score"), fontSize = 52.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, color = p.ink)
    Subtitle(text, center = true)
    if (session.mistakes.isNotEmpty()) {
        SectionTitle("Помилки")
        Glass(padding = 12.dp) {
            session.mistakes.forEach { m ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(m.question, color = p.muted, fontSize = 14.sp)
                        Text(m.correctAnswer, color = p.ink, fontWeight = FontWeight.SemiBold)
                    }
                    SpeakButton(m.correctAnswer)
                }
            }
        }
    }
    Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SecondaryButton("До завдань", Modifier.weight(1f), onClick = onBack)
        PrimaryButton("Ще раз", Modifier.weight(1f), onClick = onAgain)
    }
}
