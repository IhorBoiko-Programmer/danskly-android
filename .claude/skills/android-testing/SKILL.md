---
name: android-testing
description: How to write and run tests for the Danskly Android app (Kotlin + Jetpack Compose) with 100% line coverage. Use whenever adding a feature, fixing a bug or touching any Kotlin code in this repository.
---

# Testing Danskly (Kotlin + Jetpack Compose)

Every change ships with tests. CI fails if line coverage of the debug unit tests drops below **100%**.

## Commands

| Task | Command |
|---|---|
| All tests (JVM, Robolectric) | `./gradlew testDebugUnitTest` |
| Tests + screenshots of every screen | `./gradlew recordRoborazziDebug` (PNGs in `app/screenshots/`) |
| Coverage report (HTML) | `./gradlew koverHtmlReportDebug` → `app/build/reports/kover/htmlDebug/index.html` |
| Coverage gate (100%) | `./gradlew koverVerifyDebug` |
| One test class | `./gradlew testDebugUnitTest --tests "app.danskly.ui.SettingsScreenTest"` |

All tests are **local JVM tests**: no emulator is needed. Android framework classes come from Robolectric.

## Architecture decides how a thing is tested

```
core/      plain Kotlin, no Android imports  → fast JUnit tests
data/      Android implementations           → Robolectric (+ MockWebServer for HTTP)
feature/   Compose screens                   → Compose UI tests on Robolectric
ui/        theme and shared components       → covered through the screen tests
```

Rules:

1. **Logic goes into `core/`.** If a screen needs a decision (what to ask next, how to grade, what is due), write it as a plain class in `core/` and test it with JUnit. Screens only draw state and forward clicks.
2. **Fakes, not mocks.** The project has no mocking library on purpose. Use the fakes in `app/src/test/java/app/danskly/support/`:
   - `FakeSpeaker` — records `speak(text, slow)` calls; `voices` and `isAvailable` are configurable.
   - `FakeAiClient` — answers from a lambda, or fails with an `AiErrorKind`; `FakeAiClient.smart()` answers every app prompt sensibly; `streamParts` simulates streaming.
   - `testGraph(...)` — a full `AppGraph` with the real bundled content, in-memory progress and a `FixedClock`.
   - `InMemoryProgressStore`, `FixedClock` live in production code because previews and tests both use them.
3. **Real content in tests.** `TestContent.content` loads the shipped JSON from `src/main/assets/content`, so broken content fails the build.
4. **Determinism.** Randomness is injected (`AppGraph.random`, `Random(seed)` in generators); time comes from `Clock`. Never call `System.currentTimeMillis()` or `Random.Default` in logic that tests depend on.

## Writing a JUnit test for `core/`

```kotlin
class SpacedRepetitionTest {
    @Test
    fun wrongAnswerResetsAndIsDueNow() {
        val s = SpacedRepetition().grade(CardState(3, 999), correct = false, nowMillis = 100)
        assertEquals(CardState(0, 100), s)
    }
}
```

- Name tests as sentences: `everyGeneratedQuestionIsSolvable`.
- For generators, prefer property-style tests: generate hundreds of items and assert an invariant (`answer in options`).
- Cover error branches explicitly (malformed JSON, empty input, unknown enum values).

## Writing a Compose UI test

Extend `UiTest` (Robolectric runner, phone-sized screen, `createComposeRule`):

```kotlin
class SettingsScreenTest : UiTest() {
    @Test
    fun speedPresets() {
        launch(Route.Settings)                       // opens the route on top of Home
        click("Швидко")                              // scrolls into view, clicks, waits for idle
        assertEquals(1.25f, graph.repository.progress.value.settings.speechRate)
        assertEquals(VoiceSelector.PREVIEW, speaker.last)
    }
}
```

Helpers in `UiTest`: `launch(route, graph, dark)`, `click(text)`, `clickTag(tag)`, `text(..)`, `tag(..)`, `exists(text)`.

Finding nodes:
- Prefer visible text, as a user would. Use `Modifier.testTag("...")` only when text is ambiguous or absent (`tile:Слова`, `flip-card`, `option:<text>`, `bank:<i>`, `answer-field`, `back`, `settings`).
- Listen buttons have content descriptions `"Прослухати: <text>"` and `"Повільно: <text>"`.
- Lazy lists: `onNodeWithTag("word-list").performScrollToNode(hasTestTag("word:kuffert"))`.
- Sliders: `performSemanticsAction(SemanticsActions.SetProgress) { it(0.7f) }`.
- Text fields: `performTextInput`, `performImeAction`.

When a screen's next state depends on randomness (exercise sessions), build a **mirror** with the same seed (see `ExerciseSessionScreenTest`) instead of adding test hooks to production code.

## Screenshots

`ScreenshotTest` renders every screen (light and dark) with Roborazzi and `@GraphicsMode(NATIVE)`. Add a line there for every new screen. The images are uploaded by CI as the `screenshots` artifact and are used for visual review and Google Play screenshots (phone, 2:1).

## Coverage rules

- 100% **line** coverage on `debug` unit tests, enforced by Kover (`app/build.gradle.kts`).
- Excluded only: `BuildConfig`, Compose's generated `ComposableSingletons`, and functions annotated `@Preview`.
- Do not add exclusions to make the number pass. If a line cannot be reached, it is dead code: delete it.
- Read the XML/HTML report (`app/build/reports/kover/`) to find missed lines.

## Checklist for a new feature

1. Add a `Route` and its title in `titleOf()`.
2. Put the logic in `core/` with JUnit tests.
3. Write the screen in `feature/<name>/`, stateless where possible.
4. Add a Compose test for every button and every state (loading, error, empty, success, dark theme if it has custom colours).
5. Add the screen to `ScreenshotTest`.
6. Run `./gradlew recordRoborazziDebug koverVerifyDebug` and look at the screenshots.
