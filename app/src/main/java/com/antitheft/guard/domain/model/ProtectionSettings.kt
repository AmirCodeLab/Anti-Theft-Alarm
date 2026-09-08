package com.antitheft.guard.domain.model

/**
 * What the user has switched on. The single source of truth for the whole app: the UI writes it,
 * the service reads it and shuts itself down when nothing is left armed.
 */
data class ProtectionSettings(
    val chargerAlertsEnabled: Boolean = false,
) {

    val armedDetectors: Set<DetectorId> = buildSet {
        if (chargerAlertsEnabled) add(DetectorId.CHARGING)
    }

    val isArmed: Boolean = armedDetectors.isNotEmpty()
}
