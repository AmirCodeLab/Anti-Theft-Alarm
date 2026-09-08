package com.antitheft.guard.core.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether clap detection is allowed to claim the microphone right now.
 *
 * Android refuses to let a background start take a microphone foreground service, and a reboot is
 * a background start. The gate therefore opens only once the user is actually in the app. Until
 * then the clap preference stays saved but inactive, which is the honest state — better than a
 * switch that reads "on" over a microphone that was never opened.
 */
class MicrophoneGate {

    private val _isOpen = MutableStateFlow(false)
    val isOpen: StateFlow<Boolean> = _isOpen.asStateFlow()

    fun open() {
        _isOpen.value = true
    }
}
