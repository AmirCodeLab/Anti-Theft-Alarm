package com.antitheft.guard.di

import com.antitheft.guard.core.notification.GuardNotifier
import com.antitheft.guard.data.settings.DataStoreSettingsRepository
import com.antitheft.guard.detector.ChargingDetector
import com.antitheft.guard.detector.ThreatDetector
import com.antitheft.guard.domain.repository.SettingsRepository
import com.antitheft.guard.service.GuardServiceController
import com.antitheft.guard.ui.HomeViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** The wiring diagram for the app. */
val appModule = module {

    single<SettingsRepository> { DataStoreSettingsRepository(androidContext()) }
    single { GuardNotifier(androidContext()) }
    single { GuardServiceController(androidContext(), get()) }

    // The detector registry. Adding a feature means adding one line here and nothing else.
    single<List<ThreatDetector>> {
        listOf(
            ChargingDetector(androidContext()),
        )
    }

    viewModel { HomeViewModel(get(), get()) }
}
