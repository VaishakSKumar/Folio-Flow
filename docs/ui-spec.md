# Book Tracker UI/UX specification

Covers the Active Read Hub and the Stats and History screen. Compose source lives in
`app/src/main/java/com/booktracker/ui/`.

## 1. Design language: "Night Library"

Dark book cloth and uncoated paper, lit by one verdigris accent. Depth comes from tonal steps,
hairline edges and a faint paper grain. The Hub's book stack is the one raised, neumorphic element,
so it stays special.

| Token | Value | M3 role / use |
|---|---|---|
| Board | `#1A1815` | `background`, `surface` |
| Paper low | `#24211C` | `surfaceContainerLow`, standard cards |
| Paper high | `#2E2A24` | `surfaceContainerHigh`, selected card, tracks |
| Ink | `#EFE7D8` | `onSurface` |
| Ink muted | `#B3A998` | `onSurfaceVariant` |
| Verdigris | `#7FC8B4` | `primary`, ring, ahead |
| Amber | `#F2A65A` | `tertiary`, behind |
| Hairline | Ink at 12% | 1dp card edge |
| Heat 0–4 | `#2E2A24 #33665A #4A9483 #77C5B1 #BFF0E2` | Heatmap, light to bright on dark |

- **Type:** Literata for titles and large numerals; Figtree for controls and labels. All numerals
  that change use tabular lining figures (`tnum, lnum`) so nothing jitters.
- **Shape:** 8 / 16 / 24dp corners. Covers use 2–4dp, because books have square-ish corners.
- **Spacing:** 16dp gutter, 12dp between cards, 48dp minimum touch height.
- **Motion:** ring and pacing bar use a soft spring (about 300ms). Respect the system
  animator-scale setting; no decorative loops.
- **Tactility:** a light haptic tick on every committed page change.
- **Neumorphism:** `NeumorphicCard` is opaque Paper low with a dark shadow below right, a faint warm
  highlight above left and a hairline. Soft shadows render from API 28; below that, the hairline
  keeps the card defined. Text stays at full contrast, avoiding neumorphism's usual weakness.
- There is no separate light theme in this scope; the app is dark-first.

## 2. Active Read Hub

Vertical scroll, top to bottom: header, book card deck, page dots, challenge row, page update,
pacing health. Controls sit in the lower half of a tall phone, near the thumb.

### Adding a book
- **+ Add book** sits in the Hub header. With no books,
  the Hub shows "What are you reading?" and one **Add your first book** button.
- The Add a book screen offers three routes that all end in one editable form: search by title,
  author or ISBN; scan a barcode; or type the details by hand. Manual entry always works offline.
- Search results and scans fill the form rather than saving at once, because page counts differ by
  edition. Title and a positive total page count are required. "Already on page" is optional and
  sets the start point: it is not logged as pages read today, so joining mid-book can't fake a day.
- **Cover image:** an optional **Add cover image** button follows Total pages and opens the upload
  modal, a bottom sheet. Inside its dashed container are the action and the formats `.JPG .PNG .WEBP
  .HEIC`. Directly below sits the helper "Supported formats: JPG, PNG, WEBP, HEIC (Max 5MB)".
- Tapping the container opens the Android photo picker, which needs no storage permission. Each pick
  is checked for type and size, because the picker can return any image. A failed check shows an
  amber message and keeps the current cover. HEIC decoding needs Android 9 (API 28) or later, so a
  lower minimum SDK would need HEIC removed from both lists.
- A search or scan result may supply its own cover, which the reader can replace.
- The image is copied into app storage on save, because picker access can expire. Without one, the
  book gets a leather cover with foil text, so a missing image never leaves a blank.
- The same `coverPath` feeds the shared `BookCover`, so one cover appears on the Read card and in
  both Archive views, where it stays after the book is finished.
- **After saving:** a full-screen overlay draws a tick over "Added to Reading Now" and the book's
  title for 1.2s, with a haptic and a TalkBack announcement. No message stays on the form. It only
  appears once the database write succeeds. A failed save keeps the form and shows an amber message.
- The app then returns to Read automatically, with the new book at the front of the deck and its
  own dot. Touches and Back are ignored during the overlay, so a book can't be saved twice.
- Suggested sources: Open Library's public search API for metadata, and CameraX with ML Kit for
  barcodes. Neither is wired in yet.

### Switching books
- Readers swipe or tap the top card to rotate the deck; there is no separate Switch book button. One
  dot appears per book marked Reading, in most-recently-read order, and the app reopens on the last
  book used. Dots also accept taps. With one book, dots are hidden.
