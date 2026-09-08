package com.antitheft.guard.domain.model

/**
 * What the user has switched on. The single source of truth for the whole app: the UI writes it,
 * the service reads it and shuts itself down when nothing is left armed.
 */
data class ProtectionSettings(
    val chargerAlertsEnabled: Boolean = false,
    val motionDetectionEnabled: Boolean = false,
    val clapDetectionEnabled: Boolean = false,
    /** Switched on, but not listening — and why. */
    val clapPauseReason: ClapPauseReason = ClapPauseReason.NONE,
) {

    /**
     * Only what is genuinely running. A paused detector is left out here rather than filtered
     * again downstream, so the ongoing notice and the foreground service type mask come out right
     * as a consequence instead of each repeating the same check.
     */
    val armedDetectors: Set<DetectorId> = buildSet {
        if (chargerAlertsEnabled) add(DetectorId.CHARGING)
        if (motionDetectionEnabled) add(DetectorId.MOTION)
        if (clapDetectionEnabled && clapPauseReason == ClapPauseReason.NONE) add(DetectorId.CLAP)
    }

    val isArmed: Boolean = armedDetectors.isNotEmpty()
}
