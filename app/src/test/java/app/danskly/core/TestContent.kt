package app.danskly.core

import app.danskly.core.content.ContentParser
import app.danskly.core.model.Content
import java.io.File

/** Loads the real bundled content from `src/main/assets/content`, so tests check the shipped data. */
object TestContent {

    private val dir: File by lazy {
        listOf("src/main/assets/content", "app/src/main/assets/content", "/home/claude/danskly-android/app/src/main/assets/content")
            .map(::File)
            .first { it.isDirectory }
    }

    val content: Content by lazy { ContentParser.parse { name -> File(dir, name).readText() } }

    fun read(name: String): String = File(dir, name).readText()
}
