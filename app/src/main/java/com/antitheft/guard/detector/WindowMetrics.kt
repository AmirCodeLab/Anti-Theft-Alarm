package com.antitheft.guard.detector

import kotlin.math.sqrt

/**
 * Measurements taken over one window of PCM.
 *
 * Both read the caller's buffer in place and allocate nothing, because they run on the audio path
 * where a dropped window is a missed clap. Each reduces a window to a single number; no samples
 * are retained, copied or logged, which is what keeps the app's privacy claim true.
 */

/** Loudness of the window, in raw 16-bit sample units. */
internal fun rms(samples: ShortArray, length: Int): Double {
    var sumOfSquares = 0.0
    for (index in 0 until length) {
        val sample = samples[index].toDouble()
        sumOfSquares += sample * sample
    }
    return sqrt(sumOfSquares / length)
}

/**
 * Sign changes per sample, measured about the window's own mean so a microphone's DC offset
 * cannot pin every sample to one side and report a rate of zero.
 *
 * Returns a fraction between 0 and 1. Nothing acts on it yet: it is recorded to find out whether
 * it separates a clap's broadband crack from the low thud of a knock or a door.
 */
internal fun zeroCrossingRate(samples: ShortArray, length: Int): Double {
    if (length < 2) return 0.0

    var sum = 0.0
    for (index in 0 until length) sum += samples[index].toDouble()
    val mean = sum / length

    var crossings = 0
    var wasPositive = samples[0].toDouble() - mean >= 0.0
    for (index in 1 until length) {
        val isPositive = samples[index].toDouble() - mean >= 0.0
        if (isPositive != wasPositive) crossings++
        wasPositive = isPositive
    }
    return crossings.toDouble() / (length - 1)
}