- The card carries its own `+1`, `+5` and Custom, and the finish estimate below follows the front book.
- Each book keeps its own current page. Each update is a `ReadingEntry` (book, date, start, end).
- Today's pages are the sum across books, each floored at 0: 10 pages in one book plus 15 in another
  counts as 25.
- Pacing Health, the daily challenge, streaks and Stats use that total, so rotating alone changes
  none of them.
- The finish estimate uses that book's own pace over 14 days and appears only after 3 reading days,
  otherwise concurrent books would each borrow the whole average.

### Book stack (neumorphic)
Layers, back to front: ambient glow, far card, near card, front card.

- **Layers:** up to 3 opaque cards, each 7% smaller and 20dp higher, so only top edges show above
  the front card. Back cards show blank paper: their content is hidden and removed from accessibility.
- **Rotating:** a swipe past 22% of the card's width, or a tap, sends the front card out to the side
  and round to the back over 480ms while the others step forward. Its text fades before it passes
  behind, and the next book's fades in as it lands. Dots step the deck to the chosen book.
- Buttons on the card take their own taps, and a short drag springs back. TalkBack gets a "Next
  book" action. Title height is fixed at 2 lines so every card matches.
- **Ambient glow:** `extractGlowColor` uses Android Palette, preferring Vibrant, then Light Vibrant,
  Dark Vibrant and Dominant. Saturation is lifted to at least 45% and lightness held to 45–65% so
  dark covers still glow; near-grey covers stay grey. It is drawn at 55% opacity with a wide blur
  behind the stack and cross-fades over 250ms between books.
- The colour is calculated when a cover is added or changed, then stored with the book.
- **Fallback covers:** a book with no image gets one of four drawn leather textures, picked from its
  title so it never changes: Saddle, Oxblood, Forest or Midnight. Each has its own curated glow.
  Drawing them keeps the app free of image assets, and Read and Archive share them through `BookCover`.
- An uploaded cover that Palette can't read has no glow. It doesn't borrow a texture's colour.
- Glow never carries meaning or sits behind text directly, so contrast is unaffected.

### Finishing and deleting a book
Both actions sit on the front card, ask for confirmation, then play a
260ms exit before the database changes.

- **Mark finished** is also asked for when a page update reaches the last page. It saves the current page to `pageBeforeCompletion`, then
  sets `status = COMPLETED`, `finishDate = System.currentTimeMillis()` and `currentPage = totalPages`.
  The book leaves the deck and appears in Archive. Pages jumped over are not logged as read today.
- **Celebration:** after confirming, 28 stars burst from the card behind a "Hurray! Completed" banner
  for 1.4s with a haptic, and TalkBack announces it. The deck is locked meanwhile. With system
  animations off, only the banner shows. Leaving the screen during it still completes the book.
- **Undo:** a snackbar, "[Title] moved to Archive", offers Undo for about 10 seconds, even if that
  was the last book and the Hub is now empty. Undo restores the book and brings it to the front.
- **Restore to Active:** the Details tab of a finished book shows its dates, days and the page it
  will return to. Restore sets `status = READING`, returns `currentPage` to
  `pageBeforeCompletion`, and clears `finishDate` and `daysToComplete`. The tab says this first,
  because finishing it again records a new date. `startDate` stays, so a later finish counts from
  the original start. Reading sessions are never touched, so Stats and streaks stay correct.
- A restored book on its last page doesn't ask "Finished it?" again. Mark finished is still on the card.
- Days to Complete is `finish date − start date + 1 − paused days`, compared as local calendar dates
  rather than raw milliseconds, then stored on the book so its badge never changes.
- **Delete** removes the `BookEntity`. The foreign key's `CASCADE` removes its
  `ReadingSessionEntity` rows, so no orphaned sessions remain, and the cover file is deleted too.
  Notes go too. Those pages leave Stats and can lower a streak, so a future version should warn first.
- **Build needs:** `room-runtime`, `room-ktx` and `room-compiler` (through KSP) with
  `room.schemaLocation` set, `palette-ktx`, Coil 3 Compose, `activity-compose`,
  `lifecycle-viewmodel-compose` and `material-icons-core`.

### Book card
- Title in Literata 24 (max 2 lines), author beneath (1 line).
- Cover 112×168dp left, progress ring 160dp right. Ring shows current page large, `of N pages`
  small. The percentage is deliberately left out: the brief asks for pages versus total.
- Ring animates to its new value and starts at 12 o'clock.

### Page update
- The controls sit on the book card, under the cover, so logging needs no scrolling. There is no
  slider, because a horizontal drag on the card rotates the deck.
- `+1`, `+5` commit immediately and clamp at the total; both disable when no pages are left.
- `Custom` opens a numeric dialog validated to 0..total. Save stays disabled while invalid.
- Corrections go through `Custom`. Moving backward is allowed and reduces today's count.
- Logging is manual only. There is no session timer, so forgetting to start one can never lose a
  day's reading. Progress is measured in pages throughout the app.

