# Guard — Anti-Theft Alarm

Android assessment build for a client. Code quality is the thing being judged, not feature count.

## Stack

- Kotlin, Jetpack Compose (Material 3), single Activity
- Koin for DI (Dependency Injection)
- DataStore Preferences for persistence
- Coroutines + Flow for everything asynchronous
- minSdk 24, compileSdk 35, JDK 17
- Version catalog at `gradle/libs.versions.toml` — never hardcode a dependency version in a
  `build.gradle.kts`

## Package layout

```
com.antitheft.guard
├── domain/          models + repository contracts. NO android.* imports in this package.
├── detector/        ThreatDetector contract + one file per sensing strategy
├── data/            implementations of domain contracts
├── service/         GuardService (foreground), start controller, boot receiver
├── core/            cross-cutting helpers (notifications, audio)
├── ui/              MainActivity, screens, ViewModels, theme
└── di/              the single Koin module — the wiring diagram for the app
```

## The central abstraction — do not work around it

Every anti-theft feature is the same shape: watch something, emit an event, react. There is one
contract for all of them:

```kotlin
interface ThreatDetector {
    val id: DetectorId
    fun events(): Flow<GuardEvent>
}
```

Rules that follow from this:

- `events()` returns a **cold** flow built with `callbackFlow`. Acquire the sensor, receiver, or
  recorder when the flow is collected; release it in `awaitClose`. Setup and teardown must sit in
  the same block so a leak is impossible rather than merely avoided.
- A detector never posts a notification, plays a sound, touches settings, or knows about
  `GuardService`. It only emits `GuardEvent`s.
- `GuardService` is the only component that reacts to events. It merges the flows of the armed
  detectors and uses `flatMapLatest` on the settings flow so switching a feature off cancels its
  flow and releases its hardware.
- Adding a feature = one `DetectorId` entry + one `ThreatDetector` implementation + one line in
  the Koin detector registry. If a change requires editing `GuardService`'s lifecycle code, the
  design is being violated — stop and say so instead of patching around it.

## Other standing rules

- `ProtectionSettings` in DataStore is the single source of truth. The UI writes settings; the
  service reads them and stops itself when nothing is armed. The UI never calls `stopService`.
- All user-facing text goes in `strings.xml`. Sentence case, active voice, no ALL CAPS labels.
  Describe what the user gets, not how the system works.
- Runtime permissions are requested at the moment the user arms the relevant feature, never on
  launch.
- Foreground service type is `specialUse`; add `microphone` only when clap detection lands.
- Comments explain *why* a non-obvious choice was made. Do not comment what the code already says.
- No `runBlocking` on the main thread. No `GlobalScope`.
- Prefer adding a file over growing an existing one past ~150 lines.

## Design language

Dark only. Deep marine base (`#101A24`), one amber accent (`#FFB020`) reserved for the armed
state, red (`#FF5A5F`) reserved exclusively for an alarm actually firing so it never loses
meaning. The status beacon is the single loud element; everything else stays quiet. Motion is
used only to signal "this is live right now" and stops when protection is off.

## Build and verify

```
./gradlew :app:assembleDebug
```

After any change, build before reporting done. If a manual device check is needed, list the exact
steps rather than claiming it works.

## Roadmap

- [ ] Step 1 — charger connected / disconnected alerts
- [ ] Step 2 — motion detection with alarm playback and a stop control
- [ ] Step 3 — clap detection (bonus)

Work one step at a time. Do not start the next step until the current one builds and is confirmed.
