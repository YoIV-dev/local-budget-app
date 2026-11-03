package com.localbudget.app

import android.app.Application
import com.localbudget.app.di.AppContainer
import com.localbudget.app.di.AppDataContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LocalBudgetApplication : Application() {
    lateinit var container: AppContainer
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppDataContainer(this)
        // Re-enqueues the daily reminder if WorkManager lost it (e.g. after the app's data was restored)
        container.fridgeReminderScheduler.apply(container.fridgeReminderSettings.enabled.value)
        // Earlier scans' names, read again by today's rules (once; a failure is retried next launch)
        appScope.launch { runCatching { container.savedScanTidier.runOnce() } }
    }
}
