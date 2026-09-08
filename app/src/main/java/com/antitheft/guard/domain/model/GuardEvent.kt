package com.antitheft.guard.domain.model

/**
 * Something a detector saw. Events carry their [source] so a single handler can react to the
 * merged stream of every armed detector without knowing which flow an event arrived on.
 */
sealed interface GuardEvent {

    val source: DetectorId

    data object ChargerConnected : GuardEvent {
        override val source = DetectorId.CHARGING
    }

    data object ChargerDisconnected : GuardEvent {
        override val source = DetectorId.CHARGING
    }
}
