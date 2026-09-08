package com.antitheft.guard.detector

import com.antitheft.guard.domain.model.DetectorId
import com.antitheft.guard.domain.model.GuardEvent
import kotlinx.coroutines.flow.Flow

/**
 * One way of sensing a threat.
 *
 * [events] must return a cold flow that acquires its sensor, receiver or recorder on collection
 * and releases it in `awaitClose`, so cancelling the flow is the only teardown anyone has to
 * remember. A detector never notifies, plays audio or reads settings — it only reports what it saw.
 */
interface ThreatDetector {

    val id: DetectorId

    fun events(): Flow<GuardEvent>

    /**
     * The foreground service type this detector's hardware requires, or 0 for hardware Android
     * does not consider sensitive.
     *
     * A detector declares its own requirement so the service can claim exactly the capabilities
     * that are armed, without knowing which feature is which. Whether the detector may run at all
     * is settled before this is read: a detector missing its runtime permission never reaches the
     * armed set, so a type declared here is always one the app can back up.
     */
    val foregroundServiceType: Int get() = 0
}
