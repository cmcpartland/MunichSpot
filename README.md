# MunichSpot

MunichSpot is a Kotlin Multiplatform (Compose Multiplatform) app for iOS and Android that shows Munich infrastructure from open data on a map: recycling islands (Wertstoffinseln) and mailboxes (Briefkästen).

This is an early spike, not a finished app.

## Status

- **Android:** runs. It shows 10 hardcoded sample points on a MapLibre map. Tapping a pin opens a details sheet with a Navigate action.
- **iOS:** the shared code compiles for the iOS simulator, but the Xcode host app is not set up yet.
- **Planned:** a nightly data pipeline (AWM + OpenStreetMap to GeoJSON), clustering, filters, and user location.

## Tech stack

- Kotlin 2.4.20
- Compose Multiplatform 1.12.1 (Material 3 1.12.0-alpha03)
- Android Gradle Plugin 9.3.1
- maplibre-compose 0.20.0
- Map tiles and style: OpenFreeMap "liberty" style

Modules:

- `shared/`: the Kotlin Multiplatform library. All UI and logic live here.
- `androidApp/`: thin Android entry point.
- `iosApp/`: planned, not yet created.

## Building

Requirements:

- JDK 17 or newer. Android Studio's bundled JBR works. On macOS:
  ```
  export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
  ```
- Android compileSdk 37 (AGP downloads it automatically).
- iOS, once the host app exists, will need Xcode 26 (macOS 15.6+).

Commands:

```
./gradlew :androidApp:assembleDebug
./gradlew :androidApp:installDebug
./gradlew :shared:compileKotlinIosSimulatorArm64
```

## Data sources and attribution

- **Recycling islands:** Landeshauptstadt München, Abfallwirtschaftsbetrieb München (AWM), via opendata.muenchen.de, dataset `awm_container_opendata`. Licensed under [Datenlizenz Deutschland – Namensnennung – Version 2.0](https://www.govdata.de/dl-de/by-2-0). The source data is in EPSG:25832 and is converted to WGS84.
- **Mailboxes:** © OpenStreetMap contributors, [ODbL 1.0](https://www.openstreetmap.org/copyright).
- **Map tiles and style:** [OpenFreeMap](https://openfreemap.org). Data © OpenMapTiles and © OpenStreetMap contributors.

The Apache-2.0 license below covers the code only. The data remains under its own licenses.

## License

Copyright 2026 Connor McPartland. Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE).
