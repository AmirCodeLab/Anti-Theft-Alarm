package com.antitheft.guard.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.antitheft.guard.R
import com.antitheft.guard.domain.model.ProtectionSettings
import com.antitheft.guard.ui.components.ProtectionToggleRow
import com.antitheft.guard.ui.components.StatusBeacon
import com.antitheft.guard.ui.theme.GuardTheme

@Composable
fun HomeScreen(
    settings: ProtectionSettings,
    onChargerAlertsChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(56.dp))

        StatusBeacon(armed = settings.isArmed)

        Spacer(Modifier.height(28.dp))

        Text(
            text = stringResource(
                if (settings.isArmed) R.string.home_headline_armed else R.string.home_headline_disarmed,
            ),
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(
                if (settings.isArmed) R.string.home_subhead_armed else R.string.home_subhead_disarmed,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(40.dp))

        ProtectionToggleRow(
            title = stringResource(R.string.feature_charger_title),
            description = stringResource(R.string.feature_charger_description),
            checked = settings.chargerAlertsEnabled,
            onCheckedChange = onChargerAlertsChange,
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.home_footnote),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        )

        Spacer(Modifier.height(40.dp))
    }
}

@Preview
@Composable
private fun HomeScreenArmedPreview() {
    GuardTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            HomeScreen(
                settings = ProtectionSettings(chargerAlertsEnabled = true),
                onChargerAlertsChange = {},
            )
        }
    }
}
