# Production readiness: audit and plan

Audit date: 2026-10-25, at `cd40941` on `main`. Scope: the app (`shared/`, `androidApp/`, `iosApp/`). The nightly data pipeline already has validation, a run history and a Healthchecks.io ping (`tools/data/OBSERVABILITY.md`), so it is covered here only where the app depends on it.

## Summary

The data layer is in good shape: atomic cache writes, an ETag next to the cache, plausibility checks before anything is used or cached, `CancellationException` rethrown, and a 10 s request timeout (the file is 58 KB gzipped). What's missing for production:

1. **Offline cold start.** The map style comes from `tiles.openfreemap.org`, and our pin layers are part of that style. If the style fails to load, the map stays blank with **no pins, even with cached POIs**, and nothing tells the user why.
2. **No logging and no error reporting at all.** Every failure ends in a `catch (e: Exception) { null }` or `FetchResult.Failed` with no reason recorded. There are no crash reports.
3. **One unbounded per-frame loop** (the selected-pin poll) that never stops if the layer never renders.
4. **A plausibility rule that can wipe a valid cache** after an app update.
5. **No release setup and no Kotlin tests.** Release builds have no R8 and no signing, iOS has no privacy manifest, and CI runs only the Python tests.

## Findings

Severity: **High** = users can lose the app's core function or it can crash; **Medium** = degraded behaviour or blind spots; **Low** = hygiene.

### Hangs and battery

| # | Sev | Where | Finding |
|---|-----|-------|---------|
| H1 | High | `MapScreen.kt`, `LaunchedEffect(selected?.id)` with `while (selectedOnMap != poi.id) { withFrameNanos {} … queryRenderedFeatures }` | Runs every frame until the selected-pin layer shows up. If it never does (style failed, the pin is under a sheet edge, or a rendering quirk), it calls `queryRenderedFeatures` 60–120 times a second for as long as the pin stays selected: CPU and battery drain, and jank on low-end devices. It needs a frame or time cap (e.g. 1.5 s), and should keep the overlay as the fallback. |
| H2 | Low | `MapScreen.kt`, the `geoJson` map | `toGeoJson` for every type runs on the main thread during composition whenever pois, filters or `closedIds` change (~2,000 pois). Probably fine, but measure on a low-end Android device; if a frame takes more than 16 ms, move it to `Dispatchers.Default` via `produceState`. |
| H3 | Low | `CategoryPicker.kt`, `places?.search(query)` in `remember` | Linear scan of 6,631 names on each keystroke, on the main thread. Fine today; just keep an eye on it if the index grows. |
| H4 | Low | `RemoteData.kt` | `cache.save` (≈0.5 MB write) runs on `Dispatchers.Default`, not `IO`. Harmless at this size; use `Dispatchers.IO` for file work. |

Checked and fine: the `refreshing` guard is reset in `finally`. Location waits have timeouts (10 s and 15 s). The minute ticker only runs while the app is STARTED. Waits like `snapshotFlow { sheetBounds }.first()` are cancelled with their effect.

### Error handling and crashes

| # | Sev | Where | Finding |
|---|-----|-------|---------|
| E1 | High | `Navigation.android.kt`, `PoiSheet.kt` (`WERTSTOFFHOF_URL`) | `uriHandler.openUri` throws `IllegalArgumentException` on Android when no app can handle the URL (no browser, restricted profiles). That's an uncaught crash. Wrap it, and show a snackbar on failure. |
| E2 | Medium | `PoiLayers.kt`, `zoomToCluster` → `scope.launch { getClusterExpansionZoom … }` | An exception inside a `rememberCoroutineScope` launch crashes the app. If the source is gone (category switched mid-tap), it could throw. Wrap it in `runCatching` and fall back to `zoom + 2` (that fallback already exists for null). |
| E3 | Medium | `PoiRepository.kt`, `isPlausible` | Needs **every** `PoiType` to be present. (a) When an app update adds a type (as with bookcases), the old cached file is rejected and **deleted** on first launch, so a user who updates while offline has no data. (b) If the pipeline ever drops a type on purpose, every installed app rejects all future downloads. Base plausibility on the total count plus the long-standing core types, and keep a cache that is valid but older. |
| E4 | Medium | `PoiRepository.kt`, `parseFeatures` | One malformed feature (e.g. missing `geometry`) throws, and that rejects the whole file. The pipeline validates the file, so this is defence in depth: skip bad features and reject only if too many are skipped. |
| E5 | Low | Data contract | `pois.geojson` has no `schemaVersion`. Add one, so that future breaking changes can be published next to the old format (e.g. `pois.v2.geojson`) instead of breaking installed apps. |
| E6 | Low | `MapScreen.kt` | No UI for a failed map style (see O1). |

