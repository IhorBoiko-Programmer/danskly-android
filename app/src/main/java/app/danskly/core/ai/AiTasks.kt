package app.danskly.core.ai

import app.danskly.core.json.JsonValue
import app.danskly.core.json.str
import app.danskly.core.model.Article
import app.danskly.core.model.Example
import app.danskly.core.model.Word
import app.danskly.core.model.WordDetails
import app.danskly.core.model.WritingTask
import app.danskly.core.progress.ProgressCodec

/** The prompts the app sends to the AI, and the parsing of the structured answers. */
object AiTasks {

    const val TEACHER =
        "Ти — доброзичливий викладач данської для україномовного студента рівня A2–B1, який готується до модульного тесту " +
            "Danskuddannelse 3, модуль 5. Відповідай українською, данські приклади давай данською. " +
            "Не використовуй markdown (без зірочок, решіток і таблиць), лише простий текст."

    private const val JSON_ONLY = "\n\nВідповідай лише чистим JSON, без пояснень і без ```."

    /** Ready-made conversation starters for the AI tutor. */
    val chatStarters = listOf(
        "Розмова: подорожі" to "Давай розмовляти данською про подорожі. Став мені питання по одному й виправляй мої помилки.",
        "Вправа на переклад" to "Дай 5 речень українською для перекладу данською на тему роботи, потім перевір мої відповіді.",
        "Пояснити правило" to "Поясни простою мовою, де в данській ставити ikke: до дієслова чи після.",
        "Підготовка до усної частини" to "Проведи зі мною коротке інтерв’ю данською, як на усній частині тесту, і дай відгук.",
    )

    /** Prefixes the teacher instructions to the first user message of a chat. */
    fun chat(history: List<ChatMessage>): List<ChatMessage> = history.mapIndexed { i, m ->
        if (i == 0 && m.role == Role.USER) m.copy(content = "$TEACHER\n\n${m.content}") else m
    }

    fun wordDetailsPrompt(word: Word): String {
        val examples = if (word.isVerb) {
            "examples — 9 коротких речень, по одному на кожну форму з forms, label = назва форми."
        } else {
            "examples — 6 коротких речень у різних ситуаціях, label = 1–3 слова про ситуацію."
        }
        return "$TEACHER\nТи укладаєш словникову статтю для слова «${word.display}» (переклад: ${word.ukrainian}). Поверни ЛИШЕ JSON такої форми:\n" +
            "{\"pos\":\"частина мови українською\",\"forms\":[[\"назва форми українською\",\"данська форма\"]],\"usage\":\"2–3 речення українською: коли й у яких ситуаціях вживається слово, з чим його не плутати\",\"examples\":[{\"label\":\"...\",\"da\":\"...\",\"uk\":\"...\"}],\"phrases\":[{\"da\":\"...\",\"uk\":\"...\"}]}\n" +
            "Правила: forms — для дієслова: Інфінітив, Теперішній, Минулий, Перфект, Плюсквамперфект, Майбутній, Умовний, Наказовий, Пасив; " +
            "для іменника: Однина, Однина, означена, Множина, Множина, означена; для прикметника: en-форма, et-форма, множина й означена форма, " +
            "вищий ступінь, найвищий ступінь; для інших частин мови порожній масив. $examples phrases — 3–5 частих сполучень або сталих виразів. " +
            "Рівень A2–B1, природна сучасна данська, кожен приклад з точним перекладом.$JSON_ONLY"
    }

    fun parseWordDetails(reply: String): WordDetails {
        val obj = AiText.parseJsonLoose(reply) as? JsonValue.Obj ?: throw AiException(AiErrorKind.BAD_RESPONSE)
        val details = ProgressCodec.decodeDetails(obj)
        if (details.examples.isEmpty()) throw AiException(AiErrorKind.BAD_RESPONSE)
        return details.copy(forms = details.forms.take(MAX_FORMS), examples = details.examples.take(MAX_EXAMPLES), phrases = details.phrases.take(MAX_PHRASES))
    }

    fun newWordsPrompt(topic: String, existing: List<Word>): String =
        "$TEACHER\nДай 12 нових данських слів рівня A2–B1 на тему «$topic», яких немає в цьому списку: " +
            existing.joinToString(", ") { it.display } +
            ". Для іменників став en або et. Поверни ЛИШЕ JSON-масив рядків у форматі \"данське|переклад українською\", " +
            "наприклад [\"en bil|автомобіль\",\"at køre|їхати\"].$JSON_ONLY"

    /** Parses "en bil|автомобіль" entries, skipping anything malformed. */
    fun parseNewWords(reply: String, topic: String): List<Word> {
        val items = (AiText.parseJsonLoose(reply) as? JsonValue.Arr)?.items ?: throw AiException(AiErrorKind.BAD_RESPONSE)
        return items.mapNotNull { item ->
            val text = (item as? JsonValue.Str)?.value ?: return@mapNotNull null
            val parts = text.split("|").map { it.trim() }
            if (parts.size != 2 || parts.any { it.isEmpty() }) return@mapNotNull null
            val match = Regex("^(en|et) (.+)$").find(parts[0])
            val danish = match?.groupValues?.get(2) ?: parts[0]
            Word(danish, parts[1], topic, Article.fromText(match?.groupValues?.get(1)), isCustom = true)
        }
    }

    fun examplePrompt(word: Word): String =
        "Склади одне коротке речення данською (рівень A2–B1) зі словом «${word.display}» і дай його переклад українською. " +
            "Поверни ЛИШЕ JSON {\"da\":\"...\",\"uk\":\"...\"}$JSON_ONLY"

    fun parseExample(reply: String): Example {
        val obj = AiText.parseJsonLoose(reply) as? JsonValue.Obj ?: throw AiException(AiErrorKind.BAD_RESPONSE)
        return try {
            Example(obj.str("da"), obj.str("uk")).takeIf { it.danish.isNotBlank() && it.ukrainian.isNotBlank() }
                ?: throw AiException(AiErrorKind.BAD_RESPONSE)
        } catch (e: app.danskly.core.json.JsonException) {
            throw AiException(AiErrorKind.BAD_RESPONSE, e)
        }
    }

    fun checkWritingPrompt(task: WritingTask, text: String): String =
        "$TEACHER\n\nЗавдання: ${task.task}\n\nТекст студента:\n$text\n\n" +
            "Зроби: 1) коротка оцінка; 2) помилки у форматі «фрагмент → виправлення → пояснення»; 3) виправлена версія данською; 4) дві поради для тесту."

    fun translatePrompt(text: String): String = "Переклади українською, без коментарів:\n\n$text"

    const val PING_PROMPT = "Напиши одне данське слово-привітання, без пояснень."

    private const val MAX_FORMS = 12
    private const val MAX_EXAMPLES = 10
    private const val MAX_PHRASES = 6
}
