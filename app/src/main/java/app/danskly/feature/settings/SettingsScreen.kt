package app.danskly.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.danskly.AppGraph
import app.danskly.core.ai.AiException
import app.danskly.core.ai.AiTasks
import app.danskly.core.ai.ChatMessage
import app.danskly.core.ai.Role
import app.danskly.core.model.VoiceGender
import app.danskly.core.speech.Voice
import app.danskly.core.speech.VoiceSelector
import app.danskly.ui.ScreenColumn
import app.danskly.ui.components.Chip
import app.danskly.ui.components.Glass
import app.danskly.ui.components.GroupedList
import app.danskly.ui.components.LargeTitle
import app.danskly.ui.components.ListRow
import app.danskly.ui.components.LocalSpeaker
import app.danskly.ui.components.Note
import app.danskly.ui.components.PrimaryButton
import app.danskly.ui.components.RowDivider
import app.danskly.ui.components.SecondaryButton
import app.danskly.ui.components.SectionTitle
import app.danskly.ui.theme.LocalPalette
import java.util.Locale
import kotlinx.coroutines.launch

const val VOICE_HELP =
    "Найприродніше звучать покращені голоси. На Android: Налаштування → Мова → Синтез мовлення → Google → встанови данську мову " +
        "й обери голос високої якості. Після встановлення повернись сюди."

private val RATE_PRESETS = listOf("Повільно" to 0.6f, "Нормально" to 0.95f, "Швидко" to 1.25f)

