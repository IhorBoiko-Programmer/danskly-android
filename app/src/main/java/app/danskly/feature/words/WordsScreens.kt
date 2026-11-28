package app.danskly.feature.words

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.danskly.AppGraph
import app.danskly.core.ai.AiException
import app.danskly.core.ai.AiTasks
import app.danskly.core.ai.ChatMessage
import app.danskly.core.ai.Role as ChatRole
import app.danskly.core.learning.AddWordResult
import app.danskly.core.learning.CustomWordFactory
import app.danskly.core.learning.LearningRepository
import app.danskly.core.learning.NewWordResult
import app.danskly.core.learning.WordKind
import app.danskly.core.model.Progress
import app.danskly.core.model.Topic
import app.danskly.core.model.Word
import app.danskly.core.navigation.Route
import app.danskly.feature.today.FlipCard
import app.danskly.ui.ScreenColumn
import app.danskly.ui.components.Chip
import app.danskly.ui.components.DanishLetters
import app.danskly.ui.components.GroupedList
import app.danskly.ui.components.LargeTitle
import app.danskly.ui.components.ListRow
import app.danskly.ui.components.LocalSpeaker
import app.danskly.ui.components.Note
import app.danskly.ui.components.PrimaryButton
import app.danskly.ui.components.RowDivider
import app.danskly.ui.components.SecondaryButton
import app.danskly.ui.components.SectionTitle
import app.danskly.ui.components.Segmented
import app.danskly.ui.components.SpeakButton
import app.danskly.ui.components.SpeakPair
import app.danskly.ui.components.Subtitle
import app.danskly.ui.theme.LocalPalette
import app.danskly.ui.theme.TileColors
import kotlinx.coroutines.launch

@Composable
fun TopicsScreen(graph: AppGraph) {
    val repo = graph.repository
    val progress by repo.progress.collectAsState()
    ScreenColumn {
        LargeTitle("Слова")
        Subtitle("${repo.allWords.size} слів у ${repo.topics.size} темах")
        val rows = listOf(
            Triple("➕", "Додати своє слово", "Слова з уроків чи з життя") to Route.AddWord(null),
            Triple("🗂️", "Усі слова", "${repo.allWords.size} слів") to Route.Topic(null),
        ) + repo.topTopics.map { t -> Triple(t.icon, t.name, topicProgress(repo.wordsIn(t.name), progress)) to Route.Topic(t.name) }
        GroupedList(rows.withIndex().toList()) { (i, row) ->
            val (icon, title, subtitle) = row.first
            ListRow(icon, title, subtitle, TileColors.list[i % TileColors.list.size]) { graph.navigator.open(row.second) }
        }
        repo.topicGroups.forEachIndexed { g, group ->
            SectionTitle(group.name)
            TopicGrid(group.topics, colorOffset = g) { topic, color ->
                TopicTile(topic, repo.wordsIn(topic.name), progress, color) { graph.navigator.open(Route.Topic(topic.name)) }
            }
        }
    }
}

private fun topicProgress(words: List<Word>, progress: Progress) =
    "${words.size} слів · вивчено ${words.count { progress.isKnown(it) }}"