### Book details and notes
- One full screen for reading and finished books, with **Details** and **Notes** tabs. The card's
  **Notes** button opens it on Notes; an Archive book's overlay opens it on Details.
- Notes live in a dedicated `BookNoteEntity` table, one book to many notes, and cascade with the book.
  Restoring or finishing a book doesn't touch them, so they follow it between Reading and Archive.
- Each note is a **Note**, **Takeaway** or **Quote**, newest first. Tapping one opens the editor.
- The editor has type chips, a full-height text field, **Save** and **Discard**. Back saves, so
  leaving can't lose a thought. Only Discard drops text, and blank notes are never stored.
- Deleting a note asks for confirmation.

### Pacing health
Answers one question: is the recent daily page count matching the target?

```
rolling = max(mean(last 7 days including today), mean(7 completed days before today))
ratio   = rolling / targetPerDay
```

The `max` stops a morning check from reading as a slump: today's low partial count only counts once
it helps.

| Ratio | State | Marker | Colour |
|---|---|---|---|
| ≥ 1.10 | Ahead | ▲ | Verdigris |
| 0.90–1.09 | On pace | ● | Ink |
| < 0.90 | Behind | ▼ | Amber |
| No goal | Set a daily goal | none | Muted |
| Under 3 reading days in the window | Still learning your pace | none | Muted |

State is never colour alone: each has a word and a marker. The bar spans 0–150% of target with a
tick at the target. The line beneath gives today's count across all books and the shown book's
projected finish (`ceil(pagesLeft / bookPace)` days). Tone stays neutral; "Behind" is a fact, not a scolding.

## 3. Stats and History

One destination with two primary tabs: **Stats** and **Archive**.

### Rolling averages
Three equal metric cards, each a tab that drives a single trend chart below. One number, one unit,
one change against the previous window. No sparklines inside cards.

| Card | Window | Value | Compared with | Chart |
|---|---|---|---|---|
| Day | Last 7 days | pages ÷ 7 | Previous 7 days | 14 daily bars |
| Week | Last 4 weeks | pages ÷ 4 | Previous 4 weeks | 12 weekly bars |
| Month | Last 6 months | pages ÷ 6 | Previous 6 months | 12 monthly bars |

Days without reading count as zero, otherwise the average flatters gaps. Current bucket is verdigris,
history neutral, and a thin rule marks the average. Tapping a bar shows its value.

### Heatmap
- 53 weeks, Monday-first columns, 13dp cells with a 3dp gap; opens scrolled to the latest week.
- Levels follow the goal, not the data's maximum, so one huge day does not wash out the year:
  0 none, 1 under 50%, 2 under 100%, 3 under 150%, 4 at or above 150%. Without a goal, any reading
  uses level 2.
- Each cell has a semantic label (`Tue 14 Oct 2026, 32 pages`). Tap shows date and pages in a fixed
  line under the grid, so nothing covers the cells.
- Header shows current and best streak. Legend sits below.

### Archive
A two-way segmented control, remembered between visits, switches views. Both use the same data and
sort by finish date, newest first.

**Grid shelf:** adaptive grid (minimum 104dp, 3 columns on a typical phone) of 2:3 covers on shelf
planks, with a Days badge in each cover's bottom-left corner.

**Timeline:** grouped by finish month. Each row has a 56×84dp cover, title, `start – finish` dates,
and the Days badge. Rereads show `read 2`.

**Days to Complete**
```
days = (finish − start + 1) − pausedDays, minimum 1
```
Inclusive of both dates, so same-day finishes read `1 day`. On-hold periods are excluded. Each
reread is its own record with its own badge. Use the device's local calendar dates, stored as dates
rather than instants, so a timezone change cannot alter the count.

Empty state: "Books you finish land here, with the days each one took."

**Action overlay:** both views stay free of buttons. Tapping a book floats one overlay over it, and
tapping elsewhere closes it. It holds a **Favorite** toggle (heart), **Details** (info) and **Delete**
(dustbin). A favorite shows a small heart on its cover or row. Delete asks for confirmation, then
purges the book, its sessions, its notes and its cover file. Its pages leave Stats.

## 3a. Challenges and habit support

A fourth bottom-nav destination, plus a one-line challenge row on the Hub. Rules live in
`ui/challenges/ChallengeRules.kt`.

### Principle
A streak measures **showing up**, not reading volume. The daily goal stays in Pacing Health. Mixing
the two is what makes busy people lose a streak and quit.

### Today: one challenge, three tiers
Each day has one themed title, and any tier counts.