@Composable
fun SettingsScreen(graph: AppGraph) {
    val repo = graph.repository
    val progress by repo.progress.collectAsState()
    val voices by graph.speaker.voices.collectAsState()
    val speaker = LocalSpeaker.current
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    val s = progress.settings
    val chosen = VoiceSelector.choose(voices, s)
    var aiStatus by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    ScreenColumn {
        LargeTitle("Налаштування")

        SectionTitle("Голос")
        if (voices.isEmpty()) {
            Note("Данських голосів на телефоні не знайдено. $VOICE_HELP")
        } else {
            VoicePicker(voices, chosen, automatic = s.voiceName == null) { name ->
                repo.updateSettings { it.copy(voiceName = name) }
                speaker.speak(VoiceSelector.SAMPLE)
            }
        }
        Glass(Modifier.padding(top = 12.dp), padding = 16.dp) {
            LabeledValue("Швидкість", String.format(Locale.ROOT, "%.2f×", s.speechRate))
            Slider(
                value = s.speechRate,
                onValueChange = { v -> repo.updateSettings { it.copy(speechRate = Math.round(v * 20) / 20f) } },
                onValueChangeFinished = { speaker.speak(VoiceSelector.PREVIEW) },
                valueRange = VoiceSelector.MIN_RATE..VoiceSelector.MAX_RATE,
                colors = SliderDefaults.colors(thumbColor = p.accent, activeTrackColor = p.accent),
                modifier = Modifier.testTag("rate-slider"),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RATE_PRESETS.forEach { (label, rate) ->
                    Chip(label, selected = Math.abs(s.speechRate - rate) < RATE_EPSILON) {
                        repo.updateSettings { it.copy(speechRate = rate) }
                        speaker.speak(VoiceSelector.PREVIEW)
                    }
                }
            }
            LabeledValue("Гучність", "${Math.round(s.volume * 100)}%", Modifier.padding(top = 12.dp))
            Slider(
                value = s.volume,
                onValueChange = { v -> repo.updateSettings { it.copy(volume = Math.round(v * 20) / 20f) } },
                onValueChangeFinished = { speaker.speak(VoiceSelector.PREVIEW) },
                colors = SliderDefaults.colors(thumbColor = p.accent, activeTrackColor = p.accent),
                modifier = Modifier.testTag("volume-slider"),
            )
        }
        PrimaryButton("Перевірити голос", Modifier.fillMaxWidth().padding(top = 12.dp)) { speaker.speak(VoiceSelector.SAMPLE) }
        Note(VOICE_HELP)

        SectionTitle("ШІ")
        Glass(padding = 16.dp) {
            OutlinedTextField(
                value = s.serverUrl,
                onValueChange = { v -> repo.updateSettings { it.copy(serverUrl = v.trim()) } },
                label = { Text("Адреса сервера ШІ") },
                placeholder = { Text(graph.builtInServerUrl.ifBlank { "https://danskly.….workers.dev" }) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("server-url"),
            )
            OutlinedTextField(
                value = s.accessCode,
                onValueChange = { v -> repo.updateSettings { it.copy(accessCode = v.trim()) } },
                label = { Text("Код доступу") },
                placeholder = { Text("Якщо адміністратор задав код") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("access-code"),
            )
            SecondaryButton("Перевірити з’єднання", Modifier.fillMaxWidth().padding(top = 10.dp)) {
                aiStatus = "Перевіряю…"
                scope.launch {
                    aiStatus = try {
                        val health = graph.ai.health()
                        if (health.keys == 0) {
                            "Сервер працює, але ключі ще не додані (секрет OPENCODE_KEYS)."
                        } else {
                            val reply = graph.ai.complete(listOf(ChatMessage(Role.USER, AiTasks.PING_PROMPT)))
                            "✓ Працює. Ключів: ${health.keys}. Модель ${graph.ai.lastModel ?: "?"} відповіла: «${reply.text.trim().take(PING_PREVIEW)}»."
                        }
                    } catch (e: AiException) {
                        "✗ ${e.message}"
                    }
                }
            }
            aiStatus?.let { Text(it, color = p.ink, modifier = Modifier.padding(top = 8.dp).testTag("ai-status")) }
        }

        SectionTitle("Навчання")
        GroupedList(listOf(0, 1, 2)) { row ->
            when (row) {
                0 -> ListRow("🔁", "Автоозвучення в картках", if (s.autoSpeak) "Увімкнено" else "Вимкнено", trailing = if (s.autoSpeak) "✓" else "") {
                    repo.updateSettings { it.copy(autoSpeak = !it.autoSpeak) }
                }
                1 -> ListRow("🧹", "Очистити збережені приклади ШІ", "Збережено для ${progress.wordDetails.size} слів", trailing = "") { repo.clearAiCache() }
                else -> ListRow("⚠️", "Скинути прогрес", "Рівні слів, серія днів, результати тестів", color = p.bad, trailing = "") { confirmReset = true }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Скинути прогрес?") },
            text = { Text("Рівні слів, серія днів і результати тестів буде видалено. Свої слова й налаштування залишаться.") },
            confirmButton = {
                TextButton(onClick = {
                    repo.resetProgress()
                    confirmReset = false
                }) { Text("Скинути", color = p.bad) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Скасувати") } },
        )
    }
}

/** The current voice; tapping it opens the list of all Danish voices on the phone. */
@Composable
private fun VoicePicker(voices: List<Voice>, chosen: Voice?, automatic: Boolean, onPick: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Glass {
        ListRow("🔊", chosen?.name.orEmpty(), (if (automatic) "Автоматично · " else "") + describe(chosen), trailing = if (open) "▴" else "▾") {
            open = !open
        }
        if (open) {
            RowDivider()
            ListRow("✨", "Автоматично", "Найкращий голос на телефоні", trailing = if (automatic) "✓" else "") {
                open = false
                onPick(null)
            }
            voices.forEach { v ->
                RowDivider()
                ListRow("🔈", v.name, describe(v), trailing = if (!automatic && v == chosen) "✓" else "") {
                    open = false
                    onPick(v.name)
                }
            }
        }
    }
}

private fun describe(v: Voice?): String {
    if (v == null) return ""
    val gender = when (VoiceSelector.genderOf(v)) {
        VoiceGender.FEMALE -> "жіночий"
        VoiceGender.MALE -> "чоловічий"
        null -> "голос"
    }
    return gender + if (VoiceSelector.score(v) >= HIGH_QUALITY) " · якісний" else ""
}

@Composable
private fun LabeledValue(label: String, value: String, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = FontWeight.SemiBold, color = p.ink)
        Text(value, fontWeight = FontWeight.Bold, color = p.ink)
    }
}

private const val HIGH_QUALITY = 10
private const val RATE_EPSILON = 0.01f
private const val PING_PREVIEW = 40
