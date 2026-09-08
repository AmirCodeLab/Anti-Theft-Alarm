package com.antitheft.guard.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.antitheft.guard.R

/**
 * The only red in the app. It appears solely while an alarm is actually sounding, so the colour
 * keeps meaning "this is happening right now" rather than "something is vaguely wrong".
 */
@Composable
fun StopAlarmButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        ),
    ) {
        Text(
            text = stringResource(R.string.home_stop_alarm),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
