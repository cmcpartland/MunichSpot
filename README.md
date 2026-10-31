# PublicPoint

PublicPoint is a Kotlin Multiplatform (Compose Multiplatform) app for iOS and Android that shows Munich infrastructure from open data on a map: recycling islands (Wertstoffinseln), clothes donation containers (Altkleidercontainer), mailboxes (Briefkästen), drinking fountains (Trinkbrunnen) and public toilets (öffentliche Toiletten).

This is an early prototype, not a finished app.

## Status

**Features:**
- About 1,900 real points (864 AWM recycling islands, 265 clothes containers, 747 OSM mailboxes, 115 drinking fountains, 165 public toilet locations) from `pois.geojson`, downloaded at runtime (nothing is bundled).
- The app opens on the map with a category pill at the bottom. Tapping it expands a panel of the five categories (and "Alle"); choosing one shows only that category on the map, and the pill then shows it with a button to clear the selection.
- Place search: the expanded panel has a "Wo?" field that finds streets, parks, squares, stations, landmarks and districts from an OSM index (`places.json`, bundled and refreshed weekly from the same site as the POIs) and moves the map there, so you can combine it with a category (e.g. toilets near the Englischer Garten).
- The map opens framed on a 700 m radius (walking distance) around the user (if location is allowed and the fix is in Munich), otherwise around Marienplatz.
- Pins clustered per type: green = recycling, red = clothes containers (shown offset beside their island), yellow = mailboxes, light blue = drinking fountains, purple = public toilets. Tapping a cluster zooms in.
- Tapping a pin opens a details sheet with a Navigate action. Recycling islands list accepted materials: glass by colour, Leichtverpackungen.
- Filter button (bottom right, only while the recycling or toilet category is selected) opens a filter sheet: recycling islands by material (glass, packaging), public toilets by requirement (accessible, free, with baby changing).
- Drinking fountains show their name and remark; many are shut off in winter.
- Public toilets show address, opening hours, price, floor, accessibility, baby changing, Euro key, room types and a warning if the city lists them as defective.
- Languages: the UI follows the device language, German on German devices and English otherwise (per-app language setting supported on Android 13+ and iOS).
- Dark mode follows the system setting; the map switches to OpenFreeMap's "fiord" style.
- Locate button shows the user's position and centres the map on it. Location permission is asked when a map is first opened.
- Nightly data refresh: a GitHub Action runs `tools/data/build_pois.py` and publishes the result to GitHub Pages. On launch the app shows the last downloaded file if it has one (so it works offline), then checks for a newer one (conditional request via ETag) on launch and whenever it returns to the foreground. With no cache and no network it shows "Keine Daten" with a retry button. If the data is more than 30 days old, the attribution bar shows its date.

**Platform status:**
- **iOS:** all of the above has been run and checked in the iOS simulator, not on a physical device.
- **Android:** all of the above has been run and checked on the Android emulator (Pixel Fold, API 36), not on a physical device.

## Tech stack

- Kotlin 2.4.20
- Compose Multiplatform 1.12.1 (Material 3 1.12.0-alpha03)
- Android Gradle Plugin 9.3.1
- maplibre-compose 0.20.0
- Map tiles and style: OpenFreeMap "positron" style

Modules:

- `shared/`: the Kotlin Multiplatform library. All UI and logic live here.
- `androidApp/`: thin Android entry point.
- `iosApp/`: thin SwiftUI host app (Xcode project). A build phase runs Gradle to build the `Shared` framework.

## Building

Requirements:

- JDK 17 or newer. Android Studio's bundled JBR works. On macOS:
  ```
  export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
  ```
- Android compileSdk 37 (AGP downloads it automatically).
- iOS: Xcode 26 or newer (tested with Xcode 27.0). Deployment target is iOS 18.5, which is what the bundled MapLibre Native binaries are built for.

Commands:

```
./gradlew :androidApp:assembleDebug
./gradlew :androidApp:installDebug
./gradlew :shared:compileKotlinIosSimulatorArm64
./gradlew :shared:testAndroidHostTest        # unit tests on the JVM
./gradlew :shared:iosSimulatorArm64Test      # the same tests on the iOS simulator (macOS)
```

The tests are in `shared/src/commonTest`; `.github/workflows/app-ci.yml` runs them plus a release build on every push and PR.

To build the data locally, run `tools/data/build_pois.py` (see `tools/data/README.md`).

For iOS, open `iosApp/iosApp.xcodeproj` in Xcode and run the `iosApp` scheme on a simulator. The build phase uses `JAVA_HOME`, or Android Studio's JBR if `JAVA_HOME` is not set. To run on a device, put `TEAM_ID=<your team id>` in `iosApp/Configuration/Local.xcconfig` (gitignored, included by `Config.xcconfig`). Don't pick the team in Xcode's Signing tab, because that writes it into `project.pbxproj`.

