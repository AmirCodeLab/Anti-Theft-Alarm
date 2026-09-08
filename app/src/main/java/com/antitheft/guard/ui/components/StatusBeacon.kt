package com.antitheft.guard.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * The single loud element on the screen. The ring pulses only while armed — motion here means
 * "this is live right now", so it has to stop the moment protection does.
 */
@Composable
fun StatusBeacon(armed: Boolean, modifier: Modifier = Modifier) {
    val accent = if (armed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline

    Box(modifier = modifier.size(BEACON_SIZE), contentAlignment = Alignment.Center) {
        if (armed) {
            PulsingRing(color = accent)
        }
        Canvas(Modifier.size(BEACON_SIZE)) {
            drawCircle(color = accent, radius = CORE_RADIUS.toPx())
            drawCircle(
                color = accent.copy(alpha = 0.35f),
                radius = size.minDimension / 2 - RING_WIDTH.toPx(),
                style = Stroke(width = RING_WIDTH.toPx()),
            )
        }
    }
}

@Composable
private fun PulsingRing(color: Color) {
    val transition = rememberInfiniteTransition(label = "beacon")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse",
    )

    Canvas(Modifier.size(BEACON_SIZE)) {
        val maxRadius = size.minDimension / 2
        val radius = CORE_RADIUS.toPx() + (maxRadius - CORE_RADIUS.toPx()) * progress
        drawCircle(
            color = color.copy(alpha = 0.45f * (1f - progress)),
            radius = radius,
            style = Stroke(width = RING_WIDTH.toPx()),
        )
    }
}

private val BEACON_SIZE = 132.dp
private val CORE_RADIUS = 22.dp
private val RING_WIDTH = 2.dp