### Offline behaviour

| # | Sev | Where | Finding |
|---|-----|-------|---------|
| O1 | High | `MapScreen.kt`, `BaseStyle.Uri("https://tiles.openfreemap.org/…")` | MapLibre's ambient cache probably serves the style, sprites, glyphs and recent tiles after the first online run, but this is **untested**. On a first launch offline, or after the ambient cache is evicted, the style fails. Our sources and layers live inside the style, so the cached POIs are invisible too. maplibre-compose 0.20 exposes `mapState.style.loadState` (`StyleLoadState.Failed`) and `MapEvent.StyleLoadFailed`. Use them for a notice with a retry (re-set the style). Consider a bundled fallback style (background, Munich boundary, our layers, plus `glyphs` pointing at bundled Noto Sans Bold, or count badges without text) so pins still show with no tiles. |
| O2 | High | `DataCache.ios.kt`, `NSCachesDirectory` | iOS may purge `Caches` under storage pressure. Because nothing is bundled, a purge means "Keine Daten" the next time the app opens offline. Move the cache to `NSApplicationSupportDirectory` (and exclude it from iCloud backup with `NSURLIsExcludedFromBackupKey`). Android already uses `filesDir`. |
| O3 | Medium | `App.kt` | With no data, the only retries are on resume and the Retry button. Add an automatic retry with backoff (e.g. 5 s, 15 s, 60 s) while the status is `Failed` and the app is in the foreground. A connectivity listener (Android `ConnectivityManager`, iOS `NWPathMonitor`) is the nicer alternative, but takes more code. |
| O4 | Medium | — | Offline behaviour has never been tested on purpose. Add a manual test matrix (see step 8): first launch offline, cached launch offline, going offline mid-session, flaky network (emulator network throttling / Network Link Conditioner). |
| O5 | Low | `AndroidManifest.xml`, `allowBackup="true"` | The 0.5 MB cache is backed up to Google Drive and restored on a new device. It's harmless (it gets refreshed), but it's noise. Add `dataExtractionRules`/`fullBackupContent` to exclude `pois-latest.*` and `places-latest.*`. |

Already good: the cache comes before the network, `If-None-Match` is sent only with a cached file, a bad cache is deleted, a failed refresh keeps the current data silently, the "Lade Daten …" / "Keine Daten …" + retry pill, the staleness hint after 30 days, and the bundled place index.

### Logging

| # | Sev | Finding |
|---|-----|---------|
| L1 | Medium | There is not a single log call in the app (`println`, `Log`, `NSLog`: none). When a tester reports "Keine Daten", there is no way to tell a timeout from a 404, a failed parse, an implausible file or a disk error. |
| L2 | Low | MapLibre logs everything to logcat/NSLog by default (`MapLogging.logger`). That's fine for debug, but consider raising the threshold in release builds. |

### Error reporting

| # | Sev | Finding |
|---|-----|---------|
| R1 | Medium | No crash reporting. The README promises the app "sends nothing else" besides tiles and data, so any SDK (Sentry, Crashlytics) means updating the privacy text, the Play data safety form and the App Store privacy labels, and ideally asking for opt-in (GDPR). Two zero-SDK options exist: **Play Console Android vitals** (crashes and ANRs, on by default) and **Xcode Organizer / App Store Connect crashes** (from users who share analytics). They cover crashes and ANRs/hangs, not non-fatal errors. **Decision: store crash reports only** (step 5). |

### Release and testing

