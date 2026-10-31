# Folio Flow

A tactile Android reading tracker with forgiving streaks, pacing insights and book notes.
Native Kotlin, Jetpack Compose and Material 3. All data stays on the device.

## Features

- **Book deck.** Reading books form a physical stack. Swipe the top card or tap a dot to rotate it.
  Covers glow in a colour taken from the image with Palette, and books without a cover get a leather
  binding with its own glow.
- **Page logging on the card.** `+1`, `+5` and `Custom` sit on the book itself. There are no timers.
- **Analytics.** Rolling daily, weekly and monthly averages, a 53-week heatmap, and an estimated
  finish once a book has three reading days.
- **Challenges.** Daily tiers (Tiny, Steady, Deep), weekly and yearly milestones, up to two Rest Days,
  earned Bookmarks that protect a streak, and a Busy week switch.
- **Archive.** Grid and Timeline views with Days to Complete badges. Tap a book to reveal Favorite and
  Delete. Finishing a book offers Undo, and any book can be restored to active reading.
- **Notes.** Each book has its own Notes tab for notes, takeaways and quotes.

## Build

Requires JDK 17 and the Android SDK with platform 34. Create `local.properties` with your SDK path:

```properties
sdk.dir=C\:/Users/you/AppData/Local/Android/Sdk
```

```bash
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

```bash
./gradlew testDebugUnitTest
```

## Architecture

| Layer | Where | What it does |
|---|---|---|
| Data | `data/` | Room entities, DAOs and `BookRepository`. `ReadingSettings` holds goals. |
| Rules | `ui/stats/ReadingAnalytics.kt`, `ui/challenges/ChallengeRules.kt`, `ChallengeProgress.kt` | Pure functions, unit tested. |
| State | `ActiveBooksViewModel`, `ReadingInsightsViewModel` | `StateFlow`s derived from Room. |
| UI | `ui/hub`, `ui/stats`, `ui/challenges`, `ui/library` | Compose screens, wired in `ui/FolioFlowNavigation.kt`. |

Every statistic is derived from logged reading sessions, so totals can't drift from the history.
Design details are in [docs/ui-spec.md](docs/ui-spec.md).

## Not built yet

Reminders, online book search and barcode scanning. Search and scanning stay hidden on the Add Book
screen until they are connected.

## Fonts

Literata and Figtree are used under the SIL Open Font License 1.1.
