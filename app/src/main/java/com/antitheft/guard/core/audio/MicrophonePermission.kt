package com.antitheft.guard.core.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the microphone permission is held, as something that can be re-read.
 *
 * Granting RECORD_AUDIO from system Settings does not restart the process, so a value read once
 * at startup goes stale exactly when it matters — on the return trip from Settings, which is the
 * recovery path this exists to support. [refresh] is called whenever the app comes back to the
 * foreground.
 */
class MicrophonePermission(private val context: Context) {

    private val _isGranted = MutableStateFlow(readFromSystem())
    val isGranted: StateFlow<Boolean> = _isGranted.asStateFlow()

    fun refresh() {
        _isGranted.value = readFromSystem()
    }

    private fun readFromSystem(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
}