| # | Sev | Finding |
|---|-----|---------|
| P1 | High | `androidApp/build.gradle.kts`: no `release` build type config (no R8/minify, no shrinkResources), no signing config, `versionCode = 1`. Already on the open-ideas list. |
| P2 | High | iOS: no `PrivacyInfo.xcprivacy`. App Store submissions need one that declares required-reason APIs. KMP/Compose apps usually need FileTimestamp, SystemBootTime and UserDefaults; check with Xcode's privacy report. |
| P3 | Medium | The uncommitted working tree puts the real `DEVELOPMENT_TEAM = W8T4N7V9HS` into `project.pbxproj` (instead of `$(TEAM_ID)` from `Config.xcconfig`) and drops `CFBundleDevelopmentRegion` from `Info.plist`. Don't commit either. Put the team id in an uncommitted xcconfig override and restore the region key. |
| P4 | Medium | No Kotlin tests (`commonTest` doesn't exist), and CI only runs the Python hours tests. There's no build check on pushes to `main`. |
| P5 | Low | No Play/App Store privacy policy URL yet. Both stores require one for apps that ask for location. The README privacy section can be the source. |

## Plan

Each step is meant to be one `feature/<name>` branch. Do them in order; steps 1–4 are the blockers for a first release.

### Step 1: Logging foundation (`feature/logging`)
- [x] Add `internal expect object Log { fun d(tag, msg); fun w(tag, msg, t: Throwable? = null) }` in `commonMain`, with actuals: Android `android.util.Log`, iOS `NSLog` (or `os_log` via `platform.os`). In release, `d` is a no-op.
- [x] Log at every swallowed failure with the reason. In `fetchConditional`: HTTP status, exception class, `parse == null` (implausible) vs. a parse exception, and the ETag hit/miss. Also `loadCached` (cache deleted because …), `DataCache` write/read failures, the bundled `places.json` failing to parse, and the boundary resource failing to load.
- [x] Never log locations or search queries (privacy text).
- [x] Android debug only: enable `StrictMode` (disk/network on the main thread) in `MainActivity`.

### Step 2: Crash-proofing and hang fixes (`feature/robustness`)
- [x] H1: give the selected-pin poll in `MapScreen.kt` a cap (stop after ~1.5 s or ~90 frames; the overlay keeps showing). Check the select → highlight timing on both platforms afterwards.
- [x] E1: `runCatching` around `openUri` on Android (directions and the Wertstoffhof link), with a snackbar "Couldn't open …" (new string in `values` and `values-de`).
- [x] E2: `runCatching` around `getClusterExpansionZoom` in `zoomToCluster`.
- [x] E4: skip malformed features in `parseFeatures` (per-feature `runCatching`, logged). The file is rejected only if more than ~5 % are skipped.
- [x] H4: file I/O on `Dispatchers.IO`.

### Step 3: Offline hardening (`feature/offline`)
- [x] O2: iOS cache → `NSApplicationSupportDirectory` (create it if it's missing), excluded from backup. Migrate on first run: if a file exists in `Caches` and none exists in the new location, move it.
- [x] E3: relax `isPlausible`: total ≥ `MIN_LATEST_POIS` and the core types present (recycling, post box, fountain, toilet). Don't delete a cache only because it lacks a newer type.
- [x] O1: watch `mapState.style.loadState`. On `Failed`, show a `DataNotice`-style pill "Karte konnte nicht geladen werden" + retry (both languages), and retry automatically on resume. Then test whether the ambient cache makes cached launches work offline. If not, or for first-launch offline, build the bundled fallback style (separate branch if it gets big).
- [x] O3: automatic retry with backoff while there's no data, foreground only.
- [x] O5: Android backup rules that exclude the cache files.
- [x] `places.json` is downloaded in full (86 KB gzipped) on every launch: the nightly deploy gives it a new ETag even when `generated` is unchanged, and the app keeps neither the download nor its ETag when it's not newer. Fix it in the pipeline (don't republish an unchanged file) or store the new ETag on "not newer".
- Result: a cached launch offline shows the map and pins (MapLibre's ambient cache), so no bundled fallback style. Offline MapLibre keeps the style in Loading instead of failing, so the map pill also shows after 10 s of loading. Once the network is back the style fails (DNS) and is retried automatically (5 s, 15 s, then 60 s, foreground only, only on Failed so a slow load isn't cut off), and also right away when the pois arrive. The places fix is in the app: a download that is not newer is still cached with its ETag, and a cache as new as the bundled copy is kept.

### Step 4: Release configuration (`feature/release-setup`)
- [x] Android: `release` build type with `isMinifyEnabled = true` and `isShrinkResources = true`. Add ProGuard keep rules for Ktor/OkHttp, kotlinx.serialization and MapLibre, and check that the Compose resources (`files/places.json`, boundary) survive shrinking. Signing config read from `local.properties`/env (never committed), and a `versionCode`/`versionName` scheme.
- [x] Run the release build on the emulator and go through every flow (R8 bugs only show up there).
- [ ] Baseline profile (optional for the first release, but cheap with the `baselineprofile` plugin). Deferred.
- [x] iOS: `PrivacyInfo.xcprivacy` (no tracking, no collected data, the required-reason APIs from Xcode's report), the team id via an uncommitted xcconfig override, restore `CFBundleDevelopmentRegion` (P3; not needed after all: the generated Info.plist sets it from the project's `developmentRegion = en`, and Xcode strips the explicit key, so its version is committed). [ ] Archive build with a TestFlight upload: blocked, the team is a free personal team.
- [x] Privacy policy page (e.g. a GitHub Pages `privacy.html` next to the data) for both store listings (P5).
- Result: no keep rules were needed (the libraries ship consumer rules); `proguard-rules.pro` has only two Ktor `-dontwarn` groups. Release APK 41.8 MB (debug 57.2 MB), AAB 23.3 MB with the R8 mapping in `BUNDLE-METADATA`; the Compose resources are in the release APK. Every flow worked in the release build on the Fold emulator with no R8 errors in logcat. The upload keystore is `~/keystores/munichspot-upload.jks`; its passwords are only in `local.properties`, so back up both. Version: `appVersion = "1.0.0"` → `versionCode` 10000. iOS: MapLibre and Kotlin are linked statically (no framework manifests), so our manifest declares FileTimestamp C617.1, SystemBootTime 35F9.1, DiskSpace E174.1 and UserDefaults CA92.1 from a symbol scan (`nm`/`strings`; Xcode's privacy report needs an archive). The team id is in the gitignored `iosApp/Configuration/Local.xcconfig`. Debug and Release simulator builds work. `xcodebuild archive` fails with no provisioning profile for the free team. The privacy page (`pages/privacy.html`, English and German, contact via GitHub issues) is copied into the site by the nightly workflow, which now also runs on pushes to `pages/**`.

### Step 5: Error reporting (store crash reports only, decided 2026-10-25)
No crash-reporting SDK. The app keeps its promise to send nothing besides the tile and data requests, so the privacy text, the Play data safety form and the App Store labels stay as they are. Non-fatal errors aren't reported; the `Log` wrapper from step 1 covers them while debugging.
- [ ] Android: set up Android vitals alerts in Play Console (crash rate, ANR rate). Upload the R8 mapping file with each release (AGP bundles it into the AAB when minify is on; check that Play Console shows deobfuscated stack traces).
- [ ] iOS: archive with `DEBUG_INFORMATION_FORMAT = dwarf-with-dsym` for Release and make sure dSYMs are uploaded with each App Store Connect build, so crash logs in Xcode Organizer and App Store Connect are symbolicated. That includes the Kotlin `Shared` framework: check that Kotlin frames show up symbolicated in a test crash from TestFlight.
- [ ] Verify the whole path once per platform: a hidden debug-only crash trigger, or a temporary build sent to internal testing / TestFlight. The crash must show up symbolicated in the console. Remove the trigger afterwards.
- [x] Add a short "Crash reports" section to `README.md`: where to look (Play Console → Android vitals; Xcode Organizer → Crashes) and that iOS reports only come from users who share analytics with developers.
- Result (`feature/crash-reports`): verified locally, no build changes needed. Android: a release crash retraced with `mapping.txt` gives `CrashTestKt.crashForTest(CrashTest.kt:3)` ← `CategoryPicker.kt:146`. The AAB contains `BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map`. iOS: the Release dSYMs (simulator and device) contain the Kotlin DWARF (731 `kfun:de.munichspot` symbols, `crashForTest` included), and `atos` resolves crash addresses to Kotlin file:line. Limitation: Kotlin/Native handles an uncaught exception before it aborts, so the `.ips` crashed thread shows where the exception was caught (Compose `setContent`, the coroutine dispatcher, the ObjC export trap), not the throw site, and the report doesn't include the message. The trigger is commit `d633aab` (reverted in `a14c820`), for cherry-picking onto an internal-testing or TestFlight build. Still open (needs store accounts): the vitals alerts, deobfuscated traces in Play Console, and the end-to-end store test.
- [ ] Optional, not decided: put the throw site and message into iOS crash reports without an SDK. An unhandled-exception hook (`setUnhandledExceptionHook`) would raise an `NSException` whose reason carries the Kotlin message and stack trace, and Apple's crash reports show that reason. It would be a separate branch, tested with the `d633aab` trigger.

### Step 6: Tests and CI (`feature/tests-ci`)
- [x] `shared/src/commonTest`: `parsePoiData` (clothes derivation, legacy names, bad features), `isPlausible`, `parseSchedule` + `isOpen` (overnight, months), `freshnessHint`, `toGeoJson` escaping control characters, `PlaceIndex.search` (umlauts, "str."), `revealZoom`, `MunichArea.contains`.
- [x] `fetchConditional` with Ktor `MockEngine`: 200 → Updated + cache saved; 304 → Unchanged; 500, garbage, implausible → Failed with the cache untouched; timeout → Failed. Needs an injectable client.
- [x] GitHub Actions workflow `app-ci.yml` on pushes and PRs: `:shared:allTests` (or jvm/android unit tests + `iosSimulatorArm64Test` on macOS), `:androidApp:assembleRelease` (catches R8 problems), `:shared:compileKotlinIosArm64`.
- Result (`feature/tests-ci`): 64 tests in `shared/src/commonTest` (PoiRepository 16, Schedule and freshness 10, PlaceSearch 9, RemoteData 13, MapGeoJson 5, MunichArea 4, RevealZoom 7), run with `./gradlew :shared:testAndroidHostTest` and `:shared:iosSimulatorArm64Test` (`:shared:allTests` runs both); all pass on both. Changes for testability: `DataCache` takes an optional directory provider, `fetchConditional` an optional `HttpClient`, and `isPlausible` and `parseLatest` are `internal`. The AGP 9 host test is enabled with `withHostTest { isReturnDefaultValues = true }`. `app-ci.yml` runs the Android job (host tests + `assembleRelease`) on ubuntu and the iOS job (simulator tests + `compileKotlinIosArm64`) on macOS for pushes and PRs, skipping docs-, pages- and tools/data-only changes. Limitations: `MunichArea` is tested with a synthetic boundary, not the bundled file (Compose resources don't load in unit tests); the UI/Compose layer and the platform `DataCache` directories have no tests; the timeout test uses a real 50 ms timeout. `assembleRelease` in CI signs with the debug key (no secrets). Not yet run on GitHub.

### Step 7: Data contract (`feature/schema-version`, with the pipeline)
- [x] `build_pois.py` writes `"schemaVersion": 1`; `validate_pois.py` checks it.
- [x] The app accepts files with `schemaVersion` missing or ≤ the version it knows. Write down the rule in `tools/data/README.md`: a breaking change means a new file name, while the old one keeps being published until old app versions are gone.
- Result (`feature/schema-version`): `SCHEMA_VERSION = 1` lives in `validate_pois.py` (`build_pois.py` imports it), and the validator requires exactly that integer in the new file (not in `--previous`, since the live file has no field yet). In the app, `POI_SCHEMA_VERSION = 1` in `PoiRepository.kt`; `parseLatest` rejects a newer or non-integer `schemaVersion` with a log line, so a download becomes `Failed` and the cache stays. A missing field is accepted, so today's live file and existing caches still work. Two new tests (missing/1 accepted; 2, `"1"`, 1.5 rejected); 66 shared tests pass on Android host and iOS simulator. The rule is the "Data contract" section in `tools/data/README.md`.

### Step 8: Manual release test matrix (before each store submission)
- [ ] First launch: online / offline / denied location / location outside Munich.
- [ ] Cached launch offline (airplane mode) on both platforms: pins show, the map style loads or the fallback notice appears.
- [ ] Going offline mid-session; a slow network (emulator 2G profile, Network Link Conditioner "3G"/"Very Bad Network").
- [ ] App update over an old cache (install the previous release, open it, update, open offline).
- [ ] No maps or browser app (Android: disable Chrome and Maps) → tapping Navigate doesn't crash.
- [ ] Dark mode, German and English, the Fold unfolded, small phone, large font scale.
- [ ] A physical device on each platform (so far only the emulator and simulator have been used).
- [ ] Crash reports (first submission per platform, see README "Crash reports"): upload a throwaway build with the `d633aab` trigger cherry-picked to Play internal testing / TestFlight, type `crashtest!`, and check that the crash shows up symbolicated in Android vitals / Xcode Organizer. Then submit the real build without the trigger. Set up the vitals alerts (crash rate, ANR rate) when the app is created in Play Console.
- Result of the first run (2026-10-27, `b92b488`; Android release APK on the Fold emulator, iOS Debug build on the iPhone 18 Pro and 17e simulators; "old" = `cd40941`, the build from before the readiness work):
  - Pass:
    - App update over an old cache on both platforms (offline on Android). On iOS the four cache files moved from `Caches` to `Application Support`, which carries the backup-exclusion attribute.
    - First launch with location granted, denied and outside Munich (Berlin), on both platforms; the iOS permission text in English and German.
    - Android: first launch offline shows both pills. After the network comes back, the map style recovers on the 5 s retry and the pois on the next backoff step (58 s later in the debug repro).
    - Android: cached launch offline, going offline mid-session (pan, category, details sheet), and no Chrome or Maps (snackbar for directions and the Wertstoffhof link, no crash).
    - Dark mode (Android, and iOS from a cold start), German and English, the Fold folded and unfolded, and 720x1280 @320 dpi.
  - Bugs:
    - **Slow network never loads data.** `RemoteData.kt` sets a 10 s *total* `requestTimeoutMillis`, and `pois.geojson` is 58 KB gzipped, about 32 s at GSM speed. With the emulator's GSM profile there was still no data after 90 s. Use connect and socket timeouts instead of a total cap.
    - **Category panel can't scroll.** At font scale 2.0 on the small screen, Mailboxes, Fountains, Toilets and Bookcases are cut off and unreachable (`CategoryPicker.kt`).
    - **iOS: switching to dark mode while the app runs swaps pin icons**, e.g. mailboxes drawn as T-shirts and fountains as mailboxes. Colours stay right and a cold start in dark is fine. The likely cause is maplibre-compose 0.20's `StyleImageRegistry` (incrementing image ids kept across the style swap). Android recreates the activity on a uimode change, so it doesn't show this.
    - **Large text truncates the category segment**: "All c…" at Android 1.3, "A…" at the largest iOS size, and a fragment at Android 2.0.
  - Smaller:
    - With no data, the pois retry backs off to 60 s even after the style has loaded again. A style that recovers from a failure could trigger a pois refresh, the reverse of the existing "pois arrived → retry the style".
    - iOS first launch: the location alert keeps the app inactive, so the first `refresh()` (on resume) waits until the alert is answered.
  - Fixed in `feature/test-matrix-fixes`:
    - Connect (10 s) and socket (30 s) timeouts instead of the 10 s total.
    - The category panel scrolls within the window height.
    - A `PickerRow` layout gives the category segment its full width first; the place segment keeps at least 72 dp and is truncated with "…".
    - The style loading after a failure triggers a pois retry.
    - The refresh and both retry loops run on STARTED instead of RESUMED.
    - Not verified: the slow-network fix. The emulator's GSM and EDGE profiles are far slower than the real networks (58 KB still trickling after 7 min on "edge", while the tiles share the link), so it needs Network Link Conditioner or a phone on a bad connection.
  - Not yet tested:
    - iOS offline (cached launch, first launch, mid-session, slow network). The simulator shares the Mac's network, so it needs the hosts blocked or Network Link Conditioner.
    - iOS with Apple Maps deleted.
    - Physical devices.
    - The crash-report upload.

## Out of scope / already tracked
- More reliable nightly mailbox fetch (second Overpass server, reusing the last mailboxes): already in CLAUDE.md's open ideas. It matters to the app only through E3.
- OpenFreeMap has no SLA. A self-hosted or paid tile fallback is worth considering only if outages show up in practice (L1 logging will show style failures).
