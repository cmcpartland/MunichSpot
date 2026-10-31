# PublicPoint – notes for Claude

A Kotlin Multiplatform / Compose Multiplatform app for Android and iOS. It maps Munich open data: recycling islands, mailboxes, drinking fountains and public toilets. Read `README.md` for features, the tech stack, build commands and data sources, and `tools/data/README.md` for the data pipeline. This file covers only what the READMEs don't.

## Code map (`shared/src/commonMain/kotlin/de/publicpoint/`)
- `App.kt`: loads data (cache, then the nightly download on launch and on every foreground resume; no bundled data; states loading/ready/failed), and holds the selected category (`PoiType?`, null = all) and `PoiFilters`.
- `data/`:
  - `Poi.kt`: model (incl. `schedule` for any type), `PoiType`, `Material`, `ToiletInfo`, `RecyclingCentreInfo`, `BookcaseInfo`.
  - `PoiRepository.kt`: `PoiData` (pois + `generated`), `loadStartupPois` (cache or null), `isPlausible`, `POI_SCHEMA_VERSION` (`parseLatest` rejects a newer or non-integer top-level `schemaVersion`, a missing one is fine), the `LATEST_POIS_URL` fetch (`FetchResult`: Updated/Unchanged/Failed; ETag stored next to the cache, `If-None-Match` only with both), `freshnessHint`. `parsePois` derives `CLOTHES` pois (id `<island>-clothes`) from recycling islands with `Material.CLOTHING` and strips CLOTHING from the island; the geojson has no clothes type.
  - `Schedule.kt`: `ScheduleRule` + `List<ScheduleRule>.isOpen(LocalDateTime)` (overnight spill included), `parseSchedule`, and `rememberBerlinNow()` (Europe/Berlin, minute resolution, ticks only while the app is started; `App.kt` passes `now` to `MapScreen`). `Poi.knownClosed(now)` (in `PoiFilters.kt`) is true only with a schedule saying closed. Toilets and recycling centres (Wertstoffhöfe, `RECYCLING_CENTRE`; hours from OSM via `tools/data/osm_hours.py`) have schedules.
  - `Place.kt`, `PlaceSearch.kt`: the place index (bundled `files/places.json`, `{generated, places}`, built by `tools/data/build_places.py`; refreshed from `PLACES_URL` silently once per app start, a cached or downloaded copy is used only if plausible and its `generated` is strictly newer than the current one) and `PlaceIndex.search` (normalised prefix/word/substring ranking, ä/ß/"str." folded).
  - `DataCache*.kt`, `RemoteData.kt`: `DataCache(fileName)` (per-platform dir, atomic writes, ETag file next to it) and the shared `fetchConditional`/`loadCached` behind the pois and places downloads.
