package com.antitheft.guard.service

import android.content.Context
import androidx.annotation.StringRes
import com.antitheft.guard.R
import com.antitheft.guard.domain.model.DetectorId
import com.antitheft.guard.domain.model.GuardEvent
import com.antitheft.guard.domain.model.ProtectionSettings

/** The words shown when an event fires. */
internal data class AlertCopy(
    @param:StringRes val title: Int,
    @param:StringRes val text: Int,
)

/**
 * Maps events to their alert copy. This is the reaction table, kept apart from the service's
 * lifecycle so a new detector adds a branch here and nothing else.
 */
internal fun GuardEvent.alertCopy(): AlertCopy = when (this) {
    GuardEvent.ChargerConnected -> AlertCopy(
        title = R.string.alert_charger_connected_title,
        text = R.string.alert_charger_connected_text,
    )

    GuardEvent.ChargerDisconnected -> AlertCopy(
        title = R.string.alert_charger_disconnected_title,
        text = R.string.alert_charger_disconnected_text,
    )

    GuardEvent.MotionDetected -> AlertCopy(
        title = R.string.alert_motion_title,
        text = R.string.alert_motion_text,
    )
}

/** Names every armed feature, so the ongoing notice always says what is actually being watched. */
internal fun Context.statusText(settings: ProtectionSettings): String = settings.armedDetectors
    .mapNotNull { detector ->
        when (detector) {
            DetectorId.CHARGING -> getString(R.string.status_watching_charger)
            DetectorId.MOTION -> getString(R.string.status_watching_motion)
            // Not implemented yet, so it can never be armed and never reaches this list.
            DetectorId.CLAP -> null
        }
    }
    .joinToString(separator = getString(R.string.status_separator))
