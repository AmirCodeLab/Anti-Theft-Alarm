package com.antitheft.guard

import android.app.Application
import com.antitheft.guard.core.notification.GuardNotifier
import com.antitheft.guard.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class AntiTheftApp : Application() {

    override fun onCreate() {
        super.onCreate()

        val koin = startKoin {
            androidContext(this@AntiTheftApp)
            modules(appModule)
        }.koin

        // Channels must exist before the service posts anything, including after a reboot where
        // the boot receiver may start the service before any screen is shown.
        koin.get<GuardNotifier>().createChannels()
    }
}
