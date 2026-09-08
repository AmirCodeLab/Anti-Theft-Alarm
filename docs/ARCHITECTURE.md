# Architecture — the decisions and why

Guard is a small app with one job: notice when a phone left alone is disturbed, and say so
loudly. Every feature in it is the same shape — watch something, emit an event, react — and most
of the design follows from taking that shape seriously. This document explains the reasoning
behind the handful of decisions that shape the code. It is not a tour of the files.

## One contract for every way of sensing

Every feature is a `ThreatDetector`: it has an identity, and it exposes a stream of events.
Nothing else. A detector does not post notifications, does not play sound, does not read
settings and does not know the service that runs it exists. It reports what it saw, and that
is the whole of its authority.

The stream is **cold**. Nothing is acquired when the detector object is created; the sensor,
receiver or recorder is opened at the moment somebody starts collecting the stream, and released
when that collection is cancelled. Acquisition and release are written in the same block of code,
one at the top and one in the cancellation handler, so that they cannot drift apart. This is a
different guarantee from "we remembered to release it": it is that there is no way to write a
detector in this codebase whose hardware outlives its stream, because the stream *is* the
lifetime.

What that makes impossible is worth spelling out, because it is the reason the shape was chosen:

- A detector cannot leak its hardware. There is no `stop()` for anyone to forget to call.
- A detector cannot keep listening after it is switched off. Switching a feature off changes the
  armed set; the service re-derives the merged stream from the new set; the old stream is
  cancelled; cancellation runs the release. The microphone is closed because the flow ended, not
  because somebody sent a message asking for it to be closed.
- A detector cannot half-react. Since it has no access to the notifier, the alarm or the
  settings, a detector cannot decide on its own to sound an alarm, quietly change a setting, or
  post a notice that the rest of the app does not know about. The single place that reacts is the
  service, which sees one merged stream and does not care which detector an event came from.
- Adding a feature cannot touch the service. A new feature is an identity, a detector, and one
  line in the registry that lists detectors. If a new feature ever needed the service's lifecycle
  code to change, that would be a sign the contract had been broken — and the right response is to
  say so, not to patch around it.

The service is deliberately dumb about features. It merges whichever detectors are armed and, on
every change to the armed set, tears the merge down and builds it again. This is
cancel-and-rebuild rather than surgical add/remove on purpose: it is the same code path whether
the user switches one feature off, switches all of them off, or a permission is revoked
underneath a running detector, and it is the path that makes the release guarantee above hold.

## The wake lock, and why the feature is meaningless without it

Motion detection reads the ordinary accelerometer. On most devices the accelerometer is a
*non-wakeup* sensor: it keeps sampling while the CPU is awake and simply stops delivering
events once the CPU suspends. A phone left face-down on a table with the screen off suspends
within seconds. Without intervention, motion detection would therefore be running, armed,
showing "watching for movement" in the status bar — and delivering nothing, precisely in the
situation the feature exists for. The failure would be silent, and the user would only discover
it after the phone was gone.

So the detector holds a partial wake lock for exactly as long as its stream is collected. The
lock keeps the CPU awake so the sensor keeps reporting; it does not keep the screen on. Two
choices around it are deliberate:

- **No timeout.** A wake lock with a timeout would release itself after some interval and, from
  that moment, the feature would be off while still claiming to be on. That is worse than either
  honest state. The lock's lifetime is bounded instead by the stream: the cancellation handler
  releases it, and the stream is cancelled whenever the feature is disarmed or the service dies.
  The lint warning about a missing timeout is suppressed at that one site, with the reason.
- **The lock lives in the detector, not the service.** Only the motion detector knows it needs
  the CPU awake; charger alerts and clap detection do not. Putting the lock in the service would
  keep the CPU up for features that do not require it, and would mean the service knowing which
  feature is which — which the contract above forbids.

The cost is real: while motion detection is armed the CPU never suspends, and the battery drains
faster than it otherwise would. That is the price of a feature that actually works with the
screen off, and it is a better trade than one that pretends to.

The alternative sensor Android offers for this — the one-shot "significant motion" trigger — was
rejected. It is designed to detect a person walking, not a phone being lifted; it is too slow
and too coarse for the case at hand, and it has to be re-registered after every trigger.

## The alarm sounds on the alarm stream

The siren is played with `USAGE_ALARM` audio attributes, not the notification or media stream.
The reasoning is one sentence: an anti-theft alarm that honours the ringer switch is not an
alarm. A phone left on silent is exactly the phone most likely to be left somewhere, and the
whole feature would evaporate at the moment it was needed. Alarm usage routes the sound through
the same path as a wake-up alarm clock, which is the path users already expect to make noise
regardless of the ringer. The vibration that accompanies it is tagged the same way.

The alarm loops until it is explicitly stopped. There is no automatic timeout, because a thief
does not need long, and an alarm that gives up after thirty seconds is a feature for the thief.
Stopping it is a distinct action from disarming: silencing one alarm must not leave the phone
unwatched for the rest of the night. Both the notification's stop control and the in-app button
send the same "stop the alarm" command, and neither of them changes what is armed.

