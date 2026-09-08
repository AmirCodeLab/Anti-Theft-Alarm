package com.antitheft.guard.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antitheft.guard.core.audio.MicrophoneGate
import com.antitheft.guard.core.audio.MicrophonePermission
import com.antitheft.guard.core.notification.GuardNotifier
import com.antitheft.guard.ui.theme.GuardTheme
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel

class MainActivity : ComponentActivity() {

    private val microphoneGate: MicrophoneGate by inject()
    private val microphonePermission: MicrophonePermission by inject()
    private val notifier: GuardNotifier by inject()

    /**
     * What to do once the user answers the permission prompts. Guard is useless without them, so
     * arming waits for the answer instead of switching on regardless.
     */
    private var pendingAction: (() -> Unit)? = null

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        microphonePermission.refresh()
        val action = pendingAction
        pendingAction = null
        if (results.values.all { it }) action?.invoke()
    }

    override fun onResume() {
        super.onResume()
        // The user may have just come back from system Settings, which grants without restarting
        // the process. Nothing else would tell us the answer changed.
        microphonePermission.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // The user is here, in the foreground, which is the only moment Android will allow the
        // microphone to be claimed. Anything paused since boot can now resume.
        microphoneGate.open()
        notifier.cancel(GuardNotifier.NOTICE_ID)

        setContent {
            GuardTheme {
                val viewModel = koinViewModel<HomeViewModel>()
                val settings by viewModel.settings.collectAsStateWithLifecycle()
                val isAlarmPlaying by viewModel.isAlarmPlaying.collectAsStateWithLifecycle()

                Surface(color = MaterialTheme.colorScheme.background) {
                    HomeScreen(
                        settings = settings,
                        isAlarmPlaying = isAlarmPlaying,
                        onChargerAlertsChange = { enabled ->
                            onArm(enabled, alerting()) { viewModel.setChargerAlertsEnabled(it) }
                        },
                        onMotionDetectionChange = { enabled ->
                            onArm(enabled, alerting()) { viewModel.setMotionDetectionEnabled(it) }
                        },
                        onClapDetectionChange = { enabled ->
                            onArm(enabled, alerting() + Manifest.permission.RECORD_AUDIO) {
                                viewModel.setClapDetectionEnabled(it)
                            }
                        },
                        onOpenAppSettings = ::openAppSettings,
                        onStopAlarm = viewModel::stopAlarm,
                        modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars),
                    )
                }
            }
        }
    }

    /**
     * Permissions are asked for at the moment a feature is armed, never on launch, so each prompt
     * arrives when its purpose is obvious. Disarming never needs anything.
     */
    private fun onArm(enabled: Boolean, permissions: List<String>, apply: (Boolean) -> Unit) {
        if (!enabled) {
            apply(false)
            return
        }

        val missing = permissions.filterNot { granted(it) }
        if (missing.isEmpty()) {
            apply(true)
            return
        }

        pendingAction = { apply(true) }
        requestPermissions.launch(missing.toTypedArray())
    }

    /**
     * Sends the user to this app's system settings. Once a permission has been denied twice
     * Android stops showing the prompt at all, so Settings is the only way back.
     */
    private fun openAppSettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null),
            ),
        )
    }

    /** Posting alerts is what every feature has in common, and only API 33 asks permission for it. */
    private fun alerting(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyList()
        }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
