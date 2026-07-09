# Draft

A local-first word processor for Android that fixes the documented failures of
Google Docs and Word on mobile: it opens instantly, works fully offline, never
asks for an account or subscription, shows no ads and no AI assistant, and
puts every formatting control within thumb reach.

**Offline-only is a feature: the manifest declares no network permission at
all.** Nothing this app does can leave the device.

## Features

- **Instant + offline** — everything on-device; documents restore from
  autosave the moment the app opens.
- **No account, no ads, no upsell, no AI** — zero sign-in flows, zero premium
  gates, nothing interrupts writing.
- **Thumb-reachable formatting** — a two-row bottom toolbar pinned above the
  keyboard (`WindowInsets.ime`); the text is never hidden under the keyboard.
  Row 1 (always visible): bold, italic, underline, strikethrough, undo, redo.
  Row 2 (swappable segments): **Paragraph** (H1/H2/H3/body, bullet and
  numbered lists, align left/centre/right) · **Colour** (text + highlight,
  fixed palettes) · **Insert** (link) · **Select** (selection assist). Every
  action is at most two taps, and the style at the cursor shows as a
  toggled-on button state.
- **Selection assist** — select-word, select-paragraph, and one-character
  cursor nudge buttons, because mobile text selection is fiddly.
- **First-class outline** — a permanent top-bar button opens a bottom sheet of
  H1–H3 headings generated live; tap to jump.
- **Find & replace** — match count, previous/next, replace and replace-all.
  Live word + character count sits in the top bar.
- **Markdown speed shortcuts** — typing `# `, `## `, `### `, `- `/`* `,
  `1. ` at line start converts to the matching style; `**bold**` and
  `*italic*` convert when the closing marker is typed.
- **Never lose work** — debounced autosave (~2 s idle) to app-private
  storage; automatic snapshots every 5 minutes of active editing (last 20 per
  document); a Version History sheet previews and restores any snapshot; the
  original file is saved on demand and on exit.
- **Stable, predictable UI** — exactly one overflow menu: Save, Save as
  .docx, Export (PDF / Markdown / Plain text), Version history, Word count
  details, Theme (system / light / dark / pure-black OLED), Text size, About.

## File formats

- **Native `.draft`** — a zip containing the editor's HTML (`document.html`)
  plus `meta.json` (title, timestamps). Invisible plumbing; users just see
  documents.
- **`.docx` open and save-as** — a minimal OOXML layer written in pure Kotlin
  (no Apache POI / docx4j). Supported in both directions: paragraphs,
  headings 1–3, bold/italic/underline/strikethrough, text colour, highlight,
  bullet + numbered lists, alignment, hyperlinks. Unsupported elements
  (tables, images, …) degrade gracefully to plain paragraphs — a file is
  never refused.
- **Export** — PDF via Android's `PdfDocument`, `.md` and `.txt` via the
  editor's own converters.

## Building & installing

Requires the Android SDK (compileSdk 36) and network access to
`dl.google.com` / `maven.google.com` and Maven Central. Then either open the
`draft/` folder in Android Studio, or:

```bash
cd draft
./gradlew assembleDebug     # build
./gradlew installDebug      # build + install on a connected device
./gradlew test              # unit tests (document-format layer)
./gradlew lint              # lint
```

Stack: Kotlin 2.3, Jetpack Compose (BOM-free explicit pins, Compose 1.10.5,
Material 3 1.4.0), [compose-rich-editor](https://github.com/MohamedRejeb/compose-rich-editor)
1.0.0-rc14 as the document model (its `RichTextState`, built-in undo/redo
history, and HTML converters are canonical), DataStore for preferences, SAF
for open/save-as with persisted URI permissions, jsoup for the HTML↔docx
bridge. Single module, MVVM-lite. minSdk 26, targetSdk 36.

## Project layout

```
draft/
├── settings.gradle.kts, build.gradle.kts, gradle.properties
├── gradle/libs.versions.toml            # every dependency pinned here
├── gradle/wrapper/, gradlew, gradlew.bat
└── app/
    ├── build.gradle.kts, proguard-rules.pro
    └── src/
        ├── main/AndroidManifest.xml     # no INTERNET permission, by design
        ├── main/res/                    # theme, strings, adaptive icon
        ├── main/kotlin/com/draft/editor/
        │   ├── DraftApplication.kt      # Graph: 3-singleton service locator
        │   ├── MainActivity.kt          # theme + 2-screen navigation
        │   ├── format/                  # PURE KOTLIN document-format layer
        │   │   ├── DocModel.kt          #   neutral model + outline + colours
        │   │   ├── EditorHtml.kt        #   editor HTML dialect ⇄ model (jsoup)
        │   │   ├── DocxWriter.kt        #   model → minimal OOXML zip
        │   │   ├── DocxReader.kt        #   OOXML → model, degrades gracefully
        │   │   ├── DraftFile.kt         #   .draft zip container + meta.json
        │   │   ├── MarkdownShortcuts.kt #   "# ", "**bold**" detection
        │   │   └── FindEngine.kt        #   find/replace + word count
        │   ├── data/
        │   │   ├── DocumentStore.kt     # autosave + snapshots (app-private)
        │   │   ├── RecentFilesRepository.kt
        │   │   ├── Preferences.kt       # theme, text size (DataStore)
        │   │   └── SafFiles.kt          # SAF read/write/persist helpers
        │   ├── export/PdfExporter.kt    # PdfDocument + StaticLayout renderer
        │   └── ui/
        │       ├── theme/Theme.kt       # light/dark/OLED schemes, 17 sp body
        │       ├── home/                # recents list, open, new-doc FAB
        │       └── editor/              # screen, toolbar, sheets, ViewModel
        └── test/kotlin/com/draft/editor/format/
            ├── RoundTripTest.kt         # HTML ⇄ docx round trips + degradation
            ├── EditorHtmlTest.kt        # dialect parsing/writing, .draft zip
            └── ShortcutAndFindTest.kt   # markdown shortcuts, find, word count
```

## Verification status

This project was authored in a sandbox whose egress policy blocks
`dl.google.com` (Android SDK, Android Gradle Plugin, androidx artifacts), so
`assembleDebug` could not be executed end-to-end there. It was verified as
far as the environment physically allows:

- **Format layer: fully tested.** All unit tests (docx round-trip of the
  supported subset, graceful degradation, .draft container, markdown
  shortcuts, find engine) compile and pass on the JVM.
- **All UI, ViewModel, and data code: type-checked** against the *real*
  `richeditor-compose` 1.0.0-rc14, Compose foundation/ui, Material 3, and
  material-icons-extended binaries from Maven Central (via their JVM
  variants, with thin stubs standing in for Android-framework classes) —
  zero errors, zero warnings.
- `PdfExporter` (pure `android.graphics`/`android.text`) was compiled the
  same way; the only unresolved references are the Android framework types
  themselves.
- Dependency coordinates in `libs.versions.toml` were checked against the
  repositories where reachable.
