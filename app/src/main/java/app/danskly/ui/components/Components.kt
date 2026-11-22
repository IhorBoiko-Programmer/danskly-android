package app.danskly.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.danskly.R
import app.danskly.core.speech.Speaker
import app.danskly.core.text.TextTools
import app.danskly.ui.theme.LocalPalette

/** The speaker used by every "listen" button. Provided once at the top of the app. */
val LocalSpeaker = staticCompositionLocalOf<Speaker> { error("Speaker not provided") }

/** Soft coloured blobs behind the frosted-glass surfaces. */
@Composable
fun AppBackground(content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier
            .fillMaxSize()
            .background(p.background)
            .drawBehind {
                drawCircle(Brush.radialGradient(listOf(p.blobWarm, Color.Transparent), Offset(size.width * 0.1f, 0f), size.width * 0.9f), size.width * 0.9f, Offset(size.width * 0.1f, 0f))
                drawCircle(Brush.radialGradient(listOf(p.blobCool, Color.Transparent), Offset(size.width, size.height * 0.3f), size.width), size.width, Offset(size.width, size.height * 0.3f))
            },
    ) { content() }
}

@Composable
fun TopBar(title: String, showBack: Boolean, onBack: () -> Unit, showSettings: Boolean, onSettings: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(p.glass)
            .statusBarsPadding()
            .height(52.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(96.dp)) {
            if (showBack) {
                Text(
                    "‹ Назад",
                    color = p.accent,
                    fontSize = 17.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClickLabel = "Назад", role = Role.Button, onClick = onBack)
                        .padding(8.dp)
                        .testTag("back"),
                )
            }
        }
        Text(title, Modifier.weight(1f).testTag("title"), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = p.ink)
        Box(Modifier.width(96.dp), contentAlignment = Alignment.CenterEnd) {
            if (showSettings) {
                Icon(
                    painterResource(R.drawable.ic_settings),
                    contentDescription = "Налаштування",
                    tint = p.accent,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onSettings)
                        .padding(8.dp)
                        .size(24.dp)
                        .testTag("settings"),
                )
            }
        }
    }
}

/** A frosted-glass card. */
@Composable
fun Glass(modifier: Modifier = Modifier, radius: Dp = 18.dp, padding: Dp = 0.dp, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(radius)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(p.glass)
            .border(1.dp, p.line, shape)
            .padding(padding),
        content = content,
    )
}

@Composable
fun LargeTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(top = 8.dp, bottom = 4.dp), fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, color = LocalPalette.current.ink)
}

@Composable
fun Subtitle(text: String, modifier: Modifier = Modifier, center: Boolean = false) {
    Text(text, modifier.fillMaxWidth().padding(bottom = 12.dp), color = LocalPalette.current.muted, textAlign = if (center) TextAlign.Center else TextAlign.Start)
}

@Composable
fun SectionTitle(text: String) {
    Text(text, Modifier.padding(top = 18.dp, bottom = 8.dp), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = LocalPalette.current.ink)
}

@Composable
fun Note(text: String, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp).height(IntrinsicSize.Min).clip(RoundedCornerShape(12.dp)).background(p.glass)) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(p.accent))
        Text(text, Modifier.padding(12.dp), color = p.ink)
    }
}

/** A row in a grouped list: coloured icon, title, optional subtitle and a chevron. */
@Composable
fun ListRow(icon: String, title: String, subtitle: String? = null, color: Color = Color(0xFF8E8E93), trailing: String = "›", onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = title, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("row:$title"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(color), contentAlignment = Alignment.Center) {
            Text(icon, fontSize = 20.sp)
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = p.ink)
            if (subtitle != null) Text(subtitle, color = p.muted, fontSize = 13.sp)
        }
        Text(trailing, color = p.muted, fontSize = 22.sp)
    }
}

/** Thin separator between list rows. */
@Composable
fun RowDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(LocalPalette.current.line))
}

