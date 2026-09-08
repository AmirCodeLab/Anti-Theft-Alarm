package com.antitheft.guard.detector

import android.util.Log
import java.util.Locale

/** What became of a candidate. [PENDING] is internal bookkeeping and is never logged. */
internal enum class ClapVerdict {
    PENDING,
    FIRED,
    TOO_QUIET,
    NO_PRECEDING_SILENCE,
    IN_COOLDOWN,
    DECAY_TOO_SLOW,
}

/**
 * Records what fired and what merely nearly fired, so thresholds can be chosen from real numbers
 * instead of guesses.
 *
 * Strictly an observer: it reads the detector's decisions and never feeds anything back, so
 * switching it off cannot change what the detector does. Only aggregates ever leave here — no
 * audio samples, which keeps the promise the settings screen makes to the user.
 */
internal class ClapTelemetry {

    private var measuring = false
    private var verdict = ClapVerdict.PENDING
    private var peakRms = 0.0
    private var baseline = 0.0
    private var zcr = 0.0
    private var zcrPeak = 0.0
    private var peakAtMillis = 0L
    private var decayAtMillis = NOT_OBSERVED
    private var elevatedEndedAtMillis = NOT_OBSERVED
    private var lastWindowAtMillis = 0L
    private var windowsLeft = 0

    /** One measurement runs at a time, which also bounds how much this can ever print. */
    val isMeasuring: Boolean get() = measuring

    @Suppress("LongParameterList")
    fun peakObserved(
        nowMillis: Long,
        peakRms: Double,
        baseline: Double,
        zcr: Double,
        zcrPeak: Double,
        verdict: ClapVerdict,
    ) {
        measuring = true
        this.verdict = verdict
        this.peakRms = peakRms
        this.baseline = baseline
        this.zcr = zcr
        this.zcrPeak = zcrPeak
        peakAtMillis = nowMillis
        decayAtMillis = NOT_OBSERVED
        elevatedEndedAtMillis = NOT_OBSERVED
        lastWindowAtMillis = nowMillis
        windowsLeft = MEASUREMENT_WINDOWS
    }

    /** The detector settled a candidate this observer was still calling PENDING. */
    fun resolved(verdict: ClapVerdict) {
        if (measuring && this.verdict == ClapVerdict.PENDING) this.verdict = verdict
    }

    /**
     * Every window while a measurement is open. Primitives only, so the audio loop allocates
     * nothing; the one string is built when the measurement ends, which is rare.
     */
    fun windowObserved(nowMillis: Long, rms: Double, triggerLine: Double, decayLine: Double) {
        if (!measuring) return

        lastWindowAtMillis = nowMillis
        // Both are measured as "when it stopped", so a level that is over the line for a single
        // window reports that window's length rather than zero.
        if (elevatedEndedAtMillis == NOT_OBSERVED && rms <= triggerLine) {
            elevatedEndedAtMillis = nowMillis
        }
        if (decayAtMillis == NOT_OBSERVED && rms < decayLine) decayAtMillis = nowMillis

        windowsLeft--
        if (windowsLeft > 0) return

        emit()
        measuring = false
    }

    private fun emit() {
        val decayMs = if (decayAtMillis == NOT_OBSERVED) NOT_OBSERVED else decayAtMillis - peakAtMillis
        val elevatedUntil =
            if (elevatedEndedAtMillis == NOT_OBSERVED) lastWindowAtMillis else elevatedEndedAtMillis
        Log.d(
            CLAP_TELEMETRY_TAG,
            String.format(
                Locale.US,
                "verdict=%-20s peakRms=%7.0f baseline=%6.0f riseRatio=%5.1f " +
                    "decayMs=%4d elevatedMs=%4d zcr=%.4f zcrPeak=%.4f",
                verdict.name,
                peakRms,
                baseline,
                peakRms / baseline,
                decayMs,
                elevatedUntil - peakAtMillis,
                zcr,
                zcrPeak,
            ),
        )
    }

    private companion object {
        /**
         * How long to keep watching a candidate before printing it — about 0.5 s at a 32 ms
         * window. Long enough to see a slow sound like music finish decaying, which is the
         * measurement that matters most here. Two consequences worth knowing when reading the
         * output: decayMs and elevatedMs are capped by this, and a second sound landing inside the
         * window is folded into the first rather than printed on its own line.
         */
        const val MEASUREMENT_WINDOWS = 16

        /** Printed for decayMs when the level never came back down inside the measurement. */
        const val NOT_OBSERVED = -1L
    }
}
