package com.antitheft.guard.domain.model

/**
 * Every sensing strategy the app can run. Entries exist ahead of their implementations so the
 * persisted settings and the detector registry can be keyed by a stable identity from day one.
 */
enum class DetectorId {
    CHARGING,
    MOTION,
    CLAP,
}
