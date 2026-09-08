package com.antitheft.guard.domain.model

/**
 * Why clap detection is switched on but not listening.
 *
 * The two causes are not interchangeable: one clears itself the moment the user opens the app,
 * the other needs them to go and grant something. They therefore need different words and offer
 * different actions, which a single "paused" flag could not express.
 */
enum class ClapPauseReason {
    NONE,

    /** Android will not let a background start claim the microphone; opening the app fixes it. */
    AWAITING_APP_OPEN,

    /** The microphone permission is not granted. Only the user can change that, in Settings. */
    MICROPHONE_DENIED,
}
