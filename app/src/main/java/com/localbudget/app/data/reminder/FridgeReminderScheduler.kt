package com.localbudget.app.data.reminder

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.localbudget.app.domain.food.FridgeReminderPlanner
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Runs [FridgeReminderWorker] once a day around the user's selected time.
 * Each run is a one-off aimed at the next reminder time, and the worker queues the following
 * one. A 1-day periodic request would count from when the last run actually happened, so every
 * delay from Doze would carry over and push the reminder later and later into the night.
 * WorkManager keeps the queued run across reboots.
 */
class FridgeReminderScheduler(private val context: Context, private val settings: FridgeReminderSettings) {

    fun apply(enabled: Boolean) {
        val workManager = WorkManager.getInstance(context)
        // Periodic schedule from earlier versions, replaced by the chain below
        workManager.cancelUniqueWork(LEGACY_PERIODIC_WORK_NAME)
        workManager.cancelUniqueWork(LEGACY_TEST_WORK_NAME)
        if (!enabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        // KEEP: called on every app start, which must not push the next run back
        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, nextRunRequest())
    }

    /** Replace the existing schedule after the user changes the time. */
    fun reschedule() {
        if (settings.enabled.value) {
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, nextRunRequest())
        }
    }

    /** Called by a daily run: queues the next one behind itself (REPLACE would cancel the running one). */
    fun scheduleNext() {
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, nextRunRequest())
    }

    private fun nextRunRequest(): OneTimeWorkRequest {
        val delay = FridgeReminderPlanner.delayUntilNext(ZonedDateTime.now(), settings.time.value)
        return OneTimeWorkRequestBuilder<FridgeReminderWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .build()
    }

    private companion object {
        const val WORK_NAME = "fridge_reminder_daily"
        const val LEGACY_PERIODIC_WORK_NAME = "fridge_reminder"
        const val LEGACY_TEST_WORK_NAME = "fridge_reminder_test"
    }
}
