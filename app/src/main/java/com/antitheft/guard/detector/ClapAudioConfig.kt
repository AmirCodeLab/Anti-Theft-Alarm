package com.antitheft.guard.detector

import android.media.AudioFormat

/**
 * The one description of the audio the clap path works on.
 *
 * It sits apart from [ClapDetector] because the recorder, the analyser and the telemetry all have
 * to agree on what a window is. A second copy of these numbers would be a bug waiting for someone
 * to change one of them.
 */
internal object ClapAudioConfig {

    /** Speech and claps live well below 8 kHz, so 16 kHz costs half the samples for no loss. */
    const val SAMPLE_RATE_HZ = 16_000

    /** 512 samples is 32 ms at 16 kHz — inside the 20–50 ms window a clap's attack fits. */
    const val WINDOW_SAMPLES = 512

    /**
     * 96 samples is 6 ms at 16 kHz. A clap's attack is over in a few milliseconds, so a zero
     * crossing rate taken across a whole window is mostly decay and room resonance; this is the
     * span that gets held against the attack itself.
     */
    const val ZCR_PEAK_SAMPLES = 96

    const val BYTES_PER_SAMPLE = 2
    const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
    const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

    fun millisFor(samples: Int): Double = samples * MILLIS_PER_SECOND / SAMPLE_RATE_HZ

    private const val MILLIS_PER_SECOND = 1_000.0
}
