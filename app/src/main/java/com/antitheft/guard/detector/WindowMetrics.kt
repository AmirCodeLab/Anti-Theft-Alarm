package com.antitheft.guard.detector

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Measurements taken over one window of PCM.
 *
 * All of them read the caller's buffer in place and allocate nothing, because they run on the
 * audio path where a dropped window is a missed clap. Each reduces a window to a single number; no
 * samples are retained, copied or logged, which is what keeps the app's privacy claim true.
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
 * Sign changes per sample across the whole window, measured about the window's own mean so a
 * microphone's DC offset cannot pin every sample to one side and report a rate of zero.
 *
 * Returns a fraction between 0 and 1. Nothing acts on it yet: it is recorded to find out whether
 * it separates a clap's broadband crack from the low thud of a knock or a door.
 */
internal fun zeroCrossingRate(samples: ShortArray, length: Int): Double =
    zeroCrossingRate(samples, 0, length)

/**
 * The same measurement over a [span]-sample stretch centred on the loudest sample of the window.
 *
 * A clap is an attack of a few milliseconds followed by a much longer decay, so a rate taken over
 * the whole window mostly describes the decay and the room. This one describes the crack. The span
 * is clamped rather than centred exactly when the peak lands near an edge: a shifted window of the
 * intended length says more than a centred one of half of it.
 */
internal fun peakZeroCrossingRate(samples: ShortArray, length: Int, span: Int): Double {
    if (length < 2) return 0.0

    val start = (peakIndex(samples, length) - span / 2)
        .coerceIn(0, (length - span).coerceAtLeast(0))
    return zeroCrossingRate(samples, start, (start + span).coerceAtMost(length))
}

/** Index of the largest sample by magnitude, compared as [Int] so [Short.MIN_VALUE] cannot wrap. */
private fun peakIndex(samples: ShortArray, length: Int): Int {
    var peak = 0
    var peakMagnitude = -1
    for (index in 0 until length) {
        val magnitude = abs(samples[index].toInt())
        if (magnitude > peakMagnitude) {
            peakMagnitude = magnitude
            peak = index
        }
    }
    return peak
}

private fun zeroCrossingRate(samples: ShortArray, from: Int, until: Int): Double {
    val length = until - from
    if (length < 2) return 0.0

    var sum = 0.0
    for (index in from until until) sum += samples[index].toDouble()
    val mean = sum / length

    var crossings = 0
    var wasPositive = samples[from].toDouble() - mean >= 0.0
    for (index in from + 1 until until) {
        val isPositive = samples[index].toDouble() - mean >= 0.0
        if (isPositive != wasPositive) crossings++
        wasPositive = isPositive
    }
    return crossings.toDouble() / (length - 1)
}
