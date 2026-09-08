package com.antitheft.guard.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.antitheft.guard.R
import com.antitheft.guard.domain.model.ClapPauseReason

/**
 * Why clap detection is on but silent, and what the user can do about it.
 *
 * Kept in the quiet part of the palette on purpose: amber means armed and red means an alarm is
 * sounding right now. This is neither — it is a state, and borrowing either colour would cost
 * both of them their meaning.
 */
@Composable
fun ClapPauseNotice(
    reason: ClapPauseReason,
    onOpenAppSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (reason == ClapPauseReason.NONE) return

    Column(modifier) {
        Text(
            text = stringResource(
                when (reason) {
                    ClapPauseReason.MICROPHONE_DENIED -> R.string.clap_paused_microphone_denied
                    else -> R.string.clap_paused_awaiting_app_open
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Only one of the two is something the user can act on from here; the other clears itself.
        if (reason == ClapPauseReason.MICROPHONE_DENIED) {
            Spacer(Modifier.height(4.dp))
            TextButton(
                onClick = onOpenAppSettings,
                contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier.padding(start = 0.dp),
            ) {
                Text(
                    text = stringResource(R.string.clap_paused_open_settings),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}
