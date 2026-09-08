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
}
