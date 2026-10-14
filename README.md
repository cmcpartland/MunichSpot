# MunichSpot

MunichSpot is a Kotlin Multiplatform (Compose Multiplatform) app for iOS and Android that shows Munich infrastructure from open data on a map: recycling islands (Wertstoffinseln), mailboxes (Briefkästen), drinking fountains (Trinkbrunnen) and public toilets (öffentliche Toiletten).

This is an early prototype, not a finished app.

## Status

**Features:**
- About 1,900 real points (865 AWM recycling islands, 747 OSM mailboxes, 115 drinking fountains, 165 public toilet locations) from a bundled `pois.geojson`.
- The app opens on the map with a semi-transparent search bar at the top. Tapping it expands a list of the four categories (with the number of places), filtered as you type; choosing one shows only that category on the map, and the bar then shows it with a button to clear the selection.
- The map opens framed on a 700 m radius (walking distance) around the user (if location is allowed and the fix is in Munich), otherwise around Marienplatz.
- Pins clustered per type: green = recycling, yellow = mailboxes, light blue = drinking fountains, purple = public toilets. Tapping a cluster zooms in.
- Tapping a pin opens a details sheet with a Navigate action. Recycling islands list accepted materials: glass by colour, Leichtverpackungen, Altkleider.
- Filter button (bottom right, only while the recycling or toilet category is selected) opens a filter sheet: recycling islands by material, public toilets by requirement (accessible, free, with baby changing, hide defective ones).
- Drinking fountains show their name and remark; many are shut off in winter.
- Public toilets show address, opening hours, price, floor, accessibility, baby changing, Euro key, room types and a warning if the city lists them as defective.
- Locate button shows the user's position and centres the map on it. Location permission is asked when a map is first opened.
- Nightly data refresh: a GitHub Action runs `tools/data/build_pois.py` and publishes the result to GitHub Pages. On launch the app shows the last downloaded file if it has one, otherwise the bundled data, then switches to the nightly file when it is reachable. This also works offline.

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
- **Drinking fountains:** Landeshauptstadt München, via opendata.muenchen.de, dataset `opendata_trinkwasserbrunnen`. Licensed under [Datenlizenz Deutschland – Namensnennung – Version 2.0](https://www.govdata.de/dl-de/by-2-0). The source data is in EPSG:25832 and is converted to WGS84. Winter shut-off is not fully in the data.
- **Public toilets:** Landeshauptstadt München, via opendata.muenchen.de, dataset `wc_finder`. Licensed under [Datenlizenz Deutschland – Namensnennung – Version 2.0](https://www.govdata.de/dl-de/by-2-0). The source data is in EPSG:25832 and is converted to WGS84. The dataset lists one entry per room; rooms within 15 m of each other are merged into one location. Only city-owned toilets are included.
- **Map tiles and style:** [OpenFreeMap](https://openfreemap.org). Data © OpenMapTiles and © OpenStreetMap contributors.

`tools/data/build_pois.py` fetches all sources and generates `shared/src/commonMain/composeResources/files/pois.geojson`.

The Apache-2.0 license below covers the code only. The data remains under its own licenses.

## Privacy

Your location is used only on the device, to draw the location dot and centre the map. The app does not send it anywhere. Map tiles for the visible area are loaded from OpenFreeMap, as for any map view. The app also downloads the data file from GitHub Pages and sends nothing else.

## License

Copyright 2026 Connor McPartland. Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE).