| Tier | Requirement | Purpose |
|---|---|---|
| Tiny | 1 page | Keeps the streak on the worst day |
| Steady | Daily target met | The normal day |
| Deep | 150% of the daily target | A badge for days with time to spare |

Today's title rotates through light themes such as "Read before work" or "Finish a chapter". The
theme is flavour only; tier completion never depends on it.

### Week and year achievements
| Scope | Achievement | Rule |
|---|---|---|
| Week | Good week | Planned days minus 2, minimum 1 |
| Week | Full week | Every planned day |
| Week | Three deep reads | Deep tier 3 times |
| Year | Forty weeks | Good week 40 times |
| Year | Twelve books | 12 finished books |
| Year | Ten thousand pages | 10,000 pages |
| Year | Four seasons | A book finished in each season |

Planned days are the week's days minus rest days. Year goals deliberately leave room for bad
stretches: 40 of 52 weeks, not all of them.

### Protecting busy readers
- **Rest days:** the user picks up to 2 weekdays, such as long shift days. They neither extend nor
  break a streak.
- **Bookmarks:** earn 1 per 7 read days, hold up to 3. A missed day spends one automatically, then
  the app says so plainly.
- **Busy week:** one switch for 7 days. Only Tiny shows, Pacing Health stops judging, and reminders
  drop to one.
- A missed day with no bookmark resets the current streak. Best streak and all badges stay.

### Reaching readers who forget
Android can't force someone to read, and blocking their other apps would need accessibility or
usage-access permissions that are a poor fit here and a Play policy risk. The strongest honest
option is an opt-in commitment:

- **Your reason:** onboarding asks "Why did you download this?" Those words appear in reminders.
- **Nudge level:** Gentle (1 reminder), Firm (2), Commitment (3, spaced through the evening).
- All reminders clear once the user logs 1 page, and respect a chosen time window.
- Reminder text links straight to the Hub with one-tap `+1 page`, so the Tiny tier takes seconds.
- A home-screen widget showing today's state is a sensible follow-on.
- **Backoff:** after 3 ignored days, send one message asking whether the plan still fits, offering
  Busy week or a lower level. Constant pressure on an inactive user leads to an uninstall.
- Commitment mode stays off by default and turns off in one tap. Copy reminds, never shames.

## 4. Accessibility
- Body text meets 4.5:1 on its surface; ink and muted ink both exceed 6:1 on Paper low.
- Ring exposes progress semantics; heat cells and chart expose text alternatives.
- Layouts use `sp` and wrap, and should be checked at 200% font scale.
- Touch targets are 48dp. Heat cells are smaller by necessity; the same data is reachable by
  scrolling cells with TalkBack, and a list view of the month is a sensible follow-on.

## 5. Evidence from Bookmory 1.4.30 (build 329)

Bookmory was inspected as a reference through its bundled assets only. Its code was not decompiled,
and it is a Flutter app, so its widgets do not carry over to Compose. Nothing was executed.

| Observed in the package | Source | How this design responds |
|---|---|---|
| Cream and blue card with a cover, `Day 11`, `p. 124 / 333` and timer and add-note actions | `images/app_guide/track_your_reading.png` | Same data; progress becomes a ring, and controls are on the Hub rather than behind actions |
| Timer settings: auto-start, keep screen on; a paused state | `en.json` `readTimer` | Keep-awake is adopted; auto-start left out to keep the Hub calm |
| Daily goals versioned: "The new goal applies from {date}. Past days stay as they are." | `en.json` `goal.daily` | Store goals with an effective date; heatmap and pacing read the goal in force on that day |
| Up to 3 daily goals; goals for pages or time | `en.json` | One pages-per-day target here, as the brief asks |
| Progress units: page, percent, chapter, episode, time | `en.json` | Pages only for now. Keep `unit` in the data model so others can follow |
| Daily or weekly streak; correction tools; warning when a delete lowers a streak | `en.json` `streak` | Show streaks; warn on deleting a session that would lower one |
| Books can be on hold, and records can't be added in a paused period | `en.json` | `pausedDays` excluded from Days to Complete |
| Calendar paints covers on read days | `images/app_guide/calendar.png` | Replaced with the requested heatmap, which reads consistency across a year |
| Stats built on bars by month, goal ring, stat cards | `images/app_guide/stats.png` | Uses metric cards with one shared chart |
| Reading-count concept: "First reading" | `track_your_reading.png` | Each reread is a record |

Not established: Bookmory's actual formulas, its data schema and any behaviour beyond what its strings
and images state. No Pacing Health equivalent or GitHub-style heatmap was found in the English strings
searched; that is a bounded keyword search, not proof of absence. Colours, layout, copy and assets
here are original, and no Bookmory image, string or code should be shipped in this app.
