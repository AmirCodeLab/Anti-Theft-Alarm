package com.antitheft.guard.detector

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.antitheft.guard.domain.model.DetectorId
import com.antitheft.guard.domain.model.GuardEvent
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Reports the phone being plugged in and unplugged.
 *
 * The receiver is registered at runtime instead of in the manifest: the service is alive for
 * exactly as long as protection is armed, so a runtime registration costs nothing while disarmed
 * and sidesteps the background delivery limits a manifest receiver would hit.
 */
class ChargingDetector(private val context: Context) : ThreatDetector {

    override val id = DetectorId.CHARGING

    override fun events(): Flow<GuardEvent> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val event = when (intent?.action) {
                    Intent.ACTION_POWER_CONNECTED -> GuardEvent.ChargerConnected
                    Intent.ACTION_POWER_DISCONNECTED -> GuardEvent.ChargerDisconnected
                    else -> return
                }
                trySend(event)
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        // Both actions are protected system broadcasts, so nothing outside the system ever needs
        // to reach this receiver.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        awaitClose { context.unregisterReceiver(receiver) }
    }
}
