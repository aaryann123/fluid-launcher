# Architecture

Fluid is one activity, one Compose tree and one SharedPreferences file. The design notes in
[DESIGN.md](DESIGN.md) list every behaviour; this file explains how the pieces fit and why.

```
             FluidApp (process)
             ├─ Store        SharedPreferences  <->  Compose state
             ├─ Apps         LauncherApps, all profiles, shortcuts, Settings pages
             └─ widgetHost   one AppWidgetHost for the whole process
                     │
MainActivity ── HomeState (Motion values: drawer, widgets page, pull-left)
   │
   └─ Box ─┬─ WidgetsPane   AppWidgetHostViews in a scrolling column
           ├─ HomePane      clock, glance line, dock
           └─ Drawer        overlay: apps grid / search results, search bar
SettingsActivity, PinActivity, SplitActivity     separate small activities
DotService (notification listener), LockService (accessibility)
```

## Files

| File | Responsibility |
|---|---|
| `MainActivity.kt` | Home activity, `Motion` (finger-driven float with spring settle), `HomeState`, back handling, lifecycle resets |
| `Home.kt` | The pane container and its gesture detector, clock and glance line, dock, wallpaper theme and scrim, blur hookup |
| `Drawer.kt` | Apps grid by category, "your apps on top", search results, long-press menu, pull-to-close |
| `Widgets.kt` | `FluidWidgetHost`, `HostView` long-press, bind/configure flow, picker with live previews, resize and reorder |
| `Apps.kt` | Installed apps across profiles, app shortcuts, Settings pages, launching, split-screen pairs |
| `Search.kt` | `matchScore` and `rank`: prefix, word prefix, initials, substring; ties by launch count |
| `Categories.kt` | Keyword guess for the drawer sections, then Android's category, then system apps to Tools |
| `Contacts.kt` | "call sam" / "text sam" results once contacts permission is granted |
| `Halo.kt` | Package to Phosphor glyph, themed-icon and word fallbacks, the white-with-shadow icon bitmap |
| `Blur.kt` | Samsung `semSetBlurInfo` by reflection, else the public cross-window blur |
| `Store.kt` | Every setting, favourite and widget as a `Pref` mirrored into Compose state |
| `DotService.kt` / `LockService.kt` | Notification dots; lock screen and shade via accessibility global actions |
| `PinActivity.kt` / `SplitActivity.kt` | Pin requests from other apps; the two-app split trick |
| `FluidApp.kt` | Process start-up, and screen on/off nudges to the Lumen widgets |

## Gestures are one detector, read only in draw

A single pointer detector sits on the pane container. It locks to an axis after touch slop and decides in the
Initial pass, so it can take a gesture away from children such as widget views. If slop isn't crossed before
the long-press timeout it gives up, so long-presses always win. The drawer, widgets page and left pull are
`Motion` values the finger sets directly and a spring settles with the fling velocity. They are read only inside
`graphicsLayer {}`, so a frame of a swipe is draw work with no recomposition. That is what keeps it at 120 Hz.

## Two screens, one tree

The cover and inner screens get the same layout. Folding changes size, not structure, so widget views are
never torn down. Widget sizes are reported with every width they are shown at (`updateAppWidgetSize` with a
list of `SizeF`), so a widget can switch layouts locally when the phone folds.

## Search

`rank` sorts by `matchScore` (0 prefix, 1 word prefix, 2 initials, 3 substring), then launch count, then
alphabetical. From the second letter it adds app shortcuts, Settings pages with everyday words, and contacts;
a "call" or "text" prefix puts people first. "Auto-open single match" waits for a 0.3 s pause so typing
through "cal" on the way to "call mom" doesn't open Calendar.

## Halo icons

`GLYPHS` maps package names to Phosphor glyph names; `assets/glyphs.txt` holds the SVG path data, fetched by
`Tools/glyphs.py`. A gitignored `assets/glyph-map.txt` adds your own mappings without editing code. With no
mapping, Fluid draws the app's monochrome themed icon (Android 13+), then guesses from a word in the label
("Weather Pro" gets `cloud-sun`), then falls back to the first letter in Montserrat Heavy.

## Storage

One SharedPreferences file. Each value is a `Pref` that loads lazily and writes through. Favourites store their
label too, so a cold start draws the home before the app list has loaded.

## Things that bit us

- **Back on the bare home rebuilt everything.** The platform `finish()`es a HOME task root, then rebuilds the
  home and every widget. A lowest-priority back callback now swallows it. Found on the real Fold, not the emulator.
- **A Compose clip does nothing to a widget.** Rounded corners need the `AppWidgetHostView`'s own outline
  clipped, after zeroing Android's default 8 dp widget padding.
- **Samsung turns public window blur off.** `isCrossWindowBlurEnabled` is false on the Fold; One UI's
  `View.semSetBlurInfo` works, reached by reflection.
- **Samsung's live wallpapers report no colours.** `WallpaperColors` is null, so "auto" text colour means
  white over a soft scrim.
- **Edge swipes arrive as back.** One UI delivers them as back gestures; the home reads `BackEvent.swipeEdge`
  and `touchX` and turns them into the widgets page or the left pull.
- **Android 14 blocks widget config launches.** The provider's PendingIntent needs background-activity-launch
  opt-in options, or nothing opens.
- **Two widget hosts with one id cut off updates.** When a second host stopped listening, the home stopped
  getting updates. One host per process.
- **AppCompat's inflater breaks RemoteViews.** Fluid uses no AppCompat.
- **SHOW_ALARMS opens the alarm tab.** Tapping the clock launches the Clock app's main launch intent instead.
- **Split screen needs a full-screen requester.** Android only splits for an app opening something adjacent
  to itself, so a blank `SplitActivity` asks for app B beside itself, then opens app A.
- **Lumen's clock went stale on the lock screen.** Its non-wakeup alarm is held back while the app is frozen,
  so Fluid, always running, nudges it on screen-on and turns the quote over on screen-off.
