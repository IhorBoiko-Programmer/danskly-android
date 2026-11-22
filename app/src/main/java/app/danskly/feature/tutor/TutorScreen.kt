package app.danskly.feature.tutor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.danskly.AppGraph
import app.danskly.core.ai.AiException
import app.danskly.core.ai.AiTasks
import app.danskly.core.ai.ChatMessage
import app.danskly.core.ai.Role
import app.danskly.ui.ScreenColumn
import app.danskly.ui.components.Chip
import app.danskly.ui.components.LargeTitle
import app.danskly.ui.components.Note
import app.danskly.ui.components.PrimaryButton
import app.danskly.ui.components.ReportAi
import app.danskly.ui.theme.LocalPalette
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/** Chat with the AI teacher. Answers stream in as they are written. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TutorScreen(graph: AppGraph) {
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val failed = remember { mutableStateListOf<Int>() }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    fun send(text: String) {
        if (text.isBlank() || busy) return
        messages += ChatMessage(Role.USER, text.trim())
        val history = AiTasks.chat(messages.toList())
        messages += ChatMessage(Role.ASSISTANT, "…")
        busy = true
        scope.launch {
            graph.ai.stream(history)
                .catch { e ->
                    failed += messages.lastIndex
                    messages[messages.lastIndex] = ChatMessage(Role.ASSISTANT, (e as? AiException)?.message ?: e.message.orEmpty())
                }
                .collect { messages[messages.lastIndex] = ChatMessage(Role.ASSISTANT, it) }
            busy = false
        }
    }

    ScreenColumn {
        LargeTitle("ШІ-тренер")
        if (!graph.isAiConfigured) {
            Note("ШІ-тренер запрацює, коли в Налаштуваннях буде вказано сервер ШІ.")
            return@ScreenColumn
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
            AiTasks.chatStarters.forEach { (label, prompt) -> Chip(label) { send(prompt) } }
        }
        messages.forEachIndexed { i, m ->
            val mine = m.role == Role.USER
            Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
                val shape = RoundedCornerShape(18.dp)
                Text(
                    m.content,
                    Modifier
                        .widthIn(max = 320.dp)
                        .clip(shape)
                        .background(if (mine) p.accent else p.glass)
                        .border(1.dp, if (mine) p.accent else p.line, shape)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .testTag("message:$i"),
                    color = if (mine) Color.White else p.ink,
                )
            }
            val finishedAnswer = !mine && !(busy && i == messages.lastIndex) && i !in failed
            if (finishedAnswer) ReportAi(m.content, graph.ai)
        }
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            placeholder = { Text("Напиши повідомлення…") },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag("chat-input"),
        )
        PrimaryButton("Надіслати", Modifier.fillMaxWidth().padding(top = 8.dp), enabled = !busy) {
            send(input)
            input = ""
        }
    }
}
