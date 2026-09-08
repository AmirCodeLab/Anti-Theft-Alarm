# Guard — Anti-Theft Alarm

An Android app that watches a phone left alone and raises the alarm when it is disturbed. Arm
one or more features, put the phone down, and Guard keeps watching with the screen off.

Three features:

- **Charger alerts** — a notification the moment the phone is plugged in or unplugged.
- **Motion detection** — a loud, looping alarm if the phone is picked up or moved, with a stop
  control in the notification and in the app.
- **Clap detection** — the same alarm when Guard hears a clap, so a phone can be found by sound.
  This feature is shipped but does not work reliably; see [Known limitations](#known-limitations).

Dark only. Amber means armed, red means an alarm is sounding right now, and nothing else uses
either colour.

The reasoning behind the design is in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Building

Requirements:

- JDK 17
- Android SDK with platform 37 installed (`compileSdk = 37`, `targetSdk = 35`, `minSdk = 24`)
- Gradle via the wrapper; it resolves Android Gradle Plugin 9.2.1 and Kotlin 2.2.10 itself

```
./gradlew :app:assembleDebug
```

The APK lands at `app/build/outputs/apk/debug/app-debug.apk`. Install it with
`adb install -r app/build/outputs/apk/debug/app-debug.apk`.

There is no automated test suite. Every check below is a manual one.

## Verifying each feature by hand

Guard asks for permissions at the moment a feature is armed, never on launch. On Android 13 and
later the first feature armed prompts for notifications; clap detection additionally prompts for
the microphone.

### Charger alerts

1. Open the app and switch on **Charger alerts**. Accept the notification prompt.
2. The beacon turns amber and pulses; the headline reads "Protection is on"; an ongoing
   "Protection is on" notice appears in the status bar reading "Watching the charger".
3. Plug in a charger. A "Charger connected" alert appears.
4. Unplug it. A "Charger disconnected" alert appears.
5. Switch the feature off. The beacon goes grey, the ongoing notice disappears, and the service
   stops.

On an emulator, `adb shell dumpsys battery unplug` and `adb shell dumpsys battery set ac 1`
simulate the two events; `adb shell dumpsys battery reset` restores the real state.

### Motion detection

1. Switch on **Motion detection**. Put the phone down flat and leave it for at least four
   seconds — the first four seconds after arming are ignored, because the user is still holding
   the phone.
2. Pick the phone up, or push it across the table. The alarm sounds (the device's default alarm
   tone, or its ringtone if it has no alarm tone), the phone vibrates in a repeating pattern, a
   red **Stop alarm** button appears in the app, and a "Your phone was moved" notification with a
   **Stop alarm** action appears.
3. Switch the screen off and repeat step 2. The alarm must still sound: the accelerometer is a
   non-wakeup sensor on most devices, and the wake lock is what keeps it reporting.
4. Set the ringer to silent and repeat. The alarm must still sound: it is routed on the alarm
   stream, not the notification stream.
5. Tap **Stop alarm** in either place. The sound and vibration stop, the notification is
   removed, and the beacon stays amber — silencing the alarm does not disarm protection.
6. Move the phone again within eight seconds of an alarm. Nothing happens: re-firing an alarm
   that is already sounding is suppressed for eight seconds.

### Clap detection

1. Switch on **Clap detection**. Accept the microphone prompt. Android shows its microphone
   indicator in the status bar while this is on.
2. From about a metre, clap once, sharply. The alarm sounds as for motion, with a "Guard heard a
   clap" notification.
3. Expect this to fail some of the time, and to fire on other sharp sounds. See the finding
   below.

To watch what the detector is doing, run a debug build and filter logcat on the `ClapTelemetry`
tag. `scripts/collect-clap-data.sh` runs a labelled collection against an attached Pixel and
`scripts/summarize-clap-data.py` summarises it; both are measurement tools and change nothing
about detection.

### Reboot

1. Arm charger alerts, motion detection and clap detection.
2. `adb reboot`, or restart the phone by hand. Unlock it.
3. The "Protection is on" ongoing notice returns reading "Watching the charger, Watching for
   movement" — without clap detection. A separate notice explains that clap detection is
   waiting for the app to be opened.
4. Open the app. Clap detection resumes; the ongoing notice adds "Listening for a clap".

### Pausing and permission loss

1. With motion detection and clap detection both on, go to the app's system settings and
   revoke the microphone permission. (Android stops the app when a permission is revoked, so
   protection restarts when you return.) Return to the app.
2. The clap switch stays on, with a footer reading that Guard is not listening and an **Open
   settings** button. The ongoing notice no longer says "Listening for a clap", and the
   microphone indicator disappears.
3. Grant the permission again and return. Listening resumes.

### Turning everything off from the notification

Tap **Turn off** on the ongoing notice. Every feature disarms at once, the notice disappears, and
the app shows "Protection is off" the next time it is opened.

## Known limitations

**Clap detection is not reliable, and the data says why.** A labelled collection on a Pixel 6
Pro — five each of claps at one and three metres, a table tap, a door, a plausible false-trigger
sound and speech — produced these results from the shipped detector:

- Of ten real claps, **two** fired the alarm. The rest were judged too quiet relative to the
  room's noise floor: the detector requires a sound eight times louder than the floor, and the
  claps that failed measured between 2.0 and 7.2 times.
- A **table tap** and a **door** both fired. On the feature the detector leans on hardest — the
  ratio of the peak to the noise floor — they scored 13.4 and 13.2, higher than either real clap
  (8.1 and 8.3). The loudness ratio ranks the impostors above the claps, so no threshold on it
  can separate them.
- Once the alarm fired it played into the same microphone being measured, raised the noise floor to
  between roughly twice and three and a half times its resting level, and blinded the detector for
  roughly ten seconds after every fire. Most of the collection was lost this way, which is why the
  sample is as small as it is. A debug-only measurement mode (`CLAP_DRY_RUN_ENABLED`) now exists
  that logs the verdict without sounding the alarm; it is switched off in the shipped code.
- The one feature that pointed the right way was the zero-crossing rate measured over a six
  millisecond window around the peak — a proxy for the sharpness of the attack — which read
  higher for claps than for the tap, the door or the false-trigger sound. The sample is far too
  small and too contaminated to rely on that.
- The device reports that its `UNPROCESSED` audio source is not supported, so the automatic gain
  control in the capture chain cannot be bypassed on it, and the noise floor drifts with the
  gain state as well as with the room.

What a real fix would take: a labelled collection with the alarm out of the path (the
measurement mode above, which exists but has not yet been run at scale), enough repetitions per
sound to see the ranges rather than single points, and then a detector that decides on the
*shape* of the sound — attack sharpness and decay time — rather than on loudness relative to
the room. If the ranges for claps and taps overlap on every measurable feature, the honest
conclusion is that a single microphone cannot separate them and the feature should be removed
rather than tuned. That call has not been made because the data to make it does not exist yet.

The telemetry's duration fields are also known to be quantised: the audio hardware delivers
samples in 20 ms chunks while the analysis window is 32 ms, so a duration measured with the
wall clock carries up to ±20 ms of error. Timing by sample count would remove it; it has not
been changed yet.

**Motion detection costs battery.** While it is armed the phone holds a wake lock and never
suspends the CPU. That is what keeps the accelerometer reporting with the screen off, and there
is no way to have the feature without it on a device whose accelerometer is a non-wakeup sensor.

**Clap detection does not survive a reboot on its own.** Android does not allow the microphone to
be claimed from a background start. After a reboot the other two features come back by
themselves; clap detection waits until the app is opened, and says so.

**If the system kills and restarts the process without the app being opened**, clap detection
pauses the same way, but no notification is posted in that path — the pause is visible only when
the app is next opened. The post-reboot notice is posted by the boot receiver, and nothing
equivalent runs on a plain process restart.

**Motion detection is a fixed threshold.** A phone lifted from a table exceeds two metres per
second squared away from gravity on two consecutive samples; a phone on a surface that is itself
moving — a car, a train — will trigger it.

**No automated tests.** Verification is the manual procedure above.
