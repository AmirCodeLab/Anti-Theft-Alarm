package com.antitheft.guard.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Dark only, on purpose: this app is looked at in the dark, next to a bed, and a light mode would
// mean maintaining a second meaning for amber and red.
private val GuardColors = darkColorScheme(
    primary = Amber,
    onPrimary = Marine,
    background = Marine,
    onBackground = TextPrimary,
    surface = MarineRaised,
    onSurface = TextPrimary,
    onSurfaceVariant = TextMuted,
    outline = MarineOutline,
    error = AlarmRed,
    onError = Marine,
)

@Composable
fun GuardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GuardColors,
        typography = GuardTypography,
        content = content,
    )
}