/** Lists rows inside one glass card with separators. */
@Composable
fun <T> GroupedList(items: List<T>, row: @Composable (T) -> Unit) {
    Glass {
        items.forEachIndexed { i, item ->
            if (i > 0) RowDivider()
            row(item)
        }
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    AppButton(text, modifier, enabled, primary = true, onClick = onClick)
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, color: Color? = null, onClick: () -> Unit) {
    AppButton(text, modifier, enabled, primary = false, textColor = color, onClick = onClick)
}

@Composable
private fun AppButton(text: String, modifier: Modifier, enabled: Boolean, primary: Boolean, textColor: Color? = null, onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(14.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "press")
    Box(
        modifier
            .scale(scale)
            .clip(shape)
            .background(if (primary) p.accent else p.glass)
            .border(1.dp, if (primary) p.accent else p.line, shape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(14.dp)
            .semantics { contentDescription = text },
        contentAlignment = Alignment.Center,
    ) {
        val color = when {
            primary -> Color.White
            textColor != null -> textColor
            else -> p.ink
        }
        Text(text, color = color.copy(alpha = if (enabled) 1f else 0.45f), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

/** A pill that can be selected, e.g. quiz modes or exercise presets. */
@Composable
fun Chip(text: String, selected: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(50)
    Text(
        text,
        modifier
            .clip(shape)
            .background(if (selected) p.ink else p.glass)
            .border(1.dp, if (selected) p.ink else p.line, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        color = if (selected) p.background else p.muted,
        fontSize = 14.sp,
    )
}

/** Segmented control with equal-width options. */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(12.dp)).background(p.line).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Text(
                label,
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (on) p.ink else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(value) }
                    .semantics { this.selected = on }
                    .padding(vertical = 10.dp),
                color = if (on) p.background else p.ink,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                fontSize = 14.sp,
            )
        }
    }
}

/** Round "listen" button. With [slow] it shows a turtle and speaks slower. */
@Composable
fun SpeakButton(text: String, big: Boolean = false, slow: Boolean = false) {
    val p = LocalPalette.current
    val speaker = LocalSpeaker.current
    val clean = TextTools.forSpeech(text)
    val size = when {
        big && !slow -> 60.dp
        big -> 46.dp
        else -> 36.dp
    }
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(p.accent.copy(alpha = 0.12f))
            .clickable(onClickLabel = if (slow) "Повільно" else "Прослухати", role = Role.Button) { speaker.speak(text, slow) }
            .semantics { contentDescription = (if (slow) "Повільно: " else "Прослухати: ") + clean },
        contentAlignment = Alignment.Center,
    ) {
        if (slow) {
            Text("🐢", fontSize = if (big) 22.sp else 16.sp)
        } else {
            Icon(painterResource(R.drawable.ic_speaker), contentDescription = null, tint = p.accent, modifier = Modifier.size(if (big) 30.dp else 20.dp))
        }
    }
}

/** Normal and slow listen buttons side by side. */
@Composable
fun SpeakPair(text: String, big: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        SpeakButton(text, big)
        SpeakButton(text, big, slow = true)
    }
}

@Composable
fun ProgressLine(fraction: Float, start: Color, end: Color, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Box(modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)).background(p.line)) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                .height(7.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Brush.horizontalGradient(listOf(start, end))),
        )
    }
}

/** Text where *marked* parts are shown bold in the accent colour. */
@Composable
fun HighlightedText(text: String, style: TextStyle = TextStyle.Default, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Text(highlight(text, p.accent), modifier, style = style, color = p.ink)
}

fun highlight(text: String, accent: Color): AnnotatedString = buildAnnotatedString {
    TextTools.highlights(text).forEach { seg ->
        if (seg.highlighted) withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold)) { append(seg.text) } else append(seg.text)
    }
}

/** A Danish example with its translation, a red bar on the left and listen buttons. */
@Composable
fun ExampleRow(danish: String, ukrainian: String, tag: String? = null, slowButton: Boolean = false) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(p.accent))
        Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
            if (tag != null) Text(tag, color = p.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            HighlightedText(danish, TextStyle(fontWeight = FontWeight.SemiBold))
            Text(ukrainian, color = p.muted, fontSize = 14.sp)
        }
        if (slowButton) SpeakPair(danish) else SpeakButton(danish)
    }
}

/** Buttons that type æ, ø and å, which are missing on Ukrainian keyboards. */
@Composable
fun DanishLetters(onLetter: (String) -> Unit) {
    Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("æ", "ø", "å").forEach { letter -> Chip(letter, modifier = Modifier.widthIn(min = 48.dp)) { onLetter(letter) } }
    }
}
