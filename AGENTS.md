# AGENTS.md

Guidance for AI coding agents working in this repository. Read this first, then look
at the real directory tree before editing anything. This is a standard format read by
most coding agents. The project's detailed reference lives in [`docs/`](docs/).

## Project facts

- **Language / runtime:** Kotlin 2.0.0 on Android (JVM target 17)
- **Framework:** Jetpack Compose (Material 3), Media3/ExoPlayer, Hilt, Room, DataStore
- **Package manager / build:** Gradle (Kotlin DSL); single module `:app`. AGP 8.3.2, KSP 2.0.0-1.0.21
- **SDK:** `minSdk` 26, `targetSdk`/`compileSdk` 34. Versions centralized in `gradle/libs.versions.toml`
- **Run locally:** `./gradlew assembleDebug` then install the APK from `app/build/outputs/apk/`
- **Lint:** `./gradlew lint` (Android Lint). No ktlint/detekt/spotless configured — see *Code quality*
- **Test:** `./gradlew test` (unit) / `./gradlew connectedAndroidTest` (instrumented). **No tests exist yet** — see *Testing*
- **Type-check / compile:** `./gradlew assembleDebug` (Kotlin is compiled by the build)

Platform note: development happens on **Windows** — use `gradlew.bat` / PowerShell-compatible
invocations. `./gradlew` works under Git Bash and PowerShell 7.

## Architecture

Clean Architecture, three layers, all under `com.musicplayer.app`:

- **`domain/`** — Pure Kotlin models, repository interfaces, use cases. No Android deps.
- **`data/`** — Repository impls, Room database (`MusicDatabase` v3, 5 entities), `MediaScanner`
  (`data/local/scanner/`) discovering audio via MediaStore + SAF.
- **`player/`** — Playback singletons (`PlaybackController`, `QueueManager`, `EqualizerManager`,
  `SongDeletionHandler`) and `PlaybackService` (`MediaSessionService`). All `@Singleton`, Hilt-injected.
- **`ui/`** — Compose screens by feature under `ui/screens/`, reusable `ui/components/`,
  `ui/navigation/` (`Screen` sealed class + `NavGraph`), Material 3 theme.
- **`di/`** — `AppModule`, `DatabaseModule`, `RepositoryModule`, all `@InstallIn(SingletonComponent::class)`.

Deep reference (keep in sync with code when you change a subsystem):

| Doc | Covers |
|-----|--------|
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Layers, playback system, DI, UI overview |
| [`docs/FILE_MAP.md`](docs/FILE_MAP.md) | Every source file with purpose, fields, methods |
| [`docs/DATABASE.md`](docs/DATABASE.md) | Room schema, DAO methods, adding a table |
| [`docs/DATA_FLOW.md`](docs/DATA_FLOW.md) | Data from MediaStore/Room/DataStore through DI to UI |
| [`docs/NAVIGATION.md`](docs/NAVIGATION.md) | Routes, parameters, adding screens |
| [`docs/VIEWMODELS.md`](docs/VIEWMODELS.md) | Every ViewModel: state, methods, injection |
| [`docs/PLAYBACK.md`](docs/PLAYBACK.md) | Playback system architecture and patterns |
| [`docs/architecture-fluxes.md`](docs/architecture-fluxes.md) | The `F1..Fn` flux master list (diagram source of truth) |

## Navigation (how to read the repo)

- **Code is the source of truth.** Use `docs/` for intent and architecture, but when docs and
  code disagree, trust the code and flag the mismatch.
- **Read the minimum.** Find the smallest set of files that completes the task. Reuse context
  you've already read this session.
- **Keep out of wide searches:** `app/build/`, `.gradle/`, `*.apk`, `images/`, `graphify-out/`,
  the embedded artwork/binaries. Open a specific generated/lock file only when the task needs it.

## Architecture diagram (Mermaid -> draw.io)

The architecture flux diagram is committed and **must be kept in sync** when a structural change
makes it stale:

- **Master list:** [`docs/architecture-fluxes.md`](docs/architecture-fluxes.md) — every flux
  `F1..Fn` with trigger, source->target, mechanism, and `file:line` evidence. This is the join key.
- **Mermaid source:** [`docs/architecture.mmd`](docs/architecture.mmd) — the topology proof,
  rendered natively in the README.
- **Editable diagram:** [`docs/architecture.drawio`](docs/architecture.drawio) — mxGraph XML
  mirroring the Mermaid; refine corridor routing in the draw.io GUI (Extras -> Edit Diagram ->
  Mermaid to re-import after topology changes).

