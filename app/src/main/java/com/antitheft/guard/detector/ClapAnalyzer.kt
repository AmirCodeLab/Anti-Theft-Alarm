package com.antitheft.guard.detector

import com.antitheft.guard.BuildConfig
import kotlin.math.max

/**
 * Decides whether a window of audio was a clap.
 *
 * A fixed loudness threshold is useless here: it fires non-stop in a café and never fires in a
 * quiet bedroom. So the analyser tracks the room's own noise floor and looks for a *transient* —
 * quiet, then suddenly far above the floor, then quiet again. The final requirement is what
 * separates a clap from music, a slammed door or someone talking loudly, none of which decay
 * within a few tens of milliseconds.
 */
internal class ClapAnalyzer {

    private var baseline = 0.0
    private var previousRms = 0.0
    private var windowsLeftToDecay = 0

    /** Null until the first clap: "never fired" is not the same as "fired at time zero". */
    private var lastFiredAtMillis: Long? = null

    private val telemetry =
        if (BuildConfig.DEBUG && CLAP_TELEMETRY_ENABLED) ClapTelemetry() else null

    /**
     * Feeds one window in, and returns true when that window completed a clap. Takes the raw
     * window rather than a level so the zero-crossing rate can be measured on the peak itself;
     * neither the samples nor anything derived from them is retained past the call.
     */
    fun accept(window: ShortArray, length: Int, nowMillis: Long): Boolean {
        val rms = rms(window, length)

        if (baseline == 0.0) {
            // First window of the session: seed the floor rather than treat silence as a spike.
            baseline = rms
            previousRms = rms
            return false
        }

        val floor = max(baseline, MIN_BASELINE_RMS)
        val triggerLine = floor * ONSET_FACTOR
        val decayLine = floor * DECAYED_TO_FACTOR
        telemetry?.windowObserved(nowMillis, rms, triggerLine, decayLine)

        var clapped = false

        if (windowsLeftToDecay > 0) {
            windowsLeftToDecay--
            // The baseline stays frozen here: folding the clap itself into the floor would raise
            // it enough to swallow the very detection in progress.
            if (rms < decayLine) {
                clapped = true
                lastFiredAtMillis = nowMillis
                windowsLeftToDecay = 0
                telemetry?.resolved(ClapVerdict.FIRED)
            } else if (windowsLeftToDecay == 0) {
                telemetry?.resolved(ClapVerdict.DECAY_TOO_SLOW)
            }
        } else {
            val quietBefore = previousRms < floor * QUIET_BEFORE_FACTOR
            val loudNow = rms > triggerLine
            val lastFired = lastFiredAtMillis
            val pastCooldown = lastFired == null || nowMillis - lastFired >= COOLDOWN_MILLIS

            if (quietBefore && loudNow && pastCooldown) {
                windowsLeftToDecay = DECAY_WINDOWS
            } else {
                baseline = baseline * (1 - BASELINE_SMOOTHING) + rms * BASELINE_SMOOTHING
            }

            if (telemetry != null) {
                recordPeak(telemetry, window, length, nowMillis, rms, floor, quietBefore, loudNow, pastCooldown)
            }
        }

        previousRms = rms
        return clapped
    }

    /**
     * Opens a measurement for anything that rose above the noise, whether or not it went on to
     * fire. The bar reuses the existing quiet-before line rather than introducing a threshold of
     * its own, so this cannot drift away from what the detector actually does.
     */
    @Suppress("LongParameterList")
    private fun recordPeak(
        telemetry: ClapTelemetry,
        window: ShortArray,
        length: Int,
        nowMillis: Long,
        rms: Double,
        floor: Double,
        quietBefore: Boolean,
        loudNow: Boolean,
        pastCooldown: Boolean,
    ) {
        if (telemetry.isMeasuring || rms <= floor * QUIET_BEFORE_FACTOR) return

        val verdict = when {
            !loudNow -> ClapVerdict.TOO_QUIET
            !quietBefore -> ClapVerdict.NO_PRECEDING_SILENCE
            !pastCooldown -> ClapVerdict.IN_COOLDOWN
            else -> ClapVerdict.PENDING
        }
        telemetry.peakObserved(nowMillis, rms, floor, zeroCrossingRate(window, length), verdict)
    }

    private companion object {
        /** A clap peaks roughly an order of magnitude over the room; conversation rarely doubles it. */
        const val ONSET_FACTOR = 8.0

        /** The window before the onset has to be near the floor, or this is just sustained noise. */
        const val QUIET_BEFORE_FACTOR = 2.0

        /** Back to near-floor is what makes it a transient rather than music or a voice. */
        const val DECAYED_TO_FACTOR = 3.0

        /** ~96 ms at a 32 ms window: long enough for a real clap to die away, short enough to exclude a chord. */
        const val DECAY_WINDOWS = 3

        /** Slow enough to ignore a single loud moment, fast enough to follow a room that changes. */
        const val BASELINE_SMOOTHING = 0.05

        /** Keeps a near-silent room from turning every faint rustle into an eight-fold spike. */
        const val MIN_BASELINE_RMS = 180.0

        /** Matches the motion detector: the alarm is already sounding, so re-firing wastes battery. */
        const val COOLDOWN_MILLIS = 8_000L
    }
}
