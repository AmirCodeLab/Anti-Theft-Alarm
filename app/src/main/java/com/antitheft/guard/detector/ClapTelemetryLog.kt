package com.antitheft.guard.detector

import android.util.Log
import com.antitheft.guard.BuildConfig
import java.util.Locale

/**
 * Single switch for the clap telemetry: set to false and every telemetry line goes away. Debug
 * builds are gated separately by [clapTelemetryEnabled], so a release build never logs regardless.
 */
internal const val CLAP_TELEMETRY_ENABLED = true

/** One tag for every telemetry line, so a single logcat filter catches all of them. */
internal const val CLAP_TELEMETRY_TAG = "ClapTelemetry"

internal val clapTelemetryEnabled: Boolean get() = BuildConfig.DEBUG && CLAP_TELEMETRY_ENABLED

/**
 * Printed once when the recorder opens. Every other line the telemetry prints is a set of numbers
 * whose meaning depends on these, so they are stated rather than left to be inferred from source.
 */
internal fun logClapConfiguration(source: ClapAudioSource, unprocessedSupported: Boolean) {
    if (!clapTelemetryEnabled) return
    Log.d(
        CLAP_TELEMETRY_TAG,
        String.format(
            Locale.US,
            "config source=%s unprocessedSupported=%b sampleRate=%dHz " +
                "window=%d samples (%.1f ms) zcrPeakWindow=%d samples (%.1f ms)",
            source.name,
            unprocessedSupported,
            ClapAudioConfig.SAMPLE_RATE_HZ,
            ClapAudioConfig.WINDOW_SAMPLES,
            ClapAudioConfig.millisFor(ClapAudioConfig.WINDOW_SAMPLES),
            ClapAudioConfig.ZCR_PEAK_SAMPLES,
            ClapAudioConfig.millisFor(ClapAudioConfig.ZCR_PEAK_SAMPLES),
        ),
    )
}
