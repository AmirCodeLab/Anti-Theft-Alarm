package com.antitheft.guard.service

import android.content.pm.ServiceInfo
import android.os.Build
import com.antitheft.guard.detector.ThreatDetector
import com.antitheft.guard.domain.model.DetectorId

internal const val NO_DETECTOR_TYPES = 0

/** Each detector states its own requirement, so this stays free of any one feature's name. */
internal fun List<ThreatDetector>.typesRequiredBy(armed: Set<DetectorId>): Int =
    filter { it.id in armed }
        .fold(NO_DETECTOR_TYPES) { types, detector -> types or detector.foregroundServiceType }

/**
 * Guard is a security monitor first, which is what `specialUse` says; anything a detector adds on
 * top is a capability it actually holds right now. Below API 34 the platform takes no mask at all.
 */
internal fun foregroundTypeMask(detectorTypes: Int): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or detectorTypes
    } else {
        0
    }