- `map/MapScreen.kt`: the map. Its layers live in `PoiLayers.kt`, pins and painters in `MapPins.kt`, GeoJSON building in `MapGeoJson.kt`, and the controls (`MapControls`, `LocatePill`, `DataNotice`, `AttributionBar`) in `MapControls.kt`; colours and labels in `Categories.kt`, `boundsAround` in `RevealZoom.kt`. It holds:
  - one clustered GeoJSON source per type
  - the layer order: per type its icons, the toilet warning mark (`PoiWarningLayer`, for `Poi.possiblyDefect`), the clock mark (`PoiClosedLayer`, pois with a schedule (`PoiType.hasSchedule()`: toilets, recycling centres, bookcases) that are known closed, bottom-right; the pin itself is drawn at 50 % opacity via the `closed` GeoJSON property), badge circles and counts together, so a badge stacks with its own pin; those sources are only rebuilt when the set of closed ids changes, not every minute; the location puck on top
  - the opening view (`boundsAround`, 700 m radius, Marienplatz fallback, moving to the user if the fix is in Munich)
  - the selected-pin highlight: `SelectedPinLayers` (a one-feature source, so it pans with the map), with the overlay `SelectedPin` shown only until a frame polling `queryRenderedFeatures` finds the layer on screen
  - the reveal zoom: picking a category (or filter) with none of its pins on screen slowly zooms out, centre fixed, to the nearest two (`map/RevealZoom.kt`, plain Mercator math)
  - the anchor pin (`AnchorPinPainter`, a symbol layer on top of the map, so it moves with panning): picking a place animates to it (1.5 km for districts, else 700 m), then runs the reveal zoom for the selected category; clearing it flies back to the user's fix if it's in Munich
  - the filter button and the round locate pill (`LocatePill`; it shows after a gesture pan while the picker is collapsed, and hides once the camera is back on the user's fix). Compact (map < 680 dp wide): one bottom-end column above the picker, filter directly above the picker, locate above it. Wide (>= 680 dp, e.g. the unfolded Fold; below that the buttons could overlap a full-width picker): locate and filter in the picker's row at the right end (filter last). Either way the filter stays put while the locate pill comes and goes
  - the attribution bar (plus "Datenstand: …" when `generated` is > 30 days old, "Daten seit über einem Jahr nicht aktualisiert" beyond 365)
  - `DataNotice`: top-centre pill "Lade Daten …" / "Keine Daten …" + retry while there is no data
- `map/CategoryPicker.kt`: the bottom pill, two segments: the category, and the place (the anchor name, or the hint "Ort suchen"). Each segment has its own ✕ while set (category, anchor). Tapping either expands a panel with a place search field above the category pills; the place segment also focuses the field. Picking a place sets the anchor (`App.kt` state). Back only collapses the panel; it never clears the category or anchor (the ✕ buttons do). The picker is always centred at full width.
- `map/Categories.kt`: `hasFilters()`.
- `map/CategoryIcon.kt`: `drawCategoryIcon`, shared by the picker (`CategoryIcon`) and the map pins (`CategoryIconPainter`).
- `map/PoiFilters.kt` and `map/FilterSheet.kt`: filters per category; only recycling, recycling centres (open now) and toilets have options.
- `map/PoiSheet.kt`: the details sheet. `map/Navigation*.kt`: `openDirections` per platform (Android: Google Maps URL; iOS tries the default navigation app via `geo-navigation:`, then Apple Maps, Google Maps, the web, moving on only when opening fails).
- `map/Icons.kt`: all UI icons, drawn with Canvas; there's no icon library.

## Conventions
- UI text follows the device language (German on German devices, English otherwise). It lives in `shared/src/commonMain/composeResources/values/strings.xml` (English, fallback) and `values-de/strings.xml` (German); add every new string to both and use `stringResource(Res.string.x)` (`getString` outside composables). Non-UI code returns values, not text. iOS permission text is in `iosApp/iosApp/{en,de}.lproj/InfoPlist.strings`. Code is concise with few comments, and map-package code is `internal`.
- New icons are drawn with Canvas. Don't add icon dependencies.
- Signals derived from dataset free text (e.g. toilet `zustand`) are hints: show a warning mark and hedged "Laut Stadt …" text, never filter or branch on them in the app; text matching lives only in `build_pois.py`. Stated schedules are the exception: `build_pois.py` turns toilet `hours` into a structured `schedule` with a strict whitelist parser (`tools/data/hours.py`, unknown when unsure), the app may filter on it (`OPEN_NOW`), and unknown hours never hide a pin.

## Gotchas
- Overlay (`placedAt`) content lags behind the map while panning, so use it only for transient things like the selected-pin bridge; anything persistent belongs in a layer.
- Clothes pins sit on their island's exact point, so `MapScreen` draws the clothes layers, the selected-pin layers and overlay and the keep-visible pan with `ClothesOffset` (16 dp right, 10 dp up, viewport-anchored).
- Android needs `androidResources.enable = true` in `shared/build.gradle.kts`, or `files/places.json` and `munich_boundary.geojson` are missing at runtime.
- The OpenFreeMap styles (we use "positron") only have Noto Sans fonts. Use `"Noto Sans Bold"` for map text.
- `toGeoJson` must escape control characters. A bad name once made a whole layer vanish silently.
- `BackHandler` from `ui-backhandler` is deprecated in CMP 1.12; the replacement is the navigationevent API.
- Map style changes (source data, layer filters) take about 0.4 s to appear on the emulator. For instant feedback on a tap, such as the selected-pin highlight, draw in `MaplibreMap`'s `overlay` with `Modifier.placedAt(position)` only until the layer is rendered.
- Dark mode follows the system; the dark map style is OpenFreeMap "fiord" (it only lists Noto Sans Regular, but "Noto Sans Bold" is served from the shared font endpoint).
- Kotlin/Native can't pass a Kotlin `String` as a C vararg (`NSLog("%@", s)` crashes with `EXC_BAD_ACCESS`). `Log.ios.kt` passes the message as the format, with `%` escaped.
- Overpass (mailboxes, centres, bookcases) is flaky. `fetch` retries and rotates servers; if all fail, `build_pois.py` reuses the published `osm-elements.json` and warns.

## Local test loop (this Mac)
- `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`. The Android SDK is at `~/Library/Android/sdk`.
- **Android:**
  - Emulator AVD `Pixel_Fold_API_36` (emulator-5554), package `de.publicpoint`.
  - Run `./gradlew :androidApp:installDebug`, then `adb shell monkey -p de.publicpoint 1`.
  - The emulator has two displays, so screenshots need `adb exec-out screencap -d 4619827259835644672 -p`.
- **iOS:**
  - Simulator UDID `544C056F-40A3-4E8B-AFEE-C6D6E362D57C`, bundle `de.publicpoint.ios`.
  - Build: `xcodebuild -quiet -project iosApp/iosApp.xcodeproj -scheme iosApp -destination 'id=<udid>' -derivedDataPath <dd> build`.
  - Then `xcrun simctl install <udid> <dd>/Build/Products/Debug-iphonesimulator/PublicPoint.app` and `xcrun simctl launch`.
  - This Xcode has no Simulator.app; the simulator window lives in `Xcode.app/Contents/Applications/DeviceHub.app`.
  - The simulator hides the on-screen keyboard while the Mac keyboard is connected (⌘K toggles it).
- Keep screenshots and DerivedData out of the repo.

## Workflow
- Work on `feature/<name>` branches. The user tests on the emulator and simulator, then says "merge and push". Merge with `--no-ff`, push `main`, and delete the branch.
- Hand implementation to Sonnet or Haiku subagents. The main session plans, reviews (including screenshots) and talks to the user.

## Open ideas (not started)
- Android baseline profile (the `baselineprofile` plugin).
- TestFlight upload, once there's a paid Apple Developer team.
- With all categories shown, the city centre is crowded; a possible fix is a bigger cluster radius.