When you alter a flux (new persistence path, new system integration, changed wiring), update all
three together and re-validate the `.drawio` is well-formed. Diagram from what is actually wired
in the code, not from assumptions. See `docs/` HOWTO conventions for the full method.

## Conventions & module boundaries

- **Stay in scope.** Keep changes to what the task requires; no drive-by refactors.
- **Respect layers.** Domain stays Android-free. ViewModels talk to repositories, not Room/DataStore
  directly (except `SettingsViewModel`, which owns its DataStore keys). UI collects `StateFlow`.
- **Match local style.** Follow the patterns, naming, and Flow idioms (`map`/`combine`/`flatMapLatest`)
  already used in the file you're editing.
- **Icons:** use `Icons.AutoMirrored.Filled.*` for directional icons (e.g. `PlaylistAdd`), not the
  deprecated `Icons.Default.*` variants.

## Code quality

Write clean, idiomatic Kotlin — readable, consistent, conventional.

- **Clarity over cleverness; intention-revealing names; small, focused functions; early returns** over
  deep nesting.
- **No magic values** — name constants (DataStore keys are already centralized as `*_KEY`).
- **Handle errors explicitly** — don't swallow exceptions; the codebase guards Android API calls
  (e.g. `bluetoothClass` access) with try/catch returning a safe default, mirror that.
- **No dead weight** — no unused imports, debug logs, commented-out code, or context-free `TODO`s.
- **Comment the _why_**, not the _what_.

**Standard tooling fallback** (none configured in-repo): Kotlin community style — `ktlint`/`ktfmt`
formatting, `detekt` static analysis, per the Kotlin coding conventions. Honor `.editorconfig` if added.
Don't reformat unrelated code.

## Dependencies & security

- **Prefer what's installed.** Versions live in `gradle/libs.versions.toml` — add via the catalog,
  never hand-edit a lock. Don't swap a core dependency (Media3, Room, Hilt) on your own initiative.
- **No hardcoded secrets.** The release `signingConfig` deliberately uses the debug keystore for
  sideloading — do not add real keystore passwords to the repo; document the swap in `README.md`.
- **Validate at boundaries.** Treat MediaStore rows, SAF URIs, file paths, and intent extras as
  untrusted. Don't disable permission checks or `RECEIVER_EXPORTED` requirements to "make it work".

## Testing

The project has **no tests configured**. Do not silently leave behavior changes untested, and do not
scaffold a whole framework without being asked. When adding tests:

- **Match the ecosystem standard** — JUnit + `kotlinx-coroutines-test` for pure logic/ViewModels
  (Turbine for `Flow`), Robolectric or instrumented `androidx.test` for Android-dependent code,
  Compose UI test for screens. State which you chose and why.
- **Every bug fix gets a regression test** — failing before the fix, passing after.
- **Test behavior, not implementation** — assert on `StateFlow` outputs and contracts, not internals.
- **Keep tests deterministic** — control time, inject dispatchers, fake repositories/DAOs (in-memory
  Room), never hit the real MediaStore.
- Put unit tests in `app/src/test/`, instrumented tests in `app/src/androidTest/`, mirroring `src/main`.

If a change affects playback/persistence behavior and cannot be exercised without a device, say so
explicitly in the commit body rather than claiming it was tested.

## Before finishing

For code changes, run and fix failures (skip checks that genuinely don't exist here):

```
./gradlew lint            # Android Lint
./gradlew assembleDebug   # compile / type-check
./gradlew test            # once tests exist
```

Docs-only or diagram-only changes don't need a build. If you changed a flux, re-validate
`docs/architecture.drawio` parses as XML.

## Git & commits (absolute rules)

- **Conventional Commits.** `type(scope): subject` — lowercase type from
  `build|chore|ci|docs|feat|fix|perf|refactor|revert|style|test`, lowercase imperative subject,
  no trailing period, header <= 100 chars, blank line before body, ASCII only. This repo's history
  and `commit-msg` hook enforce it.
- **Stage explicit paths** — never `git add -A`/`.`. Never stage `CLAUDE.md`, `.env*`, keystores,
  IDE config, or build output.
- **No AI / agent attribution, ever.** Never add `Co-Authored-By: Claude`, "Generated with Claude",
  any `noreply@anthropic.com` trailer, or any mention of an AI/assistant in commit messages, PR
  text, branch/tag names, code comments, or generated files — even if tooling suggests it. The
  `commit-msg` hook strips it; do not rely on the hook, keep it out in the first place.
- **Push only with explicit authorization** for that specific push; force-push needs separate
  confirmation and never targets a protected branch.
- **History rewrites** (`rebase -i`, `filter-repo`, `reset --hard`, `commit --amend` on pushed
  commits) are risky — explain and get authorization first.
