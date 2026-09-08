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
 * Measurement mode. The detector still decides, and the telemetry still prints every verdict —
 * FIRED included — but no event leaves the detector, so the alarm never sounds; and the cooldown
 * is not applied, because it exists only to protect an alarm that is already sounding.
 *
 * The two belong on one switch: a siren fired into the microphone being measured lifts the noise
 * floor for as long as it plays and blinds the detector for the cooldown after, so a capture with
 * the alarm live describes the alarm, not the sounds. Set to false for any build that should
 * actually alarm on a clap. Release builds ignore it.
 */
internal const val CLAP_DRY_RUN_ENABLED = false

internal val clapDryRun: Boolean get() = BuildConfig.DEBUG && CLAP_DRY_RUN_ENABLED

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
            "config source=%s unprocessedSupported=%b dryRun=%b sampleRate=%dHz " +
                "window=%d samples (%.1f ms) zcrPeakWindow=%d samples (%.1f ms)",
            source.name,
            unprocessedSupported,
            clapDryRun,
            ClapAudioConfig.SAMPLE_RATE_HZ,
            ClapAudioConfig.WINDOW_SAMPLES,
            ClapAudioConfig.millisFor(ClapAudioConfig.WINDOW_SAMPLES),
            ClapAudioConfig.ZCR_PEAK_SAMPLES,
            ClapAudioConfig.millisFor(ClapAudioConfig.ZCR_PEAK_SAMPLES),
        ),
    )
}

/**
 * What the device actually hands back, as opposed to what was asked for.
 *
 * [ClapAudioConfig] asks for 512-sample windows, but `AudioRecord.read` is free to return fewer,
 * and every millisecond in the telemetry is derived from how often a window arrives rather than
 * from a sample count. A short read therefore rescales decayMs and elevatedMs without leaving any
 * trace. Measured over the first windows of a session and printed once, next to what was asked
 * for, so the two can be compared instead of assumed equal.
 */
internal class ClapReadProfile {

    private var reads = 0
    private var samples = 0L
    private var shortest = Int.MAX_VALUE
    private var firstAtMillis = 0L

    fun observe(samplesRead: Int, nowMillis: Long) {
        if (reads >= REPORT_AFTER) return
        if (reads == 0) firstAtMillis = nowMillis
        reads++
        samples += samplesRead
        if (samplesRead < shortest) shortest = samplesRead
        if (reads == REPORT_AFTER) log(nowMillis)
    }

    private fun log(nowMillis: Long) {
        if (!clapTelemetryEnabled) return
        // One fewer interval than reads: the first read starts the clock rather than filling one.
        val periodMillis = (nowMillis - firstAtMillis).toDouble() / (reads - 1)
        Log.d(
            CLAP_TELEMETRY_TAG,
            String.format(
                Locale.US,
                "reads n=%d meanSamples=%.0f shortest=%d asked=%d " +
                    "measuredPeriod=%.1f ms configPeriod=%.1f ms",
                reads,
                samples.toDouble() / reads,
                shortest,
                ClapAudioConfig.WINDOW_SAMPLES,
                periodMillis,
                ClapAudioConfig.millisFor(ClapAudioConfig.WINDOW_SAMPLES),
            ),
        )
    }

    private companion object {
        /** About two seconds of audio: enough to average out a slow first read after startRecording. */
        const val REPORT_AFTER = 100
    }
}
