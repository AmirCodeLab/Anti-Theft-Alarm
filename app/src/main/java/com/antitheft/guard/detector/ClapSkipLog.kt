package com.antitheft.guard.detector

import android.util.Log
import java.util.Locale

/**
 * Why a window that rose above the room never became a measurement.
 *
 * A candidate that opens a measurement ends in a [ClapVerdict]. These are the windows that never
 * got that far: previously they left no trace at all, so a swallowed clap and a silent room looked
 * identical in the log. An absence cannot be read; a reason can.
 */
internal enum class ClapSkip {
    /** A measurement was already open, so this window was folded into it rather than judged. */
    MEASUREMENT_BUSY,

    /** The detector was watching an earlier onset decay and was not looking for a new one. */
    IN_DECAY_WATCH,

    /** Above the room but under the line that opens a measurement — the silent majority. */
    BELOW_ONSET_BAR,
}

/**
 * Counts skips and prints them in batches.
 *
 * Sustained noise — a siren above all — skips every window for as long as it lasts, tens per
 * second. Printing each would bury the measurements these lines exist to explain, so consecutive
 * skips of one reason collapse into a single line carrying how many there were and the loudest of
 * them. Nothing is dropped: the count is the finding.
 *
 * Observation only. It is fed by the analyser and never feeds anything back, so what it prints
 * cannot change what the detector decides.
 */
internal class ClapSkipTally {

    private var reason: ClapSkip? = null
    private var windows = 0
    private var loudestRms = 0.0
    private var floorAtLoudest = 0.0
    private var openedAtMillis = 0L
    private var lastAtMillis = 0L

    fun record(nowMillis: Long, reason: ClapSkip, rms: Double, floor: Double) {
        if (this.reason != reason) flush()
        if (this.reason == null) {
            this.reason = reason
            openedAtMillis = nowMillis
        }
        lastAtMillis = nowMillis
        windows++
        if (rms > loudestRms) {
            loudestRms = rms
            floorAtLoudest = floor
        }
        if (nowMillis - openedAtMillis >= BATCH_MILLIS) flush()
    }

    /**
     * Called on every window so a batch that has stopped growing still gets printed. Without it a
     * burst of skips followed by silence would sit unprinted until the next unrelated noise.
     */
    fun tick(nowMillis: Long) {
        if (reason != null && nowMillis - openedAtMillis >= BATCH_MILLIS) flush()
    }

    private fun flush() {
        val reason = reason ?: return
        Log.d(
            CLAP_TELEMETRY_TAG,
            String.format(
                Locale.US,
                "skip=%-16s windows=%3d spanMs=%4d loudestRms=%7.0f baseline=%6.0f riseRatio=%5.1f",
                reason.name,
                windows,
                lastAtMillis - openedAtMillis,
                loudestRms,
                floorAtLoudest,
                if (floorAtLoudest > 0.0) loudestRms / floorAtLoudest else 0.0,
            ),
        )
        this.reason = null
        windows = 0
        loudestRms = 0.0
        floorAtLoudest = 0.0
    }

    private companion object {
        /** Long enough to collapse a siren into a few lines, short enough to still locate it. */
        const val BATCH_MILLIS = 500L
    }
}
