package app.danskly.feature.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.danskly.AppGraph
import app.danskly.core.exercise.ExerciseType
import app.danskly.core.learning.LearningRepository
import app.danskly.core.model.DailyTask
import app.danskly.core.model.Word
import app.danskly.core.navigation.Route
import app.danskly.ui.ScreenColumn
import app.danskly.ui.components.ExampleRow
import app.danskly.ui.components.Glass
import app.danskly.ui.components.GroupedList
import app.danskly.ui.components.LargeTitle
import app.danskly.ui.components.ListRow
import app.danskly.ui.components.LocalSpeaker
import app.danskly.ui.components.PrimaryButton
import app.danskly.ui.components.ProgressLine
import app.danskly.ui.components.SecondaryButton
import app.danskly.ui.components.SpeakPair
import app.danskly.ui.components.Subtitle
import app.danskly.ui.components.HighlightedText
import app.danskly.ui.theme.LocalPalette
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class TodayItem(val task: DailyTask, val icon: String, val title: String, val subtitle: String, val route: Route)

@Composable
fun TodayScreen(graph: AppGraph, date: LocalDate = LocalDate.now()) {
    val repo = graph.repository
    val progress by repo.progress.collectAsState()
    val p = LocalPalette.current
    val log = repo.todayLog()
    val due = repo.dueWords().size
    val fresh = repo.newWordsToday().size
    val (section, rule) = repo.ruleOfDay()
    val text = repo.textOfDay()
    val goal = LearningRepository.DAILY_GOAL
    val items = listOf(
        TodayItem(DailyTask.REVIEW, "🔁", "Повторити слова", if (due + fresh > 0) "$due на повторення · $fresh нових" else "На сьогодні все повторено", Route.Review),
        TodayItem(DailyTask.RULE, "🧩", "Правило дня", repo.content.grammar[section].rules[rule].title, Route.Rule(section, rule)),
        TodayItem(DailyTask.EXERCISE, "🎲", "Вправа дня", "10 завдань різних типів", Route.Exercise(ExerciseType.MIX)),
        TodayItem(DailyTask.READING, "📖", "Текст дня", repo.content.reading[text].title, Route.Reading(text)),
    )
    ScreenColumn {
        LargeTitle("Сьогодні")
        Subtitle(date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.forLanguageTag("uk"))))
        Row(Modifier.padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            GoalRing(log.actions / goal.toFloat())
            Column(Modifier.padding(start = 16.dp)) {
                Text("${log.actions} з $goal", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = p.ink)
                Text((if (log.actions >= goal) "Денну мету виконано!" else "дій до денної мети") + " · серія ${progress.streak} дн.", color = p.muted)
            }
        }
        GroupedList(items) { item ->
            val done = item.task in log.done
            ListRow(if (done) "✅" else item.icon, item.title, item.subtitle, if (done) p.ok else p.accent) { graph.navigator.open(item.route) }
        }
    }
}

