# Building

| | |
|---|---|
| JDK | 21 (`brew install openjdk@21`, or Android Studio's bundled JDK) |
| Android SDK | platform 37, build-tools, platform-tools (`brew install --cask android-commandlinetools`) |
| Gradle | wrapper included (`./gradlew`) |
| Node | optional, only for the `npm run` shortcuts |
| Phone | Android 12+ with USB or wireless debugging |

## First run

```bash
echo "sdk.dir=/path/to/android/sdk" > local.properties   # gitignored
export JAVA_HOME=/path/to/jdk-21                            # the npm scripts default to Homebrew's openjdk@21
npm run dev                                                 # = ./gradlew :app:installRelease + adb compile -m speed
```

`installRelease` installs on every connected device. The release build is minified (R8), shrinks resources,
ships a baseline profile, and is signed with your debug key. Reinstalling from another machine needs an
uninstall first because the debug key differs.

## Tests

```bash
npm test     # ./gradlew :app:testDebugUnitTest
```

Plain JUnit, no device: `SearchTest`, `CategoriesTest`, `DockTest`, `HaloTest` (every named glyph exists in
`assets/glyphs.txt`), `ClockTest`.

## Layout

```
app/build.gradle.kts            SDK levels, Compose BOM, release signing
app/src/main/AndroidManifest.xml  HOME activity, pin/split activities, dot and lock services
app/src/main/java/app/fluid/launcher/  all Kotlin (see ARCHITECTURE.md)
app/src/main/assets/glyphs.txt  Phosphor path data, generated
app/src/main/assets/glyph-map.txt  your own package -> glyph pairs (optional, gitignored)
app/src/main/res/font/          Montserrat (OFL)
app/src/main/baseline-prof.txt  baseline profile rules
app/src/test/                   unit tests
Tools/glyphs.py                 fetch glyph paths from the Phosphor npm package
Tools/screenshots.sh            regenerate docs/screenshots on an emulator
```

## Adding a Halo icon

1. Find the glyph name on [phosphoricons.com](https://phosphoricons.com) (regular weight).
2. Add `"com.example.app" to "glyph-name"` to `GLYPHS` in `Halo.kt`, or a `com.example.app<TAB>glyph-name` line
   to `app/src/main/assets/glyph-map.txt` for an app only you use.
3. `python3 Tools/glyphs.py` (needs network) rewrites `assets/glyphs.txt`. `npm test` checks nothing is missing.

## Adding a drawer category word

Edit `WORDS` in `Categories.kt`. Order matters: the first section whose word appears in the package or label wins,
so put specific words in earlier sections. Add a case to `CategoriesTest`.

## Regenerating screenshots

`sh Tools/screenshots.sh` boots a headless, read-only Pixel 9 Pro Fold AVD named `fold`, installs the release APK,
sets Fluid as home and captures home, apps, widgets and settings into `docs/screenshots/`. Look at each image
before committing. Never use a personal phone for these.

## Not supported yet

- Non-Samsung blur is written against the public API but only checked on the emulator.
- Icon packs, Private Space and the Discover feed (not available to third-party launchers).
