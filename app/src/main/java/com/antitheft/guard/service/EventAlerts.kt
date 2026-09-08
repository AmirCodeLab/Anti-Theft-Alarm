package com.antitheft.guard.service

import androidx.annotation.StringRes
import com.antitheft.guard.R
import com.antitheft.guard.domain.model.GuardEvent

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
}