### Release builds

- **Android:** `./gradlew :androidApp:bundleRelease` (or `assembleRelease` for an APK) builds with R8 and resource shrinking. The upload key is read from `release.storeFile`, `release.storePassword`, `release.keyAlias` and `release.keyPassword` in `local.properties`, or from the `PUBLICPOINT_STORE_FILE`, `PUBLICPOINT_STORE_PASSWORD`, `PUBLICPOINT_KEY_ALIAS` and `PUBLICPOINT_KEY_PASSWORD` environment variables. Without them the release build is signed with the debug key and Gradle prints a warning. The AAB includes the R8 mapping file for Play Console.
- **Version:** `appVersion` (major.minor) and `buildNumber` in `androidApp/build.gradle.kts` (`versionName`, `versionCode`). On iOS, `MARKETING_VERSION` and `CURRENT_PROJECT_VERSION` in `Config.xcconfig` hold the same two values. Bump the build number for every store upload; both stores reject a repeated one.
- **iOS:** the privacy manifest is `iosApp/iosApp/PrivacyInfo.xcprivacy` (no tracking, no collected data, plus the required-reason APIs found in the binary). Archiving for TestFlight needs a paid Apple Developer team.

### Crash reports

The app has no crash-reporting SDK. Crashes reach the stores' own consoles:

- **Android:** Play Console → Android vitals (crashes and ANRs). Stack traces are deobfuscated with the R8 mapping file in the AAB. To retrace a stack locally: `java -cp "/Applications/Android Studio.app/Contents/plugins/android/lib/r8.jar" com.android.tools.r8.retrace.Retrace androidApp/build/outputs/mapping/release/mapping.txt stack.txt`.
- **iOS:** Xcode Organizer → Crashes, or App Store Connect. Reports come only from users who share analytics with developers. Release builds produce a dSYM that includes the Kotlin code (the `Shared` framework is static). Archives upload it with the build. To resolve an address locally: `atos -o PublicPoint.app.dSYM/Contents/Resources/DWARF/PublicPoint -arch arm64 -l <load address> <address>`. An uncaught Kotlin exception shows the frames where Kotlin/Native caught it (e.g. Compose's `setContent` or the coroutine dispatcher), not the line that threw, and the report doesn't include the exception message.
- **Testing the path:** commit `d633aab` adds a crash trigger: typing `crashtest!` into the place search throws. Cherry-pick it onto a temporary build for internal testing or TestFlight, and never onto a release.

## Data sources and attribution

- **Recycling islands:** Landeshauptstadt München, Abfallwirtschaftsbetrieb München (AWM), via opendata.muenchen.de, dataset `awm_container_opendata`. Licensed under [Datenlizenz Deutschland – Namensnennung – Version 2.0](https://www.govdata.de/dl-de/by-2-0). The source data is in EPSG:25832 and is converted to WGS84. Accepted container types (glass by colour, packaging, clothing) are included per site. Clothes containers are not a separate source: the app derives them from the islands that accept clothing.
- **Mailboxes:** © OpenStreetMap contributors, [ODbL 1.0](https://www.openstreetmap.org/copyright).
- **Drinking fountains:** Landeshauptstadt München, via opendata.muenchen.de, dataset `opendata_trinkwasserbrunnen`. Licensed under [Datenlizenz Deutschland – Namensnennung – Version 2.0](https://www.govdata.de/dl-de/by-2-0). The source data is in EPSG:25832 and is converted to WGS84. Winter shut-off is not fully in the data.
- **Public toilets:** Landeshauptstadt München, via opendata.muenchen.de, dataset `wc_finder`. Licensed under [Datenlizenz Deutschland – Namensnennung – Version 2.0](https://www.govdata.de/dl-de/by-2-0). The source data is in EPSG:25832 and is converted to WGS84. The dataset lists one entry per room; rooms within 15 m of each other are merged into one location. Only city-owned toilets are included.
- **Place search index:** © OpenStreetMap contributors, [ODbL 1.0](https://www.openstreetmap.org/copyright), built by `tools/data/build_places.py`.
- **Map tiles and style:** [OpenFreeMap](https://openfreemap.org). Data © OpenMapTiles and © OpenStreetMap contributors.

`tools/data/build_pois.py` fetches all sources and writes `tools/data/out/pois.geojson` (gitignored).

The Apache-2.0 license below covers the code only. The data remains under its own licenses.

## Privacy

Your location is used only on the device, to draw the location dot and centre the map. The app does not send it anywhere. Map tiles for the visible area are loaded from OpenFreeMap, as for any map view. The app also downloads the data file from GitHub Pages and sends nothing else. The full privacy policy (English and German) is at https://cmcpartland.github.io/PublicPoint/privacy.html, from `pages/privacy.html`.

## License

Copyright 2026 Connor McPartland. Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE).
