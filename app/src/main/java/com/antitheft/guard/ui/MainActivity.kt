package com.antitheft.guard.ui

import android.Manifest
import android.content.pm.PackageManager
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
import com.antitheft.guard.ui.theme.GuardTheme
import org.koin.androidx.compose.koinViewModel

class MainActivity : ComponentActivity() {

    /**
     * What to do once the user answers the notification prompt. Guard is useless without
     * notifications, so arming waits for the answer instead of switching on regardless.
     */
    private var pendingAction: (() -> Unit)? = null

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val action = pendingAction
        pendingAction = null
        if (granted) action?.invoke()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            GuardTheme {
                val viewModel = koinViewModel<HomeViewModel>()
                val settings by viewModel.settings.collectAsStateWithLifecycle()

                Surface(color = MaterialTheme.colorScheme.background) {
                    HomeScreen(
                        settings = settings,
                        onChargerAlertsChange = { enabled ->
                            if (enabled) {
                                withNotificationPermission { viewModel.setChargerAlertsEnabled(true) }
                            } else {
                                viewModel.setChargerAlertsEnabled(false)
                            }
                        },
                        modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars),
                    )
                }
            }
        }
    }

    /**
     * Asks for POST_NOTIFICATIONS at the moment a feature is armed, rather than on launch, so the
     * prompt arrives when its purpose is obvious.
     */
    private fun withNotificationPermission(action: () -> Unit) {
        val alreadyGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

        if (alreadyGranted) {
            action()
            return
        }

        pendingAction = action
        requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
