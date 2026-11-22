package app.danskly.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.sp
import app.danskly.core.ai.AiClient
import app.danskly.core.ai.AiException
import kotlinx.coroutines.launch

/** Why a user reports an AI answer. [wire] is what the server stores. */
enum class ReportReason(val wire: String, val label: String) {
    OFFENSIVE("offensive", "Образливий або неприйнятний вміст"),
    WRONG("wrong", "Неправильна інформація"),
}

/**
 * A small "report" link under AI-generated text. Google Play requires apps with generative AI
 * to let users flag offensive content without leaving the app.
 */
@Composable
fun ReportAi(content: String, ai: AiClient, modifier: Modifier = Modifier) {
    val p = app.danskly.ui.theme.LocalPalette.current
    val scope = rememberCoroutineScope()
    var choosing by remember { mutableStateOf(false) }
    var status by remember(content) { mutableStateOf<String?>(null) }

    val shown = status
    if (shown != null) {
        Text(shown, color = p.muted, fontSize = 13.sp, modifier = modifier.testTag("report-status"))
    } else {
        TextButton(onClick = { choosing = true }, modifier = modifier.testTag("report")) {
            Text("⚑ Поскаржитися на відповідь", color = p.muted, fontSize = 13.sp)
        }
    }

    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text("Що не так із відповіддю?") },
            text = { Text("Ми перевіримо цю відповідь ШІ. Разом зі скаргою надсилається лише її текст.") },
            confirmButton = {
                ReportReason.entries.forEach { reason ->
                    TextButton(onClick = {
                        choosing = false
                        status = "Надсилаю…"
                        scope.launch {
                            status = try {
                                ai.report(content, reason.wire)
                                "Дякуємо! Скаргу надіслано."
                            } catch (e: AiException) {
                                "Не вдалося надіслати: ${e.message}"
                            }
                        }
                    }) { Text(reason.label) }
                }
            },
            dismissButton = { TextButton(onClick = { choosing = false }) { Text("Скасувати") } },
        )
    }
}