## The foreground-service type mask is derived, not declared

Recent Android versions require a foreground service to declare which kinds of sensitive
capability it is using — microphone, location, and so on — and will refuse or penalise a service
that claims a capability it is not entitled to at that moment. Guard's base type is `specialUse`,
which is the honest description of a security monitor. The microphone type is needed only while
clap detection is actually running.

The obvious implementation is for the service to know that "the clap feature" means "add the
microphone type". That was rejected because it puts a feature's name into the service, and the
whole design rests on the service not knowing feature names. Instead, each detector carries a
property stating the foreground-service type its own hardware requires — zero for hardware
Android does not consider sensitive. The service folds those properties together across the
armed set and re-declares itself with the result every time the armed set changes.

Two things fall out of this. First, the mask is always exactly right: it is recomputed from the
same armed set that decides which streams are collected, so it cannot claim the microphone while
nothing is listening or omit it while something is. Second, a future detector that needs, say,
location declares that fact about itself in one place, and the service picks it up without
being edited.

The mask is safe to trust because of an ordering decision made elsewhere: whether a detector may
run at all is settled before its type is read. A detector whose runtime permission is missing
never enters the armed set, so any type that reaches the mask is one the app can actually back
up with a granted permission.

## Why clap detection can be "on" and not listening

Recent Android versions will not let an app claim the microphone from a background start, and a
reboot is a background start. Guard applies that rule on every version it runs on rather than
special-casing the ones that enforce it. The boot receiver can bring charger alerts and motion
detection back on its own, but it cannot bring the microphone back; that has to wait until the user
opens the app. Separately, the microphone permission can be revoked from system settings at any
time, without the app being told.

Both situations produce the same surface state — the switch says on, nothing is listening — and
a single "paused" flag would be tempting. It was rejected because the two situations need
different words and offer different remedies. One clears itself the moment the app is opened;
the other requires the user to go and grant something, and the app can only point the way. So
the setting carries a *reason* for the pause, and the screen and the post-reboot notice each say
the thing the user can act on.

When both conditions hold at once — permission missing *and* app not yet opened since boot — the
reported reason is the missing permission. The rule is that denial outranks the pending app-open,
and the reasoning is about what the user gains from the message. If they were told "open the app"
they would open it, the gate would clear, and they would find clap detection still not listening,
now with a different explanation. Telling them about the permission first is the only message
that, once acted on, actually leaves them better off. The lower-priority reason is not lost: it
simply becomes visible once the higher one is resolved.

The gate that represents "the app has been opened since boot" is a piece of in-memory state that
opens when the main screen is created and is never closed. Process death closes it again as a
matter of course, which is correct: a fresh process is a background start until the user shows
up.

## The armed set is computed once, and everything reads it

There is one persisted settings object, written only by the UI, and read by everyone else. It
is the single source of truth for what the user has asked for. But what the user asked for and
what is actually running are not the same thing — clap detection can be on and paused — and
most of the app cares about the second.

So the settings object exposes a derived **armed set**: the detectors that are genuinely running
right now. Clap detection is in it only when it is switched on *and* its pause reason is none.
That derivation lives in exactly one place, and it is the invariant the rest of the app rests
on:

- The service collects the streams of the armed set. A paused detector's stream is never
  collected, so its hardware is never opened.
- The foreground-service type mask is folded from the armed set. A paused microphone detector
  contributes nothing, so the service never claims the microphone type for a detector that is not
  listening.
- The ongoing notification names the members of the armed set. It says "listening for a clap"
  only when that is true.
- "Is anything armed" is "is the armed set non-empty". When it becomes empty, the service stops
  itself. The UI never stops the service; it only writes settings, and the service draws its own
  conclusion.

The value of doing it once is that none of those four readers repeats the "on and not paused"
check, and none of them can get it subtly different from the others. When the rule changes — a
fourth reason for a pause, a new feature with its own conditions — it changes in one place and
every consumer is right by construction.

The settings stream itself is assembled from the persisted preferences combined with the two
live signals that can pause the microphone (the app-open gate and the permission state). That
combination happens once, at the repository, rather than in each consumer, for the same reason:
the service and the screen must be looking at the same truthful picture, and the only way to
guarantee that is to build the picture in one place.

## Permissions are asked for at the moment of use

No permission is requested on launch. Each is requested when the user arms the feature that
needs it, so the prompt arrives when its purpose is obvious: notifications when the first
feature is armed, the microphone when clap detection is. If the user declines, the feature is
not armed; there is no state in which a switch is on while the thing it needs was refused at
the prompt. Disarming never asks for anything.

Denial after the system has stopped showing the prompt is handled by pointing the user at the
app's system settings page, because that is the only route that still exists.

## What the design does not do

It does not attempt to defeat battery-optimisation settings, and does not attempt to hide
itself. Guard is a tool
the owner arms on purpose, and it behaves like one.
