package com.antitheft.guard.detector

/**
 * The telemetry side of [ClapAnalyzer], in one place.
 *
 * The analyser reports what it saw and what it decided; this decides what any of it is worth
 * printing, and in which of the two shapes — a full measurement for a candidate that was examined,
 * a [ClapSkip] for a window that never got that far. Keeping the split here rather than in the
 * analyser is what lets the whole thing be switched off without the detector noticing.
 */
internal class ClapObserver {

    private val measurement = ClapTelemetry()
    private val skips = ClapSkipTally()

    fun windowObserved(nowMillis: Long, rms: Double, triggerLine: Double, decayLine: Double) {
        measurement.windowObserved(nowMillis, rms, triggerLine, decayLine)
        skips.tick(nowMillis)
    }

    fun resolved(verdict: ClapVerdict) = measurement.resolved(verdict)

    /** A window the detector never examined, because it was watching an earlier onset decay. */
    fun decayWatchObserved(nowMillis: Long, rms: Double, floor: Double) {
        if (rms > floor * CONSIDERED_FACTOR) {
            skips.record(nowMillis, ClapSkip.IN_DECAY_WATCH, rms, floor)
        }
    }

    /**
     * Opens a measurement for anything that rose above [openBar], whether or not it went on to
     * fire, and records why for everything that did not. The bar is the detector's own
     * quiet-before line rather than one invented here, so this cannot drift away from what the
     * detector actually does.
     */
    @Suppress("LongParameterList")
    fun candidateObserved(
        window: ShortArray,
        length: Int,
        nowMillis: Long,
        rms: Double,
        floor: Double,
        openBar: Double,
        quietBefore: Boolean,
        loudNow: Boolean,
        pastCooldown: Boolean,
    ) {
        if (measurement.isMeasuring) {
            skips.record(nowMillis, ClapSkip.MEASUREMENT_BUSY, rms, floor)
            return
        }
        if (rms <= openBar) {
            // Only what rose enough to plausibly be a sound. Logging every window above a smoothed
            // average would print roughly half of them and say nothing.
            if (rms > floor * CONSIDERED_FACTOR) {
                skips.record(nowMillis, ClapSkip.BELOW_ONSET_BAR, rms, floor)
            }
            return
        }

        // IN_COOLDOWN is kept even in a dry run, where the detector goes on to judge the sound
        // anyway: the verdict says what would have shipped, and decayMs says whether it would
        // have fired had the cooldown not been in the way.
        val verdict = when {
            !loudNow -> ClapVerdict.TOO_QUIET
            !quietBefore -> ClapVerdict.NO_PRECEDING_SILENCE
            !pastCooldown -> ClapVerdict.IN_COOLDOWN
            else -> ClapVerdict.PENDING
        }
        measurement.peakObserved(
            nowMillis = nowMillis,
            peakRms = rms,
            baseline = floor,
            zcr = zeroCrossingRate(window, length),
            zcrPeak = peakZeroCrossingRate(window, length, ClapAudioConfig.ZCR_PEAK_SAMPLES),
            verdict = verdict,
        )
    }

    private companion object {
        /**
         * How far a window must rise above the floor before its rejection is worth a line. A
         * logging bar only, below the detector's own opening bar so that everything the detector
         * discards is visible; nothing but the skip log reads it.
         */
        const val CONSIDERED_FACTOR = 1.25
    }
}

/** Null in a release build, so the detector carries no observer at all rather than a dormant one. */
internal fun clapObserver(): ClapObserver? = if (clapTelemetryEnabled) ClapObserver() else null
