# MunichSpot

MunichSpot is a Kotlin Multiplatform (Compose Multiplatform) app for iOS and Android that shows Munich infrastructure from open data on a map: recycling islands (Wertstoffinseln) and mailboxes (Briefkästen).

This is an early prototype, not a finished app.

## Status

**Features:**
- About 1,600 real points (865 AWM recycling islands, 747 OSM mailboxes) from a bundled `pois.geojson`.
- Pins clustered per type: green = recycling, yellow = mailboxes. Tapping a cluster zooms in.
- Tapping a pin opens a details sheet with a Navigate action. Recycling islands list accepted materials: glass by colour, Leichtverpackungen, Altkleider.
- Filter button (bottom right) opens a filter sheet: recycling islands by material, and mailboxes.
- Locate button shows the user's position and centres the map on it. Location permission is asked only on first tap.
- Nightly data refresh: a GitHub Action runs `tools/data/build_pois.py` and publishes the result to GitHub Pages. The app shows the bundled data first, then switches to the nightly file when it is reachable.

**Platform status:**
- **iOS:** all of the above has been run and checked in the iOS simulator, not on a physical device.
- **Android:** all of the above has been run and checked on the Android emulator (Pixel Fold, API 36), not on a physical device.

## Tech stack

- Kotlin 2.4.20
- Compose Multiplatform 1.12.1 (Material 3 1.12.0-alpha03)
- Android Gradle Plugin 9.3.1
- maplibre-compose 0.20.0
- Map tiles and style: OpenFreeMap "liberty" style

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
```

To refresh the bundled data, run `tools/data/build_pois.py` (see `tools/data/README.md`).

For iOS, open `iosApp/iosApp.xcodeproj` in Xcode and run the `iosApp` scheme on a simulator. The build phase uses `JAVA_HOME`, or Android Studio's JBR if `JAVA_HOME` is not set. To run on a device, set `TEAM_ID` in `iosApp/Configuration/Config.xcconfig`.

## Data sources and attribution

- **Recycling islands:** Landeshauptstadt München, Abfallwirtschaftsbetrieb München (AWM), via opendata.muenchen.de, dataset `awm_container_opendata`. Licensed under [Datenlizenz Deutschland – Namensnennung – Version 2.0](https://www.govdata.de/dl-de/by-2-0). The source data is in EPSG:25832 and is converted to WGS84. Accepted container types (glass by colour, packaging, clothing) are included per site.
- **Mailboxes:** © OpenStreetMap contributors, [ODbL 1.0](https://www.openstreetmap.org/copyright).
- **Map tiles and style:** [OpenFreeMap](https://openfreemap.org). Data © OpenMapTiles and © OpenStreetMap contributors.

`tools/data/build_pois.py` fetches both sources and generates `shared/src/commonMain/composeResources/files/pois.geojson`.

The Apache-2.0 license below covers the code only. The data remains under its own licenses.

## Privacy

Your location is used only on the device, to draw the location dot and centre the map. The app does not send it anywhere. Map tiles for the visible area are loaded from OpenFreeMap, as for any map view. The app also downloads the data file from GitHub Pages and sends nothing else.

## License

Copyright 2026 Connor McPartland. Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE).
