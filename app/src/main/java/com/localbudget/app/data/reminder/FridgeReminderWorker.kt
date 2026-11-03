package com.localbudget.app.data.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.localbudget.app.LocalBudgetApplication
import com.localbudget.app.MainActivity
import com.localbudget.app.R
import com.localbudget.app.di.AppContainer
import com.localbudget.app.domain.food.ConsumptionEstimator
import com.localbudget.app.domain.food.FridgeReminderPlanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** Daily: re-runs the consumption estimate and posts an on-device notification if there is something new to ask. */
class FridgeReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as LocalBudgetApplication).container
        val settings = container.fridgeReminderSettings
        if (!settings.enabled.value) return Result.success()

        // Queued behind this run, so this run must not fail: WorkManager would fail the next one too
        container.fridgeReminderScheduler.scheduleNext()
        try {
            remind(container)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Fridge reminder failed; trying again tomorrow", e)
        }
        return Result.success()
    }

    private suspend fun remind(container: AppContainer) {
        val settings = container.fridgeReminderSettings
        val repository = container.fridgeRepository
        val items = repository.getAllFridgeItems().first()
        val events = repository.getAllEvents().first()
        val now = System.currentTimeMillis()
        val estimator = ConsumptionEstimator()
        val suggestions = estimator.suggest(items, events, now)
        val stockCheck = estimator.stockCheck(items, events, suggestions, now)

        val plan = FridgeReminderPlanner.plan(suggestions, stockCheck, settings.plannerState, LocalDate.now())
        val reminder = plan.reminder
        if (reminder != null && !post(reminder)) {
            // Not shown (permission revoked): leave the state so the next run tries again
            return
        }
        settings.plannerState = plan.state
    }

    private fun post(reminder: FridgeReminderPlanner.Reminder): Boolean {
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false

        createChannel(context)
        val open = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_ROUTE, MainActivity.ROUTE_FRIDGE)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_fridge)
            .setContentTitle(reminder.title)
            .setContentText(reminder.text.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        @Suppress("MissingPermission") // checked above
        manager.notify(NOTIFICATION_ID, notification)
        return true
    }

    companion object {
        private const val TAG = "FridgeReminderWorker"
        private const val CHANNEL_ID = "fridge_reminder"
        private const val NOTIFICATION_ID = 1001

        fun createChannel(context: Context) {
            val channel = NotificationChannel(CHANNEL_ID, "冷蔵庫の確認", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "使い切ったかもしれない食材や、在庫チェックのお知らせ（1日1回まで）"
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