/** Topics as square tiles, three in a row. */
@Composable
private fun TopicGrid(topics: List<Topic>, colorOffset: Int, tile: @Composable RowScope.(Topic, Color) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        topics.chunked(TOPICS_PER_ROW).forEachIndexed { r, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEachIndexed { c, topic ->
                    val color = TileColors.list[(colorOffset * 2 + r * TOPICS_PER_ROW + c) % TileColors.list.size]
                    tile(topic, color)
                }
                repeat(TOPICS_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun RowScope.TopicTile(topic: Topic, words: List<Word>, progress: Progress, color: Color, onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .weight(1f)
            .aspectRatio(1f)
            .clip(shape)
            .background(p.glass)
            .border(1.dp, p.line, shape)
            .clickable(onClickLabel = topic.name, role = Role.Button, onClick = onClick)
            .padding(8.dp)
            .testTag("topic:${topic.name}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(color), contentAlignment = Alignment.Center) {
            Text(topic.icon, fontSize = 26.sp)
        }
        Text(
            topic.name,
            Modifier.padding(top = 6.dp),
            color = p.ink,
            fontSize = 13.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text("${words.count { progress.isKnown(it) }}/${words.size}", color = p.muted, fontSize = 11.sp)
    }
}

private const val TOPICS_PER_ROW = 3

@Composable
fun TopicScreen(graph: AppGraph, topic: String?) {
    val repo = graph.repository
    val progress by repo.progress.collectAsState()
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val words = repo.wordsIn(topic)

    LazyColumn(Modifier.fillMaxSize().navigationBarsPadding().testTag("word-list"), contentPadding = PaddingValues(16.dp)) {
        item {
            LargeTitle(topic ?: "Усі слова")
            Subtitle("${words.size} слів · вивчено ${words.count { progress.isKnown(it) }}")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                PrimaryButton("Картки", Modifier.weight(1f)) { graph.navigator.open(Route.Cards(topic)) }
                SecondaryButton("Тест", Modifier.weight(1f)) { graph.navigator.open(Route.Quiz(topic)) }
            }
            SecondaryButton("➕ Додати своє слово", Modifier.fillMaxWidth().padding(bottom = 10.dp)) { graph.navigator.open(Route.AddWord(topic)) }
            if (topic != null && graph.isAiConfigured) {
                SecondaryButton("✨ Додати слова з ШІ", Modifier.fillMaxWidth().padding(bottom = 10.dp), enabled = !loading) {
                    loading = true
                    message = "Генерую слова…"
                    scope.launch {
                        message = try {
                            val reply = graph.ai.complete(listOf(ChatMessage(ChatRole.USER, AiTasks.newWordsPrompt(topic, words))), json = true)
                            val added = AiTasks.parseNewWords(reply.text, topic).count { repo.addCustomWord(it) is AddWordResult.Added }
                            if (added > 0) "Додано слів: $added" else "Нових слів не знайшлося, спробуй ще раз."
                        } catch (e: AiException) {
                            e.message
                        }
                        loading = false
                    }
                }
            }
            message?.let { Note(it) }
        }
        items(words, key = { it.danish }) { word ->
            WordRow(word, progress) { graph.navigator.open(Route.WordDetails(word.danish)) }
            RowDivider()
        }
    }
}

@Composable
private fun WordRow(word: Word, progress: Progress, onOpen: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(p.glass)
            .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)
            .testTag("word:${word.danish}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .clickable(onClickLabel = word.display, role = Role.Button, onClick = onOpen)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(word.display, fontWeight = FontWeight.SemiBold, color = if (progress.isKnown(word)) p.ok else p.ink)
            Text(word.ukrainian, Modifier.weight(1f).padding(horizontal = 12.dp), color = p.muted, textAlign = TextAlign.End)
            Text("›", color = p.muted, fontSize = 20.sp)
        }
        SpeakButton(word.display)
    }
}

