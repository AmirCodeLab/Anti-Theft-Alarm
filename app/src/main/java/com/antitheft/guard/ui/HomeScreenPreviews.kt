package com.antitheft.guard.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.antitheft.guard.domain.model.ClapPauseReason
import com.antitheft.guard.domain.model.ProtectionSettings
import com.antitheft.guard.ui.theme.GuardTheme

@Preview
@Composable
private fun HomeScreenArmedPreview() {
    GuardTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            HomeScreen(
                settings = ProtectionSettings(
                    chargerAlertsEnabled = true,
                    motionDetectionEnabled = true,
                    clapDetectionEnabled = true,
                ),
                isAlarmPlaying = false,
                onChargerAlertsChange = {},
                onMotionDetectionChange = {},
                onClapDetectionChange = {},
                onOpenAppSettings = {},
                onStopAlarm = {},
            )
        }
    }
}

@Preview
@Composable
private fun HomeScreenAlarmingPreview() {
    GuardTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            HomeScreen(
                settings = ProtectionSettings(
                    motionDetectionEnabled = true,
                    clapDetectionEnabled = true,
                    clapPauseReason = ClapPauseReason.MICROPHONE_DENIED,
                ),
                isAlarmPlaying = true,
                onChargerAlertsChange = {},
                onMotionDetectionChange = {},
                onClapDetectionChange = {},
                onOpenAppSettings = {},
                onStopAlarm = {},
            )
        }
    }
}