/** Ring that fills up as the learner approaches the daily goal. */
@Composable
fun GoalRing(fraction: Float) {
    val p = LocalPalette.current
    Canvas(Modifier.size(64.dp).testTag("goal-ring")) {
        val stroke = 7.dp.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(p.line, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
        drawArc(p.accent, -90f, 360f * fraction.coerceIn(0f, 1f), false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

/** Spaced-repetition session: due words first, then today's new words. */
@Composable
fun ReviewScreen(graph: AppGraph) {
    val repo = graph.repository
    val p = LocalPalette.current
    val speaker = LocalSpeaker.current
    val autoSpeak = repo.progress.collectAsState().value.settings.autoSpeak
    val queue = remember { (repo.dueWords().shuffled(graph.random).take(MAX_DUE) + repo.newWordsToday()).toMutableList() }
    val total = remember { queue.size }
    var done by remember { mutableStateOf(0) }
    var flipped by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf(queue.firstOrNull()) }

    ScreenColumn {
        val word = current
        if (word == null) {
            if (total > 0) LaunchedEffect(Unit) { repo.markDone(DailyTask.REVIEW) }
            Text("🎉", Modifier.fillMaxWidth().padding(top = 24.dp), fontSize = 64.sp, textAlign = TextAlign.Center)
            LargeTitle(if (total > 0) "Готово!" else "Усе повторено", Modifier.fillMaxWidth())
            Subtitle(if (total > 0) "Ти пройшов $total слів. Наступні повторення з’являться завтра." else "Слів на повторення немає. Можеш узяти нову тему в розділі «Слова».", center = true)
            PrimaryButton("Зробити вправу дня", Modifier.fillMaxWidth()) { graph.navigator.open(Route.Exercise(ExerciseType.MIX)) }
            return@ScreenColumn
        }
        if (autoSpeak && !flipped) LaunchedEffect(word, done) { speaker.speak(word.display) }
        ProgressLine(done / total.toFloat(), p.accent, p.accent.copy(alpha = 0.6f))
        Subtitle("$done з $total" + if (word.danish !in repo.progress.value.cards) " · нове слово" else "", Modifier.padding(top = 6.dp), center = true)
        FlipCard(word, flipped) { flipped = !flipped }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center) { SpeakPair(word.display, big = true) }
        if (flipped) {
            SecondaryButton("Усі форми й приклади ›", Modifier.fillMaxWidth().padding(bottom = 8.dp)) { graph.navigator.open(Route.WordDetails(word.danish)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Ще раз", Modifier.weight(1f), enabled = flipped, color = p.accent) {
                repo.grade(word, correct = false)
                queue.add(queue.removeAt(0))
                flipped = false
                current = queue.first()
            }
            PrimaryButton("Знаю", Modifier.weight(1f), enabled = flipped) {
                repo.grade(word, correct = true)
                queue.removeAt(0)
                done++
                flipped = false
                current = queue.firstOrNull()
            }
        }
    }
}

/** Danish on the front, translation on the back. */
@Composable
fun FlipCard(word: Word, flipped: Boolean, onFlip: () -> Unit) {
    val p = LocalPalette.current
    Glass(
        Modifier
            .padding(top = 4.dp)
            .testTag("flip-card"),
        radius = 24.dp,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = "Перевернути картку", onClick = onFlip)
                .padding(vertical = 48.dp, horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (flipped) {
                    Text(word.ukrainian, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = p.ink, textAlign = TextAlign.Center)
                    Text(word.topic, color = p.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                } else {
                    Text(word.display, fontSize = 36.sp, fontWeight = FontWeight.Bold, color = p.ink, textAlign = TextAlign.Center)
                    Text("торкнись, щоб побачити переклад", color = p.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

/** One grammar rule, e.g. the rule of the day. */
@Composable
fun RuleScreen(graph: AppGraph, section: Int, rule: Int) {
    val repo = graph.repository
    val s = repo.content.grammar[section]
    val r = s.rules[rule]
    LaunchedEffect(section, rule) {
        if (repo.ruleOfDay() == section to rule && DailyTask.RULE !in repo.todayLog().done) repo.recordAction(DailyTask.RULE)
    }
    ScreenColumn {
        Subtitle(s.title, Modifier.padding(bottom = 0.dp))
        LargeTitle(r.title)
        Glass(padding = 16.dp) {
            HighlightedText(r.body, modifier = Modifier.padding(bottom = 8.dp))
            r.examples.forEach { ExampleRow(it.danish, it.ukrainian) }
        }
        SecondaryButton("Усі правила розділу «${s.title}»", Modifier.fillMaxWidth().padding(top = 12.dp)) { graph.navigator.open(Route.GrammarSection(section)) }
        PrimaryButton("Потренуватися: встав слово", Modifier.fillMaxWidth().padding(top = 10.dp)) { graph.navigator.open(Route.Exercise(ExerciseType.GAP)) }
    }
}

private const val MAX_DUE = 30
