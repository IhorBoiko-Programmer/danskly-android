# Danskly — notes for contributors and AI assistants

- Kotlin + Jetpack Compose, single `app` module, package `app.danskly`.
- **All logic lives in `core/`** (plain Kotlin, no Android imports). Screens in `feature/` draw state and forward clicks.
- Dependencies are wired by hand in `AppGraph`; tests build an `AppGraph` with fakes (`support/Fakes.kt`).
- User-facing text is Ukrainian; Danish examples always come with a Ukrainian translation.
- Learning content is data, not code: `app/src/main/assets/content/*.json`. Verified verb/noun/adjective forms are in `forms.json`.
- The app never holds AI keys. It talks to the Danskly server (`/api/ai`, `/api/health`), which owns the keys and the fallback between keys and models.
- Tests and the 100% coverage rule: see `.claude/skills/android-testing/SKILL.md`. Run `./gradlew koverVerifyDebug` before pushing (it runs all tests and records screenshots).
- Keep it simple: no new libraries without a clear reason; prefer fakes over mocks; no reflection-based serialization.