/** Flash cards: words the learner knows less often come up more often. */
@Composable
fun CardsScreen(graph: AppGraph, topic: String?) {
    val repo = graph.repository
    val progress by repo.progress.collectAsState()
    val p = LocalPalette.current
    val speaker = LocalSpeaker.current
    val scope = rememberCoroutineScope()
    val pool = remember { repo.wordsIn(topic) }
    fun pick(previous: Word?): Word {
        val weighted = pool.filter { it != previous }.flatMap { w -> List(5 - progress.boxOf(w).coerceAtMost(4)) { w } }
        return (weighted.ifEmpty { pool }).random(graph.random)
    }
    var word by remember { mutableStateOf(pick(null)) }
    var flipped by remember { mutableStateOf(false) }
    var exampleError by remember { mutableStateOf<String?>(null) }
    var loadingExample by remember { mutableStateOf(false) }
    val auto = progress.settings.autoSpeak

    if (auto && !flipped) LaunchedEffect(word) { speaker.speak(word.display) }

    ScreenColumn {
        FlipCard(word, flipped) { flipped = !flipped }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            SpeakPair(word.display, big = true)
            Chip("Автоозвучення", selected = auto) {
                repo.updateSettings { it.copy(autoSpeak = !it.autoSpeak) }
                if (!auto) speaker.speak(word.display)
            }
        }
        if (flipped) {
            val example = progress.exampleSentences[word.danish]
            when {
                example != null -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(example.danish, fontWeight = FontWeight.SemiBold, color = p.ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        Text(example.ukrainian, color = p.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                    SpeakButton(example.danish)
                }
                graph.isAiConfigured -> SecondaryButton(if (loadingExample) "Генерую…" else "Показати приклад (ШІ)", Modifier.fillMaxWidth(), enabled = !loadingExample) {
                    loadingExample = true
                    exampleError = null
                    val forWord = word
                    scope.launch {
                        try {
                            val reply = graph.ai.complete(listOf(ChatMessage(ChatRole.USER, AiTasks.examplePrompt(forWord))), json = true)
                            repo.cacheExample(forWord, AiTasks.parseExample(reply.text))
                        } catch (e: AiException) {
                            exampleError = e.message
                        }
                        loadingExample = false
                    }
                }
            }
            exampleError?.let { Note(it) }
            SecondaryButton("Усі форми й приклади ›", Modifier.fillMaxWidth().padding(vertical = 8.dp)) { graph.navigator.open(Route.WordDetails(word.danish)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
            val next = { correct: Boolean ->
                repo.grade(word, correct)
                word = pick(word)
                flipped = false
                exampleError = null
            }
            SecondaryButton("Ще раз", Modifier.weight(1f), enabled = flipped, color = p.accent) { next(false) }
            PrimaryButton("Знаю", Modifier.weight(1f), enabled = flipped) { next(true) }
        }
        Subtitle("Рівень слова: ${progress.boxOf(word)}/4 · вивчено ${pool.count { progress.isKnown(it) }} з ${pool.size}", Modifier.padding(top = 12.dp), center = true)
    }
}

/** Form for adding the learner's own word. */
@Composable
fun AddWordScreen(graph: AppGraph, topic: String?) {
    val repo = graph.repository
    val p = LocalPalette.current
    var danish by remember { mutableStateOf(TextFieldValue("")) }
    var ukrainian by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(WordKind.OTHER) }
    var chosenTopic by remember { mutableStateOf(topic ?: LearningRepository.MY_WORDS) }
    var menuOpen by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var duplicate by remember { mutableStateOf<Word?>(null) }
    val topics = listOf(LearningRepository.MY_WORDS) + repo.topics.map { it.name }.filter { it != LearningRepository.MY_WORDS }

    ScreenColumn {
        LargeTitle("Нове слово")
        Subtitle("Слова з уроків, з роботи чи з життя")
        OutlinedTextField(
            value = danish,
            onValueChange = {
                danish = it
                CustomWordFactory.detectKind(it.text)?.let { k -> kind = k }
            },
            label = { Text("Данською") },
            placeholder = { Text("наприклад: bog") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("field-danish"),
        )
        DanishLetters { letter ->
            val t = danish.text
            val start = danish.selection.min
            val end = danish.selection.max
            danish = TextFieldValue(t.substring(0, start) + letter + t.substring(end), TextRange(start + letter.length))
        }
        OutlinedTextField(
            value = ukrainian,
            onValueChange = { ukrainian = it },
            label = { Text("Переклад українською") },
            placeholder = { Text("наприклад: книга") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("field-ukrainian"),
        )
        Text("Частина мови", fontWeight = FontWeight.SemiBold, color = p.ink, modifier = Modifier.padding(top = 12.dp))
        Segmented(
            listOf(WordKind.NOUN_EN to "Іменник en", WordKind.NOUN_ET to "Іменник et", WordKind.VERB to "Дієслово", WordKind.OTHER to "Інше"),
            kind,
        ) { kind = it }
        Text("Тема", fontWeight = FontWeight.SemiBold, color = p.ink, modifier = Modifier.padding(top = 12.dp))
        Box {
            Text(
                "$chosenTopic  ▾",
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(p.glass)
                    .border(1.dp, p.line, RoundedCornerShape(14.dp))
                    .clickable(role = Role.DropdownList) { menuOpen = true }
                    .padding(14.dp)
                    .testTag("topic-picker"),
                color = p.ink,
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                topics.forEach { t ->
                    DropdownMenuItem(text = { Text(t) }, onClick = {
                        chosenTopic = t
                        menuOpen = false
                    })
                }
            }
        }
        message?.let { Note(it) }
        duplicate?.let { d -> SecondaryButton("Відкрити «${d.display}» ›", Modifier.fillMaxWidth().padding(bottom = 8.dp)) { graph.navigator.open(Route.WordDetails(d.danish)) } }
        PrimaryButton("Зберегти слово", Modifier.fillMaxWidth().padding(top = 8.dp)) {
            duplicate = null
            when (val created = CustomWordFactory.create(danish.text, ukrainian, kind, chosenTopic)) {
                NewWordResult.MissingFields -> message = "Заповни слово й переклад."
                is NewWordResult.Ok -> when (val result = repo.addCustomWord(created.word)) {
                    is AddWordResult.Duplicate -> {
                        message = "Таке слово вже є: ${result.existing.display}"
                        duplicate = result.existing
                    }
                    is AddWordResult.Added -> {
                        val target = Route.Topic(result.word.topic)
                        val stack = graph.navigator.backStack.value
                        // Go back to the topic list if we came from it, otherwise show the topic instead of the form.
                        if (stack.size >= 2 && stack[stack.size - 2] == target) graph.navigator.back() else graph.navigator.replace(target)
                    }
                }
            }
        }
    }
}
