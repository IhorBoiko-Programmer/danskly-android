package app.danskly.feature.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.danskly.AppGraph
import app.danskly.R
import app.danskly.core.content.AppRelease
import app.danskly.core.learning.LearningRepository
import app.danskly.core.navigation.Route
import app.danskly.ui.ScreenColumn
import app.danskly.ui.components.Glass
import app.danskly.ui.components.PrimaryButton
import app.danskly.ui.components.ProgressLine
import app.danskly.ui.components.SecondaryButton
import app.danskly.ui.theme.LocalPalette
import app.danskly.ui.theme.TileColors
import java.time.LocalTime

/** Greeting in Danish and Ukrainian for the time of day. */
fun greetingFor(hour: Int): Pair<String, String> = when {
    hour < 10 -> "Godmorgen" to "доброго ранку"
    hour < 18 -> "Goddag" to "добрий день"
    else -> "Godaften" to "добрий вечір"
}

@Composable
fun HomeScreen(graph: AppGraph, hour: Int = LocalTime.now().hour) {
    val repo = graph.repository
    val progress by repo.progress.collectAsState()
    val p = LocalPalette.current
    val content = repo.content
    val known = repo.allWords.count { progress.isKnown(it) }
    val log = repo.todayLog()
    val due = repo.dueWords().size
    val fresh = repo.newWordsToday().size
    val (da, uk) = greetingFor(hour)
    val open: (Route) -> Unit = { graph.navigator.open(it) }

    ScreenColumn {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = p.accent, fontWeight = FontWeight.SemiBold)) { append("$da! ") }
                withStyle(SpanStyle(color = p.muted)) { append("· $uk") }
            },
        )
        Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(Color(0xFFFF6B81), p.accent, Color(0xFF96061F))))) {
                Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(48.dp).scale(1.5f))
            }
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = p.ink)) { append("Dansk") }
                    withStyle(SpanStyle(color = p.accent)) { append("ly") }
                },
                Modifier.padding(start = 12.dp),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        val newApp by graph.updates.newApp.collectAsState()
        newApp?.let { release -> NewVersionCard(release, onLater = graph.updates::dismissNewApp) }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val todaySubtitle = if (due + fresh > 0) "$due на повторення · $fresh нових" else "Повторення на сьогодні виконано"
            WideTile("☀️", "Сьогодні", todaySubtitle, TileColors.today, onClick = { open(Route.Today) }) {
                ProgressLine(log.actions / LearningRepository.DAILY_GOAL.toFloat(), TileColors.today.first, TileColors.today.second, Modifier.padding(top = 8.dp, bottom = 4.dp))
                val streak = if (progress.streak > 0) " · серія ${progress.streak} дн." else ""
                Text("${log.actions} з ${LearningRepository.DAILY_GOAL} дій$streak", color = p.muted, fontSize = 13.sp)
            }
            WideTile("📚", "Слова", "${repo.allWords.size} слів · ${repo.topics.size} тем", TileColors.words, onClick = { open(Route.Topics) }) {
                ProgressLine(known / repo.allWords.size.toFloat(), TileColors.words.first, TileColors.words.second, Modifier.padding(top = 8.dp, bottom = 4.dp))
                Text("Вивчено $known", color = p.muted, fontSize = 13.sp)
            }
            TileRow(
                Tile("✏️", "Завдання", "7 типів вправ", TileColors.exercises) { open(Route.Exercises) },
                Tile("🎧", "Читання й слухання", "${content.reading.size} текстів як на PD3", TileColors.reading) { open(Route.ReadingList) },
            )
            TileRow(
                Tile("🧩", "Граматика", "${content.ruleCount} правил", TileColors.grammar) { open(Route.Grammar) },
                Tile("🔊", "Вимова", "Алфавіт і звуки", TileColors.pronunciation) { open(Route.Pronunciation) },
            )
            TileRow(
                Tile("✍️", "Письмо", "${content.writingTasks.size} типів текстів", TileColors.writing) { open(Route.WritingList) },
                Tile("🎯", "Тест", "Перевір себе", TileColors.quiz) { open(Route.Quiz(null)) },
            )
            WideTile("✨", "ШІ-тренер", "Розмова, вправи, перевірка тексту", TileColors.tutor, onClick = { open(Route.Tutor) })
        }
    }
}

private data class Tile(val icon: String, val title: String, val subtitle: String, val colors: Pair<Color, Color>, val onClick: () -> Unit)

@Composable
private fun TileRow(left: Tile, right: Tile) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(left, right).forEach { t ->
            Box(Modifier.weight(1f)) {
                TileCard(t.title, t.colors, wide = false, onClick = t.onClick) { pressed ->
                    Column {
                        TileIcon(t.icon, t.colors, pressed, size = 64)
                        Text(t.title, Modifier.padding(top = 12.dp), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = LocalPalette.current.ink)
                        Text(t.subtitle, color = LocalPalette.current.muted, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun WideTile(icon: String, title: String, subtitle: String, colors: Pair<Color, Color>, onClick: () -> Unit, extra: @Composable () -> Unit = {}) {
    TileCard(title, colors, wide = true, onClick = onClick) { pressed ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            TileIcon(icon, colors, pressed, size = 74)
            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = LocalPalette.current.ink)
                Text(subtitle, color = LocalPalette.current.muted, fontSize = 13.sp)
                extra()
            }
        }
    }
}

/** A big tile that shrinks a little while pressed, with a springy bounce back. */
@Composable
private fun TileCard(label: String, colors: Pair<Color, Color>, wide: Boolean, onClick: () -> Unit, content: @Composable (Boolean) -> Unit) {
    val p = LocalPalette.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "tile")
    val shape = RoundedCornerShape(26.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (wide) 0.dp else 160.dp)
            .scale(scale)
            .clip(shape)
            .background(Brush.linearGradient(listOf(p.glass, p.glass, colors.second.copy(alpha = 0.18f))))
            .border(1.dp, p.line, shape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(16.dp)
            .testTag("tile:$label"),
    ) { content(pressed) }
}

/** Gradient app-style icon that grows and tilts while its tile is pressed. */
@Composable
private fun TileIcon(icon: String, colors: Pair<Color, Color>, pressed: Boolean, size: Int) {
    val scale by animateFloatAsState(if (pressed) 1.14f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "icon")
    val tilt by animateFloatAsState(if (pressed) -8f else 0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "tilt")
    Box(
        Modifier
            .size(size.dp)
            .scale(scale)
            .rotate(tilt)
            .clip(RoundedCornerShape((size * 0.29).dp))
            .background(Brush.linearGradient(listOf(colors.second, colors.first))),
        contentAlignment = Alignment.Center,
    ) {
        Text(icon, fontSize = (size * 0.48).sp)
    }
}

/** Shown on the home screen when a newer build of the app is published (builds outside Google Play). */
@Composable
private fun NewVersionCard(release: AppRelease, onLater: () -> Unit) {
    val p = LocalPalette.current
    val uri = LocalUriHandler.current
    Glass(Modifier.padding(bottom = 12.dp).testTag("new-version"), padding = 16.dp) {
        Text("🎉 Доступна нова версія ${release.versionName}", fontWeight = FontWeight.Bold, color = p.ink)
        Text("Завантаж і встанови її поверх цієї. Прогрес збережеться.", color = p.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Пізніше", Modifier.weight(1f), onClick = onLater)
            PrimaryButton("Оновити", Modifier.weight(1f)) { uri.openUri(release.url) }
        }
    }
}
