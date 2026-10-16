# MunichSpot – notes for Claude

A Kotlin Multiplatform / Compose Multiplatform app for Android and iOS. It maps Munich open data: recycling islands, mailboxes, drinking fountains and public toilets. Read `README.md` for features, the tech stack, build commands and data sources, and `tools/data/README.md` for the data pipeline. This file covers only what the READMEs don't.

## Code map (`shared/src/commonMain/kotlin/de/munichspot/`)
- `App.kt`: loads data (cache or bundled data, then the nightly download), and holds the selected category (`PoiType?`, null = all) and `PoiFilters`.
- `data/`:
  - `Poi.kt`: model, `PoiType`, `Material`, `ToiletInfo`.
  - `PoiRepository.kt`: loading, `isPlausible`, the `LATEST_POIS_URL` fetch. `parsePois` derives `CLOTHES` pois (id `<island>-clothes`) from recycling islands with `Material.CLOTHING` and strips CLOTHING from the island; the geojson has no clothes type.
  - `Place.kt`, `PlaceSearch.kt`: the place index (`files/places.json`, built by `tools/data/build_places.py`) and `PlaceIndex.search` (normalised prefix/word/substring ranking, ä/ß/"str." folded).
  - `PoiCache*.kt`: per-platform cache file with atomic writes.
- `map/MapScreen.kt`: the map. It holds:
  - one clustered GeoJSON source per type
  - the layer order: per type its icons, the toilet warning mark (`PoiWarningLayer`, for `Poi.possiblyDefect`), badge circles and counts together, so a badge stacks with its own pin; the location puck on top
  - the opening view (`boundsAround`, 700 m radius, Marienplatz fallback, moving to the user if the fix is in Munich)
  - the selected-pin highlight (`SelectedPin`), drawn in the map overlay, not as a layer
  - the reveal zoom: picking a category (or filter) with none of its pins on screen slowly zooms out, centre fixed, to the nearest two (`map/RevealZoom.kt`, plain Mercator math)
  - the anchor pin (`AnchorPin`, overlay): picking a place animates to it (1.5 km for districts, else 700 m), then runs the reveal zoom for the selected category; clearing it flies back to the user's fix if it's in Munich
  - the locate and filter buttons, and the attribution bar
- `map/CategoryPicker.kt`: the bottom pill, two segments: the category, and the place (the anchor name, or a hint: "Mein Standort" while the location dot is visible in the viewport, else "Ort suchen"). Each segment has its own ✕ while set (category, anchor). Tapping either expands a panel with a place search field above the category pills; the place segment also focuses the field. Picking a place sets the anchor (`App.kt` state). Back collapses the panel, then clears the category, then the anchor.
- `map/Categories.kt`: `hasFilters()`.
- `map/CategoryIcon.kt`: `drawCategoryIcon`, shared by the picker (`CategoryIcon`) and the map pins (`CategoryIconPainter`).
- `map/PoiFilters.kt` and `map/FilterSheet.kt`: filters per category; only recycling and toilets have options.
- `map/PoiSheet.kt`: the details sheet. `map/Navigation*.kt`: the directions URI per platform.
- `map/Icons.kt`: all UI icons, drawn with Canvas; there's no icon library.

## Conventions
- UI text is German. Code is concise with few comments, and map-package code is `internal`.
- New icons are drawn with Canvas. Don't add icon dependencies.
- Signals derived from dataset free text (e.g. toilet `zustand`) are hints: show a warning mark and hedged "Laut Stadt …" text, never filter or branch on them in the app; text matching lives only in `build_pois.py`.

## Gotchas
- Clothes pins sit on their island's exact point, so `MapScreen` draws the clothes layers, the selected-pin overlay and the keep-visible pan with `ClothesOffset` (16 dp right, 10 dp up, viewport-anchored).
- Android needs `androidResources.enable = true` in `shared/build.gradle.kts`, or `files/pois.geojson` is missing at runtime.
- The OpenFreeMap styles (we use "positron") only have Noto Sans fonts. Use `"Noto Sans Bold"` for map text.
- `toGeoJson` must escape control characters. A bad name once made a whole layer vanish silently.
- `BackHandler` from `ui-backhandler` is deprecated in CMP 1.12; the replacement is the navigationevent API.
- Map style changes (source data, layer filters) take about 0.4 s to appear on the emulator. For instant feedback on a tap, such as the selected-pin highlight, draw in `MaplibreMap`'s `overlay` with `Modifier.placedAt(position)` instead of adding a layer.
- Dark mode follows the system; the dark map style is OpenFreeMap "fiord" (it only lists Noto Sans Regular, but "Noto Sans Bold" is served from the shared font endpoint).
- Overpass (mailboxes) often returns 504. The nightly workflow retries, and a failed run keeps the last deploy live.

## Local test loop (this Mac)
- `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`. The Android SDK is at `~/Library/Android/sdk`.
- **Android:**
  - Emulator AVD `Pixel_Fold_API_36` (emulator-5554), package `de.munichspot`.
  - Run `./gradlew :androidApp:installDebug`, then `adb shell monkey -p de.munichspot 1`.
  - The emulator has two displays, so screenshots need `adb exec-out screencap -d 4619827259835644672 -p`.
- **iOS:**
  - Simulator UDID `544C056F-40A3-4E8B-AFEE-C6D6E362D57C`, bundle `de.munichspot.ios`.
  - Build: `xcodebuild -quiet -project iosApp/iosApp.xcodeproj -scheme iosApp -destination 'id=<udid>' -derivedDataPath <dd> build`.
  - Then `xcrun simctl install <udid> <dd>/Build/Products/Debug-iphonesimulator/MunichSpot.app` and `xcrun simctl launch`.
  - This Xcode has no Simulator.app; the simulator window lives in `Xcode.app/Contents/Applications/DeviceHub.app`.
  - The simulator hides the on-screen keyboard while the Mac keyboard is connected (⌘K toggles it).
- Keep screenshots and DerivedData out of the repo.

## Workflow
- Work on `feature/<name>` branches. The user tests on the emulator and simulator, then says "merge and push". Merge with `--no-ff`, push `main`, and delete the branch.
- Hand implementation to Sonnet or Haiku subagents. The main session plans, reviews (including screenshots) and talks to the user.

## Open ideas (not started)
- More reliable nightly mailbox fetch: a second Overpass server, and reuse the last mailboxes if all fail.
- "Trinkbrunnen" shows twice in the fountain details sheet.
- Android release setup: R8, a signing config, and a baseline profile.
- With all categories shown, the city centre is crowded; a possible fix is a bigger cluster radius.
