# Fluid: a minimal text launcher for the Galaxy Z Fold 7

Olauncher's calm, text-only home, plus the things it refuses to add (widgets,
pinned shortcuts/PWAs, app shortcuts, split screen, notification dots), with every gesture tracking
the finger at 120 Hz instead of jumping between screens. Built to replace One UI Home outright.

## Target
- Galaxy Z Fold 7 (the test phone runs Android 17 / One UI 9). Cover ≈ 411 dp wide (21:9), inner ≈ 750 dp (near-square).
- minSdk 31, targetSdk 36, compileSdk 37 (Compose BOM 2026.09 requires it). Kotlin + Jetpack Compose, no AppCompat
  (AppCompat's inflater breaks RemoteViews). Sideloaded release APK (R8 + baseline profile).

## Layout
One composition tree for both screens, so folding or unfolding never recreates
widget views:

```
Box [ WidgetsPane, HomePane ]   both full screen, on both the cover and the inner screen
HomePane on top; swipe right slides it away and WidgetsPane in from the left (a plain page slide)
```
The inner screen started as a split view (widgets left, home right, split at the hinge); on the real Fold
that looked cluttered, so it pages like the cover. The widgets column is capped at 560dp and centred there.
Widget views are translated but never scaled: each moved frame re-positions their Android views.
Drawer and widget picker are overlays above the panes.

**HomePane**
- Clock (large, light weight) + date + a small clock icon with the next alarm and battery beside it
  ("7:30 AM · 82%"; alarm → Clock, battery → Samsung's battery page), refreshed with the minute tick. Time/icon → the Clock app's main screen (the handler
  of SHOW_ALARMS, opened via its launch intent, since SHOW_ALARMS lands on Samsung's alarm tab); date → calendar.
- Favorites: an icon dock in a glass pill on the right edge, centred, magnifying around the finger like the Mac
  dock (draw-only: graphicsLayer scale + shift, slots never move). Tap, or slide and let go on an icon, to open it;
  a vertical swipe that starts on the dock scrubs it instead of opening the drawer. Icons shrink to fit, never scroll.
  An entry can also be an app pair ("p|first||second"), which opens both side by side.
- Notification dots: an accent dot after apps with unread, badge-allowed, non-ongoing notifications
  (a NotificationListenerService; the user grants notification access once).
- Empty state hint. First run seeds phone / messages / camera / browser when they resolve.

**WidgetsPane**
- Vertical scroll of widgets at pane width, each with a stored height. "Add widget" at the end.
- Long-press widget → edit chrome: drag handle to resize, move up/down, reconfigure (if supported), remove.
- Rounded with the system widget radius by clipping the AppWidgetHostView's own outline, after zeroing
  Android's 8dp default widget padding (otherwise square corners sit inside the clip). A Compose clip
  around the AndroidView does nothing to the widget.
- Picker: live previews (generated → preview layout → image → icon, falling back to the image when a
  provider's layout can't inflate outside its app) and a search field.

**Drawer** (overlay)
- Search bar at the bottom above the keyboard. Results grow upward, so the best match sits right
  above the thumb. With an empty query the list is alphabetical, with icons, curved along a circle
  (middle rows farthest out), rows rolling back and fading in the outer 30% at each end, a light haptic
  tick as each row crosses the middle, and a cascade that rises with the drawer's progress.
- "Your apps on top": up to 5 most-launched apps not already on home, in a row above the search bar.
- Ranking: exact prefix > word-start prefix > initials ("gm" → Google Maps) > substring.
  Diacritics and case ignored. Ties go to the most-launched app, then alphabetical.
- From the second letter search also finds app shortcuts ("new tab"), ~30 Settings pages with everyday
  words ("internet" → Wi-Fi), and contacts; "call mom" / "text mom" put people first. Nothing found →
  a web search row (default search app, else Google in the default browser, never a chooser).
- Enter launches the top match. "Auto-open single match" (default on, as in Olauncher) waits for a
  0.3 s pause in typing, so passing through "cal" on the way to "call mom" doesn't open Calendar.
- Long-press app → menu: app shortcuts (LauncherApps), open in split screen, add to/remove from home,
  rename, hide, app info, uninstall.
- Split screen: a blank full-screen SplitActivity, once focused, opens app B with LAUNCH_ADJACENT
  (Android only splits for a full-screen requester), then app A, which Samsung places in its half.
  Works on One UI 9; stock Android sends A full screen.
- Work-profile apps included, tagged "work".

## Gestures (the "fluid" part)
One custom detector on the pane container. It locks to an axis after touch slop and decides in the
Initial pointer pass, so it can take a gesture away from children (labels, widget views).
It gives up if slop isn't crossed before the long-press timeout (so long-presses always win).

| Where | Gesture | Result |
|---|---|---|
| Home | swipe up | drawer follows finger (1:1 over ~⅓ screen), spring settle with fling velocity; keyboard starts opening on release so both animate together |
| Home | swipe down | notification shade (accessibility global action if enabled, else `expandNotificationsPanel` reflection, which Olauncher still uses at targetSdk 36) |
| Home | swipe left | rubber-band pull with two stops (haptic tick at each): the swipe-left app (default camera), then keep pulling for a second app (default phone) |
| Home | swipe right | widgets page tracks finger |
| Screen edges | swipe in | Samsung delivers edge swipes as "back"; the home's back callback reads `BackEvent.swipeEdge` + `touchX` and turns a left-edge swipe into the widgets page and a right-edge swipe into the two-stop pull, measured from the edge |
| Widgets | swipe left | back to home |
| Home empty | double-tap | lock via accessibility `GLOBAL_ACTION_LOCK_SCREEN` (keeps biometrics, unlike device admin) |
| Home/widgets empty | long-press | menu: add widget, settings |
| Favorite | long-press + drag | reorder; long-press + release → rename / remove / app info |
| Drawer | drag down at list top, or back gesture | drawer follows finger / predictive-back progress, closes. The back callback is registered at overlay priority so one back closes keyboard and drawer together |

Every animated value is read only inside `graphicsLayer {}`, so per-frame work is draw-only, with no recomposition.

## Lifecycle details
- `singleTask`, `resumeWhilePausing`, `excludeFromRecents`, `configChanges` covers fold/unfold/density/uiMode so the activity is never recreated.
- onStop: snap drawer closed, clear search, hide keyboard, return to home page. When an app covers the launcher, the reset is invisible.
- Coming back from an app: home content does a short scale/fade-in, only when onStart and onResume arrive together (a predictive back-to-home peek starts us long before resume, with the home already on screen).
- Day/night flip: widget views are rebuilt (they never re-read resources on their own).
- HOME intent while visible: close whatever is open, animated.
- Back on the bare home is swallowed by a lowest-priority callback: the platform default `finish()`es a HOME
  task root (only LAUNCHER roots get moved to the back), and the system then rebuilds the home and every widget.
  Found on the real Fold (One UI 9) after the emulator pass missed it.
- One AppWidgetHost per process; only the home starts/stops listening (wrapped, it can throw). Orphaned ids are deleted at start.
- Widget sizes reported via `updateAppWidgetSize(Bundle, List<SizeF>)` with every width the widget is shown at (cover and inner), so folding switches layouts locally.
- Widget long-press is detected in an `AppWidgetHostView` subclass's `dispatchTouchEvent`, which cancels the child gesture once fired.
- Widget config / bind launches pass BAL opt-in options (Android 14+ blocks the provider's PendingIntent otherwise).

## Pinning from other apps
`CONFIRM_PIN_SHORTCUT` (Chrome "Add to home screen", PWAs) → accepted into favorites.
`CONFIRM_PIN_APPWIDGET` → bound (system "allow widgets" dialog the first time) and added to the widgets pane.

## Themes
Wallpaper (default, like Olauncher) / System / Black (OLED) / Light. Wallpaper mode toggles `FLAG_SHOW_WALLPAPER`.
Text colour: Auto / White / Black. Auto reads `WallpaperColors.HINT_SUPPORTS_DARK_TEXT`, but Samsung's live
wallpapers report no colours at all (null), so Auto usually means white text over a soft top/bottom scrim.
Picking "Wallpaper" from the home menu also switches the theme to it.
Frosted glass: the wallpaper blurs as far as the drawer or widgets page is open. Samsung turns Android's
public window blur off, so Blur.kt calls Samsung's `View.semSetBlurInfo` by reflection (public API elsewhere);
with blur the drawer tint drops from 90% to ~40%. Without blur the drawer keeps its dark scrim.
Status bar can be hidden. Text size scale 0.8–1.4. Alignment start/center/end.

## Settings (separate activity, also the app's launcher entry "Fluid")
Default-home status + request (RoleManager), theme, text size, alignment, clock/date toggles,
status bar, swipe-left app, double-tap lock (with Samsung "Allow restricted settings" instructions
for sideloaded APKs), swipe-down shade, auto-open match, auto keyboard, hidden apps.

## Storage
One SharedPreferences file mirrored into Compose state (process-wide object). No database.
Favorites store their label too, so the home renders instantly on a cold start before the app list loads.

## Not doing (yet)
Icon packs, daily wallpapers, screen time, Private Space, the Google Discover feed (not available to
third-party launchers). Installed over adb, so notification access and accessibility aren't "restricted settings".

## Review
A second-look review against AOSP/Launcher3/androidx source changed: process-wide widget host, pin bind
flow, widget rebuild on day/night, fade-only widget transforms, overlay-priority back, `ALLOW_IF_VISIBLE`
BAL mode, Launcher3's preview-clone trick, `isAccessibilityTool`, portrait lock for the cover screen.

## Verification
Unit tests for search ranking, "call/text" parsing, categories, the dock and Halo glyphs. Real phone over adb (logcat, gfxinfo,
dumpsys activity top for the view tree): several bugs only showed up there. Pixel Fold emulator (headless, adb input/screencap): fold/unfold,
gestures, widget add/resize/remove, pin shortcut, drawer search and launch, settings.
